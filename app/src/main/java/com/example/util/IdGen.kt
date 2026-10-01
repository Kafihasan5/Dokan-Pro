package com.example.util

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * IDs for records that are synced to the cloud.
 *
 * Room's auto-increment gave every phone the same sequence (1, 2, 3…), so a sale made on the
 * owner's phone and one made on a staff phone could both be "sale 5" and overwrite each other
 * in Firebase. These IDs are time-based with a random suffix, increase over time, and stay
 * below 2^53 so the web app can read them as JavaScript numbers.
 */
object IdGen {
    private val last = AtomicLong(0)
    private val random = SecureRandom()

    fun next(): Long {
        while (true) {
            val candidate = System.currentTimeMillis() * 1000 + random.nextInt(1000)
            val prev = last.get()
            val id = if (candidate <= prev) prev + 1 else candidate
            if (last.compareAndSet(prev, id)) return id
        }
    }

    /** Keeps an existing ID; assigns a fresh one to a new record (id == 0). */
    fun orNew(id: Long): Long = if (id == 0L) next() else id
}
