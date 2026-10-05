package com.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 降采样策略的单元测试。
 *
 * [resolveDownsample] 决定了模糊 Pass 的分辨率，直接影响帧率与内存占用，
 * 是低端设备不 OOM 的关键，因此单独覆盖。
 */
class DownsampleTest {

    @Test
    fun `Fallback 级别固定为 1 不做降采样`() {
        assertEquals(1, resolveDownsample(GlassQuality.Fallback, 1080, 2400))
        assertEquals(1, resolveDownsample(GlassQuality.Fallback, 400, 300))
    }

    @Test
    fun `常规尺寸使用级别自带分母`() {
        // 长边 <= 1080 时直接用级别自带的 downsampleFactor
        assertEquals(4, resolveDownsample(GlassQuality.Full, 1080, 1080))
        assertEquals(3, resolveDownsample(GlassQuality.Minimal, 720, 1080))
        assertEquals(2, resolveDownsample(GlassQuality.Medium, 640, 480))
    }

    @Test
    fun `长边超过 1080 时额外降一级`() {
        // 1080 < 长边，因此在自带分母基础上 +1
        assertEquals(5, resolveDownsample(GlassQuality.Full, 1440, 3200))
        assertEquals(4, resolveDownsample(GlassQuality.Minimal, 720, 1280))
        assertEquals(3, resolveDownsample(GlassQuality.Medium, 1440, 2560))
    }

    @Test
    fun `降采样分母不超过上限 6`() {
        // 级别自带分母最大为 4（Full），额外降一级后为 5，仍在上限内
        assertEquals(5, resolveDownsample(GlassQuality.Full, 4000, 4000))
        assertTrue(
            "Full @ 8000x8000 分母应 <= 6",
            resolveDownsample(GlassQuality.Full, 8000, 8000) <= 6,
        )
    }

    @Test
    fun `任何情况下分母都至少为 1`() {
        listOf(GlassQuality.entries).flatten().forEach { q ->
            listOf(1 to 1, 100 to 100, 1080 to 1920, 2000 to 3000).forEach { (w, h) ->
                assertTrue(
                    "${q.name} @ ${w}x$h 分母应 >= 1",
                    resolveDownsample(q, w, h) >= 1,
                )
            }
        }
    }
}
