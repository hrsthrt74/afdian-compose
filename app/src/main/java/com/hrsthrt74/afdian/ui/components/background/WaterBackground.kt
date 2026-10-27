package com.hrsthrt74.afdian.ui.components.background

import android.animation.ValueAnimator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.theme.isDarkColorScheme
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 日志 TAG。 */
private const val WaterLogTag = "WaterBackground"

/**
 * 帧间隔：60fps。
 *
 * 60Hz 屏上每帧都算，120Hz 屏上隔帧算 —— 再快没有意义，全屏噪声是真金白银的 GPU 开销。
 * （曾经压到 30fps：慢速水面看不太出来，但快速上滑时波纹会明显发顿。想省电改回 33_000_000L 即可。）
 */
private const val FrameIntervalNanos = 1_000_000_000L / 60

/**
 * 动态海水背景的 AGSL 源码（Android 13+ 的 RuntimeShader）。
 *
 * 观感：俯视一片平静的水面 —— 基础是上亮下深的静态水色，上面叠「透视压缩」的波纹
 * （越远越密越细），右侧一条阳光反射带，带内散着稀疏的碎金。整体低对比，安静，不抢内容。
 *
 * 走过的弯路（改之前先读一遍，别重复踩）：
 * - 直接对 fbm 取阈值当浪脊：噪声高处整片点亮，看起来像一朵朵白云；
 * - 用值噪声（双线性插值）取窄带：等值线是折线，会画出「低多边形碎片」；
 * - 对连续噪声取高次幂当碎金：值噪声每个格点顶点都恰好等于 1，结果整片「下雪」。
 *   碎金必须按格子哈希撒点。
 *
 * 三条硬约束：
 * 1. AGSL 的循环必须是常量上界（`for (int i = 0; i < 3; i++)`），不能写 `while` 之类的动态循环；
 * 2. 颜色一律用 `uniform float4` + `setFloatUniform`，**不要**用 `setColorUniform`
 *    （那条要求 uniform 声明成 `layout(color)`，跨色彩空间时行为不好把握）；
 * 3. 细节强度由 `uIntensity` 统一缩放：浅色模式调低，否则白底上会糊成一片云。
 */
