package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Conversation.
 */
@Entity
@Table(name = "conversation")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Conversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @NotNull
    @Column(name = "customer_login", nullable = false)
    private String customerLogin;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

    @Column(name = "booking_reference")
    private String bookingReference;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "conversation")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "conversation" }, allowSetters = true)
    private Set<Message> messageses = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Conversation id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Conversation reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCustomerLogin() {
        return this.customerLogin;
    }

    public Conversation customerLogin(String customerLogin) {
        this.setCustomerLogin(customerLogin);
        return this;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Conversation professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    public Conversation bookingReference(String bookingReference) {
        this.setBookingReference(bookingReference);
        return this;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Instant getLastMessageAt() {
        return this.lastMessageAt;
    }

    public Conversation lastMessageAt(Instant lastMessageAt) {
        this.setLastMessageAt(lastMessageAt);
        return this;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Set<Message> getMessageses() {
        return this.messageses;
    }

    public void setMessageses(Set<Message> messages) {
        if (this.messageses != null) {
            this.messageses.forEach(i -> i.setConversation(null));
        }
        if (messages != null) {
            messages.forEach(i -> i.setConversation(this));
        }
        this.messageses = messages;
    }

    public Conversation messageses(Set<Message> messages) {
        this.setMessageses(messages);
        return this;
    }

    public Conversation addMessages(Message message) {
        this.messageses.add(message);
        message.setConversation(this);
        return this;
    }

    public Conversation removeMessages(Message message) {
        this.messageses.remove(message);
        message.setConversation(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conversation)) {
            return false;
        }
        return getId() != null && getId().equals(((Conversation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Conversation{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", bookingReference='" + getBookingReference() + "'" +
            ", lastMessageAt='" + getLastMessageAt() + "'" +
            "}";
    }
}
