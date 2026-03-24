package com.log4ai.registry.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.log4ai.config.LogAgentProperties;
import com.log4ai.support.Log4AiSystemLogPaths;
import com.log4ai.web.dto.LogAgentRegistryDtos.RegistryRegisterRequest;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    LogAgentProperties.Registry.Client c = props.getRegistry().getClient();
    if (!c.isEnabled()) {
      return;
    }
    String base = c.getServerBaseUrl();
    if (base == null || base.trim().isEmpty()) {
      log.warn("log4ai.registry.client.server-base-url 未配置，跳过向 Log4AI 注册");
      return;
    }
    String token = c.getToken();
    if (token == null || token.trim().isEmpty()) {
      log.warn("log4ai.registry.client.token 未配置，跳过向 Log4AI 注册");
      return;
    }
    String serviceId = c.getServiceId();
    if (serviceId == null || serviceId.trim().isEmpty()) {
      log.warn("log4ai.registry.client.service-id 未配置，跳过向 Log4AI 注册");
      return;
    }
    String logPath = c.getLogPath();
    if (logPath == null || logPath.trim().isEmpty()) {
      logPath = systemLogPaths.resolve(workspace).currentLogFile().toString();
    }
    String display = c.getDisplayName();
    if (display == null || display.trim().isEmpty()) {
      display = serviceId;
    }
    String url = base.replaceAll("/+$", "") + "/log4ai/registry/register";
    try {
      RegistryRegisterRequest body =
          new RegistryRegisterRequest(serviceId, display, logPath);
      String json = objectMapper.writeValueAsString(body);
      RestTemplate restTemplate = buildRestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
      headers.set("Authorization", "Bearer " + token);
      HttpEntity<String> requestEntity = new HttpEntity<String>(json, headers);
      ResponseEntity<String> resp =
          restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
      if (resp.getStatusCode().is2xxSuccessful()) {
        log.info("已向 Log4AI 注册日志服务: serviceId={}, path={}", serviceId, logPath);
      } else {
        log.error(
            "向 Log4AI 注册失败: HTTP {} — {}",
            resp.getStatusCodeValue(),
            resp.getBody() != null ? resp.getBody() : "");
      }
    } catch (RestClientException e) {
      log.error("向 Log4AI 注册时异常: {}", e.getMessage(), e);
    } catch (Exception e) {
      log.error("向 Log4AI 注册时异常: {}", e.getMessage(), e);
    }
  }

  private RestTemplate buildRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(20000);
    return new RestTemplate(factory);
  }
}
