/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.imageclassifier

import android.speech.tts.TextToSpeech

import com.example.androidthings.imageclassifier.classifier.Classifier.Recognition

import java.util.ArrayList
import java.util.Locale
import java.util.NavigableMap
import java.util.Random
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.TimeUnit

class TtsSpeaker {

    /**
     * Stores joke utterances keyed by time last spoken.
     */
    private val mJokes: NavigableMap<Long, Utterance>

    /**
     * Controls where to use jokes or not. If true, jokes will be applied randomly. If false, no
     * joke will ever be played. Use [.setHasSenseOfHumor] to change the mood.
     */
    private var mHasSenseOfHumor = true

    private val isFeelingFunnyNow: Boolean
        get() = mHasSenseOfHumor && RANDOM.nextFloat() < HUMOR_THRESHOLD

    init {
        mJokes = TreeMap()
        var key = 0L
        for (joke in JOKES) {
            // can't insert them with same key
            mJokes.put(key++, joke)
        }
    }

    fun speakReady(tts: TextToSpeech) {
        tts.speak("I'm ready!", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
    }

    fun speakShutterSound(tts: TextToSpeech) {
        getRandomElement(SHUTTER_SOUNDS).speak(tts)
    }

    fun speakResults(tts: TextToSpeech, results: List<Recognition>) {
        if (results.isEmpty()) {
            tts.speak("I don't understand what I see.", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            if (isFeelingFunnyNow) {
                tts.speak("Please don't unplug me, I'll do better next time.",
                        TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            }
        } else {
            if (isFeelingFunnyNow) {
                playJoke(tts)
            }
            if (results.size == 1 || results[0].confidence > SINGLE_ANSWER_CONFIDENCE_THRESHOLD) {
                tts.speak(String.format(Locale.getDefault(),
                        "I see a %s", results[0].title),
                        TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            } else {
                tts.speak(String.format(Locale.getDefault(), "This is a %s, or maybe a %s",
                        results[0].title, results[1].title),
                        TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            }
        }

    }

    private fun playJoke(tts: TextToSpeech): Boolean {
        val now = System.currentTimeMillis()
        // choose a random joke whose last occurrence was far enough in the past
        val availableJokes = mJokes.headMap(now - JOKE_COOLDOWN_MILLIS)
        var joke: Utterance? = null
        if (!availableJokes.isEmpty()) {
            val r = RANDOM.nextInt(availableJokes.size)
            var i = 0
            for (key in availableJokes.keys) {
                if (i++ == r) {
                    joke = availableJokes.remove(key) // also removes from mJokes
                    break
                }
            }
        }
        if (joke != null) {
            joke.speak(tts)
            // add it back with the current time
            mJokes.put(now, joke)
            return true
        }
        return false
    }

    fun setHasSenseOfHumor(hasSenseOfHumor: Boolean) {
        this.mHasSenseOfHumor = hasSenseOfHumor
    }

    fun hasSenseOfHumor(): Boolean {
        return mHasSenseOfHumor
    }

    internal interface Utterance {

        fun speak(tts: TextToSpeech)
    }

    private open class SimpleUtterance internal constructor(private val mMessage: String) : Utterance {

        override fun speak(tts: TextToSpeech) {
            tts.speak(mMessage, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        }
    }

    private class ShutterUtterance internal constructor(message: String) : SimpleUtterance(message) {

        override fun speak(tts: TextToSpeech) {
            tts.setPitch(1.5f)
            tts.setSpeechRate(1.5f)
            super.speak(tts)
            tts.setPitch(1f)
            tts.setSpeechRate(1f)
        }
    }

    private class ISeeDeadPeopleUtterance : Utterance {

        override fun speak(tts: TextToSpeech) {
            tts.setPitch(0.2f)
            tts.speak("I see dead people...", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            tts.setPitch(1f)
            tts.speak("Just kidding...", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        }
    }

    companion object {

        private val UTTERANCE_ID = "com.example.androidthings.imageclassifier.UTTERANCE_ID"
        private val HUMOR_THRESHOLD = 0.2f
        private val RANDOM = Random()

        private val SHUTTER_SOUNDS = ArrayList<Utterance>()
        private val JOKES = ArrayList<Utterance>()

        init {
            SHUTTER_SOUNDS.add(ShutterUtterance("Click!"))
            SHUTTER_SOUNDS.add(ShutterUtterance("Cheeeeese!"))
            SHUTTER_SOUNDS.add(ShutterUtterance("Smile!"))

            JOKES.add(SimpleUtterance("It's a bird! It's a plane! It's... it's..."))
            JOKES.add(SimpleUtterance("Oops, someone left the lens cap on! Just kidding..."))
            JOKES.add(SimpleUtterance("Hey, that looks like me! Just kidding..."))
            JOKES.add(ISeeDeadPeopleUtterance())
        }

        /**
         * Don't play the same joke within this span of time
         */
        private val JOKE_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(2)

        /**
         * For multiple results, speak only the first if it has at least this much confidence
         */
        private val SINGLE_ANSWER_CONFIDENCE_THRESHOLD = 0.4f

        private fun <T> getRandomElement(list: List<T>): T {
            return list[RANDOM.nextInt(list.size)]
        }
    }
}
