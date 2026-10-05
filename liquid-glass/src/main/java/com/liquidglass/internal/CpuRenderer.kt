package com.liquidglass.internal

import android.graphics.Bitmap
import android.os.Build
import android.util.Log

/**
 * 纯 CPU 软件渲染器，用于 API 24–30 的降级路径。
 *
 * API 31 以下 `Modifier.blur()` 会被 Compose 静默忽略（no-op），
 * 因此必须自行完成模糊。本实现采用**可分离 Box 模糊三趟叠加**来近似高斯：
 *
 * ```
 * 原始 ──水平──▶ ──垂直──▶ ──水平──▶ ──垂直──▶ ──水平──▶ ──垂直──▶ 结果
 * ```
 *
 * 之所以用 Box 模糊而非真正的高斯，是因为滑动窗口均值可以用「前缀和 − 滑动窗口」
 * 在 O(1) 时间完成每个像素的计算，而高斯需要可变窗口边界。
 * 三趟 Box 模糊的频响已足够接近高斯，在玻璃场景中肉眼无差别。
 *
 * 关键性能前提：**必须在低分辨率位图上执行**。1/3 降采样后，
 * 一张 1080p 背景只剩约 120×213 = 2.5 万像素，单次耗时 < 3ms。
 */
public class CpuRenderer internal constructor() {

    /**
     * 对位图执行三趟可分离 Box 模糊。
     *
     * **重要**：[androidx.compose.ui.graphics.layer.GraphicsLayer.toImageBitmap]
     * 在 Android 13+ 返回 `Config#HARDWARE` 位图，其像素驻留 GPU 显存，
     * [Bitmap.getPixels] 会抛
     * `IllegalStateException: pixel access is not supported on Config#HARDWARE bitmaps`。
     * 因此这里先把源位图归一化为可读的软件位图再运算。
     *
     * @param source 输入位图（应为降采样后的低分辨率位图）
     * @param radius 模糊半径（px），取值 1~12，超出范围会被 clamp
     * @return 模糊后的新 ARGB_8888 位图；半径为 0、尺寸过小或源位图不可读时原样返回
     */
    public fun blur(source: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return source

        // HARDWARE / 非 8888 位图先转成可读软件位图；不可读则放弃模糊，
        // 保留原背景，避免异常冒泡到调用方导致宿主应用闪退
        val readable = source.toReadableArgb8888() ?: return source

        val w = readable.width
        val h = readable.height
        if (w <= 2 || h <= 2) return readable

        val pixels = IntArray(w * h)
        readable.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(pixels.size)
        val r = radius.toInt().coerceIn(1, 12)

        repeat(PASSES) {
            boxBlurHorizontal(pixels, tmp, w, h, r)
            boxBlurVertical(tmp, pixels, w, h, r)
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 把任意位图转为可进行像素读写的 [Bitmap.Config.ARGB_8888] 软件位图。
     *
     * 用于规避 `Config#HARDWARE` 位图不可读的限制：
     * [androidx.compose.ui.graphics.layer.GraphicsLayer.toImageBitmap] 在
     * Android 13+ 返回 HARDWARE 位图，需 `copy(ARGB_8888, false)` 落到系统内存。
     *
     * @return 可读位图；转换失败返回 `null`
     */
    private fun Bitmap.toReadableArgb8888(): Bitmap? {
        // Bitmap.Config.HARDWARE 由 API 26 引入，低版本不存在该配置，
        // 故带版本判断避免在 API 21-25 上触碰该常量
        val isHardwareConfig =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE
        if (config == Bitmap.Config.ARGB_8888 && !isHardwareConfig) return this
        return runCatching {
            copy(Bitmap.Config.ARGB_8888, false)
        }.getOrElse { t ->
            Log.w(TAG, "bitmap is not pixel-readable, skipping CPU blur", t)
            null
        }
    }

    /**
     * 水平方向滑动窗口均值模糊。
     *
     * 用「移出旧像素 + 移入新像素」的方式维护窗口和，每个像素 O(1)。
     * 每个颜色通道独立累加，最后再除以窗口宽度。
     */
    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            // 初始化窗口 [-r, r]
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }

            for (x in 0 until w) {
                dst[row + x] = (sumA / div shl 24) or (sumR / div shl 16) or
                    (sumG / div shl 8) or (sumB / div)

                // 窗口右移一格
                val outC = src[row + (x - r).coerceIn(0, w - 1)]
                val inC = src[row + (x + r + 1).coerceIn(0, w - 1)]
                sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                sumR += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                sumG += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    /** 垂直方向滑动窗口均值模糊，与 [boxBlurHorizontal] 互为转置。 */
    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (x in 0 until w) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }

            for (y in 0 until h) {
                dst[y * w + x] = (sumA / div shl 24) or (sumR / div shl 16) or
                    (sumG / div shl 8) or (sumB / div)

                val outC = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inC = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                sumR += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                sumG += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    internal companion object {
        /** Logcat 标签。 */
        const val TAG = "LiquidGlass"

        /** 叠加趟数，三趟足以近似高斯。 */
        const val PASSES = 3
    }
}
