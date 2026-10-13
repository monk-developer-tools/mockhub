package io.mockhub.api;

public final class ApiException extends RuntimeException {
  public final int status;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  public static ApiException notFound(String what) {
    return new ApiException(404, what + " not found");
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, message);
  }
}
