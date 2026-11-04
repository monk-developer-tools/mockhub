package io.mockhub.connection;

import io.mockhub.callout.CalloutAuth;
import io.mockhub.callout.CalloutException;
import io.mockhub.playground.Header;
import java.util.Optional;
import java.util.function.Function;

public final class ConnectionAuth implements CalloutAuth {
  private final ConnectionService connections;
  private final Function<String, Optional<String>> orgOfProject;

  public ConnectionAuth(ConnectionService connections, Function<String, Optional<String>> orgOfProject) {
    this.connections = connections;
    this.orgOfProject = orgOfProject;
  }

  @Override
  public Header header(String projectId, String connection) {
    String orgId = (projectId == null ? Optional.<String>empty() : orgOfProject.apply(projectId))
        .orElseThrow(() -> new CalloutException("Connection " + connection + " needs a stub that belongs to a project"));
    try {
      return connections.header(orgId, connection);
    } catch (ConnectionException e) {
      throw new CalloutException("Connection " + connection + ": " + e.getMessage());
    }
  }
}
