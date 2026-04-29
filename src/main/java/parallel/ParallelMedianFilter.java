package parallel;

import filter.MedianFilter;
import image.GrayImage;

public class ParallelMedianFilter {
    public static GrayImage apply(GrayImage src, int windowSize, ParallelStrategy strategy, int threads) {
        // Проверяю размер окна так же, как в последовательной версии.
        if (windowSize <= 0 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Median window size must be positive and odd");
        }

        int width = src.width;
        int height = src.height;
        int radius = windowSize / 2;
        byte[] dst = new byte[src.data.length];
        ThreadLocal<int[]> windows = ThreadLocal.withInitial(() -> new int[windowSize * windowSize]);

        ParallelImageProcessor.process(width, height, strategy, threads, (x, y) -> {
            // Каждому потоку даю своё окно, чтобы потоки не перетирали данные друг друга.
            int[] window = windows.get();
            int index = y * width + x;
            dst[index] = (byte) MedianFilter.computePixel(src, x, y, radius, window);
        });

        return new GrayImage(width, height, dst);
    }
}
