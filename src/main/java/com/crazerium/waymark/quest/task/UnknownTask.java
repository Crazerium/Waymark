package com.crazerium.waymark.quest.task;

import java.util.Map;

import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A task whose type is not known (e.g. from an addon that is not installed). Its data is written back unchanged. */
public class UnknownTask extends Task {

    private static final TaskType<UnknownTask> TYPE = TaskType.unregistered("unknown", UnknownTask::new);

    private JsonObject raw = new JsonObject();

    public UnknownTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return TYPE;
    }

    @Override
    public String getTitle() {
        return "§cUnknown task: " + JsonUtil.getString(raw, "type", "?");
    }

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
