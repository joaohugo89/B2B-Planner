package br.com.jhcomp.planner.instagram;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstagramAccountRepository extends JpaRepository<InstagramAccount, Long> {

    Optional<InstagramAccount> findFirstByOrderByIdAsc();
}
