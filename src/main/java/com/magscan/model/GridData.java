package com.magscan.model;

/**
 * A regular (nx * ny) grid of scalar values in survey (world) coordinates.
 * Row index (0..ny-1) maps to Y, column index (0..nx-1) maps to X.
 * Cells with no data are stored as Double.NaN.
 *
 * This is the central data structure passed between the data loader,
 * every image-processing filter, and every view (2D map, 3D surface,
 * profile chart).
 */
public final class GridData {

    public final int nx;
    public final int ny;
    public final double cellSize;   // meters per cell (square cells)
    public final double originX;    // world X of column 0 (cell centers)
    public final double originY;    // world Y of row 0 (cell centers)
    public final double[][] values; // [row][col] -> [y][x]

    public GridData(int nx, int ny, double cellSize, double originX, double originY, double[][] values) {
        this.nx = nx;
        this.ny = ny;
        this.cellSize = cellSize;
        this.originX = originX;
        this.originY = originY;
        this.values = values;
    }

    /** Deep-copy constructor helper so filters never mutate the source grid. */
    public GridData copyShape() {
        double[][] copy = new double[ny][nx];
        return new GridData(nx, ny, cellSize, originX, originY, copy);
    }

    public double worldX(int col) {
        return originX + col * cellSize;
    }

    public double worldY(int row) {
        return originY + row * cellSize;
    }

    public double get(int row, int col) {
        if (row < 0 || row >= ny || col < 0 || col >= nx) return Double.NaN;
        return values[row][col];
    }

    /** Clamped fetch used by stencil filters at grid edges (replicate border). */
    public double getClamped(int row, int col) {
        int r = Math.max(0, Math.min(ny - 1, row));
        int c = Math.max(0, Math.min(nx - 1, col));
        double v = values[r][c];
        return Double.isNaN(v) ? 0.0 : v;
    }

    public double min() {
        double m = Double.POSITIVE_INFINITY;
        for (double[] row : values) for (double v : row) if (!Double.isNaN(v)) m = Math.min(m, v);
        return m;
    }

    public double max() {
        double m = Double.NEGATIVE_INFINITY;
        for (double[] row : values) for (double v : row) if (!Double.isNaN(v)) m = Math.max(m, v);
        return m;
    }
}
