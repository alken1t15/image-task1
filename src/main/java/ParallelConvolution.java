public class ParallelConvolution {
    static GrayImage apply(GrayImage src, Kernel kernel, ParallelStrategy strategy, int threads) {
        int width = src.width;
        int height = src.height;

        // Каждый поток пишет только в свои пиксели, поэтому отдельная синхронизация
        // для массива dst здесь не нужна.
        byte[] dst = new byte[src.data.length];

        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        ParallelImageProcessor.process(width, height, strategy, threads, (x, y) -> {
            int index = y * width + x;
            dst[index] = (byte) Convolution.computePixel(src, kernel, x, y, kernelCenterX, kernelCenterY);
        });

        return new GrayImage(width, height, dst);
    }
}
