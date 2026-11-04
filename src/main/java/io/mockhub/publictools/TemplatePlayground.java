package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import com.github.tomakehurst.wiremock.extension.responsetemplating.TemplateEngine;
import com.github.tomakehurst.wiremock.http.ImmutableRequest;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.template.ValidationFailure;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public final class TemplatePlayground {
  public static final int MAX_TEMPLATE = 8 * 1024;
  public static final int MAX_BODY = 64 * 1024;
  public static final int MAX_OUTPUT = 256 * 1024;
  static final String CUT = "\n… cut at 256 KB";
  private static final Pattern PARTIAL = Pattern.compile("\\{\\{~?\\s*(>|#\\s*\\*)");
  private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private final TemplateSandbox sandbox;
  private final TemplateEngine models = new TemplateEngine(Map.of(), 1L, Set.of(), true, List.of());
  private final ExecutorService workers;
  private final long timeoutMillis;

  public TemplatePlayground(List<TemplateHelperProviderExtension> helpers, ExecutorService workers, long timeoutMillis) {
    this.sandbox = new TemplateSandbox(helpers);
    this.workers = workers;
    this.timeoutMillis = timeoutMillis;
  }

  public record Sample(String method, String url, Map<String, String> headers, String body) {}

  public record Result(String output, Integer status, String error) {
    static Result ok(String output) {
      return new Result(output, null, null);
    }

    static Result failed(String error) {
      return new Result(null, null, error);
    }
  }

  public Result render(String template, Sample sample) {
    Optional<String> refused = refuse(template, sample);
    if (refused.isPresent()) return Result.failed(refused.get());
    Future<Result> job;
    try {
      job = workers.submit(() -> RenderBudget.within(timeoutMillis, () -> apply(template, sample)));
    } catch (RejectedExecutionException e) {
      return Result.failed("The playground is busy. Try again in a moment.");
    }
    try {
      return job.get(timeoutMillis + 500, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      job.cancel(true);
      return Result.failed("The template took too long to render.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failed("Rendering was interrupted.");
    } catch (ExecutionException e) {
      return Result.failed(message(e.getCause()));
    }
  }

  static Optional<String> refuse(String template, Sample sample) {
    if (template == null || template.isBlank()) return Optional.of("Write a template to render.");
    if (template.length() > MAX_TEMPLATE) return Optional.of("Keep the template under 8 KB.");
    if (sample.body() != null && sample.body().length() > MAX_BODY) return Optional.of("Keep the sample body under 64 KB.");
    if (sample.method() != null && !sample.method().isBlank() && !METHODS.contains(sample.method().trim().toUpperCase(Locale.ROOT))) {
      return Optional.of("Pick one of " + String.join(", ", METHODS.stream().sorted().toList()) + ".");
    }
    if (PARTIAL.matcher(template).find()) return Optional.of("Partials are turned off in the public playground.");
    return Optional.empty();
  }

  public Result renderFor(String template, Request request, long budgetMillis) {
    return RenderBudget.within(budgetMillis, () -> apply(template, request));
  }

  private Result apply(String template, Sample sample) {
    return apply(template, SampleRequest.withCookies(request(sample)));
  }

  private Result apply(String template, Request request) {
    Map<String, Object> model = new HashMap<>(models.buildModelForRequest(request));
    Capped out = new Capped();
    try {
      sandbox.render(template, model, out);
      return Result.ok(out.text());
    } catch (IOException | RuntimeException e) {
      if (causedBy(e, Capped.Full.class)) return Result.ok(out.text() + CUT);
      String message = message(e);
      return ValidationFailure.in(message)
          .map(found -> new Result(null, found.status(), found.message()))
          .orElseGet(() -> Result.failed(message));
    }
  }

  private static String message(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof RenderBudget.Exceeded || t instanceof ValidationFailure) return t.getMessage();
    }
    return String.valueOf(error.getMessage());
  }

  private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (type.isInstance(t)) return true;
    }
    return false;
  }

  private static ImmutableRequest request(Sample sample) {
    String url = sample.url() == null || sample.url().isBlank() ? "/" : sample.url().startsWith("/") ? sample.url() : "/" + sample.url();
    String method = sample.method() == null || sample.method().isBlank() ? "GET" : sample.method().trim().toUpperCase(Locale.ROOT);
    ImmutableRequest.Builder builder = ImmutableRequest.create()
        .withAbsoluteUrl("http://playground.mockhub" + url)
        .withMethod(RequestMethod.fromString(method))
        .withClientIp("127.0.0.1")
        .withProtocol("HTTP/1.1")
        .withBody(sample.body() == null ? new byte[0] : sample.body().getBytes(StandardCharsets.UTF_8));
    if (sample.headers() != null) sample.headers().forEach((k, v) -> builder.withHeader(k, List.of(v == null ? "" : v)));
    return builder.build();
  }

  static final class Capped extends Writer {
    private final StringBuilder text = new StringBuilder();

    static final class Full extends RuntimeException {
      Full() {
        super("full", null, false, false);
      }
    }

    @Override
    public void write(char[] chars, int offset, int length) {
      int room = MAX_OUTPUT - text.length();
      if (length > room) {
        text.append(chars, offset, room);
        throw new Full();
      }
      text.append(chars, offset, length);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    String text() {
      return text.toString();
    }
  }
}
