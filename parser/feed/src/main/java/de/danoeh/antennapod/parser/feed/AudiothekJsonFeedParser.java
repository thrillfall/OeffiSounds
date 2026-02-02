package de.danoeh.antennapod.parser.feed;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.parser.feed.util.DateUtils;
import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils;

class AudiothekJsonFeedParser {

    private static final int IMAGE_WIDTH = 300;

    private AudiothekJsonFeedParser() {
    }

    static FeedHandlerResult parse(Feed feed) throws IOException, JSONException, UnsupportedFeedtypeException {
        File file = new File(feed.getLocalFileUrl());
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);

        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new UnsupportedFeedtypeException("json", "Missing data");
        }

        JSONObject programSet = data.optJSONObject("programSet");
        JSONObject editorialCollection = data.optJSONObject("editorialCollection");
        if (programSet == null && editorialCollection == null) {
            throw new UnsupportedFeedtypeException("json", "Missing data.programSet");
        }

        feed.setType(Feed.TYPE_RSS2);

        JSONObject feedRoot = programSet != null ? programSet : editorialCollection;
        feed.setTitle(feedRoot.optString("title", feed.getTitle()));
        feed.setDescription(feedRoot.optString("synopsis", null));
        feed.setLink(feedRoot.optString("sharingUrl", null));

        JSONObject image = feedRoot.optJSONObject("image");
        if (image != null) {
            String imageUrl = image.optString("url1X1", null);
            if (imageUrl == null) {
                imageUrl = image.optString("url", null);
            }
            if (imageUrl != null) {
                feed.setImageUrl(imageUrl.replace("{width}", String.valueOf(IMAGE_WIDTH)));
            }
        }

        JSONArray nodes = null;
        if (programSet != null) {
            JSONObject itemsObj = programSet.optJSONObject("items");
            nodes = itemsObj != null ? itemsObj.optJSONArray("nodes") : null;
        } else {
            JSONObject itemsObj = editorialCollection.optJSONObject("items");
            nodes = itemsObj != null ? itemsObj.optJSONArray("nodes") : null;
        }

        List<FeedItem> items = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node == null) {
                    continue;
                }

                FeedItem item = new FeedItem();
                item.setFeed(feed);

                item.setTitle(node.optString("title", null));
                item.setDescriptionIfLonger(node.optString("synopsis", null));
                item.setLink(node.optString("sharingUrl", null));

                String publicationDate = node.optString("publicationStartDateAndTime", null);
                if (!TextUtils.isEmpty(publicationDate)) {
                    item.setPubDate(DateUtils.parseOrNullIfFuture(publicationDate));
                }

                String identifier = node.optString("publicationId", null);
                if (TextUtils.isEmpty(identifier)) {
                    identifier = node.optString("assetId", null);
                }
                if (TextUtils.isEmpty(identifier)) {
                    identifier = node.optString("id", null);
                }
                if (!TextUtils.isEmpty(identifier)) {
                    item.setItemIdentifier(identifier);
                }

                JSONObject itemImage = node.optJSONObject("image");
                if (itemImage != null) {
                    String itemImageUrl = itemImage.optString("url1X1", null);
                    if (itemImageUrl == null) {
                        itemImageUrl = itemImage.optString("url", null);
                    }
                    if (itemImageUrl != null) {
                        item.setImageUrl(itemImageUrl.replace("{width}", String.valueOf(IMAGE_WIDTH)));
                    }
                }

                FeedMedia media = createMedia(item, node);
                if (media != null) {
                    item.setMedia(media);
                }

                items.add(item);
            }
        }

        feed.setItems(items);
        return new FeedHandlerResult(feed, Collections.emptyMap(), null);
    }

    private static FeedMedia createMedia(FeedItem item, JSONObject node) {
        JSONArray audios = node.optJSONArray("audios");
        if (audios == null || audios.length() == 0) {
            return null;
        }

        JSONObject audio = audios.optJSONObject(0);
        if (audio == null) {
            return null;
        }

        String url = audio.optString("downloadUrl", null);
        if (TextUtils.isEmpty(url)) {
            url = audio.optString("url", null);
        }
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        String mimeType = MimeTypeUtils.getMimeType(null, url);
        if (!MimeTypeUtils.isMediaFile(mimeType)) {
            mimeType = "audio/*";
        }

        FeedMedia media = new FeedMedia(item, url, 0, mimeType);

        int durationSeconds = node.optInt("duration", 0);
        if (durationSeconds > 0) {
            long durationMs = durationSeconds * 1000L;
            if (durationMs <= Integer.MAX_VALUE) {
                media.setDuration((int) durationMs);
            }
        }

        return media;
    }
}
