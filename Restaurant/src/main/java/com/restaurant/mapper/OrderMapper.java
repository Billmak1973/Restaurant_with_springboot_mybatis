package com.restaurant.mapper;

import com.restaurant.entity.Order;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.Tables;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单数据访问接口（MyBatis Mapper）
 * 支持堂食/自取/配送三种订单类型
 */
@Mapper
public interface OrderMapper {

    // ═══════════════════════════════════════════════════════════
    // 【1. 订单创建与基础查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 创建订单（传入完整的 Order 对象）
     * @param order 订单对象
     * @return 影响行数
     */
    int createOrder(Order order);

    /**
     * 根据订单ID查询订单完整信息
     * @param orderId 订单主键ID
     * @return Order 对象，不存在返回 null
     */
    Order findById(@Param("orderId") int orderId);

    /**
     * 根据外卖订单号查询订单
     * @param orderNumber 外卖订单号（格式：P-20260305-001 / D-20260305-001）
     * @return Order 对象，不存在返回 null
     */
    Order findByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * 根据餐桌ID查询活跃订单ID（状态=ORDERED）
     * @param tableId 餐桌ID
     * @return 订单ID，无活跃订单返回 null
     */
    Integer findActiveOrderIdByTableId(@Param("tableId") int tableId);

    /**
     * 根据餐桌ID查询已结账订单ID（用于重单逻辑）
     * @param tableId 餐桌ID
     * @return 订单ID，无记录返回 null
     */
    Integer findCheckedOutOrderIdByTableId(@Param("tableId") int tableId);

    /**
     * 根据餐桌ID查询任意状态的订单ID
     * @param tableId 餐桌ID
     * @return 订单ID，不存在返回 null
     */
    Integer findOrderIdByTableId(@Param("tableId") int tableId);

    /**
     * 根据餐桌ID和订单状态查找订单
     * @param tableId 餐桌ID
     * @param status 订单状态
     * @return Order 对象，不存在返回 null
     */
    Order findOrderByTableIdAndStatus(@Param("tableId") int tableId, @Param("status") String status);

    /**
     * 根据餐桌ID和订单状态查找订单ID
     * @param tableId 餐桌ID
     * @param status 订单状态
     * @return 订单ID，不存在返回 null
     */
    Integer findOrderIdByTableIdAndStatus(
            @Param("tableId") int tableId,
            @Param("status") String status
    );

    // ═══════════════════════════════════════════════════════════
    // 【2. 预约订单相关查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 🔧 根据 reservation_id 查询预点餐订单（状态=NO_ORDER/ORDERED）
     * @param reservationId 预约号
     * @return Order 对象，不存在返回 null
     */
    Order findPreOrderByReservationId(@Param("reservationId") String reservationId);

    /**
     * 🔧 根据 reservation_id 查询活跃订单（预点餐/已下单）
     * @param reservationId 预约号
     * @return Order 对象，不存在返回 null
     */
    Order findActiveOrderByReservationId(@Param("reservationId") String reservationId);

    /**
     * 🔧 根据 reservation_id 查询订单 ID（预约订单专用）
     * @param reservationId 预约号
     * @return 订单 ID，不存在返回 null
     */
    Integer findOrderIdByReservationId(@Param("reservationId") String reservationId);

    /**
     * 🔧 根据 reservation_id 查询活跃订单 ID（预约订单专用）
     * @param reservationId 预约号
     * @return 订单 ID，不存在返回 null
     */
    Integer findActiveOrderIdByReservationId(@Param("reservationId") String reservationId);

