package com.pmdtec.sample;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.*;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.jpeg.JpegSegmentMetadataReader;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.iptc.IptcReader;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


import static java.lang.Math.abs;
import static java.lang.Math.floor;

public class SampleActivity extends Activity {

    private static int starting_clicks = 0;
    private static int click_count_registration = 0;
    public static boolean flagFrames = true;

    public static ArrayList<FB> frames_buffer = new ArrayList<>();
    public static ArrayList<FB> frames_buffer_pico = new ArrayList<>();
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
                    System.out.println("Permission denied for device" + device);
                    Log.e(TAG,"Permission denied for device."+device);
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

        camera2video = Camera2Video.newInstance();
        Button btnStart = findViewById(R.id.buttonStart);
        Button btnStartRec = findViewById(R.id.btnStartRec);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnStartProc = findViewById(R.id.btnStartProc);
        mAmplitudeView = findViewById(R.id.imageViewAmplitude);

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
            Log.e(TAG, "Error Mobile: " + e.toString());
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
            Log.e(TAG, "Error Pico: " + e.toString());
        }
    }

    /**
     * Creates the images corresponding to Bitmaps of pico/mobile and the correspondent temporal-aligned Bitmap.
     * @param comparable : flag which says in which buffer are stored the linked informations.
     *                     0 they are in mobile buffer, 1 in Pico's one.
     * @return returns the number of images saved.
     */
    private int createImage(int comparable) {
        Log.d(TAG, "Create image");

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
                if (!(fb == null)) {
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();
                    fb.bitmap.setHasAlpha(true);
                    fb.bitmap.compress(Bitmap.CompressFormat.PNG, 10, mobile_bytes);
                    fb.linked.bitmap.compress(Bitmap.CompressFormat.JPEG, 40, pico_bytes);
                    File mob_file = new File(saveFolder, ("mobile_frame" + j + ".png"));
                    if(imagePath == null){
                        imagePath = mob_file;
                    }
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".jpg"));
                    try {
                        if(! mob_file.createNewFile() && ! pico_file.createNewFile())
                        {
                            Log.e(TAG,"Files not correctly created");
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
        else
        {

            for (FB fb : frames_buffer_pico)
            {
                if (!(fb.bitmap == null)) {
                    if(bitmapHeight == 0 && bitmapWidth==0){
                        bitmapHeight = fb.linked.bitmap.getHeight();
                        bitmapWidth = fb.linked.bitmap.getWidth();
                        Log.d(TAG," bitmapWidth: "+fb.linked.bitmap.getWidth()+" bitmapHeight "+ fb.linked.bitmap.getHeight());
                        Log.d(TAG," bitmapWidth: "+bitmapWidth+" bitmapHeight "+ bitmapHeight);

                    }
                    ByteArrayOutputStream mobile_bytes = new ByteArrayOutputStream();
                    ByteArrayOutputStream pico_bytes = new ByteArrayOutputStream();
                    fb.bitmap.compress(Bitmap.CompressFormat.JPEG, 40, pico_bytes);
                    fb.linked.bitmap.setHasAlpha(true);
                    fb.linked.bitmap.compress(Bitmap.CompressFormat.PNG, 10, mobile_bytes);
                    File mob_file = new File(saveFolder, ("mobile_frame" + j + ".png"));
                    File pico_file = new File(saveFolder, ("pico_frame" + j + ".jpg"));
                    if(imagePath == null){
                        imagePath = mob_file;
                    }
                    try {
                        if(! mob_file.createNewFile() || ! pico_file.createNewFile())
                        {
                            Log.e(TAG,"Files not correctly created");
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
        return j*2;
    }

    private void workOnFrames() {
        Log.d(TAG, "Sample Activity.workOnFrames");

        int comparable = FrameBuffer.compareFrames(frames_buffer, frames_buffer_pico);
        saveLinkedInfo();

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
        Log.i(TAG, "Mobile Data Average: "+average_mobile+" Mobile Data Standard Deviation: "+dev_mobile);
        Log.i(TAG, "Pico Data Average: "+average_pico+" Pico Data Standard Deviation: "+dev_pico);


        //Creating images
        int images_number = createImage(comparable);
        Toast.makeText(getApplicationContext(), "Correctly saved "+images_number+" images.", Toast.LENGTH_LONG).show();
        getMobileChar();



    }

    private void getMobileChar() {
        Activity activity = camera2video.getAct();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        CameraCharacteristics chars = null;
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            float fl = 0;
            if (focalLengths != null && focalLengths.length > 0) {
                Log.d(TAG,"Sono nell;'if");
                fl = focalLengths[0];
                Log.d(TAG," bitmapWidth: "+bitmapWidth+" bitmapHeight "+ bitmapHeight);
                pxWidth = (bitmapWidth*fl)/sensorSize.getWidth();
                pxHeight = (bitmapHeight*fl)/sensorSize.getHeight();
                float fx = fl/pxWidth;
                float fy = fl/pxHeight;
                Log.d(TAG,"fl: "+ fl + " px: "+ pxWidth + " py: "+pxHeight+" fx: "+fx+" fy: "+fy);
            }
        }catch (CameraAccessException ex)
        {
            Log.e(TAG,"Error: "+ex);
        }
    }

    /**
     * In a file called "infoLinking" will be saved every Pico's timestamp with every corresponding Mobile's frame.
     * We suppose that pico buffer size is lower than mobile's one.
     */
    private void saveLinkedInfo() {
        String folder = getExternalFilesDir(null) + "/data";
        File saveFolder = new File(folder);
        if (!saveFolder.exists()) {
            saveFolder.mkdirs();
        }
        String fileName = "infoLinking.txt";
        String absolutePath = folder + File.separator + fileName;

        String el_toWrite;

        try (FileWriter fileWriter = new FileWriter(absolutePath)) {
            for (FB fb : frames_buffer_pico) {
                el_toWrite = Integer.toString(fb.timestamp) + "   " + Integer.toString(fb.linked.timestamp) + '\n';
                fileWriter.write(el_toWrite);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error: " + e.toString());
        }
    }

    /**
     * Function to calculate standard deviation.
     * @param average : average of the data
     * @param mode : tells which buffer size I have to take: 0 = mobile, 1 = pico.
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
        Log.d(TAG, "millis:" + millis);
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
                if ( ! f.createNewFile() )
                {
                    Log.e(TAG,"Error creating new file.");
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
        Log.d(TAG, "amplitude: " + amplitudes.toString());
        if (!mOpened) {
            Log.d(TAG, "Device in Java not initialized");
            return;
        }
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

    //bitmap = RotateBitmap(bitmap, 90);

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
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