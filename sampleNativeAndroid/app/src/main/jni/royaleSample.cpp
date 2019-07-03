#include <jni.h>

#include <royale.hpp>
#include <royale/IPlaybackStopListener.hpp>
#include <royale/IReplay.hpp>
#include <royale/CameraManager.hpp>
#include <royale/ICameraDevice.hpp>
#include <android/log.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <memory>
#include <sample_utils/PlatformResources.hpp>
#include <condition_variable>
#include <mutex>
#include <cstdint>
#include <fstream>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <math.h>
#include <camera/NdkCameraCaptureSession.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCameraMetadataTags.h>
#include <camera/NdkCaptureRequest.h>

#define TAG "com.pmdtec.jroyale.jni"

// this represents the main camera device object
std::unique_ptr<royale::ICameraDevice> cameraDevice;
bool notified;
royale::LensParameters lens_p;
uint16_t x;
uint16_t y;
uint16_t max = 0;
uint16_t min = 65535;

#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)



#define CHECKED_CAMERA_METHOD(METHOD_TO_INVOKE)\
do\
{\
    auto status = METHOD_TO_INVOKE;\
    if (royale::CameraStatus::SUCCESS != status)\
    {\
        std::cout << royale::getStatusString(status).c_str() << std::endl\
            << "Press Enter to exit the application ...";\
        \
        return -1;\
    }\
} while (0)

namespace
{
    JavaVM *javaVM = nullptr;

    jclass jAmplitudeListener;
    jmethodID jAmplitudeListener_onAmplitudes;
    jobject jAmplitudeListenerObj;

    uint16_t width = 0;
    uint16_t height = 0;

    const float DEFAULT_MIN_DISTANCE = 10; //def 10
    int SEGMENT_MIN_STRENGTH = 300;
    float MAX_DIST_TO_STRENGTHEN = 0.1; // 5 centimeters
    const int SEGMENT_COUNT = 6;
    const int MAX_RETRY_COUNT = 10;
    const int SEGMENT_BAD_DISTANCE = 1100; //def 1100

