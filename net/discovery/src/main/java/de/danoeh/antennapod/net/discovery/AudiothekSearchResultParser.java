package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class AudiothekSearchResultParser {

    private static final int IMAGE_WIDTH = 128;

    private AudiothekSearchResultParser() {
    }

    static List<PodcastSearchResult> parseProgramSets(JSONObject root, String rssUrlTemplate) {
        List<PodcastSearchResult> results = new ArrayList<>();

        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return results;
        }
        JSONObject search = data.optJSONObject("search");
        if (search == null) {
            return results;
        }
        JSONObject programSets = search.optJSONObject("programSets");
        if (programSets == null) {
            return results;
        }
        JSONArray nodes = programSets.optJSONArray("nodes");
        if (nodes == null) {
            return results;
        }

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) {
                continue;
            }

            String programSetId = node.optString("rowId", null);
            if (programSetId == null || programSetId.isEmpty()) {
                programSetId = node.optString("id", null);
            }
            if (programSetId == null || programSetId.isEmpty()) {
                continue;
            }

            String title = node.optString("title", "");
            String author = null;
            JSONObject publicationService = node.optJSONObject("publicationService");
            if (publicationService != null) {
                author = publicationService.optString("organizationName", null);
            }

            String imageUrl = getImageUrl(node.optJSONObject("image"));
            String feedUrl = String.format(rssUrlTemplate, programSetId);

            PodcastSearchResult result = PodcastSearchResult.fromExternalSource(title, imageUrl, feedUrl, author);
            if (result.feedUrl != null) {
                results.add(result);
            }
        }

        return results;
    }

    @Nullable
    private static String getImageUrl(@Nullable JSONObject image) {
        if (image == null) {
            return null;
        }
        String imageUrl = image.optString("url1X1", null);
        if (imageUrl == null) {
            imageUrl = image.optString("url", null);
        }
        if (imageUrl == null) {
            return null;
        }
        return imageUrl.replace("{width}", String.valueOf(IMAGE_WIDTH));
    }
}
