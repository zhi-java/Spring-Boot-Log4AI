package com.log4ai.web.dto;

/**
 * REST 层共享 DTO（供同步 / 流式接口复用）。
 */
public final class LogAgentChatDtos {

  private LogAgentChatDtos() {}

  public record ChatRequest(String message, String sessionId) {}

  public record ChatResponse(String reply) {}
}
