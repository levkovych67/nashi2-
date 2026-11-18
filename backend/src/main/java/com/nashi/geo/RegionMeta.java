package com.nashi.geo;

import java.util.Arrays;

/** Map metadata of a region: {@code center} = [lon, lat], {@code bbox} = [west, south, east, north]. */
public record RegionMeta(Region code, String name, double[] center, double[] bbox) {

    public RegionMeta {
        center = center.clone();
        bbox = bbox.clone();
    }

    @Override
    public double[] center() {
        return center.clone();
    }

    @Override
    public double[] bbox() {
        return bbox.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RegionMeta m && code == m.code && name.equals(m.name)
                && Arrays.equals(center, m.center) && Arrays.equals(bbox, m.bbox);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * code.hashCode() + name.hashCode()) + Arrays.hashCode(center)) + Arrays.hashCode(bbox);
    }

    @Override
    public String toString() {
        return "RegionMeta[code=" + code + ", name=" + name + ", center=" + Arrays.toString(center)
                + ", bbox=" + Arrays.toString(bbox) + "]";
    }
}
