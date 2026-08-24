package net.jojoaddison.domain.enumeration;

/**
 * SYSTEM covers anything raised by a domain event rather than typed by a person.
 */
public enum Direction {
    CUSTOMER_TO_PROFESSIONAL,
    PROFESSIONAL_TO_CUSTOMER,
    SYSTEM,
}
