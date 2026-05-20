package com.restaurant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
//第三章：MyBatis 持久層架構與動態 SQL
//3.1. @Mapper 接口定義與 Spring 自動掃描
//技術說明：使用 @Mapper 標註數據訪問接口，並通過 @MapperScan 統一註冊到 Spring 容器，實現代理對象的自動生成與依賴注入。
//@MapperScan 避免了在每個接口上重複添加 @Mapper，提升了代碼整潔度。Spring Boot 啟動時會自動為這些接口創建動態代理，並將它們註冊為 Spring Bean，方便 Service 層通過構造函數注入
@Mapper
public interface BusinessStatusMapper {

    // ═══════════════════════════════════════════════════════════
    // 【分类 1】基础状态管理 - 营业状态的增删改查
    // ═══════════════════════════════════════════════════════════

    /**
     * 插入当日营业状态记录（若已存在则忽略）
     * @param date 营业日期
     * @return 影响行数（1=插入成功，0=已存在）
     */
    int insertTodayStatus(@Param("date") LocalDate date);

    /**
     * 查询当日下一个可用叫号
     * @param date 营业日期
     * @return 下一个叫号，无记录返回 null
     */
    Integer getNextCallNumber(@Param("date") LocalDate date);

    /**
     * 递增当日叫号（+1）
     * @param date 营业日期
     * @return 影响行数
     */
    int incrementNextCallNumber(@Param("date") LocalDate date);

    /**
     * 查询当日营业状态（是否营业中）
     * @param date 营业日期
     * @return true=营业中，false=已打烊，null=无记录
     */
    Boolean loadIsOpenStatus(@Param("date") LocalDate date);

    /**
     * 更新营业状态（支持首次插入）
     * @param date 营业日期
     * @param isOpen 是否营业
     * @param nextCallNumber 下一个叫号
     * @return 影响行数
     */
    int updateBusinessStatus(
            @Param("date") LocalDate date,
            @Param("isOpen") boolean isOpen,
            @Param("nextCallNumber") int nextCallNumber);


    // ═══════════════════════════════════════════════════════════
    // 【分类 2】数据统计更新 - 累加各类业务指标
    // ═══════════════════════════════════════════════════════════

    /**
     * 累加当日顾客总数
     * @param customerCount 本次新增顾客数
     * @param date 营业日期
     * @return 影响行数
     */
    int incrementDailyTotalCustomers(
            @Param("customerCount") int customerCount,
            @Param("date") LocalDate date);

    /**
     * 累加当日外卖订单计数
     * @param date 营业日期
     * @return 影响行数
     */
    int incrementDailyTakeoutCount(@Param("date") java.sql.Date date);

    /**
     * 累加当日取消预约没收的定金总额
     * @param date 营业日期
     * @param amount 没收金额
     * @return 影响行数
     */
    int incrementDailyCancelledPrepaidAmount(
            @Param("date") java.sql.Date date,
            @Param("amount") Double amount);


    // ═══════════════════════════════════════════════════════════
    // 【分类 3】报表查询 - 营业数据统计
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询单日报表（营业额/顾客数/订单数）
     * @param date 日期字符串 (yyyy-MM-dd)
     * @return 报表数据列表
     */
    List<Map<String, Object>> getDailyReport(@Param("date") String date);

    /**
     * 查询日期范围报表（支持多日统计）
     * @param startDate 开始日期 (yyyy-MM-dd)
     * @param endDate 结束日期 (yyyy-MM-dd)
     * @return 报表数据列表（按日期排序）
     */
    List<Map<String, Object>> getDateRangeReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);


    // ═══════════════════════════════════════════════════════════
    // 【分类 4】定金查询 - 没收定金记录
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询指定日期范围内的没收定金记录
     * @param startDate 开始日期 (yyyy-MM-dd)
     * @param endDate 结束日期 (yyyy-MM-dd)
     * @return 记录列表（含预约号/客户信息/金额/原因等）
     */
    List<Map<String, Object>> selectForfeitedDeposits(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}