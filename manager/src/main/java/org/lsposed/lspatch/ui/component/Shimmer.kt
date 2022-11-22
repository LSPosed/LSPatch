package org.lsposed.lspatch.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val ShimmerColorShades
    @Composable get() = listOf(
        MaterialTheme.colorScheme.secondaryContainer.copy(0.9f),
        MaterialTheme.colorScheme.secondaryContainer.copy(0.2f),
        MaterialTheme.colorScheme.secondaryContainer.copy(0.9f)
    )

class ShimmerScope(val brush: Brush)

@Composable
fun ShimmerAnimation(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ShimmerScope.() -> Unit
) {
    val transition = rememberInfiniteTransition()
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    )

    val brush = Brush.linearGradient(
        colors = if (enabled) ShimmerColorShades else List(3) { ShimmerColorShades[0] },
        start = Offset(10f, 10f),
        end = Offset(translateAnim, translateAnim)
    )

    Surface(modifier.background(brush)) {
        content(ShimmerScope(brush))
    }
}

@Preview
@Composable
private fun ShimmerPreview() {
    ShimmerAnimation {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(250.dp)
                    .background(brush = brush)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(vertical = 8.dp)
                    .background(brush = brush)
            )
        }
    }
}
