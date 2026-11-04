package io.mockhub.publictools;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.NumberHelper;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.tomakehurst.wiremock.common.RequestCache;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.WireMockHelpers;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TemplateSandbox {
  static final Set<String> TURNED_OFF = Set.of(
      "systemValue", "hostname", "file", "assign", "log", "partial", "block", "inline", "embedded", "precompile", "i18n", "i18nJs");
  private static final Set<String> SIZED = Set.of("range", "randomValue", "ljust", "rjust", "center", "pickRandom");

  private final Handlebars handlebars;
  private final ValueResolver[] resolvers;

  TemplateSandbox(List<TemplateHelperProviderExtension> providers) {
    Handlebars hb = new Handlebars().with(EscapingStrategy.NOOP);
    for (StringHelpers helper : StringHelpers.values()) {
      if (!helper.name().equals("now")) hb.registerHelper(helper.name(), helper);
    }
    for (NumberHelper helper : NumberHelper.values()) hb.registerHelper(helper.name(), helper);
    for (ConditionalHelpers helper : ConditionalHelpers.values()) hb.registerHelper(helper.name(), helper);
    for (WireMockHelpers helper : WireMockHelpers.values()) hb.registerHelper(helper.name(), helper);
    for (TemplateHelperProviderExtension provider : providers) provider.provideTemplateHelpers().forEach(hb::registerHelper);
    List<Map.Entry<String, Helper<?>>> registered = new ArrayList<>(hb.helpers());
    for (Map.Entry<String, Helper<?>> entry : registered) {
      String name = entry.getKey();
      hb.registerHelper(name, TURNED_OFF.contains(name) ? turnedOff(name) : guarded(name, entry.getValue()));
    }
    for (String name : TURNED_OFF) hb.registerHelper(name, turnedOff(name));
    this.handlebars = hb;
    this.resolvers = ValueResolver.defaultValueResolvers().stream().map(Counting::new).toArray(ValueResolver[]::new);
  }

  void render(String template, Map<String, Object> model, Writer out) throws IOException {
    try {
      Context context = Context.newBuilder(model).combine("requestCache", RequestCache.getCurrent()).resolver(resolvers).build();
      handlebars.compileInline(template).apply(context, out);
    } finally {
      RequestCache.onRequestEnd();
    }
  }

  private static Helper<Object> turnedOff(String name) {
    return (context, options) -> {
      throw new RenderBudget.Exceeded("The " + name + " helper is turned off in the public playground.");
    };
  }

  @SuppressWarnings("unchecked")
  private static Helper<Object> guarded(String name, Helper<?> helper) {
    Helper<Object> inner = (Helper<Object>) helper;
    boolean sized = SIZED.contains(name);
    boolean loop = name.equals("each");
    boolean expression = name.equals("fake");
    return (Object context, Options options) -> {
      RenderBudget budget = RenderBudget.current();
      budget.call();
      if (sized) {
        budget.size(context);
        for (Object param : options.params) budget.size(param);
        options.hash.values().forEach(budget::size);
      }
      if (expression) {
        budget.sizesIn(context);
        for (Object param : options.params) budget.sizesIn(param);
      }
      if (loop) budget.iterate(context);
      Object result = inner.apply(context, options);
      if (result instanceof CharSequence text) budget.content(text.length());
      else if (result instanceof Collection<?> items) budget.items(items);
      return result;
    };
  }

  private record Counting(ValueResolver inner) implements ValueResolver {
    @Override
    public Object resolve(Object context, String name) {
      return counted(inner.resolve(context, name));
    }

    @Override
    public Object resolve(Object context) {
      return counted(inner.resolve(context));
    }

    @Override
    public Set<Map.Entry<String, Object>> propertySet(Object context) {
      return inner.propertySet(context);
    }

    private static Object counted(Object value) {
      if (value instanceof CharSequence text) RenderBudget.current().content(text.length());
      return value;
    }
  }
}
