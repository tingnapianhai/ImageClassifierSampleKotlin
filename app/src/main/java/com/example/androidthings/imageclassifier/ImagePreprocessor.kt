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

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.media.Image

import junit.framework.Assert

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Class that process an Image and extracts a Bitmap in a format appropriate for
 * the TensorFlow model.
 */
class ImagePreprocessor {

    private var rgbFrameBitmap: Bitmap? = null
    private val croppedBitmap: Bitmap?

    init {
        this.croppedBitmap = Bitmap.createBitmap(Helper.IMAGE_SIZE, Helper.IMAGE_SIZE,
                Config.ARGB_8888)
        this.rgbFrameBitmap = Bitmap.createBitmap(CameraHandler.IMAGE_WIDTH,
                CameraHandler.IMAGE_HEIGHT, Config.ARGB_8888)
    }

    fun preprocessImage(image: Image?): Bitmap? {
        if (image == null) {
            return null
        }

        Assert.assertEquals("Invalid size width", rgbFrameBitmap!!.width, image.width)
        Assert.assertEquals("Invalid size height", rgbFrameBitmap!!.height, image.height)

        if (croppedBitmap != null && rgbFrameBitmap != null) {
            val bb = image.planes[0].buffer
            rgbFrameBitmap = BitmapFactory.decodeStream(ByteBufferBackedInputStream(bb))
            Helper.cropAndRescaleBitmap(rgbFrameBitmap!!, croppedBitmap, 0)
        }

        image.close()

        // For debugging
        if (SAVE_PREVIEW_BITMAP) {
            Helper.saveBitmap(croppedBitmap!!)
        }
        return croppedBitmap
    }

    private class ByteBufferBackedInputStream(internal var buf: ByteBuffer) : InputStream() {

        @Throws(IOException::class)
        override fun read(): Int {
            return if (!buf.hasRemaining()) {
                -1
            } else buf.get() and 0xFF
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            var len = len
            if (!buf.hasRemaining()) {
                return -1
            }

            len = Math.min(len, buf.remaining())
            buf.get(bytes, off, len)
            return len
        }
    }

    companion object {
        private val SAVE_PREVIEW_BITMAP = false
    }
}