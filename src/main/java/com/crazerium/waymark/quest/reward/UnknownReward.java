package com.crazerium.waymark.quest.reward;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A reward whose type is not known. It gives nothing, but its data is written back unchanged. */
public class UnknownReward extends Reward {

    private static final RewardType<UnknownReward> TYPE = RewardType.unregistered("unknown", UnknownReward::new);

    private JsonObject raw = new JsonObject();

    public UnknownReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return TYPE;
    }

    @Override
    public String getTitle() {
        return "§cUnknown reward: " + JsonUtil.getString(raw, "type", "?");
    }

    @Override
    public void claim(EntityPlayerMP player, int choice) {}

    @Override
    public void writeJson(JsonObject json) {
        for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
            if (!e.getKey()
                .equals("id")) json.add(e.getKey(), e.getValue());
        }
    }

    @Override
    public void readJson(JsonObject json) {
        raw = json;
    }
}
