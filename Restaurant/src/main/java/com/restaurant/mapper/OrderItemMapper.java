package com.restaurant.mapper;

import com.restaurant.entity.OrderItem;
import com.restaurant.entity.OrderItemServingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper {

    // ═══════════════════════════════════════════════════════════
    // 【模块一】批量插入操作
    // ═══════════════════════════════════════════════════════════

    /**
     * 批量添加订单明细到数据库
     *
     * @param orderId 订单主键ID
     * @param items   待插入的订单项列表（需包含 itemId/quantity/priceAtOrder 等字段）
     * @return 影响行数（成功插入的记录数）
     */
    int addOrderItems(@Param("orderId") int orderId, @Param("items") List<OrderItem> items);


    // ═══════════════════════════════════════════════════════════
    // 【模块二】合并订单项操作（点餐时合并相同菜品）
    // ═══════════════════════════════════════════════════════════

    /**
     * 批量更新现有订单项数量（用于合并相同菜品）
     *
     * @param orderId 订单主键ID
     * @param items   待更新的订单项列表（需包含 itemCode/quantity/priceAtOrder）
     * @return 影响行数（成功更新的记录数）
     * @note 仅更新已存在的订单项，数量累加，状态根据 served_quantity 自动计算
     */
    int updateExistingOrderItems(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    /**
     * 批量插入新订单项（通过 item_code 关联 menu_items 表）
     *
     * @param orderId 订单主键ID
     * @param items   待插入的订单项列表（需包含 itemCode/quantity/priceAtOrder）
     * @return 影响行数（成功插入的记录数）
     * @note 用于合并逻辑中新增的菜品，初始状态为 UNSERVED
     */
    int insertNewOrderItems(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    /**
     * 批量插入新订单项（支持指定初始状态）
     *
     * @param orderId        订单主键ID
     * @param items          待插入的订单项列表
     * @param initialStatus  初始状态（如 "UNSERVED"/"PREPARING"）
     * @return 影响行数
     * @note 预约订单专用，可指定菜品初始准备状态
     */
    int insertNewOrderItemsWithStatus(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items,
            @Param("initialStatus") String initialStatus);

    /**
     * 批量更新现有订单项数量（预约订单专用 - 使用 PREPARING/PREPARED 状态）
     *
     * @param orderId 订单主键ID
     * @param items   待更新的订单项列表
     * @return 影响行数
     * @note 预约订单合并时，状态逻辑与普通订单不同（使用准备状态而非上桌状态）
     */
    int updateExistingOrderItemsForReservation(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);

    /**
     * 🔧 聚餐桌专用：更新现有订单项数量（只更新 UNSERVED 状态）
     *
     * @param orderId 订单主键ID
     * @param items   待更新的订单项列表
     * @return 影响行数
     * @note 用于聚餐桌一键点餐时，避免更新已上桌的菜品，保持已上桌状态不变
     */
    int updateExistingOrderItemsForGroupedTable(
            @Param("orderId") int orderId,
            @Param("items") List<OrderItem> items);


    // ═══════════════════════════════════════════════════════════
    // 【模块三】订单金额计算
    // ═══════════════════════════════════════════════════════════

    /**
     * 重新计算订单总金额（菜品总价，不含配送费）
     *
     * @param orderId 订单主键ID
     * @return 新总金额（quantity × price_at_order 的累加和），不存在返回 null
     */
    Double recalculateOrderTotal(@Param("orderId") int orderId);

    /**
     * 更新订单总金额到主表
     *
     * @param orderId  订单主键ID
     * @param newTotal 新总金额
     * @return 影响行数
     */
    int updateOrderTotalAmount(@Param("orderId") int orderId, @Param("newTotal") double newTotal);


    // ═══════════════════════════════════════════════════════════
    // 【模块四】上菜状态查询与更新
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取订单项上菜状态（数量统计用）
     *
     * @param orderId 订单主键ID
     * @param itemId  菜品主键ID
     * @return OrderItemServingStatus 对象（含 quantity/served_quantity），不存在返回 null
     */
    OrderItemServingStatus getServingStatus(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId);

    /**
     * 获取订单项上菜状态（别名方法，功能同 getServingStatus）
     *
     * @param orderId 订单主键ID
     * @param itemId  菜品主键ID
     * @return OrderItemServingStatus 对象，不存在返回 null
     */
    OrderItemServingStatus fetchServingStatusByOrderItem(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId);

    /**
     * 🔧 通过 order_item_id 精确更新 prepared_quantity + status
     *
     * @param orderItemId      订单项主键ID（精确匹配）
     * @param preparedQuantity 新已准备数量
     * @param newStatus        新状态字符串（PREPARING/PREPARED/UNSERVED）
     * @return 影响行数
     * @note 预约订单厨房进度更新专用，避免 itemId 重复导致的误更新
     */
    int updatePreparedQuantityById(
            @Param("orderItemId") int orderItemId,
            @Param("preparedQuantity") int preparedQuantity,
            @Param("newStatus") String newStatus
    );

    /**
     * 增加已上菜数量并自动更新状态
     *
     * @param orderId           订单主键ID
     * @param itemId            菜品主键ID
     * @param newServedQuantity 新已上菜数量（绝对值，非增量）
     * @param newStatus         新状态字符串（UNSERVED/PARTIALLY_SERVED/SERVED）
     * @return 影响行数
     * @note 状态自动计算规则：0=UNSERVED, <total=PARTIALLY_SERVED, =total=SERVED
     */
    int incrementServedQuantity(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus);

    /**
     * 标记订单所有项为已上菜（批量操作）
     *
     * @param orderId 订单主键ID
     * @return 影响行数（成功更新的记录数）
     * @note 将 served_quantity 设为 quantity，status 设为 SERVED
     */
    int markAllItemsAsServed(@Param("orderId") int orderId);

    /**
     * 检查订单是否有未上菜项
     *
     * @param orderId 订单主键ID
     * @return true=有未上菜项（served_quantity < quantity 或状态为准备中），false=全部已上
     */
    boolean hasUnservedItems(@Param("orderId") int orderId);

    /**
     * 检查订单是否有已上菜项
     *
     * @param orderId 订单主键ID
     * @return true=有已上菜项（状态为 PARTIALLY_SERVED 或 SERVED），false=全部未上
     */
    boolean hasServedItems(@Param("orderId") int orderId);

    /**
     * 🔧 按 order_item_id 精确更新已上菜数量 + 状态
     *
     * @param orderItemId 订单项主键ID
     * @param served      新已上菜数量
     * @param status      新状态字符串
     * @return 影响行数
     * @note 聚餐桌精确上菜专用，避免 itemId 重复导致的批量更新
     */
    int updateServedById(@Param("orderItemId") int orderItemId,
                         @Param("served") int served,
                         @Param("status") String status);

    /**
     * 🔧 按 assigned_table_display_id + itemId 精确查找订单项
     *
     * @param itemId                 菜品主键ID
     * @param assignedTableDisplayId 分配的餐桌显示ID（如 "13" 或 "13,14,15"）
     * @return OrderItem 对象，不存在返回 null
     * @note 聚餐桌单桌分配菜品查询专用，精确匹配分配桌号
     */
    OrderItem findOrderItemByAssignedTableAndItemId(
            @Param("itemId") int itemId,
            @Param("assignedTableDisplayId") String assignedTableDisplayId
    );

    /**
     * 🔧 聚餐桌共享菜品模糊匹配查询
     *
     * @param itemId         菜品主键ID
     * @param tableDisplayId 当前操作的餐桌显示ID
     * @return OrderItem 对象，不存在返回 null
     * @note 使用 FIND_IN_SET 匹配 assigned_table_display_id 中包含当前桌号的记录
     */
    OrderItem findSharedOrderItemByFuzzyTableId(
            @Param("itemId") int itemId,
            @Param("tableDisplayId") String tableDisplayId
    );

    /**
     * 🔧 聚餐桌专用：同时更新 served_quantity + status + served_table_display_id
     *
     * @param orderItemId       订单项主键ID
     * @param newServedQuantity 新已上菜数量
     * @param newStatus         新状态字符串
     * @param newServedTableIds 新实际上菜桌号列表（如 "16,17"）
     * @return 影响行数
     * @note 聚餐桌一键上桌时，记录哪些桌号已上菜
     */
    int updateServedWithTableInfo(
            @Param("orderItemId") int orderItemId,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus,
            @Param("newServedTableIds") String newServedTableIds
    );


    // ═══════════════════════════════════════════════════════════
    // 【模块五】撤销订单项操作
    // ═══════════════════════════════════════════════════════════

    /**
     * 完全删除订单项（数量归零时使用）
     *
     * @param orderId 订单主键ID
     * @param itemId  菜品主键ID
     * @return 影响行数（成功删除的记录数）
     */
    int deleteOrderItem(@Param("orderId") int orderId, @Param("itemId") int itemId);

    /**
     * 部分撤销：更新订单项数量和状态
     *
     * @param orderId      订单主键ID
     * @param itemId       菜品主键ID
     * @param newQty       新总数量
     * @param newServedQty 新已上菜数量
     * @param newStatus    新状态字符串
     * @return 影响行数
     * @note 撤销后自动重算状态：0=UNSERVED, <total=PARTIALLY_SERVED, =total=SERVED
     */
    int updateOrderItemAfterCancel(
            @Param("orderId") int orderId,
            @Param("itemId") int itemId,
            @Param("newQty") int newQty,
            @Param("newServedQty") int newServedQty,
            @Param("newStatus") String newStatus);

    /**
     * 记录撤销审计日志到 order_cancellations 表
     *
     * @param itemCode          菜品编号（如 "A1"）
     * @param cancelledQuantity 撤销数量
     * @param reason            撤销原因（用户输入或系统默认）
     * @param beforeStatus      撤销前状态（用于审计追踪）
     * @return 影响行数
     */
    int recordCancellation(
            @Param("itemCode") String itemCode,
            @Param("cancelledQuantity") int cancelledQuantity,
            @Param("reason") String reason,
            @Param("beforeStatus") String beforeStatus);

    /**
     * 根据 itemId 反向查询 item_code
     *
     * @param itemId 菜品主键ID
     * @return 菜品编号字符串，不存在返回 null
     * @note 用于撤销日志记录时还原菜品编号
     */
    String getItemCodeByItemId(@Param("itemId") int itemId);

    /**
     * 获取订单项当前状态字符串
     *
     * @param orderId 订单主键ID
     * @param itemId  菜品主键ID
     * @return 状态字符串（如 "SERVED"/"PARTIALLY_SERVED"），不存在返回 null
     */
    String getItemStatus(@Param("orderId") int orderId, @Param("itemId") int itemId);

    /**
     * 🔧 聚餐桌专用：根据 order_item_id 删除订单项
     *
     * @param orderItemId 订单项主键ID
     * @return 影响行数
     * @note 智能撤销时数量归零使用，精确匹配主键避免误删
     */
    int deleteOrderItemByOrderItemId(@Param("orderItemId") int orderItemId);

    /**
     * 🔧 聚餐桌专用：更新订单项数量 + 已上桌数量 + 状态
     *
     * @param orderItemId       订单项主键ID
     * @param newQuantity       新总数量
     * @param newServedQuantity 新已上桌数量
     * @param newStatus         新状态字符串
     * @return 影响行数
     * @note 智能撤销时数量>0 使用，保持 served_quantity 与 quantity 的逻辑一致性
     */
    int updateGroupedOrderItemQuantity(
            @Param("orderItemId") int orderItemId,
            @Param("newQuantity") int newQuantity,
            @Param("newServedQuantity") int newServedQuantity,
            @Param("newStatus") String newStatus
    );

    /**
     * 🔧 聚餐桌共同菜品专用：智能更新 quantity + served_quantity + status + distribution
     *
     * @param orderItemId         订单项主键ID
     * @param newQuantity         新总数量
     * @param newServedQuantity   新已上桌数量
     * @param newStatus           新状态字符串
     * @param newAssignedTableIds 新分配的餐桌显示ID列表（如 "13,14,15"）
     * @param newDistribution     新数量分布JSON（如 {"13":4,"14":4,"15":3}）
     * @return 影响行数
     * @note 聚餐桌共享菜品撤销时，同步更新分配信息和分布记录
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
     *
     * @param orderItemId          订单项主键ID
     * @param assignedTableDisplayId 分配的餐桌显示ID列表（如 "13,14,15"）
     * @param quantityDistribution   quantity_distribution JSON字符串（如 {"13":2,"14":2,"15":2}）
     * @return 影响行数
     * @note 聚餐桌点餐时设置菜品分配信息，支持动态更新
     */
    int updateOrderItemDistribution(
            @Param("orderItemId") int orderItemId,
            @Param("assignedTableDisplayId") String assignedTableDisplayId,
            @Param("quantityDistribution") String quantityDistribution
    );

    /**
     * 🔧 聚餐桌延迟释放专用：清除订单明细中的分配信息
     *
     * @param orderId 订单主键ID
     * @return 影响行数
     * @note 延迟预约释放餐桌时，将 assigned_table_display_id 和 quantity_distribution 置为 NULL
     */
    int clearDistributionByOrderId(@Param("orderId") int orderId);


    // ═══════════════════════════════════════════════════════════
    // 【模块六】批量删除与检查
    // ═══════════════════════════════════════════════════════════

    /**
     * 删除订单所有明细项（整单删除或结账后清理）
     *
     * @param orderId 订单主键ID
     * @return 影响行数（成功删除的记录数）
     */
    int deleteOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 检查订单是否还有剩余明细项
     *
     * @param orderId 订单主键ID
     * @return true=还有明细项，false=订单明细已清空
     * @note 用于判断是否可以删除空订单主表
     */
    boolean hasRemainingItems(@Param("orderId") int orderId);


    // ═══════════════════════════════════════════════════════════
    // 【模块七】基础查询操作
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取订单现有项（用于合并逻辑的数量比对）
     *
     * @param orderId 订单主键ID
     * @param status  可选的状态过滤条件（null 表示不限制）
     * @return List of Map: [{item_code: "A1", quantity: 2, servedQuantity: 1, status: "..."}, ...]
     * @note 返回原始数据供 Service 层构建匹配映射表
     */
    List<Map<String, Object>> getExistingItemQuantitiesRaw(
            @Param("orderId") int orderId,
            @Param("status") String status);

    /**
     * 根据订单ID查询所有订单项（完整信息）
     *
     * @param orderId 订单主键ID
     * @return OrderItem 列表（含关联的菜品名称/编号）
     * @note 用于订单详情展示，自动关联 menu_items 表
     */
    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") int orderId);

    /**
     * 根据 order_item_id 精确查询单个订单项
     *
     * @param orderItemId 订单项主键ID
     * @return OrderItem 对象，不存在返回 null
     * @note 聚餐桌精确操作专用，避免 itemId 重复导致的歧义
     */
    OrderItem selectByPrimaryKey(@Param("orderItemId") int orderItemId);

    /**
     * 🔧 聚餐桌专用：更新订单项数量（仅数量字段）
     *
     * @param orderItemId 订单项主键ID
     * @param newQuantity 新总数量
     * @return 影响行数
     * @note 简单数量调整场景使用，不涉及状态变更
     */
    int updateOrderItemQuantity(@Param("orderItemId") int orderItemId,
                                @Param("newQuantity") int newQuantity);
}