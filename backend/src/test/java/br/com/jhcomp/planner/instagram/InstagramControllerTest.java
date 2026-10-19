package br.com.jhcomp.planner.instagram;

import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.config.SecurityConfig;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.security.JwtAuthenticationFilter;
import br.com.jhcomp.planner.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP da publicacao em segundo plano: 202 na hora e status publico para a UI. */
@WebMvcTest(InstagramController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class InstagramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private InstagramService service;

    @MockBean
    private InstagramPublishService publishing;

    private String bearer() {
        return "Bearer " + jwtService.generateAccessToken("admin@jhcomp.com.br", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("POST /api/instagram/publish/{id} responde 202 sem esperar a Meta")
    void publishIsAccepted() throws Exception {
        when(publishing.requestPublish(10L)).thenReturn(new InstagramPublishService.PublishStatus(
                10L, "07", "PUBLICANDO", true, false, null, null, null,
                Instant.parse("2026-10-19T12:00:00Z"), PostStatus.PRONTO_PARA_POSTAR));

        mockMvc.perform(post("/api/instagram/publish/10").header("Authorization", bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("PUBLICANDO"))
                .andExpect(jsonPath("$.publishing").value(true));

        verify(publishing).requestPublish(10L);
    }

    @Test
    @DisplayName("Publicar sem token responde 401 e nao agenda nada")
    void publishRequiresToken() throws Exception {
        mockMvc.perform(post("/api/instagram/publish/10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(publishing);
    }

    @Test
    @DisplayName("GET do status e publico e devolve o erro da ultima tentativa")
    void statusIsPublicAndCarriesError() throws Exception {
        when(publishing.status(10L)).thenReturn(new InstagramPublishService.PublishStatus(
                10L, "07", "ERRO", false, false,
                "Conecte uma conta do Instagram antes de publicar.", null, null,
                Instant.parse("2026-10-19T12:00:00Z"), PostStatus.PRONTO_PARA_POSTAR));

        mockMvc.perform(get("/api/instagram/publish/10/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ERRO"))
                .andExpect(jsonPath("$.publishing").value(false))
                .andExpect(jsonPath("$.error").value("Conecte uma conta do Instagram antes de publicar."))
                .andExpect(jsonPath("$.postStatus").value("PRONTO_PARA_POSTAR"));
    }
}
