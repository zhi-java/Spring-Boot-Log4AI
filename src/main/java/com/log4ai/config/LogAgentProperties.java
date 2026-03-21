package com.log4ai.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * log4ai.* 配置：大模型与日志路径、工具输出上限、脱敏规则。
 */
@ConfigurationProperties(prefix = "log4ai", ignoreUnknownFields = true)
public class LogAgentProperties {

  /** 是否启用本 Starter（关闭时不注册 Agent Bean）。 */
  private boolean enabled = true;

  private final Llm llm = new Llm();
  private final Logs logs = new Logs();
  private final Sanitize sanitize = new Sanitize();
  /** 组件嵌入时可分项关闭：Web 接口、流式 SSE 等。 */
  private final Web web = new Web();
  private final Streaming streaming = new Streaming();
  /** 业务服务向 Log4AI 注册日志路径（类似注册中心），避免在控制台暴露任意服务器路径。 */
  private final Registry registry = new Registry();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Llm getLlm() {
    return llm;
  }

  public Logs getLogs() {
    return logs;
  }

  public Sanitize getSanitize() {
    return sanitize;
  }

  public Web getWeb() {
    return web;
  }

  public Streaming getStreaming() {
    return streaming;
  }

  public Registry getRegistry() {
    return registry;
  }

  /**
   * 日志服务注册：业务应用在启动时携带共享密钥调用 {@code POST /log4ai/registry/register} 上报
   * serviceId 与本机日志路径；可选限制路径前缀并禁止控制台编辑路径。
   */
  public static class Registry {
    /** 为 true 时开放注册/注销 HTTP 接口（仍须配置 {@link #sharedSecret}）。 */
    private boolean enabled = false;
    /**
     * 与业务应用 {@code log4ai.registry.client.token} 一致的共享密钥；建议仅通过环境变量注入。
     */
    private String sharedSecret = "";
    /**
     * 非空时：仅允许注册落在此列表下（规范化后的绝对路径前缀）。为空则不校验前缀（仍须令牌）。
     */
    private List<String> allowedPathPrefixes = new ArrayList<>();
    /**
     * 为 true 时拒绝控制台 {@code PUT /log4ai/settings/logs}，仅允许通过注册接口维护服务列表。
     */
    private boolean disableUiLogPaths = false;
    private final Client client = new Client();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSharedSecret() {
      return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
      this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public List<String> getAllowedPathPrefixes() {
      return allowedPathPrefixes;
    }

    public void setAllowedPathPrefixes(List<String> allowedPathPrefixes) {
      this.allowedPathPrefixes =
          allowedPathPrefixes != null ? new ArrayList<>(allowedPathPrefixes) : new ArrayList<>();
    }

    public boolean isDisableUiLogPaths() {
      return disableUiLogPaths;
    }

    public void setDisableUiLogPaths(boolean disableUiLogPaths) {
      this.disableUiLogPaths = disableUiLogPaths;
    }

    public Client getClient() {
      return client;
    }

    /**
     * 嵌入在其他 Spring Boot 应用中时：启动后向 Log4AI 服务实例注册本机日志路径。
     */
    public static class Client {
      private boolean enabled = false;
      /** Log4AI 根地址，如 https://log4ai.internal:8080（无尾斜杠）。 */
      private String serverBaseUrl = "";
      /** 须与 Log4AI 端 {@code log4ai.registry.shared-secret} 一致。 */
      private String token = "";
      /** 本服务在 Log4AI 中的 serviceId，建议与 spring.application.name 一致。 */
      private String serviceId = "";
      private String displayName = "";
      /**
       * 活动日志文件或目录；留空则按本应用 {@code logging.file.*} 自动解析（与单实例 Log4AI 相同规则）。
       */
      private String logPath = "";

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(boolean enabled) {
        this.enabled = enabled;
      }

      public String getServerBaseUrl() {
        return serverBaseUrl;
      }

      public void setServerBaseUrl(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl == null ? "" : serverBaseUrl.trim();
      }

      public String getToken() {
        return token;
      }

      public void setToken(String token) {
        this.token = token == null ? "" : token;
      }

      public String getServiceId() {
        return serviceId;
      }

      public void setServiceId(String serviceId) {
        this.serviceId = serviceId == null ? "" : serviceId.trim();
      }

      public String getDisplayName() {
        return displayName;
      }

      public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName.trim();
      }

      public String getLogPath() {
        return logPath;
      }

      public void setLogPath(String logPath) {
        this.logPath = logPath == null ? "" : logPath.trim();
      }
    }
  }

