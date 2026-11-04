package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.Request;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

final class SampleRequest {
  private SampleRequest() {}

  static Request withCookies(Request inner) {
    Map<String, Cookie> cookies = cookies(inner.getHeader("Cookie"));
    return (Request) Proxy.newProxyInstance(Request.class.getClassLoader(), new Class<?>[] {Request.class}, (proxy, method, args) -> {
      if (method.getName().equals("getCookies") && method.getParameterCount() == 0) return cookies;
      try {
        return method.invoke(inner, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    });
  }

  static Map<String, Cookie> cookies(String header) {
    Map<String, Cookie> out = new LinkedHashMap<>();
    if (header == null) return out;
    for (String pair : header.split(";")) {
      int eq = pair.indexOf('=');
      if (eq <= 0) continue;
      out.put(pair.substring(0, eq).trim(), new Cookie(pair.substring(eq + 1).trim()));
    }
    return out;
  }
}
