package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.util.Compression;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** All progress of the player's team. Later changes arrive as small update messages. */
public class MessageSyncProgress implements IMessage {

    private String json;

    public MessageSyncProgress() {}

    public MessageSyncProgress(TeamProgress team) {
        json = team.toJson()
            .toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Compression.writeString(buf, json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        json = Compression.readString(buf);
    }

    public static class Handler implements IMessageHandler<MessageSyncProgress, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncProgress message, MessageContext ctx) {
            JsonObject json = new JsonParser().parse(message.json)
                .getAsJsonObject();
            ClientQuests.setProgress(json);
            return null;
        }
    }
}
