package net.jojoaddison.config;

import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Sample Kafka Test Container configuration for integration tests. This will start a Kafka container before the tests and stop it afterwards.
 * To use it, add @ImportTestcontainers(KafkaTestContainer.class) to your test class.
 */
public interface KafkaTestContainer {
    @Container
    KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka-native:4.3.1")
        .withStartupTimeout(Duration.ofMinutes(2))
        .withStartupAttempts(3)
        .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(KafkaTestContainer.class)));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "spring.cloud.stream.kafka.binder.brokers",
            () -> kafkaContainer.getHost() + ':' + kafkaContainer.getFirstMappedPort()
        );
    }
}
