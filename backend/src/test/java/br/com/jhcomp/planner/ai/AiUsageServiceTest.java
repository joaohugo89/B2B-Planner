package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.UsageReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiUsageServiceTest {

    private AiUsageService service(Path logFile, AiUsageRepository repository) {
        AiProperties properties = new AiProperties();
        properties.setUsageLog(logFile.toString());
        return new AiUsageService(repository, new ObjectMapper(), properties);
    }

    @Test
    @DisplayName("cada chamada vira uma linha JSON no arquivo e uma linha no banco")
    void writesJsonLineAndRow(@TempDir Path dir) throws IOException {
        Path logFile = dir.resolve("logs/api_usage.log");
        AiUsageRepository repository = mock(AiUsageRepository.class);

        AiUsageService service = service(logFile, repository);
        service.record("draft", "claude-opus-5-5", 1200, 340, "medium");
        service.record("chat", "claude-opus-5-5", 80, 15, "high");

        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(2);

        JsonNode first = new ObjectMapper().readTree(lines.get(0));
        assertThat(first.get("operation").asText()).isEqualTo("draft");
        assertThat(first.get("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(first.get("inputTokens").asLong()).isEqualTo(1200);
        assertThat(first.get("outputTokens").asLong()).isEqualTo(340);
        assertThat(first.get("effort").asText()).isEqualTo("medium");
        assertThat(Instant.parse(first.get("at").asText())).isNotNull();

        verify(repository, org.mockito.Mockito.times(2)).save(any(AiUsageRecord.class));
    }

    @Test
    @DisplayName("uma falha no banco nao derruba a resposta ao usuario")
    void databaseFailureIsSwallowed(@TempDir Path dir) throws IOException {
        Path logFile = dir.resolve("api_usage.log");
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.save(any(AiUsageRecord.class))).thenThrow(new RuntimeException("banco fora"));

        service(logFile, repository).record("chat", "claude-opus-5-5", 5, 6, "low");

        assertThat(Files.readAllLines(logFile)).hasSize(1);
    }

    @Test
    @DisplayName("o relatorio soma os tokens do mes e lista as ultimas chamadas")
    void reportSumsMonth(@TempDir Path dir) {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.totalsSince(any())).thenReturn(List.<Object[]>of(new Object[]{1200L, 340L, 3L}));
        when(repository.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of(new AiUsageRecord("draft", "claude-opus-5-5", 1000, 300, "medium")));

        UsageReport report = service(dir.resolve("api_usage.log"), repository).report();

        assertThat(report.calls()).isEqualTo(3);
        assertThat(report.inputTokens()).isEqualTo(1200);
        assertThat(report.outputTokens()).isEqualTo(340);
        assertThat(report.totalTokens()).isEqualTo(1540);
        assertThat(report.recent()).hasSize(1);
        assertThat(report.recent().get(0).operation()).isEqualTo("draft");
    }

    @Test
    @DisplayName("sem nenhuma chamada o mes volta zerado")
    void emptyMonth(@TempDir Path dir) {
        AiUsageRepository repository = mock(AiUsageRepository.class);
        when(repository.totalsSince(any())).thenReturn(List.of());
        when(repository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());

        UsageReport report = service(dir.resolve("api_usage.log"), repository).report();

        assertThat(report.calls()).isZero();
        assertThat(report.totalTokens()).isZero();
        assertThat(report.recent()).isEmpty();
    }
}
