package com.aetherwave.player

import com.aetherwave.player.data.Track
import kotlinx.coroutines.flow.MutableStateFlow

class QueueManager {
    val currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentIndex = MutableStateFlow(-1)
    val currentTrack = MutableStateFlow<Track?>(null)
    val shuffleEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(0) // 0: None, 1: All, 2: One

    private var originalQueue: List<Track> = emptyList()

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        originalQueue = tracks
        if (shuffleEnabled.value) {
            val shuffled = tracks.toMutableList()
            if (startIndex in tracks.indices) {
                val startTrack = tracks[startIndex]
                shuffled.remove(startTrack)
                shuffled.shuffle()
                shuffled.add(0, startTrack)
            } else {
                shuffled.shuffle()
            }
            currentQueue.value = shuffled
            currentIndex.value = 0
        } else {
            currentQueue.value = tracks
            currentIndex.value = startIndex
        }
        updateCurrentTrack()
    }

    fun jumpToIndex(index: Int) {
        if (index in currentQueue.value.indices) {
            currentIndex.value = index
            updateCurrentTrack()
        }
    }

    fun getNextTrackIndex(isAutoAdvance: Boolean = false): Int? {
        val queue = currentQueue.value
        if (queue.isEmpty()) return null
        val nextIdx = currentIndex.value + 1
        
        return if (nextIdx < queue.size) {
            nextIdx
        } else {
            if (repeatMode.value == 1 || (!isAutoAdvance && repeatMode.value == 0)) 0 else null
        }
    }

    fun moveToNext(isAutoAdvance: Boolean = false): Boolean {
        if (isAutoAdvance && repeatMode.value == 2) {
            return true
        }
        val nextIdx = getNextTrackIndex(isAutoAdvance)
        if (nextIdx != null) {
            currentIndex.value = nextIdx
            updateCurrentTrack()
            return true
        }
        return false
    }

    fun moveToPrevious(): Boolean {
        val queue = currentQueue.value
        if (queue.isEmpty()) return false
        val prevIdx = currentIndex.value - 1
        return if (prevIdx >= 0) {
            currentIndex.value = prevIdx
            updateCurrentTrack()
            true
        } else {
            if (repeatMode.value == 1) {
                currentIndex.value = queue.size - 1
                updateCurrentTrack()
                true
            } else {
                false
            }
        }
    }

    fun toggleShuffle() {
        shuffleEnabled.value = !shuffleEnabled.value
        val track = currentTrack.value
        if (shuffleEnabled.value) {
            val q = originalQueue.toMutableList()
            if (track != null) q.remove(track)
            q.shuffle()
            if (track != null) q.add(0, track)
            currentQueue.value = q
            currentIndex.value = 0
        } else {
            currentQueue.value = originalQueue
            if (track != null) {
                currentIndex.value = originalQueue.indexOf(track).takeIf { it >= 0 } ?: 0
            }
        }
    }

    fun toggleRepeat() {
        repeatMode.value = (repeatMode.value + 1) % 3
    }

    private fun updateCurrentTrack() {
        val q = currentQueue.value
        val idx = currentIndex.value
        if (idx in q.indices) {
            currentTrack.value = q[idx]
        } else {
            currentTrack.value = null
        }
    }
}
