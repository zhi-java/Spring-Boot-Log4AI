package com.log4ai.runtime;

import com.log4ai.agent.LogTools;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.support.Log4AiSystemLogPaths;
import com.log4ai.support.LogFileSupport;
import com.log4ai.web.dto.LogAgentSettingsDtos.LogServiceRow;
import com.log4ai.web.dto.LogAgentSettingsDtos.LogsSettingsRequest;
import com.log4ai.web.dto.LogAgentSettingsDtos.LogsSettingsView;
import com.log4ai.web.dto.LogAgentSettingsDtos.LlmSettingsRequest;
import com.log4ai.web.dto.LogAgentSettingsDtos.LlmSettingsView;
import com.log4ai.web.dto.LogAgentSettingsDtos.SettingsResponse;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 应用内运行时配置：变更 {@link LogAgentProperties} 并按需重建 LLM Agent。 */
@Service
public class Log4AiRuntimeService {

  private static final Logger log = LoggerFactory.getLogger(Log4AiRuntimeService.class);

  private final LogAgentProperties props;
  private final LogTools tools;
  private final ChatMemoryProvider memoryProvider;
  private final Log4AiAssistantsHolder holder;
  private final Log4AiSettingsPersistence persistence;
  private final Log4AiSystemLogPaths systemLogPaths;
  private final Path workspace;
  private final boolean streamingStackPresent;

  private final Object rebuildLock = new Object();

  public Log4AiRuntimeService(
      LogAgentProperties props,
      LogTools tools,
      @Qualifier("log4aiChatMemoryProvider") ChatMemoryProvider memoryProvider,
      Log4AiAssistantsHolder holder,
      Log4AiSettingsPersistence persistence,
      Log4AiSystemLogPaths systemLogPaths,
      @Value("${user.dir}") String userDir) {
    this.props = props;
    this.tools = tools;
    this.memoryProvider = memoryProvider;
    this.holder = holder;
    this.persistence = persistence;
    this.systemLogPaths = systemLogPaths;
    this.workspace = Paths.get(userDir);
    this.streamingStackPresent = streamingAssistantOnClasspath();
  }

  private static boolean streamingAssistantOnClasspath() {
    try {
      Class.forName("dev.langchain4j.model.openai.OpenAiStreamingChatModel");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** 启动或保存后：按当前 props 重建同步 / 流式 Agent（流式依赖配置与类路径）。 */
  public void rebuildAssistants() {
    synchronized (rebuildLock) {
      dev.langchain4j.model.openai.OpenAiChatModel syncModel =
          Log4AiAssistantBuilder.buildChatModel(props);
      holder.setSync(Log4AiAssistantBuilder.buildSyncAssistant(syncModel, tools, memoryProvider));
      if (streamingStackPresent && props.getStreaming().isEnabled()) {
        dev.langchain4j.model.openai.OpenAiStreamingChatModel streamModel =
            Log4AiAssistantBuilder.buildStreamingModel(props);
        holder.setStreaming(
            Log4AiAssistantBuilder.buildStreamingAssistant(streamModel, tools, memoryProvider));
      }
    }
  }

  public SettingsResponse snapshot() {
    LogAgentProperties.Llm llm = props.getLlm();
    boolean keySet = llm.getApiKey() != null && !llm.getApiKey().trim().isEmpty();
    LlmSettingsView lv =
        new LlmSettingsView(
            keySet,
            llm.getBaseUrl(),
            llm.getModelName(),
            llm.getTemperature(),
            timeoutSecondsForView(llm));
    LogFileSupport.ResolvedPaths sys = systemLogPaths.resolve(workspace);
    LogAgentProperties.Registry reg = props.getRegistry();
    List<LogServiceRow> serviceRows = new ArrayList<>();
    for (Map.Entry<String, LogAgentProperties.ServiceLogs> e : props.getLogs().getServices().entrySet()) {
      LogAgentProperties.ServiceLogs sl = e.getValue();
      serviceRows.add(
          new LogServiceRow(
              e.getKey(),
              sl.getDisplayName(),
              sl.getLogPath() != null ? sl.getLogPath().toString() : ""));
    }
    LogsSettingsView gv =
        new LogsSettingsView(
            sys.currentLogFile().toString(),
            sys.calendarRoot().toString(),
            props.getLogs().getDefaultService(),
            serviceRows,
            persistence.filePath().toString(),
            reg.isEnabled(),
            !reg.isDisableUiLogPaths());
    return new SettingsResponse(lv, gv);
  }

  /** 与 {@link Log4AiSettingsPersistence#fromProps} 一致，避免配置/持久化后出现 null 导致 NPE。 */
  private static int timeoutSecondsForView(LogAgentProperties.Llm llm) {
    Duration t = llm.getTimeout();
    if (t == null) {
      return 120;
    }
    long sec = t.getSeconds();
    return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, sec));
  }

