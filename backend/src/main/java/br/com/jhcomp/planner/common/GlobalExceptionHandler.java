package br.com.jhcomp.planner.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Todo handler aqui também chama {@link ErrorLog#registrar}, que grava o stack trace
 * completo em logs/erros-AAAA-MM-DD.log. O que vai para o console é só o que já ia antes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        ErrorLog.registrar(404, "NOT_FOUND", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        ErrorLog.registrar(404, "NOT_FOUND", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", "Recurso não encontrado: " + ex.getResourcePath(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        ErrorLog.registrar(400, "VALIDATION_ERROR", ex);
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_ERROR", "Payload inválido.", details));
    }

    /** Enum ou número inválido em path variable / query param (ex.: ?status=INVALIDO). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorLog.registrar(400, "VALIDATION_ERROR", ex);
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_ERROR", "Valor inválido para o parâmetro '" + ex.getName() + "'.",
                        List.of(ex.getName() + ": " + ex.getValue())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        ErrorLog.registrar(400, "VALIDATION_ERROR", ex);
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_ERROR",
                        "Parâmetro obrigatório ausente: " + ex.getParameterName(), List.of()));
    }

    /** JSON malformado ou enum desconhecido no corpo da requisição. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorLog.registrar(400, "MALFORMED_REQUEST", ex);
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "MALFORMED_REQUEST", "Corpo da requisição inválido.", List.of()));
    }

    /** Violação de unicidade (ex.: código de post repetido) ou de chave estrangeira. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        ErrorLog.registrar(409, "CONFLICT", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "CONFLICT", "Registro conflita com um já existente.", List.of()));
    }

    @ExceptionHandler(br.com.jhcomp.planner.ai.AiUnavailableException.class)
    public ResponseEntity<ApiError> handleAi(br.com.jhcomp.planner.ai.AiUnavailableException ex) {
        ErrorLog.registrar(503, "AI_UNAVAILABLE", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "AI_UNAVAILABLE", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(br.com.jhcomp.planner.instagram.InstagramException.class)
    public ResponseEntity<ApiError> handleInstagram(br.com.jhcomp.planner.instagram.InstagramException ex) {
        log.warn("Falha na integração com o Instagram", ex);
        ErrorLog.registrar(502, "INSTAGRAM_ERROR", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of(502, "INSTAGRAM_ERROR", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        ErrorLog.registrar(401, "UNAUTHORIZED", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "UNAUTHORIZED", "Credenciais inválidas.", List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        ErrorLog.registrar(403, "FORBIDDEN", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", "Acesso negado.", List.of()));
    }

    /** Erros que o próprio Spring MVC já classificou (405, 415, 406...). */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleErrorResponse(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ErrorLog.registrar(status.value(), status.name(), ex);
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.name(), status.getReasonPhrase(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        ErrorLog.registrar(500, "INTERNAL_ERROR", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", "Erro interno do servidor.", List.of()));
    }
}
