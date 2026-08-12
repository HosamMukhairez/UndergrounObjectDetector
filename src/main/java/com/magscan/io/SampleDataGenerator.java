package com.magscan.io;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Random;

/**
 * Generates a synthetic survey CSV so the application can be exercised
 * end-to-end without real sensor hardware. It simulates a walked
 * magnetometer survey (line spacing 0.5 m, station spacing 0.25 m) over a
 * 30 x 20 m block containing two known buried targets:
 *
 *   - a compact object (sphere/UXO-like source) at (10, 14), depth 1.5 m
 *   - an elongated linear object (pipe/cylinder) running north-south at
 *     x = 22, depth 1.0 m
 *
 * Because the true depths/positions are known, this is useful for
 * sanity-checking the depth-estimation and edge-detection filters.
 */
public final class SampleDataGenerator {

    private SampleDataGenerator() {}

    public static void writeSampleFile(Path out) throws IOException {
        double width = 30.0, height = 20.0;
        double lineSpacing = 0.5, stationSpacing = 0.25;
        double regionalGradient = 0.4;  // nT per meter, simulates diurnal/regional drift
        double noiseStd = 1.5;          // nT, simulates sensor + geologic noise
        Random rnd = new Random(42);

        // target 1: compact "sphere" style dipole source
        double t1x = 10, t1y = 14, t1z = 1.5, t1m = 4000;
        // target 2: elongated "pipe" along y at x = 22
        double t2x = 22, t2z = 1.0, t2m = 1600;

        try (PrintWriter pw = new PrintWriter(out.toFile())) {
            pw.println("# x_m,y_m,total_field_nT  (synthetic magnetometer survey)");
            for (double x = 0; x <= width; x += stationSpacing) {
                for (double y = 0; y <= height; y += lineSpacing) {
                    double dx1 = x - t1x, dy1 = y - t1y;
                    double r1sq = dx1 * dx1 + dy1 * dy1;
                    double sphere = t1m * (2 * t1z * t1z - r1sq) / Math.pow(r1sq + t1z * t1z, 2.5);

                    double perp = x - t2x; // perpendicular distance to buried pipe axis
                    double pipe = t2m * (t2z * t2z - perp * perp) / Math.pow(perp * perp + t2z * t2z, 2.0);

                    double background = 50000 + regionalGradient * x;
                    double noise = rnd.nextGaussian() * noiseStd;

                    double total = background + sphere + pipe + noise;
                    pw.printf("%.3f,%.3f,%.3f%n", x, y, total);
                }
            }
        }
    }
}
