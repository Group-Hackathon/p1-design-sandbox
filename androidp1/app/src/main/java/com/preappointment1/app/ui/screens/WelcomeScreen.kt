package com.preappointment1.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.preappointment1.app.R
import com.preappointment1.app.ui.components.LpmPrimaryButton
import com.preappointment1.app.ui.theme.*

@Composable
fun WelcomeScreen(
    onStartTracking: () -> Unit,
    onGoToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSlide by remember { mutableIntStateOf(0) }

    val slides = listOf(
        Pair(
            stringResource(R.string.welcome_slide_1_title),
            stringResource(R.string.welcome_slide_1_body)
        ),
        Pair(
            stringResource(R.string.welcome_slide_2_title),
            stringResource(R.string.welcome_slide_2_body)
        ),
        Pair(
            stringResource(R.string.welcome_slide_3_title),
            stringResource(R.string.welcome_slide_3_body)
        )
    )

    Scaffold(
        containerColor = CanvasBackground,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = currentSlide,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "slide_transition"
            ) { targetSlide ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = slides[targetSlide].first,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = TextPrimary,
                        lineHeight = 38.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = slides[targetSlide].second,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slides.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (currentSlide == index) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (currentSlide == index) SagePrimary else MintBadge)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Text(
                    text = "By continuing, you accept our Terms of Use & Privacy Policy",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable { uriHandler.openUri("https://p1-privacy-policy.pages.dev/") }
                )

                if (currentSlide < slides.size - 1) {
                    LpmPrimaryButton(
                        text = stringResource(R.string.welcome_next),
                        onClick = { currentSlide++ }
                    )
                    TextButton(
                        onClick = onGoToHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.welcome_skip),
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    LpmPrimaryButton(
                        text = stringResource(R.string.welcome_start),
                        onClick = onStartTracking
                    )
                    TextButton(
                        onClick = onGoToHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.welcome_go_home),
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
