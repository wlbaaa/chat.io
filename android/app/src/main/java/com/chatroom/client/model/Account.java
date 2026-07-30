package com.chatroom.client.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 用户账号模型。
 * 字段：user / nick / pass(role hash) / role / createdAt
 */
public class Account {

    private String user;
    private String nick;
    private String pass;
    private String role;
    private long createdAt;

    public Account() {
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("user", user != null ? user : "");
            json.put("nick", nick != null ? nick : "");
            json.put("pass", pass != null ? pass : "");
            json.put("role", role != null ? role : "user");
            json.put("createdAt", createdAt);
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    public static Account fromJson(JSONObject json) {
        Account account = new Account();
        account.user = json.optString("user", "");
        account.nick = json.optString("nick", "");
        account.pass = json.optString("pass", "");
        account.role = json.optString("role", "user");
        account.createdAt = json.optLong("createdAt", 0);
        return account;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
