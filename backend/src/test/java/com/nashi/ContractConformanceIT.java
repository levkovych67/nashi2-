package com.nashi;

import com.nashi.support.MongoIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The implementation against {@code contracts/openapi.yaml} (copied onto the test classpath by the build). Compares the
 * contract with what springdoc derives from the controllers, for the operations in {@link #IMPLEMENTED_OPERATIONS}:
 * path, method, parameters (name, location, required, enum values), success response codes (2xx and 304; error codes
 * come from {@code @RestControllerAdvice}, which springdoc cannot attribute to operations, and are covered by the HTTP
 * ITs) and, for the named schemas, the property names and enum values. Every mismatch is collected, then reported
 * together.
 * <p>
 * Both documents are read as plain maps (SnakeYAML for the contract, Jackson for springdoc's JSON) with a small
 * {@code $ref} resolver instead of swagger-parser: only names, flags and enum values are compared, and this avoids
 * pulling a second Jackson/YAML stack into the build. T14 adds {@code createSubmission}, T21 {@code getFragment}.
 * <p>
 * What the server actually sends (status, {@code Content-Type}, declared headers, and bodies against the schemas with
 * types, nulls and formats) is checked by {@link com.nashi.support.OpenApiContract} in the HTTP ITs.
 */
class ContractConformanceIT extends MongoIT {

    /** operationId of the operations implemented so far. */
    private static final List<String> IMPLEMENTED_OPERATIONS = List.of(
            "getHealth", "searchPlaces", "listMusicians", "getMusician", "getMapPins", "getMapRegions",
            "getMapPlaces", "getFilters", "getPublicConfig", "addLikes",
            "createSubmission", "getFragment");

    /** Schemas whose property names are compared: the response/request bodies of the implemented operations. */
    private static final List<String> SCHEMAS = List.of(
            "MapPin", "MapPinsResponse", "RegionStat", "PlaceStat", "FilterDefinition", "FilterOption",
            "MusicianListItem", "MusicianPage", "MusicianDetails", "LikeRequest", "LikeResponse", "PlaceSuggestion",
            "PublicConfig", "Health", "Image", "ImageCredit", "Origin", "Link", "Materials", "ClipSummary",
            "TextSource", "LifeDates", "Work", "Video", "SubmissionRequest", "SubmissionAccepted", "Fragment");

    private static final List<String> ENUMS = List.of(
            "Region", "MusicianKind", "OriginBasis", "PinPrecision", "AudioSource", "LinkPlatform", "LinkGroup",
            "PlaceType", "WorkType", "FilterType");

    private static final String SERVER_PREFIX = "/api/v2";

    @Autowired
    private RestTestClient http;

    @Test
    void allTwelveOperationsAreCovered() {
        assertThat(IMPLEMENTED_OPERATIONS).hasSize(12).doesNotHaveDuplicates();
    }

    @Test
    @SuppressWarnings("unchecked")
    void implementedOperationsMatchContract() throws IOException {
        Map<String, Object> contract = contract();
        Map<String, Object> actual = springdoc();
        List<String> problems = new ArrayList<>();

        Map<String, Map<String, Object>> contractOps = operations(contract, "");
        Map<String, Map<String, Object>> actualOps = operations(actual, SERVER_PREFIX);
        for (String operationId : IMPLEMENTED_OPERATIONS) {
            Map<String, Object> expected = contractOps.get(operationId);
            Map<String, Object> found = actualOps.get(operationId);
            if (expected == null) {
                problems.add(operationId + ": not in the contract");
                continue;
            }
            if (found == null) {
                problems.add(operationId + ": not in the implementation (" + expected.get("_where") + ")");
                continue;
            }
            if (!expected.get("_where").equals(found.get("_where"))) {
                problems.add(operationId + ": contract " + expected.get("_where") + ", implementation "
                        + found.get("_where"));
            }
            compareParameters(operationId, contract, expected, actual, found, problems);
            compareSuccessCodes(operationId, expected, found, problems);
        }

        Map<String, Object> contractSchemas = schemas(contract);
        Map<String, Object> actualSchemas = schemas(actual);
        for (String name : SCHEMAS) {
            Map<String, Object> expected = (Map<String, Object>) contractSchemas.get(name);
            Map<String, Object> found = (Map<String, Object>) actualSchemas.get(name);
            if (expected == null) {
                problems.add("schema " + name + ": not in the contract");
            } else if (found == null) {
                problems.add("schema " + name + ": not in the implementation (springdoc has "
                        + new TreeSet<>(actualSchemas.keySet()) + ")");
            } else {
                Set<String> want = properties(expected);
                Set<String> have = properties(found);
                if (want.isEmpty()) {
                    problems.add("contract schema " + name + " has no properties (composition not supported by this test)");
                } else if (have.isEmpty()) {
                    problems.add("implementation schema " + name + " has no properties");
                } else if (!want.equals(have)) {
                    problems.add("schema " + name + ": properties differ, contract-only " + minus(want, have)
                            + ", implementation-only " + minus(have, want));
                }
            }
        }
        assertThat(problems).as("contract mismatches").isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enumsMatchContract() throws IOException {
        Map<String, Object> contractSchemas = schemas(contract());
        Map<String, Object> actualSchemas = schemas(springdoc());
        List<String> problems = new ArrayList<>();
        for (String name : ENUMS) {
            Map<String, Object> expected = (Map<String, Object>) contractSchemas.get(name);
            Map<String, Object> found = (Map<String, Object>) actualSchemas.get(name);
            if (expected == null) {
                problems.add("enum " + name + ": not in the contract");
            } else if (found == null) {
                problems.add("enum " + name + ": not a named schema in the implementation");
            } else {
                List<Object> want = (List<Object>) expected.get("enum");
                List<Object> have = (List<Object>) found.get("enum");
                if (want == null || have == null) {
                    problems.add("enum " + name + ": enum values missing (contract " + want + ", implementation "
                            + have + ")");
                } else if (!new LinkedHashSet<>(want).equals(new LinkedHashSet<>(have))) {
                    problems.add("enum " + name + ": values differ, contract-only " + minus(want, have)
                            + ", implementation-only " + minus(have, want));
                } else if (!want.equals(have)) {
                    problems.add("enum " + name + ": same values, different order: contract " + want
                            + ", implementation " + have);
                }
            }
        }
        // The contract's Region enum has 27 values.
        assertThat(((List<?>) ((Map<String, Object>) contractSchemas.get("Region")).get("enum"))).hasSize(27);
        assertThat(problems).as("contract mismatches").isEmpty();
    }

    // -- operations ---------------------------------------------------------------------------------------------

    /** operationId to the operation, plus {@code _where} = "METHOD /path" (the {@code prefix} stripped). */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> operations(Map<String, Object> doc, String prefix) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
        for (var path : paths.entrySet()) {
            String relative = path.getKey().startsWith(prefix) ? path.getKey().substring(prefix.length()) : path.getKey();
            for (var method : ((Map<String, Object>) path.getValue()).entrySet()) {
                if (!(method.getValue() instanceof Map<?, ?> op) || !op.containsKey("operationId")) {
                    continue;
                }
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) op);
                copy.put("_where", method.getKey().toUpperCase() + " " + relative);
                result.put((String) op.get("operationId"), copy);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void compareParameters(String operationId, Map<String, Object> contractDoc,
                                          Map<String, Object> expected, Map<String, Object> actualDoc,
                                          Map<String, Object> found, List<String> problems) {
        Map<String, Map<String, Object>> want = parameters(contractDoc, expected);
        Map<String, Map<String, Object>> have = parameters(actualDoc, found);
        if (want.isEmpty() && have.isEmpty() && expected.containsKey("parameters")) {
            problems.add(operationId + ": parameters could not be read from either document");
        }
        for (String key : want.keySet()) {
            if (!have.containsKey(key)) {
                problems.add(operationId + ": parameter " + key + " missing in the implementation");
            }
        }
        for (String key : have.keySet()) {
            if (!want.containsKey(key)) {
                problems.add(operationId + ": parameter " + key + " is not in the contract");
            }
        }
        for (var e : want.entrySet()) {
            Map<String, Object> w = e.getValue();
            Map<String, Object> h = have.get(e.getKey());
            if (h == null) {
                continue;
            }
            boolean wantRequired = Boolean.TRUE.equals(w.get("required"));
            boolean haveRequired = Boolean.TRUE.equals(h.get("required"));
            if (wantRequired != haveRequired) {
                problems.add(operationId + ": parameter " + e.getKey() + " required: contract " + wantRequired
                        + ", implementation " + haveRequired);
            }
            List<Object> wantEnum = enumOf(contractDoc, (Map<String, Object>) w.get("schema"));
            List<Object> haveEnum = enumOf(actualDoc, (Map<String, Object>) h.get("schema"));
            if (wantEnum != null || haveEnum != null) {
                if (wantEnum == null || haveEnum == null
                        || !new LinkedHashSet<>(wantEnum).equals(new LinkedHashSet<>(haveEnum))) {
                    problems.add(operationId + ": parameter " + e.getKey() + " enum: contract " + wantEnum
                            + ", implementation " + haveEnum);
                }
            }
        }
    }

    /** "name in" to the parameter, {@code $ref}s resolved. */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parameters(Map<String, Object> doc, Map<String, Object> operation) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<Object> list = (List<Object>) operation.getOrDefault("parameters", List.of());
        for (Object o : list) {
            Map<String, Object> p = resolve(doc, (Map<String, Object>) o);
            result.put(p.get("name") + " in " + p.get("in"), p);
        }
        return result;
    }

    /** Enum values of a parameter schema, looking through {@code $ref} and array {@code items}; null if not an enum. */
    @SuppressWarnings("unchecked")
    private static List<Object> enumOf(Map<String, Object> doc, Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Map<String, Object> s = resolve(doc, schema);
        if (s.get("enum") != null) {
            return (List<Object>) s.get("enum");
        }
        Object items = s.get("items");
        return items instanceof Map<?, ?> m ? enumOf(doc, (Map<String, Object>) m) : null;
    }

    @SuppressWarnings("unchecked")
    private static void compareSuccessCodes(String operationId, Map<String, Object> expected,
                                            Map<String, Object> found, List<String> problems) {
        Set<String> want = successCodes((Map<String, Object>) expected.get("responses"));
        Set<String> have = successCodes((Map<String, Object>) found.get("responses"));
        if (want.isEmpty()) {
            problems.add(operationId + ": the contract declares no success response (nothing to compare)");
        }
        if (!want.equals(have)) {
            problems.add(operationId + ": success response codes differ, contract " + want + ", implementation "
                    + have);
        }
        // A body declared by the contract must be declared by the implementation as well (an explicit
        // @ApiResponse must not drop the schema derived from the return type).
        Map<String, Object> wantResponses = (Map<String, Object>) expected.get("responses");
        Map<String, Object> haveResponses = (Map<String, Object>) found.get("responses");
        for (String code : want) {
            boolean wantBody = ((Map<String, Object>) wantResponses.get(code)).containsKey("content");
            Object haveResponse = haveResponses.get(code);
            if (wantBody && haveResponse instanceof Map<?, ?> r && !r.containsKey("content")) {
                problems.add(operationId + ": response " + code + " has no body schema in the implementation");
            }
        }
    }

    private static Set<String> successCodes(Map<String, Object> responses) {
        Set<String> codes = new TreeSet<>();
        for (String code : responses.keySet()) {
            if (code.startsWith("2") || code.equals("304")) {
                codes.add(code);
            }
        }
        return codes;
    }

    // -- schemas ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas(Map<String, Object> doc) {
        return (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> properties(Map<String, Object> schema) {
        Object props = schema.get("properties");
        return props == null ? Set.of() : new TreeSet<>(((Map<String, Object>) props).keySet());
    }

    private static Set<Object> minus(Iterable<?> a, Iterable<?> b) {
        Set<Object> result = new LinkedHashSet<>();
        a.forEach(result::add);
        b.forEach(result::remove);
        return result;
    }

    // -- documents ----------------------------------------------------------------------------------------------

    /** Follows local {@code $ref}s ({@code #/components/...}) until the node is concrete. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolve(Map<String, Object> doc, Map<String, Object> node) {
        Map<String, Object> current = node;
        while (current.get("$ref") instanceof String ref && ref.startsWith("#/")) {
            Object target = doc;
            for (String part : ref.substring(2).split("/")) {
                target = ((Map<String, Object>) target).get(part);
            }
            current = (Map<String, Object>) target;
        }
        return current;
    }

    private static Map<String, Object> contract() throws IOException {
        try (InputStream in = new ClassPathResource("contract/openapi.yaml").getInputStream()) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> springdoc() {
        String json = http.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonMapper.builder().build().readValue(json, Map.class);
    }
}
