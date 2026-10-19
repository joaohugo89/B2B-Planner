package br.com.jhcomp.planner.instagram;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class InstagramPublishConfig {

    /**
     * Pool pequeno e dedicado: a Meta limita requisições por conta, então publicar duas coisas
     * ao mesmo tempo já é o bastante. A fila evita recusar um pedido em rajada.
     */
    @Bean
    public Executor instagramPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("ig-publish-");
        // No shutdown, espera a publicação em curso terminar antes de derrubar o contexto.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
