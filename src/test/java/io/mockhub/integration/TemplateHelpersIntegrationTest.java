package io.mockhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateHelpersIntegrationTest {

  private static final String API = "/__admin/mockhub/api";

  @TempDir
  Path data;
  private MockHubServer server;
  private ApiClient api;
  private String projectId;

  @BeforeEach
  void start() {
    server = MockHubServer.start(data);
    api = server.api();
    projectId = api.post(API + "/projects", "{\"name\":\"Auth\",\"slug\":\"auth\"}").json().get("id").asText();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private void stub(String method, String url, int status, String body) {
    ObjectNode stub = Jsons.object();
    stub.putObject("request").put("method", method).put("urlPath", url);
    ObjectNode response = stub.putObject("response").put("status", status).put("body", body);
    response.putObject("headers").put("Content-Type", "application/json");
    response.putArray("transformers").add("response-template");
    ApiClient.Result created = api.post(API + "/projects/" + projectId + "/stubs", stub.toString());
    assertThat(created.status()).as(created.body()).isEqualTo(201);
  }

  private static JsonNode claims(String token) throws Exception {
    return Jsons.MAPPER.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
  }

  @Test
  void valueHelpersMeasureBuildStringifyAndConvert() {
    // Given
    stub("POST", "/values", 200, """
        {"nameLength": {{len (jsonPath request.body '$.name')}},
         "items": {{len (jsonPath request.body '$.items')}},
         "built": {{{object id=7 name='Ada'}}},
         "quoted": {{{json (jsonPath request.body '$.name')}}},
         "price": {{toNumber (jsonPath request.body '$.price')}},
         "bad": "{{toNumber 'abc' default='none'}}",
         "page": "{{default request.query.page '1'}}"}""");

    // When
    ApiClient.Result result = api.post("/auth/values", "{\"name\":\"Grace\",\"items\":[1,2,3],\"price\":\"19.50\"}");

    // Then
    assertThat(result.status()).as(result.body()).isEqualTo(200);
    JsonNode body = result.json();
    assertThat(body.get("nameLength").asInt()).isEqualTo(5);
    assertThat(body.get("items").asInt()).isEqualTo(3);
    assertThat(body.at("/built/id").asInt()).isEqualTo(7);
    assertThat(body.at("/built/name").asText()).isEqualTo("Ada");
    assertThat(body.get("quoted").asText()).isEqualTo("Grace");
    assertThat(body.get("price").asDouble()).isEqualTo(19.5);
    assertThat(body.get("bad").asText()).isEqualTo("none");
    assertThat(body.get("page").asText()).isEqualTo("1");
  }

  @Test
  void validationHelpersAnswerWithTheMessageAndStatusWhenARuleFails() {
    // Given
    stub("POST", "/signup", 201, """
        {{required (jsonPath request.body '$.name') 'name is required' status=422}}\
        {{isEmail (jsonPath request.body '$.email') 'Enter a valid e-mail'}}\
        {{check (matches (jsonPath request.body '$.code') '^[A-Z]{3}$') 'code must be three capital letters'}}\
        {"welcome": "{{jsonPath request.body '$.name'}}", "uuid": {{isUuid request.query.ref}} }""");

    // When
    ApiClient.Result valid = api.post("/auth/signup?ref=0b9a3c56-8f7e-4d6b-9a2c-1e2f3a4b5c6d", "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"code\":\"ABC\"}");
    ApiClient.Result noName = api.post("/auth/signup", "{\"email\":\"ada@example.com\",\"code\":\"ABC\"}");
    ApiClient.Result badEmail = api.post("/auth/signup", "{\"name\":\"Ada\",\"email\":\"not-an-email\",\"code\":\"ABC\"}");
    ApiClient.Result badCode = api.post("/auth/signup", "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"code\":\"abc\"}");

    // Then
    assertThat(valid.status()).as(valid.body()).isEqualTo(201);
    assertThat(valid.json().get("welcome").asText()).isEqualTo("Ada");
    assertThat(valid.json().get("uuid").asBoolean()).isTrue();
    assertThat(noName.status()).as(noName.body()).isEqualTo(422);
    assertThat(noName.json().get("error").asText()).isEqualTo("name is required");
    assertThat(badEmail.status()).as(badEmail.body()).isEqualTo(400);
    assertThat(badEmail.json().get("error").asText()).isEqualTo("Enter a valid e-mail");
    assertThat(badEmail.contentType()).contains("application/json");
    assertThat(badCode.json().get("error").asText()).isEqualTo("code must be three capital letters");
  }

  @Test
  void signedTokensVerifyAgainstTheJwksAndDecodeFromTheAuthorizationHeader() throws Exception {
    // Given
    stub("POST", "/oauth/token", 200, """
        {"access_token": "{{jwt sub=(jsonPath request.body '$.username') scope='read write' expiresIn=600}}",
         "shared": "{{jwt sub='svc' alg='HS256' secret='s3cret'}}", "token_type": "Bearer"}""");
    stub("GET", "/.well-known/jwks.json", 200, "{{{jwks}}}");
    stub("GET", "/me", 200, """
        {"user": "{{jwtDecode request.headers.Authorization 'sub'}}", "scope": "{{lookup (jwtDecode request.headers.Authorization) 'scope'}}"}""");

    // When
    JsonNode token = api.post("/auth/oauth/token", "{\"username\":\"ada\"}").json();
    JsonNode jwks = api.get("/auth/.well-known/jwks.json").json();
    String accessToken = token.get("access_token").asText();
    JsonNode me = api.send("GET", "/auth/me", null, null, Map.of("Authorization", "Bearer " + accessToken)).json();

    // Then
    String[] parts = accessToken.split("\\.");
    JsonNode header = Jsons.MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
    JsonNode key = jwks.at("/keys/0");
    assertThat(header.get("alg").asText()).isEqualTo("RS256");
    assertThat(header.get("kid").asText()).isEqualTo(key.get("kid").asText());
    PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
        new BigInteger(1, Base64.getUrlDecoder().decode(key.get("n").asText())),
        new BigInteger(1, Base64.getUrlDecoder().decode(key.get("e").asText()))));
    Signature rsa = Signature.getInstance("SHA256withRSA");
    rsa.initVerify(publicKey);
    rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    assertThat(rsa.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    JsonNode claims = claims(accessToken);
    assertThat(claims.get("sub").asText()).isEqualTo("ada");
    assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(600);
    String shared = token.get("shared").asText();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("s3cret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String[] sharedParts = shared.split("\\.");
    assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((sharedParts[0] + "." + sharedParts[1]).getBytes(StandardCharsets.US_ASCII))))
        .isEqualTo(sharedParts[2]);
    assertThat(me.get("user").asText()).isEqualTo("ada");
    assertThat(me.get("scope").asText()).isEqualTo("read write");
    assertThat(Files.exists(data.resolve("mockhub").resolve("jwt-key.json"))).isTrue();
  }

  @Test
  void weightedResponsesPickByWeightAndStillApplyTemplating() {
    // Given
    ObjectNode stub = Jsons.object();
    stub.putObject("request").put("method", "GET").put("urlPathTemplate", "/flaky/{id}");
    ObjectNode response = stub.putObject("response").put("status", 200);
    response.putArray("transformers").add("mockhub-weighted").add("response-template");
    var responses = response.putObject("transformerParameters").putArray("responses");
    responses.addObject().put("weight", 3).put("status", 200).put("body", "ok {{request.path.id}}");
    responses.addObject().put("weight", 1).put("status", 503).put("body", "busy").putObject("headers").put("Retry-After", "5");
    responses.addObject().put("weight", 0).put("status", 418).put("body", "never");
    ApiClient.Result created = api.post(API + "/projects/" + projectId + "/stubs", stub.toString());
    assertThat(created.status()).as(created.body()).isEqualTo(201);

    // When
    java.util.List<ApiClient.Result> results = java.util.stream.IntStream.range(0, 200).mapToObj(i -> api.get("/auth/flaky/42")).toList();

    // Then
    long ok = results.stream().filter(r -> r.status() == 200).count();
    long busy = results.stream().filter(r -> r.status() == 503).count();
    assertThat(ok + busy).isEqualTo(200);
    assertThat(ok).isBetween(120L, 180L);
    assertThat(results.stream().filter(r -> r.status() == 200).map(ApiClient.Result::body).distinct().toList()).containsExactly("ok 42");
    assertThat(results.stream().filter(r -> r.status() == 503).map(ApiClient.Result::body).distinct().toList()).containsExactly("busy");
  }

  @Test
  void stubsUseTheBeeceptorStyleHelpersAgainstTheRequest() {
    // Given
    stub("POST", "/orders", 200, """
        {"light": "{{#switch (jsonPath request.body '$.color')}}{{#case 'green'}}Go{{/case}}{{#default}}Stop{{/default}}{{/switch}}",
         "due": "{{dateAdd (jsonPath request.body '$.placed') '{days:3}' 'short'}}",
         "late": {{dateDiff (jsonPath request.body '$.placed') '2026-03-10T00:00:00Z' 'days'}},
         "code": "{{padStart (jsonPath request.body '$.n') 5 '0'}}",
         "lines": [{{#repeat 3}}{{@index}}{{/repeat}}],
         "first": "{{coalesce request.query.nickname 'guest'}}",
         "kept": "[{{coalesce (jsonPath request.body '$.nickname') 'guest'}}]",
         "number": {{isInteger (jsonPath request.body '$.n')}} }""");

    // When
    ApiClient.Result answer = api.send("POST", "/auth/orders", "{\"color\": \"green\", \"placed\": \"2026-02-25T05:15:05Z\", \"n\": 42}",
        "application/json", Map.of());

    // Then
    assertThat(answer.status()).as(answer.body()).isEqualTo(200);
    JsonNode json = answer.json();
    assertThat(json.get("light").asText()).isEqualTo("Go");
    assertThat(json.get("due").asText()).isEqualTo("2026-02-28");
    assertThat(json.get("late").asInt()).isEqualTo(12);
    assertThat(json.get("code").asText()).isEqualTo("00042");
    assertThat(json.get("lines").toString()).isEqualTo("[0,1,2]");
    assertThat(json.get("first").asText()).isEqualTo("guest");
    assertThat(json.get("kept").asText()).isEqualTo("[]");
    assertThat(json.get("number").asBoolean()).isTrue();
  }
}
