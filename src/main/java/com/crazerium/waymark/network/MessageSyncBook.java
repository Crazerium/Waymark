package com.crazerium.waymark.network;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.util.Compression;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** The whole quest book, sent on login and after /waymark reload. */
public class MessageSyncBook implements IMessage {

    private String json;

    public MessageSyncBook() {}

    public MessageSyncBook(QuestBook book) {
        JsonArray chapters = new JsonArray();
        for (Chapter chapter : book.getChapters()) {
            JsonObject c = chapter.toJson();
            c.addProperty("filename", chapter.getFilename());
            chapters.add(c);
        }
        JsonObject root = new JsonObject();
        root.add("chapters", chapters);
        json = root.toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Compression.writeString(buf, json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        json = Compression.readString(buf);
    }

    public QuestBook toBook() {
        QuestBook book = new QuestBook();
        JsonObject root = new JsonParser().parse(json)
            .getAsJsonObject();
        for (JsonElement e : JsonUtil.getArray(root, "chapters")) {
            JsonObject c = e.getAsJsonObject();
            Chapter.fromJson(book, JsonUtil.getString(c, "filename", ""), c);
        }
        return book;
    }

    public static class Handler implements IMessageHandler<MessageSyncBook, IMessage> {

        @Override
        public IMessage onMessage(MessageSyncBook message, MessageContext ctx) {
            ClientQuests.setBook(message.toBook());
            return null;
        }
    }
}
