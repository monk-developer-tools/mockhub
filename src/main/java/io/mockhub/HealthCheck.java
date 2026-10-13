package io.mockhub;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheck {

  private HealthCheck() {}

  public static void main(String[] args) {
    String port = System.getenv().getOrDefault("PORT", "8080");
    System.exit(isHealthy("http://localhost:" + port) ? 0 : 1);
  }

  public static boolean isHealthy(String baseUrl) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/__admin/health")).timeout(Duration.ofSeconds(3)).build();
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }
}
