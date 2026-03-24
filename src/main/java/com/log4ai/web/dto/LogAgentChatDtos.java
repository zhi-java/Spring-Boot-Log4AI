package com.log4ai.web.dto;

/**
 * REST 层共享 DTO（供同步 / 流式接口复用）。
 */
public final class LogAgentChatDtos {

  private LogAgentChatDtos() {}

  public static class ChatRequest {
    private String message;
    private String sessionId;

    public ChatRequest() {}

    public ChatRequest(String message, String sessionId) {
      this.message = message;
      this.sessionId = sessionId;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getSessionId() {
      return sessionId;
    }

    public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
    }

    public String message() {
      return message;
    }

    public String sessionId() {
      return sessionId;
    }
  }

  public static class ChatResponse {
    private String reply;

    public ChatResponse() {}

    public ChatResponse(String reply) {
      this.reply = reply;
    }

    public String getReply() {
      return reply;
    }

    public void setReply(String reply) {
      this.reply = reply;
    }

    public String reply() {
      return reply;
    }
  }
}
