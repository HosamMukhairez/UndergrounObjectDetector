package com.magscan.processing;

import com.magscan.model.GridData;

/**
 * Frequency-domain filters used in real magnetic survey interpretation.
 * The magnetic field above buried sources satisfies Laplace's equation, so
 * several extremely useful operators (vertical derivative, upward/downward
 * continuation, analytic signal) are only clean to express as a function
 * of horizontal wavenumber k = sqrt(kx^2 + ky^2):
 *
 *   F[ d/dz  T ](k)  =  |k|      * F[T](k)          vertical derivative
 *   F[ cont  T ](k)  =  exp(-|k|*h) * F[T](k)       upward continuation by h
 *   dT/dx, dT/dy      =  (i*kx), (i*ky) * F[T](k)   horizontal derivatives
 *
 * The Analytic Signal Amplitude, |A| = sqrt((dT/dx)^2 + (dT/dy)^2 + (dT/dz)^2),
 * is the classic Nabighian/Roest-Verhoef-Pilkington edge detector: its
 * maxima sit directly over the edges of a buried source regardless of the
 * direction the source is magnetized in, which is exactly what "find the
 * outline of the buried object" needs.
 *
 * Grids are zero-padded to the next power of two (required by the FFT) and
 * edge-tapered first to suppress the ringing that a hard edge would
 * otherwise inject into the spectrum.
 */
public final class PotentialFieldTransforms {

    private PotentialFieldTransforms() {}

    @FunctionalInterface
    private interface WavenumberResponse {
        /** returns {realPart, imagPart} of the filter's response at (kx, ky) */
        double[] apply(double kx, double ky);
    }

    public static GridData verticalDerivative(GridData g) {
        return applyFilter(g, (kx, ky) -> new double[] { Math.sqrt(kx * kx + ky * ky), 0.0 });
    }

    public static GridData upwardContinuation(GridData g, double heightMeters) {
        return applyFilter(g, (kx, ky) -> {
            double k = Math.sqrt(kx * kx + ky * ky);
            return new double[] { Math.exp(-k * heightMeters), 0.0 };
        });
    }

    /** Downward continuation amplifies noise quickly; heightMeters should be small. */
    public static GridData downwardContinuation(GridData g, double heightMeters) {
        return applyFilter(g, (kx, ky) -> {
            double k = Math.sqrt(kx * kx + ky * ky);
            double clampedK = Math.min(k, 4.0 / g.cellSize); // crude anti-blowup guard
            return new double[] { Math.exp(clampedK * heightMeters), 0.0 };
        });
    }

    private static GridData derivativeX(GridData g) {
        return applyFilter(g, (kx, ky) -> new double[] { 0.0, kx });
    }

    private static GridData derivativeY(GridData g) {
        return applyFilter(g, (kx, ky) -> new double[] { 0.0, ky });
    }

    public static GridData analyticSignalAmplitude(GridData g) {
        GridData dx = derivativeX(g);
        GridData dy = derivativeY(g);
        GridData dz = verticalDerivative(g);
        GridData out = g.copyShape();
        for (int r = 0; r < g.ny; r++) {
            for (int c = 0; c < g.nx; c++) {
                double a = dx.values[r][c], b = dy.values[r][c], cz = dz.values[r][c];
                out.values[r][c] = Math.sqrt(a * a + b * b + cz * cz);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------
    // core FFT plumbing
    // ---------------------------------------------------------------

    private static GridData applyFilter(GridData g, WavenumberResponse response) {
        int nx = g.nx, ny = g.ny;
        int p = FFT.nextPowerOfTwo(2 * Math.max(nx, ny));

        double mean = mean(g);
        double[][] re = new double[p][p];
        double[][] im = new double[p][p];

        double taperFrac = 0.1;
        for (int r = 0; r < ny; r++) {
            for (int c = 0; c < nx; c++) {
                double v = g.values[r][c];
                if (Double.isNaN(v)) v = mean;
                double w = taper(c, nx, taperFrac) * taper(r, ny, taperFrac);
                re[r][c] = (v - mean) * w;
            }
        }

        fft2D(re, im, false, p);

        for (int r = 0; r < p; r++) {
            double ky = wavenumber(r, p, g.cellSize);
            for (int c = 0; c < p; c++) {
                double kx = wavenumber(c, p, g.cellSize);
                double[] resp = response.apply(kx, ky);
                double fr = resp[0], fi = resp[1];
                double vr = re[r][c], vi = im[r][c];
                re[r][c] = vr * fr - vi * fi;
                im[r][c] = vr * fi + vi * fr;
            }
        }

        fft2D(re, im, true, p);

        GridData out = g.copyShape();
        for (int r = 0; r < ny; r++) {
            for (int c = 0; c < nx; c++) {
                out.values[r][c] = re[r][c];
            }
        }
        return out;
    }

    private static double taper(int index, int size, double fraction) {
        int edge = (int) Math.round(size * fraction);
        if (edge <= 0) return 1.0;
        if (index < edge) {
            double t = (double) index / edge;
            return 0.5 * (1 - Math.cos(Math.PI * t));
        }
        if (index >= size - edge) {
            double t = (double) (size - 1 - index) / edge;
            return 0.5 * (1 - Math.cos(Math.PI * t));
        }
        return 1.0;
    }

    private static double wavenumber(int index, int size, double cellSize) {
        int shifted = index <= size / 2 ? index : index - size;
        return 2.0 * Math.PI * shifted / (size * cellSize);
    }

    private static double mean(GridData g) {
        double sum = 0; int n = 0;
        for (double[] row : g.values) {
            for (double v : row) {
                if (!Double.isNaN(v)) { sum += v; n++; }
            }
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private static void fft2D(double[][] re, double[][] im, boolean inverse, int p) {
        // rows
        for (int r = 0; r < p; r++) {
            FFT.transform(re[r], im[r], inverse);
        }
        // columns
        double[] colRe = new double[p];
        double[] colIm = new double[p];
        for (int c = 0; c < p; c++) {
            for (int r = 0; r < p; r++) { colRe[r] = re[r][c]; colIm[r] = im[r][c]; }
            FFT.transform(colRe, colIm, inverse);
            for (int r = 0; r < p; r++) { re[r][c] = colRe[r]; im[r][c] = colIm[r]; }
        }
    }
}
