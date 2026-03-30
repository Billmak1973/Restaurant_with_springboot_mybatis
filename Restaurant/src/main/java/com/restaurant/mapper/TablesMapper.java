package com.restaurant.mapper;

import com.restaurant.entity.Tables;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TablesMapper {

    // ===== 查询操作 =====

    /**
     * 查询所有餐桌
     * @return 餐桌列表
     */
    List<Tables> findAllTables();

    /**
     * 根据 table_id 查询单个餐桌
     * @param id 餐桌ID
     * @return 餐桌对象，不存在返回 null
     */
    Tables findById(@Param("id") int id);

    /**
     * 根据 display_id 查询单个餐桌
     * @param displayId 显示ID（如 "7" 或 "7a"）
     * @return 餐桌对象，不存在返回 null
     */
    Tables findByDisplayId(@Param("displayId") String displayId);

    /**
     * 查找可用餐桌（空闲状态）
     * @param capacity 最小容量（0 表示不限制）
     * @param tableType 餐桌类型（MAIN/MERGED/SUBTABLE，null 表示不限制）
     * @return 可用餐桌列表
     */
    List<Tables> findAvailableTables(
            @Param("capacity") int capacity,
            @Param("tableType") String tableType);

    /**
     * 根据主桌 ID 查询子桌列表
     * @param mainTableId 主桌 ID
     * @return 子桌列表
     */
    List<Tables> findSubTablesByMainId(@Param("mainTableId") int mainTableId);

    /**
     * 查找相邻的可用餐桌对（用于合并）
     * @param capacity 餐桌容量
     * @param colsPerRow 每行列数（用于计算相邻关系）
     * @return 相邻餐桌对列表
     */
    List<List<Tables>> findAdjacentAvailableTables(
            @Param("capacity") int capacity,
            @Param("colsPerRow") int colsPerRow);

    /**
     * 检查是否已有餐桌数据
     * @return true=已有数据
     */
    boolean hasExistingTableData();

    // ===== 插入操作 =====

    /**
     * 保存餐桌（返回自增主键）
     * @param table 餐桌对象
     * @return 影响行数
     */
    int save(Tables table);

    /**
     * 批量插入默认餐桌数据
     * @param tables 餐桌列表
     * @return 影响行数
     */
    int initializeDefaultTables(@Param("tables") List<Tables> tables);

    /**
     * 保存子桌（返回自增主键）
     * @param subTable 子桌对象
     * @return 影响行数
     */
    int saveSubTable(Tables subTable);

    // ===== 更新操作 =====

    /**
     * 更新餐桌全部字段
     * @param table 餐桌对象
     * @return 影响行数
     */
    int update(Tables table);

    /**
     * 更新餐桌状态
     * @param tableId 餐桌ID
     * @param status 新状态（VACANT/OCCUPIED/SETTING_UP/SPLITTING）
     * @param currentGroupId 顾客组ID（可为 null）
     * @param actualSeats 实际入座人数
     * @param startTime 开始时间
     * @return 影响行数
     */
    int updateTableStatus(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("startTime") LocalDateTime startTime);

    /**
     * 更新拆分状态
     * @param tableId 餐桌ID
     * @param isSplit 是否拆分
     * @return 影响行数
     */
    int updateSplitStatus(
            @Param("tableId") int tableId,
            @Param("isSplit") boolean isSplit);

    /**
     * 更新合并状态（双桌同时更新）
     * @param mainTableId 主桌ID
     * @param partnerTableId 伙伴桌ID
     * @param mergedWith1 主桌的 merged_with 值
     * @param mergedWith2 伙伴桌的 merged_with 值
     * @param groupId 关联顾客组ID
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


    // ===== 合并餐桌状态更新（拆分成两个原子方法）=====

    /**
     * 更新主桌为合并占用状态
     */
    int updateTableToMergedOccupied(
            @Param("tableId") int tableId,
            @Param("mergedWith") String mergedWith,
            @Param("groupId") Integer groupId,
            @Param("actualSeats") int actualSeats
    );

    /**
     * 更新伙伴桌为合并占用状态
     */
    int updatePartnerTableToMergedOccupied(
            @Param("tableId") int tableId,
            @Param("mergedWith") String mergedWith,
            @Param("groupId") Integer groupId,
            @Param("actualSeats") int actualSeats
    );

    /**
     * 将合并餐桌对恢复为空闲状态
     * @param tableId1 餐桌ID 1
     * @param tableId2 餐桌ID 2
     * @return 影响行数
     */
    int updateMergedPairToVacant(
            @Param("tableId1") int tableId1,
            @Param("tableId2") int tableId2);

    /**
     * 顾客离店时更新餐桌状态
     * @param tableId 餐桌ID
     * @param status 新状态
     * @param currentGroupId 顾客组ID（可为 null）
     * @param actualSeats 实际座位数
     * @param originalTableType 原始餐桌类型（用于判断是否恢复为 MAIN）
     * @return 影响行数
     */
    int updateTableStatusForDeparture(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("originalTableType") String originalTableType);

    // ===== 拆分餐桌相关原子操作（供 Service 层组合）=====

    /**
     * 更新主桌为拆分状态
     * @param mainTableId 主桌ID
     * @return 影响行数
     */
    int updateMainTableToSplitting(@Param("mainTableId") int mainTableId);

    /**
     * 获取主桌基础信息
     * @param mainTableId 主桌ID
     * @return 包含 base_id 和 display_id 的 Map
     */
    java.util.Map<String, Object> getMainTableBaseInfo(@Param("mainTableId") int mainTableId);

    /**
     * 更新顾客组的餐桌关联
     * @param groupId 顾客组ID
     * @param tableId 新餐桌ID
     * @return 影响行数
     */
    int updateCustomerGroupTableId(
            @Param("groupId") int groupId,
            @Param("tableId") int tableId);


    // ===== 合并餐桌（恢复拆分）相关 =====

    /**
     * 恢复主桌状态：取消拆分标记 + 设为空闲
     * @param tableId 主桌ID
     * @param status 新状态（通常为 "VACANT"）
     * @param isSplit 拆分标记（设为 false）
     * @return 影响行数
     */
    int restoreMainTableAfterRecombine(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("isSplit") boolean isSplit
    );


    // ===== 删除操作 =====

    /**
     * 批量删除子桌
     * @param subTableIds 子桌ID列表
     * @return 影响行数
     */
    int deleteSubTables(@Param("subTableIds") List<Integer> subTableIds);




    /**
     * 批量插入子桌（一次插入两条记录）
     * @param subTables 子桌列表（必须2个元素）
     * @return 影响行数
     */
    int batchInsertSubTables(@Param("subTables") List<Tables> subTables);



    // ===== 拆分餐桌原子操作（替代原 splitOccupiedTable）=====



    int updateTableForReservation(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("reservedTime") LocalDateTime reservedTime

    );

    /**
     * 客人入座時更新餐桌狀態（包含預定 ID 綁定）
     *
     * @param tableId 餐桌主鍵 ID
     * @param status 新狀態（通常為 OCCUPIED）
     * @param currentGroupId 當前顧客組 ID
     * @param actualSeats 實際入座人數
     * @param startTime 入座時間
     * @param reservationId 關聯的預定記錄 ID（對應表字段：current_reservation_id）
     *                      🔧 用途：記錄該餐桌是由哪個預定單轉化的，離店時用於刪除預定記錄
     * @return 影響行數
     */
    int updateTableForCheckInWithReservation(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("startTime") LocalDateTime startTime,
            @Param("reservationId") String reservationId  // 🔧 新增参数
    );


    // com.restaurant.mapper.TablesMapper.java

    // 刪除 @Update 註解
    void mergeTables(@Param("tableId") int tableId,
                     @Param("mergedWith") String mergedWith);

    /**
     * 更新伙伴桌入座状态（合并桌专用）
     */
    int updatePartnerTableForCheckIn(
            @Param("tableId") int tableId,
            @Param("status") String status,
            @Param("currentGroupId") Integer currentGroupId,
            @Param("actualSeats") int actualSeats,
            @Param("reservationId") String reservationId  // 🔧 新增参数
    );
    /**
     * 更新餐桌為聚餐桌預約狀態
     * @param tableId 餐桌主鍵
     * @param groupWith 關聯桌號字符串（如 "7,8,9"）
     * @param tableType 餐桌類型（"GROUPED"）
     * @return 影響行數
     */
    int updateTableForGroupReservation(
            @Param("tableId") int tableId,
            @Param("groupWith") String groupWith,
            @Param("tableType") String tableType
    );
    /**
     * 更新餐桌類型和 merged_with 字段（用於合併桌）
     * @param tableId 餐桌主鍵
     * @param tableType 新類型（"MERGED"）
     * @param mergedWith 伙伴桌的 display_id（如 "8"）
     * @return 影響行數
     */
    int updateTableTypeAndMergedWith(
            @Param("tableId") int tableId,
            @Param("tableType") String tableType,
            @Param("mergedWith") String mergedWith
    );

    /**
     * 更新餐桌類型和 group_with 字段（用於聚餐桌）
     * @param tableId 餐桌主鍵
     * @param tableType 新類型（"GROUPED"）
     * @param groupWith 關聯桌號列表（如 "10,11,12"）
     * @return 影響行數
     */
    int updateTableTypeAndGroupWith(
            @Param("tableId") int tableId,
            @Param("tableType") String tableType,
            @Param("groupWith") String groupWith
    );
}