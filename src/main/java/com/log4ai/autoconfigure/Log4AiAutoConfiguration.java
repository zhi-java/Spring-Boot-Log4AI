package com.log4ai.autoconfigure;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.log4ai.agent.LogAnalyzerAssistant;
import com.log4ai.agent.LogTools;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.runtime.Log4AiAssistantsHolder;
import com.log4ai.runtime.Log4AiAssistantBuilder;
import com.log4ai.runtime.Log4AiRuntimeService;
import com.log4ai.runtime.Log4AiSettingsPersistence;
import com.log4ai.runtime.Log4AiSettingsStartupListener;
import com.log4ai.support.Log4AiSystemLogPaths;
import com.log4ai.support.LogContentSanitizer;
import com.log4ai.support.LogFileSupport;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * 核心自动配置：日志读写、脱敏、同步 ChatModel、{@link LogTools}、同步 {@link LogAnalyzerAssistant}、
 * 会话 {@link ChatMemoryProvider}（供流式子系统复用）。
 */
@AutoConfiguration
@ConditionalOnClass(AiServices.class)
@EnableConfigurationProperties(LogAgentProperties.class)
@ConditionalOnProperty(prefix = "log4ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
    basePackageClasses = {
      LogTools.class,
      com.log4ai.service.LogAnalyzerAgent.class,
      Log4AiRuntimeService.class,
      Log4AiSettingsPersistence.class,
      Log4AiSettingsStartupListener.class,
    })
public class Log4AiAutoConfiguration {

  private final ConcurrentHashMap<Object, ChatMemory> log4AiChatMemories =
      new ConcurrentHashMap<>();

  @Bean
  @ConditionalOnMissingBean
  public Log4AiAssistantsHolder log4AiAssistantsHolder() {
    return new Log4AiAssistantsHolder();
  }

  /**
   * 统一会话存储：非流式与流式 Agent 均注入此后可共享同一 {@code sessionId} 上下文。
   */
  @Bean("log4aiChatMemoryProvider")
  @ConditionalOnMissingBean(name = "log4aiChatMemoryProvider")
  public ChatMemoryProvider log4aiChatMemoryProvider() {
    return memoryId ->
        log4AiChatMemories.computeIfAbsent(
            memoryId, id -> MessageWindowChatMemory.withMaxMessages(30));
  }

  @Bean
  @ConditionalOnMissingBean
  public LogContentSanitizer logContentSanitizer(LogAgentProperties props) {
    return new LogContentSanitizer(props);
  }

  @Bean
  @ConditionalOnMissingBean
  public Log4AiSystemLogPaths log4AiSystemLogPaths(
      @Value("${logging.file.name:}") String loggingFileName,
      @Value("${logging.file.path:}") String loggingFilePath) {
    return new Log4AiSystemLogPaths(loggingFileName, loggingFilePath);
  }

  @Bean
  @ConditionalOnMissingBean
  public LogFileSupport logFileSupport(
      LogAgentProperties props,
      LogContentSanitizer sanitizer,
      @Value("${user.dir}") String userDir,
      Log4AiSystemLogPaths systemLogPaths) {
    return new LogFileSupport(props, sanitizer, Path.of(userDir), systemLogPaths);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModel log4AiChatModel(LogAgentProperties props) {
    return Log4AiAssistantBuilder.buildChatModel(props);
  }

  @Bean
  @ConditionalOnMissingBean
  public LogAnalyzerAssistant logAnalyzerAssistant(
      Log4AiAssistantsHolder holder,
      OpenAiChatModel model,
      LogTools tools,
      @Qualifier("log4aiChatMemoryProvider") ChatMemoryProvider memoryProvider) {
    LogAnalyzerAssistant impl = Log4AiAssistantBuilder.buildSyncAssistant(model, tools, memoryProvider);
    holder.setSync(impl);
    return (memoryId, userMessage) -> holder.requireSync().chat(memoryId, userMessage);
  }
}
