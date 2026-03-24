package com.log4ai.web.dto;

/** 业务服务向 Log4AI 注册日志路径的请求体。 */
public final class LogAgentRegistryDtos {

  private LogAgentRegistryDtos() {}

  public static class RegistryRegisterRequest {
    private String serviceId;
    private String displayName;
    private String logPath;

    public RegistryRegisterRequest() {}

    public RegistryRegisterRequest(String serviceId, String displayName, String logPath) {
      this.serviceId = serviceId;
      this.displayName = displayName;
      this.logPath = logPath;
    }

    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
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

    public String serviceId() {
      return serviceId;
    }

    public String displayName() {
      return displayName;
    }

    public String logPath() {
      return logPath;
    }
  }
}
