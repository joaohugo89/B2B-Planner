package br.com.jhcomp.planner.copy.web;

import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.config.SecurityConfig;
import br.com.jhcomp.planner.copy.domain.HashtagBlock;
import br.com.jhcomp.planner.copy.repository.HashtagBlockRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato dos blocos de hashtags: leitura publica, escrita autenticada, nome unico. */
@WebMvcTest(HashtagBlockController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class HashtagBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private HashtagBlockRepository blocks;

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

    private static HashtagBlock block(Long id, String name, String hashtags) {
        HashtagBlock block = new HashtagBlock(name, hashtags);
        try {
            Field idField = HashtagBlock.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(block, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return block;
    }

    @Test
    @DisplayName("GET /api/hashtag-blocks exige conta e conta as hashtags do bloco")
    void listRequiresToken() throws Exception {
        when(blocks.findAllByOrderByNameAsc())
                .thenReturn(List.of(block(1L, "Manutencao", "#ManutencaoPC #SSD")));

        // Sem conta, o Guia de Copy nao existe: nem a tela nem os dados dela.
        mockMvc.perform(get("/api/hashtag-blocks"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/hashtag-blocks").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Manutencao"))
                .andExpect(jsonPath("$[0].hashtags").value("#ManutencaoPC #SSD"))
                .andExpect(jsonPath("$[0].tagCount").value(2));
    }

    @Test
    @DisplayName("POST sem token responde 401 e nao toca no repositorio")
    void createRequiresToken() throws Exception {
        mockMvc.perform(post("/api/hashtag-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Local\",\"hashtags\":\"#Guarulhos\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(blocks);
    }

    @Test
    @DisplayName("POST normaliza o que o usuario digitou: virgulas viram espacos e falta o # e adicionado")
    void createNormalizesHashtags() throws Exception {
        when(blocks.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(blocks.save(any(HashtagBlock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/hashtag-blocks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Local \",\"hashtags\":\"guarulhos, #SaoPaulo  #guarulhos\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Local"))
                .andExpect(jsonPath("$.hashtags").value("#guarulhos #SaoPaulo"))
                .andExpect(jsonPath("$.tagCount").value(2));
    }

    @Test
    @DisplayName("POST com nome ja usado responde 409")
    void createRejectsDuplicateName() throws Exception {
        when(blocks.findByNameIgnoreCase("Local")).thenReturn(Optional.of(block(7L, "Local", "#Guarulhos")));

        mockMvc.perform(post("/api/hashtag-blocks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Local\",\"hashtags\":\"#SaoPaulo\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Hashtags que viram vazio depois da normalizacao sao recusadas")
    void createRejectsEmptyAfterNormalization() throws Exception {
        mockMvc.perform(post("/api/hashtag-blocks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vazio\",\"hashtags\":\"###\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("normalize junta repetidas ignorando maiusculas")
    void normalizeDedupes() {
        assertThat(HashtagBlock.normalize("#SSD ssd #nvme")).isEqualTo("#SSD #nvme");
        assertThat(HashtagBlock.normalize("   ")).isEmpty();
    }
}
