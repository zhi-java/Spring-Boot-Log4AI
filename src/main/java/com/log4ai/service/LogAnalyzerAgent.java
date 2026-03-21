package com.log4ai.service;

import com.log4ai.agent.LogAnalyzerAssistant;
import org.springframework.stereotype.Service;

/**
 * 应用层门面：将 HTTP/业务侧会话 id 映射为 LangChain4j {@link com.log4ai.agent.LogAnalyzerAssistant} 的 {@link
 * dev.langchain4j.service.MemoryId}，使同一会话内保留多轮 ReAct 轨迹（含历史工具输出）。
 */
@Service
public class LogAnalyzerAgent {

  private final LogAnalyzerAssistant assistant;

  public LogAnalyzerAgent(LogAnalyzerAssistant assistant) {
    this.assistant = assistant;
  }

  /**
   * @param sessionId 会话标识，相同 id 共享多轮上下文；可传 null 使用 default
   * @param userMessage 用户自然语言问题
   */
  public String ask(String sessionId, String userMessage) {
    String sid =
        (sessionId == null || sessionId.isBlank()) ? "default" : sessionId.trim();
    return assistant.chat(sid, userMessage);
  }
}
