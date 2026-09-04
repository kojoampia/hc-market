package net.jojoaddison.service.payment;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Every payment provider this service is configured for, keyed by the name it answers to —
 * {@code decisions.md} D45.
 *
 * <h2>Why a registry replaced {@code @ConditionalOnMissingBean}</h2>
 *
 * <p>Until D45 there was one {@link PaymentProvider} in the estate and
 * {@code PaymentConfiguration} supplied it under {@code @ConditionalOnMissingBean}, so a real
 * provider was "one bean and no edit". D37 chose <strong>three</strong> — Paystack, Hubtel and MTN
 * MoMo, with the customer choosing between them — and one-bean-wins cannot express that: two
 * providers under that annotation is a {@code NoUniqueBeanDefinitionException} at best and a
 * silently arbitrary winner at worst. D44 wrote the ordering hazard down rather than fixing it,
 * precisely because this is the package that deletes the condition. It is deleted, and with it the
 * warning: there is nothing order-sensitive left to warn about, since every provider bean is
 * registered unconditionally and this class decides which one a booking reaches.
 *
 * <h2>The fallback is an entry, not the only entry</h2>
 *
 * <p>{@code PaymentConfiguration.unconfiguredPaymentProvider} is still here and still answers
 * {@link PaymentState#OFF_PLATFORM}, which is the truth about this estate today: the customer pays
 * the professional directly. It is injected <strong>by name</strong> rather than found by its type or
 * its {@code name()}, so this class holds no opinion about what an absent provider is called.
 *
 * <p><strong>The fallback is never a choice.</strong> {@link #choices()} excludes it, so a customer
 * cannot ask for "no provider" and get a booking created with no money behind it, and an estate with
 * one configured provider still has exactly one thing to choose from rather than two. It stays
 * reachable from {@link #named(String)} because the webhook resolves by the name in its path and
 * refusing a callback addressed to it is the fallback's own job — {@code readCallback} throws
 * {@link PaymentCallbackRefused}, which is a 401 rather than a 404 pretending to be one.
 *
 * <h2>One name, one provider — refused at startup</h2>
 *
 * <p>Injecting the fallback by bean name means no adapter can <em>become</em> the fallback by calling
 * itself {@code none}. That was written down as the guarantee and it was only half of one: the hazard
 * runs in the mirror direction. {@link #choices()} excludes the fallback <strong>by identity</strong>,
 * so an adapter claiming the same name is offered; {@link #named(String)} returns the
 * <strong>first</strong> match, and the fallback is declared first. So {@code chosen("none")} passed
 * the check and resolved to the fallback — {@code OFF_PLATFORM}, a booking in {@code REQUESTED},
 * {@code booking.requested} published, the professional told, and nobody ever asked for the money.
 *
 * <p>The same shape closes over any two adapters sharing a name: {@code distinct()} in
 * {@code choices()} offers it once, {@code findFirst()} in {@code named} always answers with the
 * same one, and the other is unreachable by a booking and unaddressable by its own callbacks — a
 * provider that takes money nothing here can confirm. Neither is reachable today, since the three
 * names are fixed constants, and {@link #refuseAmbiguousNames()} refuses both at startup anyway: a
 * name collision is a programming error rather than a runtime condition, and there is nothing a
 * running estate could usefully do about one.
 *
 * <h2>Nothing here calls {@code name()} eagerly</h2>
 *
 * <p>An index built in the constructor would ask every adapter to name itself before the context has
 * finished starting, and D44 records what it costs to trust {@code name()}: it is somebody else's
 * code, it can throw on a lazily-read merchant id, and it can answer null. Four providers is a list
 * to walk, not a map to maintain, so every lookup here asks {@link #nameOf} — which never throws and
 * never returns null — at the moment it needs an answer.
 */
@Service
public class PaymentProviders {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentProviders.class);

    /**
     * The bean name {@code PaymentConfiguration} gives the fallback. Duplicated there as a method
     * name, because a {@code @Bean} method cannot be named from a constant — the failure if the two
     * drift apart is a context that refuses to start, which is the loud direction.
     */
    public static final String FALLBACK_BEAN = "unconfiguredPaymentProvider";

    /** What {@link #nameOf} answers for an adapter that cannot name itself. Never selectable. */
    static final String UNNAMED = "unnamed";

    private final List<PaymentProvider> registered;
    private final PaymentProvider fallback;

    public PaymentProviders(List<PaymentProvider> registered, @Qualifier(FALLBACK_BEAN) PaymentProvider fallback) {
        this.registered = List.copyOf(registered);
        this.fallback = fallback;
    }

    /**
     * Refuses a registry in which two providers answer to one name, before it can take a booking.
     *
     * <p>Startup, because there is no other useful moment. A collision makes one adapter unreachable
     * by {@link #named(String)} and invisible in {@link #choices()}, and neither of those can report
     * it: the lookup has an answer and the offer looks complete. Refusing a booking at run time would
     * be refusing the wrong party for somebody else's mistake, so the context does not start.
     *
     * <p><strong>This is not the eager index D44 warned against.</strong> It reads every name once
     * through {@link #nameOf}'s safe path and keeps none of them — nothing is cached, so an adapter
     * that could not answer at startup is still asked again on every later lookup. An adapter that
     * cannot name itself is not a collision either: {@code null} is not a name two things can share,
     * and such a provider is already excluded from the offer.
     *
     * <p>Package-private and {@code @PostConstruct}: the unit tests call it directly, and one of them
     * asserts the annotation is still on it — a guard the container never runs is not a guard, which
     * is the same lesson the gateway's webhook permit learned.
     */
    @PostConstruct
    void refuseAmbiguousNames() {
        Set<String> seen = new HashSet<>();
        List<String> ambiguous = new ArrayList<>();
        for (PaymentProvider provider : registered) {
            String name = configuredName(provider);
            if (name != null && !seen.add(name) && !ambiguous.contains(name)) {
                ambiguous.add(name);
            }
        }
        if (!ambiguous.isEmpty()) {
            throw new IllegalStateException(
                "two payment providers answer to the same name and only the first is reachable: " +
                String.join(", ", ambiguous) +
                " — a callback can be handed to one of them and the other takes bookings nothing can confirm (decisions.md D45)"
            );
        }
    }

    /**
     * The provider a callback addressed to {@code name} should be handed to, if there is one.
     *
     * <p>Searches everything registered, the fallback included. D43 wrote the refusal for "a callback
     * addressed to a provider this service is not configured for" and it compared the path against
     * the single configured provider's name; with three of them that comparison becomes this lookup,
     * and it is what stops a callback signed by one provider being applied as another's.
     *
     * <p>An empty answer is the endpoint's 401. It deliberately does not fall back to anything: a
     * callback nobody here speaks for is not a callback, and handing it to whichever adapter happened
     * to be first would be asking one provider to vouch for another's signature.
     */
    public Optional<PaymentProvider> named(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return registered.stream().filter(provider -> wanted.equals(configuredName(provider))).findFirst();
    }

    /**
     * The names a customer may put on a booking, in registration order.
     *
     * <p>The fallback is not among them, and neither is an adapter that cannot name itself: a
     * provider nobody can name is a provider no callback can be routed back to, so offering it would
     * create bookings whose payments have nowhere to arrive.
     *
     * <p>The {@code distinct()} is belt and braces rather than the guarantee. Two providers sharing a
     * name would be offered once and resolved always to the same one, which is why
     * {@link #refuseAmbiguousNames()} stops the context instead of letting the list quietly tidy it
     * away.
     */
    public List<String> choices() {
        return registered
            .stream()
            .filter(provider -> provider != fallback)
            .map(this::configuredName)
            .filter(name -> name != null)
            .distinct()
            .toList();
    }

    /**
     * Which provider takes this booking's money — {@code decisions.md} D45, under D22's rule.
     *
     * <p><strong>A provider name from a client is a client-supplied field that something downstream
     * trusts</strong>, which is exactly what D22 exists to be suspicious of. It decides who ends up
     * holding a customer's money, so it is checked against what this service is actually configured
     * for and refused otherwise. It is never defaulted away: an unknown name means the client and the
     * estate disagree about how this booking can be paid for, and picking a provider on the
     * customer's behalf at that moment is the platform choosing who takes their money.
     *
     * <p>Three cases, and the middle one is why this is not a map lookup:
     *
     * <ul>
     *   <li><strong>Nothing configured.</strong> {@link #choices()} is empty — today's estate — and
     *       the fallback answers, so an unnamed request behaves exactly as it did before this
     *       package. A <em>named</em> request is still refused: the caller believes this estate
     *       collects money and it does not.
     *   <li><strong>One provider.</strong> It is the default. There is nothing to choose between, and
     *       requiring a name for a decision with one possible answer would refuse every booking made
     *       by a client written before the choice existed.
     *   <li><strong>More than one, and no name.</strong> Refused. The platform must not pick who
     *       takes the customer's money, and picking the first would make the answer depend on bean
     *       registration order — the very property this class was built to remove.
     * </ul>
     *
     * @param requested the name the client asked for, or null/blank for "no preference"
     * @throws PaymentChoiceRefused when the name is not offered, or when one was needed and none came
     */
    public PaymentProvider chosen(String requested) {
        List<String> offered = choices();
        if (requested == null || requested.isBlank()) {
            return switch (offered.size()) {
                case 0 -> fallback;
                case 1 -> named(offered.get(0)).orElse(fallback);
                default -> throw new PaymentChoiceRefused(PaymentChoiceRefused.Reason.CHOICE_REQUIRED, offered);
            };
        }
        String wanted = requested.trim().toLowerCase(Locale.ROOT);
        if (!offered.contains(wanted)) {
            // The name the client sent is not echoed anywhere a client will read it. It is a
            // stranger's string on the way to a response body, and this estate has already paid for
            // one of those (D44's provider prose in a 402) — the log is where it belongs.
            LOG.warn("a booking asked for a payment provider this service does not offer: {}", requested);
            throw new PaymentChoiceRefused(PaymentChoiceRefused.Reason.NOT_OFFERED, offered);
        }
        return named(wanted).orElse(fallback);
    }

    /**
     * A provider's name, from an adapter that may not be able to give one.
     *
     * <p>Moved here from {@code BookingPayments}, which wrapped every call to {@code name()} after a
     * review found an adapter whose {@code name()} throws landing back on the 500 that the catch
     * around {@code authorize} exists to remove (D44). It is the registry's business now because the
     * registry is what asks the question most often.
     *
     * <p>Never null and never throws. {@link #UNNAMED} is written to {@code payment_attempt.provider}
     * rather than nothing at all, because that column is not-null and the row is worth more than the
     * name — a placeholder says an adapter is misbehaving, a lost row says nothing at all.
     */
    public String nameOf(PaymentProvider provider) {
        String name = configuredName(provider);
        return name == null ? UNNAMED : name;
    }

    /**
     * The lower-cased name an adapter answers to, or null if it will not say.
     *
     * <p>Null rather than {@link #UNNAMED} on purpose: this is the form the lookups use, and an
     * adapter that cannot name itself must not become selectable by asking for {@code "unnamed"}.
     */
    private String configuredName(PaymentProvider provider) {
        if (provider == null) {
            // Reached from nameOf on the free-booking path, where nobody was asked anything. Not an
            // error, and not worth a stack trace.
            return null;
        }
        try {
            String name = provider.name();
            return name == null || name.isBlank() ? null : name.trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            LOG.error("a configured payment provider could not name itself", e);
            return null;
        }
    }
}
