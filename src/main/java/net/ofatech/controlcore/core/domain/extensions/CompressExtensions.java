package net.ofatech.controlcore.core.domain.extensions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CompressExtensions {
    private CompressExtensions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static byte[] compress(byte[] data) {
        return compress(data, Deflater.DEFAULT_COMPRESSION);
    }

    public static byte[] compress(byte[] data, int compressionLevel) {
        Objects.requireNonNull(data, "data cannot be null");

        if (compressionLevel < Deflater.NO_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("compressionLevel must be between 0 and 9.");
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (GZIPOutputStream gzipStream = new ConfigurableGzipOutputStream(outputStream, compressionLevel)) {
                gzipStream.write(data);
            }

            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compress data.", exception);
        }
    }

    public static byte[] compress(String data) {
        Objects.requireNonNull(data, "data cannot be null");

        return compress(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] compress(String data, int compressionLevel) {
        Objects.requireNonNull(data, "data cannot be null");

        return compress(data.getBytes(StandardCharsets.UTF_8), compressionLevel);
    }

    public static byte[] decompress(byte[] compressedData) {
        Objects.requireNonNull(compressedData, "compressedData cannot be null");

        try (
            ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedData);
            GZIPInputStream gzipStream = new GZIPInputStream(inputStream);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            gzipStream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decompress data.", exception);
        }
    }

    public static String decompressToString(byte[] compressedData) {
        return new String(decompress(compressedData), StandardCharsets.UTF_8);
    }

    private static final class ConfigurableGzipOutputStream extends GZIPOutputStream {
        private ConfigurableGzipOutputStream(ByteArrayOutputStream outputStream, int compressionLevel) throws IOException {
            super(outputStream);
            this.def.setLevel(compressionLevel);
        }
    }
}
