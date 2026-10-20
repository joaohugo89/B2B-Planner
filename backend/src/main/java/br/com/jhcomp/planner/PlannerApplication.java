package br.com.jhcomp.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Quem autentica aqui é o AuthController, com usuários do banco e tokens JWT.
 *
 * <p>Sem a exclusão abaixo, o Spring Boot ainda criaria um usuário "user" em memória e
 * imprimiria no log um "Using generated security password: ..." a cada boot. Essa senha
 * nunca é aceita por nenhuma tela do planner — ela só confundia quem lia o log,
 * parecendo uma credencial de verdade.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlannerApplication.class, args);
    }
}
