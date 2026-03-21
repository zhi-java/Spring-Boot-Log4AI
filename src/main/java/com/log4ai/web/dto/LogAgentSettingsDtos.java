package com.log4ai.web.dto;

import java.util.List;

/** 控制台「日志 / LLM」运行时设置的 API 模型。 */
public final class LogAgentSettingsDtos {

  private LogAgentSettingsDtos() {}

  public record SettingsResponse(LlmSettingsView llm, LogsSettingsView logs) {}

  public record LlmSettingsView(
      boolean apiKeySet,
      String baseUrl,
      String modelName,
      Double temperature,
      int timeoutSeconds) {}

  public record LlmSettingsRequest(
      String apiKey,
      String baseUrl,
      String modelName,
      Double temperature,
      Integer timeoutSeconds) {}

  /**
   * @param systemResolvedLogFile 单实例时自动解析的本进程活动日志文件（只读展示）
   * @param systemResolvedCalendarDir 日历目录根（活动日志所在目录；列举/检索均相对此根含子目录）
   */
  public record LogsSettingsView(
      String systemResolvedLogFile,
      String systemResolvedCalendarDir,
      String defaultService,
      List<LogServiceRow> services,
      String persistencePath,
      boolean registryEnabled,
      boolean uiLogPathEditable) {}

  /** 仅多服务注册与默认 serviceId；单实例路径由服务端自动解析，无需提交。 */
  public record LogsSettingsRequest(String defaultService, List<LogServiceRow> services) {}

  public record LogServiceRow(String id, String displayName, String logPath) {}
}
