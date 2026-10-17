package io.mockhub.stub;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class StubSwitchTest {

  @Test
  void aDisabledStubGetsAMatcherThatNeverMatches() {
    // Given
    ObjectNode stub = json("{'request':{'urlPath':'/a'},'metadata':{'mockhub':{'disabled':true}}}");

    // When
    StubSwitch.apply(stub);

    // Then
    assertThat(stub.at("/request/customMatcher/name").asText()).isEqualTo(StubSwitch.MATCHER);
    assertThat(stub.at("/metadata/mockhub/disabled").asBoolean()).isTrue();
  }

  @Test
  void enablingRemovesTheMatcherAndTheFlag() {
    // Given
    ObjectNode stub = json("{'request':{'urlPath':'/a','customMatcher':{'name':'mockhub-disabled'}},'metadata':{'mockhub':{'disabled':false}}}");

    // When
    StubSwitch.apply(stub);

    // Then
    assertThat(stub.at("/request").has("customMatcher")).isFalse();
    assertThat(stub.at("/metadata/mockhub").has("disabled")).isFalse();
  }

  @Test
  void keepsAnExistingCustomMatcherWhileDisabledAndRestoresIt() {
    // Given
    ObjectNode stub = json("{'request':{'customMatcher':{'name':'my-matcher','parameters':{'x':1}}},'metadata':{'mockhub':{'disabled':true}}}");

    // When
    StubSwitch.apply(stub);
    ObjectNode shown = stub.deepCopy();
    StubSwitch.reveal(shown);
    ((ObjectNode) stub.at("/metadata/mockhub")).put("disabled", false);
    StubSwitch.apply(stub);

    // Then
    assertThat(shown.at("/request/customMatcher/name").asText()).isEqualTo("my-matcher");
    assertThat(stub.at("/request/customMatcher/name").asText()).isEqualTo("my-matcher");
    assertThat(stub.at("/request/customMatcher/parameters/x").asInt()).isEqualTo(1);
  }

  @Test
  void applyingTwiceDoesNotNestTheMatcher() {
    // Given
    ObjectNode stub = json("{'request':{'urlPath':'/a'},'metadata':{'mockhub':{'disabled':true}}}");

    // When
    StubSwitch.apply(stub);
    StubSwitch.apply(stub);

    // Then
    assertThat(stub.at("/request/customMatcher").has("parameters")).isFalse();
  }
}
