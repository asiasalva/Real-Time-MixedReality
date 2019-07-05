package com.unitn.realtimemr;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.usb.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static java.lang.Math.abs;


public class AutomaticActivity extends Activity {

    public static boolean flagFrames = false;
    public static ArrayList<FB> frames_buffer = new ArrayList<>();
    public static ArrayList<FB> frames_buffer_pico = new ArrayList<>();
    private static final String TAG = "ApplicationLogCat";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";
    public static Camera2Video camera2video;
    private UsbManager mUSBManager;
    private UsbDeviceConnection mUSBConnection;
    private Bitmap mBitmap;
    private ImageView mAmplitudeView;
    public boolean mOpened;
    private int mScaleFactor;
    private int[] mResolution;
    public static int bitmapWidth = 0;
    public static int bitmapHeight = 0;

    /**
     * Broadcast receiver for user usb permission dialog
     */
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "SampleActivity.onReceive context = [" + context + "], intent = [" + intent + "]");
            String action = intent.getAction();
            Log.e(TAG, action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        NativeCamera.registerAmplitudeListener(AutomaticActivity.this::onAmplitudes);
                        performUsbPermissionCallback(device);
                        createBitmap();
                    }
                } else {
                    Log.e(TAG, "Permission denied for device." + device);
                    Toast.makeText(getApplicationContext(), "Permission denied for device.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "SampleActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.automatic_layout);
        camera2video = Camera2Video.newInstance();
        Button btnStart = findViewById(R.id.buttonStart);
        Button btnStop = findViewById(R.id.btnStop);
        mAmplitudeView = findViewById(R.id.imageViewAmplitude);

        btnStop.setEnabled(false);

        Thread picoReg = new Thread() {
            @Override
            public void start() {
                openCamera();
            }
        };

        Thread camReg = new Thread() {
            @Override
            public void start() {
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraView, camera2video);
                transaction.addToBackStack(null);
                transaction.commit();
            }
        };

        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "Start pressed");
            //Opening mobile camera
            camReg.start();
            //Opening Pico
            picoReg.start();
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        });


        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "Stop pressed.");
            flagFrames = false;
            onPause();
            camera2video.onPause();
            btnStop.setEnabled(false);
            saveBuffersInfo();
            workOnFrames();
            camera2video.onDestroy();
            onDestroy();
            Toast.makeText(getApplicationContext(), "Camera and Pico will be closed.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(AutomaticActivity.this, StartingActivity.class));
        });
    }

    /**
     * Saves on two files two buffer's timestamps.
     * In file "infoMobile" there will be timestamps of mobile camera.
     * In "infoPico" Pico's timestamps.
     * Timestamps are in milliseconds from 1970 up now, so to take real milliseconds of the registration, remember to divide for 1000.
     */
    private void saveBuffersInfo() {
        String folder = getExternalFilesDir(null) + "/data";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        String fileName = "infoMobile.txt";
        String absolutePath = folder + File.separator + fileName;
        String el_toWrite;
        try (FileWriter fileWriter = new FileWriter(absolutePath)) {
            for (FB fb_mobile : frames_buffer) {
                el_toWrite = Integer.toString(fb_mobile.timestamp) + '\n';
                fileWriter.write(el_toWrite);
            }
        } catch (IOException e) {
            Log.e(TAG, "Mobile Error: " + e.toString());
        }
        String fileNamePico = "infoPico.txt";
        String absolutePathPico = folder + File.separator + fileNamePico;
        String el_toWritePico;
        try (FileWriter fileWriter = new FileWriter(absolutePathPico)) {
            for (FB fb_pico : frames_buffer_pico) {
                el_toWritePico = Integer.toString(fb_pico.timestamp) + '\n';
                fileWriter.write(el_toWritePico);
            }
        } catch (IOException e) {
            Log.e(TAG, "Pico Error: " + e.toString());
        }
    }

    /**
     * Creates the images corresponding to Bitmaps of pico/mobile and the correspondent temporal-aligned Bitmap.
     *
     * @param comparable : flag which says in which buffer are stored the linked informations.
     *                   0 they are in mobile buffer, 1 in Pico's one.
     * @return returns the number of images saved.
     */
    private int createImage(int comparable) {
        Log.d(TAG, "Create image");
        Toast.makeText(getApplicationContext(), "Images are being creating, it could takes some time to finish.", Toast.LENGTH_LONG).show();

        Random r = new Random();
        int folder_id = r.nextInt(1000) + 1;
        String folder = getExternalFilesDir(null) + "/videos/comparisons/" + folder_id + "/";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        File imagePath = null;
        int j = 1;
        if (comparable == 0) {
            for (FB fb : frames_buffer) {
                if (!(fb.bitmap == null)) {
                    if (bitmapHeight == 0 && bitmapWidth == 0) {
                        bitmapHeight = fb.bitmap.getHeight();
                        bitmapWidth = fb.bitmap.getWidth();
                    }
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();
                    fb.bitmap.setHasAlpha(true);
                    fb.bitmap.compress(Bitmap.CompressFormat.PNG, 10, mobile_bytes);
                    fb.linked.bitmap.compress(Bitmap.CompressFormat.PNG, 40, pico_bytes);
                    File mob_file = new File(saveFolder, ("mobile_frame" + j + ".png"));
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".png"));
                    if (imagePath == null) {
                        imagePath = mob_file;
                    }
                    try {
                        if (!mob_file.createNewFile() && !pico_file.createNewFile()) {
                            Log.e(TAG, "Files not correctly created");
                        }
                        FileOutputStream mob_fo = new FileOutputStream(mob_file);
                        FileOutputStream pico_fo = new FileOutputStream(pico_file);
                        mob_fo.write(mobile_bytes.toByteArray());
                        pico_fo.write(pico_bytes.toByteArray());
                        mob_fo.flush();
                        pico_fo.flush();
                        mob_fo.close();
                        pico_fo.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error: " + e.toString());
                    }
                    j++;
                }
            }
        } else {

            for (FB fb : frames_buffer_pico) {
                if (!(fb.bitmap == null)) {
                    if (bitmapHeight == 0 && bitmapWidth == 0) {
                        bitmapHeight = fb.linked.bitmap.getHeight();
                        bitmapWidth = fb.linked.bitmap.getWidth();
                    }
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();
                    fb.bitmap.compress(Bitmap.CompressFormat.PNG, 40, pico_bytes);
                    fb.linked.bitmap.setHasAlpha(true);
                    fb.linked.bitmap.compress(Bitmap.CompressFormat.PNG, 10, mobile_bytes);
                    File mob_file = new File(saveFolder, ("mobile_frame" + j + ".png"));
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".png"));
                    if (imagePath == null) {
                        imagePath = mob_file;
                    }
                    try {
                        if (!mob_file.createNewFile() || !pico_file.createNewFile()) {
                            Log.e(TAG, "Files not correctly created");
                        }
                        FileOutputStream mob_fo = new FileOutputStream(mob_file);
                        FileOutputStream pico_fo = new FileOutputStream(pico_file);
                        mob_fo.write(mobile_bytes.toByteArray());
                        pico_fo.write(pico_bytes.toByteArray());
                        mob_fo.flush();
                        pico_fo.flush();
                        mob_fo.close();
                        pico_fo.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error: " + e.toString());
                    }
                    j++;
                }
            }
        }
        return j * 2;
    }

    /**
     * Function to work on captured frames after pressing Stop button.
     */
    private void workOnFrames() {
        Log.d(TAG, "Sample Activity.workOnFrames");

        int comparable = FrameBuffer.compareFrames(frames_buffer, frames_buffer_pico);
        //saveLinkedInfo();

        //Calculating Average of both pico's and mobile's data
        int sub_value;
        int sum = 0;
        float average_mobile;
        float average_pico;
        for (int i = 0; i < frames_buffer.size(); i++) {
            if (i + 1 != frames_buffer.size()) {
                sub_value = abs(frames_buffer.get(i).timestamp) - abs(frames_buffer.get(i + 1).timestamp);
                sum = sum + sub_value;
            }
        }
        average_mobile = sum / (float) (frames_buffer.size());
        sub_value = 0;
        sum = 0;
        for (int i = 0; i < frames_buffer_pico.size(); i++) {
            if (i + 1 != frames_buffer_pico.size()) {
                sub_value = abs(frames_buffer_pico.get(i).timestamp) - abs(frames_buffer_pico.get(i + 1).timestamp);
                sum = sum + sub_value;
            }
        }
        average_pico = sum / (float) (frames_buffer_pico.size());

        //Calculate the standard deviation for data
        double dev_mobile = calculateStandardDeviation(average_mobile, 0);
        double dev_pico = calculateStandardDeviation(average_pico, 1);
        Log.i(TAG, "Mobile Data Average: " + average_mobile + " Mobile Data Standard Deviation: " + dev_mobile);
        Log.i(TAG, "Pico Data Average: " + average_pico + " Pico Data Standard Deviation: " + dev_pico);

        //Creating images
        int images_number = createImage(comparable);
        Toast.makeText(getApplicationContext(), "Correctly saved " + images_number + " images.", Toast.LENGTH_LONG).show();
    }

    /**
     * In a file called "infoLinking" will be saved every Pico's timestamp with every corresponding Mobile's frame.
     *
     * @param comparable 0 if mobile buffer in lower than Pico's one, 1 otherwise.
     */
    private void saveLinkedInfo(int comparable) {
        String folder = getExternalFilesDir(null) + "/data";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        String fileName = "infoLinking.txt";
        String absolutePath = folder + File.separator + fileName;

        String el_toWrite;

        if (comparable == 1) {
            try (FileWriter fileWriter = new FileWriter(absolutePath)) {
                for (FB fb : frames_buffer_pico) {
                    el_toWrite = Integer.toString(fb.timestamp) + "   " + Integer.toString(fb.linked.timestamp) + '\n';
                    fileWriter.write(el_toWrite);
                    Toast.makeText(getApplicationContext(), "Info file correctly saved", Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.toString());
            }
        } else {
            try (FileWriter fileWriter = new FileWriter(absolutePath)) {
                for (FB fb : frames_buffer) {
                    el_toWrite = Integer.toString(fb.timestamp) + "   " + Integer.toString(fb.linked.timestamp) + '\n';
                    fileWriter.write(el_toWrite);
                    Toast.makeText(getApplicationContext(), "Info file correctly saved", Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.toString());
            }
        }
    }

    /**
     * Function to calculate standard deviation.
     *
     * @param average : average of the data
     * @param mode    : tells which buffer size I have to take: 0 = mobile, 1 = pico.
     * @return
     */
    private double calculateStandardDeviation(float average, int mode) {

        double dev = 0;
        double sub;
        double sum = 0;
        double pow;

        if (mode == 0) {
            for (int i = 0; i < frames_buffer.size(); i++) {
                sub = (float) frames_buffer.get(i).timestamp - average;
                pow = Math.pow(sub, 2);
                sum = sum + pow;
                dev = Math.sqrt(sum / (frames_buffer.size()));
            }
        } else {
            for (int i = 0; i < frames_buffer_pico.size(); i++) {
                sub = (float) frames_buffer_pico.get(i).timestamp - average;
                pow = Math.pow(sub, 2);
                sum = sum + pow;
                dev = Math.sqrt(sum / (frames_buffer_pico.size()));
            }
        }
        return dev;
    }

    /**
     * Will be invoked on a new frame captured by the camera.
     */
    public void onAmplitudes(int[] amplitudes) {
        if (!mOpened) {
            Log.d(TAG, "Device in Java not initialized");
            return;
        }
        flagFrames = true;
        mBitmap.setPixels(amplitudes, 0, mResolution[0], 0, 0, mResolution[0], mResolution[1]);
        runOnUiThread(() -> mAmplitudeView.setImageBitmap(Bitmap.createScaledBitmap(mBitmap,
                mResolution[0] * mScaleFactor,
                mResolution[1] * mScaleFactor, false)));
        Bitmap bitmap = Bitmap.createBitmap(mBitmap);
        FB element = new FB(bitmap, (int) System.currentTimeMillis());
        frames_buffer_pico.add(element);
    }

    /*
     ** Open Pico Camera
     */
    public void openCamera() {
        Log.i(TAG, "Opening Pico Camera");

        //check permission and request if not granted yet
        mUSBManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        if (mUSBManager != null) {
            Log.d(TAG, "Manager valid");
        }

        HashMap<String, UsbDevice> deviceList = mUSBManager.getDeviceList();

        Log.d(TAG, "USB Devices : " + deviceList.size());

        Iterator<UsbDevice> iterator = deviceList.values().iterator();
        UsbDevice device;
        boolean found = false;
        while (iterator.hasNext()) {
            device = iterator.next();
            if (device.getVendorId() == 0x1C28 ||
                    device.getVendorId() == 0x058B ||
                    device.getVendorId() == 0x1f46) {
                Log.d(TAG, "Royale device found");
                found = true;
                if (!mUSBManager.hasPermission(device)) {
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    intent.setAction(ACTION_USB_PERMISSION);
                    PendingIntent mUsbPi = PendingIntent.getBroadcast(this, 0, intent, 0);
                    mUSBManager.requestPermission(device, mUsbPi);
                } else {
                    NativeCamera.registerAmplitudeListener(this::onAmplitudes);
                    performUsbPermissionCallback(device);
                    createBitmap();
                }
                break;
            }
        }
        if (!found) {
            Toast.makeText(getApplicationContext(), "Please connect a royale device.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No Royale device found!");
        }

    }

    private void performUsbPermissionCallback(UsbDevice device) {
        Log.i(TAG, "SampleActivity.performUsbPermissionCallback device = [" + device + "]");
        mUSBConnection = mUSBManager.openDevice(device);
        Log.i(TAG, "permission granted for: " + device.getDeviceName() + ", fileDesc: " + mUSBConnection.getFileDescriptor());
        int fd = mUSBConnection.getFileDescriptor();
        mResolution = NativeCamera.openCameraNative(fd, device.getVendorId(), device.getProductId());
        if (mResolution[0] > 0) {
            mOpened = true;
        }
    }

    private void createBitmap() {
        // calculate scale factor, which scales the bitmap relative to the display mResolution
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        double displayWidth = size.x * 0.9;
        mScaleFactor = (int) displayWidth / mResolution[0];
        if (mBitmap == null) {
            mBitmap = Bitmap.createBitmap(mResolution[0], mResolution[1], Bitmap.Config.ARGB_8888);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "SampleActivity.onPause");
        super.onPause();

        if (mOpened) {
            NativeCamera.closeCameraNative();
            mOpened = false;
        }

        unregisterReceiver(mUsbReceiver);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "SampleActivity.onResume");
        super.onResume();

        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "SampleActivity.onDestroy");
        super.onDestroy();

        Log.d(TAG, "onDestroy()");
        unregisterReceiver(mUsbReceiver);

        if (mUSBConnection != null) {
            mUSBConnection.close();
        }
    }

}