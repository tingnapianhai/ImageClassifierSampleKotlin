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
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.Environment
import android.util.Log

import com.example.androidthings.imageclassifier.classifier.Classifier

import junit.framework.Assert

import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Comparator
import java.util.PriorityQueue

/**
 * Helper functions for the TensorFlow image classifier.
 */
object Helper {

    val IMAGE_SIZE = 224
    private val IMAGE_MEAN = 117
    private val IMAGE_STD = 1f
    private val LABELS_FILE = "imagenet_comp_graph_label_strings.txt"
    val MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb"
    val INPUT_NAME = "input:0"
    val OUTPUT_OPERATION = "output"
    val OUTPUT_NAME = OUTPUT_OPERATION + ":0"
    val OUTPUT_NAMES = arrayOf(OUTPUT_NAME)
    val NETWORK_STRUCTURE = longArrayOf(1, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong(), 3)
    val NUM_CLASSES = 1008

    private val MAX_BEST_RESULTS = 3
    private val RESULT_CONFIDENCE_THRESHOLD = 0.1f

    fun readLabels(context: Context): Array<String> {
        val assetManager = context.assets
        val result = ArrayList<String>()
        try {
            assetManager.open(LABELS_FILE).use { `is` ->
                BufferedReader(InputStreamReader(`is`)).use { br ->
                    var line: String
                    while ((line = br.readLine()) != null) {
                        result.add(line)
                    }
                    return result.toTypedArray<String>()
                }
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Cannot read labels from " + LABELS_FILE)
        }

    }

    fun getBestResults(confidenceLevels: FloatArray, labels: Array<String>): List<Classifier.Recognition> {
        // Find the best classifications.
        val pq = PriorityQueue(MAX_BEST_RESULTS,
                Comparator<Classifier.Recognition> { lhs, rhs ->
                    // Intentionally reversed to put high confidence at the head of the queue.
                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                })

        for (i in confidenceLevels.indices) {
            if (confidenceLevels[i] > RESULT_CONFIDENCE_THRESHOLD) {
                pq.add(Classifier.Recognition("" + i, labels[i], confidenceLevels[i]))
            }
        }

        val recognitions = ArrayList<Classifier.Recognition>()
        val recognitionsSize = Math.min(pq.size, MAX_BEST_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }
        return recognitions
    }

    fun formatResults(results: Array<String>?): String {
        val resultStr: String
        if (results == null || results.size == 0) {
            resultStr = "I don't know what I see."
        } else if (results.size == 1) {
            resultStr = "I see a " + results[0]
        } else {
            resultStr = "I see a " + results[0] + " or maybe a " + results[1]
        }
        return resultStr
    }

    fun getPixels(bitmap: Bitmap, intValues: IntArray, floatValues: FloatArray): FloatArray {
        var bitmap = bitmap
        if (bitmap.width != IMAGE_SIZE || bitmap.height != IMAGE_SIZE) {
            // rescale the bitmap if needed
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, IMAGE_SIZE, IMAGE_SIZE)
        }

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        for (i in intValues.indices) {
            val `val` = intValues[i]
            floatValues[i * 3] = ((`val` shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD
            floatValues[i * 3 + 1] = ((`val` shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD
            floatValues[i * 3 + 2] = ((`val` and 0xFF) - IMAGE_MEAN) / IMAGE_STD
        }
        return floatValues
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    fun saveBitmap(bitmap: Bitmap) {
        val file = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "tensorflow_preview.png")
        Log.d("ImageHelper", String.format("Saving %dx%d bitmap to %s.",
                bitmap.width, bitmap.height, file.absolutePath))

        if (file.exists()) {
            file.delete()
        }
        try {
            FileOutputStream(file).use { fs -> BufferedOutputStream(fs).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 99, out) } }
        } catch (e: Exception) {
            Log.w("ImageHelper", "Could not save image for debugging. " + e.message)
        }

    }

    fun cropAndRescaleBitmap(src: Bitmap, dst: Bitmap, sensorOrientation: Int) {
        Assert.assertEquals(dst.width, dst.height)
        val minDim = Math.min(src.width, src.height).toFloat()

        val matrix = Matrix()

        // We only want the center square out of the original rectangle.
        val translateX = -Math.max(0f, (src.width - minDim) / 2)
        val translateY = -Math.max(0f, (src.height - minDim) / 2)
        matrix.preTranslate(translateX, translateY)

        val scaleFactor = dst.height / minDim
        matrix.postScale(scaleFactor, scaleFactor)

        // Rotate around the center if necessary.
        if (sensorOrientation != 0) {
            matrix.postTranslate(-dst.width / 2.0f, -dst.height / 2.0f)
            matrix.postRotate(sensorOrientation.toFloat())
            matrix.postTranslate(dst.width / 2.0f, dst.height / 2.0f)
        }

        val canvas = Canvas(dst)
        canvas.drawBitmap(src, matrix, null)
    }
}
