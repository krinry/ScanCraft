#ifndef THORVG_BRIDGE_H
#define THORVG_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeInit(JNIEnv* env, jclass clazz);

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
);

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderSmoothVectorStroke(
    JNIEnv* env,
    jclass clazz,
    jobject bitmap,
    jfloatArray points,
    jfloat strokeWidth,
    jint r,
    jint g,
    jint b,
    jint a,
    jboolean isCubicSmoothed
);

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
);

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderStyledShape(
    JNIEnv* env,
    jclass clazz,
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
);

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeRenderSvg(
    JNIEnv* env,
    jclass clazz,
    jobject bitmap,
    jstring svgString,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height,
    jfloat angle,
    jfloat opacity
);

JNIEXPORT jboolean JNICALL
Java_com_krinry_scancraft_cv_ThorvgEngine_nativeApplySmudgeBlur(
    JNIEnv* env,
    jclass clazz,
    jobject bitmap,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint radius
);

#ifdef __cplusplus
}
#endif

#endif // THORVG_BRIDGE_H
