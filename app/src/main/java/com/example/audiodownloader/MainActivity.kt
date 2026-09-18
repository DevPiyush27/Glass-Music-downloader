package com.example.audiodownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiodownloader.ui.components.GlassBackground
import com.example.audiodownloader.ui.components.NeumorphColors
import com.example.audiodownloader.ui.components.neumorphicExtruded
import com.example.audiodownloader.ui.components.neumorphicRecessed
import com.example.audiodownloader.ui.tabs.DirectDownloadTab
import com.example.audiodownloader.ui.tabs.LocalPlayerTab
import com.example.audiodownloader.ui.tabs.SpotifyExtractorTab
import com.example.audiodownloader.ui.viewmodel.LocalPlayerViewModel
import com.example.audiodownloader.ui.viewmodel.MainViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val localPlayerViewModel: LocalPlayerViewModel by viewModels()

    private var hasAudioPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasAudioPermission = hasAudioPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            hasAudioPermission = hasAudioPermissionGranted()
            checkAndRequestPermissions()

            setContent {
                AudioDownloaderTheme {
                    MainAppScreen(
                        viewModel = viewModel,
                        localPlayerViewModel = localPlayerViewModel,
                        hasAudioPermission = hasAudioPermission
                    )
                }
            }
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val errorText = "Startup Crash Encountered:\n\n$sw"

            try {
                val crashFile = File(getExternalFilesDir(null), "app_crash.txt")
                crashFile.writeText(errorText)
            } catch (_: Exception) {}

            // Display native emergency fallback text screen
            val scrollView = ScrollView(this).apply {
                setBackgroundColor(0xFF222222.toInt())
                setPadding(32, 64, 32, 32)
            }
            val textView = TextView(this).apply {
                text = errorText
                setTextColor(0xFFD9534F.toInt())
                textSize = 13f
            }
            scrollView.addView(textView)
            setContentView(scrollView)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case the user granted the audio permission from system Settings.
        hasAudioPermission = hasAudioPermissionGranted()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun hasAudioPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    localPlayerViewModel: LocalPlayerViewModel,
    hasAudioPermission: Boolean
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopAppBar()
            },
            bottomBar = {
                GlassBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = viewModel::selectTab
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tabTransition"
                ) { tab ->
                    when (tab) {
                        0 -> DirectDownloadTab(viewModel = viewModel)
                        1 -> SpotifyExtractorTab(viewModel = viewModel)
                        2 -> LocalPlayerTab(
                            viewModel = localPlayerViewModel,
                            hasPermission = hasAudioPermission
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassTopAppBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Music Disc Icon Logo Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .neumorphicExtruded(
                        cornerRadius = 23.dp,
                        backgroundColor = NeumorphColors.Surface,
                        elevation = 5.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = "Audio Aurora Music Disc Logo",
                    tint = NeumorphColors.AccentCopper,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Audio Aurora",
                    color = NeumorphColors.TextCream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Neomorphic yt-dlp & Spotify Engine",
                    color = NeumorphColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun GlassBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicExtruded(
                    cornerRadius = 24.dp,
                    backgroundColor = NeumorphColors.Surface,
                    elevation = 6.dp
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassNavigationItem(
                label = "Direct",
                icon = Icons.Default.Download,
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )
            GlassNavigationItem(
                label = "Spotify",
                icon = Icons.Default.LibraryMusic,
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
            GlassNavigationItem(
                label = "Player",
                icon = Icons.Default.GraphicEq,
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun GlassNavigationItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(
                if (isSelected) {
                    Modifier.neumorphicRecessed(
                        cornerRadius = 16.dp,
                        backgroundColor = NeumorphColors.SurfacePressed
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(16.dp))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) NeumorphColors.AccentCopper else NeumorphColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (isSelected) NeumorphColors.TextCream else NeumorphColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun AudioDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeumorphColors.AccentCopper,
            secondary = NeumorphColors.AccentWarmGold,
            background = NeumorphColors.Background,
            surface = NeumorphColors.Surface
        ),
        content = content
    )
}
