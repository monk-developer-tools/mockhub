package io.mockhub.dataset;

import static io.mockhub.support.Fixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import io.mockhub.org.Organization;
import io.mockhub.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetServiceTest {

  @TempDir
  Path dir;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
  private DatasetService service;

  @BeforeEach
  void start() {
    service = new DatasetService(new FileDatasetRepository(dir), new DatasetService.Limits(1024, 4, 200, 2), clock);
    service.create("org1", json("{'key':'customers','name':'Customers','identity':['id'],'indexes':['city']}"));
  }

  private DatasetService.UploadResult upload(String mode, String csv) {
    return service.upload("org1", "customers", DatasetFormat.CSV, UploadMode.parse(mode), csv.getBytes(StandardCharsets.UTF_8));
  }

  private List<String> names() {
    return service.records("org1", "customers", null, Map.of(), 0, 50).records().stream().map(r -> r.get("name").asText()).toList();
  }

  @Test
  void replacesUpdatesOrOnlyAddsRecordsByIdentity() {
    // Given
    upload("replace", "id,name,city\n1,Ada,London\n2,Alan,Wilmslow\n");

    // When
    DatasetService.UploadResult upserted = upload("upsert", "id,name,city\n2,Alan Turing,Wilmslow\n3,Grace,Arlington\n");
    DatasetService.UploadResult added = upload("add", "id,name,city\n1,Someone else,Paris\n4,Edsger,Austin\n");

    // Then
    assertThat(upserted).extracting(DatasetService.UploadResult::added, DatasetService.UploadResult::updated, DatasetService.UploadResult::skipped)
        .containsExactly(1, 1, 0);
    assertThat(added).extracting(DatasetService.UploadResult::added, DatasetService.UploadResult::updated, DatasetService.UploadResult::skipped)
        .containsExactly(1, 0, 1);
    assertThat(names()).containsExactly("Ada", "Alan Turing", "Grace", "Edsger");
    assertThat(added.dataset().recordCount()).isEqualTo(4);
    assertThat(added.dataset().updatedAt()).isEqualTo("2026-11-04T10:00:00Z");
  }

  @Test
  void findsRecordsAndSurvivesAReload() {
    // Given
    upload("replace", "id,name,city\n1,Ada,London\n2,Alan,Wilmslow\n3,Grace,London\n");

    // When
    DatasetService reloaded = new DatasetService(new FileDatasetRepository(dir), DatasetService.Limits.DEFAULT, clock);

    // Then
    List<ObjectNode> london = reloaded.find("org1", "customers", Map.of("city", "London"), 10);
    assertThat(london).extracting(r -> r.get("name").asText()).containsExactly("Ada", "Grace");
    assertThat(reloaded.records("org1", "customers", "wilm", Map.of(), 0, 10).total()).isEqualTo(1);
    assertThat(reloaded.get("org1", "customers").columns()).extracting(Column::name).containsExactly("id", "name", "city");
    london.get(0).put("name", "changed");
    assertThat(reloaded.find("org1", "customers", Map.of("id", "1"), 1).get(0).get("name").asText()).isEqualTo("Ada");
  }

  @Test
  void refusesUploadsPastItsLimits() {
    // Then
    assertThatThrownBy(() -> upload("replace", "id,name\n" + "1,x\n".repeat(300))).isInstanceOf(ApiException.class)
        .hasMessage("Uploads can be at most 1 KB").extracting("status").isEqualTo(413);
    assertThatThrownBy(() -> upload("replace", "id\n1\n2\n3\n4\n5\n")).hasMessage("A dataset holds up to 4 records; this upload would make 5");
    assertThatThrownBy(() -> upload("replace", "id,bio\n1," + "x".repeat(250) + "\n")).hasMessageStartingWith("Record 1 is ").hasMessageEndingWith("bytes; records can be at most 200 bytes");
    assertThatThrownBy(() -> upload("replace", "id,name\n1,a\n1,b\n")).hasMessage("Records 1 and 2 have the same id");
    assertThatThrownBy(() -> upload("replace", "name\na\n")).hasMessage("Record 1 has no id, which identifies records");
    assertThatThrownBy(() -> upload("replace", "")).hasMessage("The upload is empty");
  }

  @Test
  void checksKeysSettingsAndHowManyDatasetsAnOrganisationHas() {
    // Given
    service.create("org1", json("{'key':'orders'}"));

    // Then
    assertThatThrownBy(() -> service.create("org1", json("{'key':'more'}"))).hasMessage("An organisation can have up to 2 datasets");
    assertThatThrownBy(() -> service.create("org2", json("{'key':'Bad Key'}"))).hasMessageContaining("lowercase");
    assertThatThrownBy(() -> service.create("org1", json("{'key':'orders'}"))).hasMessage("A dataset called orders already exists");
    assertThatThrownBy(() -> service.update("org1", "orders", json("{'identity':['a','b','c','d']}"))).hasMessage("Use up to 3 identity fields");
    assertThatThrownBy(() -> service.upload("org1", "orders", DatasetFormat.CSV, UploadMode.UPSERT, "a\n1\n".getBytes(StandardCharsets.UTF_8)))
        .hasMessageContaining("Set identity fields");
    assertThat(service.get("org1", "orders").name()).isEqualTo("orders");
    assertThat(service.update("org1", "orders", json("{'name':'Orders','indexes':['status']}")).indexes()).containsExactly("status");
  }

  @Test
  void changingTheIdentityChecksExistingRecords() {
    // Given
    upload("replace", "id,name,city\n1,Ada,London\n2,Alan,London\n");

    // Then
    assertThatThrownBy(() -> service.update("org1", "customers", json("{'identity':['city']}"))).hasMessage("Records 1 and 2 have the same city");
    assertThat(service.update("org1", "customers", json("{'identity':['city','name']}")).identity()).containsExactly("city", "name");
  }

  @Test
  void deletingAnOrganisationDeletesItsDatasets() {
    // Given
    upload("replace", "id,name\n1,Ada\n");

    // When
    service.onOrganizationDeleted(new Organization("org1", "One", "one", "", "", ""));

    // Then
    assertThat(service.list("org1")).isEmpty();
    assertThat(new DatasetService(new FileDatasetRepository(dir), DatasetService.Limits.DEFAULT, clock).list("org1")).isEmpty();
    assertThat(service.delete("org1", "customers")).isFalse();
  }
}
