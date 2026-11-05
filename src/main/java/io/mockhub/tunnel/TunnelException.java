package io.mockhub.tunnel;

public final class TunnelException extends RuntimeException {
  private final int status;

  public TunnelException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
