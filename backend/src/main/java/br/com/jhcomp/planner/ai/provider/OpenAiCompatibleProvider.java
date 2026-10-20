package br.com.jhcomp.planner.ai.provider;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qualquer serviço que fale o protocolo /chat/completions da OpenAI.
 *
 * <p>Vale para a própria OpenAI, para o Groq e o OpenRouter (que têm camada gratuita) e para
 * um Ollama local. Muda só a URL base, o modelo e a chave, tudo editável no painel.</p>
 */
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final RestClient http;
    private final ObjectMapper mapper;

    public OpenAiCompatibleProvider(RestClient.Builder builder, ObjectMapper mapper) {
        // Envia o corpo com Content-Length em vez de chunked (servidores simples, como um
        // Ollama ou um gateway caseiro, nem sempre aceitam chunked) e nao espera para sempre.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // Um endereco que responde com redirecionamento nao leva a chave para outro host.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(3));

        this.http = builder.requestFactory(factory).build();
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public AiReply<String> chat(AiRequest request, List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()));
        for (ChatMessage message : history) {
            String role = "assistant".equalsIgnoreCase(message.role()) ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", message.content()));
        }

        JsonNode response = post(request, messages, false);
        return reply(response, text(response));
    }

    @Override
    public <T> AiReply<T> structured(AiRequest request, String instruction, Class<T> type) {
        // Nem todo serviço compatível aceita json_schema; pedir um objeto JSON e descrever o
        // formato no prompt funciona em todos eles, inclusive num Ollama local.
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", request.system() + schemaInstruction(type)),
                Map.of("role", "user", "content", instruction));

        JsonNode response = post(request, messages, true);
        String content = limpar(text(response));

        try {
            return reply(response, mapper.readValue(content, type));
        } catch (Exception ex) {
            log.warn("Resposta fora do formato esperado: {}", content, ex);
            throw new AiUnavailableException(
                    "O modelo respondeu fora do formato esperado. Tente outro modelo ou refaça o pedido.", ex);
        }
    }

    private JsonNode post(AiRequest request, List<Map<String, String>> messages, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", messages);
        body.put("max_tokens", request.maxTokens());
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            return enviar(request, body);
        } catch (RestClientResponseException ex) {
            // Os modelos novos da OpenAI recusam max_tokens e exigem max_completion_tokens.
            if (ex.getResponseBodyAsString().contains("max_completion_tokens")) {
                body.remove("max_tokens");
                body.put("max_completion_tokens", request.maxTokens());
                return enviar(request, body);
            }
            throw erro(ex);
        }
    }

    private JsonNode enviar(AiRequest request, Map<String, Object> body) {
        try {
            JsonNode response = http.post()
                    .uri(baseUrl(request) + "/chat/completions")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("choices")) {
                throw new AiUnavailableException("O serviço de IA respondeu sem conteúdo.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw ex;
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Falha ao falar com o serviço de IA em {}", baseUrl(request), ex);
            throw new AiUnavailableException(
                    "Não foi possível falar com o serviço de IA. Confira a URL base no painel.", ex);
        }
    }

    private AiUnavailableException erro(RestClientResponseException ex) {
        String detalhe = ex.getResponseBodyAsString();
        log.warn("O serviço de IA recusou a requisição ({}): {}", ex.getStatusCode(), detalhe);
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new AiUnavailableException("A chave de API foi recusada. Confira a chave no painel.", ex);
        }
        if (ex.getStatusCode().value() == 429) {
            return new AiUnavailableException("Limite de uso do serviço de IA atingido. Tente de novo em instantes.", ex);
        }
        return new AiUnavailableException("O serviço de IA recusou a requisição: " + mensagem(detalhe), ex);
    }

    /** Tira o envelope do erro, que costuma vir como {"error":{"message":"..."}}. */
    private String mensagem(String corpo) {
        try {
            JsonNode node = mapper.readTree(corpo).path("error").path("message");
            if (!node.isMissingNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            // corpo que não é JSON: devolve como veio, truncado
        }
        return corpo.length() > 300 ? corpo.substring(0, 300) + "…" : corpo;
    }

    private String baseUrl(AiRequest request) {
        String url = request.baseUrl() == null || request.baseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : request.baseUrl().trim();
        if (!isValidBaseUrl(url)) {
            throw new AiUnavailableException(
                    "A URL base do serviço de IA é inválida. Use algo como https://api.groq.com/openai/v1.");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Só http/https e nada de usuário, query ou fragmento na URL — isso evita que um endereço
     * torto (ou colado errado) vire uma chamada a um endpoint que não é o do serviço de IA.
     * Localhost continua valendo de propósito: é assim que um Ollama local é usado.
     */
    public static boolean isValidBaseUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            return (scheme.equals("https") || scheme.equals("http"))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private String text(JsonNode response) {
        return response.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    private <T> AiReply<T> reply(JsonNode response, T content) {
        JsonNode usage = response.path("usage");
        return new AiReply<>(content, usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    }

    /** Alguns modelos devolvem o JSON dentro de uma cerca ```json; aqui ela sai. */
    private String limpar(String texto) {
        String limpo = texto.trim();
        if (limpo.startsWith("```")) {
            int inicio = limpo.indexOf('\n');
            int fim = limpo.lastIndexOf("```");
            if (inicio > 0 && fim > inicio) {
                limpo = limpo.substring(inicio + 1, fim).trim();
            }
        }
        return limpo;
    }

    /** Descreve os campos esperados, já que não usamos json_schema. */
    private String schemaInstruction(Class<?> type) {
        String campos = String.join(", ", camposDe(type));
        return """

                Responda SOMENTE com um objeto JSON válido, sem texto antes ou depois e sem cercas de código.
                O objeto deve ter exatamente estes campos: %s.
                """.formatted(campos);
    }

    private List<String> camposDe(Class<?> type) {
        List<String> campos = new ArrayList<>();
        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                campos.add(component.getName() + " (" + component.getType().getSimpleName() + ")");
            }
        }
        return campos;
    }
}
