package com.magscan.io;

import com.magscan.model.SurveyPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads raw sensor log files. Accepts comma, tab, or whitespace separated
 * text with columns: X, Y, VALUE  (extra columns, e.g. a 4th "sensor height"
 * column, are ignored). A non-numeric first line is treated as a header
 * and skipped automatically.
 */
public final class CsvLoader {

    private CsvLoader() {}

    public static List<SurveyPoint> load(Reader reader) throws IOException {
        List<SurveyPoint> points = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            boolean first = true;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                String[] tokens = trimmed.split("[,;\\t ]+");
                if (tokens.length < 3) continue;

                try {
                    double x = Double.parseDouble(tokens[0]);
                    double y = Double.parseDouble(tokens[1]);
                    double v = Double.parseDouble(tokens[2]);
                    points.add(new SurveyPoint(x, y, v));
                } catch (NumberFormatException nfe) {
                    if (first) {
                        // header row, silently skip
                    } else {
                        System.err.println("Skipping unparsable line " + lineNo + ": " + line);
                    }
                }
                first = false;
            }
        }
        if (points.isEmpty()) {
            throw new IOException("No numeric (x, y, value) rows found in file.");
        }
        return points;
    }
}
