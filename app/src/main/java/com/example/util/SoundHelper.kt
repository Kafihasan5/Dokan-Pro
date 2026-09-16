package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.R

object SoundHelper {
    fun playMessengerSound(context: Context) {
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.messenger_tone) ?: return
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
            mediaPlayer.setOnCompletionListener { mp ->
                try {
                    mp.release()
                } catch (_: Exception) {}
            }
            mediaPlayer.start()
        } catch (_: Exception) {}
    }
}
