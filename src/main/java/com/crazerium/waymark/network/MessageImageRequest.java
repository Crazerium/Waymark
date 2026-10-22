package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** A client needs a quest image it has not downloaded yet. */
public class MessageImageRequest implements IMessage {

    private String name;

    public MessageImageRequest() {}

    public MessageImageRequest(String name) {
        this.name = name;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<MessageImageRequest, IMessage> {

        @Override
        public IMessage onMessage(MessageImageRequest message, MessageContext ctx) {
            ServerImages.sendImage(ctx.getServerHandler().playerEntity, message.name);
            return null;
        }
    }
}
