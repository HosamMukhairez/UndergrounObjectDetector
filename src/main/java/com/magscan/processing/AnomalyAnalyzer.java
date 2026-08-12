package com.magscan.processing;

import com.magscan.model.GridData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Measures the lateral footprint of an anomaly: thresholds the grid at a
 * fraction of its peak-above-baseline amplitude, flood-fills the connected
 * region touching a chosen seed cell, then uses that region's principal
 * axes (PCA on cell coordinates) to report a length (long axis - useful for
 * pipes/linear features), a width (short axis), an orientation, and area.
 */
public final class AnomalyAnalyzer {

    private AnomalyAnalyzer() {}

    public static final class Anomaly {
        public final int cellCount;
        public final double centroidX, centroidY;   // world coordinates
        public final double lengthMeters;           // extent along principal axis
        public final double widthMeters;            // extent along secondary axis
        public final double orientationDegrees;     // angle of principal axis from +X axis
        public final double areaSqMeters;
        public final double peakValue;

        Anomaly(int cellCount, double cx, double cy, double length, double width,
                double orientationDeg, double area, double peakValue) {
            this.cellCount = cellCount;
            this.centroidX = cx;
            this.centroidY = cy;
            this.lengthMeters = length;
            this.widthMeters = width;
            this.orientationDegrees = orientationDeg;
            this.areaSqMeters = area;
            this.peakValue = peakValue;
        }
    }

    /**
     * @param g            source grid (ideally already regional-trend-removed)
     * @param seedRow      row of a cell known to be inside the anomaly (e.g. the profile peak)
     * @param seedCol      column of a cell known to be inside the anomaly
     * @param thresholdFrac fraction (0-1) of the seed's peak-above-baseline amplitude used as the
     *                      flood-fill cutoff; 0.5 reproduces the same "half-max" boundary used
     *                      for depth estimation
     */
    public static Anomaly analyze(GridData g, int seedRow, int seedCol, double thresholdFrac) {
        double baseline = estimateBaseline(g);
        double seedValue = g.values[seedRow][seedCol];
        boolean positive = seedValue >= baseline;
        double cutoff = baseline + (seedValue - baseline) * thresholdFrac;

        boolean[][] visited = new boolean[g.ny][g.nx];
        List<int[]> cells = new ArrayList<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { seedRow, seedCol });
        visited[seedRow][seedCol] = true;

        int[] dr = { -1, 1, 0, 0 };
        int[] dc = { 0, 0, -1, 1 };

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int r = cell[0], c = cell[1];
            cells.add(cell);
            for (int k = 0; k < 4; k++) {
                int nr = r + dr[k], nc = c + dc[k];
                if (nr < 0 || nr >= g.ny || nc < 0 || nc >= g.nx || visited[nr][nc]) continue;
                double v = g.values[nr][nc];
                if (Double.isNaN(v)) continue;
                boolean inside = positive ? (v >= cutoff) : (v <= cutoff);
                if (inside) {
                    visited[nr][nc] = true;
                    queue.add(new int[] { nr, nc });
                }
            }
        }

        int n = cells.size();
        double sumX = 0, sumY = 0;
        double peak = seedValue;
        for (int[] cell : cells) {
            double x = g.worldX(cell[1]);
            double y = g.worldY(cell[0]);
            sumX += x; sumY += y;
            double v = g.values[cell[0]][cell[1]];
            if (positive ? v > peak : v < peak) peak = v;
        }
        double cx = sumX / n, cy = sumY / n;

        double sxx = 0, syy = 0, sxy = 0;
        for (int[] cell : cells) {
            double dx = g.worldX(cell[1]) - cx;
            double dy = g.worldY(cell[0]) - cy;
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
        }
        sxx /= n; syy /= n; sxy /= n;

        // Closed-form eigenvalues/eigenvector angle of the 2x2 covariance matrix.
        double trace = sxx + syy;
        double det = sxx * syy - sxy * sxy;
        double disc = Math.sqrt(Math.max(0, trace * trace / 4 - det));
        double lambda1 = trace / 2 + disc; // major
        double lambda2 = trace / 2 - disc; // minor
        double angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);

        // extent along each principal axis = max projection - min projection
        double cosA = Math.cos(angle), sinA = Math.sin(angle);
        double minMajor = Double.POSITIVE_INFINITY, maxMajor = Double.NEGATIVE_INFINITY;
        double minMinor = Double.POSITIVE_INFINITY, maxMinor = Double.NEGATIVE_INFINITY;
        for (int[] cell : cells) {
            double dx = g.worldX(cell[1]) - cx;
            double dy = g.worldY(cell[0]) - cy;
            double proj = dx * cosA + dy * sinA;
            double perp = -dx * sinA + dy * cosA;
            minMajor = Math.min(minMajor, proj); maxMajor = Math.max(maxMajor, proj);
            minMinor = Math.min(minMinor, perp); maxMinor = Math.max(maxMinor, perp);
        }

        double length = maxMajor - minMajor + g.cellSize; // +1 cell so single-row features aren't zero-length
        double width = maxMinor - minMinor + g.cellSize;
        double area = n * g.cellSize * g.cellSize;
        double orientationDeg = Math.toDegrees(angle);

        return new Anomaly(n, cx, cy, length, width, orientationDeg, area, peak);
    }

    /** Robust baseline: median of the grid, less sensitive to the anomaly itself than the mean. */
    private static double estimateBaseline(GridData g) {
        List<Double> vals = new ArrayList<>();
        for (double[] row : g.values) for (double v : row) if (!Double.isNaN(v)) vals.add(v);
        vals.sort(null);
        return vals.get(vals.size() / 2);
    }
}
