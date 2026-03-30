package com.example.yttoxicitychecker.firebase

import android.util.Log
import com.example.yttoxicitychecker.data.model.VideoData
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager {
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = Firebase.auth
    private val TAG = "FirebaseManager"

    private fun getUserId(): String = auth.currentUser?.uid ?: "anonymous"

    suspend fun saveVideoData(videoData: VideoData) {
        try {
            val userId = getUserId()
            // Save to user's history
            val videoRef = database.child("users").child(userId).child("videos").child(videoData.videoId)
            videoRef.setValue(videoData).await()

            // Save to global app_data for recommendations
            val globalRef = database.child("app_data").child("videos").child(videoData.videoId)
            globalRef.setValue(videoData).await()

            Log.d(TAG, "Saved video: ${videoData.videoId} - ${videoData.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video", e)
            throw e
        }
    }

    suspend fun getVideoData(videoId: String): VideoData? {
        return try {
            val userId = getUserId()
            val snapshot = database.child("users").child(userId).child("videos").child(videoId)
                .get().await()
            snapshot.getValue(VideoData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video", e)
            null
        }
    }

    fun getVideoHistory(): Flow<List<VideoData>> = callbackFlow {
        val userId = getUserId()
        val videosRef = database.child("users").child(userId).child("videos")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val videos = mutableListOf<VideoData>()
                snapshot.children.forEach { videoSnapshot ->
                    videoSnapshot.getValue(VideoData::class.java)?.let {
                        videos.add(it)
                    }
                }
                trySend(videos.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        videosRef.addValueEventListener(listener)
        awaitClose { videosRef.removeEventListener(listener) }
    }

    // Get all videos for recommendations
    fun getAllVideos(): Flow<List<VideoData>> = callbackFlow {
        val videosRef = database.child("app_data").child("videos")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val videos = mutableListOf<VideoData>()
                snapshot.children.forEach { videoSnapshot ->
                    videoSnapshot.getValue(VideoData::class.java)?.let {
                        videos.add(it)
                        Log.d(TAG, "Found video in app_data: ${it.title}")
                    }
                }
                trySend(videos)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting all videos", error.toException())
                close(error.toException())
            }
        }

        videosRef.addValueEventListener(listener)
        awaitClose { videosRef.removeEventListener(listener) }
    }
}