    class MyListener : public royale::IDepthDataListener
    {
        void onNewData (const royale::DepthData *data) override
        {
            /* Demonstration of how to retrieve exposureTimes
            * There might be different ExposureTimes per RawFrameSet resulting in a vector of
            * exposureTimes, while however the last one is fixed and purely provided for further
            * reference. */
            auto sampleVector (data->exposureTimes);

            if (sampleVector.size() > 2)
            {
                LOGI ("ExposureTimes: %d, %d, %d", sampleVector.at (0), sampleVector.at (1), sampleVector.at (2));
            }


            // Determine min and max value and calculate span.
            size_t i;
            size_t n = width * height;

            uint16_t max = 0;
            uint16_t min = 65535;
            for (i = 0; i < n; i++)
            {
                if (data->points.at (i).z < min)
                {
                    min = data->points.at (i).z;
                }
                if (data->points.at (i).z > max)
                {
                    max = data->points.at (i).z;
                }
            }

           // LOGI("max: %d",max);

            uint16_t span = max - min;

            // Prevent division by zero.
            if (!span)
            {
                span = 1;
            }

            // fill a temp structure to use to populate the java int array
            jint fill[width * height];
            jfloat rawFill[width * height];

            double a;
            int x;
            int y;

            for (i = 0; i < width * height; i++)
            {
                //fill[i] = (int) (((data->points.at (i).z - min) / span) * 255.0f);
                if (data->points.at(i).z < 0.1)
                {
                    fill[i] = (int)0.1;
                    rawFill[i] = 0.1;
                } else if (data->points.at(i).z > 1.0)
                {
                    fill[i] = (int)1.0;
                    rawFill[i] = 1.0;
                } else{
                    // use min value and span to have values between 0 and 255 (for visualisation)
                    fill[i] = (int) (((data->points.at (i).z - min) / span) * 255.0f);
                    rawFill[i] = data->points.at (i).z;
                }

                a = (1-data->points.at(i).z)/0.25;
                x = (int)a;
                y = (int)(255*(a-x));
    //BRG
                if (x == 0)
                {
                    fill[i] = 0 | 255 << 8 | fill[i] << 16 | 255 << 24;
                }
                else if ( x== 1){
                    fill[i] = 0 |  255-fill[i] << 8 | 255 << 16 | 255 << 24;
                }
                else if(x==2){
                    fill[i] = fill[i] |  0 << 8 | 255 << 16 | 255 << 24;
                }
                else if (x==3){
                    fill[i] = fill[i] | 0 << 8 | 255-fill[i] << 16 | 255 << 24;
                }
                else if(x==4){
                    fill[i] = 255 |  0 << 8 | 0 << 16 | 255 << 24;
                }
                //rawFill[i] = data->points.at (i).z;
                /*if (data->points.at(i).z < 0.1)
                {
                    fill[i] = 0.1;
                    rawFill[i] = 0.1;
                } else if (data->points.at(i).z > 1.0)
                {
                    fill[i] = 1.0;
                    rawFill[i] = 1.0;
                } else{
                    // use min value and span to have values between 0 and 255 (for visualisation)
                    fill[i] = (int) (((data->points.at (i).z - min) / span) * 255.0f);
                    rawFill[i] = data->points.at (i).z;
                }
*/

                //set same value for red, green and blue; alpha to 255; to create gray image
                /*if (fill[i] > 255 || fill[i] < 0 || fill[i] == 0) {
                  fill[i] = 0 | 0 << 8 | 0 << 16 | 255 << 24;
                } else {
                    fill[i] = 51 |  fill[i] << 8 | 255-fill[i] << 16 | 255 << 24;
                            //0 |  fill[i] << 8 | 255-fill[i] << 16 | 255 << 24;
                }*/
            }

            // filter stray pixels
            for (i = 1; i < width * height - 1; i++) {
                if (rawFill[i] != 0.0f && rawFill[i-1] <= 0.0 && rawFill[i+1] <= 0.0) {
                    fill[i] = 255 | 0 << 8 | 0 << 16 | 255 << 24;
                    rawFill[i] = 0;
                }
            }

            //Controllo i segmenti eliminando i dati che creano troppo rumore
            jfloat segmentCloseness[SEGMENT_COUNT];
            jfloat segmentMins[SEGMENT_COUNT];
            for (i = 0; i < SEGMENT_COUNT; i++) {
                segmentMins[i] = DEFAULT_MIN_DISTANCE;
            }
            jint segmentMinStrengths[SEGMENT_COUNT];
            for (i = 0; i < SEGMENT_COUNT; i++) {
                segmentMinStrengths[i] = 0;
            }

            float segmentWidth = width / 6;
            int badDataCount = 0;
            int segmentRetryCount = 0;
            bool gotIt = false;

            while (!gotIt && segmentRetryCount < MAX_RETRY_COUNT){
                for (i = 0; i < width * height; i++)
                {
                    int segmentIndex = (int) ((i % width) / segmentWidth);
                    if (segmentMinStrengths[segmentIndex] > SEGMENT_MIN_STRENGTH) {
                        continue; // we already have this segment down, YEAH!!
                    }
                    if (rawFill[i] <= 0) {
                        badDataCount++;
                        continue; // skip bad data
                    }
                    if (rawFill[i] == segmentMins[segmentIndex]) {
                        rawFill[i] = 0; // mark not strong enough minimum as bad, delete
                    }
                    if (rawFill[i] < segmentMins[segmentIndex]) {
                        segmentMins[segmentIndex] = rawFill[i];
                    }
                }

                for (i = 0; i < width * height; i++)
                {
                    if (rawFill[i] == 0) {
                        continue; // skip bad data
                    }
                    int segmentIndex = (int) ((i % width) / segmentWidth);
                    if (rawFill[i] - MAX_DIST_TO_STRENGTHEN < segmentMins[segmentIndex]) {
                        segmentMinStrengths[segmentIndex] += 1;
                    }
                }

                int okCount = 0;
                for (i = 0; i < SEGMENT_COUNT; i++) {
                    if (segmentMinStrengths[i] > SEGMENT_MIN_STRENGTH) {
                        okCount++;
                    }
                }
                gotIt = okCount == SEGMENT_COUNT;
                segmentRetryCount++;
            }

            jint segmentMinCentis[SEGMENT_COUNT];
            for (i = 0; i < SEGMENT_COUNT; i++) {
                if (segmentMinStrengths[i] < SEGMENT_MIN_STRENGTH) {
                    segmentMinCentis[i] = SEGMENT_BAD_DISTANCE;
                } else {
                    segmentMinCentis[i] = (int) (segmentMins[i] * 100);
                }
            }

            // attach to the JavaVM thread and get a JNI interface pointer
            JNIEnv *env;
            javaVM->AttachCurrentThread ( (JNIEnv **) &env, NULL);

            // create java int array
            jintArray intArray = env->NewIntArray (width * height);
            jfloatArray rawFloatArray = env->NewFloatArray (width * height);
            jintArray minDistanceIntArray = env->NewIntArray (6);
            jintArray minDistanceStrengthIntArray = env->NewIntArray (6);

            // populate java int array with fill data
            env->SetIntArrayRegion (intArray, 0, width * height, fill);
            env->SetFloatArrayRegion (rawFloatArray, 0, width * height, rawFill);
            env->SetIntArrayRegion (minDistanceIntArray, 0, 6, segmentMinCentis);
            env->SetIntArrayRegion (minDistanceStrengthIntArray, 0, 6, segmentMinStrengths);

            // call java method and pass amplitude array

            env->CallVoidMethod (jAmplitudeListenerObj, jAmplitudeListener_onAmplitudes, intArray, rawFloatArray, minDistanceIntArray, minDistanceStrengthIntArray, min, max, badDataCount, segmentRetryCount);

            // detach from the JavaVM thread
            javaVM->DetachCurrentThread();
        }
    };

