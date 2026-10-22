package com.crazerium.waymark.network;

import java.util.ArrayList;
import java.util.List;

import com.crazerium.waymark.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** One part of a PNG an operator uploads for quest descriptions. */
public class MessageImageUpload implements IMessage {

    private String name;
    private int part, total;
    private byte[] data;

    public MessageImageUpload() {}

    private MessageImageUpload(String name, int part, int total, byte[] data) {
        this.name = name;
        this.part = part;
        this.total = total;
        this.data = data;
    }

    public static List<MessageImageUpload> split(String name, byte[] bytes) {
        List<MessageImageUpload> parts = new ArrayList<>();
        int size = MessageBlob.PART_SIZE;
        int total = Math.max(1, (bytes.length + size - 1) / size);
        for (int i = 0; i < total; i++) {
            int from = i * size;
            byte[] chunk = new byte[Math.max(0, Math.min(size, bytes.length - from))];
            System.arraycopy(bytes, from, chunk, 0, chunk.length);
            parts.add(new MessageImageUpload(name, i, total, chunk));
        }
        return parts;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name);
        buf.writeInt(part);
        buf.writeInt(total);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf);
        part = buf.readInt();
        total = buf.readInt();
        int length = buf.readInt();
        // Never trust a length from the network more than one part can hold.
        data = new byte[Math.max(0, Math.min(length, MessageBlob.PART_SIZE))];
        buf.readBytes(data);
    }

    public static class Handler implements IMessageHandler<MessageImageUpload, IMessage> {

        @Override
        public IMessage onMessage(MessageImageUpload message, MessageContext ctx) {
            ServerImages.receiveUpload(
                ctx.getServerHandler().playerEntity,
                message.name,
                message.part,
                message.total,
                message.data);
            return null;
        }
    }
}
