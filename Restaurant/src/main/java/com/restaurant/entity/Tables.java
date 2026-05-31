package com.restaurant.entity;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import com.restaurant.mapper.CustomerGroupMapper;
import org.apache.ibatis.type.Alias;  // ✅ 新增导入

@Alias("Tables")
public class Tables {


    public enum TableStatus {
        VACANT("空闲"),
        OCCUPIED("占用中"),
        SETTING_UP("准备中"),
        SPLITTING("拆分中"),
        RESERVED("已預定");

        private final String displayName;

        TableStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum TableType {
        MAIN("主桌"),
        MERGED("合并桌"),
        SUBTABLE("子桌"),
        GROUPED("組合桌");

        private final String displayName;

        TableType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

    }

    public  enum OrderStatus {
        NO_ORDER("未下单"),
        ORDERED_UNFINISHED("已下单(未完成)"),
        ORDERED_FINISHED("已下单(已完成)"),
        CHECKED_OUT("已结账");

        private final String displayName;
        OrderStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private int table_id; // 数据库ID
    private int baseId; // 基础ID
    private String displayId; // 显示ID
    private int capacity; // 容量
    private TableStatus status; // 餐桌状态
    private TableType tableType; // 餐桌类型
    private CustomerGroup currentGroup; // 当前占用的顾客组
    private Integer customerGroupId; // 顾客组在数据库中的ID（用于持久化）
    private LocalDateTime startTime; // 占用开始时间
    private LocalDateTime endTime; // 占用结束时间
    private boolean isSplit; // 是否拆分
    private String subTableSuffix; // 子桌后缀
    private Integer mainTableId; // 主桌ID
    private int physicalCapacity; // 物理容量
    private String mergedWith; // 合并伙伴的display_id
    private Integer currentGroupId;
    private int actualSeats = 0; // 实际入座人数
    private transient CustomerGroup pendingGroup;
    private transient OrderStatus orderStatus = OrderStatus.NO_ORDER;
    private LocalDateTime reservedTime;  // 對應 reserved_time
    private String groupWith;            // 對應 group_with

    // ===== 新增属性 =====
    private String currentReservationId;  // 对应 current_reservation_id 字段



    /**
     * 餐桌实体默认构造函数
     *
     * 功能说明：
     * 初始化餐桌对象的核心状态字段为默认值，确保新创建对象处于一致的可使用状态。
     *
     * 默认值设定：
     * - status: VACANT（空闲），新餐桌默认可接受顾客入座
     * - tableType: MAIN（主桌），默认为独立餐桌而非子桌或合并桌
     * - actualSeats: 0，初始无顾客入座，避免空指针或计算错误
     * - orderStatus: NO_ORDER，初始无关联订单，支持后续正常点餐流程
     *
     * 应用场景：
     * - 通过 new 关键字创建新餐桌实例时自动调用
     * - 反序列化或反射创建对象后手动调用以重置状态
     * - 单元测试中快速构造测试数据
     */
    public Tables() {
        // 初始化默认值（可选，但建议）// ===== 新增属性 =====
        this.status = TableStatus.VACANT;
        this.tableType = TableType.MAIN;
        this.actualSeats = 0;
        this.orderStatus = OrderStatus.NO_ORDER;
    }

    /**
     * 创建新餐桌对象（默认空闲状态）
     * @param baseId 数据库存储ID
     * @param capacity 座位容量（1/2/4/6人）
     * @param displayId UI显示编号（如"7"或"7a"）
     */
    public Tables(int baseId, int capacity, String displayId) {
        this.baseId = baseId;
        this.capacity = capacity;
        this.displayId = displayId;
        this.status = TableStatus.VACANT; // 默认状态为空闲
        this.tableType = TableType.MAIN; // 默认类型为主桌
        this.currentGroup = null;
        this.customerGroupId = null;
        this.isSplit = false;
        this.subTableSuffix = null;
        this.mainTableId = null;
        this.physicalCapacity = capacity; // 默认物理容量等于当前容量
    }

    /** 数据库存储ID */
    public int getTableId() {
        return table_id;
    }

