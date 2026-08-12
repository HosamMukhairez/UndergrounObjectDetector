package com.magscan.processing;

import com.magscan.model.GridData;

/**
 * Quick-look depth-to-source estimation using the classic "half-width rule"
 * from magnetic interpretation (see e.g. Telford, Geldart & Sheriff,
 * "Applied Geophysics"): for a simple, isolated anomaly, the depth to the
 * top of the causative body is approximately proportional to the width of
 * the anomaly measured at half of its peak amplitude. The proportionality
 * constant (shape factor) depends on the assumed source geometry.
 *
 * This is a fast field/first-pass estimate, not a substitute for full
 * forward modelling or inversion - real bodies rarely match the idealized
 * shapes exactly, and depth accuracy depends on how isolated the anomaly is
 * from its neighbors.
 */
public final class DepthEstimator {

    private DepthEstimator() {}

    public enum SourceModel {
        SPHERE("Compact object (sphere / drum / UXO-like)", 1.305),
        HORIZONTAL_CYLINDER("Elongated object (pipe / cylinder)", 1.0),
        THIN_SHEET("Thin near-vertical sheet / dike / wall footing", 0.5);

        public final String label;
        public final double shapeFactor;

        SourceModel(String label, double shapeFactor) {
            this.label = label;
            this.shapeFactor = shapeFactor;
        }

        @Override public String toString() { return label; }
    }

    public static final class Result {
        public final int peakIndex;
        public final double peakValue;
        public final double baseline;
        public final double halfWidthMeters;
        public final double estimatedDepthMeters;
        public final SourceModel model;

        Result(int peakIndex, double peakValue, double baseline, double halfWidthMeters,
               double estimatedDepthMeters, SourceModel model) {
            this.peakIndex = peakIndex;
            this.peakValue = peakValue;
            this.baseline = baseline;
            this.halfWidthMeters = halfWidthMeters;
            this.estimatedDepthMeters = estimatedDepthMeters;
            this.model = model;
        }
    }

    /** Extracts one row of the grid as a 1D profile. */
    public static double[] extractRow(GridData g, int row) {
        double[] out = new double[g.nx];
        System.arraycopy(g.values[row], 0, out, 0, g.nx);
        return out;
    }

    /** Extracts one column of the grid as a 1D profile. */
    public static double[] extractColumn(GridData g, int col) {
        double[] out = new double[g.ny];
        for (int r = 0; r < g.ny; r++) out[r] = g.values[r][col];
        return out;
    }

    /**
     * Estimates depth from a 1D profile. Baseline is taken as the average of
     * the profile's two endpoints (a simple local background estimate); the
     * peak is the point of maximum absolute deviation from that baseline.
     */
    public static Result estimate(double[] profile, double cellSize, SourceModel model) {
        int n = profile.length;
        if (n < 3) throw new IllegalArgumentException("Profile too short for depth estimation.");

        double baseline = (profile[0] + profile[n - 1]) / 2.0;

        int peakIndex = 0;
        double bestDeviation = -1;
        for (int i = 0; i < n; i++) {
            double dev = Math.abs(profile[i] - baseline);
            if (dev > bestDeviation) { bestDeviation = dev; peakIndex = i; }
        }
        double peakValue = profile[peakIndex];
        double halfAmp = baseline + (peakValue - baseline) / 2.0;
        boolean positiveAnomaly = peakValue >= baseline;

        double leftCrossing = findCrossing(profile, peakIndex, -1, halfAmp, positiveAnomaly);
        double rightCrossing = findCrossing(profile, peakIndex, +1, halfAmp, positiveAnomaly);

        double halfWidthCells;
        if (!Double.isNaN(leftCrossing) && !Double.isNaN(rightCrossing)) {
            halfWidthCells = (rightCrossing - leftCrossing) / 2.0;
        } else if (!Double.isNaN(leftCrossing)) {
            halfWidthCells = peakIndex - leftCrossing;
        } else if (!Double.isNaN(rightCrossing)) {
            halfWidthCells = rightCrossing - peakIndex;
        } else {
            halfWidthCells = n / 4.0; // fallback: anomaly wider than the profile window
        }

        double halfWidthMeters = halfWidthCells * cellSize;
        double depth = halfWidthMeters * model.shapeFactor;

        return new Result(peakIndex, peakValue, baseline, halfWidthMeters, depth, model);
    }

    /** Walks from the peak in direction dir (+1/-1) until the profile crosses halfAmp; returns fractional index. */
    private static double findCrossing(double[] profile, int peakIndex, int dir, double halfAmp, boolean positiveAnomaly) {
        int i = peakIndex;
        while (i + dir >= 0 && i + dir < profile.length) {
            double a = profile[i];
            double b = profile[i + dir];
            boolean crossed = positiveAnomaly ? (a >= halfAmp && b < halfAmp) : (a <= halfAmp && b > halfAmp);
            if (crossed) {
                double t = (halfAmp - a) / (b - a);
                return i + dir * t;
            }
            i += dir;
        }
        return Double.NaN;
    }
}
