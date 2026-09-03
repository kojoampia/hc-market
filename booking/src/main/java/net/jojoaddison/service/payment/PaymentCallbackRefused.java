package net.jojoaddison.service.payment;

/**
 * The webhook was asked to believe something it could not verify — {@code decisions.md} D43.
 *
 * <p>Thrown by {@link PaymentProvider#readCallback} for every reason a callback might not be genuine:
 * a missing or wrong signature, a body that is not this provider's shape, a provider name nothing is
 * configured for. <strong>They are deliberately one exception rather than several.</strong> The
 * endpoint answers 401 with no detail whatever the cause, so distinguishing them in the type would
 * only invite a caller to distinguish them in the response — and telling an unauthenticated stranger
 * <em>which</em> part of their forgery was wrong is how a signature check becomes an oracle.
 *
 * <p>The message is for the log, where an operator reads it. It should describe what was wrong with
 * the request rather than repeat the request: a body that failed verification is a stranger's text and
 * has no business being quoted into a log line somebody will later read.
 */
public class PaymentCallbackRefused extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentCallbackRefused(String message) {
        super(message);
    }
}
