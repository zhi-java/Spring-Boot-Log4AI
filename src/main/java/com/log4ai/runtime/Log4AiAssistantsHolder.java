package com.log4ai.runtime;

import com.log4ai.agent.LogAnalyzerAssistant;
import com.log4ai.agent.LogAnalyzerStreamingAssistant;
import java.util.Objects;

/**
 * 持有当前 LangChain4j 生成的 Agent 实例；更新 LLM 配置后通过替换引用立即作用于后续请求。
 */
public final class Log4AiAssistantsHolder {

  private volatile LogAnalyzerAssistant syncAssistant;
  private volatile LogAnalyzerStreamingAssistant streamingAssistant;

  public LogAnalyzerAssistant requireSync() {
    LogAnalyzerAssistant s = syncAssistant;
    if (s == null) {
      throw new IllegalStateException("LogAnalyzerAssistant 尚未初始化");
    }
    return s;
  }

  public void setSync(LogAnalyzerAssistant assistant) {
    this.syncAssistant = Objects.requireNonNull(assistant, "assistant");
  }

  public LogAnalyzerStreamingAssistant requireStreaming() {
    LogAnalyzerStreamingAssistant s = streamingAssistant;
    if (s == null) {
      throw new IllegalStateException("流式 Agent 未就绪（已关闭 log4ai.streaming 或未装配 LangChain4j Streaming）");
    }
    return s;
  }

  public void setStreaming(LogAnalyzerStreamingAssistant assistant) {
    this.streamingAssistant = Objects.requireNonNull(assistant, "assistant");
  }

  /** 是否已注册流式 Agent（与是否启用 SSE 能力一致）。 */
  public boolean hasStreaming() {
    return streamingAssistant != null;
  }
}
