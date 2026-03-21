package com.log4ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 声明式日志分析 Agent（LangChain4j {@code AiServices}）。
 *
 * <p><b>ReAct 在代码中的组织方式：</b>本接口由框架生成代理实例；一次 {@link #chat} 调用背后，可能发生多轮「模型推理
 * → <b>原生 Tool Calling</b>（选工具与参数）→ 本地执行 {@link LogTools} → 将工具返回值作为 Observation
 * 续写」的循环。你<strong>无需</strong>自写 {@code while} 解析 "Action: ..." 文本——编排由 LangChain4j
 * 与模型的函数调用能力完成。
 *
 * <p>系统提示词约束模型按 Thought → Action（工具）→ Observation 迭代，见 {@code
 * classpath:prompts/log-analyzer-system.txt}。
 *
 * <p>应用层门面类 {@link com.log4ai.service.LogAnalyzerAgent} 负责传入 {@link MemoryId}，使多轮对话在同一会话内保留
 * 此前的用户问题、工具结果与模型回复，便于连续追问。
 */
public interface LogAnalyzerAssistant {

  @SystemMessage(fromResource = "/prompts/log-analyzer-system.txt")
  String chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
