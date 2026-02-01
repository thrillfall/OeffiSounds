package de.danoeh.antennapod.net.discovery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudiothekPodcastSearcher implements PodcastSearcher {
    private static final String API_BASE_URL = "https://api.ardaudiothek.de";
    private static final String GRAPHQL_URL = API_BASE_URL + "/graphql";
    private static final String PROGRAM_SET_URL_TEMPLATE = API_BASE_URL + "/programsets/%s";
    private static final String USER_AGENT = "AntennaPod";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final String PROGRAM_SET_SEARCH_QUERY = "query SearchProgramSets($query:String!, $offset:Int!, $limit:Int!) {"
            + " search(query:$query, offset:$offset, limit:$limit, type:ProgramSets) {"
            + "  programSets {"
            + "   nodes {"
            + "    id rowId title synopsis sharingUrl "
            + "    image { url url1X1 } "
            + "    publicationService { organizationName }"
            + "   }"
            + "  }"
            + " }"
            + "}";

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) emitter -> {
            OkHttpClient client = AntennapodHttpClient.getHttpClient();

            JSONObject variables = new JSONObject();
            variables.put("query", query);
            variables.put("offset", 0);
            variables.put("limit", 24);

            JSONObject requestJson = new JSONObject();
            requestJson.put("query", PROGRAM_SET_SEARCH_QUERY);
            requestJson.put("variables", variables);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(GRAPHQL_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", USER_AGENT)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    emitter.onError(new IOException(response.toString()));
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject root = new JSONObject(responseBody);
                JSONArray errors = root.optJSONArray("errors");
                if (errors != null && errors.length() > 0) {
                    emitter.onError(new IOException(errors.toString()));
                    return;
                }

                emitter.onSuccess(AudiothekSearchResultParser.parseProgramSets(root, PROGRAM_SET_URL_TEMPLATE));
            } catch (IOException | JSONException e) {
                emitter.onError(e);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Single<String> lookupUrl(String resultUrl) {
        return Single.just(resultUrl);
    }

    @Override
    public boolean urlNeedsLookup(String resultUrl) {
        return false;
    }

    @Override
    public String getName() {
        return "ARD Audiothek";
    }
}
