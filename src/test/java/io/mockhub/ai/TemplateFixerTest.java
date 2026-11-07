package io.mockhub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TemplateFixerTest {

  private final AtomicReference<String> system = new AtomicReference<>();
  private final AtomicReference<String> user = new AtomicReference<>();

  private TemplateFixer fixer(String reply, String helpers, int max) {
    return new TemplateFixer((s, u) -> {
      system.set(s);
      user.set(u);
      return reply;
    }, helpers, max);
  }

  @Test
  void sendsTheTemplateErrorAndRequestAndReturnsTheFencedFix() {
    // Given
    TemplateFixer fixer = fixer("Here you go:\n```handlebars\n{\"id\": \"{{jsonPath request.body '$.id'}}\" }\n```\nDone.", TemplateFixer.PROJECT_HELPERS, 1024);

    // When
    String fixed = fixer.fix("{\"id\": {{jsonPath request.body '$.id'}}}", "found: '}}}', expected: '}}'", "POST /orders\n{\"id\": 7}");

    // Then
    assertThat(fixed).isEqualTo("{\"id\": \"{{jsonPath request.body '$.id'}}\" }");
    assertThat(system.get()).startsWith("You fix Handlebars").contains("dataset-one").contains("{{#switch x}}").doesNotContain("savedList");
    assertThat(user.get()).contains("Template:\n{\"id\"").contains("Error:\nfound: '}}}'").contains("A request it answers:\nPOST /orders");
  }

  @Test
  void binsGetTheirOwnHelpersAndAnUnfencedReplyIsUsedAsIs() {
    // Given
    TemplateFixer fixer = fixer("{{counter 'orders'}}", TemplateFixer.BIN_HELPERS, 1024);

    // When
    String fixed = fixer.fix("{{counter orders}}", "", "");

    // Then
    assertThat(fixed).isEqualTo("{{counter 'orders'}}");
    assertThat(system.get()).contains("savedList").doesNotContain("dataset-one");
    assertThat(user.get()).contains("(none given; it doesn't do what was meant)").doesNotContain("A request it answers");
  }

  @Test
  void refusesEmptyOrOversizedTemplatesAndEmptyAnswers() {
    // Given
    TemplateFixer empty = fixer("```\n```", TemplateFixer.PROJECT_HELPERS, 16 * 1024);
    TemplateFixer huge = fixer("x".repeat(17 * 1024), TemplateFixer.PROJECT_HELPERS, 16 * 1024);

    // When / Then
    assertThatThrownBy(() -> empty.fix(" ", "", "")).hasMessage("Send the template to fix");
    assertThatThrownBy(() -> empty.fix("x".repeat(17 * 1024), "", "")).hasMessage("Keep the template under 16 KB");
    assertThatThrownBy(() -> empty.fix("{{x}", "", "")).hasMessage("The AI didn't return a template. Try again.");
    assertThatThrownBy(() -> huge.fix("{{x}", "", "")).hasMessage("The AI's template was over 16 KB");
  }
}
