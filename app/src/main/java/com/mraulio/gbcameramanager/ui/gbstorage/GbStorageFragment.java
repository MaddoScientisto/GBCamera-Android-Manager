package com.mraulio.gbcameramanager.ui.gbstorage;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.gallery.CustomGridViewAdapterImage;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GbStorageFragment extends Fragment {
    private final SharedPreferences.Editor editor = StaticValues.sharedPreferences.edit();
    private final List<RemoteGbStorageImage> remoteImages = new ArrayList<>();
    private final List<RemoteGbStorageImage> visibleRemoteImages = new ArrayList<>();
    private final List<GbcImage> visibleImages = new ArrayList<>();
    private final List<Bitmap> visibleBitmaps = new ArrayList<>();
    private final List<Integer> selectedRemotePositions = new ArrayList<>();
    private final List<String> tagFilters = new ArrayList<>();
    private static final int SYNC_FILTER_ALL = 0;
    private static final int SYNC_FILTER_SYNCED = 1;
    private static final int SYNC_FILTER_UNSYNCED = 2;
    private GridView gridView;
    private TextView statusText;
    private TextView pageText;
    private CustomGridViewAdapterImage gridAdapter;
    private DisplayMetrics displayMetrics;
    private int pageSize = 20;
    private int currentPageIndex = 0;
    private int totalCount = 0;
    private String nextCursor;
    private String sort = "created-desc";
    private int syncFilter = SYNC_FILTER_ALL;
    private int thumbnailLoadGeneration = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        StaticValues.currentFragment = StaticValues.CURRENT_FRAGMENT.GBSTORAGE;
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_gbstorage, container, false);
        displayMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        statusText = view.findViewById(R.id.tvGbStorageStatus);
        pageText = view.findViewById(R.id.tvGbStoragePage);
        gridView = view.findViewById(R.id.gridViewGbStorage);
        Button firstButton = view.findViewById(R.id.btnGbStorageFirstPage);
        Button previousButton = view.findViewById(R.id.btnGbStoragePrevPage);
        Button nextButton = view.findViewById(R.id.btnGbStorageNextPage);
        Button lastButton = view.findViewById(R.id.btnGbStorageLastPage);

        setTooltip(firstButton, R.string.tooltip_first_page);
        setTooltip(previousButton, R.string.tooltip_previous_page);
        setTooltip(pageText, R.string.tooltip_page_selector);
        setTooltip(nextButton, R.string.tooltip_next_page);
        setTooltip(lastButton, R.string.tooltip_last_page);

        firstButton.setOnClickListener(v -> goToPage(0));
        previousButton.setOnClickListener(v -> {
            if (currentPageIndex > 0) {
                goToPage(currentPageIndex - 1);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (nextCursor != null) {
                goToPage(currentPageIndex + 1);
            }
        });
        lastButton.setOnClickListener(v -> goToLastPage());
        pageText.setOnClickListener(v -> showPageDialog());
        gridView.setOnItemClickListener((parent, itemView, position, id) -> openRemoteImage(position));
        gridView.setOnItemLongClickListener((parent, itemView, position, id) -> {
            toggleSelection(position);
            return true;
        });

        loadPage(0);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.gbstorage_menu, menu);
        MenuItem downloadSelectedItem = menu.findItem(R.id.action_gbstorage_download_selected);
        if (downloadSelectedItem != null) {
            downloadSelectedItem.setVisible(!selectedRemotePositions.isEmpty());
        }
        updateSelectionFab();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_gbstorage_settings:
                showSettingsDialog();
                return true;
            case R.id.action_gbstorage_filter:
                showFilterDialog();
                return true;
            case R.id.action_gbstorage_sort:
                showSortDialog();
                return true;
            case R.id.action_gbstorage_refresh:
                resetAndLoad();
                return true;
            case R.id.action_gbstorage_download_selected:
                downloadSelectedImages();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadPage(int pageIndex) {
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        if (pageIndex < 0 || (totalCount > 0 && pageIndex >= totalPages)) {
            return;
        }
        thumbnailLoadGeneration++;
        currentPageIndex = pageIndex;
        statusText.setText(R.string.gbstorage_remote_loading);
        GbStorageSyncManager.fetchRemotePage(requireContext(), pageIndex + 1, pageSize, sort, tagFilters, result -> {
            if (!result.success) {
                Utils.toast(requireContext(), result.message);
                statusText.setText(result.message);
                return;
            }
            remoteImages.clear();
            remoteImages.addAll(result.images);
            selectedRemotePositions.clear();
            nextCursor = result.nextCursor;
            totalCount = result.totalCount;
            updateGrid();
            loadVisibleThumbnails(thumbnailLoadGeneration);
        });
    }

    private void updateGrid() {
        visibleRemoteImages.clear();
        visibleImages.clear();
        visibleBitmaps.clear();
        for (RemoteGbStorageImage remoteImage : remoteImages) {
            if (!passesSyncFilter(remoteImage)) {
                continue;
            }
            visibleRemoteImages.add(remoteImage);
            visibleImages.add(remoteImage.image);
            visibleBitmaps.add(remoteImage.previewBitmap == null ? createPlaceholderBitmap() : remoteImage.previewBitmap);
        }
        gridAdapter = new CustomGridViewAdapterImage(requireContext(), R.layout.row_items, visibleImages, visibleBitmaps, false, true, !selectedRemotePositions.isEmpty(), selectedRemotePositions, true);
        gridView.setAdapter(gridAdapter);
        updateStatusText();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        pageText.setText((currentPageIndex + 1) + " / " + totalPages);
    }

    private void updateStatusText() {
        if (remoteImages.isEmpty()) {
            statusText.setText(R.string.gbstorage_remote_empty);
            return;
        }
        StringBuilder builder = new StringBuilder(getString(R.string.gbstorage_remote_total, totalCount));
        if (syncFilter != SYNC_FILTER_ALL) {
            builder.append(" | ").append(getString(R.string.gbstorage_remote_showing, visibleRemoteImages.size()));
        }
        if (!selectedRemotePositions.isEmpty()) {
            builder.append("  ").append(getString(R.string.selected_images)).append(selectedRemotePositions.size());
        }
        statusText.setText(builder.toString());
    }

    private boolean passesSyncFilter(RemoteGbStorageImage remoteImage) {
        if (syncFilter == SYNC_FILTER_SYNCED) {
            return remoteImage.image.getGbStorageSyncStatus() == GbcImage.GB_STORAGE_SYNCED;
        }
        if (syncFilter == SYNC_FILTER_UNSYNCED) {
            return remoteImage.image.getGbStorageSyncStatus() == GbcImage.GB_STORAGE_SYNC_NOT_SYNCED;
        }
        return true;
    }

    private void loadVisibleThumbnails(int generation) {
        for (RemoteGbStorageImage remoteImage : visibleRemoteImages) {
            if (remoteImage.previewBitmap != null) {
                continue;
            }
            GbStorageSyncManager.fetchRemotePreview(requireContext(), remoteImage, result -> {
                if (generation != thumbnailLoadGeneration || !result.success || result.bitmap == null) {
                    return;
                }
                for (int i = 0; i < visibleRemoteImages.size(); i++) {
                    RemoteGbStorageImage visibleRemoteImage = visibleRemoteImages.get(i);
                    if (visibleRemoteImage.id.equals(result.id)) {
                        visibleRemoteImage.previewBitmap = result.bitmap;
                        visibleBitmaps.set(i, result.bitmap);
                        if (gridAdapter != null) {
                            gridAdapter.notifyDataSetChanged();
                        }
                        break;
                    }
                }
            });
        }
    }

    private void resetAndLoad() {
        currentPageIndex = 0;
        nextCursor = null;
        selectedRemotePositions.clear();
        loadPage(0);
    }

    private void refreshCurrentPage() {
        selectedRemotePositions.clear();
        loadPage(currentPageIndex);
    }

    private void goToPage(int pageIndex) {
        if (pageIndex == currentPageIndex) {
            return;
        }
        loadPage(pageIndex);
    }

    private void goToLastPage() {
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        int lastPage = totalPages - 1;
        if (lastPage <= currentPageIndex) {
            return;
        }
        loadPage(lastPage);
    }

    private void showPageDialog() {
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentPageIndex + 1));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.page_selector_dialog)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int page = Math.max(1, Math.min(totalPages, Integer.parseInt(input.getText().toString())));
                        loadPage(page - 1);
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void openRemoteImage(int position) {
        if (!selectedRemotePositions.isEmpty()) {
            toggleSelection(position);
            return;
        }
        if (position < 0 || position >= visibleRemoteImages.size()) {
            return;
        }
        LoadingDialog loadingDialog = new LoadingDialog(requireContext(), getString(R.string.loading));
        loadingDialog.showDialog();
        GbStorageSyncManager.fetchRemoteImage(requireContext(), visibleRemoteImages.get(position), result -> {
            loadingDialog.dismissDialog();
            if (!result.success) {
                Utils.toast(requireContext(), result.message);
                return;
            }
            visibleRemoteImages.set(position, result.remoteImage);
            replaceRemoteImage(result.remoteImage);
            new RemoteImageDialog(requireActivity(), new ArrayList<>(visibleRemoteImages), position, displayMetrics, this::refreshCurrentPage).show();
        });
    }

    private void replaceRemoteImage(RemoteGbStorageImage replacement) {
        for (int i = 0; i < remoteImages.size(); i++) {
            if (remoteImages.get(i).id.equals(replacement.id)) {
                remoteImages.set(i, replacement);
                break;
            }
        }
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= visibleRemoteImages.size()) {
            return;
        }
        if (selectedRemotePositions.contains(position)) {
            selectedRemotePositions.remove(Integer.valueOf(position));
        } else {
            selectedRemotePositions.add(position);
        }
        updateGrid();
        updateSelectionFab();
        requireActivity().invalidateOptionsMenu();
    }

    private void clearSelection() {
        selectedRemotePositions.clear();
        updateGrid();
        updateSelectionFab();
        requireActivity().invalidateOptionsMenu();
    }

    private void updateSelectionFab() {
        if (StaticValues.fab == null) {
            return;
        }
        if (selectedRemotePositions.isEmpty()) {
            StaticValues.fab.hide();
            return;
        }
        StaticValues.fab.show();
        StaticValues.fab.setOnClickListener(v -> clearSelection());
    }

    private void downloadSelectedImages() {
        if (selectedRemotePositions.isEmpty()) {
            Utils.toast(requireContext(), getString(R.string.no_selected));
            return;
        }
        List<RemoteGbStorageImage> selectedImages = new ArrayList<>();
        for (int position : selectedRemotePositions) {
            if (position >= 0 && position < visibleRemoteImages.size()) {
                selectedImages.add(visibleRemoteImages.get(position));
            }
        }
        if (selectedImages.isEmpty()) {
            Utils.toast(requireContext(), getString(R.string.no_selected));
            return;
        }
        LoadingDialog loadingDialog = new LoadingDialog(requireContext(), getString(R.string.gbstorage_remote_sync_selected));
        loadingDialog.showDialog();
        downloadSelectedImageAt(selectedImages, 0, 0, 0, loadingDialog);
    }

    private void downloadSelectedImageAt(List<RemoteGbStorageImage> selectedImages, int index, int downloaded, int skipped, LoadingDialog loadingDialog) {
        if (index >= selectedImages.size()) {
            loadingDialog.dismissDialog();
            Utils.toast(requireContext(), getString(R.string.gbstorage_remote_sync_selected_done, downloaded, skipped));
            clearSelection();
            refreshCurrentPage();
            return;
        }
        loadingDialog.setLoadingDialogText((index + 1) + " / " + selectedImages.size());
        GbStorageSyncManager.downloadRemoteImage(requireContext(), selectedImages.get(index), result -> {
            boolean skippedDuplicate = !result.success && getString(R.string.image_exists).equals(result.message);
            downloadSelectedImageAt(selectedImages, index + 1, downloaded + (result.success ? 1 : 0), skipped + (skippedDuplicate ? 1 : 0), loadingDialog);
        });
    }

    private void showSortDialog() {
        String[] values = new String[]{"created-desc", "created-asc", "imported-desc", "imported-asc", "updated-desc", "updated-asc", "name-asc", "name-desc", "name-natural-asc", "name-natural-desc"};
        int checked = Math.max(0, Arrays.asList(values).indexOf(sort));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.menu_sort)
                .setSingleChoiceItems(values, checked, (dialog, which) -> {
                    sort = values[which];
                    dialog.dismiss();
                    resetAndLoad();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showFilterDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);
        EditText tagsInput = new EditText(requireContext());
        tagsInput.setHint(R.string.gbstorage_remote_tag_filter);
        tagsInput.setSingleLine(false);
        tagsInput.setText(joinTags());
        EditText pageSizeInput = new EditText(requireContext());
        pageSizeInput.setHint(R.string.gbstorage_remote_page_size);
        pageSizeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageSizeInput.setText(String.valueOf(pageSize));
        RadioGroup syncFilterGroup = new RadioGroup(requireContext());
        syncFilterGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton allButton = new RadioButton(requireContext());
        allButton.setText(R.string.gbstorage_remote_sync_filter_all);
        allButton.setId(View.generateViewId());
        RadioButton syncedButton = new RadioButton(requireContext());
        syncedButton.setText(R.string.gbstorage_remote_sync_filter_synced);
        syncedButton.setId(View.generateViewId());
        RadioButton unsyncedButton = new RadioButton(requireContext());
        unsyncedButton.setText(R.string.gbstorage_remote_sync_filter_unsynced);
        unsyncedButton.setId(View.generateViewId());
        syncFilterGroup.addView(allButton);
        syncFilterGroup.addView(syncedButton);
        syncFilterGroup.addView(unsyncedButton);
        if (syncFilter == SYNC_FILTER_SYNCED) {
            syncedButton.setChecked(true);
        } else if (syncFilter == SYNC_FILTER_UNSYNCED) {
            unsyncedButton.setChecked(true);
        } else {
            allButton.setChecked(true);
        }
        layout.addView(tagsInput);
        layout.addView(pageSizeInput);
        layout.addView(syncFilterGroup);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gbstorage_remote_filters)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    tagFilters.clear();
                    String[] parts = tagsInput.getText().toString().split(",");
                    for (String part : parts) {
                        String tag = part.trim();
                        if (!tag.isEmpty()) {
                            tagFilters.add(tag);
                        }
                    }
                    try {
                        pageSize = Math.max(1, Integer.parseInt(pageSizeInput.getText().toString()));
                    } catch (NumberFormatException ignored) {
                        pageSize = 20;
                    }
                    int checkedSyncFilter = syncFilterGroup.getCheckedRadioButtonId();
                    if (checkedSyncFilter == syncedButton.getId()) {
                        syncFilter = SYNC_FILTER_SYNCED;
                    } else if (checkedSyncFilter == unsyncedButton.getId()) {
                        syncFilter = SYNC_FILTER_UNSYNCED;
                    } else {
                        syncFilter = SYNC_FILTER_ALL;
                    }
                    resetAndLoad();
                })
                .setNeutralButton(R.string.gbstorage_remote_clear_filters, (dialog, which) -> {
                    tagFilters.clear();
                    pageSize = 20;
                    syncFilter = SYNC_FILTER_ALL;
                    resetAndLoad();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String joinTags() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tagFilters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(tagFilters.get(i));
        }
        return builder.toString();
    }

    private Bitmap createPlaceholderBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
    }

    private void showSettingsDialog() {
        View settingsView = getLayoutInflater().inflate(R.layout.fragment_gbstorage_settings, null, false);
        EditText serverAddress = settingsView.findViewById(R.id.etGbStorageServerAddress);
        EditText apiKey = settingsView.findViewById(R.id.etGbStorageApiKey);
        Spinner timestampSource = settingsView.findViewById(R.id.spGbStorageTimestampSource);
        CheckBox showSyncIcons = settingsView.findViewById(R.id.cbGbStorageShowSyncIcons);
        Button saveButton = settingsView.findViewById(R.id.btnGbStorageSave);
        Button refreshSyncStatusButton = settingsView.findViewById(R.id.btnGbStorageRefreshSyncStatus);
        Button testButton = settingsView.findViewById(R.id.btnGbStorageTestConnection);

        serverAddress.setText(StaticValues.gbStorageServerAddress);
        apiKey.setText(StaticValues.gbStorageApiKey);
        showSyncIcons.setChecked(StaticValues.showGbStorageSyncIcons);

        List<String> timestampOptions = new ArrayList<>();
        timestampOptions.add(getString(R.string.gbstorage_creation_date));
        timestampOptions.add(getString(R.string.gbstorage_current_time));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, timestampOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timestampSource.setAdapter(spinnerAdapter);
        timestampSource.setSelection("current_time".equals(StaticValues.gbStorageTimestampSource) ? 1 : 0);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gbstorage_settings)
                .setView(settingsView)
                .setNegativeButton(R.string.dialog_close_button, (dialogInterface, which) -> dialogInterface.dismiss())
                .create();

        saveButton.setOnClickListener(v -> saveSettings(serverAddress.getText().toString(), apiKey.getText().toString(), timestampSource.getSelectedItemPosition(), showSyncIcons.isChecked()));
        refreshSyncStatusButton.setOnClickListener(v -> {
            saveSettings(serverAddress.getText().toString(), apiKey.getText().toString(), timestampSource.getSelectedItemPosition(), showSyncIcons.isChecked());
            GbStorageSyncManager.refreshAllSyncStatuses(requireActivity());
        });
        testButton.setOnClickListener(v -> {
            saveSettings(serverAddress.getText().toString(), apiKey.getText().toString(), timestampSource.getSelectedItemPosition(), showSyncIcons.isChecked());
            LoadingDialog loadingDialog = new LoadingDialog(requireContext(), getString(R.string.loading));
            loadingDialog.showDialog();
            GbStorageSyncManager.testConnection(requireContext(), StaticValues.gbStorageServerAddress, StaticValues.gbStorageApiKey, result -> {
                loadingDialog.dismissDialog();
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.gbstorage_test_dialog_title)
                        .setMessage(result.getDetails())
                        .setPositiveButton(getString(R.string.dialog_close_button), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface resultDialog, int which) {
                                resultDialog.dismiss();
                            }
                        })
                        .show();
            });
        });
        dialog.show();
    }

    private void saveSettings(String serverAddress, String apiKey, int timestampSelection, boolean showSyncIcons) {
        String normalizedServerAddress = serverAddress == null ? "" : serverAddress.trim();
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        StaticValues.gbStorageServerAddress = normalizedServerAddress;
        StaticValues.gbStorageApiKey = normalizedApiKey;
        StaticValues.gbStorageTimestampSource = timestampSelection == 1 ? "current_time" : "creation_date";
        StaticValues.showGbStorageSyncIcons = showSyncIcons;
        editor.putString("gbstorage_server_address", normalizedServerAddress);
        editor.putString("gbstorage_api_key", normalizedApiKey);
        editor.putString("gbstorage_timestamp_source", StaticValues.gbStorageTimestampSource);
        editor.putBoolean("gbstorage_show_sync_icons", showSyncIcons);
        editor.apply();
        Utils.toast(requireContext(), getString(R.string.gbstorage_save_settings));
    }

    private void setTooltip(View view, int resId) {
        String tooltip = getString(resId);
        TooltipCompat.setTooltipText(view, tooltip);
        view.setContentDescription(tooltip);
    }
}
