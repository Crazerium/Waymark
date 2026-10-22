package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageRewardClaimed implements IMessage {

    private long rewardId;

    public MessageRewardClaimed() {}

    public MessageRewardClaimed(long rewardId) {
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

    public static class Handler implements IMessageHandler<MessageRewardClaimed, IMessage> {

        @Override
        public IMessage onMessage(MessageRewardClaimed message, MessageContext ctx) {
            ClientQuests.onRewardClaimed(message.rewardId);
            return null;
        }
    }
}
