package com.log4ai.autoconfigure.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * 组合多项 {@link ConditionalOnProperty}（Boot 3 中该注解不可在同一类型上重复声明）。
 */
public final class Log4AiNestedConditions {

  private Log4AiNestedConditions() {}

  /** log4ai.enabled 与 log4ai.web.enabled 均为 true（或缺省）。 */
  public static class WebLayerEnabled extends AllNestedConditions {

    public WebLayerEnabled() {
      super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "log4ai", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class Log4AiOn {}

    @ConditionalOnProperty(prefix = "log4ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class WebOn {}
  }

  /** log4ai.enabled 与 log4ai.streaming.enabled 均为 true（或缺省）。 */
  public static class StreamingLayerEnabled extends AllNestedConditions {

    public StreamingLayerEnabled() {
      super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "log4ai", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class Log4AiOn {}

    @ConditionalOnProperty(
        prefix = "log4ai.streaming",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
    static class StreamingOn {}
  }
}
