package com.restaurant.entity;

import org.apache.ibatis.type.Alias;

@Alias("OrderItemServingStatus")
public class OrderItemServingStatus {
    //  关键：去掉 final，允许 MyBatis 通过 setter 注入
    private int quantity;
    private int servedQuantity;
    private int preparedQuantity;   //  新增：已准备数量

    /**
     * 无参构造函数
     * 供 MyBatis 反射实例化对象使用
     */
    public OrderItemServingStatus() {
    }

    /**
     * 全参构造函数
     * 供业务代码手动创建状态对象时使用
     * @param quantity 总数量
     * @param servedQuantity 已上桌数量
     * @param preparedQuantity 已准备数量
     */
    public OrderItemServingStatus(int quantity, int servedQuantity, int preparedQuantity) {
        this.quantity = quantity;
        this.servedQuantity = servedQuantity;
        this.preparedQuantity = preparedQuantity;
    }

    /**
     * 设置总数量
     * @param quantity 总数量
     */
    public void setQuantity(int quantity) { this.quantity = quantity; }
    /**
     * 设置已上桌数量
     * @param servedQuantity 已上桌数量
     */
    public void setServedQuantity(int servedQuantity) { this.servedQuantity = servedQuantity; }

    /**
     * 获取总数量
     * @return 总数量
     */
    public int getQuantity() { return quantity; }
    /**
     * 获取已上桌数量
     * @return 已上桌数量
     */
    public int getServedQuantity() { return servedQuantity; }

    /** 獲取剩餘未上桌數量 */
    public int getRemainingQuantity() {
        return Math.max(0, quantity - servedQuantity);
    }

    /**
     * 返回状态对象的字符串表示
     * @return 格式化后的字段信息，用于日志调试
     */
    @Override
    public String toString() {
        return String.format("OrderItemServingStatus{quantity=%d, served=%d, prepared=%d, remaining=%d}",
                quantity, servedQuantity, preparedQuantity, getRemainingQuantity());
    }
}