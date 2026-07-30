package com.chatroom.client.mqtt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.chatroom.client.model.ChatMessage;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

/**
 * MQTT 管理器（单例）。
 * 连接 ssl://broker.emqx.io:8883，TOPIC_PREFIX="trae-chatroom-v1/"。
 * 与 web 版互通：chat / join / here 消息格式完全兼容。
 */
public class MqttManager {

    private static final String TAG = "MqttManager";
    private static final String BROKER = "ssl://broker.emqx.io:8883";
    public static final String TOPIC_PREFIX = "trae-chatroom-v1/";
    private static final String PRESENCE_SUFFIX = "/presence";

    private static MqttManager instance;

    private MqttAsyncClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private String currentNick;

    /** 回调接口，所有方法都在主线程调用。 */
    public interface Listener {
        void onConnected();

        void onDisconnected();

        void onMessage(String groupCode, ChatMessage message);
    }

    private MqttManager() {
    }

    public static synchronized MqttManager getInstance() {
        if (instance == null) {
            instance = new MqttManager();
        }
        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * 设置当前用户昵称，用于过滤自身消息及自动回复 "here" presence。
     */
    public void setCurrentNick(String nick) {
        this.currentNick = nick;
    }

    // ==================== Connection ====================

    /**
     * 异步连接 MQTT broker。使用 SSLSocketFactory，启用自动重连。
     * 连接结果通过 Listener 回调通知。
     */
    public void connect(Context context) {
        try {
            if (client == null) {
                String clientId = "chatroom-android-" + UUID.randomUUID().toString().substring(0, 8);
                client = new MqttAsyncClient(BROKER, clientId, new MemoryPersistence());
                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        Log.w(TAG, "Connection lost", cause);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onDisconnected();
                                }
                            }
                        });
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        handleMessage(topic, message);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // no-op
                    }
                });
            }

            if (client.isConnected()) {
                notifyConnected();
                return;
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setSocketFactory(SSLSocketFactory.getDefault());
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Connected to MQTT broker");
                    notifyConnected();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to connect", exception);
                    notifyDisconnected();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "connect error", e);
            notifyDisconnected();
        }
    }

    private void notifyConnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onConnected();
                }
            }
        });
    }

    private void notifyDisconnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
        });
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * 断开连接。
     */
    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "disconnect error", e);
            }
        }
    }

    // ==================== Subscribe / Unsubscribe ====================

    /**
     * 订阅指定群聊的主话题和 presence 话题。
     * - trae-chatroom-v1/{code}        （聊天 + join 消息）
     * - trae-chatroom-v1/{code}/presence （here / who presence 消息）
     */
    public void subscribe(String code) {
        if (client == null || !client.isConnected() || code == null) {
            return;
        }
        try {
            String chatTopic = TOPIC_PREFIX + code;
            String presenceTopic = TOPIC_PREFIX + code + PRESENCE_SUFFIX;
            client.subscribe(chatTopic, 0);
            client.subscribe(presenceTopic, 0);
            Log.i(TAG, "Subscribed to " + chatTopic + " and " + presenceTopic);
        } catch (MqttException e) {
            Log.e(TAG, "subscribe error", e);
        }
    }

    /**
     * 取消订阅指定群聊的所有话题。
     */
    public void unsubscribe(String code) {
        if (client == null || !client.isConnected() || code == null) {
            return;
        }
        try {
            String chatTopic = TOPIC_PREFIX + code;
            String presenceTopic = TOPIC_PREFIX + code + PRESENCE_SUFFIX;
            client.unsubscribe(chatTopic);
            client.unsubscribe(presenceTopic);
            Log.i(TAG, "Unsubscribed from " + chatTopic + " and " + presenceTopic);
        } catch (MqttException e) {
            Log.e(TAG, "unsubscribe error", e);
        }
    }

    // ==================== Publish ====================

    /**
     * 发送聊天消息到 {code} 主话题。
     * 消息格式：{type:"chat", id, nick, text, ts}
     */
    public void publishChat(String code, String nick, String text) {
        if (client == null || !client.isConnected() || code == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", "chat");
            json.put("id", UUID.randomUUID().toString());
            json.put("nick", nick != null ? nick : "");
            json.put("text", text != null ? text : "");
            json.put("ts", System.currentTimeMillis());
            MqttMessage message = new MqttMessage(json.toString().getBytes("UTF-8"));
            message.setQos(0);
            client.publish(TOPIC_PREFIX + code, message);
        } catch (Exception e) {
            Log.e(TAG, "publishChat error", e);
        }
    }

    /**
     * 发送 join 消息到 {code} 主话题（与 web 版一致）。
     * 消息格式：{type:"join", nick, ts}
     */
    public void publishJoin(String code, String nick) {
        if (client == null || !client.isConnected() || code == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", "join");
            json.put("nick", nick != null ? nick : "");
            json.put("ts", System.currentTimeMillis());
            MqttMessage message = new MqttMessage(json.toString().getBytes("UTF-8"));
            message.setQos(0);
            client.publish(TOPIC_PREFIX + code, message);
        } catch (Exception e) {
            Log.e(TAG, "publishJoin error", e);
        }
    }

    /**
     * 发送 presence 消息到 {code}/presence 话题。
     */
    private void publishPresence(String code, String type, String nick) {
        if (client == null || !client.isConnected() || code == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("nick", nick != null ? nick : "");
            json.put("ts", System.currentTimeMillis());
            MqttMessage message = new MqttMessage(json.toString().getBytes("UTF-8"));
            message.setQos(0);
            client.publish(TOPIC_PREFIX + code + PRESENCE_SUFFIX, message);
        } catch (Exception e) {
            Log.e(TAG, "publishPresence error", e);
        }
    }

    // ==================== Message Handling ====================

    private void handleMessage(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload(), "UTF-8");
            JSONObject json = new JSONObject(payload);
            String type = json.optString("type", "");
            String groupCode = extractGroupCode(topic);
            if (groupCode == null) {
                return;
            }

            String nick = json.optString("nick", "");
            long ts = json.optLong("ts", System.currentTimeMillis());

            if ("chat".equals(type)) {
                // 聊天消息：直接转发
                ChatMessage chatMsg = ChatMessage.fromJson(json);
                notifyMessage(groupCode, chatMsg);

            } else if ("join".equals(type)) {
                // join 消息：自动回复 "here" presence（跳过自己的 join）
                if (currentNick == null || !currentNick.equals(nick)) {
                    publishPresence(groupCode, "here", currentNick);
                    ChatMessage sysMsg = ChatMessage.sys(nick + " 加入了群聊");
                    sysMsg.setTs(ts);
                    notifyMessage(groupCode, sysMsg);
                }

            } else if ("here".equals(type)) {
                // here presence：不自动回复（避免循环），通知 UI（跳过自己的 here）
                if (currentNick == null || !currentNick.equals(nick)) {
                    ChatMessage sysMsg = ChatMessage.sys(nick + " 在线");
                    sysMsg.setTs(ts);
                    notifyMessage(groupCode, sysMsg);
                }

            } else if ("who".equals(type)) {
                // who 请求：回复 "here"（跳过自己的 who），不通知 UI
                if (currentNick == null || !currentNick.equals(nick)) {
                    publishPresence(groupCode, "here", currentNick);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "handleMessage error", e);
        }
    }

    private void notifyMessage(final String groupCode, final ChatMessage message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onMessage(groupCode, message);
                }
            }
        });
    }

    /**
     * 从 topic 中提取群聊 code。
     * "trae-chatroom-v1/{code}"          -> {code}
     * "trae-chatroom-v1/{code}/presence"  -> {code}
     */
    private String extractGroupCode(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        String rest = topic.substring(TOPIC_PREFIX.length());
        if (rest.endsWith(PRESENCE_SUFFIX)) {
            rest = rest.substring(0, rest.length() - PRESENCE_SUFFIX.length());
        }
        if (rest.isEmpty()) {
            return null;
        }
        return rest;
    }
}
