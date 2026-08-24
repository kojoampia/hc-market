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
 */
public enum BookingStatus {
    REQUESTED,
    DECLINED,
    RESCHEDULE_PROPOSED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
}
