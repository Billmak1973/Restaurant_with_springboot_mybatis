package com.restaurant.entity;

import org.apache.ibatis.type.Alias;

@Alias("OrderItemServingStatus")
public class OrderItemServingStatus {
    // 🔧 关键：去掉 final，允许 MyBatis 通过 setter 注入
    private int quantity;
    private int servedQuantity;
    private int preparedQuantity;   // 🔧 新增：已准备数量

    // 🔧【新增】无参构造函数（MyBatis 必需）
    public OrderItemServingStatus() {
    }

    // 🔧 保留原有构造函数（供业务代码使用）
    public OrderItemServingStatus(int quantity, int servedQuantity, int preparedQuantity) {
        this.quantity = quantity;
        this.servedQuantity = servedQuantity;
        this.preparedQuantity = preparedQuantity;
    }

    // 🔧【新增】所有字段的 setter 方法（MyBatis 通过 setter 映射）
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setServedQuantity(int servedQuantity) { this.servedQuantity = servedQuantity; }
    public void setPreparedQuantity(int preparedQuantity) { this.preparedQuantity = preparedQuantity; }

    // ===== Getters 保持不变 =====
    public int getQuantity() { return quantity; }
    public int getServedQuantity() { return servedQuantity; }
    public int getPreparedQuantity() { return preparedQuantity; }

    /** 獲取剩餘未上桌數量 */
    public int getRemainingQuantity() {
        return Math.max(0, quantity - servedQuantity);
    }

    @Override
    public String toString() {
        return String.format("OrderItemServingStatus{quantity=%d, served=%d, prepared=%d, remaining=%d}",
                quantity, servedQuantity, preparedQuantity, getRemainingQuantity());
    }
}