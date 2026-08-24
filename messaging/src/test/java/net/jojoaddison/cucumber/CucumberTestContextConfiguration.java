package net.jojoaddison.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@CucumberContextConfiguration
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
public class CucumberTestContextConfiguration {}
