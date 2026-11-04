package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.template.ValidationHelpers;
import io.mockhub.template.ValueHelpers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TemplatePlaygroundTest {

  private final ExecutorService workers = Executors.newFixedThreadPool(2);
  private final TemplatePlayground playground = new TemplatePlayground(List.of(new ValueHelpers(), new ValidationHelpers()), workers, 2000);

  @AfterEach
  void stop() {
    workers.shutdownNow();
  }

  private static TemplatePlayground.Sample sample(String body) {
    return new TemplatePlayground.Sample("POST", "/orders/42?name=Ada", Map.of("X-Trace", "abc"), body);
  }

  @Test
  void rendersTheSampleRequestWithWireMockAndMockHubHelpers() {
    // Given
    String template = "{{request.method}} {{request.path.[1]}} {{request.query.name}} {{request.headers.X-Trace}} "
        + "{{jsonPath request.body '$.item'}} {{len (jsonPath request.body '$.tags')}}";

    // When
    TemplatePlayground.Result result = playground.render(template, sample("{\"item\":\"tea\",\"tags\":[1,2,3]}"));

    // Then
    assertThat(result.error()).isNull();
    assertThat(result.output()).isEqualTo("POST 42 Ada abc tea 3");
  }

  @Test
  void loopsOverASmallRange() {
    // When
    TemplatePlayground.Result result = playground.render("{{#each (range 1 3)}}{{this}},{{/each}}", sample(""));

    // Then
    assertThat(result.output()).isEqualTo("1,2,3,");
  }

  @Test
  void returnsTheStatusAndMessageOfAFailedValidation() {
    // When
    TemplatePlayground.Result result = playground.render("{{required (jsonPath request.body '$.id') 'id is required' status=422}}ok", sample("{}"));

    // Then
    assertThat(result.status()).isEqualTo(422);
    assertThat(result.error()).isEqualTo("id is required");
  }

  @Test
  void refusesHelpersThatReadTheServer() {
    // When
    TemplatePlayground.Result system = playground.render("{{systemValue type='ENVIRONMENT' key='HOME'}}", sample(""));
    TemplatePlayground.Result host = playground.render("{{hostname}}", sample(""));

    // Then
    assertThat(system.error()).isEqualTo("The systemValue helper is turned off in the public playground.");
    assertThat(host.error()).isEqualTo("The hostname helper is turned off in the public playground.");
  }

  @Test
  void refusesPartialsBeforeRendering() {
    // When
    TemplatePlayground.Result result = playground.render("{{#*inline \"a\"}}{{> a}}{{/inline}}{{> a}}", sample(""));

    // Then
    assertThat(result.error()).isEqualTo("Partials are turned off in the public playground.");
  }

  @Test
  void stopsNestedLoopsThatWouldRunTooOften() {
    // Given
    String template = "{{#each (range 1 100)}}{{#each (range 1 100)}}x{{/each}}{{/each}}";

    // When
    TemplatePlayground.Result result = playground.render(template, sample(""));

    // Then
    assertThat(result.error()).contains("Loops in the public playground run up to 2000 times");
  }

  @Test
  void stopsRangesBuiltFromTheRequestBody() {
    // Given
    String template = "{{#each (range 0 (jsonPath request.body '$.n'))}}x{{/each}}";

    // When
    TemplatePlayground.Result result = playground.render(template, sample("{\"n\":1000000000}"));

    // Then
    assertThat(result.error()).contains("go up to 10000");
  }

  @Test
  void stopsTemplatesThatRepeatALargeBody() {
    // Given
    String body = "a".repeat(60_000);
    String template = "{{#each (range 1 1000)}}{{len request.body}}{{/each}}";

    // When
    TemplatePlayground.Result result = playground.render(template, sample(body));

    // Then
    assertThat(result.error()).isEqualTo("The template produces too much text.");
  }

  @Test
  void cutsLongOutput() {
    // Given
    String template = "{{#each (range 1 400)}}{{request.body}}{{/each}}";

    // When
    TemplatePlayground.Result result = playground.render(template, sample("b".repeat(1000)));

    // Then
    assertThat(result.output()).hasSize(TemplatePlayground.MAX_OUTPUT + TemplatePlayground.CUT.length()).endsWith(TemplatePlayground.CUT);
  }

  @Test
  void refusesOversizedInput() {
    // When
    TemplatePlayground.Result template = playground.render("x".repeat(TemplatePlayground.MAX_TEMPLATE + 1), sample(""));
    TemplatePlayground.Result body = playground.render("{{request.body}}", sample("x".repeat(TemplatePlayground.MAX_BODY + 1)));
    TemplatePlayground.Result method = playground.render("x", new TemplatePlayground.Sample("BREW", "/", Map.of(), ""));

    // Then
    assertThat(template.error()).isEqualTo("Keep the template under 8 KB.");
    assertThat(body.error()).isEqualTo("Keep the sample body under 64 KB.");
    assertThat(method.error()).startsWith("Pick one of");
  }

  @Test
  void reportsTemplateSyntaxErrors() {
    // When
    TemplatePlayground.Result result = playground.render("{{#if}}", sample(""));

    // Then
    assertThat(result.output()).isNull();
    assertThat(result.error()).isNotBlank();
  }
}
