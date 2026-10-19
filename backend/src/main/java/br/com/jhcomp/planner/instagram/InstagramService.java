package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.settings.SettingsService;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.repository.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conexão e publicação no Instagram pela Graph API da Meta.
 *
 * Fluxo: o usuário autoriza no Facebook → trocamos o code por um token de longa duração →
 * descobrimos a Página e a conta Instagram Business ligada a ela → publicamos usando o token da Página.
 */
@Service
public class InstagramService {

    private static final Logger log = LoggerFactory.getLogger(InstagramService.class);

    /** Permissões mínimas para listar a conta e publicar. */
    private static final String SCOPES =
            "instagram_basic,instagram_content_publish,pages_show_list,pages_read_engagement,business_management";

    private final InstagramProperties properties;
    private final InstagramAccountRepository accounts;
    private final PostRepository posts;
    private final SettingsService settings;
    private final RestClient http = RestClient.create();

    public InstagramService(InstagramProperties properties,
                            InstagramAccountRepository accounts,
                            PostRepository posts,
                            SettingsService settings) {
        this.properties = properties;
        this.accounts = accounts;
        this.posts = posts;
        this.settings = settings;
    }

    // As chaves vêm do painel do usuário; sem valor salvo, vale a variável de ambiente.
    public String appId() {
        return settings.valueOr(SettingsService.INSTAGRAM_APP_ID, properties.getAppId());
    }

    public String appSecret() {
        return settings.valueOr(SettingsService.INSTAGRAM_APP_SECRET, properties.getAppSecret());
    }

    public String redirectUri() {
        return settings.valueOr(SettingsService.INSTAGRAM_REDIRECT_URI, properties.getRedirectUri());
    }

    public String publicBaseUrl() {
        return settings.valueOr(SettingsService.INSTAGRAM_PUBLIC_BASE_URL, properties.getPublicBaseUrl());
    }

    @Transactional(readOnly = true)
    public Optional<InstagramAccount> connectedAccount() {
        return accounts.findFirstByOrderByIdAsc();
    }

    /**
     * Pergunta à Meta se o token ainda vale, em vez de só olhar se há conta salva.
     * Devolve o nome de usuário quando o token responde; vazio quando precisa reconectar.
     */
    public Optional<String> checkToken() {
        return connectedAccount().flatMap(conta -> {
            try {
                JsonNode resposta = get(properties.graphUrl("/" + conta.getIgUserId())
                        + "?fields=username&access_token=" + enc(conta.getAccessToken()));
                String username = resposta.path("username").asText(null);
                return Optional.ofNullable(username);
            } catch (RuntimeException ex) {
                log.warn("Token do Instagram não respondeu: {}", ex.getMessage());
                return Optional.empty();
            }
        });
    }

    public boolean isConfigured() {
        return !appId().isBlank() && !appSecret().isBlank();
    }

    public boolean hasPublicBaseUrl() {
        String url = publicBaseUrl();
        return url != null && !url.isBlank() && !url.contains("localhost") && !url.contains("127.0.0.1");
    }

