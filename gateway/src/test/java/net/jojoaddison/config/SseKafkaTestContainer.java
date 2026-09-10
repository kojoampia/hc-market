package net.jojoaddison.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A broker for the SSE fan-out tests, and a NEW file rather than a change to the generated
 * {@link KafkaTestContainer} beside it.
 *
 * <h2>Import it ALONGSIDE MongoDbTestContainer, never instead of it</h2>
 *
 * <p>{@code @ImportTestcontainers} on a test class supersedes the one on {@code @IntegrationTest}
 * rather than adding to it, so importing this alone silently drops Mongo — and the symptom is
 * Mongock timing out against {@code localhost:27017}, which reads as a broken database container
 * rather than as a lost annotation. Name both:
 *
 * <pre>{@code @ImportTestcontainers({ MongoDbTestContainer.class, SseKafkaTestContainer.class })}</pre>
 *
 * <p>Extending {@code MongoDbTestContainer} looks like the tidier fix and does not work: the
 * registrar only manages container fields <em>declared on</em> the imported type, so the inherited
 * one is never started and the failure moves to "MongoDBContainer should be started first".
 *
 * <p>The property method below is renamed for a related reason: two imported interfaces both
 * declaring {@code registerProperties} means only one of them contributes anything.
 *
 * <h2>The one property that made this necessary</h2>
 *
 * <p>The generated container registers {@code spring.cloud.stream.kafka.binder.brokers} and nothing
 * else, because everything JHipster generates for Kafka goes through Spring Cloud Stream.
 * {@link net.jojoaddison.service.MarketplaceEventFanout} uses {@code @KafkaListener}, which reads
 * {@code spring.kafka.bootstrap-servers} — a property the generated container never sets.
 *
 * <p>The failure that causes is silent, which is the whole reason this file exists rather than a
 * shrug: the context starts, the listener bean is created, no broker is ever contacted, and the test
 * simply waits for a message that was never going to arrive. Both properties are registered here so
 * that a test which times out means the code is wrong, not the harness.
 *
 * <p>{@code SPRING_KAFKA_BOOTSTRAP_SERVERS} is the same property the compose files set, so what runs
 * here is what runs in the estate.
 */
