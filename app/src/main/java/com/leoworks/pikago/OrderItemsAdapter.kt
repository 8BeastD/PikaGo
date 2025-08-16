package com.leoworks.pikago

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class OrderItemsAdapter :
    ListAdapter<OrderItem, OrderItemsAdapter.OrderItemViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_product, parent, false)
        return OrderItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        private val txtProductName: TextView = itemView.findViewById(R.id.txtProductName)
        private val txtProductDetails: TextView = itemView.findViewById(R.id.txtProductDetails)
        private val txtProductPrice: TextView = itemView.findViewById(R.id.txtProductPrice)
        private val txtProductQuantity: TextView = itemView.findViewById(R.id.txtProductQuantity)

        fun bind(item: OrderItem) {
            txtProductName.text = item.name
            txtProductDetails.text = item.description
            txtProductPrice.text = "₹${formatMoney(item.lineTotal())}"
            txtProductQuantity.text = "Qty: ${item.quantity}"

            val url = item.imageUrl.trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }

            Glide.with(itemView.context)
                .load(url)
                .placeholder(R.drawable.ic_product_placeholder)
                .error(R.drawable.ic_product_placeholder)
                .circleCrop() // keep the image inside a round avatar
                .into(imgProduct)
        }

        private fun formatMoney(value: Double): String {
            return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean {
            return oldItem.name == newItem.name && oldItem.description == newItem.description
        }
        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean = oldItem == newItem
    }
}
