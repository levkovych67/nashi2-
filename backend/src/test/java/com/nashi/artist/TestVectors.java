package com.nashi.artist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Reads {@code contracts/test-vectors/*.json}, which the build copies onto the test classpath. */
final class TestVectors {

    private TestVectors() {
    }

    static List<Map<String, String>> read(String file) {
        try (InputStream in = TestVectors.class.getResourceAsStream("/test-vectors/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Missing test vector file " + file);
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            return new Gson().fromJson(reader, new TypeToken<List<Map<String, String>>>() { }.getType());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
