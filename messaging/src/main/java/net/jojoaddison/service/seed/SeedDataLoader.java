package net.jojoaddison.service.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Loads {@code demo/seed-data.json} into the messaging service on startup.
 *
 * <h2>Two independent locks</h2>
 *
 * <p>{@code @Profile("test & dev")} requires <strong>both</strong> profiles, never one alone, and
 * {@code @ConditionalOnProperty} requires an explicit opt-in on top of that. Either lock alone
 * would be one mistake away from seeding a real database; together, a production deployment cannot
 * reach this class at all — {@code prod} sets {@code healthconnect.seed.enabled=false} and the seed
 * file is not baked into the production image.
 *
 * <p>This is why spec §14 asks for a test proving {@code prod} refuses to seed <em>even with</em>
 * {@code HEALTHCONNECT_SEED_ENABLED=true}: the property is the weaker of the two locks, and the
 * profile is what actually holds.
 */
@Component
@Profile("test & dev")
@ConditionalOnProperty(name = "healthconnect.seed.enabled", havingValue = "true")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SeedDataLoader.class);
    private static final String EXPECTED_SEED_NAME = "healthconnect-demo-seed";

    private final SeedProperties properties;
    private final MessagingSeeder messagingSeeder;
    private final ObjectMapper objectMapper;

    public SeedDataLoader(SeedProperties properties, MessagingSeeder messagingSeeder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.messagingSeeder = messagingSeeder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (messagingSeeder.alreadySeeded()) {
            LOG.info("seed already present, skipping");
            return;
        }
        messagingSeeder.load(read(), properties.isAnchorDates());
    }

    /**
     * Reads and validates the seed file.
     *
     * <p>The name check is not ceremony. The path is supplied by configuration and mounted from
     * outside the image, so "some other JSON was mounted here" is a real way for this to go wrong,
     * and it should fail loudly at startup rather than half-load something unrecognisable.
     */
    SeedFile read() throws IOException {
        Path path = Path.of(properties.getFile());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("seed file not readable: " + path.toAbsolutePath());
        }
        SeedFile seed = objectMapper.readValue(path.toFile(), SeedFile.class);
        if (seed.meta() == null || !EXPECTED_SEED_NAME.equals(seed.meta().name())) {
            throw new IllegalStateException(path.toAbsolutePath() + " is not a HealthConnect seed file");
        }
        LOG.info("loading seed {} version {} from {}", seed.meta().name(), seed.meta().version(), path.toAbsolutePath());
        return seed;
    }

    /** Used by the reseed endpoint, which truncates first. */
    public void reload() throws IOException {
        messagingSeeder.clear();
        messagingSeeder.load(read(), properties.isAnchorDates());
    }
}
