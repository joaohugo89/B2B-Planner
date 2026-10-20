package br.com.jhcomp.planner.ai.provider;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;

import java.util.List;

/**
 * Um serviço de IA com que o planner sabe conversar.
 *
 * <p>Existem duas implementações: a da Anthropic (Claude) e a do padrão OpenAI, que atende
 * tanto a OpenAI quanto Groq, OpenRouter e um Ollama rodando na própria máquina — todos
 * falam o mesmo protocolo, mudando só a URL base, o modelo e a chave.</p>
 */
public interface AiProvider {

    /** Identificador usado no painel e no banco: "anthropic" ou "openai". */
    String id();

    /** Conversa livre do assistente. */
    AiReply<String> chat(AiRequest request, List<ChatMessage> history);

    /** Pede uma resposta no formato de {@code type}, usada para montar o rascunho do post. */
    <T> AiReply<T> structured(AiRequest request, String instruction, Class<T> type);

    /** O que o modelo devolveu, com o consumo para o painel de custos. */
    record AiReply<T>(T content, long inputTokens, long outputTokens) {
    }

    /** Os parâmetros da chamada, já resolvidos a partir do painel e do ambiente. */
    record AiRequest(String apiKey, String baseUrl, String model, String effort, long maxTokens, String system) {
    }
}
