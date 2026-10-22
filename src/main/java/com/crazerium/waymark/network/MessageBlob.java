package com.crazerium.waymark.network;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.util.Compression;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A large JSON document (the whole quest book, or a team's progress) sent as gzipped parts. A big pack's book is
 * over a megabyte even compressed, more than one packet can safely carry in 1.7.10.
 */
public class MessageBlob implements IMessage {

    public static final byte BOOK = 0;
    public static final byte PROGRESS = 1;

    /** Below the 32767 byte limit of a vanilla custom payload packet, with room for the header. */
    private static final int PART_SIZE = 30000;

    private byte kind;
    private int part, total;
    private byte[] data;

    public MessageBlob() {}

    private MessageBlob(byte kind, int part, int total, byte[] data) {
        this.kind = kind;
        this.part = part;
        this.total = total;
        this.data = data;
    }

    public static List<MessageBlob> split(byte kind, byte[] gzipped) {
        List<MessageBlob> parts = new ArrayList<>();
        int total = Math.max(1, (gzipped.length + PART_SIZE - 1) / PART_SIZE);
        for (int i = 0; i < total; i++) {
            int from = i * PART_SIZE;
            int len = Math.min(PART_SIZE, gzipped.length - from);
            byte[] chunk = new byte[Math.max(0, len)];
            System.arraycopy(gzipped, from, chunk, 0, chunk.length);
            parts.add(new MessageBlob(kind, i, total, chunk));
        }
        return parts;
    }

    public static List<MessageBlob> split(byte kind, JsonObject json) {
        return split(kind, Compression.gzip(json.toString()));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeInt(part);
        buf.writeInt(total);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        part = buf.readInt();
        total = buf.readInt();
        data = new byte[buf.readInt()];
        buf.readBytes(data);
    }

    public static class Handler implements IMessageHandler<MessageBlob, IMessage> {

        private static final ByteArrayOutputStream[] buffers = new ByteArrayOutputStream[2];

        @Override
        public IMessage onMessage(MessageBlob message, MessageContext ctx) {
            if (message.kind != BOOK && message.kind != PROGRESS) return null;
            // Parts arrive in order on one connection; part 0 starts a new document.
            if (message.part == 0) buffers[message.kind] = new ByteArrayOutputStream();
            ByteArrayOutputStream buffer = buffers[message.kind];
            if (buffer == null) return null;
            buffer.write(message.data, 0, message.data.length);
            if (message.part < message.total - 1) return null;

            buffers[message.kind] = null;
            JsonObject json = new JsonParser().parse(Compression.gunzip(buffer.toByteArray()))
                .getAsJsonObject();
            if (message.kind == BOOK) ClientQuests.setBook(BookSync.read(json));
            else ClientQuests.setProgress(json);
            return null;
        }
    }
}
