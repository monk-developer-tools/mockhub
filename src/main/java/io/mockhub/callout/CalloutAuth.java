package io.mockhub.callout;

import io.mockhub.playground.Header;

public interface CalloutAuth {
  CalloutAuth NONE = (projectId, connection) -> {
    throw new CalloutException("Connections are not available here");
  };

  Header header(String projectId, String connection);
}
