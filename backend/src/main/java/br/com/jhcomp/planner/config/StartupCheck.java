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
                                                 AiAssistantService ai) {
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
