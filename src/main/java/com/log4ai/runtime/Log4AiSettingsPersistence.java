package com.log4ai.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.log4ai.config.LogAgentProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将控制台中的日志 / LLM 配置持久化到 {@code ${user.dir}/.log4ai/settings.json}，启动时覆盖 yml（若文件存在）。
 */
@Component
public class Log4AiSettingsPersistence {

  private static final Logger log = LoggerFactory.getLogger(Log4AiSettingsPersistence.class);

  private final Path file;
  private final ObjectMapper mapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  public Log4AiSettingsPersistence(@Value("${user.dir}") String userDir) {
    this.file = Path.of(userDir).resolve(".log4ai").resolve("settings.json").normalize();
  }

  public Path filePath() {
    return file;
  }

  /** @return 是否成功从磁盘加载并写入了 {@link LogAgentProperties} */
  public boolean loadIfPresent(LogAgentProperties props) {
    if (!Files.isRegularFile(file)) {
      return false;
    }
    try {
      byte[] raw = Files.readAllBytes(file);
      Stored root = mapper.readValue(raw, Stored.class);
      applyToProps(root, props);
      log.info("Log4AI 已从 {} 加载运行时配置", file);
      return true;
    } catch (IOException e) {
      log.warn("读取 {} 失败，跳过启动覆盖: {}", file, e.getMessage());
      return false;
    }
  }

  public void save(LogAgentProperties props) throws IOException {
    Files.createDirectories(file.getParent());
    Stored s = fromProps(props);
    mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), s);
    log.info("Log4AI 运行时配置已写入 {}", file);
  }

  private static void applyToProps(Stored root, LogAgentProperties props) {
    if (root == null) {
      return;
    }
    if (root.llm != null) {
      var L = root.llm;
      if (L.apiKey != null) {
        props.getLlm().setApiKey(L.apiKey);
      }
      if (L.baseUrl != null && !L.baseUrl.isBlank()) {
        props.getLlm().setBaseUrl(L.baseUrl.trim());
      }
      if (L.modelName != null && !L.modelName.isBlank()) {
        props.getLlm().setModelName(L.modelName.trim());
      }
      if (L.temperature != null) {
        props.getLlm().setTemperature(L.temperature);
      }
      if (L.timeoutSeconds != null && L.timeoutSeconds > 0) {
        props.getLlm().setTimeout(Duration.ofSeconds(L.timeoutSeconds));
      }
    }
    if (root.logs != null) {
      var G = root.logs;
      if (G.defaultService != null && !G.defaultService.isBlank()) {
        props.getLogs().setDefaultService(G.defaultService.trim());
      }
      if (G.services != null) {
        if (G.services.isEmpty()) {
          props.getLogs().setServices(new LinkedHashMap<>());
        } else {
          Map<String, LogAgentProperties.ServiceLogs> map = new LinkedHashMap<>();
          for (StoredServiceRow r : G.services) {
            if (r.id == null || r.id.isBlank()) {
              continue;
            }
            String id = r.id.trim();
            LogAgentProperties.ServiceLogs sl = new LogAgentProperties.ServiceLogs();
            if (r.displayName != null) {
              sl.setDisplayName(r.displayName);
            }
            String lp = effectiveLogPath(r);
            if (lp != null && !lp.isBlank()) {
              sl.setLogPath(Path.of(lp.trim()));
            }
            map.put(id, sl);
          }
          props.getLogs().setServices(map);
        }
      }
    }
  }

  /** 兼容旧版 settings.json：仅有 currentLogPath / historyDir 时取活动日志路径。 */
  private static String effectiveLogPath(StoredServiceRow r) {
    if (r.logPath != null && !r.logPath.isBlank()) {
      return r.logPath.trim();
    }
    if (r.currentLogPath != null && !r.currentLogPath.isBlank()) {
      return r.currentLogPath.trim();
    }
    return null;
  }

  private static Stored fromProps(LogAgentProperties props) {
    Stored s = new Stored();
    s.version = 1;
    s.llm = new StoredLlm();
    s.llm.apiKey = props.getLlm().getApiKey();
    s.llm.baseUrl = props.getLlm().getBaseUrl();
    s.llm.modelName = props.getLlm().getModelName();
    s.llm.temperature = props.getLlm().getTemperature();
    long sec = props.getLlm().getTimeout() == null ? 120 : props.getLlm().getTimeout().toSeconds();
    s.llm.timeoutSeconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, sec));

    s.logs = new StoredLogs();
    s.logs.defaultService = props.getLogs().getDefaultService();
    s.logs.services = new ArrayList<>();
    for (var e : props.getLogs().getServices().entrySet()) {
      StoredServiceRow row = new StoredServiceRow();
      row.id = e.getKey();
      row.displayName = e.getValue().getDisplayName();
      if (e.getValue().getLogPath() != null) {
        row.logPath = e.getValue().getLogPath().toString();
      }
      s.logs.services.add(row);
    }
    return s;
  }

  // ---- Jackson DTO（仅持久化层使用）----

  public static final class Stored {
    public int version;
    public StoredLlm llm;
    public StoredLogs logs;
  }

  public static final class StoredLlm {
    public String apiKey;
    public String baseUrl;
    public String modelName;
    public Double temperature;
    public Integer timeoutSeconds;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class StoredLogs {
    public String defaultService;
    public List<StoredServiceRow> services = new ArrayList<>();
  }

  public static final class StoredServiceRow {
    public String id;
    public String displayName;
    public String logPath;
    /** 旧版字段，读取时由 {@link #effectiveLogPath} 兜底 */
    @SuppressWarnings("unused")
    public String currentLogPath;

    @SuppressWarnings("unused")
    public String historyDir;
  }
}
