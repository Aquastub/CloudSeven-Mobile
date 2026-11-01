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
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_preroll_skip
import org.jetbrains.compose.resources.stringResource

/**
 * Plays an addon-supplied clip over the player before the episode starts.
 * Direct media URLs are played as-is; YouTube watch URLs are resolved through
 * the same in-app extractor used by the details-screen trailer player.
 */
@Composable
internal fun PrerollOverlay(clip: PrerollClip, onFinished: () -> Unit) {
    val isYouTube = remember(clip.url) { isYouTubeWatchUrl(clip.url) }
    var started by remember(clip.url) { mutableStateOf(false) }
    var finished by remember(clip.url) { mutableStateOf(false) }
    var canSkip by remember(clip.url) { mutableStateOf(false) }
    var resolving by remember(clip.url) { mutableStateOf(isYouTube) }
    var playbackSource by remember(clip.url) {
        mutableStateOf(if (isYouTube) null else TrailerPlaybackSource(videoUrl = clip.url))
    }

    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(clip.url) {
        if (isYouTube) {
            val resolved = runCatching {
                TrailerPlaybackResolver.resolveFromYouTubeUrl(clip.url)
            }.getOrNull()
            playbackSource = resolved
            resolving = false
            if (resolved == null) finish()
        }
    }

    // YouTube extraction can take longer than a direct MP4/HLS startup.
    LaunchedEffect(clip.url, resolving, playbackSource) {
        delay(if (resolving) PREROLL_YOUTUBE_RESOLVE_TIMEOUT_MS else PREROLL_START_TIMEOUT_MS)
        if (!started) finish()
    }

    LaunchedEffect(clip.url) {
        delay(PREROLL_SKIP_DELAY_MS)
        canSkip = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(clip.url) { detectTapGestures { } },
    ) {
        val source = playbackSource
        if (source != null && !finished) {
            PlatformPlayerSurface(
                sourceUrl = source.videoUrl,
                sourceAudioUrl = source.audioUrl,
                useYoutubeChunkedPlayback = isYouTube,
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
        }
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

private fun isYouTubeWatchUrl(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.startsWith("https://www.youtube.com/watch?") ||
        normalized.startsWith("https://youtube.com/watch?") ||
        normalized.startsWith("https://youtu.be/") ||
        normalized.startsWith("https://m.youtube.com/watch?")
}

private const val PREROLL_SKIP_DELAY_MS = 1_500L
private const val PREROLL_START_TIMEOUT_MS = 12_000L
private const val PREROLL_YOUTUBE_RESOLVE_TIMEOUT_MS = 30_000L
