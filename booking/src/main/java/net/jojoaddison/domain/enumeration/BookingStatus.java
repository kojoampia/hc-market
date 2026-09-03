package net.jojoaddison.domain.enumeration;

/**
 * D7: the spec declared eight values but its transition diagram reached only six. ACCEPTED was
 * unreachable — accepting a request moves it straight to CONFIRMED, which is what the prototype's
 * PRO_SCHEDULE already assumed — so it is gone. NO_SHOW was equally unreachable but is worth
 * keeping, and now has the transition it was missing.
 *
 * REQUESTED ──accept───▶ CONFIRMED ──complete──▶ COMPLETED
 * │                      │
 * ├──decline──▶ DECLINED  ├──no-show──▶ NO_SHOW
 * ├──propose──▶ RESCHEDULE_PROPOSED ──accept──▶ CONFIRMED
 * └──cancel───▶ CANCELLED ◀──cancel── CONFIRMED
 *
 * The Kafka topic stays `healthconnect.booking.accepted`: it names the act, not the resulting
 * state, and its payload carries status=CONFIRMED.
 *
 * D43 adds PENDING_PAYMENT in front of REQUESTED, and it is the only value that is not part of the
 * machine above:
 *
 * PENDING_PAYMENT ──payment confirmed──▶ REQUESTED
 *        └─────────payment abandoned───▶ CANCELLED
 *
 * It is where a booking waits while the customer approves a payment on their phone or on a
 * provider's page, and it is deliberately invisible to the professional: `/api/pro/requests` asks
 * for REQUESTED and the schedule asks for CONFIRMED, so a pending booking reaches neither, and no
 * `booking.requested` is published until it leaves — so messaging opens no conversation and raises
 * no notification either. A professional learns of a booking when its money is confirmed and not
 * before. Nothing about this reaches the other four services; no other app in the estate knows
 * BookingStatus at all.
 *
 * Kept in step with jdl/booking.jdl by hand, as every value here is: this file is generated, so the
 * JDL has to carry the same value or the next regeneration deletes it.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    REQUESTED,
    DECLINED,
    RESCHEDULE_PROPOSED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
}
