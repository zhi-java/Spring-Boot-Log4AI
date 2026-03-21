package com.log4ai.runtime;

import com.log4ai.config.LogAgentProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 根容器就绪后加载 {@code .log4ai/settings.json}（若存在）并重建 Agent，使持久化配置优先生效。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class Log4AiSettingsStartupListener implements ApplicationListener<ContextRefreshedEvent> {

  private final AtomicBoolean executed = new AtomicBoolean();
  private final LogAgentProperties props;
  private final Log4AiSettingsPersistence persistence;
  private final Log4AiRuntimeService runtime;

  public Log4AiSettingsStartupListener(
      LogAgentProperties props,
      Log4AiSettingsPersistence persistence,
      Log4AiRuntimeService runtime) {
    this.props = props;
    this.persistence = persistence;
    this.runtime = runtime;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (event.getApplicationContext().getParent() != null) {
      return;
    }
    if (!executed.compareAndSet(false, true)) {
      return;
    }
    if (persistence.loadIfPresent(props)) {
      runtime.rebuildAssistants();
    }
  }
}
