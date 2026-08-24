package net.jojoaddison.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.test.context.support.WithMockUser;

@CucumberContextConfiguration
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_TIMEOUT)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
public class CucumberTestContextConfiguration {}
