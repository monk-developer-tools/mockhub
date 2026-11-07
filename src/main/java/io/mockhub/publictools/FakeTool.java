package io.mockhub.publictools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import io.mockhub.api.ApiException;
import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import io.mockhub.fake.DataGenerator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class FakeTool implements PublicTool {
  public static final int MAX_COUNT = 100;
  public static final int MAX_FIELDS = 20;
  public static final int MAX_SIZE = 1000;
  static final int MAX_REQUEST = 16 * 1024;

  private final DataGenerator generator;
  private final PublicLimits limits;
  private final FakeDrafter drafter;
  private final Function<String, Optional<Principal>> signedIn;

  public FakeTool(DataGenerator generator, PublicLimits limits) {
    this(generator, limits, () -> false, (system, user) -> "", header -> Optional.empty());
  }

  public FakeTool(DataGenerator generator, PublicLimits limits, BooleanSupplier aiOn, BinDrafter.Llm llm, Function<String, Optional<Principal>> signedIn) {
    this.generator = generator;
    this.limits = limits;
    this.drafter = new FakeDrafter(aiOn, llm, generator);
    this.signedIn = signedIn;
  }

  @Override
  public Optional<ResponseDefinition> handle(PublicRequest request) {
    if (request.path().equals("/fake/draft")) {
      if (request.method().equals("GET")) return Optional.of(PublicResponses.json(200, Jsons.object().put("available", drafter.available())));
      return Optional.of(request.method().equals("POST") ? draft(request) : PublicResponses.error(405, "Send a POST with {\"description\": \"...\"}"));
    }
    if (!request.path().equals("/fake")) return Optional.empty();
    if (!request.method().equals("POST")) return Optional.of(PublicResponses.error(405, "Send a POST with the fields to generate"));
    Optional<Long> wait = limits.take(request.clientIp(), PublicLimits.Kind.WORK);
    if (wait.isPresent()) return Optional.of(PublicResponses.tooMany(wait.get(), "requests"));
    return Optional.of(generate(request));
  }

  private ResponseDefinition draft(PublicRequest request) {
    if (!drafter.available()) return PublicResponses.error(409, "This server has no AI provider set up.");
    Optional<Principal> who = signedIn.apply(request.header("Authorization"));
    if (who.isEmpty()) return PublicResponses.error(401, "Sign in to MockHub to describe the data and let AI pick the fields.");
    if (request.bodySize() > MAX_REQUEST) return PublicResponses.error(413, "Keep the request under 16 KB");
    Optional<Long> wait = limits.take("user:" + who.get().id(), PublicLimits.Kind.AI);
    if (wait.isPresent()) return PublicResponses.tooMany(wait.get(), "AI requests");
    JsonNode body = PublicJson.parse(request.body());
    try {
      FakeDrafter.Draft draft = drafter.draft(body == null ? "" : body.path("description").asText(""));
      ObjectNode out = Jsons.object().put("count", draft.count()).put("locale", draft.locale());
      out.set("fields", Jsons.MAPPER.valueToTree(draft.fields()));
      out.set("skipped", Jsons.MAPPER.valueToTree(draft.skipped()));
      return PublicResponses.json(200, out);
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, e.getMessage());
    } catch (BinDrafter.Unavailable e) {
      return PublicResponses.error(409, e.getMessage());
    } catch (RuntimeException e) {
      return PublicResponses.error(502, "The AI provider didn't answer: " + e.getMessage());
    }
  }

  private ResponseDefinition generate(PublicRequest request) {
    if (request.bodySize() > MAX_REQUEST) return PublicResponses.error(413, "Keep the request under 16 KB");
    JsonNode body = PublicJson.parse(request.body());
    if (body == null || !body.isObject()) return PublicResponses.error(400, "Send JSON like {\"count\": 5, \"fields\": [{\"name\": \"id\", \"expression\": \"uuid\"}]}");
    List<DataGenerator.Field> fields;
    try {
      fields = Jsons.MAPPER.convertValue(body.path("fields"), new TypeReference<>() {});
    } catch (IllegalArgumentException e) {
      return PublicResponses.error(400, "fields must be a list of {name, expression}");
    }
    int count = body.path("count").asInt(10);
    if (count < 1 || count > MAX_COUNT) return PublicResponses.error(400, "Generate between 1 and " + MAX_COUNT + " records");
    if (fields != null && fields.size() > MAX_FIELDS) return PublicResponses.error(400, "Use at most " + MAX_FIELDS + " fields");
    if (fields != null && fields.stream().anyMatch(f -> f != null && f.expression() != null && RenderBudget.largestNumber(f.expression()) > MAX_SIZE)) {
      return PublicResponses.error(400, "Numbers in expressions go up to " + MAX_SIZE + " here");
    }
    String locale = body.path("locale").asText("").trim();
    try {
      return PublicResponses.json(200, Jsons.object().set("records", generator.generate(fields, count, locale.isEmpty() ? null : locale)));
    } catch (ApiException e) {
      return PublicResponses.error(e.status, e.getMessage());
    }
  }
}