    /**
     * 🔧 根据 reservation_id 查询订单明细（预约订单专用）
     * @param reservationId 预约号
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByReservationId(@Param("reservationId") String reservationId);


    // ═══════════════════════════════════════════════════════════
    // 【3. 订单明细查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据订单ID查询订单明细列表
     * @param orderId 订单ID
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 根据外卖订单号查询订单明细
     * @param orderNumber 外卖订单号
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * 根据餐桌显示ID查询订单明细（带菜品信息，支持聚餐桌）
     * @param displayId 餐桌显示编号（如 "7" 或 "7a"）
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByTableDisplayId(@Param("displayId") String displayId);

    /**
     * 🔧 根据多个餐桌显示ID查询订单明细（支持聚餐桌）
     * @param tableDisplayIds 餐桌显示ID列表
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByTableDisplayIds(@Param("tableDisplayIds") List<String> tableDisplayIds);


    // ═══════════════════════════════════════════════════════════
    // 【4. 订单状态更新】
    // ═══════════════════════════════════════════════════════════

    /**
     * 结账订单（更新状态为 CHECKED_OUT）
     * @param orderId 订单ID
     * @return 影响行数
     */
    int checkoutOrder(@Param("orderId") int orderId);

    /**
     * 更新订单状态和金额
     * @param orderId 订单ID
     * @param status 新状态（如 "ORDERED" / "CHECKED_OUT"）
     * @param amount 新总金额
     * @return 影响行数
     */
    int updateOrderStatusAndAmount(
            @Param("orderId") int orderId,
            @Param("status") String status,
            @Param("amount") double amount);

    /**
     * 同时更新订单状态+items_total+total_amount
     * @param orderId 订单ID
     * @param status 新状态
     * @param itemsTotal 菜品总金额
     * @param totalAmount 最终总金额
     * @return 影响行数
     */
    int updateOrderStatusAndTotals(
            @Param("orderId") int orderId,
            @Param("status") String status,
            @Param("itemsTotal") Double itemsTotal,
            @Param("totalAmount") Double totalAmount
    );

    /**
     * 更新订单的菜品总金额和最终总金额（预约订单专用 - 无配送费）
     * @param orderId 订单ID
     * @param itemsTotal 菜品总金额
     * @param totalAmount 最终总金额
     * @return 影响行数
     */
    int updateOrderTotals(
            @Param("orderId") int orderId,
            @Param("itemsTotal") Double itemsTotal,
            @Param("totalAmount") Double totalAmount
    );

    /**
     * 更新配送费并重新计算总金额
     * @param orderId 订单ID
     * @param deliveryFee 新配送费
     * @return 影响行数
     */
    int updateDeliveryFee(@Param("orderId") int orderId, @Param("deliveryFee") Double deliveryFee);

    /**
     * 更新配送状态
     * @param orderId 订单ID
     * @param status 新状态（NOT_DELIVERED/DELIVERING/DELIVERED）
     * @return 影响行数
     */
    int updateDeliveryStatus(@Param("orderId") int orderId, @Param("status") String status);

    /**
     * 重单时更新订单：状态 + 金额 + 重单时间
     * @param orderId 订单ID
     * @param status 新状态
     * @param amount 新金额
     * @param newOrderTime 重单时间
     * @return 影响行数
     */
    int updateOrderForReorder(
            @Param("orderId") int orderId,
            @Param("status") String status,
            @Param("amount") double amount,
            @Param("newOrderTime") LocalDateTime newOrderTime
    );

    /**
     * 乐观锁更新订单状态（仅当当前状态=oldStatus时更新）
     * @param orderId 订单ID
     * @param newStatus 新状态
     * @param oldStatus 期望的旧状态
     * @return 影响行数
     */
    int updateOrderStatus(
            @Param("orderId") Integer orderId,
            @Param("status") String newStatus,
            @Param("oldStatus") String oldStatus
    );

    /**
     * 乐观锁仅更新订单状态（不更新金额，用于预点餐状态升级）
     * @param orderId 订单ID
     * @param newStatus 新状态
     * @param oldStatus 期望的旧状态
     * @return 影响行数
     */
    int updateOrderStatusOnly(
            @Param("orderId") Integer orderId,
            @Param("status") String newStatus,
            @Param("oldStatus") String oldStatus
    );


