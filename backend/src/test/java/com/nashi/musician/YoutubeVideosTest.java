package com.nashi.musician;

import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.Video;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeVideosTest {

    private static Link yt(String url) {
        return new Link(LinkPlatform.YOUTUBE, url);
    }

    @Test
    void extractsWatchShortLinkShortsAndEmbed() {
        List<Link> links = List.of(
                yt("https://www.youtube.com/watch?v=aaaaaaaaaaa"),
                yt("https://m.youtube.com/watch?t=5&v=bbbbbbbbbbb&feature=share"),
                yt("https://youtu.be/ccccccccccc?si=xyz"),
                yt("https://www.youtube.com/shorts/ddddddddddd"),
                yt("https://www.youtube.com/embed/eeeeeeeeeee"));

        assertThat(YoutubeVideos.fromLinks(links, "Artist")).containsExactly(
                new Video("aaaaaaaaaaa", "Artist"),
                new Video("bbbbbbbbbbb", "Artist"),
                new Video("ccccccccccc", "Artist"),
                new Video("ddddddddddd", "Artist"),
                new Video("eeeeeeeeeee", "Artist"));
    }

    @Test
    void ignoresChannelsHandlesPlaylistsAndYoutubeMusic() {
        List<Link> links = List.of(
                yt("https://www.youtube.com/@artist"),
                yt("https://www.youtube.com/user/artist"),
                yt("https://www.youtube.com/c/artist"),
                yt("https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx"),
                yt("https://www.youtube.com/playlist?list=PLabcdefghijk"),
                new Link(LinkPlatform.YOUTUBE_MUSIC, "https://music.youtube.com/watch?v=fffffffffff"));

        assertThat(YoutubeVideos.fromLinks(links, "Artist")).isEmpty();
    }

    @Test
    void rejectsLookalikeHosts() {
        List<Link> links = List.of(
                yt("https://youtube.com.evil.example/watch?v=ggggggggggg"),
                yt("https://evil.example/youtube.com/watch?v=hhhhhhhhhhh"),
                yt("https://notyoutube.com/watch?v=iiiiiiiiiii"));

        assertThat(YoutubeVideos.fromLinks(links, "Artist")).isEmpty();
    }

    @Test
    void dedupesAndCapsAtSix() {
        List<Link> links = new ArrayList<>();
        links.add(yt("https://youtu.be/dupdupdupdu"));
        links.add(yt("https://youtu.be/dupdupdupdu"));
        IntStream.range(0, 8)
                .mapToObj(i -> yt("https://youtu.be/" + String.format("%011d", i)))
                .forEach(links::add);

        List<Video> videos = YoutubeVideos.fromLinks(links, "Artist");

        assertThat(videos).hasSize(6);
        assertThat(videos.get(0).youtubeId()).isEqualTo("dupdupdupdu");
        assertThat(videos).extracting(Video::youtubeId).doesNotHaveDuplicates();
    }
}
