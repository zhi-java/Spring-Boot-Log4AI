package com.log4ai.web.dto;

/** 业务服务向 Log4AI 注册日志路径的请求体。 */
public final class LogAgentRegistryDtos {

  private LogAgentRegistryDtos() {}

  public record RegistryRegisterRequest(String serviceId, String displayName, String logPath) {}
}
