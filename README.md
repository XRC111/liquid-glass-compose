<div align="center">

# Liquid Glass Compose

**Android 液态玻璃（Liquid Glass）效果库 —— Jetpack Compose 实现**

真实折射、七路色散、Fresnel 边缘高光，按设备能力**四档自动降级**，最低支持 **API 21**。

[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/API-21%2B-3DDC84?style=flat-square)](#降级链路)
[![JitPack](https://img.shields.io/badge/JitPack-ready-brightgreen?style=flat-square)](#引入依赖)

</div>

---

> 配套演示程序：[liquid_glass_compose](https://github.com/XRC111/liquid_glass_compose)（含性能基准与 Release APK）。
>
> Flutter 版本：[liquid_glass_library](https://github.com/XRC111/liquid_glass_library)。

## 特性

| 特性 | 实现 |
| --- | --- |
| **真实折射** | SDF 圆角矩形 → 中心差分梯度（法线）→ 高度场厚度 → 折射位移 |
| **七路色散** | 沿折射方向垂直轴把 RGB 分离成 7 路采样，形成彩虹边 |
| **Fresnel 边缘高光** | 掠射角反射增强，形成玻璃棱线 |
| **触摸跟随** | 边缘高光随手指位置移动 |
| **四档降级** | AGSL → RenderEffect → CPU 模糊 → 渐变 |
| **共享模糊源** | 背景只捕获/模糊一次，供所有玻璃容器复用 |

## 引入依赖

### JitPack

根 `settings.gradle.kts`：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

模块：

```kotlin
dependencies {
    implementation("com.github.XRC111.liquid-glass-compose:liquid-glass:1.0.0")
}
```

### 本地 Maven

```bash
./gradlew :liquid-glass:publishToMavenLocal
```

```kotlin
dependencies {
    implementation("com.github.liquidglass:liquid-glass-compose:1.0.0")
}
```

> `repositories` 中需包含 `mavenLocal()`。

## 快速上手

四步：**创建状态 → 标记背景源 → 放玻璃 → 完事**。

```kotlin
@Composable
fun Demo() {
    val context = LocalContext.current

    // 1. 创建状态，质量分级在创建时自动决策
    val state = LiquidGlass.rememberState(context)

    Box(Modifier.fillMaxSize()) {
        // 2. 把背景标记为「玻璃采样源」：
        //    它会在 1/4 分辨率被捕获并模糊，供所有玻璃容器共享
        Image(
            painter = painterResource(R.drawable.bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().liquidGlassSource(state)
        )

        // 3. 放一块玻璃
        LiquidGlass(
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .size(220.dp, 120.dp),
            cornerRadius = 28.dp
        ) {
            Text("Liquid Glass", color = Color.White)
        }
    }
}
```

## API

### `LiquidGlass`

玻璃容器 Composable。

| 参数 | 说明 |
| --- | --- |
| `state` | `LiquidGlassState`，由 `rememberState` 创建 |
| `cornerRadius` | 圆角半径（SDF 形状） |
| `refraction` | 折射强度 |
| `dispersion` | 色散强度 |
| `fresnel` | 边缘高光强度 |
| `content` | 绘制在玻璃之上的内容 |

### `Modifier.liquidGlassSource(state)`

把任意 Composable 标记为玻璃的采样源（背景）。同一场景内多次调用只捕获一次。

### `LiquidGlassState`

| 成员 | 说明 |
| --- | --- |
| `quality` | 当前生效的 `GlassQuality` |
| `updateTouch(x, y)` | 触摸位置（驱动动态高光） |
| `invalidate()` | 背景变化时主动重捕获 |

### `GlassQuality`

| 档位 | 条件 | 后端 |
| --- | --- | --- |
| `Full` | API 33+ 且非低内存 | AGSL `RuntimeShader` + `RenderEffect` |
| `Medium` | API 31–32 或低内存 | `RenderEffect.createBlurEffect` |
| `Minimal` | API 24–30 | CPU 可分离 Box 模糊 |
| `Fallback` | API 21–23 / 极低端 | 纯渐变 + 透明度 |

决策逻辑集中在 `GlassQuality.resolve()`，是**纯函数**，可被单元测试完整覆盖。

## 降级链路

| 级别 | 折射 | 色散 | 模糊 | 离屏缓冲 |
| --- | --- | --- | --- | --- |
| Full | 高度场 | 7 路 | 1/4 分辨率 AGSL | 有 |
| Medium | 无 | 无 | 硬件两趟 | 有 |
| Minimal | 无 | 无 | CPU Box（1/3 降采样） | 有 |
| Fallback | 无 | 无 | 无 | 零 |

低内存设备（`isLowRamDevice`）强制降一级，避免离屏缓冲 OOM。

## 性能设计

- 模糊 Pass 在 1/4 分辨率执行，填充率下降约 94%
- 背景捕获按帧去重，静态背景不重复捕获
- 玻璃容器共享同一张模糊纹理，多块玻璃不叠加成本
- 触摸高光只更新 uniform，不重建着色器

## 已知限制

- AGSL 路径仅 API 33+；以下版本没有折射与色散，只有模糊（这是平台能力边界）
- 玻璃面板不宜铺满整屏
- 低端机（API 24–30）CPU 模糊会占用主线程，建议减少玻璃数量

## License

MIT（见 [LICENSE](./LICENSE)）
