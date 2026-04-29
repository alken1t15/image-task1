package app;

import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.GrayImage;
import image.ImageUtils;
import parallel.ParallelConvolution;
import parallel.ParallelMedianFilter;
import parallel.ParallelStrategy;

import java.io.IOException;
import java.util.Locale;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);

        switch (mode) {
            case "apply" -> {
                // Ожидаю: apply <input> <output> <kernelName>
                if (args.length != 4) {
                    printUsage();
                    return;
                }
                apply(args[1], args[2], args[3]);
            }
            case "benchmark" -> {
                // Ожидаю: benchmark <input> <kernelName> <iterations>
                if (args.length != 4) {
                    printUsage();
                    return;
                }
                int iterations = Integer.parseInt(args[3]);
                benchmark(args[1], args[2], iterations);
            }
            case "apply-parallel" -> {
                // Ожидаю: apply-parallel <input> <output> <filterName> <strategy> <threads>
                if (args.length != 6) {
                    printUsage();
                    return;
                }
                ParallelStrategy strategy = ParallelStrategy.parse(args[4]);
                int threads = Integer.parseInt(args[5]);
                applyParallel(args[1], args[2], args[3], strategy, threads);
            }
            case "benchmark-parallel" -> {
                // Ожидаю: benchmark-parallel <input> <filterName> <strategy> <threads> <iterations>
                if (args.length != 6) {
                    printUsage();
                    return;
                }
                ParallelStrategy strategy = ParallelStrategy.parse(args[3]);
                int threads = Integer.parseInt(args[4]);
                int iterations = Integer.parseInt(args[5]);
                benchmarkParallel(args[1], args[2], strategy, threads, iterations);
            }
            default -> printUsage();
        }
    }

    private static void apply(String inputPath, String outputPath, String filterName) throws IOException {
        // Загружаю входное изображение и сразу перевожу его в grayscale
        GrayImage input = ImageUtils.loadGray(inputPath);

        // Засекаю время перед применением фильтра
        long start = System.nanoTime();
        // Применяю выбранный фильтр к изображению
        GrayImage output = applyFilter(input, filterName);

        // Считаю, сколько времени заняла обработка
        long elapsed = System.nanoTime() - start;

        // Сохраняю результат в выходной файл
        ImageUtils.saveGray(output, outputPath);

        // Перевожу время из наносекунд в миллисекунды для более удобного вывода
        double ms = elapsed / 1_000_000.0;

        // Считаю размер изображения в мегапикселях
        double mpix = (double) input.width * input.height / 1_000_000.0;

        // Считаю пропускную способность: сколько мегапикселей обрабатывается в секунду
        double mpixPerSec = mpix / (elapsed / 1_000_000_000.0);

        System.out.printf(Locale.US,
                "Done. Filter=%s, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",
                filterName, input.width, input.height, ms, mpixPerSec);
    }

    private static void applyParallel(
            String inputPath,
            String outputPath,
            String filterName,
            ParallelStrategy strategy,
            int threads
    ) throws IOException {
        // Загружаю входное изображение так же, как в первом задании.
        GrayImage input = ImageUtils.loadGray(inputPath);

        // Засекаю только время фильтрации, без чтения и записи файла.
        long start = System.nanoTime();
        GrayImage output = applyFilterParallel(input, filterName, strategy, threads);
        long elapsed = System.nanoTime() - start;

        ImageUtils.saveGray(output, outputPath);

        double ms = elapsed / 1_000_000.0;
        double mpix = (double) input.width * input.height / 1_000_000.0;
        double mpixPerSec = mpix / (elapsed / 1_000_000_000.0);

        System.out.printf(Locale.US,
                "Done. Filter=%s, strategy=%s, threads=%d, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",
                filterName, strategy.name().toLowerCase(Locale.ROOT), threads, input.width, input.height, ms, mpixPerSec);
    }

    private static void benchmark(String inputPath, String filterName, int iterations) throws IOException {
        // Загружаю входное изображение и перевожу его в grayscale
        GrayImage input = ImageUtils.loadGray(inputPath);

        // Здесь буду хранить суммарное время всех запусков
        long total = 0;

        // Держу контрольную сумму, чтобы результат вычислений точно использовался
        int checksum = 0;

        for (int i = 0; i < iterations; i++) {
            // Засекаю время перед запуском фильтра
            long start = System.nanoTime();

            // Применяю фильтр
            GrayImage out = applyFilter(input, filterName);

            // Считаю время одного запуска
            long elapsed = System.nanoTime() - start;

            // Добавляю время к общей сумме
            total += elapsed;

            // Обновляю checksum
            checksum += out.data[i % out.data.length] & 0xFF;

            // Перевожу время в миллисекунды
            double ms = elapsed / 1_000_000.0;

            // Печатаю результат каждого отдельного запуска
            System.out.printf(Locale.US,
                    "Run %d: %.3f ms%n",
                    i + 1, ms);
        }

        // Считаю среднее время одного запуска
        double avgNs = (double) total / iterations;
        double avgMs = avgNs / 1_000_000.0;

        // Считаю размер изображения в мегапикселях
        double mpix = (double) input.width * input.height / 1_000_000.0;

        // Считаю среднюю пропускную способность
        double mpixPerSec = mpix / (avgNs / 1_000_000_000.0);

        // Печатаю итоговую статистику
        System.out.printf(Locale.US,
                "Average: filter=%s, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",
                filterName, input.width, input.height, iterations, avgMs, mpixPerSec, checksum);
    }

    private static void benchmarkParallel(
            String inputPath,
            String filterName,
            ParallelStrategy strategy,
            int threads,
            int iterations
    ) throws IOException {
        // Загружаю изображение один раз, чтобы измерять именно фильтрацию.
        GrayImage input = ImageUtils.loadGray(inputPath);

        long total = 0;
        int checksum = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();

            GrayImage out = applyFilterParallel(input, filterName, strategy, threads);

            long elapsed = System.nanoTime() - start;
            total += elapsed;
            checksum += out.data[i % out.data.length] & 0xFF;

            double ms = elapsed / 1_000_000.0;

            System.out.printf(Locale.US,
                    "Run %d: %.3f ms%n",
                    i + 1, ms);
        }

        double avgNs = (double) total / iterations;
        double avgMs = avgNs / 1_000_000.0;
        double mpix = (double) input.width * input.height / 1_000_000.0;
        double mpixPerSec = mpix / (avgNs / 1_000_000_000.0);

        System.out.printf(Locale.US,
                "Average: filter=%s, strategy=%s, threads=%d, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",
                filterName, strategy.name().toLowerCase(Locale.ROOT), threads,
                input.width, input.height, iterations, avgMs, mpixPerSec, checksum);
    }

    private static GrayImage applyFilter(GrayImage input, String filterName) {
        String name = filterName.toLowerCase(Locale.ROOT);

        // Если имя фильтра начинается с "median", считаю его медианным фильтром.
        // Для него не использую обычное ядро коэффициентов, поэтому размер окна
        // (например 3, 5 или 7) разбираю из названия фильтра отдельно.
        if (name.startsWith("median")) {
            int windowSize = parseMedianWindowSize(name);
            return MedianFilter.apply(input, windowSize);
        }

        // Для всех остальных фильтров получаю ядро по имени
        Kernel kernel = Kernels.byName(name);

        // И применяю обычную свёртку
        return Convolution.apply(input, kernel);
    }

    private static GrayImage applyFilterParallel(
            GrayImage input,
            String filterName,
            ParallelStrategy strategy,
            int threads
    ) {
        String name = filterName.toLowerCase(Locale.ROOT);

        // Median filter тоже распараллеливаю: каждый пиксель результата
        // считаю независимо от остальных пикселей результата.
        if (name.startsWith("median")) {
            int windowSize = parseMedianWindowSize(name);
            return ParallelMedianFilter.apply(input, windowSize, strategy, threads);
        }

        Kernel kernel = Kernels.byName(name);
        return ParallelConvolution.apply(input, kernel, strategy, threads);
    }

    private static int parseMedianWindowSize(String name) {
        // По названию фильтра определяю размер окна для median filter
        return switch (name) {
            case "median3" -> 3;
            case "median5" -> 5;
            case "median7" -> 7;
            default -> throw new IllegalArgumentException(
                    "Unknown median filter: " + name + ". Supported: median3, median5, median7"
            );
        };
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              java Main apply <input> <output> <filterName>
              java Main benchmark <input> <filterName> <iterations>
              java Main apply-parallel <input> <output> <filterName> <strategy> <threads>
              java Main benchmark-parallel <input> <filterName> <strategy> <threads> <iterations>

            Parallel strategies:
              pixels
              rows
              columns
              grid

            Filters:
              identity
              blur3
              blur5
              gaussian3
              gaussian5
              gaussian3_exact
              motion9
              edge_horizontal5
              edge_vertical5
              edge_45deg5
              edge_all3
              sharpen3
              sharpen5
              edge_excessive3
              emboss3
              emboss5
              mean3
              median3
              median5
              median7
            """);
    }
}
