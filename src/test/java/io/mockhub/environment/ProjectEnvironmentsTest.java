package io.mockhub.environment;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mockhub.api.ApiException;
import org.junit.jupiter.api.Test;

class ProjectEnvironmentsTest {

  private static final ProjectEnvironments DEV_QA = ProjectEnvironments.parse(json(
      "{'active':'dev','environments':[{'name':'dev','variables':{'baseUrl':'https://dev.example.com'}},{'name':'qa','variables':{'baseUrl':'https://qa.example.com','retries':3}}]}"));

  @Test
  void picksTheRequestedEnvironmentOrFallsBackToTheActiveOne() {
    // When / Then
    assertThat(DEV_QA.effective("qa")).get().extracting(Environment::name).isEqualTo("qa");
    assertThat(DEV_QA.effective(" QA ")).get().extracting(Environment::name).isEqualTo("qa");
    assertThat(DEV_QA.effective("prod")).get().extracting(Environment::name).isEqualTo("dev");
    assertThat(DEV_QA.effective(null)).get().extracting(Environment::name).isEqualTo("dev");
    assertThat(ProjectEnvironments.NONE.effective("qa")).isEmpty();
  }

  @Test
  void keepsValuesAsTextAndTheFirstEnvironmentActiveWhenNoneIsChosen() {
    // When
    ProjectEnvironments parsed = ProjectEnvironments.parse(json("{'environments':[{'name':'qa','variables':{'n':3,'on':true}},{'name':'dev'}]}"));

    // Then
    assertThat(parsed.active()).isEqualTo("qa");
    assertThat(parsed.find("qa")).get().extracting(Environment::variables).isEqualTo(java.util.Map.of("n", "3", "on", "true"));
    assertThat(DEV_QA.find("qa").orElseThrow().variables().get("retries")).isEqualTo("3");
  }

  @Test
  void explainsInvalidSetups() {
    // When / Then
    assertThatThrownBy(() -> ProjectEnvironments.parse(json("{'environments':[{'name':'QA env'}]}"))).isInstanceOf(ApiException.class)
        .hasMessageContaining("lowercase letters");
    assertThatThrownBy(() -> ProjectEnvironments.parse(json("{'environments':[{'name':'qa'},{'name':'qa'}]}"))).hasMessageContaining("two environments called qa");
    assertThatThrownBy(() -> ProjectEnvironments.parse(json("{'environments':[{'name':'qa','variables':{'bad name':1}}]}"))).hasMessageContaining("bad name");
    assertThatThrownBy(() -> ProjectEnvironments.parse(json("{'active':'prod','environments':[{'name':'qa'}]}"))).hasMessageContaining("isn't in the list");
    assertThatThrownBy(() -> DEV_QA.withActive("prod")).hasMessageContaining("prod");
  }
}
