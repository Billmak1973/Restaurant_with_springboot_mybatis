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


    private List<OrderItem> orderItems;

    private LocalDateTime reorderTime;


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
    public Order(Integer tableId, String orderNumber,String orderType, Double itemsTotal, Double deliveryFee) {
        this.tableId = tableId;
        this.orderType = orderType;
        this.orderNumber = orderNumber;
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


    /**
     * 获取订单主键
     * @return 订单主键
     */
    public Integer getOrderId() {
        return orderId;
    }

    /**
     * 设置订单主键
     * @param orderId 订单主键
     */
    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    /**
     * 获取订单编号
     * @return 订单编号字符串
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * 设置订单编号
     * @param orderNumber 订单编号字符串
     */
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    /**
     * 获取关联餐桌主键
     * @return 餐桌主键
     */
    public Integer getTableId() {
        return tableId;
    }

    /**
     * 设置关联餐桌主键
     * @param tableId 餐桌主键
     */
    public void setTableId(Integer tableId) {
        this.tableId = tableId;
    }

    /**
     * 获取关联预约记录编号
     * @return 预约记录编号
     */
    public String getReservationId() {
        return reservationId;
    }

    /**
     * 设置关联预约记录编号
     * @param reservationId 预约记录编号
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    /**
     * 获取订单类型
     * @return 订单类型字符串
     */
    public String getOrderType() {
        return orderType;
    }

    /**
     * 设置订单类型
     * @param orderType 订单类型字符串
     */
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    /**
     * 获取配送方式
     * @return 配送方式字符串
     */
    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    /**
     * 设置配送方式
     * @param deliveryMethod 配送方式字符串
     */
    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    /**
     * 获取配送地址
     * @return 配送地址字符串
     */
    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    /**
     * 设置配送地址
     * @param deliveryAddress 配送地址字符串
     */
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    /**
     * 获取客户联系电话
     * @return 联系电话字符串
     */
    public String getCustomerPhone() {
        return customerPhone;
    }

    /**
     * 设置客户联系电话
     * @param customerPhone 联系电话字符串
     */
    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    /**
     * 获取客户姓名
     * @return 客户姓名字符串
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * 设置客户姓名
     * @param customerName 客户姓名字符串
     */
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    /**
     * 获取订单创建时间
     * @return 订单时间
     */
    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    /**
     * 设置订单创建时间
     * @param orderTime 订单时间
     */
    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }

    /**
     * 获取订单状态
     * @return 状态字符串
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置订单状态
     * @param status 状态字符串
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取菜品总额
     * @return 菜品总额，为空时返回0.0
     */
    public Double getItemsTotal() {
        return itemsTotal != null ? itemsTotal : 0.0;
    }

    /**
     * 设置菜品总额并自动更新应付总额
     * @param itemsTotal 菜品总额
     */
    public void setItemsTotal(Double itemsTotal) {
        this.itemsTotal = itemsTotal;
        // 自动更新总金额
        if (this.deliveryFee == null) {
            this.deliveryFee = 0.0;
        }
        this.totalAmount = this.itemsTotal + this.deliveryFee;
    }

    /**
     * 获取配送费
     * @return 配送费，为空时返回0.0
     */
    public Double getDeliveryFee() {
        return deliveryFee != null ? deliveryFee : 0.0;
    }

    /**
     * 设置配送费并自动更新应付总额
     * @param deliveryFee 配送费
     */
    public void setDeliveryFee(Double deliveryFee) {
        this.deliveryFee = deliveryFee;
        // 自动更新总金额
        if (this.itemsTotal == null) {
            this.itemsTotal = 0.0;
        }
        this.totalAmount = this.itemsTotal + this.deliveryFee;
    }

    /**
     * 获取订单应付总额
     * @return 应付总额，为空时返回0.0
     */
    public Double getTotalAmount() {
        return totalAmount != null ? totalAmount : 0.0;
    }

    /**
     * 设置订单应付总额
     * @param totalAmount 应付总额
     */
    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * 设置订单是否已结账
     * @param checkedOut 是否已结账
     */
    public void setIsCheckedOut(Boolean checkedOut) {
        isCheckedOut = checkedOut;
    }

    /**
     * 获取订单是否已预付
     * @return 是否已预付，为空时返回false
     */
    public Boolean getIsPrepaid() {
        return isPrepaid != null ? isPrepaid : false;
    }

    /**
     * 设置订单是否已预付
     * @param prepaid 是否已预付
     */
    public void setIsPrepaid(Boolean prepaid) {
        isPrepaid = prepaid;
    }

    /**
     * 获取预付金额
     * @return 预付金额，为空时返回0.0
     */
    public Double getPrepaidAmount() {
        return prepaidAmount != null ? prepaidAmount : 0.0;
    }

    /**
     * 设置预付金额
     * @param prepaidAmount 预付金额
     */
    public void setPrepaidAmount(Double prepaidAmount) {
        this.prepaidAmount = prepaidAmount;
    }

    /**
     * 获取订单明细列表
     * @return 订单项列表
     */
    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    /**
     * 设置订单明细列表
     * @param orderItems 订单项列表
     */
    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    /**
     * 获取重新点餐时间
     * @return 重新点餐时间
     */
    public LocalDateTime getReorderTime() {
        return reorderTime;
    }

    /**
     * 获取配送状态
     * @return 配送状态枚举
     */
    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    /**
     * 设置配送状态
     * @param deliveryStatus 配送状态枚举
     */
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    /**
     * 返回订单对象的字符串表示
     * @return 格式化后的字段信息，用于日志调试
     */
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
}