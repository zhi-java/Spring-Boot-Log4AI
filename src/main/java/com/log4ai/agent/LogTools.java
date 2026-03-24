package com.log4ai.agent;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.log4ai.config.LogAgentProperties;
import com.log4ai.support.LogFileSupport;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 日志分析 Agent 的 <b>Action</b> 执行层：方法由大模型通过 <b>Tool Calling</b> 选型触发，返回值作为 <b>Observation</b>
 * 回到对话，供模型继续 ReAct 式推理直至可给出最终 Answer。
 *
 * <p>支持按 <b>serviceId</b> 切换多套日志根目录（多容器 / 宿主机挂载），与 {@code log4ai.logs.services} 配置对齐。
 */
@Component
public class LogTools {

  private final LogAgentProperties props;
  private final LogFileSupport files;

  public LogTools(LogAgentProperties props, LogFileSupport files) {
    this.props = props;
    this.files = files;
  }

  @Tool(
      name = "listLogServices",
      value =
          "列出当前已接入的业务服务（serviceId、展示名、活动日志路径与日历目录根路径）。"
              + "多服务环境下必须先调用或参考本结果，再对其他工具传入正确的 serviceId。"
              + "仅单一日志配置时也会说明全局路径。")
  public String listLogServices() {
    return files.describeServices();
  }

