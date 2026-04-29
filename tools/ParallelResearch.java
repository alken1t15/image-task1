import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.GrayImage;
import image.ImageUtils;
import parallel.ParallelConvolution;
import parallel.ParallelMedianFilter;
import parallel.ParallelStrategy;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParallelResearch {
    private static final Path OUTPUT_DIR = Path.of("research", "task2");
    private static final Path CSV_PATH = OUTPUT_DIR.resolve("parallel_results.csv");

    private static final String[] FILTERS = {
            "gaussian3",
            "gaussian5",
            "blur5",
            "sharpen3",
            "motion9",
            "median3",
            "median5"
    };

    private static final int[] THREADS = {1, 2, 4, 8};
    private static final ParallelStrategy[] STRATEGIES = {
            ParallelStrategy.PIXELS,
            ParallelStrategy.ROWS,
            ParallelStrategy.COLUMNS,
            ParallelStrategy.GRID
    };

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Files.createDirectories(OUTPUT_DIR);

        if (args.length > 0 && args[0].equals("plot-only")) {
            List<Result> results = readResults(CSV_PATH);
            drawCharts(results);
            return;
        }

        List<Path> images = List.of(
                Path.of("D:\\temp\\256.png"),
                Path.of("D:\\temp\\512.jpg"),
                Path.of("D:\\temp\\1024.jpg"),
                Path.of("D:\\temp\\2048.jpg")
        );

        List<Result> results = new ArrayList<>();
        try (BufferedWriter writer = Files.newBufferedWriter(CSV_PATH, StandardCharsets.UTF_8)) {
            writer.write("image,width,height,filter,strategy,threads,iterations,avg_ms,throughput_mpix_s,speedup_vs_sequential");
            writer.newLine();

            for (Path imagePath : images) {
                GrayImage image = ImageUtils.loadGray(imagePath.toString());
                int iterations = iterationsFor(image);
                int warmups = 2;

                for (String filter : FILTERS) {
                    Result sequential = measureSequential(imagePath, image, filter, iterations, warmups);
                    sequential.speedup = 1.0;
                    results.add(sequential);
                    writeResult(writer, sequential);
                    System.out.printf("baseline %s %s %.3f ms%n", imagePath.getFileName(), filter, sequential.avgMs);

                    for (ParallelStrategy strategy : STRATEGIES) {
                        for (int threads : THREADS) {
                            Result result = measureParallel(imagePath, image, filter, strategy, threads, iterations, warmups);
                            result.speedup = sequential.avgMs / result.avgMs;
                            results.add(result);
                            writeResult(writer, result);
                            System.out.printf(
                                    "%s %s %s %d %.3f ms speedup %.2f%n",
                                    imagePath.getFileName(),
                                    filter,
                                    strategy.name().toLowerCase(Locale.ROOT),
                                    threads,
                                    result.avgMs,
                                    result.speedup
                            );
                        }
                    }
                }
            }
        }

        drawCharts(results);
    }

    private static void drawCharts(List<Result> results) throws IOException {
        drawTimeByThreads(results, OUTPUT_DIR.resolve("parallel_time_by_threads.png"));
        drawSpeedupByThreads(results, OUTPUT_DIR.resolve("parallel_speedup_by_threads.png"));
        drawStrategyComparison(results, OUTPUT_DIR.resolve("parallel_strategy_comparison.png"));
        drawThroughputByImage(results, OUTPUT_DIR.resolve("parallel_throughput_by_image.png"));
    }

    private static List<Result> readResults(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<Result> results = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            Result result = new Result(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    parts[3],
                    parts[4],
                    Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]),
                    Double.parseDouble(parts[7]),
                    Double.parseDouble(parts[8]),
                    0
            );
            result.speedup = Double.parseDouble(parts[9]);
            results.add(result);
        }
        return results;
    }

    private static int iterationsFor(GrayImage image) {
        int pixels = image.width * image.height;
        if (pixels <= 256 * 256) {
            return 30;
        }
        if (pixels <= 512 * 512) {
            return 20;
        }
        if (pixels <= 1024 * 1024) {
            return 12;
        }
        return 6;
    }

    private static Result measureSequential(Path imagePath, GrayImage image, String filter, int iterations, int warmups) {
        for (int i = 0; i < warmups; i++) {
            applySequential(image, filter);
        }

        long totalNs = 0;
        int checksum = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            GrayImage output = applySequential(image, filter);
            totalNs += System.nanoTime() - start;
            checksum += output.data[i % output.data.length] & 0xFF;
        }

        return result(imagePath, image, filter, "sequential", 1, iterations, totalNs, checksum);
    }

    private static Result measureParallel(
            Path imagePath,
            GrayImage image,
            String filter,
            ParallelStrategy strategy,
            int threads,
            int iterations,
            int warmups
    ) {
        for (int i = 0; i < warmups; i++) {
            applyParallel(image, filter, strategy, threads);
        }

        long totalNs = 0;
        int checksum = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            GrayImage output = applyParallel(image, filter, strategy, threads);
            totalNs += System.nanoTime() - start;
            checksum += output.data[i % output.data.length] & 0xFF;
        }

        return result(
                imagePath,
                image,
                filter,
                strategy.name().toLowerCase(Locale.ROOT),
                threads,
                iterations,
                totalNs,
                checksum
        );
    }

    private static Result result(
            Path imagePath,
            GrayImage image,
            String filter,
            String strategy,
            int threads,
            int iterations,
            long totalNs,
            int checksum
    ) {
        double avgNs = (double) totalNs / iterations;
        double avgMs = avgNs / 1_000_000.0;
        double mpix = (double) image.width * image.height / 1_000_000.0;
        double throughput = mpix / (avgNs / 1_000_000_000.0);
        return new Result(
                imagePath.getFileName().toString(),
                image.width,
                image.height,
                filter,
                strategy,
                threads,
                iterations,
                avgMs,
                throughput,
                checksum
        );
    }

    private static GrayImage applySequential(GrayImage image, String filter) {
        if (filter.startsWith("median")) {
            return MedianFilter.apply(image, parseMedianWindow(filter));
        }
        Kernel kernel = Kernels.byName(filter);
        return Convolution.apply(image, kernel);
    }

    private static GrayImage applyParallel(GrayImage image, String filter, ParallelStrategy strategy, int threads) {
        if (filter.startsWith("median")) {
            return ParallelMedianFilter.apply(image, parseMedianWindow(filter), strategy, threads);
        }
        Kernel kernel = Kernels.byName(filter);
        return ParallelConvolution.apply(image, kernel, strategy, threads);
    }

    private static int parseMedianWindow(String filter) {
        return switch (filter) {
            case "median3" -> 3;
            case "median5" -> 5;
            default -> throw new IllegalArgumentException("Unknown median filter: " + filter);
        };
    }

    private static void writeResult(BufferedWriter writer, Result result) throws IOException {
        writer.write(String.format(
                Locale.US,
                "%s,%d,%d,%s,%s,%d,%d,%.6f,%.6f,%.6f",
                result.image,
                result.width,
                result.height,
                result.filter,
                result.strategy,
                result.threads,
                result.iterations,
                result.avgMs,
                result.throughput,
                result.speedup
        ));
        writer.newLine();
        writer.flush();
    }

    private static void drawTimeByThreads(List<Result> results, Path output) throws IOException {
        List<Series> series = new ArrayList<>();
        for (ParallelStrategy strategy : STRATEGIES) {
            String name = strategy.name().toLowerCase(Locale.ROOT);
            series.add(new Series(displayStrategy(name), pointsFor(results, "2048.jpg", "gaussian5", name, Metric.TIME)));
        }
        drawLineChart(
                output,
                "Среднее время gaussian5 на 2048x2048",
                "Количество потоков",
                "Время, мс",
                series,
                THREADS
        );
    }

    private static void drawSpeedupByThreads(List<Result> results, Path output) throws IOException {
        List<Series> series = new ArrayList<>();
        for (ParallelStrategy strategy : STRATEGIES) {
            String name = strategy.name().toLowerCase(Locale.ROOT);
            series.add(new Series(displayStrategy(name), pointsFor(results, "2048.jpg", "gaussian5", name, Metric.SPEEDUP)));
        }
        drawLineChart(
                output,
                "Ускорение gaussian5 на 2048x2048",
                "Количество потоков",
                "Ускорение",
                series,
                THREADS
        );
    }

    private static void drawStrategyComparison(List<Result> results, Path output) throws IOException {
        Map<String, Double> values = new LinkedHashMap<>();
        for (ParallelStrategy strategy : STRATEGIES) {
            String name = strategy.name().toLowerCase(Locale.ROOT);
            double avg = results.stream()
                    .filter(r -> r.image.equals("2048.jpg"))
                    .filter(r -> r.strategy.equals(name))
                    .filter(r -> r.threads == 4)
                    .mapToDouble(r -> r.speedup)
                    .average()
                    .orElse(0.0);
            values.put(displayStrategy(name), avg);
        }
        drawBarChart(
                output,
                "Среднее ускорение стратегий на 2048x2048, 4 потока",
                "Стратегия разбиения",
                "Ускорение",
                values
        );
    }

    private static void drawThroughputByImage(List<Result> results, Path output) throws IOException {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String image : List.of("256.png", "512.jpg", "1024.jpg", "2048.jpg")) {
            double best = results.stream()
                    .filter(r -> r.image.equals(image))
                    .filter(r -> !r.strategy.equals("sequential"))
                    .filter(r -> r.filter.equals("gaussian5"))
                    .max(Comparator.comparingDouble(r -> r.throughput))
                    .map(r -> r.throughput)
                    .orElse(0.0);
            values.put(image.replace(".png", "").replace(".jpg", "") + "x" + image.replace(".png", "").replace(".jpg", ""), best);
        }
        drawBarChart(
                output,
                "Лучшая пропускная способность gaussian5",
                "Размер изображения",
                "MPix/s",
                values
        );
    }

    private static List<Point> pointsFor(List<Result> results, String image, String filter, String strategy, Metric metric) {
        List<Point> points = new ArrayList<>();
        for (int threads : THREADS) {
            Result result = results.stream()
                    .filter(r -> r.image.equals(image))
                    .filter(r -> r.filter.equals(filter))
                    .filter(r -> r.strategy.equals(strategy))
                    .filter(r -> r.threads == threads)
                    .findFirst()
                    .orElseThrow();
            points.add(new Point(threads, metric == Metric.TIME ? result.avgMs : result.speedup));
        }
        return points;
    }

    private static String displayStrategy(String strategy) {
        return switch (strategy) {
            case "pixels" -> "Пиксели";
            case "rows" -> "Строки";
            case "columns" -> "Столбцы";
            case "grid" -> "Сетка";
            default -> strategy;
        };
    }

    private static void drawLineChart(
            Path output,
            String title,
            String xLabel,
            String yLabel,
            List<Series> series,
            int[] xValues
    ) throws IOException {
        int width = 1400;
        int height = 900;
        int left = 110;
        int right = 260;
        int top = 100;
        int bottom = 120;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        paintBackground(g, width, height);

        double maxY = series.stream()
                .flatMap(s -> s.points.stream())
                .mapToDouble(p -> p.y)
                .max()
                .orElse(1.0) * 1.15;
        double minX = xValues[0];
        double maxX = xValues[xValues.length - 1];

        drawAxes(g, title, xLabel, yLabel, width, height, left, right, top, bottom, maxY, xValues);

        Color[] colors = chartColors();
        for (int i = 0; i < series.size(); i++) {
            g.setColor(colors[i % colors.length]);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Series item = series.get(i);
            int prevX = -1;
            int prevY = -1;
            for (Point point : item.points) {
                int x = scaleX(point.x, minX, maxX, left, width - right);
                int y = scaleY(point.y, 0, maxY, height - bottom, top);
                if (prevX != -1) {
                    g.drawLine(prevX, prevY, x, y);
                }
                g.fillOval(x - 7, y - 7, 14, 14);
                prevX = x;
                prevY = y;
            }
            drawLegendItem(g, item.name, colors[i % colors.length], width - right + 35, top + i * 42);
        }

        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void drawBarChart(
            Path output,
            String title,
            String xLabel,
            String yLabel,
            Map<String, Double> values
    ) throws IOException {
        int width = 1400;
        int height = 900;
        int left = 110;
        int right = 90;
        int top = 100;
        int bottom = 140;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        prepare(g);
        paintBackground(g, width, height);

        double maxY = values.values().stream().mapToDouble(v -> v).max().orElse(1.0) * 1.2;
        drawAxes(g, title, xLabel, yLabel, width, height, left, right, top, bottom, maxY, new int[]{});

        int plotLeft = left;
        int plotRight = width - right;
        int plotBottom = height - bottom;
        int plotTop = top;
        int count = values.size();
        int gap = 55;
        int barWidth = (plotRight - plotLeft - gap * (count + 1)) / count;
        Color[] colors = chartColors();

        int i = 0;
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            int x = plotLeft + gap + i * (barWidth + gap);
            int y = scaleY(entry.getValue(), 0, maxY, plotBottom, plotTop);
            int barHeight = plotBottom - y;
            g.setColor(colors[i % colors.length]);
            g.fillRoundRect(x, y, barWidth, barHeight, 12, 12);
            g.setColor(new Color(35, 39, 47));
            drawCentered(g, entry.getKey(), x + barWidth / 2, plotBottom + 42);
            drawCentered(g, String.format(Locale.US, "%.2f", entry.getValue()), x + barWidth / 2, y - 16);
            i++;
        }

        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void drawAxes(
            Graphics2D g,
            String title,
            String xLabel,
            String yLabel,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            double maxY,
            int[] xValues
    ) {
        int plotLeft = left;
        int plotRight = width - right;
        int plotTop = top;
        int plotBottom = height - bottom;

        g.setColor(new Color(35, 39, 47));
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        drawCentered(g, title, width / 2, 58);

        g.setStroke(new BasicStroke(2f));
        g.drawLine(plotLeft, plotBottom, plotRight, plotBottom);
        g.drawLine(plotLeft, plotTop, plotLeft, plotBottom);

        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.setColor(new Color(98, 108, 122));
        for (int i = 0; i <= 5; i++) {
            double value = maxY * i / 5.0;
            int y = scaleY(value, 0, maxY, plotBottom, plotTop);
            g.setColor(new Color(222, 226, 232));
            g.drawLine(plotLeft, y, plotRight, y);
            g.setColor(new Color(75, 83, 95));
            g.drawString(String.format(Locale.US, "%.1f", value), 18, y + 8);
        }

        if (xValues.length > 0) {
            double minX = xValues[0];
            double maxX = xValues[xValues.length - 1];
            g.setColor(new Color(75, 83, 95));
            for (int value : xValues) {
                int x = scaleX(value, minX, maxX, plotLeft, plotRight);
                g.drawLine(x, plotBottom, x, plotBottom + 8);
                drawCentered(g, String.valueOf(value), x, plotBottom + 38);
            }
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        drawCentered(g, xLabel, (plotLeft + plotRight) / 2, height - 35);

        g.rotate(-Math.PI / 2);
        drawCentered(g, yLabel, -height / 2, 36);
        g.rotate(Math.PI / 2);
    }

    private static int scaleX(double value, double min, double max, int left, int right) {
        if (max == min) {
            return left;
        }
        return (int) Math.round(left + (value - min) / (max - min) * (right - left));
    }

    private static int scaleY(double value, double min, double max, int bottom, int top) {
        if (max == min) {
            return bottom;
        }
        return (int) Math.round(bottom - (value - min) / (max - min) * (bottom - top));
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void paintBackground(Graphics2D g, int width, int height) {
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, width, height);
    }

    private static void drawLegendItem(Graphics2D g, String text, Color color, int x, int y) {
        g.setColor(color);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(x, y, x + 42, y);
        g.fillOval(x + 15, y - 8, 16, 16);
        g.setColor(new Color(35, 39, 47));
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        g.drawString(text, x + 58, y + 9);
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x - metrics.stringWidth(text) / 2, y);
    }

    private static Color[] chartColors() {
        return new Color[]{
                new Color(37, 99, 235),
                new Color(5, 150, 105),
                new Color(220, 38, 38),
                new Color(124, 58, 237),
                new Color(217, 119, 6),
                new Color(8, 145, 178)
        };
    }

    private enum Metric {
        TIME,
        SPEEDUP
    }

    private record Point(double x, double y) {
    }

    private record Series(String name, List<Point> points) {
    }

    private static class Result {
        final String image;
        final int width;
        final int height;
        final String filter;
        final String strategy;
        final int threads;
        final int iterations;
        final double avgMs;
        final double throughput;
        final int checksum;
        double speedup;

        Result(
                String image,
                int width,
                int height,
                String filter,
                String strategy,
                int threads,
                int iterations,
                double avgMs,
                double throughput,
                int checksum
        ) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.filter = filter;
            this.strategy = strategy;
            this.threads = threads;
            this.iterations = iterations;
            this.avgMs = avgMs;
            this.throughput = throughput;
            this.checksum = checksum;
        }
    }
}
