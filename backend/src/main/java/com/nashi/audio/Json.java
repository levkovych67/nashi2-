package com.nashi.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

/** Null-safe reads from Gson objects; blank strings count as missing. */
final class Json {

    private Json() {
    }

    static Optional<String> string(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    static Optional<JsonObject> object(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }

    static Optional<JsonObject> first(JsonObject object, String arrayName) {
        JsonElement element = object == null ? null : object.get(arrayName);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()
                || !element.getAsJsonArray().get(0).isJsonObject()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsJsonArray().get(0).getAsJsonObject());
    }
}
