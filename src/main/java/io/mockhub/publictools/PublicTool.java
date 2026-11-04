package io.mockhub.publictools;

import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import java.util.Optional;

public interface PublicTool {
  Optional<ResponseDefinition> handle(PublicRequest request);
}
