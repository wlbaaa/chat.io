package com.chatroom.client.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 群聊模型。
 * 字段：id / code / name / icon / createdAt / messages 列表
 */
public class ChatGroup {

    private String id;
    private String code;
    private String name;
    private String icon;
    private long createdAt;
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatGroup() {
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id != null ? id : "");
            json.put("code", code != null ? code : "");
            json.put("name", name != null ? name : "");
            json.put("icon", icon != null ? icon : "");
            json.put("createdAt", createdAt);
            JSONArray msgArray = new JSONArray();
            if (messages != null) {
                for (ChatMessage msg : messages) {
                    msgArray.put(msg.toJson());
                }
            }
            json.put("messages", msgArray);
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    public static ChatGroup fromJson(JSONObject json) {
        ChatGroup group = new ChatGroup();
        group.id = json.optString("id", "");
        group.code = json.optString("code", "");
        group.name = json.optString("name", "");
        group.icon = json.optString("icon", "");
        group.createdAt = json.optLong("createdAt", 0);
        JSONArray msgArray = json.optJSONArray("messages");
        if (msgArray != null) {
            for (int i = 0; i < msgArray.length(); i++) {
                JSONObject msgJson = msgArray.optJSONObject(i);
                if (msgJson != null) {
                    group.messages.add(ChatMessage.fromJson(msgJson));
                }
            }
        }
        return group;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<ChatMessage>();
    }
}