    /** 设置数据库存储ID */
    public void setTableId(int table_id) {
        this.table_id = table_id;
    }

    /** 基础餐桌ID（拆分/合并时关联） */
    public int getBaseId() {
        return baseId;
    }

    /**
     * 设置餐桌的基础编号
     *
     * 功能说明：
     * 更新餐桌的baseId字段，用于标识餐桌在物理布局中的逻辑分组或排序基准。
     *
     * @param baseId 待设置的基础编号值
     *
     * 应用场景：
     * - 餐桌初始化时设置默认编号
     * - 餐桌重组或布局调整时更新分组标识
     */
    public void setBaseId(int baseId) {
        this.baseId = baseId;
    }

    /**
     * 获取当前餐桌关联的顾客组主键
     *
     * 功能说明：
     * 返回当前餐桌绑定的顾客组数据库主键，用于快速判断餐桌是否被占用及关联查询。
     *
     * @return 顾客组主键；未关联顾客组时返回null
     *
     * 业务规则：
     * - 仅当餐桌状态为占用时该字段非空
     * - 调用方需自行校验返回值是否为null，避免空指针异常
     */
    public Integer getCurrentGroupId() {
        return currentGroupId;
    }

    /**
     * 设置当前餐桌关联的顾客组主键
     *
     * 功能说明：
     * 更新餐桌的currentGroupId字段，建立或解除餐桌与顾客组的关联关系。
     *
     * @param currentGroupId 顾客组主键；传入null时表示解除关联
     *
     * 使用建议：
     * - 优先使用assignCustomerGroup方法确保对象与ID同步更新
     * - 本方法适用于仅需更新ID引用的内部调用场景
     */
    public void setCurrentGroupId(Integer currentGroupId) {
        this.currentGroupId = currentGroupId;
    }
    /** UI显示编号（如"7a"） */
    public String getDisplayId() {
        return displayId;
    }

    /** 设置UI显示编号 */
    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    /** 座位容量（1/2/4/6人） */
    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
    /** 获取餐桌当前状态 */
    public TableStatus getStatus() {
        return status;
    }

    /** 获取合并后的物理总容量 */
    public int getPhysicalCapacity() {
        return physicalCapacity;
    }

    /** 设置合并后的物理总容量 */
    public void setPhysicalCapacity(int physicalCapacity) {
        this.physicalCapacity = physicalCapacity;
    }

    /** 获取合并餐桌ID（格式"7+8"） */
    public String getMergedWith() {
        return mergedWith;
    }

    /** 设置合并关系标识 */
    public void setMergedWith(String mergedWith) {
        this.mergedWith = mergedWith;
    }

    /**
     * 设置餐桌状态（自动清理关联数据）
     * @note OCCUPIED: 记录开始时间
     *       VACANT/SETTING_UP: 清空所有顾客数据
     *       SPLITTING: 保留拆分标识，清除顾客组
     */
    public void setStatus(TableStatus status) {
        this.status = status;

        if (status == TableStatus.OCCUPIED) {
            // 占用状态：设置开始时间，清空结束时间
            this.startTime = LocalDateTime.now();
            this.endTime = null; // 确保结束时间为空
        } else if (status == TableStatus.VACANT || status == TableStatus.SETTING_UP) {
            // 空闲或准备中状态：清空当前顾客组和相关数据
            this.currentGroup = null;
            this.currentGroupId = null;
            this.startTime = null;
            this.endTime = null;
            this.mergedWith = null;
            this.actualSeats = 0;
            this.currentReservationId = null;  // 🔧 新增：清空预定ID
        }
        // SPLITTING状态需要特殊处理 - 不清空关键数据
        else if (status == TableStatus.SPLITTING) {
            // 保持isSplit和mainTableId等关键信息
            this.currentGroup = null; // 清除当前顾客组引用
            this.startTime = null;    // 清空开始时间
            this.endTime = null;      // 清空结束时间
            this.mergedWith = null;   // 清除合并关系
            this.actualSeats = 0;     // 重置实际座位数
            // 但保留 isSplit = true 和 mainTableId 等拆分相关的关键状态
        }
    }


    /** 餐桌类型（主桌/合并桌/子桌） */
    public TableType getTableType() {
        return tableType;
    }

