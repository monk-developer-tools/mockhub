package io.mockhub.publictools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BinRuleEdits {

  static final class Missing extends RuntimeException {
    Missing(String id) {
      super("No rule " + id + " in this bin", null, false, false);
    }
  }

  static final class Taken extends RuntimeException {
    Taken(String id) {
      super("The bin already has a rule " + id, null, false, false);
    }
  }

  private BinRuleEdits() {}

  static Optional<BinRule> find(List<BinRule> rules, String id) {
    return rules.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  static List<BinRule> create(List<BinRule> rules, JsonNode node, int at) {
    if (rules.size() >= BinRule.MAX_RULES) throw new IllegalArgumentException("A bin holds up to " + BinRule.MAX_RULES + " rules");
    BinRule rule = BinRule.from(node);
    String asked = node.path("id").asText("");
    if (!asked.isEmpty() && find(rules, asked).isPresent()) throw new Taken(asked);
    while (find(rules, rule.id()).isPresent()) rule = BinRule.from(withoutId(rule.toJson()));
    List<BinRule> out = new ArrayList<>(rules);
    out.add(at < 0 || at > out.size() ? out.size() : at, rule);
    return List.copyOf(out);
  }

  static List<BinRule> replace(List<BinRule> rules, String id, JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException("A rule is a JSON object");
    return swap(rules, id, ((ObjectNode) node.deepCopy()).put("id", id));
  }

  static List<BinRule> patch(List<BinRule> rules, String id, JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException("Send the fields to change as a JSON object");
    ObjectNode merged = find(rules, id).orElseThrow(() -> new Missing(id)).toJson();
    node.fields().forEachRemaining(e -> {
      if (e.getValue().isNull()) merged.remove(e.getKey());
      else merged.set(e.getKey(), e.getValue());
    });
    return swap(rules, id, merged.put("id", id));
  }

  static List<BinRule> delete(List<BinRule> rules, String id) {
    find(rules, id).orElseThrow(() -> new Missing(id));
    return rules.stream().filter(r -> !r.id().equals(id)).toList();
  }

  static List<BinRule> reorder(List<BinRule> rules, JsonNode ids) {
    if (!ids.isArray()) throw new IllegalArgumentException("Send the rule ids in their new order, like {\"ids\": [\"a1\", \"b2\"]}");
    List<BinRule> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode n : ids) {
      String id = n.asText("");
      if (!seen.add(id)) throw new IllegalArgumentException("Rule " + id + " is listed twice");
      out.add(find(rules, id).orElseThrow(() -> new Missing(id)));
    }
    if (out.size() != rules.size()) throw new IllegalArgumentException("List every rule once: the bin has " + rules.size() + ", you sent " + out.size());
    return List.copyOf(out);
  }

  private static List<BinRule> swap(List<BinRule> rules, String id, ObjectNode node) {
    int index = indexOf(rules, id);
    if (index < 0) throw new Missing(id);
    List<BinRule> out = new ArrayList<>(rules);
    out.set(index, BinRule.from(node));
    return List.copyOf(out);
  }

  private static int indexOf(List<BinRule> rules, String id) {
    for (int i = 0; i < rules.size(); i++) if (rules.get(i).id().equals(id)) return i;
    return -1;
  }

  private static ObjectNode withoutId(ObjectNode node) {
    node.remove("id");
    return node;
  }
}
