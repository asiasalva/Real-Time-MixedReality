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
import com.pmdtec.sample.FrameBuffer;

import org.opencv.core.Mat;

import wseemann.media.FFmpegMediaMetadataRetriever;

import static java.lang.Math.abs;
import static java.lang.Math.pow;

public class SampleActivity extends Activity {

    //private static int click_count_pico = 0;
    //private static int click_count_camera = 0;
    private static int starting_clicks = 0;
    private static int click_count_registration = 0;
    public static boolean flagFrames = true;

    public static ArrayList<FB> frames_buffer = new ArrayList<>();
    public static ArrayList<FB> frames_buffer_pico = new ArrayList<>();
    private static final String TAG = "ApplicationLogCat";
    private static final String ACTION_USB_PERMISSION = "ACTION_ROYALE_USB_PERMISSION";

    private static String  file;
    private UsbManager mUSBManager;
    private UsbDeviceConnection mUSBConnection;

    private Bitmap mBitmap;
    private ImageView mAmplitudeView;
    private boolean mOpened;


    private int mScaleFactor;
    private int[] mResolution;

    public Calendar c = new GregorianCalendar();


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


        Thread picoReg = new Thread(){
            @Override
            public void start(){
                openCamera();
            }
            @Override
            public void run(){
                Calendar c_c = new GregorianCalendar();
                int minute = c_c.get(Calendar.MINUTE);
                int seconds = c_c.get(Calendar.SECOND);
                int milliseconds = c_c.get(Calendar.MILLISECOND);
                String min_after = Integer.toString(minute);
                String sec_after = Integer.toString(seconds);
                String ms_after = Integer.toString(milliseconds);
                Log.d(TAG, "Before pico start in thread: min: "+min_after+" sec: "+sec_after+" millis: "+ms_after);
                startRecordRRF();
            }
        };
        Thread camReg = new Thread(){
            @Override
            public void start(){
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraView, camera2video);
                transaction.addToBackStack(null);
                transaction.commit();
            }
            @Override
            public void run(){
                Calendar c_c = new GregorianCalendar();
                int minute = c_c.get(Calendar.MINUTE);
                int seconds = c_c.get(Calendar.SECOND);
                int milliseconds = c_c.get(Calendar.MILLISECOND);
                String min_after = Integer.toString(minute);
                String sec_after = Integer.toString(seconds);
                String ms_after = Integer.toString(milliseconds);
                Log.d(TAG, "Before cam2vid start in thread: min: "+min_after+" sec: "+sec_after+" millis: "+ms_after);
                camera2video.startRecordingVideo();
            }
        };

        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "btnStart Listener");

            starting_clicks++;
            if (starting_clicks > 1) {
                Log.e(TAG, "Too much start clicks. Camera already Started.");
                Toast.makeText(getApplicationContext(), "Too much start clicks. Can't start already started cameras.", Toast.LENGTH_LONG).show();
            } else {
                //Opening mobile camera
                camReg.start();
                //Opening Pico
                //openCamera();
                picoReg.start();


                /*FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.cameraView, camera2video);
                transaction.addToBackStack(null);
                transaction.commit();*/
            }

        });


        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "btnStop Listener");

            //Temporaneamente
            flagFrames = false;
            onDestroy();
            camera2video.onDestroy();

            workOnFrames();
            int comparable = FrameBuffer.compareFrames(frames_buffer,frames_buffer_pico);

            if(comparable == 1) //pico buffer < mobile buffer => info saved on pico buffer
            {
                createImage(comparable);
            }

        //Create a logic to stop application or to go ahead in making the reconstruction

        });

        btnStartRec.setOnClickListener(v -> {
            Log.d(TAG, "btnStartRec Listener");
            click_count_registration++;
            if (!(click_count_registration > 1)) {
                btnStartRec.setText("StopRec");
                picoReg.run();
                camReg.run();
                //startRecordRRF();*/
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

            //Process the RRF file in a PLY file foreach frame
            processRRF();
        });
    }

    private void createImage(int comparable) {
        //0 mobile, 1 pico

        for(int i=0; i<frames_buffer_pico.size();i++){
            //frames_buffer_pico.get(i).bitmap.compress(COSE);
            //frames_buffer_pico.get(i).linked.bitmap.compress(COSE);
            //Dove le salvo ste cose ?
        }
    }

    private void workOnFrames() {

        FrameBuffer.compareFrames(frames_buffer,frames_buffer_pico);
        //Create a logic to stop application or to go ahead in making the reconstruction

        //I wanna see timestamps
        //Faccio una media

        int sub_value;
        int sum = 0;
        float average_mobile;
        float average_pico;
        for(int i=0; i<frames_buffer.size();i++){
            //Log.d(TAG,"Timestamp per mobCam l'el : "+i+" e' pari a: "+frames_buffer.get(i).timestamp);
            if( i+1 != frames_buffer.size()) {
                sub_value = abs(frames_buffer.get(i).timestamp) - abs(frames_buffer.get(i+1).timestamp);
                sum = sum + sub_value;
                //Log.d(TAG, "sum mob: " + sum);

            }
        }
        average_mobile = sum / (float)(frames_buffer.size());
        Log.d(TAG, "average mob: " + average_mobile);
        //chooseBestAverage(average_mobile,0);

        sub_value = 0;
        sum = 0;
        for(int i=0; i<frames_buffer_pico.size();i++){
            //Log.d(TAG,"Timestamp per picoCam l'el : "+i+" e' pari a: "+frames_buffer_pico.get(i).timestamp);
            if( i+1 != frames_buffer_pico.size()) {
                sub_value = abs(frames_buffer_pico.get(i).timestamp) - abs(frames_buffer_pico.get(i+1).timestamp);
                sum = sum + sub_value;
                //Log.d(TAG, "sum pico: " + sum);
            }
        }
        average_pico = sum / (float)(frames_buffer_pico.size());
        Log.d(TAG, "average mob: " + average_pico);

        double dev_mobile = calculateStandardDeviation(average_mobile,0);
        double dev_pico = calculateStandardDeviation(average_pico,1);
        Log.d(TAG, "standard deviations: MOB: " + dev_mobile + " PICO: "+dev_pico);
    }

    private double calculateStandardDeviation(float average, int mode) {

        double dev = 0;
        double sub;
        double sum = 0;
        double pow;

        //mode: 0=mobile, 1=pico
        if(mode == 0)
        {
            for(int i=0; i<frames_buffer.size();i++)
            {
                sub = (float)frames_buffer.get(i).timestamp - average;
                pow = Math.pow(sub,2);
                sum = sum + pow;
                dev = Math.sqrt(sum / (frames_buffer.size()));
            }
        }
        else
        {
            for(int i=0; i<frames_buffer_pico.size();i++)
            {
                sub = (float)frames_buffer_pico.get(i).timestamp - average;
                pow = Math.pow(sub,2);
                sum = sum + pow;
                dev = Math.sqrt(sum / (frames_buffer_pico.size()));
            }
        }
        return dev;
    }

    private void processRRF() {
        Log.d(TAG, "Starting PLY conversion");
        File dir = getExternalFilesDir(null);
        NativeCamera.semaphoreNotify(true);
        if ((NativeCamera.convertToPLY(file)) != 0) {
            Log.e(TAG, "Something went wrong with conversion");
        }
        else
        {
            Toast.makeText(getApplicationContext(), "Files PLY correctly saved", Toast.LENGTH_SHORT).show();
        }

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
            Calendar c_after = new GregorianCalendar();
            int minute_after = c_after.get(Calendar.MINUTE);
            int seconds_after = c_after.get(Calendar.SECOND);
            int milliseconds_after = c_after.get(Calendar.MILLISECOND);
            String min_after = Integer.toString(minute_after);
            String sec_after = Integer.toString(seconds_after);
            String ms_after = Integer.toString(milliseconds_after);
            Log.d(TAG, "In pico after recording: min: "+min_after+" sec: "+sec_after+" millis: "+ms_after);
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
        file = new String(directory.getAbsolutePath() + "/file" + System.currentTimeMillis() + ".rrf");
        int array[] = {0, 0, 0, 0};
        int argc = 2;
        NativeCamera.semaphoreNotify(true);
        Calendar c = new GregorianCalendar();
        int minute = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);
        int milliseconds = c.get(Calendar.MILLISECOND);
        String min = Integer.toString(minute);
        String sec = Integer.toString(seconds);
        String ms = Integer.toString(milliseconds);
        Log.d(TAG, "In pico before starting record: min: "+min+" sec: "+sec+" millis: "+ms);
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
        FB element = new FB(mBitmap, (int)System.nanoTime());
        frames_buffer_pico.add(element);

        Log.d(TAG,"Sto catturando i frames di pico");
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
        int minute = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);
        int milliseconds = c.get(Calendar.MILLISECOND);
        String min = Integer.toString(minute);
        String sec = Integer.toString(seconds);
        String ms = Integer.toString(milliseconds);
        Log.d(TAG, "In Pico bitmap: min: "+min+" sec: "+sec+" millis: "+ms);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        double displayWidth = size.x * 0.9;
        mScaleFactor = (int) displayWidth / mResolution[0];

        if (mBitmap == null) {
            mBitmap = Bitmap.createBitmap(mResolution[0], mResolution[1], Bitmap.Config.ARGB_8888);
            //frames_buffer_pico.add(mBitmap);
            //Log.d(TAG,"sto catturando i bitmap di pico"+frames_buffer_pico.toString());

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


