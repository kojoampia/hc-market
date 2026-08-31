package net.jojoaddison.config;

import java.time.Duration;
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
    @DynamicPropertySource
    static void registerSseKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", SseKafkaTestContainer::bootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.brokers", SseKafkaTestContainer::bootstrapServers);
        // A fresh topic has no committed offset for this run's group, and the consumer must not
        // start at the end and miss a message the test publishes moments later. `earliest` makes the
        // race impossible rather than unlikely — in the estate this is deliberately `latest`, because
        // a live stream has no use for events published while nobody was connected.
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }
}
