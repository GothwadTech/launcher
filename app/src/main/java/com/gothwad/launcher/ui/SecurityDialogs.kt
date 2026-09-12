package com.gothwad.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Visual PIN indicators (4 or 6 dots).
 */
@Composable
fun PinDots(
    pinLength: Int,
    enteredCount: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pinLength) { index ->
            val filled = index < enteredCount
            val dotColor = when {
                isError -> Color(0xFFFF5252)
                filled -> Color(0xFF4C8DFF)
                else -> Color.White.copy(alpha = 0.25f)
            }
            val dotSize = if (filled) 16.dp else 14.dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(
                        width = 1.5.dp,
                        color = if (isError) Color(0xFFFF5252) else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape,
                    )
            )
        }
    }
}

/**
 * TV on-screen keypad (1-9, 0, Backspace, Clear) supporting D-pad navigation
 * and hardware remote digits.
 */
@Composable
fun TvPinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫"),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { colIndex, keyStr ->
                    val buttonModifier = if (rowIndex == 0 && colIndex == 0 && firstFocusRequester != null) {
                        Modifier.focusRequester(firstFocusRequester)
                    } else {
                        Modifier
                    }

                    Surface(
                        onClick = {
                            when (keyStr) {
                                "C" -> onClear()
                                "⌫" -> onBackspace()
                                else -> if (keyStr.isNotEmpty()) onDigit(keyStr[0])
                            }
                        },
                        modifier = buttonModifier
                            .size(width = 64.dp, height = 48.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color(0xFF4C8DFF),
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = keyStr,
                                fontSize = if (keyStr.length > 1) 18.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hardware remote key helper for intercepting number keys 0-9 and backspace.
 */
fun handleRemotePinKey(
    key: Key,
    type: KeyEventType,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (key) {
        Key.Zero, Key.NumPad0 -> { onDigit('0'); true }
        Key.One, Key.NumPad1 -> { onDigit('1'); true }
        Key.Two, Key.NumPad2 -> { onDigit('2'); true }
        Key.Three, Key.NumPad3 -> { onDigit('3'); true }
        Key.Four, Key.NumPad4 -> { onDigit('4'); true }
        Key.Five, Key.NumPad5 -> { onDigit('5'); true }
        Key.Six, Key.NumPad6 -> { onDigit('6'); true }
        Key.Seven, Key.NumPad7 -> { onDigit('7'); true }
        Key.Eight, Key.NumPad8 -> { onDigit('8'); true }
        Key.Nine, Key.NumPad9 -> { onDigit('9'); true }
        Key.Backspace, Key.Delete -> { onBackspace(); true }
        else -> false
    }
}

/**
 * Full-screen Device Lock Screen.
 * Prompts user for the Device Lock PIN whenever the launcher opens or boots.
 */
@Composable
fun DeviceLockDialog(
    correctPin: String,
    pinLength: Int,
    onUnlocked: () -> Unit,
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    fun handleDigit(d: Char) {
        if (enteredPin.length < pinLength) {
            isError = false
            val newPin = enteredPin + d
            enteredPin = newPin
            if (newPin.length == pinLength) {
                if (newPin == correctPin) {
                    onUnlocked()
                } else {
                    isError = true
                    scope.launch {
                        delay(600)
                        enteredPin = ""
                        isError = false
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    fun handleClear() {
        enteredPin = ""
        isError = false
    }

    Dialog(
        onDismissRequest = { /* Cannot dismiss without entering PIN */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1117))
                .onPreviewKeyEvent { e ->
                    handleRemotePinKey(e.key, e.type, ::handleDigit, ::handleBackspace)
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161A23))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 28.dp, horizontal = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4C8DFF).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        tint = Color(0xFF4C8DFF),
                        modifier = Modifier.size(30.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.device_locked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Text(
                    text = stringResource(R.string.device_locked_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                PinDots(
                    pinLength = pinLength,
                    enteredCount = enteredPin.length,
                    isError = isError,
                )

                if (isError) {
                    Text(
                        text = stringResource(R.string.incorrect_pin),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                }

                TvPinPad(
                    onDigit = ::handleDigit,
                    onBackspace = ::handleBackspace,
                    onClear = ::handleClear,
                    firstFocusRequester = firstFocus,
                )
            }
        }
    }
}

/**
 * App Lock PIN Dialog shown when user clicks an app protected by App Lock.
 */
@Composable
fun AppLockDialog(
    app: AppEntry,
    correctPin: String,
    pinLength: Int,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    fun handleDigit(d: Char) {
        if (enteredPin.length < pinLength) {
            isError = false
            val newPin = enteredPin + d
            enteredPin = newPin
            if (newPin.length == pinLength) {
                if (newPin == correctPin) {
                    onUnlocked()
                } else {
                    isError = true
                    scope.launch {
                        delay(600)
                        enteredPin = ""
                        isError = false
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    fun handleClear() {
        enteredPin = ""
        isError = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.Back && e.type == KeyEventType.KeyDown) {
                        onDismiss()
                        true
                    } else {
                        handleRemotePinKey(e.key, e.type, ::handleDigit, ::handleBackspace)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161A23))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp, horizontal = 24.dp),
            ) {
                // App Icon & Lock Badge
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Lock,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )

                Text(
                    text = stringResource(R.string.enter_pin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )

                PinDots(
                    pinLength = pinLength,
                    enteredCount = enteredPin.length,
                    isError = isError,
                )

                if (isError) {
                    Text(
                        text = stringResource(R.string.incorrect_pin),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }

                TvPinPad(
                    onDigit = ::handleDigit,
                    onBackspace = ::handleBackspace,
                    onClear = ::handleClear,
                    firstFocusRequester = firstFocus,
                )

                // Cancel button
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Universal Dialog for entering a PIN to unlock/verify access to settings or hidden vault.
 */
@Composable
fun VerifyPinDialog(
    title: String,
    subtitle: String,
    correctPin: String,
    pinLength: Int,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    fun handleDigit(d: Char) {
        if (enteredPin.length < pinLength) {
            isError = false
            val newPin = enteredPin + d
            enteredPin = newPin
            if (newPin.length == pinLength) {
                if (newPin == correctPin) {
                    onSuccess()
                } else {
                    isError = true
                    scope.launch {
                        delay(600)
                        enteredPin = ""
                        isError = false
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    fun handleClear() {
        enteredPin = ""
        isError = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.Back && e.type == KeyEventType.KeyDown) {
                        onDismiss()
                        true
                    } else {
                        handleRemotePinKey(e.key, e.type, ::handleDigit, ::handleBackspace)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161A23))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp, horizontal = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4C8DFF).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        tint = Color(0xFF4C8DFF),
                        modifier = Modifier.size(26.dp),
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )

                PinDots(
                    pinLength = pinLength,
                    enteredCount = enteredPin.length,
                    isError = isError,
                )

                if (isError) {
                    Text(
                        text = stringResource(R.string.incorrect_pin),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }

                TvPinPad(
                    onDigit = ::handleDigit,
                    onBackspace = ::handleBackspace,
                    onClear = ::handleClear,
                    firstFocusRequester = firstFocus,
                )

                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * PIN Setup / Change Dialog.
 * Allows user to choose between 4-Digit and 6-Digit PIN, enter it, and confirm it.
 */
@Composable
fun PinSetupDialog(
    title: String,
    initialLength: Int = 4,
    onPinSaved: (pin: String, length: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedLength by remember { mutableIntStateOf(if (initialLength == 6) 6 else 4) }
    var step by remember { mutableIntStateOf(1) } // 1: Enter New, 2: Confirm
    var firstEnteredPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(step, selectedLength) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    fun handleDigit(d: Char) {
        if (currentPin.length < selectedLength) {
            isError = false
            errorMessage = ""
            val newPin = currentPin + d
            currentPin = newPin
            if (newPin.length == selectedLength) {
                if (step == 1) {
                    firstEnteredPin = newPin
                    scope.launch {
                        delay(250)
                        currentPin = ""
                        step = 2
                    }
                } else {
                    // Step 2: Confirmation
                    if (newPin == firstEnteredPin) {
                        onPinSaved(newPin, selectedLength)
                    } else {
                        isError = true
                        errorMessage = "PINs do not match. Try again"
                        scope.launch {
                            delay(700)
                            currentPin = ""
                            firstEnteredPin = ""
                            step = 1
                            isError = false
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            isError = false
            errorMessage = ""
        }
    }

    fun handleClear() {
        currentPin = ""
        isError = false
        errorMessage = ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .onPreviewKeyEvent { e ->
                    if (e.key == Key.Back && e.type == KeyEventType.KeyDown) {
                        if (step == 2) {
                            step = 1
                            currentPin = ""
                            true
                        } else {
                            onDismiss()
                            true
                        }
                    } else {
                        handleRemotePinKey(e.key, e.type, ::handleDigit, ::handleBackspace)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161A23))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp, horizontal = 24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                // Length Selector (Step 1 only)
                if (step == 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = {
                                selectedLength = 4
                                currentPin = ""
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selectedLength == 4) Color(0xFF4C8DFF) else Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.height(34.dp).padding(horizontal = 12.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.pin_4_digits),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedLength == 4) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                selectedLength = 6
                                currentPin = ""
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selectedLength == 6) Color(0xFF4C8DFF) else Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.height(34.dp).padding(horizontal = 12.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.pin_6_digits),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedLength == 6) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (step == 1) stringResource(R.string.enter_new_pin) else stringResource(R.string.confirm_pin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )

                PinDots(
                    pinLength = selectedLength,
                    enteredCount = currentPin.length,
                    isError = isError,
                )

                if (isError) {
                    Text(
                        text = errorMessage.ifEmpty { stringResource(R.string.pin_mismatch) },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }

                TvPinPad(
                    onDigit = ::handleDigit,
                    onBackspace = ::handleBackspace,
                    onClear = ::handleClear,
                    firstFocusRequester = firstFocus,
                )

                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
