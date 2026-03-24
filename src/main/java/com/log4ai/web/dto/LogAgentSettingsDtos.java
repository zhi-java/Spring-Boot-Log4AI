package com.log4ai.web.dto;

import java.util.List;

/** 控制台「日志 / LLM」运行时设置的 API 模型。 */
public final class LogAgentSettingsDtos {

  private LogAgentSettingsDtos() {}

  public static class SettingsResponse {
    private LlmSettingsView llm;
    private LogsSettingsView logs;

    public SettingsResponse() {}

    public SettingsResponse(LlmSettingsView llm, LogsSettingsView logs) {
      this.llm = llm;
      this.logs = logs;
    }

    public LlmSettingsView getLlm() {
      return llm;
    }

    public void setLlm(LlmSettingsView llm) {
      this.llm = llm;
    }

    public LogsSettingsView getLogs() {
      return logs;
    }

    public void setLogs(LogsSettingsView logs) {
      this.logs = logs;
    }

    public LlmSettingsView llm() {
      return llm;
    }

    public LogsSettingsView logs() {
      return logs;
    }
  }

  public static class LlmSettingsView {
    private boolean apiKeySet;
    private String baseUrl;
    private String modelName;
    private Double temperature;
    private int timeoutSeconds;

    public LlmSettingsView() {}

    public LlmSettingsView(
        boolean apiKeySet,
        String baseUrl,
        String modelName,
        Double temperature,
        int timeoutSeconds) {
      this.apiKeySet = apiKeySet;
      this.baseUrl = baseUrl;
      this.modelName = modelName;
      this.temperature = temperature;
      this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isApiKeySet() {
      return apiKeySet;
    }

    public void setApiKeySet(boolean apiKeySet) {
      this.apiKeySet = apiKeySet;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModelName() {
      return modelName;
    }

    public void setModelName(String modelName) {
      this.modelName = modelName;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public boolean apiKeySet() {
      return apiKeySet;
    }

    public String baseUrl() {
      return baseUrl;
    }

    public String modelName() {
      return modelName;
    }

    public Double temperature() {
      return temperature;
    }

    public int timeoutSeconds() {
      return timeoutSeconds;
    }
  }

  public static class LlmSettingsRequest {
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private Double temperature;
    private Integer timeoutSeconds;

    public LlmSettingsRequest() {}

    public LlmSettingsRequest(
        String apiKey,
        String baseUrl,
        String modelName,
        Double temperature,
        Integer timeoutSeconds) {
      this.apiKey = apiKey;
      this.baseUrl = baseUrl;
      this.modelName = modelName;
      this.temperature = temperature;
      this.timeoutSeconds = timeoutSeconds;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModelName() {
      return modelName;
    }

    public void setModelName(String modelName) {
      this.modelName = modelName;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public Integer getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public String apiKey() {
      return apiKey;
    }

    public String baseUrl() {
      return baseUrl;
    }

    public String modelName() {
      return modelName;
    }

    public Double temperature() {
      return temperature;
    }

    public Integer timeoutSeconds() {
      return timeoutSeconds;
    }
  }

  /**
   * @param systemResolvedLogFile 单实例时自动解析的本进程活动日志文件（只读展示）
   * @param systemResolvedCalendarDir 日历目录根（活动日志所在目录；列举/检索均相对此根含子目录）
   */
  public static class LogsSettingsView {
    private String systemResolvedLogFile;
    private String systemResolvedCalendarDir;
    private String defaultService;
    private List<LogServiceRow> services;
    private String persistencePath;
    private boolean registryEnabled;
    private boolean uiLogPathEditable;

    public LogsSettingsView() {}

    public LogsSettingsView(
        String systemResolvedLogFile,
        String systemResolvedCalendarDir,
        String defaultService,
        List<LogServiceRow> services,
        String persistencePath,
        boolean registryEnabled,
        boolean uiLogPathEditable) {
      this.systemResolvedLogFile = systemResolvedLogFile;
      this.systemResolvedCalendarDir = systemResolvedCalendarDir;
      this.defaultService = defaultService;
      this.services = services;
      this.persistencePath = persistencePath;
      this.registryEnabled = registryEnabled;
      this.uiLogPathEditable = uiLogPathEditable;
    }

    public String getSystemResolvedLogFile() {
      return systemResolvedLogFile;
    }

    public void setSystemResolvedLogFile(String systemResolvedLogFile) {
      this.systemResolvedLogFile = systemResolvedLogFile;
    }

    public String getSystemResolvedCalendarDir() {
      return systemResolvedCalendarDir;
    }

    public void setSystemResolvedCalendarDir(String systemResolvedCalendarDir) {
      this.systemResolvedCalendarDir = systemResolvedCalendarDir;
    }

    public String getDefaultService() {
      return defaultService;
    }

    public void setDefaultService(String defaultService) {
      this.defaultService = defaultService;
    }

    public List<LogServiceRow> getServices() {
      return services;
    }

    public void setServices(List<LogServiceRow> services) {
      this.services = services;
    }

    public String getPersistencePath() {
      return persistencePath;
    }

    public void setPersistencePath(String persistencePath) {
      this.persistencePath = persistencePath;
    }

    public boolean isRegistryEnabled() {
      return registryEnabled;
    }

    public void setRegistryEnabled(boolean registryEnabled) {
      this.registryEnabled = registryEnabled;
    }

    public boolean isUiLogPathEditable() {
      return uiLogPathEditable;
    }

    public void setUiLogPathEditable(boolean uiLogPathEditable) {
      this.uiLogPathEditable = uiLogPathEditable;
    }

    public String systemResolvedLogFile() {
      return systemResolvedLogFile;
    }

    public String systemResolvedCalendarDir() {
      return systemResolvedCalendarDir;
    }

    public String defaultService() {
      return defaultService;
    }

    public List<LogServiceRow> services() {
      return services;
    }

    public String persistencePath() {
      return persistencePath;
    }

    public boolean registryEnabled() {
      return registryEnabled;
    }

    public boolean uiLogPathEditable() {
      return uiLogPathEditable;
    }
  }

  /** 仅多服务注册与默认 serviceId；单实例路径由服务端自动解析，无需提交。 */
  public static class LogsSettingsRequest {
    private String defaultService;
    private List<LogServiceRow> services;

    public LogsSettingsRequest() {}

    public LogsSettingsRequest(String defaultService, List<LogServiceRow> services) {
      this.defaultService = defaultService;
      this.services = services;
    }

    public String getDefaultService() {
      return defaultService;
    }

    public void setDefaultService(String defaultService) {
      this.defaultService = defaultService;
    }

    public List<LogServiceRow> getServices() {
      return services;
    }

    public void setServices(List<LogServiceRow> services) {
      this.services = services;
    }

    public String defaultService() {
      return defaultService;
    }

    public List<LogServiceRow> services() {
      return services;
    }
  }

  public static class LogServiceRow {
    private String id;
    private String displayName;
    private String logPath;

    public LogServiceRow() {}

    public LogServiceRow(String id, String displayName, String logPath) {
      this.id = id;
      this.displayName = displayName;
      this.logPath = logPath;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getLogPath() {
      return logPath;
    }

    public void setLogPath(String logPath) {
      this.logPath = logPath;
    }

    public String id() {
      return id;
    }

    public String displayName() {
      return displayName;
    }

    public String logPath() {
      return logPath;
    }
  }
}
