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

    /**
     * 订单类型枚举构造函数
     * @param displayName 显示名称
     * @param dbOrderType 数据库订单类型字段值
     * @param dbDeliveryMethod 数据库配送方式字段值
     */
    OrderType(String displayName, String dbOrderType, String dbDeliveryMethod) {
        this.displayName = displayName;
        this.dbOrderType = dbOrderType;
        this.dbDeliveryMethod = dbDeliveryMethod;
    }

    /**
     * 获取订单类型的显示名称
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取数据库订单类型字段值
     * @return 数据库订单类型值
     */
    public String getDbOrderType() {
        return dbOrderType;
    }

    /**
     * 获取数据库配送方式字段值
     * @return 数据库配送方式值
     */
    public String getDbDeliveryMethod() {
        return dbDeliveryMethod;
    }

}