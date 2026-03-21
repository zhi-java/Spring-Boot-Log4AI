package com.log4ai.standalone;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 独立部署入口：可执行 JAR 使用 {@code java -jar *-standalone.jar} 启动（见 {@code spring-boot-maven-plugin} 的
 * {@code standalone} classifier）。
 *
 * <p>通过 {@code spring.config.name=application,log4ai-server} 加载可选的 {@code log4ai-server.yml} 默认示例配置，
 * 不与宿主应用的 {@code application.yml} 冲突（宿主未追加该 config name 时不会加载本 jar 内
 * {@code log4ai-server.yml}）。
 */
@SpringBootApplication(scanBasePackages = "com.log4ai")
public class Log4AiStandaloneApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Log4AiStandaloneApplication.class);
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("spring.config.name", "application,log4ai-server");
    defaults.put("spring.application.name", "log4ai-server");
    app.setDefaultProperties(defaults);
    app.run(args);
  }
}
