package com.magscan.view;

import com.magscan.processing.DepthEstimator;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;

/** Draws a single survey profile line plus the half-width depth-estimate diagnostics. */
public class ProfileChartPanel extends JPanel {

    private double[] profile;
    private double cellSize;
    private DepthEstimator.Result result;

    private static final int MARGIN = 40;

    public ProfileChartPanel() {
        setBackground(Color.WHITE);
    }

    public void setData(double[] profile, double cellSize, DepthEstimator.Result result) {
        this.profile = profile;
        this.cellSize = cellSize;
        this.result = result;
        repaint();
    }

    public void clear() {
        this.profile = null;
        this.result = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (profile == null || profile.length < 2) {
            g.setColor(Color.GRAY);
            g.drawString("Click a point on the 2D map to extract a profile and estimate depth.", 20, 20);
            return;
        }

        int w = getWidth(), h = getHeight();
        int plotW = w - 2 * MARGIN, plotH = h - 2 * MARGIN;

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : profile) { min = Math.min(min, v); max = Math.max(max, v); }
        double range = Math.max(1e-9, max - min);

        int n = profile.length;
        java.awt.geom.Point2D[] pts = new java.awt.geom.Point2D[n];
        for (int i = 0; i < n; i++) {
            double px = MARGIN + plotW * (i / (double) (n - 1));
            double py = MARGIN + plotH * (1 - (profile[i] - min) / range);
            pts[i] = new java.awt.geom.Point2D.Double(px, py);
        }

        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(MARGIN, MARGIN, plotW, plotH);

        if (result != null) {
            double baselineY = MARGIN + plotH * (1 - (result.baseline - min) / range);
            g.setColor(new Color(0, 0, 0, 90));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {4, 4}, 0));
            g.draw(new Line2D.Double(MARGIN, baselineY, MARGIN + plotW, baselineY));

            double halfAmp = result.baseline + (result.peakValue - result.baseline) / 2.0;
            double halfY = MARGIN + plotH * (1 - (halfAmp - min) / range);
            g.setColor(new Color(200, 80, 0, 160));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(MARGIN, halfY, MARGIN + plotW, halfY));

            double peakX = MARGIN + plotW * (result.peakIndex / (double) (n - 1));
            g.setColor(Color.RED);
            g.fillOval((int) peakX - 4, (int) (MARGIN + plotH * (1 - (result.peakValue - min) / range)) - 4, 8, 8);
        }

        g.setColor(new Color(20, 90, 200));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < n - 1; i++) {
            g.draw(new Line2D.Double(pts[i], pts[i + 1]));
        }

        g.setColor(Color.DARK_GRAY);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
        g.drawString(String.format("distance along profile (m), cell = %.2f m", cellSize), MARGIN, h - 12);

        if (result != null) {
            String summary = String.format(
                "Peak %.2f | baseline %.2f | half-width %.2f m | model: %s | estimated depth ~ %.2f m",
                result.peakValue, result.baseline, result.halfWidthMeters, result.model.label, result.estimatedDepthMeters);
            g.setColor(Color.BLACK);
            g.drawString(summary, MARGIN, 20);
        }
    }
}
