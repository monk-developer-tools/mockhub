package io.mockhub.support;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.Map;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.mockhub.grpc.ProtoFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GrpcTestClient implements AutoCloseable {
  private final ManagedChannel channel;
  private final List<FileDescriptor> files;

  public GrpcTestClient(int port, byte[] descriptorSet) {
    this.channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    this.files = ProtoFiles.parse(descriptorSet);
  }

  public static byte[] resource(String name) {
    try (InputStream in = GrpcTestClient.class.getResourceAsStream("/grpc/" + name)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Reply unary(String service, String method, String requestJson) {
    return unary(service, method, requestJson, Map.of());
  }

  public Reply unary(String service, String method, String requestJson, Map<String, String> metadata) {
    MethodDescriptor m = method(service, method);
    Metadata headers = new Metadata();
    metadata.forEach((k, v) -> headers.put(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER), v));
    Channel withHeaders = ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers));
    try {
      DynamicMessage reply = ClientCalls.blockingUnaryCall(withHeaders, grpcMethod(m, io.grpc.MethodDescriptor.MethodType.UNARY), options(), message(m, requestJson));
      return new Reply("OK", JsonFormat.printer().print(reply), null);
    } catch (StatusRuntimeException e) {
      return new Reply(e.getStatus().getCode().name(), null, e.getStatus().getDescription());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<String> serverStream(String service, String method, String requestJson) {
    MethodDescriptor m = method(service, method);
    Iterator<DynamicMessage> it = ClientCalls.blockingServerStreamingCall(channel, grpcMethod(m, io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING), options(), message(m, requestJson));
    List<String> out = new ArrayList<>();
    try {
      while (it.hasNext()) out.add(JsonFormat.printer().print(it.next()));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  public Reply clientStream(String service, String method, List<String> requestJsons) {
    MethodDescriptor m = method(service, method);
    CompletableFuture<DynamicMessage> reply = new CompletableFuture<>();
    StreamObserver<DynamicMessage> requests = ClientCalls.asyncClientStreamingCall(
        channel.newCall(grpcMethod(m, io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING), options()), observer(reply));
    requestJsons.forEach(json -> requests.onNext(message(m, json)));
    requests.onCompleted();
    try {
      return new Reply("OK", JsonFormat.printer().print(reply.get(20, TimeUnit.SECONDS)), null);
    } catch (ExecutionException e) {
      Status status = Status.fromThrowable(e.getCause());
      return new Reply(status.getCode().name(), null, status.getDescription());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Streamed bidiStream(String service, String method, List<String> requestJsons) {
    MethodDescriptor m = method(service, method);
    List<String> replies = new java.util.concurrent.CopyOnWriteArrayList<>();
    CompletableFuture<Void> done = new CompletableFuture<>();
    StreamObserver<DynamicMessage> requests = ClientCalls.asyncBidiStreamingCall(
        channel.newCall(grpcMethod(m, io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING), options()), new StreamObserver<>() {
          @Override
          public void onNext(DynamicMessage value) {
            try {
              replies.add(JsonFormat.printer().print(value));
            } catch (InvalidProtocolBufferException e) {
              done.completeExceptionally(e);
            }
          }

          @Override
          public void onError(Throwable t) {
            done.completeExceptionally(t);
          }

          @Override
          public void onCompleted() {
            done.complete(null);
          }
        });
    requestJsons.forEach(json -> requests.onNext(message(m, json)));
    requests.onCompleted();
    try {
      done.get(20, TimeUnit.SECONDS);
      return new Streamed("OK", List.copyOf(replies));
    } catch (ExecutionException e) {
      return new Streamed(Status.fromThrowable(e.getCause()).getCode().name(), List.copyOf(replies));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record Streamed(String status, List<String> replies) {}

  public List<String> reflectedServices() {
    CompletableFuture<List<String>> names = new CompletableFuture<>();
    StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc.newStub(channel).withDeadlineAfter(20, TimeUnit.SECONDS)
        .serverReflectionInfo(new StreamObserver<>() {
          @Override
          public void onNext(ServerReflectionResponse response) {
            names.complete(response.getListServicesResponse().getServiceList().stream().map(ServiceResponse::getName).toList());
          }

          @Override
          public void onError(Throwable t) {
            names.completeExceptionally(t);
          }

          @Override
          public void onCompleted() {
            names.complete(List.of());
          }
        });
    requests.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
    requests.onCompleted();
    try {
      return names.get(20, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T> StreamObserver<T> observer(CompletableFuture<T> result) {
    return new StreamObserver<>() {
      @Override
      public void onNext(T value) {
        result.complete(value);
      }

      @Override
      public void onError(Throwable t) {
        result.completeExceptionally(t);
      }

      @Override
      public void onCompleted() {
        result.complete(null);
      }
    };
  }

  private static CallOptions options() {
    return CallOptions.DEFAULT.withDeadlineAfter(20, TimeUnit.SECONDS);
  }

  private MethodDescriptor method(String service, String method) {
    return files.stream().flatMap(f -> f.getServices().stream())
        .filter(s -> s.getFullName().equals(service)).findFirst().orElseThrow()
        .findMethodByName(method);
  }

  private static io.grpc.MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod(MethodDescriptor m, io.grpc.MethodDescriptor.MethodType type) {
    return io.grpc.MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
        .setType(type)
        .setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(m.getService().getFullName(), m.getName()))
        .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(m.getInputType())))
        .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(m.getOutputType())))
        .build();
  }

  private static DynamicMessage message(MethodDescriptor m, String json) {
    DynamicMessage.Builder b = DynamicMessage.newBuilder(m.getInputType());
    try {
      JsonFormat.parser().merge(json, b);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
    return b.build();
  }

  @Override
  public void close() {
    channel.shutdownNow();
  }

  public record Reply(String status, String json, String description) {}
}
