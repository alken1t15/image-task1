public class MedianFilter {
    static GrayImage apply(GrayImage src, int windowSize) {
        // Проверяю, что размер окна корректный:
        // для median filter окно должно быть положительным и нечётным
        if (windowSize <= 0 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Median window size must be positive and odd");
        }

        int width = src.width;
        int height = src.height;

        // Здесь будет результат после применения фильтра
        byte[] dst = new byte[src.data.length];

        // Радиус окна вокруг текущего пикселя.
        // Например, для 3x3 radius = 1, для 5x5 radius = 2
        int radius = windowSize / 2;

        // В этот массив собираю все значения пикселей из окна,
        // чтобы потом отсортировать их и взять медиану
        int[] window = new int[windowSize * windowSize];

        // Прохожу по всем пикселям исходного изображения
        for (int y = 0; y < height; y++) {
            int dstRowOffset = y * width;

            for (int x = 0; x < width; x++) {
                // Считаю медиану через общий метод, чтобы параллельная версия
                // не дублировала правила обработки границ.
                int median = computePixel(src, x, y, radius, window);

                // Записываю медиану в результирующее изображение
                dst[dstRowOffset + x] = (byte) median;
            }
        }

        return new GrayImage(width, height, dst);
    }

    static int computePixel(GrayImage src, int x, int y, int radius, int[] window) {
        int idx = 0;

        // Собираю все пиксели из окна вокруг текущей точки (x, y).
        for (int dy = -radius; dy <= radius; dy++) {
            // Если выходим за границы, прижимаю координату к ближайшей границе изображения.
            int sy = clamp(y + dy, 0, src.height - 1);

            for (int dx = -radius; dx <= radius; dx++) {
                int sx = clamp(x + dx, 0, src.width - 1);

                // Беру значение пикселя из исходного изображения и добавляю его в окно.
                window[idx++] = src.data[sy * src.width + sx] & 0xFF;
            }
        }

        java.util.Arrays.sort(window);

        // После сортировки центральный элемент и есть медиана.
        return window[window.length / 2];
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
