package io.mockhub.template;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;

public final class ValidationTransformer implements ResponseTransformerV2 {

  @Override
  public String getName() {
    return "mockhub-validation-response";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public Response transform(Response response, ServeEvent event) {
    if (response.getStatus() != 500) return response;
    return ValidationFailure.in(response.getBodyAsString())
        .map(found -> Response.Builder.like(response).but()
            .status(found.status())
            .headers(new HttpHeaders(new HttpHeader("Content-Type", "application/json")))
            .body(Jsons.object().put("error", found.message()).put("status", found.status()).toString())
            .build())
        .orElse(response);
  }
}
