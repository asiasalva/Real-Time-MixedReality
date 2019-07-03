package com.unitn.realtimemr;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.usb.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;


import static java.lang.Math.abs;

public class SampleActivity extends Activity {

    private static final String TAG = "ApplicationLogCat";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    private static Camera2Video camera2video;
    private static String file;
    private UsbManager mUSBManager;
    private UsbDeviceConnection mUSBConnection;
    private Bitmap mBitmap;
    private ImageView mAmplitudeView;
    private boolean mOpened;
    private int mScaleFactor;
    private int[] mResolution;
    private static Uri last_video_path;
    private static float pxWidth;
    private static float pxHeight;
    private static int bitmapWidth = 0;
    private static int bitmapHeight = 0;

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
                    Log.e(TAG, "Permission denied for device." + device);
                    Toast.makeText(getApplicationContext(), "Permission denied for device", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "SampleActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_layout);

        camera2video = Camera2Video.newInstance();
        Button btnStart = findViewById(R.id.buttonStart);
        Button btnStartRec = findViewById(R.id.btnStartRec);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnStartProc = findViewById(R.id.btnStartProc);
        mAmplitudeView = findViewById(R.id.imageViewAmplitude);

        btnStop.setEnabled(false);
        btnStartRec.setEnabled(false);
        btnStartProc.setEnabled(false);

        /**
         * Thread for opening pico flexx camera and for starting record an RRF file.
         */
        Thread picoReg = new Thread() {
            @Override
            public void start() {
                openCamera();
            }

            @Override
            public void run() {
                startRecordRRF();
            }
        };

        /**
         * Thread for opening camera2 and for starting record a video.
         */
        Thread camReg = new Thread() {
            @Override
            public void start() {
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraView, camera2video);
                transaction.addToBackStack(null);
                transaction.commit();
            }

            @Override
            public void run() {
                camera2video.startRecordingVideo();
            }
        };

        /**
         * From this point there is the button's control logic
         */

        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "Pressed Start Button");

            //Opening mobile camera
            camReg.start();

            //Opening Pico
            picoReg.start();

            //Work on the other buttons
            btnStart.setEnabled(false);
            btnStartRec.setEnabled(true);
            btnStop.setEnabled(true);
        });


        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "Pressed Stop Button");
            //Close all the cameras and go back to Starting Activity
            onDestroy();
            camera2video.onDestroy();
            btnStop.setEnabled(false);
            startActivity(new Intent(SampleActivity.this, StartingActivity.class));
        });

        btnStartRec.setOnClickListener(v -> {
            Log.d(TAG, "Pressed Rec Button");
            String buttonText = (String) btnStartRec.getText();
            if (buttonText == "StopRec") {
                last_video_path = Uri.parse(camera2video.stopRecordingVideo());
                stopRecordRRF();
                btnStartRec.setEnabled(false);
                btnStartProc.setEnabled(true);
            } else {
                btnStartRec.setText("StopRec");
                picoReg.run();
                camReg.run();
            }
        });

        btnStartProc.setOnClickListener(v -> {
            Log.d(TAG, "Pressed Process Button");
            Toast.makeText(getApplicationContext(), "Processing can take some time to finish.", Toast.LENGTH_SHORT).show();
            startProcessing();
            //Process the RRF file in a PLY file foreach frame
            processRRF();
            btnStartProc.setEnabled(false);
        });
    }

    /**
     * Given RRF file, convert it to PLY format.
     * This function calls the JNI method "convertToPLY"
     */
    private void processRRF() {
        Log.d(TAG, "Starting PLY conversion");
        File dir = getExternalFilesDir(null);
        NativeCamera.semaphoreNotify(true);
        if ((NativeCamera.convertToPLY(file)) != 0) {
            Log.e(TAG, "Something went wrong with conversion");
        } else {
            Toast.makeText(getApplicationContext(), "Files PLY correctly saved", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Given the last recorded video from mobile camera, takes every frame and saves it to a different folder.
     */
    private void startProcessing() {
        Log.d(TAG, "SampleActivity.startProcessing.");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(last_video_path.toString());
        ArrayList<Bitmap> rev = new ArrayList<Bitmap>();
        MediaPlayer mp = MediaPlayer.create(getBaseContext(), last_video_path);
        int millis = mp.getDuration();
        Log.d(TAG, "Millis:" + millis);
        for (int i = 0; i < millis; i += 10) {
            Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            rev.add(bitmap);
        }
        try {
            saveFrames(rev);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Given the Bitmap of the last recorded mobile video saves each frame.
     *
     * @param rev : Array containing each bitmap of the video.
     */
    private void saveFrames(ArrayList<Bitmap> rev) {

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
                if (!f.createNewFile()) {
                    Log.e(TAG, "Error creating new file.");
                }
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes.toByteArray());
                fo.flush();
                fo.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            i++;
        }
        Log.d(TAG, "Frames saved in " + folder_id);
        Toast.makeText(getApplicationContext(), "Frames saved in folder : " + folder_id, Toast.LENGTH_LONG).show();
    }

    /**
     * Calling the stopRegistration Native method, it stops pico from registering data.
     */
    private void stopRecordRRF() {
        NativeCamera.semaphoreNotify(false);
        if ((NativeCamera.stopRegistration()) < 1) {
            Log.e(TAG, "Something went wrong with stop recording.");
            Toast.makeText(getApplicationContext(), "Ooops! Something went wrong.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "RRF file correctly saved", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Function to start the RRF recording and saving.
     */
    private void startRecordRRF() {
        Log.d(TAG, "Start RRF registration");
        //Create the directory in which i will save the rrf data
        File directory = getExternalFilesDir(null);
        File f = new File(directory, "file.rrf" + System.currentTimeMillis());
        //file.rrf, where i will store data
        file = new String(directory.getAbsolutePath() + "/file" + System.currentTimeMillis() + ".rrf");
        //the argv array, the first parameter is always = 0 because in the original command line function it was the file name
        //the array params are: file_path (=0), numberOfFrame = max number of frames i will record, framesToSkip and msToSkip. They will always be = 0 for simplicity.
        int array[] = {0, 0, 0, 0};
        //number of arguments of array, i need it to control everything gone fine in passing arguments
        int argc = 2;
        NativeCamera.semaphoreNotify(true);
        if ((NativeCamera.recordRRF(argc, file, array)) < 1) {
            Log.e(TAG, "Something went wrong with recording");
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

        //check for  user's permissions and request them if not granted yet
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
            Toast.makeText(getApplicationContext(), "Please connect a Royale device", Toast.LENGTH_SHORT).show();
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
        unregisterReceiver(mUsbReceiver);
        if (mUSBConnection != null) {
            mUSBConnection.close();
        }
    }

}