    //MyListener l;


    /*
     * This variables are used to ensure that the
     * main method is open until the camera has stopped capture.
     */
    std::mutex mtx;
    std::condition_variable condition;

    /**
     * The MyRecordListener waits for the camera device to close.
     * Then the MyRocordListener will tidy up the camera device.
     */
    class MyRecordListener : public royale::IRecordStopListener {
    public:

        void onRecordingStopped(const uint32_t numFrames) {
            std::cout << "The onRecordingStopped was invoked with numFrames=" << numFrames
                      << std::endl;

            // Notify the main method to return
            std::unique_lock<std::mutex> lock(mtx);
            notified = true;
            condition.notify_all();
        }
    };
    class MyListenerPLY : public royale::IDepthDataListener
    {
    public:
        MyListenerPLY (std::string rrfFile, uint32_t numFrames) :
                m_frameNumber (0),
                m_numFrames (numFrames),
                m_rrfFile (std::move(rrfFile))
        {
        }

        void writePLY (const std::string &filename, const royale::DepthData *data)
        {
            // For an explanation of the PLY file format please have a look at
            // https://en.wikipedia.org/wiki/PLY_(file_format)
            std::ofstream outputFile;
            std::stringstream stringStream;

            outputFile.open ("/storage/emulated/0/Android/data/com.pmdtec.sample56/files/"+filename, std::ofstream::out | std::ofstream::app );

            if (!(outputFile.is_open()))
            {
                LOGE("Can't create .%s", filename.c_str());
                std::cerr << "Outputfile " << filename << " could not be opened!" << std::endl;
                return;
            }
            else
            {
                // if the file was opened successfully write the PLY header
                LOGI("File successfully opened");
                stringStream << "ply" << std::endl;
                stringStream << "format ascii 1.0" << std::endl;
                stringStream << "comment Generated by sampleExportPLY" << std::endl;
                stringStream << "element vertex " << data->points.size() << std::endl;
                stringStream << "property float x" << std::endl;
                stringStream << "property float y" << std::endl;
                stringStream << "property float z" << std::endl;
                stringStream << "element face 0" << std::endl;
                stringStream << "property list uchar int vertex_index" << std::endl;
                stringStream << "end_header" << std::endl;

                // output XYZ coordinates into one line
                for (size_t i = 0; i < data->points.size(); ++i)
                {
                    stringStream << data->points.at (i).x << " " << data->points.at (i).y << " " << data->points.at (i).z << std::endl;
                    //zvector[i] = data->points.at (i).z;
                }

                // output stringstream to file and close it
                outputFile << stringStream.str();
            }
        }


        void onNewData (const royale::DepthData *data)
        {
            std::stringstream filename;

            m_frameNumber++;

            std::cout << "Exporting frame " << m_frameNumber << " of " << m_numFrames << std::endl;

            filename << m_frameNumber << ".ply";
            writePLY (filename.str(), data);
        }

    private:
        uint32_t m_frameNumber; // The current frame number
        uint32_t m_numFrames;   // Total number of frames in the recording
        std::string m_rrfFile;       // Recording file that was opened
    };

    class MyPlaybackStopListener : public royale::IPlaybackStopListener
    {
    public:
        MyPlaybackStopListener()
        {
            m_playbackRunning = true;
        }

        void onPlaybackStopped()
        {
            std::lock_guard<std::mutex> lock (m_stopMutex);
            m_playbackRunning = false;
        }

