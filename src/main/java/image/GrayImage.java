package image;

public class GrayImage {
    public final int width;
    public final int height;
    public final byte[] data;

    public GrayImage(int width, int height, byte[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image size must be positive");
        }
        if (data.length != width * height) {
            throw new IllegalArgumentException("Data length does not match image size");
        }
        this.width = width;
        this.height = height;
        this.data = data;
    }
}
