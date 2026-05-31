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
    private int preparedQuantity;  //  已準備數量（廚房進度）
    private String status;      // "UNSERVED", "PARTIALLY_SERVED", "SERVED"
    private double priceAtOrder; // 使用 double
    private String assignedTableDisplayId;    //  菜品归属的具体餐桌（合并桌/聚餐桌场景）
    private String servedTableDisplayId;  // 实际上菜的餐桌ID列表
    private String quantityDistribution;    // 如 {"13":4,"14":4,"15":3}
    private String servedDistribution;      // 如 {"13":4,"14":4,"15":2}

    /**
     * 无参构造函数
     * 供 MyBatis 反射实例化对象使用
     */
    public OrderItem() {
        // MyBatis 必需
    }

    /**
     * 全参构造函数
     * 供业务代码手动创建订单项对象时使用
     * @param orderId 订单主键
     * @param itemId 菜品主键
     * @param itemCode 菜品编码
     * @param itemName 菜品名称
     * @param quantity 菜品数量
     * @param priceAtOrder 下单时单价
     */
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

    /**
     * 获取订单项主键
     * @return 订单项主键
     */
    public Integer getOrderItemId() {
        return orderItemId;
    }

    /**
     * 获取关联订单主键
     * @return 订单主键
     */
    public Integer getOrderId() {
        return orderId;
    }

    /**
     * 获取关联菜品主键
     * @return 菜品主键
     */
    public Integer getItemId() {
        return itemId;
    }

    /**
     * 获取菜品编码
     * @return 菜品编码
     */
    public String getItemCode() {
        return itemCode;
    }

    /**
     * 获取菜品名称
     * @return 菜品名称
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * 获取菜品数量
     * @return 菜品数量
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * 获取已上桌数量
     * @return 已上桌数量
     */
    public int getServedQuantity() {
        return servedQuantity;
    }

    /**
     * 获取订单项状态
     * @return 状态字符串
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取下单时单价
     * @return 单价
     */
    public double getPriceAtOrder() {
        return priceAtOrder;
    }

    /**
     * 设置订单项主键
     * @param orderItemId 订单项主键
     */
    public void setOrderItemId(Integer orderItemId) {
        this.orderItemId = orderItemId;
    }

    /**
     * 设置关联订单主键
     * @param orderId 订单主键
     */
    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }
    /**
     * 设置关联菜品主键
     * @param itemId 菜品主键
     */
    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    /**
     * 设置菜品编码
     * @param itemCode 菜品编码
     */
    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    /**
     * 设置菜品名称
     * @param itemName 菜品名称
     */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    /**
     * 设置菜品数量
     * @param quantity 菜品数量
     * @throws IllegalArgumentException 数量为负数时抛出
     */
    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }
        this.quantity = quantity;
    }
    /**
     * 设置已上桌数量并自动推导状态
     * @param servedQuantity 已上桌数量
     * @throws IllegalArgumentException 数量为负或超过总数量时抛出
     */
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
    /**
     * 设置订单项状态
     * @param status 状态字符串
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 设置下单时单价
     * @param priceAtOrder 单价
     * @throws IllegalArgumentException 价格为负数时抛出
     */
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

    /**
     * 获取分配的餐桌显示编号
     * @return 分配的餐桌编号字符串
     */
    public String getAssignedTableDisplayId() { return assignedTableDisplayId; }

    /**
     * 设置分配的餐桌显示编号
     * @param assignedTableDisplayId 分配的餐桌编号字符串
     */
    public void setAssignedTableDisplayId(String assignedTableDisplayId) {
        this.assignedTableDisplayId = assignedTableDisplayId;
    }

    /**
     * 获取已上桌的餐桌显示编号
     * @return 已上桌的餐桌编号字符串
     */
    public String getServedTableDisplayId() {
        return servedTableDisplayId;
    }

    /**
     * 获取已准备数量
     * @return 已准备数量
     */
    public int getPreparedQuantity() {
        return preparedQuantity;
    }
    /**
     * 获取数量分配分布（JSON格式）
     * @return 分布字符串
     */
    public String getQuantityDistribution() {
        return quantityDistribution;
    }

    /**
     * 设置数量分配分布（JSON格式）
     * @param quantityDistribution 分布字符串
     */
    public void setQuantityDistribution(String quantityDistribution) {
        this.quantityDistribution = quantityDistribution;
    }

    public void setServedDistribution(String servedDistribution) {
        this.servedDistribution = servedDistribution;
    }
    @Override
    public String toString() {
        return String.format("OrderItem[itemCode=%s, quantity=%d, served=%d, price=%.2f, status=%s]",
                itemCode, quantity, servedQuantity, priceAtOrder, status);
    }
}

