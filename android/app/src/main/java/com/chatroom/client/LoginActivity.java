package com.chatroom.client;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chatroom.client.model.Account;
import com.chatroom.client.storage.DataStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录 / 注册界面。
 * 支持注册新账号和登录已有账号，校验通过后写入会话并跳转群列表。
 */
public class LoginActivity extends AppCompatActivity {

    private DataStore dataStore;

    private MaterialButtonToggleGroup modeToggle;
    private TextInputLayout tilUser;
    private TextInputLayout tilNick;
    private TextInputLayout tilPass;
    private TextInputEditText etUser;
    private TextInputEditText etNick;
    private TextInputEditText etPass;
    private TextView tvError;
    private MaterialButton btnSubmit;

    /** true=注册模式，false=登录模式 */
    private boolean isRegister = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dataStore = DataStore.getInstance(getApplicationContext());

        // 如果已有会话直接跳转
        String session = dataStore.getSession();
        if (session != null && dataStore.getAccount(session) != null) {
            startActivity(new Intent(this, GroupListActivity.class));
            finish();
            return;
        }

        bindViews();
        setupToggle();
    }

    private void bindViews() {
        modeToggle = findViewById(R.id.modeToggle);
        tilUser = findViewById(R.id.tilUser);
        tilNick = findViewById(R.id.tilNick);
        tilPass = findViewById(R.id.tilPass);
        etUser = findViewById(R.id.etUser);
        etNick = findViewById(R.id.etNick);
        etPass = findViewById(R.id.etPass);
        tvError = findViewById(R.id.tvError);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSubmit();
            }
        });
    }

    private void setupToggle() {
        // 默认登录模式
        modeToggle.check(R.id.btnModeLogin);
        applyMode(false);

        modeToggle.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (!isChecked) {
                    return;
                }
                if (checkedId == R.id.btnModeRegister) {
                    applyMode(true);
                } else if (checkedId == R.id.btnModeLogin) {
                    applyMode(false);
                }
            }
        });
    }

    /** 切换注册/登录模式 UI */
    private void applyMode(boolean register) {
        isRegister = register;
        tilNick.setVisibility(register ? View.VISIBLE : View.GONE);
        btnSubmit.setText(register ? "注册" : "登录");
        hideError();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void onSubmit() {
        hideError();
        String user = textOf(etUser);
        String pass = textOf(etPass);
        String nick = textOf(etNick);

        if (isRegister) {
            doRegister(user, pass, nick);
        } else {
            doLogin(user, pass);
        }
    }

    /** 注册：校验非空 -> 用户名未占用 -> 创建账号 -> 写会话 -> 跳转 */
    private void doRegister(String user, String pass, String nick) {
        if (TextUtils.isEmpty(user)) {
            showError("请输入用户名");
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            showError("请输入密码");
            return;
        }
        if (TextUtils.isEmpty(nick)) {
            showError("请输入昵称");
            return;
        }

        if (dataStore.getAccount(user) != null) {
            showError("用户名已存在");
            return;
        }

        // 创建账号，密码用 hashPass 存储
        Account account = new Account();
        account.setUser(user);
        account.setNick(nick);
        account.setPass(DataStore.hashPass(pass));
        account.setRole("user");
        account.setCreatedAt(System.currentTimeMillis());

        Map<String, Account> accounts = dataStore.loadAccounts();
        if (accounts == null) {
            accounts = new HashMap<>();
        }
        accounts.put(user, account);
        dataStore.saveAccounts(accounts);
        dataStore.setSession(user);

        Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show();
        gotoGroupList();
    }

    /** 登录：校验账号存在且密码正确 -> 写会话 -> 跳转 */
    private void doLogin(String user, String pass) {
        if (TextUtils.isEmpty(user)) {
            showError("请输入用户名");
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            showError("请输入密码");
            return;
        }

        Account account = dataStore.getAccount(user);
        if (account == null) {
            showError("账号不存在");
            return;
        }
        if (!DataStore.hashPass(pass).equals(account.getPass())) {
            showError("密码错误");
            return;
        }

        dataStore.setSession(user);
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        gotoGroupList();
    }

    private void gotoGroupList() {
        startActivity(new Intent(this, GroupListActivity.class));
        finish();
    }

    @NonNull
    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
