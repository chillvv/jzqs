package com.jzqs.app.common.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 开启 Spring 异步支持，并为后台定时/通知类任务提供独立线程池。
 *
 * 目的：送达订阅通知等后台任务原本在 Web 请求线程（Tomcat）上同步执行，
 * 会长时间占用数据库连接、与骑手实时请求争抢连接池，导致连接池打满而“系统繁忙”。
 * 改为异步后，这类任务在独立线程池运行，与 Web 请求线程解耦，避免挤占骑手请求。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @org.springframework.context.annotation.Bean(name = "backgroundTaskExecutor")
    public Executor backgroundTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("bg-task-");
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
