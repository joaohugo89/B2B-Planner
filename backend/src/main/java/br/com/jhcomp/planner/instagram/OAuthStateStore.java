package br.com.jhcomp.planner.instagram;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda os {@code state} emitidos para o diálogo da Meta até a volta do usuário.
 *
 * <p>Sem isso, qualquer um poderia mandar um {@code code} próprio para o /connect e
 * fazer o painel conectar a conta de Instagram dele (CSRF no fluxo OAuth). O state só
 * vale uma vez e expira em 10 minutos.</p>
 */
@Component
public class OAuthStateStore {

    private static final Duration VALIDADE = Duration.ofMinutes(10);
    private static final int MAX_PENDENTES = 100;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> emitidos = new ConcurrentHashMap<>();

    /** Cria um state novo e o registra como pendente. */
    public String issue() {
        limpar();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        emitidos.put(state, Instant.now().plus(VALIDADE));
        return state;
    }

    /** Consome o state da volta; falha se não foi emitido aqui, já foi usado ou expirou. */
    public void consume(String state) {
        limpar();
        Instant expiraEm = state == null ? null : emitidos.remove(state);
        if (expiraEm == null || expiraEm.isBefore(Instant.now())) {
            throw new InstagramException(
                    "A resposta da Meta não corresponde a um pedido de conexão feito por este painel. "
                            + "Clique em Conectar novamente.");
        }
    }

    private void limpar() {
        Instant agora = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = emitidos.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isBefore(agora)) {
                it.remove();
            }
        }
        // Rede de segurança: ninguém precisa de mais que isso em aberto ao mesmo tempo.
        if (emitidos.size() > MAX_PENDENTES) {
            emitidos.clear();
        }
    }
}
