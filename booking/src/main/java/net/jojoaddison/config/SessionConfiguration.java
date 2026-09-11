package net.jojoaddison.config;

import net.jojoaddison.service.session.DailyCoMeetingRoomProvider;
import net.jojoaddison.service.session.MeetingRoomProvider;
import net.jojoaddison.service.session.UnconfiguredMeetingRoomProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Which meeting-room provider answers — {@code decisions.md} D86, backlog WP-17.
 *
 * <h2>ONE bean that chooses, not two beans and a condition</h2>
 *
 * <p>The payments seam next door keeps a registry of every configured provider because a <em>customer
 * chooses</em> one per booking (D45), and that is a shape one-bean-wins cannot express. <strong>Nothing
 * chooses a meeting-room provider.</strong> An estate either hosts rooms or relays the professional's
 * own link, for every booking, so exactly one provider is ever right and a registry would be
 * scaffolding for a choice nobody makes.
 *
 * <p>That could have been written as {@code @ConditionalOnProperty} on one bean and
 * {@code @ConditionalOnMissingBean} on the other, and it deliberately is not: that is <strong>D44's
 * ordering hazard</strong>, where which bean wins depends on the order definitions are parsed.
 * D45 deleted exactly that construct from the payments configuration rather than reasoning about it.
 * One method with an {@code if} has no order to get wrong.
 *
 * <h2>Absent means D17's v1, which is not a failure</h2>
 *
 * <p>With nothing configured this supplies {@link UnconfiguredMeetingRoomProvider}, which reports that
 * the professional supplies the link. That <em>is</em> D17's recommended v1 — it needs no account and no
 * budget — so the default is the recommendation rather than a placeholder for one. WP-17's budget block
 * applies to the hosted upgrade alone.
 */
@Configuration
public class SessionConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SessionConfiguration.class);

    @Bean
    public MeetingRoomProvider meetingRoomProvider(@Value("${healthconnect.sessions.dailyco.enabled:false}") boolean dailyCoEnabled) {
        if (dailyCoEnabled) {
            // The seam refuses every call and says so at boot through its own @PostConstruct. Logged here
            // as well, because this line is the one that explains WHY a refusing provider is in the
            // context at all — somebody set a property.
            LOG.warn("sessions: dailyco is selected, so online sessions are refused until it is implemented (backlog WP-17)");
            return new DailyCoMeetingRoomProvider(true);
        }
        LOG.info("sessions: no hosted provider — the professional supplies the meeting link (decisions.md D17)");
        return new UnconfiguredMeetingRoomProvider();
    }
}
