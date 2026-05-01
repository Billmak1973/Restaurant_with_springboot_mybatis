package com.restaurant.mapper;

import com.restaurant.entity.OrderItem;
import com.restaurant.entity.OrderItemServingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.MapKey;

@Mapper
public interface OrderItemMapper {

    // ===== 批量插入 =====

    /**
     * 批量添加订单明细
     *
     * @param orderId 订单ID
     * @param items   订单项列表
     * @return 影响行数
     */
    int addOrderItems(@Param("orderId") int orderId, @Param("items") List<OrderItem> items);

    // ===== 合并订单项 =====

    /**
     * 批量更新现有订单项数量
     *
     * @param orderId 订单ID
     * @param items   待更新的 OrderItem 列表（需含 itemCode, quantity, priceAtOrder）
     * @return 影响行数
     */
    int updateExistingOrderItems(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    /**
     * 批量插入新订单项（通过 item_code 关联 menu_items）
     *
     * @param orderId 订单ID
     * @param items   待插入的 OrderItem 列表（需含 itemCode, quantity, priceAtOrder）
     * @return 影响行数
     */
    int insertNewOrderItems(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    // ===== 订单金额计算 =====

    /**
     * 重新计算订单总金额
     *
     * @param orderId 订单ID
     * @return 新总金额
     */
    Double recalculateOrderTotal(@Param("orderId") int orderId);

    /**
     * 更新订单总金额
     *
     * @param orderId  订单ID
     * @param newTotal 新总金额
     * @return 影响行数
     */
    int updateOrderTotalAmount(@Param("orderId") int orderId, @Param("newTotal") double newTotal);

    // ===== 上菜状态查询与更新 =====

    /**
     * 获取订单项上菜状态
     *
     * @param orderId 订单ID
     * @param itemId  菜品ID
     * @return 状态对象，不存在返回 null
     */
    OrderItemServingStatus getServingStatus(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId);


    /**
     * 获取订单项上菜状态
     *
     * @param orderId 订单ID
     * @param itemId  菜品ID
     * @return 状态对象，不存在返回 null
     */
    OrderItemServingStatus fetchServingStatusByOrderItem(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId);
    /**
     * 🔧 通过 order_item_id 精确更新 prepared_quantity + status
     */
    int updatePreparedQuantityById(
            @Param("orderItemId") int orderItemId,
            @Param("preparedQuantity") int preparedQuantity,
            @Param("newStatus") String newStatus
    );

    /**
     * 增加已上菜数量
     *
     * @param orderId           订单ID
     * @param itemId            菜品ID
     * @param newServedQuantity 新已上菜数量
     * @param newStatus         新状态字符串
     * @return 影响行数
     */
    int incrementServedQuantity(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus);

    /**
     * 标记订单所有项为已上菜
     *
     * @param orderId 订单ID
     * @return 影响行数
     */
    int markAllItemsAsServed(@Param("orderId") int orderId);

    /**
     * 检查订单是否有未上菜项
     *
     * @param orderId 订单ID
     * @return true=有未上菜项
     */
    boolean hasUnservedItems(@Param("orderId") int orderId);

    /**
     * 检查订单是否有已上菜项
     *
     * @param orderId 订单ID
     * @return true=有已上菜项
     */
    boolean hasServedItems(@Param("orderId") int orderId);

    // ===== 撤销订单项 =====

    /**
     * 完全删除订单项
     *
     * @param orderId 订单ID
     * @param itemId  菜品ID
     * @return 影响行数
     */
    int deleteOrderItem(@Param("orderId") int orderId, @Param("itemId") int itemId);

    /**
     * 部分撤销：更新订单项数量和状态
     *
     * @param orderId      订单ID
     * @param itemId       菜品ID
     * @param newQty       新数量
     * @param newServedQty 新已上菜数量
     * @param newStatus    新状态字符串
     * @return 影响行数
     */
    int updateOrderItemAfterCancel(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("newQty") int newQty,
            @Param("newServedQty") int newServedQty,
            @Param("newStatus") String newStatus);

    /**
     * 记录撤销审计日志
     *
     * @param itemCode          菜品编号
     * @param cancelledQuantity 撤销数量
     * @param reason            撤销原因
     * @param beforeStatus      撤销前状态
     * @return 影响行数
     */
    int recordCancellation(
            @Param("itemCode") String itemCode,
            @Param("cancelledQuantity") int cancelledQuantity,
            @Param("reason") String reason,
            @Param("beforeStatus") String beforeStatus);

    /**
     * 根据 itemId 获取 item_code
     *
     * @param itemId 菜品ID
     * @return 菜品编号
     */
    String getItemCodeByItemId(@Param("itemId") int itemId);

    /**
     * 获取订单项当前状态
     *
     * @param orderId 订单ID
     * @param itemId  菜品ID
     * @return 状态字符串
     */
    String getItemStatus(@Param("orderId") int orderId, @Param("itemId") int itemId);

    // ===== 批量删除与检查 =====

    /**
     * 删除订单所有明细
     *
     * @param orderId 订单ID
     * @return 影响行数
     */
    int deleteOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 检查订单是否还有剩余明细项
     *
     * @param orderId 订单ID
     * @return true=还有项
     */
    boolean hasRemainingItems(@Param("orderId") int orderId);

    /**
     * 获取订单现有项（用于合并逻辑）
     *
     * @param orderId 订单ID
     * @return List of Map: [{item_code: "A1", quantity: 2}, ...]
     */
    // OrderItemMapper.java
    List<Map<String, Object>> getExistingItemQuantitiesRaw(
            @Param("orderId") int orderId,
            @Param("status") String status);  // 🔧 新增参数

    // ===== OrderItemMapper.java 添加方法 =====

    int updateServedQuantityAndStatus(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("servedQuantity") int servedQuantity,
            @Param("newStatus") String newStatus
    );

    /**
     * 更新现有订单项数量（预约订单专用 - 使用 PREPARING/PREPARED 状态）
     */
    int updateExistingOrderItemsForReservation(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    /**
     * 批量插入新订单项（支持指定初始状态）
     */
    int insertNewOrderItemsWithStatus(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items,
            @Param("initialStatus") String initialStatus);

    int updateServedById(@Param("orderItemId") int orderItemId,
                         @Param("served") int served,
                         @Param("status") String status);

    /**
     * 🔧 按 assigned_table_display_id + itemId 精确查找订单项
     * @param itemId 菜品ID
     * @param assignedTableDisplayId 分配的餐桌显示ID（如 "13"）
     * @return OrderItem，不存在返回 null
     */
    OrderItem findOrderItemByAssignedTableAndItemId(
            @Param("itemId") int itemId,
            @Param("assignedTableDisplayId") String assignedTableDisplayId
    );

    int updatePreparedQuantity(
            @Param("orderItemId") int orderItemId, // 🔧 新增
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("preparedQuantity") int preparedQuantity,
            @Param("newStatus") String newStatus,
            @Param("assignedTableDisplayId") String assignedTableDisplayId
    );

    OrderItem findSharedOrderItemByFuzzyTableId(
            @Param("itemId") int itemId,
            @Param("tableDisplayId") String tableDisplayId
    );


    /**
     * 🔧 聚餐桌专用：同时更新 served_quantity + status + served_table_display_id
     */
    int updateServedWithTableInfo(
            @Param("orderItemId") int orderItemId,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus,
            @Param("newServedTableIds") String newServedTableIds
    );

    /**
     * 🔧 聚餐桌专用：更新现有订单项数量（只更新 UNSERVED 状态）
     * 用于聚餐桌一键点餐时，避免更新已上桌的菜品
     */
    int updateExistingOrderItemsForGroupedTable(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items
    );

    OrderItem selectByPrimaryKey(@Param("orderItemId") int orderItemId);


    /**
     * 🔧 聚餐桌专用：根据 order_item_id 撤销菜品（只针对 quantity_distribution 为 null 的场景） 其餘情況不能引用
     *
     * 业务规则：
     * 1. quantity 始终 -1
     * 2. 如果 served_table_display_id 包含当前餐桌号：served_quantity 也 -1
     * 3. 根据新的 served_quantity 和 quantity 重新计算 status
     *
     * @param orderItemId 订单项主键
     * @param tableDisplayId 当前操作的餐桌显示编号（用于判断 served_table_display_id）
     * @param cancellationReason 撤销原因（用于日志，SQL 中暂不使用）
     * @return 影响行数
     */
    int cancelGroupedTableOrderItemByOrderItemId(
            @Param("orderItemId") int orderItemId,
            @Param("tableDisplayId") String tableDisplayId,
            @Param("cancellationReason") String cancellationReason);

    /**
     * 🔧 聚餐桌专用：更新有 quantity_distribution 的订单项
     *
     * ⚠️ 调用顺序要求（由 Service 层保证）：
     * 1. 先计算 newQuantity（总数量 - 撤销数量）
     * 2. 再计算 newDistribution（JSON，只包含 quantity>0 的桌号）
     * 3. 最后计算 newAssignedTableIds（由 distribution.keySet() 排序后生成）
     *
     * @param orderItemId         订单项主键
     * @param newQuantity         新的总数量
     * @param newStatus           新的状态
     * @param newDistribution     新的 distribution JSON（如 {"14":2,"15":2}）
     * @param newAssignedTableIds 新的 assigned_table_display_id（如 "14,15"）
     */
    int updateGroupedOrderItemWithDistribution(
            @Param("orderItemId") int orderItemId,
            @Param("newQuantity") int newQuantity,
            @Param("newStatus") String newStatus,
            @Param("newDistribution") String newDistribution,
            @Param("newAssignedTableIds") String newAssignedTableIds
    );

    /**
     * 🔧 聚餐桌专用：根据 order_item_id 删除订单项
     */
    int deleteOrderItemByOrderItemId(@Param("orderItemId") int orderItemId);

    /**
     * 🔧 聚餐桌精確上菜更新（嚴格匹配4個字段，防止誤改）
     */
    int updateServedByExactMatch(
            @Param("orderItemId") int orderItemId,
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("assignedTableDisplayId") String assignedTableDisplayId,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus
    );

}
