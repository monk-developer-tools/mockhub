package io.mockhub.playground;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public interface HttpSender {
  record Raw(int status, List<Header> headers, byte[] body, boolean truncated) {}

  Raw send(URI uri, String method, List<Header> headers, String body, Duration timeout, int maxBytes) throws Exception;
}
