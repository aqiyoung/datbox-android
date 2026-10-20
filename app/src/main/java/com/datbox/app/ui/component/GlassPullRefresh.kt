package com.datbox.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.pullRefreshIndicatorTransform
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.datbox.app.ui.theme.BorderWarm
import com.datbox.app.ui.theme.BrandGreen
import com.datbox.app.ui.theme.WarmIvory

/**
 * 下拉刷新容器。包在可滚动列表外面即可，指示器是自己画的玻璃圆片，
 * 位置和缩放交给 [pullRefreshIndicatorTransform] 跟着手指走。
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GlassPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)

    Box(modifier = modifier.pullRefresh(state)) {
        content()

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(38.dp)
                .pullRefreshIndicatorTransform(state = state, scale = true)
                .clip(CircleShape)
                .background(WarmIvory)
                .border(0.5.dp, BorderWarm, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = BrandGreen,
                )
            } else {
                CircularProgressIndicator(
                    progress = state.progress.coerceIn(0f, 1f),
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = BrandGreen,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}
