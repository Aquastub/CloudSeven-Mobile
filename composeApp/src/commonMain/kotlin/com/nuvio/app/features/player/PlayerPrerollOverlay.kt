package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.addons.PrerollClip
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_preroll_skip
import org.jetbrains.compose.resources.stringResource

/**
 * Plays a short clip an addon supplied (a "bump") over the player before an episode starts.
 * It ends by itself, when the clip fails to load, or when the viewer taps Skip.
 */
@Composable
internal fun PrerollOverlay(clip: PrerollClip, onFinished: () -> Unit) {
    var started by remember(clip.url) { mutableStateOf(false) }
    var finished by remember(clip.url) { mutableStateOf(false) }
    var canSkip by remember(clip.url) { mutableStateOf(false) }

    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    // Skip appears after a moment, and a clip that never starts must not hold the episode back.
    LaunchedEffect(clip.url) {
        delay(PREROLL_SKIP_DELAY_MS)
        canSkip = true
    }
    LaunchedEffect(clip.url) {
        delay(PREROLL_START_TIMEOUT_MS)
        if (!started) finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow touches so nothing underneath (the paused episode) reacts.
            .pointerInput(clip.url) { detectTapGestures { } },
    ) {
        PlatformPlayerSurface(
            sourceUrl = clip.url,
            sourceHeaders = clip.headers,
            modifier = Modifier.fillMaxSize(),
            playWhenReady = true,
            resizeMode = PlayerResizeMode.Fit,
            onControllerReady = {},
            onSnapshot = { snapshot ->
                if (snapshot.isPlaying || snapshot.positionMs > 0L) started = true
                if (snapshot.isEnded) finish()
            },
            onError = { message -> if (message != null) finish() },
        )
        if (canSkip) {
            Text(
                text = stringResource(Res.string.player_preroll_skip),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x99000000))
                    .clickable { finish() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

private const val PREROLL_SKIP_DELAY_MS = 1_500L
private const val PREROLL_START_TIMEOUT_MS = 12_000L
