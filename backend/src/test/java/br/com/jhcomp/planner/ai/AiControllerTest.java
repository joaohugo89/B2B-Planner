package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.UsageEntry;
import br.com.jhcomp.planner.ai.AiDtos.UsageReport;
import br.com.jhcomp.planner.common.GlobalExceptionHandler;
import br.com.jhcomp.planner.config.SecurityConfig;
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
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O consumo da IA é informação de custo: só sai com um access token válido. */
@WebMvcTest(AiController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jhcomp.jwt.secret=jhcomp-engineering-system-test-secret-key-with-enough-length")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AiAssistantService service;

    @MockBean
    private AiUsageService usage;

    private String bearer() {
        return "Bearer " + jwtService.generateAccessToken("admin@jhcomp.com.br", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("GET /api/ai/usage sem token responde 401")
    void usageRequiresToken() throws Exception {
        mockMvc.perform(get("/api/ai/usage"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(usage);
    }

    @Test
    @DisplayName("GET /api/ai/usage devolve o total do mes e as ultimas chamadas")
    void usageReturnsReport() throws Exception {
        when(usage.report()).thenReturn(new UsageReport(
                Instant.parse("2026-10-01T03:00:00Z"), 3, 1200, 340, 1540,
                List.of(new UsageEntry(Instant.parse("2026-10-18T14:00:00Z"),
                        "draft", "claude-opus-5-5", 1000, 300, "medium"))));

        mockMvc.perform(get("/api/ai/usage").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls").value(3))
                .andExpect(jsonPath("$.inputTokens").value(1200))
                .andExpect(jsonPath("$.totalTokens").value(1540))
                .andExpect(jsonPath("$.recent[0].operation").value("draft"))
                .andExpect(jsonPath("$.recent[0].model").value("claude-opus-5-5"));
    }
}
