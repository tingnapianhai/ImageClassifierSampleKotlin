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
package com.example.androidthings.imageclassifier.classifier

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Generic interface for interacting with different recognition engines.
 */
interface Classifier {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    class Recognition(
            /**
             * A unique identifier for what has been recognized. Specific to the class, not the instance of
             * the object.
             */
            val id: String?,
            /**
             * Display name for the recognition.
             */
            val title: String?,
            /**
             * A sortable score for how good the recognition is relative to others. Higher should be better.
             */
            val confidence: Float?) {

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }

            if (title != null) {
                resultString += title + " "
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f)
            }

            return resultString.trim { it <= ' ' }
        }
    }

    fun doRecognize(bitmap: Bitmap): List<Recognition>

    fun destroyClassifier()
}
