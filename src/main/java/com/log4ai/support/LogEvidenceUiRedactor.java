package com.log4ai.support;

import java.util.regex.Pattern;

/**
 * 面向浏览器工具预览：去掉日志路径、目录说明等敏感行；行内路径、IP 等静默删除（不出现占位文案）。
 *
 * <p>保留 {@code serviceId} 相关行与检索正文（脱敏后）。完整工具输出仍进入模型对话。
 */
public final class LogEvidenceUiRedactor {

  private static final Pattern WIN_PATH =
      Pattern.compile(
          "[A-Za-z]:\\\\(?:[^\\s\\n\"'|<>*?]+\\\\)*[^\\s\\n\"'|<>*?]+");
  private static final Pattern UNIX_ABS_PATH =
      Pattern.compile("(?<!:)/(?:[A-Za-z0-9._-]+/)+[A-Za-z0-9._-]+");
  /** 常见相对日志路径，如 logs/spring.log */
  private static final Pattern REL_LOG_PATH =
      Pattern.compile("(?i)\\blogs[/\\\\][A-Za-z0-9._/-]+\\.(?:log|txt|gz)(?:\\.\\d+)?\\b");
  private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
  /** 「错误：文件不存在」等行内路径删去，保留短语义 */
  private static final Pattern PATH_SUFFIX_AFTER_msg =
      Pattern.compile(
          "(错误：)?(文件不存在|找不到日志文件|历史目录不存在|日历目录不存在)\\s*:\\s*[^\\n]+");

  private LogEvidenceUiRedactor() {}

  public static String forSsePreview(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (String line : lines) {
      if (shouldDropLine(line)) {
        continue;
      }
      String cleaned = stripSensitiveFragments(line.strip());
      if (cleaned.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append('\n');
      }
      out.append(cleaned);
    }
    String s = out.toString().replaceAll("\n{3,}", "\n\n").trim();
    return s;
  }

  private static boolean shouldDropLine(String line) {
    String t = line.strip();
    if (t.isEmpty()) {
      return false;
    }
    if (t.startsWith("文件:")) {
      return true;
    }
    if (t.startsWith("历史目录:")) {
      return true;
    }
    if (t.startsWith("日历目录:")) {
      return true;
    }
    if (t.startsWith("日历目录（唯一根目录，含子目录）:")) {
      return true;
    }
    if (t.startsWith("日志路径:")) {
      return true;
    }
    if (t.startsWith("活动日志:")) {
      return true;
    }
    if (t.startsWith("历史/轮转检索目录:")) {
      return true;
    }
    if (t.startsWith("历史/同目录检索:")) {
      return true;
    }
    if (t.startsWith("- 当前活动日志文件:")) {
      return true;
    }
    if (t.startsWith("- 历史/同目录检索:")) {
      return true;
    }
    if (t.startsWith("- 日历目录（唯一根目录，含子目录）:")) {
      return true;
    }
    if (t.matches("- [^\\s]+\\.(?:log|gz|txt)(?:\\.\\d+)?")) {
      return true;
    }
    return false;
  }

  private static String stripSensitiveFragments(String line) {
    if (line.isEmpty()) {
      return "";
    }
    String s =
        PATH_SUFFIX_AFTER_msg
            .matcher(line)
            .replaceAll(
                mr ->
                    (mr.group(1) != null ? "错误：" + mr.group(2) : mr.group(2)));
    s = WIN_PATH.matcher(s).replaceAll("");
    s = UNIX_ABS_PATH.matcher(s).replaceAll("");
    s = REL_LOG_PATH.matcher(s).replaceAll("");
    s = IPV4.matcher(s).replaceAll("");
    s = s.replaceAll("[ \t]{2,}", " ").strip();
    return s;
  }
}
