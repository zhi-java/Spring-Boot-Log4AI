package com.log4ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 流式日志分析 Agent：返回 {@link TokenStream}，由 LangChain4j 在 Tool Calling 场景下分段产出文本片段。
 *
 * <p>与 {@link LogAnalyzerAssistant} 共用同一 {@code ChatMemoryProvider} 时可保持一致的多轮上下文。
 */
public interface LogAnalyzerStreamingAssistant {

  @SystemMessage(fromResource = "/prompts/log-analyzer-system.txt")
  TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
