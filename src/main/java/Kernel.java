public class Kernel {
    final int width;
    final int height;
    final double[] values;
    final double factor;
    final double bias;

    Kernel(int width, int height, double[] values, double factor, double bias) {
        if (width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("Kernel sizes must be odd");
        }
        if (values.length != width * height) {
            throw new IllegalArgumentException("Kernel value count does not match kernel size");
        }
        this.width = width;
        this.height = height;
        this.values = values;
        this.factor = factor;
        this.bias = bias;
    }
}
