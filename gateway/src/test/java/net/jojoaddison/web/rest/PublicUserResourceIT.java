package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.User;
import net.jojoaddison.repository.UserRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the {@link PublicUserResource} REST controller.
 */
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_TIMEOUT)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
@IntegrationTest
class PublicUserResourceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private WebTestClient webTestClient;

    private User user;
    private Long numberOfUsers;

    @BeforeEach
    void initTest() {
        numberOfUsers = userRepository.count().block();
        user = UserResourceIT.initTestUser();
    }

    @AfterEach
    void cleanupAndCheck() {
        userService.deleteUser(user.getLogin()).block();
        assertThat(userRepository.count().block()).isEqualTo(numberOfUsers);
        numberOfUsers = null;
    }

    @Test
    void getAllPublicUsers() {
        // Initialize the database
        userRepository.save(user).block();

        // Get all the users
        webTestClient
            .get()
            .uri("/api/users?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[?(@.id == '%s')].login".formatted(user.getId()))
            .isEqualTo(user.getLogin())
            .jsonPath("$.[?(@.id == '%s')].keys()".formatted(user.getId()))
            .isEqualTo(Set.of("id", "login"))
            .jsonPath("$.[*].email")
            .doesNotHaveJsonPath()
            .jsonPath("$.[*].imageUrl")
            .doesNotHaveJsonPath()
            .jsonPath("$.[*].langKey")
            .doesNotHaveJsonPath();
    }
}
