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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class BBCSoundsPodcastSearcher implements PodcastSearcher {
    private static final String API_URL = "https://rms.api.bbc.co.uk/v2/experience/inline/search?q=%s&limit=24";
    private static final String FEED_URL_TEMPLATE = "https://podcasts.files.bbci.co.uk/%s.rss";

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            String encodedQuery;
            try {
                encodedQuery = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                encodedQuery = query;
            }

            String url = String.format(API_URL, encodedQuery);

            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            Request request = new Request.Builder().url(url).build();
            List<PodcastSearchResult> podcasts = new ArrayList<>();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String resultString = response.body().string();
                    JSONObject result = new JSONObject(resultString);
                    JSONArray modules = result.getJSONArray("data");
                    for (int m = 0; m < modules.length(); m++) {
                        JSONObject module = modules.getJSONObject(m);
                        if (!"container_search".equals(module.optString("id"))) {
                            continue;
                        }
                        JSONArray items = module.optJSONArray("data");
                        if (items == null) {
                            break;
                        }
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            String id = item.optString("id", null);
                            if (id == null || id.isEmpty()) {
                                continue;
                            }
                            String feedUrl = String.format(FEED_URL_TEMPLATE, id);
                            if (!feedExists(client, feedUrl)) {
                                continue;
                            }
                            JSONObject titles = item.optJSONObject("titles");
                            String title = titles != null ? titles.optString("primary", "") : "";
                            JSONObject network = item.optJSONObject("network");
                            String author = network != null ? network.optString("short_title", null) : null;
                            String imageUrl = item.optString("image_url", null);
                            if (imageUrl != null) {
                                imageUrl = imageUrl.replace("{recipe}", "512x512");
                            }
                            podcasts.add(PodcastSearchResult.fromExternalSource(title, imageUrl, feedUrl, author));
                        }
                        break;
                    }
                } else {
                    subscriber.onError(new IOException(response.toString()));
                }
            } catch (IOException | JSONException e) {
                subscriber.onError(e);
            }
            subscriber.onSuccess(podcasts);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private static boolean feedExists(OkHttpClient client, String feedUrl) {
        Request head = new Request.Builder().url(feedUrl).head().build();
        try (Response r = client.newCall(head).execute()) {
            return r.isSuccessful();
        } catch (IOException e) {
            return false;
        }
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
        return "BBC Sounds";
    }
}
