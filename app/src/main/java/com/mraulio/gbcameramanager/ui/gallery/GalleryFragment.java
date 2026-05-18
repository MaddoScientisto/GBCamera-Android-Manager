package com.mraulio.gbcameramanager.ui.gallery;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.checkFilterPass;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.fusionBitmap;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.paletteChanger;
import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.utils.StaticValues.exportSize;
import static com.mraulio.gbcameramanager.utils.StaticValues.exportSquare;
import static com.mraulio.gbcameramanager.utils.StaticValues.filterByDate;
import static com.mraulio.gbcameramanager.utils.StaticValues.showEditMenuButton;
import static com.mraulio.gbcameramanager.gbxcart.GBxCartConstants.BAUDRATE;
import static com.mraulio.gbcameramanager.ui.gallery.CollageMaker.addPadding;
import static com.mraulio.gbcameramanager.ui.gallery.CollageMaker.applyBorderToIV;
import static com.mraulio.gbcameramanager.ui.gallery.CollageMaker.createCollage;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.averageImages;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.checkSorting;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.encodeData;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.frameChange;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.makeSquareImage;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.mediaScanner;

import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.showFilterDialog;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.sortImages;

import static com.mraulio.gbcameramanager.ui.gallery.PaperUtils.paperDialog;
import static com.mraulio.gbcameramanager.utils.StaticValues.showGbStorage;
import static com.mraulio.gbcameramanager.utils.Utils.gbcImagesList;
import static com.mraulio.gbcameramanager.utils.Utils.getHiddenTags;
import static com.mraulio.gbcameramanager.utils.Utils.getSelectedTags;
import static com.mraulio.gbcameramanager.utils.Utils.imageBitmapCache;
import static com.mraulio.gbcameramanager.utils.Utils.retrieveTags;
import static com.mraulio.gbcameramanager.utils.Utils.rotateBitmap;
import static com.mraulio.gbcameramanager.utils.Utils.showNotification;
import static com.mraulio.gbcameramanager.utils.Utils.sortPalettes;
import static com.mraulio.gbcameramanager.utils.Utils.tagsHash;
import static com.mraulio.gbcameramanager.utils.Utils.toast;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.mraulio.gbcameramanager.MainActivity;
import com.mraulio.gbcameramanager.ui.gbstorage.GbStorageSyncManager;
import com.mraulio.gbcameramanager.ui.usbserial.PrintOverArduino;
import com.mraulio.gbcameramanager.utils.AnimatedGifEncoder;
import com.mraulio.gbcameramanager.utils.DiskCache;
import com.mraulio.gbcameramanager.utils.HorizontalNumberPicker;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.TouchImageView;
import com.mraulio.gbcameramanager.utils.Utils;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.model.GbcImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.xml.transform.Result;

import pl.droidsonroids.gif.GifDrawable;

public class GalleryFragment extends Fragment implements SerialInputOutputManager.Listener {
    private static final String MP4_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final long MP4_CODEC_TIMEOUT_US = 10_000L;
    private static final int MAX_GIF_EXPORT_FRAMES = 200;
    private static final int MAX_ANIMATION_PREVIEW_FRAMES = 60;
    static UsbManager manager = MainActivity.manager;
    SerialInputOutputManager usbIoManager;
    static UsbDeviceConnection connection;
    static UsbSerialPort port = null;
    public static GridView gridView;
    static LoadingDialog loadDialog;
    static SharedPreferences.Editor editor = StaticValues.sharedPreferences.edit();
    static HashSet<String> selectedFilterTags = new HashSet<>();
    static HashSet<String> hiddenFilterTags = new HashSet<>();
    public static List<GbcImage> filteredGbcImages = new ArrayList<>();
    static boolean updatingFromChangeImage = false;
    static List<Integer> selectedImages = new ArrayList<>();
    static StringBuilder sbTitle = new StringBuilder();
    static int itemsPerPage = StaticValues.imagesPage;
    static int startIndex = 0;
    static int endIndex = 0;
    static Activity galleryActivity;
    public static int currentPage;
    static int lastPage = 0;
    public static TextView tvResponseBytes;
    static boolean crop = false;
    boolean showPalettes = true;
    public static boolean showInfo = false;
    static TextView tv_page;
    boolean keepFrame = false;
    public static CustomGridViewAdapterImage customGridViewAdapterImage;
    static List<Bitmap> imagesForPage;
    static List<GbcImage> gbcImagesForPage;
    public static TextView tv;
    DisplayMetrics displayMetrics;
    public static DiskCache diskCache;

    public static boolean[] selectionMode = {false};
    static boolean alreadyMultiSelect = false;
    static AlertDialog deleteDialog;

    public GalleryFragment() {
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        galleryActivity = getActivity();

        sortPalettes();
        StaticValues.currentFragment = StaticValues.CURRENT_FRAGMENT.GALLERY;
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);
        MainActivity.pressBack = true;
        displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        tv = view.findViewById(R.id.text_gallery);
        gridView = view.findViewById(R.id.gridView);
        loadDialog = new LoadingDialog(getContext(), null);
        setHasOptionsMenu(true);

        diskCache = new DiskCache(getContext());

        Button btnPrevPage = view.findViewById(R.id.btnPrevPage);
        Button btnNextPage = view.findViewById(R.id.btnNextPage);
        Button btnFirstPage = view.findViewById(R.id.btnFirstPage);
        Button btnLastPage = view.findViewById(R.id.btnLastPage);

        tv_page = view.findViewById(R.id.tv_page);
        setTooltip(btnFirstPage, R.string.tooltip_first_page);
        setTooltip(btnPrevPage, R.string.tooltip_previous_page);
        setTooltip(tv_page, R.string.tooltip_page_selector);
        setTooltip(btnNextPage, R.string.tooltip_next_page);
        setTooltip(btnLastPage, R.string.tooltip_last_page);

        tv_page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog spinnerDialog = numberPickerPageDialog(getContext());

