package com.magscan.model;

/**
 * One raw reading from the magnetometer/gradiometer sensor:
 * a survey position (x, y in meters, local survey grid) and the
 * measured magnetic field / anomaly value (typically nanoTesla, nT).
 */
public final class SurveyPoint {

    public final double x;
    public final double y;
    public final double value;

    public SurveyPoint(double x, double y, double value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f) = %.3f", x, y, value);
    }
}
