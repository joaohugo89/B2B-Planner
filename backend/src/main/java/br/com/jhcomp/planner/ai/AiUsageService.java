package br.com.jhcomp.planner.ai;

import br.com.jhcomp.planner.ai.AiDtos.UsageEntry;
import br.com.jhcomp.planner.ai.AiDtos.UsageReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de consumo do assistente: uma linha JSON em logs/api_usage.log por chamada,
 * mais uma tabela no banco para consulta pelo painel.
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsageRepository repository;
    private final ObjectMapper objectMapper;
    private final Path logFile;

    public AiUsageService(AiUsageRepository repository, ObjectMapper objectMapper, AiProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.logFile = Path.of(properties.getUsageLog());
    }

    /** Grava a chamada no arquivo e no banco. Nunca deixa uma falha de log derrubar a resposta. */
    @Transactional
    public void record(String operation, String model, long inputTokens, long outputTokens, String effort) {
        appendToFile(Instant.now(), operation, model, inputTokens, outputTokens, effort);
        try {
            repository.save(new AiUsageRecord(operation, model, inputTokens, outputTokens, effort));
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel gravar o consumo da IA no banco", ex);
        }
    }

    /** Total do mês corrente e as últimas chamadas. */
    @Transactional(readOnly = true)
    public UsageReport report() {
        Instant monthStart = LocalDate.now(ZoneId.systemDefault())
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        List<Object[]> rows = repository.totalsSince(monthStart);
        long input = 0;
        long output = 0;
        long calls = 0;
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            input = ((Number) row[0]).longValue();
            output = ((Number) row[1]).longValue();
            calls = ((Number) row[2]).longValue();
        }

        List<UsageEntry> recent = repository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(record -> new UsageEntry(
                        record.getCreatedAt(),
                        record.getOperation(),
                        record.getModel(),
                        record.getInputTokens(),
                        record.getOutputTokens(),
                        record.getEffort()))
                .toList();

        return new UsageReport(monthStart, calls, input, output, input + output, recent);
    }

    private void appendToFile(Instant at, String operation, String model,
                              long inputTokens, long outputTokens, String effort) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("at", at.toString());
        line.put("operation", operation);
        line.put("model", model);
        line.put("inputTokens", inputTokens);
        line.put("outputTokens", outputTokens);
        line.put("effort", effort);

        try {
            String json = objectMapper.writeValueAsString(line);
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            Files.writeString(logFile, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException ex) {
            log.warn("Nao foi possivel serializar o consumo da IA", ex);
        } catch (IOException ex) {
            log.warn("Nao foi possivel escrever em {}", logFile, ex);
        }
    }
}
