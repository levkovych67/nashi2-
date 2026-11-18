package com.nashi.musician;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.LifeDates;
import com.nashi.artist.Link;
import com.nashi.artist.Origin;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageUrls;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** {@link Artist} to the public DTOs. Null collections of old documents become empty lists. */
@Component
public class MusicianMapper {

    private final FileStorage storage;

    public MusicianMapper(FileStorage storage) {
        this.storage = storage;
    }

    public MusicianListItem toListItem(Artist a) {
        List<ImageAsset> images = orEmpty(a.getImages());
        Origin origin = a.getOrigin();
        return new MusicianListItem(a.getId(), a.getSlug(), a.getName(), a.getKind(),
                images.isEmpty() ? null : thumbOf(images.get(0)),
                origin == null ? null : origin.placeName(), origin == null ? null : origin.region(),
                a.getLikesCount(), hasAudio(a));
    }

    public MusicianDetails toDetails(Artist a) {
        return new MusicianDetails(a.getId(), a.getSlug(), a.getName(), a.getKind(), a.getTagline(), a.getSummary(),
                a.getDescription(), a.getTextSource(),
                orEmpty(a.getImages()).stream().map(this::toImage).toList(),
                toOrigin(a.getOrigin(), a.getPin()),
                a.getDates() == null ? new LifeDates(null, null, null, null) : a.getDates(),
                orEmpty(a.getGenres()),
                new MusicianDetails.Materials(
                        orEmpty(a.getAudio()).stream().map(MusicianMapper::toClip).toList(),
                        orEmpty(a.getWorks()), orEmpty(a.getVideos())),
                orEmpty(a.getLinks()).stream()
                        .sorted(Comparator.comparing((Link l) -> l.platform().group()))
                        .map(l -> new MusicianDetails.LinkDto(l.platform(), l.platform().group(), l.url()))
                        .toList(),
                a.getYoutubeChannelUrl(), a.getLikesCount(), hasAudio(a), a.getUpdatedAt());
    }

    private MusicianDetails.Image toImage(ImageAsset i) {
        String url = i.key() != null ? storage.publicUrl(i.key()) : i.url();
        String thumb = thumbOf(i);
        return new MusicianDetails.Image(url, thumb != null ? thumb : url, i.alt(), i.credit());
    }

    private String thumbOf(ImageAsset i) {
        return ImageUrls.thumb(i, storage);
    }

    private static MusicianDetails.OriginDto toOrigin(Origin o, GeoJsonPoint pin) {
        if (o == null || pin == null) {
            return null;
        }
        return new MusicianDetails.OriginDto(o.placeId(), o.placeName(), o.region(), o.region().uaName(), o.basis(),
                o.precision(), pin.getY(), pin.getX());
    }

    private static MusicianDetails.ClipSummary toClip(AudioClip c) {
        return new MusicianDetails.ClipSummary(c.clipId(), c.title(), c.source(), c.durationSec(), c.externalUrl());
    }

    private static boolean hasAudio(Artist a) {
        return a.getAudio() != null && !a.getAudio().isEmpty();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
