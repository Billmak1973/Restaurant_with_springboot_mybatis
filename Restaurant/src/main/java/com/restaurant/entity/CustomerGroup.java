package com.restaurant.entity;

import org.apache.ibatis.type.Alias;
import java.time.LocalDateTime;

@Alias("CustomerGroup")
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
    private transient Tables pendingTable;

    // ===== 构造函数 =====

    //  新增：MyBatis 必需的默认构造函数
    public CustomerGroup() {
    }

    public CustomerGroup(int callNumber, int groupSize) {  //  参数改名
        this.callNumber = callNumber;
        this.groupSize = groupSize;  // 赋值改名
        this.startTime = LocalDateTime.now();
        this.isAssigned = false;
        this.shownWaitMessage = false;
        this.tableId = null;
        this.position = 0;
    }

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

    public int getGroup_id() { return group_id; }
    public void setGroup_id(int group_id) { this.group_id = group_id; }

    public int getCallNumber() { return callNumber; }

    //  改名：getGroupSize / setGroupSize
    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public boolean isAssigned() { return isAssigned; }
    public void setAssigned(boolean assigned) { isAssigned = assigned; }

    public boolean hasShownWaitMessage() { return shownWaitMessage; }

    public Integer getTableId() { return tableId; }
    public void setTableId(Integer tableId) { this.tableId = tableId; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Tables getPendingTable() { return pendingTable; }
    public void setPendingTable(Tables pendingTable) { this.pendingTable = pendingTable; }

    @Override
    public String toString() {
        return "顾客组 #" + callNumber + " (" + groupSize + "人)" +  // 改名
                (isAssigned ? " [已入座]" : " [等待中]");
    }
}