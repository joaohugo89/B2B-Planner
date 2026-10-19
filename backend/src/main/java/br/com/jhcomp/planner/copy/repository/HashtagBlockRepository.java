package br.com.jhcomp.planner.copy.repository;

import br.com.jhcomp.planner.copy.domain.HashtagBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HashtagBlockRepository extends JpaRepository<HashtagBlock, Long> {

    List<HashtagBlock> findAllByOrderByNameAsc();

    Optional<HashtagBlock> findByNameIgnoreCase(String name);
}
