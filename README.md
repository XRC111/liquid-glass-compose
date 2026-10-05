# Liquid Glass Compose

Android 上的**液态玻璃（Liquid Glass）**效果库，使用 Jetpack Compose 编写。

真实的折射、色散与边缘高光，按系统能力**四档自动降级**，最低支持 **API 21**。

- 平台：`minSdk 21` / `compileSdk 35` / `targetSdk 35`
- 渲染：AGSL `RuntimeShader`（API 33+）→ `RenderEffect` 硬件模糊（API 31-32）→ CPU 可分离高斯（API 24-30）→ 渐变（API 21-23）
- 模糊 Pass 在 **1/4 分辨率**执行，低内存设备自动降级
- 触摸跟随：玻璃边缘高光随手指移动

示例程序见配套仓库 [liquid_glass_compose](https://github.com/liquidglass/liquid_glass_compose)（含 Release APK）。

---

## 引入依赖

### JitPack

在**根** `settings.gradle.kts` 添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

然后在模块中引入：

```kotlin
dependencies {
    implementation("com.github.<your-github-user>.liquid-glass-compose:liquid-glass:1.0.0")
}
```

### 本地 Maven 仓库

```bash
./gradlew :liquid-glass:publishToMavenLocal
```

```kotlin
dependencies {
    implementation("com.github.liquidglass:liquid-glass-compose:1.0.0")
}
```

> 提示：`mavenLocal()` 需要出现在 `repositories` 中。

---

## 快速上手

四步：**创建状态 → 标记背景源 → 放玻璃 → 完事**。

```kotlin
@Composable
fun Demo() {
    val context = LocalContext.current
    // 1. 创建状态，质量分级在创建时自动决策
    val state = LiquidGlass.rememberState(context)

    Box(Modifier.fillMaxSize()) {
        // 2. 把背景内容标记为「玻璃采样源」
        //    它会在 1/4 分辨率被捕获并模糊，供所有玻璃容器共享
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(state),
        )

        // 3. 放一块玻璃
        GlassCard(
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Text("Hello, Liquid Glass", modifier = Modifier.padding(20.dp))
        }
    }
}
```

### 自定义玻璃容器

`GlassCard` / `GlassTabBar` 只是薄封装，核心是 `Modifier.liquidGlass`：

```kotlin
Box(
    modifier = Modifier
        .size(200.dp)
        .liquidGlass(state, shape = RoundedCornerShape(24.dp)),
)
```

### 调参

```kotlin
state.thickness = 1.5f          // 玻璃厚度：影响边缘折射位移量
state.cornerRadius = 24f        // 圆角（dp）
state.tint = Color(0x22FFFFFF)  // 玻璃染色
state.enableTouchFollow = true  // 触摸跟随高光
state.decayTouch()              // 主动让高光衰减
```

---

## API

### `LiquidGlass`

| 成员 | 说明 |
|------|------|
| `LiquidGlass.rememberState(context, forceQuality)` | 创建并记住 `LiquidGlassState`；`forceQuality` 可强制指定质量级别（调试用） |

### `Modifier` 扩展

| 扩展 | 说明 |
|------|------|
| `Modifier.liquidGlassSource(state, enabled)` | 标记背景采样源。内部用 `drawWithCache` + `GraphicsLayer` 在绘制阶段捕获，模糊在后台线程执行，不阻塞 UI |
| `Modifier.liquidGlass(state, shape)` | 把任意可组合内容渲染为液态玻璃 |

### 组合式函数

| 函数 | 说明 |
|------|------|
| `GlassCard(state, modifier, onClick, content)` | 带点击反馈的玻璃卡片 |
| `GlassTabBar(state, tabs, selectedIndex, onTabSelected, modifier)` | 玻璃 TabBar |

### `LiquidGlassState`

持有一块玻璃的全部渲染参数与运行时数据。

| 属性 | 类型 | 说明 |
|------|------|------|
| `quality` | `GlassQuality` | 当前生效的质量级别 |
| `tint` | `Color` | 玻璃染色 |
| `thickness` | `Float` | 厚度，影响边缘折射位移 |
| `cornerRadius` | `Float` | 圆角（dp） |
| `enableTouchFollow` | `Boolean` | 是否启用触摸跟随 |
| `backgroundBitmap` | `ImageBitmap?` | 捕获到的背景原图 |
| `blurredBackground` | `ImageBitmap?` | 后台模糊结果 |
| `lastRenderCostMs` | `Float` | 最近一次后台模糊耗时（ms），可用于埋点 |
| `shaderWarmedUp` | `Boolean` | AGSL shader 是否已完成预热 |

### `GlassQuality`

| 级别 | 触发条件 | 实现 | 折射 | 色散 |
|------|----------|------|:---:|:---:|
| `Full` | API 33+ 且非低内存设备 | AGSL `RuntimeShader` + `RenderEffect`，SDF 高度场折射 + 7 路色散 + Fresnel 边缘光 | ✅ | 7 路 |
| `Medium` | API 31-32，或 API 33+ 低内存设备 | `RenderEffect.createBlurEffect` 硬件模糊 + Canvas 色调/高光 | ❌ | ❌ |
| `Minimal` | API 24-30 | CPU 可分离高斯模糊（降采样后近似） | ❌ | ❌ |
| `Fallback` | API 21-23 | 半透明渐变 + 1px 边框 | ❌ | ❌ |

分级逻辑：

```kotlin
val quality = GlassQuality.resolve(
    sdkInt = Build.VERSION.SDK_INT,
    isLowRamDevice = activityManager.isLowRamDevice,
    hasHardwareAcceleration = true,
)
```

`ActivityManager.isLowRamDevice == true` 会强制把 `Full` 降为 `Medium`；无硬件加速则降到 `Minimal`。

---

## 效果原理

### AGSL 路径（API 33+）

1. **SDF 圆角矩形**：`sdRoundRect()` 求有符号距离场，得到玻璃的解析形状
2. **高度场法线**：以 SDF 构造厚度场，中心差分求梯度得到表面法线
3. **折射位移**：按法线方向对背景采样 UV 做偏移，偏移量与 [thickness] 成正比
4. **7 路色散**：R/G/B 三通道按 7 个不同偏移权重采样后加权合成，权重和为 1
5. **Fresnel 边缘光**：`pow(1 - saturate(normal.z), 2.6)` 在掠射角处提亮，形成玻璃边缘亮边
6. **触摸动态光照**：手指位置作为额外光源叠加到边缘光上

### 降级路径

- **Medium**：`RenderEffect` 只能做形变/模糊/颜色滤镜，**无法采样位移**，因此没有折射，只保留模糊 + 高光 + 色调
- **Minimal**：CPU 侧三趟「水平 + 垂直」滑动窗口 Box 模糊近似高斯，O(1)/像素；在 1/3~1/4 降采样后执行，单帧 < 3ms
- **Fallback**：完全不模糊，仅半透明渐变 + 边框，保证 API 21 设备不崩溃、不 OOM

---

## 性能设计

| 手段 | 收益 |
|------|------|
| 模糊 Pass 在 1/4 分辨率执行 | 模糊计算量降至 1/16 |
| `resolveDownsample()` 动态降采样 | 长边 > 1080px 额外降一级，上限 6 |
| 背景捕获与模糊异步化 | 单线程低优先级 `Executors`，不阻塞 UI |
| 背景捕获与模糊结果共享 | 同一 `LiquidGlassState` 下多个玻璃容器只算一次 |
| 低内存设备强制降级 | `isLowRamDevice` → `Full` 降为 `Medium` |
| AGSL shader 预热 | 首次进入避免编译卡顿 |

---

## 已知限制

- **Medium / Minimal / Fallback 无折射**：受限于 `RenderEffect` 的能力与 CPU 成本，只有 AGSL 路径（API 33+）能实现真实像素位移折射
- **背景源需要显式标记**：库无法自动抓取任意上层内容，必须用 `Modifier.liquidGlassSource` 标记背景层
- **`GraphicsLayer.toImageBitmap()` 是挂起函数**：捕获在协程内完成，极端情况下首帧可能短暂看到未模糊的背景
- **不建议单屏同时放过多玻璃容器**：捕获与模糊结果虽然共享，但每个容器的 AGSL 绘制仍是一次独立 Pass

---

## 工程结构

```
liquid-glass-compose/
├── liquid-glass/                       # 库模块
│   └── src/main/java/com/liquidglass/
│       ├── LiquidGlass.kt              # 公开入口：rememberState / Modifier 扩展 / GlassCard / GlassTabBar
│       ├── LiquidGlassState.kt         # 状态与降采样策略
│       ├── GlassQuality.kt             # 四档质量分级
│       ├── AgslShaders.kt              # AGSL 着色器源码
│       └── internal/
│           ├── AgslRenderer.kt         # AGSL RuntimeShader + RenderEffect（API 33+）
│           ├── RenderEffectRenderer.kt # createBlurEffect 硬件模糊（API 31+）
│           └── CpuRenderer.kt          # 可分离 Box 高斯（API 24-30）
└── sample/                             # 示例应用（3 个演示页面）
```

---

## 构建与测试

```bash
./gradlew :liquid-glass:assembleRelease        # 产出 AAR
./gradlew :liquid-glass:testReleaseUnitTest    # 单元测试
./gradlew :sample:assembleDebug                # 示例 APK
./gradlew :liquid-glass:publishToMavenLocal    # 发布到本地 Maven
```

单元测试覆盖 `GlassQuality` 的四档分级逻辑、低内存/无硬件加速的降级决策，以及 `resolveDownsample` 的降采样边界。

---

## License

MIT
