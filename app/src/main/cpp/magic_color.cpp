#include "native_scanner.h"

// Clamp utility
static inline uint8_t clamp255(int val) {
    if (val < 0) return 0;
    if (val > 255) return 255;
    return static_cast<uint8_t>(val);
}

// Magic Color: Background whitening + local contrast enhancement + vivid text
void applyMagicColorNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;

    int total = width * height;
    std::vector<uint8_t> gray(total);
    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        uint8_t r = (p >> 16) & 0xff;
        uint8_t g = (p >> 8) & 0xff;
        uint8_t b = p & 0xff;
        gray[i] = static_cast<uint8_t>((r * 77 + g * 150 + b * 29) >> 8);
    }

    // Downsampled background illumination estimation
    int step = 8;
    int bw = (width + step - 1) / step;
    int bh = (height + step - 1) / step;
    std::vector<uint8_t> bg(bw * bh, 0);

    for (int by = 0; by < bh; ++by) {
        for (int bx = 0; bx < bw; ++bx) {
            uint8_t maxVal = 0;
            int startY = by * step;
            int endY = std::min(startY + step, height);
            int startX = bx * step;
            int endX = std::min(startX + step, width);
            for (int y = startY; y < endY; ++y) {
                for (int x = startX; x < endX; ++x) {
                    if (gray[y * width + x] > maxVal) {
                        maxVal = gray[y * width + x];
                    }
                }
            }
            bg[by * bw + bx] = std::max(maxVal, static_cast<uint8_t>(128));
        }
    }

    // Normalize each pixel by its local background estimate
    for (int y = 0; y < height; ++y) {
        int by = std::min(y / step, bh - 1);
        for (int x = 0; x < width; ++x) {
            int bx = std::min(x / step, bw - 1);
            float bgVal = static_cast<float>(bg[by * bw + bx]);

            uint32_t p = pixels[y * width + x];
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            // Normalize background to 255
            float factor = 255.0f / bgVal;
            r = static_cast<int>(r * factor);
            g = static_cast<int>(g * factor);
            b = static_cast<int>(b * factor);

            // S-curve contrast boost on text
            int lum = (r * 77 + g * 150 + b * 29) >> 8;
            if (lum < 160) {
                float darkFactor = (lum / 160.0f) * 0.85f;
                r = static_cast<int>(r * darkFactor);
                g = static_cast<int>(g * darkFactor);
                b = static_cast<int>(b * darkFactor);
            }

            pixels[y * width + x] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
        }
    }
}

// Bradley-Roth Fast Adaptive Thresholding for B&W documents
void applyBradleySauvolaBWNative(uint32_t* pixels, int width, int height, float k) {
    if (!pixels || width <= 0 || height <= 0) return;

    int total = width * height;
    std::vector<uint64_t> integral((width + 1) * (height + 1), 0);

    // Compute integral image
    for (int y = 0; y < height; ++y) {
        uint64_t sum = 0;
        for (int x = 0; x < width; ++x) {
            uint32_t p = pixels[y * width + x];
            uint8_t lum = static_cast<uint8_t>((((p >> 16) & 0xff) * 77 + ((p >> 8) & 0xff) * 150 + (p & 0xff) * 29) >> 8);
            sum += lum;
            integral[(y + 1) * (width + 1) + (x + 1)] = integral[y * (width + 1) + (x + 1)] + sum;
        }
    }

    int s = std::max(width, height) / 16; // window size
    int s2 = s / 2;
    float threshT = 1.0f - k;

    for (int y = 0; y < height; ++y) {
        int y1 = std::max(0, y - s2);
        int y2 = std::min(height - 1, y + s2);
        for (int x = 0; x < width; ++x) {
            int x1 = std::max(0, x - s2);
            int x2 = std::min(width - 1, x + s2);

            int count = (x2 - x1 + 1) * (y2 - y1 + 1);
            uint64_t sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)]
                         - integral[y1 * (width + 1) + (x2 + 1)]
                         - integral[(y2 + 1) * (width + 1) + x1]
                         + integral[y1 * (width + 1) + x1];

            uint32_t p = pixels[y * width + x];
            uint8_t lum = static_cast<uint8_t>((((p >> 16) & 0xff) * 77 + ((p >> 8) & 0xff) * 150 + (p & 0xff) * 29) >> 8);

            if (lum * count < sum * threshT) {
                pixels[y * width + x] = 0xFF000000; // Black text
            } else {
                pixels[y * width + x] = 0xFFFFFFFF; // Crisp white paper
            }
        }
    }
}

