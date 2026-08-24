package net.jojoaddison.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Notification.
 */
@Entity
@Table(name = "notification")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Notification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "recipient_login", nullable = false)
    private String recipientLogin;

    @NotNull
    @Column(name = "kind", nullable = false)
    private String kind;

    @NotNull
    @Size(max = 400)
    @Column(name = "body", length = 400, nullable = false)
    private String body;

    @NotNull
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deep_link")
    private String deepLink;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Notification id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipientLogin() {
        return this.recipientLogin;
    }

    public Notification recipientLogin(String recipientLogin) {
        this.setRecipientLogin(recipientLogin);
        return this;
    }

    public void setRecipientLogin(String recipientLogin) {
        this.recipientLogin = recipientLogin;
    }

    public String getKind() {
        return this.kind;
    }

    public Notification kind(String kind) {
        this.setKind(kind);
        return this;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getBody() {
        return this.body;
    }

    public Notification body(String body) {
        this.setBody(body);
        return this;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getRaisedAt() {
        return this.raisedAt;
    }

    public Notification raisedAt(Instant raisedAt) {
        this.setRaisedAt(raisedAt);
        return this;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getReadAt() {
        return this.readAt;
    }

    public Notification readAt(Instant readAt) {
        this.setReadAt(readAt);
        return this;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public String getDeepLink() {
        return this.deepLink;
    }

    public Notification deepLink(String deepLink) {
        this.setDeepLink(deepLink);
        return this;
    }

    public void setDeepLink(String deepLink) {
        this.deepLink = deepLink;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification)) {
            return false;
        }
        return getId() != null && getId().equals(((Notification) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Notification{" +
            "id=" + getId() +
            ", recipientLogin='" + getRecipientLogin() + "'" +
            ", kind='" + getKind() + "'" +
            ", body='" + getBody() + "'" +
            ", raisedAt='" + getRaisedAt() + "'" +
            ", readAt='" + getReadAt() + "'" +
            ", deepLink='" + getDeepLink() + "'" +
            "}";
    }
}
