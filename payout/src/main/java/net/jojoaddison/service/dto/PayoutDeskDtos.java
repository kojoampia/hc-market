package net.jojoaddison.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The brokerage desk's payout surface — {@code decisions.md} D95, backlog NEW-51.
 *
 * <p>New records rather than the generated {@code PayoutDTO}, which a regeneration rewrites and which
 * exposes {@code id} and the whole entity including its {@code entrieses} collection. What the desk
 * sends and what it is shown are a contract; a mapstruct DTO is a mirror of a table.
 *
 * <p>All money is minor units (pesewas) and {@code grossMinor - commissionMinor == netMinor} holds on
 * every batch, exactly as it does on every ledger row — {@link net.jojoaddison.service.PayoutRun}
 * refuses a batch where it does not, because a settlement figure nobody can reconcile is worse than
 * no settlement.
 */
public final class PayoutDeskDtos {

    private PayoutDeskDtos() {}

    /**
     * What the desk asks for: one professional, one inclusive period.
     *
     * <p>{@code professionalRef} and not a login. {@code Payout} is keyed by the reference, the ledger
     * carries both, and the desk works from the catalogue's references. There is deliberately no
     * "everybody" form: a run that batches the whole estate in one call is a single button that moves
     * every professional's money, and nothing has asked for it.
     */
    public record OpenBatch(
        @NotBlank @Size(max = 100) String professionalRef,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
    ) {}

    /**
     * What a human supplies after they have made the transfer.
     *
     * <p>Both fields are required, and that is the point of the endpoint rather than a validation
     * detail: {@code PAID} without a {@code bankReference} is a claim that money moved with nothing
     * to check it against, which is precisely the state a reconciliation cannot resolve. Act 987
     * gates this platform <em>sending</em> money through a provider API, which is why
     * {@code PaymentProvider} has no settlement call and must not gain one — recording a transfer a
     * person already made needs no licence, and these two fields are the model's own answer to that
     * (D95, backlog NEW-51).
     */
    public record Settle(@NotNull LocalDate settledOn, @NotBlank @Size(max = 100) String bankReference) {}

    /**
     * One batch, as the desk is shown it.
     *
     * <p>{@code entries} is the number of ledger rows attached. It is on the view because it is the
     * only figure that shows the attachment happened — see
     * {@code SettlementLedgerRepository.countByPayoutId}.
     */
    public record BatchView(
        String reference,
        String professionalRef,
        LocalDate periodStart,
        LocalDate periodEnd,
        long grossMinor,
        long commissionMinor,
        long netMinor,
        String currency,
        String status,
        LocalDate settledOn,
        String bankReference,
        long entries
    ) {}
}
