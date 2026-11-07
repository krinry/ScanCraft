#include "native_scanner.h"

// Invert 3x3 matrix
static bool invert3x3(const double A[3][3], double inv[3][3]) {
    double det = A[0][0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1]) -
                 A[0][1] * (A[1][0] * A[2][2] - A[1][2] * A[2][0]) +
                 A[0][2] * (A[1][0] * A[2][1] - A[1][1] * A[2][0]);

    if (std::abs(det) < 1e-12) return false;
    double invdet = 1.0 / det;

    inv[0][0] = (A[1][1] * A[2][2] - A[1][2] * A[2][1]) * invdet;
    inv[0][1] = (A[0][2] * A[2][1] - A[0][1] * A[2][2]) * invdet;
    inv[0][2] = (A[0][1] * A[1][2] - A[0][2] * A[1][1]) * invdet;

    inv[1][0] = (A[1][2] * A[2][0] - A[1][0] * A[2][2]) * invdet;
    inv[1][1] = (A[0][0] * A[2][2] - A[0][2] * A[2][0]) * invdet;
    inv[1][2] = (A[1][0] * A[0][2] - A[0][0] * A[1][2]) * invdet;

    inv[2][0] = (A[1][0] * A[2][1] - A[2][0] * A[1][1]) * invdet;
    inv[2][1] = (A[2][0] * A[0][1] - A[0][0] * A[2][1]) * invdet;
    inv[2][2] = (A[0][0] * A[1][1] - A[1][0] * A[0][1]) * invdet;

    return true;
}

// Compute 3x3 homography matrix mapping rectangle (0,0)-(dstW, dstH) back to source quad
static bool computeHomography(int dstW, int dstH, const Quad& q, double H[3][3]) {
    // Mapping from (0,0)->TL, (dstW,0)->TR, (dstW,dstH)->BR, (0,dstH)->BL
    double u0 = 0, v0 = 0;
    double u1 = dstW, v1 = 0;
    double u2 = dstW, v2 = dstH;
    double u3 = 0, v3 = dstH;

    double dx1 = q.tr.x - q.br.x;
    double dx2 = q.bl.x - q.br.x;
    double dx3 = q.tl.x - q.tr.x + q.br.x - q.bl.x;

    double dy1 = q.tr.y - q.br.y;
    double dy2 = q.bl.y - q.br.y;
    double dy3 = q.tl.y - q.tr.y + q.br.y - q.bl.y;

    if (std::abs(dx3) < 1e-6 && std::abs(dy3) < 1e-6) {
        // Affine
        H[0][0] = (q.tr.x - q.tl.x) / dstW;
        H[0][1] = (q.bl.x - q.tl.x) / dstH;
        H[0][2] = q.tl.x;
        H[1][0] = (q.tr.y - q.tl.y) / dstW;
        H[1][1] = (q.bl.y - q.tl.y) / dstH;
        H[1][2] = q.tl.y;
        H[2][0] = 0;
        H[2][1] = 0;
        H[2][2] = 1;
        return true;
    }

    double d = dx1 * dy2 - dy1 * dx2;
    if (std::abs(d) < 1e-12) return false;

    double a13 = (dx3 * dy2 - dy3 * dx2) / d;
    double a23 = (dx1 * dy3 - dy1 * dx3) / d;

    H[0][0] = (q.tr.x - q.tl.x + a13 * q.tr.x) / dstW;
    H[0][1] = (q.bl.x - q.tl.x + a23 * q.bl.x) / dstH;
    H[0][2] = q.tl.x;

    H[1][0] = (q.tr.y - q.tl.y + a13 * q.tr.y) / dstW;
    H[1][1] = (q.bl.y - q.tl.y + a23 * q.bl.y) / dstH;
    H[1][2] = q.tl.y;

    H[2][0] = a13 / dstW;
    H[2][1] = a23 / dstH;
    H[2][2] = 1.0;

    return true;
}

void warpPerspectiveNative(
    const uint32_t* srcPixels, int srcW, int srcH,
    uint32_t* dstPixels, int dstW, int dstH,
    const Quad& quad
) {
    if (!srcPixels || !dstPixels || dstW <= 0 || dstH <= 0 || srcW <= 0 || srcH <= 0) return;

    double H[3][3];
    if (!computeHomography(dstW, dstH, quad, H)) return;

    for (int y = 0; y < dstH; ++y) {
        uint32_t* dstRow = dstPixels + y * dstW;
        double h01_y_h02 = H[0][1] * y + H[0][2];
        double h11_y_h12 = H[1][1] * y + H[1][2];
        double h21_y_h22 = H[2][1] * y + H[2][2];

        for (int x = 0; x < dstW; ++x) {
            double w = H[2][0] * x + h21_y_h22;
            if (std::abs(w) < 1e-10) continue;

            double invW = 1.0 / w;
            double sx = (H[0][0] * x + h01_y_h02) * invW;
            double sy = (H[1][0] * x + h11_y_h12) * invW;

            int isx = static_cast<int>(sx);
            int isy = static_cast<int>(sy);

            if (isx >= 0 && isx < srcW - 1 && isy >= 0 && isy < srcH - 1) {
                // Bilinear interpolation
                float fx = static_cast<float>(sx - isx);
                float fy = static_cast<float>(sy - isy);
                float w00 = (1.0f - fx) * (1.0f - fy);
                float w01 = fx * (1.0f - fy);
                float w10 = (1.0f - fx) * fy;
                float w11 = fx * fy;

                uint32_t p00 = srcPixels[isy * srcW + isx];
                uint32_t p01 = srcPixels[isy * srcW + (isx + 1)];
                uint32_t p10 = srcPixels[(isy + 1) * srcW + isx];
                uint32_t p11 = srcPixels[(isy + 1) * srcW + (isx + 1)];

                uint32_t a = 255;
                uint32_t r = static_cast<uint32_t>(((p00 >> 16) & 0xff) * w00 + ((p01 >> 16) & 0xff) * w01 + ((p10 >> 16) & 0xff) * w10 + ((p11 >> 16) & 0xff) * w11);
                uint32_t g = static_cast<uint32_t>(((p00 >> 8) & 0xff) * w00 + ((p01 >> 8) & 0xff) * w01 + ((p10 >> 8) & 0xff) * w10 + ((p11 >> 8) & 0xff) * w11);
                uint32_t b = static_cast<uint32_t>((p00 & 0xff) * w00 + (p01 & 0xff) * w01 + (p10 & 0xff) * w10 + (p11 & 0xff) * w11);

                dstRow[x] = (a << 24) | (r << 16) | (g << 8) | b;
            } else if (isx >= 0 && isx < srcW && isy >= 0 && isy < srcH) {
                dstRow[x] = srcPixels[isy * srcW + isx];
            } else {
                dstRow[x] = 0xFFFFFFFF; // white outside boundary
            }
        }
    }
}
