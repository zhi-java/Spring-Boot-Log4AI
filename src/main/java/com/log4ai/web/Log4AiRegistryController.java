package com.log4ai.web;

import com.log4ai.config.LogAgentProperties;
import com.log4ai.runtime.Log4AiRuntimeService;
import com.log4ai.web.dto.LogAgentRegistryDtos.RegistryRegisterRequest;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务应用携带共享密钥注册本机日志路径，避免在 Log4AI 控制台暴露服务器文件系统路径。
 *
 * <p>启用条件：{@code log4ai.registry.enabled=true} 且配置 {@code log4ai.registry.shared-secret}。
 */
@RestController
@RequestMapping(path = "/log4ai/registry", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "log4ai.registry", name = "enabled", havingValue = "true")
public class Log4AiRegistryController {

  private final Log4AiRuntimeService runtime;
  private final LogAgentProperties props;

  public Log4AiRegistryController(Log4AiRuntimeService runtime, LogAgentProperties props) {
    this.runtime = runtime;
    this.props = props;
  }

  @PostMapping(
      path = "/register",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public void register(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Log4AI-Registry-Token", required = false) String headerToken,
      @RequestBody(required = false) RegistryRegisterRequest body) {
    assertRegistryToken(resolveToken(authorization, headerToken));
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
    }
    try {
      runtime.registerOrUpdateService(body.serviceId(), body.displayName(), body.logPath());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "持久化失败: " + e.getMessage());
    }
  }

  @DeleteMapping(path = "/services/{serviceId}")
  public void unregister(
      @PathVariable("serviceId") String serviceId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Log4AI-Registry-Token", required = false) String headerToken) {
    assertRegistryToken(resolveToken(authorization, headerToken));
    try {
      runtime.removeRegisteredService(serviceId);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "持久化失败: " + e.getMessage());
    }
  }

  private void assertRegistryToken(String token) {
    String expected = props.getRegistry().getSharedSecret();
    if (expected == null || expected.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "log4ai.registry.shared-secret 未配置，拒绝注册");
    }
    if (token == null || token.isBlank() || !expected.equals(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "注册令牌无效");
    }
  }

  private static String resolveToken(String authorization, String headerToken) {
    if (headerToken != null && !headerToken.isBlank()) {
      return headerToken.trim();
    }
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorization.substring(7).trim();
    }
    return "";
  }
}