private const val WaterAgsSource = """
uniform float2 uResolution;  // 画布尺寸（px），由绘制阶段写入
uniform float uTime;         // 秒，动画时间
uniform float uIntensity;    // 细节强度：深色 1.0 / 浅色 0.62
uniform float4 uFar;         // 远景/上部水色
uniform float4 uDeep;        // 近景/下部水色
uniform float4 uCrest;       // 波纹亮色
uniform float4 uSpark;       // 阳光碎金

// 2D -> 2D 哈希：给每个格点一个伪随机梯度方向。
// 用乘法版（无 sin）：经典 sin 哈希每个格点要算两次 sin，全屏逐像素跑下来很贵，
// 这个版本只有几次乘加，统计性质一样。
float2 hash22(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return -1.0 + 2.0 * fract((p3.xx + p3.yz) * p3.zy);
}

// 2D -> 1D 哈希：给格子撒点用（碎金）
float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 梯度（Perlin）噪声，范围约 [-0.7, 0.7]。
// 为什么不用值噪声：值噪声的双线性插值让等值线是折线，取窄带当浪脊时会画出
// 「低多边形碎片」，一眼假。梯度噪声的等值线是光滑曲线，才像水。
float perlin(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    // 五次曲线淡入淡出：C2 连续，比 smoothstep 更不容易看出格子
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(hash22(i), f);
    float b = dot(hash22(i + float2(1.0, 0.0)), f - float2(1.0, 0.0));
    float c = dot(hash22(i + float2(0.0, 1.0)), f - float2(0.0, 1.0));
    float d = dot(hash22(i + float2(1.0, 1.0)), f - float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;
    float aspect = res.x / res.y;
    float t = uTime * 0.20;

    // 1) 基础水色：上亮下深。渐变范围特意拉得比屏幕还长（-0.15 ~ 1.05）——
    //    这样画面上方始终是最亮的 uFar，深色要到屏幕最底部才完全铺开，
    //    整页看起来是「一片浅水慢慢变深」，而不是上半屏亮、下半屏忽然沉下去
    float3 col = mix(uFar.rgb, uDeep.rgb, smoothstep(-0.15, 1.05, uv.y));

    // 2) 透视映射：屏幕越靠上（越远）波纹越密越细。
    //    depth 用 +0.28 避免近处除零；y 走 log，屏幕空间里波距才是均匀的
    float depth = uv.y + 0.28;
    float2 p = float2((uv.x - 0.5) * aspect * 1.7 / depth, log(depth) * 6.0);

    // 2b) 镜头平移：整片水面向右匀速移动。
    //     采样点减时间项 => 图案向右走（f(x - vt) 才是向右平移，加号会变成向左）。
    //     系数就是速度：0.012 时画面底部约 2%/秒屏宽、顶部约 0.5% —— 近处快远处慢，
    //     所以看起来是「镜头在动」而不是「贴图在滚」。
    p.x -= uTime * 0.030;

    // 3) 两层梯度噪声：低频决定浪形，高频给细纹；第二层被第一层推着走，避免规则感
    //    这两层**不带任何时间项**：横向运动全部交给上面的镜头平移。
    //    原先它们各自带着 t*0.45 / -t*0.70 的横向漂移，速度是平移的十几倍、方向还相反，
    //    平移叠上去直接被打包吞掉 —— 这就是「怎么加都看不出在平移」的原因。
    float h1 = perlin(p * float2(0.85, 1.60));
    float h2 = perlin(p * float2(2.10, 3.90) + float2(0.0, h1 * 1.20));

    // 4) 波带：在「被浪高扰动的 y」上取正弦。
    //    这是全篇的关键 —— 正弦保证等值线是连续流动的横带，
    //    再做窄化就得到浪脊；直接用噪声窄带会碎成一块块。
    //    前两项的配比就是「浪高 / 浪密」：p.y 的系数越小浪越疏，
    //    h1/h2 的系数越大起伏越高（形状被噪声推得更狠）
    float phase = p.y * 5.4 + h1 * 6.2 + h2 * 2.9 + t * 0.55;
    float bands = 0.5 + 0.5 * sin(phase);
    //    门槛压到 0.60：亮带的上升段更长、两侧是渐隐的，不会出现一条硬边
    float crest = smoothstep(0.60, 1.0, bands);
    crest *= crest;

    // 4b) 细波纹：更高频的第二层，只给浪脊加一点点碎光，避免大浪看起来像一团云
    float fine = 0.5 + 0.5 * sin(p.y * 16.0 + h2 * 6.0 + t * 2.6);
    //    这一层最容易出硬边（高频 + 窄带），门槛放宽、权重压低，让细纹只当「毛边」用
    float fineCrest = smoothstep(0.70, 1.0, fine) * 0.30;

    // 5) 明暗与浪脊共用同一条波带：亮处起脊、暗处是波谷，观感才连贯
    float nearFade = smoothstep(0.02, 0.45, uv.y);
    col *= mix(1.0 - 0.10 * uIntensity, 1.0 + 0.09 * uIntensity, bands);
    //    亮带变宽后总量会变大，mix 系数相应从 0.50 压到 0.42，整体亮度不变、过渡更绵
    col = mix(col, uCrest.rgb, (crest + fineCrest * crest) * 0.42 * nearFade * uIntensity);

    // 6) 阳光反射：一条从上往下变宽的柔和亮带（偏右，避开居中的文字）。
    //    这束光是整幅画面里唯一的「方向性」元素，必须让它自己动 ——
    //    位置横移、宽度呼吸、亮度明灭三条频率错开，看起来才像水面在晃而不是贴了张渐变图
    float sunX = 0.72 + 0.085 * sin(uTime * 0.30) + 0.015 * sin(uTime * 0.71 + 1.3);
    float spread = (0.08 + uv.y * 0.5) * (1.0 + 0.14 * sin(uTime * 0.19 + 0.9));
    float dx = (uv.x - sunX) / spread;
    float column = exp(-dx * dx * 1.8) * (1.0 - smoothstep(0.35, 1.0, uv.y));
    float columnGain = 0.05 + 0.014 * sin(uTime * 0.31 + 2.1);
    col = mix(col, uSpark.rgb, column * columnGain * uIntensity);

    // 7) 碎金：按格子哈希撒点，只让极少数格子亮，且只出现在浪脊上（真实的水面反光就是这样）。
    //    注意不能对连续噪声取幂 —— 值噪声每个格点都恰好到 1，取幂只会把整片压成「下雪」。
    float2 cellUv = fragCoord / 6.0;
    float2 cell = floor(cellUv);
    float2 f = fract(cellUv) - 0.5;
    float rnd = hash21(cell);
    // 横向拉长成短划线，比圆点更像水面反光
    float dotShape = smoothstep(0.5, 0.10, length(f * float2(0.45, 1.0)));
    float lit = step(0.996, rnd);
    float twinkle = 0.55 + 0.45 * sin(uTime * 2.2 + rnd * 6.2831);
    float sparkBand = smoothstep(0.04, 0.20, uv.y) * (1.0 - smoothstep(0.40, 0.72, uv.y));
    col += uSpark.rgb * lit * dotShape * twinkle * crest * (0.08 + column * 1.5) * sparkBand * 3.0 * uIntensity;

    return half4(col, 1.0);
}
"""

