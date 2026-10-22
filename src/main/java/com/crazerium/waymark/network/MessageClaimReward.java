package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerProgress;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageClaimReward implements IMessage {

    private long rewardId;

    public MessageClaimReward() {}

    public MessageClaimReward(long rewardId) {
        this.rewardId = rewardId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(rewardId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rewardId = buf.readLong();
    }

    public static class Handler implements IMessageHandler<MessageClaimReward, IMessage> {

        @Override
        public IMessage onMessage(MessageClaimReward message, MessageContext ctx) {
            ServerProgress.claim(ctx.getServerHandler().playerEntity, message.rewardId);
            return null;
        }
    }
}
