package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.domain.ServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The read queries behind the public marketplace API.
 *
 * <p>Kept separate from the generated per-entity repositories so that regenerating the catalog from
 * JDL cannot silently drop them.
 */
@Repository
public interface MarketplaceQueryRepository extends org.springframework.data.jpa.repository.JpaRepository<Professional, Long> {
    Optional<Professional> findByReference(String reference);

    @Query("select count(p) from Professional p where p.category.code = :code")
    long countByCategoryCode(@Param("code") String code);

    /** Derived, not stored: the specialities actually present in a category. */
    @Query("select distinct p.speciality from Professional p where p.category.code = :code order by p.speciality")
    List<String> findSpecialitiesByCategoryCode(@Param("code") String code);

    @Query("select s from ServiceOffering s where s.professional.reference = :ref and s.active = true order by s.sortOrder")
    List<ServiceOffering> findActiveServices(@Param("ref") String reference);

    /**
     * The two "from" prices of a professional's ACTIVE services, in one row — {@code decisions.md}
     * D100, backlog NEW-50.
     *
     * <p>Column 0 is the <strong>literal</strong> minimum and column 1 is the cheapest service with
     * a price on it. They differ only for a professional who publishes something free, and both are
     * null when nothing is published at all.
     *
     * <p><strong>This javadoc used to claim the single minimum was "exactly as the prototype
     * computed it", and that was false.</strong> The prototype filters {@code s.price > 0} at three
     * sites; this query did not, so for the two seeded professionals with a free service the two
     * disagreed — the API answered {@code 0} where the prototype showed ₵280 and ₵420. The claim was
     * a parity assertion made in the one file that could have settled it, and settled nothing. Both
     * quantities are returned now and each is named for what it is, so neither has to stand in for
     * the other.
     *
     * <p><strong>One query, two columns, deliberately.</strong> {@link MarketplaceService#toCard}
     * calls this once per card and browse maps the whole catalogue, so a second finder would double
     * the statement count on the busiest public read for a quantity derivable in the same pass.
     *
     * <p><strong>{@code List<Object[]>}, not {@code Object[]}</strong> — the trap CLAUDE.md records
     * against {@code EarningsRepository.lifetime}. A single-row aggregate declared as {@code
     * Object[]} hands back the <em>list</em> wrapped in an array, so the first element is itself an
     * {@code Object[]} and the first cast to {@code Number} fails with a 500 that says nothing about
     * why. An aggregate with no {@code group by} always yields exactly one row, of two nulls when
     * nothing matched — the caller treats an empty list as that same answer rather than trusting it.
     */
    @Query(
        "select min(s.priceMinor), min(case when s.priceMinor > 0 then s.priceMinor else null end) " +
        "from ServiceOffering s where s.professional.reference = :ref and s.active = true"
    )
    List<Object[]> findPriceFloors(@Param("ref") String reference);

    @Query("select s from AvailabilitySlot s where s.professional.reference = :ref and s.taken = false " +
           "and s.slotDate >= :from and s.slotDate <= :to order by s.slotDate, s.slotTime")
    List<AvailabilitySlot> findFreeSlots(@Param("ref") String reference, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select r from Review r where r.professional.reference = :ref order by r.publishedOn desc")
    Page<Review> findReviews(@Param("ref") String reference, Pageable pageable);

    /** The five-bar review distribution on the profile screen. */
    @Query("select r.stars, count(r) from Review r where r.professional.reference = :ref group by r.stars")
    List<Object[]> countReviewsByStars(@Param("ref") String reference);

    @Query("select count(r) from Review r")
    long countAllReviews();

    // ------------------------------------------------------ the professional workspace --

    Optional<Professional> findByUserLogin(String userLogin);

    /** The editor lists hidden services too — that is the whole point of a publish toggle. */
    @Query("select s from ServiceOffering s where s.professional.userLogin = :login order by s.sortOrder, s.name")
    List<ServiceOffering> findAllServicesForOwner(@Param("login") String login);

    @Query("select s from ServiceOffering s where s.reference = :ref and s.professional.userLogin = :login")
    Optional<ServiceOffering> findOwnedService(@Param("ref") String reference, @Param("login") String login);

    @Query("select r from Review r where r.professional.userLogin = :login order by r.publishedOn desc")
    List<Review> findReviewsForOwner(@Param("login") String login);

    @Query("select r from Review r where r.reference = :ref and r.professional.userLogin = :login")
    Optional<Review> findOwnedReview(@Param("ref") String reference, @Param("login") String login);

    /** Working hours: every slot the professional holds, taken or not, from today onward. */
    @Query("select s from AvailabilitySlot s where s.professional.userLogin = :login and s.slotDate >= :from order by s.slotDate, s.slotTime")
    List<AvailabilitySlot> findOwnedSlots(@Param("login") String login, @Param("from") LocalDate from);
}
