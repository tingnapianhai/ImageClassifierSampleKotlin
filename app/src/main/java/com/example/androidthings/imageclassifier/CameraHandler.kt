/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.imageclassifier

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.util.Size
import java.util.Collections
import android.content.Context.CAMERA_SERVICE

class CameraHandler// Lazy-loaded singleton, so only one instance of the camera is created.
private constructor() {
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    /**
     * An [ImageReader] that handles still image capture.
     */
    private var mImageReader: ImageReader? = null
    /**
     * Callback handling device state changes
     */
    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d(TAG, "Opened camera.")
            mCameraDevice = cameraDevice
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.d(TAG, "Camera disconnected, closing.")
            closeCaptureSession()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, i: Int) {
            Log.d(TAG, "Camera device error, closing.")
            closeCaptureSession()
            cameraDevice.close()
        }

        override fun onClosed(cameraDevice: CameraDevice) {
            Log.d(TAG, "Closed camera, releasing")
            mCameraDevice = null
        }
    }
    /**
     * Callback handling session state changes
     */
    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) {
                return
            }
            // When the session is ready, we start capture.
            mCaptureSession = cameraCaptureSession
            triggerImageCapture()
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            Log.w(TAG, "Failed to configure camera")
        }
    }
    /**
     * Callback handling capture session events
     */
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            Log.d(TAG, "Partial result")
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            session.close()
            mCaptureSession = null
            Log.d(TAG, "CaptureSession closed")
        }
    }

    private object InstanceHolder {
        private val mCamera = CameraHandler()
    }

    /**
     * Initialize the camera device
     */
    fun initializeCamera(context: Context,
                         backgroundHandler: Handler,
                         imageAvailableListener: ImageReader.OnImageAvailableListener) {
        // Discover the camera instance
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camIds = arrayOf<String>()
        try {
            camIds = manager.cameraIdList
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Cam access exception getting IDs", e)
        }

        if (camIds.size < 1) {
            Log.d(TAG, "No cameras found")
            return
        }
        val id = camIds[0]
        Log.d(TAG, "Using camera id " + id)
        // Initialize the image processor
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES)
        mImageReader!!.setOnImageAvailableListener(
                imageAvailableListener, backgroundHandler)
        // Open the camera resource
        try {
            manager.openCamera(id, mStateCallback, backgroundHandler)
        } catch (cae: CameraAccessException) {
            Log.d(TAG, "Camera access exception", cae)
        }

    }

    /**
     * Begin a still image capture
     */
    fun takePicture() {
        if (mCameraDevice == null) {
            Log.w(TAG, "Cannot capture image. Camera not initialized.")
            return
        }
        // Here, we create a CameraCaptureSession for capturing still images.
        try {
            mCameraDevice!!.createCaptureSession(
                    listOf<Surface>(mImageReader!!.surface),
                    mSessionCallback, null)
        } catch (cae: CameraAccessException) {
            Log.d(TAG, "access exception while preparing pic", cae)
        }

    }

    /**
     * Execute a new capture request within the active session
     */
    private fun triggerImageCapture() {
        try {
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            Log.d(TAG, "Capture request created.")
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, null)
        } catch (cae: CameraAccessException) {
            Log.d(TAG, "camera capture exception")
        }

    }

    private fun closeCaptureSession() {
        if (mCaptureSession != null) {
            try {
                mCaptureSession!!.close()
            } catch (ex: Exception) {
                Log.e(TAG, "Could not close capture session", ex)
            }

            mCaptureSession = null
        }
    }

    /**
     * Close the camera resources
     */
    fun shutDown() {
        closeCaptureSession()
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
        }
    }

    companion object {
        private val TAG = CameraHandler::class.java!!.getSimpleName()

        val IMAGE_WIDTH = 640
        val IMAGE_HEIGHT = 480

        private val MAX_IMAGES = 1
        val instance: CameraHandler
            get() = InstanceHolder.mCamera

        /**
         * Helpful debugging method:  Dump all supported camera formats to log.  You don't need to run
         * this for normal operation, but it's very helpful when porting this code to different
         * hardware.
         */
        fun dumpFormatInfo(context: Context) {
            val manager = context.getSystemService(CAMERA_SERVICE) as CameraManager
            var camIds = arrayOf<String>()
            try {
                camIds = manager.cameraIdList
            } catch (e: CameraAccessException) {
                Log.d(TAG, "Cam access exception getting IDs")
            }

            if (camIds.size < 1) {
                Log.d(TAG, "No cameras found")
            }
            val id = camIds[0]
            Log.d(TAG, "Using camera id " + id)
            try {
                val characteristics = manager.getCameraCharacteristics(id)
                val configs = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                for (format in configs!!.outputFormats) {
                    Log.d(TAG, "Getting sizes for format: " + format)
                    for (s in configs.getOutputSizes(format)) {
                        Log.d(TAG, "\t" + s.toString())
                    }
                }
                val effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                for (effect in effects!!) {
                    Log.d(TAG, "Effect available: " + effect)
                }
            } catch (e: CameraAccessException) {
                Log.d(TAG, "Cam access exception getting characteristics.")
            }

        }
    }
}