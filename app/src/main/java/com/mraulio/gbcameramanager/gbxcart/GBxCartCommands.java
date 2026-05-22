package com.mraulio.gbcameramanager.gbxcart;

import static com.mraulio.gbcameramanager.utils.StaticValues.dateLocale;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.btnAddImages;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.btnDelSav;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.cbDeleted;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.cbLastSeen;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.layoutCb;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.readSav;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment.showImages;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialUtils.magicIsReal;

import android.content.Context;
import android.graphics.Bitmap;
import com.mraulio.gbcameramanager.utils.AppTask;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.gameboycameralib.constants.IndexedPalette;
import com.mraulio.gbcameramanager.ui.usbserial.UsbSerialFragment;
import com.mraulio.gbcameramanager.utils.Utils;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GBxCartCommands {
    //Class that contains the methods to communicate with the GBxCart
    //Translated from the code from Lesserkuma
    private static final String TAG = "GBxCartCommands";

    private static final int TIMEOUT = 2000;
        private static final int CAMERA_TIMEOUT = 10000;
        private static final int CAMERA_CLOCK_TOGGLES_PER_POLL = 200000;
        private static final int CAMERA_READ_CHUNK_SIZE = 64;
            private static final int CAMERA_LIVE_FRAME_ADDRESS = 0xA100;
            public static final int CAMERA_LIVE_FRAME_SIZE = 16 * 14 * 16;
        private static final int CAMERA_LIVE_FRAME_WIDTH = 128;
        private static final int CAMERA_LIVE_FRAME_HEIGHT = 112;
            public static final int MIN_EXPOSURE_MICROSECONDS = 256;
            public static final int MAX_EXPOSURE_MICROSECONDS = 1048560;
            public static final int MIN_VOLTAGE_OUT_MILLIVOLTS = -992;
            public static final int MAX_VOLTAGE_OUT_MILLIVOLTS = 992;
            public static final int VOLTAGE_OUT_STEP_MILLIVOLTS = 32;
            public static final int MIN_CONTRAST_LEVEL = 1;
            public static final int MAX_CONTRAST_LEVEL = 16;
            public static final int MAX_GAIN_INDEX = 20;
            public static final int MAX_EDGE_OPERATION_INDEX = 3;
            public static final int MAX_DITHER_PATTERN_INDEX = 9;
            public static final int[] GB_PHOTO_EXPOSURE_MICROSECONDS = new int[] {
                256, 272, 304, 352, 400, 464, 512, 560, 608, 704, 800, 912, 1008, 1136, 1264, 1376,
                1504, 1744, 2000, 2256, 2512, 2752, 3008, 3504, 4000, 4496, 5008, 5504, 6000, 7008, 8000, 9008,
                10000, 11264, 12512, 13760, 15008, 17504, 20000, 22496, 25008, 27504, 30000, 35008, 40000, 45008, 50000, 55008,
                60000, 65008, 70000, 75008, 80000, 90000, 100000, 112496, 125008, 142496, 160000, 180000, 200000, 225008, 250000, 275008,
                300000, 350000, 400000, 450000, 500000, 550000, 600000, 700000, 800000, 900000, 1000000, 1048560,
            };
            private static final int[] GAIN_REGISTER_VALUES = new int[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x15, 0x09, 0x17, 0x0A, 0x0B, 0x0C, 0x0D, 0x1C, 0x0E, 0x1D, 0x0F, 0x1F};
            private static final String[] GAIN_LABELS = new String[] {"14.0 dB", "15.5 dB", "17.0 dB", "18.5 dB", "20.0 dB", "21.5 dB", "23.0 dB", "24.5 dB", "26.0 dB", "27.5 dB", "29.0 dB", "30.5 dB", "32.0 dB", "35.0 dB", "38.0 dB", "41.0 dB", "44.0 dB", "45.5 dB", "47.0 dB", "51.5 dB", "57.5 dB"};
            private static final String[] EDGE_LABELS = new String[] {"2D", "Horizontal", "Vertical", "None"};
            private static final String[] DITHER_LABELS = new String[] {"Off", "Default", "2x2", "Grid", "Maze", "Nest", "Fuzz", "Vertical", "Horizontal", "Mix"};
            private static final int[] EDGE_OPERATION_VALUES = new int[] {0x60, 0x20, 0x40, 0x00};
            private static final int[] EDGE_RATIO_VALUES = new int[] {0x00, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70};
            private static final int[] VOLTAGE_REFERENCE_VALUES = new int[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
            private static final int[] ZERO_POINT_VALUES = new int[] {0x00, 0x80, 0x40};
            private static final int[][] DITHER_HIGH_LIGHT_VALUES = new int[][] {
                {0x80, 0x8F, 0xD0, 0xE6}, {0x82, 0x90, 0xC8, 0xE3}, {0x84, 0x90, 0xC0, 0xE0}, {0x85, 0x91, 0xB8, 0xDD},
                {0x86, 0x91, 0xB1, 0xDB}, {0x87, 0x92, 0xAA, 0xD8}, {0x88, 0x92, 0xA5, 0xD5}, {0x89, 0x92, 0xA2, 0xD2},
                {0x8A, 0x92, 0xA1, 0xC8}, {0x8B, 0x92, 0xA0, 0xBE}, {0x8C, 0x92, 0x9E, 0xB4}, {0x8D, 0x92, 0x9C, 0xAC},
                {0x8E, 0x92, 0x9B, 0xA5}, {0x8F, 0x92, 0x99, 0xA0}, {0x90, 0x92, 0x97, 0x9A}, {0x92, 0x92, 0x92, 0x92},
            };
            private static final int[][] DITHER_LOW_LIGHT_VALUES = new int[][] {
                {0x80, 0x94, 0xDC, 0xFF}, {0x82, 0x95, 0xD2, 0xFF}, {0x84, 0x96, 0xCA, 0xFF}, {0x86, 0x96, 0xC4, 0xFF},
                {0x88, 0x97, 0xBE, 0xFF}, {0x8A, 0x97, 0xB8, 0xFF}, {0x8B, 0x98, 0xB2, 0xF5}, {0x8C, 0x98, 0xAC, 0xEB},
                {0x8D, 0x98, 0xAA, 0xDD}, {0x8E, 0x98, 0xA8, 0xD0}, {0x8F, 0x98, 0xA6, 0xC4}, {0x90, 0x98, 0xA4, 0xBA},
                {0x92, 0x98, 0xA1, 0xB2}, {0x94, 0x98, 0x9D, 0xA8}, {0x96, 0x98, 0x99, 0xA0}, {0x98, 0x98, 0x98, 0x98},
            };
            private static final int[][] DITHER_PATTERN_MATRICES = new int[][] {
                {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
                {0x00, 0x0C, 0x03, 0x0F, 0x08, 0x04, 0x0B, 0x07, 0x02, 0x0E, 0x01, 0x0D, 0x0A, 0x06, 0x09, 0x05},
                {0x01, 0x01, 0x0A, 0x0A, 0x01, 0x01, 0x0A, 0x0A, 0x0D, 0x0D, 0x03, 0x03, 0x0D, 0x0D, 0x03, 0x03},
                {0x0C, 0x08, 0x07, 0x0C, 0x07, 0x01, 0x02, 0x07, 0x07, 0x07, 0x02, 0x08, 0x0D, 0x08, 0x08, 0x0C},
                {0x00, 0x01, 0x03, 0x05, 0x02, 0x0A, 0x0B, 0x0D, 0x04, 0x0C, 0x07, 0x08, 0x06, 0x0E, 0x09, 0x0F},
                {0x00, 0x01, 0x08, 0x0B, 0x02, 0x06, 0x0A, 0x0C, 0x09, 0x0E, 0x03, 0x04, 0x0D, 0x0F, 0x05, 0x07},
                {0x00, 0x09, 0x0E, 0x07, 0x04, 0x0D, 0x02, 0x0B, 0x08, 0x01, 0x06, 0x0F, 0x0C, 0x05, 0x0A, 0x03},
                {0x00, 0x0A, 0x07, 0x0D, 0x01, 0x0B, 0x04, 0x0E, 0x02, 0x08, 0x05, 0x0F, 0x03, 0x09, 0x06, 0x0C},
                {0x00, 0x01, 0x02, 0x03, 0x0A, 0x0B, 0x08, 0x09, 0x07, 0x04, 0x05, 0x06, 0x0D, 0x0E, 0x0F, 0x0C},
                {0x00, 0x01, 0x02, 0x03, 0x0A, 0x0B, 0x08, 0x09, 0x07, 0x04, 0x05, 0x06, 0x0D, 0x0E, 0x0F, 0x0C},
            };
            private static final int[] DITHER_PATTERN_MIX_MIDDLE = new int[] {0x00, 0x08, 0x04, 0x0C, 0x0D, 0x01, 0x09, 0x05, 0x06, 0x0E, 0x02, 0x0A, 0x0B, 0x07, 0x0F, 0x03};
            private static final int[] DITHER_PATTERN_MIX_HIGH = new int[] {0x00, 0x0A, 0x07, 0x0D, 0x01, 0x0B, 0x04, 0x0E, 0x02, 0x08, 0x05, 0x0F, 0x03, 0x09, 0x06, 0x0C};
    private static FileOutputStream fos = null;
    private static BufferedOutputStream bos = null;

    private static int firmwareVersion = -1;
    public static boolean powerControlSupport = false;
    public static boolean bootloaderResetSupport = false;
    public static String deviceName = "";

    public static class LiveCameraSettings {
        public final int exposureMicroseconds;
        public final int gainIndex;
        public final int voltageOutMillivolts;
        public final int edgeOperation;
        public final boolean edgeExclusive;
        public final int edgeRatioIndex;
        public final int voltageReferenceIndex;
        public final int zeroPointIndex;
        public final boolean invertOutput;
        public final int contrastLevel;
        public final int ditherPattern;
        public final boolean ditherHighLight;

        public LiveCameraSettings(int exposureMicroseconds, int gainIndex, int voltageOutMillivolts, int edgeOperation,
                                  boolean edgeExclusive, int edgeRatioIndex, int voltageReferenceIndex, int zeroPointIndex,
                                  boolean invertOutput, int contrastLevel, int ditherPattern, boolean ditherHighLight) {
            this.exposureMicroseconds = exposureMicroseconds;
            this.gainIndex = gainIndex;
            this.voltageOutMillivolts = voltageOutMillivolts;
            this.edgeOperation = edgeOperation;
            this.edgeExclusive = edgeExclusive;
            this.edgeRatioIndex = edgeRatioIndex;
            this.voltageReferenceIndex = voltageReferenceIndex;
            this.zeroPointIndex = zeroPointIndex;
            this.invertOutput = invertOutput;
            this.contrastLevel = contrastLevel;
            this.ditherPattern = ditherPattern;
            this.ditherHighLight = ditherHighLight;
        }

        public static LiveCameraSettings defaultSettings() {
            return new LiveCameraSettings(6000, 0, 192, 0, true, 0, 3, 1, false, 8, 1, true);
        }

        public LiveCameraSettings constrain() {
            return new LiveCameraSettings(
                    constrainValue(exposureMicroseconds, MIN_EXPOSURE_MICROSECONDS, MAX_EXPOSURE_MICROSECONDS),
                    constrainValue(gainIndex, 0, MAX_GAIN_INDEX),
                    constrainToStep(voltageOutMillivolts, MIN_VOLTAGE_OUT_MILLIVOLTS, MAX_VOLTAGE_OUT_MILLIVOLTS, VOLTAGE_OUT_STEP_MILLIVOLTS),
                    constrainValue(edgeOperation, 0, MAX_EDGE_OPERATION_INDEX),
                    edgeExclusive,
                    constrainValue(edgeRatioIndex, 0, EDGE_RATIO_VALUES.length - 1),
                    constrainValue(voltageReferenceIndex, 0, VOLTAGE_REFERENCE_VALUES.length - 1),
                    constrainValue(zeroPointIndex, 0, ZERO_POINT_VALUES.length - 1),
                    invertOutput,
                    constrainValue(contrastLevel, MIN_CONTRAST_LEVEL, MAX_CONTRAST_LEVEL),
                    constrainValue(ditherPattern, 0, MAX_DITHER_PATTERN_INDEX),
                    ditherHighLight);
        }

        public byte[] toParameterRegisters() {
            LiveCameraSettings settings = constrain();
            int exposureValue = constrainValue(settings.exposureMicroseconds >> 4, 0x10, 0xFFFF);
            return new byte[] {
                    (byte) ((settings.edgeExclusive ? 0x80 : 0x00) | EDGE_OPERATION_VALUES[settings.edgeOperation] | GAIN_REGISTER_VALUES[settings.gainIndex]),
                    (byte) (exposureValue & 0xFF),
                    (byte) ((exposureValue >> 8) & 0xFF),
                    (byte) (EDGE_RATIO_VALUES[settings.edgeRatioIndex] | (settings.invertOutput ? 0x08 : 0x00) | VOLTAGE_REFERENCE_VALUES[settings.voltageReferenceIndex]),
                    (byte) (ZERO_POINT_VALUES[settings.zeroPointIndex] | toVoltageOutRegister(settings.voltageOutMillivolts)),
            };
        }

        public byte[] createDitherPatternRegisters() {
            LiveCameraSettings settings = constrain();
            int[] ranges = settings.ditherHighLight ? DITHER_HIGH_LIGHT_VALUES[settings.contrastLevel - 1] : DITHER_LOW_LIGHT_VALUES[settings.contrastLevel - 1];
            int[][] bases = new int[][] {createDitherBaseValues(ranges[0], ranges[1]), createDitherBaseValues(ranges[1], ranges[2]), createDitherBaseValues(ranges[2], ranges[3])};
            int[] lowPattern = DITHER_PATTERN_MATRICES[settings.ditherPattern];
            int[] middlePattern = settings.ditherPattern == 9 ? DITHER_PATTERN_MIX_MIDDLE : lowPattern;
            int[] highPattern = settings.ditherPattern == 9 ? DITHER_PATTERN_MIX_HIGH : lowPattern;
            byte[] registers = new byte[48];
            for (int index = 0; index < 16; index++) {
                int outputOffset = index * 3;
                registers[outputOffset] = (byte) bases[0][lowPattern[index]];
                registers[outputOffset + 1] = (byte) bases[1][middlePattern[index]];
                registers[outputOffset + 2] = (byte) bases[2][highPattern[index]];
            }
            return registers;
        }

        public LiveCameraSettings withExposure(int exposureMicroseconds) {
            return new LiveCameraSettings(exposureMicroseconds, gainIndex, voltageOutMillivolts, edgeOperation, edgeExclusive, edgeRatioIndex, voltageReferenceIndex, zeroPointIndex, invertOutput, contrastLevel, ditherPattern, ditherHighLight).constrain();
        }

        public LiveCameraSettings withExposureBand(int voltageOutMillivolts, int gainIndex) {
            return new LiveCameraSettings(exposureMicroseconds, gainIndex, voltageOutMillivolts, 0, true, edgeRatioIndex, voltageReferenceIndex, zeroPointIndex, invertOutput, contrastLevel, ditherPattern, ditherHighLight).constrain();
        }

        public LiveCameraSettings withTwoDimensionalEdge() {
            return new LiveCameraSettings(exposureMicroseconds, gainIndex, voltageOutMillivolts, 0, true, edgeRatioIndex, voltageReferenceIndex, zeroPointIndex, invertOutput, contrastLevel, ditherPattern, ditherHighLight).constrain();
        }

        public String summary() {
            LiveCameraSettings settings = constrain();
            return "exposure " + settings.exposureMicroseconds + " us, gain " + GAIN_LABELS[settings.gainIndex]
                    + ", voltage " + settings.voltageOutMillivolts + " mV, edge " + EDGE_LABELS[settings.edgeOperation]
                    + ", contrast " + settings.contrastLevel + ", dither " + DITHER_LABELS[settings.ditherPattern]
                    + (settings.ditherHighLight ? " high" : " low");
        }
    }

    public interface LiveCameraSettingsController {
        LiveCameraSettings getSettings();
        boolean isAutomaticAdjustmentEnabled();
        void onAutomaticSettings(LiveCameraSettings settings);
    }

    public interface LiveCameraProgressListener {
        void onProgress(LiveCameraProgress progress);
        void onStopped();
    }

    public static String getGainLabel(int gainIndex) {
        return GAIN_LABELS[constrainValue(gainIndex, 0, MAX_GAIN_INDEX)];
    }

    public static String getEdgeLabel(int edgeOperation) {
        return EDGE_LABELS[constrainValue(edgeOperation, 0, MAX_EDGE_OPERATION_INDEX)];
    }

    public static String getDitherLabel(int ditherPattern) {
        return DITHER_LABELS[constrainValue(ditherPattern, 0, MAX_DITHER_PATTERN_INDEX)];
    }

    public static int snapExposureToGbPhotoValue(int exposureMicroseconds) {
        int constrained = constrainValue(exposureMicroseconds, MIN_EXPOSURE_MICROSECONDS, MAX_EXPOSURE_MICROSECONDS);
        int nearest = GB_PHOTO_EXPOSURE_MICROSECONDS[0];
        int nearestDistance = Math.abs(constrained - nearest);
        for (int index = 1; index < GB_PHOTO_EXPOSURE_MICROSECONDS.length; index++) {
            int candidate = GB_PHOTO_EXPOSURE_MICROSECONDS[index];
            int distance = Math.abs(constrained - candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static int getGbPhotoExposureIndex(int exposureMicroseconds) {
        int snapped = snapExposureToGbPhotoValue(exposureMicroseconds);
        for (int index = 0; index < GB_PHOTO_EXPOSURE_MICROSECONDS.length; index++) {
            if (GB_PHOTO_EXPOSURE_MICROSECONDS[index] == snapped) {
                return index;
            }
        }
        return 0;
    }

    public static void readFirmwareInfo(UsbSerialPort port) {
        try {
            Integer cmd = GBxCartConstants.DEVICE_CMD.get("QUERY_FW_INFO");

            byte[] command = new byte[]{ (byte)(cmd & 0xFF) };
            port.write(command, TIMEOUT);

            // Read up to ~64 bytes
            byte[] buffer = new byte[64];
            int len = -1;

            try {
                len = port.read(buffer, 100);   // one read, one timeout
            } catch (Exception e) {
                //Toast.makeText(context, "Error en readFirmwareInfo (no response)\n" + e.toString(), Toast.LENGTH_LONG).show();
            }

            if (len < 4) {
                return;
            }

            byte[] data = Arrays.copyOf(buffer, len);

            // Parse firmwareVersion
            int firmwareVersion = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

            // ---------- L1 ----------
            if (firmwareVersion < 2) {
                GBxCartCommands.firmwareVersion = firmwareVersion;
                GBxCartCommands.powerControlSupport = false;
                GBxCartCommands.bootloaderResetSupport = false;
                GBxCartCommands.deviceName = "GBxCart RW";
            }

            // ---------- L2–L11 ----------
            if (firmwareVersion < 12) {
                GBxCartCommands.firmwareVersion = firmwareVersion;
                GBxCartCommands.powerControlSupport = true;
                GBxCartCommands.bootloaderResetSupport = false;
                GBxCartCommands.deviceName = "GBxCart RW";
            }

            // ---------- L12+ ----------
            if (data.length < 10) {
                return;
            }

            int deviceNameLength = data[9] & 0xFF;

            // Extract device name
            String deviceName = "GBxCart RW";
            if (deviceNameLength > 1) {
                deviceName = new String(data, 10, deviceNameLength - 1);
            }

            int flagsIndex = 10 + deviceNameLength;
            boolean powerCtrl = false;
            boolean bootReset = false;

            if (data.length >= flagsIndex + 2) {
                powerCtrl = (data[flagsIndex] & 0xFF) != 0;
                bootReset = (data[flagsIndex + 1] & 0xFF) != 0;
            }

            // Store globally
            GBxCartCommands.firmwareVersion = firmwareVersion;
            GBxCartCommands.powerControlSupport = powerCtrl;
            GBxCartCommands.bootloaderResetSupport = bootReset;
            GBxCartCommands.deviceName = deviceName;

        } catch (Exception e) {
            //Toast.makeText(context, "Error en readFirmwareInfo (Unknown error)\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean waitForAck(UsbSerialPort port) {
        byte[] ack = new byte[1];
        try {
            int len = port.read(ack, TIMEOUT);
            if (len == 1 && (ack[0] == 1 || ack[0] == 3)) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    public static void powerOff(UsbSerialPort port, Context context) {
        byte[] command = new byte[1];
        int cmd;
        if (firmwareVersion >= 12) {
            cmd = GBxCartConstants.DEVICE_CMD.get("CART_PWR_OFF");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
                waitForAck(port);

            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
        } else if (firmwareVersion >= 2 && firmwareVersion <= 11) {
            cmd = GBxCartConstants.DEVICE_CMD.get("OFW_CART_PWR_OFF");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
            delay(200);
        } else {
            cmd = GBxCartConstants.DEVICE_CMD.get("OFW_CART_PWR_OFF");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void powerOn(UsbSerialPort port, Context context) {
        byte[] command = new byte[1];
        int cmd;
        if (firmwareVersion >= 12) {
            cmd = GBxCartConstants.DEVICE_CMD.get("CART_PWR_ON");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
                waitForAck(port);

            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
        } else if (firmwareVersion >= 2 && firmwareVersion <= 11) {
            cmd = GBxCartConstants.DEVICE_CMD.get("OFW_CART_PWR_ON");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
            delay(200);
        } else {
            cmd = GBxCartConstants.DEVICE_CMD.get("OFW_CART_PWR_ON");
            command[0] = (byte) cmd;
            try {
                port.write(command, TIMEOUT);
            } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOff\n" + e.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void setCartType(UsbSerialPort port, Context context) {
        byte[] command = new byte[1];

        try {
            int cmd = GBxCartConstants.DEVICE_CMD.get("SET_MODE_DMG");
            command[0] = (byte) cmd; //SET_MODE_DMG
            if (firmwareVersion < 12) {
                port.write(command, TIMEOUT);
            } else {
                port.write(command, TIMEOUT);
                waitForAck(port);
            }

            cmd = GBxCartConstants.DEVICE_CMD.get("SET_VOLTAGE_5V");
            command[0] = (byte) cmd; //SET_VOLTAGE_5V
            if (firmwareVersion < 12) {
                port.write(command, TIMEOUT);
            } else {
                port.write(command, TIMEOUT);
                waitForAck(port);
            }

            setFwVariable("DMG_READ_METHOD", 1, port, context);

            setFwVariable("CART_MODE", 1, port, context);

        } catch (Exception e) {
//            Toast.makeText(context, "Error en SetCartType\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private static void setFwVariable(String key, int value, UsbSerialPort port, Context context) {
        int size = 0;
        int keyInt = 0;
        for (Map.Entry<String, int[]> entry : GBxCartConstants.DEVICE_VAR.entrySet()) {
            String k = entry.getKey();
            int[] v = entry.getValue();
            if (k.contains(key)) {
                if (v[0] == 8) {
                    size = 1;
                } else if (v[0] == 16) {
                    size = 2;
                } else if (v[0] == 32) {
                    size = 4;
                }
                keyInt = v[1];
                break;
            }
        }
        int temp = GBxCartConstants.DEVICE_CMD.get("SET_VARIABLE");
        ByteBuffer bb = ByteBuffer.allocate(10);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) temp);
        bb.put((byte) size);
        bb.putInt(keyInt);
        bb.putInt(value);
        byte[] byteArray = bb.array();
        try {
            if (firmwareVersion < 12) {
                port.write(byteArray, TIMEOUT);
                delay(10);
            } else {
                port.write(byteArray, TIMEOUT);
                waitForAck(port);
            }
        } catch (Exception e) {
//            Toast.makeText(context, "ErrorsetFwVariable" + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] CartRead_ROM(int length, UsbSerialPort port, Context context, TextView tv) {
        int max_length = 64;

        if (length > max_length) {
            length = max_length;
        }
        int num = (int) Math.ceil((double) length / (double) max_length);
        byte[] buffer = new byte[num * length];

        byte[] commandByte = new byte[1];

        String command = "DMG_CART_READ";

        try {
            for (int i = 0; i < num; i++) {
                int x = GBxCartConstants.DEVICE_CMD.get(command);
                commandByte[0] = (byte) x;
                port.write(commandByte, TIMEOUT);
            }
        } catch (Exception e) {
//            Toast.makeText(context, "Error en cartReadRom\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
        return buffer;
    }

    public static String ReadRomName(UsbSerialPort port, Context context, TextView tv) {
        byte[] readLength = new byte[0x10];
        byte[] receivedData = new byte[0x10];

        try {
            setFwVariable("TRANSFER_SIZE", 0x10, port, context);
            setFwVariable("DMG_ACCESS_MODE", 1, port, context);
            setFwVariable("DMG_READ_CS_PULSE", 1, port, context);
            setFwVariable("ADDRESS", 0x134, port, context);

            CartRead_ROM(0x10, port, context, tv);
            int len = port.read(readLength, TIMEOUT);//Intento leer manualmente
            receivedData = (Arrays.copyOf(readLength, len));

        } catch (Exception e) {
//            Toast.makeText(context, "Error en PowerOn\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
        return new String(receivedData);
    }

    public static void Cart_write(int address, int value, UsbSerialPort port, Context context) {

        byte[] buffer = new byte[6];
        buffer[0] = (byte) (GBxCartConstants.DEVICE_CMD.get("DMG_CART_WRITE") & 0xFF);
        byte[] addressBytes = ByteBuffer.allocate(4).putInt(address).array();
        System.arraycopy(addressBytes, 0, buffer, 1, 4);
        buffer[5] = (byte) (value & 0xFF);

        try {
            if (firmwareVersion < 12) {
                port.write(buffer, TIMEOUT);
            } else {
                port.write(buffer, TIMEOUT);
                waitForAck(port);
            }
        } catch (Exception e) {
//            Toast.makeText(context, "Error en Cart_write\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    public static void CartRead_RAM(int address, int length, UsbSerialPort port, Context context) {

        byte[] commandByte = new byte[1];

        String command = "DMG_CART_READ";
        try {
//
            int x = GBxCartConstants.DEVICE_CMD.get(command);
            commandByte[0] = (byte) x;
            port.write(commandByte, TIMEOUT);

        } catch (Exception e) {
//            Toast.makeText(context, "Error en cartReadRom\n" + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private static void writeRomRegister(int address, int value, UsbSerialPort port, Context context) throws Exception {
        setFwVariable("DMG_WRITE_CS_PULSE", 0, port, context);
        ByteBuffer bb = ByteBuffer.allocate(6);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) (GBxCartConstants.DEVICE_CMD.get("DMG_CART_WRITE") & 0xFF));
        bb.putInt(address);
        bb.put((byte) (value & 0xFF));
        port.write(bb.array(), TIMEOUT);
        if (firmwareVersion >= 12 && !waitForAck(port)) {
            throw new IOException("GBxCart did not acknowledge ROM register write");
        }
    }

    private static void writeSramWindow(int address, byte[] data, UsbSerialPort port, Context context) throws Exception {
        setFwVariable("DMG_WRITE_CS_PULSE", 1, port, context);
        setFwVariable("TRANSFER_SIZE", 1, port, context);
        byte[] command = new byte[] {(byte) (GBxCartConstants.DEVICE_CMD.get("DMG_CART_WRITE_SRAM") & 0xFF)};
        for (int index = 0; index < data.length; index++) {
            setFwVariable("ADDRESS", address + index, port, context);
            port.write(command, TIMEOUT);
            port.write(new byte[] {data[index]}, TIMEOUT);
            if (firmwareVersion >= 12 && !waitForAck(port)) {
                throw new IOException("GBxCart did not acknowledge SRAM register write");
            }
        }
    }

    private static byte[] readSramWindow(int address, int length, UsbSerialPort port, Context context) throws Exception {
        if (length > CAMERA_READ_CHUNK_SIZE) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(length);
            int offset = 0;
            while (offset < length) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Live camera capture interrupted");
                }
                int chunkLength = Math.min(CAMERA_READ_CHUNK_SIZE, length - offset);
                output.write(readSramWindow(address + offset, chunkLength, port, context));
                offset += chunkLength;
            }
            return output.toByteArray();
        }

        setFwVariable("DMG_WRITE_CS_PULSE", 0, port, context);
        setFwVariable("TRANSFER_SIZE", length, port, context);
        setFwVariable("ADDRESS", 0xA000 + address, port, context);
        setFwVariable("DMG_ACCESS_MODE", 3, port, context);
        setFwVariable("DMG_READ_CS_PULSE", 1, port, context);
        purgeInput(port);
        port.write(new byte[] {(byte) (GBxCartConstants.DEVICE_CMD.get("DMG_CART_READ") & 0xFF)}, TIMEOUT);
        byte[] data = readExact(port, length, CAMERA_TIMEOUT);
        setFwVariable("DMG_READ_CS_PULSE", 0, port, context);
        return data;
    }

    private static byte readSramByte(int address, UsbSerialPort port, Context context) throws Exception {
        return readSramWindow(address, 1, port, context)[0];
    }

    private static void toggleClock(int count, UsbSerialPort port) throws Exception {
        if (firmwareVersion < 12) {
            return;
        }
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) (GBxCartConstants.DEVICE_CMD.get("CLK_TOGGLE") & 0xFF));
        bb.putInt(count);
        port.write(bb.array(), CAMERA_TIMEOUT);
        if (!waitForAck(port)) {
            throw new IOException("GBxCart did not acknowledge clock toggle");
        }
    }

    private static void prepareCameraRegisterBank(UsbSerialPort port, Context context) throws Exception {
        writeRomRegister(0x0000, 0x0A, port, context);
        writeRomRegister(0x4000, 0x10, port, context);
    }

    private static void initializeLiveCamera(UsbSerialPort port, Context context) throws Exception {
        initializeLiveCamera(port, context, LiveCameraSettings.defaultSettings());
    }

    private static void initializeLiveCamera(UsbSerialPort port, Context context, LiveCameraSettings settings) throws Exception {
        prepareCameraRegisterBank(port, context);
        writeSramWindow(0xA001, settings.constrain().toParameterRegisters(), port, context);
        writeSramWindow(0xA006, settings.constrain().createDitherPatternRegisters(), port, context);
        writeRomRegister(0x0000, 0x00, port, context);
    }

    private static byte[] captureLiveCameraFrame(UsbSerialPort port, Context context) throws Exception {
        return captureLiveCameraFrame(port, context, LiveCameraSettings.defaultSettings());
    }

    private static byte[] captureLiveCameraFrame(UsbSerialPort port, Context context, LiveCameraSettings settings) throws Exception {
        LiveCameraSettings constrained = settings.constrain();
        prepareCameraRegisterBank(port, context);
        writeSramWindow(0xA001, constrained.toParameterRegisters(), port, context);
        writeSramWindow(0xA006, constrained.createDitherPatternRegisters(), port, context);
        writeSramWindow(0xA000, new byte[] {0x01}, port, context);

        long deadline = System.currentTimeMillis() + CAMERA_TIMEOUT;
        byte status;
        do {
            status = readSramByte(0, port, context);
            if ((status & 0x01) == 0) {
                break;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Timed out waiting for Game Boy Camera capture, status 0x" + Integer.toHexString(status & 0xFF));
            }
            toggleClock(CAMERA_CLOCK_TOGGLES_PER_POLL, port);
        } while (!Thread.currentThread().isInterrupted());

        writeRomRegister(0x0000, 0x0A, port, context);
        writeRomRegister(0x4000, 0x00, port, context);
        return readSramWindow(CAMERA_LIVE_FRAME_ADDRESS - 0xA000, CAMERA_LIVE_FRAME_SIZE, port, context);
    }

    private static byte[] readExact(UsbSerialPort port, int length, int timeout) throws Exception {
        byte[] output = new byte[length];
        int offset = 0;
        long deadline = System.currentTimeMillis() + timeout;
        while (offset < length) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Live camera capture interrupted");
            }
            int remaining = length - offset;
            byte[] buffer = new byte[Math.min(remaining, 4096)];
            int read = port.read(buffer, Math.max(1, (int) (deadline - System.currentTimeMillis())));
            if (read > 0) {
                System.arraycopy(buffer, 0, output, offset, Math.min(read, remaining));
                offset += Math.min(read, remaining);
                deadline = System.currentTimeMillis() + timeout;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IOException("Timed out reading " + length + " bytes from GBxCart; received " + offset);
            }
        }
        return output;
    }

    private static void purgeInput(UsbSerialPort port) {
        try {
            port.purgeHwBuffers(true, false);
        } catch (Exception ignored) {
        }
    }

    private static Bitmap renderLiveCameraFrame(byte[] frame) {
        ImageCodec imageCodec = new ImageCodec(CAMERA_LIVE_FRAME_WIDTH, CAMERA_LIVE_FRAME_HEIGHT);
        return imageCodec.decodeWithPalette(IndexedPalette.EVEN_DIST_PALETTE, frame, false);
    }

    private static String frameStats(byte[] frame, int frameCount) {
        return "Frame " + frameCount;
    }

    private static LiveCameraSettings adjustLiveCameraSettings(byte[] frame, LiveCameraSettings currentSettings) {
        final int histogramTileCount = 12;
        final int defaultTargetBrightness = histogramTileCount * 96;
        final int sensitivity0 = 5;
        final int sensitivity1 = 10;
        final int sensitivity2 = 20;
        final int sensitivity3 = 95;
        final int edgeOnlyThreshold = 20;
        final int brightClippedTileMaximum = 16;
        final int darkClippedTileMinimum = 176;
        int[][] centerTiles = new int[][] {{6, 4}, {9, 4}, {5, 5}, {10, 5}, {7, 6}, {8, 6}, {7, 7}, {8, 7}, {5, 8}, {10, 8}, {6, 9}, {9, 9}};

        int histogram = 0;
        int brightClippedTiles = 0;
        int darkClippedTiles = 0;
        for (int[] tile : centerTiles) {
            int tileOffset = ((tile[1] * 16) + tile[0]) * 16;
            int tileValue = calculateTile(frame, tileOffset);
            histogram += tileValue;
            if (tileValue <= brightClippedTileMaximum) {
                brightClippedTiles++;
            } else if (tileValue >= darkClippedTileMinimum) {
                darkClippedTiles++;
            }
        }

        int error = (histogram - defaultTargetBrightness) / histogramTileCount;
        LiveCameraSettings constrained = currentSettings.constrain();
        int exposure = constrained.exposureMicroseconds >> 4;
        int newExposure = exposure;
        int absoluteError = Math.abs(error);

        if (absoluteError > sensitivity3) {
            newExposure = error < 0 ? exposure >> 1 : exposure << 1;
        } else if (absoluteError > sensitivity2) {
            int step = Math.max(exposure >> 3, 1);
            newExposure = exposure + (error < 0 ? -step : step);
        } else if (absoluteError > sensitivity1) {
            int step = Math.max(exposure >> 4, 1);
            newExposure = exposure + (error < 0 ? -step : step);
        } else if (absoluteError > sensitivity0) {
            newExposure = exposure + (error < 0 ? -1 : 1);
        }

        if (brightClippedTiles >= histogramTileCount / 2) {
            newExposure = Math.min(newExposure, Math.max(exposure >> 1, 1));
        } else if (brightClippedTiles >= histogramTileCount / 3) {
            int step = Math.max(exposure >> 2, 1);
            newExposure = Math.min(newExposure, exposure - step);
        }

        if (darkClippedTiles >= histogramTileCount / 2) {
            newExposure = Math.max(newExposure, exposure << 1);
        } else if (darkClippedTiles >= histogramTileCount / 3) {
            int step = Math.max(exposure >> 2, 1);
            newExposure = Math.max(newExposure, exposure + step);
        }

        int lowLimit = MIN_EXPOSURE_MICROSECONDS >> 4;
        int highLimit = MAX_EXPOSURE_MICROSECONDS >> 4;
        LiveCameraSettings adjusted = constrained.withExposure(constrainValue(newExposure, lowLimit, highLimit) << 4);
        return absoluteError > edgeOnlyThreshold ? renderSettingsFromExposure(adjusted) : adjusted.withTwoDimensionalEdge();
    }

    private static LiveCameraSettings renderSettingsFromExposure(LiveCameraSettings settings) {
        LiveCameraSettings constrained = settings.constrain();
        int exposure = constrained.exposureMicroseconds;
        if (exposure < 768) {
            return constrained.withExposureBand(64, 0);
        }
        if (exposure < 32000) {
            return constrained.withExposureBand(160, 0);
        }
        if (exposure < 282000) {
            return constrained.withExposureBand(96, 4);
        }
        if (exposure < 573000) {
            return constrained.withExposureBand(-192, 8);
        }
        return constrained.withExposureBand(-416, 10);
    }

    private static int calculateTile(byte[] frame, int tileOffset) {
        int total = 0;
        for (int row = 0; row < 8; row++) {
            int offset = tileOffset + (row * 2);
            total += countBits(frame[offset] & 0xFF) + (countBits(frame[offset + 1] & 0xFF) * 2);
        }
        return total;
    }

    private static int countBits(int value) {
        int count = 0;
        while (value != 0) {
            count += value & 1;
            value >>= 1;
        }
        return count;
    }

    private static int toVoltageOutRegister(int millivolts) {
        int stepped = constrainToStep(millivolts, MIN_VOLTAGE_OUT_MILLIVOLTS, MAX_VOLTAGE_OUT_MILLIVOLTS, VOLTAGE_OUT_STEP_MILLIVOLTS);
        int steps = stepped / VOLTAGE_OUT_STEP_MILLIVOLTS;
        return stepped < 0 ? (~(-steps) + 1) & 0x1F : (steps & 0x1F) | 0x20;
    }

    private static int[] createDitherBaseValues(int start, int end) {
        int[] values = new int[16];
        int current = start << 8;
        int step = start < end ? ((end << 8) - current) >> 4 : 0;
        for (int index = 0; index < values.length; index++, current += step) {
            values[index] = current >> 8;
        }
        return values;
    }

    private static int constrainValue(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    private static int constrainToStep(int value, int min, int max, int step) {
        int constrained = constrainValue(value, min, max);
        int remainder = constrained % step;
        return remainder == 0 ? constrained : constrained - remainder;
    }

    public static class LiveCameraProgress {
        public final Bitmap bitmap;
        public final byte[] frame;
        public final String status;
        public final int frameCount;
        public final LiveCameraSettings settings;

        public LiveCameraProgress(Bitmap bitmap, byte[] frame, String status, int frameCount, LiveCameraSettings settings) {
            this.bitmap = bitmap;
            this.frame = frame;
            this.status = status;
            this.frameCount = frameCount;
            this.settings = settings;
        }
    }

    public static class LiveCameraAsyncTask extends AppTask<Void, LiveCameraProgress, Void> {
        private final UsbSerialPort port;
        private final Context context;
        private final LiveCameraSettingsController settingsController;
        private final LiveCameraProgressListener progressListener;

        public LiveCameraAsyncTask(UsbSerialPort port, Context context, LiveCameraSettingsController settingsController, LiveCameraProgressListener progressListener) {
            this.port = port;
            this.context = context;
            this.settingsController = settingsController;
            this.progressListener = progressListener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            int frameCount = 0;
            LiveCameraSettings settings = settingsController.getSettings().constrain();
            try {
                publishProgress(new LiveCameraProgress(null, null, "Preparing GBxCart live camera...", frameCount, settings));
                powerOff(port, context);
                setCartType(port, context);
                powerOn(port, context);
                initializeLiveCamera(port, context, settings);

                while (!isCancelled() && !Thread.currentThread().isInterrupted()) {
                    if (!settingsController.isAutomaticAdjustmentEnabled()) {
                        settings = settingsController.getSettings().constrain();
                    }
                    byte[] frame = captureLiveCameraFrame(port, context, settings);
                    frameCount++;
                    Bitmap bitmap = renderLiveCameraFrame(frame);
                    String status = frameStats(frame, frameCount);
                    if (settingsController.isAutomaticAdjustmentEnabled()) {
                        settings = adjustLiveCameraSettings(frame, settings).constrain();
                        settingsController.onAutomaticSettings(settings);
                        status += "\nAuto: " + settings.exposureMicroseconds + " us";
                    }
                    publishProgress(new LiveCameraProgress(bitmap, Arrays.copyOf(frame, frame.length), status, frameCount, settings));
                    delay(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "Live camera failed", e);
                publishProgress(new LiveCameraProgress(null, null, "GBxCart live camera error: " + e, frameCount, settings));
            } finally {
                try {
                    powerOff(port, context);
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(LiveCameraProgress... values) {
            super.onProgressUpdate(values);
            if (values.length == 0 || values[0] == null) {
                return;
            }
            LiveCameraProgress progress = values[0];
            progressListener.onProgress(progress);
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            progressListener.onStopped();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            progressListener.onStopped();
        }
    }

    public static class ReadPHOTORomAsyncTask extends AppTask<Void, Integer, Void> {
        private Context context;
        private TextView tv;
        private UsbSerialPort port;
        List<File> fullRomFileList;
        List<byte[]> fullRomFileBytes;

        public ReadPHOTORomAsyncTask(UsbSerialPort port, Context context, TextView tv, List<File> fullRomFileList, List<byte[]> fullRomFileBytes) {
            this.port = port;
            this.context = context;
            this.tv = tv;
            this.fullRomFileList = fullRomFileList;
            this.fullRomFileBytes = fullRomFileBytes;
        }


        @Override
        protected Void doInBackground(Void... voids) {
            //DUMP 1 MB ROM file
            LocalDateTime now = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                now = LocalDateTime.now();
            }
            Date nowDate = new Date();

            String fileName = "PhotoFullRom_";

            String folderName = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "HH-mm-ss");
                folderName = "PhotoFullRom_" + dtf.format(now);
                fileName += dtf.format(now) + "-full.gbc";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                folderName = "PhotoFullRom_" + sdf.format(nowDate);
                fileName += sdf.format(nowDate) + "-full.gbc";
            }

            UsbSerialFragment.photoFolder = new File(Utils.PHOTO_DUMPS_FOLDER, folderName);
            //I create the new directory if it doesn't exists
            try {
                if (!UsbSerialFragment.photoFolder.exists() && !UsbSerialFragment.photoFolder.mkdirs()) {
                    throw new IllegalStateException("Couldn't create dir: " + UsbSerialFragment.photoFolder);
                }
            } catch (Exception e) {
//                Toast toast = Toast.makeText(context, "Error making directory: " + e.toString(), Toast.LENGTH_SHORT);
//                toast.show();
            }
            File file = new File(UsbSerialFragment.photoFolder, fileName);
            // create the new file inside the directory
            try {
                if (!file.createNewFile()) {
                    throw new IllegalStateException("Couldn't create file: " + file);
                }
            } catch (Exception e) {
//                Toast toast = Toast.makeText(context, "Error making file: " + e.toString(), Toast.LENGTH_SHORT);
//                toast.show();
            }

            setFwVariable("TRANSFER_SIZE", 64, port, context);
            setFwVariable("DMG_ACCESS_MODE", 1, port, context);
            setFwVariable("DMG_READ_CS_PULSE", 1, port, context);

            try {
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);

                int bytesPerBank = 0x4000; // 16 KiB
                int transferSize = 64;
                int chunksPerBank = bytesPerBank / transferSize;

                setFwVariable("DMG_READ_CS_PULSE", 1, port, context);
                setFwVariable("DMG_ACCESS_MODE", 1, port, context); // MODE_ROM_READ
                setFwVariable("TRANSFER_SIZE", transferSize, port, context);

                int totalIterations = 64 * chunksPerBank;
                int currentIteration = 0;

                for (int bank = 0; bank < 64; bank++) {
                    // Select ROM bank
                    Cart_write(0x2100, bank, port, context);

                    // First bank starts at 0, others at 0x4000
                    if (bank == 0) {
                        setFwVariable("ADDRESS", 0x0000, port, context);
                    } else {
                        setFwVariable("ADDRESS", 0x4000, port, context);
                    }

                    for (int j = 0; j < chunksPerBank; j++) {
                        // Send DMG_CART_READ
                        int cmdInt = GBxCartConstants.DEVICE_CMD.get("DMG_CART_READ");
                        byte[] cmd = new byte[]{ (byte)(cmdInt & 0xFF) };
                        port.write(cmd, TIMEOUT);

                        byte[] buffer = new byte[transferSize];
                        int total = 0;

                        if (firmwareVersion >= 12) { // L12+
                            while (total < transferSize) {
                                byte[] temp = new byte[64];
                                int n = port.read(temp, TIMEOUT);
                                if (n <= 0) break;
                                System.arraycopy(temp, 0, buffer, total, n);
                                total += n;
                            }
                        } else { // L2-L11
                            int len = port.read(buffer, TIMEOUT);
                            total = (len > 0) ? len : 0;
                        }

                        if (total > 0) {
                            bos.write(buffer, 0, total);
                        }

                        currentIteration++;
                        int progress = currentIteration * 100 / totalIterations;
                        publishProgress(progress, 0);
                    }
                }

                bos.close();
                tv.append("\n" + tv.getContext().getString(R.string.done_dumping_photo));

            } catch (Exception e) {
                Log.e(TAG, "Full ROM dump failed", e);
                Utils.toast(context, "Error en FullReadRom\n" + e);
            }

            publishProgress(100, 1);

            //Now I divide the 1MB file into 8. First will be the gbc rom, next 7 ram files
            int fileSize = (int) file.length();
            int partSize = fileSize / 8; // Divides the file in 8 parts
            byte[] buffer = new byte[partSize];

            try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
                for (int i = 0; i < 8; i++) {
                    String extension = "";
                    if (i == 0) {
                        extension = ".gbc";
                    } else extension = ".part_" + i + ".sav";
                    File outputFile = new File(UsbSerialFragment.photoFolder, fileName + extension);

                    try (OutputStream writer = new FileOutputStream(outputFile)) {
                        int bytesRead = 0;
                        while (bytesRead < partSize && reader.read(buffer) != -1) {
                            writer.write(buffer);
                            bytesRead += buffer.length;
                        }
                    }
                    if (i != 0) {//Because 0 is the actual rom
                        try (InputStream is = new FileInputStream(outputFile)) {
                            byte[] bufferAux = new byte[1024];
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                            int read;
                            while ((read = is.read(bufferAux)) != -1) {
                                outputStream.write(bufferAux, 0, read);
                            }
                            byte[] fileBytes = outputStream.toByteArray();

                            if (magicIsReal(fileBytes)) {
                                fullRomFileList.add(outputFile);
                                fullRomFileBytes.add(fileBytes);
                            } else {
                                outputFile.delete();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int progress = values[0];
            int finishedExtracting = values[1];
            if (finishedExtracting == 0)
                tv.setText(context.getString(R.string.dumping_rom_wait) + "\n" + progress + "%");
            else if (finishedExtracting == 1) {
                tv.append("\n" + context.getString(R.string.analyzing));
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            tv.append("\n" + context.getString(R.string.done_analyzing));
            tv.append("\n" + tv.getContext().getString(R.string.done_dumping_photo));
            powerOff(port, context);

            UsbSerialFragment.readRomSavs();
        }
    }

    public static class ReadRamAsyncTask extends AppTask<Void, Integer, Void> {
        private Context context;
        private TextView tv;
        private UsbSerialPort port;
        File latestFile;

        public ReadRamAsyncTask(UsbSerialPort port, Context context, TextView tv, File latestFile) {
            this.port = port;
            this.context = context;
            this.tv = tv;
            this.latestFile = latestFile;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            LocalDateTime now = null;
            Date nowDate = new Date();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                now = LocalDateTime.now();
            }
            String fileName = "gbCamera_";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateLocale + "_HH-mm-ss");
                fileName += dtf.format(now) + ".sav";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(dateLocale + "_HH-mm-ss", Locale.getDefault());
                fileName += sdf.format(nowDate) + ".sav";
            }

            File file = new File(Utils.SAVE_FOLDER, fileName);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            //I create the new directory if it doesn't exists
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Couldn't create dir: " + parent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Couldn't prepare RAM dump output file", e);
                Utils.toast(context, "Error making file: " + e);
            }
            //# Enable SRAM access
            Cart_write(0x6000, 0x01, port, context);
            Cart_write(0x0000, 0x0A, port, context);

            setFwVariable("TRANSFER_SIZE", 64, port, context);
            setFwVariable("DMG_ACCESS_MODE", 3, port, context);
            setFwVariable("DMG_READ_CS_PULSE", 1, port, context);

            try {
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);
                for (int i = 0; i < 16; i++) {
                    // Set SRAM bank
                    Cart_write(0x4000, i, port, context);
                    // Set ADDRESS once per bank switch
                    setFwVariable("ADDRESS", 0xA000, port, context);
                    // Read 8 KiB of SRAM
                    for (int j = 0; j < 128; j++) {
                        byte[] readLength = new byte[64];
                        CartRead_RAM(j * 64, 64, port, context);
                        int len = port.read(readLength, TIMEOUT);

                        try {
                            outputStream.write(Arrays.copyOf(readLength, len));
                            outputStream.flush();
                        } catch (IOException e) {
                        }

                        int totalIterations = 16 * 128;
                        int currentIteration = i * 128 + j + 1;
                        int progress = currentIteration * 100 / totalIterations;

                        publishProgress(progress);
                    }
                }
                bos.write(outputStream.toByteArray());
                bos.close();
            } catch (Exception e) {
                Log.e(TAG, "RAM dump failed", e);
                Utils.toast(context, "Error en READRAM\n" + e);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int progress = values[0];
            tv.setText(context.getString(R.string.dumping_ram_wait) + "\n" + progress + "%");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            tv.append("\n" + tv.getContext().getString(R.string.done_dumping_ram));
            powerOff(port, context);

            //To get the extracted file, as the latest one in the directory
            latestFile = null;
            //To get the last created file
            File[] files = Utils.SAVE_FOLDER.listFiles();
            if (files != null && files.length > 0) {
                Arrays.sort(files, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });
                latestFile = files[0];

            }
            byte[] fileBytes = new byte[0];
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fileBytes = Files.readAllBytes(latestFile.toPath());
                } else {
                    FileInputStream fis = new FileInputStream(latestFile);
                    fileBytes = new byte[(int) latestFile.length()];
                    fis.read(fileBytes);
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!magicIsReal(fileBytes)) {
                tv.append(context.getString(R.string.no_valid_file));
                return;
            }

            tv.append(context.getString(R.string.last_sav_name) + latestFile.getName() + ".\n" +
                    context.getString(R.string.size) + latestFile.length() / 1024 + "KB");
            readSav(latestFile, fileBytes, 0);
            btnAddImages.setVisibility(View.VISIBLE);
            btnDelSav.setVisibility(View.VISIBLE);
            layoutCb.setVisibility(View.VISIBLE);
            showImages(cbLastSeen, cbDeleted);

        }
    }

}
