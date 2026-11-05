package io.mockhub.grpc;

import com.github.tomakehurst.wiremock.store.BlobStore;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.List;
import java.util.function.Supplier;
import org.wiremock.grpc.internal.Jetty12GrpcHttpServerFactory;

public final class GrpcServerFactory extends Jetty12GrpcHttpServerFactory {
  private final Supplier<List<FileDescriptor>> descriptors;

  public GrpcServerFactory(BlobStore unused, Supplier<List<FileDescriptor>> descriptors) {
    super(unused);
    this.descriptors = descriptors;
  }

  @Override
  public String getName() {
    return "mockhub-grpc";
  }

  @Override
  public synchronized void loadFileDescriptors() {
    if (grpcFilter != null) grpcFilter.loadFileDescriptors(descriptors.get());
  }
}
