package br.com.jhcomp.planner.post.web;

import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.config.SecurityConfig;
import br.com.jhcomp.planner.post.domain.Audience;
import br.com.jhcomp.planner.post.domain.Post;
import br.com.jhcomp.planner.post.domain.PostFormat;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.service.PostService;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PlannerMetrics;
import br.com.jhcomp.planner.security.JwtAuthenticationFilter;
import br.com.jhcomp.planner.security.JwtService;
import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o contrato HTTP do PostController com a cadeia de seguranca real:
 * leitura e publica, escrita exige um access token valido.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PostService service;

    /** O filtro confere a versao do token contra o usuario; sem isso nenhum Bearer passa. */
    @MockitoBean
    private AppUserRepository users;

    @BeforeEach
    void stubUsuario() {
        AppUser user = new AppUser("admin@jhcomp.com.br", "hash", "Admin");
        user.setRole("ROLE_ADMIN");
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
    }

    private String bearer() {
        return "Bearer " + jwtService.generateAccessToken("admin@jhcomp.com.br", "ROLE_ADMIN");
    }

    private static Post post(Long id, String code, String title, PostStatus status) {
        Post post = new Post(code, title);
        post.setStatus(status);
        post.setAudience(Audience.HIBRIDO);
        post.setFormat(PostFormat.CARROSSEL);
        post.setSlideCount(7);
        try {
            Field idField = Post.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(post, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return post;
    }

    @Test
    @DisplayName("GET /api/posts e publico e serializa a lista")
    void listIsPublic() throws Exception {
        when(service.list(null, null, true)).thenReturn(List.of(
                post(1L, "01", "JH.Comp — Tecnologia para pessoas e empresas", PostStatus.PRONTO_PARA_POSTAR),
                post(2L, "02", "Seu computador pode estar muito mais rápido", PostStatus.AGENDADO)));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("01"))
                .andExpect(jsonPath("$[0].title").value("JH.Comp — Tecnologia para pessoas e empresas"))
                .andExpect(jsonPath("$[0].status").value("PRONTO_PARA_POSTAR"))
                .andExpect(jsonPath("$[1].audience").value("HIBRIDO"));
    }

    @Test
    @DisplayName("GET /api/posts repassa os filtros de status e audience")
    void listForwardsFilters() throws Exception {
        when(service.list(PostStatus.AGENDADO, null, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/posts").param("status", "AGENDADO"))
                .andExpect(status().isOk());

        verify(service).list(PostStatus.AGENDADO, null, true);
    }

    @Test
    @DisplayName("GET /api/posts com status invalido responde 400, nao 500")
    void listRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/posts").param("status", "INVENTADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/posts/metrics devolve o resumo do planner")
    void metricsAreExposed() throws Exception {
        when(service.metrics(true)).thenReturn(new PlannerMetrics(6, 3, 3, 0, 2, 2, 5, 1, 15500, 2380));

        mockMvc.perform(get("/api/posts/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.readyToPost").value(3))
                .andExpect(jsonPath("$.estimatedReach").value(15500));
    }

    @Test
    @DisplayName("GET /api/posts/code/{code} devolve o post do batch")
    void getByCode() throws Exception {
        when(service.getFinalizadoByCode("01"))
                .thenReturn(post(1L, "01", "JH.Comp — Tecnologia", PostStatus.PRONTO_PARA_POSTAR));

        mockMvc.perform(get("/api/posts/code/01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("01"))
                .andExpect(jsonPath("$.slideCount").value(7));
    }

    @Test
    @DisplayName("GET de codigo inexistente responde 404 com corpo ApiError")
    void getByCodeNotFound() throws Exception {
        when(service.getFinalizadoByCode("99")).thenThrow(new NotFoundException("Post", "99"));

        mockMvc.perform(get("/api/posts/code/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH de status sem token responde 401 e nao chega ao service")
    void patchStatusRequiresToken() throws Exception {
        mockMvc.perform(patch("/api/posts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLICADO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PATCH de status com token invalido responde 401")
    void patchStatusRejectsInvalidToken() throws Exception {
        mockMvc.perform(patch("/api/posts/1/status")
                        .header("Authorization", "Bearer nao-e-um-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLICADO\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PATCH de status com refresh token e recusado")
    void patchStatusRejectsRefreshToken() throws Exception {
        String refresh = jwtService.generateRefreshToken("admin@jhcomp.com.br", "ROLE_ADMIN");

        mockMvc.perform(patch("/api/posts/1/status")
                        .header("Authorization", "Bearer " + refresh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLICADO\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PATCH de status com Bearer valido altera o post")
    void patchStatusWithValidToken() throws Exception {
        when(service.changeStatus(eq(1L), any(PostStatus.class)))
                .thenReturn(post(1L, "01", "JH.Comp — Tecnologia", PostStatus.PUBLICADO));

        mockMvc.perform(patch("/api/posts/1/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLICADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"));

        verify(service).changeStatus(1L, PostStatus.PUBLICADO);
    }

    @Test
    @DisplayName("access token de antes do logout e recusado")
    void patchStatusRejectsRevokedAccessToken() throws Exception {
        // "Sair de todas as sessoes" subiu a versao do usuario para 1; o token ainda diz 0.
        AppUser revogado = new AppUser("admin@jhcomp.com.br", "hash", "Admin");
        revogado.setRole("ROLE_ADMIN");
        revogado.revokeTokens();
        when(users.findByEmail(any())).thenReturn(Optional.of(revogado));

        mockMvc.perform(patch("/api/posts/1/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLICADO\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("com Bearer valido, a lista vem completa: rascunhos inclusive")
    void listWithTokenIncludesDrafts() throws Exception {
        when(service.list(null, null, false)).thenReturn(List.of(
                post(1L, "01", "Rascunho em andamento", PostStatus.RASCUNHO),
                post(2L, "02", "Pronto para publicar", PostStatus.PRONTO_PARA_POSTAR)));

        mockMvc.perform(get("/api/posts").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(service).list(null, null, false);
    }

    @Test
    @DisplayName("sem login, pedir um rascunho pelo id responde 404")
    void draftIsInvisibleToVisitor() throws Exception {
        when(service.getFinalizadoById(7L)).thenThrow(new NotFoundException("Post", 7L));

        mockMvc.perform(get("/api/posts/7"))
                .andExpect(status().isNotFound());

        verify(service, never()).getById(7L);
    }

    @Test
    @DisplayName("com Bearer valido, o mesmo rascunho abre normalmente")
    void draftIsVisibleWithToken() throws Exception {
        when(service.getById(7L)).thenReturn(post(7L, "07", "Rascunho em andamento", PostStatus.RASCUNHO));

        mockMvc.perform(get("/api/posts/7").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
    }
}
