package com.chatroom.client.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * 消息模型，与 web 版 JSON 格式兼容。
 * 字段：type("chat"|"sys") / id / nick / text / ts
 */
public class ChatMessage {

    /** 消息类型："chat" 或 "sys" */
    private String type;
    private String id;
    private String nick;
    private String text;
    private long ts;

    public ChatMessage() {
    }

    /**
     * 构造一条聊天消息。
     */
    public static ChatMessage chat(String nick, String text) {
        ChatMessage msg = new ChatMessage();
        msg.type = "chat";
        msg.id = UUID.randomUUID().toString();
        msg.nick = nick != null ? nick : "";
        msg.text = text != null ? text : "";
        msg.ts = System.currentTimeMillis();
        return msg;
    }

    /**
     * 构造一条系统消息。
     */
    public static ChatMessage sys(String text) {
        ChatMessage msg = new ChatMessage();
        msg.type = "sys";
        msg.id = UUID.randomUUID().toString();
        msg.nick = "";
        msg.text = text != null ? text : "";
        msg.ts = System.currentTimeMillis();
        return msg;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type != null ? type : "chat");
            json.put("id", id != null ? id : "");
            json.put("nick", nick != null ? nick : "");
            json.put("text", text != null ? text : "");
            json.put("ts", ts);
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    public static ChatMessage fromJson(JSONObject json) {
        ChatMessage msg = new ChatMessage();
        msg.type = json.optString("type", "chat");
        msg.id = json.optString("id", "");
        msg.nick = json.optString("nick", "");
        msg.text = json.optString("text", "");
        msg.ts = json.optLong("ts", 0);
        return msg;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }
}
