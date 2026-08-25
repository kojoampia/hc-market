package net.jojoaddison.service.dto.marketplace;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * The professional workspace's shapes — spec §6, "Professional workspace".
 *
 * <p>These differ from the public ones in two ways that matter. They show <strong>hidden</strong>
 * services, because a listing editor that cannot see what it has hidden is not an editor. And they
 * carry no professional reference: every endpoint resolves the caller from the JWT, so a reference
 * in the payload would be either redundant or an attempt to act as someone else.
 */
public final class ProDtos {

    private ProDtos() {}

    // ------------------------------------------------------------------- services --

    /** A service as its owner sees it, including {@code active} — the live/hidden flag. */
    public record OwnedService(
        String ref,
        String name,
        Integer durationMinutes,
        long priceMinor,
        String currency,
        String description,
        boolean active,
        Integer sortOrder
    ) {}

    public record SaveService(
        @NotBlank @Size(max = 255) String name,
        @Min(0) Integer durationMinutes,
        @NotNull @Min(0) Long priceMinor,
        @Size(max = 3) String currency,
        @Size(max = 500) String description,
        Integer sortOrder
    ) {}

    // -------------------------------------------------------------------- profile --

    /**
     * The listing editor. {@code verification}, {@code insured} and {@code policeClearance} are
     * shown but <strong>not editable</strong> — a professional who could set their own verified flag
     * is a trust chain with a hole in it. Those move only through the admin queue (spec §13 #3).
     */
    public record OwnedProfile(
        String ref,
        String displayName,
        String headline,
        String speciality,
        String city,
        String countryCode,
        Integer yearsPractising,
        String bio,
        List<String> languages,
        List<String> deliveryModes,
        Integer responseMinutes,
        List<String> credentials,
        List<String> highlights,
        String verification,
        boolean insured,
        boolean policeClearance
    ) {}

    public record SaveProfile(
        @Size(max = 255) String displayName,
        @Size(max = 160) String headline,
        @Size(max = 255) String speciality,
        @Size(max = 255) String city,
        @Min(0) Integer yearsPractising,
        String bio,
        List<String> languages,
        List<String> deliveryModes,
        @Min(0) Integer responseMinutes,
        List<String> credentials,
        List<String> highlights
    ) {}

    /** The verification checklist the profile screen shows — read-only, and derived. */
    public record VerificationChecklist(String verification, boolean insured, boolean policeClearance, boolean hasCredentials, boolean published) {}

    // --------------------------------------------------------------- availability --

    /** One day of working hours, as the editor renders it. */
    public record WorkingDay(LocalDate date, List<Slot> slots) {}

    public record Slot(String time, boolean taken) {}

    /**
     * Replacing the slots for a day.
     *
     * <p>A day is replaced whole rather than patched slot by slot, because "these are my hours on
     * Tuesday" is how a professional thinks about it. Slots already taken are never removed — see
     * the resource.
     */
    public record SetWorkingDay(@NotNull LocalDate date, @NotNull List<@NotBlank @Size(max = 5) String> slots) {}

    // ------------------------------------------------------------------ availability rules --
    //
    // decisions.md D20. The rule is what a professional edits; the slot is what a customer books.
    // Times cross the wire as HH:mm, the same five characters slots have always used, so nothing
    // downstream has to learn a second time format.

    public record RuleView(
        Long id,
        String weekday,
        String startTime,
        String endTime,
        Integer slotMinutes,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active
    ) {}

    public record SaveRule(
        @NotBlank String weekday,
        @NotBlank @Size(max = 5) String startTime,
        @NotBlank @Size(max = 5) String endTime,
        @NotNull Integer slotMinutes,
        @NotNull LocalDate validFrom,
        LocalDate validUntil,
        Boolean active
    ) {}

    /** {@code closed} true means no sessions that day; false with a window means those hours instead. */
    public record OverrideView(Long id, LocalDate date, boolean closed, String startTime, String endTime, String note) {}

    public record SaveOverride(
        @NotNull LocalDate date,
        @NotNull Boolean closed,
        @Size(max = 5) String startTime,
        @Size(max = 5) String endTime,
        @Size(max = 200) String note
    ) {}

    /** What a generation run actually did, so the professional sees the effect rather than assuming it. */
    public record GeneratedView(LocalDate from, LocalDate to, int created, int removed, int daysClosed, int keptBecauseTaken) {}

    // -------------------------------------------------------------------- reviews --

    /**
     * A review as its subject sees it. Same fields as the public view plus {@code canReply}, so the
     * screen does not have to infer from a null reply whether replying is still open.
     */
    public record OwnedReview(
        String ref,
        String authorName,
        String authorInitials,
        int stars,
        LocalDate publishedOn,
        String body,
        String professionalReply,
        boolean canReply
    ) {}

    public record PublishReply(@NotBlank @Size(max = 2000) String body) {}

    /** The distribution beside the reviews list — five buckets, one star to five. */
    public record ReviewSummary(List<OwnedReview> reviews, List<Integer> starDistribution, long total, long replied) {}
}
