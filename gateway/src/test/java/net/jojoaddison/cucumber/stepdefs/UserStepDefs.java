package net.jojoaddison.cucumber.stepdefs;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

public class UserStepDefs extends StepDefs {

    @Autowired
    private WebTestClient webTestClient;

    @When("I list users")
    public void i_list_users() throws Throwable {
        actions = webTestClient.get().uri("/api/admin/users").accept(MediaType.APPLICATION_JSON).exchange();
    }

    @Then("the operation succeeds")
    public void the_operation_succeeds() throws Throwable {
        actions.expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE);
    }
}
