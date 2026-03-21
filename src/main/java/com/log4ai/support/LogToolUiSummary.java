package com.log4ai.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将工具返回（已脱敏）压缩为浏览器工具条上的一行中文摘要，不展示正文。
 */
public final class LogToolUiSummary {

  private static final Pattern MATCH_SEGMENTS =
      Pattern.compile("返回匹配段数:\\s*(\\d+)");
  private static final Pattern TAIL_LINES = Pattern.compile("共返回末尾\\s*(\\d+)\\s*行");
  private static final Pattern HEAD_LINES = Pattern.compile("共返回开头\\s*(\\d+)\\s*行");
  private static final Pattern LEVEL_SCAN = Pattern.compile("基于尾部窗口扫描行数:\\s*(\\d+)");
  private static final Pattern EXC_BLOCKS = Pattern.compile("启发式提取块数:\\s*(\\d+)");
  private static final Pattern LIST_ALL_FILES = Pattern.compile("列出全部\\s*(\\d+)\\s*个文件");
  private static final Pattern LIST_CUR_FILES = Pattern.compile("当前列举\\s*(\\d+)\\s*个文件");
  private static final Pattern LIST_MATCH_FILES = Pattern.compile("匹配\\s*(\\d+)\\s*个文件");
  private static final Pattern KEYWORD_LINE = Pattern.compile("(?m)^关键字:\\s*(.+)$");

  private LogToolUiSummary() {}

  /**
   * @param toolName 工具名，与 {@link com.log4ai.agent.LogTools} 一致
   * @param redacted {@link LogEvidenceUiRedactor#forSsePreview} 的结果
   */
  public static String line(String toolName, String redacted) {
    String r = redacted == null ? "" : redacted.trim();
    String name = toolName == null ? "" : toolName;
    if (r.isEmpty()) {
      return "已完成";
    }
    if (r.contains("未能执行")) {
      return "未执行成功，请确认 serviceId 或参数";
    }
    if (r.contains("检索失败")
        || r.contains("读取活动日志末尾失败")
        || r.contains("读取活动日志开头失败")
        || r.contains("列出日历目录文件失败")
        || r.contains("列出历史日志文件失败")
        || r.contains("日历目录内文件检索失败")
        || r.contains("历史日志文件检索失败")
        || r.contains("日志级别统计失败")
        || r.contains("读取文件元信息失败")
        || r.contains("JSON 字段检索失败")
        || r.contains("异常片段提取失败")) {
      return "执行失败";
    }
    if (r.contains("未知 serviceId")) {
      return "serviceId 无效，需先确认接入配置";
    }

    return switch (name) {
      case "listLogServices" -> summarizeList(r);
      case "searchCurrentLog",
          "searchCurrentLogWithTimeFilter",
          "searchCurrentLogRegex" -> summarizeSearch(r, "活动日志");
      case "searchLogFile" -> summarizeSearch(r, "日历目录");
      case "tailCurrentLog" -> summarizeTail(r);
      case "headCurrentLog" -> summarizeHead(r);
      case "listHistoricalLogs" -> summarizeListHist(r);
      case "summarizeRecentLogLevels" -> summarizeLevels(r);
      case "getLogFileMeta" -> "已读取文件元信息";
      case "searchJsonFieldInLogFile" -> summarizeJsonSearch(r);
      case "findExceptionBlocksInRecentLog" -> summarizeExceptionBlocks(r);
      default -> fallback(r);
    };
  }

  private static String summarizeList(String r) {
    if (r.contains("单实例")) {
      return "当前为单实例日志接入";
    }
    long n = r.lines().map(String::strip).filter(l -> l.startsWith("- serviceId=")).count();
    if (n > 0) {
      return "已加载 " + n + " 个服务的接入配置";
    }
    return "已加载日志接入配置";
  }

  private static String summarizeSearch(String r, String where) {
    if (r.contains("无匹配")) {
      return where + " · 无匹配";
    }
    Matcher sm = MATCH_SEGMENTS.matcher(r);
    if (!sm.find()) {
      return where + " · 已检索";
    }
    String seg = sm.group(1);
    if ("0".equals(seg)) {
      return where + " · 无匹配";
    }
    Matcher km = KEYWORD_LINE.matcher(r);
    if (km.find()) {
      String kw = km.group(1).strip();
      if (kw.length() > 18) {
        kw = kw.substring(0, 15) + "...";
      }
      return where + " · 命中 " + seg + " 段 · " + kw;
    }
    return where + " · 命中 " + seg + " 段";
  }

  private static String summarizeTail(String r) {
    Matcher m = TAIL_LINES.matcher(r);
    if (m.find()) {
      return "活动日志尾部 · " + m.group(1) + " 行";
    }
    return "已读取活动日志尾部";
  }

  private static String summarizeHead(String r) {
    Matcher m = HEAD_LINES.matcher(r);
    if (m.find()) {
      return "活动日志开头 · " + m.group(1) + " 行";
    }
    return "已读取活动日志开头";
  }

  private static String summarizeLevels(String r) {
    Matcher m = LEVEL_SCAN.matcher(r);
    if (m.find()) {
      return "尾部级别统计 · 采样 " + m.group(1) + " 行";
    }
    return "已完成级别统计";
  }

  private static String summarizeJsonSearch(String r) {
    if (r.contains("无匹配")) {
      return "JSON 字段 · 无匹配";
    }
    if (r.contains("命中段数: 0")) {
      return "JSON 字段 · 无匹配";
    }
    return "JSON 字段 · 已检索";
  }

  private static String summarizeExceptionBlocks(String r) {
    Matcher m = EXC_BLOCKS.matcher(r);
    if (m.find()) {
      return "尾部异常片段 · " + m.group(1) + " 块";
    }
    return "已扫描尾部异常模式";
  }

  private static String summarizeListHist(String r) {
    Matcher m0 = LIST_CUR_FILES.matcher(r);
    if (m0.find()) {
      return "日历目录 · " + m0.group(1) + " 个文件";
    }
    Matcher m1 = LIST_ALL_FILES.matcher(r);
    if (m1.find()) {
      return "日历目录 · " + m1.group(1) + " 个文件";
    }
    Matcher m2 = LIST_MATCH_FILES.matcher(r);
    if (m2.find()) {
      return "日历目录 · 筛选 " + m2.group(1) + " 个文件";
    }
    return "已扫描日历目录";
  }

  private static String fallback(String r) {
    String first =
        r.lines()
            .map(String::strip)
            .filter(l -> !l.isEmpty())
            .findFirst()
            .orElse("");
    if (first.length() > 42) {
      first = first.substring(0, 39) + "...";
    }
    return first.isEmpty() ? "已完成" : first;
  }
}
