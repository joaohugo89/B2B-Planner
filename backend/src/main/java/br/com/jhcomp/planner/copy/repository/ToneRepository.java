package br.com.jhcomp.planner.copy.repository;

import br.com.jhcomp.planner.copy.domain.Tone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToneRepository extends JpaRepository<Tone, Long> {

    List<Tone> findAllByOrderByLabelAsc();

    Optional<Tone> findByCode(String code);
}
