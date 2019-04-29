package com.pmdtec.sample;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.CameraDevice;
import android.hardware.usb.*;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class SampleActivity extends Activity {

    //private static int click_count_pico = 0;
    //private static int click_count_camera = 0;
    private static int starting_clicks = 0;
    private static int click_count_registration = 0;

    private static final String TAG = "ApplicationLogCat";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    private UsbManager mUSBManager;
    private UsbDeviceConnection mUSBConnection;

    private Bitmap mBitmap;
    private ImageView mAmplitudeView;
    private boolean mOpened;


    private int mScaleFactor;
    private int[] mResolution;

    private static Uri last_video_path;

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
                        NativeCamera.registerAmplitudeListener(SampleActivity.this::onAmplitudes);
                        performUsbPermissionCallback(device);
                        createBitmap();
                    }
                } else {
                    System.out.println("Permission denied for device" + device);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "SampleActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        Log.d(TAG, "onCreate()");

        Camera2Video camera2video = Camera2Video.newInstance();
        Button btnStart = findViewById(R.id.buttonStart);
        Button btnStartRec = findViewById(R.id.btnStartRec);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnStartProc = findViewById(R.id.btnStartProc);
        mAmplitudeView = findViewById(R.id.imageViewAmplitude);

        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "btnStart Listener");

            starting_clicks++;
            if (starting_clicks > 1) {
                Log.e(TAG, "Too much start clicks. Camera already Started.");
                Toast.makeText(getApplicationContext(), "Too much start clicks. Can't start already started cameras.", Toast.LENGTH_LONG).show();
            } else {
                //Opening Pico
                openCamera();

                //Opening mobile camera
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraView, camera2video);
                transaction.addToBackStack(null);
                transaction.commit();
            }

        });

        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "btnStop Listener");
            //Create a logic to stop application or to go ahead in making the reconstruction
        });

        btnStartRec.setOnClickListener(v -> {
            Log.d(TAG, "btnStartRec Listener");
            click_count_registration++;
            if (!(click_count_registration > 1)) {
                btnStartRec.setText("StopRec");
                camera2video.startRecordingVideo();
                startRecordRRF();
            } else {
                btnStartRec.setText("StartRec");
                click_count_registration--;
                last_video_path = Uri.parse(camera2video.stopRecordingVideo());
                stopRecordRRF();
            }
        });

        btnStartProc.setOnClickListener(v -> {
            Log.d(TAG, "btnStartProc Listener");
            //Start Processing mobile camera video
            startProcessing();
        });
    }

    private void startProcessing() {
        Log.d(TAG, "start proc");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        retriever.setDataSource(last_video_path.toString());
        ArrayList<Bitmap> rev = new ArrayList<Bitmap>();

        MediaPlayer mp = MediaPlayer.create(getBaseContext(), last_video_path);

        int millis = mp.getDuration();
        Log.d(TAG, "millis:" + millis);
        for (int i = 0; i < millis; i += 10) {
            Log.d(TAG, "millisecond: " + i);
            Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            rev.add(bitmap);
        }
        Log.d(TAG, "out from the loop");
        try {
            saveFrames(rev);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveFrames(ArrayList<Bitmap> rev) {
        //File directory = getExternalFilesDir(null);
        Random r = new Random();
        int folder_id = r.nextInt(1000) + 1;

        String folder = getExternalFilesDir(null) + "/videos/frames/" + folder_id + "/";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }

        int i = 1;
        for (Bitmap b : rev) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 40, bytes);
            File f = new File(saveFolder, ("frame" + i + ".jpg"));

            try {
                f.createNewFile();
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes.toByteArray());
                fo.flush();
                fo.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            i++;
        }
        Log.d(TAG, "saved in " + folder_id);
        Toast.makeText(getApplicationContext(), "Folder id : " + folder_id, Toast.LENGTH_LONG).show();

    }

    private void stopRecordRRF() {
        NativeCamera.semaphoreNotify(false);
        if ((NativeCamera.stopRegistration()) < 1) {
            Log.e(TAG, "Something went wrong with stop recording.");
        } else {
            Toast.makeText(getApplicationContext(), "File correctly saved", Toast.LENGTH_SHORT).show();
        }
    }

    /*
     ** @param directory : the directory in which i will save the rrf data
     *  @param f : file.rrf, where i will store data
     *  @param file : the file path i will pass to jni function
     *  @param array : the argv array, the first parameter is always = 0 because in the original function it was the file name
     *  @param argc = number of arguments of array, i need it to control everything gone fine in passing arguments
     *  the array params are: file_path (=0), numberOfFrame = max number of frames i will record, framesToSkip and msToSkip. They will always be = 0 for simplicity.
     */
    private void startRecordRRF() {
        Log.d(TAG, "Start RRF registration");
        File directory = getExternalFilesDir(null);
        File f = new File(directory, "file.rrf" + System.currentTimeMillis());
        String file = new String(directory.getAbsolutePath() + "/file" + System.currentTimeMillis() + ".rrf");
        int array[] = {0, 0, 0, 0};
        int argc = 2;
        Log.d(TAG, "ho creato gli argomenti");
        NativeCamera.semaphoreNotify(true);
        if ((NativeCamera.recordRRF(argc, file, array)) < 1) {
            Log.e(TAG, "something went wrong with recording");
        }
    }


    /**
     * Will be invoked on a new frame captured by the camera.
     */
    public void onAmplitudes(int[] amplitudes) {
        if (!mOpened) {
            Log.d(TAG, "Device in Java not initialized");
            return;
        }
        mBitmap.setPixels(amplitudes, 0, mResolution[0], 0, 0, mResolution[0], mResolution[1]);
        runOnUiThread(() -> mAmplitudeView.setImageBitmap(Bitmap.createScaledBitmap(mBitmap,
                mResolution[0] * mScaleFactor,
                mResolution[1] * mScaleFactor, false)));
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


