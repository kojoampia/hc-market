package net.jojoaddison.web.rest;

import jakarta.persistence.EntityManager;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.VerificationState;

/**
 * Test fixtures for {@link Professional}.
 *
 * <p><strong>This class holds no tests.</strong> It survives only because the generated
 * {@code AvailabilitySlotResourceIT}, {@code CredentialResourceIT}, {@code HighlightResourceIT} and
 * {@code ServiceOfferingResourceIT} call {@code ProfessionalResourceIT.createEntity(em)} to build
 * their required parent. The generated CRUD tests that used to live here went with the generated
 * {@code ProfessionalResource}, which {@link MarketplaceResource} replaced — see its class comment
 * for why the two cannot coexist.
 *
 * <p>The public marketplace API is covered by {@code MarketplaceResourceIT} instead, which tests
 * the contract spec §6 actually specifies rather than generated CRUD.
 *
 * <p>Keeping the {@code ...IT} name is deliberate: the four callers above are generated files and
 * will be rewritten with this exact reference on any regeneration. Renaming this class would break
 * them again every time.
 */
public final class ProfessionalResourceIT {

    private ProfessionalResourceIT() {}

    /**
     * A valid Professional with every required field set. Persists the required Category too,
     * because {@code Professional.category} is non-null.
     */
    public static Professional createEntity(EntityManager em) {
        Professional professional = new Professional()
            .reference("p-test-" + System.nanoTime())
            .userLogin("test.user." + System.nanoTime())
            .displayName("Test Professional")
            .initials("TP")
            .headline("Test headline")
            .speciality("Nutritionist")
            .city("Accra")
            .countryCode("GH")
            .yearsPractising(5)
            .verification(VerificationState.VERIFIED)
            .insured(true)
            .policeClearance(true)
            .responseMinutes(30)
            .rebookRatePct(50)
            .languages("English")
            .deliveryModes("ONLINE");
        professional.setCategory(persistedCategory(em));
        return professional;
    }

    public static Professional createUpdatedEntity(EntityManager em) {
        Professional professional = createEntity(em);
        professional.displayName("Updated Professional").city("Kumasi").verification(VerificationState.PENDING).insured(false);
        return professional;
    }

    private static Category persistedCategory(EntityManager em) {
        Category category = new Category().code("CAT" + System.nanoTime()).name("Test category").sortOrder(1);
        em.persist(category);
        em.flush();
        return category;
    }
}
