package io.mockhub.runtime;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Map;

public final class CorsTransformer implements ResponseTransformerV2 {
  private final CorsForRequest cors;

  public CorsTransformer(CorsForRequest cors) {
    this.cors = cors;
  }

  @Override
  public String getName() {
    return "mockhub-cors";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public Response transform(Response response, ServeEvent event) {
    if (response.getHeaders().getHeader("Access-Control-Allow-Origin").isPresent()) return response;
    Map<String, String> headers = cors.headers(event.getRequest());
    if (headers.isEmpty()) return response;
    HttpHeaders merged = response.getHeaders();
    for (Map.Entry<String, String> h : headers.entrySet()) merged = merged.plus(new HttpHeader(h.getKey(), h.getValue()));
    return Response.Builder.like(response).but().headers(merged).build();
  }
}