  public void applyLlm(LlmSettingsRequest req) throws IOException {
    synchronized (rebuildLock) {
      if (req.apiKey() != null) {
        props.getLlm().setApiKey(req.apiKey());
      }
      if (req.baseUrl() != null && !req.baseUrl().trim().isEmpty()) {
        props.getLlm().setBaseUrl(req.baseUrl().trim());
      }
      if (req.modelName() != null && !req.modelName().trim().isEmpty()) {
        props.getLlm().setModelName(req.modelName().trim());
      }
      if (req.temperature() != null) {
        props.getLlm().setTemperature(req.temperature());
      }
      if (req.timeoutSeconds() != null && req.timeoutSeconds() > 0) {
        props.getLlm().setTimeout(Duration.ofSeconds(req.timeoutSeconds().longValue()));
      }
      rebuildAssistants();
      persistence.save(props);
    }
  }

  public void applyLogs(LogsSettingsRequest req) throws IOException {
    synchronized (rebuildLock) {
      if (props.getRegistry().isDisableUiLogPaths()) {
        throw new IllegalArgumentException(
            "已在服务端开启 log4ai.registry.disable-ui-log-paths，禁止通过控制台修改日志服务列表；"
                + "请由各业务应用使用注册接口上报路径，或临时关闭该选项。");
      }
      String def =
          req.defaultService() == null || req.defaultService().trim().isEmpty()
              ? "default"
              : req.defaultService().trim();
      props.getLogs().setDefaultService(def);
      Map<String, LogAgentProperties.ServiceLogs> map = new LinkedHashMap<>();
      if (req.services() != null) {
        for (LogServiceRow row : req.services()) {
          if (row.id() == null || row.id().trim().isEmpty()) {
            continue;
          }
          String id = row.id().trim();
          if (row.logPath() == null || row.logPath().trim().isEmpty()) {
            throw new IllegalArgumentException("服务 \"" + id + "\" 须填写日志路径（文件或目录）");
          }
          LogAgentProperties.ServiceLogs sl = new LogAgentProperties.ServiceLogs();
          if (row.displayName() != null) {
            sl.setDisplayName(row.displayName());
          }
          sl.setLogPath(Paths.get(row.logPath().trim()));
          map.put(id, sl);
        }
      }
      props.getLogs().setServices(map);
      if (!map.isEmpty() && !map.containsKey(def)) {
        String fallback = map.keySet().iterator().next();
        log.warn(
            "defaultService=\"{}\" 不在已注册服务 id 列表中，已自动改为第一个已注册服务 \"{}\"",
            def,
            fallback);
        def = fallback;
        props.getLogs().setDefaultService(def);
      }
      persistence.save(props);
    }
  }

  /**
   * 业务应用通过 {@code POST /log4ai/registry/register} 上报单条服务；与控制台全量保存不同，为按 id 合并。
   */
  public void registerOrUpdateService(String serviceId, String displayName, String logPathStr)
      throws IOException {
    if (serviceId == null || serviceId.trim().isEmpty()) {
      throw new IllegalArgumentException("serviceId 不能为空");
    }
    if (logPathStr == null || logPathStr.trim().isEmpty()) {
      throw new IllegalArgumentException("logPath 不能为空");
    }
    Path raw = Paths.get(logPathStr.trim());
    Path normalized = raw.isAbsolute() ? raw.normalize() : workspace.resolve(raw).normalize();
    Log4AiRegistryPathValidator.validateAllowedPrefix(normalized, props);
    synchronized (rebuildLock) {
      Map<String, LogAgentProperties.ServiceLogs> map =
          new LinkedHashMap<>(props.getLogs().getServices());
      LogAgentProperties.ServiceLogs sl = new LogAgentProperties.ServiceLogs();
      sl.setDisplayName(displayName != null ? displayName.trim() : "");
      sl.setLogPath(normalized);
      map.put(serviceId.trim(), sl);
      props.getLogs().setServices(map);
      String def = props.getLogs().getDefaultService();
      if (!map.isEmpty() && (def == null || def.trim().isEmpty() || !map.containsKey(def))) {
        String fallback = map.keySet().iterator().next();
        log.warn(
            "defaultService=\"{}\" 不在已注册服务 id 列表中，已自动改为 \"{}\"",
            def,
            fallback);
        props.getLogs().setDefaultService(fallback);
      }
      persistence.save(props);
    }
  }

  /** 注销某 serviceId（注册接口调用）。 */
  public void removeRegisteredService(String serviceId) throws IOException {
    if (serviceId == null || serviceId.trim().isEmpty()) {
      throw new IllegalArgumentException("serviceId 不能为空");
    }
    synchronized (rebuildLock) {
      Map<String, LogAgentProperties.ServiceLogs> map =
          new LinkedHashMap<>(props.getLogs().getServices());
      if (map.remove(serviceId.trim()) == null) {
        throw new IllegalArgumentException("未找到服务: " + serviceId);
      }
      props.getLogs().setServices(map);
      if (!map.isEmpty()) {
        String def = props.getLogs().getDefaultService();
        if (def != null && !def.trim().isEmpty() && !map.containsKey(def)) {
          props.getLogs().setDefaultService(map.keySet().iterator().next());
        }
      }
      persistence.save(props);
    }
  }
}
