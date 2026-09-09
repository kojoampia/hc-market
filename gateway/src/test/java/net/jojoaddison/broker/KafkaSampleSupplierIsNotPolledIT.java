package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.MimeTypeUtils;

/**
 * The generated Kafka sample's <em>supplier</em> must not be bound — backlog NEW-21,
 * {@code decisions.md} D62.
 *
 * <p>{@code broker.KafkaProducer} is a generated {@code Supplier<String>} returning the constant
 * {@code "kafka_producer"}. Spring Cloud Stream polls a <strong>bound</strong> supplier on
 * {@code spring.integration.poller.fixed-delay}, which the framework defaults to <strong>1s</strong>
 * and which nothing in this repository configures. So for as long as generated
 * {@code application-kafka.yml} named it in {@code spring.cloud.function.definition}, every one of the
 * five services put one message a second onto the broker four products borrow (D27), with no caller,
 * no consumer and no purpose. Measured on the shared broker at 5.9/s across six such publishers, five
 * of them ours, past an end offset of 5.78 million.
 *
 * <p><strong>This is the inverse of a test D59 deliberately did not carry forward.</strong> Each
 * deleted {@code Healthconnect<Svc>KafkaResourceIT} held a {@code producesPooledMessages} asserting
 * that {@code output.receive(1500, "kafkaProducer-out-0")} yields {@code "kafka_producer"} — CI
 * asserting this defect works. The same destination is asked the opposite question here.
 *
 * <p><strong>Why a test and not only a grep.</strong> The remedy is one token in a config file, and a
 * grep of that file can only say what the file says. This asks Spring Cloud Stream itself, through the
 * test binder, whether anything is bound to that destination — which is the property that decides what
 * reaches a real broker, and which would survive the supplier being renamed, re-bound under another
 * binding name, or reintroduced by a fresh {@code definition}.
 *
 * <p><strong>The consumer half is the positive control</strong>, because the assertion above is a
 * negative one and an unbound binder would satisfy it for the wrong reason. {@code kafkaConsumer}
 * stays named, and it stays named for a mechanical reason as well as a documented one: an explicit
 * {@code definition} is what stops Spring Cloud Function falling back to auto-discovering a lone
 * function bean, which would bind {@code KafkaProducer} again the day anything removes the consumer.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ... --force} leaves it in place while
 * it rewrites {@code application-kafka.yml} and puts {@code kafkaProducer} back in the definition.
 * That is the point of it — the config edit is a regeneration hazard and this is what goes red.
 *
 * @see KafkaSampleIsNotAnApiIT which guards the doors D59 closed, one defect earlier
 */
@IntegrationTest
@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
@ActiveProfiles({ "kafka" })
class KafkaSampleSupplierIsNotPolledIT {

    /** The binding the generated {@code definition} bound {@code broker.KafkaProducer} to. */
    private static final String SUPPLIER_BINDING = "kafkaProducer-out-0";

    /**
     * The binding {@code broker.KafkaConsumer} is still bound to, and this test's positive control.
     *
     * <p>It is the <strong>only</strong> Spring Cloud Stream input binding this estate has in any
     * service — the domain event consumers are plain {@code @KafkaListener}s and not bindings — so the
     * test binder's single input channel is this one's, and the <em>unnamed</em>
     * {@code InputDestination.send} addresses it. Naming it does not work and the failure is an NPE
     * from inside the binder: it registers channels by <em>destination</em>, which for this binding is
     * {@code sse-topic}.
     */
    private static final String CONSUMER_BINDING = "kafkaConsumer-in-0";

    /**
     * Two poll windows at the framework's 1s default. A bound supplier does not need them — its first
     * message is already queued by the time any test runs — so this timeout is only ever paid on the
     * passing path.
     */
    private static final long TWO_POLLS_MILLIS = 2_500;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Test
    @DisplayName("nothing is published on the generated supplier's binding, because nothing polls it")
    void theSampleSupplierIsNotBound() {
        assertThat(output.receive(TWO_POLLS_MILLIS, SUPPLIER_BINDING))
            .as(
                "%s carried a message. broker.KafkaProducer is bound again, so this service is putting " +
                "one message a second onto the broker four products borrow, with nothing consuming it. " +
                "Take `kafkaProducer` back out of spring.cloud.function.definition in " +
                "src/main/resources/config/application-kafka.yml — a regeneration puts it back. " +
                "See backlog NEW-21 and decisions.md D62.",
                SUPPLIER_BINDING
            )
            .isNull();
    }

    /**
     * The control. Without it, a binder that bound nothing at all — a broken test context, a
     * {@code definition} emptied rather than narrowed — would satisfy the assertion above by answering
     * the question "is anything working here?" with no.
     */
    @Test
    @DisplayName("the consumer half is still bound — otherwise the assertion above means nothing")
    void theSampleConsumerIsStillBound() {
        var headers = new MessageHeaders(Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN_VALUE));
        var message = new GenericMessage<>("a-control-message", headers);

        assertThatNoException()
            .as(
                "%s is not bound, so nothing in this service is wired to the broker and the supplier " +
                "assertion beside this one proves nothing. spring.cloud.function.definition must still " +
                "name kafkaConsumer. See decisions.md D62.",
                CONSUMER_BINDING
            )
            .isThrownBy(() -> input.send(message));
    }
}
