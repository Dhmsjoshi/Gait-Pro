package com.gait.biomedicaltwin.config.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor") // 🔥 Is name ko dhund raha hai aapka custom proxy engine
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Industrial Standard Core Parameters
        executor.setCorePoolSize(5);       // Minimum active threads jo hamesha ready rahenge
        executor.setMaxPoolSize(10);       // Heavy load par maximum threads kitne badh sakte hain
        executor.setQueueCapacity(500);    // Threads busy hone par kitne tasks queue mein wait karenge
        executor.setThreadNamePrefix("GaitAsyncThread-"); // Logs mein pehchanne ke liye clear prefix

        executor.initialize();
        return executor;
    }
}
