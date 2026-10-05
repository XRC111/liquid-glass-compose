package com.liquidglass.internal

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 基于 [android.graphics.RenderEffect.createBlurEffect] 的玻璃渲染器（API 31–32）。
 *
 * 该级别**不支持折射**。[android.graphics.RenderEffect] 只能做形变、模糊、
 * 颜色滤镜三类操作，没有「采样时按偏移量重新取样」的能力，因此无法实现
 * 像素位移与色散。取而代之的方案是：
 *
 * - 硬件两趟高斯模糊（[android.graphics.RenderEffect.createBlurEffect]）；
 * - Canvas 层叠加色调与边缘高光，模拟玻璃的材质感。
 */
@RequiresApi(Build.VERSION_CODES.S)
public class RenderEffectRenderer internal constructor() {

    /**
     * 构造指定半径的模糊 RenderEffect。
     *
     * @param radiusPx 模糊半径（px）。`TileMode.MIRROR` 让边缘像素镜像延伸，
     *   避免模糊结果在边界处出现透明衰减。
     */
    public fun blurEffect(radiusPx: Float): android.graphics.RenderEffect =
        android.graphics.RenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            android.graphics.Shader.TileMode.MIRROR,
        )
}
