package net.jojoaddison.service.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the demo seed loader, under {@code healthconnect.seed}.
 *
 * <p>Registered with {@link Component} rather than by adding it to
 * {@code @EnableConfigurationProperties} on the application class. That is deliberate:
 * {@code jhipster jdl --force} rewrites every generated file and discards edits to them, so an
 * entry on the generated app class would vanish on the next regeneration and the seed would
 * silently stop loading. A new file with its own annotation survives.
 *
 * <p>{@link #enabled} is the second of two independent locks. The first is the {@code test & dev}
 * profile pair on {@link SeedDataLoader}. Both must hold, because "demo data appeared in
 * production" is the kind of incident that ends a brokerage, and one lock is one mistake away from
 * being off.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.seed")
public class SeedProperties {

    /** Master switch. {@code prod} sets this false and the seed file is not in the production image. */
    private boolean enabled = false;

    /** Path to the seed JSON. Mounted into the container by the dev compose file. */
    private String file = "demo/seed-data.json";

    /**
     * When false (the default), every date in the seed is shifted by {@code today - $meta.demoToday}
     * so a demo run months from now still shows "tomorrow" and a live month-to-date figure. Set true
     * to load the dates exactly as written, which is what the verification tests want.
     */
    private boolean anchorDates = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isAnchorDates() {
        return anchorDates;
    }

    public void setAnchorDates(boolean anchorDates) {
        this.anchorDates = anchorDates;
    }
}
