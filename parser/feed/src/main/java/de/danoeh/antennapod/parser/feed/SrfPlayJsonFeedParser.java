package de.danoeh.antennapod.parser.feed;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.parser.feed.util.DateUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Parses SRF Play episodeComposition JSON into a Feed with episodes.
 * For each episode, resolves the audio URL via the mediaComposition endpoint.
 */
class SrfPlayJsonFeedParser {

    private static final String MEDIA_COMPOSITION_URL =
            "https://il.srgssr.ch/integrationlayer/2.0/mediaComposition/byUrn/%s?vector=APPPLAY";

    private SrfPlayJsonFeedParser() {
    }

    static boolean canParse(JSONObject root) {
        return root.has("episodeList") && root.has("show");
    }

    static FeedHandlerResult parse(Feed feed, OkHttpClient httpClient)
            throws IOException, JSONException, UnsupportedFeedtypeException {
        File file = new File(feed.getLocalFileUrl());
        String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);

        if (!canParse(root)) {
            throw new UnsupportedFeedtypeException("json", "Not an SRF episodeComposition");
        }

        feed.setType(Feed.TYPE_RSS2);

        JSONObject show = root.optJSONObject("show");
        if (show != null) {
            feed.setTitle(show.optString("title", feed.getTitle()));
            feed.setDescription(show.optString("lead", null));
            String imageUrl = show.optString("podcastImageUrl", null);
            if (TextUtils.isEmpty(imageUrl)) {
                imageUrl = show.optString("imageUrl", null);
            }
            if (!TextUtils.isEmpty(imageUrl)) {
                feed.setImageUrl(imageUrl);
            }
        }

        JSONArray episodeList = root.optJSONArray("episodeList");
        List<FeedItem> items = new ArrayList<>();
        if (episodeList != null) {
            for (int i = 0; i < episodeList.length(); i++) {
                JSONObject episode = episodeList.optJSONObject(i);
                if (episode == null) {
                    continue;
                }

                FeedItem item = new FeedItem();
                item.setFeed(feed);
                item.setTitle(episode.optString("title", null));
                item.setDescriptionIfLonger(episode.optString("lead", null));

                String pubDate = episode.optString("publishedDate", null);
                if (!TextUtils.isEmpty(pubDate)) {
                    item.setPubDate(DateUtils.parseOrNullIfFuture(pubDate));
                }

                String episodeId = episode.optString("id", null);
                if (!TextUtils.isEmpty(episodeId)) {
                    item.setItemIdentifier(episodeId);
                }

                String itemImageUrl = episode.optString("imageUrl", null);
                if (!TextUtils.isEmpty(itemImageUrl)) {
                    item.setImageUrl(itemImageUrl);
                }

                FeedMedia media = resolveMedia(item, episode, httpClient);
                if (media != null) {
                    item.setMedia(media);
                }

                items.add(item);
            }
        }

        feed.setItems(items);
        return new FeedHandlerResult(feed, Collections.emptyMap(), null);
    }

    private static FeedMedia resolveMedia(FeedItem item, JSONObject episode, OkHttpClient client) {
        JSONArray mediaList = episode.optJSONArray("mediaList");
        if (mediaList == null || mediaList.length() == 0) {
            return null;
        }

        JSONObject firstMedia = mediaList.optJSONObject(0);
        if (firstMedia == null) {
            return null;
        }

        String urn = firstMedia.optString("urn", null);
        long durationMs = firstMedia.optLong("duration", 0);

        if (TextUtils.isEmpty(urn)) {
            return null;
        }

        String audioUrl = fetchAudioUrl(client, urn);
        if (TextUtils.isEmpty(audioUrl)) {
            return null;
        }

        FeedMedia media = new FeedMedia(item, audioUrl, 0, "audio/mpeg");
        if (durationMs > 0 && durationMs <= Integer.MAX_VALUE) {
            media.setDuration((int) durationMs);
        }
        return media;
    }

    private static String fetchAudioUrl(OkHttpClient client, String urn) {
        String url = String.format(MEDIA_COMPOSITION_URL, urn);
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JSONObject root = new JSONObject(response.body().string());
            JSONArray chapterList = root.optJSONArray("chapterList");
            if (chapterList == null || chapterList.length() == 0) {
                return null;
            }
            JSONObject chapter = chapterList.optJSONObject(0);
            if (chapter == null) {
                return null;
            }
            JSONArray resourceList = chapter.optJSONArray("resourceList");
            if (resourceList == null || resourceList.length() == 0) {
                return null;
            }
            // Prefer PROGRESSIVE MP3
            for (int i = 0; i < resourceList.length(); i++) {
                JSONObject resource = resourceList.optJSONObject(i);
                if (resource != null && "PROGRESSIVE".equals(resource.optString("streaming", ""))) {
                    return resource.optString("url", null);
                }
            }
            // Fallback to first resource
            JSONObject firstResource = resourceList.optJSONObject(0);
            return firstResource != null ? firstResource.optString("url", null) : null;
        } catch (IOException | JSONException e) {
            return null;
        }
    }
}
