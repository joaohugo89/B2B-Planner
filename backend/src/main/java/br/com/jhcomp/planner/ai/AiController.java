package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.ChatRequest;
import br.com.jhcomp.planner.ai.AiDtos.ChatResponse;
import br.com.jhcomp.planner.ai.AiDtos.DraftRequest;
import br.com.jhcomp.planner.ai.AiDtos.DraftResponse;
import br.com.jhcomp.planner.ai.AiDtos.UsageReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "Assistente de Conteúdo", description = "Conversa com o Claude para criar posts")
public class AiController {

    public record AiStatus(boolean enabled, String model) {
    }

    private final AiAssistantService service;
    private final AiUsageService usage;

    public AiController(AiAssistantService service, AiUsageService usage) {
        this.service = service;
        this.usage = usage;
    }

    /** Público: a UI usa isto para esconder o chat quando não há chave configurada. */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(service.isEnabled(), service.model());
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(service.chat(request.messages()));
    }

    @PostMapping("/draft")
    public DraftResponse draft(@Valid @RequestBody DraftRequest request) {
        return service.draft(request.brief(), request.audience(), request.format());
    }

    /** Consumo de tokens: total do mês e as últimas chamadas. Exige token de acesso. */
    @GetMapping("/usage")
    public UsageReport usage() {
        return usage.report();
    }
}
