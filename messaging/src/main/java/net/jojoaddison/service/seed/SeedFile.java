package net.jojoaddison.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The messaging service's slice of {@code demo/seed-data.json}: conversations and notifications.
 *
 * <p>The seed calls them {@code threads}, matching the REST path and the prototype's screen. The
 * entity is {@code Conversation} because a JHipster entity named {@code Thread} shadows
 * {@code java.lang.Thread} inside its own package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedFile(@JsonProperty("$meta") Meta meta, List<SeedThread> threads, List<SeedNotification> notifications) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, LocalDate demoToday, String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedThread(
        String ref,
        String customerLogin,
        String professionalRef,
        String bookingReference,
        List<SeedMessage> messages
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedMessage(Integer seq, String direction, Instant sentAt, String body, Boolean read) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedNotification(
        String ref,
        String recipientLogin,
        String kind,
        String body,
        LocalDate raisedOn,
        Boolean read
    ) {}
}