    /** URL do diálogo de autorização da Meta, para onde o botão "Conectar" manda o usuário. */
    public String authorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder
                .fromUriString("https://www.facebook.com/" + properties.getApiVersion() + "/dialog/oauth")
                .queryParam("client_id", appId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    /** Troca o code da autorização pela conexão definitiva e guarda o token. */
    @Transactional
    public InstagramAccount connect(String code) {
        requireConfigured();

        String shortLived = get(properties.graphUrl("/oauth/access_token")
                        + "?client_id=" + enc(appId())
                        + "&client_secret=" + enc(appSecret())
                        + "&redirect_uri=" + enc(redirectUri())
                        + "&code=" + enc(code))
                .path("access_token").asText(null);

        if (shortLived == null) {
            throw new InstagramException("A Meta não devolveu um token para este código de autorização.");
        }

        JsonNode longLived = get(properties.graphUrl("/oauth/access_token")
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + enc(appId())
                + "&client_secret=" + enc(appSecret())
                + "&fb_exchange_token=" + enc(shortLived));

        String userToken = longLived.path("access_token").asText(shortLived);
        long expiresIn = longLived.path("expires_in").asLong(0);

        JsonNode pages = get(properties.graphUrl("/me/accounts")
                + "?fields=id,name,access_token&access_token=" + enc(userToken));

        for (JsonNode page : pages.path("data")) {
            String pageId = page.path("id").asText();
            String pageToken = page.path("access_token").asText(userToken);

            JsonNode linked = get(properties.graphUrl("/" + pageId)
                    + "?fields=instagram_business_account{id,username}&access_token=" + enc(pageToken));
            JsonNode ig = linked.path("instagram_business_account");

            if (!ig.path("id").asText("").isBlank()) {
                InstagramAccount account = accounts.findFirstByOrderByIdAsc()
                        .orElseGet(() -> new InstagramAccount(ig.path("id").asText(), pageToken));

                account.setIgUserId(ig.path("id").asText());
                account.setUsername(ig.path("username").asText(null));
                account.setPageId(pageId);
                account.setPageName(page.path("name").asText(null));
                account.setAccessToken(pageToken);
                account.setTokenExpires(expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);

                return accounts.save(account);
            }
        }

        throw new InstagramException(
                "Nenhuma conta Instagram Profissional ligada às suas Páginas do Facebook foi encontrada. "
                        + "Confira se a conta é Comercial/Criador e está vinculada a uma Página.");
    }

    @Transactional
    public void disconnect() {
        accounts.findFirstByOrderByIdAsc().ifPresent(accounts::delete);
    }

    /** Confere os limites do Instagram para um post, sem publicar. */
    @Transactional(readOnly = true)
    public InstagramRules.Check checkPost(Long postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new InstagramException("Post não encontrado: " + postId));

        int imagens = post.getSlides().isEmpty()
                ? (post.getCoverImageUrl() == null ? 0 : 1)
                : post.getSlides().size();

        return InstagramRules.check(buildCaption(post), imagens);
    }

    /**
     * Publica o post no Instagram: uma imagem só, ou carrossel quando há mais de um slide.
     * As artes precisam estar acessíveis publicamente — a Meta baixa cada imagem da URL.
     */
    @Transactional
    public Post publish(Long postId) {
        InstagramAccount account = connectedAccount()
                .orElseThrow(() -> new InstagramException("Conecte uma conta do Instagram antes de publicar."));

        if (!hasPublicBaseUrl()) {
            throw new InstagramException(
                    "Defina INSTAGRAM_PUBLIC_BASE_URL com uma URL pública (ngrok ou domínio): "
                            + "a Meta precisa baixar as artes, e localhost não é acessível para ela.");
        }

        Post post = posts.findById(postId)
                .orElseThrow(() -> new InstagramException("Post não encontrado: " + postId));

        if (post.getInstagramMediaId() != null) {
            throw new InstagramException("Este post já foi publicado no Instagram.");
        }

        List<String> images = post.getSlides().isEmpty()
                ? (post.getCoverImageUrl() == null ? List.of() : List.of(post.getCoverImageUrl()))
                : post.getSlides();

        String caption = buildCaption(post);

        // Confere os limites da Meta aqui: erro claro em vez de recusa genérica da API.
        InstagramRules.Check check = InstagramRules.check(caption, images.size());
        if (!check.ok()) {
            throw new InstagramException(String.join(" ", check.problems()));
        }

        String base = properties.graphUrl("/" + account.getIgUserId());
        String token = account.getAccessToken();

        String creationId;
        if (images.size() == 1) {
            creationId = post(base + "/media"
                    + "?image_url=" + enc(publicUrl(images.get(0)))
                    + "&caption=" + enc(caption)
                    + "&access_token=" + enc(token)).path("id").asText();
        } else {
            List<String> children = new ArrayList<>();
            for (String image : images) {
                children.add(post(base + "/media"
                        + "?image_url=" + enc(publicUrl(image))
                        + "&is_carousel_item=true"
                        + "&access_token=" + enc(token)).path("id").asText());
            }
            creationId = post(base + "/media"
                    + "?media_type=CAROUSEL"
                    + "&children=" + enc(String.join(",", children))
                    + "&caption=" + enc(caption)
                    + "&access_token=" + enc(token)).path("id").asText();
        }

        String mediaId = post(base + "/media_publish"
                + "?creation_id=" + enc(creationId)
                + "&access_token=" + enc(token)).path("id").asText();

        post.setInstagramMediaId(mediaId);
        post.setPublishedAt(Instant.now());
        post.setStatus(PostStatus.PUBLICADO);

        log.info("Post {} publicado no Instagram como mídia {}", post.getCode(), mediaId);
        return posts.save(post);
    }

