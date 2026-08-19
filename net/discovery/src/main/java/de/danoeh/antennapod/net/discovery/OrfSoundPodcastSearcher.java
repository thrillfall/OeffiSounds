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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class OrfSoundPodcastSearcher implements PodcastSearcher {
    private static final String API_URL = "https://audioapi.orf.at/radiothek/api/2.0/podcasts";

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            Request request = new Request.Builder().url(API_URL).build();
            List<PodcastSearchResult> podcasts = new ArrayList<>();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String resultString = response.body().string();
                    JSONObject root = new JSONObject(resultString);
                    JSONObject stations = root.optJSONObject("payload");
                    if (stations == null) {
                        stations = root;
                    }
                    String queryLower = query.toLowerCase(Locale.ROOT);
                    Iterator<String> stationKeys = stations.keys();
                    while (stationKeys.hasNext()) {
                        String stationKey = stationKeys.next();
                        JSONArray stationPodcasts = stations.optJSONArray(stationKey);
                        if (stationPodcasts == null) {
                            continue;
                        }
                        for (int i = 0; i < stationPodcasts.length(); i++) {
                            JSONObject podcast = stationPodcasts.getJSONObject(i);
                            String title = podcast.optString("title", "");
                            String description = podcast.optString("description", "");
                            if (!title.toLowerCase(Locale.ROOT).contains(queryLower)
                                    && !description.toLowerCase(Locale.ROOT).contains(queryLower)) {
                                continue;
                            }
                            String feedUrl = null;
                            JSONObject urls = podcast.optJSONObject("urls");
                            if (urls != null) {
                                feedUrl = urls.optString("feed", null);
                            }
                            if (feedUrl == null || feedUrl.isEmpty()) {
                                continue;
                            }
                            String imageUrl = null;
                            JSONObject image = podcast.optJSONObject("image");
                            if (image != null) {
                                JSONObject versions = image.optJSONObject("versions");
                                if (versions != null) {
                                    JSONObject standard = versions.optJSONObject("standard");
                                    if (standard != null) {
                                        imageUrl = standard.optString("path", null);
                                    }
                                }
                            }
                            String author = podcast.optString("author", "ORF");
                            podcasts.add(PodcastSearchResult.fromExternalSource(title, imageUrl, feedUrl, author));
                        }
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
        return "ORF Sound";
    }
}
