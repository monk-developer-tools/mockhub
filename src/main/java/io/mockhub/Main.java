package io.mockhub;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import io.mockhub.ext.MockHubExtensionFactory;
import io.mockhub.tunnel.TunnelClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Main {
  public static final String VERSION =
      Main.class.getPackage().getImplementationVersion() != null
          ? Main.class.getPackage().getImplementationVersion()
          : "dev";

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("tunnel")) {
      TunnelClient.main(Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    String level = System.getenv("MOCKHUB_LOG_LEVEL");
    if (level != null && !level.isBlank()) System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.trim().toLowerCase());
    Config config = new Config(System.getenv());
    List<String> userArgs = new ArrayList<>(config.extraWireMockArgs);
    userArgs.addAll(Arrays.asList(args));

    List<String> finalArgs = new ArrayList<>();
    addDefault(finalArgs, userArgs, "--port", String.valueOf(config.port));
    addDefault(finalArgs, userArgs, "--root-dir", config.dataDir.toString());
    if (config.httpsPort >= 0) addDefault(finalArgs, userArgs, "--https-port", String.valueOf(config.httpsPort));
    addDefault(finalArgs, userArgs, "--max-request-journal-entries", String.valueOf(config.journalMaxEntries));
    if (config.proxyEnabled) {
      config.proxyCaKeystore().getParent().toFile().mkdirs();
      if (!userArgs.contains("--enable-browser-proxying")) finalArgs.add("--enable-browser-proxying");
      addDefault(finalArgs, userArgs, "--ca-keystore", config.proxyCaKeystore().toString());
    }
    if (!userArgs.contains("--disable-extensions-scanning")) {
      finalArgs.add("--disable-extensions-scanning");
    }
    finalArgs.addAll(userArgs);

    MockHubOptions options = new MockHubOptions(finalArgs.toArray(String[]::new));
    if (options.help()) {
      System.out.println(options.helpText());
      return;
    }

    options.filesRoot().createIfNecessary();
    options.filesRoot().child("__files").createIfNecessary();
    options.filesRoot().child("mappings").createIfNecessary();
    MockHubExtensionFactory factory = new MockHubExtensionFactory(config);
    options.getDeclaredExtensions().add(factory);

    WireMockServer server = new WireMockServer(options);
    try {
      server.start();
      factory.serverStarted(server.port());
    } catch (FatalStartupException e) {
      System.out.println("MockHub could not start: " + e.getMessage());
      System.exit(1);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.stop();
      factory.serverStopped();
    }, "mockhub-shutdown"));

    System.out.printf(
        "%nMockHub %s is running%n  UI:      http://localhost:%d/__admin/mockhub/ui/%n  Mocks:   http://localhost:%d/<project>/...%s%s%n  Data:    %s%n  Auth:    %s%n%n",
        VERSION,
        server.port(),
        server.port(),
        server.isHttpsEnabled() ? String.format("%n  HTTPS:   https://localhost:%d/<project>/...", server.httpsPort()) : "",
        config.proxyEnabled ? String.format("%n  Proxy:   http://localhost:%d (choose its project on the Server page)", server.port()) : "",
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