  /**
   * Web/Servlet 能力：关闭后不再注册 MVC 控制器（仅保留 Agent、工具等供业务代码注入）。
   */
  public static class Web {
    private boolean enabled = true;
    /** 是否暴露 {@code /log4ai/ui} 跳转；关闭后仍可保留 {@code /log4ai/chat} 等同路径行为取决于 {@link #enabled}。 */
    private boolean consoleEnabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isConsoleEnabled() {
      return consoleEnabled;
    }

    public void setConsoleEnabled(boolean consoleEnabled) {
      this.consoleEnabled = consoleEnabled;
    }
  }

  /** 流式 Agent + SSE；关闭后仅同步轮询 {@code /log4ai/chat}（在 Web 开启时）。 */
  public static class Streaming {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Llm {
    /** 兼容 OpenAI API 的密钥（勿提交到仓库，建议环境变量）。 */
    private String apiKey = "";
    /** Base URL，如 https://api.openai.com/v1 或私有化网关。 */
    private String baseUrl = "https://api.openai.com/v1";
    private String modelName = "gpt-4o-mini";
    private Double temperature = 0.2;
    private Duration timeout = Duration.ofSeconds(120);

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModelName() {
      return modelName;
    }

    public void setModelName(String modelName) {
      this.modelName = modelName;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(Duration timeout) {
      this.timeout = timeout;
    }
  }

  public static class Logs {
    /**
     * 多服务场景下，工具参数 {@code serviceId} 为空时使用该键，须与 {@link #services} 中某项键名一致。
     * 未配置 {@link #services} 时该字段无意义。
     */
    private String defaultService = "default";
    /**
     * 按业务服务维度配置日志：每服务一条日志路径（活动日志文件或目录，见 {@link ServiceLogs#getLogPath()}）。
     * 为空 Map 时，由运行时按 {@code logging.file.*} 与本进程工作目录自动解析单实例日志，无需在 yml 配置全局路径。
     */
    private Map<String, ServiceLogs> services = new LinkedHashMap<>();
    /** 单次工具返回的最大字符数（防爆 Token）。 */
    private int maxToolOutputChars = 32_000;
    /** 搜索命中时最多返回的“匹配段”数量（每段含上下文行）。 */
    private int maxMatchSegments = 30;
    /** 每个匹配段：匹配行前包含的行数。 */
    private int defaultLinesBefore = 5;
    /** 每个匹配段：匹配行后包含的行数。 */
    private int defaultLinesAfter = 15;
    /** tail 工具允许的最大行数上限（调用方传入会被截断到此值）。 */
    private int maxTailLines = 500;
    /** head 工具允许的最大行数上限。 */
    private int maxHeadLines = 500;
    /** 堆栈等过长单行在脱敏后再截断到此长度。 */
    private int maxLineLength = 4_000;
    /**
     * 非空时：除「必须落在日历根下」外，路径还须以其中某一前缀开头（规范化后的绝对路径）。
     * 与 {@link com.log4ai.runtime.Log4AiRegistryPathValidator} 的注册前缀可配合使用，形成双重要求。
     */
    private List<String> allowedReadPathPrefixes = new ArrayList<>();
    /** 正则检索工具中 pattern 字符串最大长度（防爆 ReDoS 与过长模式）。 */
    private int maxRegexPatternLength = 512;
    /** JSON 行扫描工具最多扫描的行数（从文件头顺序读）。 */
    private int maxJsonScanLines = 8_000;

    public String getDefaultService() {
      return defaultService;
    }

