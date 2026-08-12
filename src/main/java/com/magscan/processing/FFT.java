package com.magscan.processing;

/**
 * Minimal iterative radix-2 Cooley-Tukey complex FFT, operating in place on
 * parallel real/imaginary arrays. Array length must be a power of two.
 * Used to move gridded magnetic data into the wavenumber domain so that
 * vertical-derivative and continuation operators (which are only clean to
 * express as functions of wavenumber) can be applied.
 */
public final class FFT {

    private FFT() {}

    /** In-place FFT (inverse=false) or inverse FFT (inverse=true, includes 1/N scaling). */
    public static void transform(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        if (n == 0) return;
        if ((n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be a power of two, got " + n);
        }

        // bit-reversal permutation
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }

        double sign = inverse ? 1.0 : -1.0;
        for (int len = 2; len <= n; len <<= 1) {
            double ang = sign * 2 * Math.PI / len;
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1.0, curIm = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = i + k + len / 2;
                    double uRe = re[a], uIm = im[a];
                    double vRe = re[b] * curRe - im[b] * curIm;
                    double vIm = re[b] * curIm + im[b] * curRe;
                    re[a] = uRe + vRe; im[a] = uIm + vIm;
                    re[b] = uRe - vRe; im[b] = uIm - vIm;
                    double nextRe = curRe * wRe - curIm * wIm;
                    double nextIm = curRe * wIm + curIm * wRe;
                    curRe = nextRe; curIm = nextIm;
                }
            }
        }

        if (inverse) {
            for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
        }
    }

    public static int nextPowerOfTwo(int v) {
        int p = 1;
        while (p < v) p <<= 1;
        return p;
    }
}
