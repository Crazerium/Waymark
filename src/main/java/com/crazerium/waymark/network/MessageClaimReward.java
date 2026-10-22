package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerProgress;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageClaimReward implements IMessage {

    private long rewardId;
    private int choice;

    public MessageClaimReward() {}

    /** {@code choice} is the picked option for choice rewards, -1 otherwise. */
    public MessageClaimReward(long rewardId, int choice) {
        this.rewardId = rewardId;
        this.choice = choice;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(rewardId);
        buf.writeInt(choice);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rewardId = buf.readLong();
        choice = buf.readInt();
    }

    public static class Handler implements IMessageHandler<MessageClaimReward, IMessage> {

        @Override
        public IMessage onMessage(MessageClaimReward message, MessageContext ctx) {
            ServerProgress.claim(ctx.getServerHandler().playerEntity, message.rewardId, message.choice);
            return null;
        }
    }
}