        void waitForStop()
        {
            bool running = true;
            do
            {
                {
                    std::lock_guard<std::mutex> lock (m_stopMutex);
                    running = m_playbackRunning;
                }

                std::this_thread::sleep_for (std::chrono::milliseconds (50));
            }
            while (running);
        }

    private:
        std::mutex m_stopMutex;      // Mutex to synchronize the access to m_playbackRunning
        bool m_playbackRunning; // Shows if the playback is still running
    };

    MyRecordListener myRecordListener;
    MyListener listener;

#ifdef __cplusplus
    extern "C" {
#endif

    JNIEXPORT void JNICALL
    Java_com_pmdtec_sample_NativeCamera_semaphoreNotify(JNIEnv *env, jclass type, jboolean notificated) {
        //Set to 1 if need to record, 0 if you want to stop recording
        notified = notificated;
    }


    JNIEXPORT jint JNICALL
    JNI_OnLoad (JavaVM *vm, void *)
    {
        // Cache the java environment for later use.
        javaVM = vm;

        JNIEnv *env;
        if (javaVM->GetEnv (reinterpret_cast<void **> (&env), JNI_VERSION_1_6) != JNI_OK)
        {
            LOGE ("can not cache the java native interface environment");
            return -1;
        }

        // Find the AmplitudeListener class.
        {
            jclass tmpClazz = env->FindClass ("com/pmdtec/sample/NativeCamera$AmplitudeListener");
            if (env->ExceptionCheck ())
            {
                LOGE ("can not find class=[com.pmdtec.sample.NativeCamera.AmplitudeListener] with path=[com/pmdtec/sample/NativeCamera$AmplitudeListener]");
                return -1;
            }
            LOGI ("found class=[com.pmdtec.sample.NativeCamera.AmplitudeListener] with path=[com/pmdtec/sample/NativeCamera$AmplitudeListener]");
            jAmplitudeListener = reinterpret_cast<jclass> (env->NewGlobalRef (tmpClazz));
            env->DeleteLocalRef (tmpClazz);
        }

        // Get the onAmplitudes Method from the AmplitudeListener class.
        {
            jAmplitudeListener_onAmplitudes = env->GetMethodID (jAmplitudeListener, "onAmplitudes", "([I)V");
            if (env->ExceptionCheck ())
            {
                LOGE ("can not get method=[onAmplitudes] with signature=[([I)V] from class=[com.pmdtec.sample.NativeCamera.AmplitudeListener]");
                return -1;
            }
            LOGI ("got method=[onAmplitudes] with signature=[([I)V] from class=[com.pmdtec.sample.NativeCamera.AmplitudeListener]");
        }

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL
    JNI_OnUnload (JavaVM *vm, void *)
    {
        // Obtain the JNIEnv from the VM

        JNIEnv *env;
        vm->GetEnv (reinterpret_cast<void **> (&env), JNI_VERSION_1_6);

        env->DeleteGlobalRef (jAmplitudeListener);
    }

    JNIEXPORT jintArray JNICALL
    Java_com_pmdtec_sample_NativeCamera_openCameraNative (JNIEnv *env, jclass type, jint fd, jint vid, jint pid)
    {
        // std::unique_ptr<MyListener> listener;

        // the camera manager will query for a connected camera
        {
            auto cFD = static_cast<uint32_t> (fd);
            auto cVID = static_cast<uint32_t> (vid);
            auto cPID = static_cast<uint32_t> (pid);

            royale::CameraManager manager;

            auto cameraList = manager.getConnectedCameraList (cFD, cVID, cPID);
            LOGI ("Detected %zu camera(s).", cameraList.size ());

            if (!cameraList.empty ())
            {
                cameraDevice = manager.createCamera (cameraList.at (0));
            }
        }
        // the camera device is now available and CameraManager can be deallocated here

        if (cameraDevice == nullptr)
        {
            LOGI ("Cannot create the camera device");
            return jintArray ();
        }

        // IMPORTANT: call the initialize method before working with the camera device;
        auto ret = cameraDevice->initialize ();
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Cannot initialize the camera device, CODE %d", static_cast<uint32_t> (ret));
        }

        royale::Vector<royale::String> opModes;
        royale::String cameraName;
        royale::String cameraId;

        ret = cameraDevice->getUseCases (opModes);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to get use cases, CODE %d", (int) ret);
        }

        ret = cameraDevice->getMaxSensorWidth (width);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to get max sensor width, CODE %d", (int) ret);
        }

