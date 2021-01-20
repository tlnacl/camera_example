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
import com.otaliastudios.cameraview.controls.Flash
import com.otaliastudios.cameraview.controls.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class CameraViewFragment : Fragment() {
    lateinit var cameraView: CameraView
    private lateinit var container: ConstraintLayout
    private lateinit var photoVideoSwitch: Button
    private lateinit var captureButton: Button
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
        cameraView = view.findViewById(R.id.camera_view);
        captureButton = view.findViewById(R.id.camera_capture_button)
        photoVideoSwitch = view.findViewById(R.id.photoVideoSwitch);
        cameraSwitch = view.findViewById(R.id.camera_switch_button);
        cameraView.setLifecycleOwner(viewLifecycleOwner)
        cameraView.addCameraListener(Listener())

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        updateCameraUi()
//        camera.cameraOptions.supportedFacing
        cameraSwitch.setOnClickListener { cameraView.toggleFacing() }
        photoVideoSwitch.setOnClickListener {
            if (cameraView.mode == Mode.PICTURE) {
                cameraView.mode = Mode.VIDEO
            } else {
                cameraView.mode = Mode.PICTURE
            }
            updateCameraUi()
        }

        view.findViewById<ImageButton>(R.id.flash_button)?.let { imageButton ->
            imageButton.setOnClickListener {
                Log.d(TAG, "updateCameraUi: imageButton clicked")
                when (cameraView.flash) {
                    Flash.AUTO -> cameraView.flash = Flash.OFF
                    Flash.OFF -> cameraView.flash = Flash.ON
                    Flash.ON -> cameraView.flash = Flash.AUTO
                    else -> Flash.OFF
                }
            }
        }

        captureButton.setOnClickListener {
            when {
                cameraView.isTakingVideo -> cameraView.stopVideo()
                cameraView.isTakingPicture -> {
                }// ignore
                else -> {
                    if (cameraView.mode == Mode.PICTURE) cameraView.takePicture()
                    else {
                        captureButton.isSelected = true
                        val file = MainActivity.createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)
                        cameraView.takeVideo(file)
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
            if (cameraView.isTakingVideo) {
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
            captureButton.isSelected = false
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

        photoVideoSwitch.text = if (cameraView.mode == Mode.PICTURE) "Photo" else "Video"
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