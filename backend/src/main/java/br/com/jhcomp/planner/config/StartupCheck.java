package br.com.jhcomp.planner.config;

import br.com.jhcomp.planner.ai.AiAssistantService;
import br.com.jhcomp.planner.instagram.InstagramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Relatório de configuração no boot: diz, em uma olhada, o que está pronto e o que
 * falta ligar. O que é obrigatório para subir já falha antes daqui (perfil prod);
 * isto cobre o resto, que apenas desliga funcionalidades.
 */
@Configuration
public class StartupCheck {

    private static final Logger log = LoggerFactory.getLogger(StartupCheck.class);

    @Bean
    public ApplicationRunner reportConfiguration(Environment environment,
                                                 InstagramService instagram,
                                                 AiAssistantService ai,
                                                 br.com.jhcomp.planner.video.VideoPipeline video) {
        return args -> {
            List<String> linhas = new ArrayList<>();

            linhas.add(item("Banco de dados", true,
                    environment.getProperty("spring.datasource.url", "—")));

            linhas.add(item("Assistente de IA", ai.isEnabled(),
                    ai.isEnabled()
                            ? "modelo " + ai.model()
                            : "sem chave — defina no painel da conta ou em ANTHROPIC_API_KEY"));

            linhas.add(item("Instagram (app)", instagram.isConfigured(),
                    instagram.isConfigured()
                            ? "app " + instagram.appId()
                            : "faltam INSTAGRAM_APP_ID e INSTAGRAM_APP_SECRET"));

            linhas.add(item("Instagram (URL pública)", instagram.hasPublicBaseUrl(),
                    instagram.hasPublicBaseUrl()
                            ? instagram.publicBaseUrl()
                            : "sem URL pública — a Meta não consegue baixar as artes"));

            linhas.add(item("Instagram (conta)", instagram.connectedAccount().isPresent(),
                    instagram.connectedAccount()
                            .map(conta -> "@" + conta.getUsername())
                            .orElse("nenhuma conta conectada — use a aba Instagram")));

            var motores = video.engines();
            boolean render = Boolean.TRUE.equals(motores.get(br.com.jhcomp.planner.video.VideoJob.Step.RENDER));
            linhas.add(item("Video Studio (render)", render,
                    render ? "FFmpeg encontrado" : "FFmpeg não encontrado — instale ou defina FFMPEG_PATH"));
            motores.remove(br.com.jhcomp.planner.video.VideoJob.Step.RENDER);
            linhas.add(item("Video Studio (motores)", motores.containsValue(true),
                    motores.containsValue(true)
                            ? "ligados: " + motores.entrySet().stream().filter(java.util.Map.Entry::getValue)
                                    .map(e -> e.getKey().name()).toList()
                            : "nenhum motor de IA configurado — áudio e legenda entram à mão"));

            log.info("""

                    ====================================================================
                     JH.COMP Planner — configuração
                    {}
                    ====================================================================""",
                    String.join("\n", linhas));
        };
    }

    private String item(String nome, boolean pronto, String detalhe) {
        return " %s %-26s %s".formatted(pronto ? "[ok]  " : "[falta]", nome, detalhe);
    }
}
