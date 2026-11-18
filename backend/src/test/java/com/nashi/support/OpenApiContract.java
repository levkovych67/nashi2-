package com.nashi.support;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.fail;

/**
 * Validates real responses against {@code contracts/openapi.yaml} (copied onto the test classpath as
 * {@code contract/openapi.yaml}): the status must be declared for the operation, the {@code Content-Type} must be one
 * of its media types, every header it declares must be present and match its schema, and the body must match the
 * declared schema. Schemas are checked as JSON Schema 2020-12 in the OpenAPI 3.1 dialect, with {@code format}
 * asserted ({@code uri}, {@code date-time}), so {@code null} where the contract says {@code string}, a wrong type or
 * a bad format fails. {@code $ref}s resolve inside the contract document.
 * <p>
 * {@code com.nashi.ContractConformanceIT} compares the contract with springdoc's description;
 * this checks what the server actually sends.
 */
public final class OpenApiContract {

    /** The contract document's IRI for the validator; nothing is fetched from it. */
    private static final String BASE = "https://contract.nashi.test/openapi.json";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode DOC = load();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(Dialects.getOpenApi31(),
            builder -> builder
                    .schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true)
                            .locale(Locale.ENGLISH).build())
                    .schemas(Map.of(BASE, DOC.toString())));
    private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

    private OpenApiContract() {
    }

    /** Checks status, content type, declared headers and body of a response of {@code operationId}. */
    public static void assertResponse(String operationId, EntityExchangeResult<byte[]> result) {
        int status = result.getStatus().value();
        String pointer = operationPointer(operationId) + "/responses/" + status;
        JsonNode response = DOC.at(pointer);
        if (response.isMissingNode()) {
            fail(operationId + ": status " + status + " is not declared in the contract; body: " + text(result));
        }
        while (response.has("$ref")) {
            pointer = response.get("$ref").asString().substring(1);
            response = DOC.at(pointer);
        }
        List<String> problems = new ArrayList<>();
        checkHeaders(pointer, response, result.getResponseHeaders(), problems);
        checkBody(pointer, response, result, problems);
        if (!problems.isEmpty()) {
            fail(operationId + " " + status + " does not match the contract:\n  " + String.join("\n  ", problems)
                    + "\nbody: " + text(result));
        }
    }

    /** Checks a JSON document against {@code #/components/schemas/{name}}. */
    public static void assertSchema(String name, String json) {
        List<String> problems = validate("/components/schemas/" + escape(name), JSON.readTree(json));
        if (!problems.isEmpty()) {
            fail(name + " does not match the contract:\n  " + String.join("\n  ", problems) + "\njson: " + json);
        }
    }

    private static void checkHeaders(String pointer, JsonNode response, HttpHeaders headers, List<String> problems) {
        JsonNode declared = response.path("headers");
        for (String name : declared.propertyNames()) {
            String headerPointer = pointer + "/headers/" + escape(name);
            JsonNode header = declared.get(name);
            while (header.has("$ref")) {
                headerPointer = header.get("$ref").asString().substring(1);
                header = DOC.at(headerPointer);
            }
            String value = headers.getFirst(name);
            if (value == null) {
                problems.add("header " + name + " is missing");
                continue;
            }
            JsonNode node = "integer".equals(header.path("schema").path("type").asString())
                    && value.matches("-?\\d+")
                    ? JsonNodeFactory.instance.numberNode(Long.parseLong(value))
                    : JsonNodeFactory.instance.stringNode(value);
            validate(headerPointer + "/schema", node).forEach(p -> problems.add("header " + name + ": " + p));
        }
    }

    private static void checkBody(String pointer, JsonNode response, EntityExchangeResult<byte[]> result,
                                  List<String> problems) {
        byte[] body = result.getResponseBody();
        JsonNode content = response.get("content");
        if (content == null) {
            if (body != null && body.length > 0) {
                problems.add("the contract declares no body");
            }
            return;
        }
        MediaType type = result.getResponseHeaders().getContentType();
        String key = type == null ? null : type.getType() + "/" + type.getSubtype();
        if (key == null || !content.has(key)) {
            problems.add("Content-Type " + type + " is not one of " + content.propertyNames());
            return;
        }
        if (body == null || body.length == 0) {
            problems.add("the body is empty");
            return;
        }
        problems.addAll(validate(pointer + "/content/" + escape(key) + "/schema", JSON.readTree(body)));
    }

    private static List<String> validate(String pointer, JsonNode instance) {
        Schema schema = SCHEMAS.computeIfAbsent(pointer,
                p -> REGISTRY.getSchema(SchemaLocation.of(BASE + "#" + fragment(p))));
        return schema.validate(instance).stream().map(Error::toString).toList();
    }

    private static String operationPointer(String operationId) {
        JsonNode paths = DOC.get("paths");
        for (String path : paths.propertyNames()) {
            JsonNode item = paths.get(path);
            for (String method : item.propertyNames()) {
                if (METHODS.contains(method) && operationId.equals(item.get(method).path("operationId").asString())) {
                    return "/paths/" + escape(path) + "/" + method;
                }
            }
        }
        throw new IllegalArgumentException("No operation " + operationId + " in the contract");
    }

    /** JSON Pointer token (RFC 6901). */
    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /** A JSON Pointer as a URI fragment: path templates' braces percent-encoded. */
    private static String fragment(String pointer) {
        return pointer.replace("{", "%7B").replace("}", "%7D");
    }

    private static String text(EntityExchangeResult<byte[]> result) {
        byte[] body = result.getResponseBody();
        return body == null ? "<none>" : new String(body, StandardCharsets.UTF_8);
    }

    private static JsonNode load() {
        try (InputStream in = new ClassPathResource("contract/openapi.yaml").getInputStream()) {
            return YAMLMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
