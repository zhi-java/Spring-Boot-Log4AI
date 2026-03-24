package com.log4ai.web;

import com.log4ai.config.LogAgentProperties;
import com.log4ai.service.LogAnalyzerAgent;
import com.log4ai.web.dto.LogAgentChatDtos.ChatRequest;
import com.log4ai.web.dto.LogAgentChatDtos.ChatResponse;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 非流式 HTTP 与控制台入口；可通过 {@code log4ai.web.enabled=false} 关闭以作纯组件嵌入。
 */
@RestController
@RequestMapping(path = "/log4ai", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "log4ai.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@CrossOrigin
public class LogAgentChatController {

  private final LogAnalyzerAgent agent;
  private final LogAgentProperties props;

  public LogAgentChatController(LogAnalyzerAgent agent, LogAgentProperties props) {
    this.agent = agent;
    this.props = props;
  }

  /** 跳转至内置控制台（{@code log4ai.web.console-enabled=false} 时可关闭）。 */
  @GetMapping("/ui")
  public void consoleUi(HttpServletResponse response) throws IOException {
    if (!props.getWeb().isConsoleEnabled()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Log4AI console disabled");
      return;
    }
    response.sendRedirect("/log4ai/index.html");
  }

  @PostMapping("/chat")
  public ChatResponse chat(
      @RequestBody ChatRequest body,
      @RequestHeader(value = "X-Log4AI-Session", required = false) String headerSession) {
    if (body == null || body.message() == null || body.message().trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
    }
    String sid =
        headerSession != null && !headerSession.trim().isEmpty()
            ? headerSession
            : body.sessionId();
    return new ChatResponse(agent.ask(sid, body.message()));
  }
}
