package com.magscan.view;

import com.magscan.model.GridData;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/**
 * Renders a GridData as a 2D color-mapped raster ("plan view" map), with a
 * color-scale legend, optional contour lines, live value readout on hover,
 * and click-to-pick a seed cell (used to drive anomaly length/width
 * analysis and profile extraction).
 */
public class Map2DPanel extends JPanel {

    public interface HoverListener {
        void onHover(double worldX, double worldY, double value, int row, int col);
    }
    public interface PickListener {
        void onPick(int row, int col);
    }

    private static final int LEGEND_WIDTH = 70;
    private static final int MARGIN = 30;

    private GridData grid;
    private double dataMin, dataMax;
    private boolean showContours = false;
    private int contourLevels = 8;
    private Integer highlightRow = null;   // profile row overlay
    private Integer highlightCol = null;   // profile column overlay
    private Integer pickedRow = null;
    private Integer pickedCol = null;

    private HoverListener hoverListener;
    private PickListener pickListener;

    public Map2DPanel() {
        setBackground(Color.DARK_GRAY);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { handlePointer(e.getX(), e.getY(), false); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handlePointer(e.getX(), e.getY(), true); }
        });
    }

    public void setGrid(GridData grid) {
        this.grid = grid;
        this.dataMin = grid.min();
        this.dataMax = grid.max();
        repaint();
    }

    public void setShowContours(boolean show) { this.showContours = show; repaint(); }
    public void setContourLevels(int levels) { this.contourLevels = Math.max(2, levels); repaint(); }

    public void setHighlightRow(Integer row) { this.highlightRow = row; this.highlightCol = null; repaint(); }
    public void setHighlightColumn(Integer col) { this.highlightCol = col; this.highlightRow = null; repaint(); }

    public void setHoverListener(HoverListener l) { this.hoverListener = l; }
    public void setPickListener(PickListener l) { this.pickListener = l; }

    private java.awt.Rectangle plotArea() {
        int w = getWidth() - LEGEND_WIDTH - MARGIN * 2;
        int h = getHeight() - MARGIN * 2;
        return new java.awt.Rectangle(MARGIN, MARGIN, Math.max(1, w), Math.max(1, h));
    }

    private void handlePointer(int px, int py, boolean isClick) {
        if (grid == null) return;
        java.awt.Rectangle area = plotArea();
        if (!area.contains(px, py)) return;
        double u = (px - area.x) / (double) area.width;
        double v = 1.0 - (py - area.y) / (double) area.height;
        int col = (int) Math.round(u * (grid.nx - 1));
        int row = (int) Math.round(v * (grid.ny - 1));
        col = Math.max(0, Math.min(grid.nx - 1, col));
        row = Math.max(0, Math.min(grid.ny - 1, row));

        if (isClick) {
            pickedRow = row; pickedCol = col;
            if (pickListener != null) pickListener.onPick(row, col);
            repaint();
        } else if (hoverListener != null) {
            hoverListener.onHover(grid.worldX(col), grid.worldY(row), grid.get(row, col), row, col);
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (grid == null) {
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("Load survey data to see the 2D map.", 20, 20);
            return;
        }

        java.awt.Rectangle area = plotArea();
        BufferedImage img = new BufferedImage(grid.nx, grid.ny, BufferedImage.TYPE_INT_RGB);
        double range = Math.max(1e-9, dataMax - dataMin);
        for (int r = 0; r < grid.ny; r++) {
            for (int c = 0; c < grid.nx; c++) {
                double v = grid.values[r][c];
                double n = Double.isNaN(v) ? 0 : (v - dataMin) / range;
                Color col = Double.isNaN(v) ? Color.GRAY : ColorScale.colorFor(n);
                // image row 0 = top = highest Y so the map reads north-up
                img.setRGB(c, grid.ny - 1 - r, col.getRGB());
            }
        }
        g.drawImage(img, area.x, area.y, area.width, area.height, null);

        if (showContours) drawContours(g, area);

        if (highlightRow != null) {
            int py = area.y + (int) Math.round(area.height * (1.0 - highlightRow / (double) Math.max(1, grid.ny - 1)));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(area.x, py, area.x + area.width, py);
        }
        if (highlightCol != null) {
            int px = area.x + (int) Math.round(area.width * (highlightCol / (double) Math.max(1, grid.nx - 1)));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(px, area.y, px, area.y + area.height);
        }
        if (pickedRow != null && pickedCol != null) {
            int px = area.x + (int) Math.round(area.width * (pickedCol / (double) Math.max(1, grid.nx - 1)));
            int py = area.y + (int) Math.round(area.height * (1.0 - pickedRow / (double) Math.max(1, grid.ny - 1)));
            g.setColor(Color.BLACK);
            g.fillOval(px - 5, py - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawOval(px - 5, py - 5, 10, 10);
        }

        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(area.x, area.y, area.width, area.height);
        g.drawString(String.format("X: %.1f m .. %.1f m", grid.worldX(0), grid.worldX(grid.nx - 1)),
                area.x, area.y + area.height + 18);

        drawLegend(g, area);
    }

    private void drawContours(Graphics2D g, java.awt.Rectangle area) {
        double range = Math.max(1e-9, dataMax - dataMin);
        g.setColor(new Color(0, 0, 0, 140));
        g.setStroke(new BasicStroke(1f));
        for (int level = 1; level < contourLevels; level++) {
            double threshold = dataMin + range * level / (double) contourLevels;
            for (int r = 0; r < grid.ny - 1; r++) {
                for (int c = 0; c < grid.nx - 1; c++) {
                    double v00 = grid.values[r][c];
                    double v10 = grid.values[r][c + 1];
                    double v01 = grid.values[r + 1][c];
                    drawSegmentIfCrossing(g, area, v00, v10, threshold, c, r, c + 1, r);
                    drawSegmentIfCrossing(g, area, v00, v01, threshold, c, r, c, r + 1);
                }
            }
        }
    }

    private void drawSegmentIfCrossing(Graphics2D g, java.awt.Rectangle area,
                                        double vA, double vB, double threshold,
                                        int colA, int rowA, int colB, int rowB) {
        if (Double.isNaN(vA) || Double.isNaN(vB)) return;
        if ((vA - threshold) * (vB - threshold) > 0) return;
        double t = (vB == vA) ? 0.5 : (threshold - vA) / (vB - vA);
        double col = colA + (colB - colA) * t;
        double row = rowA + (rowB - rowA) * t;
        int px = area.x + (int) Math.round(area.width * (col / (double) Math.max(1, grid.nx - 1)));
        int py = area.y + (int) Math.round(area.height * (1.0 - row / (double) Math.max(1, grid.ny - 1)));
        g.fillOval(px - 1, py - 1, 2, 2);
    }

    private void drawLegend(Graphics2D g, java.awt.Rectangle area) {
        int lx = area.x + area.width + 20;
        int ly = area.y;
        int lw = 20, lh = area.height;
        for (int i = 0; i < lh; i++) {
            double n = 1.0 - i / (double) lh;
            g.setColor(ColorScale.colorFor(n));
            g.drawLine(lx, ly + i, lx + lw, ly + i);
        }
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(lx, ly, lw, lh);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        g.drawString(String.format("%.1f", dataMax), lx + lw + 5, ly + 10);
        g.drawString(String.format("%.1f", dataMin), lx + lw + 5, ly + lh);
        g.drawString(String.format("%.1f", (dataMax + dataMin) / 2), lx + lw + 5, ly + lh / 2);
    }
}
