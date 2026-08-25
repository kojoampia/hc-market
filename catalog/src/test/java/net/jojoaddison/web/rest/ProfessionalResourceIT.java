package net.jojoaddison.web.rest;

import jakarta.persistence.EntityManager;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.VerificationState;

/**
 * Test fixtures for {@link Professional}.
 *
 * <p><strong>This class holds no tests.</strong> It exists because the generated
 * {@code AvailabilitySlotResourceIT}, {@code CredentialResourceIT}, {@code HighlightResourceIT} and
 * {@code ServiceOfferingResourceIT} call it to build their required parent. The generated CRUD suite
 * that JHipster puts here went with the generated {@code ProfessionalResource}, which
 * {@link MarketplaceResource} replaced — the two cannot coexist on {@code /api/professionals/{id}}
 * vs {@code /{ref}}.
 *
 * <p><strong>Regeneration restores those tests over this file.</strong> Deleting
 * {@code ProfessionalResource.java} afterwards is not enough; this has to be rewritten too, or the
 * suite fails against endpoints that no longer exist.
 */
public final class ProfessionalResourceIT {

    private ProfessionalResourceIT() {}

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
        return createEntity(em).displayName("Updated Professional").city("Kumasi").verification(VerificationState.PENDING).insured(false);
    }

    private static Category persistedCategory(EntityManager em) {
        Category category = new Category().code("CAT" + System.nanoTime()).name("Test category").sortOrder(1);
        em.persist(category);
        em.flush();
        return category;
    }
}
