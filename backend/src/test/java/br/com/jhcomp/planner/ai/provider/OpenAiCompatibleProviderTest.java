package br.com.jhcomp.planner.ai.provider;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiDtos.PostDraft;
import br.com.jhcomp.planner.ai.AiDtos.PostDraftBatch;
import br.com.jhcomp.planner.ai.AiUnavailableException;
import br.com.jhcomp.planner.ai.provider.AiProvider.AiReply;
import br.com.jhcomp.planner.ai.provider.AiProvider.AiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe um servidor local que responde como a API da OpenAI: cobre o caminho usado por
 * Groq, OpenRouter, OpenAI e Ollama sem gastar chamada de verdade.
 */
class OpenAiCompatibleProviderTest {

    private static final String CHAT_JSON =
            "{\"choices\":[{\"message\":{\"content\":\"resposta do modelo\"}}],"
            + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":45}}";

    /** Rascunho devolvido dentro de uma cerca ```json, como alguns modelos fazem. */
    private static final String DRAFT_JSON =
            "{\"choices\":[{\"message\":{\"content\":\"```json\\n"
            + "{\\\"title\\\":\\\"Backup que funciona\\\",\\\"brandPillar\\\":\\\"Autoridade Tecnica\\\","
            + "\\\"format\\\":\\\"CARROSSEL\\\",\\\"audience\\\":\\\"B2B\\\",\\\"slideCount\\\":5,"
            + "\\\"summary\\\":\\\"resumo\\\",\\\"caption\\\":\\\"legenda\\\",\\\"hashtags\\\":\\\"#jhcomp\\\","
            + "\\\"ctaLabel\\\":\\\"Chame no direct\\\"}\\n```\"}}],"
            + "\"usage\":{\"prompt_tokens\":300,\"completion_tokens\":90}}";

    private HttpServer server;
    private OpenAiCompatibleProvider provider;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String resposta = CHAT_JSON;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = resposta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        provider = new OpenAiCompatibleProvider(RestClient.builder(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private AiRequest request() {
        return new AiRequest("sk-teste", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "modelo-x", "medium", 4000, "Voce e o estrategista da JH.COMP.");
    }

    @Test
    @DisplayName("a conversa devolve o texto e o consumo de tokens")
    void chatReadsContentAndUsage() {
        AiReply<String> reply = provider.chat(request(), List.of(new ChatMessage("user", "me da uma ideia")));

        assertThat(reply.content()).isEqualTo("resposta do modelo");
        assertThat(reply.inputTokens()).isEqualTo(120);
        assertThat(reply.outputTokens()).isEqualTo(45);
        assertThat(corpoRecebido.get()).contains("\"model\":\"modelo-x\"").contains("estrategista");
    }

    @Test
    @DisplayName("o rascunho sai do JSON mesmo vindo dentro de cerca de codigo")
    void structuredParsesFencedJson() {
        resposta = DRAFT_JSON;

        AiReply<PostDraft> reply = provider.structured(request(), "briefing", PostDraft.class);

        assertThat(reply.content().title()).isEqualTo("Backup que funciona");
        assertThat(reply.content().slideCount()).isEqualTo(5);
        assertThat(reply.inputTokens()).isEqualTo(300);
        assertThat(corpoRecebido.get()).contains("\"response_format\"");
    }

    @Test
    @DisplayName("chave recusada vira uma mensagem que o painel sabe mostrar")
    void rejectedKeyBecomesFriendlyError() {
        status = 401;
        resposta = "{\"error\":{\"message\":\"Incorrect API key provided\"}}";

        assertThatThrownBy(() -> provider.chat(request(), List.of(new ChatMessage("user", "oi"))))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("chave");
    }

    @Test
    @DisplayName("resposta que nao e o JSON esperado nao derruba o backend")
    void brokenJsonBecomesFriendlyError() {
        resposta = "{\"choices\":[{\"message\":{\"content\":\"desculpe, nao entendi\"}}],\"usage\":{}}";

        assertThatThrownBy(() -> provider.structured(request(), "briefing", PostDraft.class))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("formato");
    }

    @Test
    @DisplayName("URL base torta e recusada antes de qualquer chamada")
    void invalidBaseUrlIsRejected() {
        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("https://api.groq.com/openai/v1")).isTrue();
        // Ollama roda em casa: localhost continua valendo de proposito.
        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("http://localhost:11434/v1")).isTrue();

        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("file:///etc/passwd")).isFalse();
        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("https://usuario:senha@api.exemplo.com")).isFalse();
        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("https://api.exemplo.com?x=1")).isFalse();
        assertThat(OpenAiCompatibleProvider.isValidBaseUrl("nao e uma url")).isFalse();

        AiRequest torta = new AiRequest("sk", "file:///etc/passwd", "m", "medium", 10, "s");
        assertThatThrownBy(() -> provider.chat(torta, List.of(new ChatMessage("user", "oi"))))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("inválida");
    }

    @Test
    @DisplayName("a instrucao descreve tambem os campos de dentro de uma lista")
    void schemaDescribesNestedRecords() {
        resposta = "{\"choices\":[{\"message\":{\"content\":\"{\\\"drafts\\\":[]}\"}}],\"usage\":{}}";

        // A resposta vazia faz o metodo falhar, mas o que importa aqui e o pedido enviado.
        try {
            provider.structured(request(), "briefing", PostDraftBatch.class);
        } catch (AiUnavailableException esperado) {
            // lista vazia nao e problema deste teste
        }

        // Sem os campos internos descritos, o modelo devolveria cada item pela metade.
        assertThat(corpoRecebido.get())
                .contains("drafts")
                .contains("em que cada item tem")
                .contains("title")
                .contains("slideCount");
    }
}
