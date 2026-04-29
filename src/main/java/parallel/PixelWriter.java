package parallel;

@FunctionalInterface
interface PixelWriter {
    void write(int x, int y);
}