// Grayscale with contrast stretch
void applyGrayscaleNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    uint8_t minL = 255, maxL = 0;
    std::vector<uint8_t> lums(total);

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        uint8_t l = static_cast<uint8_t>((((p >> 16) & 0xff) * 77 + ((p >> 8) & 0xff) * 150 + (p & 0xff) * 29) >> 8);
        lums[i] = l;
        if (l < minL) minL = l;
        if (l > maxL) maxL = l;
    }

    float range = std::max(1, maxL - minL);
    for (int i = 0; i < total; ++i) {
        uint8_t stretched = clamp255(static_cast<int>((lums[i] - minL) * 255.0f / range));
        pixels[i] = 0xFF000000 | (stretched << 16) | (stretched << 8) | stretched;
    }
}

// Lighten: Whitens background paper while preserving text contrast
void applyLightenNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;

        int lum = (r * 77 + g * 150 + b * 29) >> 8;
        if (lum > 70) {
            // High boost on background and paper
            r = std::min(255, static_cast<int>(r * 1.25f + 20));
            g = std::min(255, static_cast<int>(g * 1.25f + 20));
            b = std::min(255, static_cast<int>(b * 1.25f + 20));
        } else {
            // Keep text dark and readable
            r = static_cast<int>(r * 0.9f);
            g = static_cast<int>(g * 0.9f);
            b = static_cast<int>(b * 0.9f);
        }

        pixels[i] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }
}

// Shadow Removal: coarse background grid estimation + Retinex shading correction
void applyNoShadowNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;

    int total = width * height;
    std::vector<uint8_t> lum(total);
    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        lum[i] = static_cast<uint8_t>((((p >> 16) & 0xff) * 77 + ((p >> 8) & 0xff) * 150 + (p & 0xff) * 29) >> 8);
    }

    // Estimate local background illumination using coarse grid
    int step = 16;
    int bw = (width + step - 1) / step;
    int bh = (height + step - 1) / step;
    std::vector<uint8_t> bg(bw * bh, 128);

    for (int by = 0; by < bh; ++by) {
        for (int bx = 0; bx < bw; ++bx) {
            int startY = by * step;
            int endY = std::min(startY + step, height);
            int startX = bx * step;
            int endX = std::min(startX + step, width);

            // Find 90th percentile luminance in the block to represent paper background
            std::vector<uint8_t> blockLums;
            blockLums.reserve((endY - startY) * (endX - startX));
            for (int y = startY; y < endY; ++y) {
                for (int x = startX; x < endX; ++x) {
                    blockLums.push_back(lum[y * width + x]);
                }
            }
            if (!blockLums.empty()) {
                std::sort(blockLums.begin(), blockLums.end());
                size_t p90 = blockLums.size() * 90 / 100;
                bg[by * bw + bx] = std::max(blockLums[p90], static_cast<uint8_t>(60));
            }
        }
    }

    // Bilinearly normalize pixel colors by background illumination
    for (int y = 0; y < height; ++y) {
        float fby = static_cast<float>(y) / step;
        int by0 = std::min(static_cast<int>(fby), bh - 1);
        int by1 = std::min(by0 + 1, bh - 1);
        float fy = fby - by0;

        for (int x = 0; x < width; ++x) {
            float fbx = static_cast<float>(x) / step;
            int bx0 = std::min(static_cast<int>(fbx), bw - 1);
            int bx1 = std::min(bx0 + 1, bw - 1);
            float fx = fbx - bx0;

            float bgEst = (1.0f - fx) * (1.0f - fy) * bg[by0 * bw + bx0]
                        + fx * (1.0f - fy) * bg[by0 * bw + bx1]
                        + (1.0f - fx) * fy * bg[by1 * bw + bx0]
                        + fx * fy * bg[by1 * bw + bx1];

            if (bgEst < 60.0f) bgEst = 60.0f;

            uint32_t p = pixels[y * width + x];
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            float factor = 245.0f / bgEst;
            r = static_cast<int>(r * factor);
            g = static_cast<int>(g * factor);
            b = static_cast<int>(b * factor);

            pixels[y * width + x] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
        }
    }
}

