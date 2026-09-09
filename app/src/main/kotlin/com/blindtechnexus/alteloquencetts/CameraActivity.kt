package com.blindtechnexus.alteloquencetts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera activity with voice guidance for face detection and alignment.
 */
class CameraActivity : ComponentActivity() {
    
    private lateinit var cameraExecutor: ExecutorService
    private var tts: TextToSpeech? = null
    private var lastFaceGuidanceTime = 0L
    private val guidanceCooldown = 2000L // 2 seconds between voice guidance messages
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS initialized for camera guidance")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
        
        setContent {
            CameraScreen(
                onBack = { finish() },
                onSettings = { openVoiceGuidanceSettings() }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts?.shutdown()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start camera
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun openVoiceGuidanceSettings() {
        val intent = Intent(this, VoiceGuidanceSettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun speakGuidance(text: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFaceGuidanceTime > guidanceCooldown && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastFaceGuidanceTime = currentTime
        }
    }
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CAMERA_PERMISSION = 1001
        const val VOICE_GUIDANCE_PREFS = "VoiceGuidancePrefs"
        const val VOICE_GUIDANCE_ENABLED = "voice_guidance_enabled"
        const val VIBRATION_ENABLED = "vibration_enabled"
        const val VIBRATION_INTENSITY = "vibration_intensity"
        const val VIBRATION_WEAK = "weak"
        const val VIBRATION_MEDIUM = "medium"
        const val VIBRATION_STRONG = "strong"
    }
}

/**
 * Composable function for the camera screen.
 */
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text(text = stringResource(R.string.go_back))
            }
            
            Text(
                text = stringResource(R.string.camera_title),
                style = MaterialTheme.typography.titleLarge
            )
            
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.voice_guidance_settings)
                )
            }
        }
        
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                CameraPreview(lifecycleOwner, context)
            }
            
            // Camera controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { /* Take photo logic will be implemented */ },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = stringResource(R.string.capture_button),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.camera_permission_required))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { 
                    permissionLauncher.launch(Manifest.permission.CAMERA) 
                }) {
                    Text(text = stringResource(R.string.camera_permission_rationale))
                }
            }
        }
    }
}

/**
 * Camera preview composable.
 */
@Composable
fun CameraPreview(lifecycleOwner: LifecycleOwner, context: Context) {
    val preview = remember { Preview.Builder().build() }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }
    
    AndroidView(
        factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)
            
            // Set up camera
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // Set up preview
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    
                    // Set up image analysis for face detection
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    
                    imageAnalysis.setAnalyzer(
                        ContextCompat.getMainExecutor(ctx),
                        CameraFaceAnalyzer(ctx)
                    )
                    
                    // Bind to lifecycle
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Error setting up camera", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(16f / 9f)
    )
}

/**
 * Image analyzer for face detection.
 */
class CameraFaceAnalyzer(
    private val context: Context
) : ImageAnalysis.Analyzer {
    
    private var lastGuidanceTime = 0L
    private val guidanceCooldown = 2000L
    private var isFaceAligned = false
    private var lastFacePosition: Rect? = null
    
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        
        val faceDetector = FaceDetection.getClient(options)
        
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                processFaces(faces, imageProxy.width, imageProxy.height)
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Error detecting faces", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
                faceDetector.close()
            }
    }
    
    private fun processFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int) {
        val currentTime = System.currentTimeMillis()
        
        if (faces.isEmpty()) {
            // No face detected
            if (currentTime - lastGuidanceTime > guidanceCooldown) {
                speakGuidance(R.string.no_face_detected)
                lastGuidanceTime = currentTime
            }
            return
        }
        
        // For simplicity, use the first face detected
        val face = faces[0]
        val faceRect = face.boundingBox
        
        // Calculate center of the image
        val centerX = imageWidth / 2f
        val centerY = imageHeight / 2f
        
        // Calculate center of the face
        val faceCenterX = faceRect.centerX()
        val faceCenterY = faceRect.centerY()
        
        // Calculate size thresholds
        val minFaceSize = imageWidth * 0.3f // Face should be at least 30% of image width
        val maxFaceSize = imageWidth * 0.7f // Face should be at most 70% of image width
        val faceWidth = faceRect.width().toFloat()
        
        // Check alignment
        val xDiff = Math.abs(faceCenterX - centerX)
        val yDiff = Math.abs(faceCenterY - centerY)
        
        val xThreshold = imageWidth * 0.15f // 15% from center
        val yThreshold = imageHeight * 0.15f // 15% from center
        
        val isCentered = xDiff <= xThreshold && yDiff <= yThreshold
        val isProperSize = faceWidth >= minFaceSize && faceWidth <= maxFaceSize
        
        if (isCentered && isProperSize) {
            if (!isFaceAligned) {
                // Face is now aligned
                isFaceAligned = true
                speakGuidance(R.string.face_aligned_message)
                vibrate(VIBRATION_MEDIUM)
                lastGuidanceTime = currentTime
            }
        } else {
            isFaceAligned = false
            
            // Provide guidance based on position and size
            if (currentTime - lastGuidanceTime > guidanceCooldown) {
                if (!isProperSize) {
                    if (faceWidth < minFaceSize) {
                        speakGuidance(R.string.move_closer)
                    } else {
                        speakGuidance(R.string.move_away)
                    }
                } else if (!isCentered) {
                    // Provide directional guidance
                    val xDirection = if (faceCenterX < centerX - xThreshold * 0.5) {
                        R.string.move_right
                    } else if (faceCenterX > centerX + xThreshold * 0.5) {
                        R.string.move_left
                    } else if (faceCenterY < centerY - yThreshold * 0.5) {
                        R.string.move_down
                    } else if (faceCenterY > centerY + yThreshold * 0.5) {
                        R.string.move_up
                    } else {
                        // Close to center but not quite there
                        if (xDiff > yDiff) {
                            if (faceCenterX < centerX) R.string.move_a_little_right
                            else R.string.move_a_little_left
                        } else {
                            if (faceCenterY < centerY) R.string.move_a_little_down
                            else R.string.move_a_little_up
                        }
                    }
                    speakGuidance(xDirection)
                }
                lastGuidanceTime = currentTime
            }
        }
        
        lastFacePosition = faceRect
    }
    
    private fun speakGuidance(stringRes: Int) {
        val sharedPrefs = context.getSharedPreferences(CameraActivity.VOICE_GUIDANCE_PREFS, Context.MODE_PRIVATE)
        val voiceGuidanceEnabled = sharedPrefs.getBoolean(CameraActivity.VOICE_GUIDANCE_ENABLED, true)
        
        if (voiceGuidanceEnabled) {
            val tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts.speak(context.getString(stringRes), TextToSpeech.QUEUE_FLUSH, null, null)
                    tts.shutdown()
                }
            }
        }
    }
    
    private fun vibrate(intensity: String) {
        val sharedPrefs = context.getSharedPreferences(CameraActivity.VOICE_GUIDANCE_PREFS, Context.MODE_PRIVATE)
        val vibrationEnabled = sharedPrefs.getBoolean(CameraActivity.VIBRATION_ENABLED, true)
        
        if (!vibrationEnabled) return
        
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, 128))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
}