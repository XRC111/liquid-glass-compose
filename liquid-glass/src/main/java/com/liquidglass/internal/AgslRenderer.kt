package com.liquidglass.internal

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.liquidglass.AgslShaders
import com.liquidglass.LiquidGlassState

/**
 * 基于 AGSL RuntimeShader 的玻璃渲染器，仅在 API 33+ 可用。
 *
 * 工作流程：
 * 1. 把捕获到的背景按相对偏移绘制进 [androidx.compose.ui.graphics.layer.GraphicsLayer]，
 *    完成背景裁剪对齐；
 * 2. 为该 Layer 设置 `RenderEffect.createRuntimeShaderEffect(shader, "image")`，
 *    硬件会把 Layer 内容作为名为 `image` 的 `uniform shader` 传入着色器；
 * 3. 每帧更新 uniform（分辨率 / 触摸点 / 色散 / 厚度 / 色调）；
 * 4. `drawLayer` 输出最终结果。
 *
 * 该渲染器是**唯一支持真实折射与色散**的后端。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class AgslRenderer internal constructor() {

    private val shader = android.graphics.RuntimeShader(AgslShaders.GLASS_AGSL)

    /**
     * 预热着色器，触发 SkSL 编译与管线构建，避免首帧卡顿。
     *
     * @return 预热是否成功。部分厂商驱动无法编译 AGSL，此时返回 `false`；
     *   调用方应据此降级到不依赖 AGSL 的渲染路径，而不是继续使用本渲染器。
     */
    public fun warmUp(): Boolean = runCatching {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, 8f, 8f, paint)
        bmp.recycle()
        true
    }.getOrElse { t ->
        Log.w(TAG, "AGSL shader warm-up failed; caller should fall back", t)
        false
    }

    /**
     * 更新 uniform 并返回绑定好的 RenderEffect。
     *
     * @param width 玻璃容器宽（px）
     * @param height 玻璃容器高（px）
     * @param state 玻璃状态，提供触摸点、色散、厚度等参数
     * @return 可直接挂到 `GraphicsLayer.renderEffect` 的 RenderEffect；
     *   若驱动拒绝该 uniform 设置则返回 `null`，调用方应跳过本帧的硬件效果
     */
    public fun updateEffect(
        width: Int,
        height: Int,
        state: LiquidGlassState,
    ): android.graphics.RenderEffect? {
        val n = state.normalizedTouch()
        return runCatching {
            shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
            shader.setFloatUniform("mouse", n.x * width, n.y * height)
            shader.setFloatUniform("touchStrength", state.touchStrength)
            shader.setFloatUniform("refractiveIndex", REFRACTIVE_INDEX)
            shader.setFloatUniform("dispersion", state.quality.dispersion)
            shader.setFloatUniform("edgeRadius", state.cornerRadiusPx)
            shader.setFloatUniform("thickness", state.thicknessPx)
            shader.setColorUniform("tint", state.tint.toArgbInt())
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, UNIFORM_IMAGE)
        }.getOrElse { t ->
            Log.w(TAG, "AGSL uniform update failed; skipping refraction this frame", t)
            null
        }
    }

    internal companion object {
        /** Logcat 标签。 */
        const val TAG = "LiquidGlass"

        /** 玻璃典型折射率。 */
        const val REFRACTIVE_INDEX = 1.5f

        /** Layer 内容传入 AGSL 时使用的 uniform 名。 */
        const val UNIFORM_IMAGE = "image"
    }
}

/**
 * 把 Compose 颜色转为 AGSL `half4` uniform 所需的 int（ARGB）。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)
