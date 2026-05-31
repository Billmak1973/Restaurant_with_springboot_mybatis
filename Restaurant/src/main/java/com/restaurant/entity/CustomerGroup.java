package com.restaurant.entity;

import org.apache.ibatis.type.Alias;
import java.time.LocalDateTime;

@Alias("CustomerGroup")//@Alias 是 MyBatis 提供的注解，用于为 Java 类定义一个简短别名，替代冗长的全限定类名（包名+类名）。
public class CustomerGroup {
    // ===== 属性 =====
    private int group_id;
    private int callNumber;
    private int groupSize;  //  改名：与数据库 group_size 匹配
    private LocalDateTime startTime;
    private boolean isAssigned;
    private boolean shownWaitMessage;
    private Integer tableId;
    private int position;

    // ===== 构造函数 =====

    /**
     * 无参构造函数
     * 供框架反射实例化对象使用
     */
    public CustomerGroup() {
    }

    /**
     * 双参构造函数（用于新顾客入队）
     * @param callNumber 叫号序号
     * @param groupSize 顾客组人数
     */
    public CustomerGroup(int callNumber, int groupSize) {  //  参数改名
        this.callNumber = callNumber;
        this.groupSize = groupSize;  // 赋值改名
        this.startTime = LocalDateTime.now();
        this.isAssigned = false;
        this.shownWaitMessage = false;
        this.tableId = null;
        this.position = 0;
    }

    /**
     * 全参构造函数（用于从数据库加载或复制对象）
     * @param group_id 顾客组主键
     * @param callNumber 叫号序号
     * @param groupSize 顾客组人数
     * @param startTime 入队时间
     * @param isAssigned 是否已分配餐桌
     * @param shownWaitMessage 是否已显示等待提示
     * @param tableId 分配的餐桌主键
     * @param position 排队位置
     */
    public CustomerGroup(int group_id, int callNumber, int groupSize, LocalDateTime startTime,
                         boolean isAssigned, boolean shownWaitMessage, Integer tableId, int position) {
        this.group_id = group_id;
        this.callNumber = callNumber;
        this.groupSize = groupSize;  //  赋值改名
        this.startTime = startTime;
        this.isAssigned = isAssigned;
        this.shownWaitMessage = shownWaitMessage;
        this.tableId = tableId;
        this.position = position;
    }

    // ===== Getter/Setter =====

    /**
     * 获取顾客组主键
     * @return 主键
     */
    public int getGroup_id() { return group_id; }
    public void setGroup_id(int group_id) { this.group_id = group_id; }

    /**
     * 获取叫号序号
     * @return 叫号序号
     */
    public int getCallNumber() { return callNumber; }

    /**
     * 获取顾客组人数
     * @return 人数
     */
    public int getGroupSize() { return groupSize; }

    /**
     * 设置顾客组人数
     * @param groupSize 人数
     */
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    /**
     * 获取入队时间
     * @return 入队时间
     */
    public LocalDateTime getStartTime() { return startTime; }

    /**
     * 设置入队时间
     * @param startTime 入队时间
     */
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    /**
     * 获取是否已分配餐桌
     * @return true=已分配，false=等待中
     */
    public boolean isAssigned() { return isAssigned; }

    /**
     * 设置是否已分配餐桌
     * @param assigned true=已分配，false=等待中
     */
    public void setAssigned(boolean assigned) { isAssigned = assigned; }

    /**
     * 获取是否已显示等待提示
     * @return true=已显示，false=未显示
     */
    public boolean hasShownWaitMessage() { return shownWaitMessage; }

    /**
     * 获取分配的餐桌主键
     * @return 餐桌主键，未分配时返回null
     */
    public Integer getTableId() { return tableId; }

    /**
     * 设置分配的餐桌主键
     * @param tableId 餐桌主键
     */
    public void setTableId(Integer tableId) { this.tableId = tableId; }

    /**
     * 获取排队位置
     * @return 排队位置序号
     */
    public int getPosition() { return position; }

    /**
     * 设置排队位置
     * @param position 排队位置序号
     */
    public void setPosition(int position) { this.position = position; }

    /**
     * 返回顾客组对象的字符串表示
     * @return 格式化后的顾客组信息，用于日志或界面展示
     */
    @Override
    public String toString() {
        return "顾客组 #" + callNumber + " (" + groupSize + "人)" +  // 改名
                (isAssigned ? " [已入座]" : " [等待中]");
    }
}