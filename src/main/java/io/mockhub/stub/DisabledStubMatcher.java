package io.mockhub.stub;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestMatcherExtension;

public final class DisabledStubMatcher extends RequestMatcherExtension {
  @Override
  public String getName() {
    return StubSwitch.MATCHER;
  }

  @Override
  public MatchResult match(Request request, Parameters parameters) {
    return MatchResult.noMatch();
  }
}
