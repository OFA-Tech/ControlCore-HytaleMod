package net.ofatech.controlcore.core.data.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.util.Objects;

public final class StreamAccess {
    private StreamAccess() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static byte[] readAllBytes(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        try {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stream bytes.", exception);
        }
    }

    public static String readUtf8(InputStream inputStream) {
        return new String(readAllBytes(inputStream), StandardCharsets.UTF_8);
    }

    public static void copy(InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        Objects.requireNonNull(outputStream, "outputStream cannot be null");

        try {
            inputStream.transferTo(outputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy stream.", exception);
        }
    }

    public static byte[] copyToBytes(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copy(inputStream, outputStream);
        return outputStream.toByteArray();
    }
}
