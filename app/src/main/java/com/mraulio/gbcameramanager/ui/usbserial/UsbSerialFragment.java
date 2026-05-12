package com.mraulio.gbcameramanager.ui.usbserial;

import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.gbxcart.GBxCartConstants.BAUDRATE;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialUtils.deleteFolderRecursive;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialUtils.magicIsReal;
import static com.mraulio.gbcameramanager.utils.StaticValues.FILTER_DUPLICATED;
import static com.mraulio.gbcameramanager.utils.Utils.saveTypeNames;
import static com.mraulio.gbcameramanager.utils.Utils.toast;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.mraulio.gbcameramanager.MainActivity;
import com.mraulio.gbcameramanager.ui.gallery.CustomGridViewAdapterImage;
import com.mraulio.gbcameramanager.ui.gallery.SaveImageAsyncTask;
import com.mraulio.gbcameramanager.ui.importFile.ImagesImportDialog;
import com.mraulio.gbcameramanager.utils.LoadingDialog;
import com.mraulio.gbcameramanager.utils.StaticValues;
import com.mraulio.gbcameramanager.utils.Utils;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.ui.importFile.HexToTileData;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.gameboycameralib.constants.IndexedPalette;
import com.mraulio.gbcameramanager.gameboycameralib.saveExtractor.Extractor;
import com.mraulio.gbcameramanager.gameboycameralib.saveExtractor.SaveImageExtractor;
import com.mraulio.gbcameramanager.gbxcart.GBxCartCommands;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.ImageData;
import com.mraulio.gbcameramanager.ui.importFile.ImportFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * By Mraulio
 */
public class UsbSerialFragment extends Fragment implements SerialInputOutputManager.Listener {
    public static File photoFolder;
    static File latestFile;
    boolean ape = false;
    static UsbDeviceConnection connection;
    static UsbSerialPort port = null;
    static UsbManager manager = MainActivity.manager;
    SerialInputOutputManager usbIoManager;
    String romName = "";
    int numImagesAdded;
    private static final String ACTION_USB_PERMISSION = "com.mraulio.gbcameramanager.USB_PERMISSION";
    boolean isRomExtracted;
    public static LinearLayout layoutCb;
    public static LinearLayout layoutPicNRecControls;
    public static CheckBox cbLastSeen;
    public static CheckBox cbDeleted;
    static GridView gridView;
    boolean gbxMode = true;
    public static List<File> fullRomFileList = new ArrayList<>();
    public static List<byte[]> fullRomFileBytes = new ArrayList<>();
    static TextView tv;

    TextView tvMode;
    public static Button btnSave, btnReadRomName, btnReadRam, btnFullRom, btnPrintBanner, btnAddImages, btnDelSav, btnDecode, btnDelete, btnReadPicNRec;
    Button btnImportPicoGbSerialBuffer;
    Button btnClearPicoGbSerialBuffer;
    Button btnSelectAllPicoGbSerialBuffer;
    EditText etPicoGbSerialImportTag;
    ImageView ivPicoGbSerialLastPreview;
    Button btnPicNRecStartFirst, btnPicNRecStartCurrent, btnPicNRecEndCurrent, btnPicNRecEndLast, btnPicNRecPreviewMinus, btnPicNRecPreviewPlus, btnPicNRecPreviewLast, btnPicNRecPreviewMinusTen, btnPicNRecPreviewPlusTen, btnPicNRecPreviewFirst;
    EditText etPicNRecStart, etPicNRecEnd;
    ImageView ivPicNRecPreview;
    SeekBar sbPicNRecPreview;
    TextView tvPicNRecDeviceInfo, tvPicNRecPreviewStatus;
    RadioButton rbGbx, rbApe, rbPicNRec, rbPicoGbSerial;
    public static RadioButton rbPrint;
    RadioGroup rbGroup;
    LinearLayout layoutPicoGbSerialControls;
    CheckBox cbPicoGbSerialAutoImport;
    CheckBox cbPicoGbSerialAutoDeleteDuplicates;
    //    public static Switch swIsCartJpUsb;
    Spinner spSaveType;

    static Utils.SAVE_TYPE_INT_JP_HK saveTypeIntJpHk;
    static List<Bitmap> extractedImagesBitmaps = new ArrayList<>();
    static List<GbcImage> extractedImagesList = new ArrayList<>();
    static List<List<GbcImage>> listActiveImages = new ArrayList<>();
    static List<List<GbcImage>> listDeletedImages = new ArrayList<>();
    static List<List<Bitmap>> listDeletedBitmaps = new ArrayList<>();
    static List<List<Bitmap>> listDeletedBitmapsRedStroke = new ArrayList<>();
    public static List<GbcImage> finalListImages = new ArrayList<>();
    static List<List<Bitmap>> listActiveBitmaps = new ArrayList<>();
    public static List<Bitmap> finalListBitmaps = new ArrayList<>();
    static List<GbcImage> lastSeenImage = new ArrayList<>();
    static List<Bitmap> lastSeenBitmap = new ArrayList<>();
    static LinkedHashMap<GbcImage, Bitmap> importedImagesHashUsb = new LinkedHashMap<>();
    boolean isPhotoSave = false;
    List saveTypes = new ArrayList();
    private int picNRecReportedLastImageNumber = 0;
    private int picNRecEffectiveLastImageIndex = 0;
    private int picNRecPreviewImageNumber = PicNRecCommands.FIRST_IMAGE_SLOT;
    private PendingUsbAction pendingUsbAction = PendingUsbAction.NONE;
    private boolean usbReceiverRegistered = false;
    private PicoGbSerialCommands.ReceivePicoGbSerialAsyncTask picoGbSerialReceiveTask;
    private boolean picoGbSerialActive = false;
    private int picoGbSerialCaptureCount = 0;
    private PicoGbSerialCommands.StreamParser picoGbSerialStreamParser;
    private final List<Integer> picoGbSerialSelectedImages = new ArrayList<>();
    private CustomGridViewAdapterImage picoGbSerialBufferAdapter;
    private final Handler picoGbSerialClearHandler = new Handler();

