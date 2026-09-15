package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ProductThumbnail(
    imagePath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    fallbackIcon: ImageVector = Icons.Default.Inventory2,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    productName: String? = null
) {
    val localFile = remember(imagePath) {
        if (!imagePath.isNullOrBlank()) {
            val f = File(imagePath)
            if (f.exists()) f else null
        } else null
    }

    if (localFile != null) {
        AsyncImage(
            model = localFile,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(2.dp)
        )
    } else if (!productName.isNullOrBlank()) {
        val palette = listOf(
            Color(0xFF0E9F6E), // Brand green
            Color(0xFF2563EB), // Blue
            Color(0xFFD97706), // Amber
            Color(0xFF7C3AED), // Purple
            Color(0xFFDB2777), // Pink
            Color(0xFF0891B2)  // Cyan
        )
        val initialChar = productName.trim().firstOrNull()?.toString() ?: "প"
        val tileColor = palette[kotlin.math.abs(productName.hashCode()) % palette.size]
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(tileColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = initialChar,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = tileColor
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
