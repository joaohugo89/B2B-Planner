package br.com.jhcomp.planner.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jhcomp.ai")
public class AiProperties {

    /** Chave da API Anthropic. Vazia desliga o assistente. */
    private String apiKey = "";

    /** Modelo Claude usado pelo assistente de conteúdo. */
    private String model = "claude-opus-5-5";

    /** Teto de tokens por resposta. */
    private long maxTokens = 16000;

    /** Endpoint da API; vazio usa o da Anthropic. Útil para gateway próprio ou teste local. */
    private String baseUrl = "";

    /** Esforço do modelo quando o painel não define outro: low, medium ou high. */
    private String effort = "medium";

    /** Arquivo com uma linha JSON por chamada à API. */
    private String usageLog = "logs/api_usage.log";

    /** Minutos que um rascunho fica guardado em memória. */
    private long draftCacheMinutes = 60;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getEffort() { return effort; }
    public void setEffort(String effort) { this.effort = effort; }

    public String getUsageLog() { return usageLog; }
    public void setUsageLog(String usageLog) { this.usageLog = usageLog; }

    public long getDraftCacheMinutes() { return draftCacheMinutes; }
    public void setDraftCacheMinutes(long draftCacheMinutes) { this.draftCacheMinutes = draftCacheMinutes; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
