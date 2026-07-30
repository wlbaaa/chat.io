package com.chatroom.client.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.chatroom.client.model.Account;
import com.chatroom.client.model.ChatGroup;
import com.chatroom.client.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 基于 SharedPreferences 的本地数据存储。
 * 负责账号、会话、群注册表以及用户群列表/消息的持久化。
 */
public class DataStore {

    private static final String PREFS_NAME = "chatroom_prefs";
    private static final String KEY_ACCOUNTS = "chatroom.accounts.v1";
    private static final String KEY_SESSION = "chatroom.session.v1";
    private static final String KEY_REGISTRY = "chatroom.groups.v1";
    private static final String KEY_GROUPS_PREFIX = "chatroom.state.v1.";

    private static DataStore instance;
    private final SharedPreferences prefs;

    private DataStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized DataStore getInstance(Context context) {
        if (instance == null) {
            instance = new DataStore(context);
        }
        return instance;
    }

    // ==================== Password Hash ====================

    /**
     * djb2 hash，与 web 版一致。
     * long h=5381; for each char: h=((h<<5)+h)^char; return Long.toUnsignedString(h,16);
     */
    public static String hashPass(String input) {
        if (input == null) {
            input = "";
        }
        long h = 5381;
        for (int i = 0; i < input.length(); i++) {
            h = ((h << 5) + h) ^ input.charAt(i);
        }
        return Long.toUnsignedString(h, 16);
    }

    // ==================== Accounts ====================

