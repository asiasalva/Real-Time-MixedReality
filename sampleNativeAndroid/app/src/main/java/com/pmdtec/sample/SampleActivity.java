package com.pmdtec.sample;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.CameraDevice;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.*;
import android.widget.*;

import com.example.android.camera2video.Camera2VideoFragment;
import com.wonderkiln.camerakit.CameraView;

import java.util.*;

public class SampleActivity extends Activity
{
    private static int click_count_normal = 0;
    private static int click_count_pico = 0;
    private static int click_count_registration = 0;
    private static final int VIDEO_CAPTURE = 101;

    private static final String TAG = "RoyaleAndroidSampleV3";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    private UsbManager mUSBManager;
    private UsbDeviceConnection mUSBConnection;

    private Bitmap mBitmap;
    private ImageView mAmplitudeView;
    private CameraView camera;
    private boolean isRecording = false;
    private boolean mOpened;
    private CameraDevice camera2;

    private int mScaleFactor;
    private int[] mResolution;



    /**
     * broadcast receiver for user usb permission dialog
     */
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i(TAG, "SampleActivity.onReceive context = [" + context + "], intent = [" + intent + "]");

            String action = intent.getAction();
            Log.e(TAG, action);
            if (ACTION_USB_PERMISSION.equals(action))
            {
                Log.d(TAG,"sono nell'if di equals");
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                {
                    if (device != null)
                    {
                        NativeCamera.registerAmplitudeListener(SampleActivity.this::onAmplitudes);
                        performUsbPermissionCallback(device);
                        createBitmap();
                    }
                }
                else
                {
                    System.out.println("permission denied for device" + device);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "SampleActivity.onCreate savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);


        Log.d(TAG, "onCreate()");

        Button btnStart = findViewById(R.id.buttonStart);
        Button btnStartRec = findViewById(R.id.btnStartRec);
        Button btnStop = findViewById(R.id.btnStop);
        mAmplitudeView = findViewById(R.id.imageViewAmplitude);
        camera = findViewById(R.id.cameraView);

        Log.e(TAG, "DEBUG R.ID.CAMERA: "+R.id.cameraView);
        Log.e(TAG, "DEBUG CAMERA: "+camera);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
                openMobileCamera();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"Sono nel click del bottone stop");
                if (click_count_normal != click_count_pico )
                {
                    Log.e(TAG,"Something went wrong in click system");
                }
                if (click_count_normal < 1)
                {
                    Log.e(TAG,"You can't stop something that has not ever started");
                }
                else
                {
                    click_count_normal--;
                    click_count_pico--;
                    camera.stop();
                    //how to stop pico?
                    //once finished, control registrations are saved and ask user if he wanna go on.
                }
            }
        });

        btnStartRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click_count_registration++;
                if ( ! (click_count_registration > 1) ) {

                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    Camera2VideoFragment camera2video = Camera2VideoFragment.newInstance();
                    transaction.replace(R.id.cameraView, camera2video);
                    transaction.addToBackStack(null);
                    transaction.commit();

                    Camera2VideoFragment.

                    camera2video.onClick(camera);
                    Log.d(TAG,"Onclick: "+camera.getId());
                    //Camera2VideoFragment transaction = getFragmentManager().beginTransaction();

                    //getFragmentManager().beginTransaction().replace(R.id.cameraView, Fragment.instantiate())


                        //    .replace(R.id.cameraView, Camera2VideoFragment.newInstance())
                      //      .commit();


                    //How to take Pico Data???
                }
                else
                {
                    click_count_registration--;
                    Log.e(TAG,"Too much registration click. End one registration before starting another!");
                }
            }
        });

    }

    /*
    * Start recording the video from mobile camera
     */
    private void startRecording() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        startActivityForResult(intent, VIDEO_CAPTURE);

    }

    private boolean hasCamera() {
        return (getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_CAMERA_FRONT));
    }

    /*
    ** Check that the request code passed through as an argument matches that specified when the intent was launched,
    * verify that the recording session was successful and extract the path of the video media file
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri videoUri = data.getData();
        if (requestCode == VIDEO_CAPTURE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Video saved to:\n" +
                        videoUri, Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Video recording cancelled.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to record video",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /*
    ** Open mobile camera.
     */
    private void openMobileCamera() {
        click_count_normal++;
        if (! (click_count_normal > 1) )
        {
            camera.start();
        }
        else
        {
            click_count_normal--;
            Log.e(TAG,"Too much clicks. Camera already started");
        }
        //camera.start();
    }


    /**
     * Will be invoked on a new frame captured by the camera.
     */
    public void onAmplitudes(int[] amplitudes)
    {
        if (!mOpened)
        {
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
    public void openCamera()
    {
        Log.i(TAG, "SampleActivity.openCamera");
        click_count_pico++;

        if (! (click_count_pico > 1)) {

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
                    Log.d(TAG, "royale device found");
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
                Log.e(TAG, "No royale device found!!!");
            }
        }
        else
        {
            click_count_pico--;
            Log.e(TAG,"Too much clicks. Camera already started");
        }
    }

    private void performUsbPermissionCallback(UsbDevice device)
    {
        Log.i(TAG, "SampleActivity.performUsbPermissionCallback device = [" + device + "]");

        mUSBConnection = mUSBManager.openDevice(device);
        Log.i(TAG, "permission granted for: " + device.getDeviceName() + ", fileDesc: " + mUSBConnection.getFileDescriptor());

        int fd = mUSBConnection.getFileDescriptor();

        mResolution = NativeCamera.openCameraNative(fd, device.getVendorId(), device.getProductId());

        if (mResolution[0] > 0)
        {
            mOpened = true;
        }
    }

    private void createBitmap()
    {
        // calculate scale factor, which scales the bitmap relative to the display mResolution
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        double displayWidth = size.x * 0.9;
        mScaleFactor = (int) displayWidth / mResolution[0];

        if (mBitmap == null)
        {
            mBitmap = Bitmap.createBitmap(mResolution[0], mResolution[1], Bitmap.Config.ARGB_8888);
        }
    }

    @Override
    protected void onPause()
    {
        Log.i(TAG, "SampleActivity.onPause");
        super.onPause();

        if (mOpened)
        {
            NativeCamera.closeCameraNative();
            mOpened = false;
        }

        unregisterReceiver(mUsbReceiver);
    }

    @Override
    protected void onResume()
    {
        Log.i(TAG, "SampleActivity.onResume");
        super.onResume();

        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG, "SampleActivity.onDestroy");
        super.onDestroy();

        Log.d(TAG, "onDestroy()");
        unregisterReceiver(mUsbReceiver);

        if (mUSBConnection != null)
        {
            mUSBConnection.close();
        }
    }

}

