package br.com.jhcomp.planner.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Grava toda exceção tratada pelo {@link GlobalExceptionHandler} em
 * logs/erros-AAAA-MM-DD.log, com stack trace completo.
 *
 * <p>O logger "br.com.jhcomp.planner.errors" está configurado em logback-spring.xml com
 * additivity=false e aponta só para o arquivo: nada disso aparece no console, que continua
 * mostrando exatamente o que mostrava antes.
 */
final class ErrorLog {

    private static final Logger log = LoggerFactory.getLogger("br.com.jhcomp.planner.errors");

    private ErrorLog() {
    }

    /** 5xx entra como ERROR; 4xx como WARN, que é o que o arquivo guarda no mínimo. */
    static void registrar(int status, String codigo, Throwable ex) {
        String msg = "HTTP {} {} em {} — {}: {}";
        Object[] args = {
                status, codigo, requisicao(),
                ex.getClass().getName(), ex.getMessage(), ex
        };
        if (status >= 500) {
            log.error(msg, args);
        }
        else {
            log.warn(msg, args);
        }
    }

    /** "POST /api/posts" quando há uma requisição no contexto; "-" fora dela. */
    private static String requisicao() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String query = req.getQueryString();
            return req.getMethod() + " " + req.getRequestURI() + (query == null ? "" : "?" + query);
        }
        return "-";
    }
}