/**
 * 海水调色板。
 *
 * 只放外观参数（颜色 + 细节强度），不放尺寸：尺寸由绘制区域决定，动画节奏是组件内部常量。
 */
@Immutable
data class WaterPalette(
    /** 远景（画面上部）水色，偏亮。 */
    val far: Color,
    /** 近景（画面下部）水色，偏深。 */
    val deep: Color,
    /** 浪脊亮色，叠在明暗之上。 */
    val crest: Color,
    /** 阳光碎金，只在反射柱附近出现。 */
    val spark: Color,
    /**
     * 细节强度：波纹、光柱、碎金整体乘这个系数。
     *
     * 浅色模式必须调低——同样的白色亮线压在浅底上，观感会从「水波」变成「一团云」。
     */
    val intensity: Float,
)

/** 深色模式：深海夜景。 */
private val DarkWaterPalette = WaterPalette(
    far = Color(0xFF0B2A3E),
    deep = Color(0xFF04121D),
    crest = Color(0xFF63C6DB),
    spark = Color(0xFFDFF7FF),
    intensity = 0.8f,
)

/** 浅色模式：浅海白天。整体抬高亮度、压低对比，保证半透明卡片里的深色文字仍然可读。 */
private val LightWaterPalette = WaterPalette(
    far = Color(0xFFADDCF4),
    deep = Color(0xFF3589C4),
    crest = Color(0xFFF0FBFF),
    spark = Color(0xFFFFFFFF),
    intensity = 0.8f,
)

/**
 * 取当前主题对应的海水调色板。
 *
 * 深浅判断走项目的 [isDarkColorScheme]（看页面底色亮度），而不是「用户选了哪个模式」——
 * 跟随壁纸取色、自定义主题色都可能让色板与模式对不上。
 */
@Composable
fun rememberWaterPalette(): WaterPalette =
    if (isDarkColorScheme(MiuixTheme.colorScheme)) DarkWaterPalette else LightWaterPalette

/**
 * 动态海水背景。
 *
 * 使用方只需给一个铺满绘制区域的 modifier（通常 `Modifier.matchParentSize()` / `fillMaxSize()`），
 * 它会自己画在最底层，不要再套背景色，否则会盖住水面。
 *
 * 降级：设备不支持 runtime shader（API < 33）、或 AGSL 编译失败时，退化成一张静态竖向渐变，
 * 观感不塌也不会崩。**legacy 包的隐藏逻辑在调用方**（本组件不读 BuildConfig），
 * 这样组件本身与包身份解耦，日后哪个页面想用都能直接用。
 *
 * @param modifier 绘制区域
 * @param animated 是否播放动画（系统「移除动画」开启时由内部自动停帧）。
 *        页面被销毁时 `LaunchedEffect` 随之取消；App 切后台后没有帧可用，`withFrameNanos`
 *        会自然挂起，不需要额外的生命周期判断。
 */
