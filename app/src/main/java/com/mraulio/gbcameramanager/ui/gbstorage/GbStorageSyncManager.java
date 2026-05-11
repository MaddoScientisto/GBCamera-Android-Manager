package com.mraulio.gbcameramanager.ui.gbstorage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.db.ImageDataDao;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcPalette;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.gallery.GalleryUtils;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GbStorageSyncManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String SOURCE = "GBCamera Android Manager";
    private static final String SOURCE_FORMAT = "android-manager-v1";
    private static final int BATCH_SIZE = 20;
    private static final int PREVIEW_THUMBNAIL_WIDTH = 80;
    private static final int PREVIEW_THUMBNAIL_HEIGHT = 72;

    private GbStorageSyncManager() {
    }

    public interface ConnectionResultCallback {
        void onResult(ConnectionResult result);
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
        final int selectedCount;
        final int overwriteCount;
        final int skippedCount;

        SyncPlan(List<GbcImage> imagesToSync, Set<String> overwriteHashes, int selectedCount, int overwriteCount, int skippedCount) {
            this.imagesToSync = imagesToSync;
            this.overwriteHashes = overwriteHashes;
            this.selectedCount = selectedCount;
            this.overwriteCount = overwriteCount;
            this.skippedCount = skippedCount;
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

                MAIN.post(() -> {
                    loadingDialog.dismissDialog();
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
        }

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout contentLayout = new LinearLayout(activity);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout);

        List<CheckBox> keepBoxes = new ArrayList<>();
        if (duplicates.isEmpty()) {
            TextView noDuplicates = new TextView(activity);
            noDuplicates.setText(activity.getString(R.string.gbstorage_sync_duplicates_none));
            noDuplicates.setPadding(0, 0, 0, 16);
            contentLayout.addView(noDuplicates);
        } else {
            for (DuplicateMatch duplicate : duplicates) {
                SyncPreviewItem previewItem = findPreviewItem(previewItems, duplicate.sourceHash);
                CheckBox[] checkBoxHolder = new CheckBox[1];
                ViewGroup row = buildPreviewRow(activity, previewItem, true, activity.getString(R.string.gbstorage_sync_duplicate_note), activity.getString(R.string.gbstorage_sync_overwrite_label), checkBoxHolder);
                keepBoxes.add(checkBoxHolder[0]);
                contentLayout.addView(row);
            }
        }

        TextView reviewHeader = new TextView(activity);
        reviewHeader.setText(activity.getString(R.string.gbstorage_sync_preview_title));
        reviewHeader.setPadding(0, 24, 0, 12);
        reviewHeader.setTextColor(Color.BLACK);
        contentLayout.addView(reviewHeader);

        LinearLayout reviewListLayout = new LinearLayout(activity);
        reviewListLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.addView(reviewListLayout);

        final Runnable refreshReview = () -> {
            SyncPlan syncPlan = buildSyncPlan(previewItems, duplicates, keepBoxes);
            summary.setText(activity.getString(R.string.gbstorage_sync_preview_counts, syncPlan.selectedCount, syncPlan.imagesToSync.size(), syncPlan.overwriteCount, syncPlan.skippedCount));

            reviewListLayout.removeAllViews();
            if (syncPlan.imagesToSync.isEmpty()) {
                TextView emptyView = new TextView(activity);
                emptyView.setText(activity.getString(R.string.gbstorage_sync_duplicates_none));
                reviewListLayout.addView(emptyView);
                return;
            }

            for (SyncPreviewItem item : previewItems) {
                if (!item.duplicate || syncPlan.overwriteHashes.contains(item.image.getHashCode())) {
                    String status = item.duplicate && syncPlan.overwriteHashes.contains(item.image.getHashCode())
                            ? activity.getString(R.string.gbstorage_sync_overwrite_label)
                            : activity.getString(R.string.gbstorage_sync_new_label);
                    ViewGroup row = buildPreviewRow(activity, item, false, null, status, null);
                    reviewListLayout.addView(row);
                }
            }
        };

        if (!keepBoxes.isEmpty()) {
            for (CheckBox keepBox : keepBoxes) {
                keepBox.setOnCheckedChangeListener((buttonView, isChecked) -> refreshReview.run());
            }
        }

        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        refreshReview.run();

        builder.setView(root);
        builder.setPositiveButton(activity.getString(R.string.gbstorage_sync_confirm), (dialog, which) -> {
            SyncPlan syncPlan = buildSyncPlan(previewItems, duplicates, keepBoxes);
            startTransfer(activity, syncPlan.imagesToSync, syncPlan.overwriteHashes, timestampSource, normalizeVirtualPath(virtualPathField.getText() == null ? "" : virtualPathField.getText().toString()));
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
            startTransfer(activity, imagesToSync, overwriteHashes, timestampSource, "");
        });
        builder.setNegativeButton(activity.getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private static SyncPlan buildSyncPlan(List<SyncPreviewItem> previewItems, List<DuplicateMatch> duplicates, List<CheckBox> keepBoxes) {
        Set<String> overwriteHashes = new HashSet<>();
        for (int i = 0; i < duplicates.size(); i++) {
            if (keepBoxes.get(i).isChecked()) {
                overwriteHashes.add(duplicates.get(i).sourceHash);
            }
        }

        List<GbcImage> imagesToSync = new ArrayList<>();
        for (SyncPreviewItem item : previewItems) {
            if (!item.duplicate || overwriteHashes.contains(item.image.getHashCode())) {
                imagesToSync.add(item.image);
            }
        }

        int selectedCount = previewItems.size();
        int overwriteCount = overwriteHashes.size();
        int skippedCount = selectedCount - imagesToSync.size();
        return new SyncPlan(imagesToSync, overwriteHashes, selectedCount, overwriteCount, skippedCount);
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

        int height = Math.max(1, imageBytes.length / 40);
        ImageCodec imageCodec = new ImageCodec(160, height);
        GbcPalette palette = Utils.hashPalettes.get(gbcImage.getPaletteId());
        if (palette == null) {
            palette = Utils.hashPalettes.get("bw");
        }

        Bitmap bitmap = imageCodec.decodeWithPalette(palette.getPaletteColorsInt(), imageBytes, gbcImage.isInvertPalette());
        bitmap = Utils.rotateBitmap(bitmap, gbcImage);
        int scaledHeight = Math.max(1, Math.round((float) bitmap.getHeight() * PREVIEW_THUMBNAIL_WIDTH / bitmap.getWidth()));
        return Bitmap.createScaledBitmap(bitmap, PREVIEW_THUMBNAIL_WIDTH, scaledHeight, false);
    }

    private static byte[] resolveImageBytes(GbcImage gbcImage) {
        if (gbcImage.getImageBytes() != null && gbcImage.getImageBytes().length > 0) {
            return gbcImage.getImageBytes();
        }

        if (StaticValues.db == null) {
            return null;
        }

        ImageDataDao imageDataDao = StaticValues.db.imageDataDao();
        byte[] imageBytes = imageDataDao.getDataByImageId(gbcImage.getHashCode());
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

    private static void startTransfer(Activity activity, List<GbcImage> selectedImages, Set<String> overwriteHashes, String timestampSource, String virtualPath) {
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
                            buildBulkRequest(batch, overwriteHashes, timestampSource, virtualPath, processedInBatch -> {
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

                int finalImportedTotal = importedTotal;
                int finalSkippedTotal = skippedTotal;
                MAIN.post(() -> {
                    progressDialog.dismiss();
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

    private static JSONObject buildBulkRequest(List<GbcImage> batch, Set<String> overwriteHashes, String timestampSource, String virtualPath, ProgressUpdateCallback progressUpdateCallback) throws Exception {
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
            byte[] imageBytes = gbcImage.getImageBytes();
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
                .put("skipDuplicates", true)
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

    private static final class HttpResult {
        final int responseCode;
        final String responseBody;

        HttpResult(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }
    }
}