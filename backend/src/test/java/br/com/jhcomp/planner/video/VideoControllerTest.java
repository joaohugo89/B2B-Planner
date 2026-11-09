package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.config.SecurityConfig;
import br.com.jhcomp.planner.security.JwtAuthenticationFilter;
import br.com.jhcomp.planner.security.JwtService;
import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato que mais importa aqui é o de acesso: vídeo de rosto e voz não pode ter nenhuma
 * rota aberta, nem de leitura — ao contrário das artes do feed.
 */
@WebMvcTest(VideoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private VideoService service;

    @MockitoBean
    private VideoPipeline pipeline;

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

    @Test
    @DisplayName("sem login, nem a lista nem os arquivos nem os jobs respondem")
    void tudoExigeLogin() throws Exception {
        mockMvc.perform(get("/api/video/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/video/projects/1/files/FINAL")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/video/engines")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/video/projects/1/jobs")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"step\":\"RENDER\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service, pipeline);
    }

    @Test
    @DisplayName("logado, a lista responde e não expõe o nome dos arquivos no disco")
    void listaLogado() throws Exception {
        VideoProject project = new VideoProject();
        project.setTitle("Limpeza de PC");
        project.replace(VideoFile.BASE, "base-segredo.mp4");
        when(service.list(null)).thenReturn(List.of(project));

        mockMvc.perform(get("/api/video/projects").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Limpeza de PC"))
                .andExpect(jsonPath("$[0].hasBase").value(true))
                .andExpect(jsonPath("$[0].hasFinal").value(false))
                .andExpect(jsonPath("$[0].baseVideo").doesNotExist());
    }

    @Test
    @DisplayName("etapa ou tipo de arquivo que não existe responde 400, não 500")
    void valoresInvalidos() throws Exception {
        mockMvc.perform(post("/api/video/projects/1/jobs").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"step\":\"INVENTADA\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/video/projects/1/files/SEGREDO").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("recusa da regra de negócio chega como 400 com a explicação")
    void etapaSemPreRequisito() throws Exception {
        when(pipeline.submit(1L, VideoJob.Step.RENDER))
                .thenThrow(new IllegalArgumentException("Envie o vídeo-base antes de renderizar."));

        mockMvc.perform(post("/api/video/projects/1/jobs").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"step\":\"RENDER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Envie o vídeo-base antes de renderizar."));
    }
}
