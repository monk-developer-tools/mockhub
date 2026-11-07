package io.mockhub.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.WireMockHelpers;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PortableHelpersTest {

  private final Handlebars handlebars = engine();

  private static Handlebars engine() {
    Handlebars hb = new Handlebars().with(EscapingStrategy.NOOP);
    for (WireMockHelpers helper : WireMockHelpers.values()) hb.registerHelper(helper.name(), helper);
    List<TemplateHelperProviderExtension> providers = List.of(new ValueHelpers(), new ValidationHelpers(), new FlowHelpers((min, max) -> max),
        new CollectionHelpers(() -> new Random(7)), new TextHelpers(), new DateMathHelpers());
    providers.forEach(p -> p.provideTemplateHelpers().forEach(hb::registerHelper));
    return hb;
  }

  private String render(String template, Map<String, Object> model) throws IOException {
    return handlebars.compileInline(template).apply(model);
  }

  private String render(String template) throws IOException {
    return render(template, Map.of());
  }

  @Test
  void switchPicksTheMatchingCaseOrTheDefaultAndNests() throws IOException {
    // Given
    String template = "{\"light\": {{#switch color}}{{#case 'red'}}\"Stop\"{{/case}}{{#case 'green'}}\"Go\"{{/case}}"
        + "{{#default}}\"Unknown\"{{/default}}{{/switch}} }";
    String nested = "{{#switch a}}{{#case 'x'}}{{#switch b}}{{#case 'y'}}xy{{/case}}{{#default}}x?{{/default}}{{/switch}}{{/case}}"
        + "{{#default}}none{{/default}}{{/switch}}";

    // When
    String green = render(template, Map.of("color", "green"));
    String blue = render(template, Map.of("color", "blue"));
    String inner = render(nested, Map.of("a", "x", "b", "z"));

    // Then
    assertThat(green).isEqualTo("{\"light\": \"Go\" }");
    assertThat(blue).isEqualTo("{\"light\": \"Unknown\" }");
    assertThat(inner).isEqualTo("x?");
    assertThatThrownBy(() -> render("{{#case 'a'}}x{{/case}}")).hasMessageContaining("only works inside {{#switch}}");
    assertThat(render("{{default missing 'fallback'}}")).isEqualTo("fallback");
  }

  @Test
  void repeatLoopsWithCommasIndexAndTotalAndCapsAtAThousand() throws IOException {
    // Given
    String fixed = "[{{#repeat 2}}{\"n\": {{@index}} }{{/repeat}}]";

    // When
    String two = render(fixed);
    String plain = render("{{#repeat 3 comma=false}}{{@index}}/{{@total}} {{/repeat}}");
    String ranged = render("{{#repeat 1 4}}x{{/repeat}}");
    String capped = render("{{#repeat 5000 comma=false}}.{{/repeat}}");

    // Then
    assertThat(two).isEqualTo("[{\"n\": 0 },{\"n\": 1 }]");
    assertThat(plain).isEqualTo("0/3 1/3 2/3 ");
    assertThat(ranged).isEqualTo("x,x,x,x");
    assertThat(capped).hasSize(FlowHelpers.MAX_REPEAT);
    assertThatThrownBy(() -> render("{{#repeat 'many'}}x{{/repeat}}")).hasMessageContaining("whole number");
  }

  @Test
  void sortsReversesAndPicksFromLists() throws IOException {
    // Given
    Map<String, Object> model = Map.of("items", List.of(Map.of("name", "b", "price", 30), Map.of("name", "a", "price", 5), Map.of("name", "c", "price", 12)));

    // When
    String words = render("{{sort (array 'lion' 'zebra' 'alligator' 'elephant')}}");
    String numbers = render("{{sort '[10, 2, 33]' 'desc'}}");
    String byPrice = render("{{#each (sort items 'price')}}{{name}}{{/each}}", model);
    String reversed = render("{{#each (reverse (sort items 'price'))}}{{name}}{{/each}}", model);
    String one = render("{{oneOf 'draft' 'approved'}}");
    String some = render("{{someOf (array 'car' 'house' 'boat' 'bike' 'land') 1 2 true}}");
    String text = render("{{someOf (array 'car' 'house' 'boat') 3}}");

    // Then
    assertThat(words).isEqualTo("[\"alligator\",\"elephant\",\"lion\",\"zebra\"]");
    assertThat(numbers).isEqualTo("[33,10,2]");
    assertThat(byPrice).isEqualTo("acb");
    assertThat(reversed).isEqualTo("bca");
    assertThat(one).isIn("draft", "approved");
    assertThat(some).matches("\\[(\"(car|house|boat|bike|land)\",?){1,2}]");
    assertThat(text.split(",")).containsExactlyInAnyOrder("car", "house", "boat");
    assertThat(render("{{reverse 'text'}}")).isEqualTo("text");
  }

  @Test
  void concatMergeCoalesceAndJsonParseFollowBeeceptorsRules() throws IOException {
    // Given
    Map<String, Object> model = Map.of("data", "{\"user\": {\"name\": \"John\"}, \"items\": [{\"id\": 1}]}");

    // When
    String hello = render("{{concat 'Hello' ' ' 'World'}}");
    String lists = render("{{concat (array 1 2) (array 3 4)}}");
    String skips = render("{{concat 'A' null 'B'}}|{{concat '' 0}}|{{concat '' false}}");
    String merged = render("{{{json (objectMerge (object id=1 status='PENDING') (object status='ACTIVE' role='admin'))}}}");
    String guest = render("{{coalesce undefined null 'guest'}}|{{coalesce 0 10}}|{{coalesce false true}}|[{{coalesce '' 'fallback'}}]");
    String name = render("{{jsonParse data 'user.name'}}|{{jsonParse data 'items.0.id'}}", model);

    // Then
    assertThat(hello).isEqualTo("Hello World");
    assertThat(lists).isEqualTo("[1,2,3,4]");
    assertThat(skips).isEqualTo("AB|0|false");
    assertThat(merged).isEqualTo("{\"id\":1,\"status\":\"ACTIVE\",\"role\":\"admin\"}");
    assertThat(guest).isEqualTo("guest|0|false|[]");
    assertThat(name).isEqualTo("John|1");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "{{padStart '42' 5 '0'}}|00042",
      "{{padEnd '42' 5 '0'}}|42000",
      "{{padStart 'abc' 2}}|abc",
      "{{urlDecode 'hello%20world'}}|hello world",
      "{{base64Decode 'aGVsbG8gd29ybGQ='}}|hello world",
      "{{#base64Decode}}dXNlcm5hbWU6cGFzc3dvcmQ={{/base64Decode}}|username:password",
      "{{lowercase 'HELLO WORLD'}}|hello world",
      "{{uppercase 'hello world'}}|HELLO WORLD",
      "{{isNumber '42'}} {{isNumber '3.14'}} {{isNumber 'hello'}} {{isNumber ''}}|true true false false",
      "{{isInteger '42'}} {{isInteger '3.14'}} {{isInteger '-5'}} {{isInteger 'not-a-number'}}|true false true false",
      "{{isDate '2023-12-25'}} {{isDate '2023-12-25T10:30:00Z'}} {{isDate 'invalid-date'}} {{isDate ''}}|true true false false",
  })
  void textAndTypeHelpersMatchTheDocumentedExamples(String template, String expected) throws IOException {
    // Given
    // When
    String out = render(template);

    // Then
    assertThat(out).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "{{dateAdd '2026-02-25T05:15:05Z' '{days:3}' 'iso'}}|2026-02-28T05:15:05.000+00:00",
      "{{dateAdd '2026-02-25T05:15:05Z' '{hours:-5}' 'utc'}}|2026-02-25T00:15:05Z",
      "{{dateAdd '2026-02-25T05:15:05Z' '{days:1, hours:3}' 'utc'}}|2026-02-26T08:15:05Z",
      "{{dateAdd 1740460505 '{days:7}' 'unix'}}|1741065305",
      "{{dateAdd '2026-02-25T05:15:05Z' (object days=3) 'short'}}|2026-02-28",
      "{{dateAdd (dateParse '25-02-2026 05:15:05' 'DD-MM-YYYY HH:mm:ss') '{months:1}' 'long'}}|2026-03-25 05:15:05",
      "{{dateAdd (dateParse 1740460505000 'timestamp') '{weeks:1}' 'YYYYMMDD'}}|20250304",
      "{{dateAdd '2026-01-31' '{month:1}' 'us'}}|02/28/2026",
      "{{dateDiff '2026-02-25T05:15:05Z' '2026-02-28T05:15:05Z' 'days'}}|3",
      "{{dateDiff '2026-02-25T05:15:05Z' '2026-02-25T11:15:05Z' 'hours'}}|6",
      "{{dateDiff '2026-03-01' '2026-01-01' 'months'}}|-2",
  })
  void dateHelpersMatchTheDocumentedExamples(String template, String expected) throws IOException {
    // Given
    // When
    String out = render(template);

    // Then
    assertThat(out).isEqualTo(expected);
  }

  @Test
  void dateHelpersExplainWhatTheyCannotRead() {
    // Given
    // When / Then
    assertThatThrownBy(() -> render("{{dateAdd 'soon' '{days:1}'}}")).hasMessageContaining("Can't read 'soon' as a date");
    assertThatThrownBy(() -> render("{{dateAdd '2026-01-01' '{fortnights:1}'}}")).hasMessageContaining("Unknown unit");
    assertThatThrownBy(() -> render("{{dateDiff '2026-01-01' '2026-02-01'}}")).hasMessageContaining("two dates and a unit");
  }
}
