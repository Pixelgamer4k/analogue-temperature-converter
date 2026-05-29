package com.analogue.temperatureconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class GearState(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val teeth: Int,
    val radius: Float,
    val angle: Float,
    val colorType: String // "brass", "steel", "bronze", "copper"
)

data class ConverterUIState(
    val celsiusValue: Float = 0f,
    val fahrenheitValue: Float = 32f,
    val celsiusDialAngle: Float = 0f,
    val fahrenheitDialAngle: Float = 64f,
    val gears: List<GearState> = emptyList(),
    val backlashGap: Float = 1.2f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f
)

sealed class ConverterEvent {
    data class Tick(val velocity: Float) : ConverterEvent()
}

class TemperatureViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConverterEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Physics parameters
    private val backlash = 1.5f // degrees of play
    private val damping = 0.82f  // inertia friction damping
    private val springK = 0.22f  // drag attraction force

    // Core state in degrees
    private var celsiusAngle = 0f
    private var celsiusVelocity = 0f
    private var targetCelsiusAngle = 0f
    private var isDraggingCelsius = false

    private var fahrenheitAngle = 64f
    private var fahrenheitVelocity = 0f
    private var targetFahrenheitAngle = 64f
    private var isDraggingFahrenheit = false

    // Tick counters
    private var lastTickAngleC = 0f
    private val toothAngleC = 360f / 80f // 80 teeth on C dial

    init {
        // Build initial gears list
        updateGearsState()

        // Start real-time physics loop (~60 FPS)
        viewModelScope.launch {
            var lastTime = System.nanoTime()
            while (isActive) {
                val currentTime = System.nanoTime()
                val dt = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                
                val clampedDt = dt.coerceAtMost(0.03f)
                updatePhysics(clampedDt)
                delay(16)
            }
        }
    }

    // Setters for dragging state
    fun startDragCelsius() {
        isDraggingCelsius = true
        celsiusVelocity = 0f
        targetCelsiusAngle = celsiusAngle
    }

    fun dragCelsiusTo(angle: Float) {
        // Un-wrap angle to allow continuous smooth rotation
        val diff = angle - (targetCelsiusAngle % 360f)
        val normalizedDiff = when {
            diff > 180f -> diff - 360f
            diff < -180f -> diff + 360f
            else -> diff
        }
        targetCelsiusAngle += normalizedDiff
    }

    fun stopDragCelsius() {
        isDraggingCelsius = false
    }

    fun startDragFahrenheit() {
        isDraggingFahrenheit = true
        fahrenheitVelocity = 0f
        targetFahrenheitAngle = fahrenheitAngle
    }

    fun dragFahrenheitTo(angle: Float) {
        val diff = angle - (targetFahrenheitAngle % 360f)
        val normalizedDiff = when {
            diff > 180f -> diff - 360f
            diff < -180f -> diff + 360f
            else -> diff
        }
        targetFahrenheitAngle += normalizedDiff
    }

    fun stopDragFahrenheit() {
        isDraggingFahrenheit = false
    }

    fun updateTilt(x: Float, y: Float) {
        _uiState.update { it.copy(tiltX = x, tiltY = y) }
    }

    private fun updatePhysics(dt: Float) {
        // 1. Update Dial C angle with inertia
        if (isDraggingCelsius) {
            val torque = (targetCelsiusAngle - celsiusAngle) * springK
            celsiusVelocity = (celsiusVelocity + torque) * damping
            celsiusAngle += celsiusVelocity
        } else if (!isDraggingFahrenheit) {
            // Apply standard friction
            celsiusVelocity *= damping
            if (abs(celsiusVelocity) < 0.001f) celsiusVelocity = 0f
            celsiusAngle += celsiusVelocity
        }

        // 2. Ideal Fahrenheit position based on Dial C
        val fahrenheitIdeal = celsiusAngle * 1.8f + 64f

        // 3. Backlash and meshing constraint:
        // Fahrenheit gear must remain within the backlash range of fahrenheitIdeal.
        if (isDraggingFahrenheit) {
            val torque = (targetFahrenheitAngle - fahrenheitAngle) * springK
            fahrenheitVelocity = (fahrenheitVelocity + torque) * damping
            fahrenheitAngle += fahrenheitVelocity

            // Back-propagate to Celsius with backlash gap
            val celsiusIdeal = (fahrenheitAngle - 64f) / 1.8f
            val diffC = celsiusAngle - celsiusIdeal
            val backlashC = backlash / 1.8f
            if (diffC > backlashC) {
                celsiusAngle = celsiusIdeal + backlashC
                celsiusVelocity = 0f
            } else if (diffC < -backlashC) {
                celsiusAngle = celsiusIdeal - backlashC
                celsiusVelocity = 0f
            }
        } else {
            // Fahrenheit is driven by Celsius through the gear train with backlash
            val diff = fahrenheitAngle - fahrenheitIdeal
            if (diff > backlash) {
                fahrenheitAngle = fahrenheitIdeal + backlash
                fahrenheitVelocity = celsiusVelocity * 1.8f
            } else if (diff < -backlash) {
                fahrenheitAngle = fahrenheitIdeal - backlash
                fahrenheitVelocity = celsiusVelocity * 1.8f
            } else {
                // Decay independent fahrenheit speed when inside backlash deadzone
                fahrenheitVelocity *= damping
                fahrenheitAngle += fahrenheitVelocity
            }
        }

        // Keep values in range
        // Celsius range: -50C to 150C -> angle range: -100 to 300
        val minC = -100f // -50C
        val maxC = 300f  // 150C
        if (celsiusAngle < minC) {
            celsiusAngle = minC
            celsiusVelocity = 0f
        } else if (celsiusAngle > maxC) {
            celsiusAngle = maxC
            celsiusVelocity = 0f
        }

        val minF = minC * 1.8f + 64f
        val maxF = maxC * 1.8f + 64f
        if (fahrenheitAngle < minF) {
            fahrenheitAngle = minF
            fahrenheitVelocity = 0f
        } else if (fahrenheitAngle > maxF) {
            fahrenheitAngle = maxF
            fahrenheitVelocity = 0f
        }

        // 4. Trigger haptic/sound ticks based on actual tooth movement
        val currentVelocity = if (isDraggingFahrenheit) fahrenheitVelocity else celsiusVelocity
        val deltaTick = celsiusAngle - lastTickAngleC
        if (abs(deltaTick) >= toothAngleC) {
            val steps = (abs(deltaTick) / toothAngleC).toInt()
            if (steps > 0) {
                lastTickAngleC += steps * toothAngleC * if (deltaTick > 0) 1f else -1f
                viewModelScope.launch {
                    _events.emit(ConverterEvent.Tick(abs(currentVelocity)))
                }
            }
        }

        // 5. Update state
        val cVal = celsiusAngle / 2f
        val fVal = (fahrenheitAngle) / 2f

        _uiState.update { state ->
            state.copy(
                celsiusValue = cVal,
                fahrenheitValue = fVal,
                celsiusDialAngle = celsiusAngle,
                fahrenheitDialAngle = fahrenheitAngle,
                gears = calculateGearsList()
            )
        }
    }

    private fun updateGearsState() {
        _uiState.update { it.copy(gears = calculateGearsList()) }
    }

    private fun calculateGearsList(): List<GearState> {
        // Core gear train implementing F = C * 1.8 + 64 (in angles)
        // 12 functional gears mesh perfectly based on gear finder Solution 4
        
        val angleC = celsiusAngle

        // Shaft 1: meshes with C (80 teeth -> 40 teeth)
        // Center: (-39.7, -132.2)
        val angleG1a = -angleC * (80f / 40f) + 180f
        val angleG1b = angleG1a

        // Shaft 2: meshes with G1b (30 teeth -> 50 teeth)
        // Center: (4.3, 21.7)
        val angleG2a = -angleG1b * (30f / 50f)
        val angleG2b = angleG2a

        // Shaft 3: meshes with G2b (40 teeth -> 60 teeth)
        // Center: (76.3, -164.9)
        val angleG3a = -angleG2b * (40f / 60f) + 180f
        val angleG3b = angleG3a

        // Shaft 4: meshes with G3b (50 teeth -> 50 teeth)
        // Center: (132.5, 27.0)
        val angleG4a = -angleG3b * (50f / 50f)
        val angleG4b = angleG4a

        // Shaft 5: meshes with G4b (45 teeth -> 30 teeth)
        // Center: (136.0, -122.9)
        val angleG5a = -angleG4b * (45f / 30f) + 180f
        val angleG5b = angleG5a

        // Shaft F (Dial F): meshes with G5b (48 teeth -> 32 teeth)
        // Center: (240, 0)
        val angleGF = fahrenheitAngle

        return listOf(
            GearState("dial_c", -240f, 0f, 80, 160f, angleC, "steel_blue"),
            
            GearState("gear_1a", -39.7f, -132.2f, 40, 80f, angleG1a, "brass"),
            GearState("gear_1b", -39.7f, -132.2f, 30, 60f, angleG1b, "copper"),
            
            GearState("gear_2a", 4.3f, 21.7f, 50, 100f, angleG2a, "steel"),
            GearState("gear_2b", 4.3f, 21.7f, 40, 80f, angleG2b, "bronze"),
            
            GearState("gear_3a", 76.3f, -164.9f, 60, 120f, angleG3a, "brass"),
            GearState("gear_3b", 76.3f, -164.9f, 50, 100f, angleG3b, "copper"),
            
            GearState("gear_4a", 132.5f, 27.0f, 50, 100f, angleG4a, "steel"),
            GearState("gear_4b", 132.5f, 27.0f, 45, 90f, angleG4b, "bronze"),
            
            GearState("gear_5a", 136.0f, -122.9f, 30, 60f, angleG5a, "brass"),
            GearState("gear_5b", 136.0f, -122.9f, 48, 96f, angleG5b, "copper"),
            
            GearState("dial_f", 240f, 0f, 32, 64f, angleGF, "steel_orange")
        )
    }
}
