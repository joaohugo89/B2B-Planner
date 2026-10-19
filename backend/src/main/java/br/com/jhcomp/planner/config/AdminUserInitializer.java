package br.com.jhcomp.planner.config;

import br.com.jhcomp.planner.user.domain.AppUser;
import br.com.jhcomp.planner.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cria o usuário administrador inicial caso ainda não exista.
 *
 * <p>Com JHCOMP_ADMIN_PASSWORD definida, essa senha é usada (comportamento de sempre). Sem ela,
 * uma senha aleatória é gerada no primeiro boot e impressa no log uma única vez — não há como
 * recuperá-la depois, só redefinir apagando o usuário ou usando a variável.</p>
 */
@Configuration
public class AdminUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Bean
    public ApplicationRunner seedAdminUser(AppUserRepository users,
                                           PasswordEncoder encoder,
                                           @Value("${jhcomp.admin.email:admin@jhcomp.com.br}") String email,
                                           @Value("${jhcomp.admin.password:}") String password,
                                           @Value("${jhcomp.admin.name:JH.COMP Admin}") String name) {
        return args -> {
            String normalized = email.toLowerCase();
            if (users.findByEmail(normalized).isPresent()) {
                return;
            }

            boolean generated = password == null || password.isBlank();
            String effective = generated ? randomPassword() : password;
            users.save(new AppUser(normalized, encoder.encode(effective), name));

            if (generated) {
                log.warn("""

                        ====================================================================
                         Usuário administrador criado: {}
                         Senha gerada automaticamente: {}
                         Anote agora: ela não será exibida novamente. Para definir a sua,
                         apague o usuário e suba com JHCOMP_ADMIN_PASSWORD.
                        ====================================================================
                        """, normalized, effective);
            } else {
                log.info("Usuário administrador {} criado com a senha de JHCOMP_ADMIN_PASSWORD.", normalized);
            }
        };
    }

    /** 24 bytes aleatórios em Base64 url-safe: forte e fácil de copiar do log. */
    private static String randomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
