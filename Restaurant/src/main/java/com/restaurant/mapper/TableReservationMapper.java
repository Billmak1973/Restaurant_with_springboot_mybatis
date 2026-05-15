package com.restaurant.mapper;

import com.restaurant.entity.TableReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TableReservationMapper {
    int insert(TableReservation reservation);
    TableReservation findById(@Param("id") String id);  // 🔧 int → String
    int updateStatus(@Param("id") String id, @Param("status") String status);  // 🔧 int → String

    // 🔧 新增方法
    TableReservation findByCode(@Param("code") String code);
    boolean existsByCode(@Param("code") String code);
    Integer getMaxSequenceToday(@Param("datePrefix") String datePrefix);

    int delete(@Param("id") String reservationId);


    String findActiveReservationIdByTableId(@Param("tableId") Integer tableId);

    List<Map<String, Object>> findQuantityModeReservationsForLog();

    List<Map<String, Object>> findPreOrderReservationsForMonitor();

    /**
     * 🔧 根据预约号查询完整预约详情
     */
    TableReservation findDetailById(@Param("id") String reservationId);

    TableReservation findReservationByTableId(@Param("tableId") String tableId);

    /**
     * 更新预约记录中的具体桌号 (reserved_table_ids)
     */
    int updateReservedTableIds(@Param("id") String reservationId, @Param("tableIds") String tableIds);

    /**
     * 🔧 根据预约号片段查询
     */
    List<Map<String, Object>> findReservationsByCodeFragment(@Param("codeFragment") String codeFragment);

    /**
     * 🔧 根据电话号码后4位查询
     */
    List<Map<String, Object>> findReservationsByPhoneLast4(@Param("phoneLast4") String phoneLast4);

    /**
     *  根据预约号片段查询完整预约详情（支持模糊查询）
     */
    List<TableReservation>findDetailByCodeFragment(@Param("codeFragment") String codeFragment);

    /**
     * 更新预约记录（只更新非空字段）
     */
    int updateReservation(TableReservation reservation);

    /**
     * 🔧 CANCEL 模式专用：根据预约号片段查询完整预约详情（支持所有状态）
     */
    List<TableReservation> findDetailByCodeFragmentForCancel(@Param("codeFragment") String codeFragment);

    /**
     * 记录取消预约时没收的定金
     */
    int insertForfeitedDeposit(@Param("reservationId") String reservationId,
                               @Param("customerName") String customerName,
                               @Param("customerPhone") String customerPhone,
                               @Param("reservationTime") LocalDateTime reservationTime,
                               @Param("forfeitedAmount") Double forfeitedAmount,
                               @Param("reason") String reason);

    /**
     * 🔧 更新预约记录的 pre_order 标志
     */
    int updatePreOrderFlag(@Param("reservationId") String reservationId,
                           @Param("preOrder") boolean preOrder);

    /**
     * 🔧 延迟预约专用更新方法（新增 newConfigDesc 和 tableSelectionMode 参数）
     * @param releaseTables 是否释放餐桌（清空 table_config_desc 和 reserved_table_ids）
     */
    int updateReservationForDelay(@Param("reservationId") String reservationId,
                                  @Param("newTime") LocalDateTime newTime,
                                  @Param("newStatus") String newStatus,
                                  @Param("within15h") Boolean within15h,
                                  @Param("newConfigDesc") String newConfigDesc,
                                  @Param("tableSelectionMode") String tableSelectionMode,  // 🔧 新增
                                  @Param("releaseTables") boolean releaseTables);

    /**
     * 🔧 根据 reservation_id 查询预付信息
     */
    Map<String, Object> findPrepaidInfoByReservationId(@Param("reservationId") String reservationId);


    /**
     * 根据时间范围和状态列表查询预约记录
    *
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
