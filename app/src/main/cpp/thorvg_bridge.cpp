#include "thorvg_bridge.h"
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>
#include <memory>
#include "thorvg.h"

#define LOG_TAG "ThorvgEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool g_tvg_initialized = false;

static void ensureInitialized() {
    if (!g_tvg_initialized) {
        tvg::Initializer::init(2);
        g_tvg_initialized = true;
        LOGI("ThorVG initialized successfully with 2 threads.");
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeInit(JNIEnv* /* env */, jclass /* clazz */) {
    ensureInitialized();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderStroke(
    JNIEnv* env,
    jclass clazz,
    jobject bitmap,
    jfloatArray points,
    jfloat strokeWidth,
    jint r,
    jint g,
    jint b,
    jint a
) {
    return Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderSmoothVectorStroke(
        env, clazz, bitmap, points, strokeWidth, r, g, b, a, JNI_TRUE
    );
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderSmoothVectorStroke(
    JNIEnv* env,
    jclass /* clazz */,
    jobject bitmap,
    jfloatArray points,
    jfloat strokeWidth,
    jint r,
    jint g,
    jint b,
    jint a,
    jboolean isCubicSmoothed
) {
    if (!bitmap || !points) return JNI_FALSE;
    ensureInitialized();

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Invalid bitmap format for ThorVG stroke rendering");
        return JNI_FALSE;
    }

    jsize ptCount = env->GetArrayLength(points);
    if (ptCount < 4) return JNI_FALSE; // Need at least 2 points (4 floats)

    jfloat* pts = env->GetFloatArrayElements(points, nullptr);
    if (!pts) return JNI_FALSE;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        env->ReleaseFloatArrayElements(points, pts, JNI_ABORT);
        return JNI_FALSE;
    }

    auto canvas = std::unique_ptr<tvg::SwCanvas>(tvg::SwCanvas::gen());
    if (!canvas) {
        AndroidBitmap_unlockPixels(env, bitmap);
        env->ReleaseFloatArrayElements(points, pts, JNI_ABORT);
        return JNI_FALSE;
    }

    canvas->target(static_cast<uint32_t*>(pixels), info.stride / 4, info.width, info.height, tvg::ColorSpace::ABGR8888);

    auto shape = tvg::Shape::gen();
    shape->strokeWidth(strokeWidth);
    shape->strokeCap(tvg::StrokeCap::Round);
    shape->strokeJoin(tvg::StrokeJoin::Round);
    shape->strokeFill(static_cast<uint8_t>(r), static_cast<uint8_t>(g), static_cast<uint8_t>(b), static_cast<uint8_t>(a));

    int numPoints = ptCount / 2;
    shape->moveTo(pts[0], pts[1]);

    if (isCubicSmoothed && numPoints >= 3) {
        // Continuous tangent Bézier spline fitting for smooth freehand drawing & signatures
        float currX = pts[0];
        float currY = pts[1];

        for (int i = 1; i < numPoints - 1; ++i) {
            float ctrlX = pts[i * 2];
            float ctrlY = pts[i * 2 + 1];
            float nextX = pts[(i + 1) * 2];
            float nextY = pts[(i + 1) * 2 + 1];

            // Midpoint between current control point and next point
            float midX = (ctrlX + nextX) * 0.5f;
            float midY = (ctrlY + nextY) * 0.5f;

            // Convert quadratic curve (curr -> ctrl -> mid) to exact cubic Bezier
            float c1x = currX + (2.0f / 3.0f) * (ctrlX - currX);
            float c1y = currY + (2.0f / 3.0f) * (ctrlY - currY);
            float c2x = midX + (2.0f / 3.0f) * (ctrlX - midX);
            float c2y = midY + (2.0f / 3.0f) * (ctrlY - midY);

            shape->cubicTo(c1x, c1y, c2x, c2y, midX, midY);
            currX = midX;
            currY = midY;
        }

        // Final segment to last point
        shape->lineTo(pts[ptCount - 2], pts[ptCount - 1]);
    } else {
        for (int i = 2; i < ptCount; i += 2) {
            shape->lineTo(pts[i], pts[i + 1]);
        }
    }

    canvas->add(shape);
    canvas->draw(false);
    canvas->sync();

    AndroidBitmap_unlockPixels(env, bitmap);
    env->ReleaseFloatArrayElements(points, pts, JNI_ABORT);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderShape(
    JNIEnv* env,
    jclass clazz,
    jobject bitmap,
    jint shapeType,
    jfloat x1,
    jfloat y1,
    jfloat x2,
    jfloat y2,
    jfloat strokeWidth,
    jint r,
    jint g,
    jint b,
    jint a,
    jboolean isFilled
) {
    jint fillType = isFilled ? 1 : 0;
    return Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderStyledShape(
        env, clazz, bitmap, shapeType,
        x1, y1, x2, y2,
        strokeWidth,
        r, g, b, a,
        fillType,
        r, g, b, a,
        r, g, b, a,
        0, // Solid border
        4.0f // default corner radius
    );
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderStyledShape(
    JNIEnv* env,
    jclass /* clazz */,
    jobject bitmap,
    jint shapeType,
    jfloat x1,
    jfloat y1,
    jfloat x2,
    jfloat y2,
    jfloat strokeWidth,
    jint strokeR,
    jint strokeG,
    jint strokeB,
    jint strokeA,
    jint fillType,
    jint fillR1,
    jint fillG1,
    jint fillB1,
    jint fillA1,
    jint fillR2,
    jint fillG2,
    jint fillB2,
    jint fillA2,
    jint dashStyle,
    jfloat cornerRadius
) {
    if (!bitmap) return JNI_FALSE;
    ensureInitialized();

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        return JNI_FALSE;
    }

    auto canvas = std::unique_ptr<tvg::SwCanvas>(tvg::SwCanvas::gen());
    if (!canvas) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    canvas->target(static_cast<uint32_t*>(pixels), info.stride / 4, info.width, info.height, tvg::ColorSpace::ABGR8888);

    auto shape = tvg::Shape::gen();

    // 1. Border stroke configuration
    if (strokeWidth > 0.0f && strokeA > 0) {
        shape->strokeWidth(strokeWidth);
        shape->strokeCap(tvg::StrokeCap::Round);
        shape->strokeJoin(tvg::StrokeJoin::Round);
        shape->strokeFill(static_cast<uint8_t>(strokeR), static_cast<uint8_t>(strokeG), static_cast<uint8_t>(strokeB), static_cast<uint8_t>(strokeA));

        if (dashStyle == 1) { // Dashed
            float dashes[] = { strokeWidth * 2.8f, strokeWidth * 1.8f };
            shape->strokeDash(dashes, 2);
        } else if (dashStyle == 2) { // Dotted
            float dots[] = { 1.5f, strokeWidth * 1.6f };
            shape->strokeDash(dots, 2);
        } else {
            shape->strokeDash(nullptr, 0);
        }
    } else {
        shape->strokeWidth(0.0f);
    }

    // 2. Custom Styling: Solid, Linear Gradient, or Radial Gradient Fill
    if (fillType == 1) { // Solid Fill
        shape->fill(static_cast<uint8_t>(fillR1), static_cast<uint8_t>(fillG1), static_cast<uint8_t>(fillB1), static_cast<uint8_t>(fillA1));
    } else if (fillType == 2) { // Linear Gradient Fill
        tvg::Fill::ColorStop stops[2] = {
            { 0.0f, static_cast<uint8_t>(fillR1), static_cast<uint8_t>(fillG1), static_cast<uint8_t>(fillB1), static_cast<uint8_t>(fillA1) },
            { 1.0f, static_cast<uint8_t>(fillR2), static_cast<uint8_t>(fillG2), static_cast<uint8_t>(fillB2), static_cast<uint8_t>(fillA2) }
        };
        auto linGrad = tvg::LinearGradient::gen();
        linGrad->linear(x1, y1, x2, y2);
        linGrad->colorStops(stops, 2);
        shape->fill(linGrad);
    } else if (fillType == 3) { // Radial Gradient Fill
        float cx = (x1 + x2) * 0.5f;
        float cy = (y1 + y2) * 0.5f;
        float r = std::max(std::abs(x2 - x1), std::abs(y2 - y1)) * 0.5f;
        tvg::Fill::ColorStop stops[2] = {
            { 0.0f, static_cast<uint8_t>(fillR1), static_cast<uint8_t>(fillG1), static_cast<uint8_t>(fillB1), static_cast<uint8_t>(fillA1) },
            { 1.0f, static_cast<uint8_t>(fillR2), static_cast<uint8_t>(fillG2), static_cast<uint8_t>(fillB2), static_cast<uint8_t>(fillA2) }
        };
        auto radGrad = tvg::RadialGradient::gen();
        radGrad->radial(cx, cy, r, cx, cy, 0.0f);
        radGrad->colorStops(stops, 2);
        shape->fill(radGrad);
    }

    // 3. Shape Geometry Overlay
    switch (shapeType) {
        case 0: // RECTANGLE OUTLINE
        case 1: // FILLED / REDACTION BOX
        case 5: { // HIGHLIGHTER BOX
            float left = std::min(x1, x2);
            float top = std::min(y1, y2);
            float w = std::abs(x2 - x1);
            float h = std::abs(y2 - y1);
            float cr = std::max(0.0f, cornerRadius);
            shape->appendRect(left, top, w, h, cr, cr);
            break;
        }
        case 2: { // CIRCLE / ELLIPSE
            float cx = (x1 + x2) * 0.5f;
            float cy = (y1 + y2) * 0.5f;
            float rx = std::abs(x2 - x1) * 0.5f;
            float ry = std::abs(y2 - y1) * 0.5f;
            shape->appendCircle(cx, cy, rx, ry);
            break;
        }
        case 3: { // STRAIGHT LINE
            shape->moveTo(x1, y1);
            shape->lineTo(x2, y2);
            break;
        }
        case 4: { // DIRECTIONAL ARROW
            shape->moveTo(x1, y1);
            shape->lineTo(x2, y2);

            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = std::sqrt(dx * dx + dy * dy);
            if (len > 0.001f) {
                float udx = dx / len;
                float udy = dy / len;
                float arrowLen = std::max(18.0f, strokeWidth * 3.5f);
                float arrowW = std::max(10.0f, strokeWidth * 2.0f);

                float p1x = x2 - arrowLen * udx + arrowW * (-udy);
                float p1y = y2 - arrowLen * udy + arrowW * udx;
                float p2x = x2 - arrowLen * udx - arrowW * (-udy);
                float p2y = y2 - arrowLen * udy - arrowW * udx;

                shape->moveTo(x2, y2);
                shape->lineTo(p1x, p1y);
                shape->moveTo(x2, y2);
                shape->lineTo(p2x, p2y);
            }
            break;
        }
        default:
            shape->moveTo(x1, y1);
            shape->lineTo(x2, y2);
            break;
    }

    canvas->add(shape);
    canvas->draw(false);
    canvas->sync();

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderSvg(
    JNIEnv* env,
    jclass /* clazz */,
    jobject bitmap,
    jstring svgString,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height,
    jfloat angle,
    jfloat opacity
) {
    if (!bitmap || !svgString) return JNI_FALSE;
    ensureInitialized();

    const char* svgChars = env->GetStringUTFChars(svgString, nullptr);
    if (!svgChars) return JNI_FALSE;
    std::string svg(svgChars);
    env->ReleaseStringUTFChars(svgString, svgChars);

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        return JNI_FALSE;
    }

    auto canvas = std::unique_ptr<tvg::SwCanvas>(tvg::SwCanvas::gen());
    if (!canvas) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    canvas->target(static_cast<uint32_t*>(pixels), info.stride / 4, info.width, info.height, tvg::ColorSpace::ABGR8888);

    auto picture = tvg::Picture::gen();
    if (picture->load(svg.c_str(), static_cast<uint32_t>(svg.length()), "svg", nullptr, true) == tvg::Result::Success) {
        if (width > 0.0f && height > 0.0f) {
            picture->size(width, height);
        }
        picture->translate(x, y);
        if (angle != 0.0f) {
            picture->rotate(angle);
        }
        if (opacity < 1.0f) {
            picture->opacity(static_cast<uint8_t>(std::max(0.0f, std::min(1.0f, opacity)) * 255.0f));
        }
        canvas->add(picture);
        canvas->draw(false);
        canvas->sync();
    } else {
        LOGE("ThorVG failed to parse SVG string");
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeApplySmudgeBlur(
    JNIEnv* env,
    jclass /* clazz */,
    jobject bitmap,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint radius
) {
    if (!bitmap) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }

    int imgW = static_cast<int>(info.width);
    int imgH = static_cast<int>(info.height);

    int l = std::max(0, std::min(left, right));
    int r = std::min(imgW, std::max(left, right));
    int t = std::max(0, std::min(top, bottom));
    int b = std::min(imgH, std::max(top, bottom));

    int w = r - l;
    int h = b - t;
    if (w <= 1 || h <= 1) return JNI_TRUE;

    int rad = std::max(2, std::min(radius, 40));

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        return JNI_FALSE;
    }

    uint32_t* buf = static_cast<uint32_t*>(pixels);
    int stride = info.stride / 4;

    // Fast 2-pass separable box/smudge blur across the selected region
    std::vector<uint32_t> temp(w * h);

    // Pass 1: Horizontal blur from buf -> temp
    for (int y = 0; y < h; ++y) {
        int srcY = t + y;
        uint32_t* srcRow = buf + srcY * stride;
        uint32_t* dstRow = temp.data() + y * w;

        int rSum = 0, gSum = 0, bSum = 0, aSum = 0;
        int count = 0;

        for (int k = -rad; k <= rad; ++k) {
            int px = std::clamp(l + k, 0, imgW - 1);
            uint32_t c = srcRow[px];
            rSum += (c & 0x000000FF);
            gSum += ((c >> 8) & 0x000000FF);
            bSum += ((c >> 16) & 0x000000FF);
            aSum += ((c >> 24) & 0x000000FF);
            count++;
        }

        for (int x = 0; x < w; ++x) {
            dstRow[x] = (static_cast<uint32_t>(rSum / count)) |
                        (static_cast<uint32_t>(gSum / count) << 8) |
                        (static_cast<uint32_t>(bSum / count) << 16) |
                        (static_cast<uint32_t>(aSum / count) << 24);

            int xRemove = std::clamp(l + x - rad, 0, imgW - 1);
            int xAdd = std::clamp(l + x + rad + 1, 0, imgW - 1);

            uint32_t cRem = srcRow[xRemove];
            uint32_t cAdd = srcRow[xAdd];

            rSum += (cAdd & 0x000000FF) - (cRem & 0x000000FF);
            gSum += ((cAdd >> 8) & 0x000000FF) - ((cRem >> 8) & 0x000000FF);
            bSum += ((cAdd >> 16) & 0x000000FF) - ((cRem >> 16) & 0x000000FF);
            aSum += ((cAdd >> 24) & 0x000000FF) - ((cRem >> 24) & 0x000000FF);
        }
    }

    // Pass 2: Vertical blur from temp -> buf
    for (int x = 0; x < w; ++x) {
        int rSum = 0, gSum = 0, bSum = 0, aSum = 0;
        int count = 0;

        for (int k = -rad; k <= rad; ++k) {
            int py = std::clamp(k, 0, h - 1);
            uint32_t c = temp[py * w + x];
            rSum += (c & 0x000000FF);
            gSum += ((c >> 8) & 0x000000FF);
            bSum += ((c >> 16) & 0x000000FF);
            aSum += ((c >> 24) & 0x000000FF);
            count++;
        }

        for (int y = 0; y < h; ++y) {
            int dstY = t + y;
            buf[dstY * stride + (l + x)] =
                (static_cast<uint32_t>(rSum / count)) |
                (static_cast<uint32_t>(gSum / count) << 8) |
                (static_cast<uint32_t>(bSum / count) << 16) |
                (static_cast<uint32_t>(aSum / count) << 24);

            int yRemove = std::clamp(y - rad, 0, h - 1);
            int yAdd = std::clamp(y + rad + 1, 0, h - 1);

            uint32_t cRem = temp[yRemove * w + x];
            uint32_t cAdd = temp[yAdd * w + x];

            rSum += (cAdd & 0x000000FF) - (cRem & 0x000000FF);
            gSum += ((cAdd >> 8) & 0x000000FF) - ((cRem >> 8) & 0x000000FF);
            bSum += ((cAdd >> 16) & 0x000000FF) - ((cRem >> 16) & 0x000000FF);
            aSum += ((cAdd >> 24) & 0x000000FF) - ((cRem >> 24) & 0x000000FF);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

} // extern "C"