// Deblur / Sharp Text Filter: 3x3 Laplacian edge difference & unsharp masking
void applyDeblurNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width < 3 || height < 3) return;

    int total = width * height;
    std::vector<uint32_t> copy(pixels, pixels + total);

    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            uint32_t center = copy[y * width + x];
            int cr = (center >> 16) & 0xff;
            int cg = (center >> 8) & 0xff;
            int cb = center & 0xff;

            uint32_t top = copy[(y - 1) * width + x];
            uint32_t bot = copy[(y + 1) * width + x];
            uint32_t left = copy[y * width + (x - 1)];
            uint32_t right = copy[y * width + (x + 1)];

            // Laplacian edge difference: 4*center - (top + bot + left + right)
            int lapR = 4 * cr - (((top >> 16) & 0xff) + ((bot >> 16) & 0xff) + ((left >> 16) & 0xff) + ((right >> 16) & 0xff));
            int lapG = 4 * cg - (((top >> 8) & 0xff) + ((bot >> 8) & 0xff) + ((left >> 8) & 0xff) + ((right >> 8) & 0xff));
            int lapB = 4 * cb - ((top & 0xff) + (bot & 0xff) + (left & 0xff) + (right & 0xff));

            // Sharpness weight 0.65
            int nr = static_cast<int>(cr + 0.65f * lapR);
            int ng = static_cast<int>(cg + 0.65f * lapG);
            int nb = static_cast<int>(cb + 0.65f * lapB);

            pixels[y * width + x] = 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
        }
    }
}

// Vivid Color Document Filter: HSV saturation boost for ink/stamps and paper background whitening
void applyColorDocNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        float r = ((p >> 16) & 0xff) / 255.0f;
        float g = ((p >> 8) & 0xff) / 255.0f;
        float b = (p & 0xff) / 255.0f;

        float maxC = std::max({r, g, b});
        float minC = std::min({r, g, b});
        float delta = maxC - minC;

        float h = 0.0f, s = 0.0f, v = maxC;
        if (maxC > 1e-5f) {
            s = delta / maxC;
        }

        if (delta > 1e-5f) {
            if (maxC == r) {
                h = 60.0f * (fmod(((g - b) / delta), 6.0f));
            } else if (maxC == g) {
                h = 60.0f * (((b - r) / delta) + 2.0f);
            } else {
                h = 60.0f * (((r - g) / delta) + 4.0f);
            }
            if (h < 0.0f) h += 360.0f;
        }

        // Boost saturation for stamps and ink
        s = std::min(1.0f, s * 1.35f);
        // Brighten paper
        if (v > 0.65f) {
            v = std::min(1.0f, v * 1.15f + 0.05f);
        }

        // HSV back to RGB
        float c = v * s;
        float x = c * (1.0f - std::abs(fmod(h / 60.0f, 2.0f) - 1.0f));
        float m = v - c;

        float nr = 0, ng = 0, nb = 0;
        if (h < 60) { nr = c; ng = x; nb = 0; }
        else if (h < 120) { nr = x; ng = c; nb = 0; }
        else if (h < 180) { nr = 0; ng = c; nb = x; }
        else if (h < 240) { nr = 0; ng = x; nb = c; }
        else if (h < 300) { nr = x; ng = 0; nb = c; }
        else { nr = c; ng = 0; nb = x; }

        int ir = static_cast<int>((nr + m) * 255.0f);
        int ig = static_cast<int>((ng + m) * 255.0f);
        int ib = static_cast<int>((nb + m) * 255.0f);

        pixels[i] = 0xFF000000 | (clamp255(ir) << 16) | (clamp255(ig) << 8) | clamp255(ib);
    }
}

