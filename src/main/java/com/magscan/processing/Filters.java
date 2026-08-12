package com.magscan.processing;

import com.magscan.model.GridData;

/**
 * Classic space-domain image-processing filters applied directly to the
 * gridded magnetic data.
 */
public final class Filters {

    private Filters() {}

    /** Separable Gaussian smoothing, sigma given in grid cells. Suppresses sensor noise. */
    public static GridData gaussianBlur(GridData g, double sigmaCells) {
        if (sigmaCells <= 0) return g;
        int radius = Math.max(1, (int) Math.ceil(sigmaCells * 3));
        double[] kernel = new double[2 * radius + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            double w = Math.exp(-(i * i) / (2.0 * sigmaCells * sigmaCells));
            kernel[i + radius] = w;
            sum += w;
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;

        GridData horiz = g.copyShape();
        for (int r = 0; r < g.ny; r++) {
            for (int c = 0; c < g.nx; c++) {
                double acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    acc += kernel[k + radius] * g.getClamped(r, c + k);
                }
                horiz.values[r][c] = acc;
            }
        }

        GridData out = g.copyShape();
        for (int r = 0; r < g.ny; r++) {
            for (int c = 0; c < g.nx; c++) {
                double acc = 0;
                for (int k = -radius; k <= radius; k++) {
                    int rr = Math.max(0, Math.min(g.ny - 1, r + k));
                    acc += kernel[k + radius] * horiz.values[rr][c];
                }
                out.values[r][c] = acc;
            }
        }
        return out;
    }

    /** Sobel gradient-magnitude edge detector: highlights the boundaries of anomalies. */
    public static GridData sobelEdges(GridData g) {
        GridData out = g.copyShape();
        double[][] kx = { {-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1} };
        double[][] ky = { {-1, -2, -1}, {0, 0, 0}, {1, 2, 1} };
        for (int r = 0; r < g.ny; r++) {
            for (int c = 0; c < g.nx; c++) {
                double gx = 0, gy = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        double v = g.getClamped(r + dr, c + dc);
                        gx += kx[dr + 1][dc + 1] * v;
                        gy += ky[dr + 1][dc + 1] * v;
                    }
                }
                out.values[r][c] = Math.sqrt(gx * gx + gy * gy);
            }
        }
        return out;
    }

    /**
     * Discrete Laplacian (second derivative) edge detector. Zero-crossings of
     * this filter sit over the edges of an anomaly, similar to the classic
     * Marr-Hildreth edge scheme, and are commonly used on gradient data
     * because they are less biased by a source's magnetization direction
     * than a plain gradient magnitude.
     */
    public static GridData laplacian(GridData g) {
        GridData out = g.copyShape();
        double h2 = g.cellSize * g.cellSize;
        for (int r = 0; r < g.ny; r++) {
            for (int c = 0; c < g.nx; c++) {
                double center = g.getClamped(r, c);
                double lap = (g.getClamped(r, c - 1) + g.getClamped(r, c + 1)
                            + g.getClamped(r - 1, c) + g.getClamped(r + 1, c)
                            - 4 * center) / h2;
                out.values[r][c] = lap;
            }
        }
        return out;
    }

    /**
     * Regional-residual separation: fits a best-fit plane (least squares) to
     * the whole grid and subtracts it. This removes broad regional/diurnal
     * trends so that only the local anomalies from near-surface buried
     * objects remain - a standard first preprocessing step before any edge
     * or depth analysis.
     */
    public static GridData removeRegionalTrend(GridData g) {
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0, sz = 0, sxz = 0, syz = 0;
        int n = 0;
        for (int r = 0; r < g.ny; r++) {
            double y = g.worldY(r);
            for (int c = 0; c < g.nx; c++) {
                double v = g.values[r][c];
                if (Double.isNaN(v)) continue;
                double x = g.worldX(c);
                sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
                sz += v; sxz += x * v; syz += y * v;
                n++;
            }
        }
        if (n < 3) return g;

        // Solve normal equations for z = a*x + b*y + c via 3x3 linear system.
        double[][] A = {
            { sxx, sxy, sx },
            { sxy, syy, sy },
            { sx,  sy,  n  }
        };
        double[] b = { sxz, syz, sz };
        double[] coeffs = solve3x3(A, b);
        double a = coeffs[0], bb = coeffs[1], c0 = coeffs[2];

        GridData out = g.copyShape();
        for (int r = 0; r < g.ny; r++) {
            double y = g.worldY(r);
            for (int c = 0; c < g.nx; c++) {
                double v = g.values[r][c];
                if (Double.isNaN(v)) { out.values[r][c] = Double.NaN; continue; }
                double x = g.worldX(c);
                double plane = a * x + bb * y + c0;
                out.values[r][c] = v - plane;
            }
        }
        return out;
    }

    private static double[] solve3x3(double[][] A, double[] b) {
        // Gaussian elimination with partial pivoting on a 3x3 system.
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(A[i], 0, m[i], 0, 3);
            m[i][3] = b[i];
        }
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
            }
            double[] tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp;
            double pv = m[col][col];
            if (Math.abs(pv) < 1e-12) continue;
            for (int row = 0; row < 3; row++) {
                if (row == col) continue;
                double factor = m[row][col] / pv;
                for (int k = col; k < 4; k++) m[row][k] -= factor * m[col][k];
            }
        }
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = Math.abs(m[i][i]) > 1e-12 ? m[i][3] / m[i][i] : 0.0;
        }
        return result;
    }
}
