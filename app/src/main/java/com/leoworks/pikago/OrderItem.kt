package com.leoworks.pikago

data class OrderItem(
    // UI / Basic fields
    val name: String,
    val description: String = "",
    val price: Double = 0.0,       // kept for compatibility (unit price)
    val quantity: Int = 1,
    val imageUrl: String = "",     // non-null for Glide
    val category: String? = null,

    // DB mapping fields (optional)
    val id: String? = null,        // order_items.id
    val orderId: String? = null,   // order_items.order_id
    val productId: String? = null, // order_items.product_id
    val productPrice: Double = 0.0,
    val serviceType: String? = null,
    val servicePrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun unitPrice(): Double = if (price > 0.0) price else (productPrice + servicePrice)
    fun lineTotal(): Double = if (totalPrice > 0.0) totalPrice else unitPrice() * quantity
}
