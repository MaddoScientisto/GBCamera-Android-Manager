package com.mraulio.gbcameramanager.ui.gbstorage;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.content.DialogInterface;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Spinner;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.model.GbcFrame;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.frames.FramesFragment;
import com.mraulio.gbcameramanager.ui.gallery.GalleryUtils;
import com.mraulio.gbcameramanager.ui.palettes.CustomGridViewAdapterPalette;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class RemoteImageDialog {
    private final Activity activity;
    private final Context context;
    private final List<RemoteGbStorageImage> remoteImages;
    private final DisplayMetrics displayMetrics;
    private final Runnable onDownloaded;
    private RemoteGbStorageImage remoteImage;
    private int currentPosition;
    private Dialog dialog;
    private ImageView imageView;
    private GridView gridViewPalette;
    private GridView gridViewFrames;
    private Spinner frameGroupSpinner;
    private CheckBox invertCheckBox;
    private CheckBox keepFrameCheckBox;
    private CheckBox cropCheckBox;
    private Button paletteFrameButton;
    private boolean keepFrame;
    private boolean showPalettes = true;
    private Bitmap baseBitmap;

    public RemoteImageDialog(Activity activity, List<RemoteGbStorageImage> remoteImages, int position, DisplayMetrics displayMetrics, Runnable onDownloaded) {
        this.activity = activity;
        this.context = activity;
        this.remoteImages = remoteImages;
        this.currentPosition = position;
        this.remoteImage = remoteImages.get(position);
        this.displayMetrics = displayMetrics;
        this.onDownloaded = onDownloaded;
    }

    public void show() {
        dialog = new Dialog(context);
        dialog.setContentView(R.layout.image_main_dialog);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Utils.sortPalettes();
            }
        });

        imageView = dialog.findViewById(R.id.image_view);
        Button printButton = dialog.findViewById(R.id.print_button);
        Button rotateButton = dialog.findViewById(R.id.btnRotate);
        cropCheckBox = dialog.findViewById(R.id.cbCrop);
        Button saveButton = dialog.findViewById(R.id.save_button);
        Button shareButton = dialog.findViewById(R.id.share_button);
        Button downloadButton = dialog.findViewById(R.id.btn_paperize_collage);
        invertCheckBox = dialog.findViewById(R.id.cbInvert);
        keepFrameCheckBox = dialog.findViewById(R.id.cbFrameKeep);
        paletteFrameButton = dialog.findViewById(R.id.btnPaletteFrame);
        gridViewPalette = dialog.findViewById(R.id.gridViewPal);
        gridViewFrames = dialog.findViewById(R.id.gridViewFra);
        frameGroupSpinner = dialog.findViewById(R.id.spFrameGroupsImage);

        printButton.setVisibility(GONE);
        rotateButton.setVisibility(StaticValues.showRotationButton ? VISIBLE : GONE);
        downloadButton.setVisibility(VISIBLE);
        downloadButton.setText(R.string.gbstorage_remote_sync);

        View dialogBackground = dialog.findViewById(android.R.id.content).getRootView();
        dialogBackground.setOnTouchListener(new View.OnTouchListener() {
            float downY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int swipeThreshold = 200;
                float y = event.getY();
                float x = event.getX();
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int topTwoThirdsHeight = screenHeight * 2 / 3;
                int leftHalf = screenWidth / 2;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        float upY = event.getY();
                        if (upY > downY + swipeThreshold) {
                            changeImage(true);
                        } else if (upY < downY - swipeThreshold) {
                            changeImage(false);
                        } else if (y < topTwoThirdsHeight) {
                            changeImage(x <= leftHalf);
                        } else {
                            dialog.dismiss();
                        }
                        break;
                }
                return true;
            }
        });

        keepFrame = remoteImage.image.isLockFrame();
        keepFrameCheckBox.setChecked(keepFrame);
        invertCheckBox.setChecked(keepFrame ? remoteImage.image.isInvertFramePalette() : remoteImage.image.isInvertPalette());

        configurePalettes();
        configureFrames();
        refreshImage();

        invertCheckBox.setOnClickListener(v -> {
            if (keepFrame) {
                remoteImage.image.setInvertFramePalette(invertCheckBox.isChecked());
            } else {
                remoteImage.image.setInvertPalette(invertCheckBox.isChecked());
            }
            refreshImage();
        });
        keepFrameCheckBox.setOnClickListener(v -> {
            keepFrame = keepFrameCheckBox.isChecked();
            remoteImage.image.setLockFrame(keepFrame);
            invertCheckBox.setChecked(keepFrame ? remoteImage.image.isInvertFramePalette() : remoteImage.image.isInvertPalette());
            refreshImage();
        });
        rotateButton.setOnClickListener(v -> {
            int rotation = remoteImage.image.getRotation();
            remoteImage.image.setRotation(rotation != 0 ? rotation - 1 : 3);
            refreshImage();
        });
        paletteFrameButton.setOnClickListener(v -> {
            showPalettes = !showPalettes;
            paletteFrameButton.setText(showPalettes ? context.getString(R.string.btn_show_frames) : context.getString(R.string.btn_show_palettes));
            gridViewPalette.setVisibility(showPalettes ? VISIBLE : GONE);
            gridViewFrames.setVisibility(showPalettes ? GONE : VISIBLE);
            frameGroupSpinner.setVisibility(showPalettes ? GONE : VISIBLE);
        });
        saveButton.setOnClickListener(v -> {
            List<GbcImage> images = new ArrayList<>();
            images.add(remoteImage.image);
            GalleryUtils.showExportOptionsDialog(activity, context, images, cropCheckBox.isChecked(), false);
        });
        shareButton.setOnClickListener(v -> {
            List<GbcImage> images = new ArrayList<>();
            images.add(remoteImage.image);
            GalleryUtils.showExportOptionsDialog(activity, context, images, cropCheckBox.isChecked(), true);
        });
        downloadButton.setOnClickListener(v -> downloadRemoteImage());

        Window window = dialog.getWindow();
        if (window != null) {
            int desiredWidth = (int) (displayMetrics.widthPixels * 0.8);
            window.setLayout(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void changeImage(boolean previous) {
        int nextPosition = currentPosition + (previous ? -1 : 1);
        if (nextPosition < 0) {
            Utils.toast(context, context.getString(R.string.toast_first_image));
            return;
        }
        if (nextPosition >= remoteImages.size()) {
            Utils.toast(context, context.getString(R.string.toast_last_image));
            return;
        }
        currentPosition = nextPosition;
        RemoteGbStorageImage nextImage = remoteImages.get(nextPosition);
        if (nextImage.image.getImageBytes() != null) {
            remoteImage = nextImage;
            refreshControlsForImage();
            return;
        }
        LoadingDialog loadingDialog = new LoadingDialog(context, context.getString(R.string.loading));
        loadingDialog.showDialog();
        GbStorageSyncManager.fetchRemoteImage(context, nextImage, result -> {
            loadingDialog.dismissDialog();
            if (!result.success) {
                Utils.toast(context, result.message);
                return;
            }
            remoteImages.set(currentPosition, result.remoteImage);
            remoteImage = result.remoteImage;
            refreshControlsForImage();
        });
    }

    private void refreshControlsForImage() {
        keepFrame = remoteImage.image.isLockFrame();
        keepFrameCheckBox.setChecked(keepFrame);
        invertCheckBox.setChecked(keepFrame ? remoteImage.image.isInvertFramePalette() : remoteImage.image.isInvertPalette());
        configurePalettes();
        configureFrames();
        refreshImage();
    }

    private void refreshImage() {
        try {
            baseBitmap = GalleryUtils.frameChange(remoteImage.image, remoteImage.image.getFrameId(), remoteImage.image.isInvertPalette(), remoteImage.image.isInvertFramePalette(), keepFrame, true);
            Utils.imageBitmapCache.put(remoteImage.image.getHashCode(), baseBitmap);
            Bitmap displayBitmap = Utils.rotateBitmap(baseBitmap, remoteImage.image);
            imageView.setImageBitmap(Bitmap.createScaledBitmap(displayBitmap, displayBitmap.getWidth() * 6, displayBitmap.getHeight() * 6, false));
            imageView.setMaxHeight(displayMetrics.heightPixels / 2);
        } catch (Exception exception) {
            Utils.toast(context, exception.getMessage());
        }
    }

    private void configurePalettes() {
        CustomGridViewAdapterPalette adapterPalette = new CustomGridViewAdapterPalette(context, R.layout.palette_grid_item, Utils.sortedPalettes, false, false, false);
        for (int i = 0; i < Utils.sortedPalettes.size(); i++) {
            if (Utils.sortedPalettes.get(i).getPaletteId().equals(remoteImage.image.getPaletteId())) {
                adapterPalette.setLastSelectedImagePosition(i);
            }
            if (Utils.sortedPalettes.get(i).getPaletteId().equals(remoteImage.image.getFramePaletteId())) {
                adapterPalette.setLastSelectedFramePosition(i);
            }
        }
        gridViewPalette.setAdapter(adapterPalette);
        gridViewPalette.setOnItemClickListener((parent, view, position, id) -> {
            if (keepFrame) {
                remoteImage.image.setFramePaletteId(Utils.sortedPalettes.get(position).getPaletteId());
                adapterPalette.setLastSelectedFramePosition(position);
            } else {
                remoteImage.image.setPaletteId(Utils.sortedPalettes.get(position).getPaletteId());
                adapterPalette.setLastSelectedImagePosition(position);
            }
            adapterPalette.notifyDataSetChanged();
            refreshImage();
        });
    }

    private void configureFrames() {
        List<String> frameGroupList = new ArrayList<>();
        List<String> frameGroupIds = new ArrayList<>();
        frameGroupList.add(context.getString(R.string.sp_all_frame_groups));
        for (LinkedHashMap.Entry<String, String> entry : Utils.frameGroupsNames.entrySet()) {
            frameGroupList.add(entry.getValue() + " (" + entry.getKey() + ")");
            frameGroupIds.add(entry.getKey());
        }
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, frameGroupList);
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frameGroupSpinner.setAdapter(groupAdapter);
        frameGroupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                List<GbcFrame> frames = new ArrayList<>();
                frames.add(null);
                if (position == 0) {
                    frames.addAll(Utils.framesList);
                } else {
                    String groupId = frameGroupIds.get(position - 1);
                    for (GbcFrame frame : Utils.framesList) {
                        String currentGroup = frame.getFrameId().substring(0, frame.getFrameId().length() - 2);
                        if (currentGroup.equals(groupId)) {
                            frames.add(frame);
                        }
                    }
                }
                FramesFragment.CustomGridViewAdapterFrames adapter = new FramesFragment.CustomGridViewAdapterFrames(context, R.layout.frames_row_items, frames, false, false);
                for (int i = 0; i < frames.size(); i++) {
                    GbcFrame frame = frames.get(i);
                    if ((frame == null && remoteImage.image.getFrameId() == null) || (frame != null && frame.getFrameId().equals(remoteImage.image.getFrameId()))) {
                        adapter.setLastSelectedPosition(i);
                    }
                }
                gridViewFrames.setAdapter(adapter);
                gridViewFrames.setOnItemClickListener((gridParent, gridView, framePosition, frameId) -> {
                    GbcFrame selectedFrame = frames.get(framePosition);
                    remoteImage.image.setFrameId(selectedFrame == null ? null : selectedFrame.getFrameId());
                    adapter.setLastSelectedPosition(framePosition);
                    adapter.notifyDataSetChanged();
                    refreshImage();
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void downloadRemoteImage() {
        LoadingDialog loadingDialog = new LoadingDialog(context, context.getString(R.string.gbstorage_remote_sync));
        loadingDialog.showDialog();
        GbStorageSyncManager.downloadRemoteImage(context, remoteImage, result -> {
            loadingDialog.dismissDialog();
            Utils.toast(context, result.message);
            if (result.success) {
                if (onDownloaded != null) {
                    onDownloaded.run();
                }
                dialog.dismiss();
            }
        });
    }
}
