package io.mockhub.stub;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

public final class BoundedFilenameMaker extends FilenameMaker {

  public static final int MAX_BYTES = 200;
  private static final int KEPT_END = 48;

  private final FilenameMaker delegate;

  public BoundedFilenameMaker(FilenameMaker delegate) {
    this.delegate = delegate;
  }

  @Override
  public String filenameFor(StubMapping stub) {
    return shorten(delegate.filenameFor(stub));
  }

  @Override
  public String sanitizeUrl(String url) {
    return delegate.sanitizeUrl(url);
  }

  static String shorten(String name) {
    if (bytes(name) <= MAX_BYTES) return name;
    String end = name.substring(name.length() - KEPT_END);
    String start = name.substring(0, MAX_BYTES - KEPT_END - 1);
    while (bytes(start + "-" + end) > MAX_BYTES) start = start.substring(0, start.length() - 1);
    return start + "-" + end;
  }

  private static int bytes(String s) {
    return s.getBytes(UTF_8).length;
  }
}
