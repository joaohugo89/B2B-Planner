package br.com.jhcomp.planner.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(VideoProperties.class)
public class VideoConfig {

    private static final Logger log = LoggerFactory.getLogger(VideoConfig.class);

    /**
     * Uma thread só, de propósito: voz e sincronia labial carregam modelos de vários
     * gigabytes numa GPU de 12 GB, e dois ao mesmo tempo derrubam os dois. A fila é a
     * gerência de VRAM.
     */
    @Bean
    public Executor videoJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("video-job-");
        // Um job pode levar dezenas de minutos: o shutdown não espera, e o VideoPipeline
        // encerra o motor em curso. O job volta como erro explicado no próximo boot.
        executor.initialize();
        return executor;
    }

    @Bean
    public ApplicationRunner failOrphanedVideoJobs(VideoJobStore store) {
        return args -> {
            int orfaos = store.failOrphaned();
            if (orfaos > 0) {
                log.warn("{} job(s) de vídeo estavam em andamento no boot e foram marcados como erro.", orfaos);
            }
        };
    }
}
