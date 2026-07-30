package com.chatroom.client.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chatroom.client.R;
import com.chatroom.client.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 聊天消息适配器。
 * 三种视图类型：TYPE_ME / TYPE_OTHER / TYPE_SYS，
 * 通过 msg.type 与 msg.nick 与当前昵称比较决定。
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    public static final int TYPE_ME = 0;
    public static final int TYPE_OTHER = 1;
    public static final int TYPE_SYS = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentNick;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(String currentNick) {
        this.currentNick = currentNick != null ? currentNick : "";
    }

    /** 设置全部消息并刷新 */
    public void setMessages(List<ChatMessage> list) {
        messages.clear();
        if (list != null) {
            messages.addAll(list);
        }
        notifyDataSetChanged();
    }

    /** 追加单条消息 */
    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg == null) {
            return TYPE_OTHER;
        }
        // 系统消息
        if ("sys".equals(msg.getType())) {
            return TYPE_SYS;
        }
        // 自己的消息
        String nick = msg.getNick();
        if (nick != null && nick.equals(currentNick)) {
            return TYPE_ME;
        }
        return TYPE_OTHER;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        int type = getItemViewType(position);

        // 默认全部隐藏
        holder.tvSys.setVisibility(View.GONE);
        holder.llOther.setVisibility(View.GONE);
        holder.llMe.setVisibility(View.GONE);

        if (type == TYPE_SYS) {
            // 系统消息
            holder.tvSys.setVisibility(View.VISIBLE);
            holder.tvSys.setText(msg.getText());
        } else if (type == TYPE_ME) {
            // 自己的消息
            holder.llMe.setVisibility(View.VISIBLE);
            holder.tvBubbleMe.setText(msg.getText());
            holder.tvTimeMe.setText(timeFmt.format(new Date(msg.getTs())));
        } else {
            // 别人的消息
            holder.llOther.setVisibility(View.VISIBLE);
            String nick = msg.getNick();
            holder.tvNickOther.setText(nick != null && !nick.isEmpty() ? nick : "匿名");
            holder.tvBubbleOther.setText(msg.getText());
            holder.tvTimeOther.setText(timeFmt.format(new Date(msg.getTs())));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSys;
        View llOther;
        TextView tvNickOther;
        TextView tvBubbleOther;
        TextView tvTimeOther;
        View llMe;
        TextView tvBubbleMe;
        TextView tvTimeMe;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSys = itemView.findViewById(R.id.tvSys);
            llOther = itemView.findViewById(R.id.llOther);
            tvNickOther = itemView.findViewById(R.id.tvNickOther);
            tvBubbleOther = itemView.findViewById(R.id.tvBubbleOther);
            tvTimeOther = itemView.findViewById(R.id.tvTimeOther);
            llMe = itemView.findViewById(R.id.llMe);
            tvBubbleMe = itemView.findViewById(R.id.tvBubbleMe);
            tvTimeMe = itemView.findViewById(R.id.tvTimeMe);
        }
    }
}
