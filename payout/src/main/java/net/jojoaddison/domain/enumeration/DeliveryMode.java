package net.jojoaddison.domain.enumeration;

/**
 * Mirrors the booking service's enum. Duplicated rather than shared because each service owns its
 * own schema and must be deployable without the others.
 */
public enum DeliveryMode {
    IN_PERSON,
    ONLINE,
    HOME_VISIT,
}
