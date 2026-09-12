package com.gothwad.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV

@Composable
fun ModeSelectionDialog(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF16181D),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Choose Your Experience",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Select how you would like to use the launcher on this device. You can change this anytime in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 1. Android TV Card
                        ModeOptionCard(
                            title = "Android TV",
                            subtitle = "Optimized for smart TVs, leanback remotes, hero carousels and smooth fluid dock.",
                            icon = AppIcons.Tv,
                            accentColor = Color(0xFF3B82F6),
                            selected = currentMode == MODE_TV,
                            enabled = true,
                            badge = "Remote Friendly",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectMode(MODE_TV) },
                        )

                        // 2. Desktop PC Card
                        ModeOptionCard(
                            title = "Desktop PC",
                            subtitle = "Windows & Linux style interface with bottom taskbar, start menu, system tray, and desktop shortcuts.",
                            icon = AppIcons.Desktop,
                            accentColor = Color(0xFF10B981),
                            selected = currentMode == MODE_PC,
                            enabled = true,
                            badge = "Mouse & Keyboard",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectMode(MODE_PC) },
                        )

                        // 3. Android Phone Card (Coming Soon)
                        ModeOptionCard(
                            title = "Android Phone",
                            subtitle = "Vertical phone launcher with gesture navigation, vertical app drawer, and widgets.",
                            icon = AppIcons.Phone,
                            accentColor = Color(0xFFF59E0B),
                            selected = false,
                            enabled = false,
                            badge = "Coming Soon",
                            modifier = Modifier.weight(1f),
                            onClick = { /* Disabled */ },
                        )
                    }

                    if (onDismiss != null) {
                        Spacer(Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    enabled: Boolean,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    val targetAlpha = if (!enabled) 0.45f else 1f
    val borderColor = when {
        selected -> accentColor
        isHighlighted && enabled -> Color.White.copy(alpha = 0.4f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        modifier = modifier
            .scale(if (isHighlighted && enabled) 1.03f else 1f)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(enabled = enabled, interactionSource = interactionSource),
        shape = RoundedCornerShape(18.dp),
        color = when {
            selected -> accentColor.copy(alpha = 0.15f)
            isHighlighted && enabled -> Color.White.copy(alpha = 0.07f)
            else -> Color(0xFF1E2128)
        },
        border = BorderStroke(if (selected || isHighlighted) 2.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (badge == "Coming Soon") Color(0xFFE11D48).copy(alpha = 0.25f)
                        else accentColor.copy(alpha = 0.2f)
                    )
                    .border(
                        1.dp,
                        if (badge == "Coming Soon") Color(0xFFE11D48) else accentColor,
                        CircleShape,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = if (badge == "Coming Soon") Color(0xFFFF4D6D) else accentColor,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Icon circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = if (enabled) 0.35f else 0.12f),
                                accentColor.copy(alpha = if (enabled) 0.15f else 0.05f),
                            )
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = Color.White.copy(alpha = targetAlpha),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = Color.White.copy(alpha = if (enabled) 0.65f else 0.35f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Current Mode",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            } else if (enabled) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Select",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
