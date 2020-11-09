package com.android.example.cameraxbasic.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.android.example.cameraxbasic.R
import java.io.File

class MediaVideoFragment internal constructor() : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?) = inflater.inflate(R.layout.fragment_media_video, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val resource = args.getString("file_name")?.let { File(it) } ?: R.drawable.ic_photo
        val videoView = view.findViewById<VideoView>(R.id.videoView)
        Log.d("MediaVideoFragment", "onViewCreated resource: $resource")
        if (resource is File && resource.extension.equals("mp4", true)) {
            videoView.setVideoPath(resource.absolutePath)
            val mediaController = MediaController(requireContext())
            videoView.setMediaController(mediaController)
            videoView.start()
        }
    }

    companion object {
        private const val FILE_NAME_KEY = "file_name"

        fun create(image: File) = MediaVideoFragment().apply {
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, image.absolutePath)
            }
        }
    }
}