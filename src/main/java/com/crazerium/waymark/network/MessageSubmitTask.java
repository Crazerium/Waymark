package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerProgress;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The player pressed a task's submit button (hand in items, tick a checkmark). */
public class MessageSubmitTask implements IMessage {

    private long taskId;

    public MessageSubmitTask() {}

    public MessageSubmitTask(long taskId) {
        this.taskId = taskId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(taskId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        taskId = buf.readLong();
    }

    public static class Handler implements IMessageHandler<MessageSubmitTask, IMessage> {

        @Override
        public IMessage onMessage(MessageSubmitTask message, MessageContext ctx) {
            ServerProgress.submit(ctx.getServerHandler().playerEntity, message.taskId);
            return null;
        }
    }
}
