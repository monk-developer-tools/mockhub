package io.mockhub.grpc;

import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.wiremock.grpc.internal.GrpcRequest;

public final class GrpcRequestFilter implements StubRequestFilterV2 {
  @Override
  public String getName() {
    return "mockhub-grpc-request";
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    return RequestFilterAction.continueWith(request instanceof GrpcRequest ? new GrpcHeaderSafeRequest(request) : request);
  }
}
