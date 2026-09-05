package net.jojoaddison.service.payment.provider;

import jakarta.annotation.PostConstruct;
import java.util.List;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import net.jojoaddison.service.payment.PaymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A named provider whose wire protocol nobody here has read — {@code decisions.md} D45.
 *
 * <p>The three adapters D37 chose extend this and add nothing but a name, their settings and the
 * list of what they still need from their provider's documentation. <strong>That is the whole of
 * what could honestly be built.</strong> This repository has no network access, no provider account
 * and no credentials, so a signature scheme, a JSON field path, a status vocabulary or a callback
 * envelope written here would be invention — and invention that passes the mocks written to match it,
 * on the one path where a customer's money is already committed. See this package's
 * {@code package-info} for what each adapter is missing, item by item.
 *
 * <h2>Failing closed, and what each failure is worth</h2>
 *
 * <p>Every provider-specific call throws, and the two kinds of throw are deliberate rather than
 * uniform — the same asymmetry {@code PaymentConfiguration.UnconfiguredPaymentProvider} draws, for
 * the same reason.
 *
 * <ul>
 *   <li>{@code authorize}, {@code capture}, {@code refund}, {@code voidAuthorization} and
 *       {@code status} throw {@link UnsupportedOperationException}. {@code BookingPayments} catches a
 *       {@code RuntimeException} around the provider call and answers {@link PaymentState#FAILED},
 *       which is a <strong>502 and no booking</strong> — the customer is told the provider could not
 *       be asked, which is exactly true. A polite {@code FAILED} return would say the same thing to
 *       the customer and nothing at all to the log; a throw carries a stack trace to the ERROR line
 *       that {@code BookingPayments.authorize} already writes.
 *   <li>{@code readCallback} throws {@link PaymentCallbackRefused}, which the webhook turns into a
 *       flat 401. Not an {@code UnsupportedOperationException}: that method is reached by whoever
 *       posts to a public endpoint rather than by this platform's own code, and a 500 with a stack
 *       trace per probe tells a stranger their request got further in than it did.
 * </ul>
 *
 * <p><strong>An enabled adapter with no secret refuses the same way, and says so differently.</strong>
 * The refusal is identical from outside — one 401, no detail, no oracle — and the message that
 * reaches the log names the missing configuration instead of the missing integration, because those
 * are two different jobs for two different people.
 *
 * <h2>What implementing one of these looks like</h2>
 *
 * <p>Override {@code authorize} and {@code readCallback} first; those two are the whole of the
 * booking path. Everything else stays as it is until something calls it: {@code capture} and
 * {@code status} have no caller in this estate yet, and {@code refund} and {@code voidAuthorization}
 * are reached only from {@code BookingPayments.release}, which flags the attempt row for a person
 * when they throw — so leaving them unimplemented degrades to "somebody must reconcile this by hand"
 * rather than to silence.
 *
 * <p><strong>And override {@link #integratedCalls()} in the same commit.</strong> D50 built the first
 * of the three, and the estate's startup log went on announcing that every enabled adapter refuses
 * everything — a service taking real money through a working integration while saying in its own
 * first ten lines that it cannot. The account is made from that method now, so it stays true by
 * construction: an adapter that implements a call and does not list it understates itself in a log
 * nobody would think to disbelieve, which is the cheaper direction of the same mistake.
 *
 * <p>Two rules that are not obvious from the port and are easy to get wrong once, expensively:
 * verify the callback <em>before</em> parsing it and over the bytes as received (see
 * {@link PaymentCallback}), and never copy the provider's own words into an outcome's reason — they
 * are where a customer's name or phone number arrives unannounced, and D44 records what that cost
 * when it reached a 402 response body.
 */
public abstract class ProviderAwaitingIntegration implements PaymentProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderAwaitingIntegration.class);

    private final String name;
    private final PaymentProviderProperties.Provider settings;

    protected ProviderAwaitingIntegration(String name, PaymentProviderProperties.Provider settings) {
        this.name = name;
        this.settings = settings;
    }

    /**
     * Which of this adapter's money calls really speak to the provider — {@code decisions.md} D50.
     *
     * <p>Empty is the honest answer for a class that adds nothing but a name, and it is the default
     * so that a new seam cannot claim more than it does by forgetting to say anything. A subclass
     * that overrides {@code authorize} or {@code readCallback} lists them here.
     *
     * <p>It exists for the startup account and for nothing else. It is deliberately <em>not</em>
     * consulted at dispatch time: what happens when an unimplemented call is reached is decided by
     * the call throwing, in one place, rather than by a list that could disagree with the code
     * beneath it. A list that is wrong makes a log line wrong; a list that routes would make a
     * payment wrong.
     */
    protected List<String> integratedCalls() {
        return List.of();
    }

    /**
     * Says at startup what this adapter can actually do with money.
     *
     * <p>Only enabled adapters are constructed at all — {@code PaymentConfiguration} registers each
     * under {@code @ConditionalOnProperty} — so reaching this method is itself the news, and there is
     * no {@code isEnabled()} test here for that reason.
     *
     * <p>WARN for a seam, INFO for an integration. The WARN is not pessimism: an adapter that refuses
     * every authorization turns a priced booking naming it into a 502, and an estate that quietly
     * changed what it does with customers' money when somebody set a variable would deserve worse
     * than a log line. The INFO is the counterpart D50 needed — the same event, reported truthfully
     * for an adapter that works, because a WARN nobody can act on is a WARN everybody learns to skip.
     */
    @PostConstruct
    void announceIntegration() {
        List<String> integrated = integratedCalls();
        if (integrated.isEmpty()) {
            LOG.warn(
                "payments: the {} adapter is ENABLED and is NOT IMPLEMENTED — it refuses every authorization and every " +
                    "callback, so a priced booking naming it answers 502 (decisions.md D45)",
                name
            );
            return;
        }
        LOG.info(
            "payments: the {} adapter is enabled and implements {}; every other money call refuses (decisions.md D50)",
            name,
            integrated
        );
    }

    /**
     * The name this provider is chosen by and addressed at, on {@code /webhooks/payments/{provider}}.
     *
     * <p>Final, and a constant rather than anything read at call time: {@code PaymentProviders} looks
     * providers up by it and D44 found what an adapter whose {@code name()} throws costs — a lazily
     * resolved merchant id here would put an exception in the middle of the registry.
     */
    @Override
    public final String name() {
        return name;
    }

    /** Whether a callback addressed to this adapter could be verified at all, if it were written. */
    protected final boolean canVerifyCallbacks() {
        return settings.hasSecret();
    }

    /**
     * The value this provider signs its callbacks with. Null when none is configured — a caller
     * should ask {@link #canVerifyCallbacks()} rather than test this, so that "unconfigured" is one
     * decision in one place.
     */
    protected final String signingSecret() {
        return settings.getSecret();
    }

    @Override
    public PaymentOutcome authorize(PaymentIntent intent) {
        throw notIntegrated("authorize");
    }

    @Override
    public PaymentOutcome capture(String providerReference, long amountMinor, String currency) {
        throw notIntegrated("capture");
    }

    @Override
    public PaymentOutcome refund(String providerReference, long amountMinor, String currency, String reason) {
        throw notIntegrated("refund");
    }

    @Override
    public PaymentOutcome voidAuthorization(String providerReference, String reason) {
        throw notIntegrated("voidAuthorization");
    }

    @Override
    public PaymentOutcome status(String providerReference) {
        throw notIntegrated("status");
    }

    /**
     * Refuses, because nothing here can tell a genuine callback from a forged one.
     *
     * <p>Two ways to be unable to, and they are told apart in the message only. Whoever reads the log
     * needs to know whether they are missing an integration or a variable; whoever posted the request
     * gets one answer either way.
     */
    @Override
    public PaymentOutcome readCallback(PaymentCallback callback) {
        if (!canVerifyCallbacks()) {
            throw new PaymentCallbackRefused(
                "the " + name + " adapter has no signing secret configured, so no callback can be verified (decisions.md D45)"
            );
        }
        throw new PaymentCallbackRefused(
            "the " + name + " adapter cannot verify a callback: its signature scheme is not implemented (decisions.md D45)"
        );
    }

    /**
     * The one message every unimplemented call gives, naming the call and where to read about it.
     *
     * <p>It quotes nothing from the intent — no amount, no login, no booking reference — because it
     * is rendered nowhere but a log line and because the habit of not putting a customer in an
     * exception message is cheaper to keep than to acquire.
     */
    protected final UnsupportedOperationException notIntegrated(String call) {
        return new UnsupportedOperationException(
            "the %s payment adapter does not implement %s: nothing here has ever spoken to %s. See the package documentation in %s (decisions.md D45)".formatted(
                name,
                call,
                name,
                ProviderAwaitingIntegration.class.getPackageName()
            )
        );
    }
}
