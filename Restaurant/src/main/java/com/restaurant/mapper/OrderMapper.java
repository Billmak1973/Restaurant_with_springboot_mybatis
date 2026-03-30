// com.restaurant.mapper.OrderMapper.java
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
    // 【订单创建与查询】
    // ═══════════════════════════════════════════════════════════

    /**
     * 创建订单（传入完整的 Order 对象）
     * @param order 订单对象
     * @return 影响行数
     */
    int createOrder(Order order);



    /**
     * 根据餐桌ID查询活跃订单ID
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
     * 根据订单ID查询订单总金额
     * @param orderId 订单ID
     * @return 总金额，不存在返回 null
     */
    Double getOrderTotalAmount(@Param("orderId") int orderId);

    /**
     * 根据订单ID查询订单创建时间
     * @param orderId 订单ID
     * @return 创建时间，不存在返回 null
     */
    Timestamp getOrderCreateTime(@Param("orderId") int orderId);

    /**
     * 根据餐桌ID查询活跃订单头信息
     * @param tableId 餐桌ID
     * @return 订单头信息 Map，无记录返回 null
     */
    Map<String, Object> getActiveOrderHeaderByTableId(@Param("tableId") int tableId);

    /**
     * 根据订单ID查询订单明细列表
     * @param orderId 订单ID
     * @return 明细列表（Map格式）
     */
    List<Map<String, Object>> getOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 根据餐桌显示ID查询订单明细（带菜品信息）
     * @param displayId 餐桌显示编号（如 "7" 或 "7a"）
     * @return OrderItem 列表
     */
    List<OrderItem> findOrderItemsByTableDisplayId(@Param("displayId") String displayId);

    /**
     * 根据外卖订单号查询订单
     * @param orderNumber 外卖订单号（格式：P-20260305-001 / D-20260305-001）
     * @return Order 对象，不存在返回 null
     */
    Order findByOrderNumber(@Param("orderNumber") String orderNumber);

    // ═══════════════════════════════════════════════════════════
    // 【订单状态更新】
    // ═══════════════════════════════════════════════════════════

    /**
     * 结账订单
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
     * 检查餐桌是否有历史结账记录
     * @param tableId 餐桌ID
     * @return true=有记录，false=无记录
     */
    boolean isOrderPreviouslyCheckedOut(@Param("tableId") int tableId);

    /**
     * 获取餐桌最新订单状态（内存缓存用）
     * @param tableId 餐桌ID
     * @return Tables.OrderStatus 枚举值
     */
    Tables.OrderStatus getLatestOrderStatus(@Param("tableId") int tableId);

    // ═══════════════════════════════════════════════════════════
    // 【订单删除与迁移】
    // ═══════════════════════════════════════════════════════════

    /**
     * 删除订单（仅当订单明细为空时）
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
     * 检查餐桌是否有任何订单记录
     * @param tableId 餐桌ID
     * @return true=有记录，false=无记录
     */
    boolean hasAnyOrders(@Param("tableId") int tableId);

    // ═══════════════════════════════════════════════════════════
    // 【营收统计】
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新当日营收
     * @param amount 营收金额
     * @param revenueDate 营收日期
     * @return 影响行数
     */
    int updateDailyRevenue(@Param("amount") double amount, @Param("revenueDate") Date revenueDate);

    // ═══════════════════════════════════════════════════════════
    // 【季度销售报表】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取季度菜品销售报表
     * @param year 年份
     * @param quarter 季度（Q1/Q2/Q3/Q4）
     * @param category 分类前缀（A/B/C/D 或 "全部"）
     * @return 报表数据列表（Map格式）
     */
    List<Map<String, Object>> getQuarterlyDishSalesReport(
            @Param("year") int year,
            @Param("quarter") String quarter,
            @Param("category") String category);

    /**
     * 获取菜品销售可用的年份列表
     * @return 年份字符串列表
     */
    List<String> getAvailableYearsForDishSales();

    // ═══════════════════════════════════════════════════════════
    // 【季度销售记录 - 原子操作】
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询订单明细用于季度销售记录
     * @param orderId 订单ID
     * @return 明细列表（含 item_code, name, price_at_order, quantity）
     */
    List<Map<String, Object>> findOrderItemsForSalesRecord(@Param("orderId") int orderId);

    /**
     * 检查季度销售记录是否存在
     * @param itemCode 菜品编号
     * @param itemName 菜品名称
     * @param salePrice 销售单价
     * @param year 年份
     * @param quarter 季度
     * @return 销售记录ID，不存在返回 null
     */
    Integer checkQuarterlySalesExists(
            @Param("itemCode") String itemCode,
            @Param("itemName") String itemName,
            @Param("salePrice") double salePrice,
            @Param("year") int year,
            @Param("quarter") String quarter);

    /**
     * 更新季度销售数量
     * @param salesId 销售记录ID
     * @param quantity 增加的数量
     * @return 影响行数
     */
    int updateQuarterlySalesQuantity(@Param("salesId") int salesId, @Param("quantity") int quantity);

    /**
     * 插入季度销售记录
     * @param itemCode 菜品编号
     * @param itemName 菜品名称
     * @param salePrice 销售单价
     * @param quantity 销售数量
     * @param year 年份
     * @param quarter 季度
     * @return 影响行数
     */
    int insertQuarterlySalesRecord(
            @Param("itemCode") String itemCode,
            @Param("itemName") String itemName,
            @Param("salePrice") double salePrice,
            @Param("quantity") int quantity,
            @Param("year") int year,
            @Param("quarter") String quarter);

    /**
     * 批量记录季度销售（单方法完成查询+更新+插入，使用CTE）
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
    // 【订单列表查询 - 按类型】
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
    // 【外卖订单号生成】
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取下一个外卖订单序号（用于生成订单号）
     * @param prefix 前缀："P"=自取 / "D"=配送
     * @param dateStr 日期字符串：格式 "20260305"
     * @param deliveryMethod 配送方式："PICKUP" / "DELIVERY"
     * @return 下一个序号（从1开始）
     */
    Integer getNextOrderNumber(
            @Param("prefix") String prefix,
            @Param("dateStr") String dateStr,
            @Param("deliveryMethod") String deliveryMethod
    );

    // 新增：根据外卖订单号查询订单明细
    List<OrderItem> findOrderItemsByOrderNumber(@Param("orderNumber") String orderNumber);

    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 根据订单ID查询订单（完整信息）
     */
    Order findById(@Param("orderId") int orderId);

    /**
     * 更新订单的菜品总价、配送费和总金额
     */
    int updateOrderItemsTotalAndDeliveryFee(
            @Param("orderId") int orderId,
            @Param("itemsTotal") Double itemsTotal,
            @Param("deliveryFee") Double deliveryFee,
            @Param("totalAmount") Double totalAmount
    );


    /**
     * 根据订单号查询订单状态（外卖订单用）
     * @param orderNumber 订单号
     * @return OrderStatus 枚举值
     */
    Tables.OrderStatus getOrderStatusByOrderNumber(@Param("orderNumber") String orderNumber);



    /**
     * 记录撤销（支持菜品/整单）
     */
    int recordCancellation(
            @Param("cancellationType") String cancellationType,  // "ITEM" 或 "ORDER"
            @Param("orderId") Integer orderId,
            @Param("orderNumber") String orderNumber,
            @Param("itemCode") String itemCode,                   // 菜品撤销时填写
            @Param("cancelledQuantity") Integer cancelledQuantity, // 菜品撤销时填写
            @Param("beforeStatus") String beforeStatus,           // 菜品撤销时填写
            @Param("cancelledAmount") Double cancelledAmount,     // 整单撤销时填写
            @Param("reason") String reason
    );

    /**
     * 更新配送状态
     * @param orderId 订单ID
     * @param status 新状态（NOT_DELIVERED/DELIVERING/DELIVERED）
     * @return 影响行数
     */
    int updateDeliveryStatus(@Param("orderId") int orderId, @Param("status") String status);


    int updateDeliveryFee(@Param("orderId") int orderId, @Param("deliveryFee") Double deliveryFee);

    /**
     * 重单时更新订单：状态 + 金额 + 重单时间
     */
    int updateOrderForReorder(
            @Param("orderId") int orderId,
            @Param("status") String status,
            @Param("amount") double amount,
            @Param("newOrderTime") LocalDateTime newOrderTime  // 🔧 新增参数
    );

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
}
