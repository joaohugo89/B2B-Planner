package br.com.jhcomp.planner.instagram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O state do OAuth é o que impede alguém de conectar a conta de Instagram dele no painel. */
class OAuthStateStoreTest {

    private final OAuthStateStore store = new OAuthStateStore();

    @Test
    @DisplayName("o state emitido aqui e aceito uma unica vez")
    void issuedStateIsAcceptedOnce() {
        String state = store.issue();

        assertThatCode(() -> store.consume(state)).doesNotThrowAnyException();
        assertThatThrownBy(() -> store.consume(state)).isInstanceOf(InstagramException.class);
    }

    @Test
    @DisplayName("state inventado, vazio ou nulo e recusado")
    void unknownStateIsRejected() {
        assertThatThrownBy(() -> store.consume("inventado")).isInstanceOf(InstagramException.class);
        assertThatThrownBy(() -> store.consume("")).isInstanceOf(InstagramException.class);
        assertThatThrownBy(() -> store.consume(null)).isInstanceOf(InstagramException.class);
    }

    @Test
    @DisplayName("cada pedido recebe um state diferente e imprevisivel")
    void statesAreUnique() {
        assertThat(store.issue()).isNotEqualTo(store.issue()).hasSizeGreaterThanOrEqualTo(32);
    }
}
