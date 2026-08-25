package net.jojoaddison.domain;

import static net.jojoaddison.domain.FavouriteTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FavouriteTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Favourite.class);
        Favourite favourite1 = getFavouriteSample1();
        Favourite favourite2 = new Favourite();
        assertThat(favourite1).isNotEqualTo(favourite2);

        favourite2.setId(favourite1.getId());
        assertThat(favourite1).isEqualTo(favourite2);

        favourite2 = getFavouriteSample2();
        assertThat(favourite1).isNotEqualTo(favourite2);
    }
}
