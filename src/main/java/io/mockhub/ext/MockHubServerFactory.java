package io.mockhub.ext;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty12.Jetty12HttpServer;
import com.github.tomakehurst.wiremock.store.BlobStore;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.mockhub.domain.CustomDomainFilter;
import io.mockhub.tls.ClientCertFilter;
import io.mockhub.tls.JettyTls;
import io.mockhub.tls.TlsTarget;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.wiremock.grpc.internal.GrpcFilter;
import org.wiremock.grpc.internal.Jetty12GrpcHttpServerFactory;

public final class MockHubServerFactory extends Jetty12GrpcHttpServerFactory {
  private final Supplier<List<FileDescriptor>> descriptors;
  private final Consumer<TlsTarget> httpsReady;
  private final Function<String, Optional<String>> basePathForHost;

  public MockHubServerFactory(BlobStore unused, Supplier<List<FileDescriptor>> descriptors, Consumer<TlsTarget> httpsReady,
      Function<String, Optional<String>> basePathForHost) {
    super(unused);
    this.descriptors = descriptors;
    this.httpsReady = httpsReady;
    this.basePathForHost = basePathForHost;
  }

  @Override
  public String getName() {
    return "mockhub-server";
  }

  @Override
  public synchronized void loadFileDescriptors() {
    if (grpcFilter != null) grpcFilter.loadFileDescriptors(descriptors.get());
  }

  @Override
  public HttpServer buildHttpServer(Options options, AdminRequestHandler adminRequestHandler, StubRequestHandler stubRequestHandler) {
    return new Jetty12HttpServer(options, adminRequestHandler, stubRequestHandler) {
      @Override
      protected void decorateMockServiceContextBeforeConfig(ServletContextHandler context) {
        grpcFilter = new GrpcFilter(stubRequestHandler);
        loadFileDescriptors();
        context.addFilter(new FilterHolder(new CustomDomainFilter(basePathForHost)), "/*", EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(new ClientCertFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(grpcFilter), "/*", EnumSet.of(DispatcherType.REQUEST));
      }

      @Override
      protected void finalizeSetup(Options finalOptions) {
        super.finalizeSetup(finalOptions);
        httpsReady.accept(new JettyTls(httpsConnector, finalOptions.httpsSettings()));
      }
    };
  }
}
