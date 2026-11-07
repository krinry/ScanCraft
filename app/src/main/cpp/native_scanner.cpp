#include "native_scanner.h"

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_krinry_scancraft_cv_NativeScannerEngine_detectBounds(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray yuvData,
    jint width,
    jint height
) {
    jbyte* yuv = env->GetByteArrayElements(yuvData, nullptr);
    if (!yuv) return nullptr;

    Quad quad = detectDocumentBounds(reinterpret_cast<const uint8_t*>(yuv), width, height);
    env->ReleaseByteArrayElements(yuvData, yuv, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(8);
    jfloat pts[8] = {
        quad.tl.x, quad.tl.y,
        quad.tr.x, quad.tr.y,
        quad.br.x, quad.br.y,
        quad.bl.x, quad.bl.y
    };
    env->SetFloatArrayRegion(result, 0, 8, pts);
    return result;
}

JNIEXPORT void JNICALL
Java_com_krinry_scancraft_cv_NativeScannerEngine_warpPerspective(
    JNIEnv* env,
    jobject /* this */,
    jobject srcBitmap,
    jobject dstBitmap,
    jfloatArray quadPoints
) {
    AndroidBitmapInfo srcInfo, dstInfo;
    void* srcPixels = nullptr;
    void* dstPixels = nullptr;

    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0 ||
        AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0) {
        return;
    }

    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0) return;
    if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        return;
    }

    jfloat* pts = env->GetFloatArrayElements(quadPoints, nullptr);
    if (pts) {
        Quad quad = {
            {pts[0], pts[1]},
            {pts[2], pts[3]},
            {pts[4], pts[5]},
            {pts[6], pts[7]}
        };

        warpPerspectiveNative(
            static_cast<const uint32_t*>(srcPixels), srcInfo.width, srcInfo.height,
            static_cast<uint32_t*>(dstPixels), dstInfo.width, dstInfo.height,
            quad
        );

        env->ReleaseFloatArrayElements(quadPoints, pts, JNI_ABORT);
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, dstBitmap);
}

JNIEXPORT void JNICALL
Java_com_krinry_scancraft_cv_NativeScannerEngine_applyFilter(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jint filterMode
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* px = static_cast<uint32_t*>(pixels);
    switch (filterMode) {
        case 1: // Magic Color
            applyMagicColorNative(px, info.width, info.height);
            break;
        case 2: // B&W Sauvola
            applyBradleySauvolaBWNative(px, info.width, info.height, 0.20f);
            break;
        case 3: // B&W 2 (Higher contrast)
            applyBradleySauvolaBWNative(px, info.width, info.height, 0.28f);
            break;
        case 4: // Grayscale
            applyGrayscaleNative(px, info.width, info.height);
            break;
        case 5: // Lighten
            applyLightenNative(px, info.width, info.height);
            break;
        case 6: // No Shadow
            applyNoShadowNative(px, info.width, info.height);
            break;
        case 7: // Deblur
            applyDeblurNative(px, info.width, info.height);
            break;
        case 8: // Vivid / Color Doc
            applyColorDocNative(px, info.width, info.height);
            break;
        case 9: // Blueprint / Dark Invert
            applyBlueprintInvertNative(px, info.width, info.height);
            break;
        case 10: // Eco Text
            applyEcoTextNative(px, info.width, info.height);
            break;
        case 11: // Stamp & Seal
            applyStampSealNative(px, info.width, info.height);
            break;
        case 12: // Warm Paper
            applyWarmPaperNative(px, info.width, info.height);
            break;
        default:
            break;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_com_krinry_scancraft_cv_NativeScannerEngine_adjustTones(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jint brightness,
    jint contrast
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    applyBrightnessContrastNative(static_cast<uint32_t*>(pixels), info.width, info.height, brightness, contrast);

    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT jfloatArray JNICALL
Java_com_krinry_scancraft_cv_NativeScannerEngine_snapPointInBitmap(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat ptX,
    jfloat ptY,
    jfloat searchRadius
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    bool isSnapped = false;
    Point2D snapped = snapPointToNearestEdge(
        static_cast<const uint32_t*>(pixels),
        info.width,
        info.height,
        ptX,
        ptY,
        searchRadius,
        isSnapped
    );

    AndroidBitmap_unlockPixels(env, bitmap);

    jfloatArray result = env->NewFloatArray(3);
    jfloat res[3] = {snapped.x, snapped.y, isSnapped ? 1.0f : 0.0f};
    env->SetFloatArrayRegion(result, 0, 3, res);
    return result;
}

} // extern "C"
