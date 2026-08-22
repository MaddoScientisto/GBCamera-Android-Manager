package com.mraulio.gbcameramanager.ui.extraGallery;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.averageImages;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.mediaScanner;
import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.utils.StaticValues.imagesPage;
import static com.mraulio.gbcameramanager.utils.StaticValues.showEditMenuButton;
import static com.mraulio.gbcameramanager.utils.Utils.IMAGES_FOLDER;
import static com.mraulio.gbcameramanager.utils.Utils.showNotification;
import static com.mraulio.gbcameramanager.utils.Utils.toast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mraulio.gbcameramanager.R;

import com.mraulio.gbcameramanager.ui.gallery.GalleryUtils;
import com.mraulio.gbcameramanager.ui.gallery.RgbUtils;
import com.mraulio.gbcameramanager.utils.UnicodeExifInterface;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pl.droidsonroids.gif.GifDrawable;

public class ExtraGalleryFragment extends Fragment implements RgbUtils.OnRgbSaved {
    HashMap<File, Bitmap> loadedFilesBitmap = new HashMap<>();
    public static ExtraGalleryFragment egf;
    List<File> fileList;
    private RecyclerView recyclerView;
    Switch swHdr, swRgb, swGif, swCollage, swFusion, swPaper;
    Button btnFirstPage, btnLastPage, btnPrevPage, btnNextPage;
    TextView tvPage;
    public static boolean showInfoExtra;

