package net.jojoaddison;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jojoaddison.config.AsyncSyncConfiguration;
import net.jojoaddison.config.DatabaseTestcontainer;
import net.jojoaddison.config.JacksonConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        HealthconnectBookingApp.class,
        JacksonConfiguration.class,
        AsyncSyncConfiguration.class,
        net.jojoaddison.config.JacksonHibernateConfiguration.class,
    }
)
@ImportTestcontainers(DatabaseTestcontainer.class)
public @interface IntegrationTest {}
