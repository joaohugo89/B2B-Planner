package br.com.jhcomp.planner.copy.repository;

import br.com.jhcomp.planner.copy.domain.CopyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CopyEntryRepository extends JpaRepository<CopyEntry, Long> {

    List<CopyEntry> findAllByOrderByPostCodeAsc();

    List<CopyEntry> findByToneOrderByPostCodeAsc(String tone);

    List<CopyEntry> findByPostCodeOrderByIdAsc(String postCode);
}
