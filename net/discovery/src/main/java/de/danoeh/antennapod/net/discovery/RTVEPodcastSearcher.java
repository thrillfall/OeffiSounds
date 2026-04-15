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
import java.util.List;
import java.util.Locale;

public class RTVEPodcastSearcher implements PodcastSearcher {
    private static final String API_URL =
            "https://api.rtve.es/api/agr-programas/18690/programas.json?size=100";
    private static final String FEED_URL_TEMPLATE =
            "https://api.rtve.es/api/programas/%s/audios.rss";

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            Request request = new Request.Builder().url(API_URL).build();
            List<PodcastSearchResult> podcasts = new ArrayList<>();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject root = new JSONObject(body);
                    JSONArray items = root.getJSONObject("page").getJSONArray("items");
                    String queryLower = query.toLowerCase(Locale.ROOT);

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String name = item.optString("name", "");
                        String description = item.optString("description", "");

                        if (!name.toLowerCase(Locale.ROOT).contains(queryLower)
                                && !description.toLowerCase(Locale.ROOT).contains(queryLower)) {
                            continue;
                        }

                        String id = item.optString("id", "");
                        String feedUrl = String.format(FEED_URL_TEMPLATE, id);
                        String imageUrl = item.optString("imagePodcast", null);
                        if (imageUrl == null || imageUrl.isEmpty()) {
                            imageUrl = item.optString("logo", null);
                        }

                        podcasts.add(PodcastSearchResult.fromExternalSource(
                                name, imageUrl, feedUrl, "RTVE"));
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
        return "RTVE";
    }
}
