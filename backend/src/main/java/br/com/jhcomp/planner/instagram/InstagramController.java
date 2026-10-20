package br.com.jhcomp.planner.instagram;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/instagram")
@Tag(name = "Instagram", description = "Conexão com a conta e publicação dos posts")
public class InstagramController {

    public record ConnectionStatus(
            boolean configured,
            boolean publicUrlReady,
            boolean connected,
            String username,
            String pageName,
            Instant tokenExpires,
            /** null quando não foi verificado; true/false com o resultado do teste na Meta. */
            Boolean tokenValid) {
    }

    /** Resultado da checagem dos limites do Instagram para um post. */
    public record ContentCheck(
            boolean ok, int captionLength, int maxCaption, int hashtags, int maxHashtags,
            int mentions, int images, java.util.List<String> problems) {
    }

    public record AuthorizationUrl(String url, String state) {
    }

    public record ConnectRequest(@NotBlank String code, @NotBlank String state) {
    }

    private final InstagramService service;
    private final InstagramPublishService publishing;
    private final OAuthStateStore states;

    public InstagramController(InstagramService service, InstagramPublishService publishing,
                               OAuthStateStore states) {
        this.service = service;
        this.publishing = publishing;
        this.states = states;
    }

    /** Público: a UI usa para saber se mostra "Conectar", "Publicar" ou o aviso de configuração. */
    @GetMapping("/status")
    public ConnectionStatus status() {
        return service.connectedAccount()
                .map(account -> new ConnectionStatus(
                        service.isConfigured(),
                        service.hasPublicBaseUrl(),
                        true,
                        account.getUsername(),
                        account.getPageName(),
                        account.getTokenExpires(),
                        null))
                .orElseGet(() -> new ConnectionStatus(
                        service.isConfigured(), service.hasPublicBaseUrl(), false, null, null, null, null));
    }

    /** Pergunta à Meta se o token ainda responde — o "health check" da conexão. */
    @GetMapping("/health")
    public ConnectionStatus health() {
        ConnectionStatus base = status();
        if (!base.connected()) {
            return base;
        }
        String username = service.checkToken().orElse(null);
        return new ConnectionStatus(
                base.configured(), base.publicUrlReady(), true,
                username == null ? base.username() : username,
                base.pageName(), base.tokenExpires(), username != null);
    }

    /** Confere os limites do Instagram para o post, sem publicar nada. */
    @GetMapping("/check/{postId}")
    public ContentCheck check(@PathVariable Long postId) {
        InstagramRules.Check resultado = service.checkPost(postId);
        return new ContentCheck(
                resultado.ok(),
                resultado.captionLength(), InstagramRules.MAX_CAPTION,
                resultado.hashtags(), InstagramRules.MAX_HASHTAGS,
                resultado.mentions(), resultado.images(), resultado.problems());
    }

    @GetMapping("/authorize-url")
    public AuthorizationUrl authorizeUrl() {
        String state = states.issue();
        return new AuthorizationUrl(service.authorizationUrl(state), state);
    }

    @PostMapping("/connect")
    public ConnectionStatus connect(@Valid @RequestBody ConnectRequest request) {
        // O state tem de ser um dos emitidos aqui: sem isso, um code de fora conectaria outra conta.
        states.consume(request.state());
        service.connect(request.code());
        return status();
    }

    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect() {
        service.disconnect();
        return ResponseEntity.noContent().build();
    }

    /**
     * Agenda a publicação e responde 202 na hora: um carrossel leva dezenas de segundos
     * conversando com a Meta. A UI acompanha pelo endpoint de status.
     */
    @PostMapping("/publish/{postId}")
    public ResponseEntity<InstagramPublishService.PublishStatus> publish(@PathVariable Long postId) {
        return ResponseEntity.accepted().body(publishing.requestPublish(postId));
    }

    /** Andamento da publicação em segundo plano, para a UI mostrar "Publicando…" ou o erro. */
    @GetMapping("/publish/{postId}/status")
    public InstagramPublishService.PublishStatus publishStatus(@PathVariable Long postId) {
        return publishing.status(postId);
    }
}
