package sitavi.seenema

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.PI
import kotlin.math.cos

const val PROJECT_URL = "https://github.com/Sitavi/Seenema-movie-tracker"
private const val ISSUES_URL = "$PROJECT_URL/issues"
private const val IMDB_URL = "https://www.imdb.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.link_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the mark carries its own room for the halo, so it needs less around it
            Spacer(Modifier.height(8.dp))
            AppMark()
            Text(stringResource(R.string.brand), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))

            FilledTonalButton(onClick = { openUrl(PROJECT_URL) }) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_star))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_star_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                AboutRow(
                    icon = { Icon(painterResource(R.drawable.ic_code), null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.about_source),
                    subtitle = PROJECT_URL.removePrefix("https://"),
                    onClick = { openUrl(PROJECT_URL) },
                )
                AboutDivider()
                AboutRow(
                    icon = { Icon(painterResource(R.drawable.ic_bug_report), null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.about_bug),
                    subtitle = stringResource(R.string.about_bug_sub),
                    onClick = { openUrl(ISSUES_URL) },
                )
                AboutDivider()
                AboutRow(
                    icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.about_data),
                    subtitle = stringResource(R.string.about_data_sub),
                    onClick = { openUrl(IMDB_URL) },
                )
                AboutDivider()
                AboutRow(
                    icon = { Icon(painterResource(R.drawable.ic_gavel), null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.about_licenses),
                    subtitle = stringResource(R.string.about_licenses_sub),
                    onClick = { showLicenses = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.licenses_app), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.licenses_built_with), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.licenses_list), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.licenses_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

/**
 * The launcher icon inside a solid tonal rim. The rim itself never changes; a
 * broad, soft light breathes through it, swelling out from the icon's edge to
 * the rim's edge and drawing back in, over and over.
 */
@Composable
private fun AppMark() {
    val rim = MaterialTheme.colorScheme.secondaryContainer
    // light lightens: painting with the primary would darken the rim instead
    val glow = Color.White
    val phase by rememberInfiniteTransition(label = "rim").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart),
        label = "rim",
    )
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val outerR = 70.dp.toPx()
            val innerR = 56.dp.toPx()   // the icon covers everything inside this
            val edge = 2.dp.toPx()

            drawCircle(color = rim, radius = outerR)

            // one breath out and back per cycle, easing at both ends
            val breath = (1f - cos(2.0 * PI * phase).toFloat()) / 2f
            val peak = (innerR - edge) + ((outerR - edge) - (innerR - edge)) * breath
            // the wash is far wider than the band itself, so its edges are
            // always off screen and it never reads as a line
            val spread = 17.dp.toPx()
            val inner = ((peak - spread) / outerR).coerceIn(0f, 1f)
            val mid = (peak / outerR).coerceIn(0f, 1f)
            val outer = ((peak + spread) / outerR).coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    inner to Color.Transparent,
                    mid to glow.copy(alpha = 0.34f),
                    outer to Color.Transparent,
                    1f to Color.Transparent,
                    center = center,
                    radius = outerR,
                ),
                radius = outerR,
            )
        }
        // the launcher icon itself: its own background, with the adaptive
        // icon's outer bleed cropped the way a round launcher mask does
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                // 108dp artwork drawn at 1.5x so the mask keeps the 72dp safe
                // zone, exactly what a round launcher icon shows; required so
                // the circle's own size doesn't clamp it
                modifier = Modifier.requiredSize(168.dp),
            )
        }
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun AboutRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = icon,
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