    // ═══════════════════════════════════════════════════════════
    // 【5. 预约订单关联更新】
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据预约 ID 更新关联的预点餐订单的餐桌 ID
     * @param reservationId 预约 ID
     * @param tableId 餐桌 ID (通常为主桌 ID)
     * @return 影响行数
     */
    int updateTableIdByReservationId(@Param("reservationId") String reservationId, @Param("tableId") int tableId);

    /**
     * 根据预约 ID 更新订单类型（仅更新特定原类型的订单）
     * @param reservationId 预约 ID
     * @param originalType 原订单类型（如 "RESERVATION"）
     * @param newType 新订单类型（如 "DINE_IN"）
     * @return 影响行数
     */
    int updateOrderTypeByReservationId(
            @Param("reservationId") String reservationId,
            @Param("originalType") String originalType,
            @Param("newType") String newType
    );

    /**
     * 🔧 根据预约号更新预付信息
     * @param reservationId 预约号
     * @param isPrepaid 是否预付
     * @param prepaidAmount 预付金额
     * @return 影响行数
     */
    int updatePrepaidInfoByReservationId(
            @Param("reservationId") String reservationId,
            @Param("isPrepaid") Boolean isPrepaid,
            @Param("prepaidAmount") Double prepaidAmount
    );

    /**
     * 根据预约ID清空订单的餐桌ID（释放餐桌时使用）
     * @param reservationId 预约ID
     * @return 影响行数
     */
    int clearTableIdByReservationId(@Param("reservationId") String reservationId);


    // ═══════════════════════════════════════════════════════════
    // 【6. 订单删除与迁移】
    // ═══════════════════════════════════════════════════════════

    /**
     * 删除订单（仅当订单明细为空时使用）
     * @param orderId 订单ID
     * @return 影响行数
     */
    int deleteOrder(@Param("orderId") int orderId);

    /**
     * 删除指定餐桌的所有订单
     * @param tableId 餐桌ID
     * @return 影响行数
     */
    int deleteTableOrdersByTableId(@Param("tableId") int tableId);

    /**
     * 将指定餐桌的所有活跃订单迁移到新餐桌
     * @param fromTableId 原餐桌ID
     * @param toTableId 目标餐桌ID
     * @return 影响行数
     */
    int migrateOrdersToTable(
            @Param("fromTableId") int fromTableId,
            @Param("toTableId") int toTableId
    );

    /**
     * 检查餐桌是否有任何订单记录（任意状态）
     * @param tableId 餐桌ID
     * @return true=有记录，false=无记录
     */
    boolean hasAnyOrders(@Param("tableId") int tableId);

    /**
     * 检查餐桌是否有历史结账记录
     * @param tableId 餐桌ID
     * @return true=有记录，false=无记录
     */
    boolean isOrderPreviouslyCheckedOut(@Param("tableId") int tableId);


    // ═══════════════════════════════════════════════════════════
    // 【7. 订单状态查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取餐桌最新订单状态（内存缓存用）
     * @param tableId 餐桌ID
     * @return Tables.OrderStatus 枚举值
     */
    Tables.OrderStatus getLatestOrderStatus(@Param("tableId") int tableId);

    /**
     * 根据订单号查询订单状态（外卖订单用）
     * @param orderNumber 订单号
     * @return Tables.OrderStatus 枚举值
     */
    Tables.OrderStatus getOrderStatusByOrderNumber(@Param("orderNumber") String orderNumber);


    // ═══════════════════════════════════════════════════════════
    // 【8. 订单列表查询 - 按类型】
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询堂食订单列表（活跃订单）
     * @return 订单列表（含餐桌显示ID、状态、时间等）
     */
    List<Map<String, Object>> findDineInOrders();

