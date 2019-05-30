package com.pmdtec.sample;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


import static java.lang.Math.abs;

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
                picoReg.start();
            }

        });


        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "btnStop Listener");

            //Temporaneamente
            flagFrames = false;
            onDestroy();
            camera2video.onDestroy();

            saveBuffersInfo();
            workOnFrames();

            //Create a logic to stop application

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

    private void saveBuffersInfo() {
        String folder = getExternalFilesDir(null) + "/data";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        String fileName = "infoMobile.txt";
        String absolutePath = folder + File.separator + fileName;

        String el_toWrite;

        try(FileWriter fileWriter = new FileWriter(absolutePath)) {
            for (FB fb_mobile : frames_buffer) {
                el_toWrite = Integer.toString(fb_mobile.timestamp)+'\n';
                fileWriter.write(el_toWrite);
            }
        }catch (IOException e) {
            Log.e(TAG,"Error Mobile: "+e.toString());
        }

        String fileNamePico = "infoPico.txt";
        String absolutePathPico = folder + File.separator + fileNamePico;

        String el_toWritePico;

        try(FileWriter fileWriter = new FileWriter(absolutePathPico)) {
            for (FB fb_pico : frames_buffer_pico) {
                el_toWritePico = Integer.toString(fb_pico.timestamp)+'\n';
                fileWriter.write(el_toWritePico);
            }
        }catch (IOException e) {
            Log.e(TAG,"Error Pico: "+e.toString());
        }
    }

    private void createImage(int comparable) {
        //0 mobile, 1 pico
        Log.d(TAG,"create image");

        Random r = new Random();
        int folder_id = r.nextInt(1000) + 1;

        String folder = getExternalFilesDir(null) + "/videos/comparisons/" + folder_id + "/";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }

        int j = 1;
        if( comparable == 0)
        {
            for (FB fb : frames_buffer)
            {
                if (!(fb == null))
                {
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();

                    fb.linked.bitmap.compress(Bitmap.CompressFormat.JPEG, 40, pico_bytes);

                    File mob_file = new File(saveFolder, ("mobile_frame" +j + ".jpg"));
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".jpg"));
                    try {
                        mob_file.createNewFile();
                        pico_file.createNewFile();
                        FileOutputStream mob_fo = new FileOutputStream(mob_file);
                        FileOutputStream pico_fo = new FileOutputStream(pico_file);
                        fb.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, mobile_bytes);
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
        else
        {
            Log.d(TAG,"Dimensione del buffer di Pico = "+frames_buffer_pico.size());

            for (int i=0; i<frames_buffer_pico.size(); i++)
            {
                if( ! (frames_buffer_pico.get(i) == null ))
                {
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();
                    Log.d(TAG,"Inizio a salvare l'immagine di "+frames_buffer_pico.get(i).timestamp+" e di " + frames_buffer_pico.get(i).linked.timestamp);
                    frames_buffer_pico.get(i).bitmap.compress(Bitmap.CompressFormat.JPEG, 40, pico_bytes);
                    frames_buffer_pico.get(i).linked.bitmap.setHasAlpha(true);
                    frames_buffer_pico.get(i).linked.bitmap.compress(Bitmap.CompressFormat.PNG, 10, mobile_bytes);
                    //fb.linked.bitmap.compress(Bitmap.CompressFormat.PNG, 40, mobile_bytes);
                    //Log.d(TAG,"Ho eseguito la compressione dei bitmap");
                    File mob_file = new File(saveFolder, ("mobile_frame" + j + ".png"));
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".jpg"));

                    try {
                        //Log.d(TAG,"try");
                        mob_file.createNewFile();
                        pico_file.createNewFile();
                        FileOutputStream mob_fo = new FileOutputStream(mob_file);
                        FileOutputStream pico_fo = new FileOutputStream(pico_file);
                        mob_fo.write(mobile_bytes.toByteArray());
                        pico_fo.write(pico_bytes.toByteArray());
                        mob_fo.flush();
                        pico_fo.flush();
                        mob_fo.close();
                        pico_fo.close();
                        //Log.d(TAG,"Fine try");
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error: " + e.toString());
                    }
                    //Log.d(TAG,"Immagini "+i+" salvate correttamente");
                    j++;
                }
            }
        }
    }
    private void workOnFrames() {

        Log.d(TAG, "Sample Activity.workOnFrames");
        int comparable = FrameBuffer.compareFrames(frames_buffer,frames_buffer_pico);
        saveLinkedInfo();
        //Faccio una media
        int sub_value;
        int sum = 0;
        float average_mobile;
        float average_pico;
        for(int i=0; i<frames_buffer.size();i++){
            if( i+1 != frames_buffer.size()) {
                sub_value = abs(frames_buffer.get(i).timestamp) - abs(frames_buffer.get(i+1).timestamp);
                sum = sum + sub_value;
            }
        }
        average_mobile = sum / (float)(frames_buffer.size());
        sub_value = 0;
        sum = 0;
        for(int i=0; i<frames_buffer_pico.size();i++){
            if( i+1 != frames_buffer_pico.size()) {
                sub_value = abs(frames_buffer_pico.get(i).timestamp) - abs(frames_buffer_pico.get(i+1).timestamp);
                sum = sum + sub_value;
            }
        }
        average_pico = sum / (float)(frames_buffer_pico.size());
        double dev_mobile = calculateStandardDeviation(average_mobile,0);
        double dev_pico = calculateStandardDeviation(average_pico,1);
        createImage(comparable);

        Toast.makeText(getApplicationContext(), "Images saved.", Toast.LENGTH_LONG).show();
    }

    private void saveLinkedInfo() {
        String folder = getExternalFilesDir(null) + "/data";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        String fileName = "infoLinking.txt";
        String absolutePath = folder + File.separator + fileName;

        String el_toWrite;

        try(FileWriter fileWriter = new FileWriter(absolutePath)) {
            el_toWrite = "Pico                                Mobile"+'\n';
            for (FB fb : frames_buffer_pico)
            {
                el_toWrite =Integer.toString(fb.timestamp)+" "+Integer.toString(fb.linked.timestamp)+'\n';
                fileWriter.write(el_toWrite);
            }
        }catch (IOException e) {
            Log.e(TAG,"Error Mobile: "+e.toString());
        }
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
        if ((NativeCamera.recordRRF(argc, file, array)) < 1) {
            Log.e(TAG, "Something went wrong with recording");
        }
    }


    /**
     * Will be invoked on a new frame captured by the camera.
     */
    public void onAmplitudes(int[] amplitudes) {
        Log.d(TAG,"amplitude: "+amplitudes.toString());
        if (!mOpened) {
            Log.d(TAG, "Device in Java not initialized");
            return;
        }
        mBitmap.setPixels(amplitudes, 0, mResolution[0], 0, 0, mResolution[0], mResolution[1]);
        runOnUiThread(() -> mAmplitudeView.setImageBitmap(Bitmap.createScaledBitmap(mBitmap,
                mResolution[0] * mScaleFactor,
                mResolution[1] * mScaleFactor, false)));
        Bitmap bitmap = Bitmap.createBitmap(mBitmap);
        FB element = new FB(bitmap, (int)System.currentTimeMillis());
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

    public static Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}