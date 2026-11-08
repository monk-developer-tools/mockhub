package io.mockhub.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RawHttpConnectionTest {

  private static CompletableFuture<Void> serve(ServerSocket server, List<String> responses) {
    return CompletableFuture.runAsync(() -> {
      try (Socket socket = server.accept()) {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        OutputStream out = socket.getOutputStream();
        for (String response : responses) {
          for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
            assertThat(line).doesNotContain("\r");
          }
          out.write(response.getBytes(StandardCharsets.US_ASCII));
          out.flush();
        }
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Test
  void readsLengthAndChunkedBodiesOnOneKeptAliveConnectionAndSeesAClose() throws Exception {
    // Given
    try (ServerSocket server = new ServerSocket(0)) {
      CompletableFuture<Void> done = serve(server, List.of(
          "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nContent-Type: text/plain\r\n\r\nhello",
          "HTTP/1.1 201 Created\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nabcd\r\n3;ext=1\r\nefg\r\n0\r\nX-Trailer: yes\r\n\r\n",
          "HTTP/1.1 404 Not Found\r\nContent-Length: 2\r\nConnection: close\r\n\r\nno"));
      byte[] request = RawHttpConnection.request("GET", "localhost", "/x", null, null);

      // When
      RawHttpConnection.Response first;
      RawHttpConnection.Response second;
      RawHttpConnection.Response third;
      try (RawHttpConnection connection = new RawHttpConnection("localhost", server.getLocalPort())) {
        first = connection.exchange(request);
        second = connection.exchange(request);
        third = connection.exchange(request);
      }
      done.join();

      // Then
      assertThat(first).isEqualTo(new RawHttpConnection.Response(200, 5, false));
      assertThat(second).isEqualTo(new RawHttpConnection.Response(201, 7, false));
      assertThat(third).isEqualTo(new RawHttpConnection.Response(404, 2, true));
    }
  }

  @Test
  void buildsARequestWithAContentLengthInBytes() {
    // Given
    String body = "{\"name\":\"Zoë\"}";

    // When
    String request = new String(RawHttpConnection.request("POST", "h:1", "/p", "application/json", body), StandardCharsets.UTF_8);

    // Then
    assertThat(request).isEqualTo("POST /p HTTP/1.1\r\nHost: h:1\r\nUser-Agent: mockhub-bench\r\nAccept: */*\r\n"
        + "Content-Type: application/json\r\nContent-Length: 15\r\n\r\n" + body);
  }
}
