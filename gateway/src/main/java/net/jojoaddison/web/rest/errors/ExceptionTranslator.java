package net.jojoaddison.web.rest.errors;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.web.rest.errors.ExceptionTranslation;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;
import tech.jhipster.web.util.HeaderUtil;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 * The error response follows RFC7807 - Problem Details for HTTP APIs (https://tools.ietf.org/html/rfc7807).
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler implements ExceptionTranslation {

    private static final String FIELD_ERRORS_KEY = "fieldErrors";
    private static final String MESSAGE_KEY = "message";
    private static final String PATH_KEY = "path";
    private static final boolean CASUAL_CHAIN_ENABLED = false;

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTranslator.class);

    @Value("${jhipster.clientApp.name:healthconnectGateway}")
    private String applicationName;

    private final Environment env;

    public ExceptionTranslator(Environment env) {
        this.env = env;
    }

    @ExceptionHandler
    @Override
    public Mono<ResponseEntity<Object>> handleAnyException(Throwable ex, ServerWebExchange request) {
        LOG.debug("Converting Exception to Problem Details:", ex);
        ProblemDetailWithCause pdCause = wrapAndCustomizeProblem(ex, request);
        return handleExceptionInternal((Exception) ex, pdCause, buildHeaders(ex), HttpStatusCode.valueOf(pdCause.getStatus()), request);
    }

    @SuppressWarnings("java:S2638")
    @Nullable
    @Override
    protected Mono<ResponseEntity<Object>> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        ServerWebExchange request
    ) {
        body = body == null ? wrapAndCustomizeProblem(ex, (ServerWebExchange) request) : body;
        if (request.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        return Mono.just(
            new ResponseEntity<>(body, updateContentType(headers), HttpStatusCode.valueOf(((ProblemDetailWithCause) body).getStatus()))
        );
    }

    protected ProblemDetailWithCause wrapAndCustomizeProblem(Throwable ex, ServerWebExchange request) {
        return customizeProblem(getProblemDetailWithCause(ex), ex, request);
    }

    private ProblemDetailWithCause getProblemDetailWithCause(Throwable ex) {
        if (
            ex instanceof net.jojoaddison.service.UsernameAlreadyUsedException
        ) return (ProblemDetailWithCause) new LoginAlreadyUsedException().getBody();
        if (
            ex instanceof net.jojoaddison.service.EmailAlreadyUsedException
        ) return (ProblemDetailWithCause) new EmailAlreadyUsedException().getBody();
        if (
            ex instanceof net.jojoaddison.service.InvalidPasswordException
        ) return (ProblemDetailWithCause) new InvalidPasswordException().getBody();

        if (ex instanceof AuthenticationException) {
            // Ensure no information about existing users is revealed via failed authentication attempts
            return ProblemDetailWithCauseBuilder.instance()
                .withStatus(toStatus(ex).value())
                .withTitle("Unauthorized")
                .withDetail("Invalid credentials")
                .build();
        }
        if (
            ex instanceof ErrorResponseException exp && exp.getBody() instanceof ProblemDetailWithCause problemDetailWithCause
        ) return problemDetailWithCause;
        return ProblemDetailWithCauseBuilder.instance().withStatus(toStatus(ex).value()).build();
    }

    protected ProblemDetailWithCause customizeProblem(ProblemDetailWithCause problem, Throwable err, ServerWebExchange request) {
        if (problem.getStatus() <= 0) problem.setStatus(toStatus(err));

        if (problem.getType() == null || problem.getType().equals(URI.create("about:blank"))) problem.setType(getMappedType(err));

        // higher precedence to Custom/ResponseStatus types
        String title = extractTitle(err, problem.getStatus());
        String problemTitle = problem.getTitle();
        if (problemTitle == null || !problemTitle.equals(title)) {
            problem.setTitle(title);
        }

        if (problem.getDetail() == null) {
            // higher precedence to cause
            problem.setDetail(getCustomizedErrorDetails(err));
        }

        Map<String, Object> problemProperties = problem.getProperties();
        if (problemProperties == null || !problemProperties.containsKey(MESSAGE_KEY)) problem.setProperty(
            MESSAGE_KEY,
            getMappedMessageKey(err) != null ? getMappedMessageKey(err) : "error.http." + problem.getStatus()
        );

        if (problemProperties == null || !problemProperties.containsKey(PATH_KEY)) problem.setProperty(PATH_KEY, getPathValue(request));

        if (
            err instanceof WebExchangeBindException fieldException &&
            (problemProperties == null || !problemProperties.containsKey(FIELD_ERRORS_KEY))
        ) problem.setProperty(FIELD_ERRORS_KEY, getFieldErrors(fieldException));

        problem.setCause(buildCause(err.getCause(), request).orElse(null));

        return problem;
    }

    private String extractTitle(Throwable err, int statusCode) {
        return getCustomizedTitle(err) != null ? getCustomizedTitle(err) : extractTitleForResponseStatus(err, statusCode);
    }

    private List<FieldErrorVM> getFieldErrors(WebExchangeBindException ex) {
        return ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(f ->
                new FieldErrorVM(
                    f.getObjectName().replaceFirst("DTO$", ""),
                    f.getField(),
                    StringUtils.isNotBlank(f.getDefaultMessage()) ? f.getDefaultMessage() : f.getCode()
                )
            )
            .toList();
    }

    private String extractTitleForResponseStatus(Throwable err, int statusCode) {
        var specialStatus = extractResponseStatus(err);
        return specialStatus == null ? HttpStatus.valueOf(statusCode).getReasonPhrase() : specialStatus.reason();
    }

    private HttpStatus toStatus(final Throwable throwable) {
        // Let the ErrorResponse take this responsibility
        if (throwable instanceof ErrorResponse err) return HttpStatus.valueOf(err.getBody().getStatus());

        return Optional.ofNullable(getMappedStatus(throwable)).orElse(
            Optional.ofNullable(resolveResponseStatus(throwable)).map(ResponseStatus::value).orElse(HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    private ResponseStatus extractResponseStatus(final Throwable throwable) {
        return resolveResponseStatus(throwable);
    }

    private ResponseStatus resolveResponseStatus(final Throwable type) {
        final ResponseStatus candidate = findMergedAnnotation(type.getClass(), ResponseStatus.class);
        return candidate == null && type.getCause() != null ? resolveResponseStatus(type.getCause()) : candidate;
    }

    private URI getMappedType(Throwable err) {
        if (
            err instanceof MethodArgumentNotValidException || err instanceof ConstraintViolationException
        ) return ErrorConstants.CONSTRAINT_VIOLATION_TYPE;
        return ErrorConstants.DEFAULT_TYPE;
    }

    private String getMappedMessageKey(Throwable err) {
        if (err instanceof MethodArgumentNotValidException || err instanceof ConstraintViolationException) {
            return ErrorConstants.ERR_VALIDATION;
        } else if (err instanceof ConcurrencyFailureException || err.getCause() instanceof ConcurrencyFailureException) {
            return ErrorConstants.ERR_CONCURRENCY_FAILURE;
        } else if (err instanceof WebExchangeBindException) {
            return ErrorConstants.ERR_VALIDATION;
        }
        return null;
    }

    private String getCustomizedTitle(Throwable err) {
        if (
            err instanceof MethodArgumentNotValidException || err instanceof ConstraintViolationException
        ) return "Method argument not valid";
        return null;
    }

    private String getCustomizedErrorDetails(Throwable err) {
        Collection<String> activeProfiles = List.of(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            if (err instanceof HttpMessageConversionException) return "Unable to convert http message";
            if (err instanceof DataAccessException) return "Failure during data access";
            if (containsPackageName(err.getMessage())) return "Unexpected runtime exception";
        }
        return err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
    }

    private HttpStatus getMappedStatus(Throwable err) {
        // Where we disagree with Spring defaults
        if (err instanceof AccessDeniedException) return HttpStatus.FORBIDDEN;
        if (err instanceof ConcurrencyFailureException) return HttpStatus.CONFLICT;
        // A BROKEN USER STORE IS NOT A REFUSED CREDENTIAL, AND THIS ARM MUST STAY ABOVE THE NEXT ONE —
        // decisions.md D106, backlog NEW-61. `AuthenticationServiceException` and its subclass
        // `InternalAuthenticationServiceException` (which the servlet stack uses to wrap whatever a
        // `UserDetailsService` threw) are `AuthenticationException`s meaning the estate is broken: an
        // unreachable account store, a Mongo timeout. Answering 401 would tell the caller their
        // credentials were wrong about an outage, and `deploy/observability/hc-market-rules.yaml` keys
        // its alerting on 5xx rates, so it would silence the alert too. Returning null falls through to
        // 500, which is this estate's standing direction: an ERROR is a fact about the estate that is
        // wrong and nobody chose (D97).
        // NOTE WHAT THAT null RESTS ON: `toStatus` then tries `resolveResponseStatus`, which RECURSES
        // INTO getCause() — and `InternalAuthenticationServiceException` exists to wrap. So "excluded
        // means 500" holds only while no cause carries @ResponseStatus. It holds today (the gateway's one
        // annotated throwable is AccountResource's private AccountResourceException, thrown nowhere near
        // authentication, and a Mongo or socket cause carries no annotation), and a cause that did carry
        // one would rightly win.
        if (err instanceof AuthenticationServiceException) return null;
        // EVERY OTHER WAY OF FAILING TO AUTHENTICATE IS 401, BY SUPERCLASS AND NOT BY ENUMERATION.
        // The body mapping in `getProblemDetailWithCause` has always branched on `AuthenticationException`
        // so that no failure mode reveals whether an account exists; this half enumerated subclasses and
        // did not name `UserNotActivatedException`, the estate's own custom member of the family. The
        // status therefore fell through to 500 — so `500` meant "registered here and never activated" and
        // `401` meant "not registered", askable about any login or address with no credential at all
        // (`POST /api/register` is permitAll, and the exception is thrown by the lookup BEFORE the
        // password encoder is consulted, so the supplied password could not change the answer). The title
        // and the `message` property leaked it a second and third time, because `customizeProblem` derives
        // both from the status: "Internal Server Error" rather than "Unauthorized", and `error.http.500`
        // rather than `error.http.401`. All three move with this line.
        // THIS WIDENS SIX BUCKETS, NOT ONE, and all six are right at a login endpoint. Measured
        // main-vs-head: `UserNotActivatedException` plus `DisabledException`, `LockedException`,
        // `AccountExpiredException`, `CredentialsExpiredException` and `CompromisedPasswordException` all
        // go 500 -> 401. The five extras are unreachable today — `UserWithId.fromUser` sets every account
        // flag true and no `CompromisedPasswordChecker` is configured — so this is a door closed before
        // anyone walked through it, not a behaviour change anybody will see.
        // The two arms below are now redundant and are kept deliberately: they are generated lines, and
        // they are the floor a regeneration that drops this one falls back to. (Verified dead: deleting
        // both leaves the unit test 5/5 green, because the arm above already answers for them.)
        if (err instanceof AuthenticationException) return HttpStatus.UNAUTHORIZED;
        if (err instanceof BadCredentialsException) return HttpStatus.UNAUTHORIZED;
        if (err instanceof UsernameNotFoundException) return HttpStatus.UNAUTHORIZED;
        if (err instanceof ConstraintViolationException) return HttpStatus.BAD_REQUEST;
        return null;
    }

    private URI getPathValue(ServerWebExchange request) {
        if (request == null) return URI.create("about:blank");
        return request.getRequest().getURI();
    }

    private HttpHeaders buildHeaders(Throwable err) {
        return err instanceof BadRequestAlertException badRequestAlertException
            ? HeaderUtil.createFailureAlert(
                  applicationName,
                  true,
                  badRequestAlertException.getEntityName(),
                  badRequestAlertException.getErrorKey(),
                  badRequestAlertException.getMessage()
              )
            : null;
    }

    private HttpHeaders updateContentType(HttpHeaders headers) {
        if (headers == null) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return headers;
    }

    public Optional<ProblemDetailWithCause> buildCause(final Throwable throwable, ServerWebExchange request) {
        if (throwable != null && isCasualChainEnabled()) {
            return Optional.of(customizeProblem(getProblemDetailWithCause(throwable), throwable, request));
        }
        return Optional.ofNullable(null);
    }

    private boolean isCasualChainEnabled() {
        // Customize as per the needs
        return CASUAL_CHAIN_ENABLED;
    }

    private boolean containsPackageName(String message) {
        // This list is for sure not complete
        return Strings.CS.containsAny(message, "org.", "java.", "net.", "jakarta.", "javax.", "com.", "io.", "de.", "net.jojoaddison");
    }
}
