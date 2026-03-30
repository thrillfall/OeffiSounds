package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.SquareImageView;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;

public class BbcSoundsFeaturedSection extends HomeSection {
    public static final String TAG = "BbcSoundsFeaturedSection";

    // Public (no auth) endpoint powering the BBC Sounds homepage for unauthenticated users
    private static final String BBC_LISTEN_SIGNIN_URL =
            "https://rms.api.bbc.co.uk/v2/experience/inline/listen/sign-in?continue_listening_control=true";
    // Module ID for the editorial speech/podcast picks shown on the BBC Sounds home page
    private static final String MODULE_UNMISSABLE_SPEECH = "unmissable_speech";
    private static final String BBC_FEED_URL_TEMPLATE = "https://podcasts.files.bbci.co.uk/%s.rss";
    private static final int NUM_ITEMS = 8;

    private Disposable disposable;
    private BbcSoundsHorizontalAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new BbcSoundsHorizontalAdapter((MainActivity) requireActivity());
        listAdapter.setDummyViews(NUM_ITEMS);
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(listAdapter);
        int paddingHorizontal = (int) (12 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        viewBinding.emptyLabel.setText(R.string.home_new_empty_text);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    protected void handleMoreClick() {
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.home_bbc_sounds_featured_title);
    }

    @Override
    protected String getMoreLinkTitle() {
        return "";
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        listAdapter.setDummyViews(NUM_ITEMS);

        disposable = Observable.fromCallable(() -> {
                    Request request = new Request.Builder().url(BBC_LISTEN_SIGNIN_URL).build();
                    try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response: " + response);
                        }
                        String body = response.body() != null ? response.body().string() : "";
                        return parseFeatured(body);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(items);
                    boolean isEmpty = items.isEmpty();
                    viewBinding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    viewBinding.emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(new ArrayList<>());
                    viewBinding.recyclerView.setVisibility(View.GONE);
                    viewBinding.emptyLabel.setVisibility(View.VISIBLE);
                });
    }

    private static List<BbcSoundsItem> parseFeatured(String json) throws JSONException {
        // /v2/experience/inline/listen/sign-in is the public (no-auth) BBC Sounds home page
        // endpoint. It returns an array of inline_display_module objects. We target the
        // "unmissable_speech" module which is the editorial podcast picks shown to unauthenticated
        // users ("Podcasts Packed With Personality" / "Recommended Today"). Falls back to the
        // first module with a list of items if the module ID changes.
        JSONObject root = new JSONObject(json);
        JSONArray modules = root.optJSONArray("data");
        if (modules == null) {
            return new ArrayList<>();
        }

        JSONArray targetData = null;
        JSONArray fallbackData = null;
        for (int m = 0; m < modules.length(); m++) {
            JSONObject module = modules.optJSONObject(m);
            if (module == null) {
                continue;
            }
            JSONArray moduleData = module.optJSONArray("data");
            if (moduleData == null || moduleData.length() == 0) {
                continue;
            }
            if (MODULE_UNMISSABLE_SPEECH.equals(module.optString("id", ""))) {
                targetData = moduleData;
                break;
            }
            if (fallbackData == null) {
                fallbackData = moduleData;
            }
        }

        JSONArray data = targetData != null ? targetData : fallbackData;
        if (data == null) {
            return new ArrayList<>();
        }

        List<BbcSoundsItem> items = new ArrayList<>();
        for (int i = 0; i < data.length() && items.size() < NUM_ITEMS; i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }

            // Use the tlec_urn brand PID (last segment after ':') for the RSS feed URL.
            // The brand/tlec PID has significantly better RSS feed coverage than the series id.
            String tlecUrn = item.optString("tlec_urn", null);
            String pid = null;
            if (tlecUrn != null && tlecUrn.contains(":")) {
                pid = tlecUrn.substring(tlecUrn.lastIndexOf(':') + 1);
            }
            if (pid == null || pid.isEmpty()) {
                continue;
            }

            JSONObject titles = item.optJSONObject("titles");
            String title = titles != null ? titles.optString("primary", "") : "";

            String imageUrl = item.optString("image_url", null);
            if (imageUrl != null) {
                imageUrl = imageUrl.replace("{recipe}", "512x512");
            }

            String feedUrl = String.format(BBC_FEED_URL_TEMPLATE, pid);
            items.add(new BbcSoundsItem(title, imageUrl, feedUrl));
        }
        return items;
    }

    private static class BbcSoundsItem {
        public final String title;
        public final String imageUrl;
        public final String feedUrl;

        private BbcSoundsItem(String title, String imageUrl, String feedUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class BbcSoundsHorizontalAdapter extends RecyclerView.Adapter<BbcSoundsHorizontalAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<BbcSoundsItem> items = new ArrayList<>();
        private int dummyViews = 0;

        BbcSoundsHorizontalAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<BbcSoundsItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MainActivity activity = mainActivityRef.get();
            View convertView;
            if (activity != null) {
                convertView = View.inflate(activity, R.layout.horizontal_feed_item, null);
            } else {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.horizontal_feed_item, parent, false);
            }
            return new Holder(convertView);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MainActivity activity = mainActivityRef.get();
            if (activity == null) {
                holder.itemView.setAlpha(0.1f);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }
            holder.cardView.setVisibility(View.VISIBLE);
            holder.actionButton.setVisibility(View.GONE);
            if (position >= items.size()) {
                holder.itemView.setAlpha(0.1f);
                Glide.with(activity).clear(holder.imageView);
                holder.imageView.setImageResource(R.color.medium_gray);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }

            holder.itemView.setAlpha(1.0f);
            BbcSoundsItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleView.setText(item.title);
            holder.titleView.setVisibility(View.VISIBLE);
            holder.imageView.setOnClickListener(v ->
                    activity.startActivity(new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent()));

            Glide.with(activity)
                    .load(item.imageUrl)
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .centerCrop()
                            .dontAnimate())
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return items.size() + dummyViews;
        }

        static class Holder extends RecyclerView.ViewHolder {
            SquareImageView imageView;
            androidx.cardview.widget.CardView cardView;
            android.widget.Button actionButton;
            TextView titleView;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.discovery_cover);
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
                actionButton = itemView.findViewById(R.id.actionButton);
                cardView = itemView.findViewById(R.id.cardView);
                titleView = itemView.findViewById(R.id.titleLabel);
            }
        }
    }
}
