package com.mraulio.gbcameramanager.ui.gbstorage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.db.ImageDataDao;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcPalette;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.ImageData;
import com.mraulio.gbcameramanager.ui.gallery.GalleryUtils;
import com.mraulio.gbcameramanager.ui.gallery.GalleryFragment;
import com.mraulio.gbcameramanager.utils.DiskCache;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Inflater;

public final class GbStorageSyncManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService PREVIEW_EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String SOURCE = "GBCamera Android Manager";
    private static final String SOURCE_FORMAT = "android-manager-v1";
    private static final int BATCH_SIZE = 20;
    private static final int PREVIEW_THUMBNAIL_WIDTH = 80;
    private static final int PREVIEW_THUMBNAIL_HEIGHT = 72;
    private static final String DUPLICATE_MODE_SKIP = "skip";
    private static final String DUPLICATE_MODE_OVERWRITE = "overwrite";
    private static final String DUPLICATE_MODE_DUPLICATE = "duplicate";

    private GbStorageSyncManager() {
    }

    public interface ConnectionResultCallback {
        void onResult(ConnectionResult result);
    }

    public interface RemotePageCallback {
        void onResult(RemotePageResult result);
    }

    public interface RemoteImageCallback {
        void onResult(RemoteImageResult result);
    }

    public interface RemotePreviewCallback {
        void onResult(RemotePreviewResult result);
    }

    public static final class RemotePageResult {
        public final boolean success;
        public final String message;
        public final List<RemoteGbStorageImage> images;
        public final String nextCursor;
        public final int pageSize;
        public final int totalCount;

        RemotePageResult(boolean success, String message, List<RemoteGbStorageImage> images, String nextCursor, int pageSize, int totalCount) {
            this.success = success;
            this.message = message;
            this.images = images;
            this.nextCursor = nextCursor;
            this.pageSize = pageSize;
            this.totalCount = totalCount;
        }
    }

    public static final class RemoteImageResult {
        public final boolean success;
        public final String message;
        public final RemoteGbStorageImage remoteImage;
        public final Bitmap bitmap;

        RemoteImageResult(boolean success, String message, RemoteGbStorageImage remoteImage, Bitmap bitmap) {
            this.success = success;
            this.message = message;
            this.remoteImage = remoteImage;
            this.bitmap = bitmap;
        }
    }

    public static final class RemotePreviewResult {
        public final boolean success;
        public final String id;
        public final Bitmap bitmap;

        RemotePreviewResult(boolean success, String id, Bitmap bitmap) {
            this.success = success;
            this.id = id;
            this.bitmap = bitmap;
        }
    }

    private static final class SyncStatusSummary {
        final int totalCount;
        final int syncedCount;
        final int notSyncedCount;

        SyncStatusSummary(int totalCount, int syncedCount, int notSyncedCount) {
            this.totalCount = totalCount;
            this.syncedCount = syncedCount;
            this.notSyncedCount = notSyncedCount;
        }
    }

    public static final class ConnectionResult {
        private final boolean success;
        private final String details;

        ConnectionResult(boolean success, String details) {
            this.success = success;
            this.details = details;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getDetails() {
            return details;
        }
    }

    private static final class DuplicateMatch {
        final String sourceHash;
        final String remoteId;
        final String remoteName;
        final String sourceFormat;
        final String contentHash;

        DuplicateMatch(String sourceHash, String remoteId, String remoteName, String sourceFormat, String contentHash) {
            this.sourceHash = sourceHash;
            this.remoteId = remoteId;
            this.remoteName = remoteName;
            this.sourceFormat = sourceFormat;
            this.contentHash = contentHash;
        }
    }

    private static final class SyncPreviewItem {
        final GbcImage image;
        final Bitmap thumbnail;
        final String createdText;
        final boolean duplicate;

        SyncPreviewItem(GbcImage image, Bitmap thumbnail, String createdText, boolean duplicate) {
            this.image = image;
            this.thumbnail = thumbnail;
            this.createdText = createdText;
            this.duplicate = duplicate;
        }
    }

    private static final class SyncPlan {
        final List<GbcImage> imagesToSync;
        final Set<String> overwriteHashes;
        final String duplicateMode;
        final int selectedCount;
        final int overwriteCount;
        final int skippedCount;

        SyncPlan(List<GbcImage> imagesToSync, Set<String> overwriteHashes, String duplicateMode, int selectedCount, int overwriteCount, int skippedCount) {
            this.imagesToSync = imagesToSync;
            this.overwriteHashes = overwriteHashes;
            this.duplicateMode = duplicateMode;
            this.selectedCount = selectedCount;
            this.overwriteCount = overwriteCount;
            this.skippedCount = skippedCount;
        }
    }

    private static final class ReviewListAdapter extends BaseAdapter {
        private final Context context;
        private final List<SyncPreviewItem> previewItems;
        private final List<SyncPreviewItem> visibleItems = new ArrayList<>();
        private final List<String> visibleStatuses = new ArrayList<>();

        ReviewListAdapter(Context context, List<SyncPreviewItem> previewItems, Set<String> overwriteHashes) {
            this.context = context;
            this.previewItems = previewItems;
            updateSelection(overwriteHashes, DUPLICATE_MODE_SKIP);
        }

        void updateSelection(Set<String> overwriteHashes, String duplicateMode) {
            visibleItems.clear();
            visibleStatuses.clear();
            for (SyncPreviewItem item : previewItems) {
                boolean shouldInclude = !item.duplicate || !DUPLICATE_MODE_SKIP.equals(duplicateMode);
                if (!shouldInclude) {
                    continue;
                }

                visibleItems.add(item);
                visibleStatuses.add(item.duplicate
                        ? (DUPLICATE_MODE_OVERWRITE.equals(duplicateMode) ? context.getString(R.string.gbstorage_sync_overwrite_label) : context.getString(R.string.gbstorage_sync_duplicate_label))
                        : context.getString(R.string.gbstorage_sync_new_label));
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visibleItems.size();
        }

        @Override
        public SyncPreviewItem getItem(int position) {
            return visibleItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SyncPreviewItem item = getItem(position);
            String status = visibleStatuses.get(position);
            return buildPreviewRow(context, item, false, item.duplicate ? context.getString(R.string.gbstorage_sync_duplicate_note) : null, status, null);
        }
    }

    private interface ProgressUpdateCallback {
        void onProgress(int processedInBatch);
    }

    private static final class SyncProgressDialog {
        private final AlertDialog dialog;
        private final ProgressBar progressBar;
        private final TextView statusText;
        private final TextView detailText;

        SyncProgressDialog(Context context, String title) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 30, 40, 20);

            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(18f);
            titleView.setTextColor(Color.BLACK);
            titleView.setPadding(0, 0, 0, 20);
            layout.addView(titleView);

            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(true);
            progressBar.setMax(100);
            progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            layout.addView(progressBar);

            statusText = new TextView(context);
            statusText.setPadding(0, 20, 0, 0);
            layout.addView(statusText);

            detailText = new TextView(context);
            detailText.setPadding(0, 8, 0, 0);
            layout.addView(detailText);

            builder.setView(layout);
            builder.setCancelable(false);
            builder.setNegativeButton(context.getString(R.string.cancel), (dialogInterface, which) -> dialogInterface.dismiss());

            dialog = builder.create();
        }

        void show() {
            dialog.show();
        }

        void dismiss() {
            dialog.dismiss();
        }

        void update(int processed, int total, String detail, String estimate) {
            progressBar.setIndeterminate(true);
            statusText.setText(detail);
            detailText.setText(estimate);
        }
    }

    public static void testConnection(Context context, String serverAddress, String apiKey, ConnectionResultCallback callback) {
        if (TextUtils.isEmpty(serverAddress) || TextUtils.isEmpty(apiKey)) {
            MAIN.post(() -> callback.onResult(new ConnectionResult(false, context.getString(R.string.gbstorage_test_failed) + "\nMissing server address or API key.")));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                HttpResult result = executeRequest(resolveBaseUrl(serverAddress) + "/api/images?limit=1", "GET", apiKey, null);
                String details = result.responseCode >= 200 && result.responseCode < 300
                        ? context.getString(R.string.gbstorage_test_ok)
                        : context.getString(R.string.gbstorage_test_failed) + "\n" + (TextUtils.isEmpty(result.responseBody) ? ("HTTP " + result.responseCode) : result.responseBody);
                MAIN.post(() -> callback.onResult(new ConnectionResult(result.responseCode >= 200 && result.responseCode < 300, details)));
            } catch (Exception exception) {
                MAIN.post(() -> callback.onResult(new ConnectionResult(false, context.getString(R.string.gbstorage_test_failed) + "\n" + (exception.getMessage() == null ? exception.toString() : exception.getMessage()))));
            }
        });
    }

    public static void fetchRemotePage(Context context, int pageNumber, int limit, String sort, List<String> tags, RemotePageCallback callback) {
        if (TextUtils.isEmpty(StaticValues.gbStorageServerAddress) || TextUtils.isEmpty(StaticValues.gbStorageApiKey)) {
            MAIN.post(() -> callback.onResult(new RemotePageResult(false, context.getString(R.string.gbstorage_test_failed), Collections.emptyList(), null, limit, 0)));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder(resolveBaseUrl(StaticValues.gbStorageServerAddress))
                        .append("/api/images?limit=").append(Math.max(1, limit))
                        .append("&page=").append(Math.max(1, pageNumber))
                        .append("&sort=").append(urlEncode(TextUtils.isEmpty(sort) ? "created-desc" : sort));
                if (tags != null) {
                    for (String tag : tags) {
                        if (!TextUtils.isEmpty(tag)) {
                            urlBuilder.append("&tags=").append(urlEncode(tag.trim()));
                        }
                    }
                }

                HttpResult result = executeRequest(urlBuilder.toString(), "GET", StaticValues.gbStorageApiKey, null);
                if (result.responseCode < 200 || result.responseCode >= 300) {
                    throw new IOException(result.responseBody);
                }

                JSONObject response = new JSONObject(result.responseBody);
                JSONArray items = response.optJSONArray("items");
                List<RemoteGbStorageImage> remoteImages = new ArrayList<>();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        GbcImage image = new GbcImage();
                        image.setHashCode(item.optString("sourceHash"));
                        image.setName(item.optString("name"));
                        image.setRotation(item.optInt("rotation", 0));
                        image.setCreationDate(parseRemoteDate(item.optString("createdAtUtc")));
                        image.setTags(jsonArrayToTags(item.optJSONArray("tags")));
                        image.setGbStorageSyncStatus(localContainsHash(image.getHashCode()) ? GbcImage.GB_STORAGE_SYNCED : GbcImage.GB_STORAGE_SYNC_NOT_SYNCED);
                        RemoteGbStorageImage remoteImage = new RemoteGbStorageImage(item.optString("id"), image);
                        remoteImage.previewBitmap = getCachedRemotePreview(context, remoteImage.id);
                        remoteImages.add(remoteImage);
                    }
                }

                MAIN.post(() -> callback.onResult(new RemotePageResult(
                        true,
                        "",
                        remoteImages,
                        response.optString("nextCursor", null),
                        response.optInt("pageSize", limit),
                        response.optInt("totalCount", remoteImages.size()))));
            } catch (Exception exception) {
                MAIN.post(() -> callback.onResult(new RemotePageResult(false, exception.getMessage(), Collections.emptyList(), null, limit, 0)));
            }
        });
    }

    public static void fetchRemotePreview(Context context, RemoteGbStorageImage remoteImage, RemotePreviewCallback callback) {
        if (remoteImage == null || TextUtils.isEmpty(remoteImage.id)) {
            MAIN.post(() -> callback.onResult(new RemotePreviewResult(false, null, null)));
            return;
        }
        PREVIEW_EXECUTOR.execute(() -> {
            Bitmap bitmap = getCachedRemotePreview(context, remoteImage.id);
            if (bitmap == null) {
                bitmap = fetchRemotePreviewBitmap(remoteImage.id, null, null);
                if (bitmap != null) {
                    putCachedRemotePreview(context, remoteImage.id, bitmap);
                }
            }
            Bitmap finalBitmap = bitmap;
            MAIN.post(() -> callback.onResult(new RemotePreviewResult(finalBitmap != null, remoteImage.id, finalBitmap)));
        });
    }

    public static void fetchRemoteImage(Context context, RemoteGbStorageImage listImage, RemoteImageCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                RemoteGbStorageImage detailedImage = fetchRemoteImageInternal(listImage);
                Bitmap bitmap = createThumbnailBitmap(context, detailedImage.image);
                if (bitmap != null) {
                    Utils.imageBitmapCache.put(detailedImage.image.getHashCode(), bitmap);
                }
                MAIN.post(() -> callback.onResult(new RemoteImageResult(true, "", detailedImage, bitmap)));
            } catch (Exception exception) {
                MAIN.post(() -> callback.onResult(new RemoteImageResult(false, exception.getMessage(), listImage, null)));
            }
        });
    }

    public static void downloadRemoteImage(Context context, RemoteGbStorageImage listImage, RemoteImageCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                RemoteGbStorageImage detailedImage = fetchRemoteImageInternal(listImage);
                GbcImage gbcImage = detailedImage.image;
                if (localContainsHash(gbcImage.getHashCode())) {
                    MAIN.post(() -> callback.onResult(new RemoteImageResult(false, context.getString(R.string.image_exists), detailedImage, null)));
                    return;
                }

                Bitmap bitmap = createGalleryBitmap(gbcImage);
                if (bitmap == null) {
                    throw new IOException(context.getString(R.string.gbstorage_remote_download_failed));
                }

                ImageData imageData = new ImageData();
                imageData.setImageId(gbcImage.getHashCode());
                imageData.setData(gbcImage.getImageBytes());
                gbcImage.setGbStorageSyncStatus(GbcImage.GB_STORAGE_SYNCED);
                StaticValues.db.imageDao().insert(gbcImage);
                StaticValues.db.imageDataDao().insert(imageData);
                Utils.gbcImagesList.add(gbcImage);
                Utils.imageBitmapCache.put(gbcImage.getHashCode(), bitmap);
                if (GalleryFragment.diskCache != null) {
                    GalleryFragment.diskCache.put(gbcImage.getHashCode(), bitmap);
                }
                Utils.retrieveTags(Utils.gbcImagesList);
                MAIN.post(() -> callback.onResult(new RemoteImageResult(true, context.getString(R.string.gbstorage_remote_downloaded), detailedImage, bitmap)));
            } catch (Exception exception) {
                MAIN.post(() -> callback.onResult(new RemoteImageResult(false, exception.getMessage(), listImage, null)));
            }
        });
    }

    private static RemoteGbStorageImage fetchRemoteImageInternal(RemoteGbStorageImage listImage) throws Exception {
        HttpResult result = executeRequest(
                resolveBaseUrl(StaticValues.gbStorageServerAddress) + "/api/images/" + urlEncode(listImage.id) + "?exportProfile=android-manager-v1",
                "GET",
                StaticValues.gbStorageApiKey,
                null);
        if (result.responseCode < 200 || result.responseCode >= 300) {
            throw new IOException(result.responseBody);
        }

        JSONObject response = new JSONObject(result.responseBody);
        JSONObject payload = response.getJSONObject("payload");
        JSONArray images = payload.getJSONObject("state").getJSONArray("images");
        JSONObject imageJson = images.getJSONObject(0);

        GbcImage image = new GbcImage();
        image.setHashCode(imageJson.optString("hash", response.optString("sourceHash")));
        image.setName(imageJson.optString("title", response.optString("name")));
        image.setCreationDate(parseRemoteDate(imageJson.optString("created", response.optString("createdAtUtc"))));
        image.setPaletteId(optStringOrDefault(imageJson, "palette", StaticValues.defaultPaletteId));
        image.setFramePaletteId(optStringOrDefault(imageJson, "framePalette", StaticValues.defaultPaletteId));
        image.setFrameId(emptyToNull(imageJson.optString("frame", null)));
        image.setInvertPalette(imageJson.optBoolean("invertPalette", false));
        image.setInvertFramePalette(imageJson.optBoolean("invertFramePalette", false));
        image.setLockFrame(imageJson.optBoolean("lockFrame", false));
        image.setRotation(imageJson.optInt("rotation", response.optInt("rotation", 0)));
        image.setTags(jsonArrayToTags(imageJson.optJSONArray("tags")));
        image.setImageMetadata(jsonObjectToMap(imageJson.optJSONObject("meta")));
        image.setImageBytes(Utils.ensureGbcImageHasBlackBorder(decodeCompressedTileBytes(payload.optString(image.getHashCode()))));
        image.setGbStorageSyncStatus(localContainsHash(image.getHashCode()) ? GbcImage.GB_STORAGE_SYNCED : GbcImage.GB_STORAGE_SYNC_NOT_SYNCED);
        RemoteGbStorageImage remoteImage = new RemoteGbStorageImage(listImage.id, image);
        remoteImage.previewBitmap = listImage.previewBitmap;
        return remoteImage;
    }

    private static Bitmap fetchRemotePreviewBitmap(String id, String imagePaletteId, String framePaletteId) {
        try {
            StringBuilder urlBuilder = new StringBuilder(resolveBaseUrl(StaticValues.gbStorageServerAddress))
                    .append("/api/images/").append(urlEncode(id)).append("/preview");
            if (!TextUtils.isEmpty(imagePaletteId) || !TextUtils.isEmpty(framePaletteId)) {
                urlBuilder.append("?");
                if (!TextUtils.isEmpty(imagePaletteId)) {
                    urlBuilder.append("imagePaletteId=").append(urlEncode(imagePaletteId));
                }
                if (!TextUtils.isEmpty(framePaletteId)) {
                    if (!TextUtils.isEmpty(imagePaletteId)) {
                        urlBuilder.append("&");
                    }
                    urlBuilder.append("framePaletteId=").append(urlEncode(framePaletteId));
                }
            }
            BinaryHttpResult result = executeBinaryRequest(urlBuilder.toString(), StaticValues.gbStorageApiKey);
            if (result.responseCode >= 200 && result.responseCode < 300) {
                return BitmapFactory.decodeByteArray(result.body, 0, result.body.length);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Bitmap getCachedRemotePreview(Context context, String id) {
        if (context == null || TextUtils.isEmpty(id)) {
            return null;
        }
        return new DiskCache(context).get(remotePreviewCacheKey(id));
    }

    private static void putCachedRemotePreview(Context context, String id, Bitmap bitmap) {
        if (context == null || TextUtils.isEmpty(id) || bitmap == null) {
            return;
        }
        new DiskCache(context).put(remotePreviewCacheKey(id), bitmap);
    }

    private static String remotePreviewCacheKey(String id) {
        return "gbstorage_preview_" + Base64.encodeToString(id.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    public static void startSelectionSync(Activity activity, List<Integer> selectedIndexes, List<GbcImage> filteredGbcImages) {
        if (selectedIndexes == null || selectedIndexes.isEmpty()) {
            Utils.toast(activity, activity.getString(R.string.no_selected));
            return;
        }
        if (TextUtils.isEmpty(StaticValues.gbStorageServerAddress) || TextUtils.isEmpty(StaticValues.gbStorageApiKey)) {
            Utils.toast(activity, activity.getString(R.string.gbstorage_test_failed));
            return;
        }

        final List<GbcImage> selectedImages = new ArrayList<>();
        final List<Integer> orderedIndexes = new ArrayList<>(selectedIndexes);
        Collections.sort(orderedIndexes);
        for (int index : orderedIndexes) {
            selectedImages.add(filteredGbcImages.get(index));
        }

        LoadingDialog loadingDialog = new LoadingDialog(activity, activity.getString(R.string.loading));
        loadingDialog.showDialog();

        EXECUTOR.execute(() -> {
            try {
                List<String> hashes = new ArrayList<>();
                for (GbcImage gbcImage : selectedImages) {
                    hashes.add(gbcImage.getHashCode());
                }

                HttpResult duplicateResult = executeRequest(
                        resolveBaseUrl(StaticValues.gbStorageServerAddress) + "/api/images/duplicates/check",
                        "POST",
                        StaticValues.gbStorageApiKey,
                        new JSONObject().put("hashes", new JSONArray(hashes)));

                if (duplicateResult.responseCode < 200 || duplicateResult.responseCode >= 300) {
                    throw new IOException(duplicateResult.responseBody);
                }

                JSONObject duplicateJson = new JSONObject(duplicateResult.responseBody);
                JSONArray duplicatesArray = duplicateJson.optJSONArray("duplicates");
                List<DuplicateMatch> duplicates = new ArrayList<>();
                Set<String> duplicateHashes = new HashSet<>();
                if (duplicatesArray != null) {
                    for (int i = 0; i < duplicatesArray.length(); i++) {
                        JSONObject duplicateObject = duplicatesArray.getJSONObject(i);
                        String sourceHash = duplicateObject.optString("sourceHash");
                        duplicateHashes.add(sourceHash);
                        duplicates.add(new DuplicateMatch(
                                sourceHash,
                                duplicateObject.optString("id"),
                                duplicateObject.optString("name"),
                                duplicateObject.optString("sourceFormat"),
                                duplicateObject.optString("contentHash")));
                    }
                }

                List<SyncPreviewItem> previewItems = buildPreviewItems(activity, selectedImages, duplicateHashes);
                applySyncStatusToHashes(selectedImages, duplicateHashes, GbcImage.GB_STORAGE_SYNCED, false);

                MAIN.post(() -> {
                    loadingDialog.dismissDialog();
                    refreshGallerySyncIndicators();
                    showDuplicateDialog(activity, previewItems, duplicates, StaticValues.gbStorageTimestampSource);
                });
            } catch (Exception exception) {
                MAIN.post(() -> {
                    loadingDialog.dismissDialog();
                    showMessage(activity, activity.getString(R.string.gbstorage_sync_failed) + "\n" + exception.getMessage());
                });
            }
        });
    }

    private static void showDuplicateDialog(Activity activity, List<SyncPreviewItem> previewItems, List<DuplicateMatch> duplicates, String timestampSource) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.gbstorage_sync_preview_title));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 20, 40, 10);

        EditText virtualPathField = new EditText(activity);
        virtualPathField.setHint(activity.getString(R.string.gbstorage_virtual_path));
        virtualPathField.setSingleLine(true);
        virtualPathField.setText("/");
        virtualPathField.setSelection(virtualPathField.getText().length());
        root.addView(virtualPathField);

        TextView summary = new TextView(activity);
        summary.setText("");
        summary.setPadding(0, 0, 0, 20);
        root.addView(summary);

        if (!duplicates.isEmpty()) {
            TextView duplicateHeader = new TextView(activity);
            duplicateHeader.setText(activity.getString(R.string.gbstorage_duplicates_title));
            duplicateHeader.setPadding(0, 0, 0, 12);
            duplicateHeader.setTextColor(Color.BLACK);
            root.addView(duplicateHeader);

            RadioGroup duplicateModeGroup = new RadioGroup(activity);
            duplicateModeGroup.setOrientation(RadioGroup.HORIZONTAL);
            RadioButton skipButton = buildDuplicateModeButton(activity, R.string.gbstorage_skip, DUPLICATE_MODE_SKIP);
            RadioButton overwriteButton = buildDuplicateModeButton(activity, R.string.gbstorage_overwrite_label_short, DUPLICATE_MODE_OVERWRITE);
            RadioButton duplicateButton = buildDuplicateModeButton(activity, R.string.gbstorage_duplicate_label, DUPLICATE_MODE_DUPLICATE);
            duplicateModeGroup.addView(skipButton);
            duplicateModeGroup.addView(overwriteButton);
            duplicateModeGroup.addView(duplicateButton);
            duplicateModeGroup.check(skipButton.getId());
            root.addView(duplicateModeGroup);

            Map<String, SyncPreviewItem> previewItemsByHash = new LinkedHashMap<>();
            for (SyncPreviewItem previewItem : previewItems) {
                previewItemsByHash.put(previewItem.image.getHashCode(), previewItem);
            }

            ScrollView duplicateScrollView = new ScrollView(activity);
            duplicateScrollView.setVerticalScrollBarEnabled(true);
            duplicateScrollView.setScrollbarFadingEnabled(false);
            LinearLayout contentLayout = new LinearLayout(activity);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            duplicateScrollView.addView(contentLayout);

            for (DuplicateMatch duplicate : duplicates) {
                SyncPreviewItem previewItem = previewItemsByHash.get(duplicate.sourceHash);
                ViewGroup row = buildPreviewRow(activity, previewItem, false, activity.getString(R.string.gbstorage_sync_duplicate_note), activity.getString(R.string.gbstorage_skip), null);
                contentLayout.addView(row);
            }

            LinearLayout.LayoutParams duplicateListParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.addView(duplicateScrollView, duplicateListParams);

            TextView reviewHeader = new TextView(activity);
            reviewHeader.setText(activity.getString(R.string.gbstorage_sync_preview_title));
            reviewHeader.setPadding(0, 24, 0, 12);
            reviewHeader.setTextColor(Color.BLACK);
            root.addView(reviewHeader);

            TextView reviewEmptyView = new TextView(activity);
            reviewEmptyView.setText(activity.getString(R.string.gbstorage_sync_duplicates_none));
            reviewEmptyView.setVisibility(View.GONE);
            root.addView(reviewEmptyView);

            ListView reviewListView = new ListView(activity);
            reviewListView.setDividerHeight(0);
            reviewListView.setFastScrollEnabled(true);
            reviewListView.setFastScrollAlwaysVisible(true);
            reviewListView.setVerticalScrollBarEnabled(true);
            reviewListView.setScrollbarFadingEnabled(false);
            ReviewListAdapter reviewAdapter = new ReviewListAdapter(activity, previewItems, Collections.emptySet());
            reviewListView.setAdapter(reviewAdapter);
            root.addView(reviewListView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            final Runnable[] refreshReviewHolder = new Runnable[1];
            refreshReviewHolder[0] = () -> {
                SyncPlan syncPlan = buildSyncPlan(previewItems, duplicates, getSelectedDuplicateMode(duplicateModeGroup));
                summary.setText(activity.getString(R.string.gbstorage_sync_preview_counts, syncPlan.selectedCount, syncPlan.imagesToSync.size(), syncPlan.overwriteCount, syncPlan.skippedCount));
                reviewAdapter.updateSelection(syncPlan.overwriteHashes, syncPlan.duplicateMode);
                boolean hasItemsToSync = !syncPlan.imagesToSync.isEmpty();
                reviewListView.setVisibility(hasItemsToSync ? View.VISIBLE : View.GONE);
                reviewEmptyView.setVisibility(hasItemsToSync ? View.GONE : View.VISIBLE);
            };

            duplicateModeGroup.setOnCheckedChangeListener((group, checkedId) -> refreshReviewHolder[0].run());

            refreshReviewHolder[0].run();

            builder.setView(root);
            builder.setPositiveButton(activity.getString(R.string.gbstorage_sync_confirm), (dialog, which) -> {
                SyncPlan syncPlan = buildSyncPlan(previewItems, duplicates, getSelectedDuplicateMode(duplicateModeGroup));
                startTransfer(activity, syncPlan.imagesToSync, syncPlan.overwriteHashes, syncPlan.duplicateMode, timestampSource, normalizeVirtualPath(virtualPathField.getText() == null ? "" : virtualPathField.getText().toString()));
            });
            builder.setNegativeButton(activity.getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
            builder.show();
            return;
        }

        TextView noDuplicates = new TextView(activity);
        noDuplicates.setText(activity.getString(R.string.gbstorage_sync_duplicates_none));
        noDuplicates.setPadding(0, 0, 0, 16);
        root.addView(noDuplicates);

        ListView reviewListView = new ListView(activity);
        reviewListView.setDividerHeight(0);
        reviewListView.setFastScrollEnabled(true);
        reviewListView.setFastScrollAlwaysVisible(true);
        reviewListView.setVerticalScrollBarEnabled(true);
        reviewListView.setScrollbarFadingEnabled(false);
        ReviewListAdapter reviewAdapter = new ReviewListAdapter(activity, previewItems, Collections.emptySet());
        reviewListView.setAdapter(reviewAdapter);
        root.addView(reviewListView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        summary.setText(activity.getString(R.string.gbstorage_sync_preview_counts, previewItems.size(), previewItems.size(), 0, 0));

        builder.setView(root);
        builder.setPositiveButton(activity.getString(R.string.gbstorage_sync_confirm), (dialog, which) -> {
            List<GbcImage> imagesToSync = new ArrayList<>();
            for (SyncPreviewItem item : previewItems) {
                imagesToSync.add(item.image);
            }
            startTransfer(activity, imagesToSync, Collections.emptySet(), DUPLICATE_MODE_SKIP, timestampSource, normalizeVirtualPath(virtualPathField.getText() == null ? "" : virtualPathField.getText().toString()));
        });
        builder.setNegativeButton(activity.getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private static void showSyncPreviewDialog(Activity activity, List<SyncPreviewItem> previewItems, Set<String> overwriteHashes, String timestampSource) {
        List<SyncPreviewItem> itemsToSync = new ArrayList<>();
        for (SyncPreviewItem item : previewItems) {
            if (!item.duplicate || overwriteHashes.contains(item.image.getHashCode())) {
                itemsToSync.add(item);
            }
        }

        int selectedCount = previewItems.size();
        int overwriteCount = overwriteHashes.size();
        int skippedCount = selectedCount - itemsToSync.size();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.gbstorage_sync_preview_title));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 20, 40, 10);

        TextView summary = new TextView(activity);
        summary.setText(activity.getString(R.string.gbstorage_sync_preview_counts, selectedCount, itemsToSync.size(), overwriteCount, skippedCount));
        summary.setPadding(0, 0, 0, 20);
        root.addView(summary);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout listLayout = new LinearLayout(activity);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout);

        for (SyncPreviewItem item : itemsToSync) {
            String status = item.duplicate && overwriteHashes.contains(item.image.getHashCode())
                    ? activity.getString(R.string.gbstorage_sync_overwrite_label)
                    : activity.getString(R.string.gbstorage_sync_new_label);
            ViewGroup row = buildPreviewRow(activity, item, false, item.duplicate ? activity.getString(R.string.gbstorage_sync_duplicate_note) : null, status, null);
            listLayout.addView(row);
        }

        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        builder.setView(root);
        builder.setPositiveButton(activity.getString(R.string.gbstorage_sync_confirm), (dialog, which) -> {
            List<GbcImage> imagesToSync = new ArrayList<>();
            for (SyncPreviewItem item : itemsToSync) {
                imagesToSync.add(item.image);
            }
            startTransfer(activity, imagesToSync, overwriteHashes, DUPLICATE_MODE_OVERWRITE, timestampSource, "");
        });
        builder.setNegativeButton(activity.getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private static SyncPlan buildSyncPlan(List<SyncPreviewItem> previewItems, List<DuplicateMatch> duplicates, String duplicateMode) {
        Set<String> overwriteHashes = new HashSet<>();
        if (DUPLICATE_MODE_OVERWRITE.equals(duplicateMode)) {
            for (DuplicateMatch duplicate : duplicates) {
                overwriteHashes.add(duplicate.sourceHash);
            }
        }

        List<GbcImage> imagesToSync = new ArrayList<>();
        for (SyncPreviewItem item : previewItems) {
            if (!item.duplicate || !DUPLICATE_MODE_SKIP.equals(duplicateMode)) {
                imagesToSync.add(item.image);
            }
        }

        int selectedCount = previewItems.size();
        int overwriteCount = overwriteHashes.size();
        int skippedCount = selectedCount - imagesToSync.size();
        return new SyncPlan(imagesToSync, overwriteHashes, duplicateMode, selectedCount, overwriteCount, skippedCount);
    }

    private static RadioButton buildDuplicateModeButton(Context context, int labelResourceId, String duplicateMode) {
        RadioButton button = new RadioButton(context);
        button.setId(View.generateViewId());
        button.setText(context.getString(labelResourceId));
        button.setTag(duplicateMode);
        button.setPadding(0, 0, dp(context, 12), dp(context, 8));
        return button;
    }

    private static String getSelectedDuplicateMode(RadioGroup duplicateModeGroup) {
        View checkedView = duplicateModeGroup.findViewById(duplicateModeGroup.getCheckedRadioButtonId());
        Object tag = checkedView == null ? null : checkedView.getTag();
        return tag == null ? DUPLICATE_MODE_SKIP : String.valueOf(tag);
    }

    private static SyncPreviewItem findPreviewItem(List<SyncPreviewItem> previewItems, String sourceHash) {
        for (SyncPreviewItem item : previewItems) {
            if (item.image.getHashCode().equals(sourceHash)) {
                return item;
            }
        }
        return null;
    }

    private static List<SyncPreviewItem> buildPreviewItems(Context context, List<GbcImage> selectedImages, Set<String> duplicateHashes) {
        List<SyncPreviewItem> previewItems = new ArrayList<>(selectedImages.size());
        for (GbcImage gbcImage : selectedImages) {
            previewItems.add(new SyncPreviewItem(
                    gbcImage,
                    createThumbnailBitmap(context, gbcImage),
                    formatDisplayTimestamp(gbcImage.getCreationDate()),
                    duplicateHashes.contains(gbcImage.getHashCode())));
        }
        return previewItems;
    }

    private static ViewGroup buildPreviewRow(Context context, SyncPreviewItem item, boolean selectable, String subtitle, String status, CheckBox[] checkBoxHolder) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        ImageView thumbnailView = new ImageView(context);
        LinearLayout.LayoutParams thumbnailParams = new LinearLayout.LayoutParams(dp(context, PREVIEW_THUMBNAIL_WIDTH), dp(context, PREVIEW_THUMBNAIL_HEIGHT));
        thumbnailParams.rightMargin = dp(context, 12);
        thumbnailView.setLayoutParams(thumbnailParams);
        thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (item != null && item.thumbnail != null) {
            thumbnailView.setImageBitmap(item.thumbnail);
        }
        row.addView(thumbnailView);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(context);
        titleView.setText(item != null && item.image.getName() != null ? item.image.getName() : context.getString(R.string.menu_gbstorage));
        titleView.setTextColor(Color.BLACK);
        textColumn.addView(titleView);

        TextView hashView = new TextView(context);
        hashView.setTypeface(Typeface.MONOSPACE);
        hashView.setText(item != null ? item.image.getHashCode() : "");
        textColumn.addView(hashView);

        TextView createdView = new TextView(context);
        createdView.setText(item != null ? item.createdText : "");
        textColumn.addView(createdView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = new TextView(context);
            subtitleView.setText(subtitle);
            textColumn.addView(subtitleView);
        }

        if (!TextUtils.isEmpty(status)) {
            TextView statusView = new TextView(context);
            statusView.setText(status);
            statusView.setTextColor(colorForStatus(context, status));
            textColumn.addView(statusView);
        }

        if (selectable) {
            CheckBox checkBox = new CheckBox(context);
            checkBox.setText(activityLabelForCheckbox(status, context));
            checkBox.setPadding(0, dp(context, 6), 0, 0);
            checkBox.setChecked(false);
            textColumn.addView(checkBox);
            if (checkBoxHolder != null && checkBoxHolder.length > 0) {
                checkBoxHolder[0] = checkBox;
            }
        }

        row.addView(textColumn);

        return row;
    }

    private static String activityLabelForCheckbox(String status, Context context) {
        return context.getString(R.string.gbstorage_overwrite_existing);
    }

    private static int colorForStatus(Context context, String status) {
        if (context.getString(R.string.gbstorage_sync_overwrite_label).equals(status)) {
            return Color.parseColor("#B45309");
        }
        if (context.getString(R.string.gbstorage_sync_new_label).equals(status)) {
            return Color.parseColor("#166534");
        }
        if (context.getString(R.string.gbstorage_sync_duplicate_note).equals(status)) {
            return Color.parseColor("#6B7280");
        }
        return Color.BLACK;
    }

    private static Bitmap createThumbnailBitmap(Context context, GbcImage gbcImage) {
        byte[] imageBytes = resolveImageBytes(gbcImage);
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        int height = Utils.getGbcImageHeight(imageBytes);
        ImageCodec imageCodec = new ImageCodec(Utils.GB_CAMERA_IMAGE_WIDTH, height);
        GbcPalette palette = Utils.hashPalettes.get(gbcImage.getPaletteId());
        if (palette == null) {
            palette = Utils.hashPalettes.get("bw");
        }

        Bitmap bitmap = imageCodec.decodeWithPalette(palette.getPaletteColorsInt(), imageBytes, gbcImage.isInvertPalette());
        bitmap = Utils.rotateBitmap(bitmap, gbcImage);
        int scaledHeight = Math.max(1, Math.round((float) bitmap.getHeight() * PREVIEW_THUMBNAIL_WIDTH / bitmap.getWidth()));
        return Bitmap.createScaledBitmap(bitmap, PREVIEW_THUMBNAIL_WIDTH, scaledHeight, false);
    }

    private static Bitmap createGalleryBitmap(GbcImage gbcImage) throws IOException {
        byte[] imageBytes = resolveImageBytes(gbcImage);
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        return GalleryUtils.frameChange(
                gbcImage,
                gbcImage.getFrameId(),
                gbcImage.isInvertPalette(),
                gbcImage.isInvertFramePalette(),
                gbcImage.isLockFrame(),
                null);
    }

    private static byte[] resolveImageBytes(GbcImage gbcImage) {
        if (gbcImage.getImageBytes() != null && gbcImage.getImageBytes().length > 0) {
            byte[] imageBytes = Utils.ensureGbcImageHasBlackBorder(gbcImage.getImageBytes());
            gbcImage.setImageBytes(imageBytes);
            return imageBytes;
        }

        if (StaticValues.db == null) {
            return null;
        }

        ImageDataDao imageDataDao = StaticValues.db.imageDataDao();
        byte[] imageBytes = imageDataDao.getDataByImageId(gbcImage.getHashCode());
        imageBytes = Utils.ensureGbcImageHasBlackBorder(imageBytes);
        gbcImage.setImageBytes(imageBytes);
        return imageBytes;
    }

    private static String formatDisplayTimestamp(Date date) {
        Date safeDate = date == null ? new Date() : date;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(safeDate);
    }

    private static String normalizeVirtualPath(String virtualPath) {
        String normalized = virtualPath == null ? "" : virtualPath.trim();
        if (TextUtils.isEmpty(normalized)) {
            return "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void startTransfer(Activity activity, List<GbcImage> selectedImages, Set<String> overwriteHashes, String duplicateMode, String timestampSource, String virtualPath) {
        SyncProgressDialog progressDialog = new SyncProgressDialog(activity, activity.getString(R.string.gbstorage_sync_in_progress));
        progressDialog.show();

        EXECUTOR.execute(() -> {
            try {
                int total = selectedImages.size();
                int processed = 0;
                int importedTotal = 0;
                int skippedTotal = 0;
                long startTime = System.currentTimeMillis();

                for (int startIndex = 0; startIndex < selectedImages.size(); startIndex += BATCH_SIZE) {
                    int endIndex = Math.min(startIndex + BATCH_SIZE, selectedImages.size());
                    List<GbcImage> batch = selectedImages.subList(startIndex, endIndex);
                    int batchBase = startIndex;

                    HttpResult result = executeRequest(
                            resolveBaseUrl(StaticValues.gbStorageServerAddress) + "/api/images/bulk",
                            "POST",
                            StaticValues.gbStorageApiKey,
                            buildBulkRequest(batch, overwriteHashes, duplicateMode, timestampSource, virtualPath, processedInBatch -> {
                                int overallProcessed = batchBase + processedInBatch;
                                String status = activity.getString(R.string.gbstorage_sync_progress, overallProcessed, total);
                                String estimate = estimateRemaining(startTime, overallProcessed, total);
                                MAIN.post(() -> progressDialog.update(overallProcessed, total, status, estimate));
                            }));

                    if (result.responseCode < 200 || result.responseCode >= 300) {
                        throw new IOException(result.responseBody);
                    }

                    JSONObject responseJson = new JSONObject(result.responseBody);
                    importedTotal += responseJson.optInt("importedCount", batch.size());
                    skippedTotal += responseJson.optInt("skippedDuplicateCount", 0);
                    processed = endIndex;

                    int processedFinal = processed;
                    int totalFinal = total;
                    String status = activity.getString(R.string.gbstorage_sync_progress, processedFinal, totalFinal);
                    String estimate = estimateRemaining(startTime, processedFinal, totalFinal);
                    MAIN.post(() -> progressDialog.update(processedFinal, totalFinal, status, estimate));
                }

                markImagesWithStatus(selectedImages, GbcImage.GB_STORAGE_SYNCED);

                int finalImportedTotal = importedTotal;
                int finalSkippedTotal = skippedTotal;
                MAIN.post(() -> {
                    progressDialog.dismiss();
                    refreshGallerySyncIndicators();
                    showMessage(activity, activity.getString(R.string.gbstorage_sync_complete) + "\nImported: " + finalImportedTotal + "\nSkipped: " + finalSkippedTotal);
                });
            } catch (Exception exception) {
                MAIN.post(() -> {
                    progressDialog.dismiss();
                    showMessage(activity, activity.getString(R.string.gbstorage_sync_failed) + "\n" + exception.getMessage());
                });
            }
        });
    }

    private static JSONObject buildBulkRequest(List<GbcImage> batch, Set<String> overwriteHashes, String duplicateMode, String timestampSource, String virtualPath, ProgressUpdateCallback progressUpdateCallback) throws Exception {
        JSONObject payload = new JSONObject();
        JSONObject state = new JSONObject();
        JSONArray images = new JSONArray();
        long lastUpdate = System.currentTimeMillis() / 1000;
        state.put("lastUpdateUTC", lastUpdate);
        state.put("images", images);
        payload.put("state", state);

        for (int index = 0; index < batch.size(); index++) {
            GbcImage gbcImage = batch.get(index);
            String hashCode = gbcImage.getHashCode();
            byte[] imageBytes = resolveImageBytes(gbcImage);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IOException("Missing image data for " + gbcImage.getName());
            }
            String rawHex = Utils.bytesToHex(imageBytes);
            StringBuilder formattedHex = new StringBuilder(rawHex.length() + (rawHex.length() / 32));
            for (int i = 0; i < rawHex.length(); i++) {
                if (i > 0 && i % 32 == 0) {
                    formattedHex.append('\n');
                }
                formattedHex.append(rawHex.charAt(i));
            }

            payload.put(hashCode, GalleryUtils.encodeData(formattedHex.toString()));

            JSONObject imageObject = new JSONObject();
            imageObject.put("hash", hashCode);
            imageObject.put("created", formatTimestamp(gbcImage, timestampSource, index));
            imageObject.put("title", gbcImage.getName());
            imageObject.put("tags", new JSONArray(gbcImage.getTags()));
            imageObject.put("palette", gbcImage.getPaletteId());
            imageObject.put("framePalette", gbcImage.getFramePaletteId());
            imageObject.put("invertFramePalette", gbcImage.isInvertFramePalette());
            imageObject.put("frame", gbcImage.getFrameId());
            imageObject.put("invertPalette", gbcImage.isInvertPalette());
            imageObject.put("lockFrame", gbcImage.isLockFrame());
            imageObject.put("rotation", gbcImage.getRotation());

            JSONObject metaObject = new JSONObject();
            LinkedHashMap imageMetadata = gbcImage.getImageMetadata();
            if (imageMetadata != null) {
                for (Object keyObject : imageMetadata.keySet()) {
                    String key = String.valueOf(keyObject);
                    if ("frameIndex".equals(key)) {
                        continue;
                    }
                    Object value = imageMetadata.get(keyObject);
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Boolean) {
                        metaObject.put(key, (Boolean) value);
                    } else if ("isCopy".equals(key) || "cpuFast".equals(key)) {
                        metaObject.put(key, Boolean.parseBoolean(String.valueOf(value)));
                    } else {
                        metaObject.put(key, String.valueOf(value));
                    }
                }
            }
            imageObject.put("meta", metaObject);
            images.put(imageObject);

            if (progressUpdateCallback != null) {
                progressUpdateCallback.onProgress(index + 1);
            }
        }

        return new JSONObject()
                .put("payload", payload)
                .put("ownerId", "android-manager")
                .put("duplicateMode", duplicateMode)
                .put("overwriteSourceHashes", new JSONArray(new ArrayList<>(overwriteHashes)))
                .put("source", SOURCE)
            .put("sourceFormat", SOURCE_FORMAT)
            .put("virtualPath", TextUtils.isEmpty(virtualPath) ? JSONObject.NULL : virtualPath.trim());
    }

    private static String formatTimestamp(GbcImage gbcImage, String timestampSource, int index) {
        Date date;
        if ("current_time".equals(timestampSource)) {
            date = new Date(System.currentTimeMillis() + index);
        } else {
            date = gbcImage.getCreationDate() == null ? new Date() : gbcImage.getCreationDate();
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.getDefault());
        return dateFormat.format(date);
    }

    private static String estimateRemaining(long startTime, int processed, int total) {
        if (processed <= 0) {
            return "";
        }
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, total - processed);
        long estimatedMs = elapsed * remaining / processed;
        long seconds = estimatedMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "Estimated remaining: %02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        return String.format(Locale.getDefault(), "Estimated remaining: %02d:%02d", minutes, seconds % 60);
    }

    private static String resolveBaseUrl(String serverAddress) {
        String baseUrl = serverAddress.trim();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public static void refreshAllSyncStatuses(Activity activity) {
        if (StaticValues.db == null) {
            Utils.toast(activity, activity.getString(R.string.gbstorage_sync_failed));
            return;
        }
        if (TextUtils.isEmpty(StaticValues.gbStorageServerAddress) || TextUtils.isEmpty(StaticValues.gbStorageApiKey)) {
            Utils.toast(activity, activity.getString(R.string.gbstorage_test_failed));
            return;
        }

        List<GbcImage> allImages = Utils.gbcImagesList == null ? Collections.emptyList() : new ArrayList<>(Utils.gbcImagesList);
        if (allImages.isEmpty()) {
            showMessage(activity, activity.getString(R.string.no_images));
            return;
        }

        LoadingDialog loadingDialog = new LoadingDialog(activity, activity.getString(R.string.gbstorage_refresh_sync_status));
        loadingDialog.showDialog();

        EXECUTOR.execute(() -> {
            try {
                SyncStatusSummary summary = refreshSyncStatusesInternal(allImages);
                MAIN.post(() -> {
                    loadingDialog.dismissDialog();
                    refreshGallerySyncIndicators();
                    showMessage(activity, activity.getString(R.string.gbstorage_refresh_sync_status_complete, summary.syncedCount, summary.notSyncedCount, summary.totalCount));
                });
            } catch (Exception exception) {
                MAIN.post(() -> {
                    loadingDialog.dismissDialog();
                    showMessage(activity, activity.getString(R.string.gbstorage_sync_failed) + "\n" + exception.getMessage());
                });
            }
        });
    }

    private static SyncStatusSummary refreshSyncStatusesInternal(List<GbcImage> images) throws Exception {
        Set<String> syncedHashes = queryDuplicateHashes(images);
        int syncedCount = 0;
        int notSyncedCount = 0;
        for (GbcImage image : images) {
            int status = syncedHashes.contains(image.getHashCode()) ? GbcImage.GB_STORAGE_SYNCED : GbcImage.GB_STORAGE_SYNC_NOT_SYNCED;
            image.setGbStorageSyncStatus(status);
            StaticValues.db.imageDao().update(image);
            if (status == GbcImage.GB_STORAGE_SYNCED) {
                syncedCount++;
            } else {
                notSyncedCount++;
            }
        }
        return new SyncStatusSummary(images.size(), syncedCount, notSyncedCount);
    }

    private static Set<String> queryDuplicateHashes(List<GbcImage> images) throws Exception {
        Set<String> duplicateHashes = new HashSet<>();
        for (int startIndex = 0; startIndex < images.size(); startIndex += BATCH_SIZE) {
            int endIndex = Math.min(startIndex + BATCH_SIZE, images.size());
            JSONArray hashes = new JSONArray();
            for (GbcImage image : images.subList(startIndex, endIndex)) {
                hashes.put(image.getHashCode());
            }

            HttpResult duplicateResult = executeRequest(
                    resolveBaseUrl(StaticValues.gbStorageServerAddress) + "/api/images/duplicates/check",
                    "POST",
                    StaticValues.gbStorageApiKey,
                    new JSONObject().put("hashes", hashes));

            if (duplicateResult.responseCode < 200 || duplicateResult.responseCode >= 300) {
                throw new IOException(duplicateResult.responseBody);
            }

            JSONObject duplicateJson = new JSONObject(duplicateResult.responseBody);
            JSONArray duplicatesArray = duplicateJson.optJSONArray("duplicates");
            if (duplicatesArray == null) {
                continue;
            }
            for (int i = 0; i < duplicatesArray.length(); i++) {
                JSONObject duplicateObject = duplicatesArray.getJSONObject(i);
                duplicateHashes.add(duplicateObject.optString("sourceHash"));
            }
        }
        return duplicateHashes;
    }

    private static void applySyncStatusToHashes(List<GbcImage> images, Set<String> hashes, int status, boolean requireMembership) {
        if (StaticValues.db == null || images == null || images.isEmpty()) {
            return;
        }
        for (GbcImage image : images) {
            boolean matches = hashes.contains(image.getHashCode());
            if ((requireMembership && !matches) || (!requireMembership && !matches)) {
                continue;
            }
            image.setGbStorageSyncStatus(status);
            StaticValues.db.imageDao().update(image);
        }
    }

    private static void markImagesWithStatus(List<GbcImage> images, int status) {
        if (StaticValues.db == null || images == null) {
            return;
        }
        for (GbcImage image : images) {
            image.setGbStorageSyncStatus(status);
            StaticValues.db.imageDao().update(image);
        }
    }

    private static void refreshGallerySyncIndicators() {
        if (GalleryFragment.customGridViewAdapterImage != null) {
            GalleryFragment.customGridViewAdapterImage.notifyDataSetChanged();
        }
    }

    private static void showMessage(Context context, String message) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.gbstorage_test_dialog_title)
                .setMessage(message)
                .setPositiveButton(context.getString(R.string.dialog_close_button), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static HttpResult executeRequest(String url, String method, String apiKey, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Api-Key", apiKey.trim());
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int responseCode = connection.getResponseCode();
        InputStream inputStream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readStream(inputStream);
        return new HttpResult(responseCode, responseBody == null ? "" : responseBody);
    }

    private static String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static BinaryHttpResult executeBinaryRequest(String url, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "image/png");
        connection.setRequestProperty("X-Api-Key", apiKey.trim());
        int responseCode = connection.getResponseCode();
        InputStream inputStream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (inputStream != null) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
        }
        return new BinaryHttpResult(responseCode, outputStream.toByteArray());
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }

    private static boolean localContainsHash(String hash) {
        if (TextUtils.isEmpty(hash) || Utils.gbcImagesList == null) {
            return false;
        }
        for (GbcImage image : Utils.gbcImagesList) {
            if (hash.equals(image.getHashCode())) {
                return true;
            }
        }
        return false;
    }

    private static HashSet<String> jsonArrayToTags(JSONArray tagsJson) {
        HashSet<String> tags = new HashSet<>();
        if (tagsJson == null) {
            return tags;
        }
        for (int i = 0; i < tagsJson.length(); i++) {
            String tag = tagsJson.optString(i);
            if (!TextUtils.isEmpty(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static LinkedHashMap jsonObjectToMap(JSONObject jsonObject) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (jsonObject == null) {
            return map;
        }
        JSONArray names = jsonObject.names();
        if (names == null) {
            return map;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            map.put(key, jsonObject.opt(key));
        }
        return map;
    }

    private static String optStringOrDefault(JSONObject object, String key, String defaultValue) {
        String value = object.optString(key, defaultValue);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    private static String emptyToNull(String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }

    private static Date parseRemoteDate(String value) {
        if (TextUtils.isEmpty(value)) {
            return new Date();
        }
        String[] patterns = new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss:SSS"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(value);
            } catch (ParseException ignored) {
            }
        }
        return new Date();
    }

    private static byte[] decodeCompressedTileBytes(String rawData) throws Exception {
        if (TextUtils.isEmpty(rawData)) {
            return new byte[0];
        }
        byte[] compressed = rawData.getBytes(StandardCharsets.ISO_8859_1);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) {
                break;
            }
            outputStream.write(buffer, 0, count);
        }
        inflater.end();
        String inflated = outputStream.toString("UTF-8");
        String hex = inflated.replaceAll("[^0-9A-Fa-f]", "");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static final class HttpResult {
        final int responseCode;
        final String responseBody;

        HttpResult(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }
    }

    private static final class BinaryHttpResult {
        final int responseCode;
        final byte[] body;

        BinaryHttpResult(int responseCode, byte[] body) {
            this.responseCode = responseCode;
            this.body = body;
        }
    }
}