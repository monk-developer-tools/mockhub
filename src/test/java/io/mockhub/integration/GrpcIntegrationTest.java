package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.api.Jsons;
import io.mockhub.support.GrpcTestClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GrpcIntegrationTest {

  private static final String API = "/__admin/mockhub/api";
  private static final String SERVICE = "library.v1.BookService";
  private static final byte[] LIBRARY = GrpcTestClient.resource("library.dsc");

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = project("Library", "library");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private String project(String name, String slug) {
    return api.post(API + "/projects", "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}").json().get("id").asText();
  }

  private ApiClient.Result upload(String project, String name, byte[] content) {
    return api.postBytes(API + "/projects/" + project + "/grpc/descriptors?name=" + name, content, "application/octet-stream");
  }

  private JsonNode stub(String body) {
    ApiClient.Result r = api.post(API + "/projects/" + projectId + "/stubs", body);
    assertThat(r.status()).as(r.body()).isEqualTo(201);
    return r.json();
  }

  private static String grpcStub(String method, String match, String response) {
    return "{\"name\":\"" + method + "\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/" + SERVICE + "/" + method + "\""
        + (match == null ? "" : ",\"bodyPatterns\":[{\"equalToJson\":" + match + ",\"ignoreExtraElements\":true}]")
        + "},\"response\":" + response + ",\"metadata\":{\"mockhub\":{\"type\":\"grpc\"}}}";
  }

  @Test
  void listsServicesMethodsAndExampleMessagesOfAnUploadedDescriptor() {
    // Given
    ApiClient.Result uploaded = upload(projectId, "library.dsc", LIBRARY);

    // When
    JsonNode view = api.get(API + "/projects/" + projectId + "/grpc").json();

    // Then
    assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(201);
    assertThat(view.at("/descriptors/0/name").asText()).isEqualTo("library.dsc");
    assertThat(view.at("/services/0/name").asText()).isEqualTo(SERVICE);
    JsonNode getBook = view.at("/services/0/methods/0");
    assertThat(getBook.get("path").asText()).isEqualTo("/library.v1.BookService/GetBook");
    assertThat(getBook.get("kind").asText()).isEqualTo("unary");
    assertThat(getBook.at("/requestExample/id").asText()).isEqualTo("42");
    assertThat(getBook.at("/responseExample/genre").asText()).isEqualTo("FICTION");
    assertThat(getBook.at("/responseExample/publishedAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
    assertThat(view.at("/services/0/methods/1/kind").asText()).isEqualTo("server-streaming");
  }

  @Test
  void servesAServiceFromAnUploadedProtoFileWithoutCompilingIt() {
    // Given
    byte[] proto = GrpcTestClient.resource("library.proto");
    ApiClient.Result uploaded = upload(projectId, "library.proto", proto);
    stub(grpcStub("GetBook", "{\"id\":\"9\"}", "{\"status\":200,\"jsonBody\":{\"id\":\"9\",\"title\":\"Solaris\"}}"));

    // When
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.unary(SERVICE, "GetBook", "{\"id\":\"9\"}");
    }
    ApiClient.Result broken = upload(projectId, "broken.proto", "syntax = \"proto3\";\nmessage A { Nope b = 1; }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // Then
    assertThat(uploaded.status()).as(uploaded.body()).isEqualTo(201);
    assertThat(uploaded.json().at("/descriptors/0/name").asText()).isEqualTo("library.proto");
    assertThat(reply.status()).isEqualTo("OK");
    assertThat(read(reply.json()).get("title").asText()).isEqualTo("Solaris");
    assertThat(broken.status()).isEqualTo(400);
    assertThat(broken.body()).contains("Line 2: Unknown type Nope");
  }

  @Test
  void answersUnaryCallsFromStubsMatchedOnTheRequestMessage() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", "{\"id\":\"7\"}", "{\"status\":200,\"jsonBody\":{\"id\":\"7\",\"title\":\"Dune\",\"year\":1965,\"genre\":\"FICTION\"}}"));
    stub(grpcStub("GetBook", "{\"id\":\"404\"}", "{\"status\":200,\"headers\":{\"grpc-status-name\":\"NOT_FOUND\",\"grpc-status-reason\":\"No book 404\"}}"));

    // When
    GrpcTestClient.Reply found;
    GrpcTestClient.Reply missing;
    GrpcTestClient.Reply unmatched;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      found = client.unary(SERVICE, "GetBook", "{\"id\":\"7\"}");
      missing = client.unary(SERVICE, "GetBook", "{\"id\":\"404\"}");
      unmatched = client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}");
    }

    // Then
    assertThat(found.status()).isEqualTo("OK");
    assertThat(read(found.json()).get("title").asText()).isEqualTo("Dune");
    assertThat(read(found.json()).get("year").asInt()).isEqualTo(1965);
    assertThat(missing.status()).isEqualTo("NOT_FOUND");
    assertThat(missing.description()).isEqualTo("No book 404");
    assertThat(unmatched.status()).isEqualTo("UNIMPLEMENTED");
  }

  @Test
  void recordsGrpcCallsInTheHistoryOfTheProjectThatOwnsTheService() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\"}}"));

    // When
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}");
    }
    String history = api.get(API + "/projects/" + projectId + "/history").body();

    // Then
    assertThat(history).contains("/library.v1.BookService/GetBook");
  }

  @Test
  void keepsTheGrpcPathWithoutTheProjectPrefix() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);

    // When
    JsonNode created = stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\"}}"));
    JsonNode all = api.get("/__admin/mappings").json();

    // Then
    assertThat(created.at("/request/urlPath").asText()).isEqualTo("/library.v1.BookService/GetBook");
    assertThat(all.at("/mappings").findValuesAsText("urlPath")).contains("/library.v1.BookService/GetBook");
  }

  @Test
  void answersServerStreamingCallsWithOneMessage() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("ListBooks", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\",\"author\":\"Le Guin\"}}"));

    // When
    List<String> messages;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      messages = client.serverStream(SERVICE, "ListBooks", "{\"author\":\"Le Guin\"}");
    }

    // Then
    assertThat(messages).hasSize(1);
    assertThat(read(messages.get(0)).get("author").asText()).isEqualTo("Le Guin");
  }

  @Test
  void answersClientStreamingCallsFromTheFirstMessageThatMatchesAStub() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("AddBooks", "{\"title\":\"Emma\"}", "{\"status\":200,\"jsonBody\":{\"added\":2,\"lastTitle\":\"Emma\"}}"));

    // When
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.clientStream(SERVICE, "AddBooks", List.of("{\"title\":\"Persuasion\"}", "{\"title\":\"Emma\"}"));
    }

    // Then
    assertThat(reply.status()).as(reply.description()).isEqualTo("OK");
    assertThat(read(reply.json()).get("lastTitle").asText()).isEqualTo("Emma");
  }

  @Test
  void templatesTheResponseFromTheRequestMessage() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"body\":\"{\\\"id\\\":\\\"{{jsonPath request.body '$.id'}}\\\",\\\"title\\\":\\\"Book {{jsonPath request.body '$.id'}}\\\"}\",\"transformers\":[\"response-template\"]}"));

    // When
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.unary(SERVICE, "GetBook", "{\"id\":\"77\"}");
    }

    // Then
    assertThat(reply.status()).as(reply.description()).isEqualTo("OK");
    assertThat(read(reply.json()).get("title").asText()).isEqualTo("Book 77");
  }

  @Test
  void listsUploadedServicesThroughServerReflection() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);

    // When
    List<String> services;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      services = client.reflectedServices();
    }

    // Then
    assertThat(services).contains(SERVICE);
  }

  @Test
  void refusesGrpcCallsToAProjectThatRequiresAClientCertificateEvenWithAForgedHeader() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\"}}"));
    api.put(API + "/projects/" + projectId + "/runtime", "{\"requireClientCert\":true}");

    // When
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}", Map.of("x-mockhub-client-cert-subject", "CN=forged"));
    }

    // Then
    assertThat(reply.status()).isEqualTo("PERMISSION_DENIED");
  }

  @Test
  void refusesAServiceAlreadyServedByAnotherProject() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    String other = project("Other", "other");

    // When
    ApiClient.Result clash = upload(other, "books.dsc", LIBRARY);
    ApiClient.Result fine = upload(other, "other.dsc", GrpcTestClient.resource("other.dsc"));

    // Then
    assertThat(clash.status()).isEqualTo(400);
    assertThat(clash.json().get("error").asText()).contains("library.v1.BookService").contains("already served by library.dsc in project Library");
    assertThat(fine.status()).as(fine.body()).isEqualTo(201);
  }

  @Test
  void rejectsFilesThatAreNotDescriptorSets() {
    // When
    ApiClient.Result garbage = upload(projectId, "library.dsc", "syntax = \"proto3\";".getBytes());
    ApiClient.Result badName = upload(projectId, "library.proto", LIBRARY);

    // Then
    assertThat(garbage.status()).isEqualTo(400);
    assertThat(badName.status()).isEqualTo(400);
    assertThat(api.get(API + "/projects/" + projectId + "/grpc").json().get("descriptors")).isEmpty();
  }

  @Test
  void stopsServingAServiceOnceItsDescriptorIsDeleted() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\"}}"));

    // When
    ApiClient.Result deleted = api.delete(API + "/projects/" + projectId + "/grpc/descriptors?name=library.dsc");
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}");
    }

    // Then
    assertThat(deleted.status()).isEqualTo(204);
    assertThat(reply.status()).isEqualTo("UNIMPLEMENTED");
  }

  @Test
  void servesUploadedDescriptorsAgainAfterARestart() {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\",\"title\":\"Kindred\"}}"));
    server.close();

    // When
    server = MockHubServer.start(data);
    GrpcTestClient.Reply reply;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), LIBRARY)) {
      reply = client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}");
    }

    // Then
    assertThat(reply.status()).as(reply.description()).isEqualTo("OK");
    assertThat(read(reply.json()).get("title").asText()).isEqualTo("Kindred");
  }

  @Test
  void descriptorsTravelInBackups(@TempDir Path other) {
    // Given
    upload(projectId, "library.dsc", LIBRARY);
    stub(grpcStub("GetBook", null, "{\"status\":200,\"jsonBody\":{\"id\":\"1\",\"title\":\"Beloved\"}}"));
    String backup = api.get(API + "/backup").body();

    // When
    GrpcTestClient.Reply reply;
    JsonNode view;
    try (MockHubServer fresh = MockHubServer.start(other)) {
      fresh.api().post(API + "/restore", backup);
      view = fresh.api().get(API + "/projects/" + projectId + "/grpc").json();
      try (GrpcTestClient client = new GrpcTestClient(fresh.port(), LIBRARY)) {
        reply = client.unary(SERVICE, "GetBook", "{\"id\":\"1\"}");
      }
    }

    // Then
    assertThat(view.at("/descriptors/0/name").asText()).isEqualTo("library.dsc");
    assertThat(reply.status()).as(reply.description()).isEqualTo("OK");
    assertThat(read(reply.json()).get("title").asText()).isEqualTo("Beloved");
  }

  private static JsonNode read(String json) {
    try {
      return Jsons.MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void answersABidirectionalStreamWithOneMessageFromTheFirstMatchingStub() {
    // Given
    byte[] chat = GrpcTestClient.resource("chat.dsc");
    upload(projectId, "chat.dsc", chat);
    stub("{\"name\":\"Talk\",\"request\":{\"method\":\"POST\",\"urlPath\":\"/chat.v1.ChatService/Talk\","
        + "\"bodyPatterns\":[{\"equalToJson\":{\"text\":\"bye\"},\"ignoreExtraElements\":true}]},"
        + "\"response\":{\"status\":200,\"jsonBody\":{\"text\":\"see you\"}},\"metadata\":{\"mockhub\":{\"type\":\"grpc\"}}}");

    // When
    GrpcTestClient.Streamed talk;
    try (GrpcTestClient client = new GrpcTestClient(server.port(), chat)) {
      talk = client.bidiStream("chat.v1.ChatService", "Talk", List.of("{\"text\":\"hi\"}", "{\"text\":\"bye\"}"));
    }

    // Then
    assertThat(talk.status()).isEqualTo("OK");
    assertThat(talk.replies()).hasSize(1);
    assertThat(read(talk.replies().get(0)).get("text").asText()).isEqualTo("see you");
  }
}
