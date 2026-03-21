package com.log4ai.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.log4ai.runtime.Log4AiAssistantsHolder;
import com.log4ai.support.LogEvidenceUiRedactor;
import com.log4ai.support.LogToolUiSummary;

import dev.langchain4j.service.tool.ToolExecution;

/**
 * 将流式 Agent（见 {@link com.log4ai.runtime.Log4AiAssistantsHolder}）的 {@code TokenStream} 桥接为 SSE；由 {@link
 * com.log4ai.autoconfigure.Log4AiStreamingAutoConfiguration} 注册 Bean（非流式场景下不创建）。
 *
 * <p>在独立线程中执行 {@link dev.langchain4j.service.TokenStream#start()}，避免占用 Servlet 线程直至流结束。
 */
public class LogAnalyzerStreamingService {

  private static final long SSE_TIMEOUT_MS = 600_000L;

  private final Log4AiAssistantsHolder assistantsHolder;

  public LogAnalyzerStreamingService(Log4AiAssistantsHolder assistantsHolder) {
    this.assistantsHolder = assistantsHolder;
  }

  /**
   * 通过 SSE 推送：{@code event:delta}（文本增量）、{@code event:tool}（工具执行摘要）、{@code event:done} 结束。
   */
  public SseEmitter stream(String sessionId, String userMessage) {
    String sid =
        (sessionId == null || sessionId.isBlank()) ? "default" : sessionId.trim();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    CompletableFuture.runAsync(
        () -> {
          try {
            assistantsHolder
                .requireStreaming()
                .chat(sid, userMessage)
                .onNext(
                    token -> {
                      if (token == null || token.isEmpty()) {
                        return;
                      }
                      try {
                        safeSend(
                            emitter,
                            SseEmitter.event()
                                .name("delta")
                                .data(Map.of("t", token), MediaType.APPLICATION_JSON));
                      } catch (IOException e) {
                        try {
                          emitter.completeWithError(e);
                        } catch (Exception ignored) {
                          // 连接已关闭时 completeWithError 也可能失败
                        }
                      }
                    })
                .onToolExecuted(exec -> sendToolEvent(emitter, exec))
                .onComplete(
                    response -> {
                      try {
                        safeSend(
                            emitter,
                            SseEmitter.event()
                                .name("done")
                                .data(Map.of(), MediaType.APPLICATION_JSON));
                      } catch (IOException ignored) {
                        // ignore
                      }
                      try {
                        emitter.complete();
                      } catch (Exception ignored) {
                        // 客户端已断开时 complete 可能无效
                      }
                    })
                .onError(
                    error -> {
                      try {
                        String msg =
                            error.getMessage() == null ? error.toString() : error.getMessage();
                        safeSend(
                            emitter,
                            SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", msg), MediaType.APPLICATION_JSON));
                      } catch (IOException ignored) {
                        // ignore
                      }
                      try {
                        emitter.completeWithError(error);
                      } catch (Exception ignored) {
                        // 已结束或客户端已断开
                      }
                    })
                .start();
          } catch (Throwable t) {
            emitter.completeWithError(t);
          }
        });

    return emitter;
  }

  private void sendToolEvent(SseEmitter emitter, ToolExecution exec) {
    try {
      String name = exec.request() != null ? exec.request().name() : "tool";
      String raw = exec.result() == null ? "" : exec.result();
      String redacted = LogEvidenceUiRedactor.forSsePreview(raw);
      String summary = LogToolUiSummary.line(name, redacted);
      safeSend(
          emitter,
          SseEmitter.event()
              .name("tool")
              .data(Map.of("name", name, "summary", summary), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      try {
        emitter.completeWithError(e);
      } catch (Exception ignored) {
        // 连接已关闭
      }
    }
  }

  /**
   * 客户端已断开时 {@link SseEmitter#send} 常抛出 {@link IllegalStateException}（而非仅 IOException），
   * 若向上抛到 LangChain4j 会刷 ERROR。发送失败时静默返回，表示无需再向该连接写数据。
   */
  private static void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event)
      throws IOException {
    try {
      emitter.send(event);
    } catch (IllegalStateException ignored) {
      // emitter 已 complete / 底层连接已关闭
    }
  }
}
