package io.mockhub.template;

import java.util.ArrayList;
import java.util.Collection;

final class JsonList extends ArrayList<Object> {

  JsonList(Collection<?> items) {
    super(items);
  }

  @Override
  public String toString() {
    return JsonValue.stringify(this, false);
  }
}
