package com.log4ai.runtime;

import com.log4ai.agent.LogAnalyzerAssistant;
import com.log4ai.agent.LogAnalyzerStreamingAssistant;
import com.log4ai.agent.LogTools;
import com.log4ai.config.LogAgentProperties;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;

/** 根据当前 {@link LogAgentProperties} 构建 ChatModel 与 AiServices Agent。 */
public final class Log4AiAssistantBuilder {

  private Log4AiAssistantBuilder() {}

  public static OpenAiChatModel buildChatModel(LogAgentProperties props) {
    var llm = props.getLlm();
    var b =
        OpenAiChatModel.builder()
            .apiKey(llm.getApiKey())
            .baseUrl(llm.getBaseUrl())
            .modelName(llm.getModelName())
            .timeout(llm.getTimeout());
    if (llm.getTemperature() != null) {
      b.temperature(llm.getTemperature());
    }
    return b.build();
  }

  public static OpenAiStreamingChatModel buildStreamingModel(LogAgentProperties props) {
    var llm = props.getLlm();
    var b =
        OpenAiStreamingChatModel.builder()
            .apiKey(llm.getApiKey())
            .baseUrl(llm.getBaseUrl())
            .modelName(llm.getModelName())
            .timeout(llm.getTimeout());
    if (llm.getTemperature() != null) {
      b.temperature(llm.getTemperature());
    }
    return b.build();
  }

  public static LogAnalyzerAssistant buildSyncAssistant(
      OpenAiChatModel model, LogTools tools, ChatMemoryProvider memoryProvider) {
    return AiServices.builder(LogAnalyzerAssistant.class)
        .chatLanguageModel(model)
        .chatMemoryProvider(memoryProvider)
        .tools(tools)
        .build();
  }

  public static LogAnalyzerStreamingAssistant buildStreamingAssistant(
      OpenAiStreamingChatModel model, LogTools tools, ChatMemoryProvider memoryProvider) {
    return AiServices.builder(LogAnalyzerStreamingAssistant.class)
        .streamingChatLanguageModel(model)
        .chatMemoryProvider(memoryProvider)
        .tools(tools)
        .build();
  }
}
