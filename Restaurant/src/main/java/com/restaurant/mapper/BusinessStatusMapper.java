package com.restaurant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessStatusMapper {

    // ===== 基础 CRUD =====

    int insertTodayStatus(@Param("date") LocalDate date);

    Integer getNextCallNumber(@Param("date") LocalDate date);

    int countByDate(@Param("date") LocalDate date);

    int incrementNextCallNumber(@Param("date") LocalDate date);

    int incrementDailyTotalCustomers(
            @Param("customerCount") int customerCount,
            @Param("date") LocalDate date);

    Boolean loadIsOpenStatus(@Param("date") LocalDate date);

    int updateBusinessStatus(
            @Param("date") LocalDate date,
            @Param("isOpen") boolean isOpen,
            @Param("nextCallNumber") int nextCallNumber);

    // ===== 报表查询 =====

    List<Map<String, Object>> getDailyReport(@Param("date") String date);

    List<Map<String, Object>> getDateRangeReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 递增当日外卖订单计数
     * @param date 营业日期
     * @return 影响行数
     */
    int incrementDailyTakeoutCount(@Param("date") java.sql.Date date);


}
