package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** A repeatable quest of the player's team was reset and can be done again. */
public class MessageQuestReset implements IMessage {

    private long questId;

    public MessageQuestReset() {}

    public MessageQuestReset(long questId) {
        this.questId = questId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(questId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        questId = buf.readLong();
    }

    public static class Handler implements IMessageHandler<MessageQuestReset, IMessage> {

        @Override
        public IMessage onMessage(MessageQuestReset message, MessageContext ctx) {
            ClientQuests.onQuestReset(message.questId);
            return null;
        }
    }
}
