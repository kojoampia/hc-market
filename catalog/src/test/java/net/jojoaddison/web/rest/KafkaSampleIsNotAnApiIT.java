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
 * <p>{@code messageBroker kafka} in {@code jdl/catalog.jdl} makes the generator emit
 * {@code HealthconnectCatalogKafkaResource} beside the entity CRUD D54 deleted. It carried three
 * mappings and no {@code @PreAuthorize}, under this service's blanket
 * {@code .requestMatchers("/api/**").authenticated()}, and the gateway routes
 * {@code /services/healthconnectcatalog/api/**} straight at it.
 *
 * <p>{@code POST /publish} put a caller's arbitrary string onto the broker four products borrow
 * (D27), on a topic created on demand; {@code GET /register} attached the caller to
 * {@code broker.KafkaConsumer}, which fans every message it receives to <strong>every</strong>
 * registered emitter with no per-user filter at all. The mechanism was established rather than
 * assumed and both of D54's readings of it were wrong — the whole chain, and the profile group that
 * decides it, is in D59 and in booking's copy of this file.
 *
 * <p>Nothing in the repository called any of the three paths. {@code broker.KafkaConsumer} and
 * {@code broker.KafkaProducer} stay: they are named by {@code spring.cloud.function.definition} in a
 * generated file and, with the resource gone, they map no URL — D54's rule for an orphaned generated
 * class. What the supplier does to the shared broker unprompted is backlog NEW-21, not this.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/catalog.jdl --force} leaves it
 * in place while it puts the resource back. That is the point of it.
 *
 * @see GeneratedCrudIsNotAnApiIT which is the pattern this follows
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "a.stranger", authorities = { AuthoritiesConstants.USER })
class KafkaSampleIsNotAnApiIT {

    private static final String SAMPLE = "/api/healthconnect-catalog-kafka";

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
        mockMvc.perform(get("/api/professionals")).andExpect(status().isOk());
    }

    /**
     * {@code message} is supplied on the {@code POST} deliberately. Without it a restored resource
     * would answer 400 on a missing required request parameter, and this guard would pass against a
     * live publish endpoint — the same reasoning that makes {@link GeneratedCrudIsNotAnApiIT} forge a
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
