package com.crazerium.waymark.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;

/** Gzipped strings for packets; quest JSON compresses roughly tenfold. */
public final class Compression {

    private Compression() {}

    public static void writeString(ByteBuf buf, String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        buf.writeInt(bytes.size());
        buf.writeBytes(bytes.toByteArray());
    }

    public static String readString(ByteBuf buf) {
        byte[] data = new byte[buf.readInt()];
        buf.readBytes(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) bytes.write(chunk, 0, n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
