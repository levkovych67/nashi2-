package com.nashi.submission;

import com.nashi.api.FieldViolation;
import com.nashi.artist.LinkClassifier;
import com.nashi.artist.LinkGroup;
import com.nashi.artist.MusicianKind;
import com.nashi.config.NashiProperties;
import com.nashi.place.PlaceStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * All validation of a submission ({@code docs/02-contracts.md} 2.10), collected into one list: the Jakarta rules of
 * {@link SubmissionRequest} (mapped to Ukrainian messages), then the rules across fields, places and files. Field
 * names are the contract's ({@code summary}, {@code links[0]}, {@code images[2]}). Called by hand so the rate limit
 * runs before any validation.
 */
@Component
public class SubmissionValidator {

    private static final Set<String> WIKIPEDIA_HOSTS = Set.of("uk.wikipedia.org", "en.wikipedia.org");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "youtu.be", "www.youtu.be");
    private static final long MB = 1024L * 1024L;

    private final PlaceStore places;
    private final Validator beanValidator;
    private final NashiProperties.SubmissionProps limits;

    public SubmissionValidator(PlaceStore places, Validator beanValidator, NashiProperties properties) {
        this.places = places;
        this.beanValidator = beanValidator;
        this.limits = properties.submission();
    }

    public List<FieldViolation> validate(SubmissionRequest r, List<MultipartFile> images) {
        List<FieldViolation> out = new ArrayList<>();
        beanValidator.validate(r).stream().map(SubmissionValidator::toViolation).distinct().forEach(out::add);
        validatePlaces(r, out);
        validateLinks(r, out);
        validateImages(images, out);
        return out;
    }

    private void validatePlaces(SubmissionRequest r, List<FieldViolation> out) {
        // an id over the length cap already has its violation; it is not looked up
        if (notBlank(r.originPlaceId()) && withinCap(r.originPlaceId()) && places.find(r.originPlaceId()).isEmpty()) {
            out.add(new FieldViolation("originPlaceId", "Невідоме місце"));
        }
        if (notBlank(r.birthPlaceId()) && withinCap(r.birthPlaceId())) {
            if (r.kind() != null && r.kind() != MusicianKind.PERSON) {
                out.add(new FieldViolation("birthPlaceId", "Місце народження лише для окремого виконавця"));
            } else if (places.find(r.birthPlaceId()).isEmpty()) {
                out.add(new FieldViolation("birthPlaceId", "Невідоме місце"));
            }
        }
    }

    private void validateLinks(SubmissionRequest r, List<FieldViolation> out) {
        boolean listedLinkInvalid = false;
        boolean hasMusicOrVideo = false;
        if (r.links() != null) {
            for (int i = 0; i < r.links().size(); i++) {
                String url = r.links().get(i);
                if (url == null) {
                    continue;
                }
                if (hostOf(url) == null) {
                    out.add(new FieldViolation("links[" + i + "]", "Потрібне повне посилання http(s)"));
                    listedLinkInvalid = true;
                } else {
                    hasMusicOrVideo |= isMusicOrVideo(url);
                }
            }
        }
        if (notBlank(r.youtubeChannelUrl())) {
            String host = hostOf(r.youtubeChannelUrl().trim());
            if (host == null || !YOUTUBE_HOSTS.contains(host)) {
                out.add(new FieldViolation("youtubeChannelUrl", "Потрібне посилання на канал YouTube"));
            } else {
                hasMusicOrVideo = true;
            }
        }
        if (notBlank(r.wikipediaUrl())) {
            String host = hostOf(r.wikipediaUrl().trim());
            if (host == null || !WIKIPEDIA_HOSTS.contains(host)) {
                out.add(new FieldViolation("wikipediaUrl", "Потрібне посилання на uk.wikipedia.org або en.wikipedia.org"));
            }
        }
        if (!hasMusicOrVideo && !listedLinkInvalid && r.links() != null && !r.links().isEmpty()) {
            out.add(new FieldViolation("links", "Додайте посилання на музику або відео (Spotify, YouTube тощо)"));
        }
    }

    private void validateImages(List<MultipartFile> images, List<FieldViolation> out) {
        int count = images == null ? 0 : images.size();
        if (count < 1 || count > limits.maxImages()) {
            out.add(new FieldViolation("images", "Додайте від 1 до " + limits.maxImages() + " фото"));
            return;
        }
        for (int i = 0; i < count; i++) {
            MultipartFile file = images.get(i);
            if (file.isEmpty()) {
                out.add(new FieldViolation("images[" + i + "]", "Порожній файл"));
            } else if (file.getSize() > limits.maxImageMb() * MB) {
                out.add(new FieldViolation("images[" + i + "]", "Файл більший за " + limits.maxImageMb() + " МБ"));
            }
        }
    }

    private static boolean isMusicOrVideo(String url) {
        LinkGroup group = LinkClassifier.classify(url.trim()).group();
        return group == LinkGroup.MUSIC || group == LinkGroup.VIDEO;
    }

    /** The lower-case host of an absolute {@code http(s)} URL, or {@code null} for anything else. */
    private static String hostOf(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private static boolean withinCap(String placeId) {
        return placeId.length() <= SubmissionRequest.MAX_PLACE_ID;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static FieldViolation toViolation(ConstraintViolation<?> v) {
        String field = v.getPropertyPath().toString().replace(".<list element>", "");
        var annotation = v.getConstraintDescriptor().getAnnotation();
        String message;
        if (annotation instanceof Size size) {
            Object value = v.getInvalidValue();
            int length = value instanceof Collection<?> c ? c.size() : value instanceof CharSequence s ? s.length() : 0;
            String unit = value instanceof Collection<?> ? "елементів" : "символів";
            message = length < size.min() ? "Мінімум " + size.min() + " " + unit
                    : "Максимум " + size.max() + " " + unit;
        } else if (annotation instanceof AssertTrue) {
            message = "Потрібна згода";
        } else if (annotation instanceof NotBlank || annotation instanceof NotNull) {
            message = field.equals("consent") ? "Потрібна згода" : "Обов'язкове поле";
        } else {
            message = "Некоректне значення";
        }
        return new FieldViolation(field, message);
    }
}
