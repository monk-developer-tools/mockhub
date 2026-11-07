package io.mockhub.template;

import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.List;

public final class PortableHelpers {

  private PortableHelpers() {}

  public static List<TemplateHelperProviderExtension> providers() {
    return List.of(new FlowHelpers(), new CollectionHelpers(), new TextHelpers(), new DateMathHelpers());
  }
}
