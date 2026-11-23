package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.TreeSet;

/** Only the three fields declared as Set in the intent contract are unordered. */
final class IntentSnapshotComparison {
 private IntentSnapshotComparison() {}

 static boolean matches(JsonNode reviewed, JsonNode current) {
  var left = canonical(reviewed);
  var right = canonical(current);
  return left != null && right != null && left.equals(right);
 }

 private static JsonNode canonical(JsonNode source) {
  if (source == null || !source.isObject()) return null;
  ObjectNode copy = source.deepCopy();
  if (!sortSet(copy.path("policy"), "allowedOutcomes")
      || !sortSet(copy.path("policy"), "allowedProviderIds")
      || !sortSet(copy.path("instruction"), "allowedAlternatives")) return null;
  return copy;
 }

 private static boolean sortSet(JsonNode parent, String field) {
  if (!(parent instanceof ObjectNode object) || !object.path(field).isArray()) return false;
  var members = new TreeSet<String>();
  for (var value : object.path(field)) {
   if (!value.isTextual() || value.textValue().isBlank() || !members.add(value.textValue())) return false;
  }
  var sorted = object.putArray(field);
  members.forEach(sorted::add);
  return true;
 }
}
