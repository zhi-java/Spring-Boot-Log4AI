package com.log4ai.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.log4ai.runtime.Log4AiAssistantsHolder;

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
   * 通过 SSE 推送：{@code event:delta}（文本增量）、{@code event:done} 结束。
   *
   * <p>在 LangChain4j 0.30.0 下，{@code TokenStream} 仅支持文本增量与完成/错误回调，暂无工具执行事件回调，故这里不再推送
   * {@code event:tool}。
   */
  public SseEmitter stream(String sessionId, String userMessage) {
    String sid =
        (sessionId == null || sessionId.trim().isEmpty()) ? "default" : sessionId.trim();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    CompletableFuture.runAsync(
        new Runnable() {
          @Override
          public void run() {
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
                                  .data(singletonMap("t", token), MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                          try {
                            emitter.completeWithError(e);
                          } catch (Exception ignored) {
                            // 连接已关闭时 completeWithError 也可能失败
                          }
                        }
                      })
                  .onComplete(
                      response -> {
                        try {
                          safeSend(
                              emitter,
                              SseEmitter.event()
                                  .name("done")
                                  .data(Collections.emptyMap(), MediaType.APPLICATION_JSON));
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
                                  .data(singletonMap("message", msg), MediaType.APPLICATION_JSON));
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
          }
        });

    return emitter;
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

  private static Map<String, Object> singletonMap(String key, Object value) {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put(key, value);
    return map;
  }
}
