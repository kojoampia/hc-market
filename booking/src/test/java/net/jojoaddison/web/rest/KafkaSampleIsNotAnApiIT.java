package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * JHipster's Kafka sample must not answer anybody — backlog NEW-17, {@code decisions.md} D59.
 *
 * <p>{@code messageBroker kafka} in {@code jdl/booking.jdl} makes the generator emit
 * {@code HealthconnectBookingKafkaResource} beside the entity CRUD D54 deleted. It carried three
 * mappings and no {@code @PreAuthorize}, under this service's blanket
 * {@code .requestMatchers("/api/**").authenticated()}, and the gateway routes
 * {@code /services/healthconnectbooking/api/**} straight at it — so any token this estate accepts
 * reached all three.
 *
 * <p><strong>{@code POST /publish} is the serious one, and what it does was established rather than
 * assumed.</strong> D54 first said the binding pointed at the shared broker with
 * {@code auto-create-topics: true}; its own review then said that cited {@code application-kafka.yml}
 * and the {@code kafka} profile was active nowhere. <strong>Both readings were wrong, and the first
 * one was wrong in the right direction.</strong> {@code application.yml} puts {@code kafka} in
 * <em>both</em> {@code spring.profiles.group.dev} and {@code spring.profiles.group.prod}, so the
 * profile is active in every environment this estate has — read off the running quality container,
 * which reports {@code activeProfiles: [secret-samples, kafka, api-docs, dev, test]}. The binder is
 * pointed at {@code hc-shared-quality-kafka:9092}, the broker four products borrow (D27), and its
 * sibling bindings from the same file have auto-created {@code sse-topic} and
 * {@code kafkaProducer-out-0} on it. So {@code /publish} put a caller's arbitrary string onto shared
 * infrastructure, on a topic ({@code binding-out-0}, the destination defaulting to the binding name)
 * created on demand.
 *
 * <p>{@code GET /register} is the other half and is a disclosure rather than a write:
 * {@code broker.KafkaConsumer.accept} sends every message it receives to <strong>every</strong>
 * registered emitter, with no per-user filter of any kind. Nothing in hc-market publishes to
 * {@code sse-topic} — measured at end offset 0 on the shared broker — but three other products sit on
 * that broker, and the door does not know that.
 *
 * <p>The fix is the one this repository has applied to every generated resource before it: delete it,
 * and put it on CLAUDE.md's delete table. Nothing called any of the three paths — checked against the
 * prototype, both deploy scripts, both verify scripts, {@code quality/startup.sh} and the other four
 * services; the only hits were this resource's own {@code @RequestMapping} and its own generated IT.
 * {@code broker.KafkaConsumer} and {@code broker.KafkaProducer} <strong>stay</strong>: they are named
 * by {@code spring.cloud.function.definition} in a generated file, and with the resource gone they map
 * no URL, which is D54's rule for an orphaned generated class. What the supplier does to the shared
 * broker unprompted is backlog NEW-21, not this.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/booking.jdl --force} leaves it
 * in place while it puts the resource back. That is the point of it.
 *
 * @see AuditTrailIsNotAnApiIT which is the pattern this follows
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.stranger", authorities = { AuthoritiesConstants.USER })
class KafkaSampleIsNotAnApiIT {

    private static final String SAMPLE = "/api/healthconnect-booking-kafka";

    /**
     * One mapping the generated sample carried.
     *
     * @param method the verb, because a {@code GET} against a {@code POST}-only sample answers 405 and
     *               would read as a refusal
     * @param path   the full path, quoted back in every assertion — a battery that fires as one number
     *               cannot tell you which door opened
     */
    private record Door(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static Stream<Door> doors() {
        return Stream.of(
            new Door("POST", SAMPLE + "/publish"),
            new Door("GET", SAMPLE + "/register"),
            new Door("GET", SAMPLE + "/unregister")
        );
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * A positive control, because every other test here is a negative one — D54's review, finding 3.
     *
     * <p>The refusal set is {@code 401/403/404/405} rather than a bare 404 on purpose: deletion gives
     * 404 today and a future gate would give 403. The price of that latitude is that a 404 for the
     * wrong reason reads exactly like a 404 for the right one, so one endpoint that must answer
     * establishes that the application is up, mapped and serving in the same run.
     */
    @Test
    @DisplayName("the service is actually serving — otherwise every refusal below is meaningless")
    void theSurvivingApiStillAnswers() throws Exception {
        mockMvc.perform(get("/api/bookings/mine")).andExpect(status().isOk());
    }

    /**
     * {@code message} is supplied on the {@code POST} deliberately. Without it a restored resource
     * would answer 400 on a missing required request parameter, and this guard would pass against a
     * live publish endpoint — the same reasoning that makes {@code GeneratedCrudIsNotAnApiIT} forge a
     * complete valid DTO rather than an empty body.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("doors")
    @DisplayName("no generated Kafka sample mapping answers a token this estate accepts")
    void nobodyReachesTheSample(Door door) throws Exception {
        var request = door.method().equals("POST")
            ? post(door.path()).with(csrf()).param("message", "a-stranger-was-here")
            : get(door.path());

        mockMvc
            .perform(request)
            .andExpect(result ->
                assertThat(result.getResponse().getStatus())
                    .as(
                        "%s must not answer — it is JHipster's Kafka sample, restored. See backlog NEW-17 and decisions.md D59.",
                        door
                    )
                    .isIn(401, 403, 404, 405)
            );
    }
}
