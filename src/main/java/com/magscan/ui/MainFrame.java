package com.magscan.ui;

import com.magscan.io.CsvLoader;
import com.magscan.io.GridCsvWriter;
import com.magscan.io.IDWGridder;
import com.magscan.io.SampleDataGenerator;
import com.magscan.model.GridData;
import com.magscan.model.SurveyPoint;
import com.magscan.processing.AnomalyAnalyzer;
import com.magscan.processing.DepthEstimator;
import com.magscan.processing.Filters;
import com.magscan.processing.PotentialFieldTransforms;
import com.magscan.view.Map2DPanel;
import com.magscan.view.Map3DPanel;
import com.magscan.view.ProfileChartPanel;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ButtonGroup;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainFrame extends JFrame {

    private GridData rawGrid;
    private GridData currentGrid;

    private final Map2DPanel map2D = new Map2DPanel();
    private final Map3DPanel map3D = new Map3DPanel();
    private final ProfileChartPanel profileChart = new ProfileChartPanel();
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("No data loaded. Use File > Open Survey Data, or File > Load Sample Data.");
    private final JComboBox<DepthEstimator.SourceModel> modelCombo =
            new JComboBox<>(DepthEstimator.SourceModel.values());
    private final JRadioButton rowProfileRadio = new JRadioButton("Row (E-W)", true);
    private final JRadioButton colProfileRadio = new JRadioButton("Column (N-S)");
    private final JTabbedPane tabs = new JTabbedPane();

    private int pickedRow = -1, pickedCol = -1;

    public MainFrame() {
        super("MagScan - Underground Magnetic Survey Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());

        JPanel root = new JPanel(new BorderLayout());
        tabs.addTab("2D Map", map2D);
        tabs.addTab("3D Surface", map3D);
        tabs.addTab("Profile & Depth", buildProfileTab());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, buildSidePanel());
        split.setResizeWeight(1.0);
        split.setDividerLocation(940);

        root.add(split, BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.SOUTH);
        setContentPane(root);

        map2D.setHoverListener((wx, wy, value, row, col) ->
                statusLabel.setText(String.format("X=%.2f m  Y=%.2f m  value=%.3f  (row %d, col %d)", wx, wy, value, row, col)));
        map2D.setPickListener(this::onPick);
    }

    // ----------------------------------------------------------------
    // layout builders
    // ----------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Open Survey Data (CSV/TXT)...", e -> openFile()));
        file.add(menuItem("Load Synthetic Sample Data", e -> loadSampleData()));
        file.addSeparator();
        file.add(menuItem("Save Current Grid As CSV...", e -> saveGridCsv()));
        file.add(menuItem("Export Current View As PNG...", e -> exportViewPng()));
        file.addSeparator();
        file.add(menuItem("Exit", e -> System.exit(0)));
        bar.add(file);

        JMenu filters = new JMenu("Filters");
        filters.add(menuItem("Reset To Raw Gridded Data", e -> resetToRaw()));
        filters.addSeparator();
        filters.add(menuItem("Remove Regional Trend (Detrend)", e -> applyFilter(Filters::removeRegionalTrend, "Removed regional trend")));
        filters.add(menuItem("Gaussian Smoothing...", e -> gaussianDialog()));
        filters.addSeparator();
        filters.add(menuItem("Sobel Edge Detection", e -> applyFilter(Filters::sobelEdges, "Applied Sobel edge detector")));
        filters.add(menuItem("Laplacian Edge Detection", e -> applyFilter(Filters::laplacian, "Applied Laplacian edge detector")));
        filters.addSeparator();
        filters.add(menuItem("Vertical Derivative (Fourier)", e -> applyFilter(PotentialFieldTransforms::verticalDerivative, "Applied Fourier vertical derivative")));
        filters.add(menuItem("Upward Continuation...", e -> continuationDialog()));
        filters.add(menuItem("Analytic Signal Amplitude (Outline Detector)", e -> applyFilter(PotentialFieldTransforms::analyticSignalAmplitude, "Computed analytic signal amplitude")));
        bar.add(filters);

        JMenu help = new JMenu("Help");
        help.add(menuItem("About MagScan", e -> JOptionPane.showMessageDialog(this,
                "MagScan - underground magnetic survey viewer\n" +
                "2D/3D visualization + edge/depth analysis for magnetometer survey data.\n" +
                "Depth and length estimates are quick-look approximations based on classic\n" +
                "half-width rules; they are not a substitute for full geophysical inversion.")));
        bar.add(help);

        return bar;
    }

    private JMenuItem menuItem(String label, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(action);
        return item;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BorderLayout());
        side.setPreferredSize(new Dimension(320, 100));

        JPanel controls = new JPanel();
        controls.setLayout(new GridLayout(0, 1, 4, 6));
        controls.setBorder(BorderFactory.createTitledBorder("Display"));

        JCheckBox contourBox = new JCheckBox("Show contour lines");
        contourBox.addActionListener(e -> map2D.setShowContours(contourBox.isSelected()));
        controls.add(contourBox);

        JSlider exaggeration = new JSlider(1, 40, 8);
        exaggeration.setBorder(BorderFactory.createTitledBorder("3D vertical exaggeration"));
        exaggeration.addChangeListener(e -> map3D.setHeightExaggeration(exaggeration.getValue()));
        controls.add(exaggeration);

        JPanel analysis = new JPanel();
        analysis.setLayout(new BorderLayout(4, 4));
        analysis.setBorder(BorderFactory.createTitledBorder("Anomaly Analysis"));

        JPanel top = new JPanel(new GridLayout(0, 1, 2, 2));
        top.add(new JLabel("Assumed buried-object shape:"));
        top.add(modelCombo);

        ButtonGroup axisGroup = new ButtonGroup();
        axisGroup.add(rowProfileRadio);
        axisGroup.add(colProfileRadio);
        JPanel axisPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        axisPanel.add(new JLabel("Profile direction:"));
        axisPanel.add(rowProfileRadio);
        axisPanel.add(colProfileRadio);
        rowProfileRadio.addActionListener(e -> refreshProfile());
        colProfileRadio.addActionListener(e -> refreshProfile());
        top.add(axisPanel);

        JButton analyzeExtent = new JButton("Analyze Anomaly Length/Width");
        analyzeExtent.addActionListener(e -> analyzeExtent());
        top.add(analyzeExtent);

        analysis.add(top, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(300, 260));
        analysis.add(logScroll, BorderLayout.CENTER);

        side.add(controls, BorderLayout.NORTH);
        side.add(analysis, BorderLayout.CENTER);
        return side;
    }

    private JPanel buildProfileTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(profileChart, BorderLayout.CENTER);
        JLabel hint = new JLabel(" Click any point on the 2D Map tab to pick a target; its profile and depth estimate will appear here.");
        p.add(hint, BorderLayout.NORTH);
        return p;
    }

    // ----------------------------------------------------------------
    // data loading
    // ----------------------------------------------------------------

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open raw sensor survey file (x, y, value columns)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try (FileReader reader = new FileReader(file)) {
            List<SurveyPoint> points = CsvLoader.load(reader);
            gridAndDisplay(points, "Loaded " + points.size() + " readings from " + file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load file:\n" + ex.getMessage(),
                    "Load error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSampleData() {
        try {
            Path tmp = Files.createTempFile("magscan_sample", ".csv");
            SampleDataGenerator.writeSampleFile(tmp);
            try (FileReader reader = new FileReader(tmp.toFile())) {
                List<SurveyPoint> points = CsvLoader.load(reader);
                gridAndDisplay(points, "Loaded synthetic sample survey (" + points.size() +
                        " readings) containing a known compact target near (10,14) at 1.5 m depth " +
                        "and a known pipe-like target at x=22 at 1.0 m depth.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not generate sample data:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void gridAndDisplay(List<SurveyPoint> points, String message) {
        rawGrid = IDWGridder.grid(points, 180, 2.0);
        currentGrid = rawGrid;
        updateViews();
        log(message + String.format("  ->  gridded to %d x %d cells (%.3f m/cell).",
                rawGrid.nx, rawGrid.ny, rawGrid.cellSize));
        pickedRow = pickedCol = -1;
        profileChart.clear();
    }

    // ----------------------------------------------------------------
    // filters
    // ----------------------------------------------------------------

    private interface GridOp { GridData apply(GridData g); }

    private void applyFilter(GridOp op, String label) {
        if (currentGrid == null) { warnNoData(); return; }
        currentGrid = op.apply(currentGrid);
        updateViews();
        log(label + ".");
    }

    private void resetToRaw() {
        if (rawGrid == null) { warnNoData(); return; }
        currentGrid = rawGrid;
        updateViews();
        log("Reset view to raw gridded data.");
    }

    private void gaussianDialog() {
        if (currentGrid == null) { warnNoData(); return; }
        String input = JOptionPane.showInputDialog(this,
                "Gaussian smoothing sigma, in grid cells:", "1.5");
        if (input == null) return;
        try {
            double sigma = Double.parseDouble(input.trim());
            applyFilter(g -> Filters.gaussianBlur(g, sigma), "Applied Gaussian smoothing (sigma=" + sigma + " cells)");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a numeric sigma value.", "Invalid input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void continuationDialog() {
        if (currentGrid == null) { warnNoData(); return; }
        String input = JOptionPane.showInputDialog(this,
                "Upward continuation height, in meters (smooths/simulates viewing from higher above ground):", "2.0");
        if (input == null) return;
        try {
            double height = Double.parseDouble(input.trim());
            applyFilter(g -> PotentialFieldTransforms.upwardContinuation(g, height),
                    "Applied upward continuation (" + height + " m)");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a numeric height in meters.", "Invalid input", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ----------------------------------------------------------------
    // picking / profile / depth / extent
    // ----------------------------------------------------------------

    private void onPick(int row, int col) {
        pickedRow = row; pickedCol = col;
        refreshProfile();
    }

    private void refreshProfile() {
        if (currentGrid == null || pickedRow < 0 || pickedCol < 0) return;
        boolean useRow = rowProfileRadio.isSelected();
        double[] profile = useRow ? DepthEstimator.extractRow(currentGrid, pickedRow)
                                   : DepthEstimator.extractColumn(currentGrid, pickedCol);
        DepthEstimator.SourceModel model = (DepthEstimator.SourceModel) modelCombo.getSelectedItem();
        DepthEstimator.Result result = DepthEstimator.estimate(profile, currentGrid.cellSize, model);
        profileChart.setData(profile, currentGrid.cellSize, result);

        if (useRow) map2D.setHighlightRow(pickedRow); else map2D.setHighlightColumn(pickedCol);
        tabs.setSelectedIndex(2);

        log(String.format("Profile at (%.2f, %.2f): peak %.2f, half-width %.2f m, model=%s -> estimated depth ~ %.2f m",
                currentGrid.worldX(pickedCol), currentGrid.worldY(pickedRow),
                result.peakValue, result.halfWidthMeters, model.label, result.estimatedDepthMeters));
    }

    private void analyzeExtent() {
        if (currentGrid == null || pickedRow < 0 || pickedCol < 0) {
            JOptionPane.showMessageDialog(this, "Click a point on the 2D map first to select the anomaly to measure.",
                    "No point selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            AnomalyAnalyzer.Anomaly a = AnomalyAnalyzer.analyze(currentGrid, pickedRow, pickedCol, 0.5);
            log(String.format(
                "Anomaly extent at (%.2f, %.2f): length %.2f m, width %.2f m, orientation %.1f deg, area %.1f sq m, cells=%d",
                a.centroidX, a.centroidY, a.lengthMeters, a.widthMeters, a.orientationDegrees, a.areaSqMeters, a.cellCount));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not analyze extent:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------------------
    // export
    // ----------------------------------------------------------------

    private void saveGridCsv() {
        if (currentGrid == null) { warnNoData(); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save current (possibly filtered) grid as CSV");
        chooser.setSelectedFile(new File("magscan_grid.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
            GridCsvWriter.write(currentGrid, writer, "value");
            log("Saved current grid to " + chooser.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save file:\n" + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportViewPng() {
        Component view = tabs.getSelectedComponent();
        if (view == null || view.getWidth() == 0) { warnNoData(); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export current view as PNG");
        chooser.setSelectedFile(new File("magscan_view.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_ARGB);
            view.paint(img.getGraphics());
            ImageIO.write(img, "png", chooser.getSelectedFile());
            log("Exported view to " + chooser.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not export image:\n" + ex.getMessage(),
                    "Export error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private void updateViews() {
        map2D.setGrid(currentGrid);
        map3D.setGrid(currentGrid);
    }

    private void warnNoData() {
        JOptionPane.showMessageDialog(this, "Load survey data first (File > Open Survey Data, or File > Load Sample Data).",
                "No data", JOptionPane.INFORMATION_MESSAGE);
    }

    private void log(String message) {
        logArea.append(message + "\n\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
