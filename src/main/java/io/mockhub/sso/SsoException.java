package io.mockhub.sso;

public final class SsoException extends RuntimeException {
  public SsoException(String message) {
    super(message);
  }

  public SsoException(String message, Throwable cause) {
    super(message, cause);
  }
}
