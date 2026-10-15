package io.mockhub.ai;

public final class AiException extends RuntimeException {
  public AiException(String message) {
    super(message);
  }

  public AiException(String message, Throwable cause) {
    super(message, cause);
  }
}
