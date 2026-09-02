package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Every provider handle this platform has been given — {@code decisions.md} D41.
 *
 * <p>A top-level interface in its own file, like {@link ErasureRunRepository}: methods added to a
 * generated repository are discarded by the next {@code jhipster jdl --force}, and Spring Data creates
 * no beans for nested interfaces.
 *
 * <h2>Two questions, and each has a different asker</h2>
 *
 * <p><strong>By booking reference</strong> — "what money is there against this booking?" — is the
 * platform's own question, asked when a booking has to be released, captured or refunded. It returns a
 * list rather than an optional on purpose: one booking can carry several attempts once a customer may
 * choose between providers (WP-13), and a repository that returned a single row would make the second
 * attempt look like a lost update rather than what it is.
 *
 * <p><strong>By provider reference</strong> — "which booking is this?" — is the <em>provider's</em>
 * question, and nothing asks it yet. It is here because it is the lookup a webhook needs (WP-11): a
 * confirmation arrives carrying the provider's own handle and nothing else, and without an index on
 * that column the estate would have no way to find out what it was for.
 */
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    List<PaymentAttempt> findByBookingReferenceOrderByRecordedAtAsc(String bookingReference);

    Optional<PaymentAttempt> findByProviderReference(String providerReference);
}
