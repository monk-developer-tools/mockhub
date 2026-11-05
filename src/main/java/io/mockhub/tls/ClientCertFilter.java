package io.mockhub.tls;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ClientCertFilter extends HttpFilter {
  static final String ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    Object attribute = request.getAttribute(ATTRIBUTE);
    X509Certificate[] chainOfCerts = attribute instanceof X509Certificate[] certs ? certs : null;
    Map<String, String> added = chainOfCerts != null && chainOfCerts.length > 0 ? ClientCertificates.headers(chainOfCerts[0]) : Map.of();
    chain.doFilter(new CertificateHeaders(request, added), response);
  }

  static final class CertificateHeaders extends HttpServletRequestWrapper {
    private final Map<String, String> added = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    CertificateHeaders(HttpServletRequest request, Map<String, String> added) {
      super(request);
      this.added.putAll(added);
    }

    @Override
    public String getHeader(String name) {
      if (ClientCertificates.isReserved(name)) return added.get(name);
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (!ClientCertificates.isReserved(name)) return super.getHeaders(name);
      String value = added.get(name);
      return Collections.enumeration(value == null ? List.of() : List.of(value));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      List<String> names = new ArrayList<>();
      for (Enumeration<String> e = super.getHeaderNames(); e != null && e.hasMoreElements(); ) {
        String name = e.nextElement();
        if (!ClientCertificates.isReserved(name)) names.add(name);
      }
      names.addAll(added.keySet());
      return Collections.enumeration(names);
    }
  }
}
