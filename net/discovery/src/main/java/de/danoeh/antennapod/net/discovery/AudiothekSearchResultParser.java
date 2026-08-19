package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class AudiothekSearchResultParser {

    private static final int IMAGE_WIDTH = 128;
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";

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

    static List<PodcastSearchResult> parseHomescreenCharts(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject embedded = root.optJSONObject("_embedded");
        if (embedded == null) {
            return new ArrayList<>();
        }
        JSONObject mostPlayed = embedded.optJSONObject("mt:mostPlayed");
        if (mostPlayed == null) {
            return new ArrayList<>();
        }
        JSONObject mostPlayedEmbedded = mostPlayed.optJSONObject("_embedded");
        if (mostPlayedEmbedded == null) {
            return new ArrayList<>();
        }
        Object itemsObj = mostPlayedEmbedded.opt("mt:items");

        List<PodcastSearchResult> results = new ArrayList<>();
        java.util.Set<String> seenFeedUrls = new java.util.LinkedHashSet<>();
        JSONArray itemsArray;
        if (itemsObj instanceof JSONObject) {
            itemsArray = new JSONArray().put(itemsObj);
        } else if (itemsObj instanceof JSONArray) {
            itemsArray = (JSONArray) itemsObj;
        } else {
            return results;
        }

        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject item = itemsArray.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject itemEmbedded = item.optJSONObject("_embedded");
            if (itemEmbedded == null) {
                continue;
            }
            JSONObject programSet = itemEmbedded.optJSONObject("mt:programSet");
            if (programSet == null) {
                continue;
            }

            JSONObject links = programSet.optJSONObject("_links");
            JSONObject self = links != null ? links.optJSONObject("self") : null;
            String href = self != null ? self.optString("href", null) : null;
            if (href == null) {
                continue;
            }
            href = href.replace("{?order,offset,limit}", "");
            String feedUrl = normalizeFeedUrl(href.startsWith("http") ? href : API_BASE_URL + href);

            if (!seenFeedUrls.add(feedUrl)) {
                continue; // skip duplicate program sets
            }

            JSONObject squareImage = links != null ? links.optJSONObject("mt:squareImage") : null;
            if (squareImage == null) {
                squareImage = links != null ? links.optJSONObject("mt:image") : null;
            }
            String imageUrl = squareImage != null ? squareImage.optString("href", null) : null;
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{width}", "400");
                imageUrl = imageUrl.replace("{ratio}", "1x1");
            }

            String title = programSet.optString("title", "");
            results.add(PodcastSearchResult.fromExternalSource(title, imageUrl, feedUrl, null));
        }
        return results;
    }

    private static String normalizeFeedUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replace("://api.ardaudiothek.de./", "://api.ardaudiothek.de/");
    }
}
