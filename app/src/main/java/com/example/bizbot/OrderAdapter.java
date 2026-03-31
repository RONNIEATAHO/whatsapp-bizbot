package com.example.bizbot;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {
    private List<OrderEntity> orders;

    public OrderAdapter(List<OrderEntity> orders) {
        this.orders = orders;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        OrderEntity order = orders.get(position);
        holder.tvName.setText(order.customerName);
        holder.tvDetails.setText(order.message);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), OrderDetailsActivity.class);
            intent.putExtra("customer", order.customerName);
            intent.putExtra("message", order.message);
            intent.putExtra("notificationKey", order.notificationKey);
            intent.putExtra("phoneNumber", order.phoneNumber);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails;
        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvDetails = itemView.findViewById(R.id.tvOrderDetails);
        }
    }
}
