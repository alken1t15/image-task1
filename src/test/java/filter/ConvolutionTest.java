package filter;

import image.GrayImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBufferByte;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class ConvolutionTest {

    @Test
    void identityShouldReturnSameImage() {
        GrayImage input = randomImage(17, 13, 42);

        GrayImage output = Convolution.apply(input, Kernels.byName("identity"));

        assertImagesEqual(input, output);
    }

    @Test
    void zeroKernelShouldProduceBlackImage() {
        GrayImage input = randomImage(9, 7, 123);

        Kernel zeroKernel = new Kernel(
                3, 3,
                new double[]{
                        0, 0, 0,
                        0, 0, 0,
                        0, 0, 0
                },
                1.0, 0.0
        );

        GrayImage output = Convolution.apply(input, zeroKernel);

        for (byte b : output.data) {
            assertEquals(0, b & 0xFF);
        }
    }

    @Test
    void outputShouldKeepSameSize() {
        GrayImage input = randomImage(31, 19, 7);

        GrayImage output = Convolution.apply(input, Kernels.byName("gaussian3"));

        assertEquals(input.width, output.width);
        assertEquals(input.height, output.height);
        assertEquals(input.data.length, output.data.length);
    }

    @Test
    void outputValuesShouldStayInRange0To255() {
        GrayImage input = randomImage(25, 25, 99);

        String[] filters = {
                "blur3", "blur5", "gaussian3", "gaussian5",
                "edge_all3", "sharpen3", "emboss3", "motion9"
        };

        for (String name : filters) {
            GrayImage output = Convolution.apply(input, Kernels.byName(name));

            for (byte b : output.data) {
                int value = b & 0xFF;
                assertTrue(value >= 0 && value <= 255,
                        "filter=" + name + ", value=" + value);
            }
        }
    }

    @Test
    void medianOnConstantImageShouldReturnSameImage() {
        GrayImage input = constantImage(11, 8, 137);

        GrayImage output3 = MedianFilter.apply(input, 3);
        GrayImage output5 = MedianFilter.apply(input, 5);

        assertImagesEqual(input, output3);
        assertImagesEqual(input, output5);
    }

    @Test
    void medianShouldRemoveSingleImpulseNoise() {
        GrayImage input = constantImage(7, 7, 100);

        input.data[3 * input.width + 3] = (byte) 255;

        GrayImage output = MedianFilter.apply(input, 3);

        assertEquals(100, output.data[3 * output.width + 3] & 0xFF);
    }

    @Test
    void gaussian3ShouldMatchJavaConvolveOpInsideImage() {
        GrayImage input = randomImage(20, 15, 555);
        Kernel kernel = Kernels.byName("gaussian3");

        GrayImage mine = Convolution.apply(input, kernel);
        GrayImage reference = applyWithJavaConvolveOp(input, kernel);

        assertInteriorEqual(mine, reference, kernel.width / 2, kernel.height / 2);
    }

    @Test
    void sharpen3ShouldMatchJavaConvolveOpInsideImage() {
        GrayImage input = randomImage(20, 15, 777);
        Kernel kernel = Kernels.byName("sharpen3");

        GrayImage mine = Convolution.apply(input, kernel);
        GrayImage reference = applyWithJavaConvolveOp(input, kernel);

        assertInteriorEqual(mine, reference, kernel.width / 2, kernel.height / 2);
    }

    @Test
    void blur5ShouldMatchJavaConvolveOpInsideImage() {
        GrayImage input = randomImage(30, 18, 999);
        Kernel kernel = Kernels.byName("blur5");

        GrayImage mine = Convolution.apply(input, kernel);
        GrayImage reference = applyWithJavaConvolveOp(input, kernel);

        assertInteriorEqual(mine, reference, kernel.width / 2, kernel.height / 2);
    }

    @Test
    void paddingKernelWithZerosShouldNotChangeResult() {
        GrayImage input = randomImage(16, 12, 2024);

        Kernel k3 = Kernels.byName("gaussian3");
        Kernel padded5 = new Kernel(
                5, 5,
                new double[]{
                        0, 0, 0, 0, 0,
                        0, 1, 2, 1, 0,
                        0, 2, 4, 2, 0,
                        0, 1, 2, 1, 0,
                        0, 0, 0, 0, 0
                },
                1.0 / 16.0, 0.0
        );

        GrayImage out1 = Convolution.apply(input, k3);
        GrayImage out2 = Convolution.apply(input, padded5);

        assertImagesEqual(out1, out2);
    }

    private static GrayImage randomImage(int width, int height, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[width * height];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) random.nextInt(256);
        }

        return new GrayImage(width, height, data);
    }

    private static GrayImage constantImage(int width, int height, int value) {
        byte[] data = new byte[width * height];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) value;
        }
        return new GrayImage(width, height, data);
    }

    private static void assertImagesEqual(GrayImage expected, GrayImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }

    private static void assertInteriorEqual(GrayImage expected, GrayImage actual, int rx, int ry) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);

        for (int y = ry; y < expected.height - ry; y++) {
            for (int x = rx; x < expected.width - rx; x++) {
                int e = expected.data[y * expected.width + x] & 0xFF;
                int a = actual.data[y * actual.width + x] & 0xFF;

                assertTrue(
                        Math.abs(e - a) <= 1,
                        "Mismatch at (" + x + ", " + y + "): expected=" + e + ", actual=" + a
                );
            }
        }
    }

    private static GrayImage applyWithJavaConvolveOp(GrayImage input, Kernel kernel) {
        BufferedImage src = toBufferedImage(input);

        float[] data = new float[kernel.values.length];
        for (int i = 0; i < kernel.values.length; i++) {
            data[i] = (float) (kernel.values[i] * kernel.factor);
        }

        java.awt.image.Kernel awtKernel =
                new java.awt.image.Kernel(kernel.width, kernel.height, data);

        BufferedImage dst = new BufferedImage(
                input.width,
                input.height,
                BufferedImage.TYPE_BYTE_GRAY
        );

        ConvolveOp op = new ConvolveOp(awtKernel, ConvolveOp.EDGE_ZERO_FILL, null);
        op.filter(src, dst);

        GrayImage out = fromBufferedImage(dst);

        if (kernel.bias != 0.0) {
            byte[] biased = new byte[out.data.length];
            for (int i = 0; i < out.data.length; i++) {
                int value = (out.data[i] & 0xFF) + (int) Math.round(kernel.bias);
                value = clamp(value, 0, 255);
                biased[i] = (byte) value;
            }
            return new GrayImage(out.width, out.height, biased);
        }

        return out;
    }

    private static BufferedImage toBufferedImage(GrayImage image) {
        BufferedImage buffered = new BufferedImage(
                image.width,
                image.height,
                BufferedImage.TYPE_BYTE_GRAY
        );

        byte[] dst = ((DataBufferByte) buffered.getRaster().getDataBuffer()).getData();
        System.arraycopy(image.data, 0, dst, 0, image.data.length);

        return buffered;
    }

    private static GrayImage fromBufferedImage(BufferedImage image) {
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData().clone();
        return new GrayImage(image.getWidth(), image.getHeight(), data);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
