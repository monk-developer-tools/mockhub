package io.mockhub.grpc;

public final class GrpcException extends RuntimeException {
  public GrpcException(String message) {
    super(message);
  }
}
