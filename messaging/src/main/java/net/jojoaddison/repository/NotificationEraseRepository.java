package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The two ways a notification can be about an erased customer — {@code decisions.md} D24/D32.
 *
 * <p>A separate top-level interface rather than methods on the generated {@code NotificationRepository},
 * which a regeneration would discard.
 */
@Repository
public interface NotificationEraseRepository extends JpaRepository<Notification, Long> {
    /** Addressed <em>to</em> the customer. */
    @Query("select n from Notification n where n.recipientLogin = :login")
    List<Notification> addressedTo(@Param("login") String login);

    /**
     * <em>About</em> the customer, but addressed to somebody else.
     *
     * <p>These are the ones that are easy to miss. {@code booking.requested} raises "Ama Mensah asked
     * for a home visit on 12 Sep" in the <strong>professional's</strong> bell menu — the customer's
     * name, sitting in a row keyed to a different person's login, which no query by recipient will
     * ever return. The link back is {@code deepLink}, which is {@code /bookings/<ref>} for every
     * notification this service raises.
     *
     * <p>The caller builds that link set from the customer's conversations <em>and</em> from the
     * customer's own notifications — {@code decisions.md} D36. Conversations alone are not enough:
     * threads are deduped by professional, so a repeat booking's reference appears on no conversation
     * at all. Both columns this feature filters by, {@code deep_link} and {@code recipient_login}, are
     * indexed by the {@code privacy_indexes} changelog; neither of these is a scan.
     */
    @Query("select n from Notification n where n.deepLink in :deepLinks")
    List<Notification> linkedToAny(@Param("deepLinks") List<String> deepLinks);
}
