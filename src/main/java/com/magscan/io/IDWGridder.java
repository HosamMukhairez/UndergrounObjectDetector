package com.magscan.io;

import com.magscan.model.GridData;
import com.magscan.model.SurveyPoint;

import java.util.List;

/**
 * Converts scattered (x, y, value) sensor readings into a regular grid
 * using Inverse Distance Weighting (IDW). This is the standard first step
 * in geophysical survey processing: raw walked/dragged sensor tracks are
 * rarely on a perfect grid, so they must be interpolated onto one before
 * any image filter (which needs a regular pixel lattice) can be applied.
 */
public final class IDWGridder {

    private IDWGridder() {}

    public static GridData grid(List<SurveyPoint> points, int targetResolution, double power) {
        if (points.isEmpty()) throw new IllegalArgumentException("No points to grid.");

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (SurveyPoint p : points) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        double extentX = Math.max(maxX - minX, 1e-6);
        double extentY = Math.max(maxY - minY, 1e-6);
        double largestExtent = Math.max(extentX, extentY);

        double cellSize = largestExtent / Math.max(8, targetResolution);
        int nx = Math.max(2, (int) Math.round(extentX / cellSize) + 1);
        int ny = Math.max(2, (int) Math.round(extentY / cellSize) + 1);

        double[][] out = new double[ny][nx];
        int n = points.size();
        double[] px = new double[n], py = new double[n], pv = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = points.get(i).x;
            py[i] = points.get(i).y;
            pv[i] = points.get(i).value;
        }

        double smoothing = cellSize * cellSize * 0.01; // avoids singularities when a
                                                        // grid node sits exactly on a sample
        for (int row = 0; row < ny; row++) {
            double wy = minY + row * cellSize;
            for (int col = 0; col < nx; col++) {
                double wx = minX + col * cellSize;

                double weightSum = 0.0;
                double valueSum = 0.0;
                boolean exact = false;
                double exactValue = 0.0;

                for (int i = 0; i < n; i++) {
                    double dx = wx - px[i];
                    double dy = wy - py[i];
                    double d2 = dx * dx + dy * dy + smoothing;
                    double w = 1.0 / Math.pow(d2, power / 2.0);
                    weightSum += w;
                    valueSum += w * pv[i];
                    if (d2 < 1e-9) { exact = true; exactValue = pv[i]; }
                }
                out[row][col] = exact ? exactValue : (weightSum > 0 ? valueSum / weightSum : Double.NaN);
            }
        }

        return new GridData(nx, ny, cellSize, minX, minY, out);
    }
}
