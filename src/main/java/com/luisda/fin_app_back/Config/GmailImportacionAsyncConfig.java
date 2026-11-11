package com.luisda.fin_app_back.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Executor acotado para que la importación masiva no bloquee las peticiones HTTP. */
@Configuration
public class GmailImportacionAsyncConfig {
    @Bean(name = "gmailImportExecutor")
    public ThreadPoolTaskExecutor gmailImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("gmail-import-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
