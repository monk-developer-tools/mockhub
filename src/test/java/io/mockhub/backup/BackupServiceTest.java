package io.mockhub.backup;

import static io.mockhub.support.Fixtures.json;
import static io.mockhub.support.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.org.Organization;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.stub.StubService;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.support.FakeStubGateway;
import io.mockhub.support.InMemoryOrganizationRepository;
import io.mockhub.files.BodyFiles;
import io.mockhub.support.InMemoryDataStore;
import io.mockhub.support.InMemoryProjectRepository;
import io.mockhub.support.InMemoryTeamRepository;
import io.mockhub.support.MutableClock;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackupServiceTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-16T10:00:00Z"));
  private Server source;

  private static Path tempDir() {
    try {
      Path dir = java.nio.file.Files.createTempDirectory("mockhub-backup");
      dir.toFile().deleteOnExit();
      return dir;
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static final class Server {
    final FakeStubGateway gateway = new FakeStubGateway();
    final OrganizationService orgs;
    final TeamService teams;
    final ProjectService projects;
    final StubService stubs;
    final BackupService backups;

    Server(MutableClock clock) {
      AtomicReference<ProjectService> ref = new AtomicReference<>();
      stubs = new StubService(gateway, ref::get);
      projects = new ProjectService(new InMemoryProjectRepository(), List.of(stubs), clock);
      ref.set(projects);
      teams = new TeamService(new InMemoryTeamRepository(), List.of(), clock);
      orgs = new OrganizationService(new InMemoryOrganizationRepository(), List.of(projects, teams), clock);
      backups = new BackupService(orgs, teams, projects, gateway, new InMemoryDataStore(), new BodyFiles(tempDir()), clock);
    }

    Organization org(String name) {
      return orgs.create(tree("{'name':'" + name + "'}"));
    }

    Project project(Organization org, String slug, String... paths) {
      Team team = teams.ensureDefault(org.id());
      Project p = projects.create(tree("{'orgId':'" + org.id() + "','teamId':'" + team.id() + "','name':'" + slug + "','slug':'" + slug + "'}"));
      for (String path : paths) stubs.create(p.id(), json("{'request':{'method':'GET','urlPath':'" + path + "'},'response':{'status':200}}"));
      return p;
    }

    List<String> urls(String projectId) {
      return stubs.stubsOf(projectId).stream().map(s -> s.getRequest().getUrlPath()).sorted().toList();
    }
  }

  private Organization payments;
  private Organization retail;
  private Project payByBank;
  private Project cards;
  private Project basket;

  @BeforeEach
  void setUp() {
    source = new Server(clock);
    payments = source.org("Payments");
    retail = source.org("Retail");
    payByBank = source.project(payments, "pay-by-bank", "/consents", "/payments");
    cards = source.project(payments, "cards", "/cards");
    basket = source.project(retail, "basket", "/basket");
  }

  @Test
  void backsUpOnlyTheChosenProjectsWithTheirOrganisations() {
    // When
    ObjectNode file = source.backups.export(BackupSelection.of(Set.of(), Set.of(payByBank.id())));

    // Then
    assertThat(file.get("format").asText()).isEqualTo("mockhub-backup");
    assertThat(file.get("scope").asText()).isEqualTo("selection");
    assertThat(file.get("organizations").findValuesAsText("name")).containsExactly("Payments");
    assertThat(file.get("projects").findValuesAsText("slug")).containsExactly("pay-by-bank");
    assertThat(file.get("mappings")).hasSize(2);
    assertThat(source.backups.fileName(BackupSelection.of(Set.of(), Set.of(payByBank.id())))).isEqualTo("mockhub-pay-by-bank-2026-10-16.json");
  }

  @Test
  void backsUpWholeOrganisationsAndCanMixInSingleProjects() {
    // When
    ObjectNode file = source.backups.export(BackupSelection.of(Set.of(payments.id()), Set.of(basket.id())));

    // Then
    assertThat(file.get("organizations").findValuesAsText("name")).containsExactlyInAnyOrder("Payments", "Retail");
    assertThat(file.get("projects").findValuesAsText("slug")).containsExactlyInAnyOrder("pay-by-bank", "cards", "basket");
    assertThat(file.get("mappings")).hasSize(4);
    assertThat(source.backups.fileName(BackupSelection.of(Set.of(payments.id()), Set.of()))).isEqualTo("mockhub-payments-2026-10-16.json");
  }

  @Test
  void refusesAnEmptySelection() {
    // When / Then
    assertThatThrownBy(() -> source.backups.export(BackupSelection.of(Set.of(), Set.of()))).hasMessageContaining("Choose at least one");
    assertThatThrownBy(() -> source.backups.export(BackupSelection.of(Set.of("nope"), Set.of()))).hasMessageContaining("not found");
  }

  @Test
  void previewsWhatAFileWouldChange() {
    // Given
    JsonNode file = source.backups.export(BackupSelection.all());
    Server target = new Server(clock);
    Organization other = target.org("Other");
    target.project(other, "cards", "/elsewhere");

    // When
    JsonNode preview = target.backups.preview(file);

    // Then
    assertThat(preview.get("scope").asText()).isEqualTo("full");
    JsonNode paymentsNode = preview.get("organizations").get(0);
    assertThat(paymentsNode.get("name").asText()).isEqualTo("Payments");
    assertThat(paymentsNode.get("exists").asBoolean()).isFalse();
    assertThat(paymentsNode.get("projects").findValuesAsText("status")).containsExactly("slug-taken", "new");
    assertThat(paymentsNode.get("projects").get(0).get("newSlug").asText()).isEqualTo("cards-2");
    assertThat(paymentsNode.get("projects").get(1).get("stubCount").asInt()).isEqualTo(2);
    assertThat(source.backups.preview(file).get("organizations").get(0).get("projects").findValuesAsText("status")).containsOnly("replace");
  }

  @Test
  void restoresOnlyTheChosenProjectsAndMovesClashingBasePaths() {
    // Given
    JsonNode file = source.backups.export(BackupSelection.all());
    Server target = new Server(clock);
    target.project(target.org("Other"), "cards", "/elsewhere");

    // When
    BackupService.Result result = target.backups.restore(file,
        new RestoreOptions(BackupSelection.of(Set.of(), Set.of(payByBank.id(), cards.id())), null, false));

    // Then
    assertThat(result.projects()).isEqualTo(2);
    assertThat(result.organizations()).isEqualTo(1);
    assertThat(result.stubs()).isEqualTo(3);
    assertThat(result.renamed()).singleElement().satisfies(r -> {
      assertThat(r.from()).isEqualTo("/cards");
      assertThat(r.to()).isEqualTo("/cards-2");
    });
    assertThat(target.projects.find(basket.id())).isEmpty();
    assertThat(target.urls(cards.id())).containsExactly("/cards-2/cards");
    assertThat(target.urls(payByBank.id())).containsExactly("/pay-by-bank/consents", "/pay-by-bank/payments");
    assertThat(target.projects.get(payByBank.id()).orgId()).isEqualTo(payments.id());
  }

  @Test
  void replacingAProjectRemovesStubsThatAreNotInTheBackup() {
    // Given
    JsonNode file = source.backups.export(BackupSelection.of(Set.of(), Set.of(cards.id())));
    source.stubs.create(cards.id(), json("{'request':{'method':'GET','urlPath':'/added-later'},'response':{'status':200}}"));

    // When
    source.backups.restore(file, new RestoreOptions(BackupSelection.all(), null, false));

    // Then
    assertThat(source.urls(cards.id())).containsExactly("/cards/cards");
  }

  @Test
  void importsACopyIntoAnotherOrganisation() {
    // Given
    JsonNode file = source.backups.export(BackupSelection.of(Set.of(), Set.of(payByBank.id())));

    // When
    BackupService.Result result = source.backups.restore(file,
        new RestoreOptions(BackupSelection.all(), retail.id(), true));

    // Then
    assertThat(result.organizations()).isZero();
    Project copy = source.projects.findByOrg(retail.id()).stream().filter(p -> p.name().equals("pay-by-bank")).findFirst().orElseThrow();
    assertThat(copy.id()).isNotEqualTo(payByBank.id());
    assertThat(copy.slug()).isEqualTo("pay-by-bank-2");
    assertThat(source.urls(copy.id())).containsExactly("/pay-by-bank-2/consents", "/pay-by-bank-2/payments");
    assertThat(source.urls(payByBank.id())).containsExactly("/pay-by-bank/consents", "/pay-by-bank/payments");
  }

  @Test
  void carriesTeamsAcrossAndPlacesProjectsInAChosenTeam() {
    // Given
    Team checkout = source.teams.create(payments.id(), tree("{'name':'Checkout'}"));
    Project pos = source.projects.create(tree("{'orgId':'" + payments.id() + "','teamId':'" + checkout.id() + "','name':'POS','slug':'pos'}"));
    JsonNode file = source.backups.export(BackupSelection.of(Set.of(), Set.of(pos.id())));
    Server target = new Server(clock);
    Organization other = target.org("Other");
    Team landing = target.teams.create(other.id(), tree("{'name':'Landing'}"));

    // When
    target.backups.restore(file, RestoreOptions.everything());
    Server third = new Server(clock);
    Organization elsewhere = third.org("Elsewhere");
    Team inbox = third.teams.create(elsewhere.id(), tree("{'name':'Inbox'}"));
    third.backups.restore(file, new RestoreOptions(BackupSelection.all(), elsewhere.id(), inbox.id(), false));

    // Then
    assertThat(file.get("teams").findValuesAsText("name")).containsExactly("Checkout");
    assertThat(target.teams.get(checkout.id()).name()).isEqualTo("Checkout");
    assertThat(target.projects.get(pos.id()).teamId()).isEqualTo(checkout.id());
    assertThat(target.backups.preview(file).at("/organizations/0/projects/0/teamName").asText()).isEqualTo("Checkout");
    assertThat(third.projects.get(pos.id()).teamId()).isEqualTo(inbox.id());
    assertThat(landing.orgId()).isEqualTo(other.id());
  }

  @Test
  void readsOlderBackupsAndRejectsOtherFiles() {
    // Given
    ObjectNode legacy = source.backups.export(BackupSelection.all());
    legacy.remove(List.of("format", "version", "scope", "createdAt"));
    Server target = new Server(clock);

    // When
    BackupService.Result result = target.backups.restore(legacy, RestoreOptions.everything());

    // Then
    assertThat(result.projects()).isEqualTo(3);
    assertThatThrownBy(() -> target.backups.preview(tree("{'openapi':'3.0.0'}"))).hasMessageContaining("isn't a MockHub backup");
    assertThatThrownBy(() -> target.backups.restore(legacy, new RestoreOptions(BackupSelection.all(), "missing", false)))
        .hasMessageContaining("not found");
  }
}
