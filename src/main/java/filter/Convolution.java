package filter;

import image.GrayImage;

public class Convolution {
    public static GrayImage apply(GrayImage src, Kernel kernel) {
        int width = src.width;
        int height = src.height;

        // В этот массив буду записывать результат свёртки
        byte[] dst = new byte[src.data.length];

        // Нахожу центр ядра.
        // Например, для ядра 3x3 центр будет (1, 1), для 5x5 -> (2, 2)
        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        // Прохожу по всем пикселям изображения
        for (int y = 0; y < height; y++) {
            // Здесь накапливаю сумму произведений пикселей на коэффициенты ядра
            int dstRowOffset = y * width;

            for (int x = 0; x < width; x++) {
                // Считаю один пиксель через общий метод, чтобы последовательная
                // и параллельная версии использовали одинаковую математику.
                dst[dstRowOffset + x] = (byte) computePixel(src, kernel, x, y, kernelCenterX, kernelCenterY);
            }
        }

        return new GrayImage(width, height, dst);
    }

    public static int computePixel(GrayImage src, Kernel kernel, int x, int y) {
        return computePixel(src, kernel, x, y, kernel.width / 2, kernel.height / 2);
    }

    public static int computePixel(GrayImage src, Kernel kernel, int x, int y, int kernelCenterX, int kernelCenterY) {
        int width = src.width;
        int height = src.height;

        // Здесь накапливаю сумму произведений пикселей на коэффициенты ядра.
        double sum = 0.0;

        for (int ky = 0; ky < kernel.height; ky++) {
            /* Вычисляю координату пикселя в исходном изображении.
             mod использую для wrap-around обработки границ,
             если вышли за край, "заворачиваемся" на другую сторону изображения.
             */
            int sy = mod(y + ky - kernelCenterY, height);
            int srcRowOffset = sy * width;
            int kernelRowOffset = ky * kernel.width;

            // Прохожу по всем элементам ядра по x.
            for (int kx = 0; kx < kernel.width; kx++) {
                int sx = mod(x + kx - kernelCenterX, width);

                // Беру яркость пикселя из исходного изображения.
                int pixel = src.data[srcRowOffset + sx] & 0xFF;

                // Добавляю вклад этого пикселя в итоговую сумму.
                sum += pixel * kernel.values[kernelRowOffset + kx];
            }
        }

        // После свёртки применяю factor и bias.
        int value = (int) Math.round(sum * kernel.factor + kernel.bias);

        // Ограничиваю результат диапазоном допустимых значений яркости.
        return clamp(value, 0, 255);
    }

    // Этот метод нужен, чтобы зажать число в допустимый диапазон.
    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // Этот метод нужен для корректной обработки выхода за границы изображения.
    static int mod(int value, int size) {
        return ((value % size) + size) % size;
    }
}
