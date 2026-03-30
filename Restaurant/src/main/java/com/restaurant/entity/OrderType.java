package com.restaurant.entity;

/**
 * 訂單類型枚舉（統一管理堂食、自取、配送）
 * 對應數據庫 table_orders 表的 order_type 和 delivery_method 字段
 */
public enum OrderType {
    DINE_IN("堂食", "DINE_IN", null),
    PICKUP("自取", "TAKEOUT", "PICKUP"),
    DELIVERY("配送", "TAKEOUT", "DELIVERY"),
    RESERVATION("预约", "RESERVATION", null);  // 🔧 新增预约类型

    private final String displayName;   // 顯示名稱（GUI 用）
    private final String dbOrderType;   // 對應數據庫 order_type 字段
    private final String dbDeliveryMethod; // 對應數據庫 delivery_method 字段

    OrderType(String displayName, String dbOrderType, String dbDeliveryMethod) {
        this.displayName = displayName;
        this.dbOrderType = dbOrderType;
        this.dbDeliveryMethod = dbDeliveryMethod;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDbOrderType() {
        return dbOrderType;
    }

    public String getDbDeliveryMethod() {
        return dbDeliveryMethod;
    }

    /**
     * 根據數據庫字段還原枚舉（查詢時用）
     */
    public static OrderType fromDbValues(String orderType, String deliveryMethod) {
        if ("DINE_IN".equals(orderType)) {
            return DINE_IN;
        } else if ("TAKEOUT".equals(orderType)) {
            if ("DELIVERY".equals(deliveryMethod)) {
                return DELIVERY;
            } else {
                return PICKUP; // 默認自取
            }
        }
        return PICKUP; // 默認值
    }
}