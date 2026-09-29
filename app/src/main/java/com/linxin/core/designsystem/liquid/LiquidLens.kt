package com.linxin.core.designsystem.liquid

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangularShape

/**
 * 带可调色散强度的圆角矩形折射。
 *
 * 库自带的 `lens(chromaticAberration = true/false)` 只能开关色散：它把 shader 里的
 * `chromaticAberration` uniform 写死成 1f，而这个 uniform 实际是个 0..1 的乘数
 * （`dispersionIntensity = chromaticAberration * (居中坐标乘积 / 半尺寸乘积)`），
 * 所以复制官方 AGSL、只改这一处，设置页才能拉一根真正的色散滑杆。
 *
 * AGSL 与 cornerRadii 取值逻辑复制自 Kyant0/AndroidLiquidGlass (Apache-2.0) 的
 * internal `Shaders.kt` / `effects/Lens.kt`；dispersion = 0 时六个采样点重合，
 * 输出与不带色散的折射完全一致。
 */
fun BackdropEffectScope.lensWithDispersion(
    refractionHeight: Float,
    refractionAmount: Float,
    dispersion: Float,
    depthEffect: Boolean = false,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).coerceAtLeast(0f)
    }

    // 官方实现在这里会抛 UnsupportedOperationException；底栏用的是 Capsule()，
    // 属于 CornerBasedShape，取不到圆角半径说明用法变了，静默跳过比崩掉整个首页好。
    val radii = cornerRadiiPx ?: return

    runtimeShaderEffect(
        key = "LxRefractionWithDispersion",
        shaderString = RefractionWithDispersionShader,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        setFloatUniform("chromaticAberration", dispersion.coerceIn(0f, 1f))
    }
}

private val BackdropEffectScope.cornerRadiiPx: FloatArray?
    get() = when (val shape = shape) {
        is RoundedRectangularShape -> {
            val corners = shape.corners(size, layoutDirection, this)
            floatArrayOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        }

        is AbsoluteRoundedCornerShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            floatArrayOf(
                shape.topStart.toPx(size, this).coerceAtMost(maxRadius),
                shape.topEnd.toPx(size, this).coerceAtMost(maxRadius),
                shape.bottomEnd.toPx(size, this).coerceAtMost(maxRadius),
                shape.bottomStart.toPx(size, this).coerceAtMost(maxRadius),
            )
        }

        is CornerBasedShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            val isLtr = layoutDirection == LayoutDirection.Ltr
            floatArrayOf(
                (if (isLtr) shape.topStart else shape.topEnd).toPx(size, this).coerceAtMost(maxRadius),
                (if (isLtr) shape.topEnd else shape.topStart).toPx(size, this).coerceAtMost(maxRadius),
                (if (isLtr) shape.bottomEnd else shape.bottomStart).toPx(size, this).coerceAtMost(maxRadius),
                (if (isLtr) shape.bottomStart else shape.bottomEnd).toPx(size, this).coerceAtMost(maxRadius),
            )
        }

        else -> null
    }

private const val RoundedRectSdfFunctions = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

private const val RefractionWithDispersionShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$RoundedRectSdfFunctions

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""
