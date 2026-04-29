package parallel;

import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.GrayImage;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelFilterTest {
    @Test
    void parallelConvolutionShouldMatchSequentialForAllStrategies() {
        GrayImage input = randomImage(23, 17, 1001);
        Kernel kernel = Kernels.byName("gaussian5");
        GrayImage expected = Convolution.apply(input, kernel);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            // Сравниваю каждую стратегию с последовательной версией,
            // потому что для второго задания это главный критерий корректности.
            GrayImage actual = ParallelConvolution.apply(input, kernel, strategy, 4);
            assertImagesEqual(expected, actual);
        }
    }

    @Test
    void parallelMedianShouldMatchSequentialForAllStrategies() {
        GrayImage input = randomImage(19, 21, 2002);
        GrayImage expected = MedianFilter.apply(input, 5);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            GrayImage actual = ParallelMedianFilter.apply(input, 5, strategy, 4);
            assertImagesEqual(expected, actual);
        }
    }

    @Test
    void parallelConvolutionShouldWorkWhenThreadsMoreThanImageParts() {
        GrayImage input = randomImage(5, 4, 3003);
        Kernel kernel = Kernels.byName("sharpen3");
        GrayImage expected = Convolution.apply(input, kernel);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            // Потоков специально больше, чем строк или столбцов,
            // чтобы проверить маленькие изображения и крайние случаи разбиения.
            GrayImage actual = ParallelConvolution.apply(input, kernel, strategy, 16);
            assertImagesEqual(expected, actual);
        }
    }

    private static GrayImage randomImage(int width, int height, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[width * height];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) random.nextInt(256);
        }

        return new GrayImage(width, height, data);
    }

    private static void assertImagesEqual(GrayImage expected, GrayImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }
}
