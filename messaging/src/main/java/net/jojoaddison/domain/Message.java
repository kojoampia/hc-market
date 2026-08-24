package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.Direction;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Message.
 */
@Entity
@Table(name = "message")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @NotNull
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "messageses" }, allowSetters = true)
    private Conversation conversation;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Message id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public Message direction(Direction direction) {
        this.setDirection(direction);
        return this;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public String getBody() {
        return this.body;
    }

    public Message body(String body) {
        this.setBody(body);
        return this;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getSentAt() {
        return this.sentAt;
    }

    public Message sentAt(Instant sentAt) {
        this.setSentAt(sentAt);
        return this;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getReadAt() {
        return this.readAt;
    }

    public Message readAt(Instant readAt) {
        this.setReadAt(readAt);
        return this;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Message conversation(Conversation conversation) {
        this.setConversation(conversation);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message)) {
            return false;
        }
        return getId() != null && getId().equals(((Message) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Message{" +
            "id=" + getId() +
            ", direction='" + getDirection() + "'" +
            ", body='" + getBody() + "'" +
            ", sentAt='" + getSentAt() + "'" +
            ", readAt='" + getReadAt() + "'" +
            "}";
    }
}
