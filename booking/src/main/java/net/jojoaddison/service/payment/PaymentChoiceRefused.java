package net.jojoaddison.service.payment;

import java.util.List;

/**
 * The customer named a payment provider this estate cannot use, or named none where one was needed —
 * {@code decisions.md} D45.
 *
 * <p>Thrown by {@link PaymentProviders#chosen(String)} <em>before</em> any provider is asked
 * anything, so nothing has been committed when it arrives and there is nothing to give back. That is
 * the difference between this and a {@link PaymentState#DECLINED} outcome: a decline is an answer
 * about money, this is a request that could not be acted on at all.
 *
 * <p><strong>It carries no customer input.</strong> The name that was asked for is logged and goes no
 * further — {@code CustomerBookingResource} renders this into a response body, and D44 records what
 * it cost the last time a string from outside travelled that route. What it does carry is
 * {@link #offered()}, which is this service's own configuration and is safe to say out loud: without
 * it a client that has to name a provider has no way to learn which names exist, and would be left
 * guessing at the one field on the request that decides who takes the money.
 */
public class PaymentChoiceRefused extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Why the choice could not be honoured. Two reasons, because the client's next move differs —
     * the same argument that keeps {@link PaymentState#DECLINED} and {@link PaymentState#FAILED}
     * apart one layer down.
     */
    public enum Reason {
        /**
         * The name is not one this service is configured for. The realistic cause is a client holding
         * a list the estate has since changed, so the client's move is to re-read and ask again —
         * which is also the right move when the name was never valid. A 409, for the same reason
         * D22's price mismatch is one.
         */
        NOT_OFFERED,
        /**
         * More than one provider is configured and the request named none. The request is incomplete
         * rather than in conflict with anything, so the client's move is to ask the customer. A 400.
         */
        CHOICE_REQUIRED,
    }

    private final transient Reason reason;
    private final transient List<String> offered;

    public PaymentChoiceRefused(Reason reason, List<String> offered) {
        super(message(reason, offered));
        this.reason = reason;
        this.offered = List.copyOf(offered);
    }

    public Reason reason() {
        return reason;
    }

    /** The provider names this service is configured for. Possibly empty, never null. */
    public List<String> offered() {
        return offered;
    }

    private static String message(Reason reason, List<String> offered) {
        String names = offered.isEmpty() ? "none" : String.join(", ", offered);
        return switch (reason) {
            case NOT_OFFERED -> "this estate does not offer that payment provider; it offers: " + names;
            case CHOICE_REQUIRED -> "this booking has to name a payment provider; choose one of: " + names;
        };
    }
}
