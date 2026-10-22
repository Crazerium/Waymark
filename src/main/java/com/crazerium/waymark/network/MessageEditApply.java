package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** An editor change the server applied; every client applies it to its own copy of the book. */
public class MessageEditApply implements IMessage {

    private String json;

    public MessageEditApply() {}

    public MessageEditApply(JsonObject op) {
        json = op.toString();
    }

    public JsonObject op() {
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        json = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<MessageEditApply, IMessage> {

        @Override
        public IMessage onMessage(MessageEditApply message, MessageContext ctx) {
            ClientQuests.applyEdit(message.op());
            return null;
        }
    }
}
