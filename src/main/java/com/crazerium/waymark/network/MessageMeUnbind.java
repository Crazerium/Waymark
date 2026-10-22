package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerProgress;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** A team member unbinds the team's ME network from the quest book. */
public class MessageMeUnbind implements IMessage {

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<MessageMeUnbind, IMessage> {

        @Override
        public IMessage onMessage(MessageMeUnbind message, MessageContext ctx) {
            ServerProgress.unbindMeNetwork(ctx.getServerHandler().playerEntity);
            return null;
        }
    }
}
