package com.magscan.view;

import java.awt.Color;

/**
 * Maps a normalized value in [0, 1] to a color using a multi-stop gradient
 * (deep blue -> cyan -> green -> yellow -> red), the same style of palette
 * used by most geophysical mapping software for magnetic anomaly maps.
 */
public final class ColorScale {

    private static final Color[] STOPS = {
        new Color(0, 0, 90),
        new Color(0, 0, 220),
        new Color(0, 160, 255),
        new Color(0, 220, 200),
        new Color(60, 220, 60),
        new Color(230, 230, 0),
        new Color(255, 140, 0),
        new Color(220, 0, 0)
    };

    private ColorScale() {}

    public static Color colorFor(double normalized) {
        double t = Math.max(0, Math.min(1, normalized));
        double scaled = t * (STOPS.length - 1);
        int idx = (int) Math.floor(scaled);
        if (idx >= STOPS.length - 1) return STOPS[STOPS.length - 1];
        double frac = scaled - idx;
        Color a = STOPS[idx], b = STOPS[idx + 1];
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * frac);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * frac);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * frac);
        return new Color(r, g, bl);
    }
}
