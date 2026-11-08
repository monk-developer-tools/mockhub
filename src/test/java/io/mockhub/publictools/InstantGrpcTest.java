package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstantGrpcTest {
  private static final String SLUG = "m-abcde23456";
  private static final String NAMESPACE = "m_abcde23456";

  private static HttpServletRequest request(String uri, String contentType, Map<String, String> headers) {
    return (HttpServletRequest) Proxy.newProxyInstance(InstantGrpcTest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getRequestURI" -> uri;
          case "getContentType" -> contentType;
          case "getHeader" -> headers.get((String) args[0]);
          case "getRequestURL" -> new StringBuffer("http://localhost:8080" + uri);
          default -> null;
        });
  }

  @Test
  void routesACallWithTheMockHeaderToThatMocksOwnCopyOfTheService() {
    // Given
    InstantGrpcRouter router = new InstantGrpcRouter(Set.of(NAMESPACE + ".helloworld.Greeter")::contains, true);
    Map<String, String> header = Map.of(InstantGrpc.HEADER, SLUG);

    // When
    HttpServletRequest routed = router.route(request("/helloworld.Greeter/SayHello", "application/grpc", header));
    HttpServletRequest plainHttp = router.route(request("/helloworld.Greeter/SayHello", "application/json", header));
    HttpServletRequest noHeader = router.route(request("/helloworld.Greeter/SayHello", "application/grpc+proto", Map.of()));
    HttpServletRequest badHeader = router.route(request("/helloworld.Greeter/SayHello", "application/grpc", Map.of(InstantGrpc.HEADER, "../x")));
    HttpServletRequest unknown = router.route(request("/other.Service/Go", "application/grpc", header));
    HttpServletRequest already = router.route(request("/" + NAMESPACE + ".helloworld.Greeter/SayHello", "application/grpc", header));

    // Then
    assertThat(routed.getRequestURI()).isEqualTo("/" + NAMESPACE + ".helloworld.Greeter/SayHello");
    assertThat(routed.getRequestURL().toString()).isEqualTo("http://localhost:8080/" + NAMESPACE + ".helloworld.Greeter/SayHello");
    assertThat(plainHttp.getRequestURI()).isEqualTo("/helloworld.Greeter/SayHello");
    assertThat(noHeader.getRequestURI()).isEqualTo("/helloworld.Greeter/SayHello");
    assertThat(badHeader.getRequestURI()).isEqualTo("/helloworld.Greeter/SayHello");
    assertThat(unknown.getRequestURI()).isEqualTo("/other.Service/Go");
    assertThat(already.getRequestURI()).isEqualTo("/" + NAMESPACE + ".helloworld.Greeter/SayHello");
  }

  @Test
  void marksOnlyStubsInTheMocksOwnNamespaceAsGrpc() {
    // Given
    ObjectNode own = Jsons.object();
    own.putObject("request").put("urlPath", "/" + NAMESPACE + ".helloworld.Greeter/SayHello");
    ObjectNode someoneElse = Jsons.object();
    someoneElse.putObject("request").put("urlPath", "/m_zzzzz99999.helloworld.Greeter/SayHello");
    ObjectNode signedIn = Jsons.object();
    signedIn.putObject("request").put("url", "/inventory.v1.StockService/GetItem");

    // When
    InstantGrpc.tag(own, SLUG);
    InstantGrpc.tag(someoneElse, SLUG);
    InstantGrpc.tag(signedIn, SLUG);

    // Then
    assertThat(own.at("/metadata/mockhub/type").asText()).isEqualTo("grpc");
    assertThat(someoneElse.has("metadata")).isFalse();
    assertThat(signedIn.has("metadata")).isFalse();
    assertThat(InstantGrpc.original(NAMESPACE + ".helloworld.Greeter", SLUG)).isEqualTo("helloworld.Greeter");
  }
}
