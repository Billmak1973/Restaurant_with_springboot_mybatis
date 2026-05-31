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

    private LocalDateTime rescheduledTime;  //  延迟后的新预约时间

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

    // ===== 预付信息 (修改为 Double) =====
    private Boolean isPrepaid;
    private Double prepaidAmount;   // 使用 Double 替代 BigDecimal，与 Order 实体保持一致

    // ===== 其他 =====
    private String notes;
    private LocalDateTime createdAt;

    /**
     * 餐桌预约记录默认构造函数
     *
     * 功能说明：
     * 创建空的预约对象实例，供框架反射实例化、反序列化或手动逐项赋值使用。
     *
     * 应用场景：
     * - MyBatis 结果映射时自动调用
     * - 单元测试中构造测试数据
     * - 手动创建预约记录时逐步设置属性
     */
    public TableReservation() {
    }

    /**
     * 餐桌预约记录全参构造函数
     *
     * 功能说明：
     * 一次性初始化预约记录的所有核心字段，确保对象创建时状态完整、不可变。
     *
     * 参数说明：
     * - reservationId: 预约记录唯一标识（主键）
     * - customerName: 预约客人姓名
     * - customerPhone: 预约客人联系电话
     * - reservationTime: 预约到店时间
     * - tableCount: 预约餐桌数量
     * - tableConfigDesc: 餐桌配置描述（如"2人桌 x1, 4人桌 x2"）
     * - groupType: 顾客组类型（如"家庭"/"商务"/"朋友"）
     * - reservedTableIds: 已预留的餐桌编号列表（逗号分隔）
     * - tableSelectionMode: 餐桌选择模式（"AUTO"/"MANUAL"）
     * - manualTableNumbers: 手动指定的餐桌编号（仅手动模式有效）
     * - status: 预约状态（"PRE_CONFIRMED"/"CONFIRMED"/"CANCELLED"等）
     * - within15h: 是否在1.5小时内到店（用于优先分配逻辑）
     * - preOrder: 是否已预点餐（关联预点餐订单）
     * - isPrepaid: 是否已支付定金
     * - prepaid: 定金金额
     *
     *
     * 业务规则：
     * - reservedTableIds 与 manualTableNumbers 互斥，由 tableSelectionMode 决定生效字段
     * - within15h 字段由系统自动计算，调用方无需手动设置
     */
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

    /**
     * 获取预约记录唯一标识
     * @return 预约ID字符串
     */
    public String getReservationId() {
        return reservationId;
    }
    /**
     * 设置预约记录唯一标识
     * @param reservationId 预约ID字符串
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    /**
     * 获取预约客人姓名
     * @return 客人姓名字符串
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * 获取预约客人联系电话
     * @return 联系电话字符串
     */
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    /**
     * 获取预约客人联系电话
     * @return 联系电话
     */
    public String getCustomerPhone() {
        return customerPhone;
    }

    /**
     * 设置预约客人联系电话
     * @param customerPhone 联系电话
     */
    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    /**
     * 获取预约到店时间
     * @return 预约时间
     */
    public LocalDateTime getReservationTime() {
        return reservationTime;
    }

    /**
     * 设置预约到店时间
     * @param reservationTime 预约时间
     */
    public void setReservationTime(LocalDateTime reservationTime) {
        this.reservationTime = reservationTime;
    }

    /**
     * 获取改签后的预约时间
     * @return 改签时间
     */
    public LocalDateTime getRescheduledTime() {
        return rescheduledTime;
    }

    /**
     * 设置改签后的预约时间
     * @param rescheduledTime 改签时间
     */
    public void setRescheduledTime(LocalDateTime rescheduledTime) {
        this.rescheduledTime = rescheduledTime;
    }

    /**
     * 获取预约餐桌数量
     * @return 餐桌数量
     */
    public Integer getTableCount() {
        return tableCount;
    }

    /**
     * 设置预约餐桌数量
     * @param tableCount 餐桌数量
     */
    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    /**
     * 获取餐桌配置描述
     * @return 配置描述字符串
     */
    public String getTableConfigDesc() {
        return tableConfigDesc;
    }

    /**
     * 设置餐桌配置描述
     * @param tableConfigDesc 配置描述字符串
     */
    public void setTableConfigDesc(String tableConfigDesc) {
        this.tableConfigDesc = tableConfigDesc;
    }

    /**
     * 获取顾客组类型
     * @return 组类型字符串
     */
    public String getGroupType() {
        return groupType;
    }

    /**
     * 设置顾客组类型
     * @param groupType 组类型字符串
     */
    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    /**
     * 获取预留餐桌编号列表
     * @return 预留餐桌编号字符串
     */
    public String getReservedTableIds() {
        return reservedTableIds;
    }

    /**
     * 设置预留餐桌编号列表
     * @param reservedTableIds 预留餐桌编号字符串
     */
    public void setReservedTableIds(String reservedTableIds) {
        this.reservedTableIds = reservedTableIds;
    }

    /**
     * 获取餐桌选择模式
     * @return 选择模式字符串
     */
    public String getTableSelectionMode() {
        return tableSelectionMode;
    }

    /**
     * 设置餐桌选择模式
     * @param tableSelectionMode 选择模式字符串
     */
    public void setTableSelectionMode(String tableSelectionMode) {
        this.tableSelectionMode = tableSelectionMode;
    }

    /**
     * 获取手动指定的餐桌编号
     * @return 手动指定的编号字符串
     */
    public String getManualTableNumbers() {
        return manualTableNumbers;
    }

    /**
     * 设置手动指定的餐桌编号
     * @param manualTableNumbers 手动指定的编号字符串
     */
    public void setManualTableNumbers(String manualTableNumbers) {
        this.manualTableNumbers = manualTableNumbers;
    }

    /**
     * 获取预约状态
     * @return 状态字符串
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置预约状态
     * @param status 状态字符串
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取是否在1.5小时内预约
     * @return 是否在1.5小时内
     */
    public Boolean getWithin15h() {
        return within15h;
    }

    /**
     * 设置是否在1.5小时内预约
     * @param within15h 是否在1.5小时内
     */
    public void setWithin15h(Boolean within15h) {
        this.within15h = within15h;
    }

    /**
     * 获取是否预点餐标志
     * @return 预点餐标志，数据库为空时默认返回false
     */
    public Boolean getPreOrder() {
        return preOrder != null ? preOrder : false;
    }

    /**
     * 设置是否预点餐标志
     * @param preOrder 预点餐标志
     */
    public void setPreOrder(Boolean preOrder) {
        this.preOrder = preOrder;
    }

    /**
     * 获取是否已支付定金标志
     * @return 定金支付标志
     */
    public Boolean getIsPrepaid() {
        return isPrepaid;
    }

    /**
     * 设置是否已支付定金标志
     * @param isPrepaid 定金支付标志
     */
    public void setIsPrepaid(Boolean isPrepaid) {
        this.isPrepaid = isPrepaid;
    }

    /**
     * 获取定金金额
     * @return 定金金额，数据库为空时默认返回0.0
     */
    public Double getPrepaidAmount() {
        // 防止 null 返回 0.0，方便计算
        return prepaidAmount != null ? prepaidAmount : 0.0;
    }

    /**
     * 设置定金金额
     * @param prepaidAmount 定金金额
     */
    public void setPrepaidAmount(Double prepaidAmount) {
        this.prepaidAmount = prepaidAmount;
    }

    /**
     * 获取预约备注信息
     * @return 备注字符串
     */
    public String getNotes() {
        return notes;
    }

    /**
     * 设置预约备注信息
     * @param notes 备注字符串
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * 获取预约记录创建时间
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回预约记录对象的字符串表示
     * @return 格式化后的字段信息，用于日志调试
     */
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