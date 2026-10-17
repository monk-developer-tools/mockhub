package io.mockhub.ext;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Urls;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.mockhub.api.Jsons;
import io.mockhub.project.ApiKey;
import io.mockhub.project.Project;
import java.util.Optional;
import java.util.function.Function;

public final class ApiKeyFilter implements StubRequestFilterV2 {
  private final Function<String, Optional<Project>> projectBySlug;

  public ApiKeyFilter(Function<String, Optional<Project>> projectBySlug) {
    this.projectBySlug = projectBySlug;
  }

  @Override
  public String getName() {
    return "mockhub-api-key";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    if (request.getMethod().equals(RequestMethod.OPTIONS) || isWsdl(request)) return RequestFilterAction.continueWith(request);
    String slug = firstSegment(Urls.getPath(request.getUrl()));
    if (slug.isEmpty()) return RequestFilterAction.continueWith(request);
    ApiKey key = projectBySlug.apply(slug).map(Project::apiKey).orElse(null);
    if (key == null || key.accepts(request.getHeader(key.header()))) return RequestFilterAction.continueWith(request);
    return RequestFilterAction.stopWith(rejection(request, key));
  }

  static String firstSegment(String path) {
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    int slash = trimmed.indexOf('/');
    return slash < 0 ? trimmed : trimmed.substring(0, slash);
  }

  private static boolean isWsdl(Request request) {
    return request.getMethod().equals(RequestMethod.GET)
        && Urls.splitQueryFromUrl(request.getUrl()).keySet().stream().anyMatch(k -> k.equalsIgnoreCase("wsdl"));
  }

  private static ResponseDefinition rejection(Request request, ApiKey key) {
    String message = request.getHeader(key.header()) == null ? "Missing API key" : "Invalid API key";
    String contentType = Optional.ofNullable(request.getHeader("Content-Type")).orElse("").toLowerCase();
    if (contentType.contains("soap+xml")) {
      return xml("application/soap+xml; charset=utf-8", "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body><soap:Fault>"
          + "<soap:Code><soap:Value>soap:Sender</soap:Value></soap:Code><soap:Reason><soap:Text xml:lang=\"en\">" + message
          + ". Send it in the " + key.header() + " header.</soap:Text></soap:Reason></soap:Fault></soap:Body></soap:Envelope>");
    }
    if (contentType.contains("xml") || request.getHeader("SOAPAction") != null) {
      return xml("text/xml; charset=utf-8", "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><soap:Fault>"
          + "<faultcode>soap:Client</faultcode><faultstring>" + message + ". Send it in the " + key.header() + " header.</faultstring>"
          + "</soap:Fault></soap:Body></soap:Envelope>");
    }
    return new ResponseDefinitionBuilder().withStatus(401)
        .withHeader("Content-Type", "application/json")
        .withBody(Jsons.write(Jsons.object().put("error", message + ". Send it in the " + key.header() + " header.").put("header", key.header())))
        .build();
  }

  private static ResponseDefinition xml(String contentType, String body) {
    return new ResponseDefinitionBuilder().withStatus(401).withHeader("Content-Type", contentType).withBody(body).build();
  }
}
