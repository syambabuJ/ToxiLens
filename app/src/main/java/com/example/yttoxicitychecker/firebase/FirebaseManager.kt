package com.toxilens.yttoxicitychecker.firebase

import android.util.Log
import com.toxilens.yttoxicitychecker.data.model.VideoData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager {
    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "FirebaseManager"

    private fun getUserId(): String {
        return auth.currentUser?.uid ?: throw Exception("User not logged in")
    }

    suspend fun saveVideoData(videoData: VideoData) {
        try {
            val userId = getUserId()
            val videoRef = database.child("users").child(userId).child("videos").child(videoData.videoId)
            videoRef.setValue(videoData).await()
            Log.d(TAG, "Saved video: ${videoData.videoId}")
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video history", e)
            close(e)
        }
    }

    fun getAllVideos(): Flow<List<VideoData>> = callbackFlow {
        try {
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
                    trySend(videos)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }

            videosRef.addValueEventListener(listener)
            awaitClose { videosRef.removeEventListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all videos", e)
            close(e)
        }
    }
}