        ret = cameraDevice->getMaxSensorHeight (height);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to get max sensor height, CODE %d", (int) ret);
        }

        ret = cameraDevice->getId (cameraId);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to get camera ID, CODE %d", (int) ret);
        }

        ret = cameraDevice->getCameraName (cameraName);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to get camera name, CODE %d", (int) ret);
        }

        // display some information about the connected camera
        LOGI ("====================================");
        LOGI ("        Camera information");
        LOGI ("====================================");
        LOGI ("Id:              %s", cameraId.c_str ());
        LOGI ("Type:            %s", cameraName.c_str ());
        LOGI ("Width:           %d", width);
        LOGI ("Height:          %d", height);
        LOGI ("Operation modes: %zu", opModes.size ());

        for (int i = 0; i < opModes.size (); i++)
        {
            LOGI ("    %s", opModes.at (i).c_str ());
        }


        // register a data listener
        ret = cameraDevice->registerDataListener(&listener);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to register data listener, CODE %d", (int) ret);
        }

        // set an operation mode
        ret = cameraDevice->setUseCase (opModes[5]);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to set use case, CODE %d", (int) ret);
        }

        cameraDevice->setExposureMode(royale::ExposureMode::AUTOMATIC);

        ret = cameraDevice->startCapture();
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to start capture, CODE %d", (int) ret);
        }


        jint fill[2];
        fill[0] = width;
        fill[1] = height;

        jintArray intArray = env->NewIntArray (2);

        env->SetIntArrayRegion (intArray, 0, 2, fill);

        return intArray;
    }

    JNIEXPORT void JNICALL
    Java_com_royale_royaleandroidexample_MainActivity_setCameraParams (JNIEnv *env, jobject thiz, jint tresholdCm, jint minStrength) {
        MAX_DIST_TO_STRENGTHEN = tresholdCm / 100.0f;
        SEGMENT_MIN_STRENGTH = minStrength;
    }


    JNIEXPORT void JNICALL
    Java_com_pmdtec_sample_NativeCamera_registerAmplitudeListener (JNIEnv *env, jclass type, jobject amplitudeListener)
    {
        std::cout << "Sono nella funzione cpp";
        if (nullptr != jAmplitudeListenerObj)
        {
            env->DeleteGlobalRef (jAmplitudeListenerObj);
        }

        jAmplitudeListenerObj = env->NewGlobalRef (amplitudeListener);
    }

    JNIEXPORT void JNICALL
    Java_com_pmdtec_sample_NativeCamera_closeCameraNative (JNIEnv *env, jclass type)
    {
        cameraDevice->stopCapture ();
    }

    JNIEXPORT jint JNICALL
    Java_com_pmdtec_sample_NativeCamera_recordRRF(JNIEnv *env, jclass type, jint argc, jstring file_name, jintArray argv)
    {
        LOGI ("JNI RECORDRRF");
        // Receive the parameters to capture from the command line:
        if (2 > argc) {
            std::cout << "There are no parameters specified! Use:" << std::endl
                      << file_name
                      << " C:/path/to/file.rrf [numberOfFrames [framesToSkip [msToSkip]]]"
                      << std::endl;
            LOGI ("There are no parameters specified. ");
            return -1;
        }

        const char *file = (*env).GetStringUTFChars(file_name, 0);
        jint *args;

        args=(*env).GetIntArrayElements(argv,0);

        auto numberOfFrames = argc >= 2 ? (args[1]) : 0;
        auto framesToSkip = argc >= 3 ? (args[2]) : 0;
        auto msToSkip = argc >= 4 ? (args[3]) : 0;

        LOGI ("Ho creato le variabili nella funzione jni: %s %d %d %d e notified: %d", file, numberOfFrames, framesToSkip, msToSkip, notified);
        if(notified) {
            LOGI ("Prima di registrare");
            CHECKED_CAMERA_METHOD (cameraDevice->startRecording(file,0x02));
            LOGI ("Sono dopo la registrazione ma nell'if");
            return 1;
        } else {
            LOGI("Can't record now.");
            return -1;
        }

        (*env).ReleaseStringUTFChars(file_name, file);

    }
        JNIEXPORT jint JNICALL
        Java_com_pmdtec_sample_NativeCamera_stopRegistration(JNIEnv *env, jclass type) {
            if (!notified) {
                LOGI("La variabile notified vale: %d", notified);
                CHECKED_CAMERA_METHOD (cameraDevice->stopRecording());
                return 1;
            } else
            {
                LOGI("Can't stop recording.");
                return -1;
            }
        }
    JNIEXPORT jint JNICALL
    Java_com_pmdtec_sample_NativeCamera_convertToPLY(JNIEnv *env, jclass type, jstring file_name) {

        std::unique_ptr<MyListenerPLY> listener;

        //PlaybackStopListener which will be called as soon as the playback stops.
        MyPlaybackStopListener stopListener;

        // Royale's API treats the .rrf file as a camera, which it captures data from.
        std::unique_ptr<royale::ICameraDevice> cameraDevice;

        //create a pointer to file_name to use it as a string instead of a jstring
        const char *filename =  (*env).GetStringUTFChars(file_name,0);

        LOGI("filename = %s ", filename);

        // Use the camera manager to open the recorded file, this block scope is because we can allow
        // the CameraManager to go out of scope once the file has been opened.
        {
            royale::CameraManager manager;

            // create a device from the file
            cameraDevice = manager.createCamera(filename);
        }

        // if the file was loaded correctly the cameraDevice is now available
        if (cameraDevice == nullptr)
        {
            LOGE("Cannot load the file ");
            std::cerr << "Cannot load the file " << filename << std::endl;
            return 1;
        }

        // cast the cameraDevice to IReplay which offers more options for playing back recordings
        auto replayControls = dynamic_cast<royale::IReplay *> (cameraDevice.get());
        if (replayControls == nullptr)
        {
            LOGE("Unable to cast to IReplay interface");
            std::cerr << "Unable to cast to IReplay interface" << std::endl;
            return 1;
        }

        // IMPORTANT: call the initialize method before working with the camera device
        if (cameraDevice->initialize() != royale::CameraStatus::SUCCESS)
        {
            LOGE("Cannot initialize the camera device");
            std::cerr << "Cannot initialize the camera device" << std::endl;
            return 1;
        }

        // turn off the looping of the playback
        replayControls->loop (false);

        // Turn off the timestamps to speed up the conversion. If timestamps are enabled, an .rrf that
        // was recorded at 5FPS will generate callbacks to onNewData() at only 5 callbacks per second.
        replayControls->useTimestamps (true);

        // retrieve the total number of frames from the recording
        auto numFrames = replayControls->frameCount();

        // Create and register the data listener
        listener.reset (new MyListenerPLY (filename, numFrames));
        if (cameraDevice->registerDataListener (listener.get()) != royale::CameraStatus::SUCCESS)
        {
            LOGE("Error registering data listener");
            std::cerr << "Error registering data listener" << std::endl;
            return 1;
        }

        // register a playback stop listener. This will be called as soon
        // as the file has been played back once (because loop is turned off)
        replayControls->registerStopListener (&stopListener);
        // start capture mode
        if (cameraDevice->startCapture() != royale::CameraStatus::SUCCESS)
        {
            LOGE("Error starting the capturing");
            std::cerr << "Error starting the capturing" << std::endl;
            return 1;
        }

        // block until the playback has finished
        stopListener.waitForStop();

        // stop capture mode
        if (cameraDevice->stopCapture() != royale::CameraStatus::SUCCESS)
        {
            LOGE("Error starting the capturing");
            std::cerr << "Error stopping the capturing" << std::endl;
            return 1;
        }

        //Release filename space
        (*env).ReleaseStringUTFChars(file_name, filename);

        return 0;
    }

    JNIEXPORT jfloat JNICALL
    Java_com_pmdtec_sample_NativeCamera_getXFocalLength(JNIEnv *env, jclass type) {
        float focalLX  = lens_p.focalLength.first;
        return focalLX;

    }
    JNIEXPORT jfloat JNICALL
    Java_com_pmdtec_sample_NativeCamera_getYFocalLength(JNIEnv *env, jclass type) {
        float focalLY  = lens_p.focalLength.second;
        return focalLY;
    }
    JNIEXPORT jfloat JNICALL
    Java_com_pmdtec_sample_NativeCamera_getXFocalCenter(JNIEnv *env, jclass type) {
        float focalCenterX  = x;
        return focalCenterX;
    }
    JNIEXPORT jfloat JNICALL
    Java_com_pmdtec_sample_NativeCamera_getYFocalCenter(JNIEnv *env, jclass type) {
        float focalCenterY  = y;
        return focalCenterY;
    }

#ifdef __cplusplus
}
#endif
}
