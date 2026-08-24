package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Conversation} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ConversationDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String customerLogin;

    @NotNull
    private String professionalRef;

    private String bookingReference;

    private Instant lastMessageAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCustomerLogin() {
        return customerLogin;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getProfessionalRef() {
        return professionalRef;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationDTO)) {
            return false;
        }

        ConversationDTO conversationDTO = (ConversationDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, conversationDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ConversationDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", bookingReference='" + getBookingReference() + "'" +
            ", lastMessageAt='" + getLastMessageAt() + "'" +
            "}";
    }
}
