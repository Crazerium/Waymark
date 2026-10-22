package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageQuestCompleted implements IMessage {

    private long questId;
    private long time;

    public MessageQuestCompleted() {}

    public MessageQuestCompleted(long questId, long time) {
        this.questId = questId;
        this.time = time;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(questId);
        buf.writeLong(time);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        questId = buf.readLong();
        time = buf.readLong();
    }

    public static class Handler implements IMessageHandler<MessageQuestCompleted, IMessage> {

        @Override
        public IMessage onMessage(MessageQuestCompleted message, MessageContext ctx) {
            ClientQuests.onQuestCompleted(message.questId, message.time);
            return null;
        }
    }
}
