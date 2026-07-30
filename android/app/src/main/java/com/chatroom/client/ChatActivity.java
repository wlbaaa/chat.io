package com.chatroom.client;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chatroom.client.adapter.MessageAdapter;
import com.chatroom.client.model.Account;
import com.chatroom.client.model.ChatGroup;
import com.chatroom.client.model.ChatMessage;
import com.chatroom.client.mqtt.MqttManager;
import com.chatroom.client.storage.DataStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天界面。
 * 订阅指定群聊话题，加载历史消息，发送并接收实时消息。
 */
public class ChatActivity extends AppCompatActivity implements MqttManager.Listener {

    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_GROUP_CODE = "groupCode";
    public static final String EXTRA_GROUP_NAME = "groupName";

    private DataStore dataStore;
    private MqttManager mqtt;
    private String currentUser;
    private String currentNick;
    private String groupId;
    private String groupCode;
    private String groupName;

    private RecyclerView rvMessages;
    private TextInputEditText etInput;
    private MaterialButton btnSend;
    private MessageAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 读取 intent 参数
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        groupCode = getIntent().getStringExtra(EXTRA_GROUP_CODE);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);

        dataStore = DataStore.getInstance(getApplicationContext());
        currentUser = dataStore.getSession();
        Account account = currentUser != null ? dataStore.getAccount(currentUser) : null;
        currentNick = account != null ? account.getNick() : currentUser;

        bindViews();
        setupToolbar();
        setupAdapter();
        loadMessages();
    }

    private void bindViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(groupName != null ? groupName : "群聊");
        toolbar.setNavigationOnClickListener(v -> finish());
        // 副标题显示加入码
        TextView tvSubTitle = findViewById(R.id.tvSubTitle);
        tvSubTitle.setVisibility(View.VISIBLE);
        tvSubTitle.setText("加入码: " + (groupCode != null ? groupCode : ""));

        btnSend.setOnClickListener(v -> onSend());
    }

    private void setupAdapter() {
        adapter = new MessageAdapter(currentNick);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    /** 加载历史消息 */
    private void loadMessages() {
        messages.clear();
        if (currentUser != null) {
            for (ChatGroup g : dataStore.loadGroups(currentUser)) {
                if ((groupId != null && groupId.equals(g.getId()))
                        || (groupCode != null && groupCode.equals(g.getCode()))) {
                    messages.addAll(g.getMessages());
                    break;
                }
            }
        }
        adapter.setMessages(messages);
        scrollToBottom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置当前页为 MQTT 监听者并连接（连接成功后在 onConnected 中订阅）
        mqtt = MqttManager.getInstance();
        mqtt.setListener(this);
        mqtt.setCurrentNick(currentNick);
        mqtt.connect(getApplicationContext());
    }

    /** 发送消息 */
    private void onSend() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text) || groupCode == null || currentNick == null) {
            return;
        }
        // 本地立即追加
        ChatMessage msg = ChatMessage.chat(currentNick, text);
        messages.add(msg);
        adapter.addMessage(msg);
        dataStore.appendMessage(currentUser, groupCode, msg);
        // 发布到 MQTT
        mqtt.publishChat(groupCode, currentNick, text);
        etInput.setText("");
        scrollToBottom();
    }

    /** 滚动到底部 */
    private void scrollToBottom() {
        rvMessages.post(() -> {
            int count = adapter.getItemCount();
            if (count > 0) {
                rvMessages.smoothScrollToPosition(count - 1);
            }
        });
    }

    // ==================== MqttManager.Listener ====================

    @Override
    public void onConnected() {
        // 订阅当前群并发布加入消息
        if (groupCode != null) {
            mqtt.subscribe(groupCode);
            mqtt.publishJoin(groupCode, currentNick);
        }
    }

    @Override
    public void onDisconnected() {
        // 自动重连会处理
    }

    @Override
    public void onMessage(@NonNull String code, @NonNull ChatMessage message) {
        // 只处理当前群聊
        if (groupCode == null || !groupCode.equals(code)) {
            return;
        }
        // 过滤自身发送的聊天消息：onSend 已本地追加，避免重复显示
        if ("chat".equals(message.getType())
                && currentNick != null && currentNick.equals(message.getNick())) {
            return;
        }
        messages.add(message);
        adapter.addMessage(message);
        dataStore.appendMessage(currentUser, groupCode, message);
        scrollToBottom();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 离开页面时取消订阅当前群
        if (mqtt != null && groupCode != null) {
            mqtt.unsubscribe(groupCode);
            mqtt.setListener(null);
        }
    }
}
