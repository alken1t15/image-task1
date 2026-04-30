package image;

public class ColorImage {
    public static final int CHANNELS = 3;

    public final int width;
    public final int height;
    public final byte[] data;

    public ColorImage(int width, int height, byte[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image size must be positive");
        }
        if (data.length != width * height * CHANNELS) {
            throw new IllegalArgumentException("Data length does not match RGB image size");
        }
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public static int offset(int width, int x, int y) {
        return (y * width + x) * CHANNELS;
    }
}
