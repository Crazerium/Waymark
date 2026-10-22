package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Tells the client whether its player may use the editor, so the book can show the edit button. */
public class MessageCanEdit implements IMessage {

    private boolean canEdit;

    public MessageCanEdit() {}

    public MessageCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(canEdit);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        canEdit = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<MessageCanEdit, IMessage> {

        @Override
        public IMessage onMessage(MessageCanEdit message, MessageContext ctx) {
            ClientQuests.setCanEdit(message.canEdit);
            return null;
        }
    }
}
