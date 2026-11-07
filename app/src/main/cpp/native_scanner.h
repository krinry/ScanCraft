#ifndef OPENSCANNER_NATIVE_SCANNER_H
#define OPENSCANNER_NATIVE_SCANNER_H

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>

#define LOG_TAG "OpenScannerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct Point2D {
    float x;
    float y;
};

struct Quad {
    union { Point2D tl; Point2D topLeft; };
    union { Point2D tr; Point2D topRight; };
    union { Point2D br; Point2D bottomRight; };
    union { Point2D bl; Point2D bottomLeft; };
};

// C++ Algorithm prototypes for document processing pipeline
Quad detectDocumentBounds(const uint8_t* grayBuffer, int width, int height);
Point2D snapPointToNearestEdge(
    const uint32_t* argbPixels,
    int width,
    int height,
    float ptX,
    float ptY,
    float searchRadius,
    bool& outSnapped
);
void warpPerspectiveNative(
    const uint32_t* srcPixels, int srcW, int srcH,
    uint32_t* dstPixels, int dstW, int dstH,
    const Quad& quad
);
void applyMagicColorNative(uint32_t* pixels, int width, int height);
void applyBradleySauvolaBWNative(uint32_t* pixels, int width, int height, float k = 0.2f);
void applyGrayscaleNative(uint32_t* pixels, int width, int height);
void applyLightenNative(uint32_t* pixels, int width, int height);
void applyNoShadowNative(uint32_t* pixels, int width, int height);
void applyDeblurNative(uint32_t* pixels, int width, int height);
void applyColorDocNative(uint32_t* pixels, int width, int height);
void applyBlueprintInvertNative(uint32_t* pixels, int width, int height);
void applyEcoTextNative(uint32_t* pixels, int width, int height);
void applyStampSealNative(uint32_t* pixels, int width, int height);
void applyWarmPaperNative(uint32_t* pixels, int width, int height);
void applyBrightnessContrastNative(uint32_t* pixels, int width, int height, int brightness, int contrast, int sharpness = 0);

#endif // OPENSCANNER_NATIVE_SCANNER_H