    private enum PendingUsbAction {
        NONE,
        GBX_MODE,
        READ_ROM_NAME,
        READ_RAM,
        FULL_ROM,
        PICNREC_MODE,
        READ_PICNREC,
        PICO_GB_SERIAL_MODE
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) {
                return;
            }

            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                pendingUsbAction = PendingUsbAction.NONE;
                tv.setText("USB permission denied");
                toast(requireContext(), "USB permission denied");
                return;
            }

            resumePendingUsbAction();
        }
    };
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usb_serial, container, false);
        StaticValues.currentFragment = StaticValues.CURRENT_FRAGMENT.USB_SERIAL;

        gridView = view.findViewById(R.id.gridView);
        gridView.setNumColumns(2);//To see the images bigger in case there is corruption extracting with gbxcart
        tv = view.findViewById(R.id.textV);
        tvMode = view.findViewById(R.id.tvMode);
        rbGroup = view.findViewById(R.id.rbGroup);
        tv.setMovementMethod(new ScrollingMovementMethod());
        btnSave = view.findViewById(R.id.btnSave);
        cbLastSeen = view.findViewById(R.id.cbLastSeen);
        cbDeleted = view.findViewById(R.id.cbDeletedImages);
        layoutCb = view.findViewById(R.id.layout_cb);
        layoutPicNRecControls = view.findViewById(R.id.layoutPicNRecControls);
        layoutPicoGbSerialControls = view.findViewById(R.id.layoutPicoGbSerialControls);
        cbPicoGbSerialAutoImport = view.findViewById(R.id.cbPicoGbSerialAutoImport);
        cbPicoGbSerialAutoDeleteDuplicates = view.findViewById(R.id.cbPicoGbSerialAutoDeleteDuplicates);
        etPicoGbSerialImportTag = view.findViewById(R.id.etPicoGbSerialImportTag);
        btnImportPicoGbSerialBuffer = view.findViewById(R.id.btnImportPicoGbSerialBuffer);
        btnClearPicoGbSerialBuffer = view.findViewById(R.id.btnClearPicoGbSerialBuffer);
        btnSelectAllPicoGbSerialBuffer = view.findViewById(R.id.btnSelectAllPicoGbSerialBuffer);
        ivPicoGbSerialLastPreview = view.findViewById(R.id.ivPicoGbSerialLastPreview);
        tvPicNRecDeviceInfo = view.findViewById(R.id.tvPicNRecDeviceInfo);
        tvPicNRecPreviewStatus = view.findViewById(R.id.tvPicNRecPreviewStatus);
        etPicNRecStart = view.findViewById(R.id.etPicNRecStart);
        etPicNRecEnd = view.findViewById(R.id.etPicNRecEnd);
        sbPicNRecPreview = view.findViewById(R.id.sbPicNRecPreview);
        ivPicNRecPreview = view.findViewById(R.id.ivPicNRecPreview);
        btnPicNRecStartFirst = view.findViewById(R.id.btnPicNRecStartFirst);
        btnPicNRecStartCurrent = view.findViewById(R.id.btnPicNRecStartCurrent);
        btnPicNRecEndCurrent = view.findViewById(R.id.btnPicNRecEndCurrent);
        btnPicNRecEndLast = view.findViewById(R.id.btnPicNRecEndLast);
        btnPicNRecPreviewMinus = view.findViewById(R.id.btnPicNRecPreviewMinus);
        btnPicNRecPreviewPlus = view.findViewById(R.id.btnPicNRecPreviewPlus);
        btnPicNRecPreviewLast = view.findViewById(R.id.btnPicNRecPreviewLast);
        btnPicNRecPreviewMinusTen = view.findViewById(R.id.btnPicNRecPreviewMinusTen);
        btnPicNRecPreviewPlusTen = view.findViewById(R.id.btnPicNRecPreviewPlusTen);
        btnPicNRecPreviewFirst = view.findViewById(R.id.btnPicNRecPreviewFirst);
        spSaveType = view.findViewById(R.id.sp_save_type_usb);

        saveTypes.add("International");
        saveTypes.add("Japanese");
        saveTypes.add("Hello Kitty");

        ArrayAdapter<String> adapterSaveType = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, saveTypes);
        adapterSaveType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSaveType.setAdapter(adapterSaveType);

        saveTypeIntJpHk = Utils.SAVE_TYPE_INT_JP_HK.INT;
        spSaveType.setSelection(0);
        spSaveType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveTypeIntJpHk = Utils.SAVE_TYPE_INT_JP_HK.valueOf(saveTypeNames.get(saveTypes.get(position)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        btnFullRom = view.findViewById(R.id.btnFullRom);
        btnReadRomName = view.findViewById(R.id.btnReadRom);
        btnReadRam = view.findViewById(R.id.btnReadRam);
        btnReadPicNRec = view.findViewById(R.id.btnReadPicNRec);

        btnPrintBanner = view.findViewById(R.id.btnPrintBanner);
        btnAddImages = view.findViewById(R.id.btnAddImages);
        btnDelSav = view.findViewById(R.id.btnDelSav);
        btnDelete = view.findViewById(R.id.btnDelete);
        btnDecode = view.findViewById(R.id.btnDecode);


        rbApe = view.findViewById(R.id.rbApe);
        rbGbx = view.findViewById(R.id.rbGbx);
        rbPicNRec = view.findViewById(R.id.rbPicNRec);
        rbPicoGbSerial = view.findViewById(R.id.rbPicoGbSerial);
        rbPrint = view.findViewById(R.id.rbPrint);

        List<Integer> sizesInteger = new ArrayList<>();
        sizesInteger.add(0);
        sizesInteger.add(20);
        sizesInteger.add(40);
        sizesInteger.add(80);
        sizesInteger.add(100);
        sizesInteger.add(120);
        sizesInteger.add(150);
        sizesInteger.add(151);
        sizesInteger.add(155);
        sizesInteger.add(160);
        sizesInteger.add(200);
        sizesInteger.add(300);

        List<String> sizes = new ArrayList<>();
        sizes.add("0");
        sizes.add("20");
        sizes.add("40");
        sizes.add("80");
        sizes.add("100");
        sizes.add("120");
        sizes.add("150");
        sizes.add("151");
        sizes.add("155");
        sizes.add("160");
        sizes.add("200");
        sizes.add("300");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, sizes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        cbLastSeen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImages(cbLastSeen, cbDeleted);
            }
        });
        cbDeleted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImages(cbLastSeen, cbDeleted);
            }
        });

        btnPrintBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //PRINT IMAGE
                PrintOverArduino printOverArduino = new PrintOverArduino();
                printOverArduino.banner = true;
                try {
                    List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                    if (availableDrivers.isEmpty()) {
                        return;
                    }
                    UsbSerialDriver driver = availableDrivers.get(0);
                    printOverArduino.sendThreadDelay(connection, driver.getDevice(), tv, null);
                } catch (Exception e) {
                    tv.append(e.toString());
                    Toast toast = Toast.makeText(getContext(), getString(R.string.error_print_image) + e.toString(), Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });

        btnDelSav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                String title = isRomExtracted ? getString(R.string.delete_photo_folder_dialog) : getString(R.string.delete_sav_dialog);
                builder.setTitle(title);
                String fileToDelete = isRomExtracted ? "folder " + photoFolder.getName() : latestFile.getName();
                builder.setMessage(getString(R.string.sure_delete_sav) + fileToDelete + "?");
                builder.setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Try to delete the file
                        if (isRomExtracted) {
                            if (deleteFolderRecursive(photoFolder)) {
                                toast(getContext(), "PHOTO FOLDER DELETED");
                                tv.setText(getString(R.string.deleted_sav) + photoFolder.getName());

                            } else {
                                toast(getContext(), "COULDNT DELETE PHOTO FOLDER");
                            }
                        } else {
                            if (latestFile.delete()) {
                                toast(getContext(), getString(R.string.toast_sav_deleted));
                                tv.setText(getString(R.string.deleted_sav) + latestFile.getName());

                            } else {
                                toast(getContext(), getString(R.string.toast_couldnt_delete_sav));
                            }
                        }
                        btnAddImages.setVisibility(View.GONE);
                        btnDelSav.setVisibility(View.GONE);
                        layoutCb.setVisibility(View.GONE);
                        listActiveBitmaps.clear();
                        listActiveImages.clear();
                        listDeletedBitmaps.clear();
                        listDeletedImages.clear();
                        listDeletedBitmapsRedStroke.clear();
                        showImages(cbLastSeen, cbDeleted);
                    }
                });
                builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                //Show the dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        btnAddImages.setOnClickListener(v -> {
            try {
                numImagesAdded = 0;
                List<GbcImage> newGbcImages = new ArrayList<>();
                List<Bitmap> listNewBitmaps = new ArrayList<>();
                List<String> checkDuplicatedImport = new ArrayList<>();
                for (int i = 0; i < finalListImages.size(); i++) {
                    GbcImage gbcImage = finalListImages.get(i);
                    boolean alreadyAdded = false;
                    //If the image already exists (by the hash) it doesn't add it. Same if it's already added
                    for (GbcImage image : Utils.gbcImagesList) {
                        if (image.getHashCode().equals(gbcImage.getHashCode())) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    for (String hashDup : checkDuplicatedImport) {
                        if (hashDup.equals(gbcImage.getHashCode())) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        newGbcImages.add(gbcImage);
                        listNewBitmaps.add(finalListBitmaps.get(i));
                        checkDuplicatedImport.add(gbcImage.getHashCode());
                    }
                }

                if (newGbcImages.size() > 0) {
                    DocumentFile documentFile = null;
                    if (latestFile != null){
                        documentFile = DocumentFile.fromFile(latestFile);
                    }
                    ImagesImportDialog imagesImportDialog = new ImagesImportDialog(newGbcImages, listNewBitmaps, documentFile, getContext(), getActivity(), tv, numImagesAdded);
                    imagesImportDialog.createImagesImportDialog();
                } else {
                    tv.setText(getString(R.string.no_new_images));
                    toast(getContext(), getString(R.string.no_new_images));
                }
            } catch (Exception e) {
                tv.setText("Error en btn add\n" + e.toString());
                e.printStackTrace();
            }
        });

        btnSave.setOnClickListener(v -> saveTv());

        btnDelete.setOnClickListener(v -> tv.setText(""));

        btnDecode.setOnClickListener(v -> {
            //Method to decode the textview data
            extractedImagesBitmaps.clear();
            extractedImagesList.clear();
            try {
                extractHexImages(tv.getText().toString());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            CustomGridViewAdapterImage customGridViewAdapterImage = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, finalListBitmaps, true, true, false, null);
            gridView.setAdapter(customGridViewAdapterImage);
            tv.append(finalListImages.size() + " " + getString(R.string.images));
            btnAddImages.setVisibility(View.VISIBLE);
        });
        btnFullRom.setOnClickListener(v -> {
            isRomExtracted = true;
            btnDelSav.setText(getString(R.string.delete_folder));

            btnAddImages.setVisibility(View.GONE);
            extractedImagesList.clear();
            extractedImagesBitmaps.clear();
            fullRomFileList.clear();
            if (!ensureGbxConnection(PendingUsbAction.FULL_ROM)) {
                return;
            }
            fullRomDump();
        });

        btnReadRomName.setOnClickListener(v -> {
            if (!ensureGbxConnection(PendingUsbAction.READ_ROM_NAME)) {
                return;
            }
            completeReadRomName();
        });

        btnReadRam.setOnClickListener(v -> {
            btnDelSav.setText(getString(R.string.btn_delete_sav));

            isRomExtracted = false;
            btnAddImages.setVisibility(View.GONE);
            extractedImagesList.clear();
            extractedImagesBitmaps.clear();
            if (!ensureGbxConnection(PendingUsbAction.READ_RAM)) {
                return;
            }
            completeRamDump();
        });

        btnReadPicNRec.setOnClickListener(v -> {
            try {
                if (port == null || !port.isOpen()) {
                    if (!ensurePicNRecConnection(PendingUsbAction.READ_PICNREC)) {
                        return;
                    }
                }
                String rangeError = getPicNRecRangeError();
                if (!rangeError.isEmpty()) {
                    tv.setText(getString(R.string.picnrec_range_invalid) + rangeError);
                    return;
                }
                btnAddImages.setVisibility(View.GONE);
                listActiveImages.clear();
                listActiveBitmaps.clear();
                listDeletedImages.clear();
                listDeletedBitmaps.clear();
                listDeletedBitmapsRedStroke.clear();
                finalListImages.clear();
                finalListBitmaps.clear();
                new PicNRecCommands.ReadPicNRecAsyncTask(port, getContext(), tv, parsePicNRecNumber(etPicNRecStart), parsePicNRecNumber(etPicNRecEnd)).execute();
            } catch (Exception e) {
                tv.setText(getString(R.string.picnrec_error) + e.toString());
                Toast toast = Toast.makeText(getContext(), getString(R.string.picnrec_error) + e.toString(), Toast.LENGTH_LONG);
                toast.show();
            }
        });

        rbApe.setOnClickListener(v -> arduinoPrinterMode());
        rbPrint.setOnClickListener(v -> printOverArduinoMode());
        rbGbx.setOnClickListener(v -> gbxMode());
        rbPicNRec.setOnClickListener(v -> picNRecMode());
        rbPicoGbSerial.setOnClickListener(v -> picoGbSerialMode());
        btnImportPicoGbSerialBuffer.setOnClickListener(v -> importPicoGbSerialBuffer());
        btnClearPicoGbSerialBuffer.setOnClickListener(v -> clearPicoGbSerialBuffer());
        btnSelectAllPicoGbSerialBuffer.setOnClickListener(v -> selectAllPicoGbSerialBuffer());
        gridView.setOnItemClickListener((parent, itemView, position, id) -> {
            if (!picoGbSerialActive || position < 0 || position >= finalListImages.size()) {
                return;
            }
            if (picoGbSerialSelectedImages.contains(position)) {
                picoGbSerialSelectedImages.remove(Integer.valueOf(position));
            } else {
                picoGbSerialSelectedImages.add(position);
            }
            refreshPicoGbSerialBuffer();
        });

        btnPicNRecStartFirst.setOnClickListener(v -> etPicNRecStart.setText(String.valueOf(PicNRecCommands.FIRST_IMAGE_SLOT)));
        btnPicNRecStartCurrent.setOnClickListener(v -> etPicNRecStart.setText(String.valueOf(picNRecPreviewImageNumber)));
        btnPicNRecEndCurrent.setOnClickListener(v -> etPicNRecEnd.setText(String.valueOf(picNRecPreviewImageNumber)));
        btnPicNRecEndLast.setOnClickListener(v -> etPicNRecEnd.setText(String.valueOf(getPicNRecUiLastImageNumber())));
        btnPicNRecPreviewMinus.setOnClickListener(v -> setPicNRecPreviewImageNumber(picNRecPreviewImageNumber - 1, true));
        btnPicNRecPreviewPlus.setOnClickListener(v -> setPicNRecPreviewImageNumber(picNRecPreviewImageNumber + 1, true));
        btnPicNRecPreviewLast.setOnClickListener(v -> setPicNRecPreviewImageNumber(getPicNRecUiLastImageNumber(), true));
        btnPicNRecPreviewMinusTen.setOnClickListener(v -> setPicNRecPreviewImageNumber(picNRecPreviewImageNumber - 10, true));
        btnPicNRecPreviewPlusTen.setOnClickListener(v -> setPicNRecPreviewImageNumber(picNRecPreviewImageNumber + 10, true));
        btnPicNRecPreviewFirst.setOnClickListener(v -> setPicNRecPreviewImageNumber(PicNRecCommands.FIRST_IMAGE_SLOT, true));
        sbPicNRecPreview.setMax(PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX - PicNRecCommands.FIRST_IMAGE_SLOT);
        sbPicNRecPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setPicNRecPreviewImageNumber(PicNRecCommands.FIRST_IMAGE_SLOT + progress, false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setPicNRecPreviewImageNumber(PicNRecCommands.FIRST_IMAGE_SLOT + seekBar.getProgress(), true);
            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!usbReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            ContextCompat.registerReceiver(requireContext(), usbPermissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            usbReceiverRegistered = true;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (usbReceiverRegistered) {
            requireContext().unregisterReceiver(usbPermissionReceiver);
            usbReceiverRegistered = false;
        }
        stopPicoGbSerialReceiving();
    }

    private void resumePendingUsbAction() {
        PendingUsbAction action = pendingUsbAction;
        pendingUsbAction = PendingUsbAction.NONE;

        switch (action) {
            case GBX_MODE:
                gbxMode();
                break;
            case READ_ROM_NAME:
                if (ensureGbxConnection(PendingUsbAction.READ_ROM_NAME)) {
                    completeReadRomName();
                }
                break;
            case READ_RAM:
                if (ensureGbxConnection(PendingUsbAction.READ_RAM)) {
                    completeRamDump();
                }
                break;
            case FULL_ROM:
                if (ensureGbxConnection(PendingUsbAction.FULL_ROM)) {
                    fullRomDump();
                }
                break;
            case PICNREC_MODE:
                picNRecMode();
                break;
            case READ_PICNREC:
                if (ensurePicNRecConnection(PendingUsbAction.READ_PICNREC)) {
                    new PicNRecCommands.ReadPicNRecAsyncTask(port, getContext(), tv, parsePicNRecNumber(etPicNRecStart), parsePicNRecNumber(etPicNRecEnd)).execute();
                }
                break;
            case PICO_GB_SERIAL_MODE:
                picoGbSerialMode();
                break;
            case NONE:
            default:
                break;
        }
    }

    private boolean ensureGbxConnection(PendingUsbAction actionOnPermission) {
        pendingUsbAction = actionOnPermission;
        try {
            connect();
            if (usbIoManager != null) {
                usbIoManager.stop();
            }
            port.setParameters(BAUDRATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);
            GBxCartCommands.readFirmwareInfo(port);
            pendingUsbAction = PendingUsbAction.NONE;
            return true;
        } catch (IllegalStateException e) {
            tv.setText(e.getMessage());
            return false;
        } catch (Exception e) {
            pendingUsbAction = PendingUsbAction.NONE;
            tv.setText("Error in CONNECT\n" + e);
            Toast.makeText(getContext(), "Error in connect." + e, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean ensurePicNRecConnection(PendingUsbAction actionOnPermission) {
        pendingUsbAction = actionOnPermission;
        try {
            connect();
            if (port == null) {
                throw new IllegalStateException("No USB serial device found");
            }
            if (usbIoManager != null) {
                usbIoManager.stop();
            }
            port.setParameters(PicNRecCommands.DEFAULT_BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);
            PicNRecCommands.flushInput(port);
            tv.append(getString(R.string.tv_connected));
            pendingUsbAction = PendingUsbAction.NONE;
            return true;
        } catch (IllegalStateException e) {
            tv.setText(e.getMessage());
            return false;
        } catch (Exception e) {
            pendingUsbAction = PendingUsbAction.NONE;
            tv.setText(getString(R.string.picnrec_error) + e);
            Toast.makeText(getContext(), getString(R.string.picnrec_error) + e, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean ensurePicoGbSerialConnection(PendingUsbAction actionOnPermission) {
        pendingUsbAction = actionOnPermission;
        try {
            connect();
            if (port == null) {
                throw new IllegalStateException("No USB serial device found");
            }
            if (usbIoManager != null) {
                usbIoManager.stop();
            }
            try {
                port.setParameters(PicoGbSerialCommands.DEFAULT_BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (Exception e) {
                tv.append("\n" + getString(R.string.pico_gb_serial_control_warning) + e.toString());
            }
            try {
                port.setDTR(true);
            } catch (Exception e) {
                tv.append("\n" + getString(R.string.pico_gb_serial_control_warning) + e.toString());
            }
            try {
                port.setRTS(true);
            } catch (Exception e) {
                tv.append("\n" + getString(R.string.pico_gb_serial_control_warning) + e.toString());
            }
            tv.append(getString(R.string.tv_connected));
            pendingUsbAction = PendingUsbAction.NONE;
            return true;
        } catch (IllegalStateException e) {
            tv.setText(e.getMessage());
            return false;
        } catch (Exception e) {
            pendingUsbAction = PendingUsbAction.NONE;
            tv.setText(getString(R.string.pico_gb_serial_error) + e);
            Toast.makeText(getContext(), getString(R.string.pico_gb_serial_error) + e, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public void arduinoPrinterMode() {
        try {
            gbxMode = false;
            hidePicNRecControls();
            hidePicoGbSerialControls();
            tvMode.setVisibility(View.VISIBLE);
            tvMode.setText(getString(R.string.arduino_mode));
            rbGroup.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);
            btnDecode.setVisibility(View.VISIBLE);
            btnPrintBanner.setVisibility(View.GONE);
            ape = true;
            connect();
            port.setDTR(true);
            port.setRTS(true);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbIoManager.start();
            tv.append(getString(R.string.tv_connected));
        } catch (Exception e) {
            Toast toast = Toast.makeText(getContext(), getString(R.string.error_arduino) + e.toString(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void printOverArduinoMode() {
        try {
            StaticValues.printingEnabled = true;
            gbxMode = false;
            ape = false;
            hidePicNRecControls();
            hidePicoGbSerialControls();
            tvMode.setVisibility(View.VISIBLE);
            tvMode.setText(getString(R.string.print_mode));
            rbGroup.setVisibility(View.GONE);
            btnPrintBanner.setVisibility(View.VISIBLE);
            connect();
            port.setDTR(true);
            port.setRTS(true);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbIoManager.start();
            tv.append(getString(R.string.tv_connected));
        } catch (Exception e) {
            Toast toast = Toast.makeText(getContext(), getString(R.string.error_arduino) + e.toString(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void gbxMode() {
        gbxMode = true;
        ape = false;
        hidePicNRecControls();
        hidePicoGbSerialControls();
        tvMode.setText(getString(R.string.gbxcart_mode));
        tvMode.setVisibility(View.VISIBLE);
        rbGroup.setVisibility(View.GONE);
        btnReadRam.setVisibility(View.VISIBLE);
        spSaveType.setVisibility(View.VISIBLE);
        btnReadRomName.setVisibility(View.VISIBLE);
        if (!ensureGbxConnection(PendingUsbAction.GBX_MODE)) {
            return;
        }
        completeReadRomName();
    }

    private void picNRecMode() {
        gbxMode = false;
        ape = false;
        stopPicoGbSerialReceiving();
        hidePicoGbSerialControls();
        tvMode.setText(getString(R.string.picnrec_mode));
        tvMode.setVisibility(View.VISIBLE);
        rbGroup.setVisibility(View.GONE);
        btnReadPicNRec.setVisibility(View.VISIBLE);
        layoutPicNRecControls.setVisibility(View.VISIBLE);
        btnReadRam.setVisibility(View.GONE);
        btnReadRomName.setVisibility(View.GONE);
        btnFullRom.setVisibility(View.GONE);
        spSaveType.setVisibility(View.GONE);
        btnAddImages.setVisibility(View.GONE);
        btnDelSav.setVisibility(View.GONE);
        layoutCb.setVisibility(View.GONE);
        if (!ensurePicNRecConnection(PendingUsbAction.PICNREC_MODE)) {
            return;
        }
        detectPicNRecDevice();
    }

    private void picoGbSerialMode() {
        gbxMode = false;
        ape = false;
        hidePicNRecControls();
        tvMode.setText(getString(R.string.pico_gb_serial_mode));
        tvMode.setVisibility(View.VISIBLE);
        rbGroup.setVisibility(View.GONE);
        layoutPicoGbSerialControls.setVisibility(View.VISIBLE);
        btnReadRam.setVisibility(View.GONE);
        btnReadRomName.setVisibility(View.GONE);
        btnFullRom.setVisibility(View.GONE);
        btnReadPicNRec.setVisibility(View.GONE);
        spSaveType.setVisibility(View.GONE);
        btnAddImages.setVisibility(View.GONE);
        btnDelSav.setVisibility(View.GONE);
        layoutCb.setVisibility(View.GONE);
        finalListImages.clear();
        finalListBitmaps.clear();
        picoGbSerialSelectedImages.clear();
        refreshPicoGbSerialBuffer();
        if (!ensurePicoGbSerialConnection(PendingUsbAction.PICO_GB_SERIAL_MODE)) {
            return;
        }
        startPicoGbSerialReceiving();
    }

    private void connectPicNRecSerial() {
        if (!ensurePicNRecConnection(PendingUsbAction.PICNREC_MODE)) {
            throw new IllegalStateException("USB permission requested. Try again after accepting it.");
        }
    }

    private void hidePicNRecControls() {
        if (layoutPicNRecControls != null) {
            layoutPicNRecControls.setVisibility(View.GONE);
        }
        if (btnReadPicNRec != null) {
            btnReadPicNRec.setVisibility(View.GONE);
        }
    }

    private void hidePicoGbSerialControls() {
        if (layoutPicoGbSerialControls != null) {
            layoutPicoGbSerialControls.setVisibility(View.GONE);
        }
    }

    private void startPicoGbSerialReceiving() {
        stopPicoGbSerialReceiving();
        tv.setText(getString(R.string.pico_gb_serial_contacting));
        picoGbSerialActive = true;
        picoGbSerialCaptureCount = 0;
        picoGbSerialStreamParser = new PicoGbSerialCommands.StreamParser();
        usbIoManager = new SerialInputOutputManager(port, this);
        usbIoManager.start();
        tv.setText(getString(R.string.pico_gb_serial_receiving_no_status));
    }

    private void stopPicoGbSerialReceiving() {
        picoGbSerialActive = false;
        picoGbSerialClearHandler.removeCallbacksAndMessages(null);
        if (picoGbSerialReceiveTask != null) {
            picoGbSerialReceiveTask.cancel(true);
            picoGbSerialReceiveTask = null;
        }
        if (usbIoManager != null) {
            usbIoManager.stop();
        }
    }

    private void schedulePicoGbSerialClear() {
        picoGbSerialClearHandler.removeCallbacksAndMessages(null);
        picoGbSerialClearHandler.postDelayed(() -> {
            if (!picoGbSerialActive || port == null || !port.isOpen()) {
                return;
            }
            try {
                port.write("CLEAR\n".getBytes(), 1000);
                tv.append("\n" + getString(R.string.pico_gb_serial_cleared));
            } catch (Exception e) {
                tv.append("\n" + getString(R.string.pico_gb_serial_clear_failed) + e.toString());
            }
        }, 500);
    }

    private void importPicoGbSerialBuffer() {
        List<GbcImage> images = new ArrayList<>();
        List<Bitmap> bitmaps = new ArrayList<>();
        if (picoGbSerialSelectedImages.isEmpty()) {
            images.addAll(finalListImages);
            bitmaps.addAll(finalListBitmaps);
        } else {
            for (int index : picoGbSerialSelectedImages) {
                if (index >= 0 && index < finalListImages.size()) {
                    images.add(finalListImages.get(index));
                    bitmaps.add(finalListBitmaps.get(index));
                }
            }
        }
        if (importPicoGbSerialImages(images, bitmaps, false, true, false)) {
            if (picoGbSerialSelectedImages.isEmpty()) {
                finalListImages.clear();
                finalListBitmaps.clear();
            } else {
                removeSelectedPicoGbSerialImages();
            }
            refreshPicoGbSerialBuffer();
        }
    }

    private boolean importPicoGbSerialImages(List<GbcImage> images, List<Bitmap> bitmaps, boolean clearImportedFromBuffer, boolean importDuplicates, boolean quiet) {
        List<GbcImage> newGbcImages = new ArrayList<>();
        List<Bitmap> newBitmaps = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            GbcImage image = images.get(i);
            boolean alreadyAdded = false;
            for (GbcImage existingImage : Utils.gbcImagesList) {
                if (existingImage.getHashCode().equals(image.getHashCode())) {
                    alreadyAdded = true;
                    break;
                }
            }
            for (String hash : hashes) {
                if (hash.equals(image.getHashCode())) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                newGbcImages.add(image);
                newBitmaps.add(bitmaps.get(i));
                hashes.add(image.getHashCode());
            } else if (importDuplicates) {
                GbcImage duplicateImage = image.clone();
                duplicateImage.setHashCode(createPicoGbSerialDuplicateHash(image.getHashCode(), newGbcImages));
                duplicateImage.setTags(new HashSet<>(image.getTags()));
                duplicateImage.addTag(FILTER_DUPLICATED);
                newGbcImages.add(duplicateImage);
                Bitmap bitmap = bitmaps.get(i);
                Bitmap.Config config = bitmap.getConfig() == null ? Bitmap.Config.ARGB_8888 : bitmap.getConfig();
                newBitmaps.add(bitmap.copy(config, true));
                hashes.add(duplicateImage.getHashCode());
            }
        }

        if (newGbcImages.isEmpty()) {
            tv.setText(getString(R.string.no_new_images));
            toast(getContext(), getString(R.string.no_new_images));
            return false;
        }

        applyPicoGbSerialImportTag(newGbcImages);

        LoadingDialog saveDialog = quiet ? null : new LoadingDialog(getContext(), getString(R.string.load_saving_images));
        if (saveDialog != null) {
            saveDialog.showDialog();
        }
        new SaveImageAsyncTask(newGbcImages, newBitmaps, getContext(), quiet ? null : tv, 0, null, saveDialog, quiet).execute();
        if (clearImportedFromBuffer) {
            finalListImages.clear();
            finalListBitmaps.clear();
            refreshPicoGbSerialBuffer();
        }
        return true;
    }

    private void applyPicoGbSerialImportTag(List<GbcImage> images) {
        String tag = etPicoGbSerialImportTag.getText().toString().trim();
        if (tag.isEmpty()) {
            return;
        }
        for (GbcImage image : images) {
            image.addTag(tag);
        }
    }

    private void clearPicoGbSerialBuffer() {
        if (picoGbSerialSelectedImages.isEmpty()) {
            finalListImages.clear();
            finalListBitmaps.clear();
        } else {
            removeSelectedPicoGbSerialImages();
        }
        refreshPicoGbSerialBuffer();
    }

    private void selectAllPicoGbSerialBuffer() {
        if (!picoGbSerialSelectedImages.isEmpty()) {
            picoGbSerialSelectedImages.clear();
            refreshPicoGbSerialBuffer();
            return;
        }
        picoGbSerialSelectedImages.clear();
        for (int i = 0; i < finalListImages.size(); i++) {
            picoGbSerialSelectedImages.add(i);
        }
        refreshPicoGbSerialBuffer();
    }

    private void removeSelectedPicoGbSerialImages() {
        Collections.sort(picoGbSerialSelectedImages, Collections.reverseOrder());
        for (int index : picoGbSerialSelectedImages) {
            if (index >= 0 && index < finalListImages.size()) {
                finalListImages.remove(index);
                finalListBitmaps.remove(index);
            }
        }
        picoGbSerialSelectedImages.clear();
    }

    private void refreshPicoGbSerialBuffer() {
        picoGbSerialSelectedImages.removeIf(index -> index < 0 || index >= finalListImages.size());
        boolean hasSelection = !picoGbSerialSelectedImages.isEmpty();
        if (btnImportPicoGbSerialBuffer != null) {
            btnImportPicoGbSerialBuffer.setText(hasSelection ? R.string.pico_gb_serial_import_selected : R.string.pico_gb_serial_import_buffer);
        }
        if (btnClearPicoGbSerialBuffer != null) {
            btnClearPicoGbSerialBuffer.setText(hasSelection ? R.string.pico_gb_serial_clear_selected : R.string.pico_gb_serial_clear_buffer);
        }
        if (btnSelectAllPicoGbSerialBuffer != null) {
            btnSelectAllPicoGbSerialBuffer.setText(hasSelection ? R.string.pico_gb_serial_select_none : R.string.pico_gb_serial_select_all);
        }
        if (picoGbSerialBufferAdapter == null) {
            picoGbSerialBufferAdapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, finalListBitmaps, true, true, true, picoGbSerialSelectedImages, true);
            gridView.setAdapter(picoGbSerialBufferAdapter);
        } else {
            picoGbSerialBufferAdapter.notifyDataSetChanged();
        }
    }

    private boolean shouldDropPicoGbSerialDuplicate(GbcImage image) {
        if (!cbPicoGbSerialAutoDeleteDuplicates.isChecked()) {
            return false;
        }
        for (GbcImage existingImage : Utils.gbcImagesList) {
            if (existingImage.getHashCode().equals(image.getHashCode())) {
                return true;
            }
        }
        for (GbcImage bufferedImage : finalListImages) {
            if (bufferedImage.getHashCode().equals(image.getHashCode())) {
                return true;
            }
        }
        return false;
    }

    private String createPicoGbSerialDuplicateHash(String originalHash, List<GbcImage> pendingImages) {
        int suffix = 1;
        String candidate;
        do {
            candidate = originalHash + "-dup-" + suffix++;
        } while (picoGbSerialHashExists(candidate, pendingImages));
        return candidate;
    }

    private boolean picoGbSerialHashExists(String hash, List<GbcImage> pendingImages) {
        for (GbcImage existingImage : Utils.gbcImagesList) {
            if (existingImage.getHashCode().equals(hash)) {
                return true;
            }
        }
        for (GbcImage pendingImage : pendingImages) {
            if (pendingImage.getHashCode().equals(hash)) {
                return true;
            }
        }
        return false;
    }

    private void detectPicNRecDevice() {
        tv.setText(getString(R.string.picnrec_detecting));
        new PicNRecCommands.DetectPicNRecAsyncTask(port, new PicNRecCommands.DeviceInfoListener() {
            @Override
            public void onDeviceInfo(int reportedLastImageNumber) {
                picNRecReportedLastImageNumber = reportedLastImageNumber;
                picNRecEffectiveLastImageIndex = PicNRecCommands.getEffectiveLastImageIndex(reportedLastImageNumber);
                int defaultSlot = getPicNRecUiLastImageNumber();
                tv.setText("");
                tvPicNRecDeviceInfo.setText(getString(R.string.picnrec_device_info, defaultSlot, PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX));
                etPicNRecStart.setText(String.valueOf(PicNRecCommands.FIRST_IMAGE_SLOT));
                etPicNRecEnd.setText(String.valueOf(defaultSlot));
                setPicNRecPreviewImageNumber(defaultSlot, true);
            }

            @Override
            public void onDeviceInfoError(Exception exception) {
                tv.setText(getString(R.string.picnrec_error) + exception.toString());
            }
        }).execute();
    }

    private int parsePicNRecNumber(EditText editText) throws NumberFormatException {
        return Integer.parseInt(editText.getText().toString().trim());
    }

    private int getPicNRecUiLastImageNumber() {
        if (picNRecEffectiveLastImageIndex <= PicNRecCommands.FIRST_IMAGE_SLOT) {
            return PicNRecCommands.FIRST_IMAGE_SLOT;
        }
        return Math.max(PicNRecCommands.FIRST_IMAGE_SLOT, picNRecEffectiveLastImageIndex - 1);
    }

    private String getPicNRecRangeError() {
        int startImageNumber;
        int endImageNumber;
        try {
            startImageNumber = parsePicNRecNumber(etPicNRecStart);
            endImageNumber = parsePicNRecNumber(etPicNRecEnd);
        } catch (NumberFormatException e) {
            return getString(R.string.picnrec_range_whole_numbers);
        }

        if (startImageNumber < PicNRecCommands.FIRST_IMAGE_SLOT || startImageNumber > PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX) {
            return getString(R.string.picnrec_range_start_bounds);
        }

        if (endImageNumber < startImageNumber || endImageNumber > PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX) {
            return getString(R.string.picnrec_range_end_bounds);
        }

        if (picNRecReportedLastImageNumber >= PicNRecCommands.FIRST_IMAGE_SLOT
                && picNRecReportedLastImageNumber <= PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX
                && startImageNumber <= picNRecReportedLastImageNumber
                && endImageNumber >= picNRecReportedLastImageNumber) {
            return getString(R.string.picnrec_range_incomplete_slot) + " " + picNRecReportedLastImageNumber;
        }

        return "";
    }

    private void setPicNRecPreviewImageNumber(int imageNumber, boolean loadPreview) {
        int safeImageNumber = Math.min(Math.max(PicNRecCommands.FIRST_IMAGE_SLOT, imageNumber), PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX);
        if (safeImageNumber == picNRecReportedLastImageNumber) {
            if (imageNumber > picNRecPreviewImageNumber && safeImageNumber < PicNRecCommands.MAX_SUPPORTED_IMAGE_INDEX) {
                safeImageNumber++;
            } else if (safeImageNumber > PicNRecCommands.FIRST_IMAGE_SLOT) {
                safeImageNumber--;
            } else {
                safeImageNumber++;
            }
        }
        picNRecPreviewImageNumber = safeImageNumber;
        sbPicNRecPreview.setProgress(picNRecPreviewImageNumber - PicNRecCommands.FIRST_IMAGE_SLOT);
        tvPicNRecPreviewStatus.setText(getString(R.string.picnrec_preview_slot) + " " + picNRecPreviewImageNumber);
        if (loadPreview) {
            previewPicNRecImage();
        }
    }

    private void previewPicNRecImage() {
        try {
            if (port == null || !port.isOpen()) {
                connectPicNRecSerial();
            }
            if (picNRecPreviewImageNumber == picNRecReportedLastImageNumber) {
                tvPicNRecPreviewStatus.setText(getString(R.string.picnrec_range_incomplete_slot) + " " + picNRecReportedLastImageNumber);
                return;
            }
            new PicNRecCommands.PreviewPicNRecAsyncTask(port, getContext(), picNRecPreviewImageNumber, ivPicNRecPreview, tvPicNRecPreviewStatus).execute();
        } catch (Exception e) {
            tvPicNRecPreviewStatus.setText(getString(R.string.picnrec_preview_failed) + e.toString());
        }
    }

    public static boolean readSav(File file, byte[] saveBytes, int saveBank) {
        try {
            Extractor extractor = new SaveImageExtractor(new IndexedPalette(IndexedPalette.EVEN_DIST_PALETTE));

            //Check for Magic or FF bytes
            if (!magicIsReal(saveBytes)) {
                return false;
            }
            //Extract the images

            latestFile = file;
            extractedImagesList.clear();
            extractedImagesBitmaps.clear();
            if (file.length() / 1024 == 128) {
                importedImagesHashUsb = extractor.extractGbcImages(saveBytes, file.getName(), saveBank, saveTypeIntJpHk);
                for (HashMap.Entry<GbcImage, Bitmap> entry : importedImagesHashUsb.entrySet()) {
                    GbcImage gbcImage = entry.getKey();
                    Bitmap imageBitmap = entry.getValue();
                    ImageData imageData = new ImageData();
                    imageData.setImageId(gbcImage.getHashCode());
                    imageData.setData(gbcImage.getImageBytes());
                    extractedImagesBitmaps.add(imageBitmap);
                    extractedImagesList.add(gbcImage);
                }

                listActiveImages.add(new ArrayList<>(extractedImagesList.subList(0, extractedImagesList.size() - StaticValues.deletedCount[saveBank] - 1)));
                listActiveBitmaps.add(new ArrayList<>(extractedImagesBitmaps.subList(0, extractedImagesBitmaps.size() - StaticValues.deletedCount[saveBank] - 1)));
                lastSeenImage.add(extractedImagesList.get(extractedImagesList.size() - StaticValues.deletedCount[saveBank] - 1));
                lastSeenBitmap.add(extractedImagesBitmaps.get(extractedImagesBitmaps.size() - StaticValues.deletedCount[saveBank] - 1));
                listDeletedImages.add(new ArrayList<>(extractedImagesList.subList(extractedImagesList.size() - StaticValues.deletedCount[saveBank], extractedImagesList.size())));

                listDeletedBitmaps.add(new ArrayList<>(extractedImagesBitmaps.subList(extractedImagesBitmaps.size() - StaticValues.deletedCount[saveBank], extractedImagesBitmaps.size())));

                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStrokeWidth(2);
                int startX = 160;
                int startY = 0;
                int endX = 0;
                int endY = 144;
                listDeletedBitmapsRedStroke.add(new ArrayList<>());
                for (Bitmap bitmap : listDeletedBitmaps.get(saveBank)) {
                    Bitmap copiedBitmap = bitmap.copy(bitmap.getConfig(), true);//Need to get a copy of the original bitmap, or else I'll paint on it
                    Canvas canvas = new Canvas(copiedBitmap);
                    canvas.drawLine(startX, startY, endX, endY, paint);
                    listDeletedBitmapsRedStroke.get(saveBank).add(copiedBitmap);
                }
            } else {
                tv.append(gridView.getContext().getString(R.string.no_good_dump));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public static void readRomSavs() {
        listActiveImages.clear();
        listActiveBitmaps.clear();
        listDeletedImages.clear();
        listDeletedBitmaps.clear();
        listDeletedBitmapsRedStroke.clear();
        finalListBitmaps.clear();
        finalListImages.clear();
        lastSeenImage.clear();
        lastSeenBitmap.clear();

        tv.append(tv.getContext().getString(R.string.sav_parts) + fullRomFileList.size());
        try {
            for (int i = 0; i < fullRomFileList.size(); i++) {
                readSav(fullRomFileList.get(i), fullRomFileBytes.get(i), i);
            }

            btnAddImages.setVisibility(View.VISIBLE);
            btnDelSav.setVisibility(View.VISIBLE);
            layoutCb.setVisibility(View.VISIBLE);
            showImages(cbLastSeen, cbDeleted);
        } catch (Exception e) {
            e.printStackTrace();
            toast(tv.getContext(), "Error: " + e.toString());
        }
    }

    private void completeReadRomName() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOff(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.setCartType(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOn(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                romName = GBxCartCommands.ReadRomName(port, getContext(), tv);
                tv.setText(getString(R.string.rom_name) + romName);
                //Changing the Spinner if the rom is International, Japanese or Hello Kitty
            }
        }, 200);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOff(port, getContext());
            }
        }, 200);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (romName.startsWith("PHOTO")) {
                    btnFullRom.setVisibility(View.VISIBLE);
                    isPhotoSave = true;
                } else {
                    btnFullRom.setVisibility(View.GONE);
                    isPhotoSave = false;
                }

                //Select the spinner value if HK or JP
                if (romName.trim().equals("POCKETCAMERA")){
                    spSaveType.setSelection(saveTypes.indexOf("Japanese"));
                } else if (romName.trim().equals("POCKETCAMERA_SN")){
                    spSaveType.setSelection(saveTypes.indexOf("Hello Kitty"));
                }
            }
        }, 200);
    }

    private void fullRomDump() {
        tv.setText("");
        tv.append(getString(R.string.dumping_rom_wait));
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOff(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.setCartType(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOn(port, getContext());
            }
        }, 100);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new GBxCartCommands.ReadPHOTORomAsyncTask(port, getContext(), tv, fullRomFileList, fullRomFileBytes).execute();
            }
        }, 200);
    }

    private void completeRamDump() {

        Handler handlerRam = new Handler();
        handlerRam.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOff(port, getContext());
            }
        }, 100);
        handlerRam.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.setCartType(port, getContext());
            }
        }, 100);
        handlerRam.postDelayed(new Runnable() {
            @Override
            public void run() {
                GBxCartCommands.powerOn(port, getContext());
            }
        }, 100);
        handlerRam.postDelayed(new Runnable() {
            @Override
            public void run() {
                listActiveImages.clear();
                listActiveBitmaps.clear();
                listDeletedImages.clear();
                listDeletedBitmaps.clear();
                listDeletedBitmapsRedStroke.clear();

                new GBxCartCommands.ReadRamAsyncTask(port, getContext(), tv, latestFile).execute();
            }
        }, 200);
    }

    //Refactor
    public static void showImages(CheckBox showLastSeen, CheckBox showDeleted) {
        List<Bitmap> bitmapsAdapterList = new ArrayList<>();
        finalListImages.clear();
        finalListBitmaps.clear();
        if (!showLastSeen.isChecked() && !showDeleted.isChecked()) {
            for (List<GbcImage> gbcImageList : listActiveImages) {
                finalListImages.addAll(gbcImageList);
            }
            for (List<Bitmap> bitmapList : listActiveBitmaps) {
                finalListBitmaps.addAll(bitmapList);
            }

            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);

        } else if (showLastSeen.isChecked() && !showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.add(lastSeenImage.get(i));
            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                finalListBitmaps.add(lastSeenBitmap.get(i));
            }
            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);

        } else if (!showLastSeen.isChecked() && showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.addAll(listDeletedImages.get(i));
            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                bitmapsAdapterList.addAll(listActiveBitmaps.get(i));
                finalListBitmaps.addAll(listDeletedBitmaps.get(i));
                bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke.get(i));

            }
        } else if (showLastSeen.isChecked() && showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.add(lastSeenImage.get(i));
                finalListImages.addAll(listDeletedImages.get(i));

            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                bitmapsAdapterList.addAll(listActiveBitmaps.get(i));

                finalListBitmaps.add(lastSeenBitmap.get(i));
                bitmapsAdapterList.add(lastSeenBitmap.get(i));

                finalListBitmaps.addAll(listDeletedBitmaps.get(i));
                bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke.get(i));
            }
        }
        gridView.setAdapter((new CustomGridViewAdapterImage(showLastSeen.getContext(), R.layout.row_items, finalListImages, bitmapsAdapterList, true, true, false, null)));
    }

    private void saveTv() {
        String texto = tv.getText().toString();
        LocalDateTime now = null;
        DateTimeFormatter dtf = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dtf = DateTimeFormatter.ofPattern(dateLocale+"_HH-mm-ss");
            now = LocalDateTime.now();
        }
        String fileName = "hex_";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fileName += dtf.format(now) + ".txt";
        } else
            fileName += ".txt";
        File file = new File(Utils.ARDUINO_HEX_FOLDER, fileName);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Couldn't create dir: " + parent);
            }

        } catch (Exception e) {
            Toast toast = Toast.makeText(getContext(), getString(R.string.error_file) + e.toString(), Toast.LENGTH_SHORT);
            toast.show();
        }
        //I create the new directory if it doesn't exists
        try (FileOutputStream outputStream = new FileOutputStream(file); OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);) {

            outputStreamWriter.write(texto);
            Toast toast = Toast.makeText(getContext(), getString(R.string.saved_to_file), Toast.LENGTH_SHORT);
            toast.show();

        } catch (Exception e) {
            Toast toast = Toast.makeText(getContext(), getString(R.string.error_file) + e.toString(), Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private void connect() {
        manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        ProbeTable customTable = UsbSerialProber.getDefaultProbeTable();
        customTable.addProduct(PicoGbSerialCommands.PICO_VENDOR_ID, PicoGbSerialCommands.PICO_PRODUCT_ID, CdcAcmSerialDriver.class);
        List<UsbSerialDriver> availableDrivers = new UsbSerialProber(customTable).findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        if (!manager.hasPermission(device)) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            Intent permissionBroadcast = new Intent(ACTION_USB_PERMISSION).setPackage(requireContext().getPackageName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(getContext(), 0, permissionBroadcast, flags);
            manager.requestPermission(device, permissionIntent);
            throw new IllegalStateException("USB permission requested. Try again after accepting it.");
        }
        closeCurrentUsbConnection();
        connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            throw new IllegalArgumentException("Connection is null");
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            if (port.isOpen()) port.close();
            port.open(connection);
//            port.setParameters(BAUDRATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        } catch (Exception e) {
            tv.append(e.toString());
            Toast.makeText(getContext(), "Error in connect." + e.toString(), Toast.LENGTH_SHORT).show();
            throw new IllegalStateException(e);
        }

        //USE IN ARDUINO MODE ONLY
        usbIoManager = new SerialInputOutputManager(port, this);
    }

    private void closeCurrentUsbConnection() {
        try {
            if (usbIoManager != null) {
                usbIoManager.stop();
                usbIoManager = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (port != null && port.isOpen()) {
                port.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception ignored) {
        }
    }

    //For the Arduino Printer Emulator or Printing over arduino
    @Override
    public void onNewData(byte[] data) {
        if (picoGbSerialActive) {
            try {
                List<byte[]> payloads = picoGbSerialStreamParser.feed(data);
                if (payloads.isEmpty()) {
                    return;
                }
                List<PicoGbSerialCommands.DecodedCapture> captures = new ArrayList<>();
                for (byte[] payload : payloads) {
                    picoGbSerialCaptureCount++;
                    captures.add(PicoGbSerialCommands.decodeCapture(getContext(), payload, picoGbSerialCaptureCount));
                }
                getActivity().runOnUiThread(() -> {
                    int bufferedImageCount = 0;
                    int autoImportedImageCount = 0;
                    Bitmap lastReceivedBitmap = null;
                    for (PicoGbSerialCommands.DecodedCapture capture : captures) {
                        if (!capture.bitmaps.isEmpty()) {
                            lastReceivedBitmap = capture.bitmaps.get(capture.bitmaps.size() - 1);
                        }
                        if (cbPicoGbSerialAutoImport.isChecked()) {
                            if (importPicoGbSerialImages(capture.images, capture.bitmaps, false, false, true)) {
                                autoImportedImageCount += capture.images.size();
                            }
                        } else {
                            for (int i = 0; i < capture.images.size(); i++) {
                                GbcImage image = capture.images.get(i);
                                if (shouldDropPicoGbSerialDuplicate(image)) {
                                    continue;
                                }
                                finalListImages.add(image);
                                finalListBitmaps.add(capture.bitmaps.get(i));
                                bufferedImageCount++;
                            }
                        }
                    }
                    if (lastReceivedBitmap != null) {
                        ivPicoGbSerialLastPreview.setImageBitmap(lastReceivedBitmap);
                    }
                    refreshPicoGbSerialBuffer();
                    if (bufferedImageCount > 0) {
                        gridView.post(() -> gridView.setSelection(finalListImages.size() - 1));
                    }
                    int imageCount = 0;
                    for (PicoGbSerialCommands.DecodedCapture capture : captures) {
                        imageCount += capture.images.size();
                    }
                    tv.setText(getString(R.string.pico_gb_serial_received, picoGbSerialCaptureCount, cbPicoGbSerialAutoImport.isChecked() ? autoImportedImageCount : imageCount, finalListImages.size()));
                    schedulePicoGbSerialClear();
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> tv.setText(getString(R.string.pico_gb_serial_error) + e.toString()));
            }
            return;
        }
        if (ape) {
            String msg;
            msg = new String(data);

            getActivity().runOnUiThread(() -> {
                tv.append(msg);
            });
        } else {
            BigInteger bigInt = new BigInteger(1, data);
            String hexString = bigInt.toString(16);
            // Make sure the string has pair length
            if (hexString.length() % 2 != 0) {
                hexString = "0" + hexString;
            }

            // Format the string in blocks of 2 chars
            hexString = String.format("%0" + (hexString.length() + hexString.length() % 2) + "X", new BigInteger(hexString, 16));
            hexString = hexString.replaceAll("..", "$0 ");//To separate with spaces every hex byte
            String finalHexString = hexString;
            getActivity().runOnUiThread(() -> {
                tv.append(finalHexString);

            });
        }
    }

    //For the arduino printing function, try using the other
//    @Override
//    public void onNewData(byte[] data) {
//        //USE ON ARDUINO MODE ONLY
//        for (byte b : data) {
//            if (!String.format("%02X ", b).equals("00 ")) {
//                dataCreate.append(String.format("%02X ", b));
//            }
//        }
//        getActivity().runOnUiThread(() -> {
//            tv.append(dataCreate.toString());
//        });
//    }

    @Override
    public void onRunError(Exception e) {
    }

    public void extractHexImages(String fileContent) throws NoSuchAlgorithmException {
        finalListBitmaps.clear();
        finalListImages.clear();
        List<String> dataList = HexToTileData.separateData(fileContent);
        String data = "";
        int index = 1;
        for (String string : dataList) {
            data = string.replaceAll(System.lineSeparator(), " ");
            byte[] bytes = ImportFragment.convertToByteArray(data);
            GbcImage gbcImage = new GbcImage();
            gbcImage.setImageBytes(bytes);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            String hashHex = Utils.bytesToHex(hash);
            gbcImage.setHashCode(hashHex);
            ImageData imageData = new ImageData();
            imageData.setImageId(hashHex);
            imageData.setData(bytes);
            gbcImage.setName(index++ + "-" + " arduino");
            int height = (data.length() + 1) / 120;//To get the real height of the image
            ImageCodec imageCodec = new ImageCodec(160, height);
            Bitmap image = imageCodec.decodeWithPalette(Utils.hashPalettes.get(gbcImage.getPaletteId()).getPaletteColorsInt(), gbcImage.getImageBytes(), false);
            finalListBitmaps.add(image);
            finalListImages.add(gbcImage);
        }
    }

}
