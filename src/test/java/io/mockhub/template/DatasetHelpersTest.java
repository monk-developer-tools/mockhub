package io.mockhub.template;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import io.mockhub.dataset.DatasetFormat;
import io.mockhub.dataset.DatasetService;
import io.mockhub.dataset.FileDatasetRepository;
import io.mockhub.dataset.UploadMode;
import io.mockhub.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetHelpersTest {

  @TempDir
  Path dir;
  private final AtomicReference<String> org = new AtomicReference<>("retail");
  private final Handlebars handlebars = new Handlebars().with(EscapingStrategy.NOOP);

  @BeforeEach
  void start() {
    DatasetService datasets = new DatasetService(new FileDatasetRepository(dir), DatasetService.Limits.DEFAULT,
        new MutableClock(Instant.parse("2026-11-04T10:00:00Z")));
    datasets.create("retail", json("{'key':'customers','identity':['id'],'indexes':['city']}"));
    datasets.upload("retail", "customers", DatasetFormat.NDJSON, UploadMode.REPLACE, String.join("\n",
        "{\"id\":1,\"name\":\"Ada\",\"city\":\"London\",\"address\":{\"street\":\"St James's Square\"},\"tags\":[\"vip\"]}",
        "{\"id\":2,\"name\":\"Alan\",\"city\":\"Wilmslow\"}",
        "{\"id\":3,\"name\":\"Grace\",\"city\":\"London\",\"vip\":true}").getBytes(StandardCharsets.UTF_8));
    new DatasetHelpers(datasets, () -> Optional.ofNullable(org.get())).provideTemplateHelpers().forEach(handlebars::registerHelper);
    new ValueHelpers().provideTemplateHelpers().forEach(handlebars::registerHelper);
  }

  private String render(String template, Object context) throws Exception {
    return handlebars.compileInline(template).apply(context);
  }

  @Test
  void findsOneRecordAndReadsItsFields() throws Exception {
    // Given
    Map<String, Object> request = Map.of("path", Map.of("id", "1"));

    // When
    String name = render("{{#with (dataset-one 'customers' id=request.path.id)}}{{name}} at {{address.street}}{{/with}}", Map.of("request", request));
    String whole = render("{{{dataset-one 'customers' id=2}}}", Map.of());

    // Then
    assertThat(name).isEqualTo("Ada at St James's Square");
    assertThat(whole).isEqualTo("{\"id\":2,\"name\":\"Alan\",\"city\":\"Wilmslow\"}");
    assertThat(render("{{#unless (dataset-one 'customers' id=99)}}none{{/unless}}", Map.of())).isEqualTo("none");
  }

  @Test
  void listsMatchingRecordsAsJsonOrForEach() throws Exception {
    // When
    String names = render("{{#each (dataset 'customers' city='London')}}{{name}};{{/each}}", Map.of());
    String limited = render("{{{dataset 'customers' city='London' limit=1}}}", Map.of());

    // Then
    assertThat(names).isEqualTo("Ada;Grace;");
    assertThat(limited).isEqualTo("[{\"id\":1,\"name\":\"Ada\",\"city\":\"London\",\"address\":{\"street\":\"St James's Square\"},\"tags\":[\"vip\"]}]");
    assertThat(render("{{len (dataset 'customers')}}", Map.of())).isEqualTo("3");
    assertThat(render("{{{dataset 'customers' city='London' vip=true}}}", Map.of())).contains("Grace").doesNotContain("Ada");
  }

  @Test
  void explainsWhatIsMissing() {
    // Then
    assertThatThrownBy(() -> render("{{dataset-one 'nope' id=1}}", Map.of())).hasMessageContaining("No dataset called nope in this organisation");
    assertThatThrownBy(() -> render("{{dataset 'customers' a=1 b=2 c=3 d=4}}", Map.of())).hasMessageContaining("Filter on up to 3 fields");
    org.set(null);
    assertThatThrownBy(() -> render("{{dataset 'customers'}}", Map.of())).hasMessageContaining("Datasets work in the stubs of a project");
  }
}
