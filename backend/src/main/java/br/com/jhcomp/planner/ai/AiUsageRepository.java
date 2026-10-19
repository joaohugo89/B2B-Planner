package br.com.jhcomp.planner.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiUsageRepository extends JpaRepository<AiUsageRecord, Long> {

    List<AiUsageRecord> findTop20ByOrderByCreatedAtDesc();

    /** Soma de tokens e número de chamadas desde uma data; devolve [entrada, saída, chamadas]. */
    @Query("""
            select coalesce(sum(u.inputTokens), 0), coalesce(sum(u.outputTokens), 0), count(u)
            from AiUsageRecord u
            where u.createdAt >= :since
            """)
    List<Object[]> totalsSince(@Param("since") Instant since);
}
