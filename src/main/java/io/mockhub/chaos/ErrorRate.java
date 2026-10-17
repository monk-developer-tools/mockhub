package io.mockhub.chaos;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.stub.StubMetadata;
import java.util.Map;

public record ErrorRate(double percent, int status, String body, Fault fault) {
  public static final String KEY = "errorRate";
  public static final int DEFAULT_STATUS = 500;

  public static ErrorRate of(StubMapping stub) {
    if (!(StubMetadata.of(stub).get(KEY) instanceof Map<?, ?> m)) return null;
    double percent = number(m.get("percent"), 0);
    if (percent <= 0) return null;
    int status = (int) number(m.get("status"), DEFAULT_STATUS);
    Object body = m.get("body");
    return new ErrorRate(Math.min(100, percent), status < 100 || status > 599 ? DEFAULT_STATUS : status,
        body == null ? null : body.toString(), fault(m.get("fault")));
  }

  public boolean hits(double random) {
    return random * 100 < percent;
  }

  private static double number(Object value, double fallback) {
    if (value instanceof Number n) return n.doubleValue();
    try {
      return value == null ? fallback : Double.parseDouble(value.toString());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static Fault fault(Object value) {
    if (value == null || value.toString().isBlank()) return null;
    try {
      return Fault.valueOf(value.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
