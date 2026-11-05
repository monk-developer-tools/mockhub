package io.mockhub.tunnel;

import io.mockhub.api.Jsons;
import io.mockhub.auth.Principal;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TunnelServlet extends HttpServlet {
  public static final String PATH = "/mockhub/tunnel";
  private static final Pattern NEXT = Pattern.compile("/([^/]+)/next");
  private static final Pattern RESPOND = Pattern.compile("/([^/]+)/responses/([^/]+)");
  private static final int MAX_MESSAGE = TunnelHub.MAX_BODY * 2;

  private final TunnelHub hub;
  private final Function<String, Optional<Principal>> authenticate;

  public TunnelServlet(TunnelHub hub, Function<String, Optional<Principal>> authenticate) {
    this.hub = hub;
    this.authenticate = authenticate;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    try {
      Principal principal = authenticate.apply(request.getHeader("Authorization"))
          .orElseThrow(() -> new TunnelException(401, "Sign in with an access token: --token mh_..."));
      Matcher next = NEXT.matcher(path);
      if (next.matches()) {
        poll(request, response, hub.next(next.group(1), principal));
        return;
      }
      Matcher respond = RESPOND.matcher(path);
      if (respond.matches()) {
        hub.respond(respond.group(1), principal, respond.group(2), TunnelMessages.readResponse(Jsons.MAPPER.readTree(read(request))));
        response.setStatus(204);
        return;
      }
      throw new TunnelException(404, "Unknown tunnel call " + path);
    } catch (TunnelException e) {
      error(response, e.status(), e.getMessage());
    } catch (IOException e) {
      error(response, 400, "Couldn't read the message: " + e.getMessage());
    }
  }

  private static void poll(HttpServletRequest request, HttpServletResponse response, CompletableFuture<TunnelMessages.Request> next) {
    AsyncContext async = request.startAsync();
    async.setTimeout(TunnelHub.POLL_WAIT.toMillis() + 15_000);
    next.whenComplete((delivered, failure) -> {
      try {
        if (failure != null) {
          Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
          error(response, cause instanceof TunnelException t ? t.status() : 500, cause.getMessage());
        } else if (delivered == null) {
          response.setStatus(204);
        } else {
          write(response, 200, Jsons.write(TunnelMessages.write(delivered)));
        }
      } catch (IOException ignored) {
        response.setStatus(500);
      } finally {
        async.complete();
      }
    });
  }

  private static byte[] read(HttpServletRequest request) throws IOException {
    try (InputStream in = request.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int n;
      while ((n = in.read(chunk)) != -1) {
        if (out.size() + n > MAX_MESSAGE) throw new TunnelException(413, "Answers through a tunnel can be up to 5 MB");
        out.write(chunk, 0, n);
      }
      return out.toByteArray();
    }
  }

  private static void error(HttpServletResponse response, int status, String message) throws IOException {
    write(response, status, Jsons.write(Jsons.object().put("error", message)));
  }

  private static void write(HttpServletResponse response, int status, String json) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }
}
