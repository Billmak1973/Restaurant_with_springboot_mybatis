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
     * 🔧 聚餐桌专用：根据 order_item_id 删除订单项
     */
    int deleteOrderItemByOrderItemId(@Param("orderItemId") int orderItemId);

    /**
     * 🔧 聚餐桌专用：更新订单项数量 + 已上桌数量 + 状态（聚餐桌中的單桌專用）
     * @param orderItemId      订单项主键
     * @param newQuantity      新总数量
     * @param newServedQuantity 新已上桌数量
     * @param newStatus        新状态
     * @return 影响行数
     */
    int updateGroupedOrderItemQuantity(
            @Param("orderItemId") int orderItemId,
            @Param("newQuantity") int newQuantity,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus
    );

    /**
     * 🔧 聚餐桌共同菜品专用：智能更新 quantity + served_quantity + status + distribution
     */
    int updateSharedDishOrderItem(
            @Param("orderItemId") int orderItemId,
            @Param("newQuantity") int newQuantity,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus,
            @Param("newAssignedTableIds") String newAssignedTableIds,
            @Param("newDistribution") String newDistribution
    );

    /**
     * 🔧 更新订单项的 assigned_table_display_id 和 quantity_distribution
     * @param orderItemId 订单项主键
     * @param assignedTableDisplayId 分配的餐桌显示ID列表（如 "13,14,15"）
     * @param quantityDistribution quantity_distribution JSON字符串（如 {"13":2,"14":2,"15":2}）
     * @return 影响行数
     */
    int updateOrderItemDistribution(
            @Param("orderItemId") int orderItemId,
            @Param("assignedTableDisplayId") String assignedTableDisplayId,
            @Param("quantityDistribution") String quantityDistribution
    );

    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 🔧 聚餐桌延迟释放专用：清除订单明细中的分配信息
     * 将 assigned_table_display_id 和 quantity_distribution 设置为 NULL
     *
     * @param orderId 订单ID
     * @return 影响行数
     */
    int clearDistributionByOrderId(@Param("orderId") int orderId);

    /**
     * 🔧 聚餐桌专用：更新订单项数量
     */
    int updateOrderItemQuantity(@Param("orderItemId") int orderItemId,
                                @Param("newQuantity") int newQuantity);
}
