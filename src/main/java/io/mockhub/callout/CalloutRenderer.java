package io.mockhub.callout;

import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.playground.Header;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class CalloutRenderer {
  private static final Set<String> NOT_FORWARDED = Set.of(
      "host", "content-length", "connection", "transfer-encoding", "accept-encoding", "expect", "upgrade", "keep-alive",
      "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-real-ip");

  private final Supplier<TemplateEngine> engine;

  public CalloutRenderer(Supplier<TemplateEngine> engine) {
    this.engine = engine;
  }

  public OutboundRequest render(CalloutSpec spec, ServeEvent event, LoggedResponse response) {
    Request incoming = event.getRequest();
    Map<String, Object> model = model(event, response);
    String url = apply(spec.url(), model).trim();
    if (spec.forward()) {
      Map<String, Header> headers = new LinkedHashMap<>();
      for (HttpHeader h : incoming.getHeaders().all()) {
        if (!NOT_FORWARDED.contains(h.key().toLowerCase(Locale.ROOT))) headers.put(h.key().toLowerCase(Locale.ROOT), new Header(h.key(), String.join(", ", h.values())));
      }
      spec.headers().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), new Header(k, apply(v, model))));
      return new OutboundRequest(incoming.getMethod().getName(), url, new ArrayList<>(headers.values()), incoming.getBodyAsString());
    }
    List<Header> headers = new ArrayList<>();
    spec.headers().forEach((k, v) -> headers.add(new Header(k, apply(v, model))));
    String method = apply(spec.method(), model).trim().toUpperCase(Locale.ROOT);
    return new OutboundRequest(method, url, headers, method.equals("GET") || method.equals("HEAD") ? "" : apply(spec.body(), model));
  }

  public OutboundRequest unrendered(CalloutSpec spec) {
    List<Header> headers = new ArrayList<>();
    spec.headers().forEach((k, v) -> headers.add(new Header(k, v)));
    return new OutboundRequest(spec.method().toUpperCase(Locale.ROOT), spec.url(), headers, spec.body());
  }

  private Map<String, Object> model(ServeEvent event, LoggedResponse response) {
    Map<String, Object> model = new HashMap<>(engine.get().buildModelForRequest(event));
    model.put("originalRequest", model.get("request"));
    if (response != null) {
      Map<String, Object> headers = new LinkedHashMap<>();
      if (response.getHeaders() != null) {
        for (HttpHeader h : response.getHeaders().all()) headers.put(h.key(), h.firstValue());
      }
      Map<String, Object> original = new HashMap<>();
      original.put("status", response.getStatus());
      original.put("headers", headers);
      original.put("body", response.getBodyAsString());
      model.put("originalResponse", original);
    }
    return model;
  }

  private String apply(String template, Map<String, Object> model) {
    if (template == null || template.isEmpty() || !template.contains("{{")) return template == null ? "" : template;
    try {
      return engine.get().getUncachedTemplate(template).apply(model);
    } catch (RuntimeException e) {
      throw new CalloutException("Template error in \"" + abbreviate(template) + "\": " + e.getMessage());
    }
  }

  private static String abbreviate(String s) {
    return s.length() > 60 ? s.substring(0, 60) + "…" : s;
  }
}
