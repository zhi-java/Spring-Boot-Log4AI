package com.log4ai.web;

import com.log4ai.service.LogAnalyzerStreamingService;
import com.log4ai.web.dto.LogAgentChatDtos.ChatRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式接口。始终注册映射；若未启用流式或未装配 {@link LogAnalyzerStreamingService}，返回 503 而非缺失路由（404）。
 */
@RestController
@RequestMapping(path = "/log4ai")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@CrossOrigin
public class LogAgentStreamController {

  private final ObjectProvider<LogAnalyzerStreamingService> streamingService;

  public LogAgentStreamController(ObjectProvider<LogAnalyzerStreamingService> streamingService) {
    this.streamingService = streamingService;
  }

  @PostMapping(
      value = "/chat/stream",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chatStream(
      @RequestBody ChatRequest body,
      @RequestHeader(value = "X-Log4AI-Session", required = false) String headerSession) {
    LogAnalyzerStreamingService svc = streamingService.getIfAvailable();
    if (svc == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "流式未启用或未装配：请确认 log4ai.streaming.enabled=true 且应用已正常创建 OpenAiStreamingChatModel / LogAnalyzerStreamingService。");
    }
    if (body == null || body.message() == null || body.message().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
    }
    String sid = headerSession != null && !headerSession.isBlank() ? headerSession : body.sessionId();
    return svc.stream(sid, body.message());
  }
}
