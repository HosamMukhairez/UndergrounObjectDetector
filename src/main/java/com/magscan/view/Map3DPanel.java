package com.magscan.view;

import com.magscan.model.GridData;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders the grid as a rotatable/zoomable 3D surface where surface height
 * encodes the magnetic value. Implemented with plain Java2D (no external 3D
 * library): each grid quad is rotated with a standard azimuth/elevation
 * rotation matrix, projected orthographically, and painted back-to-front
 * (painter's algorithm), which is stable for a single-valued height field
 * like a survey grid.
 *
 * Controls: drag with the mouse to orbit, mouse wheel to zoom.
 */
public class Map3DPanel extends JPanel {

    private GridData grid;
    private double dataMin, dataMax, dataMean;

    private double azimuth = Math.toRadians(35);
    private double elevation = Math.toRadians(35);
    private double zoom = 1.0;
    private double heightExaggeration = 8.0;

    private int lastMouseX, lastMouseY;

    private static final int MAX_RENDER_CELLS = 130; // downsample large grids for smooth interaction

    public Map3DPanel() {
        setBackground(Color.BLACK);
        MouseAdapter orbit = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { lastMouseX = e.getX(); lastMouseY = e.getY(); }
        };
        addMouseListener(orbit);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                azimuth += Math.toRadians(dx * 0.4);
                elevation -= Math.toRadians(dy * 0.4);
                elevation = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), elevation));
                lastMouseX = e.getX(); lastMouseY = e.getY();
                repaint();
            }
        });
        addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                zoom *= Math.pow(1.1, -e.getWheelRotation());
                zoom = Math.max(0.2, Math.min(6.0, zoom));
                repaint();
            }
        });
    }

    public void setGrid(GridData grid) {
        this.grid = grid;
        this.dataMin = grid.min();
        this.dataMax = grid.max();
        double sum = 0; int n = 0;
        for (double[] row : grid.values) for (double v : row) if (!Double.isNaN(v)) { sum += v; n++; }
        this.dataMean = n == 0 ? 0 : sum / n;
        repaint();
    }

    public void setHeightExaggeration(double factor) { this.heightExaggeration = factor; repaint(); }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (grid == null) {
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("Load survey data to see the 3D surface.", 20, 20);
            return;
        }

        int strideX = Math.max(1, grid.nx / MAX_RENDER_CELLS);
        int strideY = Math.max(1, grid.ny / MAX_RENDER_CELLS);

        double range = Math.max(1e-9, dataMax - dataMin);
        double worldSpan = Math.max(grid.nx, grid.ny) * grid.cellSize;
        double scale = (Math.min(getWidth(), getHeight()) / worldSpan) * 0.8 * zoom;
        double heightScale = scale * heightExaggeration / Math.max(1e-9, range);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        List<Quad> quads = new ArrayList<>();

        for (int r = 0; r + strideY < grid.ny; r += strideY) {
            for (int c = 0; c + strideX < grid.nx; c += strideX) {
                double[] p00 = project(r, c, cx, cy, scale, heightScale);
                double[] p01 = project(r, c + strideX, cx, cy, scale, heightScale);
                double[] p10 = project(r + strideY, c, cx, cy, scale, heightScale);
                double[] p11 = project(r + strideY, c + strideX, cx, cy, scale, heightScale);

                double avgVal = avgOf(r, c, strideY, strideX);
                double norm = (avgVal - dataMin) / range;
                double depth = (p00[2] + p01[2] + p10[2] + p11[2]) / 4.0;
                quads.add(new Quad(p00, p01, p11, p10, ColorScale.colorFor(norm), depth));
            }
        }

        quads.sort(Comparator.comparingDouble(q -> q.depth));

        for (Quad q : quads) {
            Polygon poly = new Polygon();
            poly.addPoint((int) q.a[0], (int) q.a[1]);
            poly.addPoint((int) q.b[0], (int) q.b[1]);
            poly.addPoint((int) q.c[0], (int) q.c[1]);
            poly.addPoint((int) q.d[0], (int) q.d[1]);
            g.setColor(q.color);
            g.fillPolygon(poly);
            g.setColor(q.color.darker());
            g.drawPolygon(poly);
        }

        g.setColor(Color.LIGHT_GRAY);
        g.drawString("Drag to orbit - scroll to zoom - vertical exaggeration x" +
                String.format("%.1f", heightExaggeration), 10, getHeight() - 10);
    }

    private double avgOf(int r, int c, int strideY, int strideX) {
        double sum = 0; int n = 0;
        for (int dr = 0; dr <= strideY; dr++) {
            for (int dc = 0; dc <= strideX; dc++) {
                int rr = Math.min(grid.ny - 1, r + dr);
                int cc = Math.min(grid.nx - 1, c + dc);
                double v = grid.values[rr][cc];
                if (!Double.isNaN(v)) { sum += v; n++; }
            }
        }
        return n == 0 ? dataMean : sum / n;
    }

    /** Rotates+projects grid cell (row, col) to screen space; returns {screenX, screenY, depth}. */
    private double[] project(int row, int col, int cx, int cy, double scale, double heightScale) {
        double x = (grid.worldX(col) - (grid.worldX(0) + grid.worldX(grid.nx - 1)) / 2.0);
        double y = (grid.worldY(row) - (grid.worldY(0) + grid.worldY(grid.ny - 1)) / 2.0);
        double value = grid.values[Math.min(grid.ny - 1, row)][Math.min(grid.nx - 1, col)];
        if (Double.isNaN(value)) value = dataMean;
        double z = (value - dataMean);

        double xs = x * scale;
        double ys = y * scale;
        double zs = z * heightScale;

        // rotate about Z (azimuth)
        double x1 = xs * Math.cos(azimuth) - ys * Math.sin(azimuth);
        double y1 = xs * Math.sin(azimuth) + ys * Math.cos(azimuth);
        double z1 = zs;

        // rotate about X (elevation)
        double y2 = y1 * Math.cos(elevation) - z1 * Math.sin(elevation);
        double z2 = y1 * Math.sin(elevation) + z1 * Math.cos(elevation);

        double screenX = cx + x1;
        double screenY = cy - z2;
        double depth = y2; // used only for painter's-algorithm ordering

        return new double[] { screenX, screenY, depth };
    }

    private static final class Quad {
        final double[] a, b, c, d;
        final Color color;
        final double depth;
        Quad(double[] a, double[] b, double[] c, double[] d, Color color, double depth) {
            this.a = a; this.b = b; this.c = c; this.d = d; this.color = color; this.depth = depth;
        }
    }
}
