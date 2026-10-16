package io.github.meistermods.siliconic.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/** Reads process integers without silently truncating fractions or overflowing their range. */
public final class ProcessJson {
  public static int getInt(JsonObject json, String name) {
    JsonElement value = json.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new JsonParseException("Process field '" + name + "' must be a 32-bit integer");
    try {
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new JsonParseException(
          "Process field '" + name + "' must be a 32-bit integer", exception);
    }
  }

  public static int getInt(JsonObject json, String name, int fallback) {
    return json.has(name) ? getInt(json, name) : fallback;
  }

  private ProcessJson() {}
}
