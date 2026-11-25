package com.nashi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The frontend generates its types from swagger.json, so it must describe what the API really sends.
 */
class SwaggerContractTest {

    private static final String REF = "#/components/schemas/";

    private static JsonObject schemas;
    private static JsonObject paths;

    @BeforeAll
    static void loadSwagger() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("swagger.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            schemas = root.getAsJsonObject("components").getAsJsonObject("schemas");
            paths = root.getAsJsonObject("paths");
        }
    }

    @Test
    void artistListTakesNoStatus() {
        // Public artist endpoints return APPROVED artists only, so there is nothing to filter by.
        JsonArray parameters = paths.getAsJsonObject("/api/v1/artists").getAsJsonObject("get").getAsJsonArray("parameters");
        List<String> names = new ArrayList<>();
        parameters.forEach(p -> names.add(p.getAsJsonObject().get("name").getAsString()));
        assertEquals(List.of("region", "pageable"), names);
    }

    @Test
    void mapPinRegionAndCityCoverEvents() {
        assertEquals("Artists and events", property("MapPinDTO", "region").get("description").getAsString());
        assertEquals("Artists and events", property("MapPinDTO", "city").get("description").getAsString());
    }

    @Test
    void artistViewDtoHasNewFields() {
        assertHasProperties("ArtistViewDTO",
                "categories", "birthDate", "deathDate", "birthPlace", "locationBasis", "genres", "works", "wikidataUrl");
    }

    @Test
    void mapPinDtoHasFilterFields() {
        assertHasProperties("MapPinDTO", "categories", "region", "city", "year", "genres");
    }

    @Test
    void artistDtoMinifiedHasCategories() {
        assertHasProperties("ArtistDTOMinified", "categories");
    }

    @Test
    void artistWorkSchemaExists() {
        assertHasProperties("ArtistWork",
                "wikidataId", "title", "year", "imageUrl", "fullImageUrl", "imageCredit", "articleUrl");
        assertEquals(REF + "ImageCredit", property("ArtistWork", "imageCredit").get("$ref").getAsString());
    }

    @Test
    void newFieldsPointAtSharedSchemas() {
        assertEquals(REF + "ArtistCategory", itemsRef("ArtistViewDTO", "categories"));
        assertEquals(REF + "ArtistWork", itemsRef("ArtistViewDTO", "works"));
        assertEquals(REF + "LocationBasis", property("ArtistViewDTO", "locationBasis").get("$ref").getAsString());
        assertEquals(REF + "ArtistCategory", itemsRef("MapPinDTO", "categories"));
        assertEquals(REF + "ArtistCategory", itemsRef("ArtistDTOMinified", "categories"));
    }

    @Test
    void enumSchemasMatchJava() {
        assertEquals(List.of("MUSICIAN", "BAND", "VISUAL_ARTIST", "WRITER"), enumOf(schema("ArtistCategory")));
        assertEquals(names(ArtistCategory.values()), enumOf(schema("ArtistCategory")));
        assertEquals(List.of("BIRTH", "FORMATION", "RESIDENCE", "WORK"), enumOf(schema("LocationBasis")));
        assertEquals(names(LocationBasis.values()), enumOf(schema("LocationBasis")));
    }

    @Test
    void regionEnumsMatchJava() throws IOException {
        List<String> java = names(Region.values());
        List<List<String>> regionEnums = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Path.of("swagger.json"), StandardCharsets.UTF_8)) {
            collectEnums(JsonParser.parseReader(reader), regionEnums);
        }
        // A region enum is any enum that names a region (parameters and schema properties alike).
        regionEnums.removeIf(values -> values.stream().noneMatch(java::contains));

        assertFalse(regionEnums.isEmpty(), "swagger.json has no Region enum");
        for (List<String> values : regionEnums) {
            assertEquals(java, values, "every Region enum in swagger.json must equal Region.values()");
        }
    }

    @Test
    void socialLinkPlatformEnumListsNewValues() {
        List<String> platforms = enumOf(property("SocialLinkDTO", "platform"));

        List<String> added = List.of("X", "DEEZER", "BANDCAMP", "DISCOGS", "MUSICBRAINZ", "LASTFM", "COMMONS",
                "WIKISOURCE", "GOODREADS", "OPEN_LIBRARY", "ARTSY", "WIKIDATA", "ESU", "CHTYVO", "WIKIART");
        List<String> missing = added.stream().filter(p -> !platforms.contains(p)).toList();
        assertTrue(missing.isEmpty(), "SocialLinkDTO.platform misses " + missing);
        assertEquals(names(SocialLink.Platform.values()), platforms, "swagger must list every Java platform, in order");
    }

    @Test
    void compressionIsOn() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertTrue("true".equals(properties.getProperty("server.compression.enabled")),
                "server.compression.enabled must be true");
        boolean json = Arrays.stream(properties.getProperty("server.compression.mime-types", "").split(","))
                .map(String::trim)
                .anyMatch("application/json"::equals);
        assertTrue(json, "server.compression.mime-types must include application/json");
    }

    // ------------------------------------------------------------------------

    private static JsonObject schema(String name) {
        JsonObject schema = schemas.getAsJsonObject(name);
        assertNotNull(schema, "swagger.json has no schema " + name);
        return schema;
    }

    private static JsonObject property(String schemaName, String propertyName) {
        JsonObject property = schema(schemaName).getAsJsonObject("properties").getAsJsonObject(propertyName);
        assertNotNull(property, schemaName + " has no property " + propertyName);
        return property;
    }

    private static String itemsRef(String schemaName, String propertyName) {
        return property(schemaName, propertyName).getAsJsonObject("items").get("$ref").getAsString();
    }

    private static void assertHasProperties(String schemaName, String... expected) {
        Set<String> actual = schema(schemaName).getAsJsonObject("properties").keySet();
        List<String> missing = Arrays.stream(expected).filter(p -> !actual.contains(p)).toList();
        assertTrue(missing.isEmpty(), schemaName + " misses " + missing);
    }

    private static List<String> enumOf(JsonObject schema) {
        JsonArray values = schema.getAsJsonArray("enum");
        assertNotNull(values, "schema has no enum");
        List<String> result = new ArrayList<>();
        for (JsonElement value : values) {
            result.add(value.getAsString());
        }
        return result;
    }

    private static void collectEnums(JsonElement element, List<List<String>> found) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectEnums(child, found));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("enum") && object.get("enum").isJsonArray()) {
                found.add(enumOf(object));
            }
            object.entrySet().forEach(entry -> collectEnums(entry.getValue(), found));
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toList());
    }
}
