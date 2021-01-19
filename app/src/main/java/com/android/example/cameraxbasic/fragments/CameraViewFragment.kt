package com.android.example.cameraxbasic.fragments

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.android.example.cameraxbasic.MainActivity
import com.android.example.cameraxbasic.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.otaliastudios.cameraview.*
import com.otaliastudios.cameraview.controls.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class CameraViewFragment : Fragment() {
    lateinit var camera: CameraView
    private lateinit var container: ConstraintLayout
    private lateinit var photoVideoSwitch: Button
    private lateinit var cameraSwitch: ImageButton
    private lateinit var outputDirectory: File

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_camera_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.camera_container)
        camera = view.findViewById(R.id.camera);
        photoVideoSwitch = view.findViewById(R.id.photoVideoSwitch);
        cameraSwitch = view.findViewById(R.id.camera_switch_button);
        camera.setLifecycleOwner(viewLifecycleOwner)
        camera.addCameraListener(Listener())

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        updateCameraUi()
        cameraSwitch.setOnClickListener { camera.toggleFacing() }
        photoVideoSwitch.setOnClickListener {
            if (camera.mode == Mode.PICTURE) {
                camera.mode = Mode.VIDEO
            } else {
                camera.mode = Mode.PICTURE
            }
            updateCameraUi()
        }

        view.findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener {
            when {
                camera.isTakingVideo -> camera.stopVideo()
                camera.isTakingPicture -> {
                }// ignore
                else -> {
                    if (camera.mode == Mode.PICTURE) camera.takePicture()
                    else {
                        val file = MainActivity.createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)
                        camera.takeVideo(file)
                    }
                }
            }
        }

        // Listener for button used to view the most recent photo
        view.findViewById<ImageButton>(R.id.photo_view_button).setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                        requireActivity(), R.id.fragment_container
                ).navigate(PhotoFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }

    private inner class Listener : CameraListener() {
        override fun onCameraOpened(options: CameraOptions) {
            super.onCameraOpened(options)
        }

        override fun onCameraError(exception: CameraException) {
            super.onCameraError(exception)
            Log.e(TAG, "onCameraError: $exception")
        }


        override fun onPictureTaken(result: PictureResult) {
            if (camera.isTakingVideo) {
                Log.d(TAG, "Image capture while isTakingVideo")
                return
            }
            Log.d(TAG, "Photo capture succeeded")
            val file = MainActivity.createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
            CameraUtils.writeToFile(result.data, file) { file ->
                handleResultFile(file)
            }
        }

        override fun onVideoTaken(result: VideoResult) {
            super.onVideoTaken(result)
            Log.d(TAG, "Video capture succeeded")
            handleResultFile(result.file)
        }

        override fun onVideoRecordingStart() {
            super.onVideoRecordingStart()
            Log.d(TAG, "onVideoRecordingStart")
        }

        override fun onVideoRecordingEnd() {
            super.onVideoRecordingEnd()
            Log.d(TAG, "onVideoRecordingEnd")
        }
    }

    private fun handleResultFile(file: File?) {
        val savedUri = Uri.fromFile(file)
        Log.d(TAG, "handleResultFile: $savedUri")

        // We can only change the foreground Drawable using API level 23+ API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Update the gallery thumbnail with latest picture taken
            setGalleryThumbnail(savedUri)
        }
    }

    private fun updateCameraUi() {
        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.max()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        photoVideoSwitch.text = if (camera.mode == Mode.PICTURE) "Photo" else "Video"
    }

    private fun setGalleryThumbnail(uri: Uri) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = container.findViewById<ImageButton>(R.id.photo_view_button)

        // Run the operations in the view's thread
        thumbnail.post {

            // Remove thumbnail padding
            thumbnail.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thumbnail)
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"
    }
}