package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * The derived rating of a professional, read from the {@code professional_rating} view.
 *
 * <p>This class exists so that {@link Professional} never needs a {@code rating} or
 * {@code reviewCount} column. It is the persistence-layer expression of the rule the prototype
 * enforced by computing every figure at render time: <strong>a rating that disagrees with its
 * reviews is a bug the schema should make impossible.</strong>
 *
 * <p>{@link Immutable} is not decoration. It tells Hibernate this is read-only, so an accidental
 * {@code save()} of a rating fails at the mapping layer rather than silently attempting an UPDATE
 * against a view. There is deliberately no setter and no repository write method.
 *
 * <p>A professional with no reviews has <em>no row here at all</em> — the view is built with
 * {@code GROUP BY}, so it cannot manufacture a 0.0 rating for someone who has simply never been
 * reviewed. Callers must distinguish "unrated" from "rated zero"; {@code ProfessionalService}
 * does this by returning an empty rating rather than a zeroed one.
 */
@Entity
@Immutable
@Table(name = "professional_rating")
public class ProfessionalRating implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The view is keyed by the professional it summarises; there is exactly one row per professional. */
    @Id
    @Column(name = "professional_id")
    private Long professionalId;

    /** {@code AVG(stars)} rounded to one decimal, matching the prototype's display precision. */
    @Column(name = "rating")
    private BigDecimal rating;

    @Column(name = "review_count")
    private Long reviewCount;

    public Long getProfessionalId() {
        return professionalId;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessionalRating other)) {
            return false;
        }
        return professionalId != null && professionalId.equals(other.professionalId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProfessionalRating{professionalId=" + professionalId + ", rating=" + rating + ", reviewCount=" + reviewCount + "}";
    }
}
