package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.grpc.ProtoFiles;
import io.mockhub.support.GrpcTestClient;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstantGrpcIntegrationTest {
  private static final String GREETER = """
      syntax = "proto3";
      package helloworld;
      message HelloRequest { string name = 1; }
      message HelloReply { string message = 1; int32 times = 2; }
      service Greeter {
        rpc SayHello (HelloRequest) returns (HelloReply);
        rpc SayHelloStream (HelloRequest) returns (stream HelloReply);
      }
      """;
  private static final byte[] CLIENT_DESCRIPTOR = ProtoFiles.compile(GREETER, "greeter.proto", "");

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data, Map.of("MOCKHUB_PUBLIC_TOOLS", "true"));
    api = server.api();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private JsonNode create(String proto) {
    ApiClient.Result r = api.post("/__public/mocks", Jsons.object().put("from", "grpc").put("text", proto).put("fileName", "greeter.proto").toString());
    assertThat(r.status()).as(r.body()).isEqualTo(201);
    return r.json();
  }

  private ApiClient.Result put(JsonNode mock, String stubId, String stub) {
    return api.send("PUT", "/__public/mocks/" + mock.get("slug").asText() + "/stubs/" + stubId, stub, "application/json",
        Map.of("X-Mock-Token", mock.get("token").asText()));
  }

  private JsonNode get(JsonNode mock, String part) {
    return api.send("GET", "/__public/mocks/" + mock.get("slug").asText() + part, null, null, Map.of("X-Mock-Token", mock.get("token").asText())).json();
  }

  @Test
  void twoVisitorsMockTheSameServiceWithoutAnAccountAndEachGetsTheirOwnAnswers() {
    // Given
    JsonNode first = create(GREETER);
    JsonNode second = create(GREETER);
    JsonNode stubs = get(second, "").get("stubs");
    JsonNode sayHello = null;
    for (JsonNode s : stubs) if (s.get("name").asText().equals("helloworld.Greeter/SayHello")) sayHello = s;
    String namespaced = sayHello.at("/request/urlPath").asText();
    ApiClient.Result changed = put(second, sayHello.get("id").asText(),
        "{\"name\":\"SayHello\",\"request\":{\"method\":\"POST\",\"urlPath\":\"" + namespaced + "\"},"
            + "\"response\":{\"status\":200,\"jsonBody\":{\"message\":\"Hi from the second mock\",\"times\":2}}}");

    // When
    GrpcTestClient.Reply fromFirst;
    GrpcTestClient.Reply fromSecond;
    GrpcTestClient.Reply noHeader;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), CLIENT_DESCRIPTOR)) {
      fromFirst = client.unary("helloworld.Greeter", "SayHello", "{\"name\":\"Ada\"}", Map.of("x-mockhub-mock", first.get("slug").asText()));
      fromSecond = client.unary("helloworld.Greeter", "SayHello", "{\"name\":\"Ada\"}", Map.of("x-mockhub-mock", second.get("slug").asText()));
      noHeader = client.unary("helloworld.Greeter", "SayHello", "{\"name\":\"Ada\"}");
    }
    JsonNode grpc = get(second, "/grpc");

    // Then
    assertThat(changed.status()).as(changed.body()).isEqualTo(200);
    assertThat(stubs).hasSize(2);
    assertThat(namespaced).isEqualTo("/" + second.get("slug").asText().replace('-', '_') + ".helloworld.Greeter/SayHello");
    assertThat(fromFirst.status()).isEqualTo("OK");
    assertThat(fromFirst.json()).contains("\"message\"").doesNotContain("second mock");
    assertThat(fromSecond.status()).isEqualTo("OK");
    assertThat(fromSecond.json()).contains("Hi from the second mock");
    assertThat(noHeader.status()).isEqualTo("UNIMPLEMENTED");
    assertThat(grpc.get("header").asText()).isEqualTo("x-mockhub-mock");
    assertThat(grpc.at("/services/0/original").asText()).isEqualTo("helloworld.Greeter");
    assertThat(grpc.at("/services/0/methods/0/originalPath").asText()).isEqualTo("/helloworld.Greeter/SayHello");
    assertThat(grpc.at("/services/0/methods/1/kind").asText()).isEqualTo("server-streaming");
  }

  @Test
  void aClientCanCallTheNamespacedServiceDirectlyAndTheCallIsRecorded() {
    // Given
    JsonNode mock = create(GREETER);
    String namespace = mock.get("slug").asText().replace('-', '_');
    byte[] namespacedDescriptor = ProtoFiles.compile(GREETER, "greeter.proto", namespace);

    // When
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), namespacedDescriptor)) {
      reply = client.unary(namespace + ".helloworld.Greeter", "SayHello", "{\"name\":\"Bo\"}");
    }
    JsonNode requests = get(mock, "/requests");

    // Then
    assertThat(reply.status()).isEqualTo("OK");
    assertThat(requests.toString()).contains(namespace + ".helloworld.Greeter/SayHello");
  }

  @Test
  void aBrokenProtoFileIsRefusedWithTheLineAndNoMockIsLeftBehind() {
    // When
    ApiClient.Result refused = api.post("/__public/mocks", Jsons.object().put("from", "grpc")
        .put("text", "syntax = \"proto3\";\nservice S {\n  rpc Go (Missing) returns (Missing);\n}\n").toString());
    ApiClient.Result empty = api.post("/__public/mocks", Jsons.object().put("from", "grpc").put("text", "syntax = \"proto3\";\nmessage A { string b = 1; }\n").toString());

    // Then
    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.body()).contains("Line 3: Unknown type Missing");
    assertThat(empty.status()).isEqualTo(400);
    assertThat(empty.body()).contains("defines no service");
  }
}
