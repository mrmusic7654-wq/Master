package com.mastercontrol.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * Catalog thumbnail rendered from a local URI with Coil.
 *
 * Only the small generated thumbnail is decoded — never the source video, and
 * never a full-resolution frame — so the library grid stays cheap on low-RAM
 * devices. When the file is missing or undecodable the placeholder states below
 * are shown instead of an empty box.
 */
@Composable
fun VideoThumbnail(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = McDimens.ThumbnailCornerRadius,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Movie,
) {
    var failed by remember(model) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) failed = true
                },
            )
        }
        if (model == null || failed) {
            Icon(
                imageVector = if (failed) Icons.Filled.BrokenImage else placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(McDimens.IconSizeLarge),
            )
        }
    }
}

/** Poster-sized thumbnail used at the top of the video details screen. */
@Composable
fun PosterImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    VideoThumbnail(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        cornerRadius = McDimens.CardCornerRadius,
        contentScale = ContentScale.Fit,
    )
}
