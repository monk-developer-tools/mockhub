package io.mockhub.publictools;

import io.mockhub.common.ClientAddress;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.Optional;

public final class PublicToolsFilter implements StubRequestFilterV2 {
  private final boolean enabled;
  private final PublicLimits limits;
  private final List<PublicTool> tools;

  public PublicToolsFilter(boolean enabled, PublicLimits limits, List<PublicTool> tools) {
    this.enabled = enabled;
    this.limits = limits;
    this.tools = tools;
  }

  @Override
  public String getName() {
    return "mockhub-public-tools";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    if (!enabled || !handles(Urls.getPath(request.getUrl()))) return RequestFilterAction.continueWith(request);
    if (request.getMethod().equals(RequestMethod.OPTIONS) && request.getHeader("Access-Control-Request-Method") != null
        && !BinTool.capturesAt(Urls.getPath(request.getUrl()).substring(PublicRequest.PREFIX.length()))) {
      return RequestFilterAction.stopWith(PublicResponses.preflight());
    }
    PublicRequest publicRequest = PublicRequest.from(request);
    PublicLimits.Kind kind = BinTool.usesBinRate(publicRequest.path()) ? PublicLimits.Kind.SEND : PublicLimits.Kind.ANY;
    Optional<Long> wait = limits.take(ClientAddress.of(request), kind);
    if (wait.isPresent()) return RequestFilterAction.stopWith(PublicResponses.tooMany(wait.get(), "requests"));
    for (PublicTool tool : tools) {
      Optional<ResponseDefinition> response = tool.handle(publicRequest);
      if (response.isPresent()) return RequestFilterAction.stopWith(response.get());
    }
    return RequestFilterAction.stopWith(PublicResponses.error(404, "No public tool at " + PublicRequest.PREFIX + publicRequest.path()));
  }

  static boolean handles(String path) {
    return path.equals(PublicRequest.PREFIX) || path.startsWith(PublicRequest.PREFIX + "/");
  }
}
