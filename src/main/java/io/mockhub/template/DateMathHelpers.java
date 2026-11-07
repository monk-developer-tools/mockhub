package io.mockhub.template;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.Map;

public final class DateMathHelpers implements TemplateHelperProviderExtension {

  @Override
  public String getName() {
    return "mockhub-dates";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of(
        "dateParse", (Helper<Object>) (value, options) -> TemplateDates.parse(value, text(options, 0)),
        "dateAdd", (Helper<Object>) (value, options) -> {
          if (options.params.length == 0) throw new IllegalArgumentException("Say how much to add, like {{dateAdd date '{days:3}'}}");
          return TemplateDates.format(TemplateDates.add(TemplateDates.parse(value, null), options.param(0)), text(options, 1));
        },
        "dateDiff", (Helper<Object>) (value, options) -> {
          if (options.params.length < 2) throw new IllegalArgumentException("Give two dates and a unit, like {{dateDiff start end 'days'}}");
          return TemplateDates.diff(TemplateDates.parse(value, null), TemplateDates.parse(options.param(0), null), options.param(1).toString());
        });
  }

  private static String text(Options options, int index) {
    return options.params.length > index && options.param(index) != null ? options.param(index).toString() : null;
  }
}
