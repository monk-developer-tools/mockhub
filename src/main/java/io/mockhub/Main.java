package io.mockhub;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.standalone.CommandLineOptions;
import io.mockhub.ext.MockHubExtensionFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Main {
  public static final String VERSION =
      Main.class.getPackage().getImplementationVersion() != null
          ? Main.class.getPackage().getImplementationVersion()
          : "dev";

  public static void main(String[] args) {
    Config config = new Config(System.getenv());
    List<String> userArgs = new ArrayList<>(config.extraWireMockArgs);
    userArgs.addAll(Arrays.asList(args));

    List<String> finalArgs = new ArrayList<>();
    addDefault(finalArgs, userArgs, "--port", String.valueOf(config.port));
    addDefault(finalArgs, userArgs, "--root-dir", config.dataDir.toString());
    addDefault(finalArgs, userArgs, "--max-request-journal-entries", String.valueOf(config.journalMaxEntries));
    if (config.stubCors && !userArgs.contains("--enable-stub-cors")) {
      finalArgs.add("--enable-stub-cors");
    }
    finalArgs.addAll(userArgs);

    CommandLineOptions options = new CommandLineOptions(finalArgs.toArray(String[]::new));
    if (options.help()) {
      System.out.println(options.helpText());
      return;
    }

    options.filesRoot().createIfNecessary();
    options.filesRoot().child("__files").createIfNecessary();
    options.filesRoot().child("mappings").createIfNecessary();
    options.getDeclaredExtensions().add(new MockHubExtensionFactory(config));

    WireMockServer server = new WireMockServer(options);
    try {
      server.start();
    } catch (FatalStartupException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "mockhub-shutdown"));

    System.out.printf(
        "%nMockHub %s is running%n  UI:      http://localhost:%d/__admin/mockhub/ui/%n  Mocks:   http://localhost:%d/<project>/...%n  Data:    %s%n  Auth:    %s%n%n",
        VERSION,
        server.port(),
        server.port(),
        options.filesRoot().getPath(),
        config.users.isEmpty() ? "users and SSO are managed in the UI" : config.users.size() + " user(s) from MOCKHUB_USERS");
  }

  private static void addDefault(List<String> out, List<String> userArgs, String flag, String value) {
    boolean overridden = userArgs.stream().anyMatch(a -> a.equals(flag) || a.startsWith(flag + "="));
    if (!overridden) {
      out.add(flag);
      out.add(value);
    }
  }
}
