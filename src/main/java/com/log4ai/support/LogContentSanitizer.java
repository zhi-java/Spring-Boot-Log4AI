package com.log4ai.support;

import com.log4ai.config.LogAgentProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志发给模型前的脱敏；避免令牌、密码、证件号等进入外部 LLM。
 */
public final class LogContentSanitizer {

  private record Rule(Pattern pattern, String replacement) {}

  private static final List<Rule> DEFAULT =
      List.of(
          new Rule(
              Pattern.compile("(?i)authorization\\s*:\\s*bearer\\s+[^\\s]+"),
              "Authorization: Bearer ***"),
          new Rule(
              Pattern.compile(
                  "(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|session[_-]?id)\\s*[:=]\\s*[^\\s&\"',}]+"),
              "$1=***"),
          new Rule(
              Pattern.compile("(?i)(api[_-]?key|apikey|client_secret|secret|password|passwd|pwd)\\s*[:=]\\s*[^\\s&\"',}]+"),
              "$1=***"),
          new Rule(
              Pattern.compile("\\beyJ[a-zA-Z0-9_\\-]+=*\\.[a-zA-Z0-9_=*\\-]+\\.[a-zA-Z0-9_=*\\-]+\\b"),
              "[JWT]***"),
          new Rule(Pattern.compile("(?i)\\bAKIA[0-9A-Z]{16}\\b"), "AKIA***"),
          new Rule(
              Pattern.compile(
                  "(?i)\\b(?:\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])\\b"),
              "[ID]***"));

  private final boolean enabled;
  private final List<Rule> rules;

  public LogContentSanitizer(LogAgentProperties props) {
    this.enabled = props.getSanitize().isEnabled();
    List<Rule> list = new ArrayList<>(DEFAULT);
    for (LogAgentProperties.PatternReplace pr : props.getSanitize().getExtraPatterns()) {
      if (pr.getRegex() != null && !pr.getRegex().isBlank()) {
        list.add(new Rule(Pattern.compile(pr.getRegex(), Pattern.CASE_INSENSITIVE), "***"));
      }
    }
    this.rules = List.copyOf(list);
  }

  public String line(String raw, int maxLineLength) {
    if (raw == null) {
      return "";
    }
    String s = raw;
    if (enabled) {
      for (Rule r : rules) {
        s = r.pattern().matcher(s).replaceAll(r.replacement());
      }
    }
    if (s.length() > maxLineLength) {
      return s.substring(0, maxLineLength) + " ... [行已截断]";
    }
    return s;
  }
}
