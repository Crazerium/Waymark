package com.crazerium.waymark.network;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.crazerium.waymark.client.ClientImages;
import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.util.Compression;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A large document sent from the server in parts: the whole quest book or a team's progress (gzipped JSON), or a
 * quest image (PNG). A big pack's book is over a megabyte even compressed, more than one packet can safely carry in
 * 1.7.10.
 */
public class MessageBlob implements IMessage {

    public static final byte BOOK = 0;
    public static final byte PROGRESS = 1;
    public static final byte IMAGE = 2;

    /** Below the 32767 byte limit of a vanilla custom payload packet, with room for the header. */
    public static final int PART_SIZE = 30000;

    private byte kind;
    /** For images, the file name; empty otherwise. */
    private String name = "";
    private int part, total;
    private byte[] data;

    public MessageBlob() {}

    private MessageBlob(byte kind, String name, int part, int total, byte[] data) {
        this.kind = kind;
        this.name = name;
        this.part = part;
        this.total = total;
        this.data = data;
    }

    public static List<MessageBlob> split(byte kind, byte[] gzipped) {
        return split(kind, "", gzipped);
    }

    public static List<MessageBlob> split(byte kind, String name, byte[] gzipped) {
        List<MessageBlob> parts = new ArrayList<>();
        int total = Math.max(1, (gzipped.length + PART_SIZE - 1) / PART_SIZE);
        for (int i = 0; i < total; i++) {
            int from = i * PART_SIZE;
            int len = Math.min(PART_SIZE, gzipped.length - from);
            byte[] chunk = new byte[Math.max(0, len)];
            System.arraycopy(gzipped, from, chunk, 0, chunk.length);
            parts.add(new MessageBlob(kind, name, i, total, chunk));
        }
        return parts;
    }

    public static List<MessageBlob> split(byte kind, JsonObject json) {
        return split(kind, Compression.gzip(json.toString()));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        ByteBufUtils.writeUTF8String(buf, name);
        buf.writeInt(part);
        buf.writeInt(total);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        name = ByteBufUtils.readUTF8String(buf);
        part = buf.readInt();
        total = buf.readInt();
        data = new byte[buf.readInt()];
        buf.readBytes(data);
    }

    public static class Handler implements IMessageHandler<MessageBlob, IMessage> {

        private static final ByteArrayOutputStream[] buffers = new ByteArrayOutputStream[3];

        @Override
        public IMessage onMessage(MessageBlob message, MessageContext ctx) {
            if (message.kind < BOOK || message.kind > IMAGE) return null;
            // Parts arrive in order on one connection; part 0 starts a new document.
            if (message.part == 0) buffers[message.kind] = new ByteArrayOutputStream();
            ByteArrayOutputStream buffer = buffers[message.kind];
            if (buffer == null) return null;
            buffer.write(message.data, 0, message.data.length);
            if (message.part < message.total - 1) return null;

            buffers[message.kind] = null;
            if (message.kind == IMAGE) {
                ClientImages.receive(message.name, buffer.toByteArray());
                return null;
            }
            JsonObject json = new JsonParser().parse(Compression.gunzip(buffer.toByteArray()))
                .getAsJsonObject();
            if (message.kind == BOOK) ClientQuests.setBook(BookSync.read(json));
            else ClientQuests.setProgress(json);
            return null;
        }
    }
}
