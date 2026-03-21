package com.log4ai.autoconfigure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import com.log4ai.autoconfigure.condition.Log4AiNestedConditions.StreamingLayerEnabled;
import com.log4ai.agent.LogAnalyzerStreamingAssistant;
import com.log4ai.agent.LogTools;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.runtime.Log4AiAssistantsHolder;
import com.log4ai.runtime.Log4AiAssistantBuilder;
import com.log4ai.service.LogAnalyzerStreamingService;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * 流式 LLM + {@link LogAnalyzerStreamingService}；可由 {@code log4ai.streaming.enabled=false} 关闭。
 */
@AutoConfiguration(after = Log4AiAutoConfiguration.class)
@ConditionalOnClass(OpenAiStreamingChatModel.class)
@Conditional(StreamingLayerEnabled.class)
public class Log4AiStreamingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OpenAiStreamingChatModel log4AiStreamingChatModel(LogAgentProperties props) {
    return Log4AiAssistantBuilder.buildStreamingModel(props);
  }

  @Bean
  @ConditionalOnMissingBean
  public LogAnalyzerStreamingAssistant logAnalyzerStreamingAssistant(
      Log4AiAssistantsHolder holder,
      OpenAiStreamingChatModel streamingModel,
      LogTools tools,
      @Qualifier("log4aiChatMemoryProvider") ChatMemoryProvider memoryProvider) {
    LogAnalyzerStreamingAssistant impl =
        Log4AiAssistantBuilder.buildStreamingAssistant(streamingModel, tools, memoryProvider);
    holder.setStreaming(impl);
    return (memoryId, userMessage) -> holder.requireStreaming().chat(memoryId, userMessage);
  }

  @Bean
  @ConditionalOnMissingBean
  public LogAnalyzerStreamingService logAnalyzerStreamingService(Log4AiAssistantsHolder holder) {
    return new LogAnalyzerStreamingService(holder);
  }
}
