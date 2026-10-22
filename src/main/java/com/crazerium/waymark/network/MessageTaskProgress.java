package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageTaskProgress implements IMessage {

    private long taskId;
    private long progress;

    public MessageTaskProgress() {}

    public MessageTaskProgress(long taskId, long progress) {
        this.taskId = taskId;
        this.progress = progress;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(taskId);
        buf.writeLong(progress);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        taskId = buf.readLong();
        progress = buf.readLong();
    }

    public static class Handler implements IMessageHandler<MessageTaskProgress, IMessage> {

        @Override
        public IMessage onMessage(MessageTaskProgress message, MessageContext ctx) {
            ClientQuests.onTaskProgress(message.taskId, message.progress);
            return null;
        }
    }
}
