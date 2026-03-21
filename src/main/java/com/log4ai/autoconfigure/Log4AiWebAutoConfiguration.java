package com.log4ai.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;

import com.log4ai.autoconfigure.condition.Log4AiNestedConditions.WebLayerEnabled;
import com.log4ai.web.Log4AiRegistryController;
import com.log4ai.web.LogAgentChatController;
import com.log4ai.web.LogAgentSettingsController;
import com.log4ai.web.LogAgentStreamController;

/**
 * MVC 控制器扫描；必须晚于 {@link Log4AiStreamingAutoConfiguration}，否则 {@link LogAgentStreamController} 上的条件
 * 可能在流式 Bean 尚未注册时判定失败，导致 /log4ai/chat/stream 映射缺失（客户端 404）。
 */
@AutoConfiguration(
    after = {Log4AiAutoConfiguration.class, Log4AiStreamingAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional(WebLayerEnabled.class)
@ComponentScan(
    basePackageClasses = {
      LogAgentChatController.class,
      LogAgentStreamController.class,
      LogAgentSettingsController.class,
      Log4AiRegistryController.class
    })
public class Log4AiWebAutoConfiguration {}
