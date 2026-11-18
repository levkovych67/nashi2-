package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.LifeDates;
import com.nashi.artist.Link;
import com.nashi.artist.MusicianKind;
import com.nashi.config.NashiProperties;
import com.nashi.telegram.Html;
import com.nashi.telegram.TelegramGateway;
import com.nashi.wiki.CandidateGroup;
import com.nashi.wiki.CandidateSourceFactory;
import com.nashi.wiki.MediaWikiParser;
import com.nashi.wiki.SummaryCleaner;
import com.nashi.wiki.WikiArtistData;
import com.nashi.wiki.WikiCandidate;
import com.nashi.wiki.WikiHttp;
import com.nashi.wiki.WikiMusicianMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fills an approved musician from the Wikipedia article the submitter named (D-43): the article's Wikidata item
 * (MediaWiki {@code pageprops}) goes through the import's collector, then {@code wikidataId}, {@code wikiTitle},
 * {@code tagline}, {@code summary}, {@code description}, {@code textSource}, {@code dates}, {@code genres} and
 * {@code works} take what Wikipedia has, missing {@code links} and the YouTube channel are added, and the Commons
 * portrait goes after the submitter's photos. The name, origin and pin the moderators approved never change. When the
 * item already belongs to another musician nothing changes and the group is told.
 * <p>
 * Only uk.wikipedia.org and en.wikipedia.org articles; every request goes through {@link WikiHttp}. The update is a
 * targeted {@code $set} guarded by {@code version} (so likes counted meanwhile are not overwritten); when the artist
 * changed meanwhile it is applied again to the fresh document.
 */
