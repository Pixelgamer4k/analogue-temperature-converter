package com.analogue.temperatureconverter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: TemperatureViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var isAccelerometerFallback = false

    // SoundPool resources
    private lateinit var soundPool: SoundPool
    private var tickSoundId = 0
    private var soundLoaded = false

    // Hardware vibration
    private lateinit var vibrator: Vibrator

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                // Map rotation values to beautiful parallax tilt offsets
                val tiltX = event.values[1] * 2.8f
                val tiltY = event.values[0] * 2.8f
                viewModel.updateTilt(tiltX, tiltY)
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Low-pass filtered tilt mapping from accelerometer
                val tiltX = -event.values[0] * 0.35f
                val tiltY = event.values[1] * 0.35f
                viewModel.updateTilt(tiltX, tiltY)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupImmersiveMode()

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            isAccelerometerFallback = true
        }

        // Initialize Haptics
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        tickSoundId = soundPool.load(this, R.raw.tick, 1)
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFFD4AF37), // Brass gold
                    background = Color(0xFF0F1012),
                    surface = Color(0xFF191B1F)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()

                    // Collect tick events from ViewModel
                    LaunchedEffect(Unit) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is ConverterEvent.Tick -> {
                                    triggerTickSound(event.velocity)
                                    triggerTickHaptic(event.velocity)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // The primary dynamic analogue computer canvas
                        AnalogueComputerCanvas(
                            state = state,
                            onDragStartC = { viewModel.startDragCelsius() },
                            onDragC = { angle -> viewModel.dragCelsiusTo(angle) },
                            onDragEndC = { viewModel.stopDragCelsius() },
                            onDragStartF = { viewModel.startDragFahrenheit() },
                            onDragF = { angle -> viewModel.dragFahrenheitTo(angle) },
                            onDragEndF = { viewModel.stopDragFahrenheit() },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top header
                        TopStatusBar()

                        // Interactive overlay controls
                        BottomControlOverlay(
                            state = state,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    private fun triggerTickSound(velocity: Float) {
        if (!soundLoaded) return
        // Pitch modulation based on speed: slower is lower pitch, faster is crisp high pitch
        val rate = (0.75f + (velocity * 0.04f)).coerceIn(0.7f, 1.8f)
        // Volume maps from quiet click to strong snap
        val volume = (0.15f + (velocity * 0.05f)).coerceIn(0.1f, 1.0f)
        soundPool.play(tickSoundId, volume, volume, 1, 0, rate)
    }

    private fun triggerTickHaptic(velocity: Float) {
        // Create mechanical crisp click sensations on each tick
        val duration = (4 + (velocity * 0.5f)).toLong().coerceIn(2, 12)
        val amplitude = (25 + (velocity * 8)).toInt().coerceIn(15, 255)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // Fail-safe for device support issues
        }
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}

@Composable
fun TopStatusBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ANALOGUE TEMPERATURE COMPUTER",
            color = Color(0xBBD4AF37), // Weathered brass gold
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "GEAR CALIBRATED • MODEL F-C 9:5",
            color = Color(0x66FFFFFF),
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun BottomControlOverlay(state: ConverterUIState, modifier: Modifier = Modifier) {
    var isCalibratorEngaged by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xE61B1D22), Color(0xE60D0E10))
                )
            )
            .border(1.dp, Color(0x33D4AF37), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Digital Calibration HUD Overlay (M3 Expressive expandable panel)
        AnimatedVisibility(
            visible = isCalibratorEngaged,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CELSIUS DIAL",
                            color = Color(0x9900B0FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f°C", state.celsiusValue),
                            color = Color(0xFF00E5FF),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Interlocking Gear Symbol
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.CenterVertically),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙",
                            color = Color(0xFFD4AF37),
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FAHRENHEIT DIAL",
                            color = Color(0xFFFF6D00),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f°F", state.fahrenheitValue),
                            color = Color(0xFFFF9100),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x1AD4AF37))
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Toggle HUD button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { isCalibratorEngaged = !isCalibratorEngaged }
                .background(if (isCalibratorEngaged) Color(0x1AD4AF37) else Color(0x0AFFFFFF))
                .border(1.dp, if (isCalibratorEngaged) Color(0xFFD4AF37) else Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚙",
                color = if (isCalibratorEngaged) Color(0xFFD4AF37) else Color(0x99FFFFFF),
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = if (isCalibratorEngaged) "DISENGAGE CALIBRATOR" else "ENGAGE DIGITAL HUD",
                color = if (isCalibratorEngaged) Color(0xFFD4AF37) else Color(0x99FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
