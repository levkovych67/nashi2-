package com.nashi.artist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Reads {@code contracts/test-vectors/*.json}, which the build copies onto the test classpath. */
public final class TestVectors {

    private TestVectors() {
    }

    public static List<Map<String, String>> read(String file) {
        return read(file, new TypeToken<List<Map<String, String>>>() { }.getType());
    }

    public static <T> T read(String file, Type type) {
        try (InputStream in = TestVectors.class.getResourceAsStream("/test-vectors/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Missing test vector file " + file);
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            return new Gson().fromJson(reader, type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
