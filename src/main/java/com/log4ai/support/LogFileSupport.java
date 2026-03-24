package com.log4ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.log4ai.config.LogAgentProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 大文件友好的读行、tail、gzip 与按关键字检索（带上下行上下文）。支持按 {@code serviceId} 解析多套日志根路径（业务服务 /
 * 宿主机挂载）。
 *
 * <p>性能：普通文本 {@link #tail} 优先从文件末尾逆窗口解析，避免 GB 级日志全表扫描；{@code .gz} 仍为顺序解压。{@link
 * #searchInFile} 对关键字单次编译大小写不敏感匹配，避免每行构造小写串。
 *
 * <p><b>路径安全</b>：所有读文件操作必须在 {@link ResolvedPaths#calendarRoot()} 之下；若配置了 {@code
 * log4ai.logs.allowed-read-path-prefixes}，还须命中其一。{@code searchNamedLog} 仅允许日历根内路径（禁止任意绝对路径逃逸）。
 */
public final class LogFileSupport {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** 超过此大小的非压缩文件 tail 仍用顺序读，避免单次窗口超过 int 与内存尖刺。 */
  private static final long TAIL_FROM_END_MAX_FILE_BYTES = 512L * 1024 * 1024;

  private final LogAgentProperties props;
  private final LogContentSanitizer sanitizer;
  private final Path workspace;
  private final Log4AiSystemLogPaths systemLogPaths;

  public LogFileSupport(
      LogAgentProperties props,
      LogContentSanitizer sanitizer,
      Path workspace,
      Log4AiSystemLogPaths systemLogPaths) {
    this.props = props;
    this.sanitizer = sanitizer;
    this.workspace = workspace;
    this.systemLogPaths = systemLogPaths;
  }

  /** 解析后的活动日志与日历目录根（已转为绝对路径；列举/检索相对此根含子目录）。 */
  public static final class ResolvedPaths {
    private final Path currentLogFile;
    private final Path calendarRoot;
    private final String serviceKey;

    public ResolvedPaths(Path currentLogFile, Path calendarRoot, String serviceKey) {
      this.currentLogFile = currentLogFile;
      this.calendarRoot = calendarRoot;
      this.serviceKey = serviceKey;
    }

    public Path getCurrentLogFile() {
      return currentLogFile;
    }

    public Path getCalendarRoot() {
      return calendarRoot;
    }

    public String getServiceKey() {
      return serviceKey;
    }

    public Path currentLogFile() {
      return currentLogFile;
    }

    public Path calendarRoot() {
      return calendarRoot;
    }

    public String serviceKey() {
      return serviceKey;
    }
  }

  /**
   * 根据 serviceId 解析路径。未配置 {@code log4ai.logs.services} 时使用本进程 {@code logging.file.*} 与约定路径自动解析。
   * 已配置 services 时，{@code serviceId} 为空则使用 {@code default-service}，且键必须存在。
   *
   * @throws IllegalArgumentException 多服务已配置但 serviceId 非法或未配置 logPath
   */
  public ResolvedPaths resolvePaths(String serviceIdInput) {
    Map<String, LogAgentProperties.ServiceLogs> map = props.getLogs().getServices();
    if (map == null || map.isEmpty()) {
      return systemLogPaths.resolve(workspace);
    }
    String id =
        serviceIdInput == null || serviceIdInput.trim().isEmpty()
            ? props.getLogs().getDefaultService().trim()
            : serviceIdInput.trim();
    LogAgentProperties.ServiceLogs sl = map.get(id);
    if (sl == null) {
      throw new IllegalArgumentException(
          "未知 serviceId=\"" + id + "\"。已配置键: " + String.join(", ", map.keySet()));
    }
    if (sl.getLogPath() == null) {
      throw new IllegalArgumentException("服务 \"" + id + "\" 未配置日志路径（logPath）");
    }
    return resolveRegisteredServicePaths(sl, id);
  }

  private ResolvedPaths resolveRegisteredServicePaths(LogAgentProperties.ServiceLogs sl, String id) {
    Path r = resolve(sl.getLogPath());
    if (Files.isDirectory(r, LinkOption.NOFOLLOW_LINKS)) {
      Path dir = r.normalize();
      Path app = dir.resolve("application.log");
      Path spr = dir.resolve("spring.log");
      Path cur = Files.exists(app) ? app : spr;
      return new ResolvedPaths(cur.normalize(), dir, id);
    }
    Path file = r.normalize();
    Path parent = file.getParent();
    if (parent == null) {
      parent = workspace;
    }
    return new ResolvedPaths(file, parent.normalize(), id);
  }

  /** 供模型查看当前接入了哪些业务服务及路径（truncated）。 */
  public String describeServices() {
    Map<String, LogAgentProperties.ServiceLogs> map = props.getLogs().getServices();
    if (map == null || map.isEmpty()) {
      try {
        ResolvedPaths r = resolvePaths(null);
        return trimToBudget(
            "单实例：使用本进程自动解析的日志（依据 logging.file.name / logging.file.path，否则默认可为 logs/spring.log）。\n"
                + "- 当前活动日志文件: "
                + r.currentLogFile()
                + "\n- 日历目录（唯一根目录，含子目录）: "
                + r.calendarRoot()
                + "\n工具参数 serviceId 可传空串。\n");
      } catch (IllegalArgumentException e) {
        return trimToBudget("描述服务配置失败: " + e.getMessage());
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append("多服务日志配置。工具调用时须指定 serviceId（与下列键一致）；传空串等同 defaultService=\"")
        .append(props.getLogs().getDefaultService())
        .append("\"。\n");
    for (Map.Entry<String, LogAgentProperties.ServiceLogs> e : map.entrySet()) {
      String key = e.getKey();
      LogAgentProperties.ServiceLogs sl = e.getValue();
      sb.append("- serviceId=").append(key);
      if (sl.getDisplayName() != null && !sl.getDisplayName().trim().isEmpty()) {
        sb.append(" （").append(sl.getDisplayName()).append("）");
      }
      try {
        ResolvedPaths r = resolvePaths(key);
        sb.append("\n  活动日志: ").append(r.currentLogFile());
        sb.append("\n  日历目录（唯一根目录，含子目录）: ").append(r.calendarRoot());
      } catch (IllegalArgumentException ignored) {
        sb.append("\n  （路径解析失败）");
      }
      sb.append('\n');
    }
    return trimToBudget(sb.toString());
  }

  private Path resolve(Path p) {
    if (p == null) {
      return null;
    }
    if (p.isAbsolute()) {
      return p.normalize();
    }
    return workspace.resolve(p).normalize();
  }

  /**
   * 任意待读文件必须位于日历根之下；若配置了 {@code allowed-read-path-prefixes}，还须以其中某一前缀为根。
   */
  public void assertReadablePath(Path file, ResolvedPaths r) {
    if (file == null) {
      throw new IllegalArgumentException("路径不能为空");
    }
    Path abs = file.toAbsolutePath().normalize();
    Path root = r.calendarRoot().toAbsolutePath().normalize();
    if (!abs.startsWith(root)) {
      throw new IllegalArgumentException(
          "禁止读取日历根目录外的路径。日历根: " + root + "；请求: " + abs);
    }
    List<String> prefixes = props.getLogs().getAllowedReadPathPrefixes();
    if (prefixes != null && !prefixes.isEmpty()) {
      boolean ok = false;
      for (String ex : prefixes) {
        if (ex == null || ex.trim().isEmpty()) {
          continue;
        }
        Path pfx = java.nio.file.Paths.get(ex.trim()).toAbsolutePath().normalize();
        if (abs.startsWith(pfx)) {
          ok = true;
          break;
        }
      }
      if (!ok) {
        throw new IllegalArgumentException(
            "路径不在 log4ai.logs.allowed-read-path-prefixes 白名单内: " + abs);
      }
    }
  }

  /**
   * 将 fileName 解析为日历根下的绝对路径；相对路径相对日历根，绝对路径必须在日历根之下（防目录穿越逃逸）。
   */
  public Path resolvePathUnderCalendarRoot(String fileName, ResolvedPaths r) {
    if (fileName == null || fileName.trim().isEmpty()) {
      throw new IllegalArgumentException("fileName 不能为空");
    }
    Path root = r.calendarRoot().toAbsolutePath().normalize();
    String fn = fileName.trim().replace('\\', '/');
    Path candidate = java.nio.file.Paths.get(fn);
    Path abs = candidate.isAbsolute() ? candidate.normalize() : root.resolve(candidate).normalize();
    if (!abs.startsWith(root)) {
      throw new IllegalArgumentException(
          "文件必须位于日历目录根之下；相对路径相对于日历根，禁止解析到日历根外。");
    }
    return abs;
  }

  public String tail(Path file, int lineCount, ResolvedPaths r) throws IOException {
    assertReadablePath(file, r);
    int n = Math.min(Math.max(lineCount, 1), props.getLogs().getMaxTailLines());
    if (!Files.exists(file)) {
      return "错误：文件不存在: " + file;
    }
    String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
    boolean gzip = lower.endsWith(".gz");
    List<String> rawLines;
    if (gzip) {
      rawLines = tailLinesSequential(file, n);
    } else {
      long size = Files.size(file);
      if (size == 0) {
        rawLines = Collections.emptyList();
      } else if (size > Integer.MAX_VALUE - 16 || size > TAIL_FROM_END_MAX_FILE_BYTES) {
        rawLines = tailLinesSequential(file, n);
      } else {
        rawLines = tailLastLinesPlainFromEnd(file, n, size);
      }
    }
    return buildTailOutput(file, rawLines);
  }

  /** 读取文件开头若干行（受 maxHeadLines 限制），路径须通过日历根与白名单校验。 */
  public String head(Path file, int lineCount, ResolvedPaths r) throws IOException {
    assertReadablePath(file, r);
    int n = Math.min(Math.max(lineCount, 1), props.getLogs().getMaxHeadLines());
    if (!Files.exists(file)) {
      return "错误：文件不存在: " + file;
    }
    List<String> rawLines = new ArrayList<>();
    try (Stream<String> stream = openLines(file)) {
      java.util.Iterator<String> it = stream.iterator();
      int c = 0;
      while (it.hasNext() && c < n) {
        rawLines.add(it.next());
        c++;
      }
    }
    return buildHeadOutput(file, rawLines);
  }

  private String buildHeadOutput(Path file, List<String> rawLines) {
    int maxLen = props.getLogs().getMaxLineLength();
    StringBuilder sb = new StringBuilder();
    sb.append("文件: ").append(file).append("\n共返回开头 ").append(rawLines.size()).append(" 行。\n---\n");
    for (String l : rawLines) {
      sb.append(sanitizer.line(l, maxLen)).append('\n');
    }
    return trimToBudget(sb.toString());
  }

  /** 顺序扫描保留末尾 n 行（适用于 gzip 或超大/非 UTF-8 回退）。 */
  private List<String> tailLinesSequential(Path file, int n) throws IOException {
    Deque<String> buf = new ArrayDeque<>(n + 1);
    try (Stream<String> stream = openLines(file)) {
      stream.forEach(
          line -> {
            if (buf.size() >= n) {
              buf.pollFirst();
            }
            buf.addLast(line);
          });
    }
    return new ArrayList<>(buf);
  }

  /**
   * 从普通文本文件末尾扩大窗口直至满足行数或读完文件；仅适用 size 可放入 int 的场景。
   */
  private List<String> tailLastLinesPlainFromEnd(Path file, int lineCount, long size)
      throws IOException {
    int maxLine = props.getLogs().getMaxLineLength();
    long window = Math.min(size, Math.max(65_536L, (long) lineCount * (long) Math.min(maxLine, 8192)));
    while (true) {
      long start = size - window;
      int len = (int) (size - start);
      byte[] chunk = readFileRangeFully(file, start, len);
      String text = new String(chunk, StandardCharsets.UTF_8);
      List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
      if (start > 0 && !lines.isEmpty()) {
        lines.remove(0);
      }
      if (lines.size() >= lineCount) {
        return new ArrayList<>(lines.subList(lines.size() - lineCount, lines.size()));
      }
      if (window >= size) {
        return new ArrayList<>(lines);
      }
      long next = Math.min(size, window * 2);
      if (next <= window) {
        return new ArrayList<>(lines);
      }
      window = next;
    }
  }

  private static byte[] readFileRangeFully(Path file, long position, int length) throws IOException {
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
      ByteBuffer buf = ByteBuffer.allocate(length);
      ch.position(position);
      while (buf.hasRemaining()) {
        if (ch.read(buf) == -1) {
          break;
        }
      }
      buf.flip();
      byte[] out = new byte[buf.remaining()];
      buf.get(out);
      return out;
    }
  }

  private String buildTailOutput(Path file, List<String> rawLines) {
    int maxLen = props.getLogs().getMaxLineLength();
    StringBuilder sb = new StringBuilder();
    sb.append("文件: ").append(file).append("\n共返回末尾 ").append(rawLines.size()).append(" 行。\n---\n");
    for (String l : rawLines) {
      sb.append(sanitizer.line(l, maxLen)).append('\n');
    }
    return trimToBudget(sb.toString());
  }

  public String searchCurrent(String serviceId, String keyword, int linesBefore, int linesAfter)
      throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    return searchInFile(
        r,
        r.currentLogFile(),
        keyword,
        linesBefore,
        linesAfter,
        null,
        r.serviceKey());
  }

  /**
   * 活动日志：关键字 + 可选时间行子串过滤（行须同时包含关键字与时间子串）。
   */
  public String searchCurrentWithTimeFilter(
      String serviceId,
      String keyword,
      String timeRangeSubstring,
      int linesBefore,
      int linesAfter)
      throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    String tr =
        timeRangeSubstring == null || timeRangeSubstring.trim().isEmpty() ? null : timeRangeSubstring;
    return searchInFile(
        r,
        r.currentLogFile(),
        keyword,
        linesBefore,
        linesAfter,
        tr,
        r.serviceKey());
  }

  /**
   * 活动日志：Java 正则检索（大小写不敏感），受 maxRegexPatternLength 限制。
   */
  public String searchCurrentRegex(String serviceId, String regexPattern, int linesBefore, int linesAfter)
      throws IOException {
    if (regexPattern == null || regexPattern.trim().isEmpty()) {
      return "错误：regexPattern 不能为空";
    }
    int maxLen = props.getLogs().getMaxRegexPatternLength();
    if (regexPattern.length() > maxLen) {
      return "错误：正则过长（最大 " + maxLen + " 字符）";
    }
    ResolvedPaths r = resolvePaths(serviceId);
    final Pattern pat;
    try {
      pat = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    } catch (PatternSyntaxException e) {
      return "错误：正则语法无效: " + e.getMessage();
    }
    Predicate<String> lineMatcher = raw -> pat.matcher(raw).find();
    return searchInFileWithMatcher(
        r,
        r.currentLogFile(),
        lineMatcher,
        linesBefore,
        linesAfter,
        r.serviceKey(),
        "正则: " + regexPattern,
        null);
  }

  public String searchNamedLog(String serviceId, String fileName, String keyword, String timeRange)
      throws IOException {
    if (fileName == null || fileName.trim().isEmpty()) {
      return "错误：fileName 不能为空";
    }
    ResolvedPaths r = resolvePaths(serviceId);
    Path p = resolvePathUnderCalendarRoot(fileName, r);
    if (!Files.exists(p)) {
      return "错误：找不到日志文件: " + p;
    }
    String range = timeRange == null || timeRange.trim().isEmpty() ? null : timeRange;
    return searchInFile(
        r,
        p,
        keyword,
        props.getLogs().getDefaultLinesBefore(),
        props.getLogs().getDefaultLinesAfter(),
        range,
        r.serviceKey());
  }

  /** 基于活动日志尾部窗口统计日志级别出现次数（启发式，非结构化解析）。 */
  public String summarizeRecentLogLevels(String serviceId, int tailLineCount) throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    String tailOut = tail(r.currentLogFile(), tailLineCount, r);
    if (tailOut.startsWith("错误：")) {
      return tailOut;
    }
    List<String> lines = linesAfterMarker(tailOut, "---");
    Pattern levelPat =
        Pattern.compile(
            "\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\\b",
            Pattern.CASE_INSENSITIVE);
    Map<String, Integer> counts = new LinkedHashMap<>();
    String[] order = {"ERROR", "FATAL", "SEVERE", "WARN", "INFO", "DEBUG", "TRACE"};
    for (String o : order) {
      counts.put(o, 0);
    }
    int scanned = 0;
    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      scanned++;
      Matcher m = levelPat.matcher(line);
      if (m.find()) {
        String lv = m.group(1).toUpperCase(Locale.ROOT);
        if ("WARNING".equals(lv)) {
          lv = "WARN";
        }
        Integer current = counts.get(lv);
        counts.put(lv, current == null ? 1 : current + 1);
      }
    }
    StringBuilder sb = new StringBuilder();
    sb.append("serviceId: ").append(r.serviceKey()).append('\n');
    sb.append("文件: ").append(r.currentLogFile()).append('\n');
    sb.append("基于尾部窗口扫描行数: ").append(scanned).append("（启发式匹配级别关键字）\n");
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      if (e.getValue() > 0) {
        sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
      }
    }
    if (counts.values().stream().mapToInt(Integer::intValue).sum() == 0) {
      sb.append("（未识别到常见级别关键字，可能为非标准格式）\n");
    }
    return trimToBudget(sb.toString());
  }

  /** 日历根内文件的元信息：大小、修改时间、是否 gzip。 */
  public String getLogFileMeta(String serviceId, String fileName) throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    Path p = resolvePathUnderCalendarRoot(fileName, r);
    assertReadablePath(p, r);
    if (!Files.exists(p)) {
      return "错误：文件不存在: " + p;
    }
    long size = Files.size(p);
    java.nio.file.attribute.FileTime lmt = Files.getLastModifiedTime(p);
    boolean gz = p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
    StringBuilder sb = new StringBuilder();
    sb.append("serviceId: ").append(r.serviceKey()).append('\n');
    sb.append("路径: ").append(p).append('\n');
    sb.append("大小(字节): ").append(size).append('\n');
    sb.append("最后修改: ").append(lmt).append('\n');
    sb.append("gzip: ").append(gz).append('\n');
    return trimToBudget(sb.toString());
  }

  /**
   * 在日历根内文件中扫描前若干行，对每行尝试解析 JSON，并按点分路径匹配字段值是否包含 valueContains。
   */
  public String searchJsonFieldInLogFile(
      String serviceId, String fileName, String fieldPath, String valueContains) throws IOException {
    if (fieldPath == null || fieldPath.trim().isEmpty()) {
      return "错误：fieldPath 不能为空";
    }
    ResolvedPaths r = resolvePaths(serviceId);
    Path p = resolvePathUnderCalendarRoot(fileName, r);
    assertReadablePath(p, r);
    if (!Files.exists(p)) {
      return "错误：文件不存在: " + p;
    }
    String needle =
        valueContains == null || valueContains.trim().isEmpty() ? null : valueContains;
    int maxLines = Math.max(1, props.getLogs().getMaxJsonScanLines());
    String[] parts = fieldPath.trim().split("\\.");
    List<String> hits = new ArrayList<>();
    int maxSeg = Math.max(1, props.getLogs().getMaxMatchSegments());
    int lineNo = 0;
    try (Stream<String> stream = openLines(p)) {
      java.util.Iterator<String> it = stream.iterator();
      while (it.hasNext() && lineNo < maxLines && hits.size() < maxSeg) {
        String raw = it.next();
        lineNo++;
        String t = raw.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) {
          continue;
        }
        JsonNode node;
        try {
          node = JSON.readTree(t);
        } catch (Exception ignored) {
          continue;
        }
        JsonNode cur = node;
        for (String part : parts) {
          if (cur == null || !cur.isObject()) {
            cur = null;
            break;
          }
          cur = cur.get(part);
        }
        if (cur == null || cur.isMissingNode()) {
          continue;
        }
        String text = cur.isTextual() ? cur.asText() : cur.toString();
        if (needle == null || text.contains(needle)) {
          hits.add(
              "----- 行 "
                  + lineNo
                  + " -----\n"
                  + sanitizer.line(raw, props.getLogs().getMaxLineLength())
                  + '\n');
        }
      }
    }
    StringBuilder out = new StringBuilder();
    out.append("serviceId: ").append(r.serviceKey()).append('\n');
    out.append("文件: ").append(p).append('\n');
    out.append("字段路径: ").append(fieldPath).append('\n');
    if (needle != null) {
      out.append("值包含: ").append(needle).append('\n');
    }
    out.append("扫描行上限: ").append(maxLines).append("；命中段数: ").append(hits.size()).append('\n');
    if (hits.isEmpty()) {
      out.append("无匹配（非 JSON 行或字段不存在）。\n");
      return trimToBudget(out.toString());
    }
    for (String h : hits) {
      out.append(h);
    }
    return trimToBudget(out.toString());
  }

  /**
   * 从活动日志尾部窗口启发式提取异常相关片段（含 Exception 字样、Caused by、典型堆栈行）。
   */
  public String findExceptionBlocksInRecentLog(String serviceId, int tailLineCount) throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    String tailOut = tail(r.currentLogFile(), tailLineCount, r);
    if (tailOut.startsWith("错误：")) {
      return tailOut;
    }
    List<String> lines = linesAfterMarker(tailOut, "---");
    Pattern excStart =
        Pattern.compile(
            "Exception|Error:\\s|Caused by:|\\tat\\s+\\S+\\.(?:\\w+\\.)+\\w+\\s*\\(");
    List<String> blocks = new ArrayList<>();
    int maxBlocks = Math.max(1, props.getLogs().getMaxMatchSegments());
    StringBuilder cur = null;
    for (String line : lines) {
      boolean hit = excStart.matcher(line).find();
      if (hit) {
        if (cur != null && cur.length() > 0) {
          blocks.add(cur.toString());
          if (blocks.size() >= maxBlocks) {
            break;
          }
        }
        cur = new StringBuilder();
        cur.append(sanitizer.line(line, props.getLogs().getMaxLineLength())).append('\n');
      } else if (cur != null) {
        if (line.startsWith("\tat ") || line.trim().startsWith("... ")) {
          cur.append(sanitizer.line(line, props.getLogs().getMaxLineLength())).append('\n');
        } else if (cur.length() > 2000) {
          blocks.add(cur.toString());
          if (blocks.size() >= maxBlocks) {
            break;
          }
          cur = null;
        } else if (!line.trim().isEmpty()) {
          cur.append(sanitizer.line(line, props.getLogs().getMaxLineLength())).append('\n');
        }
      }
    }
    if (cur != null && cur.length() > 0 && blocks.size() < maxBlocks) {
      blocks.add(cur.toString());
    }
    StringBuilder sb = new StringBuilder();
    sb.append("serviceId: ").append(r.serviceKey()).append('\n');
    sb.append("文件: ").append(r.currentLogFile()).append('\n');
    sb.append("启发式提取块数: ").append(blocks.size()).append("（上限 ").append(maxBlocks).append("）\n");
    if (blocks.isEmpty()) {
      sb.append("未在尾部窗口识别到典型异常模式。\n");
      return trimToBudget(sb.toString());
    }
    for (int i = 0; i < blocks.size(); i++) {
      sb.append("===== 块 #").append(i + 1).append(" =====\n");
      sb.append(blocks.get(i));
    }
    return trimToBudget(sb.toString());
  }

  public String listCalendarLogs(String serviceId, String date) throws IOException {
    ResolvedPaths r = resolvePaths(serviceId);
    Path dir = r.calendarRoot();
    if (!Files.exists(dir)) {
      return "错误：日历目录不存在: " + dir;
    }
    String key = date == null ? "" : date.trim();
    Pattern nameFilter =
        key.isEmpty()
            ? null
            : Pattern.compile(Pattern.quote(key), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    List<String> collected = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(dir, 8)) {
      stream
          .filter(Files::isRegularFile)
          .map(
              p ->
                  dir.relativize(p)
                      .normalize()
                      .toString()
                      .replace('\\', '/'))
          .forEach(
              rel -> {
                if (nameFilter == null || nameFilter.matcher(rel).find()) {
                  collected.add(rel);
                }
              });
    }
    Collections.sort(collected);
    final int maxCollect = 5000;
    boolean overCap = false;
    final List<String> names;
    if (collected.size() > maxCollect) {
      names = new ArrayList<>(collected.subList(0, maxCollect));
      overCap = true;
    } else {
      names = collected;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("serviceId: ").append(r.serviceKey()).append('\n');
    sb.append("日历目录: ").append(dir).append('\n');
    sb.append("（递归含子目录；相对路径自日历根起）\n");
    if (overCap) {
      sb.append("匹配文件过多，仅保留前 ").append(maxCollect).append(" 条用于列举。\n");
    }
    if (key.isEmpty()) {
      sb.append("未指定日期过滤，")
          .append(overCap ? "当前列举 " : "列出全部 ")
          .append(names.size())
          .append(" 个文件:\n");
    } else {
      sb.append("过滤关键字 \"").append(key).append("\"，匹配 ").append(names.size()).append(" 个文件:\n");
    }
    int maxList = 200;
    int shown = Math.min(names.size(), maxList);
    for (int i = 0; i < shown; i++) {
      sb.append("- ").append(names.get(i)).append('\n');
    }
    if (names.size() > maxList) {
      sb.append("... 其余 ").append(names.size() - maxList).append(" 个文件未列出\n");
    }
    return trimToBudget(sb.toString());
  }

  private String searchInFile(
      ResolvedPaths r,
      Path file,
      String keyword,
      int linesBefore,
      int linesAfter,
      String timeRangeSubstring,
      String serviceKeyLabel)
      throws IOException {
    if (keyword == null || keyword.trim().isEmpty()) {
      return "错误：keyword 不能为空";
    }
    Pattern kwPattern =
        Pattern.compile(
            Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    Predicate<String> lineMatcher =
        raw ->
            kwPattern.matcher(raw).find()
                && (timeRangeSubstring == null || raw.contains(timeRangeSubstring));
    return searchInFileWithMatcher(
        r,
        file,
        lineMatcher,
        linesBefore,
        linesAfter,
        serviceKeyLabel,
        "关键字: " + keyword,
        timeRangeSubstring);
  }

  private String searchInFileWithMatcher(
      ResolvedPaths r,
      Path file,
      Predicate<String> lineMatcher,
      int linesBefore,
      int linesAfter,
      String serviceKeyLabel,
      String matchDescription,
      String timeRangeSubstringForHeader)
      throws IOException {
    assertReadablePath(file, r);
    if (!Files.exists(file)) {
      return "错误：文件不存在: " + file;
    }
    int before = Math.max(0, linesBefore);
    int after = Math.max(0, linesAfter);
    int maxSeg = Math.max(1, props.getLogs().getMaxMatchSegments());

    Deque<String> beforeBuf = new ArrayDeque<>();
    List<String> hitBlocks = new ArrayList<>();
    int segments = 0;

    try (Stream<String> stream = openLines(file)) {
      java.util.Iterator<String> it = stream.iterator();
      while (it.hasNext() && segments < maxSeg) {
        String raw = it.next();
        boolean match = lineMatcher.test(raw);
        if (match) {
          StringBuilder chunk = new StringBuilder();
          chunk.append("===== 匹配段 #").append(segments + 1).append(" =====\n");
          for (String b : beforeBuf) {
            chunk.append(sanitizer.line(b, props.getLogs().getMaxLineLength())).append('\n');
          }
          chunk.append(sanitizer.line(raw, props.getLogs().getMaxLineLength())).append('\n');
          int taken = 0;
          while (taken < after && it.hasNext()) {
            String n = it.next();
            chunk.append(sanitizer.line(n, props.getLogs().getMaxLineLength())).append('\n');
            taken++;
          }
          hitBlocks.add(chunk.toString());
          segments++;
          beforeBuf.clear();
        } else {
          if (beforeBuf.size() >= before) {
            beforeBuf.pollFirst();
          }
          beforeBuf.addLast(raw);
        }
      }
    }

    StringBuilder out = new StringBuilder();
    if (serviceKeyLabel != null) {
      out.append("serviceId: ").append(serviceKeyLabel).append('\n');
    }
    out.append("文件: ").append(file).append('\n');
    out.append(matchDescription).append('\n');
    if (timeRangeSubstringForHeader != null) {
      out.append("时间子串过滤: ").append(timeRangeSubstringForHeader).append('\n');
    }
    out.append("返回匹配段数: ").append(hitBlocks.size()).append("（每文件最多 ").append(maxSeg).append(" 段）\n");
    if (hitBlocks.isEmpty()) {
      out.append("无匹配。\n");
      return trimToBudget(out.toString());
    }
    for (String h : hitBlocks) {
      out.append(h);
    }
    if (segments >= maxSeg) {
      out.append(
          "\n[说明] 已达 maxMatchSegments 上限，更多匹配未展示；可缩小关键字或指定更具体的文件/时间。\n");
    }
    return trimToBudget(out.toString());
  }

  private Stream<String> openLines(Path file) throws IOException {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".gz")) {
      Charset cs = StandardCharsets.UTF_8;
      try {
        return new BufferedReader(
                new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), cs))
            .lines();
      } catch (MalformedInputException e) {
        Charset gbk = Charset.forName("GBK");
        return new BufferedReader(
                new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), gbk))
            .lines();
      }
    }
    try {
      return Files.lines(file, StandardCharsets.UTF_8);
    } catch (MalformedInputException e) {
      return Files.lines(file, Charset.forName("GBK"));
    }
  }

  private static List<String> linesAfterMarker(String text, String marker) {
    List<String> result = new ArrayList<>();
    if (text == null || text.isEmpty()) {
      return result;
    }
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    boolean collect = false;
    for (String line : lines) {
      if (collect) {
        result.add(line);
      } else if (marker.equals(line)) {
        collect = true;
      }
    }
    return result;
  }

  private String trimToBudget(String s) {
    int max = props.getLogs().getMaxToolOutputChars();
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max)
        + "\n\n[说明] 工具输出已超过 maxToolOutputChars="
        + max
        + "，已截断；请让模型缩小检索范围或分步查询。\n";
  }
}
