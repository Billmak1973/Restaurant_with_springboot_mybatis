package com.restaurant.service;

import com.restaurant.entity.*;
import com.restaurant.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final MenuItemMapper menuItemMapper;
    private final TablesMapper tablesMapper;  //  新增
    private final BusinessStatusMapper businessStatusMapper;  // 🔧 新增字段

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        MenuItemMapper menuItemMapper,
                        TablesMapper tablesMapper,
                        BusinessStatusMapper businessStatusMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.menuItemMapper = menuItemMapper;
        this.tablesMapper = tablesMapper;
        this.businessStatusMapper = businessStatusMapper;
    }


    @Transactional(readOnly = true)
    public List<OrderItem> loadFormalOrderItems(String tableDisplayId) {
        if (tableDisplayId == null || tableDisplayId.trim().isEmpty() || "未选择".equals(tableDisplayId)) {
            return Collections.emptyList();
        }
        return orderMapper.findOrderItemsByTableDisplayId(tableDisplayId.trim());
    }


    /**
     * 加载堂食订单列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadDineInOrders() {
        return orderMapper.findDineInOrders();
    }

    /**
     * 加载自取订单列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadPickupOrders() {
        return orderMapper.findPickupOrders();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadDeliveryOrders() {
        return orderMapper.findDeliveryOrders();
    }

    @Transactional(readOnly = true)
    public int getNextTakeoutOrderNumber(OrderType orderType) {
        String prefix = (orderType == OrderType.PICKUP) ? "P" : "D";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String deliveryMethod = orderType.getDbDeliveryMethod(); // PICKUP 或 DELIVERY

        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod);
        return next != null ? next : 1;
    }


    // ===== OrderService.java - createOrder 方法（完整版）=====

    @Transactional
    public Integer createOrder(
            Integer tableId,
            String orderNumber,
            String orderType,
            String deliveryMethod,
            String deliveryAddress,
            String customerPhone,
            String customerName,
            Double itemsTotal,
            Double deliveryFee,
            Double totalAmount,
            List<OrderItem> orderItems,
            Order.DeliveryStatus deliveryStatus  // 🔧 新增参数
    ) {
        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】校验 delivery_status 合法性
        // ═══════════════════════════════════════════════════════════
        validateDeliveryStatus(orderType, deliveryMethod, deliveryStatus);
        // ═══════════════════════════════════════════════════════════

        // 创建 Order 实体
        Order order = new Order();
        order.setTableId(tableId);
        order.setOrderNumber(orderNumber);
        order.setOrderType(orderType);
        order.setDeliveryMethod(deliveryMethod);
        order.setDeliveryAddress(deliveryAddress);
        order.setCustomerPhone(customerPhone);
        order.setCustomerName(customerName);

        // 设置三金额字段
        order.setItemsTotal(itemsTotal);
        order.setDeliveryFee(deliveryFee);
        order.setTotalAmount(totalAmount);

        order.setStatus("ORDERED");
        order.setIsCheckedOut(false);

        // 🔧 设置配送状态（校验通过后）
        order.setDeliveryStatus(deliveryStatus);

        // 插入订单主表
        orderMapper.createOrder(order);
        Integer orderId = order.getOrderId();

        if (orderId == null || orderId <= 0) {
            throw new RuntimeException("创建订单主记录失败");
        }

        // 插入订单项
        if (orderItems != null && !orderItems.isEmpty()) {
            orderItemMapper.addOrderItems(orderId, orderItems);
        }
        if (orderId == null || orderId <= 0) {
            // 🔧 打印详细错误信息
            System.err.println(" 创建订单失败！order 对象状态：");
            System.err.println("  orderType=" + order.getOrderType());
            System.err.println("  deliveryMethod=" + order.getDeliveryMethod());
            System.err.println("  deliveryStatus=" + order.getDeliveryStatus());
            throw new RuntimeException("创建订单主记录失败");
        }

        return orderId;
    }

    /**
     * 🔧 校验 delivery_status 合法性
     * 规则：仅 DELIVERY 模式允许非空状态，其他必须为 NULL
     */
    private void validateDeliveryStatus(String orderType, String deliveryMethod, Order.DeliveryStatus deliveryStatus) {
        // 堂食订单：配送状态必须为 NULL
        if ("DINE_IN".equals(orderType)) {
            if (deliveryStatus != null) {
                throw new IllegalArgumentException("堂食订单的配送状态必须为 NULL");
            }
            return;
        }

        // 外卖订单
        if ("TAKEOUT".equals(orderType)) {
            if ("DELIVERY".equals(deliveryMethod)) {
                // 配送订单：配送状态不能为 NULL
                if (deliveryStatus == null) {
                    throw new IllegalArgumentException("配送订单必须指定配送状态");
                }
            } else if ("PICKUP".equals(deliveryMethod)) {
                // 自取订单：配送状态必须为 NULL
                if (deliveryStatus != null) {
                    throw new IllegalArgumentException("自取订单的配送状态必须为 NULL");
                }
            } else {
                // delivery_method 为 NULL 的异常情况
                if (deliveryStatus != null) {
                    throw new IllegalArgumentException("未指定配送方式的订单，配送状态必须为 NULL");
                }
            }
        }
    }


    /**
     * 获取下一个外卖订单序号（返回数字，由 Controller 拼接完整订单号）
     *
     * @param prefix         前缀："P"=自取 / "D"=配送
     * @param dateStr        日期字符串："20260305"
     * @param deliveryMethod 配送方式："PICKUP" / "DELIVERY"
     * @return 下一个序号（从 1 开始）
     */
    @Transactional(readOnly = true)
    public Integer getNextTakeoutOrderNumber(String prefix, String dateStr, String deliveryMethod) {
        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod);
        return next != null ? next : 1;
    }


    @Transactional(readOnly = true)
    public List<OrderItem> loadOrderItemsByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return orderMapper.findOrderItemsByOrderNumber(orderNumber.trim());
    }


    /**
     * 根据订单号查询配送费
     */
    @Transactional(readOnly = true)
    public Double getDeliveryFeeByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return 0.0;
        }
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null) {
            return 0.0;
        }
        return order.getDeliveryFee() != null ? order.getDeliveryFee() : 0.0;
    }

    // ===== OrderService.java 新增方法 =====

    /**
     * 根据餐桌ID查询活跃订单ID（供 Controller 调用）
     */
    @Transactional(readOnly = true)
    public Integer findActiveOrderIdByTableId(Integer tableId) {
        if (tableId == null) return null;
        return orderMapper.findActiveOrderIdByTableId(tableId);
    }

    /**
     * 根据订单号查询活跃订单（供 Controller 调用）
     */
    @Transactional(readOnly = true)
    public Order findActiveOrderByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) return null;
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        // 只返回 ORDERED 状态的活跃订单
        return (order != null && "ORDERED".equals(order.getStatus())) ? order : null;
    }

    // OrderService.java - mergeOrderItems 方法
    @Transactional
    public void mergeOrderItems(Integer orderId, Map<String, Integer> newItemsMap) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        if (newItemsMap == null || newItemsMap.isEmpty()) {
            return;
        }

        // 1. 获取订单现有项（原始列表）
        List<Map<String, Object>> rawList = orderItemMapper.getExistingItemQuantitiesRaw(orderId);

        // 2. 手动转换为 item_code -> quantity 映射
        Map<String, Integer> existingItems = new HashMap<>();
        for (Map<String, Object> row : rawList) {
            String code = ((String) row.get("itemCode")).trim().toUpperCase();
            Integer qty = (Integer) row.get("quantity");
            existingItems.put(code, qty);//關鍵
        }

        // 3. 分离更新/插入项（原有逻辑）
        List<OrderItem> itemsToUpdate = new ArrayList<>();
        List<OrderItem> itemsToInsert = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : newItemsMap.entrySet()) {
            String itemCode = entry.getKey().trim().toUpperCase();
            int newQty = entry.getValue();

            MenuItem menuItem = menuItemMapper.findById(itemCode);
            if (menuItem == null || !menuItem.isActive()) {
                throw new RuntimeException("菜品不存在或已售罄: " + itemCode);
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setItemCode(itemCode);
            orderItem.setQuantity(newQty);
            orderItem.setPriceAtOrder(menuItem.getPrice());

            if (existingItems.containsKey(itemCode)) {
                itemsToUpdate.add(orderItem);
            } else {
                itemsToInsert.add(orderItem);
            }
        }

        // 4. 执行批量更新/插入 + 重算金额（原有逻辑）
        if (!itemsToUpdate.isEmpty()) {
            orderItemMapper.updateExistingOrderItems(orderId, itemsToUpdate);
        }
        if (!itemsToInsert.isEmpty()) {
            orderItemMapper.insertNewOrderItems(orderId, itemsToInsert);
        }

        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }
    }

    /**
     * 根据订单号获取订单状态显示文本
     */
    @Transactional(readOnly = true)
    public String getOrderStatusDisplayByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return "订单情况：";
        }
        Tables.OrderStatus status = orderMapper.getOrderStatusByOrderNumber(orderNumber.trim());
        if (status == null) {
            return "订单情况：未找到订单";
        }
        return "订单情况：" + status.getDisplayName();
    }

    // ===== OrderService.java 新增方法 =====

    @Transactional
    public void markItemsAsServed(String tableNumber, int itemId, int quantity) throws SQLException {
        // ===== 1. 基础验证 =====
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber.trim())) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }
        if (itemId <= 0) {
            throw new IllegalArgumentException("无效的菜品ID");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }

        // ===== 2. 获取餐桌 =====
        Tables table = tablesMapper.findByDisplayId(tableNumber);
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();

        // ===== 3. 获取活跃订单 =====
        Integer orderId = orderMapper.findActiveOrderIdByTableId(tableId);
        if (orderId == null) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");
        }

        // ===== 4. 🔧 查询当前状态 + 计算新值 =====
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            throw new IllegalStateException("订单中找不到菜品: " + itemId);
        }

        int totalQuantity = currentStatus.getQuantity();
        int currentServed = currentStatus.getServedQuantity();
        int newServedQuantity = Math.min(currentServed + quantity, totalQuantity);

        // 计算状态
        String newStatus = (newServedQuantity >= totalQuantity) ? "SERVED" :
                (newServedQuantity > 0) ? "PARTIALLY_SERVED" : "UNSERVED";

        // ===== 5. 🔧 执行更新（传入 4 个参数）=====
        orderItemMapper.incrementServedQuantity(orderId, itemId, newServedQuantity, newStatus);

        // ===== 6. 检查是否全部上桌 =====
        boolean allServed = !orderItemMapper.hasUnservedItems(orderId);
        if (allServed && table != null) {
            table.setOrderStatus(Tables.OrderStatus.ORDERED_FINISHED);
        }

        System.out.println(" 部分上桌成功 - 餐桌: " + tableNumber +
                ", 菜品ID: " + itemId +
                ", 已上桌: " + newServedQuantity + "/" + totalQuantity);
    }

    /**
     * 一鍵標記所有菜品為已上桌（Service 層事務管理）
     *
     * @param tableNumber 餐桌編號
     * @throws SQLException 操作失敗
     */
    @Transactional
    public void markAllItemsAsServed(String tableNumber) throws SQLException {
        // 1. 基礎驗證
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber.trim())) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }

        // 2. 獲取餐桌
        Tables table = tablesMapper.findByDisplayId(tableNumber);
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();

        // 3. 獲取活躍訂單
        Integer orderId = orderMapper.findActiveOrderIdByTableId(tableId);
        if (orderId == null) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");
        }

        // 4. 檢查是否有待上桌菜品
        if (!orderItemMapper.hasUnservedItems(orderId)) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 沒有待上桌的菜品");
        }

        // 5. 執行批量上桌
        int updatedCount = orderItemMapper.markAllItemsAsServed(orderId);
        if (updatedCount <= 0) {
            throw new IllegalStateException("未找到可更新的菜品明細");
        }

        // 6. 更新餐桌狀態
        if (table != null) {
            table.setOrderStatus(Tables.OrderStatus.ORDERED_FINISHED);
        }

        System.out.println("全部上桌成功 - 餐桌: " + tableNumber + ", 更新菜品數: " + updatedCount);
    }

    /**
     * 標記外賣訂單菜品為製作完成（僅支持訂單號）
     */
    @Transactional
    public void markTakeoutItemsAsReady(String orderNumber, int itemId, int quantity) throws SQLException {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("訂單號不能为空");
        }
        if (itemId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("无效的菜品 ID 或數量");
        }

        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("訂單不存在：" + orderNumber);
        }

        Integer orderId = order.getOrderId();
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            throw new IllegalStateException("訂單中找不到菜品：" + itemId);
        }

        int totalQuantity = currentStatus.getQuantity();
        int currentServed = currentStatus.getServedQuantity();
        int newServedQuantity = Math.min(currentServed + quantity, totalQuantity);

        String newStatus = (newServedQuantity >= totalQuantity) ? "SERVED" :
                (newServedQuantity > 0) ? "PARTIALLY_SERVED" : "UNSERVED";

        orderItemMapper.incrementServedQuantity(orderId, itemId, newServedQuantity, newStatus);
        System.out.println(" 外賣訂單標記完成 - 訂單號：" + orderNumber + ", 菜品：" + itemId);
    }

    /**
     * 一鍵標記外賣訂單所有菜品為製作完成
     */
    @Transactional
    public void markAllTakeoutItemsAsReady(String orderNumber) throws SQLException {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("訂單號不能为空");
        }

        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("訂單不存在：" + orderNumber);
        }

        Integer orderId = order.getOrderId();
        if (!orderItemMapper.hasUnservedItems(orderId)) {
            throw new IllegalStateException("訂單 " + orderNumber + " 沒有待製作的菜品");
        }

        int updatedCount = orderItemMapper.markAllItemsAsServed(orderId);
        if (updatedCount <= 0) {
            throw new IllegalStateException("未找到可更新的菜品明細");
        }
        System.out.println("✅ 外賣訂單全部標記完成 - 訂單號: " + orderNumber + ", 更新菜品數: " + updatedCount);
    }
    // OrderService.java

    /**
     * 撤銷堂食訂單中的菜品（@Transactional 自動管理事務）
     */
    @Transactional
    public void cancelOrderItem(String tableNumber, String itemCode, int cancelQuantity, String cancellationReason) throws SQLException {
        // ===== 1. 基礎驗證 =====
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("餐桌號不能为空");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("菜品編號不能为空");
        }
        if (cancelQuantity <= 0) {
            throw new IllegalArgumentException("撤銷數量必須大於 0");
        }

        // ===== 2. 獲取餐桌 → 餐桌 ID =====
        Tables table = tablesMapper.findByDisplayId(tableNumber.trim());
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();

        // ===== 3. 獲取活躍訂單 =====
        Integer orderId = orderMapper.findActiveOrderIdByTableId(tableId);
        if (orderId == null) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");
        }

        // ===== 4. 獲取菜品 ID =====
        Integer itemId = menuItemMapper.findItemIdByCode(itemCode.trim().toUpperCase());
        if (itemId == null) {
            throw new IllegalStateException("菜品 " + itemCode + " 不存在");
        }

        // ===== 5. 查詢當前菜品狀態 =====
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            throw new IllegalStateException("訂單中找不到菜品: " + itemCode);
        }

        // ===== 6. 計算新數量和狀態 =====
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - cancelQuantity);
        int newServedQty = Math.min(servedQty, newQty);

        // ===== 7. 執行撤銷操作 =====
        if (newQty == 0) {
            // ✅ 完全撤銷：記錄審計 + 刪除明細
            orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                    cancellationReason != null ? cancellationReason : "用戶撤銷",
                    servedQty > 0 ? "SERVED" : "UNSERVED");
            orderItemMapper.deleteOrderItem(orderId, itemId);  // ✅ 直接 DELETE，不設置 "DELETED" 狀態
        } else {
            // ✅ 部分撤銷：更新數量和狀態（只用三個合法值）
            String newStatus = (newServedQty == 0) ? "UNSERVED" :
                    (newServedQty >= newQty) ? "SERVED" : "PARTIALLY_SERVED";
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
        }

        // ===== 8. 重新計算訂單總金額 =====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // ===== 9. 檢查訂單是否為空 → 刪除空訂單 =====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
            System.out.println(" 空訂單已自動刪除: orderId=" + orderId);
        }

        System.out.println(" 撤銷成功 - 餐桌:" + tableNumber + ", 菜品:" + itemCode + ", 數量:" + cancelQuantity);
    }

    /**
     * 撤銷外賣訂單中的菜品（@Transactional，原因可選）
     */
    @Transactional
    public void cancelTakeoutOrderItem(String orderNumber, int itemId, int quantity, String cancellationReason) throws SQLException {
        // 1. 基礎驗證
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("訂單號不能为空");
        }
        if (itemId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("无效的菜品 ID 或數量");
        }

        // 2. 通過訂單號查找訂單
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("訂單不存在: " + orderNumber);
        }
        Integer orderId = order.getOrderId();

        // 3. 查詢當前菜品狀態
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            throw new IllegalStateException("訂單中找不到菜品: " + itemId);
        }

        // 4. 計算新數量和狀態
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - quantity);
        int newServedQty = Math.min(servedQty, newQty);

        // 5. 執行撤銷
        if (newQty == 0) {
            // 完全撤銷：記錄審計 + 刪除明細
            String itemCode = orderItemMapper.getItemCodeByItemId(itemId);
            orderItemMapper.recordCancellation(itemCode, quantity,
                    cancellationReason != null ? cancellationReason : "用戶撤銷",
                    servedQty > 0 ? "SERVED" : "UNSERVED");
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            // 部分撤銷：更新數量和狀態
            String newStatus = (newServedQty == 0) ? "UNSERVED" :
                    (newServedQty >= newQty) ? "SERVED" : "PARTIALLY_SERVED";
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
        }

        // 6. 重新計算訂單總金額
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // 7. 檢查訂單是否為空 → 刪除空訂單
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
        }

        System.out.println(" 外賣撤銷成功 - 訂單號:" + orderNumber + ", 菜品:" + itemId);
    }

    /**
     * 撤销整个外卖订单（整单取消）
     */
    @Transactional
    public void cancelTakeoutOrder(String orderNumber, String reason) throws SQLException {
        // 1. 基础验证
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }

        // 2. 通过订单号查找订单
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("订单不存在：" + orderNumber);
        }
        Integer orderId = order.getOrderId();

        // 3. 记录整单取消审计日志
        //  正确：调用统一的 recordCancellation 方法
        orderMapper.recordCancellation(
                "ORDER",                        // cancellationType: 整单撤销
                orderId,                        // orderId
                orderNumber,                    // orderNumber
                null,                           // itemCode: 整单撤销无需填写
                null,                           // cancelledQuantity: 整单撤销无需填写
                null,                           // beforeStatus: 整单撤销无需填写
                order.getTotalAmount(),         // cancelledAmount: 订单总金额
                reason != null ? reason : "顾客取消订单"  // reason
        );

        // 4. 删除订单明细
        orderItemMapper.deleteOrderItemsByOrderId(orderId);

        // 5. 删除订单主表
        orderMapper.deleteOrder(orderId);

        System.out.println(" 外卖整单撤销成功 - 订单号:" + orderNumber);
    }

    @Transactional
    public void updateDeliveryStatus(String orderNumber, Order.DeliveryStatus newStatus) {
        // 1. 验证参数
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("配送状态不能为空");
        }

        // 2. 查询订单
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("订单不存在: " + orderNumber);
        }

        // 3. 验证是否为配送订单
        if (!"DELIVERY".equals(order.getDeliveryMethod())) {
            throw new IllegalStateException("订单 " + orderNumber + " 不是配送订单，不能更新配送状态");
        }

        // 4. 执行更新
        int updated = orderMapper.updateDeliveryStatus(
                order.getOrderId(),
                newStatus.name()  // 传枚举的 name: "NOT_DELIVERED" 等
        );

        if (updated == 0) {
            throw new RuntimeException("更新配送状态失败");
        }

        System.out.println("🚚 配送状态更新成功: " + orderNumber + " → " + newStatus.getDisplayName());
    }


    @Transactional
    public void updateOrderDeliveryFee(Integer orderId, Double newDeliveryFee) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        if (newDeliveryFee == null || newDeliveryFee < 0) {
            throw new IllegalArgumentException("配送费不能为负数");
        }

        // 先查询原订单，获取 items_total
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }

        Double itemsTotal = order.getItemsTotal() != null ? order.getItemsTotal() : 0.0;
        Double totalAmount = itemsTotal + newDeliveryFee;

        // 更新配送费和总金额
        int updated = orderMapper.updateDeliveryFee(orderId, newDeliveryFee);
        if (updated == 0) {
            throw new RuntimeException("更新配送费失败");
        }

        System.out.println("🚚 配送费更新成功: orderId=" + orderId +
                ", 新配送费=" + newDeliveryFee + ", 新总金额=" + totalAmount);
    }


    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processTakeoutCheckout(String orderNumber, double paymentAmount) {
        Map<String, Object> result = new HashMap<>();
        try {
            // ===== 1. 查询订单（加锁，防止并发）=====
            Order order = orderMapper.findByOrderNumber(orderNumber);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在：" + orderNumber);
                return result;
            }

            // 🔧 ===== 2. 加强结账前状态验证 =====
            String deliveryMethod = order.getDeliveryMethod();

            // 先检查是否已结账（最优先）
            if ("CHECKED_OUT".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "订单已结账，不能重复结账");
                return result;
            }

            if ("PICKUP".equals(deliveryMethod)) {
                // 🟢 自取订单：检查制作状态
                if (order.getTableId() != null) {
                    Tables table = tablesMapper.findById(order.getTableId());
                    if (table != null) {
                        Tables.OrderStatus orderStatus = table.getOrderStatus();
                        if (orderStatus != Tables.OrderStatus.ORDERED_FINISHED) {
                            result.put("success", false);
                            result.put("message", "自取订单尚未制作完成，当前状态：" +
                                    (orderStatus != null ? orderStatus.getDisplayName() : "未知"));
                            return result;
                        }
                    }
                }
            } else if ("DELIVERY".equals(deliveryMethod)) {
                // 🚚 配送订单：检查配送状态
                Order.DeliveryStatus deliveryStatus = order.getDeliveryStatus();
                if (deliveryStatus != Order.DeliveryStatus.DELIVERED) {
                    result.put("success", false);
                    result.put("message", "配送订单尚未送达，当前状态：" +
                            (deliveryStatus != null ? deliveryStatus.getDisplayName() : "未知") +
                            "\n\n请先将配送状态更新为【已送达】才能结账！");
                    return result;
                }
            }

            // ===== 3. 检查支付金额 =====
            double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
            if (paymentAmount < totalAmount) {
                result.put("success", false);
                result.put("message", "支付金额不足");
                return result;
            }

            // ===== 4. 执行结账操作 =====
            // 4.1 更新订单状态为已结账
            int updated = orderMapper.checkoutOrder(order.getOrderId());
            if (updated == 0) {
                throw new RuntimeException("更新订单状态失败，订单可能已被结账");
            }

            // 4.2 更新当日营收 + 外卖订单计数
            LocalDate revenueDate = order.getOrderTime() != null ?
                    order.getOrderTime().toLocalDate() : LocalDate.now();
            java.sql.Date sqlDate = java.sql.Date.valueOf(revenueDate);

            orderMapper.updateDailyRevenue(totalAmount, sqlDate);
            businessStatusMapper.incrementDailyTakeoutCount(sqlDate);

            // 4.3 记录季度销售统计
            String quarter = getQuarterFromDate(revenueDate);
            orderMapper.recordQuarterlySales(order.getOrderId(), revenueDate.getYear(), quarter);

            // 4.4 删除订单记录（外卖订单结账后清理）
            orderItemMapper.deleteOrderItemsByOrderId(order.getOrderId());
            orderMapper.deleteOrder(order.getOrderId());

            // ===== 5. 返回结果 =====
            result.put("success", true);
            result.put("changeAmount", paymentAmount - totalAmount);
            result.put("totalAmount", totalAmount);
            result.put("revenueDate", sqlDate);

            System.out.println("✅ 外卖订单结账成功：" + orderNumber +
                    ", 金额：" + totalAmount +
                    ", 找零：" + (paymentAmount - totalAmount));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "结账失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private String getQuarterFromDate(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    /**
     * 获取订单详情（支持堂食 + 外卖）
     *
     * @param orderType  "DINE_IN" 或 "TAKEOUT"
     * @param identifier 餐桌号 或 订单号
     * @return 订单详情 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderDetails(String orderType, String identifier) {
        Map<String, Object> result = new HashMap<>();
        try {
            if ("DINE_IN".equals(orderType)) {
                // 堂食：通过餐桌号查询
                Tables table = tablesMapper.findByDisplayId(identifier);
                if (table == null) {
                    result.put("error", "未找到餐桌：" + identifier);
                    return result;
                }
                // 查询订单
                Integer orderId = orderMapper.findActiveOrderIdByTableId(table.getTableId());
                if (orderId == null) {
                    result.put("error", "该餐桌没有活跃订单");
                    return result;
                }
                // 获取订单详情
                Order order = orderMapper.findById(orderId);
                List<OrderItem> items = orderMapper.findOrderItemsByOrderId(orderId);
                result.put("orderTime", order.getOrderTime());
                result.put("totalAmount", order.getTotalAmount());
                result.put("items", itemsToMapList(items));

                // ===== 🔧 新增：补充金额明细字段（修复下方金额显示为 0 的问题）=====
                result.put("itemsTotal", order.getItemsTotal());
                result.put("deliveryFee", order.getDeliveryFee());
                result.put("deliveryMethod", order.getDeliveryMethod());

            } else if ("TAKEOUT".equals(orderType)) {
                // 外卖：通过订单号查询
                Order order = orderMapper.findByOrderNumber(identifier);
                if (order == null) {
                    result.put("error", "未找到订单：" + identifier);
                    return result;
                }
                List<OrderItem> items = orderMapper.findOrderItemsByOrderId(order.getOrderId());
                result.put("orderTime", order.getOrderTime());
                result.put("totalAmount", order.getTotalAmount());
                result.put("items", itemsToMapList(items));

                // ===== 🔧 新增：补充金额明细字段（修复下方金额显示为 0 的问题）=====
                result.put("itemsTotal", order.getItemsTotal());
                result.put("deliveryFee", order.getDeliveryFee());
                result.put("deliveryMethod", order.getDeliveryMethod());
            }
        } catch (Exception e) {
            result.put("error", "查询订单详情失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 辅助方法：将 OrderItem 列表转换为 Map 列表（供 View 层渲染）
     */
    private List<Map<String, Object>> itemsToMapList(List<OrderItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("itemCode", item.getItemCode());
            map.put("itemName", item.getItemName());
            map.put("quantity", item.getQuantity());
            map.put("servedQuantity", item.getServedQuantity());
            map.put("price", item.getPriceAtOrder());
            map.put("subtotal", item.getQuantity() * item.getPriceAtOrder());
            map.put("status", item.getStatus());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 根据订单号查询订单状态（返回枚举）
     *
     * @param orderNumber 订单号
     * @return Tables.OrderStatus 枚举
     */
    @Transactional(readOnly = true)
    public Tables.OrderStatus getOrderStatusByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return Tables.OrderStatus.NO_ORDER;
        }
        return orderMapper.getOrderStatusByOrderNumber(orderNumber.trim());
    }


/**
 * 重置已结账订单（供重单使用）
 *
 * @param orderId 已结账订单ID
 */
    @Transactional
    public void resetCheckedOutOrder(Integer orderId) {
        if (orderId == null) return;

        // 🔧【关键修复】使用 updateOrderForReorder 而不是 updateOrderStatusAndAmount
        // 这样才能记录 reorder_time
        orderMapper.updateOrderForReorder(
                orderId,
                "ORDERED",    // 状态
                0.0,          // 金额清零
                LocalDateTime.now()  // 🔧 设置重单时间为当前时间
        );

        System.out.println("🔄 订单重置成功: orderId=" + orderId);
    }

    /**
     * 根据餐桌ID查询最近的已结账订单
     */
    @Transactional(readOnly = true)
    public Integer findCheckedOutOrderIdByTableId(Integer tableId) {
        if (tableId == null) return null;
        return orderMapper.findCheckedOutOrderIdByTableId(tableId);
    }


    /**
     * 取消重新点餐（恢复订单为已结账状态）
     * @param tableNumber 餐桌显示编号
     * @return Map{success: Boolean, message: String}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReorder(String tableNumber) {
        Map<String, Object> result = new HashMap<>();

        try {
            // ===== 1. 基础验证 =====
            if (tableNumber == null || tableNumber.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "餐桌号不能为空");
                return result;
            }

            // ===== 2. 查询餐桌 =====
            Tables table = tablesMapper.findByDisplayId(tableNumber.trim());
            if (table == null) {
                result.put("success", false);
                result.put("message", "餐桌不存在: " + tableNumber);
                return result;
            }
            int tableId = table.getTableId();

            // ===== 3. 查询活跃订单 =====
            Integer orderId = orderMapper.findActiveOrderIdByTableId(tableId);
            if (orderId == null) {
                result.put("success", false);
                result.put("message", "餐桌 " + tableNumber + " 没有活跃订单，无需取消重新点餐");
                return result;
            }

            // ===== 4. 验证是否为重新点单场景 =====
            if (!orderMapper.isOrderPreviouslyCheckedOut(tableId)) {
                result.put("success", false);
                result.put("message", "餐桌 " + tableNumber + " 的订单是全新订单，不属于重新点单场景");
                return result;
            }

            // ===== 5. 验证是否有已上菜菜品 =====
            if (orderItemMapper.hasServedItems(orderId)) {
                result.put("success", false);
                result.put("message", "餐桌 " + tableNumber + " 有已上桌菜品，不能取消重新点餐");
                return result;
            }

            // ===== 6. 执行恢复操作（仅数据库）=====

            LocalDateTime now = LocalDateTime.now();

            // 🔧 调用新方法：状态+金额+重单时间 三合一更新
            orderMapper.updateOrderForReorder(
                    orderId,
                    "CHECKED_OUT",    // 重置为已结账
                    0.0,              // 金额清零
                    now               // 🔧 更新重单时间为当前时间
            );
            // 6.1 更新订单状态为已结账 + 金额清零
           // orderMapper.updateOrderStatusAndAmount(orderId, "CHECKED_OUT", 0.0);

            // 6.2 可选：清空订单明细（根据业务需求决定）
             orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // ===== 7. 返回成功结果 =====
            result.put("success", true);
            result.put("message", "餐桌 " + tableNumber + " 已恢复为已结账状态");
            result.put("tableNumber", tableNumber);  // 🔧 返回餐桌号供调用方刷新
            System.out.println("✅ 已取消重新点餐 - 餐桌: " + tableNumber);

        } catch (Exception e) {
            // 系统异常：记录日志 + 返回错误
            result.put("success", false);
            result.put("message", "系统错误: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }
}