    /**
     * 从 KEY_ACCOUNTS 读取所有账号，返回 user -> Account 的映射。
     */
    public Map<String, Account> loadAccounts() {
        Map<String, Account> accounts = new HashMap<>();
        String json = prefs.getString(KEY_ACCOUNTS, "");
        if (json == null || json.isEmpty()) {
            return accounts;
        }
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject accountJson = obj.optJSONObject(key);
                if (accountJson != null) {
                    accounts.put(key, Account.fromJson(accountJson));
                }
            }
        } catch (JSONException e) {
            // ignore corrupt data
        }
        return accounts;
    }

    /**
     * 保存全部账号到 KEY_ACCOUNTS。
     */
    public void saveAccounts(Map<String, Account> accounts) {
        JSONObject obj = new JSONObject();
        if (accounts != null) {
            for (Map.Entry<String, Account> entry : accounts.entrySet()) {
                try {
                    obj.put(entry.getKey(), entry.getValue().toJson());
                } catch (JSONException e) {
                    // ignore
                }
            }
        }
        prefs.edit().putString(KEY_ACCOUNTS, obj.toString()).apply();
    }

    /**
     * 获取指定用户名的账号，不存在返回 null。
     */
    public Account getAccount(String user) {
        if (user == null) {
            return null;
        }
        return loadAccounts().get(user);
    }

    // ==================== Session ====================

    /**
     * 获取当前会话用户名，无会话返回 null。
     */
    public String getSession() {
        return prefs.getString(KEY_SESSION, null);
    }

    /**
     * 设置当前会话用户名。传入 null 清除会话。
     */
    public void setSession(String user) {
        SharedPreferences.Editor editor = prefs.edit();
        if (user == null) {
            editor.remove(KEY_SESSION);
        } else {
            editor.putString(KEY_SESSION, user);
        }
        editor.apply();
    }

    // ==================== Group Registry ====================

    /**
     * 从 KEY_REGISTRY 读取群注册表，返回 code -> ChatGroup 的映射。
     */
    public Map<String, ChatGroup> loadGroupRegistry() {
        Map<String, ChatGroup> registry = new HashMap<>();
        String json = prefs.getString(KEY_REGISTRY, "");
        if (json == null || json.isEmpty()) {
            return registry;
        }
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject groupJson = obj.optJSONObject(key);
                if (groupJson != null) {
                    registry.put(key, ChatGroup.fromJson(groupJson));
                }
            }
        } catch (JSONException e) {
            // ignore corrupt data
        }
        return registry;
    }

    private void saveGroupRegistry(Map<String, ChatGroup> registry) {
        JSONObject obj = new JSONObject();
        if (registry != null) {
            for (Map.Entry<String, ChatGroup> entry : registry.entrySet()) {
                try {
                    obj.put(entry.getKey(), entry.getValue().toJson());
                } catch (JSONException e) {
                    // ignore
                }
            }
        }
        prefs.edit().putString(KEY_REGISTRY, obj.toString()).apply();
    }

    /**
     * 检查加入码是否已被注册。
     */
    public boolean isCodeTaken(String code) {
        if (code == null) {
            return false;
        }
        return loadGroupRegistry().containsKey(code);
    }

    /**
     * 注册新群聊到全局注册表，并添加到 owner 的群列表。
     *
     * @param code  加入码
     * @param name  群名称
     * @param icon  群图标
     * @param owner 群主用户名
     * @return 创建的 ChatGroup
     */
    public ChatGroup registerGroup(String code, String name, String icon, String owner) {
        long now = System.currentTimeMillis();

        // 写入注册表
        Map<String, ChatGroup> registry = loadGroupRegistry();
        ChatGroup regGroup = new ChatGroup();
        regGroup.setId(code);
        regGroup.setCode(code);
        regGroup.setName(name);
        regGroup.setIcon(icon);
        regGroup.setCreatedAt(now);
        regGroup.setMessages(new ArrayList<ChatMessage>());
        registry.put(code, regGroup);
        saveGroupRegistry(registry);

        // 添加到 owner 的群列表（避免重复）
        List<ChatGroup> groups = loadGroups(owner);
        boolean exists = false;
        for (ChatGroup g : groups) {
            if (code.equals(g.getCode())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            ChatGroup userGroup = new ChatGroup();
            userGroup.setId(code);
            userGroup.setCode(code);
            userGroup.setName(name);
            userGroup.setIcon(icon);
            userGroup.setCreatedAt(now);
            userGroup.setMessages(new ArrayList<ChatMessage>());
            groups.add(userGroup);
            saveGroups(owner, groups);
        }

        return regGroup;
    }

    // ==================== User Groups ====================

    /**
     * 从 "chatroom.state.v1.{user}" 读取用户的群列表。
     */
    public List<ChatGroup> loadGroups(String user) {
        List<ChatGroup> groups = new ArrayList<>();
        if (user == null) {
            return groups;
        }
        String json = prefs.getString(KEY_GROUPS_PREFIX + user, "");
        if (json == null || json.isEmpty()) {
            return groups;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject groupJson = arr.optJSONObject(i);
                if (groupJson != null) {
                    groups.add(ChatGroup.fromJson(groupJson));
                }
            }
        } catch (JSONException e) {
            // ignore corrupt data
        }
        return groups;
    }

    /**
     * 保存用户的群列表到 "chatroom.state.v1.{user}"。
     */
    public void saveGroups(String user, List<ChatGroup> groups) {
        if (user == null) {
            return;
        }
        JSONArray arr = new JSONArray();
        if (groups != null) {
            for (ChatGroup group : groups) {
                arr.put(group.toJson());
            }
        }
        prefs.edit().putString(KEY_GROUPS_PREFIX + user, arr.toString()).apply();
    }

    /**
     * 向用户的指定群追加一条消息。
     *
     * @param user    用户名
     * @param groupId 群 ID（与 code 相同）
     * @param message 要追加的消息
     */
    public void appendMessage(String user, String groupId, ChatMessage message) {
        if (user == null || groupId == null || message == null) {
            return;
        }
        List<ChatGroup> groups = loadGroups(user);
        for (ChatGroup group : groups) {
            if (groupId.equals(group.getId()) || groupId.equals(group.getCode())) {
                group.getMessages().add(message);
                saveGroups(user, groups);
                return;
            }
        }
    }
}
