package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.Camera
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ImageButton
import androidx.camera.core.CameraX
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.VideoCapture
import androidx.camera.view.CameraView
import androidx.camera.view.CameraView.CaptureMode.IMAGE
import androidx.camera.view.CameraView.CaptureMode.VIDEO
import androidx.core.net.toFile
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.android.example.cameraxbasic.MainActivity
import com.android.example.cameraxbasic.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

//TODO cameraView with photo and video still have issues. No offical sample yet.
class CameraViewFragment : Fragment() {
    lateinit var cameraView: CameraView

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var outputDirectory: File

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_camera_view, container, false)

    @SuppressLint("MissingPermission", "RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        cameraView = view.findViewById(R.id.camera_view)
        cameraView.bindToLifecycle(this)

        view.findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener {
            when (cameraView.captureMode) {
                IMAGE -> {
                    // Create output file to hold the image
                    val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    cameraView.takePicture(
                            outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                            Log.d(TAG, "Photo capture succeeded: $savedUri")

                            // We can only change the foreground Drawable using API level 23+ API
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                // Update the gallery thumbnail with latest picture taken
                                setGalleryThumbnail(view, savedUri)
                            }

                            // Implicit broadcasts will be ignored for devices running API level >= 24
                            // so if you only target API level 24+ you can remove this statement
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                requireActivity().sendBroadcast(
                                        Intent(Camera.ACTION_NEW_PICTURE, savedUri)
                                )
                            }

                            // If the folder selected is an external media directory, this is
                            // unnecessary but otherwise other apps will not be able to access our
                            // images unless we scan them using [MediaScannerConnection]
                            val mimeType = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(savedUri.toFile().absolutePath),
                                    arrayOf(mimeType)
                            ) { _, uri ->
                                Log.d(TAG, "Image capture scanned into media store: $uri")
                            }
                        }
                    })
                }
                VIDEO -> {
                    // Get a stable reference of the modifiable image capture use case
                    if (cameraView.isRecording) {
                        Log.d(TAG, "stopRecording")
                        cameraView.stopRecording()
                    } else {
                        // TODO disable all other buttons
                        // Create output file to hold the image
                        val photoFile = createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)


                        // Create output options object which contains file + metadata
                        val outputOptions = VideoCapture.OutputFileOptions.Builder(photoFile).build()


                        // Setup image capture listener which is triggered after photo has been taken
                        Log.d(TAG, "startRecording")
                        cameraView.startRecording(
                                outputOptions, cameraExecutor, object : VideoCapture.OnVideoSavedCallback {
                            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                                Log.e(TAG, "Video capture failed: ${cause?.message}", cause)
                            }

                            override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                                Log.d(TAG, "Video capture succeeded: $savedUri")

                                // We can only change the foreground Drawable using API level 23+ API
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    // Update the gallery thumbnail with latest picture taken
                                    setGalleryThumbnail(view, savedUri)
                                }

                                // Implicit broadcasts will be ignored for devices running API level >= 24
                                // so if you only target API level 24+ you can remove this statement
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    requireActivity().sendBroadcast(
                                            Intent(android.hardware.Camera.ACTION_NEW_VIDEO, savedUri)
                                    )
                                }

                                // If the folder selected is an external media directory, this is
                                // unnecessary but otherwise other apps will not be able to access our
                                // images unless we scan them using [MediaScannerConnection]
                                val mimeType = MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(savedUri.toFile().extension)
                                MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(savedUri.toFile().absolutePath),
                                        arrayOf(mimeType)
                                ) { _, uri ->
                                    Log.d(TAG, "Image capture scanned into media store: $uri")
                                }
                            }
                        })
                    }
                }
            }
        }

        view.findViewById<ImageButton>(R.id.camera_switch_button).let {
            cameraView.toggleCamera()
        }

        view.findViewById<ImageButton>(R.id.photo_view_button).setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                        requireActivity(), R.id.fragment_container
                ).navigate(PhotoFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath))
            }
        }

        view.findViewById<ImageButton>(R.id.flash_button).setOnClickListener {
            if (cameraView.captureMode == IMAGE) {
                cameraView.flash = if (cameraView.flash == FLASH_MODE_ON) FLASH_MODE_OFF else FLASH_MODE_ON
            }
        }

        val photoVideoSwitch = view.findViewById<Button>(R.id.photoVideoSwitch)
        photoVideoSwitch.setOnClickListener {
            if (cameraView.captureMode == IMAGE) {
                photoVideoSwitch.text = "Video"
                cameraView.captureMode = VIDEO
            } else {
                photoVideoSwitch.text = "Photo"
                cameraView.captureMode = IMAGE
            }
        }
    }

    private fun setGalleryThumbnail(view: View, uri: Uri) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = view.findViewById<ImageButton>(R.id.photo_view_button)

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

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PhotoFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
//        CameraX.shutdown()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraView"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)
    }
}