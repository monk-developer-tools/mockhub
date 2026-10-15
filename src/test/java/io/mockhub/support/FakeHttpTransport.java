package io.mockhub.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.sso.HttpTransport;
import io.mockhub.sso.SsoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class FakeHttpTransport implements HttpTransport {
  private final Map<String, JsonNode> gets = new HashMap<>();
  private final Map<String, Function<Map<String, String>, JsonNode>> posts = new HashMap<>();
  public final List<Map<String, String>> postedForms = new ArrayList<>();
  public int getCount;

  public FakeHttpTransport onGet(String url, JsonNode response) {
    gets.put(url, response);
    return this;
  }

  public FakeHttpTransport onPost(String url, Function<Map<String, String>, JsonNode> response) {
    posts.put(url, response);
    return this;
  }

  @Override
  public JsonNode getJson(String url) {
    getCount++;
    JsonNode r = gets.get(url);
    if (r == null) throw new SsoException("No fake GET for " + url);
    return r;
  }

  @Override
  public JsonNode postForm(String url, Map<String, String> form) {
    postedForms.add(form);
    Function<Map<String, String>, JsonNode> r = posts.get(url);
    if (r == null) throw new SsoException("No fake POST for " + url);
    return r.apply(form);
  }
}
