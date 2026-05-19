package com.restaurant.mapper;

import com.restaurant.entity.Tables;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TablesMapper {

    // ═══════════════════════════════════════════════════════════
    // 【查询操作】- 餐桌数据读取
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询所有餐桌（按 base_id 和 display_id 排序）
     * @return 餐桌列表
     */
    List<Tables> findAllTables();

    /**
     * 根据 table_id 查询单个餐桌
     * @param id 餐桌主键 ID
     * @return 餐桌对象，不存在返回 null
     */
    Tables findById(@Param("id") int id);

    /**
     * 根据 display_id 查询单个餐桌
     * @param displayId 显示 ID（如 "7" 或 "7a"）
     * @return 餐桌对象，不存在返回 null
     */
    Tables findByDisplayId(@Param("displayId") String displayId);

    /**
     * 查找可用餐桌（空闲状态 + 容量过滤 + 类型过滤）
     * @param capacity 最小容量（0 表示不限制）
     * @param tableType 餐桌类型（MAIN/MERGED/SUBTABLE，null 表示不限制）
     * @return 可用餐桌列表（按容量和 base_id 升序）
     */
    List<Tables> findAvailableTables(
            @Param("capacity") int capacity,
            @Param("tableType") String tableType);

    /**
     * 根据主桌 ID 查询子桌列表（拆分餐桌专用）
     * @param mainTableId 主桌 ID
     * @return 子桌列表
     */
    List<Tables> findSubTablesByMainId(@Param("mainTableId") int mainTableId);


    // ═══════════════════════════════════════════════════════════
    // 【插入操作】- 餐桌数据创建
    // ═══════════════════════════════════════════════════════════

    /**
     * 保存子桌（返回自增主键，拆分餐桌专用）
     * @param subTable 子桌对象
     * @return 影响行数
     */
    int saveSubTable(Tables subTable);


    // ═══════════════════════════════════════════════════════════
    // 【更新操作】- 餐桌状态与属性变更
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新餐桌全部字段（通用更新方法）
     * @param table 餐桌对象（需包含 table_id）
     * @return 影响行数
     */
    int update(Tables table);

    /**
     * 更新餐桌状态（核心方法：支持占用/空闲/准备中等状态切换）
     * @param tableId 餐桌 ID
     * @param status 新状态（VACANT/OCCUPIED/SETTING_UP/SPLITTING/RESERVED）
     * @param currentGroupId 顾客组 ID（可为 null）
     * @param actualSeats 实际入座人数
     * @param startTime 开始时间（占用时设置）
     * @return 影响行数
     */
    int updateTableStatus(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("startTime") LocalDateTime startTime);

    /**
     * 更新拆分状态（标记餐桌为拆分中）
     * @param tableId 餐桌 ID
     * @param isSplit 是否拆分（true=拆分中）
     * @return 影响行数
     */
    int updateSplitStatus(
            @Param("tableId") int tableId,
            @Param("isSplit") boolean isSplit);

    /**
     * 更新合并状态（双桌同时更新，旧方法）
     * @param mainTableId 主桌 ID
     * @param partnerTableId 伙伴桌 ID
     * @param mergedWith1 主桌的 merged_with 值
     * @param mergedWith2 伙伴桌的 merged_with 值
     * @param groupId 关联顾客组 ID
     * @param actualSeats1 主桌实际座位数
     * @param actualSeats2 伙伴桌实际座位数
     * @return 影响行数
     */
    int updateMergeStatus(
            @Param("mainTableId") int mainTableId,
            @Param("partnerTableId") int partnerTableId,
            @Param("mergedWith1") String mergedWith1,
            @Param("mergedWith2") String mergedWith2,
            @Param("groupId") Integer groupId,
            @Param("actualSeats1") int actualSeats1,
            @Param("actualSeats2") int actualSeats2);


    // ═══════════════════════════════════════════════════════════
    // 【合并餐桌 - 原子操作】- 支持事务内组合调用
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新主桌为合并占用状态（原子操作 1/2）
     * @param tableId 餐桌 ID
     * @param mergedWith 伙伴桌的 display_id（如 "8"）
     * @param groupId 关联顾客组 ID
     * @param actualSeats 实际入座人数
     * @return 影响行数
     */
    int updateTableToMergedOccupied(
            @Param("tableId") int tableId,
            @Param("mergedWith") String mergedWith,
            @Param("groupId") Integer groupId,
            @Param("actualSeats") int actualSeats
    );

    /**
     * 更新伙伴桌为合并占用状态（原子操作 2/2）
     * @param tableId 餐桌 ID
     * @param mergedWith 伙伴桌的 display_id（如 "7"）
     * @param groupId 关联顾客组 ID
     * @param actualSeats 实际入座人数
     * @return 影响行数
     */
    int updatePartnerTableToMergedOccupied(
            @Param("tableId") int tableId,
            @Param("mergedWith") String mergedWith,
            @Param("groupId") Integer groupId,
            @Param("actualSeats") int actualSeats
    );

    /**
     * 将合并餐桌对恢复为空闲状态（取消合并时用）
     * @param tableId1 餐桌 ID 1
     * @param tableId2 餐桌 ID 2
     * @return 影响行数
     */
    int updateMergedPairToVacant(
            @Param("tableId1") int tableId1,
            @Param("tableId2") int tableId2);

    /**
     * 顾客离店时更新餐桌状态（支持恢复餐桌类型）
     * @param tableId 餐桌 ID
     * @param status 新状态（通常为 SETTING_UP）
     * @param currentGroupId 顾客组 ID（设为 null）
     * @param actualSeats 实际座位数（设为 0）
     * @param originalTableType 原始餐桌类型（用于判断是否恢复为 MAIN）
     * @return 影响行数
     */
    int updateTableStatusForDeparture(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("originalTableType") String originalTableType);


    // ═══════════════════════════════════════════════════════════
    // 【拆分餐桌 - 原子操作】- 支持事务内组合调用
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新主桌为拆分状态（拆分餐桌第一步）
     * @param mainTableId 主桌 ID
     * @return 影响行数
     */
    int updateMainTableToSplitting(@Param("mainTableId") int mainTableId);

    /**
     * 批量删除子桌（恢复拆分时用）
     * @param subTableIds 子桌 ID 列表
     * @return 影响行数
     */
    int deleteSubTables(@Param("subTableIds") List<Integer> subTableIds);

    /**
     * 批量插入子桌（一次插入两条记录，拆分餐桌专用）
     * @param subTables 子桌列表（必须 2 个元素）
     * @return 影响行数
     */
    int batchInsertSubTables(@Param("subTables") List<Tables> subTables);


    // ═══════════════════════════════════════════════════════════
    // 【恢复拆分】- 合并餐桌（取消拆分）
    // ═══════════════════════════════════════════════════════════

    /**
     * 恢复主桌状态：取消拆分标记 + 设为空闲 + 重置类型
     * @param tableId 主桌 ID
     * @param status 新状态（通常为 "VACANT"）
     * @param isSplit 拆分标记（设为 false）
     * @return 影响行数
     */
    int restoreMainTableAfterRecombine(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("isSplit") boolean isSplit
    );


    // ═══════════════════════════════════════════════════════════
    // 【预定相关】- 餐桌预定状态管理
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新餐桌为预定状态（1.5 小时内锁定餐桌）
     * @param tableId 餐桌 ID
     * @param status 新状态（通常为 "RESERVED"）
     * @param reservedTime 下次预定时间（用于 1.5 小时锁定）
     * @return 影响行数
     */
    int updateTableForReservation(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("reservedTime") LocalDateTime reservedTime
    );

    /**
     * 更新餐桌为预定状态（带 reservation_id 绑定，新版本）
     * @param tableId 餐桌 ID
     * @param status 新状态（通常为 "RESERVED"）
     * @param reservedTime 下次预定时间
     * @param reservationId 关联的预定记录 ID
     * @return 影响行数
     */
    int updateTableForReservationWithId(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("reservedTime") LocalDateTime reservedTime,
            @Param("reservationId") String reservationId
    );

    /**
     * 客人入座时更新餐桌状态（包含预定 ID 绑定）
     * @param tableId 餐桌主键 ID
     * @param status 新状态（通常为 OCCUPIED）
     * @param currentGroupId 当前顾客组 ID
     * @param actualSeats 实际入座人数
     * @param startTime 入座时间
     * @param reservationId 关联的预定记录 ID（离店时用于删除预定记录）
     * @return 影响行数
     */
    int updateTableForCheckInWithReservation(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("startTime") LocalDateTime startTime,
            @Param("reservationId") String reservationId
    );

    /**
     * 更新伙伴桌入座状态（合并桌专用，支持 reservation_id 绑定）
     * @param tableId 餐桌 ID
     * @param status 新状态
     * @param currentGroupId 顾客组 ID
     * @param actualSeats 实际入座人数
     * @param reservationId 关联的预定记录 ID
     * @return 影响行数
     */
    int updatePartnerTableForCheckIn(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("reservationId") String reservationId
    );

    /**
     * 更新餐桌为聚餐桌预定状态（3 张或以上 6 人桌）
     * @param tableId 餐桌主键
     * @param groupWith 关联桌号字符串（如 "7,8,9"）
     * @param tableType 餐桌类型（"GROUPED"）
     * @return 影响行数
     */
    int updateTableForGroupReservation(
            @Param("tableId") int tableId,
            @Param("groupWith") String groupWith,
            @Param("tableType") String tableType
    );

    /**
     * 更新聚餐桌入座状态（客人实际入座时调用）
     * @param tableId 餐桌主键
     * @param status 新状态（通常为 OCCUPIED）
     * @param currentGroupId 当前顾客组 ID
     * @param actualSeats 实际入座人数
     * @param groupWith 关联桌号列表（如 "10,11,12"）
     * @param tableType 餐桌类型（"GROUPED"）
     * @return 影响行数
     */
    int updateTableForGroupedCheckIn(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("groupWith") String groupWith,
            @Param("tableType") String tableType
    );


    // ═══════════════════════════════════════════════════════════
    // 【餐桌类型管理】- 合并桌/聚餐桌类型切换
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新餐桌类型和 merged_with 字段（合并桌专用）
     * @param tableId 餐桌主键
     * @param tableType 新类型（"MERGED"）
     * @param mergedWith 伙伴桌的 display_id（如 "8"）
     * @return 影响行数
     */
    int updateTableTypeAndMergedWith(
            @Param("tableId") int tableId,
            @Param("tableType") String tableType,
            @Param("mergedWith") String mergedWith
    );

    /**
     * 更新餐桌类型和 group_with 字段（聚餐桌专用）
     * @param tableId 餐桌主键
     * @param tableType 新类型（"GROUPED"）
     * @param groupWith 关联桌号列表（如 "10,11,12"）
     * @return 影响行数
     */
    int updateTableTypeAndGroupWith(
            @Param("tableId") int tableId,
            @Param("tableType") String tableType,
            @Param("groupWith") String groupWith
    );

    /**
     * 合并餐桌：设置 merged_with 字段（旧方法，建议用原子操作替代）
     * @param tableId 餐桌 ID
     * @param mergedWith 伙伴桌的 display_id
     */
    void mergeTables(@Param("tableId") int tableId,
                     @Param("mergedWith") String mergedWith);


    // ═══════════════════════════════════════════════════════════
    // 【清理/重置操作】- 取消预定/释放餐桌
    // ═══════════════════════════════════════════════════════════

    /**
     * 取消预约时还原餐桌：状态改 VACANT + 类型重置 MAIN + 清空关联字段
     * @param tableId 餐桌主键 ID
     * @return 影响行数
     */
    int resetTableAfterReservationCancel(@Param("tableId") int tableId);

    /**
     * 重置聚餐桌为空闲状态（清空 group_with 等关联字段）
     * @param tableId 餐桌主键 ID
     * @return 影响行数
     */
    int resetGroupedTableToVacant(@Param("tableId") int tableId);

    /**
     * 清空餐桌的 current_reservation_id 字段
     * @param tableId 餐桌主键 ID
     * @return 影响行数
     */
    int clearCurrentReservationId(@Param("tableId") int tableId);


    // ═══════════════════════════════════════════════════════════
    // 【辅助查询】- 业务逻辑支撑
    // ═══════════════════════════════════════════════════════════

    /**
     * 检查餐桌是否有任何订单记录（用于判断能否清空餐桌）
     * @param tableId 餐桌 ID
     * @return true=有记录，false=无记录
     */
    boolean hasAnyOrders(@Param("tableId") int tableId);

}