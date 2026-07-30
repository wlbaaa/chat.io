package com.chatroom.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chatroom.client.adapter.GroupAdapter;
import com.chatroom.client.model.Account;
import com.chatroom.client.model.ChatGroup;
import com.chatroom.client.model.ChatMessage;
import com.chatroom.client.mqtt.MqttManager;
import com.chatroom.client.storage.DataStore;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

/**
 * 群列表界面。
 * 展示用户加入的群，支持创建/加入、进入聊天、退出群聊，
 * 并通过 MQTT 接收实时消息刷新列表预览。
 */
public class GroupListActivity extends AppCompatActivity implements MqttManager.Listener {

    private static final int REQ_CREATE_GROUP = 1001;

    private DataStore dataStore;
    private MqttManager mqtt;
    private String currentUser;
    private String currentNick;

    private RecyclerView rvGroups;
    private View emptyState;
    private GroupAdapter adapter;
    private final List<ChatGroup> groups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);

        dataStore = DataStore.getInstance(getApplicationContext());
        currentUser = dataStore.getSession();

        // 未登录则回到登录页
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        Account account = dataStore.getAccount(currentUser);
        currentNick = account != null ? account.getNick() : currentUser;

        bindViews();
        setupToolbar();
        setupAdapter();
    }

    private void bindViews() {
        rvGroups = findViewById(R.id.rvGroups);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(currentNick);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) {
                startActivityForResult(
                        new Intent(this, CreateGroupActivity.class), REQ_CREATE_GROUP);
                return true;
            }
            return false;
        });
    }

    private void setupAdapter() {
        adapter = new GroupAdapter();
        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvGroups.setAdapter(adapter);

        // 点击进入聊天
        adapter.setOnItemClickListener((group, position) -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("groupId", group.getId());
            intent.putExtra("groupCode", group.getCode());
            intent.putExtra("groupName", group.getName());
            startActivity(intent);
        });

        // 退出群聊
        adapter.setOnLeaveClickListener((group, position) -> confirmLeave(group));
    }

    /** 退出确认对话框 */
    private void confirmLeave(ChatGroup group) {
        new AlertDialog.Builder(this)
                .setTitle("退出群聊")
                .setMessage("确定退出 \"" + group.getName() + "\" 吗？")
                .setPositiveButton("退出", (dialog, which) -> leaveGroup(group))
                .setNegativeButton("取消", null)
                .show();
    }

    private void leaveGroup(ChatGroup group) {
        String code = group.getCode();
        // 取消订阅
        if (mqtt != null && mqtt.isConnected() && code != null) {
            mqtt.unsubscribe(code);
        }
        // 从本地列表移除
        List<ChatGroup> list = dataStore.loadGroups(currentUser);
        List<ChatGroup> remaining = new ArrayList<>();
        for (ChatGroup g : list) {
            if (code == null || !code.equals(g.getCode())) {
                remaining.add(g);
            }
        }
        dataStore.saveGroups(currentUser, remaining);
        loadGroups();
    }

    /** 从存储重新加载群列表 */
    private void loadGroups() {
        groups.clear();
        groups.addAll(dataStore.loadGroups(currentUser));
        adapter.setGroups(groups);
        emptyState.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置 MQTT 监听并连接
        mqtt = MqttManager.getInstance();
        mqtt.setListener(this);
        mqtt.setCurrentNick(currentNick);
        mqtt.connect(getApplicationContext());

        loadGroups();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂时清除监听，避免 ChatActivity 覆盖后仍回调到本页
        if (mqtt != null) {
            mqtt.setListener(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CREATE_GROUP && resultCode == RESULT_OK) {
            loadGroups();
            // 若已连接，订阅所有群（包含新建的）
            if (mqtt != null && mqtt.isConnected()) {
                subscribeAllGroups();
            }
        }
    }

    /** 订阅当前用户加入的全部群 */
    private void subscribeAllGroups() {
        if (mqtt == null || !mqtt.isConnected()) {
            return;
        }
        for (ChatGroup g : groups) {
            if (g.getCode() != null) {
                mqtt.subscribe(g.getCode());
            }
        }
    }

    // ==================== MqttManager.Listener ====================

    @Override
    public void onConnected() {
        // 连接成功后订阅所有群
        subscribeAllGroups();
    }

    @Override
    public void onDisconnected() {
        // 自动重连会处理，这里无需额外操作
    }

    @Override
    public void onMessage(@NonNull String groupCode, @NonNull ChatMessage message) {
        // 持久化消息
        dataStore.appendMessage(currentUser, groupCode, message);
        // 刷新对应群在列表中的预览
        for (int i = 0; i < groups.size(); i++) {
            ChatGroup g = groups.get(i);
            if (groupCode.equals(g.getCode())) {
                // 重新从存储读取该群最新数据
                List<ChatGroup> latest = dataStore.loadGroups(currentUser);
                for (ChatGroup lg : latest) {
                    if (groupCode.equals(lg.getCode())) {
                        groups.set(i, lg);
                        adapter.updateGroup(lg);
                        break;
                    }
                }
                break;
            }
        }
    }
}