    /** 设置餐桌类型 */
    public void setTableType(TableType tableType) {
        this.tableType = tableType;
    }


    public Integer getCustomerGroupId() {
        return customerGroupId;
    }

    public void setCustomerGroupId(Integer customerGroupId) {
        this.customerGroupId = customerGroupId;
    }

    /**
     * 用餐开始时间（占用时记录）
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** 设置用餐开始时间 */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * 用餐结束时间（离店时记录）
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /** 设置用餐结束时间 */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }



    /** 是否处于拆分状态 */
    public boolean isSplit() {
        return isSplit;
    }

    /** 设置拆分状态标记 */
    public void setSplit(boolean split) {
        isSplit = split;
    }

    /** 子餐桌后缀（如"a","b"，主桌为空） */
    public String getSubTableSuffix() {
        return subTableSuffix;
    }

    /** 设置子餐桌标识后缀 */
    public void setSubTableSuffix(String subTableSuffix) {
        this.subTableSuffix = subTableSuffix;
    }

    /** 所属主餐桌ID（子桌时有效） */
    public Integer getMainTableId() {
        return mainTableId;
    }

    /** 设置所属主餐桌ID */
    public void setMainTableId(Integer mainTableId) {
        this.mainTableId = mainTableId;
    }

    /** 格式化开始时间（空时返回"未开始"） */
    public String getFormattedStartTime() {
        if (startTime != null) {
            return startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "未开始";
    }

    /** 格式化结束时间（空时返回"未结束"） */
    public String getFormattedEndTime() {
        if (endTime != null) {
            return endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "未结束";
    }

    /**
     * 设置当前餐桌关联的顾客组对象
     *
     * 功能说明：
     * 仅更新顾客组对象引用，不修改currentGroupId字段。
     * 适用于内部状态同步场景，由调用方确保ID与对象的一致性。
     *
     * @param group 待关联的顾客组对象；传入null时表示解除关联
     *
     */
    public void setCurrentGroup(CustomerGroup group) {
        this.currentGroup = group;
        // 不再设置 currentGroupId
    }

    /**
     * 分配顾客组到当前餐桌（确保对象与ID同步）
     *
     * 功能说明：
     * 1. 同步设置顾客组对象与对应的顾客组ID，避免数据不一致
     * 2. 若顾客组非空且餐桌状态为占用中，自动记录入座开始时间
     *
     * @param group 待分配的顾客组对象；传入null时表示解除关联并清空ID
     *
     * 业务规则：
     * - 仅当餐桌状态为OCCUPIED时更新startTime，确保入座时间准确
     * - 解除关联时同时清空currentGroup与currentGroupId，保持状态纯净
     *
     * 应用场景：
     * - 顾客入座时绑定顾客组与餐桌
     * - 换桌操作时更新关联关系并重置时间戳
     */
    public void assignCustomerGroup(CustomerGroup group) {
        this.currentGroup = group;
        this.currentGroupId = (group != null) ? group.getGroup_id() : null;
        // 同时设置时间戳
        if (group != null && this.status == TableStatus.OCCUPIED) {
            this.startTime = LocalDateTime.now();
        }
    }


    public boolean isConsistent() {
        if (status == TableStatus.OCCUPIED) {
            if (currentGroupId == null) {
                System.err.println("⚠️ 餐桌 #" + displayId + " 狀態為OCCUPIED，但currentGroupId為空");
                return false;
            }

            if (currentGroup == null) {
                // 允許currentGroup為null，但currentGroupId必須有值
                return true;
            }

            // 當兩者都存在時，檢查ID是否匹配
            return currentGroupId.equals(currentGroup.getGroup_id());
        }
        return true;
    }

    /**
     * 获取当前餐桌关联的顾客组对象
     *
     * 功能说明：
     * 返回当前餐桌绑定的顾客组对象。若顾客组ID存在但对象引用为null，
     * @return 顾客组对象；未关联顾客组时返回null
     *
     * 容错处理：
     * - 捕获加载异常时静默处理，保持业务状态不变，避免中断主流程
     * - 调用方需自行校验返回值是否为null，再进行后续操作
     */
    public CustomerGroup getCurrentGroup() {
        // 如果ID存在但对象为null，尝试重新加载
        if (currentGroupId != null && currentGroup == null) {
            try {
                // 这里应该调用DAO加载，但为避免循环依赖，先简单处理
                // 实际应用中应有适当的数据加载机制
                System.err.println("警告：餐桌 #" + displayId + " 有group ID但无group对象");
            } catch (Exception e) {
                // 忽略错误，保持业务状态不变
            }
        }
        return currentGroup;
    }

    /** 合并餐桌实际使用座位数（独立餐桌始终=0） */
    public int getActualSeats() {
        return actualSeats;
    }

    /** 设置合并餐桌实际占用座位数 */
    public void setActualSeats(int actualSeats) {
        this.actualSeats = actualSeats;
    }

    /**
     * 获取待分配的顾客组
     * @return 未就座的顾客组引用
     */
    public CustomerGroup getPendingGroup() {
        return pendingGroup;
    }

    /** 获取餐桌当前的订单状态枚举值，用于界面展示与业务流程校验*/
    public OrderStatus getOrderStatus() { return orderStatus; }

    /** 设置餐桌的订单状态，订单状态变更时同步更新以触发界面刷新*/
    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    /**
     * 獲取下次預定時間（用於 1.5 小時鎖定）
     * @return reserved_time
     */
    public LocalDateTime getReservedTime() {
        return reservedTime;
    }

    /**
     * 設置下次預定時間
     * @param reservedTime 預定時間
     */
    public void setReservedTime(LocalDateTime reservedTime) {
        this.reservedTime = reservedTime;
    }

    /**
     * 獲取組合預定關聯桌號（如 "7,8"）
     * @return group_with
     */
    public String getGroupWith() {
        return groupWith;
    }

    /**
     * 設置組合預定關聯桌號
     * @param groupWith 關聯桌號字符串
     */
    public void setGroupWith(String groupWith) {
        this.groupWith = groupWith;
    }


    /** 获取当前餐桌关联的预约记录编号，用于支持预约顾客的入座与订单关联*/
    public String getCurrentReservationId() {
        return currentReservationId;
    }

    /** 设置当前餐桌关联的预约记录编号，顾客入座时绑定预约与餐桌*/
    public void setCurrentReservationId(String currentReservationId) {
        this.currentReservationId = currentReservationId;
    }


    /**
     * 生成动态餐桌图标（含椅子占用状态）
     * @param tableColor 餐桌主体颜色
     * @note 支持合并/拆分/聚餐桌状态，不同容量布局（1-6人）
     */
    //7.2. 自定義繪圖與餐桌狀態可視化 (Icon 接口)
    //技術說明：重寫 Icon.paintIcon() 方法，使用 Graphics2D 動態繪製帶椅子狀態的餐桌圖標，實現業務狀態的直觀表達。
    //將業務狀態（占用人數、餐桌類型）轉化為視覺元素（椅子顏色、佈局），實現「所見即所得」的直觀管理。通過 RenderingHints 啟用抗鋸齒，提升圖標繪製品質。此設計避免了預先渲染大量圖片的內存開銷，實現動態按需繪製。
    public Icon createTableIcon(Color tableColor) {
        int size = 80;
        int chairSize = 15;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 颜色常量
                Color brown = new Color(139, 69, 19); // 空位颜色（棕色）
                Color red = Color.RED;                 // 占位颜色

                // ═══════════════════════════════════════════════════════════
                // 【1】绘制餐桌主体（圆形）
                // ═══════════════════════════════════════════════════════════
                g2.setColor(tableColor);
                g2.fillOval(x + size / 4, y + size / 4, size / 2, size / 2);
                g2.setColor(Color.DARK_GRAY);
                g2.drawOval(x + size / 4, y + size / 4, size / 2, size / 2);

                // ═══════════════════════════════════════════════════════════
                // 【2】确定椅子数量和颜色数组
                // ═══════════════════════════════════════════════════════════
                int chairCount;
                Color[] chairColors;

                // 状态判断标志
                boolean isOccupied = (getStatus() == TableStatus.OCCUPIED);
                boolean isMerged = (tableType == TableType.MERGED && isOccupied);
                boolean isGrouped = (tableType == TableType.GROUPED && isOccupied);
                boolean isSubTable = (isSplit && subTableSuffix != null);

                if (isOccupied && currentGroup != null) {
                    // ── 占用状态：根据餐桌类型计算占用椅子数 ──
                    chairCount = capacity;  // 椅子数量 = 餐桌容量
                    chairColors = new Color[chairCount];
                    Arrays.fill(chairColors, brown);  // 默认全部为空位（棕色）

                    int occupiedChairs;

                    if (isMerged) {
                        // 🔹 合并餐桌：使用 actualSeats（该桌实际入座人数）
                        occupiedChairs = actualSeats;
                    }
                    else if (isGrouped) {
                        //錯誤例子    occupiedChairs = capacity;  这里写死了 capacity（6人）
                        // 🔹 聚餐桌：每张桌独立显示自己的占用情况
                        occupiedChairs = actualSeats;
                    }
                    else if (isSubTable) {
                        // 🔹 拆分餐桌：使用顾客组人数（但不超过子桌容量）
                        occupiedChairs = Math.min(currentGroup.getGroupSize(), capacity);
                    }
                    else {
                        // 🔹 普通餐桌：顾客组人数与容量的较小值
                        occupiedChairs = Math.min(currentGroup.getGroupSize(), capacity);
                    }

                    // 设置占用的椅子为红色
                    for (int i = 0; i < Math.min(occupiedChairs, chairCount); i++) {
                        chairColors[i] = red;
                    }
                }
                else {
                    // ── 非占用状态：全部椅子为空位 ──
                    chairCount = capacity;
                    chairColors = new Color[chairCount];
                    Arrays.fill(chairColors, brown);
                }

                // ═══════════════════════════════════════════════════════════
                // 【3】根据容量绘制椅子（不同布局）
                // ═══════════════════════════════════════════════════════════
                if (capacity == 1) {
                    // ── 1人桌：座位在左侧边缘 ──
                    if ("a".equals(subTableSuffix)) {
                        // 子桌a：座位在左边
                        drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    }
                    else if ("b".equals(subTableSuffix)) {
                        // 子桌b：座位在右边
                        drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    }
                    else {
                        // 普通1人桌：座位在左边
                        drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    }
                }
                else if (capacity == 2) {
                    // ── 2人桌：左右各一个座位 ──
                    drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);      // 左
                    drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[1]); // 右
                }
                else if (capacity == 4) {
                    // ── 4人桌：四边各一个座位（上右下左顺时针）─
                    drawChair(g2, x + size / 2 - chairSize / 2, y, chairSize, chairColors[0]);              // 上
                    drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[1]); // 右
                    drawChair(g2, x + size / 2 - chairSize / 2, y + size - chairSize, chairSize, chairColors[2]); // 下
                    drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[3]);              // 左
                }
                else if (capacity == 6) {
                    // ── 6人桌：圆形均匀排列（6个方向）─
                    int centerX = x + size / 2;
                    int centerY = y + size / 2;
                    int radius = (int) (size * 0.4);  // 椅子环绕半径

                    for (int i = 0; i < 6; i++) {
                        // 计算角度：从顶部开始，顺时针每60度一个
                        double angle = Math.toRadians(360.0 / 6 * i - 90);
                        int chairX = (int) (centerX + radius * Math.cos(angle) - chairSize / 2);
                        int chairY = (int) (centerY + radius * Math.sin(angle) - chairSize / 2);
                        drawChair(g2, chairX, chairY, chairSize, chairColors[i]);
                    }
                }
                else {
                    // ── 其他容量：兜底显示一个椅子 ──
                    drawChair(g2, x + size / 2 - chairSize / 2, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                }

                g2.dispose();
            }

            /**
             * 辅助方法：绘制单个椅子（带边框的正方形）
             */
            private void drawChair(Graphics2D g2, int x, int y, int size, Color color) {
                g2.setColor(color);
                g2.fillRect(x, y, size, size);      // 填充颜色
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, size, size);      // 黑色边框
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

}
