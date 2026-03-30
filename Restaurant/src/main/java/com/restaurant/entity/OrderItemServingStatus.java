
package com.restaurant.entity;


import org.apache.ibatis.type.Alias;

/**
 * 封裝訂單項的上菜狀態信息
 * @note 不包含業務邏輯，僅是數據容器
 */
@Alias("OrderItemServingStatus")
public class OrderItemServingStatus {
    private final int quantity;        // 點菜總數量
    private final int servedQuantity;  // 已上桌數量

    public OrderItemServingStatus(int quantity, int servedQuantity) {
        this.quantity = quantity;
        this.servedQuantity = servedQuantity;
    }

    public int getQuantity() { return quantity; }
    public int getServedQuantity() { return servedQuantity; }

    /** 獲取剩餘未上桌數量 */
    public int getRemainingQuantity() { return Math.max(0, quantity - servedQuantity); }

    @Override
    public String toString() {
        return String.format("OrderItemServingStatus{quantity=%d, served=%d, remaining=%d}",
                quantity, servedQuantity, getRemainingQuantity());
    }
}