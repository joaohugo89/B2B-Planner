package br.com.jhcomp.planner.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que todo handler do GlobalExceptionHandler manda a exceção, com stack trace,
 * para o logger "br.com.jhcomp.planner.errors" — o que o logback-spring.xml grava em
 * logs/erros-AAAA-MM-DD.log.
 */
class ErrorLogTest {

    private static final String LOGGER_ERROS = "br.com.jhcomp.planner.errors";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level nivelOriginal;

    @BeforeEach
    void capturarLogger() {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(LOGGER_ERROS);
        nivelOriginal = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void soltarLogger() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(nivelOriginal);
    }

    @Test
    @DisplayName("erro não tratado vai para o log de erros como ERROR, com stack trace")
    void erroNaoTratado() {
        handler.handleGeneric(new IllegalStateException("estourou"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent evento = appender.list.get(0);
        assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
        assertThat(evento.getFormattedMessage()).contains("HTTP 500", "INTERNAL_ERROR", "estourou");
        assertThat(evento.getThrowableProxy()).isNotNull();
        assertThat(evento.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
    }

    @Test
    @DisplayName("erro de cliente (4xx) vai como WARN, também com a exceção anexada")
    void erroDeCliente() {
        handler.handleNotFound(new NotFoundException("Post", 999L));
        handler.handleDenied(new AccessDeniedException("sem permissão"));

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getThrowableProxy()).isNotNull();
        });
        assertThat(appender.list.get(0).getFormattedMessage()).contains("HTTP 404", "NOT_FOUND");
        assertThat(appender.list.get(1).getFormattedMessage()).contains("HTTP 403", "FORBIDDEN");
    }

    @Test
    @DisplayName("conflito de integridade também é registrado no arquivo")
    void conflito() {
        handler.handleConflict(new DataIntegrityViolationException("duplicate key"));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("HTTP 409", "CONFLICT");
    }

}
