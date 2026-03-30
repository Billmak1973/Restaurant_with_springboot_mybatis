package com.restaurant.entity;

import org.apache.ibatis.type.Alias;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单主表实体类（对应 table_orders）
 * 支持堂食/自取/配送三种订单类型
 */
@Alias("Order")
public class Order {

    public enum DeliveryStatus {
        NOT_DELIVERED("未配送"),
        DELIVERING("送單中"),
        DELIVERED("已送達");

        private final String displayName;
        DeliveryStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }

        // 數據庫值轉換
        public static DeliveryStatus fromDbValue(String value) {
            if (value == null) return NOT_DELIVERED;
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return NOT_DELIVERED;
            }
        }
    }
    // ═══════════════════════════════════════════════════════════
    // 【主键字段】
    // ═══════════════════════════════════════════════════════════

    /** 订单 ID（数据库自增主键） */
    private Integer orderId;

    /** 外卖订单号（格式：P-20260305-001 / D-20260305-001，堂食为 null） */
    private String orderNumber;

    // ═══════════════════════════════════════════════════════════
    // 【关联字段】
    // ═══════════════════════════════════════════════════════════

    /** 餐桌 ID（外卖/预定时为 NULL） */
    private Integer tableId;

    /** 预定 ID（预定订单时填写） */
    private String reservationId;

    // ═══════════════════════════════════════════════════════════
    // 【订单类型】
    // ═══════════════════════════════════════════════════════════

    /** 订单类型：DINE_IN（堂食）/ TAKEOUT（外卖） */
    private String orderType;

    /** 外卖配送方式：PICKUP（自取）/ DELIVERY（配送），仅外卖有效 */
    private String deliveryMethod;

    private DeliveryStatus deliveryStatus;  // 配送狀態

    /** 配送地址（仅配送模式有效） */
    private String deliveryAddress;

    /** 客户电话（外卖必填，堂食可选） */
    private String customerPhone;

    /** 客户姓名（外卖可选，堂食可为 null） */
    private String customerName;

    // ═══════════════════════════════════════════════════════════
    // 【订单信息】
    // ═══════════════════════════════════════════════════════════

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 订单状态：ORDERED（已下单）/ CHECKED_OUT（已结账） */
    private String status;

    // ═══════════════════════════════════════════════════════════
    // 【金额字段】- 核心修改：三金额分离
    // ═══════════════════════════════════════════════════════════

    /** 菜品总金额（不含配送费） */
    private Double itemsTotal;

    /** 配送费（仅配送模式有效，堂食/自取为 0） */
    private Double deliveryFee;

    /** 最终总金额（= itemsTotal + deliveryFee） */
    private Double totalAmount;

    // ═══════════════════════════════════════════════════════════
    // 【结账状态】
    // ═══════════════════════════════════════════════════════════

    /** 是否已结账 */
    private Boolean isCheckedOut;

    /** 是否预付金额 */
    private Boolean isPrepaid;

    /** 预付金额数目 */
    private Double prepaidAmount;

    // ═══════════════════════════════════════════════════════════
    // 【关联的订单项列表】（非数据库字段，用于业务逻辑）
    // ═══════════════════════════════════════════════════════════

    private List<OrderItem> orderItems;

    private LocalDateTime reorderTime;

    // ═══════════════════════════════════════════════════════════
    // 【构造方法】
    // ═══════════════════════════════════════════════════════════

    /** 默认构造函数（MyBatis 必需） */
    public Order() {
        this.deliveryStatus = null;
    }

    /**
     * 简化构造函数（用于创建新订单）
     * @param tableId 餐桌 ID
     * @param orderType 订单类型
     * @param itemsTotal 菜品总金额
     * @param deliveryFee 配送费
     */
    public Order(Integer tableId, String orderType, Double itemsTotal, Double deliveryFee) {
        this.tableId = tableId;
        this.orderType = orderType;
        this.itemsTotal = itemsTotal != null ? itemsTotal : 0.0;
        this.deliveryFee = deliveryFee != null ? deliveryFee : 0.0;
        this.totalAmount = this.itemsTotal + this.deliveryFee;  // 自动计算
        this.status = "ORDERED";
        this.isCheckedOut = false;
        this.isPrepaid = false;
        this.prepaidAmount = 0.0;

    }

    /**
     * 完整构造函数
     */
    public Order(Integer orderId, String orderNumber, Integer tableId, String reservationId,
                 String orderType, String deliveryMethod, String deliveryAddress,
                 String customerPhone, String customerName, LocalDateTime orderTime,
                 String status, Double itemsTotal, Double deliveryFee, Double totalAmount,
                 Boolean isCheckedOut, Boolean isPrepaid, Double prepaidAmount) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.tableId = tableId;
        this.reservationId = reservationId;
        this.orderType = orderType;
        this.deliveryMethod = deliveryMethod;
        this.deliveryAddress = deliveryAddress;
        this.customerPhone = customerPhone;
        this.customerName = customerName;
        this.orderTime = orderTime;
        this.status = status;
        this.itemsTotal = itemsTotal != null ? itemsTotal : 0.0;
        this.deliveryFee = deliveryFee != null ? deliveryFee : 0.0;
        this.totalAmount = totalAmount != null ? totalAmount : 0.0;
        this.isCheckedOut = isCheckedOut != null ? isCheckedOut : false;
        this.isPrepaid = isPrepaid != null ? isPrepaid : false;
        this.prepaidAmount = prepaidAmount != null ? prepaidAmount : 0.0;
    }

    // ═══════════════════════════════════════════════════════════
    // 【Getter & Setter】
    // ═══════════════════════════════════════════════════════════

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Integer getTableId() {
        return tableId;
    }

    public void setTableId(Integer tableId) {
        this.tableId = tableId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // ── 三金额字段 Getter/Setter ──

    public Double getItemsTotal() {
        return itemsTotal != null ? itemsTotal : 0.0;
    }

    public void setItemsTotal(Double itemsTotal) {
        this.itemsTotal = itemsTotal;
        // 自动更新总金额
        if (this.deliveryFee == null) {
            this.deliveryFee = 0.0;
        }
        this.totalAmount = this.itemsTotal + this.deliveryFee;
    }

    public Double getDeliveryFee() {
        return deliveryFee != null ? deliveryFee : 0.0;
    }

    public void setDeliveryFee(Double deliveryFee) {
        this.deliveryFee = deliveryFee;
        // 自动更新总金额
        if (this.itemsTotal == null) {
            this.itemsTotal = 0.0;
        }
        this.totalAmount = this.itemsTotal + this.deliveryFee;
    }

    public Double getTotalAmount() {
        return totalAmount != null ? totalAmount : 0.0;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    // ── 结账状态 Getter/Setter ──

    public Boolean getIsCheckedOut() {
        return isCheckedOut != null ? isCheckedOut : false;
    }

    public void setIsCheckedOut(Boolean checkedOut) {
        isCheckedOut = checkedOut;
    }

    public Boolean getIsPrepaid() {
        return isPrepaid != null ? isPrepaid : false;
    }

    public void setIsPrepaid(Boolean prepaid) {
        isPrepaid = prepaid;
    }

    public Double getPrepaidAmount() {
        return prepaidAmount != null ? prepaidAmount : 0.0;
    }

    public void setPrepaidAmount(Double prepaidAmount) {
        this.prepaidAmount = prepaidAmount;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    // ===== Getter/Setter =====

    public LocalDateTime getReorderTime() {
        return reorderTime;
    }

    public void setReorderTime(LocalDateTime reorderTime) {
        this.reorderTime = reorderTime;
    }
    // ═══════════════════════════════════════════════════════════
    // 【辅助方法】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取订单最终应付总额（含配送费）
     * @return 最终总金额
     */
    public Double getFinalAmount() {
        double base = getItemsTotal();
        double fee = getDeliveryFee();
        return base + fee;
    }

    /**
     * 判断是否为堂食订单
     */
    public boolean isDineIn() {
        return "DINE_IN".equals(orderType);
    }

    /**
     * 判断是否为外卖订单
     */
    public boolean isTakeout() {
        return "TAKEOUT".equals(orderType);
    }

    /**
     * 判断是否为配送订单
     */
    public boolean isDelivery() {
        return "DELIVERY".equals(deliveryMethod);
    }

    /**
     * 判断是否为自取订单
     */
    public boolean isPickup() {
        return "PICKUP".equals(deliveryMethod);
    }

    // ═══════════════════════════════════════════════════════════
    // 【toString 方法】
    // ═══════════════════════════════════════════════════════════

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", orderNumber='" + orderNumber + '\'' +
                ", tableId=" + tableId +
                ", orderType='" + orderType + '\'' +
                ", deliveryMethod='" + deliveryMethod + '\'' +
                ", status='" + status + '\'' +
                ", itemsTotal=" + String.format("%.2f", getItemsTotal()) +
                ", deliveryFee=" + String.format("%.2f", getDeliveryFee()) +
                ", totalAmount=" + String.format("%.2f", getTotalAmount()) +
                ", isCheckedOut=" + isCheckedOut +
                '}';
    }

    // ═══════════════════════════════════════════════════════════
    // 【equals & hashCode】（可选，用于集合操作）
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return orderId != null && orderId.equals(order.orderId);
    }

    @Override
    public int hashCode() {
        return orderId != null ? orderId.hashCode() : 0;
    }

    // Getter/Setter
    // Getter/Setter
    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    // 輔助方法：判斷是否為配送訂單且可開始配送
    public boolean canStartDelivery() {
        return "DELIVERY".equals(deliveryMethod)
                && "ORDERED".equals(status)
                && deliveryStatus == DeliveryStatus.NOT_DELIVERED;
    }
}