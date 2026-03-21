package com.log4ai.registry.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.support.Log4AiSystemLogPaths;
import com.log4ai.web.dto.LogAgentRegistryDtos.RegistryRegisterRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 业务应用启动完成后向 Log4AI 服务实例注册本机日志路径（须配置 {@code log4ai.registry.client.*}）。
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class Log4AiRegistryClientBootstrap implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(Log4AiRegistryClientBootstrap.class);

  private final LogAgentProperties props;
  private final Log4AiSystemLogPaths systemLogPaths;
  private final Path workspace;
  private final ObjectMapper objectMapper;

  public Log4AiRegistryClientBootstrap(
      LogAgentProperties props,
      Log4AiSystemLogPaths systemLogPaths,
      Path workspace,
      ObjectMapper objectMapper) {
    this.props = props;
    this.systemLogPaths = systemLogPaths;
    this.workspace = workspace;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    var c = props.getRegistry().getClient();
    if (!c.isEnabled()) {
      return;
    }
    String base = c.getServerBaseUrl();
    if (base == null || base.isBlank()) {
      log.warn("log4ai.registry.client.server-base-url 未配置，跳过向 Log4AI 注册");
      return;
    }
    String token = c.getToken();
    if (token == null || token.isBlank()) {
      log.warn("log4ai.registry.client.token 未配置，跳过向 Log4AI 注册");
      return;
    }
    String serviceId = c.getServiceId();
    if (serviceId == null || serviceId.isBlank()) {
      log.warn("log4ai.registry.client.service-id 未配置，跳过向 Log4AI 注册");
      return;
    }
    String logPath = c.getLogPath();
    if (logPath == null || logPath.isBlank()) {
      logPath = systemLogPaths.resolve(workspace).currentLogFile().toString();
    }
    String display = c.getDisplayName();
    if (display == null || display.isBlank()) {
      display = serviceId;
    }
    String url = base.replaceAll("/+$", "") + "/log4ai/registry/register";
    try {
      RegistryRegisterRequest body =
          new RegistryRegisterRequest(serviceId, display, logPath);
      String json = objectMapper.writeValueAsString(body);
      HttpClient http =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json; charset=UTF-8")
              .header("Authorization", "Bearer " + token)
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        log.info("已向 Log4AI 注册日志服务: serviceId={}, path={}", serviceId, logPath);
      } else {
        log.error(
            "向 Log4AI 注册失败: HTTP {} — {}",
            resp.statusCode(),
            resp.body() != null ? resp.body() : "");
      }
    } catch (Exception e) {
      log.error("向 Log4AI 注册时异常: {}", e.getMessage(), e);
    }
  }
}
