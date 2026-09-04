package com.mraulio.gbcameramanager.ui.gallery;

import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.frameChange;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.makeSquareImage;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.mediaScanner;
import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.utils.StaticValues.exportSize;
import static com.mraulio.gbcameramanager.utils.StaticValues.exportSquare;
import static com.mraulio.gbcameramanager.utils.Utils.encodeImage;
import static com.mraulio.gbcameramanager.utils.Utils.hashPalettes;
import static com.mraulio.gbcameramanager.utils.Utils.showNotification;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.importFile.newpalette.SimpleItemTouchHelperCallback;
import com.mraulio.gbcameramanager.utils.FourThumbSeekBar;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class RgbUtils {

    public static final String MODE_RGB = "rgb";
    public static final String MODE_AVERAGE_RGB = "average-rgb";
    private static final String GROUP_BY_COLOR = "by-color";
    private static final String GROUP_BY_IMAGE = "by-image";
    private static final String CHANNEL_RED = "r";
    private static final String CHANNEL_GREEN = "g";
    private static final String CHANNEL_BLUE = "b";
    private static final String CHANNEL_NEUTRAL = "n";
    private static final int[] AEB_STEP_VALUES = {0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private static final String PREF_RGB_BATCH_GROUPING = "rgb_batch_grouping";
    private static final String PREF_RGB_BATCH_NEUTRAL = "rgb_batch_neutral";
    private static final String PREF_RGB_BATCH_CROP = "rgb_batch_crop";
    private static final String PREF_RGB_BATCH_AEB = "rgb_batch_aeb";
    private static final String PREF_RGB_BATCH_ORDER_1 = "rgb_batch_order_1";
    private static final String PREF_RGB_BATCH_ORDER_2 = "rgb_batch_order_2";
    private static final String PREF_RGB_BATCH_ORDER_3 = "rgb_batch_order_3";
    private static final String PREF_RGB_BATCH_ORDER_4 = "rgb_batch_order_4";

    List<Bitmap> rgbnBitmaps;
    List<GbcImage> gbcImages;
    Context context;
    boolean crop, extraGallery, addNeutral = true;
    FourThumbSeekBar ftRed, ftGreen, ftBlue;

    Bitmap rgbImage;
    HashMap<String, byte[]> imageBytesHash = new HashMap<>();
    final float[] redFactor = {1.0f};
    final float[] greenFactor = {1.0f};
    final float[] blueFactor = {1.0f};
    GridAdapterRGB gridAdapter;
    ImageView rgbImageView;

    public interface OnRgbSaved {
        void onButtonRgbSaved();
    }

    public RgbUtils(Context context, List<Bitmap> rgbnBitmaps, boolean extraGallery, @Nullable List<GbcImage> gbcImages) {
        this.context = context;
        this.rgbnBitmaps = rgbnBitmaps;
        this.extraGallery = extraGallery;
        this.gbcImages = gbcImages;
    }

    public static boolean shouldUseLegacyRgbDialog(int imageCount) {
        return imageCount == 3;
    }

    public static boolean isBatchRgbSelectionValid(String mode, int imageCount) {
        if (MODE_AVERAGE_RGB.equals(mode)) {
            return imageCount >= 6 && (imageCount % 3 == 0 || imageCount % 4 == 0);
        }

        return imageCount > 3 && (imageCount % 3 == 0 || imageCount % 4 == 0);
    }

    public static boolean hasMatchingDimensions(List<Bitmap> bitmaps) {
        if (bitmaps == null || bitmaps.isEmpty()) {
            return false;
        }

        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();
        for (Bitmap bitmap : bitmaps) {
            if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                return false;
            }
        }

        return true;
    }

    public static void showBatchComposeDialog(Context context, List<Bitmap> sourceBitmaps, boolean extraGallery, String mode, @Nullable OnRgbSaved listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MODE_AVERAGE_RGB.equals(mode) ? context.getString(R.string.average_rgb_item) : context.getString(R.string.rgb_item));

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.rgb_batch_dialog, null);
        builder.setView(dialogView);

        ImageView previewImage = dialogView.findViewById(R.id.iv_rgb_batch_preview);
        TextView statusText = dialogView.findViewById(R.id.tv_rgb_batch_status);
        Switch neutralSwitch = dialogView.findViewById(R.id.sw_rgb_batch_neutral);
        Switch cropSwitch = dialogView.findViewById(R.id.sw_rgb_batch_crop);
        RadioButton byImageRadio = dialogView.findViewById(R.id.rb_rgb_group_by_image);
        RadioButton byColorRadio = dialogView.findViewById(R.id.rb_rgb_group_by_color);
        LinearLayout neutralRow = dialogView.findViewById(R.id.row_rgb_batch_pos4);
        LinearLayout aebRow = dialogView.findViewById(R.id.row_rgb_batch_aeb);
        Spinner firstSpinner = dialogView.findViewById(R.id.spinner_rgb_batch_pos1);
        Spinner secondSpinner = dialogView.findViewById(R.id.spinner_rgb_batch_pos2);
        Spinner thirdSpinner = dialogView.findViewById(R.id.spinner_rgb_batch_pos3);
        Spinner fourthSpinner = dialogView.findViewById(R.id.spinner_rgb_batch_pos4);
        Spinner aebSpinner = dialogView.findViewById(R.id.spinner_rgb_batch_aeb);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_rgb_batch);
        Button saveButton = dialogView.findViewById(R.id.btn_save_rgb_batch);

        String[] channelLabels = {"R", "G", "B", "N"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, channelLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firstSpinner.setAdapter(adapter);
        secondSpinner.setAdapter(adapter);
        thirdSpinner.setAdapter(adapter);
        fourthSpinner.setAdapter(adapter);

        Integer[] aebLabels = new Integer[AEB_STEP_VALUES.length];
        for (int index = 0; index < AEB_STEP_VALUES.length; index++) {
            aebLabels[index] = AEB_STEP_VALUES[index];
        }
        ArrayAdapter<Integer> aebAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, aebLabels);
        aebAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aebSpinner.setAdapter(aebAdapter);

        firstSpinner.setSelection(getSavedSpinnerIndex(PREF_RGB_BATCH_ORDER_1, 0));
        secondSpinner.setSelection(getSavedSpinnerIndex(PREF_RGB_BATCH_ORDER_2, 1));
        thirdSpinner.setSelection(getSavedSpinnerIndex(PREF_RGB_BATCH_ORDER_3, 2));
        fourthSpinner.setSelection(getSavedSpinnerIndex(PREF_RGB_BATCH_ORDER_4, 3));

        String savedGrouping = StaticValues.sharedPreferences.getString(PREF_RGB_BATCH_GROUPING, GROUP_BY_IMAGE);
        boolean groupByColor = GROUP_BY_COLOR.equals(savedGrouping);
        byColorRadio.setChecked(groupByColor);
        byImageRadio.setChecked(!groupByColor);

        boolean defaultNeutral = sourceBitmaps.size() % 3 != 0 && sourceBitmaps.size() % 4 == 0;
        boolean allowNeutral = sourceBitmaps.size() % 4 == 0;
        boolean rememberedNeutral = StaticValues.sharedPreferences.getBoolean(PREF_RGB_BATCH_NEUTRAL, defaultNeutral);
        boolean initialNeutral = allowNeutral && rememberedNeutral;
        neutralSwitch.setChecked(initialNeutral);
        neutralSwitch.setEnabled(allowNeutral);
        neutralRow.setVisibility(initialNeutral ? View.VISIBLE : View.GONE);

        boolean initialCrop = StaticValues.sharedPreferences.getBoolean(PREF_RGB_BATCH_CROP, false);
        cropSwitch.setChecked(initialCrop);

        int savedAebStep = getSavedAebStep();
        aebSpinner.setSelection(indexOfAebStep(savedAebStep));
        aebRow.setVisibility(MODE_AVERAGE_RGB.equals(mode) ? View.VISIBLE : View.GONE);

        final boolean[] addNeutralHolder = {initialNeutral};
        final boolean[] cropHolder = {initialCrop};
        final String[] groupingHolder = {groupByColor ? GROUP_BY_COLOR : GROUP_BY_IMAGE};
        final List<Bitmap>[] pendingOutputs = new List[]{new ArrayList<>()};

        Runnable[] refreshPreview = new Runnable[1];
        refreshPreview[0] = () -> {
            int aebSteps = getSelectedAebStep(aebSpinner);
            List<String> activeOrder = getActiveChannelOrder(firstSpinner, secondSpinner, thirdSpinner, fourthSpinner, addNeutralHolder[0]);
            persistBatchDialogState(addNeutralHolder[0], cropHolder[0], groupingHolder[0], aebSteps, firstSpinner, secondSpinner, thirdSpinner, fourthSpinner);
            String validationMessage = validateBatchConfiguration(context, mode, sourceBitmaps.size(), addNeutralHolder[0], activeOrder, aebSteps);

            if (!validationMessage.isEmpty()) {
                pendingOutputs[0] = new ArrayList<>();
                previewImage.setImageBitmap(null);
                statusText.setText(validationMessage);
                saveButton.setEnabled(false);
                return;
            }

            try {
                List<HashMap<String, Bitmap>> groups = buildComposeGroups(sourceBitmaps, activeOrder, groupingHolder[0], mode, aebSteps);
                if (MODE_AVERAGE_RGB.equals(mode)) {
                    pendingOutputs[0] = new ArrayList<>();
                    pendingOutputs[0].add(composeAverageRgb(groups, addNeutralHolder[0], cropHolder[0]));
                    statusText.setText(context.getString(R.string.average_rgb_status, groups.size()));
                } else {
                    pendingOutputs[0] = composeRgbGroups(groups, addNeutralHolder[0], cropHolder[0]);
                    statusText.setText(context.getString(R.string.rgb_batch_status, pendingOutputs[0].size()));
                }

                Bitmap previewBitmap = pendingOutputs[0].get(0);
                previewImage.setImageBitmap(extraGallery
                        ? previewBitmap
                        : Bitmap.createScaledBitmap(previewBitmap, previewBitmap.getWidth() * 4, previewBitmap.getHeight() * 4, false));
                saveButton.setEnabled(true);
            } catch (IllegalArgumentException e) {
                pendingOutputs[0] = new ArrayList<>();
                previewImage.setImageBitmap(null);
                statusText.setText(e.getMessage());
                saveButton.setEnabled(false);
            }
        };

        neutralSwitch.setOnClickListener(v -> {
            addNeutralHolder[0] = neutralSwitch.isChecked();
            neutralRow.setVisibility(addNeutralHolder[0] ? View.VISIBLE : View.GONE);
            refreshPreview[0].run();
        });
        cropSwitch.setOnClickListener(v -> {
            cropHolder[0] = cropSwitch.isChecked();
            refreshPreview[0].run();
        });
        byImageRadio.setOnClickListener(v -> {
            groupingHolder[0] = GROUP_BY_IMAGE;
            refreshPreview[0].run();
        });
        byColorRadio.setOnClickListener(v -> {
            groupingHolder[0] = GROUP_BY_COLOR;
            refreshPreview[0].run();
        });

        AdapterViewListener adapterViewListener = new AdapterViewListener(refreshPreview[0]);
        firstSpinner.setOnItemSelectedListener(adapterViewListener);
        secondSpinner.setOnItemSelectedListener(adapterViewListener);
        thirdSpinner.setOnItemSelectedListener(adapterViewListener);
        fourthSpinner.setOnItemSelectedListener(adapterViewListener);
        aebSpinner.setOnItemSelectedListener(adapterViewListener);

        AlertDialog dialog = builder.create();
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            if (pendingOutputs[0].isEmpty()) {
                return;
            }

            try {
                int savedCount = saveComposeResults(context, pendingOutputs[0], extraGallery, MODE_AVERAGE_RGB.equals(mode));
                Toast.makeText(context, context.getString(R.string.rgb_saved_count, savedCount), Toast.LENGTH_LONG).show();
                if (listener != null) {
                    listener.onButtonRgbSaved();
                }
                dialog.dismiss();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, context.getString(R.string.animation_export_failed), Toast.LENGTH_LONG).show();
            }
        });

        refreshPreview[0].run();
        dialog.show();
    }


    public void showRgbDialog(OnRgbSaved listener) {


        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("RGB");

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.rgb_dialog, null);
        builder.setView(dialogView);

        LinearLayout lySbs = dialogView.findViewById(R.id.ly_sbs);
        LinearLayout lyFt = dialogView.findViewById(R.id.ly_fourThumbs);

        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_rgb);
        Button btnSave = dialogView.findViewById(R.id.btn_save_rgb);
        Switch swNeutral = dialogView.findViewById(R.id.sw_neutral);
        Switch swCrop = dialogView.findViewById(R.id.sw_crop_rgb);

        rgbImageView = dialogView.findViewById(R.id.rgb_image);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_RGB);
        recyclerView.setLayoutManager(new GridLayoutManager(context, rgbnBitmaps.size()));


        if (!extraGallery) {
            for (int i = 0; i < gbcImages.size(); i++) {
                GbcImage gbcImage = gbcImages.get(i);
                try {
                    Bitmap image = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);

                    byte[] imageBytes = encodeImage(image, "bw");
                    imageBytesHash.put(gbcImage.getHashCode(), imageBytes);

                    rgbnBitmaps.set(i, image);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        gridAdapter = new GridAdapterRGB(rgbnBitmaps);
        recyclerView.setAdapter(gridAdapter);
        if (extraGallery) {
            lySbs.setVisibility(View.VISIBLE);
            lyFt.setVisibility(View.GONE);

            SeekBar sbRedFactor = dialogView.findViewById(R.id.sb_redFactor);
            SeekBar sbGreenFactor = dialogView.findViewById(R.id.sb_greenFactor);
            SeekBar sbBlueFactor = dialogView.findViewById(R.id.sb_blueFactor);
            TextView tvRedFactor = dialogView.findViewById(R.id.tv_redFactor);
            TextView tvGreenFactor = dialogView.findViewById(R.id.tv_greenFactor);
            TextView tvBlueFactor = dialogView.findViewById(R.id.tv_blueFactor);

            sbRedFactor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                    redFactor[0] = sbRedFactor.getProgress() / 100.0f;
                    tvRedFactor.setText(sbRedFactor.getProgress() + "%");
                    updateRgbImage();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            sbGreenFactor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    greenFactor[0] = sbGreenFactor.getProgress() / 100.0f;
                    tvGreenFactor.setText(sbGreenFactor.getProgress() + "%");

                    updateRgbImage();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            sbBlueFactor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    blueFactor[0] = sbBlueFactor.getProgress() / 100.0f;
                    tvBlueFactor.setText(sbBlueFactor.getProgress() + "%");

                    updateRgbImage();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

        } else {
            ftRed = dialogView.findViewById(R.id.ft_red);
            ftGreen = dialogView.findViewById(R.id.ft_green);
            ftBlue = dialogView.findViewById(R.id.ft_blue);

            ftRed.setOnThumbMoveListener(new OnThumbMoveListener() {
                @Override
                public void onThumbMove(int[] thumbValues) {
                    int[] convertedValues = new int[4];
                    for (int i = 0; i < thumbValues.length; i++) {
                        int x = rgbToArgb(thumbValues[i]);
                        convertedValues[i] = x;
                    }

                    ImageCodec imageCodec = new ImageCodec(160, imageBytesHash.get(gbcImages.get(0).getHashCode()).length / 40);//imageBytes.length/40 to get the height of the image
                    Bitmap image = imageCodec.decodeWithPalette(convertedValues, imageBytesHash.get(gbcImages.get(0).getHashCode()), true);
                    rgbnBitmaps.set(0, image);

                    gridAdapter.notifyDataSetChanged();
                    updateRgbImage();
                }
            });

            ftGreen.setOnThumbMoveListener(thumbValues -> {
                int[] convertedValues = new int[4];
                for (int i = 0; i < thumbValues.length; i++) {
                    int x = rgbToArgb(thumbValues[i]);
                    convertedValues[i] = x;
                }

                ImageCodec imageCodec = new ImageCodec(160, imageBytesHash.get(gbcImages.get(1).getHashCode()).length / 40);//imageBytes.length/40 to get the height of the image
                Bitmap image = imageCodec.decodeWithPalette(convertedValues, imageBytesHash.get(gbcImages.get(1).getHashCode()), true);
                rgbnBitmaps.set(1, image);
                gridAdapter.notifyDataSetChanged();

                updateRgbImage();

            });

            ftBlue.setOnThumbMoveListener(thumbValues -> {
                int[] convertedValues = new int[4];
                for (int i = 0; i < thumbValues.length; i++) {
                    int x = rgbToArgb(thumbValues[i]);
                    convertedValues[i] = x;
                }
                ImageCodec imageCodec = new ImageCodec(160, imageBytesHash.get(gbcImages.get(2).getHashCode()).length / 40);//imageBytes.length/40 to get the height of the image
                Bitmap image = imageCodec.decodeWithPalette(convertedValues, imageBytesHash.get(gbcImages.get(2).getHashCode()), true);

                rgbnBitmaps.set(2, image);
                gridAdapter.notifyDataSetChanged();
                updateRgbImage();

            });
        }

        if (rgbnBitmaps.size() != 4) {
            swNeutral.setVisibility(View.GONE);
        }

        swNeutral.setOnClickListener(v -> {
            addNeutral = swNeutral.isChecked();
            updateRgbImage();

        });

        if (rgbnBitmaps.get(0).getHeight() != 144 && rgbnBitmaps.get(0).getHeight() != 224) {
            swCrop.setVisibility(View.GONE);
        } else {
            swCrop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    crop = swCrop.isChecked();
                    updateRgbImage();

                }
            });
        }

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(gridAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);

        gridAdapter.setOnItemMovedListener((fromPosition, toPosition) -> {
            recyclerView.post(() -> {
                GridAdapterRGB.ViewHolder fromViewHolder = (GridAdapterRGB.ViewHolder) recyclerView.findViewHolderForAdapterPosition(fromPosition);
                GridAdapterRGB.ViewHolder toViewHolder = (GridAdapterRGB.ViewHolder) recyclerView.findViewHolderForAdapterPosition(toPosition);

                if (fromViewHolder != null) {
                    updateImageViewBackground(fromViewHolder, fromPosition);
                }
                if (toViewHolder != null) {
                    updateImageViewBackground(toViewHolder, toPosition);
                }
            });

            // Swap the elements in the lists
            if (!extraGallery) {
                Collections.swap(gbcImages, fromPosition, toPosition);
                resetThumbs();
            } else {
                List<Bitmap> newRgbBitmapList = new ArrayList<>();
                for (int i = 0; i < gridAdapter.getItemCount(); i++) {
                    newRgbBitmapList.add(gridAdapter.getItems().get(i));
                }
                rgbnBitmaps = newRgbBitmapList;
            }
            updateRgbImage();

        });

        rgbImage = combineImages(rgbnBitmaps, redFactor[0], greenFactor[0], blueFactor[0]);
        rgbImageView.setImageBitmap(extraGallery ? rgbImage : Bitmap.createScaledBitmap(rgbImage, rgbImage.getWidth() * 4, rgbImage.getHeight() * 4, false));

        AlertDialog dialog = builder.create();

        dialog.show();

        btnCancel.setOnClickListener(new View.OnClickListener() {
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
                File file;
                String prefix = "RGB_";
                prefix += extraGallery ? "extra_" : "";
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");

                    file = new File(Utils.IMAGES_FOLDER, prefix + dtf.format(now) + ".png");
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                    file = new File(Utils.IMAGES_FOLDER, prefix + sdf.format(nowDate) + ".png");
                }
                try (FileOutputStream out = new FileOutputStream(file)) {
                    Bitmap bitmap = Bitmap.createScaledBitmap(rgbImage, rgbImage.getWidth(), rgbImage.getHeight(), false);
                    //Make square if checked in settings
                    if (exportSquare) {
                        bitmap = makeSquareImage(bitmap);
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Toast toast = Toast.makeText(context, context.getString(R.string.toast_saved) + " RGB", Toast.LENGTH_LONG);
                    toast.show();
                    mediaScanner(file, context);
                    showNotification(context, file);

                    if (listener != null) {
                        if (listener != null) {
                            listener.onButtonRgbSaved();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void resetThumbs() {
        ftRed.resetThumbs();
        ftGreen.resetThumbs();
        ftBlue.resetThumbs();

        for (int i = 0; i < gbcImages.size(); i++) {
            GbcImage gbcImage = gbcImages.get(i);
            ImageCodec imageCodec = new ImageCodec(160, imageBytesHash.get(gbcImage.getHashCode()).length / 40);//imageBytes.length/40 to get the height of the image
            Bitmap image = imageCodec.decodeWithPalette(hashPalettes.get("bw").getPaletteColorsInt(), imageBytesHash.get(gbcImage.getHashCode()), false);
            rgbnBitmaps.set(i, image);
        }

        gridAdapter.notifyDataSetChanged();
        updateRgbImage();
    }

    private void updateRgbImage() {

        rgbImage = combineImages(rgbnBitmaps, redFactor[0], greenFactor[0], blueFactor[0]);
        rgbImageView.setImageBitmap(extraGallery ? rgbImage : Bitmap.createScaledBitmap(rgbImage, rgbImage.getWidth() * 4, rgbImage.getHeight() * 4, false));
    }

    private void updateImageViewBackground(GridAdapterRGB.ViewHolder viewHolder, int position) {
        switch (position) {
            case 0:
                viewHolder.mImageView.setBackgroundColor(Color.RED);
                break;
            case 1:
                viewHolder.mImageView.setBackgroundColor(Color.GREEN);
                break;
            case 2:
                viewHolder.mImageView.setBackgroundColor(Color.BLUE);
                break;
            case 3:
                viewHolder.mImageView.setBackgroundColor(Color.BLACK);
                break;
            default:
                break;
        }
    }

    public static int rgbToArgb(int x) {
        int alpha = 255;
        int argb = (alpha << 24) | (x << 16) | (x << 8) | x;
        return argb;
    }

    private Bitmap combineImages(List<Bitmap> bitmapsRGB, float redFactor, float greenFactor, float blueFactor) {

        Bitmap firstImage;
        Bitmap secondImage;
        Bitmap thirdImage;
        Bitmap fourthImage = null;

        firstImage = bitmapsRGB.get(0);
        secondImage = bitmapsRGB.get(1);
        thirdImage = bitmapsRGB.get(2);

        if (bitmapsRGB.size() == 4) {
            fourthImage = bitmapsRGB.get(3);
        }

        int width = firstImage.getWidth();
        int height = firstImage.getHeight();
        Bitmap combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = Color.red(firstImage.getPixel(x, y));
                    int green = Color.green(secondImage.getPixel(x, y));
                    int blue = Color.blue(thirdImage.getPixel(x, y));
                    int neutral = fourthImage != null && addNeutral ? Color.red(fourthImage.getPixel(x, y)) : 255;

                    red = Math.min((int) (red * redFactor), 255);
                    green = Math.min((int) (green * greenFactor), 255);
                    blue = Math.min((int) (blue * blueFactor), 255);

                    int combinedColor;
                    if (fourthImage != null) {
                        int finalRed = blendChannel(red, neutral);
                        int finalGreen = blendChannel(green, neutral);
                        int finalBlue = blendChannel(blue, neutral);
                        combinedColor = Color.rgb(finalRed, finalGreen, finalBlue);
                    } else {
                        combinedColor = Color.rgb(red, green, blue);
                    }

                    combined.setPixel(x, y, combinedColor);
                }
            }
        } catch (IllegalArgumentException e) {
            Utils.toast(context, context.getString(R.string.sizes_exception));
        }

        if (crop) {
            if (height == 144) {
                combined = Bitmap.createBitmap(combined, 16, 16, 128, 112);
            }
            //For the wild frames
            else if (height == 224) {
                combined = Bitmap.createBitmap(combined, 16, 40, 128, 112);
            }
        }

        return combined;
    }

    private int blendChannel(int color, int neutral) {
        return (int) (color * (neutral / 255.0));
    }

    private static List<String> getActiveChannelOrder(Spinner firstSpinner, Spinner secondSpinner, Spinner thirdSpinner, Spinner fourthSpinner, boolean addNeutral) {
        List<String> order = new ArrayList<>();
        order.add(firstSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT));
        order.add(secondSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT));
        order.add(thirdSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT));
        if (addNeutral) {
            order.add(fourthSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT));
        }
        return order;
    }

    private static String validateBatchConfiguration(Context context, String mode, int imageCount, boolean addNeutral, List<String> activeOrder) {
        return validateBatchConfiguration(context, mode, imageCount, addNeutral, activeOrder, 0);
    }

    private static String validateBatchConfiguration(Context context, String mode, int imageCount, boolean addNeutral, List<String> activeOrder, int aebSteps) {
        if (activeOrder.size() != new java.util.HashSet<>(activeOrder).size()) {
            return context.getString(R.string.rgb_batch_order_error);
        }

        if (!activeOrder.containsAll(Arrays.asList(CHANNEL_RED, CHANNEL_GREEN, CHANNEL_BLUE))) {
            return context.getString(R.string.rgb_batch_order_error);
        }

        if (addNeutral && !activeOrder.contains(CHANNEL_NEUTRAL)) {
            return context.getString(R.string.rgb_batch_order_error);
        }

        int channelCount = addNeutral ? 4 : 3;
        String modeLabel = MODE_AVERAGE_RGB.equals(mode) ? context.getString(R.string.average_rgb_item) : context.getString(R.string.rgb_item);
        if (MODE_AVERAGE_RGB.equals(mode) && aebSteps > 0) {
            int imagesPerAebSet = channelCount * getAebExposureCount(aebSteps);
            if (imageCount % imagesPerAebSet != 0) {
                return context.getString(R.string.average_rgb_aeb_invalid_count, aebSteps, imagesPerAebSet);
            }
            return "";
        }

        if (imageCount % channelCount != 0) {
            return context.getString(R.string.rgb_batch_invalid_count, modeLabel, channelCount);
        }

        if (MODE_AVERAGE_RGB.equals(mode) && (imageCount / channelCount) < 2) {
            return context.getString(R.string.average_rgb_requires_groups);
        }

        return "";
    }

    private static List<HashMap<String, Bitmap>> buildComposeGroups(List<Bitmap> sourceBitmaps, List<String> activeOrder, String groupingMode, String mode, int aebSteps) {
        if (!MODE_AVERAGE_RGB.equals(mode) || aebSteps <= 0) {
            return buildRgbGroups(sourceBitmaps, activeOrder, groupingMode);
        }

        int imagesPerAebSet = activeOrder.size() * getAebExposureCount(aebSteps);
        List<HashMap<String, Bitmap>> groups = new ArrayList<>();
        for (int startIndex = 0; startIndex < sourceBitmaps.size(); startIndex += imagesPerAebSet) {
            List<Bitmap> chunk = new ArrayList<>(sourceBitmaps.subList(startIndex, startIndex + imagesPerAebSet));
            groups.addAll(buildRgbGroups(chunk, activeOrder, groupingMode));
        }
        return groups;
    }

    private static List<HashMap<String, Bitmap>> buildRgbGroups(List<Bitmap> sourceBitmaps, List<String> activeOrder, String groupingMode) {
        if (!hasMatchingDimensions(sourceBitmaps)) {
            throw new IllegalArgumentException(StaticValues.fab.getContext().getString(R.string.sizes_exception));
        }

        int groupCount = sourceBitmaps.size() / activeOrder.size();
        List<HashMap<String, Bitmap>> groups = new ArrayList<>();
        for (int imageIndex = 0; imageIndex < groupCount; imageIndex++) {
            HashMap<String, Bitmap> group = new HashMap<>();
            for (int channelIndex = 0; channelIndex < activeOrder.size(); channelIndex++) {
                int sourceIndex = GROUP_BY_COLOR.equals(groupingMode)
                        ? imageIndex + (groupCount * channelIndex)
                        : channelIndex + (activeOrder.size() * imageIndex);
                group.put(activeOrder.get(channelIndex), sourceBitmaps.get(sourceIndex));
            }
            groups.add(group);
        }

        return groups;
    }

    private static List<Bitmap> composeRgbGroups(List<HashMap<String, Bitmap>> groups, boolean addNeutral, boolean crop) {
        List<Bitmap> outputs = new ArrayList<>();
        for (HashMap<String, Bitmap> group : groups) {
            outputs.add(composeRgbGroup(group, addNeutral, crop));
        }
        return outputs;
    }

    private static Bitmap composeAverageRgb(List<HashMap<String, Bitmap>> groups, boolean addNeutral, boolean crop) {
        List<Bitmap> averagedChannels = new ArrayList<>();
        List<String> channels = new ArrayList<>(Arrays.asList(CHANNEL_RED, CHANNEL_GREEN, CHANNEL_BLUE));
        if (addNeutral) {
            channels.add(CHANNEL_NEUTRAL);
        }

        for (String channel : channels) {
            List<Bitmap> channelBitmaps = new ArrayList<>();
            for (HashMap<String, Bitmap> group : groups) {
                Bitmap channelBitmap = group.get(channel);
                if (channelBitmap == null) {
                    throw new IllegalArgumentException(StaticValues.fab.getContext().getString(R.string.rgb_batch_order_error));
                }
                channelBitmaps.add(channelBitmap);
            }
            averagedChannels.add(GalleryUtils.averageImages(channelBitmaps));
        }

        return composeRgbBitmaps(averagedChannels, addNeutral, crop);
    }

    private static Bitmap composeRgbGroup(HashMap<String, Bitmap> group, boolean addNeutral, boolean crop) {
        List<Bitmap> orderedBitmaps = new ArrayList<>();
        orderedBitmaps.add(group.get(CHANNEL_RED));
        orderedBitmaps.add(group.get(CHANNEL_GREEN));
        orderedBitmaps.add(group.get(CHANNEL_BLUE));
        if (addNeutral) {
            orderedBitmaps.add(group.get(CHANNEL_NEUTRAL));
        }

        return composeRgbBitmaps(orderedBitmaps, addNeutral, crop);
    }

    private static Bitmap composeRgbBitmaps(List<Bitmap> bitmapsRGB, boolean addNeutral, boolean crop) {
        if (!hasMatchingDimensions(bitmapsRGB) || bitmapsRGB.size() < 3) {
            throw new IllegalArgumentException(StaticValues.fab.getContext().getString(R.string.sizes_exception));
        }

        Bitmap firstImage = bitmapsRGB.get(0);
        Bitmap secondImage = bitmapsRGB.get(1);
        Bitmap thirdImage = bitmapsRGB.get(2);
        Bitmap fourthImage = addNeutral && bitmapsRGB.size() > 3 ? bitmapsRGB.get(3) : null;

        int width = firstImage.getWidth();
        int height = firstImage.getHeight();
        Bitmap combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = Color.red(firstImage.getPixel(x, y));
                int green = Color.green(secondImage.getPixel(x, y));
                int blue = Color.blue(thirdImage.getPixel(x, y));
                int neutral = fourthImage != null ? Color.red(fourthImage.getPixel(x, y)) : 255;

                int combinedColor;
                if (fourthImage != null) {
                    combinedColor = Color.rgb(blendChannelStatic(red, neutral), blendChannelStatic(green, neutral), blendChannelStatic(blue, neutral));
                } else {
                    combinedColor = Color.rgb(red, green, blue);
                }

                combined.setPixel(x, y, combinedColor);
            }
        }

        if (crop) {
            if (height == 144) {
                combined = Bitmap.createBitmap(combined, 16, 16, 128, 112);
            } else if (height == 224) {
                combined = Bitmap.createBitmap(combined, 16, 40, 128, 112);
            }
        }

        return combined;
    }

    private static int blendChannelStatic(int color, int neutral) {
        return (int) (color * (neutral / 255.0));
    }

    private static int saveComposeResults(Context context, List<Bitmap> outputs, boolean extraGallery, boolean averageRgb) throws IOException {
        String prefix = averageRgb ? "Average_RGB_" : "RGB_";
        if (extraGallery) {
            prefix += "extra_";
        }

        LocalDateTime now = null;
        Date nowDate = new Date();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            now = LocalDateTime.now();
        }

        for (int index = 0; index < outputs.size(); index++) {
            String timestamp;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");
                timestamp = dtf.format(now);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                timestamp = sdf.format(nowDate);
            }

            String suffix = outputs.size() > 1 ? String.format(Locale.getDefault(), "%02d_", index + 1) : "";
            File file = new File(Utils.IMAGES_FOLDER, prefix + suffix + timestamp + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                Bitmap bitmap = Bitmap.createScaledBitmap(outputs.get(index), outputs.get(index).getWidth(), outputs.get(index).getHeight(), false);
                if (exportSquare) {
                    bitmap = makeSquareImage(bitmap);
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                mediaScanner(file, context);
                showNotification(context, file);
            }
        }

        return outputs.size();
    }

    private static int getAebExposureCount(int aebSteps) {
        return aebSteps == 0 ? 1 : (aebSteps * 2) + 1;
    }

    private static int getSelectedAebStep(Spinner aebSpinner) {
        Object selectedValue = aebSpinner.getSelectedItem();
        return selectedValue instanceof Integer ? (Integer) selectedValue : 0;
    }

    private static int getSavedAebStep() {
        int savedValue = StaticValues.sharedPreferences.getInt(PREF_RGB_BATCH_AEB, 0);
        return indexOfAebStep(savedValue) >= 0 ? savedValue : 0;
    }

    private static int indexOfAebStep(int value) {
        for (int index = 0; index < AEB_STEP_VALUES.length; index++) {
            if (AEB_STEP_VALUES[index] == value) {
                return index;
            }
        }
        return 0;
    }

    private static int getSavedSpinnerIndex(String preferenceKey, int defaultValue) {
        int savedValue = StaticValues.sharedPreferences.getInt(preferenceKey, defaultValue);
        return Math.max(0, Math.min(savedValue, 3));
    }

    private static void persistBatchDialogState(boolean addNeutral, boolean crop, String groupingMode, int aebSteps,
                                                Spinner firstSpinner, Spinner secondSpinner, Spinner thirdSpinner, Spinner fourthSpinner) {
        StaticValues.sharedPreferences.edit()
                .putString(PREF_RGB_BATCH_GROUPING, groupingMode)
                .putBoolean(PREF_RGB_BATCH_NEUTRAL, addNeutral)
                .putBoolean(PREF_RGB_BATCH_CROP, crop)
                .putInt(PREF_RGB_BATCH_AEB, aebSteps)
                .putInt(PREF_RGB_BATCH_ORDER_1, firstSpinner.getSelectedItemPosition())
                .putInt(PREF_RGB_BATCH_ORDER_2, secondSpinner.getSelectedItemPosition())
                .putInt(PREF_RGB_BATCH_ORDER_3, thirdSpinner.getSelectedItemPosition())
                .putInt(PREF_RGB_BATCH_ORDER_4, fourthSpinner.getSelectedItemPosition())
                .apply();
    }

    private static class AdapterViewListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable onChange;

        AdapterViewListener(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            onChange.run();
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }


}
