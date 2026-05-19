package com.restaurant.mapper;

import com.restaurant.entity.TableReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TableReservationMapper {

    // ═══════════════════════════════════════════════════════════
    // 【基础 CRUD 操作】
    // ═══════════════════════════════════════════════════════════

    /**
     * 插入新的预约记录
     * @param reservation 预约实体对象
     * @return 影响行数
     */
    int insert(TableReservation reservation);

    /**
     * 根据预约号查询预约记录（基础字段）
     * @param id 预约号（reservation_id）
     * @return TableReservation 对象，不存在返回 null
     */
    TableReservation findById(@Param("id") String id);

    /**
     * 🔧 根据预约号查询完整预约详情（包含所有字段，含 rescheduled_time 等）
     * @param reservationId 预约号
     * @return TableReservation 对象，不存在返回 null
     */
    TableReservation findDetailById(@Param("id") String reservationId);

    /**
     * 根据预约号更新预约状态
     * @param id 预约号
     * @param status 新状态值
     * @return 影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 检查预约号是否已存在（用于生成唯一预约号）
     * @param code 预约号
     * @return true=已存在，false=不存在
     */
    boolean existsByCode(@Param("code") String code);

    /**
     * 获取当天预约号的最大顺序号（用于生成新预约号）
     * @param datePrefix 日期前缀（如 "R20260322"）
     * @return 最大顺序号，无记录返回 null
     */
    Integer getMaxSequenceToday(@Param("datePrefix") String datePrefix);

    /**
     * 根据预约号删除预约记录
     * @param reservationId 预约号
     * @return 影响行数
     */
    int delete(@Param("id") String reservationId);


    // ═══════════════════════════════════════════════════════════
    // 【餐桌关联查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据餐桌ID查找当前有效的预约号
     * @param tableId 餐桌主键ID
     * @return 预约号，无有效预约返回 null
     */
    String findActiveReservationIdByTableId(@Param("tableId") Integer tableId);

    /**
     * 根据餐桌显示号查找关联的预约记录
     * @param tableId 餐桌显示号（如 "7" 或 "7a"）
     * @return TableReservation 对象，不存在返回 null
     */
    TableReservation findReservationByTableId(@Param("tableId") String tableId);

    /**
     * 更新预约记录中关联的具体桌号列表
     * @param reservationId 预约号
     * @param tableIds 桌号列表字符串（如 "7,8,9"）
     * @return 影响行数
     */
    int updateReservedTableIds(@Param("id") String reservationId, @Param("tableIds") String tableIds);


    // ═══════════════════════════════════════════════════════════
    // 【预约列表查询 - 监控/日志用】
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询数量模式预约记录（用于日志显示）
     * @return Map 列表，包含预约基本信息
     */
    List<Map<String, Object>> findQuantityModeReservationsForLog();

    /**
     * 查询预点餐模式的预约记录（用于监控面板）
     * @return Map 列表，包含预点餐预约信息
     */
    List<Map<String, Object>> findPreOrderReservationsForMonitor();


    // ═══════════════════════════════════════════════════════════
    // 【模糊查询 - 支持多种条件】
    // ═══════════════════════════════════════════════════════════

    /**
     * 🔧 根据预约号片段模糊查询（仅返回基本信息，用于 CREATE/ASSIGN 模式）
     * @param codeFragment 预约号片段
     * @return Map 列表，包含预约基本信息
     */
    List<Map<String, Object>> findReservationsByCodeFragment(@Param("codeFragment") String codeFragment);

    /**
     * 🔧 根据电话号码后4位模糊查询（仅返回基本信息）
     * @param phoneLast4 电话号码后4位
     * @return Map 列表，包含预约基本信息
     */
    List<Map<String, Object>> findReservationsByPhoneLast4(@Param("phoneLast4") String phoneLast4);

    /**
     * 🔧 根据预约号片段查询完整预约详情（支持模糊查询，用于 EDIT_TIME 模式）
     * @param codeFragment 预约号片段
     * @return TableReservation 列表，包含完整预约信息
     */
    List<TableReservation> findDetailByCodeFragment(@Param("codeFragment") String codeFragment);

    /**
     * 🔧 CANCEL 模式专用：根据预约号片段查询完整预约详情（支持所有状态）
     * @param codeFragment 预约号片段
     * @return TableReservation 列表，包含完整预约信息（状态：PRE_CONFIRMED/CONFIRMED/NO_SHOW/DELAYED）
     */
    List<TableReservation> findDetailByCodeFragmentForCancel(@Param("codeFragment") String codeFragment);


    // ═══════════════════════════════════════════════════════════
    // 【预约记录更新 - 动态字段】
    // ═══════════════════════════════════════════════════════════

    /**
     * 动态更新预约记录（只更新非 null 字段，避免覆盖其他数据）
     * @param reservation 包含待更新字段的预约对象
     * @return 影响行数
     */
    int updateReservation(TableReservation reservation);


    // ═══════════════════════════════════════════════════════════
    // 【特殊业务操作】
    // ═══════════════════════════════════════════════════════════

    /**
     * 记录取消预约时没收的定金（写入 forfeited_deposits 表）
     * @param reservationId 预约号
     * @param customerName 客户姓名
     * @param customerPhone 客户电话
     * @param reservationTime 原预约时间
     * @param forfeitedAmount 没收金额
     * @param reason 取消原因
     * @return 影响行数
     */
    int insertForfeitedDeposit(@Param("reservationId") String reservationId,
                               @Param("customerName") String customerName,
                               @Param("customerPhone") String customerPhone,
                               @Param("reservationTime") LocalDateTime reservationTime,
                               @Param("forfeitedAmount") Double forfeitedAmount,
                               @Param("reason") String reason);

    /**
     * 🔧 更新预约记录的 pre_order 标志（预点餐状态切换）
     * @param reservationId 预约号
     * @param preOrder 是否预点餐
     * @return 影响行数
     */
    int updatePreOrderFlag(@Param("reservationId") String reservationId,
                           @Param("preOrder") boolean preOrder);

    /**
     * 🔧 延迟预约专用更新方法（支持多字段原子更新）
     * @param reservationId 预约号
     * @param newTime 新的预约时间
     * @param newStatus 新状态
     * @param within15h 是否 1.5 小时内
     * @param newConfigDesc 新的餐桌配置描述
     * @param tableSelectionMode 餐桌选择模式（MANUAL/QUANTITY）
     * @param releaseTables 是否释放餐桌（true=清空 reserved_table_ids 和 table_config_desc）
     * @return 影响行数
     */
    int updateReservationForDelay(@Param("reservationId") String reservationId,
                                  @Param("newTime") LocalDateTime newTime,
                                  @Param("newStatus") String newStatus,
                                  @Param("within15h") Boolean within15h,
                                  @Param("newConfigDesc") String newConfigDesc,
                                  @Param("tableSelectionMode") String tableSelectionMode,
                                  @Param("releaseTables") boolean releaseTables);


    // ═══════════════════════════════════════════════════════════
    // 【辅助查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 🔧 根据 reservation_id 查询预付信息（仅返回 is_prepaid 和 prepaid_amount）
     * @param reservationId 预约号
     * @return Map{is_prepaid: Boolean, prepaid_amount: Double}，不存在返回空 Map
     */
    Map<String, Object> findPrepaidInfoByReservationId(@Param("reservationId") String reservationId);

    /**
     * 根据时间范围和状态列表查询预约记录（支持批量筛选）
     * @param startTime 查询开始时间（包含）
     * @param endTime   查询结束时间（包含）
     * @param statuses  预约状态列表（可选，传 null 或空列表则查询所有状态）
     * @return 符合条件的预约记录列表
     */
    List<TableReservation> findReservationsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<String> statuses);
}