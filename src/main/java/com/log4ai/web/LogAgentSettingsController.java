package com.log4ai.web;

import com.log4ai.runtime.Log4AiRuntimeService;
import com.log4ai.web.dto.LogAgentSettingsDtos.LogsSettingsRequest;
import com.log4ai.web.dto.LogAgentSettingsDtos.LlmSettingsRequest;
import com.log4ai.web.dto.LogAgentSettingsDtos.SettingsResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 内置控制台用的运行时配置 API（日志多服务注册、LLM 连接）。保存后立即写盘并重建 Agent。
 */
@RestController
@RequestMapping(path = "/log4ai/settings", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "log4ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@CrossOrigin
public class LogAgentSettingsController {

  private final Log4AiRuntimeService runtime;

  public LogAgentSettingsController(Log4AiRuntimeService runtime) {
    this.runtime = runtime;
  }

  @GetMapping
  public SettingsResponse get() {
    return runtime.snapshot();
  }

  @PutMapping(path = "/llm", consumes = MediaType.APPLICATION_JSON_VALUE)
  public SettingsResponse putLlm(@RequestBody(required = false) LlmSettingsRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
    }
    try {
      runtime.applyLlm(body);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "保存配置文件失败: " + e.getMessage());
    }
    return runtime.snapshot();
  }

  @PutMapping(path = "/logs", consumes = MediaType.APPLICATION_JSON_VALUE)
  public SettingsResponse putLogs(@RequestBody(required = false) LogsSettingsRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
    }
    try {
      runtime.applyLogs(body);
    } catch (IllegalArgumentException e) {
      String msg = e.getMessage();
      if (msg != null && msg.contains("disable-ui-log-paths")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "保存配置文件失败: " + e.getMessage());
    }
    return runtime.snapshot();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
    Throwable c = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
    String msg = c.getMessage() == null ? "请求体不是合法 JSON 或与字段类型不匹配" : c.getMessage();
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }
}
