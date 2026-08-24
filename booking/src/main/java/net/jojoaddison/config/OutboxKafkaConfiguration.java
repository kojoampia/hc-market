package net.jojoaddison.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * A string-serialising Kafka producer for the outbox.
 *
 * <p>JHipster wires Spring Cloud Stream, which configures the shared producer with
 * {@code ByteArraySerializer}. Injecting the autoconfigured {@code KafkaTemplate} and sending a
 * {@code String} therefore fails with:
 *
 * <pre>Can't convert key of class java.lang.String to class ...ByteArraySerializer</pre>
 *
 * <p>which is a startup-time misconfiguration that only shows up at publish time. The outbox
 * absorbed it exactly as intended — the events stayed unsent and kept retrying rather than being
 * lost — but the right fix is a producer that speaks the format the outbox actually writes.
 *
 * <p>Marked {@code @Primary}-free on purpose: this template is named and injected by name, so the
 * Spring Cloud Stream producer is left alone and JHipster's own messaging keeps working.
 */
@Configuration
public class OutboxKafkaConfiguration {

    /**
     * Bootstrap servers come from configuration directly rather than from Boot's
     * {@code KafkaProperties}: JHipster wires Spring Cloud Stream, not
     * {@code spring-boot-starter-kafka}, so that class is not on the classpath at all.
     */
    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(
        @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers:${spring.cloud.stream.kafka.binder.brokers:localhost:9092}}") String bootstrapServers
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // The publisher marks a row sent only after the broker acknowledges, so the acknowledgement
        // has to mean something: acks=all waits for the in-sync replicas, not just the leader.
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }
}
