package com.log4ai.runtime;

import com.log4ai.config.LogAgentProperties;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** 校验注册接口上报的日志路径是否落在允许的前缀下。 */
public final class Log4AiRegistryPathValidator {

  private Log4AiRegistryPathValidator() {}

  public static void validateAllowedPrefix(Path absoluteNormalized, LogAgentProperties props) {
    List<String> prefixes = props.getRegistry().getAllowedPathPrefixes();
    if (prefixes == null || prefixes.isEmpty()) {
      return;
    }
    Path abs = absoluteNormalized.toAbsolutePath().normalize();
    for (String prefix : prefixes) {
      if (prefix == null || prefix.trim().isEmpty()) {
        continue;
      }
      Path p = Paths.get(prefix.trim()).toAbsolutePath().normalize();
      if (abs.startsWith(p)) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "日志路径不在允许的前缀内，当前前缀白名单: " + String.join(", ", prefixes));
  }
}
