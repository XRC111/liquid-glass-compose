package com.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.GlassCard
import com.liquidglass.GlassQuality
import com.liquidglass.GlassTabBar
import com.liquidglass.LiquidGlass
import com.liquidglass.LiquidGlassState
import com.liquidglass.liquidGlass
import com.liquidglass.liquidGlassSource

/**
 * 示例应用入口。
 *
 * 三个演示页面与库示例程序保持一致：
 * 1. [DemoPage.Home] 单卡片（触摸跟随高光 + 厚度调节 + 强制降级开关）
 * 2. [DemoPage.TabBar] 玻璃 TabBar
 * 3. [DemoPage.Cards] 玻璃卡片列表
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LiquidGlassSampleApp() }
    }
}

/** 演示页面枚举。 */
enum class DemoPage(val label: String) {
    /** 首页：单张玻璃卡片。 */
    Home("首页"),

    /** TabBar 演示。 */
    TabBar("TabBar"),

    /** 卡片列表演示。 */
    Cards("卡片"),
}

/**
 * 示例 App 根组合。
 *
 * 三个页面共享同一个 [LiquidGlassState]，以便观察同一份质量分级在不同
 * 尺寸的玻璃容器上的表现差异。
 */
@Composable
fun LiquidGlassSampleApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(DemoPage.Home) }
    var forceQuality by remember { mutableStateOf<GlassQuality?>(null) }

    val state = LiquidGlass.rememberState(context = context, forceQuality = forceQuality)

    GlassDemoBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 标记为玻璃背景来源：以 1/4 分辨率捕获并模糊，供所有玻璃采样
                .liquidGlassSource(state, enabled = true),
        ) {
            Column(Modifier.fillMaxSize()) {
                DemoSwitchBar(
                    current = page,
                    onSelect = { page = it },
                    modifier = Modifier.padding(16.dp),
                )
                Box(Modifier.weight(1f)) {
                    when (page) {
                        DemoPage.Home -> HomePage(state) { forceQuality = it }
                        DemoPage.TabBar -> TabBarPage(state)
                        DemoPage.Cards -> CardsPage(state)
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 页面                                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomePage(state: LiquidGlassState, onForceQuality: (GlassQuality?) -> Unit) {
    var thickness by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        QualityBanner(state)

        Spacer(Modifier.height(16.dp))

        GlassCard(
            state = state,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Liquid Glass",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "按住卡片并拖动，边缘高光与折射会跟随手指移动。",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("厚度", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("薄" to 0.5f, "标准" to 1f, "厚" to 2f).forEach { option ->
                Chip(
                    label = option.first,
                    selected = thickness == option.second,
                    onClick = {
                        thickness = option.second
                        state.thickness = option.second.dp
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("强制质量分级", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("自动", state.quality == GlassQuality.resolve(LocalContext.current)) {
                onForceQuality(null)
            }
            GlassQuality.entries.forEach { q ->
                Chip(q.name, state.quality == q) { onForceQuality(q) }
            }
        }
    }
}

@Composable
private fun TabBarPage(state: LiquidGlassState) {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "未读", "归档", "设置")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            QualityBanner(state)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "玻璃 TabBar",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "整条 TabBar 是一块玻璃。选中项的位置由触摸驱动，" +
                    "边缘高光会随手指滑动实时移动。",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        GlassTabBar(
            state = state,
            tabs = tabs,
            selectedIndex = selected,
            onTabSelected = { selected = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardsPage(state: LiquidGlassState) {
    val items = remember {
        List(9) { index ->
            "项目 ${index + 1}" to "第 ${index + 1} 张玻璃卡片，" +
                "按压缩放并拖动可以观察折射与色散。"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { QualityBanner(state) }
        items(items) { item ->
            GlassCard(
                state = state,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = item.first,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.second,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* 小组件                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun DemoSwitchBar(
    current: DemoPage,
    onSelect: (DemoPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DemoPage.entries.forEach { page ->
            Chip(page.label, page == current) { onSelect(page) }
        }
    }
}

@Composable
private fun QualityBanner(state: LiquidGlassState) {
    val quality = state.quality
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = "当前质量：${quality.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = quality.describe(),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

private fun GlassQuality.describe(): String = when (this) {
    GlassQuality.Full -> "AGSL RuntimeShader：SDF 高度场折射 + 7 路色散 + Fresnel 边缘光"
    GlassQuality.Medium -> "RenderEffect 硬件模糊：无折射，保留 Fresnel 高光与色调"
    GlassQuality.Minimal -> "CPU 可分离高斯：降采样后近似模糊，无折射"
    GlassQuality.Fallback -> "渐变 + 透明度：最省电路径，兼容 API 21-23"
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Color.White.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) Color(0xFF101828) else Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    LiquidGlassSampleApp()
}
