package de.danoeh.antennapod.parser.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

public class AudiothekJsonFeedParserTest {

    @Test
    public void parseFeed_programSetJson_createsFeedAndItems() throws Exception {
        String json = "{"
                + "\"data\":{"
                + "\"programSet\":{"
                + "\"id\":\"8758656\","
                + "\"coreId\":\"urn:ard:show:44b142e0544f2807\","
                + "\"title\":\"Das Portal - ARD Science-Fiction-Hörspiele\","
                + "\"synopsis\":\"Synopsis\","
                + "\"sharingUrl\":\"https://www.ardaudiothek.de/sendung/foo/urn:ard:show:bar/\","
                + "\"image\":{\"url1X1\":\"https://img.example/{width}.jpg\"},"
                + "\"items\":{"
                + "\"nodes\":[{"
                + "\"publicationId\":\"urn:ard:publication:abc\","
                + "\"title\":\"Episode 1\","
                + "\"synopsis\":\"Episode synopsis\","
                + "\"publicationStartDateAndTime\":\"2026-01-22T00:01:39+01:00\","
                + "\"duration\":10,"
                + "\"sharingUrl\":\"https://www.ardaudiothek.de/episode/urn:ard:episode:abc/\","
                + "\"audios\":[{\"url\":\"https://example.com/audio.mp3\"}]"
                + "}]"
                + "}"
                + "}"
                + "}"
                + "}";

        File tmp = File.createTempFile("audiothek", ".json");
        Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);

        Feed feed = new Feed("https://api.ardaudiothek.de/programsets/8758656", null);
        feed.setLocalFileUrl(tmp.getAbsolutePath());

        FeedHandler handler = new FeedHandler();
        FeedHandlerResult result = handler.parseFeed(feed);

        assertNotNull(result);
        assertEquals("Das Portal - ARD Science-Fiction-Hörspiele", result.feed.getTitle());
        assertEquals("https://www.ardaudiothek.de/sendung/foo/urn:ard:show:bar/", result.feed.getLink());
        assertNotNull(result.feed.getImageUrl());

        assertNotNull(result.feed.getItems());
        assertTrue(result.feed.getItems().size() >= 1);

        FeedItem item = result.feed.getItems().get(0);
        assertEquals("Episode 1", item.getTitle());
        assertNotNull(item.getMedia());
        assertEquals("https://example.com/audio.mp3", item.getMedia().getDownloadUrl());

        // cleanup
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
    }
}
