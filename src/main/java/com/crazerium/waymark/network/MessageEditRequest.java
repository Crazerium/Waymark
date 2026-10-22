package com.crazerium.waymark.network;

import com.crazerium.waymark.server.ServerEditor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** An editor change sent by an operator's client; the server checks, applies and rebroadcasts it. */
public class MessageEditRequest implements IMessage {

    private String json;

    public MessageEditRequest() {}

    public MessageEditRequest(JsonObject op) {
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

    public static class Handler implements IMessageHandler<MessageEditRequest, IMessage> {

        @Override
        public IMessage onMessage(MessageEditRequest message, MessageContext ctx) {
            ServerEditor.handle(ctx.getServerHandler().playerEntity, message.op());
            return null;
        }
    }
}
