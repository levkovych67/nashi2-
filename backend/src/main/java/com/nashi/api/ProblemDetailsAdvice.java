package com.nashi.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders every error as RFC 9457 {@code application/problem+json} with the contract's {@code code} and, for field
 * errors, {@code errors[]} ({@code docs/02-contracts.md} section 1). Titles and details are Ukrainian and never
 * contain exception text. Spring MVC's own exceptions (unknown path, wrong method or content type, bad parameter,
 * upload over the limit) keep their status and get the code for it.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String INTERNAL = "INTERNAL";

    private static final Map<String, String> TITLES = Map.of(
            VALIDATION_FAILED, "Некоректні дані",
            "NOT_FOUND", "Не знайдено",
            "PAYLOAD_TOO_LARGE", "Завеликий запит",
            "UNSUPPORTED_MEDIA_TYPE", "Непідтримуваний формат",
            "RATE_LIMITED", "Забагато запитів",
            INTERNAL, "Внутрішня помилка",
            "UNAVAILABLE", "Сервіс тимчасово недоступний");

    /** Details for errors that carry none of their own (Spring MVC exceptions, unexpected failures). */
    private static final Map<String, String> DEFAULT_DETAILS = Map.of(
            VALIDATION_FAILED, "Некоректний запит",
            "NOT_FOUND", "Нічого не знайдено за цим запитом",
            "PAYLOAD_TOO_LARGE", PayloadTooLargeException.DETAIL,
            "UNSUPPORTED_MEDIA_TYPE", "Тип вмісту запиту не підтримується",
            "RATE_LIMITED", "Забагато запитів. Спробуйте пізніше",
            INTERNAL, "Щось пішло не так. Спробуйте пізніше",
            "UNAVAILABLE", "Сервіс тимчасово недоступний. Спробуйте пізніше");

    private static final String INVALID_VALUE = "Некоректне значення";

    /**
     * RFC 9457's "no further semantics" type; the contract requires {@code type}, and {@code code} is what clients
     * switch on. Spring 7 leaves the type unset (omitted) unless it is given.
     */
    private static final URI TYPE = URI.create("about:blank");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitedException limited) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()));
        }
        ProblemDetail body = problem(ex.status(), ex.code(), ex.getMessage(), ex.errors());
        return handleExceptionInternal(ex, body, headers, ex.status(), request);
    }

    /** Anything unexpected: logged with its stack trace, answered with a generic 500 that reveals nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception for {}", request.getDescription(false), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return handleExceptionInternal(ex, problem(status, INTERNAL, DEFAULT_DETAILS.get(INTERNAL), List.of()),
                new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, violations(ex.getBindingResult()), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bean) {
                errors.addAll(violations(bean));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> errors.add(new FieldViolation(name, message(error))));
            }
        }
        return validationFailed(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getPropertyName() == null
                ? List.of()
                : List.of(new FieldViolation(ex.getPropertyName(), INVALID_VALUE));
        return validationFailed(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, List.of(new FieldViolation(ex.getParameterName(), "Обов'язковий параметр")),
                headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, List.of(new FieldViolation(ex.getRequestPartName(), "Обов'язкова частина запиту")),
                headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The parser's message quotes the input, so it never reaches the client.
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Тіло запиту не є коректним JSON", List.of());
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Every response goes through here. Bodies built above pass as they are; Spring MVC's own problem bodies
     * (English, without {@code code}) are replaced with the contract's.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        Object problem = body;
        if (!isContractProblem(body)) {
            if (statusCode.is5xxServerError()) {
                log.error("Request failed with {} for {}", statusCode.value(), request.getDescription(false), ex);
            }
            String code = codeFor(statusCode);
            problem = problem(statusCode, code, DEFAULT_DETAILS.get(code), List.of());
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationFailed(
            Exception ex, List<FieldViolation> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED,
                ValidationFailedException.detailFor(errors), errors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    static ProblemDetail problem(HttpStatusCode status, String code, String detail, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(TYPE);
        problem.setTitle(TITLES.getOrDefault(code, TITLES.get(INTERNAL)));
        problem.setProperty("code", code);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", List.copyOf(errors));
        }
        return problem;
    }

    static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "NOT_FOUND";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMITED";
            case 503 -> "UNAVAILABLE";
            // The contract has no code for 405, 406 and the like: they are bad requests too.
            default -> status.is4xxClientError() ? VALIDATION_FAILED : INTERNAL;
        };
    }

    private static boolean isContractProblem(Object body) {
        return body instanceof ProblemDetail problem
                && problem.getProperties() != null
                && problem.getProperties().containsKey("code");
    }

    private static List<FieldViolation> violations(Errors result) {
        List<FieldViolation> errors = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            errors.add(new FieldViolation(error.getField(), message(error)));
        }
        for (ObjectError error : result.getGlobalErrors()) {
            errors.add(new FieldViolation(error.getObjectName(), message(error)));
        }
        return errors;
    }

    private static String message(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? INVALID_VALUE : message;
    }

    /** The name the client sent ({@code @RequestParam("q")}), not the Java parameter name. */
    private static String parameterName(MethodParameter parameter) {
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            String name = switch (annotation) {
                case RequestParam a -> a.name().isEmpty() ? a.value() : a.name();
                case PathVariable a -> a.name().isEmpty() ? a.value() : a.name();
                case RequestPart a -> a.name().isEmpty() ? a.value() : a.name();
                case RequestHeader a -> a.name().isEmpty() ? a.value() : a.name();
                default -> "";
            };
            if (!name.isEmpty()) {
                return name;
            }
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
