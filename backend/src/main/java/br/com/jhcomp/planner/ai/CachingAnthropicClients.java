package br.com.jhcomp.planner.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.stereotype.Component;

/** Mantém um cliente por chave: a chave muda no painel e o cliente é recriado. */
@Component
public class CachingAnthropicClients implements AnthropicClients {

    private final AiProperties properties;

    private volatile AnthropicClient client;
    private volatile String clientKey;

    public CachingAnthropicClients(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public AnthropicClient forKey(String apiKey) {
        AnthropicClient current = client;
        if (current != null && apiKey.equals(clientKey)) {
            return current;
        }
        synchronized (this) {
            if (client == null || !apiKey.equals(clientKey)) {
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
                String baseUrl = properties.getBaseUrl();
                if (baseUrl != null && !baseUrl.isBlank()) {
                    builder.baseUrl(baseUrl.trim());
                }
                client = builder.build();
                clientKey = apiKey;
            }
            return client;
        }
    }
}
