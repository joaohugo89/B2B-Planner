package br.com.jhcomp.planner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "jhcomp.cors")
public class CorsProperties {

    /**
     * Origens que o navegador pode usar para chamar a API. O padrão são as duas do Vite
     * local, para que rodar sem variável nenhuma continue funcionando.
     */
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
}
