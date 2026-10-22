package com.crazerium.waymark.network;

import java.util.ArrayList;
import java.util.List;

import com.crazerium.waymark.client.ClientImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The names of the quest images on the server; sent on login and after each upload. */
public class MessageImageIndex implements IMessage {

    private List<String> names = new ArrayList<>();

    public MessageImageIndex() {}

    public MessageImageIndex(List<String> names) {
        this.names = names;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(names.size());
        for (String name : names) ByteBufUtils.writeUTF8String(buf, name);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        names = new ArrayList<>();
        for (int i = 0; i < count; i++) names.add(ByteBufUtils.readUTF8String(buf));
    }

    public static class Handler implements IMessageHandler<MessageImageIndex, IMessage> {

        @Override
        public IMessage onMessage(MessageImageIndex message, MessageContext ctx) {
            ClientImages.setIndex(message.names);
            return null;
        }
    }
}
