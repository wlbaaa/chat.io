package com.chatroom.client.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chatroom.client.R;
import com.chatroom.client.model.ChatGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 群列表适配器。
 * 绑定 icon / name / code，提供点击和退出两种回调。
 */
public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    private final List<ChatGroup> groups = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnLeaveClickListener leaveClickListener;

    /** 点击群项回调 */
    public interface OnItemClickListener {
        void onItemClick(ChatGroup group, int position);
    }

    /** 点击退出按钮回调 */
    public interface OnLeaveClickListener {
        void onLeaveClick(ChatGroup group, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLeaveClickListener(OnLeaveClickListener listener) {
        this.leaveClickListener = listener;
    }

    /** 设置数据并刷新 */
    public void setGroups(List<ChatGroup> list) {
        groups.clear();
        if (list != null) {
            groups.addAll(list);
        }
        notifyDataSetChanged();
    }

    /** 追加或更新某群（按 code 去重），用于收到新消息时刷新预览 */
    public void updateGroup(ChatGroup group) {
        if (group == null) {
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            ChatGroup g = groups.get(i);
            if (g.getCode() != null && g.getCode().equals(group.getCode())) {
                groups.set(i, group);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ChatGroup group = groups.get(position);
        final int pos = holder.getAdapterPosition();

        // 图标
        String icon = group.getIcon();
        holder.tvIcon.setText(icon != null && !icon.isEmpty() ? icon : "💬");
        // 群名
        String name = group.getName();
        holder.tvName.setText(name != null && !name.isEmpty() ? name : "未命名群聊");
        // 加入码
        holder.tvCode.setText("加入码: " + (group.getCode() != null ? group.getCode() : ""));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    clickListener.onItemClick(group, pos);
                }
            }
        });

        holder.btnLeave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (leaveClickListener != null) {
                    leaveClickListener.onLeaveClick(group, pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon;
        TextView tvName;
        TextView tvCode;
        ImageButton btnLeave;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIcon);
            tvName = itemView.findViewById(R.id.tvName);
            tvCode = itemView.findViewById(R.id.tvCode);
            btnLeave = itemView.findViewById(R.id.btnLeave);
        }
    }
}
