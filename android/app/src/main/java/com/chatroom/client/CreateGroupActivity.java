package com.chatroom.client;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chatroom.client.model.ChatGroup;
import com.chatroom.client.storage.DataStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Random;

/**
 * 创建 / 加入群聊界面。
 * - 创建：群名 + 图标 + 加入码（可随机），校验后 registerGroup。
 * - 加入：输入加入码，校验已注册后加入本地群列表。
 */
public class CreateGroupActivity extends AppCompatActivity {

    /** 可选 emoji 图标列表 */
    private static final String[] ICONS = {
            "💬", "🔥", "🌟", "🎮", "🎵", "📚", "💻", "⚽", "🍕", "🐱",
            "🌈", "🚀", "☕", "🎨", "🏆", "🌙", "🎬", "📷", "🎲", "🌮",
            "🦊", "🌸", "⚡"
    };

    private DataStore dataStore;
    private String currentUser;

    private MaterialButtonToggleGroup modeToggle;
    private View createForm;
    private View joinForm;
    private TextInputEditText etGroupName;
    private TextInputEditText etGroupCode;
    private TextInputEditText etJoinCode;
    private LinearLayout iconRow;

    private String selectedIcon = ICONS[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        dataStore = DataStore.getInstance(getApplicationContext());
        currentUser = dataStore.getSession();

        bindViews();
        setupToolbar();
        setupToggle();
        setupIcons();
        setupButtons();
    }

    private void bindViews() {
        modeToggle = findViewById(R.id.modeToggle);
        createForm = findViewById(R.id.createForm);
        joinForm = findViewById(R.id.joinForm);
        etGroupName = findViewById(R.id.etGroupName);
        etGroupCode = findViewById(R.id.etGroupCode);
        etJoinCode = findViewById(R.id.etJoinCode);
        iconRow = findViewById(R.id.iconRow);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void setupToggle() {
        modeToggle.check(R.id.btnModeCreate);
        applyMode(true);
        modeToggle.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (!isChecked) {
                    return;
                }
                if (checkedId == R.id.btnModeCreate) {
                    applyMode(true);
                } else if (checkedId == R.id.btnModeJoin) {
                    applyMode(false);
                }
            }
        });
    }

    private void applyMode(boolean create) {
        createForm.setVisibility(create ? View.VISIBLE : View.GONE);
        joinForm.setVisibility(create ? View.GONE : View.VISIBLE);
    }

    /** 动态构建 emoji 图标选择条 */
    private void setupIcons() {
        iconRow.removeAllViews();
        for (int i = 0; i < ICONS.length; i++) {
            final String icon = ICONS[i];
            TextView tv = new TextView(this);
            tv.setText(icon);
            tv.setTextSize(26f);
            tv.setGravity(Gravity.CENTER);
            int pad = (int) (getResources().getDisplayMetrics().density * 8);
            tv.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(pad);
            tv.setLayoutParams(lp);
            tv.setBackground(getResources().getDrawable(R.drawable.bg_icon_selector));
            tv.setSelected(icon.equals(selectedIcon));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedIcon = icon;
                    updateIconSelection();
                }
            });
            iconRow.addView(tv);
        }
    }

    private void updateIconSelection() {
        for (int i = 0; i < iconRow.getChildCount(); i++) {
            View child = iconRow.getChildAt(i);
            if (child instanceof TextView) {
                child.setSelected(((TextView) child).getText().toString().equals(selectedIcon));
            }
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnRandomCode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etGroupCode.setText(randomCode());
            }
        });
        findViewById(R.id.btnCreate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCreate();
            }
        });
        findViewById(R.id.btnJoin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doJoin();
            }
        });
    }

    /** 创建群聊 */
    private void doCreate() {
        String name = textOf(etGroupName);
        String code = textOf(etGroupCode).toUpperCase();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入群名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, "加入码需为 6 位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dataStore.isCodeTaken(code)) {
            Toast.makeText(this, "加入码已被使用，请换一个", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(currentUser)) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 注册群聊（registerGroup 会写入注册表并加入 owner 群列表）
        dataStore.registerGroup(code, name, selectedIcon, currentUser);
        Toast.makeText(this, "群聊创建成功", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    /** 加入群聊 */
    private void doJoin() {
        String code = textOf(etJoinCode).toUpperCase();
        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, "请输入 6 位加入码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dataStore.isCodeTaken(code)) {
            Toast.makeText(this, "加入码不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(currentUser)) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 从注册表获取群信息，加入本地列表（避免重复）
        ChatGroup registered = dataStore.loadGroupRegistry().get(code);
        String name = registered != null ? registered.getName() : code;
        String icon = registered != null ? registered.getIcon() : "💬";

        List<ChatGroup> groups = dataStore.loadGroups(currentUser);
        for (ChatGroup g : groups) {
            if (code.equals(g.getCode())) {
                Toast.makeText(this, "已加入该群聊", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
                return;
            }
        }
        ChatGroup userGroup = new ChatGroup();
        userGroup.setId(code);
        userGroup.setCode(code);
        userGroup.setName(name);
        userGroup.setIcon(icon);
        userGroup.setCreatedAt(System.currentTimeMillis());
        groups.add(userGroup);
        dataStore.saveGroups(currentUser, groups);

        Toast.makeText(this, "已加入群聊", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    /** 生成 6 位大写字母 + 数字随机码 */
    @NonNull
    private String randomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @NonNull
    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