public interface SseKafkaTestContainer {
    @Container
    KafkaContainer sseKafkaContainer = new KafkaContainer("apache/kafka-native:4.3.1")
        .withStartupTimeout(Duration.ofMinutes(2))
        .withStartupAttempts(3)
        .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(SseKafkaTestContainer.class)));

    static String bootstrapServers() {
        return sseKafkaContainer.getHost() + ':' + sseKafkaContainer.getFirstMappedPort();
    }

    /**
     * NOT called {@code registerProperties}, and that is not style.
     *
     * <p>{@link MongoDbTestContainer} declares a static method by that exact name. Two interfaces
     * imported into the same test both contributing {@code registerProperties} means only one of
     * them contributes anything — and the one that loses takes its property with it. Found the hard
     * way: the first run of this test failed with Mongock timing out against
     * {@code localhost:27017}, which reads as a broken Mongo container rather than as a name clash
     * in the harness.
     */
    /* The six topics MarketplaceEventFanout subscribes to, created BEFORE the application context
       starts — which is what this method's timing gives us, since the container is already up.
       
       Without this the consumer subscribes while every topic is UNKNOWN_TOPIC_OR_PARTITION, the
       producer auto-creates them later, and the consumer does not necessarily notice: it is holding
       cached metadata for topics that did not exist, and `metadata.max.age.ms` defaults to five
       minutes. MarketplaceEventFanoutIT only passed because it publishes seconds after startup and
       caught a refresh; MarketplaceStreamFramingIT publishes ~45s in and never saw the message.
       A latent flake in one test and a hard failure in the other, from the same cause. */
    java.util.concurrent.atomic.AtomicBoolean TOPICS_CREATED = new java.util.concurrent.atomic.AtomicBoolean();

    /* Called from a property SUPPLIER, not from registerSseKafkaProperties directly. That method runs
       BEFORE the container starts — the registry.add calls only hand over lazy suppliers — so an
       eager call here fails with "Mapped port can only be obtained after the container is started".
       Piggybacking on the first property resolution puts this exactly where it needs to be: after
       the broker is up, before the application context reads its Kafka settings. */
    static String bootstrapServersWithTopics() {
        String servers = bootstrapServers();
        if (TOPICS_CREATED.compareAndSet(false, true)) createTopics();
        return servers;
    }

    /**
     * The six topics this harness creates, which are <em>meant</em> to be the six
     * {@link net.jojoaddison.service.MarketplaceEventFanout} subscribes to.
     *
     * <p>Named here rather than inside {@link #createTopics} so a test can ask about the set it was
     * given instead of re-listing it — {@code MarketplaceEventFanoutIT} does, and a list re-typed in a
     * second file is how one of them goes quietly out of date.
     *
     * <p><strong>That correspondence is a second list, and it is checked rather than asserted</strong>
     * — D79's review finding. The listener names its topics as
     * {@code ${healthconnect.topics.…:healthconnect.…}} placeholders whose defaults happen to be these
     * literals, so nothing about the two lists ties them together: a seventh topic on the annotation
     * would leave this constant stale in silence, the broker would auto-create it at whatever
     * {@code num.partitions} says, and
     * {@code MarketplaceEventFanoutIT.theBarrierRestsOnOnePartitionPerTopic} would never look at it —
     * which is exactly the "a topic nothing here chose" case that method exists to catch. That test now
     * reads the <strong>resolved</strong> topic set off the running
     * {@code KafkaListenerEndpointRegistry} and requires it to equal this list.
     */
    List<String> TOPICS = List.of(
        "healthconnect.booking.requested",
        "healthconnect.booking.accepted",
        "healthconnect.booking.declined",
        "healthconnect.booking.cancelled",
        "healthconnect.booking.completed",
        "healthconnect.notification.raised"
    );

    /**
     * ONE partition per topic, and it is load-bearing rather than a default nobody thought about —
     * {@code decisions.md} D79.
     *
     * <p>Kafka orders records within a partition and makes no promise across them, so
     * {@code MarketplaceEventFanoutIT}'s barrier — a second event published after the one under test,
     * whose arrival is taken as proof the earlier one has finished emitting — is sound here <em>only</em>
     * because the barrier and its event share a partition. Raise this and nothing fails to compile,
     * no count moves, and that test's exact assertions start racing the emissions they exist to catch.
     *
     * <p>Which is why the test asserts the <strong>broker's</strong> answer against a literal 1 and not
     * against this constant. Changing this number is meant to go red; a test comparing it with itself
     * would adopt the new value and report success. It would also miss the other way in — a topic that
     * already existed on a reused broker with a partition count nothing here chose.
     */
    int PARTITIONS_PER_TOPIC = 1;

    static void createTopics() {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            admin
                .createTopics(
                    TOPICS.stream()
                        .map(t -> new NewTopic(t, PARTITIONS_PER_TOPIC, (short) 1))
                        .toList()
                )
                .all()
                .get();
        } catch (Exception e) {
            throw new IllegalStateException("could not create the SSE test topics", e);
        }
    }

    /**
     * How many partitions the broker says a topic has — asked of the broker, never of
     * {@link #PARTITIONS_PER_TOPIC}.
     *
     * <p>The distinction is the point: this answers what the running estate is, so it catches a changed
     * constant, a topic created by somebody else, and a broker-side {@code num.partitions} alike. It
     * lives here because this interface owns the only {@code AdminClient} in the harness.
     *
     * @throws IllegalStateException if the broker cannot be asked, or does not know the topic — an
     *     unanswered question is never reported as an answer of zero
     */
    static int partitionCountOf(String topic) {
        TopicDescription described;
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            described = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
        } catch (InterruptedException e) {
            // Restore the flag before the exception leaves: swallowing it would strand a harness
            // thread that was asked to stop. Broad catch below for everything else, as in createTopics.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while asking the broker about " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("could not ask the broker how many partitions " + topic + " has", e);
        }
        if (described == null) {
            throw new IllegalStateException("the broker does not know the topic " + topic);
        }
        return described.partitions().size();
    }

    @DynamicPropertySource
    static void registerSseKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", SseKafkaTestContainer::bootstrapServersWithTopics);
        registry.add("spring.cloud.stream.kafka.binder.brokers", SseKafkaTestContainer::bootstrapServersWithTopics);
        // A fresh topic has no committed offset for this run's group, and the consumer must not
        // start at the end and miss a message the test publishes moments later. `earliest` makes the
        // race impossible rather than unlikely — in the estate this is deliberately `latest`, because
        // a live stream has no use for events published while nobody was connected.
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }
}
