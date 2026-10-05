package com.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GlassQuality] 分级决策的单元测试。
 *
 * 该测试完全基于 [GlassQuality.resolve] 这个纯函数，不依赖 Android 运行时，
 * 因此可以在 JVM 上直接执行（无需设备或模拟器）。
 */
class GlassQualityTest {

    /* ------------------------- 版本 → 级别映射 ------------------------- */

    @Test
    fun `API 33 及以上命中 Full`() {
        listOf(33, 34, 35, 36).forEach { sdk ->
            assertEquals(
                "API $sdk 应命中 Full",
                GlassQuality.Full,
                GlassQuality.resolve(sdkInt = sdk),
            )
        }
    }

    @Test
    fun `API 31 到 32 命中 Medium`() {
        listOf(31, 32).forEach { sdk ->
            assertEquals(
                "API $sdk 应命中 Medium",
                GlassQuality.Medium,
                GlassQuality.resolve(sdkInt = sdk),
            )
        }
    }

    @Test
    fun `API 24 到 30 命中 Minimal`() {
        listOf(24, 25, 26, 27, 28, 29, 30).forEach { sdk ->
            assertEquals(
                "API $sdk 应命中 Minimal",
                GlassQuality.Minimal,
                GlassQuality.resolve(sdkInt = sdk),
            )
        }
    }

    @Test
    fun `API 21 到 23 命中 Fallback`() {
        listOf(21, 22, 23).forEach { sdk ->
            assertEquals(
                "API $sdk 应命中 Fallback",
                GlassQuality.Fallback,
                GlassQuality.resolve(sdkInt = sdk),
            )
        }
    }

    /* --------------------------- 低内存降级 --------------------------- */

    @Test
    fun `低内存设备在 API 33 以上降级为 Medium`() {
        assertEquals(
            GlassQuality.Medium,
            GlassQuality.resolve(sdkInt = 33, isLowRamDevice = true),
        )
        assertEquals(
            GlassQuality.Medium,
            GlassQuality.resolve(sdkInt = 35, isLowRamDevice = true),
        )
    }

    @Test
    fun `低内存设备在低版本上保持原级别`() {
        // 低版本本身已经不依赖离屏缓冲，无需再降
        assertEquals(
            GlassQuality.Minimal,
            GlassQuality.resolve(sdkInt = 30, isLowRamDevice = true),
        )
        assertEquals(
            GlassQuality.Fallback,
            GlassQuality.resolve(sdkInt = 21, isLowRamDevice = true),
        )
    }

    @Test
    fun `低内存设备绝不会命中 Full`() {
        listOf(21, 24, 31, 33, 35).forEach { sdk ->
            assertFalse(
                "API $sdk 低内存设备不应命中 Full",
                GlassQuality.resolve(sdkInt = sdk, isLowRamDevice = true) == GlassQuality.Full,
            )
        }
    }

    /* ------------------------- 硬件加速关闭 ------------------------- */

    @Test
    fun `未开启硬件加速时 AGSL 路径不可用`() {
        val q = GlassQuality.resolve(sdkInt = 35, hasHardwareAcceleration = false)
        assertEquals(GlassQuality.Medium, q)
        assertFalse(q.supportsRefraction)
    }

    @Test
    fun `未开启硬件加速时低版本不受影响`() {
        assertEquals(
            GlassQuality.Minimal,
            GlassQuality.resolve(sdkInt = 28, hasHardwareAcceleration = false),
        )
    }

    /* --------------------------- 能力标志 --------------------------- */

    @Test
    fun `只有 Full 支持折射`() {
        assertTrue(GlassQuality.Full.supportsRefraction)
        GlassQuality.entries.filter { it != GlassQuality.Full }.forEach { q ->
            assertFalse("${q.name} 不应支持折射", q.supportsRefraction)
        }
    }

    @Test
    fun `只有 Fallback 不需要离屏缓冲`() {
        assertFalse(GlassQuality.Fallback.requiresOffscreenBuffer)
        GlassQuality.entries.filter { it != GlassQuality.Fallback }.forEach { q ->
            assertTrue("${q.name} 需要离屏缓冲", q.requiresOffscreenBuffer)
        }
    }

    @Test
    fun `只有 Full 开启多路色散`() {
        assertEquals(7, GlassQuality.Full.dispersionSamples)
        GlassQuality.entries.filter { it != GlassQuality.Full }.forEach { q ->
            assertEquals("${q.name} 应为单次采样", 1, q.dispersionSamples)
            assertEquals("${q.name} 色散应为 0", 0f, q.dispersion, 1e-6f)
        }
    }

    @Test
    fun `各级别的模糊半径非负且 Fallback 为零`() {
        GlassQuality.entries.forEach { q ->
            assertTrue("${q.name} 模糊半径应为非负", q.blurRadius >= 0f)
        }
        assertEquals(0f, GlassQuality.Fallback.blurRadius, 1e-6f)
        assertTrue(GlassQuality.Full.blurRadius > 0f)
    }

    @Test
    fun `各级别的降采样分母至少为 1`() {
        GlassQuality.entries.forEach { q ->
            assertTrue("${q.name} 降采样分母应 >= 1", q.downsampleFactor >= 1)
        }
    }

    @Test
    fun `透明度在 0 到 1 之间`() {
        GlassQuality.entries.forEach { q ->
            assertTrue("${q.name} alpha 应在 0..1", q.alpha > 0f && q.alpha <= 1f)
        }
    }

    @Test
    fun `所有级别都支持边缘高光`() {
        GlassQuality.entries.forEach { q ->
            assertTrue("${q.name} 应支持边缘高光", q.supportsEdgeHighlight)
        }
    }
}
