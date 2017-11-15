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

import android.app.Activity
import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

import com.example.androidthings.imageclassifier.classifier.Classifier
import com.example.androidthings.imageclassifier.classifier.TensorFlowImageClassifier
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManagerService

import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ImageClassifierActivity : Activity(), ImageReader.OnImageAvailableListener {

    private var mImagePreprocessor: ImagePreprocessor? = null
    private var mTtsEngine: TextToSpeech? = null
    private var mTtsSpeaker: TtsSpeaker? = null
    private var mCameraHandler: CameraHandler? = null
    private var mTensorFlowClassifier: TensorFlowImageClassifier? = null

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    private var mImage: ImageView? = null
    private var mResultViews: Array<TextView>? = null

    private val mReady = AtomicBoolean(false)
    private var mButtonDriver: ButtonInputDriver? = null
    private var mReadyLED: Gpio? = null

    private val mInitializeOnBackground = Runnable {
        mImagePreprocessor = ImagePreprocessor()

        mTtsSpeaker = TtsSpeaker()
        mTtsSpeaker!!.setHasSenseOfHumor(true)
        mTtsEngine = TextToSpeech(this@ImageClassifierActivity,
                TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        mTtsEngine!!.language = Locale.US
                        mTtsEngine!!.setOnUtteranceProgressListener(utteranceListener)
                        mTtsSpeaker!!.speakReady(mTtsEngine)
                    } else {
                        Log.w(TAG, "Could not open TTS Engine (onInit status=" + status
                                + "). Ignoring text to speech")
                        mTtsEngine = null
                    }
                })
        mCameraHandler = CameraHandler.instance
        mCameraHandler!!.initializeCamera(
                this@ImageClassifierActivity, mBackgroundHandler!!,
                this@ImageClassifierActivity)

        mTensorFlowClassifier = TensorFlowImageClassifier(this@ImageClassifierActivity)

        setReady(true)
    }

    private val mBackgroundClickHandler = Runnable {
        if (mTtsEngine != null) {
            mTtsSpeaker!!.speakShutterSound(mTtsEngine)
        }
        mCameraHandler!!.takePicture()
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            setReady(false)
        }

        override fun onDone(utteranceId: String) {
            setReady(true)
        }

        override fun onError(utteranceId: String) {
            setReady(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)
        mImage = findViewById(R.id.imageView) as ImageView
        mResultViews = arrayOfNulls(3)
        mResultViews[0] = findViewById(R.id.result1) as TextView
        mResultViews[1] = findViewById(R.id.result2) as TextView
        mResultViews[2] = findViewById(R.id.result3) as TextView

        init()
    }

    private fun init() {
        initPIO()

        mBackgroundThread = HandlerThread("BackgroundThread")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
        mBackgroundHandler!!.post(mInitializeOnBackground)
    }

    private fun initPIO() {
        val pioService = PeripheralManagerService()
        try {
            mReadyLED = pioService.openGpio(BoardDefaults.getGPIOForLED())
            mReadyLED!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
            mButtonDriver = ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_ENTER)
            mButtonDriver!!.register()
        } catch (e: IOException) {
            mButtonDriver = null
            Log.w(TAG, "Could not open GPIO pins", e)
        }

    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "Received key up: " + keyCode + ". Ready = " + mReady.get())
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mReady.get()) {
                setReady(false)
                mBackgroundHandler!!.post(mBackgroundClickHandler)
            } else {
                Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds")
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun setReady(ready: Boolean) {
        mReady.set(ready)
        if (mReadyLED != null) {
            try {
                mReadyLED!!.value = ready
            } catch (e: IOException) {
                Log.w(TAG, "Could not set LED", e)
            }

        }
    }

    override fun onImageAvailable(reader: ImageReader) {
        var bitmap: Bitmap?
        reader.acquireNextImage().use { image -> bitmap = mImagePreprocessor!!.preprocessImage(image) }

        runOnUiThread { mImage!!.setImageBitmap(bitmap) }

        val results = mTensorFlowClassifier!!.doRecognize(bitmap!!)

        Log.d(TAG, "Got the following results from Tensorflow: " + results)
        if (mTtsEngine != null) {
            // speak out loud the result of the image recognition
            mTtsSpeaker!!.speakResults(mTtsEngine, results)
        } else {
            // if theres no TTS, we don't need to wait until the utterance is spoken, so we set
            // to ready right away.
            setReady(true)
        }

        runOnUiThread {
            for (i in mResultViews!!.indices) {
                if (results.size > i) {
                    val r = results[i]
                    mResultViews!![i].text = r.title + " : " + r.confidence!!.toString()
                } else {
                    mResultViews!![i].text = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mBackgroundThread != null) mBackgroundThread!!.quit()
        } catch (t: Throwable) {
            // close quietly
        }

        mBackgroundThread = null
        mBackgroundHandler = null

        try {
            if (mCameraHandler != null) mCameraHandler!!.shutDown()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            if (mTensorFlowClassifier != null) mTensorFlowClassifier!!.destroyClassifier()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            if (mButtonDriver != null) mButtonDriver!!.close()
        } catch (t: Throwable) {
            // close quietly
        }

        if (mTtsEngine != null) {
            mTtsEngine!!.stop()
            mTtsEngine!!.shutdown()
        }
    }

    companion object {
        private val TAG = "ImageClassifierActivity"
    }

}
