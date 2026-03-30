package com.restaurant.entity;

import org.apache.ibatis.type.Alias;

@Alias("OrderItem")
public class OrderItem {
    private Integer orderItemId;
    private Integer orderId;
    private Integer itemId;
    private String itemCode;
    private String itemName;
    private int quantity;
    private int servedQuantity;
    private String status;      // "UNSERVED", "PARTIALLY_SERVED", "SERVED"
    private double priceAtOrder; // 使用 double

    public OrderItem() {
        // MyBatis 必需
    }

    // 构造方法
    public OrderItem(Integer orderId, Integer itemId, String itemCode,
                     String itemName, int quantity, double priceAtOrder) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.quantity = quantity;
        this.servedQuantity = 0;
        this.status = "UNSERVED";
        this.priceAtOrder = priceAtOrder;
    }

    // ===== Getters =====
    public Integer getOrderItemId() {
        return orderItemId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getServedQuantity() {
        return servedQuantity;
    }

    public String getStatus() {
        return status;
    }

    public double getPriceAtOrder() {
        return priceAtOrder;
    }

    // ===== Setters =====
    public void setOrderItemId(Integer orderItemId) {
        this.orderItemId = orderItemId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }
        this.quantity = quantity;
    }

    public void setServedQuantity(int servedQuantity) {
        if (servedQuantity < 0 || servedQuantity > this.quantity) {
            throw new IllegalArgumentException("已上菜数量不能为负数或超过总数量");
        }
        this.servedQuantity = servedQuantity;

        // 自动更新状态
        if (servedQuantity == 0) {
            this.status = "UNSERVED";
        } else if (servedQuantity < this.quantity) {
            this.status = "PARTIALLY_SERVED";
        } else {
            this.status = "SERVED";
        }
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPriceAtOrder(double priceAtOrder) {
        if (priceAtOrder < 0) {
            throw new IllegalArgumentException("价格不能为负数");
        }
        this.priceAtOrder = priceAtOrder;
    }

    // ===== 辅助方法 =====
    /**
     * 计算该订单项的小计金额（数量 × 单价）
     * @return 小计金额（double 类型）
     */
    public double getSubtotal() {
        return quantity * priceAtOrder;
    }

    /**
     * 计算剩余未上菜数量
     * @return 未上菜数量
     */
    public int getRemainingQuantity() {
        return quantity - servedQuantity;
    }


    @Override
    public String toString() {
        return String.format("OrderItem[itemCode=%s, quantity=%d, served=%d, price=%.2f, status=%s]",
                itemCode, quantity, servedQuantity, priceAtOrder, status);
    }
}

