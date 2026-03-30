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
}
