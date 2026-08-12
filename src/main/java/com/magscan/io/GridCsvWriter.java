package com.magscan.io;

import com.magscan.model.GridData;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/** Writes a GridData back out as an (x, y, value) CSV, e.g. after applying filters. */
public final class GridCsvWriter {

    private GridCsvWriter() {}

    public static void write(GridData grid, Writer writer, String valueColumnName) throws IOException {
        try (PrintWriter pw = new PrintWriter(writer)) {
            pw.println("x_m,y_m," + valueColumnName);
            for (int r = 0; r < grid.ny; r++) {
                double y = grid.worldY(r);
                for (int c = 0; c < grid.nx; c++) {
                    double x = grid.worldX(c);
                    double v = grid.values[r][c];
                    if (Double.isNaN(v)) continue;
                    pw.printf("%.4f,%.4f,%.6f%n", x, y, v);
                }
            }
        }
    }
}
