package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DlfPodcastSearcher implements PodcastSearcher {
    private static final String DLF_PODCASTS_PAGE = "https://www.deutschlandfunk.de/podcasts";
    private static final String API_URL = "https://dlf-audiothek-appapi.deutschlandradio.de/broadcasts";
    private static final String KULTUR_DOMAIN = "www.deutschlandfunkkultur.de";

    private static final Pattern DATA_JSON_PATTERN =
            Pattern.compile("data-json=\"([^\"]+)\"");

    public static String feedUrlForBroadcast(String stationId, String sophoraId) {
        if ("4".equals(stationId)) {
            return "https://www.deutschlandfunk.de/" + sophoraId + ".xml";
        } else if ("3".equals(stationId)) {
            return "https://" + KULTUR_DOMAIN + "/" + sophoraId + ".xml";
        }
        return null;
    }

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            List<PodcastSearchResult> podcasts = new ArrayList<>();
            String queryLower = query.toLowerCase(Locale.ROOT);
            Set<String> seenFeedUrls = new HashSet<>();

            try {
                // 1) Scrape DLF website for the complete podcast list (includes series with feeds)
                Map<String, String> titleToImage = loadApiImages(client);
                Request pageReq = new Request.Builder().url(DLF_PODCASTS_PAGE).build();
                try (Response resp = client.newCall(pageReq).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String html = resp.body().string();
                        Matcher m = DATA_JSON_PATTERN.matcher(html);
                        while (m.find()) {
                            String encoded = m.group(1);
                            String decoded = encoded.replace("&quot;", "\"")
                                    .replace("&amp;", "&")
                                    .replace("&lt;", "<")
                                    .replace("&gt;", ">");
                            try {
                                JSONObject data = new JSONObject(decoded);
                                JSONObject val = data.optJSONObject("value");
                                if (val == null) {
                                    continue;
                                }
                                String feedUrl = val.optString("pathPodcast", "");
                                if (feedUrl.isEmpty() || !feedUrl.endsWith(".xml")
                                        || seenFeedUrls.contains(feedUrl)) {
                                    continue;
                                }
                                String title = val.optString("title", "");
                                String description = val.optString("description", "");
                                if (!title.toLowerCase(Locale.ROOT).contains(queryLower)
                                        && !description.toLowerCase(Locale.ROOT).contains(queryLower)) {
                                    continue;
                                }
                                seenFeedUrls.add(feedUrl);
                                String imageUrl = titleToImage.get(title);
                                podcasts.add(PodcastSearchResult.fromExternalSource(
                                        title, imageUrl, feedUrl, "Deutschlandfunk"));
                            } catch (JSONException ignored) {
                                // Skip entries that do not have the expected shape
                            }
                        }
                    }
                }

                // 2) Add Kultur podcasts from API (website only lists topic portals)
                Request apiReq = new Request.Builder().url(API_URL).build();
                try (Response resp = client.newCall(apiReq).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JSONArray broadcasts = new JSONArray(resp.body().string());
                        for (int i = 0; i < broadcasts.length(); i++) {
                            JSONObject b = broadcasts.getJSONObject(i);
                            if (!"podcast".equals(b.optString("broadcast_doc_type"))
                                    || !"3".equals(b.optString("station_id"))) {
                                continue;
                            }
                            String feedUrl = feedUrlForBroadcast("3", b.optString("sophora_id"));
                            if (feedUrl == null || seenFeedUrls.contains(feedUrl)) {
                                continue;
                            }
                            String title = b.optString("broadcast_title", "");
                            String description = b.optString("broadcast_description", "");
                            if (!title.toLowerCase(Locale.ROOT).contains(queryLower)
                                    && !description.toLowerCase(Locale.ROOT).contains(queryLower)) {
                                continue;
                            }
                            seenFeedUrls.add(feedUrl);
                            String imageUrl = b.optString("broadcast_image_logo", null);
                            podcasts.add(PodcastSearchResult.fromExternalSource(
                                    title, imageUrl, feedUrl, "Deutschlandfunk Kultur"));
                        }
                    }
                }

            } catch (IOException | JSONException e) {
                subscriber.onError(e);
            }
            subscriber.onSuccess(podcasts);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Load broadcast images from the API, keyed by title, so website-scraped
     * entries can be enriched with cover art.
     */
    private static Map<String, String> loadApiImages(OkHttpClient client) {
        Map<String, String> map = new HashMap<>();
        Request req = new Request.Builder().url(API_URL).build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                JSONArray broadcasts = new JSONArray(resp.body().string());
                for (int i = 0; i < broadcasts.length(); i++) {
                    JSONObject b = broadcasts.getJSONObject(i);
                    if (!"4".equals(b.optString("station_id"))) {
                        continue;
                    }
                    String title = b.optString("broadcast_title", "");
                    String img = b.optString("broadcast_image_logo", null);
                    if (!title.isEmpty() && img != null && !img.isEmpty()) {
                        map.put(title, img);
                    }
                }
            }
        } catch (IOException | JSONException ignored) {
            // Feed url lookup is optional, the search result stays usable without it
        }
        return map;
    }

    @Override
    public Single<String> lookupUrl(String url) {
        return Single.just(url);
    }

    @Override
    public boolean urlNeedsLookup(String url) {
        return false;
    }

    @Override
    public String getName() {
        return "Deutschlandfunk";
    }
}
