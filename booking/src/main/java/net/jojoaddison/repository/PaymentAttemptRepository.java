package net.jojoaddison.repository;

import java.util.List;
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
 * question, and it is what the webhook asks (WP-11/D43): a confirmation arrives carrying the
 * provider's own handle and nothing else, and without an index on that column the estate would have
 * no way to find out what it was for.
 *
 * <p>It returns a <strong>list</strong>, and that is not tidiness. {@code provider_reference} is
 * deliberately not unique — the changelog says why: a provider that reuses a handle across a void and
 * a retry is telling us the truth about its own model, and refusing the second row would lose the
 * handle again. A derived query returning {@code Optional} therefore throws
 * {@code IncorrectResultSizeDataAccessException} the first time that happens, which on a public
 * webhook is a 500 for a callback that is entirely legitimate. Newest first, because a repeated handle
 * describes the same payment and the latest row is the one the platform's beliefs are recorded on.
 */
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    List<PaymentAttempt> findByBookingReferenceOrderByRecordedAtAsc(String bookingReference);

    List<PaymentAttempt> findByProviderReferenceOrderByRecordedAtDesc(String providerReference);
}