@Service
public class WikipediaEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaEnrichmentService.class);
    static final Set<String> HOSTS = Set.of("uk.wikipedia.org", "en.wikipedia.org");
    static final int MAX_ATTEMPTS = 3;
    private static final String ARTICLE_PATH = "/wiki/";

    private final ArtistRepository artists;
    private final MongoTemplate mongo;
    private final CandidateSourceFactory sources;
    private final WikiHttp http;
    private final TelegramGateway gateway;
    private final ApplicationEventPublisher events;
    private final String siteBaseUrl;
    private final Clock clock;
    private final boolean enabled;

    /** @param enabled {@code nashi.wiki.enrichment.enabled}; false in tests, which must not reach Wikipedia */
    public WikipediaEnrichmentService(ArtistRepository artists, MongoTemplate mongo, CandidateSourceFactory sources,
                                      WikiHttp http, TelegramGateway gateway, ApplicationEventPublisher events,
                                      NashiProperties properties, Clock clock,
                                      @Value("${nashi.wiki.enrichment.enabled:true}") boolean enabled) {
        this.artists = artists;
        this.mongo = mongo;
        this.sources = sources;
        this.http = http;
        this.gateway = gateway;
        this.events = events;
        this.siteBaseUrl = properties.siteBaseUrl();
        this.clock = clock;
        this.enabled = enabled;
    }

    @Async("jobsExecutor")
    @EventListener
    public void onRequested(EnrichmentRequested event) {
        if (!enabled) {
            log.debug("Wikipedia enrichment is off; artist {} stays as approved", event.artistId());
            return;
        }
        enrich(event.artistId(), event.wikipediaUrl());
    }

    /** @return whether the artist was changed */
    public boolean enrich(String artistId, String wikipediaUrl) {
        Optional<Article> article = Article.of(wikipediaUrl);
        if (article.isEmpty()) {
            log.warn("Artist {}: {} is not an article of uk or en Wikipedia; not enriched", artistId, wikipediaUrl);
            return false;
        }
        String qid = MediaWikiParser.parseWikibaseItem(http.get(article.get().pagepropsUrl()));
        if (qid == null) {
            log.info("Artist {}: {} has no Wikidata item; not enriched", artistId, wikipediaUrl);
            return false;
        }
        Artist artist = artists.findById(artistId).orElse(null);
        if (artist == null) {
            log.warn("Artist {} is gone; not enriched", artistId);
            return false;
        }
        Optional<Artist> owner = otherOwner(qid, artistId);
        if (owner.isPresent()) {
            announceDuplicate(article.get(), artist, owner.get());
            return false;
        }
        CandidateGroup group = artist.getKind() == MusicianKind.BAND ? CandidateGroup.ENSEMBLE : CandidateGroup.MUSIC;
        List<WikiArtistData> found = sources.create()
                .fetchDetails(List.of(new WikiCandidate(qid, article.get().title(), group)));
        if (found.isEmpty()) {
            log.info("Artist {}: Wikidata {} gave nothing to import (linked to Russia, or no label)", artistId, qid);
            return false;
        }
        return apply(artist, found.get(0), article.get());
    }

    private boolean apply(Artist artist, WikiArtistData data, Article article) {
        for (int attempt = 1; ; attempt++) {
            Update update = changes(artist, data);
            if (update == null) {
                log.info("Artist {}: Wikipedia has nothing new", artist.getId());
                return false;
            }
            update.set("updatedAt", clock.instant()).inc("version", 1);
            Query current = Query.query(Criteria.where("_id").is(artist.getId()).and("version").is(artist.getVersion()));
            try {
                if (mongo.updateFirst(current, update, Artist.class).getMatchedCount() > 0) {
                    log.info("Artist {} enriched from Wikidata {}", artist.getId(), data.qid());
                    events.publishEvent(new ArtistChangedEvent(artist.getId()));
                    return true;
                }
            } catch (DuplicateKeyException e) {
                // the import saved this Wikidata item as an artist of its own meanwhile
                Artist self = artist;
                otherOwner(data.qid(), artist.getId()).ifPresent(owner -> announceDuplicate(article, self, owner));
                return false;
            }
            Artist fresh = artists.findById(artist.getId()).orElse(null);
            if (fresh == null || attempt == MAX_ATTEMPTS) {
                log.warn("Artist {} kept changing or is gone; not enriched", artist.getId());
                return false;
            }
            artist = fresh;
        }
    }

    /** The {@code $set} of what Wikipedia adds to the artist; null when it adds nothing. */
    private static Update changes(Artist artist, WikiArtistData data) {
        Update update = new Update();
        setIfChanged(update, "wikidataId", artist.getWikidataId(), data.qid());
        setIfChanged(update, "wikiTitle", artist.getWikiTitle(), data.wikiTitle());
        setIfChanged(update, "tagline", artist.getTagline(), data.tagline());
        String description = SummaryCleaner.description(data.biography());
        setIfChanged(update, "summary", artist.getSummary(), SummaryCleaner.summary(data.biography()));
        setIfChanged(update, "description", artist.getDescription(), description);
        if (description != null) {
            setIfChanged(update, "textSource", artist.getTextSource(), WikiMusicianMapper.textSource(data.wikiTitle()));
        }
        setIfChanged(update, "dates", artist.getDates(), filled(artist.getDates(), WikiMusicianMapper.dates(data)));
        setIfChanged(update, "genres", artist.getGenres(), nonEmpty(data.genres()));
        setIfChanged(update, "works", artist.getWorks(), nonEmpty(WikiMusicianMapper.works(data)));
        setIfChanged(update, "links", artist.getLinks(), withMissingLinks(artist.getLinks(), WikiMusicianMapper.links(data)));
        if (artist.getYoutubeChannelUrl() == null) {
            setIfChanged(update, "youtubeChannelUrl", null, data.youtubeChannelUrl());
        }
        setIfChanged(update, "images", artist.getImages(),
                withPortrait(artist.getImages(), WikiMusicianMapper.image(data).orElse(null)));
        return update.getUpdateObject().isEmpty() ? null : update;
    }

    /** Sets {@code wiki} when there is one and it differs from what the artist has. */
    private static void setIfChanged(Update update, String field, Object current, Object wiki) {
        if (wiki != null && !wiki.equals(current)) {
            update.set(field, wiki);
        }
    }

    /** The artist's dates with the missing ones from Wikipedia; null when neither has any. */
    private static LifeDates filled(LifeDates own, LifeDates wiki) {
        LifeDates base = own == null ? new LifeDates(null, null, null, null) : own;
        LifeDates merged = new LifeDates(first(base.birth(), wiki.birth()), first(base.death(), wiki.death()),
                first(base.activeFrom(), wiki.activeFrom()), first(base.activeTo(), wiki.activeTo()));
        return merged.equals(new LifeDates(null, null, null, null)) ? null : merged;
    }

    private static String first(String own, String wiki) {
        return own != null ? own : wiki;
    }

    private static <T> List<T> nonEmpty(List<T> list) {
        return list == null || list.isEmpty() ? null : List.copyOf(list);
    }

    /** The artist's links, then each Wikipedia link whose page is not among them yet; null when none is new. */
    private static List<Link> withMissingLinks(List<Link> own, List<Link> wiki) {
        List<Link> links = new ArrayList<>(own == null ? List.of() : own);
        Set<String> pages = new HashSet<>();
        links.forEach(link -> pages.add(page(link.url())));
        boolean added = false;
        for (Link link : wiki) {
            if (pages.add(page(link.url()))) {
                links.add(link);
                added = true;
            }
        }
        return added ? links : null;
    }

    /** A URL compared without scheme, "www.", query, fragment and trailing slash, ignoring case. */
    private static String page(String url) {
        String page = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "").replaceFirst("^www\\.", "");
        page = page.replaceFirst("[?#].*$", "");
        return page.endsWith("/") ? page.substring(0, page.length() - 1) : page;
    }

    /** The submitter's photos first, then the Commons portrait; null when there is none or it is already there. */
    private static List<ImageAsset> withPortrait(List<ImageAsset> own, ImageAsset portrait) {
        List<ImageAsset> images = new ArrayList<>(own == null ? List.of() : own);
        if (portrait == null || images.stream().anyMatch(image -> Objects.equals(image.url(), portrait.url()))) {
            return null;
        }
        images.add(portrait);
        return images;
    }

    private Optional<Artist> otherOwner(String qid, String artistId) {
        return artists.findByWikidataId(qid).filter(owner -> !owner.getId().equals(artistId));
    }

    private void announceDuplicate(Article article, Artist artist, Artist owner) {
        String path = "/m/" + owner.getSlug();
        log.info("Artist {}: Wikidata item of {} already belongs to {}", artist.getId(), article.title(), owner.getId());
        try {
            gateway.sendText("⚠️ Вікіпедія «" + Html.escape(article.title()) + "» для «" + Html.escape(artist.getName())
                    + "»: цей запис уже є як <a href=\"" + Html.escape(siteBaseUrl + path) + "\">"
                    + Html.escape(path) + "</a>");
        } catch (RuntimeException e) {
            log.warn("Could not tell the group about the duplicate of artist {}: {}", artist.getId(), e.getMessage());
        }
    }

    /** An article of uk or en Wikipedia: the host and the title (spaces, not underscores). */
    record Article(String host, String title) {

        static Optional<Article> of(String url) {
            if (url == null) {
                return Optional.empty();
            }
            try {
                URI uri = URI.create(url.trim());
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                String path = uri.getRawPath();
                if (!(scheme.equals("https") || scheme.equals("http")) || !HOSTS.contains(host) || path == null
                        || !path.startsWith(ARTICLE_PATH)) {
                    return Optional.empty();
                }
                String title = URLDecoder.decode(path.substring(ARTICLE_PATH.length()).replace("+", "%2B"),
                        StandardCharsets.UTF_8).replace('_', ' ').trim();
                return title.isEmpty() ? Optional.empty() : Optional.of(new Article(host, title));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        String pagepropsUrl() {
            return "https://" + host + "/w/api.php?action=query&format=json&formatversion=2&prop=pageprops"
                    + "&ppprop=wikibase_item&redirects=1&titles="
                    + URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }
}