  @Tool(
      name = "searchCurrentLog",
      value =
          "在指定业务服务的活动日志中按关键字全文检索，返回命中段落及前后文。"
              + "keyword 使用日志中可能出现的英文或中文片段；linesBefore/linesAfter 控制上下文行数。"
              + "serviceId 与已注册服务的 ID 一致；单实例（未注册多服务）时传空串。")
  public String searchCurrentLog(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("关键字，如 Exception、NullPointerException、login failed、超时") String keyword,
      @P("匹配行之前包含的行数，默认可用 5") int linesBefore,
      @P("匹配行之后包含的行数，默认可用 15") int linesAfter) {
    try {
      int b =
          linesBefore >= 0 ? linesBefore : props.getLogs().getDefaultLinesBefore();
      int a = linesAfter >= 0 ? linesAfter : props.getLogs().getDefaultLinesAfter();
      return files.searchCurrent(normServiceId(serviceId), keyword, b, a);
    } catch (IllegalArgumentException e) {
      return "活动日志关键字检索未能执行: " + e.getMessage() + " 建议先查看当前已接入的日志服务说明。";
    } catch (IOException e) {
      return "活动日志检索失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "tailCurrentLog",
      value =
          "读取指定业务服务活动日志的最后若干行。serviceId 含义同 searchCurrentLog。")
  public String tailCurrentLog(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("要读取的末尾行数，例如 50～200") int lineCount) {
    try {
      LogFileSupport.ResolvedPaths r = files.resolvePaths(normServiceId(serviceId));
      return files.tail(r.currentLogFile(), lineCount, r);
    } catch (IllegalArgumentException e) {
      return "读取活动日志末尾未能执行: " + e.getMessage() + " 建议先查看当前已接入的日志服务说明。";
    } catch (IOException e) {
      return "读取活动日志末尾失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "listHistoricalLogs",
      value =
          "列出指定业务服务「日历目录」下的日志文件（仅此一根目录，递归包含子目录，返回相对日历根的路径）。"
              + "date 可为 yyyy-MM-dd 或路径/文件名片段；空字符串列出全部（可能较多）。"
              + "serviceId 指定该业务挂载的日历根所属服务。")
  public String listHistoricalLogs(
      @P("业务服务 ID；未配置多服务时传空字符串") String serviceId,
      @P("日期或文件名子串过滤；不需要过滤时传空字符串") String date) {
    try {
      return files.listCalendarLogs(normServiceId(serviceId), date);
    } catch (IllegalArgumentException e) {
      return "列出日历目录文件未能执行: " + e.getMessage() + " 建议先查看当前已接入的日志服务说明。";
    } catch (IOException e) {
      return "列出日历目录文件失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "searchLogFile",
      value =
          "在指定业务服务的「日历目录」（含子目录）下，按相对路径定位文件并检索关键字。支持 .gz。"
              + "fileName 可为子路径，如 2024/03/app.log 或仅文件名；相对时基于该 serviceId 的日历根解析。"
              + "timeRange 可选：时间子串过滤日志行。")
  public String searchLogFile(
      @P("业务服务 ID；未配置多服务时传空字符串") String serviceId,
      @P("文件名或路径，例如 app-2026-03-19.log 或 error.log.gz") String fileName,
      @P("关键字") String keyword,
      @P("可选时间子串过滤，如 20:15 或 08:0；不需要时传空字符串") String timeRange) {
    try {
      String tr = timeRange == null || timeRange.trim().isEmpty() ? null : timeRange;
      return files.searchNamedLog(normServiceId(serviceId), fileName, keyword, tr);
    } catch (IllegalArgumentException e) {
      return "日历目录内文件检索未能执行: " + e.getMessage() + " 建议先查看当前已接入的日志服务说明。";
    } catch (IOException e) {
      return "日历目录内文件检索失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "headCurrentLog",
      value =
          "读取指定业务服务活动日志的**开头**若干行（用于确认格式、启动横幅）。"
              + "lineCount 受配置 maxHeadLines 限制。serviceId 同 searchCurrentLog。")
  public String headCurrentLog(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("开头行数，例如 30～100") int lineCount) {
    try {
      LogFileSupport.ResolvedPaths r = files.resolvePaths(normServiceId(serviceId));
      return files.head(r.currentLogFile(), lineCount, r);
    } catch (IllegalArgumentException e) {
      return "读取活动日志开头未能执行: " + e.getMessage() + " 建议先查看当前已接入的日志服务说明。";
    } catch (IOException e) {
      return "读取活动日志开头失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "searchCurrentLogWithTimeFilter",
      value =
          "在活动日志中按**关键字**检索，且匹配行须**同时包含**可选时间子串 timeSubstring（如 10:25 或 2026-03-20）。"
              + "适合缩小到某时段。serviceId 同 searchCurrentLog。")
  public String searchCurrentLogWithTimeFilter(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("关键字") String keyword,
      @P("时间行子串；不需要时传空字符串") String timeSubstring,
      @P("匹配行前上下文行数") int linesBefore,
      @P("匹配行后上下文行数") int linesAfter) {
    try {
      int b =
          linesBefore >= 0 ? linesBefore : props.getLogs().getDefaultLinesBefore();
      int a = linesAfter >= 0 ? linesAfter : props.getLogs().getDefaultLinesAfter();
      return files.searchCurrentWithTimeFilter(
          normServiceId(serviceId), keyword, timeSubstring, b, a);
    } catch (IllegalArgumentException e) {
      return "带时间过滤的活动日志检索未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "带时间过滤的活动日志检索失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "searchCurrentLogRegex",
      value =
          "在活动日志中用 **Java 正则**（大小写不敏感）全文检索，返回命中段及上下文。"
              + "pattern 长度受 maxRegexPatternLength 限制；复杂模式慎用以免回溯过慢。")
  public String searchCurrentLogRegex(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("正则表达式，如 NullPointerException|timeout") String pattern,
      @P("匹配行前上下文行数") int linesBefore,
      @P("匹配行后上下文行数") int linesAfter) {
    try {
      int b =
          linesBefore >= 0 ? linesBefore : props.getLogs().getDefaultLinesBefore();
      int a = linesAfter >= 0 ? linesAfter : props.getLogs().getDefaultLinesAfter();
      return files.searchCurrentRegex(normServiceId(serviceId), pattern, b, a);
    } catch (IllegalArgumentException e) {
      return "活动日志正则检索未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "活动日志正则检索失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "summarizeRecentLogLevels",
      value =
          "读取活动日志**尾部**若干行，启发式统计 TRACE/DEBUG/INFO/WARN/ERROR 等关键字出现次数（非严谨解析）。"
              + "用于快速判断近期错误/告警密度。")
  public String summarizeRecentLogLevels(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("尾部采样行数，如 200～800") int tailLineCount) {
    try {
      return files.summarizeRecentLogLevels(normServiceId(serviceId), tailLineCount);
    } catch (IllegalArgumentException e) {
      return "日志级别统计未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "日志级别统计失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "getLogFileMeta",
      value =
          "获取日历目录内某文件的**元信息**（大小、最后修改时间、是否 gzip）。"
              + "fileName 为相对日历根的路径，规则同 searchLogFile。")
  public String getLogFileMeta(
      @P("业务服务 ID；未配置多服务时传空字符串") String serviceId,
      @P("相对日历根的文件路径") String fileName) {
    try {
      return files.getLogFileMeta(normServiceId(serviceId), fileName);
    } catch (IllegalArgumentException e) {
      return "读取文件元信息未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "读取文件元信息失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "searchJsonFieldInLogFile",
      value =
          "在日历目录内日志文件中扫描行：对**整行 JSON** 解析，按点分路径 fieldPath（如 user.id）匹配字段；"
              + "valueContains 非空时字段文本还须包含该子串。扫描行数受 maxJsonScanLines 限制。")
  public String searchJsonFieldInLogFile(
      @P("业务服务 ID；未配置多服务时传空字符串") String serviceId,
      @P("相对日历根的文件路径") String fileName,
      @P("JSON 字段路径，如 message 或 ctx.userId") String fieldPath,
      @P("值须包含的子串；任意值匹配时传空字符串") String valueContains) {
    try {
      return files.searchJsonFieldInLogFile(
          normServiceId(serviceId), fileName, fieldPath, valueContains);
    } catch (IllegalArgumentException e) {
      return "JSON 字段检索未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "JSON 字段检索失败: " + e.getMessage();
    }
  }

  @Tool(
      name = "findExceptionBlocksInRecentLog",
      value =
          "从活动日志**尾部**窗口启发式提取异常相关片段（含 Exception、Caused by、典型 \\tat 堆栈行）。"
              + "用于在未精确关键字时快速抓取栈信息。")
  public String findExceptionBlocksInRecentLog(
      @P("业务服务 ID；未注册多服务时传空字符串") String serviceId,
      @P("尾部采样行数，如 300～1000") int tailLineCount) {
    try {
      return files.findExceptionBlocksInRecentLog(normServiceId(serviceId), tailLineCount);
    } catch (IllegalArgumentException e) {
      return "异常片段提取未能执行: " + e.getMessage();
    } catch (IOException e) {
      return "异常片段提取失败: " + e.getMessage();
    }
  }

  private String normServiceId(String serviceId) {
    return serviceId == null ? "" : serviceId;
  }
}
