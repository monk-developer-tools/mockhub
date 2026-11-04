package io.mockhub.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValidationFailureTest {

  @Test
  void theMessageAndStatusSurviveBeingWrappedInATemplateError() {
    // Given
    ValidationFailure failure = new ValidationFailure(422, "name is required: “é” & <b>");

    // When
    var found = ValidationFailure.in("inline@1a2b:1:5: " + failure.getMessage() + " (more context)");

    // Then
    assertThat(found).hasValueSatisfying(f -> {
      assertThat(f.status()).isEqualTo(422);
      assertThat(f.message()).isEqualTo("name is required: “é” & <b>");
    });
    assertThat(ValidationFailure.in("Some other template error")).isEmpty();
  }
}
