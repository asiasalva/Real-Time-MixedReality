#include <jni.h>

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
#include <string>

#define TAG "com.pmdtec.jroyale.jni"

// this represents the main camera device object
std::unique_ptr<royale::ICameraDevice> cameraDevice;
bool notified;

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

    class MyListener : public royale::IDepthDataListener
    {
        void onNewData (const royale::DepthData *data) override
        {
            /* Demonstration of how to retrieve exposureTimes
            * There might be different ExposureTimes per RawFrameSet resulting in a vector of
            * exposureTimes, while however the last one is fixed and purely provided for further
            * reference. */

            if (data->exposureTimes.size () >= 3)
            {
                LOGI ("ExposureTimes: %d, %d, %d", data->exposureTimes.at (0), data->exposureTimes.at (1), data->exposureTimes.at (2));
            }

            // Determine min and max value and calculate span.
            uint16_t max = 0;
            uint16_t min = 65535;

            size_t i;
            size_t n = width * height;

            // Linear search for max and min grayValue.
            for (i = 0; i < n; i++)
            {
                const auto point = data->points.at (i);

                if (point.grayValue < min)
                {
                    min = point.grayValue;
                }
                if (point.grayValue > max)
                {
                    max = point.grayValue;
                }
            }

            uint16_t span = max - min;

            // Prevent division by zero.
            if (!span)
            {
                span = 1;
            }

            // Fill a temp structure to use to populate the java int array;
            jint fill[n];
            for (i = 0; i < n; i++)
            {
                // use min value and span to have values between 0 and 255 (for visualisation)
                fill[i] = (int) ( ( (data->points.at (i).grayValue - min) / (float) span) * 255.0f);
                // set same value for red, green and blue; alpha to 255; to create gray image
                fill[i] = fill[i] | fill[i] << 8 | fill[i] << 16 | 255 << 24;
            }

            // Attach to the JavaVM thread and get a JNI interface pointer.
            JNIEnv *env;
            javaVM->AttachCurrentThread (&env, NULL);

            // Cast the n value to an java size type
            auto jN = static_cast<jsize> (n);

            // create java int array
            jintArray intArray = env->NewIntArray (jN);

            // populate java int array with fill data
            env->SetIntArrayRegion (intArray, 0, jN, fill);

            // call java method and pass amplitude array
            env->CallVoidMethod (jAmplitudeListenerObj, jAmplitudeListener_onAmplitudes, intArray);

            // detach from the JavaVM thread
            javaVM->DetachCurrentThread ();
        }
    };

    /**
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

        // IMPORTANT: call the initialize method before working with the camera device
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
        ret = cameraDevice->registerDataListener (&listener);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to register data listener, CODE %d", (int) ret);
        }

        // set an operation mode
        ret = cameraDevice->setUseCase (opModes[0]);
        if (ret != royale::CameraStatus::SUCCESS)
        {
            LOGI ("Failed to set use case, CODE %d", (int) ret);
        }

        ret = cameraDevice->startCapture ();
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
            CHECKED_CAMERA_METHOD (cameraDevice->startRecording(file));
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

#ifdef __cplusplus
}
#endif
}
