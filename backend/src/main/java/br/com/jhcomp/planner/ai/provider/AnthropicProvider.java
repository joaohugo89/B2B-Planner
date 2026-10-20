package br.com.jhcomp.planner.ai.provider;

import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.ai.AiUnavailableException;
import br.com.jhcomp.planner.ai.AnthropicClients;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Claude pelo SDK oficial, com structured output nativo para o rascunho. */
@Component
public class AnthropicProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private final AnthropicClients clients;

    public AnthropicProvider(AnthropicClients clients) {
        this.clients = clients;
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public AiReply<String> chat(AiRequest request, List<ChatMessage> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxTokens())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(request.effort())).build())
                .system(request.system());

        for (ChatMessage message : history) {
            if ("assistant".equalsIgnoreCase(message.role())) {
                builder.addAssistantMessage(message.content());
            } else {
                builder.addUserMessage(message.content());
            }
        }

        Message response = call(() -> clients.forKey(request.apiKey()).messages().create(builder.build()));

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .trim();

        return new AiReply<>(text, response.usage().inputTokens(), response.usage().outputTokens());
    }

    @Override
    public <T> AiReply<T> structured(AiRequest request, String instruction, Class<T> type) {
        StructuredMessageCreateParams<T> params = withEffort(MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxTokens())
                .system(request.system())
                .outputConfig(type)
                .addUserMessage(instruction)
                .build(), request.effort());

        StructuredMessage<T> response = call(() -> clients.forKey(request.apiKey()).messages().create(params));

        T content = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .findFirst()
                .orElseThrow(() -> new AiUnavailableException("O modelo não devolveu um rascunho utilizável."));

        return new AiReply<>(content, response.usage().inputTokens(), response.usage().outputTokens());
    }

    /**
     * outputConfig(type) monta um output_config só com o formato. Aqui o esforço escolhido
     * no painel é somado a esse formato, sem perder o structured output.
     */
    private <T> StructuredMessageCreateParams<T> withEffort(StructuredMessageCreateParams<T> params, String effort) {
        MessageCreateParams raw = params.rawParams();
        OutputConfig.Builder config = OutputConfig.builder().effort(OutputConfig.Effort.of(effort));
        raw.outputConfig().flatMap(OutputConfig::format).ifPresent(config::format);

        return new StructuredMessageCreateParams<>(
                params.outputType(),
                raw.toBuilder().outputConfig(config.build()).build());
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (AnthropicServiceException ex) {
            log.warn("Falha na chamada à API da Anthropic", ex);
            throw new AiUnavailableException("A API do Claude recusou a requisição: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada ao falar com o Claude", ex);
            throw new AiUnavailableException("Não foi possível falar com o assistente agora.", ex);
        }
    }
}