    /** Legenda final: texto do post seguido das hashtags. */
    private String buildCaption(Post post) {
        String body = post.getCaption() == null ? post.getSummary() : post.getCaption();
        String tags = post.getHashtags();
        return java.util.stream.Stream.of(body, tags)
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String publicUrl(String path) {
        String base = publicBaseUrl().replaceAll("/+$", "");
        return path.startsWith("http") ? path : base + path;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new InstagramException(
                    "Integração não configurada: defina INSTAGRAM_APP_ID e INSTAGRAM_APP_SECRET no backend.");
        }
    }

    private JsonNode get(String url) {
        return comRetry(() -> check(http.get().uri(url).retrieve().body(JsonNode.class)));
    }

    /**
     * Repete a chamada com espera crescente (1s, 2s, 4s) quando a falha é passageira —
     * limite de requisições da Meta ou instabilidade de rede. Erro de validação não repete.
     */
    private JsonNode comRetry(java.util.function.Supplier<JsonNode> chamada) {
        RuntimeException ultima = null;

        for (int tentativa = 0; tentativa < 3; tentativa++) {
            if (tentativa > 0) {
                long espera = (long) Math.pow(2, tentativa - 1) * 1000L;
                log.info("Nova tentativa na API da Meta em {}ms (tentativa {}/3)", espera, tentativa + 1);
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException interrupcao) {
                    Thread.currentThread().interrupt();
                    throw new InstagramException("Publicação interrompida.", interrupcao);
                }
            }

            try {
                return chamada.get();
            } catch (RestClientException ex) {
                ultima = new InstagramException("Falha ao falar com a API da Meta: " + ex.getMessage(), ex);
            } catch (InstagramException ex) {
                if (!ehPassageiro(ex.getMessage())) {
                    throw ex;   // erro de conteúdo ou permissão: repetir não ajuda
                }
                ultima = ex;
            }
        }

        throw ultima == null ? new InstagramException("Falha desconhecida na API da Meta.") : ultima;
    }

    /** Mensagens da Meta que indicam limite de uso ou instabilidade momentânea. */
    private boolean ehPassageiro(String mensagem) {
        if (mensagem == null) {
            return false;
        }
        String m = mensagem.toLowerCase(java.util.Locale.ROOT);
        return m.contains("rate limit") || m.contains("request limit") || m.contains("too many")
                || m.contains("temporarily") || m.contains("try again") || m.contains("timeout");
    }

    private JsonNode post(String url) {
        return comRetry(() -> check(http.post().uri(url).retrieve().body(JsonNode.class)));
    }

    /** A Graph API devolve 200 com um objeto "error" em várias falhas. */
    private JsonNode check(JsonNode body) {
        if (body == null) {
            throw new InstagramException("A Meta devolveu uma resposta vazia.");
        }
        JsonNode error = body.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new InstagramException("Meta: " + error.path("message").asText("erro desconhecido"));
        }
        return body;
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