// Adjust Brightness & Contrast & Sharpness
void applyBrightnessContrastNative(uint32_t* pixels, int width, int height, int brightness, int contrast, int sharpness) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    float factor = (259.0f * (contrast + 255.0f)) / (255.0f * (259.0f - contrast));

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;

        r = static_cast<int>(factor * (r - 128) + 128 + brightness);
        g = static_cast<int>(factor * (g - 128) + 128 + brightness);
        b = static_cast<int>(factor * (b - 128) + 128 + brightness);

        pixels[i] = 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    if (sharpness > 0) {
        applyDeblurNative(pixels, width, height);
    }
}

// Blueprint Invert Filter: Negative inverted document (crisp white text on dark blueprint/slate)
void applyBlueprintInvertNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;

        int lum = (r * 77 + g * 150 + b * 29) >> 8;
        // Invert luminance
        int inv = 255 - lum;

        int nr, ng, nb;
        if (inv < 120) {
            // Dark blueprint background tone
            nr = static_cast<int>(inv * 0.12f);
            ng = static_cast<int>(inv * 0.20f);
            nb = static_cast<int>(inv * 0.35f + 16);
        } else {
            // High contrast text
            float boost = (inv - 120) / 135.0f;
            nr = clamp255(static_cast<int>(200 + boost * 55));
            ng = clamp255(static_cast<int>(220 + boost * 35));
            nb = 255;
        }

        pixels[i] = 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
    }
}

// Eco Text Filter: Ultra-clean ink-saver binarization with isolated speckle reduction
void applyEcoTextNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    applyBradleySauvolaBWNative(pixels, width, height, 0.14f);
}

// Stamp & Seal Filter: Isolates and saturates official red/blue stamps and signatures
void applyStampSealNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;

        float fr = r / 255.0f;
        float fg = g / 255.0f;
        float fb = b / 255.0f;

        float maxC = std::max({fr, fg, fb});
        float minC = std::min({fr, fg, fb});
        float delta = maxC - minC;

        float sat = (maxC > 1e-4f) ? (delta / maxC) : 0.0f;

        // Check for colored ink (red seal or blue stamp)
        bool isRedInk = (r > 110) && (r > g * 1.35f) && (r > b * 1.35f);
        bool isBlueInk = (b > 100) && (b > r * 1.25f) && (b > g * 1.15f);

        if (isRedInk) {
            // Punch up red stamp
            int nr = std::min(255, static_cast<int>(r * 1.35f + 15));
            int ng = static_cast<int>(g * 0.75f);
            int nb = static_cast<int>(b * 0.75f);
            pixels[i] = 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
        } else if (isBlueInk) {
            // Punch up blue ink / signature
            int nr = static_cast<int>(r * 0.70f);
            int ng = static_cast<int>(g * 0.85f);
            int nb = std::min(255, static_cast<int>(b * 1.35f + 20));
            pixels[i] = 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
        } else {
            // Regular text or paper
            int lum = (r * 77 + g * 150 + b * 29) >> 8;
            if (lum < 140) {
                // Crisp deep black text
                int dark = static_cast<int>(lum * 0.65f);
                pixels[i] = 0xFF000000 | (dark << 16) | (dark << 8) | dark;
            } else {
                // Pure clean white paper
                pixels[i] = 0xFFFFFFFF;
            }
        }
    }
}

// Warm Paper Filter: Eye-care soft parchment tone with softened contrast
void applyWarmPaperNative(uint32_t* pixels, int width, int height) {
    if (!pixels || width <= 0 || height <= 0) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xff;
        int g = (p >> 8) & 0xff;
        int b = p & 0xff;

        int lum = (r * 77 + g * 150 + b * 29) >> 8;
        if (lum > 140) {
            // Warm parchment background
            int wr = std::min(255, lum + 10);
            int wg = std::min(255, lum + 4);
            int wb = std::max(0, lum - 14);
            pixels[i] = 0xFF000000 | (clamp255(wr) << 16) | (clamp255(wg) << 8) | clamp255(wb);
        } else {
            // Softened charcoal ink
            int dark = std::max(20, static_cast<int>(lum * 0.85f));
            pixels[i] = 0xFF000000 | ((dark + 4) << 16) | (dark << 8) | std::max(0, dark - 4);
        }
    }
}
