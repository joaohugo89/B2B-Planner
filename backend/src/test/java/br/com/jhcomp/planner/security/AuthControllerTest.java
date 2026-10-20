package br.com.jhcomp.planner.security;

import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.config.SecurityConfig;
import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de login/refresh do admin padrao, com BCrypt e JwtService reais.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class AuthControllerTest {

    private static final String EMAIL = "admin@jhcomp.com.br";
    private static final String PASSWORD = "jhcomp@2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AppUserRepository users;

    private final ObjectMapper json = new ObjectMapper();

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = new AppUser(EMAIL, passwordEncoder.encode(PASSWORD), "JH.COMP Admin");
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("login com as credenciais do admin devolve access e refresh token")
    void loginWithValidCredentials() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("JH.COMP Admin"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = json.readTree(response);
        assertThat(jwtService.parse(node.get("accessToken").asText()))
                .hasValueSatisfying(claims -> {
                    assertThat(claims.getSubject()).isEqualTo(EMAIL);
                    assertThat(jwtService.isAccessToken(claims)).isTrue();
                });
        assertThat(jwtService.parse(node.get("refreshToken").asText()))
                .hasValueSatisfying(claims -> assertThat(jwtService.isRefreshToken(claims)).isTrue());
    }

    @Test
    @DisplayName("login com senha errada responde 401")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("login de usuario inexistente responde 401")
    void loginWithUnknownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ninguem@jhcomp.com.br", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login com e-mail malformado responde 400")
    void loginWithInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nao-e-email", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("refresh com refresh token valido emite novos tokens")
    void refreshWithValidToken() throws Exception {
        String refresh = jwtService.generateRefreshToken(EMAIL, "ROLE_ADMIN");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.name").value("JH.COMP Admin"));
    }

    @Test
    @DisplayName("refresh usando um access token responde 401")
    void refreshRejectsAccessToken() throws Exception {
        String access = jwtService.generateAccessToken(EMAIL, "ROLE_ADMIN");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + access + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh com token corrompido responde 401")
    void refreshRejectsGarbage() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"lixo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revoga o refresh token e o refresh seguinte responde 401")
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = jwtService.generateRefreshToken(EMAIL, "ROLE_ADMIN", admin.getTokenVersion());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("refresh com versao antiga do token responde 401")
    void refreshRejectsStaleTokenVersion() throws Exception {
        String stale = jwtService.generateRefreshToken(EMAIL, "ROLE_ADMIN", admin.getTokenVersion());
        admin.revokeTokens();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(stale)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login apos logout emite um refresh token que volta a funcionar")
    void loginAfterLogoutIssuesWorkingToken() throws Exception {
        admin.revokeTokens();

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh = json.readTree(response).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout com token invalido responde 401")
    void logoutRejectsGarbage() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("lixo")))
                .andExpect(status().isUnauthorized());
    }
}
