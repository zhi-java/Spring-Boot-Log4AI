package com.log4ai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.registry.client.Log4AiRegistryClientBootstrap;
import com.log4ai.support.Log4AiSystemLogPaths;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 业务应用侧：启动后向 Log4AI 注册本机日志路径（可选）。
 *
 * @see com.log4ai.config.LogAgentProperties.Registry.Client
 */
@AutoConfiguration(after = Log4AiAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnBean(ObjectMapper.class)
@ConditionalOnProperty(prefix = "log4ai.registry.client", name = "enabled", havingValue = "true")
public class Log4AiRegistryClientAutoConfiguration {

  @Bean
  public Log4AiRegistryClientBootstrap log4AiRegistryClientBootstrap(
      LogAgentProperties props,
      Log4AiSystemLogPaths systemLogPaths,
      @Value("${user.dir}") String userDir,
      ObjectMapper objectMapper) {
    return new Log4AiRegistryClientBootstrap(props, systemLogPaths, Path.of(userDir), objectMapper);
  }
}
