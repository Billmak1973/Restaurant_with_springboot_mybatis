package com.restaurant.entity;

import org.apache.ibatis.type.Alias;
import java.time.LocalDateTime;

/**
 * 餐桌预定记录实体类
 * 对应数据库表：table_reservations
 */
@Alias("TableReservation")
public class TableReservation {

    // ===== 主键 =====
    private String reservationId;

    // ===== 客户信息 =====
    private String customerName;
    private String customerPhone;

    // ===== 预定时间 =====
    private LocalDateTime reservationTime;

    // ===== 桌子配置 =====
    private Integer tableCount;
    private String tableConfigDesc;
    private String groupType;       // MAIN, MERGED, GROUP
    private String reservedTableIds;       // 本单预定桌号 (如 "7" 或 "7,8")

    // ===== 餐桌选择方式 =====
    private String tableSelectionMode; // MANUAL, QUANTITY
    private String manualTableNumbers; // 手动输入的桌号

    // ===== 状态管理 =====
    private String status;          // CONFIRMED, CANCELLED, NO_SHOW
    private Boolean within15h;      // 是否 1.5 小时内
    private Boolean preOrder;

    // ===== 预付信息 (🔧 修改为 Double) =====
    private Boolean isPrepaid;
    private Double prepaidAmount;   // 使用 Double 替代 BigDecimal，与 Order 实体保持一致

    // ===== 其他 =====
    private String notes;
    private LocalDateTime createdAt;

    // ===== 构造函数 =====
    public TableReservation() {
    }

    public TableReservation(String reservationId, String customerName, String customerPhone,
                            LocalDateTime reservationTime, Integer tableCount, String tableConfigDesc,
                            String groupType, String reservedTableIds, String tableSelectionMode,
                            String manualTableNumbers, String status, Boolean within15h,
                             Boolean preOrder , Boolean isPrepaid, Double prepaidAmount,
                            String notes, LocalDateTime createdAt) {
        this.reservationId = reservationId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.reservationTime = reservationTime;
        this.tableCount = tableCount;
        this.tableConfigDesc = tableConfigDesc;
        this.groupType = groupType;
        this.reservedTableIds = reservedTableIds;
        this.tableSelectionMode = tableSelectionMode;
        this.manualTableNumbers = manualTableNumbers;
        this.status = status;
        this.within15h = within15h;
        this.preOrder = preOrder;
        this.isPrepaid = isPrepaid;
        this.prepaidAmount = prepaidAmount;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    // ===== Getter & Setter =====
    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public LocalDateTime getReservationTime() {
        return reservationTime;
    }

    public void setReservationTime(LocalDateTime reservationTime) {
        this.reservationTime = reservationTime;
    }

    public Integer getTableCount() {
        return tableCount;
    }

    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    public String getTableConfigDesc() {
        return tableConfigDesc;
    }

    public void setTableConfigDesc(String tableConfigDesc) {
        this.tableConfigDesc = tableConfigDesc;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public String getReservedTableIds() {
        return reservedTableIds;
    }

    public void setReservedTableIds(String reservedTableIds) {
        this.reservedTableIds = reservedTableIds;
    }

    public String getTableSelectionMode() {
        return tableSelectionMode;
    }

    public void setTableSelectionMode(String tableSelectionMode) {
        this.tableSelectionMode = tableSelectionMode;
    }

    public String getManualTableNumbers() {
        return manualTableNumbers;
    }

    public void setManualTableNumbers(String manualTableNumbers) {
        this.manualTableNumbers = manualTableNumbers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getWithin15h() {
        return within15h;
    }

    public void setWithin15h(Boolean within15h) {
        this.within15h = within15h;
    }

    public Boolean getPreOrder() {
        return preOrder != null ? preOrder : false;
    }

    public void setPreOrder(Boolean preOrder) {
        this.preOrder = preOrder;
    }

    public Boolean getIsPrepaid() {
        return isPrepaid;
    }

    public void setIsPrepaid(Boolean isPrepaid) {
        this.isPrepaid = isPrepaid;
    }

    public Double getPrepaidAmount() {
        // 防止 null 返回 0.0，方便计算
        return prepaidAmount != null ? prepaidAmount : 0.0;
    }

    public void setPrepaidAmount(Double prepaidAmount) {
        this.prepaidAmount = prepaidAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "TableReservation{" +
                "reservationId=" + reservationId +
                ", customerName='" + customerName + '\'' +
                ", customerPhone='" + customerPhone + '\'' +
                ", reservationTime=" + reservationTime +
                ", tableCount=" + tableCount +
                ", isPrepaid=" + isPrepaid +
                ", prepaidAmount=" + prepaidAmount +
                ", status='" + status + '\'' +
                '}';
    }
}