    public static boolean selectionModeExtra = false;
    public static LinkedHashSet<Integer> selectedFilesIndex = new LinkedHashSet<>();
    private int page = 0, lastPage, globalImageIndex;
    private int itemsPage = imagesPage;
    ImageAdapter imageAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        egf = this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        StaticValues.currentFragment = StaticValues.CURRENT_FRAGMENT.EXTRA_GALLERY;
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_extra_gallery, container, false);

        btnFirstPage = view.findViewById(R.id.btnFirstPage);
        btnLastPage = view.findViewById(R.id.btnLastPage);
        btnPrevPage = view.findViewById(R.id.btnPrevPage);
        btnNextPage = view.findViewById(R.id.btnNextPage);
        tvPage = view.findViewById(R.id.tv_page);

        swHdr = view.findViewById(R.id.sw_hdr);
        swRgb = view.findViewById(R.id.sw_rgb);
        swGif = view.findViewById(R.id.sw_gif);
        swCollage = view.findViewById(R.id.sw_collage);
        swFusion = view.findViewById(R.id.sw_fusion);
        swPaper = view.findViewById(R.id.sw_paper);

        fileList = loadFilesFromDirectory(IMAGES_FOLDER);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));

        recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(getContext(), recyclerView, new RecyclerViewItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (page != lastPage) {
                    globalImageIndex = position + (page * itemsPage);
                } else {
                    globalImageIndex = fileList.size() - (itemsPage - position);
                }
                if (selectionModeExtra) {

                    if (selectedFilesIndex.contains(globalImageIndex)) {
                        selectedFilesIndex.remove(globalImageIndex);
                    } else {
                        selectedFilesIndex.add(globalImageIndex);
                    }
                    if (selectedFilesIndex.size() == 0) {
                        hideSelectionOptionsExtra(getActivity());
                    }
                    for (int i : selectedFilesIndex) {
                        System.out.println(i);
                    }
                    imageAdapter.notifyDataSetChanged();
                } else {
                    if (position != RecyclerView.NO_POSITION) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        LayoutInflater inflater = LayoutInflater.from(getContext());
                        View dialogView = inflater.inflate(R.layout.dialog_extra, null);
                        builder.setView(dialogView);

                        ImageView imageView = dialogView.findViewById(R.id.imageView);

                        if (isGif(fileList.get(globalImageIndex))) {
                            try {
                                InputStream inputStream = new FileInputStream(fileList.get(globalImageIndex));

                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                                byte[] buffer = new byte[1024];
                                int length;
                                while ((length = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, length);
                                }
                                byte[] byteArray = outputStream.toByteArray();

                                inputStream.close();
                                outputStream.close();

                                GifDrawable gifDrawable = new GifDrawable(byteArray);

                                imageView.setImageDrawable(gifDrawable);

                                inputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            imageView.setImageBitmap(getBitmapFromFile(fileList.get(globalImageIndex)));
                        }

                        Button btnClose = dialogView.findViewById(R.id.btn_close_extra);
                        Button btnShare = dialogView.findViewById(R.id.btn_share_extra);
                        Button btnSave = dialogView.findViewById(R.id.btn_save_extra);
                        Button btnDelete = dialogView.findViewById(R.id.btn_delete_extra);
                        btnShare.setVisibility(VISIBLE);
                        btnSave.setVisibility(VISIBLE);
                        btnDelete.setVisibility(VISIBLE);
                        setPreviewActionTooltips(btnSave, btnShare);

                        AlertDialog dialog = builder.create();

                        btnClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });

                        btnShare.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showExtraExportOptionsDialog(fileList.get(globalImageIndex), true);
                            }
                        });

                        btnSave.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showExtraExportOptionsDialog(fileList.get(globalImageIndex), false);
                            }
                        });

                        btnDelete.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                File file = fileList.get(globalImageIndex);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                                builder.setTitle(getString(R.string.sure_delete_sav) + " " + file.getName())
                                        .setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface deleteDialog, int id) {

                                                if (file.delete()) {
                                                    toast(getContext(), getString(R.string.deleted_sav) + file.getName());
                                                } else {
                                                    toast(getContext(), getString(R.string.toast_couldnt_delete_sav));
                                                }

                                                deleteDialog.dismiss();
                                                dialog.dismiss();
                                                hideSelectionOptionsExtra(getActivity());
                                            }
                                        })

                                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface deleteDialog, int id) {
                                                deleteDialog.dismiss();
                                            }
                                        });

                                AlertDialog alertDialog = builder.create();
                                alertDialog.show();

                            }
                        });

                        dialog.show();
                    }
                }

            }
        }, new RecyclerViewItemClickListener.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(View view, int position) {
                if (page != lastPage) {
                    globalImageIndex = position + (page * itemsPage);
                } else {
                    globalImageIndex = fileList.size() - (itemsPage - position);
                }
                if (!selectionModeExtra) {
                    selectionModeExtra = true;
                    StaticValues.fab.show();

                    if (selectedFilesIndex.contains(globalImageIndex)) {
                        selectedFilesIndex.remove(globalImageIndex);
                    } else {
                        selectedFilesIndex.add(globalImageIndex);
                    }
                } else {
                    int firstImage = Collections.min(selectedFilesIndex);
                    int lastImage = Collections.max(selectedFilesIndex);

                    selectedFilesIndex.clear();
                    selectedFilesIndex.add(globalImageIndex);

                    if (firstImage < globalImageIndex) {
                        selectedFilesIndex.clear();
                        for (int i = firstImage; i < globalImageIndex; i++) {
                            if (!selectedFilesIndex.contains(i)) {
                                selectedFilesIndex.add(i);
                            }
                        }
                        selectedFilesIndex.add(globalImageIndex);
                    } else if (firstImage > globalImageIndex) {
                        for (int i = lastImage; i > globalImageIndex; i--) {
                            if (!selectedFilesIndex.contains(i)) {
                                selectedFilesIndex.add(i);
                            }
                        }
                    }
                }

                imageAdapter.notifyDataSetChanged();

                if (selectedFilesIndex.size() == 0) {
                    hideSelectionOptionsExtra(getActivity());
                } else {
                    getActivity().invalidateOptionsMenu();
                }

                return true;
            }
        }));

        loadAndDisplayImages();

        setupSwitchListeners();

        btnNextPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextPage();
            }
        });
        btnPrevPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prevPage();
            }
        });

        btnFirstPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page > 0) {
                    page = 0;
                    loadAndDisplayImages();
                }
            }
        });

        btnLastPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page < lastPage) {
                    page = lastPage;
                    loadAndDisplayImages();
                }
            }
        });
        return view;
    }

    public void prevPage() {
        if (page > 0) {
            page--;
            loadAndDisplayImages();
        }
    }

    public void nextPage() {
        if (page < lastPage) {
            page++;
            loadAndDisplayImages();
        }
    }

    public void hideSelectionOptionsExtra(Activity activity) {
        showEditMenuButton = false;
        selectedFilesIndex.clear();
        selectionModeExtra = false;
        loadAndDisplayImages();
        StaticValues.fab.hide();
        activity.invalidateOptionsMenu();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem deleteSelectedItem = menu.findItem(R.id.action_delete_selected_extra);
        if (deleteSelectedItem != null) {
            deleteSelectedItem.setVisible(selectionModeExtra && !selectedFilesIndex.isEmpty());
        }
    }

    public void loadAndDisplayImages() {
        fileList = loadFilesFromDirectory(IMAGES_FOLDER);
        itemsPage = StaticValues.imagesPage;
        lastPage = (fileList.size() - 1) / itemsPage;
        tvPage.setText((page + 1) + " / " + (lastPage + 1));
        int startIndex, endIndex;

        //In case the last page is not complete
        if (page == lastPage && (fileList.size() % itemsPage) != 0) {
            itemsPage = fileList.size() % itemsPage;
            startIndex = fileList.size() - itemsPage;
            endIndex = fileList.size();

        } else {
            startIndex = page * itemsPage;
            endIndex = Math.min(startIndex + itemsPage, fileList.size());
        }
        List<File> fileListPage = fileList.subList(startIndex, endIndex);

        imageAdapter = new ImageAdapter(fileListPage, fileList, selectedFilesIndex);
        recyclerView.setAdapter(imageAdapter);
    }

    private void setupSwitchListeners() {
        swHdr.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
        swRgb.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
        swGif.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
        swCollage.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
        swFusion.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
        swPaper.setOnClickListener(v -> {
            page = 0;
            hideSelectionOptionsExtra(getActivity());
        });
    }

    private List<File> loadFilesFromDirectory(File directory) {
        List<File> fileList = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && (file.getName().toLowerCase().endsWith(".png") || file.getName().toLowerCase().endsWith(".gif"))) {
                    boolean addFile = false;
                    if (swHdr.isChecked() && file.getName().startsWith("HDR")) {
                        addFile = true;
                    } else if (swRgb.isChecked() && (file.getName().startsWith("RGB_") || file.getName().startsWith("Average_RGB_"))) {
                        addFile = true;
                    } else if (swCollage.isChecked() && file.getName().startsWith("Collage_")) {
                        addFile = true;
                    } else if (swGif.isChecked() && file.getName().startsWith("GIF_")) {
                        addFile = true;
                    } else if (swFusion.isChecked() && file.getName().startsWith("Fusion_")) {
                        addFile = true;
                    } else if (swPaper.isChecked() && file.getName().startsWith("paperized_")) {
                        addFile = true;
                    }
                    if (addFile) {
                        fileList.add(file);
                    }
                }
            }

            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
        }
        return fileList;
    }

    private Bitmap getBitmapFromFile(File file) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private void launchRgbComposition(String mode) {
        List<Bitmap> bitmapList = new ArrayList<>();
        for (int index : selectedFilesIndex) {
            Bitmap bitmap = getBitmapFromFile(fileList.get(index));
            if (bitmap != null) {
                bitmapList.add(bitmap);
            }
        }

        if (!RgbUtils.hasMatchingDimensions(bitmapList)) {
            Utils.toast(getContext(), getString(R.string.sizes_exception));
            return;
        }

        if (RgbUtils.MODE_RGB.equals(mode) && RgbUtils.shouldUseLegacyRgbDialog(bitmapList.size())) {
            RgbUtils rgbUtils = new RgbUtils(getContext(), bitmapList, true, null);
            rgbUtils.showRgbDialog(this);
        } else {
            RgbUtils.showBatchComposeDialog(getContext(), bitmapList, true, mode, this);
        }
    }

    @Override
    public void onButtonRgbSaved() {
        hideSelectionOptionsExtra(getActivity());
    }

    private void setPreviewActionTooltips(View saveButton, View shareButton) {
        setTooltip(saveButton, R.string.tooltip_save_image);
        setTooltip(shareButton, R.string.tooltip_share_image);
    }

    private void setTooltip(View view, int resId) {
        if (view == null) {
            return;
        }
        String tooltip = getString(resId);
        TooltipCompat.setTooltipText(view, tooltip);
        view.setContentDescription(tooltip);
    }

    private void showExtraExportOptionsDialog(File sourceFile, boolean share) {
        Activity activity = getActivity();
        if (activity == null || getContext() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View exportView = activity.getLayoutInflater().inflate(R.layout.dialog_export_options, null);
        builder.setView(exportView);
        builder.setTitle(share ? R.string.export_dialog_title_share : R.string.export_dialog_title_save);
        builder.setNegativeButton(R.string.cancel, null);

        RadioButton rbExportPng = exportView.findViewById(R.id.rbExportPng);
        RadioButton rbExportTxt = exportView.findViewById(R.id.rbExportTxt);
        CheckBox cbExportMetadata = exportView.findViewById(R.id.cbExportMetadataDialog);
        CheckBox cbExportSquare = exportView.findViewById(R.id.cbExportSquareDialog);
        TextView tvExportSizePrompt = exportView.findViewById(R.id.tvExportSizePrompt);

        boolean gifSource = isGif(sourceFile);
        rbExportPng.setChecked(true);
        rbExportTxt.setChecked(false);
        rbExportTxt.setEnabled(false);
        cbExportMetadata.setChecked(false);
        cbExportMetadata.setEnabled(false);
        cbExportSquare.setChecked(StaticValues.exportSquare);
        cbExportSquare.setEnabled(!gifSource);
        tvExportSizePrompt.setEnabled(!gifSource);

        AlertDialog exportDialog = builder.create();
        int[] buttonIds = new int[]{
                R.id.btnExport1x, R.id.btnExport2x, R.id.btnExport3x, R.id.btnExport4x, R.id.btnExport5x,
                R.id.btnExport6x, R.id.btnExport7x, R.id.btnExport8x, R.id.btnExport9x, R.id.btnExport10x
        };
        for (int i = 0; i < buttonIds.length; i++) {
            Button sizeButton = exportView.findViewById(buttonIds[i]);
            final int exportSize = i + 1;
            sizeButton.setEnabled(!gifSource || exportSize == 1);
            sizeButton.setOnClickListener(v -> {
                GalleryUtils.ExportOptions options = new GalleryUtils.ExportOptions(true, exportSize, cbExportSquare.isChecked(), false);
                if (share) {
                    shareExtraFile(sourceFile, options);
                } else {
                    saveExtraFile(sourceFile, options);
                }
                exportDialog.dismiss();
            });
        }

        exportDialog.show();
    }

    private void saveExtraFile(File sourceFile, GalleryUtils.ExportOptions options) {
        if (getContext() == null) {
            return;
        }
        File tempFile = null;
        try {
            tempFile = buildExtraExportFile(sourceFile, options, true);
            writeExtraExportFile(sourceFile, tempFile, options);
            String displayName = buildExtraExportFileName(sourceFile, options);
            String mimeType = displayName.toLowerCase(Locale.ROOT).endsWith(".gif") ? "image/gif" : "image/png";
            Utils.SavedExportEntry savedExportEntry = saveFileToConfiguredLocation(tempFile, displayName, mimeType);
            Toast.makeText(getContext(), getString(R.string.toast_saved), Toast.LENGTH_LONG).show();
            showNotification(getContext(), savedExportEntry);
        } catch (IOException e) {
            e.printStackTrace();
            toast(getContext(), getString(R.string.animation_export_failed));
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private Utils.SavedExportEntry saveFileToConfiguredLocation(File sourceFile, String displayName, String mimeType) throws IOException {
        Utils.SavedExportEntry savedExportEntry = Utils.saveExportToConfiguredLocation(
                getContext(), sourceFile, displayName, mimeType, false);
        if (savedExportEntry == null) {
            throw new IOException("Unable to save exported image");
        }
        return savedExportEntry;
    }

    private void shareExtraFile(File sourceFile, GalleryUtils.ExportOptions options) {
        if (getContext() == null) {
            return;
        }
        try {
            File outputFile = buildExtraExportFile(sourceFile, options, true);
            writeExtraExportFile(sourceFile, outputFile, options);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", outputFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(outputFile.getName().toLowerCase(Locale.ROOT).endsWith(".gif") ? "image/gif" : "image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (IOException e) {
            e.printStackTrace();
            toast(getContext(), getString(R.string.animation_export_failed));
        }
    }

    private File buildExtraExportFile(File sourceFile, GalleryUtils.ExportOptions options, boolean cacheFile) {
        String extension = getExtraExportExtension(sourceFile);
        if (cacheFile) {
            return new File(requireContext().getExternalCacheDir(), "extra_export_" + System.currentTimeMillis() + extension);
        }

        return new File(Utils.IMAGES_FOLDER, buildExtraExportFileName(sourceFile, options));
    }

    private String buildExtraExportFileName(File sourceFile, GalleryUtils.ExportOptions options) {
        String baseName = sourceFile.getName();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex >= 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        return baseName + "_export_" + options.exportSize + "x" + getExtraExportExtension(sourceFile);
    }

    private String getExtraExportExtension(File sourceFile) {
        return sourceFile.getName().toLowerCase(Locale.ROOT).endsWith(".gif") ? ".gif" : ".png";
    }

    private void writeExtraExportFile(File sourceFile, File outputFile, GalleryUtils.ExportOptions options) throws IOException {
        if (isGif(sourceFile)) {
            try (InputStream inputStream = new FileInputStream(sourceFile);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.flush();
            }
            return;
        }

        Bitmap bitmap = getBitmapFromFile(sourceFile);
        if (bitmap == null) {
            throw new IOException("Unable to decode bitmap");
        }
        if (options.exportSquare) {
            bitmap = GalleryUtils.makeSquareImage(bitmap);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * options.exportSize, bitmap.getHeight() * options.exportSize, false);
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        }
    }

    private void confirmDeleteSelectedExtra() {
        if (getContext() == null || getActivity() == null || selectedFilesIndex.isEmpty()) {
            return;
        }

        int total = selectedFilesIndex.size();
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.delete_selected_extra_title))
                .setMessage(getString(R.string.delete_selected_extra_message, total))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    int deletedCount = deleteSelectedExtraFiles();
                    if (deletedCount == total) {
                        toast(getContext(), getString(R.string.delete_selected_extra_success, deletedCount));
                    } else {
                        toast(getContext(), getString(R.string.delete_selected_extra_partial, deletedCount, total));
                    }
                    hideSelectionOptionsExtra(getActivity());
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private int deleteSelectedExtraFiles() {
        List<Integer> indexes = new ArrayList<>(selectedFilesIndex);
        Collections.sort(indexes, Collections.reverseOrder());
        int deletedCount = 0;
        for (int index : indexes) {
            if (index >= 0 && index < fileList.size()) {
                File file = fileList.get(index);
                if (file.delete()) {
                    deletedCount++;
                    loadedFilesBitmap.remove(file);
                }
            }
        }
        return deletedCount;
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

        private List<File> fileListPage;
        private List<File> fileListTotal;
        private HashSet<Integer> selectedFilesIndexes;

        public ImageAdapter(List<File> fileListPage, List<File> fileListTotal, HashSet<Integer> selectedFilesIndexes) {
            this.fileListPage = fileListPage;
            this.fileListTotal = fileListTotal;
            this.selectedFilesIndexes = selectedFilesIndexes;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ImageView imageView;
            public TextView tvImageType, tvImageSize;
            public ProgressBar progressBar;

            public ViewHolder(View view) {
                super(view);
                imageView = view.findViewById(R.id.imageView);
                tvImageType = view.findViewById(R.id.tv_image_type);
                tvImageSize = view.findViewById(R.id.tv_image_size);
                progressBar = view.findViewById(R.id.progressBar);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.rv_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            bindView(holder, position);
        }

        private void bindView(ViewHolder holder, int position) {
            View itemView = holder.itemView;
            File file = fileListPage.get(position);
            final int[] width = {0};
            final int[] height = {0};
            if (isGif(file)) {
                try {
                    InputStream inputStream = new FileInputStream(file);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    byte[] byteArray = outputStream.toByteArray();

                    inputStream.close();
                    outputStream.close();

                    GifDrawable gifDrawable = new GifDrawable(byteArray);
                    if (showInfoExtra) {
                        height[0] = gifDrawable.getIntrinsicHeight();
                        width[0] = gifDrawable.getIntrinsicWidth();
                    }
                    showInfo(showInfoExtra, file, itemView, width[0], height[0], holder.tvImageType, holder.tvImageSize, selectedFilesIndexes, position, fileListTotal);
                    holder.imageView.setImageDrawable(gifDrawable);
                    holder.progressBar.setVisibility(GONE);
                    holder.imageView.setVisibility(VISIBLE);

                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap[] bitmap = new Bitmap[1];
                        if (loadedFilesBitmap.containsKey(file) && loadedFilesBitmap.get(file) != null) {
                            bitmap[0] = loadedFilesBitmap.get(file);
                            if (showInfoExtra) {
                                height[0] = isPaper(file) ? bitmap[0].getHeight() * 10 : bitmap[0].getHeight(); //For the paper images may not be exact, as the scaled size is casted to int
                                width[0] = isPaper(file) ? bitmap[0].getWidth() * 10 : bitmap[0].getWidth();
                            }
                        } else {
                            bitmap[0] = getBitmapFromFile(file);
                            if (bitmap[0] != null) {
                                if (showInfoExtra) {
                                    height[0] = bitmap[0].getHeight();
                                    width[0] = bitmap[0].getWidth();
                                }
                                if (isPaper(file)) {
                                    bitmap[0] = Bitmap.createScaledBitmap(bitmap[0], (int) (bitmap[0].getWidth() * 0.1), (int) (bitmap[0].getHeight() * 0.1), false);
                                }
                                loadedFilesBitmap.put(file, bitmap[0]);
                            }
                        }

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showInfo(showInfoExtra, file, itemView, width[0], height[0], holder.tvImageType, holder.tvImageSize, selectedFilesIndexes, position, fileListTotal);
                                holder.imageView.setImageBitmap(bitmap[0]);
                                holder.progressBar.setVisibility(GONE);
                                holder.imageView.setVisibility(VISIBLE);
                            }
                        });
                    }
                });

            }
        }

        @Override
        public int getItemCount() {
            return fileListPage.size();
        }

    }


    private void showInfo(boolean showInfoExtra, File file, View itemView, int width, int height, TextView tvImageType, TextView tvImageSize,
                          HashSet<Integer> selectedFilesIndexes, int position, List<File> fileListTotal) {
        boolean shouldCheck = false;

        if (selectionModeExtra && selectedFilesIndexes != null && !selectedFilesIndexes.isEmpty()) {
            int actualIndex;
            if (page != lastPage) {
                actualIndex = position + (page * itemsPage);
            } else {
                actualIndex = fileListTotal.size() - (itemsPage - position);
            }
            if (selectedFilesIndexes.contains(actualIndex)) {
                shouldCheck = true;
            }
            if (!showInfoExtra) {
                itemView.setBackgroundColor(shouldCheck ? getContext().getColor(R.color.teal_700) : Color.TRANSPARENT);
            }
        }

        if (showInfoExtra) {
            GradientDrawable drawable = (GradientDrawable) getResources().getDrawable(R.drawable.border_layout);
            drawable.setColor(shouldCheck ? getResources().getColor(R.color.teal_700) : Color.TRANSPARENT);
            itemView.setBackground(drawable);

            String type = "";
            String fileName = file.getName();
            if (fileName.startsWith("HDR")) {
                type = "HDR";
            } else if (fileName.startsWith("Average_RGB")) {
                type = "Avg+RGB";
            } else if (fileName.startsWith("GIF")) {
                type = "GIF";
            } else if (fileName.startsWith("RGB")) {
                type = "RGB";
            } else if (fileName.startsWith("Collage")) {
                type = "Collage";
            } else if (fileName.startsWith("Fusion")) {
                type = "Fusion";
            } else if (fileName.startsWith("paperized")) {
                type = "Paper";
            }

            tvImageType.setText(type);
            tvImageSize.setText(width + "x" + height);
        } else {
            tvImageType.setVisibility(View.GONE);
            tvImageSize.setVisibility(View.GONE);
        }
    }

    private boolean isGif(File file) {
        return file.getName().toLowerCase().endsWith(".gif");
    }

    private boolean isPaper(File file) {
        return file.getName().startsWith("paperized");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_average_extra:
                if (!selectedFilesIndex.isEmpty()) {
                    List<Bitmap> bitmapListToAverage = new ArrayList<>();
                    for (int i : selectedFilesIndex) {
                        File file = fileList.get(i);

                        Bitmap bitmap = getBitmapFromFile(file);
                        if (bitmap != null) {
                            bitmapListToAverage.add(bitmap);
                        }
                    }
                    try {
                        Bitmap averagedBitmap = averageImages(bitmapListToAverage);

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        LayoutInflater inflater = LayoutInflater.from(getContext());
                        View dialogView = inflater.inflate(R.layout.dialog_extra, null);
                        builder.setView(dialogView);

                        ImageView imageView = dialogView.findViewById(R.id.imageView);
                        imageView.setImageBitmap(averagedBitmap);

                        Button btnClose = dialogView.findViewById(R.id.btn_close_extra);
                        Button btnSave = dialogView.findViewById(R.id.btn_save_extra);
                        btnSave.setVisibility(VISIBLE);

                        AlertDialog dialog = builder.create();

                        btnClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });

                        btnSave.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LocalDateTime now = null;
                                Date nowDate = new Date();
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    now = LocalDateTime.now();
                                }
                                File file = null;
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");

                                    file = new File(Utils.IMAGES_FOLDER, "HDR_extra_" + dtf.format(now) + ".png");
                                } else {
                                    SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                                    file = new File(Utils.IMAGES_FOLDER, "HDR_extra_" + sdf.format(nowDate) + ".png");

                                }
                                File tempFile = null;
                                try {
                                    tempFile = File.createTempFile("gbcam_export_", ".png", getContext().getCacheDir());
                                    try (FileOutputStream out = new FileOutputStream(tempFile)) {
                                        averagedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    }
                                    Utils.SavedExportEntry savedExportEntry = saveFileToConfiguredLocation(
                                            tempFile, file.getName(), "image/png");
                                    Toast toast = Toast.makeText(getContext(), getString(R.string.toast_saved) + " HDR!", Toast.LENGTH_LONG);
                                    toast.show();
                                    showNotification(getContext(), savedExportEntry);
                                    hideSelectionOptionsExtra(getActivity());
                                    dialog.dismiss();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    if (tempFile != null && tempFile.exists()) {
                                        tempFile.delete();
                                    }
                                }
                            }
                        });

                        dialog.show();
                    } catch (IllegalArgumentException e) {
                        Utils.toast(getContext(), getString(R.string.sizes_exception));
                    }
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;

            case R.id.action_rgb_extra:
                if (!selectedFilesIndex.isEmpty()) {
                    if (RgbUtils.shouldUseLegacyRgbDialog(selectedFilesIndex.size()) || RgbUtils.isBatchRgbSelectionValid(RgbUtils.MODE_RGB, selectedFilesIndex.size())) {
                        launchRgbComposition(RgbUtils.MODE_RGB);
                    } else {
                        Utils.toast(getContext(), getString(R.string.select_rgb_batch));
                    }
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_average_rgb_extra:
                if (!selectedFilesIndex.isEmpty()) {
                    if (RgbUtils.isBatchRgbSelectionValid(RgbUtils.MODE_AVERAGE_RGB, selectedFilesIndex.size())) {
                        launchRgbComposition(RgbUtils.MODE_AVERAGE_RGB);
                    } else {
                        Utils.toast(getContext(), getString(R.string.select_average_rgb));
                    }
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_delete_selected_extra:
                if (!selectedFilesIndex.isEmpty()) {
                    confirmDeleteSelectedExtra();
                } else {
                    Utils.toast(getContext(), getString(R.string.no_selected));
                }
                return true;
//            case R.id.action_gif_extra:
//                toast(getContext(), "Nothing yet");
//                return true;
//            case R.id.action_collage_extra:
//                toast(getContext(), "Nothing yet");
//                return true;
            case R.id.action_toggle_info:
                if (showInfoExtra) {
                    showInfoExtra = false;
                } else {
                    showInfoExtra = true;
                }
                loadAndDisplayImages();
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                break;
        }
        return false;

    }

}
