package com.weliao.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 回复走独立线程池：模型调用可能要几秒到几十秒，
 * 绝对不能占用 WebSocket 的 IO 线程，否则一条慢回复会拖住所有人的消息。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AI_EXECUTOR = "aiExecutor";

    @Bean(name = AI_EXECUTOR)
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-reply-");
        // 队列满时由调用线程执行，宁可慢一点也不要丢回复
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
