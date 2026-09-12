package com.jzqs.app.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 取餐订阅消息发送专用线程池。
 *
 * <p>为什么固定成「单线程 + 1 个排队位 + 丢弃最旧任务」：
 * <ol>
 *   <li>发送任务本身是幂等的：每次触发都会重新扫描所有「已到释放时间但仍未发送」的订单，
 *       所以个别触发被丢弃不会漏消息，下一分钟自然会补上；</li>
 *   <li>旧配置（2 线程 + 8 队列 + CallerRunsPolicy）在微信接口变慢/卡住时会把队列堆满，
 *       随后 CallerRunsPolicy 让调度线程同步执行任务，导致定时调度整体停摆，
 *       恢复后一次性补发——这正是「到点的消息拖到几小时后才收到」的根因；</li>
 *   <li>单线程串行发送也避免了对微信接口形成并发冲击。</li>
 * </ol>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "backgroundTaskExecutor")
    public Executor backgroundTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("bg-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
