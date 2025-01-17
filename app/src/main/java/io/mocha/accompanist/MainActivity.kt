package io.mocha.accompanist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import io.mocha.accompanist.data.model.lyrics.SyncedLyrics
import io.mocha.accompanist.data.model.playback.LyricsState
import io.mocha.accompanist.data.parser.LyricifySyllableParser
import io.mocha.accompanist.ui.composable.background.FlowingLightBackground
import io.mocha.accompanist.ui.composable.lyrics.KaraokeLyricsView
import io.mocha.accompanist.ui.theme.AccompanistTheme
import kotlinx.coroutines.android.awaitFrame

class MainActivity : ComponentActivity() {
    private fun Context.resourceUri(resourceId: Int): Uri = with(resources) {
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(getResourcePackageName(resourceId))
            .appendPath(getResourceTypeName(resourceId))
            .appendPath(getResourceEntryName(resourceId))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fucking edge to edge
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            statusBarStyle = SystemBarStyle.dark(Color.White.toArgb())
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false

        setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            val item = MediaItem.fromUri(this.resourceUri(R.raw.test))
            val mediaSession = remember { MediaSession.Builder(context, player).build() }
            var currentPosition by remember { mutableLongStateOf(0L) }
            val listState = rememberLazyListState()
            val currentPlayer by rememberUpdatedState(player)

            DisposableEffect(Unit) {
                player.setMediaItem(item)
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                player.prepare()
                player.playWhenReady = true
                onDispose {
                    mediaSession.release()
                    player.release()
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    awaitFrame()
                    currentPosition = currentPlayer.currentPosition
                }
            }

            AccompanistTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ) { innerPadding ->
                    Box {
                        FlowingLightBackground(
                            ImageBitmap.imageResource(
                                resources,
                                R.drawable.test
                            )
                        )

                        Column(Modifier.padding()) {
                            var data: List<String>
                            var lyrics: SyncedLyrics? by remember {
                                mutableStateOf(null)
                            }

                            LaunchedEffect(Unit) {
                                val asset = application.assets.open("test.qrc")
                                data = asset.bufferedReader().use { it.readLines() }
                                asset.close()
                                lyrics = LyricifySyllableParser.parse(data)
                            }
                            lyrics?.let {
                                val lyricsState = remember(currentPosition) {
                                    LyricsState(
                                        { currentPlayer.currentPosition.toInt() },
                                        currentPlayer.duration.toInt(),
                                        it,
                                        listState
                                    )
                                }
                                KaraokeLyricsView(
                                    lyricsState = lyricsState,
                                    lyrics = it,
                                    onLineClicked = { line ->
                                        currentPlayer.seekTo(line.start.toLong())
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                )
                            }
                        }
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(innerPadding.calculateTopPadding().value.dp * 4)
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(0.6f),
                                        1f to Color.Transparent
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