    /**
     * 查询自取订单列表（活跃订单）
     * @return 订单列表（含订单号、状态、时间等）
     */
    List<Map<String, Object>> findPickupOrders();

    /**
     * 查询配送订单列表（活跃订单）
     * @return 订单列表（含订单号、地址、状态、时间等）
     */
    List<Map<String, Object>> findDeliveryOrders();


    // ═══════════════════════════════════════════════════════════
    // 【9. 订单号生成】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取下一个订单序号（用于生成订单号）
     * @param prefix 前缀："T"=堂食 / "P"=自取 / "D"=配送
     * @param dateStr 日期字符串：格式 "20260305"
     * @param deliveryMethod 配送方式："PICKUP" / "DELIVERY" / null
     * @param orderType 订单类型："DINE_IN" / "TAKEOUT" / "RESERVATION"
     * @return 下一个序号（从1开始）
     */
    Integer getNextOrderNumber(
            @Param("prefix") String prefix,
            @Param("dateStr") String dateStr,
            @Param("deliveryMethod") String deliveryMethod,
            @Param("orderType") String orderType
    );


    // ═══════════════════════════════════════════════════════════
    // 【10. 营收统计】
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新当日营收
     * @param amount 营收金额
     * @param revenueDate 营收日期
     * @return 影响行数
     */
    int updateDailyRevenue(@Param("amount") double amount, @Param("revenueDate") Date revenueDate);


    // ═══════════════════════════════════════════════════════════
    // 【11. 季度销售报表查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取季度菜品销售报表
     * @param year 年份
     * @param quarter 季度（Q1/Q2/Q3/Q4）
     * @param category 分类前缀（A/B/C/D 或 "全部"）
     * @return 报表数据列表（Map格式：item_code/item_name/total_quantity/total_revenue等）
     */
    List<Map<String, Object>> getQuarterlyDishSalesReport(
            @Param("year") int year,
            @Param("quarter") String quarter,
            @Param("category") String category);

    /**
     * 获取菜品销售数据中有记录的年份列表
     * @return 按降序排列的年份字符串列表（例如["2026", "2025"]）
     */
    List<String> getAvailableYearsForDishSales();


    // ═══════════════════════════════════════════════════════════
    // 【12. 季度销售记录 - 原子操作（批量记录）】
    // ═══════════════════════════════════════════════════════════

    /**
     * 批量记录季度销售（单方法完成查询+更新+插入，使用临时表+CTE）
     * @param orderId 订单ID
     * @param year 年份
     * @param quarter 季度（Q1/Q2/Q3/Q4）
     * @return 影响的总行数
     */
    int recordQuarterlySales(
            @Param("orderId") int orderId,
            @Param("year") int year,
            @Param("quarter") String quarter);


    // ═══════════════════════════════════════════════════════════
    // 【13. 订单撤销审计】
    // ═══════════════════════════════════════════════════════════

    /**
     * 记录撤销审计日志（支持菜品撤销/整单撤销）
     * @param cancellationType 撤销类型："ITEM"=菜品 / "ORDER"=整单
     * @param orderId 订单ID（整单撤销必填）
     * @param orderNumber 订单号（整单撤销必填）
     * @param itemCode 菜品编号（菜品撤销必填）
     * @param cancelledQuantity 撤销数量（菜品撤销必填）
     * @param beforeStatus 撤销前状态（菜品撤销必填）
     * @param cancelledAmount 撤销金额（整单=订单总额，菜品=菜品小计）
     * @param reason 撤销原因
     * @return 影响行数
     */
    int recordCancellation(
            @Param("cancellationType") String cancellationType,
            @Param("orderId") Integer orderId,
            @Param("orderNumber") String orderNumber,
            @Param("itemCode") String itemCode,
            @Param("cancelledQuantity") Integer cancelledQuantity,
            @Param("beforeStatus") String beforeStatus,
            @Param("cancelledAmount") Double cancelledAmount,
            @Param("reason") String reason
    );

}