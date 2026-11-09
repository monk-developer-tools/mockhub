package io.mockhub.ext;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import io.mockhub.Config;
import io.mockhub.ai.AiProviders;
import io.mockhub.ai.AiSettingsStore;
import io.mockhub.ai.AssistantJobs;
import io.mockhub.ai.AssistantService;
import io.mockhub.controller.EnvironmentController;
import io.mockhub.controller.GitController;
import io.mockhub.environment.EnvHelpers;
import io.mockhub.environment.EnvironmentFilter;
import io.mockhub.environment.EnvironmentScope;
import io.mockhub.environment.EnvironmentService;
import io.mockhub.environment.EnvironmentStore;
import io.mockhub.controller.TemplateController;
import io.mockhub.git.GitLinkStore;
import io.mockhub.git.GitSyncService;
import io.mockhub.git.JGitRemote;
import io.mockhub.ai.ServerProjectSetup;
import io.mockhub.ai.BodyGenerator;
import io.mockhub.ai.TemplateFixer;
import io.mockhub.chaos.ErrorRateTransformer;
import io.mockhub.controller.RuntimeController;
import io.mockhub.runtime.CorsForRequest;
import io.mockhub.runtime.CorsHeaders;
import io.mockhub.runtime.CorsTransformer;
import io.mockhub.runtime.DefaultResponseStubs;
import io.mockhub.runtime.LoadMeter;
import io.mockhub.runtime.ProjectScopeMatcher;
import io.mockhub.runtime.RateLimiter;
import io.mockhub.runtime.RuntimeService;
import io.mockhub.runtime.RuntimeFilter;
import io.mockhub.runtime.RuntimeTransformer;
import io.mockhub.callout.CalloutListener;
import io.mockhub.callout.CalloutLog;
import io.mockhub.callout.CalloutRenderer;
import io.mockhub.callout.CalloutService;
import io.mockhub.callout.CalloutTargets;
import io.mockhub.history.HistoryCalloutLog;
import io.mockhub.history.HistoryRecorder;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import io.mockhub.history.ServedRequestListener;
import io.mockhub.otel.OtlpExporter;
import io.mockhub.history.HistoryStore;
import io.mockhub.contract.ContractPatcher;
import io.mockhub.contract.ContractStore;
import io.mockhub.contract.DriftNotifier;
import io.mockhub.contract.DriftService;
import io.mockhub.controller.ContractController;
import io.mockhub.history.TrafficCounters;
import io.mockhub.controller.ConnectionController;
import io.mockhub.controller.DatasetController;
import io.mockhub.controller.HistoryController;
import io.mockhub.Main;
import io.mockhub.callout.SyncCalloutTransformer;
import io.mockhub.controller.GraphQlController;
import io.mockhub.controller.GrpcController;
import io.mockhub.grpc.FileDescriptorStore;
import io.mockhub.grpc.GrpcCatalog;
import io.mockhub.grpc.GrpcRequestFilter;
import io.mockhub.controller.TlsController;
import io.mockhub.tls.TlsService;
import io.mockhub.tls.TlsStore;
import io.mockhub.graphql.ExampleOperations;
import io.mockhub.graphql.GraphQlEngine;
import io.mockhub.graphql.GraphQlTransformer;
import io.mockhub.graphql.MockSchemas;
import io.mockhub.importer.GraphQlStubImporter;
import io.mockhub.importer.SoapSampleImporter;
import io.mockhub.soap.SoapEndpointTransformer;
import io.mockhub.echo.EchoTransformer;
import io.mockhub.connection.ConnectionAuth;
import io.mockhub.connection.ConnectionProxyTransformer;
import io.mockhub.controller.CalloutController;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.mockhub.stub.DisabledStubMatcher;
import io.mockhub.ai.ChatCompletions;
import io.mockhub.ai.JdkLlmTransport;
import io.mockhub.controller.AiController;
import io.mockhub.backup.BackupService;
import io.mockhub.auth.AccessPolicy;
import io.mockhub.ai.StagedChanges;
import io.mockhub.api.Jsons;
import io.mockhub.api.ApiRouter;
import io.mockhub.controller.AccessTokenController;
import io.mockhub.controller.ApiController;
import io.mockhub.controller.StagedController;
import io.mockhub.mcp.InternalApi;
import io.mockhub.mcp.McpServer;
import io.mockhub.mcp.McpTask;
import io.mockhub.mcp.McpTools;
import io.mockhub.mcp.ReviewStager;
import io.mockhub.token.AccessTokenService;
import io.mockhub.token.FileAccessTokenRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.Optional;
import java.util.Set;
import com.github.tomakehurst.wiremock.http.Request;
import io.mockhub.audit.AuditLog;
import io.mockhub.audit.AuditRecorder;
import io.mockhub.audit.FileAuditLog;
import io.mockhub.audit.LoggingAuditLog;
import io.mockhub.audit.ServiceAuditNames;
import io.mockhub.controller.AuditController;
import java.time.Duration;
import io.mockhub.data.CrudService;
import io.mockhub.data.CrudTransformer;
import io.mockhub.data.DataStore;
import io.mockhub.data.FileDataStore;
import io.mockhub.data.ProjectDataCleaner;
import io.mockhub.data.RecordPolicy;
import io.mockhub.connection.ConnectionService;
import io.mockhub.connection.FileConnectionRepository;
import io.mockhub.connection.SecretBox;
import io.mockhub.connection.TokenClient;
import io.mockhub.dataset.DatasetService;
import io.mockhub.store.JsonFile;
import io.mockhub.dataset.FileDatasetRepository;
import io.mockhub.fake.DataGenerator;
import io.mockhub.fake.FakeData;
import io.mockhub.controller.PlaygroundController;
import io.mockhub.playground.JdkHttpSender;
import io.mockhub.playground.PlaygroundHistory;
import io.mockhub.playground.PlaygroundService;
import io.mockhub.playground.TargetGuard;
import io.mockhub.publictools.BinDrafter;
import io.mockhub.publictools.BinProject;
import io.mockhub.publictools.TotpTool;
import io.mockhub.publictools.BinTool;
import io.mockhub.publictools.BinTunnelKeys;
import io.mockhub.publictools.Bins;
import io.mockhub.publictools.EchoTool;
import io.mockhub.publictools.FakeTool;
import io.mockhub.publictools.InstantMockFilter;
import io.mockhub.publictools.InstantMockTool;
import io.mockhub.publictools.InstantExamples;
import io.mockhub.publictools.InstantMocks;
import io.mockhub.publictools.InstantRecordPolicy;
import io.mockhub.publictools.SandboxTemplateTransformer;
import io.mockhub.org.Organization;
import io.mockhub.publictools.PublicLimits;
import io.mockhub.publictools.PublicToolsFilter;
import io.mockhub.publictools.RenderTool;
import io.mockhub.publictools.TemplatePlayground;
import java.net.InetAddress;
import io.mockhub.files.BodyFiles;
import io.mockhub.fake.FakeHelpers;
import io.mockhub.template.JwtHelpers;
import io.mockhub.template.JwtKeys;
import io.mockhub.template.ValidationHelpers;
import io.mockhub.template.ValidationTransformer;
import io.mockhub.template.DatasetHelpers;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import io.mockhub.template.PortableHelpers;
import io.mockhub.template.ValueHelpers;
import io.mockhub.template.WeightedTransformer;
import io.mockhub.auth.ProjectScope;
import io.mockhub.auth.AuthService;
import io.mockhub.auth.Role;
import io.mockhub.auth.Principal;
import io.mockhub.auth.TokenService;
import io.mockhub.controller.AuthController;
import io.mockhub.controller.BackupController;
import io.mockhub.controller.DataController;
import io.mockhub.controller.FilesController;
import io.mockhub.controller.BaseUrls;
import io.mockhub.controller.ExampleController;
import io.mockhub.runtime.IpAllowList;
import io.mockhub.controller.ProxyController;
import io.mockhub.proxy.ProxyService;
import io.mockhub.controller.TunnelController;
import io.mockhub.tunnel.TunnelHub;
import io.mockhub.tunnel.TunnelServlet;
import io.mockhub.tunnel.TunnelProxyTransformer;
import io.mockhub.controller.OpenApiLinkController;
import io.mockhub.importer.SpecFetcher;
import io.mockhub.controller.ImportExportController;
import io.mockhub.controller.OrganizationController;
import io.mockhub.controller.ProjectController;
import io.mockhub.controller.ProjectViews;
import io.mockhub.controller.RequestLogController;
import io.mockhub.controller.SsoController;
import io.mockhub.controller.StubController;
import io.mockhub.controller.SystemController;
import io.mockhub.controller.TeamChoice;
import io.mockhub.controller.TeamController;
import io.mockhub.controller.UserController;
import io.mockhub.controller.UserViews;
import io.mockhub.example.ExampleCatalog;
import io.mockhub.example.ExampleInstaller;
import io.mockhub.export.BrunoExporter;
import io.mockhub.export.ExporterRegistry;
import io.mockhub.export.PostmanExporter;
import io.mockhub.export.WireMockMappingsExporter;
import io.mockhub.importer.ImporterRegistry;
import io.mockhub.importer.WireMockArchiveImporter;
import io.mockhub.importer.OpenApiImporter;
import io.mockhub.importer.PostmanImporter;
import io.mockhub.importer.WireMockJsonImporter;
import io.mockhub.importer.WsdlStubImporter;
import io.mockhub.journal.RequestLogService;
import io.mockhub.org.FileOrganizationRepository;
import io.mockhub.org.OrganizationService;
import io.mockhub.project.FileProjectRepository;
import io.mockhub.project.Project;
import io.mockhub.project.ProjectService;
import io.mockhub.sso.AzureEntraProvider;
import io.mockhub.sso.JdkHttpTransport;
import io.mockhub.sso.JwtVerifier;
import io.mockhub.sso.RoleMapper;
import io.mockhub.sso.SsoService;
import io.mockhub.sso.SsoSettingsStore;
import io.mockhub.stub.StubGateway;
import io.mockhub.stub.StubService;
import io.mockhub.snapshot.FileSnapshotRepository;
import io.mockhub.snapshot.SnapshotCleanup;
import io.mockhub.snapshot.SnapshotRepository;
import io.mockhub.snapshot.SnapshotService;
import io.mockhub.controller.SnapshotController;
import io.mockhub.stub.WireMockStubGateway;
import io.mockhub.team.FileTeamRepository;
import io.mockhub.team.Team;
import io.mockhub.team.TeamService;
import io.mockhub.user.FileUserRepository;
import io.mockhub.user.PasswordHasher;
import io.mockhub.user.UserService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import io.mockhub.controller.StateController;
import io.mockhub.state.ProjectStateStore;
import io.mockhub.state.StateRepository;
import io.mockhub.state.StateScope;
import io.mockhub.state.StateScopeListener;
import org.wiremock.extensions.state.StateExtension;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MockHubExtensionFactory implements ExtensionFactory {
  private final Config config;
  private final AtomicInteger port = new AtomicInteger();
  private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();

  public MockHubExtensionFactory(Config config) {
    this.config = config;
  }

  public void serverStarted(int actualPort) {
    port.set(actualPort);
  }

  public void serverStopped() {
    for (AutoCloseable closeable : closeables) {
      long started = System.currentTimeMillis();
      try {
        closeable.close();
        long took = System.currentTimeMillis() - started;
        if (took > 1000) LoggerFactory.getLogger(MockHubExtensionFactory.class).warn("Closing {} took {} ms", closeable, took);
      } catch (Exception e) {
        LoggerFactory.getLogger(MockHubExtensionFactory.class).warn("Couldn't close {}: {}", closeable, e.getMessage());
      }
    }
  }

  @Override
  public List<Extension> create(WireMockServices services) {
    Clock clock = Clock.systemUTC();
    Path dataDir = Path.of(services.getOptions().filesRoot().getPath());
    StubGateway gateway = new WireMockStubGateway(services.getAdmin());

    AtomicReference<ProjectService> projectsRef = new AtomicReference<>();
    StubService stubs = new StubService(gateway, projectsRef::get);
    DataStore dataStore = new FileDataStore(dataDir);
    BodyFiles bodyFiles = new BodyFiles(dataDir);
    CrudService crud = new CrudService(dataStore, config.publicTools
        ? new InstantRecordPolicy(id -> projectsRef.get().find(id).map(p -> Organization.INSTANT_ID.equals(p.orgId())).orElse(false))
        : RecordPolicy.NONE, config.maxRecords);
    Path historyDir = dataDir.resolve("mockhub").resolve("history");
    HistoryStore history = new HistoryStore(historyDir, clock, Duration.ofDays(config.historyDays), config.historyMaxMb * 1024L * 1024L);
    TrafficCounters traffic = new TrafficCounters(historyDir, clock);
    StagedChanges staged = new StagedChanges(dataDir, clock);
    SnapshotRepository snapshotStore = new FileSnapshotRepository(dataDir);
    UserService users = new UserService(new FileUserRepository(dataDir), config.users, new PasswordHasher(), clock);
    AccessTokenService accessTokens = new AccessTokenService(new FileAccessTokenRepository(dataDir), users::find,
        id -> projectsRef.get().find(id).isPresent(), clock);
    GrpcCatalog grpc = new GrpcCatalog(new FileDescriptorStore(dataDir), id -> projectsRef.get().find(id).map(Project::name).orElse(null));
    SecretBox secrets = SecretBox.of(config.secretKey, JsonFile.inDataDir(dataDir, "secret.key"));
    TlsService tls = new TlsService(new TlsStore(dataDir, secrets));
    AtomicReference<AuthService> authRef = new AtomicReference<>();
    Bins bins = new Bins(dataDir, clock);
    BinTunnelKeys binKeys = new BinTunnelKeys(bins);
    TunnelHub tunnels = new TunnelHub((principal, projectId) -> projectId.startsWith("bin:") ? principal.id().equals(projectId) : principal.allowsProject(projectId)
        && projectsRef.get().find(projectId).map(p -> principal.isAdmin() || principal.canInTeam(p.orgId(), p.teamId(), Role.EDITOR)).orElse(false),
        Executors.newSingleThreadScheduledExecutor(r -> {
          Thread t = new Thread(r, "mockhub-tunnels");
          t.setDaemon(true);
          return t;
        }), clock);
    accessTokens.onRevoke(tunnels::onTokenRevoked);
    TunnelServlet tunnelServlet = new TunnelServlet(tunnels, header -> binKeys.principal(header).or(() -> authRef.get().principal(header)));
    ProxyService proxy = new ProxyService(config.proxyEnabled, IpAllowList.ofText(config.proxyAllow), config.proxyCaKeystore(),
        id -> projectsRef.get().find(id));
    MockHubServerFactory server = new MockHubServerFactory(services.getStores().getBlobStore("grpc"), grpc::fileDescriptors, tls::attach,
        host -> projectsRef.get().findForHost(host).map(Project::basePath), proxy, tunnelServlet, name -> grpc.ownerOf(name).isPresent(), config.publicTools);
    grpc.onChange(server::loadFileDescriptors);
    ContractStore contracts = new ContractStore(dataDir);
    EnvironmentStore environmentStore = new EnvironmentStore(dataDir);
    ProjectService projects = new ProjectService(new FileProjectRepository(dataDir),
        List.of(stubs, grpc, new ProjectDataCleaner(dataStore, bodyFiles), history, traffic, staged, accessTokens, new SnapshotCleanup(snapshotStore), proxy,
            contracts, environmentStore), clock);
    projectsRef.set(projects);
    TeamService teams = new TeamService(new FileTeamRepository(dataDir), List.of(users), clock);
    DatasetService datasets = new DatasetService(new FileDatasetRepository(dataDir), DatasetService.Limits.DEFAULT, clock);
    CalloutTargets calloutTargets = new CalloutTargets(new TargetGuard(config.calloutAllowPrivate, InetAddress::getAllByName, "MOCKHUB_CALLOUT_ALLOW_PRIVATE"),
        () -> port.get() > 0 ? port.get() : services.getOptions().portNumber(), config.publicUrl);
    ConnectionService connections = new ConnectionService(new FileConnectionRepository(dataDir),
        secrets,
        new TokenClient(new JdkHttpSender(), url -> calloutTargets.resolve(url, null).uri()), clock);
    OrganizationService orgs = new OrganizationService(new FileOrganizationRepository(dataDir), List.of(projects, teams, users, datasets, connections), clock);
    Function<String, Set<String>> maskedHeaders = projectId -> projects.find(projectId).flatMap(p -> orgs.find(p.orgId()))
        .map(o -> Set.copyOf(o.maskedHeaders())).orElse(Set.of());
    history.maskHeaders(maskedHeaders);
    projects.assignOrphans(orgs.ensureDefault().id());
    orgs.findAll().forEach(o -> teams.ensureDefault(o.id()));
    projects.assignTeams(orgId -> teams.ensureDefault(orgId).id());
    RequestLogService requestLog = new RequestLogService(gateway, projects);
    requestLog.maskHeaders(maskedHeaders);
    StateScope stateScope = new StateScope();
    StateRepository stateRepository = new StateRepository(dataStore, () -> projects.findAll().stream().map(Project::id).toList(), config.maxRecords);
    CalloutLog calloutLog = new HistoryCalloutLog(history);
    ConnectionAuth connectionAuth = new ConnectionAuth(connections, id -> projects.find(id).map(Project::orgId));
    CalloutService callouts = new CalloutService(
        new CalloutRenderer(services::getTemplateEngine),
        calloutTargets,
        new JdkHttpSender(), calloutLog, clock, connectionAuth, Thread::sleep, tunnels);
    ScheduledExecutorService calloutScheduler = Executors.newScheduledThreadPool(4, r -> {
      Thread t = new Thread(r, "mockhub-callout");
      t.setDaemon(true);
      return t;
    });
    ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mockhub-history-maintenance");
      t.setDaemon(true);
      return t;
    });
    closeables.add(maintenance::shutdownNow);
    closeables.add(calloutScheduler::shutdownNow);
    closeables.add(traffic::save);
    closeables.add(history);
    maintenance.scheduleWithFixedDelay(() -> safely(history::prune), 1, 30, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(traffic::save), 1, 1, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(accessTokens::flushUse), 1, 1, TimeUnit.MINUTES);
    DriftService drift = new DriftService(contracts, history, projects::find,
        new DriftNotifier(new JdkHttpSender(), new TargetGuard(config.calloutAllowPrivate, InetAddress::getAllByName, "MOCKHUB_CALLOUT_ALLOW_PRIVATE")),
        p -> config.publicUrl == null || config.publicUrl.isBlank() ? null
            : config.publicUrl.replaceAll("/+$", "") + "/__admin/mockhub/ui/projects/" + p.id() + "/contract", clock);
    maintenance.scheduleWithFixedDelay(() -> safely(drift::checkAll), 2, 5, TimeUnit.MINUTES);

    SsoSettingsStore ssoSettings = new SsoSettingsStore(dataDir, config.ssoClientSecret);
    JdkHttpTransport http = new JdkHttpTransport();
    SsoService sso = new SsoService(
        List.of(new AzureEntraProvider(http, new JwtVerifier(http, clock), config.ssoAuthority)),
        ssoSettings, users, new RoleMapper(orgs::exists, id -> teams.find(id).map(Team::orgId)), clock);
    AuthService auth = new AuthService(users, TokenService.fromDataDir(dataDir, clock), ssoSettings::active, accessTokens::principal);
    authRef.set(auth);
    AccessPolicy policy = new AccessPolicy(id -> projects.find(id).map(p -> new ProjectScope(p.orgId(), p.teamId())), config.publicTools);

    BaseUrls urls = new BaseUrls(config.publicUrl);
    ExporterRegistry exporters = new ExporterRegistry(List.of(new PostmanExporter(), new BrunoExporter(), new WireMockMappingsExporter()));
    MockSchemas graphQlSchemas = new MockSchemas();
    ExampleOperations graphQlExamples = new ExampleOperations(graphQlSchemas);
    SpecFetcher specFetcher = new SpecFetcher(new JdkHttpSender(), new TargetGuard(config.playgroundAllowPrivate, InetAddress::getAllByName));
    ImporterRegistry importers = new ImporterRegistry(List.of(new WireMockJsonImporter(), new WsdlStubImporter(), new OpenApiImporter(),
        new GraphQlStubImporter(graphQlExamples), new SoapSampleImporter(), new PostmanImporter()));
    ProjectViews views = new ProjectViews(stubs, requestLog, urls, clock);
    UserViews userViews = new UserViews(orgs, teams);
    TeamChoice teamChoice = new TeamChoice(orgs, teams);
    ExampleCatalog examples = ExampleCatalog.fromClasspath();
    ExampleInstaller exampleInstaller = new ExampleInstaller(examples, projects, stubs, importers, crud, bodyFiles, grpc);
    AiSettingsStore aiSettings = new AiSettingsStore(dataDir, config.aiApiKey);
    AiProviders aiProviders = AiProviders.defaults();
    ChatCompletions chat = new ChatCompletions(new JdkLlmTransport(), aiProviders, config.publicUrl);
    BinDrafter.Llm publicLlm = (system, user) -> {
      ArrayNode messages = Jsons.MAPPER.createArrayNode();
      messages.addObject().put("role", "system").put("content", system);
      messages.addObject().put("role", "user").put("content", user);
      return chat.complete(aiSettings.get(), messages, null).message().path("content").asText("");
    };
    RateLimiter rateLimiter = new RateLimiter(clock);
    DefaultResponseStubs defaultResponses = new DefaultResponseStubs(stubs);
    RuntimeService runtimeService = new RuntimeService(projects, defaultResponses, rateLimiter);
    SnapshotService snapshots = new SnapshotService(snapshotStore, projects, stubs, runtimeService, bodyFiles, environmentStore, clock);
    AssistantService assistant = new AssistantService(aiSettings, chat, stubs, history, clock, calloutLog, runtimeService, snapshots,
        new ServerProjectSetup(projects, stubs, importers, specFetcher, crud, grpc, drift));
    AssistantJobs assistantJobs = new AssistantJobs(assistant, Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mockhub-assistant");
      t.setDaemon(true);
      return t;
    }), clock);

    AuditLog auditLog = new LoggingAuditLog(new FileAuditLog(dataDir, Duration.ofDays(config.auditRetentionDays), clock));
    AuditRecorder auditRecorder = new AuditRecorder(auditLog, new ServiceAuditNames(projects, stubs, teams, orgs, users), clock);

    FakeData fakeData = new FakeData(config.fakeLocale);
    FakeHelpers fakeHelpers = new FakeHelpers(fakeData);
    ValueHelpers valueHelpers = new ValueHelpers();
    List<TemplateHelperProviderExtension> portableHelpers = PortableHelpers.providers();
    EnvironmentScope environmentScope = new EnvironmentScope();
    EnvironmentService environments = new EnvironmentService(environmentStore, projects);
    DatasetHelpers datasetHelpers = new DatasetHelpers(datasets, () -> stateScope.current().flatMap(projects::find).map(Project::orgId));
    ValidationHelpers validationHelpers = new ValidationHelpers();
    JwtHelpers jwtHelpers = new JwtHelpers(new JwtKeys(dataDir), clock);
    CorsHeaders corsHeaders = new CorsHeaders(config.stubCors);
    LoadMeter loadMeter = new LoadMeter(clock);
    Function<String, Optional<Project>> projectForPath = segment -> grpc.ownerOf(segment).flatMap(projects::find).or(() -> projects.findForPath(segment));
    CorsForRequest corsForRequest = new CorsForRequest(projectForPath, corsHeaders);
    PlaygroundHistory playgroundHistory = new PlaygroundHistory(dataDir);

    Function<Request, Principal> principals = request -> auth.principal(request.getHeader("Authorization")).orElse(null);
    ApiRouter api = new ApiRouter(principals, auditRecorder);
    List<ApiController> controllers = List.of(
        new AuditController(auditLog, config.auditRetentionDays),
        new SystemController(auth, urls, dataDir.toString(), config.journalMaxEntries),
        new AuthController(auth, users, userViews, sso, ssoSettings, urls, config.loginNote),
        new OrganizationController(orgs, projects, users, userViews, teams),
        new TeamController(orgs, teams, projects, users, userViews),
        new UserController(users, userViews, auth, orgs, teams),
        new SsoController(ssoSettings, sso, orgs, teams, urls),
        new ProjectController(projects, views, teamChoice),
        new RuntimeController(runtimeService),
        new ExampleController(examples, exampleInstaller, views, teamChoice),
        new StubController(stubs, projects),
        new RequestLogController(requestLog, history),
        new HistoryController(projects, history, traffic, Main.VERSION),
        new CalloutController(calloutLog),
        new GraphQlController(projects, graphQlExamples),
        new GrpcController(projects, grpc),
        new TlsController(tls),
        new ProxyController(proxy),
        new TunnelController(tunnels),
        new ImportExportController(projects, stubs, exporters, importers, urls, new WireMockArchiveImporter(bodyFiles), drift),
        new ContractController(projects, drift, new ContractPatcher(aiSettings, chat, history::get)),
        new OpenApiLinkController(specFetcher, importers),
        new AiController(aiSettings, aiProviders, chat, assistant, assistantJobs, projects, urls, new BodyGenerator(aiSettings, chat),
            new TemplateFixer(publicLlm::complete, TemplateFixer.PROJECT_HELPERS, TemplateFixer.MAX_PROJECT_TEMPLATE)),
        new BackupController(new BackupService(orgs, teams, projects, gateway, dataStore, bodyFiles, datasets, connections, grpc, environmentStore, clock)),
        new DataController(projects, stubs, dataStore, crud),
        new StateController(projects, stateRepository, clock),
        new DatasetController(orgs, datasets),
        new ConnectionController(orgs, connections),
        new FilesController(projects, stubs, bodyFiles),
        new PlaygroundController(
            new PlaygroundService(new JdkHttpSender(), new TargetGuard(config.playgroundAllowPrivate, InetAddress::getAllByName),
                playgroundHistory, () -> "http://127.0.0.1:" + (port.get() > 0 ? port.get() : services.getOptions().portNumber()), clock),
            playgroundHistory, new DataGenerator(fakeData), config.publicUrl),
        new AccessTokenController(accessTokens, auth, projects, clock),
        new StagedController(projects, staged, assistant),
        new SnapshotController(snapshots, projects),
        new EnvironmentController(environments),
        new GitController(new GitSyncService(new GitLinkStore(dataDir), new JGitRemote(dataDir.resolve("mockhub").resolve("git-work")), snapshots,
            projects, secrets, new TargetGuard(config.gitAllowPrivate, InetAddress::getAllByName, "MOCKHUB_GIT_ALLOW_PRIVATE"),
            config.gitAllowPrivate, clock)));
    controllers.forEach(c -> c.register(api));
    McpTools mcpTools = new McpTools(new InternalApi(api.task(), policy),
        new ReviewStager(staged, assistant::tools, policy),
        resource("/mcp/reference.md"), clock);
    McpServer mcpServer = new McpServer(mcpTools, Main.VERSION);

    PublicLimits publicLimits = new PublicLimits(rateLimiter);
    TemplatePlayground templatePlayground = new TemplatePlayground(Stream.concat(Stream.of(fakeHelpers, valueHelpers, validationHelpers, jwtHelpers), portableHelpers.stream()).toList(), renderWorkers(), 2000);
    new TemplateController(templatePlayground).register(api);
    if (config.publicTools) orgs.ensureInstant();
    InstantMocks instantMocks = new InstantMocks(dataDir, projects, stubs, crud, () -> teams.ensureDefault(Organization.INSTANT_ID).id(), clock);
    Function<String, Optional<Principal>> publicUser =
        header -> Optional.ofNullable(authRef.get()).filter(AuthService::enabled).flatMap(a -> a.principal(header)).filter(p -> !p.anonymous());
    BooleanSupplier publicAiOn = () -> aiSettings.get().enabled();
    BinProject binProject = BinProject.around(bins, projects, stubs, crud, teamChoice, defaultResponses);
    PublicToolsFilter publicTools = new PublicToolsFilter(config.publicTools, publicLimits, List.of(
        new EchoTool(),
        new TotpTool(clock::millis),
        BinTool.create(bins, publicLimits, templatePlayground, new TargetGuard(config.binAllowPrivate, InetAddress::getAllByName, "MOCKHUB_BIN_ALLOW_PRIVATE"), tunnels, tunnels::isOnline,
            publicUser, publicAiOn, publicLlm, binProject),
        new FakeTool(new DataGenerator(fakeData), publicLimits, publicAiOn, publicLlm, publicUser),
        new RenderTool(templatePlayground, publicLimits),
        new InstantMockTool(instantMocks, history, publicLimits, importers, new InstantExamples(examples, exampleInstaller), specFetcher, graphQlExamples, grpc)));
    maintenance.scheduleWithFixedDelay(() -> safely(bins::prune), 10, 10, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(bins::flush), 5, 5, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(instantMocks::prune), 5, 60, TimeUnit.MINUTES);
    maintenance.scheduleWithFixedDelay(() -> safely(rateLimiter::prune), 10, 10, TimeUnit.MINUTES);

    ServedRequestListener otel = OtlpExporter.start(config.otel, new JdkHttpSender(), Main.VERSION);
    closeables.add(otel);
    List<Extension> extensions = new ArrayList<>(List.of(
        server,
        new GrpcRequestFilter(),
        publicTools,
        new InstantMockFilter(projectForPath, instantMocks),
        new SandboxTemplateTransformer(templatePlayground),
        new PersistenceListener(),
        new CrudTransformer(crud),
        new ErrorRateTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        new DisabledStubMatcher(),
        new CalloutListener(callouts, calloutScheduler),
        new HistoryRecorder(projectForPath, history, traffic, otel),
        new StateScopeListener(projectForPath, stateScope),
        new SyncCalloutTransformer(callouts),
        new GraphQlTransformer(new GraphQlEngine(graphQlSchemas)),
        new SoapEndpointTransformer(),
        new EchoTransformer(projects::find),
        new ConnectionProxyTransformer(connectionAuth),
        new TunnelProxyTransformer(tunnels),
        new RuntimeFilter(projectForPath, corsHeaders, rateLimiter, loadMeter),
        new ApiKeyFilter(projectForPath, corsForRequest::headers),
        new EnvironmentFilter(projectForPath, environments, environmentScope),
        new RuntimeTransformer(projects::find, rateLimiter, loadMeter),
        new CorsTransformer(corsForRequest),
        new ProjectScopeMatcher(projectForPath),
        fakeHelpers,
        valueHelpers,
        datasetHelpers,
        new EnvHelpers(environmentScope),
        validationHelpers,
        new ValidationTransformer(),
        new WeightedTransformer(() -> ThreadLocalRandom.current().nextDouble()),
        jwtHelpers,
        new AuthFilter(auth, policy),
        new MockHubAdminExtension(api, new McpTask(mcpServer, principals))));
    extensions.addAll(portableHelpers);
    extensions.addAll(new StateExtension(new ProjectStateStore(stateRepository, stateScope, clock)).create(services));
    return extensions;
  }

  private static String resource(String path) {
    try (InputStream in = MockHubExtensionFactory.class.getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing resource " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ExecutorService renderWorkers() {
    return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
      Thread t = new Thread(r, "mockhub-public-render");
      t.setDaemon(true);
      return t;
    });
  }

  private static void safely(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException e) {
      System.getLogger(MockHubExtensionFactory.class.getName()).log(System.Logger.Level.WARNING, "Maintenance failed: " + e.getMessage());
    }
  }
}