@Composable
fun WaterBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val palette = rememberWaterPalette()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        WaterShaderLayer(modifier = modifier, palette = palette, animated = animated)
    } else {
        // API < 33：没有 RuntimeShader，铺一张静态渐变兜底
        StaticWaterFallback(modifier, palette)
    }
}

/**
 * 不支持 runtime shader 时的静态兜底：只用调色板画竖向渐变。
 *
 * 与 shader 版本共用同一个调色板，深浅色切换时观感保持一致。
 */
@Composable
private fun StaticWaterFallback(modifier: Modifier, palette: WaterPalette) {
    Spacer(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(palette.far, palette.deep)),
        ),
    )
}

/**
 * runtime shader 版本的实现（真正的海水）。
 *
 * 重绘策略：时间存在 [mutableFloatStateOf] 里，**只在绘制阶段读**——
 * 这样每帧只失效 draw，不触发重组；uniform 改动本身不会让 Compose 重绘。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun WaterShaderLayer(
    modifier: Modifier,
    palette: WaterPalette,
    animated: Boolean,
) {
    // AGSL 编译失败会抛异常（比如字符串写错一个分号），这里兜住退化成静态渐变，
    // 不让一个装饰性背景把整个页面搞崩
    val shader = remember {
        runCatching { RuntimeShader(WaterAgsSource) }
            .onFailure { AppLog.e(WaterLogTag, "海水 shader 编译失败，回落到静态渐变", it) }
            .getOrNull()
    }
    if (shader == null) {
        // 编译失败是稳定结果，这个分支不会再翻回来，提前 return 是安全的
        StaticWaterFallback(modifier, palette)
        return
    }
    // asBrush() 复用同一个可变 shader 实例：uniform 改了就能生效，不必每帧重建 Brush
    val brush = remember(shader) { shader.asBrush() }

    // 动画时间（秒）。只有绘制阶段读它，见本函数顶部注释
    var time by remember { mutableFloatStateOf(0f) }

    // 系统关掉动画时不要动：无障碍设置里的「移除动画」应当让背景静止
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    LaunchedEffect(animated, animatorsEnabled) {
        if (!animated || !animatorsEnabled) return@LaunchedEffect
        // 以第一次拿到帧的时刻为零点：进入页面时时间从 0 开始，画面不会跳到某个随机相位
        val startNanos = withFrameNanos { it }
        var lastEmitNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                // 按 FrameIntervalNanos 抽帧（当前 60fps）：跳过还没到间隔的帧
                if (now - lastEmitNanos >= FrameIntervalNanos) {
                    lastEmitNanos = now
                    time = (now - startNanos) / 1_000_000_000f
                }
            }
        }
    }

    Spacer(
        modifier = modifier.drawBehind {
            if (size.width <= 0f || size.height <= 0f) return@drawBehind
            shader.setFloatUniform("uResolution", size.width, size.height)
            // 读 time 这个 State：每次变化只让本节点失效 draw，不重组整页
            shader.setFloatUniform("uTime", time)
            // 颜色每帧写一遍，开销是四次 uniform 赋值，可以忽略；好处是主题换了不用额外同步
            shader.setFloatUniform("uFar", palette.far.red, palette.far.green, palette.far.blue, 1f)
            shader.setFloatUniform("uDeep", palette.deep.red, palette.deep.green, palette.deep.blue, 1f)
            shader.setFloatUniform("uCrest", palette.crest.red, palette.crest.green, palette.crest.blue, 1f)
            shader.setFloatUniform("uSpark", palette.spark.red, palette.spark.green, palette.spark.blue, 1f)
            shader.setFloatUniform("uIntensity", palette.intensity)
            drawRect(brush = brush)
        },
    )
}
