package br.com.jhcomp.planner.instagram;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava o bug que impediu a primeira conexão com o Instagram.
 *
 * <p>Os parâmetros da Meta são montados à mão e já vão codificados. Passando a String crua ao
 * RestClient, ele a trata como template e codifica de novo — o {@code %3A} do https vira
 * {@code %253A}, e a Meta responde "redirect_uri isn't an absolute URI". O teste sobe um
 * servidor local e confere o que chega do outro lado.</p>
 */
class InstagramUrlEncodingTest {

    private static final String REDIRECT = "https://planner.exemplo.com/instagram/callback";

    private HttpServer server;
    private final AtomicReference<String> queryRecebida = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/access_token", exchange -> {
            queryRecebida.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"access_token\":\"t\"}".getBytes(StandardCharsets.UTF_8);
            // A Meta responde o /oauth/access_token assim, por heranca antiga do Facebook.
            exchange.getResponseHeaders().add("Content-Type", "text/javascript;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("o redirect_uri chega inteiro, sem a codificacao dobrada")
    void redirectUriArrivesDecodable() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/access_token"
                + "?redirect_uri=" + URLEncoder.encode(REDIRECT, StandardCharsets.UTF_8);

        // URI.create é o que o InstagramService usa; passar a String crua quebraria o teste.
        RestClient.create().get().uri(URI.create(url)).retrieve().body(String.class);

        String query = queryRecebida.get();
        assertThat(query).contains("redirect_uri=https%3A%2F%2Fplanner.exemplo.com%2Finstagram%2Fcallback");
        // "%253A" seria o sinal da segunda codificação, que a Meta recusa.
        assertThat(query).doesNotContain("%25");
    }

    @Test
    @DisplayName("passar a String crua dobraria a codificacao — o erro que a Meta recusou")
    void rawStringWouldDoubleEncode() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/access_token"
                + "?redirect_uri=" + URLEncoder.encode(REDIRECT, StandardCharsets.UTF_8);

        RestClient.create().get().uri(url).retrieve().body(String.class);

        // Documenta o comportamento que causou o bug, para ninguém "simplificar" de volta.
        assertThat(queryRecebida.get()).contains("%25");
    }

    @Test
    @DisplayName("resposta em text/javascript e lida como JSON, como a Meta devolve")
    void textJavascriptIsParsed() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/access_token";

        // Pedir JsonNode direto falharia: nao ha conversor para text/javascript.
        byte[] bruto = RestClient.create().get().uri(URI.create(url)).retrieve().body(byte[].class);

        assertThat(bruto).isNotNull();
        assertThat(new String(bruto, StandardCharsets.UTF_8)).contains("access_token");
    }

    @Test
    @DisplayName("campos aninhados com chaves nao quebram a montagem da URI")
    void nestedFieldsAreAccepted() {
        // Era assim que a Graph API respondia com "Illegal character in query": as chaves da
        // sintaxe de campos aninhados nao sao aceitas por URI.create sem codificacao.
        String cru = "https://graph.facebook.com/v21.0/123?fields=instagram_business_account{id,username}";

        assertThatThrownBy(() -> URI.create(cru)).isInstanceOf(IllegalArgumentException.class);

        String seguro = cru.replace("{", "%7B").replace("}", "%7D");
        assertThat(URI.create(seguro).getRawQuery()).contains("%7Bid,username%7D");
    }
}