                spinnerDialog.show();
            }
        });

        view.setOnTouchListener(new OnSwipeTouchListener.OnSwipesTouchListener(getContext()) {
            @Override
            public void onSwipeLeft() {
                nextPage();
            }

            @Override
            public void onSwipeRight() {
                prevPage();
            }
        });

        btnPrevPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prevPage();
            }
        });

        btnNextPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nextPage();
            }
        });
        btnFirstPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage > 0) {
                    currentPage = 0;
                    updateGridView();
                    tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
                    editor.putInt("current_page", currentPage);
                    editor.apply();
                }
            }

        });
        btnLastPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage < lastPage) {
                    currentPage = lastPage;
                    updateGridView();
                    tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
                    editor.putInt("current_page", currentPage);
                    editor.apply();
                }
            }
        });

        /**
         * Dialog when clicking an image
         */
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!selectionMode[0]) {
                    MainImageDialog mainImageDialog = new MainImageDialog(gridView, keepFrame, lastPage, position,
                            filteredGbcImages, getContext(), displayMetrics, showPalettes, getActivity(),
                            port, usbIoManager, tvResponseBytes, connection, tv, manager, null, null);
                    mainImageDialog.showImageDialog();
                } else {
                    int globalImageIndex;
                    if (currentPage != lastPage) {
                        globalImageIndex = position + (currentPage * itemsPerPage);
                    } else {
                        globalImageIndex = filteredGbcImages.size() - (itemsPerPage - position);
                    }
                    if (selectedImages.contains(globalImageIndex)) {
                        selectedImages.remove(Integer.valueOf(globalImageIndex));

                    } else if (!selectedImages.contains(globalImageIndex)) {
                        selectedImages.add(globalImageIndex);
                    }
                    if (selectedImages.size() == 0) {
                        hideSelectionOptions(getActivity());
                    } else {
                        updateTitleText();
                        if (selectedImages.size() > 1) {
                            showEditMenuButton = true;
                        } else {
                            showEditMenuButton = false;
                        }
                        getActivity().invalidateOptionsMenu();
                    }
                    customGridViewAdapterImage.notifyDataSetChanged();
                }
            }
        });
        //LongPress on an image start selection Mode
        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                if (!selectionMode[0]) StaticValues.fab.show();

                int globalImageIndex;
                if (currentPage != lastPage) {
                    globalImageIndex = position + (currentPage * itemsPerPage);
                } else {
                    globalImageIndex = filteredGbcImages.size() - (itemsPerPage - position);
                }
                if (selectionMode[0]) {

                    Collections.sort(selectedImages);

                    int firstImage = selectedImages.get(0);
                    int lastImage = selectedImages.get(selectedImages.size() - 1);
                    selectedImages.clear();
                    selectedImages.add(globalImageIndex);
                    if (firstImage < globalImageIndex) {
                        selectedImages.clear();
                        for (int i = firstImage; i < globalImageIndex; i++) {
                            if (!selectedImages.contains(i)) {
                                selectedImages.add(i);
                            }
                        }
                        selectedImages.add(globalImageIndex);
                    } else if (firstImage > globalImageIndex) {
                        for (int i = lastImage; i > globalImageIndex; i--) {
                            if (!selectedImages.contains(i)) {
                                selectedImages.add(i);
                            }
                        }
                    }

                    alreadyMultiSelect = true;
                    if (selectedImages.size() > 1) {
                        showEditMenuButton = true;
                    } else {
                        showEditMenuButton = false;
                    }
                    getActivity().invalidateOptionsMenu();
                    updateTitleText();

                } else {
                    selectedImages.add(globalImageIndex);
                    selectionMode[0] = true;
                    alreadyMultiSelect = false;
                    updateTitleText();
                }
                customGridViewAdapterImage.notifyDataSetChanged();

                return true;
            }
        });

        if (MainActivity.doneLoading) updateFromMain(getContext());

        return view;
    }

    private static void updateTitleText() {
        if (!selectedFilterTags.isEmpty() || !hiddenFilterTags.isEmpty() || filterByDate) {
            sbTitle.append(tv.getContext().getString(R.string.filtered_images) + filteredGbcImages.size());
        } else {
            sbTitle.append(tv.getContext().getString(R.string.total_images) + filteredGbcImages.size());
        }
        if (selectedImages.size() > 0) {
            sbTitle.append("  " + tv.getContext().getString(R.string.selected_images) + selectedImages.size());
        }
        tv.setText(sbTitle.toString());
        sbTitle.setLength(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_multi_edit:
                if (selectionMode[0] && selectedImages.size() > 1) {
                    MainImageDialog mainImageDialog = new MainImageDialog(gridView, keepFrame, lastPage, 0,
                            filteredGbcImages, getContext(), displayMetrics, showPalettes, getActivity(),
                            port, usbIoManager, tvResponseBytes, connection, tv, manager, selectedImages, customGridViewAdapterImage);
                    mainImageDialog.showImageDialog();
                } else Utils.toast(getContext(), getString(R.string.select_minimum_toast));

                return true;

            case R.id.action_filter_tags:
                if (selectionMode[0]) {
                    Utils.toast(getContext(), getString(R.string.unselect_all_toast));
                } else {
                    showFilterDialog(getContext(), tagsHash, displayMetrics);
                }
                return true;

            case R.id.action_sort:
                if (selectionMode[0]) {
                    Utils.toast(getContext(), getString(R.string.unselect_all_toast));
                } else {
                    sortImages(getContext(), displayMetrics);
                }
                return true;

            case R.id.action_collage:
                if (!selectedImages.isEmpty()) {
                    //If there are too many images selected, the resulting image to show will be too big (because of the *6 in the ImageView)
                    int scaledCollage = 4;
                    int maxZoom = 10;
                    if (selectedImages.size() > 40) {
                        scaledCollage = 1;
                        maxZoom = 30;
                    }
                    if (selectedImages.size() > 200) {
                        toast(getContext(), getString(R.string.collage_too_many_images));
                        return true;
                    }

                    final Bitmap[] collagedImage = new Bitmap[1];
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    List<Bitmap> collageBitmapList = new ArrayList<>();
                    View collageView = inflater.inflate(R.layout.collage_dialog, null);
                    TouchImageView imageView = collageView.findViewById(R.id.iv_collage);
                    imageView.setMaxZoom(maxZoom);

                    Button btnReloadCollage = collageView.findViewById(R.id.btnReloadCollage);
                    Button btnSaveCollage = collageView.findViewById(R.id.save_btn_collage);
                    Button btnCancel = collageView.findViewById(R.id.cancel_button);

                    Button btnPrint = collageView.findViewById(R.id.print_button_collage);
                    Button btnPaperizeCollage = collageView.findViewById(R.id.btn_paperize_collage);

                    btnPrint.setVisibility(StaticValues.printingEnabled ? VISIBLE : GONE);
                    btnPaperizeCollage.setVisibility(StaticValues.showPaperizeButton ? VISIBLE : GONE);


                    Switch swCropCollage = collageView.findViewById(R.id.swCropCollage);
                    Switch swHorizontalOrientation = collageView.findViewById(R.id.sw_orientation);
                    Switch swHalfFrame = collageView.findViewById(R.id.sw_half_frame);
                    TextView tvExtraPadding = collageView.findViewById(R.id.tv_extra_padding);
                    SeekBar swExtraPadding = collageView.findViewById(R.id.sb_extra_padding);
                    ImageView ivPaddingColor = collageView.findViewById(R.id.iv_padding_color);
                    TextView tvNPCols = collageView.findViewById(R.id.tvNPCols);
                    HorizontalNumberPicker nPColsRows = collageView.findViewById(R.id.numberPickerCols);
                    nPColsRows.setMax(30);
                    nPColsRows.setMin(1);

                    final int[] colsRowsValue = {1};
                    final int[] lastPicked = {Color.parseColor("#FFFFFF")};
                    final int[] extraPaddingMultiplier = {0};

                    btnPrint.setOnClickListener(view -> {
                        Bitmap printBitmap = getPrintBitmap(colsRowsValue[0], lastPicked[0], swCropCollage.isChecked(), swHorizontalOrientation.isChecked(), swHalfFrame.isChecked(), extraPaddingMultiplier[0]);
                        if (printBitmap != null) {
                            try {
//                                imageView.setImageBitmap(Bitmap.createScaledBitmap(printBitmap, printBitmap.getWidth() * 5, printBitmap.getHeight() * 5, false));
                                connect();
                                usbIoManager.start();
                                port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                View dialogView = getActivity().getLayoutInflater().inflate(R.layout.print_dialog, null);
                                tvResponseBytes = dialogView.findViewById(R.id.tvResponseBytes);
                                builder.setView(dialogView);

                                builder.setNegativeButton(getString(R.string.dialog_close_button), (dialog, which) -> {
                                });

                                AlertDialog dialog = builder.create();
                                dialog.show();

                                //PRINT IMAGE
                                PrintOverArduino printOverArduino = new PrintOverArduino();

                                printOverArduino.banner = false;
                                try {
                                    List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                                    if (availableDrivers.isEmpty()) {
                                        return;
                                    }
                                    UsbSerialDriver driver = availableDrivers.get(0);
                                    List<byte[]> imageByteList = new ArrayList();

                                    imageByteList.add(Utils.encodeImage(printBitmap, "bw"));
                                    printOverArduino.sendThreadDelay(connection, driver.getDevice(), tvResponseBytes, imageByteList);
                                } catch (Exception e) {
                                    tv.append(e.toString());
                                    Toast toast = Toast.makeText(getContext(), getContext().getString(R.string.error_print_image) + e.toString(), Toast.LENGTH_LONG);
                                    toast.show();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    btnPaperizeCollage.setOnClickListener(view -> {
                        Bitmap printBitmap = getPrintBitmap(colsRowsValue[0], lastPicked[0], swCropCollage.isChecked(), swHorizontalOrientation.isChecked(), swHalfFrame.isChecked(), extraPaddingMultiplier[0]);
                        if (printBitmap != null) {
                            List<Bitmap> printHolder = new ArrayList<>();
                            printHolder.add(printBitmap);
                            paperDialog(printHolder, getContext());
                        }
                    });

                    ivPaddingColor.setOnClickListener(v -> ColorPickerDialogBuilder
                            .with(getContext())
                            .setTitle(getString(R.string.choose_color))
                            .initialColor(lastPicked[0])
                            .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                            .density(12)
                            .showAlphaSlider(false)
                            .setOnColorSelectedListener(selectedColor -> Utils.toast(getContext(), getString(R.string.selected_color) + Integer.toHexString(selectedColor).substring(2).toUpperCase()))
                            .setPositiveButton("OK", new ColorPickerClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                    applyBorderToIV(ivPaddingColor, selectedColor);
                                    lastPicked[0] = selectedColor;

                                }
                            })
                            .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                            })
                            .build()
                            .show());
                    Dialog dialog = new Dialog(getContext());

                    tvExtraPadding.setText(getString(R.string.tv_extra_padding) + extraPaddingMultiplier[0]);
                    swExtraPadding.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            extraPaddingMultiplier[0] = progress;
                            tvExtraPadding.setText(getString(R.string.tv_extra_padding) + extraPaddingMultiplier[0]);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    swHorizontalOrientation.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (swHorizontalOrientation.isChecked()) {
                                tvNPCols.setText("Rows");
                            } else {
                                tvNPCols.setText("Cols");
                            }
                        }
                    });
                    int finalScaledCollage = scaledCollage;
                    btnReloadCollage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            colsRowsValue[0] = nPColsRows.getValue();
                            collagedImage[0] = createCollage(collageBitmapList, colsRowsValue[0], swCropCollage.isChecked(), swHorizontalOrientation.isChecked(), swHalfFrame.isChecked(), extraPaddingMultiplier[0], lastPicked[0]);
                            Bitmap bitmap = Bitmap.createScaledBitmap(collagedImage[0], collagedImage[0].getWidth() * finalScaledCollage, collagedImage[0].getHeight() * finalScaledCollage, false);
                            imageView.setImageBitmap(bitmap);
                        }
                    });

                    List<Integer> indexesToLoad = new ArrayList<>();
                    for (int i : selectedImages) {
                        String hashCode = filteredGbcImages.get(i).getHashCode();
                        if (imageBitmapCache.get(hashCode) == null) {
                            indexesToLoad.add(i);
                        }
                    }

                    btnSaveCollage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            LocalDateTime now = null;
                            Date nowDate = new Date();
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                now = LocalDateTime.now();
                            }
                            File file = null;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");

                                file = new File(Utils.IMAGES_FOLDER, "Collage_" + dtf.format(now) + ".png");
                            } else {
                                SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                                file = new File(Utils.IMAGES_FOLDER, "Collage_" + sdf.format(nowDate) + ".png");
                            }
                            try (FileOutputStream out = new FileOutputStream(file)) {
                                Bitmap bitmap = Bitmap.createScaledBitmap(collagedImage[0], collagedImage[0].getWidth() * exportSize, collagedImage[0].getHeight() * exportSize, false);
                                //Make square if checked in settings
                                if (exportSquare) {
                                    bitmap = makeSquareImage(bitmap);
                                }
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                Toast toast = Toast.makeText(getContext(), getString(R.string.toast_saved) + " " + getString(R.string.collage), Toast.LENGTH_LONG);
                                toast.show();
                                mediaScanner(file, getContext());
                                showNotification(getContext(), file);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    btnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dialog.dismiss();
                        }
                    });

                    loadDialog.showDialog();
                    LoadBitmapCacheAsyncTask asyncTask = new LoadBitmapCacheAsyncTask(indexesToLoad, loadDialog, new AsyncTaskCompleteListener<Result>() {
                        @Override
                        public void onTaskComplete(Result result) {

                            for (int i : selectedImages) {
                                Bitmap image = rotateBitmap(imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()), filteredGbcImages.get(i));
                                collageBitmapList.add(image);
                            }
                            try {
                                collagedImage[0] = createCollage(collageBitmapList, colsRowsValue[0], swCropCollage.isChecked(), swHorizontalOrientation.isChecked(), swHalfFrame.isChecked(), extraPaddingMultiplier[0], lastPicked[0]);
                                Bitmap bitmap = Bitmap.createScaledBitmap(collagedImage[0], collagedImage[0].getWidth() * finalScaledCollage, collagedImage[0].getHeight() * finalScaledCollage, false);
                                imageView.setImageBitmap(bitmap);
                                dialog.setContentView(collageView);
                                int screenHeight = displayMetrics.heightPixels;
                                int desiredHeight = screenHeight;
                                Window window = dialog.getWindow();
                                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, desiredHeight);
                                lastPicked[0] = collagedImage[0].getPixel(0, 0);
                                applyBorderToIV(ivPaddingColor, lastPicked[0]);
                                dialog.show();
                            } catch (IllegalArgumentException e) {
                                Utils.toast(getContext(), getString(R.string.sizes_exception));
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            loadDialog.dismissDialog();
                        }
                    });
                    asyncTask.execute();
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;

            case R.id.action_duplicate:
                if (selectionMode[0]) {
                    DuplicateDialog duplicateDialog = new DuplicateDialog(getContext(), selectedImages, customGridViewAdapterImage, filteredGbcImages, getActivity());
                    duplicateDialog.createDuplicateDialog();
                }
                return true;

            case R.id.action_delete:
                if (!selectedImages.isEmpty()) {
                    Collections.sort(selectedImages);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(getString(R.string.delete_all_title));

                    GridView deleteImageGridView = new GridView(getContext());
                    deleteImageGridView.setNumColumns(4);
                    deleteImageGridView.setPadding(30, 10, 30, 10);
                    List<Bitmap> deleteBitmapList = new ArrayList<>();
                    List<GbcImage> deleteGbcImage = new ArrayList<>();
                    List<Integer> indexesToLoad = new ArrayList<>();
                    for (int i : selectedImages) {
                        String hashCode = filteredGbcImages.get(i).getHashCode();
                        if (imageBitmapCache.get(hashCode) == null) {
                            indexesToLoad.add(i);
                        }
                        deleteGbcImage.add(filteredGbcImages.get(i));
                    }

                    builder.setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            loadDialog.showDialog();
                            loadDialog.setLoadingDialogText("");
                            new DeleteImageAsyncTask(selectedImages, getActivity(), loadDialog).execute();
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            loadDialog.dismissDialog();
                        }
                    });
                    loadDialog.showDialog();
                    LoadBitmapCacheAsyncTask asyncTask = new LoadBitmapCacheAsyncTask(indexesToLoad, loadDialog, new AsyncTaskCompleteListener<Result>() {
                        @Override
                        public void onTaskComplete(Result result) {
                            loadDialog.dismissDialog();
                            for (int i : selectedImages) {
                                deleteBitmapList.add(imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()));
                            }
                            deleteImageGridView.setAdapter(new CustomGridViewAdapterImage(gridView.getContext(), R.layout.row_items, deleteGbcImage, deleteBitmapList, false, showInfo, false, null));
                            builder.setView(deleteImageGridView);
                            deleteDialog = builder.create();
                            deleteDialog.show();

                        }
                    });
                    asyncTask.execute();

                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_average:
                if (!selectedImages.isEmpty()) {
                    crop = false;
                    final Bitmap[] averaged = new Bitmap[1];
                    LayoutInflater inflater = LayoutInflater.from(getContext());

                    View averageView = inflater.inflate(R.layout.average_dialog, null);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                    builder.setView(averageView);

                    CheckBox cbAverageCrop = averageView.findViewById(R.id.cb_average_crop);
                    TouchImageView imageView = averageView.findViewById(R.id.iv_average);
                    cbAverageCrop.setOnClickListener(v -> {
                        if (!crop) {
                            crop = true;
                        } else {
                            crop = false;
                        }
                    });
                    builder.setTitle("HDR");

                    imageView.setPadding(10, 10, 10, 10);
                    List<Integer> indexesToLoad = new ArrayList<>();
                    for (int i : selectedImages) {
                        String hashCode = filteredGbcImages.get(i).getHashCode();
                        if (imageBitmapCache.get(hashCode) == null) {
                            indexesToLoad.add(i);
                        }
                    }
                    builder.setPositiveButton(getString(R.string.btn_save), (dialog, which) -> {
                        LocalDateTime now = null;
                        Date nowDate = new Date();
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            now = LocalDateTime.now();
                        }
                        File file = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");

                            file = new File(Utils.IMAGES_FOLDER, "HDR_" + dtf.format(now) + ".png");
                        } else {
                            SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                            file = new File(Utils.IMAGES_FOLDER, "HDR_" + sdf.format(nowDate) + ".png");

                        }
                        //Regular image
                        if (averaged[0].getHeight() == 144 && averaged[0].getWidth() == 160 && crop) {
                            averaged[0] = Bitmap.createBitmap(averaged[0], 16, 16, 128, 112);
                        }
                        //Rotated image
                        else if (averaged[0].getHeight() == 160 && averaged[0].getWidth() == 144 && crop) {
                            averaged[0] = Bitmap.createBitmap(averaged[0], 16, 16, 112, 128);
                        }
                        //Regular Wild frame
                        else if (averaged[0].getHeight() == 224 && averaged[0].getWidth() == 160 && crop) {
                            averaged[0] = Bitmap.createBitmap(averaged[0], 16, 40, 128, 112);
                        }
                        //Rotated Wild frame
                        else if (averaged[0].getHeight() == 160 && averaged[0].getWidth() == 224 && crop) {
                            averaged[0] = Bitmap.createBitmap(averaged[0], 40, 16, 112, 128);
                        }
                        try (FileOutputStream out = new FileOutputStream(file)) {
                            Bitmap savedAveraged = Bitmap.createScaledBitmap(averaged[0], averaged[0].getWidth() * exportSize, averaged[0].getHeight() * exportSize, false);
                            savedAveraged.compress(Bitmap.CompressFormat.PNG, 100, out);
                            Toast toast = Toast.makeText(getContext(), getString(R.string.toast_saved) + " HDR!", Toast.LENGTH_LONG);
                            toast.show();
                            mediaScanner(file, getContext());
                            showNotification(getContext(), file);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    });
                    loadDialog.setLoadingDialogText("");
                    loadDialog.showDialog();
                    LoadBitmapCacheAsyncTask asyncTask = new LoadBitmapCacheAsyncTask(indexesToLoad, loadDialog, new AsyncTaskCompleteListener<Result>() {
                        @Override
                        public void onTaskComplete(Result result) {
                            List<Bitmap> listBitmaps = new ArrayList<>();

                            for (int i : selectedImages) {
                                Bitmap image = imageBitmapCache.get(filteredGbcImages.get(i).getHashCode());
                                image = rotateBitmap(image, (filteredGbcImages.get(i)));
                                listBitmaps.add(image);

                            }
                            try {
                                Bitmap bitmap = averageImages(listBitmaps);
                                averaged[0] = bitmap;
                                imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 6, bitmap.getHeight() * 6, false));

                                AlertDialog dialog = builder.create();
                                dialog.show();
                            } catch (IllegalArgumentException e) {
                                Utils.toast(getContext(), getString(R.string.sizes_exception));
                            }
                            loadDialog.dismissDialog();
                        }
                    });
                    asyncTask.execute();
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_gif:
                //Using this library https://github.com/nbadal/android-gif-encoder

                if (!selectedImages.isEmpty()) {
                    Activity animationActivity = getActivity();
                    Context animationContext = getContext();
                    if (animationActivity == null || animationContext == null) {
                        return true;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(getString(R.string.animate_item));

                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    View dialogView = inflater.inflate(R.layout.animation_dialog, null);

                    builder.setView(dialogView);
                    TextView tv_animation = dialogView.findViewById(R.id.tv_animation);
                    Button reload_anim = dialogView.findViewById(R.id.btnReload);
                    Switch swLoop = dialogView.findViewById(R.id.swLoop);
                    Switch swBounce = dialogView.findViewById(R.id.swBounce);
                    Switch swSort = dialogView.findViewById(R.id.swSort);
                    Switch swCrop = dialogView.findViewById(R.id.swCrop);
                    Switch swMp4 = dialogView.findViewById(R.id.swMp4);

                        updateAnimationExportSwitch(swMp4,
                            buildAnimationFrameSequence(new ArrayList<>(selectedImages), swSort.isChecked(), swBounce.isChecked()).size(),
                            true);

                        swBounce.setOnCheckedChangeListener((buttonView, isChecked) -> updateAnimationExportSwitch(
                            swMp4,
                            buildAnimationFrameSequence(new ArrayList<>(selectedImages), swSort.isChecked(), swBounce.isChecked()).size(),
                            true));

                    ImageView imageView = dialogView.findViewById(R.id.animation_image);
                    imageView.setAdjustViewBounds(true);
                    imageView.setPadding(30, 10, 30, 10);
                    SeekBar seekBar = dialogView.findViewById(R.id.animation_seekbar);
                    final int[] fps = {10};
                    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            fps[0] = progress;
                            tv_animation.setText(fps[0] + " fps");

                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    builder.setPositiveButton(getString(R.string.btn_save), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<Integer> frameSequence = buildAnimationFrameSequence(new ArrayList<>(selectedImages), swSort.isChecked(), swBounce.isChecked());
                            boolean gifAllowed = frameSequence.size() <= MAX_GIF_EXPORT_FRAMES;
                            boolean exportMp4 = swMp4.isChecked() || !gifAllowed;
                            boolean loopAnimation = swLoop.isChecked();
                            boolean cropAnimation = swCrop.isChecked();
                            int frameRate = fps[0];

                            if (!exportMp4 && !gifAllowed) {
                                Utils.toast(animationContext, getString(R.string.animation_mp4_only_limit));
                                return;
                            }

                            loadDialog.setLoadingDialogText("");
                            loadDialog.showDialog();

                            new Thread(() -> {
                                File tempAnimationFile = null;
                                try {
                                    String fileName = buildAnimationFileName(exportMp4 ? "ANIM_" : "GIF_", exportMp4 ? ".mp4" : ".gif");
                                    String mimeType = exportMp4 ? "video/mp4" : "image/gif";
                                    tempAnimationFile = File.createTempFile("gbcam_export_", exportMp4 ? ".mp4" : ".gif", animationContext.getCacheDir());

                                    if (exportMp4) {
                                        writeMp4Animation(tempAnimationFile, frameSequence, cropAnimation, frameRate);
                                    } else {
                                        writeGifAnimation(tempAnimationFile, frameSequence, cropAnimation, frameRate, loopAnimation);
                                    }

                                    Utils.SavedExportEntry savedExportEntry = Utils.saveExportToConfiguredLocation(
                                            animationContext,
                                            tempAnimationFile,
                                            fileName,
                                            mimeType,
                                            false
                                    );

                                    animationActivity.runOnUiThread(() -> {
                                        loadDialog.dismissDialog();
                                        showNotification(animationContext, savedExportEntry);
                                        Utils.toast(animationContext, getString(R.string.toast_saved) + (exportMp4 ? " MP4!" : " GIF!"));
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    animationActivity.runOnUiThread(() -> {
                                        loadDialog.dismissDialog();
                                        Utils.toast(animationContext, getString(R.string.animation_export_failed));
                                    });
                                } finally {
                                    if (tempAnimationFile != null && tempAnimationFile.exists()) {
                                        tempAnimationFile.delete();
                                    }
                                }
                            }).start();

                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });

                    AlertDialog animationDialog = builder.create();
                    Runnable renderPreview = () -> {
                        List<Integer> frameSequence = buildAnimationFrameSequence(new ArrayList<>(selectedImages), swSort.isChecked(), swBounce.isChecked());
                        updateAnimationExportSwitch(swMp4, frameSequence.size(), false);
                        List<Integer> previewSequence = buildPreviewFrameSequence(frameSequence);

                        loadDialog.setLoadingDialogText("");
                        loadDialog.showDialog();
                        new Thread(() -> {
                            try {
                                GifDrawable gifDrawable = new GifDrawable(buildGifBytes(previewSequence, fps[0], swLoop.isChecked(), swCrop.isChecked()));
                                gifDrawable.start();
                                animationActivity.runOnUiThread(() -> {
                                    imageView.setImageDrawable(gifDrawable);
                                    loadDialog.dismissDialog();
                                    if (!animationDialog.isShowing()) {
                                        animationDialog.show();
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                                animationActivity.runOnUiThread(() -> {
                                    loadDialog.dismissDialog();
                                    if (!animationDialog.isShowing()) {
                                        animationDialog.show();
                                    }
                                    Utils.toast(animationContext, getString(R.string.animation_export_failed));
                                });
                            }
                        }).start();
                    };

                    reload_anim.setOnClickListener(v -> renderPreview.run());
                    renderPreview.run();

                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_json:
                if (!selectedImages.isEmpty()) {
                    Collections.sort(selectedImages);
                    JSONObject jsonObject = new JSONObject();
                    List<Integer> indexesToLoad = new ArrayList<>();
                    for (int i : selectedImages) {
                        String hashCode = filteredGbcImages.get(i).getHashCode();
                        if (imageBitmapCache.get(hashCode) == null) {
                            indexesToLoad.add(i);
                        }
                    }
                    loadDialog.showDialog();
                    LoadBitmapCacheAsyncTask asyncTask = new LoadBitmapCacheAsyncTask(indexesToLoad, loadDialog, result -> {
                        try {
                            JSONObject stateObject = new JSONObject();
                            JSONArray imagesArray = new JSONArray();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                            for (int i = 0; i < selectedImages.size(); i++) {
                                GbcImage gbcImage = filteredGbcImages.get(selectedImages.get(i));
                                JSONObject imageObject = new JSONObject();
                                imageObject.put("hash", gbcImage.getHashCode());
                                imageObject.put("created", sdf.format(gbcImage.getCreationDate()));
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
                                LinkedHashMap lhm = gbcImage.getImageMetadata();

                                if (lhm != null) { //Last seen images don't have metadata
                                    for (Object key : lhm.keySet()) {
                                        if (key.equals("frameIndex")) continue;
                                        Object value = lhm.get(key);
                                        if (value == null) {
                                            continue;
                                        }
                                        if (value instanceof Boolean) {
                                            metaObject.put((String) key, (Boolean) value);
                                        } else if (key.equals("isCopy") || key.equals("cpuFast")) {
                                            metaObject.put((String) key, Boolean.parseBoolean(String.valueOf(value)));
                                        } else {
                                            metaObject.put((String) key, String.valueOf(value));
                                        }
                                    }
                                }
                                imageObject.put("meta", metaObject);
                                imagesArray.put(imageObject);
                            }
                            stateObject.put("images", imagesArray);
                            stateObject.put("lastUpdateUTC", System.currentTimeMillis() / 1000);
                            jsonObject.put("state", stateObject);
                            for (int i = 0; i < selectedImages.size(); i++) {
                                GbcImage gbcImage = filteredGbcImages.get(selectedImages.get(i));
                                String txt = Utils.bytesToHex(gbcImage.getImageBytes());//Sending the original image bytes, not the one with the actual frame
                                StringBuilder sb = new StringBuilder();
                                for (int j = 0; j < txt.length(); j++) {
                                    if (j > 0 && j % 32 == 0) {
                                        sb.append("\n");
                                    }
                                    sb.append(txt.charAt(j));
                                }
                                String tileData = sb.toString();
                                String deflated = encodeData(tileData);
                                jsonObject.put(gbcImage.getHashCode(), deflated);

                            }
                            String jsonString = jsonObject.toString(2);
                            SimpleDateFormat dateFormat = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());

                            String fileName = "imagesJson" + dateFormat.format(new Date()) + ".json";

                            if (!Utils.IMAGES_JSON.exists()) {
                                Utils.IMAGES_JSON.mkdirs();
                            }
                            File file = new File(Utils.IMAGES_JSON, fileName);

                            try (FileWriter fileWriter = new FileWriter(file)) {
                                fileWriter.write(jsonString);
                                Utils.toast(getContext(), getString(R.string.json_backup_saved) + "\n" + file.getAbsolutePath());
                                showNotification(getContext(), file);
                            } catch (IOException e) {
                                e.printStackTrace();
                                Utils.toast(getContext(), getString(R.string.json_backup_failed) + "\n" + e.getMessage());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Utils.toast(getContext(), getString(R.string.json_backup_failed) + "\n" + e.getMessage());
                        }
                        loadDialog.dismissDialog();

                    });
                    asyncTask.execute();
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_gbstorage_sync:
                if (selectionMode[0] && !selectedImages.isEmpty()) {
                    GbStorageSyncManager.startSelectionSync(getActivity(), selectedImages, filteredGbcImages);
                } else {
                    Utils.toast(getContext(), getString(R.string.no_selected));
                }
                return true;
            case R.id.action_rgb:
                if (!selectedImages.isEmpty()) {
                    if (selectedImages.size() != 3 && selectedImages.size() != 4) {
                        Utils.toast(getContext(), getString(R.string.select_rgb));
                    } else {
                        List<Bitmap> bitmapList = new ArrayList<>();

                        for (int i : selectedImages) {
                            Bitmap bitmap = imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()).copy(imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()).getConfig(), true);
                            bitmap = rotateBitmap(bitmap, (filteredGbcImages.get(i)));
                            bitmapList.add(bitmap);
                        }
                        int width = bitmapList.get(0).getWidth();
                        int height = bitmapList.get(0).getHeight();
                        boolean sameSize = true;
                        for (Bitmap bitmap : bitmapList) {
                            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                                sameSize = false;
                            }
                        }
                        if (sameSize) {
                            List<GbcImage> gbcImages = new ArrayList<>();
                            for (int i : selectedImages) {
                                gbcImages.add(filteredGbcImages.get(i));
                            }
                            RgbUtils rgbUtils = new RgbUtils(getContext(), bitmapList, false,gbcImages);
                            rgbUtils.showRgbDialog(null);
                        } else {
                            Utils.toast(getContext(), getString(R.string.sizes_exception));
                        }
                    }
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            case R.id.action_toggle_info:
                if (showInfo) {
                    showInfo = false;
                } else {
                    showInfo = true;
                }
                updateGridView();
                getActivity().invalidateOptionsMenu();

                return true;
            case R.id.action_fusion:
                if (selectionMode[0]) {
                    if (selectedImages.size() != 2) {
                        Utils.toast(getContext(), "Select 2 images");
                    } else {
                        List<Integer> indexesToLoad = new ArrayList<>();
                        for (int i : selectedImages) {
                            String hashCode = filteredGbcImages.get(i).getHashCode();
                            if (imageBitmapCache.get(hashCode) == null) {
                                indexesToLoad.add(i);
                            }
                        }

                        LoadBitmapCacheAsyncTask asyncTask = new LoadBitmapCacheAsyncTask(indexesToLoad, loadDialog, result -> {
                            List<Bitmap> bitmapList = new ArrayList<>();

                            for (int i : selectedImages) {
                                Bitmap bitmap = imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()).copy(imageBitmapCache.get(filteredGbcImages.get(i).getHashCode()).getConfig(), true);
                                bitmap = rotateBitmap(bitmap, (filteredGbcImages.get(i)));
                                bitmapList.add(bitmap);
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle(getContext().getString(R.string.menu_fusion));
                            LayoutInflater inflater = LayoutInflater.from(getContext());
                            View dialogView = inflater.inflate(R.layout.dialog_fun, null);

                            builder.setView(dialogView);

                            Button btnSave = dialogView.findViewById(R.id.btn_save_fun);
                            Button btnCancel = dialogView.findViewById(R.id.btn_cancel_fun);
                            RadioButton rb1x1 = dialogView.findViewById(R.id.rb_1x1);
                            RadioButton rbHoriz = dialogView.findViewById(R.id.rb_horiz);
                            RadioButton rbVert = dialogView.findViewById(R.id.rb_vert);
                            Switch swAddGallery = dialogView.findViewById(R.id.sw_add_gallery);

                            final int[] mode = {0};
                            final Bitmap[] mergedBitmap = new Bitmap[1];

                            try {
                                mergedBitmap[0] = fusionBitmap(bitmapList, 0);

                                ImageView ivFun = dialogView.findViewById(R.id.iv_fun);

                                rb1x1.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        mode[0] = 0;
                                        mergedBitmap[0] = fusionBitmap(bitmapList, mode[0]);
                                        ivFun.setImageBitmap(Bitmap.createScaledBitmap(mergedBitmap[0], mergedBitmap[0].getWidth() * 4, mergedBitmap[0].getHeight() * 4, false));

                                    }
                                });
                                rbHoriz.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        mode[0] = 1;
                                        mergedBitmap[0] = fusionBitmap(bitmapList, mode[0]);
                                        ivFun.setImageBitmap(Bitmap.createScaledBitmap(mergedBitmap[0], mergedBitmap[0].getWidth() * 4, mergedBitmap[0].getHeight() * 4, false));

                                    }
                                });
                                rbVert.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        mode[0] = 2;
                                        mergedBitmap[0] = fusionBitmap(bitmapList, mode[0]);
                                        ivFun.setImageBitmap(Bitmap.createScaledBitmap(mergedBitmap[0], mergedBitmap[0].getWidth() * 4, mergedBitmap[0].getHeight() * 4, false));

                                    }
                                });
                                AlertDialog dialog = builder.create();

                                ivFun.setImageBitmap(Bitmap.createScaledBitmap(mergedBitmap[0], mergedBitmap[0].getWidth() * 4, mergedBitmap[0].getHeight() * 4, false));

                                btnCancel.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dialog.dismiss();
                                    }
                                });
                                GbcImage gbcImage = new GbcImage();
                                final Bitmap[] fusedImage = {null};

                                btnSave.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {

                                        if (swAddGallery.isChecked()) {
                                            // Need to change all images to B&W and redo the collage first for the encoding to work
                                            List<Bitmap> bwBitmapsFun = new ArrayList<>();
                                            for (int i : selectedImages) {
                                                GbcImage gbcImage = filteredGbcImages.get(i);
                                                //Need to change the palette to bw so the encodeImage method works
                                                Bitmap image;
                                                try {
                                                    image = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                image = rotateBitmap(image, gbcImage);
                                                bwBitmapsFun.add(image);
                                            }

                                            fusedImage[0] = fusionBitmap(bwBitmapsFun, mode[0]);
                                            byte[] imageBytes;

                                            try {
                                                if (fusedImage[0].getWidth() != 160 && fusedImage[0].getHeight() == 160) {
                                                    Matrix matrix = new Matrix();
                                                    matrix.postRotate(270);
                                                    fusedImage[0] = Bitmap.createBitmap(fusedImage[0], 0, 0, fusedImage[0].getWidth(), fusedImage[0].getHeight(), matrix, false);
                                                    gbcImage.setRotation(1);
                                                }
                                                imageBytes = Utils.encodeImage(fusedImage[0], "bw");
                                                gbcImage.setImageBytes(imageBytes);
                                                fusedImage[0] = paletteChanger(gbcImage.getPaletteId(), imageBytes, gbcImage.isInvertPalette());
                                                byte[] hash = MessageDigest.getInstance("SHA-256").digest(imageBytes);
                                                String hashHex = Utils.bytesToHex(hash);

                                                boolean alreadyIncluded = false;
                                                for (GbcImage image : gbcImagesList) {
                                                    if (image.getHashCode().equals(hashHex)) {
                                                        alreadyIncluded = true;
                                                        break;
                                                    }
                                                }

                                                //If the image already exists I don't save it. It could be duplicated or add here a method to have different hash for same images
                                                if (!alreadyIncluded) {
                                                    gbcImage.setHashCode(hashHex);
                                                    gbcImage.setName("Fused");

                                                    HashSet tags = new HashSet();
                                                    tags.add("Fusion");
                                                    gbcImage.setTags(tags);
                                                    List<GbcImage> gbcImages = new ArrayList<>();
                                                    List<Bitmap> bitmaps = new ArrayList<>();
                                                    bitmaps.add(fusedImage[0]);
                                                    gbcImages.add(gbcImage);
                                                    new SaveImageAsyncTask(gbcImages, bitmaps, getContext(), null, 0, customGridViewAdapterImage, loadDialog).execute();
                                                } else {
                                                    toast(getContext(), getContext().getString(R.string.image_exists));
                                                }

                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            } catch (NoSuchAlgorithmException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }

                                        LocalDateTime now = null;
                                        Date nowDate = new Date();
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            now = LocalDateTime.now();
                                        }
                                        File file = null;
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");

                                            file = new File(Utils.IMAGES_FOLDER, "Fusion_" + dtf.format(now) + ".png");
                                        } else {
                                            SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                                            file = new File(Utils.IMAGES_FOLDER, "Fusion_" + sdf.format(nowDate) + ".png");
                                        }
                                        try (FileOutputStream out = new FileOutputStream(file)) {
                                            Bitmap bitmap = Bitmap.createScaledBitmap(mergedBitmap[0], mergedBitmap[0].getWidth() * exportSize, mergedBitmap[0].getHeight() * exportSize, false);
                                            //Make square if checked in settings
                                            if (exportSquare) {
                                                bitmap = makeSquareImage(bitmap);
                                            }
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                            Toast toast = Toast.makeText(getContext(), getString(R.string.toast_saved) + " Fusion", Toast.LENGTH_LONG);
                                            toast.show();
                                            mediaScanner(file, getContext());
                                            showNotification(getContext(), file);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });


                                dialog.show();
                            } catch (IllegalArgumentException iae) {
                                Utils.toast(getContext(), getString(R.string.sizes_exception));
                                iae.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        asyncTask.execute();
                    }
                } else
                    Utils.toast(getContext(), getString(R.string.no_selected));
                return true;
            default:
                break;
        }
        return false;
    }

    @Override
    public void onPrepareOptionsMenu(android.view.Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem syncItem = menu.findItem(R.id.action_gbstorage_sync);
        if (syncItem != null) {
            syncItem.setVisible(showGbStorage && selectionMode[0] && !selectedImages.isEmpty());
        }
    }


    public static void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateGridView();
            tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
            editor.putInt("current_page", currentPage);
            editor.apply();
        }
    }

    public static void nextPage() {
        if (currentPage < lastPage) {
            currentPage++;
            updateGridView();
            tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
            editor.putInt("current_page", currentPage);
            editor.apply();
        }
    }

    private void setTooltip(View view, int resId) {
        String tooltip = getString(resId);
        TooltipCompat.setTooltipText(view, tooltip);
        view.setContentDescription(tooltip);
    }

    private AlertDialog numberPickerPageDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        NumberPicker numberPicker = new NumberPicker(context);
        builder.setTitle(getString(R.string.page_selector_dialog));
        builder.setView(numberPicker);
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(lastPage + 1);
        numberPicker.setWrapSelectorWheel(true);
        numberPicker.setValue(currentPage + 1);

        // Disable keyboard
        numberPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        AlertDialog dialog = builder.create();

        numberPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedValue = numberPicker.getValue();

                if (selectedValue != currentPage + 1) {
                    currentPage = selectedValue - 1;
                    updateGridView();
                    tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
                }
                dialog.hide();
            }
        });
        return dialog;
    }

    public void updateFromMain(Context context) {
        if (Utils.gbcImagesList.size() > 0) {
            retrieveTags(gbcImagesList);
            checkSorting(context);
            selectedFilterTags = getSelectedTags();
            hiddenFilterTags = getHiddenTags();
            updateGridView();
            updateTitleText();
            tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
        } else {
            tv.setText(tv.getContext().getString(R.string.no_images));
        }
    }

    //Method to update the gallery gridview
    public static void updateGridView() {

        //Bitmap list to store current page bitmaps
        filteredGbcImages = new ArrayList<>();

        if (selectedFilterTags.isEmpty() && hiddenFilterTags.isEmpty() && !filterByDate) {
            filteredGbcImages = Utils.gbcImagesList;
        } else {
            filteredGbcImages.clear();

            for (GbcImage gbcImageToFilter : Utils.gbcImagesList) {
                if (checkFilterPass(gbcImageToFilter)) {
                    filteredGbcImages.add(gbcImageToFilter);
                }
            }
        }

        imagesForPage = new ArrayList<>();
        itemsPerPage = StaticValues.imagesPage;
        //In case the list of images is shorter than the pagination size
        if (filteredGbcImages.size() < itemsPerPage) {
            itemsPerPage = filteredGbcImages.size();
        }
        try {
            if (filteredGbcImages.size() > 0)//In case all images are deleted
            {
                lastPage = (filteredGbcImages.size() - 1) / itemsPerPage;
                //In case the last page is not complete
                if (currentPage == lastPage && (filteredGbcImages.size() % itemsPerPage) != 0) {
                    itemsPerPage = filteredGbcImages.size() % itemsPerPage;
                    startIndex = filteredGbcImages.size() - itemsPerPage;
                    endIndex = filteredGbcImages.size();

                } else {
                    startIndex = currentPage * itemsPerPage;
                    endIndex = Math.min(startIndex + itemsPerPage, filteredGbcImages.size());
                }
                boolean doAsync = false;
                //The bitmaps come from the BitmapCache map, using the gbcimage hashcode
                for (GbcImage gbcImage : filteredGbcImages.subList(startIndex, endIndex)) {
                    if (!imageBitmapCache.containsKey(gbcImage.getHashCode())) {
                        doAsync = true;
                    }
                }

                if (doAsync) {
                    new UpdateGridViewAsyncTask().execute();
                } else {
                    List<Bitmap> bitmapList = new ArrayList<>();
                    for (GbcImage gbcImage : filteredGbcImages.subList(startIndex, endIndex)) {
                        bitmapList.add(imageBitmapCache.get(gbcImage.getHashCode()));
                    }
                    customGridViewAdapterImage = new CustomGridViewAdapterImage(gridView.getContext(), R.layout.row_items, filteredGbcImages.subList(startIndex, endIndex), bitmapList, false, showInfo, true, selectedImages);

                    if (updatingFromChangeImage) {
                        MainImageDialog.fastImageChange();
                        updatingFromChangeImage = false;
                    }
                    MainImageDialog.isChanging = false;
                    gridView.setAdapter(customGridViewAdapterImage);
                }
                tv_page.setText((currentPage + 1) + " / " + (lastPage + 1));
                updateTitleText();
            } else {
                if (Utils.gbcImagesList.isEmpty()) {
                    tv.setText(tv.getContext().getString(R.string.no_images));
                } else
                    tv.setText(tv.getContext().getString(R.string.no_filtered_images));
                tv_page.setText("");
                gridView.setAdapter(null);
            }
            if (itemsPerPage * currentPage >= filteredGbcImages.size()) {
                prevPage();
            }

        } catch (Exception e) {
            //In case there is an exception, recover the app by going to first page
            e.printStackTrace();
            currentPage = 0;
            editor.putInt("current_page", currentPage);
            editor.apply();
            updateGridView();
        }
    }

    private void connect() {
        manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            if (port.isOpen()) port.close();
            port.open(connection);
            port.setParameters(BAUDRATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        } catch (Exception e) {
            tv.append(e.toString());
            Toast.makeText(getContext(), "Error in connect." + e.toString(), Toast.LENGTH_SHORT).show();
        }

        usbIoManager = new SerialInputOutputManager(port, this);
    }

    public static void hideSelectionOptions(Activity activity) {
        showEditMenuButton = false;
        selectedImages.clear();
        selectionMode[0] = false;
        gridView.setAdapter(customGridViewAdapterImage);
        StaticValues.fab.hide();
        updateTitleText();
        activity.invalidateOptionsMenu();
    }

    @Override
    public void onNewData(byte[] data) {
        BigInteger bigInt = new BigInteger(1, data);
        String hexString = bigInt.toString(16);
        // Make sure the string is of pair length
        if (hexString.length() % 2 != 0) {
            hexString = "0" + hexString;
        }
        // Format the string in 2 chars blocks
        hexString = String.format("%0" + (hexString.length() + hexString.length() % 2) + "X", new BigInteger(hexString, 16));
        hexString = hexString.replaceAll("..", "$0 ");//To separate with spaces every hex byte
        String finalHexString = hexString;
        getActivity().runOnUiThread(() -> {
            tvResponseBytes.append(finalHexString);
        });
    }

    @Override
    public void onRunError(Exception e) {

    }

    private List<Integer> buildAnimationFrameSequence(List<Integer> sourceSelection, boolean sortFrames, boolean bounceFrames) {
        List<Integer> frameSequence = new ArrayList<>(sourceSelection);
        if (sortFrames) {
            Collections.sort(frameSequence);
        }
        if (bounceFrames && frameSequence.size() > 2) {
            for (int i = frameSequence.size() - 2; i > 0; i--) {
                frameSequence.add(frameSequence.get(i));
            }
        }
        return frameSequence;
    }

    private List<Integer> buildPreviewFrameSequence(List<Integer> frameSequence) {
        if (frameSequence.size() <= MAX_ANIMATION_PREVIEW_FRAMES) {
            return new ArrayList<>(frameSequence);
        }

        List<Integer> previewSequence = new ArrayList<>();
        double step = (double) (frameSequence.size() - 1) / (MAX_ANIMATION_PREVIEW_FRAMES - 1);
        int lastIndex = -1;
        for (int i = 0; i < MAX_ANIMATION_PREVIEW_FRAMES; i++) {
            int sampledIndex = (int) Math.round(i * step);
            sampledIndex = Math.min(sampledIndex, frameSequence.size() - 1);
            if (sampledIndex != lastIndex) {
                previewSequence.add(frameSequence.get(sampledIndex));
                lastIndex = sampledIndex;
            }
        }
        return previewSequence;
    }

    private void updateAnimationExportSwitch(Switch swMp4, int frameCount, boolean notifyUser) {
        boolean mp4Only = frameCount > MAX_GIF_EXPORT_FRAMES;
        if (mp4Only) {
            boolean shouldNotify = notifyUser && (!swMp4.isChecked() || swMp4.isEnabled());
            swMp4.setChecked(true);
            swMp4.setEnabled(false);
            if (shouldNotify && getContext() != null) {
                Utils.toast(getContext(), getString(R.string.animation_mp4_only_limit));
            }
        } else {
            swMp4.setEnabled(true);
        }
    }

    private byte[] buildGifBytes(List<Integer> frameSequence, int fps, boolean loopAnimation, boolean cropFrames) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        encoder.setRepeat(loopAnimation ? 0 : -1);
        encoder.setFrameRate(Math.max(1, fps));
        if (!encoder.start(bos)) {
            throw new IOException("Unable to start GIF export");
        }
        for (int frameIndex : frameSequence) {
            Bitmap bitmap = renderAnimationBitmap(frameIndex, cropFrames);
            encoder.addFrame(bitmap);
        }
        if (!encoder.finish()) {
            throw new IOException("Unable to finish GIF export");
        }
        return bos.toByteArray();
    }

    private void writeGifAnimation(File outputFile, List<Integer> frameSequence, boolean cropFrames, int fps, boolean loopAnimation) throws IOException {
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.setRepeat(loopAnimation ? 0 : -1);
            encoder.setFrameRate(Math.max(1, fps));
            if (!encoder.start(out)) {
                throw new IOException("Unable to start GIF export");
            }
            for (int frameIndex : frameSequence) {
                Bitmap bitmap = renderAnimationBitmap(frameIndex, cropFrames);
                encoder.addFrame(bitmap);
            }
            if (!encoder.finish()) {
                throw new IOException("Unable to finish GIF export");
            }
        }
    }

    private String buildAnimationFileName(String prefix, String extension) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");
            return prefix + dtf.format(now) + extension;
        }

        Date nowDate = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
        return prefix + sdf.format(nowDate) + extension;
    }

    private void writeMp4Animation(File outputFile, List<Integer> frameSequence, boolean cropFrames, int fps) throws IOException {
        if (frameSequence.isEmpty()) {
            throw new IOException("No animation frames to encode");
        }

        Bitmap firstFrame = renderAnimationBitmap(frameSequence.get(0), cropFrames);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();
        if ((width & 1) != 0 || (height & 1) != 0) {
            throw new IOException("MP4 export requires even frame dimensions");
        }

        MediaCodec encoder = MediaCodec.createEncoderByType(MP4_MIME_TYPE);
        MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int[] trackIndexHolder = new int[]{-1};
        boolean[] muxerStartedHolder = new boolean[]{false};

        try {
            int colorFormat = selectMp4ColorFormat(encoder);
            MediaFormat format = MediaFormat.createVideoFormat(MP4_MIME_TYPE, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, calculateMp4Bitrate(width, height, fps));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, fps));
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            long frameDurationUs = 1_000_000L / Math.max(1, fps);
            long presentationTimeUs = 0L;

            for (int framePosition = 0; framePosition < frameSequence.size(); framePosition++) {
                Bitmap bitmap = framePosition == 0
                        ? firstFrame
                        : renderAnimationBitmap(frameSequence.get(framePosition), cropFrames);
                byte[] frameBytes = bitmapToYuv420(bitmap, colorFormat);
                boolean frameQueued = false;
                while (!frameQueued) {
                    int inputBufferIndex = encoder.dequeueInputBuffer(MP4_CODEC_TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Unable to access MP4 input buffer");
                        }
                        inputBuffer.clear();
                        inputBuffer.put(frameBytes);
                        encoder.queueInputBuffer(inputBufferIndex, 0, frameBytes.length, presentationTimeUs, 0);
                        presentationTimeUs += frameDurationUs;
                        frameQueued = true;
                    }
                    drainMp4Encoder(encoder, muxer, bufferInfo, trackIndexHolder, muxerStartedHolder);
                }
            }

            boolean endOfStreamQueued = false;
            boolean endOfStreamReached = false;
            while (!endOfStreamQueued) {
                int inputBufferIndex = encoder.dequeueInputBuffer(MP4_CODEC_TIMEOUT_US);
                if (inputBufferIndex >= 0) {
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    endOfStreamQueued = true;
                }
                endOfStreamReached = drainMp4Encoder(encoder, muxer, bufferInfo, trackIndexHolder, muxerStartedHolder);
            }

            while (!endOfStreamReached) {
                endOfStreamReached = drainMp4Encoder(encoder, muxer, bufferInfo, trackIndexHolder, muxerStartedHolder);
            }

            if (!muxerStartedHolder[0]) {
                throw new IOException("MP4 encoder did not produce a valid output track");
            }
        } finally {
            try {
                encoder.stop();
            } catch (Exception ignored) {
            }
            encoder.release();

            if (muxerStartedHolder[0]) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
            }
            muxer.release();
        }
    }

    private int selectMp4ColorFormat(MediaCodec encoder) throws IOException {
        MediaCodecInfo.CodecCapabilities capabilities = encoder.getCodecInfo().getCapabilitiesForType(MP4_MIME_TYPE);
        int[] preferredFormats = new int[]{
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        };
        for (int preferredFormat : preferredFormats) {
            for (int colorFormat : capabilities.colorFormats) {
                if (colorFormat == preferredFormat && isSupportedMp4ColorFormat(colorFormat)) {
                    return colorFormat;
                }
            }
        }
        throw new IOException("No supported YUV420 color format for MP4 export");
    }

    private boolean isSupportedMp4ColorFormat(int colorFormat) {
        return colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar;
    }

    private int calculateMp4Bitrate(int width, int height, int fps) {
        return Math.max(width * height * Math.max(1, fps) * 2, 500_000);
    }

    private boolean drainMp4Encoder(MediaCodec encoder, MediaMuxer muxer, MediaCodec.BufferInfo bufferInfo,
                                    int[] trackIndexHolder, boolean[] muxerStartedHolder) throws IOException {
        while (true) {
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, MP4_CODEC_TIMEOUT_US);
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return false;
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStartedHolder[0]) {
                    throw new IOException("MP4 muxer format changed twice");
                }
                trackIndexHolder[0] = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxerStartedHolder[0] = true;
            } else if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                if (outputBuffer == null) {
                    throw new IOException("Unable to access MP4 output buffer");
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size > 0) {
                    if (!muxerStartedHolder[0]) {
                        throw new IOException("MP4 muxer has not started");
                    }

                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(trackIndexHolder[0], outputBuffer, bufferInfo);
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    return true;
                }
            }
        }
    }

    private Bitmap renderAnimationBitmap(int index, boolean cropFrames) throws IOException {
        Bitmap sourceBitmap = loadAnimationSourceBitmap(index);
        Bitmap.Config config = sourceBitmap.getConfig() != null ? sourceBitmap.getConfig() : Bitmap.Config.ARGB_8888;
        Bitmap bitmap = sourceBitmap.copy(config, true);
        if (cropFrames) {
            if (bitmap.getHeight() == 144 && bitmap.getWidth() == 160) {
                bitmap = Bitmap.createBitmap(bitmap, 16, 16, 128, 112);
            } else if (bitmap.getHeight() == 224 && crop) {
                bitmap = Bitmap.createBitmap(bitmap, 16, 40, 128, 112);
            }
        }

        bitmap = rotateBitmap(bitmap, filteredGbcImages.get(index));
        return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * exportSize, bitmap.getHeight() * exportSize, false);
    }

    private Bitmap loadAnimationSourceBitmap(int index) throws IOException {
        GbcImage gbcImage = filteredGbcImages.get(index);
        String imageHash = gbcImage.getHashCode();

        Bitmap memoryBitmap = imageBitmapCache.get(imageHash);
        if (memoryBitmap != null) {
            return memoryBitmap;
        }

        Bitmap diskBitmap = diskCache.get(imageHash);
        if (diskBitmap != null) {
            return diskBitmap;
        }

        byte[] imageBytes = StaticValues.db.imageDataDao().getDataByImageId(imageHash);
        if (imageBytes == null) {
            throw new IOException("Unable to load image data for animation");
        }

        gbcImage.setImageBytes(imageBytes);
        if (gbcImage.getFramePaletteId() == null) {
            gbcImage.setFramePaletteId("bw");
        }

        Bitmap bitmap = paletteChanger(gbcImage.getPaletteId(), imageBytes, gbcImage.isInvertPalette());
        if (bitmap.getHeight() == 144 && gbcImage.getFrameId() != null) {
            bitmap = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);
        }
        diskCache.put(imageHash, bitmap);
        return bitmap;
    }

    private byte[] bitmapToYuv420(Bitmap bitmap, int colorFormat) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        boolean semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                && colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar;

        int yIndex = 0;
        int uvIndex = width * height;
        int uIndex = width * height;
        int vIndex = width * height + (width * height / 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = argb[y * width + x];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                int yValue = clampToByte(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                int uValue = clampToByte(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                int vValue = clampToByte(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);

                yuv[yIndex++] = (byte) yValue;

                if ((y & 1) == 0 && (x & 1) == 0) {
                    if (semiPlanar) {
                        yuv[uvIndex++] = (byte) uValue;
                        yuv[uvIndex++] = (byte) vValue;
                    } else {
                        yuv[uIndex++] = (byte) uValue;
                        yuv[vIndex++] = (byte) vValue;
                    }
                }
            }
        }

        return yuv;
    }

    private int clampToByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private Bitmap getPrintBitmap(int colsRowsValue, int lastPicked, boolean swCropCollageChecked, boolean swHorizontalOrientationChecked, boolean swHalfFrameChecked, int extraPaddingMultiplier) {
        final int PRINT_WIDTH = 160; //  Prints need to be 160px in width
        List<Bitmap> collageBwBitmaps = new ArrayList<>();

        // Need to change all images to B&W and redo the collage first for the encoding to work
        for (int i : selectedImages) {
            GbcImage gbcImage = filteredGbcImages.get(i);
            //Need to change the palette to bw so the encodeImage method works
            Bitmap image;
            try {
                image = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            image = rotateBitmap(image, gbcImage);

            collageBwBitmaps.add(image);

        }
        if (lastPicked != Color.parseColor("#000000")) {//If border is not black, make it always white.
            lastPicked = Color.parseColor("#FFFFFF");
        }

        Bitmap printBitmap = createCollage(collageBwBitmaps, colsRowsValue, swCropCollageChecked, swHorizontalOrientationChecked, swHalfFrameChecked, extraPaddingMultiplier, lastPicked);

        if (swHorizontalOrientationChecked) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            printBitmap = Bitmap.createBitmap(printBitmap, 0, 0, printBitmap.getWidth(), printBitmap.getHeight(), matrix, false);
        }

        if (printBitmap.getWidth() <= PRINT_WIDTH || (printBitmap.getWidth() > PRINT_WIDTH && colsRowsValue == 1)) {

            //If only 1 column or row, adjust the padding to fit the 160px wide
            int paddingMult = ((PRINT_WIDTH - printBitmap.getWidth()) / 8) / 2;
            if (paddingMult != 0) {
                //Add padding on each side to center it
                printBitmap = addPadding(printBitmap, paddingMult, Color.parseColor("#FFFFFF"));
            }
            return printBitmap;

        } else {
            // Calculate the proportional height
            int originalWidth = printBitmap.getWidth();
            int originalHeight = printBitmap.getHeight();
            float aspectRatio = (float) originalHeight / originalWidth;
            int desiredHeight = Math.round(160 * aspectRatio);

            // Adjust height to be multiple of 16
            while (desiredHeight % 16 != 0) {
                desiredHeight++;
            }
            // Scalate the bitmap to new size
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(printBitmap, 160, desiredHeight, false);

            // If new size if not multiple of 16, add white pixels at the bottom
            if (desiredHeight % 16 != 0) {
                int extraHeight = 16 - (desiredHeight % 16);
                Bitmap adjustedBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), desiredHeight + extraHeight, scaledBitmap.getConfig());
                Canvas canvas = new Canvas(adjustedBitmap);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(scaledBitmap, 0, 0, null);
                return adjustedBitmap;
            } else {
                return scaledBitmap;
            }
        }

    }

}