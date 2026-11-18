package com.nashi.geo;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Centers and bounding boxes of the 27 regions, read from {@code geo/regions-meta.json}
 * (copied from {@code contracts/geo} by the Gradle build). Fails on startup if the file is not exactly the 27 regions.
 */
@Component
public class RegionCatalog {

    static final String RESOURCE = "geo/regions-meta.json";

    private record Raw(Region code, String name, double[] center, double[] bbox) {}

    private final String resource;
    private Map<Region, RegionMeta> byRegion = new EnumMap<>(Region.class);
    private List<RegionMeta> all = List.of();

    public RegionCatalog() {
        this(RESOURCE);
    }

    RegionCatalog(String resource) {
        this.resource = resource;
    }

    @PostConstruct
    void load() {
        Raw[] raw;
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            raw = JsonMapper.builder().build().readValue(in, Raw[].class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        Map<Region, RegionMeta> map = new EnumMap<>(Region.class);
        for (Raw r : raw) {
            if (r.code() == null || r.center() == null || r.center().length != 2
                    || r.bbox() == null || r.bbox().length != 4 || r.name() == null) {
                throw new IllegalStateException("Malformed region entry in " + resource + ": " + r.code());
            }
            if (map.put(r.code(), new RegionMeta(r.code(), r.name(), r.center(), r.bbox())) != null) {
                throw new IllegalStateException("Duplicate region " + r.code() + " in " + resource);
            }
        }
        if (map.size() != Region.values().length) {
            throw new IllegalStateException(
                    "Expected " + Region.values().length + " regions in " + resource + ", found " + map.size());
        }
        byRegion = map;
        all = List.copyOf(map.values());
    }

    public List<RegionMeta> all() {
        return all;
    }

    public RegionMeta of(Region r) {
        return byRegion.get(r);
    }
}
