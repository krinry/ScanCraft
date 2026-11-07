#ifndef _TVG_CONFIG_H_
#define _TVG_CONFIG_H_

#define THORVG_VERSION_STRING "1.1.3"
#define THORVG_THREAD_SUPPORT 1
#define THORVG_CPU_ENGINE_SUPPORT 1
#define THORVG_SVG_LOADER_SUPPORT 1

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#define THORVG_NEON_SUPPORT 1
#endif

#if defined(__x86_64__) && defined(__AVX2__)
#define THORVG_AVX_SUPPORT 1
#endif

#endif // _TVG_CONFIG_H_
