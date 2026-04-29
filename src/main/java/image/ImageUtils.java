package image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class ImageUtils {
    public static GrayImage loadGray(String path) throws IOException {
        BufferedImage input = ImageIO.read(new File(path));
        if (input == null) {
            throw new IOException("Unsupported image format: " + path);
        }

        // Создаю новое изображение такого же размера, но уже в формате grayscale
        BufferedImage gray = new BufferedImage(
                input.getWidth(),
                input.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        // Перерисовываю исходное изображение в gray.
        // За счет TYPE_BYTE_GRAY оно автоматически переводится в оттенки серого
        Graphics2D g = gray.createGraphics();
        try {
            g.drawImage(input, 0, 0, null);
        } finally {
            // После работы освобождаю графический контекст
            g.dispose();
        }

        // Достаю сырые байты пикселей из grayscale-изображения
        // clone() делаю, чтобы получить отдельную копию массива, а не ссылку на внутренний буфер BufferedImage
        byte[] data = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData().clone();
        return new GrayImage(gray.getWidth(), gray.getHeight(), data);
    }

    public static void saveGray(GrayImage image, String path) throws IOException {
        BufferedImage output = new BufferedImage(
                image.width,
                image.height,
                BufferedImage.TYPE_BYTE_GRAY
        );

        byte[] dst = ((DataBufferByte) output.getRaster().getDataBuffer()).getData();
        System.arraycopy(image.data, 0, dst, 0, image.data.length);

        String format = extractFormat(path);
        boolean ok = ImageIO.write(output, format, new File(path));
        if (!ok) {
            throw new IOException("No writer found for format: " + format);
        }
    }

    static String extractFormat(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || dot == path.length() - 1) {
            return "png";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
