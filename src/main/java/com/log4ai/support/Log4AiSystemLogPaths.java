package com.log4ai.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;

/**
 * 单实例（未注册 services）时，按 Spring Boot 日志配置与约定解析「本进程」正在写入的日志文件；<b>日历目录</b>取该文件所在目录（按日期/子目录组织的日志树均在此根下）。
 *
 * <p>由 {@link com.log4ai.autoconfigure.Log4AiAutoConfiguration} 注册为 Bean。
 */
public final class Log4AiSystemLogPaths {

  private final String loggingFileName;
  private final String loggingFilePath;

  public Log4AiSystemLogPaths(
      @Value("${logging.file.name:}") String loggingFileName,
      @Value("${logging.file.path:}") String loggingFilePath) {
    this.loggingFileName = loggingFileName == null ? "" : loggingFileName.trim();
    this.loggingFilePath = loggingFilePath == null ? "" : loggingFilePath.trim();
  }

  /** @param workspaceRoot 进程工作目录，用于解析相对路径 */
  public LogFileSupport.ResolvedPaths resolve(Path workspaceRoot) {
    Path currentFile = resolveCurrentLogFile(workspaceRoot);
    Path calendarRoot =
        currentFile.getParent() != null
            ? currentFile.getParent()
            : workspaceRoot.resolve("logs").normalize();
    return new LogFileSupport.ResolvedPaths(
        currentFile.normalize(), calendarRoot.normalize(), "_system");
  }

  private Path resolveCurrentLogFile(Path workspaceRoot) {
    if (!loggingFileName.isEmpty()) {
      Path p = Paths.get(loggingFileName);
      return p.isAbsolute() ? p : workspaceRoot.resolve(p).normalize();
    }
    if (!loggingFilePath.isEmpty()) {
      Path dir = Paths.get(loggingFilePath);
      if (!dir.isAbsolute()) {
        dir = workspaceRoot.resolve(dir);
      }
      dir = dir.normalize();
      Path spring = dir.resolve("spring.log");
      if (Files.exists(spring)) {
        return spring.normalize();
      }
      Path app = dir.resolve("application.log");
      if (Files.exists(app)) {
        return app.normalize();
      }
      return spring.normalize();
    }
    Path logsDir = workspaceRoot.resolve("logs");
    Path spring = logsDir.resolve("spring.log");
    Path app = logsDir.resolve("application.log");
    if (Files.exists(spring)) {
      return spring.normalize();
    }
    if (Files.exists(app)) {
      return app.normalize();
    }
    return spring.normalize();
  }
}
