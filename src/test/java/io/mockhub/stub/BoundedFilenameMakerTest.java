package io.mockhub.stub;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoundedFilenameMakerTest {

  private final BoundedFilenameMaker maker = new BoundedFilenameMaker(new FilenameMaker());

  @Test
  void keepsShortNamesAsWireMockMakesThem() {
    // Given
    StubMapping stub = WireMock.get("/orders/7").build();

    // When
    String name = maker.filenameFor(stub);

    // Then
    assertThat(name).isEqualTo(new FilenameMaker().filenameFor(stub));
  }

  @Test
  void shortensLongNamesButKeepsTheStubIdSoNamesStayUnique() {
    // Given
    UUID id = UUID.randomUUID();
    StubMapping stub = WireMock.get("/customers?where=" + "custom(fields(email=\"someone@example.com\"))and".repeat(8)).withId(id).build();

    // When
    String name = maker.filenameFor(stub);

    // Then
    assertThat(new FilenameMaker().filenameFor(stub).getBytes(UTF_8).length).isGreaterThan(255);
    assertThat(name.getBytes(UTF_8).length).isLessThanOrEqualTo(BoundedFilenameMaker.MAX_BYTES);
    assertThat(name).startsWith("get-customers").endsWith("-" + id + ".json");
  }

  @Test
  void neverSplitsACharacterWhenCountingBytes() {
    // Given
    String name = "é".repeat(300) + "-" + UUID.randomUUID();

    // When
    String shortened = BoundedFilenameMaker.shorten(name);

    // Then
    assertThat(shortened.getBytes(UTF_8).length).isLessThanOrEqualTo(BoundedFilenameMaker.MAX_BYTES);
    assertThat(shortened).endsWith(name.substring(name.length() - 36));
  }
}