    public void setDefaultService(String defaultService) {
      this.defaultService = defaultService == null ? "default" : defaultService;
    }

    public Map<String, ServiceLogs> getServices() {
      return services;
    }

    public void setServices(Map<String, ServiceLogs> services) {
      this.services = services != null ? new LinkedHashMap<>(services) : new LinkedHashMap<>();
    }

    public int getMaxToolOutputChars() {
      return maxToolOutputChars;
    }

    public void setMaxToolOutputChars(int maxToolOutputChars) {
      this.maxToolOutputChars = maxToolOutputChars;
    }

    public int getMaxMatchSegments() {
      return maxMatchSegments;
    }

    public void setMaxMatchSegments(int maxMatchSegments) {
      this.maxMatchSegments = maxMatchSegments;
    }

    public int getDefaultLinesBefore() {
      return defaultLinesBefore;
    }

    public void setDefaultLinesBefore(int defaultLinesBefore) {
      this.defaultLinesBefore = defaultLinesBefore;
    }

    public int getDefaultLinesAfter() {
      return defaultLinesAfter;
    }

    public void setDefaultLinesAfter(int defaultLinesAfter) {
      this.defaultLinesAfter = defaultLinesAfter;
    }

    public int getMaxTailLines() {
      return maxTailLines;
    }

    public void setMaxTailLines(int maxTailLines) {
      this.maxTailLines = maxTailLines;
    }

    public int getMaxHeadLines() {
      return maxHeadLines;
    }

    public void setMaxHeadLines(int maxHeadLines) {
      this.maxHeadLines = maxHeadLines;
    }

    public List<String> getAllowedReadPathPrefixes() {
      return allowedReadPathPrefixes;
    }

    public void setAllowedReadPathPrefixes(List<String> allowedReadPathPrefixes) {
      this.allowedReadPathPrefixes =
          allowedReadPathPrefixes != null ? new ArrayList<>(allowedReadPathPrefixes) : new ArrayList<>();
    }

    public int getMaxRegexPatternLength() {
      return maxRegexPatternLength;
    }

    public void setMaxRegexPatternLength(int maxRegexPatternLength) {
      this.maxRegexPatternLength = maxRegexPatternLength;
    }

    public int getMaxJsonScanLines() {
      return maxJsonScanLines;
    }

    public void setMaxJsonScanLines(int maxJsonScanLines) {
      this.maxJsonScanLines = maxJsonScanLines;
    }

    public int getMaxLineLength() {
      return maxLineLength;
    }

    public void setMaxLineLength(int maxLineLength) {
      this.maxLineLength = maxLineLength;
    }
  }

  /** 单个接入业务的日志路径（宿主机文件路径或目录）。 */
  public static class ServiceLogs {
    /** 人类可读名称（服务名），仅供 listLogServices 展示。 */
    private String displayName = "";
    /**
     * 日志路径（必填）：一般为当前活动日志文件；若为目录，则活动日志默认为该目录下 {@code application.log}，日历目录列举与检索亦以该目录为唯一根（含子目录）。
     */
    private Path logPath;

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName == null ? "" : displayName;
    }

    public Path getLogPath() {
      return logPath;
    }

    public void setLogPath(Path logPath) {
      this.logPath = logPath;
    }
  }

  /**
   * 脱敏：额外正则替换（replacement 恒为 ***），在默认规则之后应用。
   */
  public static class Sanitize {
    private boolean enabled = true;
    private List<PatternReplace> extraPatterns = new ArrayList<>();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<PatternReplace> getExtraPatterns() {
      return extraPatterns;
    }

    public void setExtraPatterns(List<PatternReplace> extraPatterns) {
      this.extraPatterns = extraPatterns;
    }
  }

  public static class PatternReplace {
    /** Java 正则。 */
    private String regex;
    /** 固定替换为 *** 时可忽略；若需要可扩展，当前实现统一掩码。 */

    public String getRegex() {
      return regex;
    }

    public void setRegex(String regex) {
      this.regex = regex;
    }
  }
}
