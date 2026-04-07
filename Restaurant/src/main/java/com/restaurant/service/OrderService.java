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
    private final TableReservationMapper reservationMapper;
    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        MenuItemMapper menuItemMapper,
                        TablesMapper tablesMapper,
                        BusinessStatusMapper businessStatusMapper,
                        TableReservationMapper reservationMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.menuItemMapper = menuItemMapper;
        this.tablesMapper = tablesMapper;
        this.businessStatusMapper = businessStatusMapper;
        this.reservationMapper = reservationMapper;
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

    /**
     * 🔧【核心修复】根据 served_quantity 和 total_quantity 计算合并后的正确状态
     * 4种情况：
     * 1. 预约订单（客人未入座）→ PREPARING/PREPARED
     * 2. 客人已入座 + 菜品未上桌 → PREPARING
     * 3. 客人已入座 + 菜品已上桌 → PARTIALLY_SERVED/SERVED
     * 4. 普通堂食订单 → PARTIALLY_SERVED/SERVED/UNSERVED
     *
     * @param servedQty          已上桌/已准备数量
     * @param originalQty        原订单总数量
     * @param newQty             本次新增数量
     * @param originalStatus     数据库当前状态
     * @param isReservationOrder 是否为预约订单（order_type='RESERVATION'）
     * @param isReservationSeated 是否为预约入座（currentReservationId 不为空）
     * @return 合并后应使用的状态字符串
     */
    private String calculateMergedStatus(int servedQty, int originalQty, int newQty,
                                         String originalStatus,
                                         boolean isReservationOrder,
                                         boolean isReservationSeated) {
        int totalQty = originalQty + newQty;

        // ═══════════════════════════════════════════════════════════
        // 【情况1】预约订单（客人未入座）→ 只能是 PREPARING/PREPARED
        // ═══════════════════════════════════════════════════════════
        if (isReservationOrder && !isReservationSeated) {
            if (servedQty >= totalQty) {
                return "PREPARED";  // 全部已准备
            } else {
                return "PREPARING"; // 部分或未准备
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【情况2&3】预约入座（currentReservationId 存在）
        // ═══════════════════════════════════════════════════════════
        if (isReservationSeated) {
            // ──【情况2】菜品未上桌（PREPARING/PREPARED/UNSERVED）→ PREPARING
            if ("PREPARING".equals(originalStatus) ||
                    "PREPARED".equals(originalStatus) ||
                    "UNSERVED".equals(originalStatus)) {
                return "PREPARING";
            }
            // ──【情况3】菜品已上桌（PARTIALLY_SERVED/SERVED）→ PARTIALLY_SERVED
            else if ("PARTIALLY_SERVED".equals(originalStatus) ||
                    "SERVED".equals(originalStatus)) {
                if (servedQty >= totalQty) {
                    return "SERVED";
                } else {
                    return "PARTIALLY_SERVED";
                }
            }
            // 兜底
            else {
                return "PREPARING";
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【情况4】普通堂食订单（无 currentReservationId）
        // ═══════════════════════════════════════════════════════════
        if (servedQty >= totalQty) {
            return "SERVED";
        } else if (servedQty > 0) {
            return "PARTIALLY_SERVED";
        } else {
            return "UNSERVED";
        }
    }

    @Transactional
    public void mergeOrderItems(Integer orderId, Map<String, Integer> newItemsMap) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        if (newItemsMap == null || newItemsMap.isEmpty()) {
            return;
        }

        // 1. 查询订单
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修復】只有預點餐訂單 + NO_ORDER 狀態才升級為 ORDERED
        // ═══════════════════════════════════════════════════════════
        if ("RESERVATION".equals(order.getOrderType()) &&
                "NO_ORDER".equals(order.getStatus()) &&
                !newItemsMap.isEmpty()) {

            int updated = orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");
            if (updated > 0) {
                System.out.println(" [狀態升級] 預點餐訂單: orderId=" + orderId +
                        ", reservationId=" + order.getReservationId() +
                        ", NO_ORDER → ORDERED");
            } else {
                // 可能並發情況下已被其他請求升級，記錄警告但不拋異常
                System.out.println("[狀態升級] 訂單 " + orderId + " 可能已被其他請求升級，跳過");
            }
        }

        // 2. 🔧【核心】判断订单类型和入座状态
        boolean isReservationOrder = "RESERVATION".equals(order.getOrderType());
        boolean isReservationSeated = false;

        if (order.getTableId() != null) {
            Tables table = tablesMapper.findById(order.getTableId());
            if (table != null &&
                    table.getCurrentReservationId() != null &&
                    !table.getCurrentReservationId().isEmpty()) {
                isReservationSeated = true;
                System.out.println("🔍 检测到预约入座订单: orderId=" + orderId +
                        ", reservationId=" + table.getCurrentReservationId());
            }
        }

        // 3. 🔧【核心修复】获取订单现有项 + served_quantity + 状态
        List<Map<String, Object>> rawList = orderItemMapper.getExistingItemQuantitiesRaw(orderId,null);
        Map<String, Integer> existingItems = new HashMap<>();
        Map<String, Integer> existingServedQty = new HashMap<>();
        Map<String, String> existingItemStatus = new HashMap<>();

        for (Map<String, Object> row : rawList) {
            String code = ((String) row.get("itemCode")).trim().toUpperCase();
            Integer qty = (Integer) row.get("quantity");
            Integer served = (Integer) row.get("servedQuantity");
            String status = (String) row.get("status");

            existingItems.put(code, qty);
            existingServedQty.put(code, served != null ? served : 0);
            existingItemStatus.put(code, status);
        }

        // 4. 分离更新/插入项
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

        // 5. 🔧【核心修复】根据4种情况计算合并后的正确状态
        if (!itemsToUpdate.isEmpty()) {
            // 🔹 预约入座：按计算后的状态分组
            List<OrderItem> servedGroup = new ArrayList<>();    // PARTIALLY_SERVED/SERVED → 普通逻辑
            List<OrderItem> unservedGroup = new ArrayList<>();  // PREPARING/PREPARED/UNSERVED → 预约逻辑

            for (OrderItem item : itemsToUpdate) {
                String itemCode = item.getItemCode();
                int newQty = item.getQuantity();

                // 🔧 获取原有数据
                Integer originalQty = existingItems.get(itemCode);
                Integer originalServed = existingServedQty.getOrDefault(itemCode, 0);
                String originalStatus = existingItemStatus.get(itemCode);

                // 🔧【核心】计算合并后的状态（4种情况）
                String newStatus = calculateMergedStatus(
                        originalServed,           // served_qty
                        originalQty,              // 原数量
                        newQty,                   // 新增数量
                        originalStatus,           // 原状态
                        isReservationOrder,       // 是否预约订单
                        isReservationSeated       // 是否预约入座
                );

                System.out.println("🔧 菜品 " + itemCode +
                        " 原:" + originalStatus + "(" + originalServed + "/" + originalQty + ")" +
                        " + 新增:" + newQty +
                        " → 新状态:" + newStatus + "(" + originalServed + "/" + (originalQty + newQty) + ")" +
                        " [预约订单:" + isReservationOrder + ", 预约入座:" + isReservationSeated + "]");

                // 根据新状态分组处理
                if ("SERVED".equals(newStatus) || "PARTIALLY_SERVED".equals(newStatus)) {
                    servedGroup.add(item);
                } else {
                    unservedGroup.add(item);
                }

                // 🔧 临时保存新状态供后续使用
                item.setStatus(newStatus);
            }

            //  已上桌的菜品 → 普通堂食逻辑
            if (!servedGroup.isEmpty()) {
                System.out.println(" 已上桌菜品 → 普通逻辑，数量: " + servedGroup.size());
                orderItemMapper.updateExistingOrderItems(orderId, servedGroup);
            }
            //  未上桌的菜品 → 预约逻辑
            if (!unservedGroup.isEmpty()) {
                System.out.println(" 未上桌菜品 → 预约逻辑，数量: " + unservedGroup.size());
                orderItemMapper.updateExistingOrderItemsForReservation(orderId, unservedGroup);
            }
        }

        // 6. 🔧 新插入的菜品设置初始状态
        if (!itemsToInsert.isEmpty()) {
            String initialStatus = "UNSERVED";  //  修复：统一为 UNSERVED

            System.out.println(" 新菜品初始状态: " + initialStatus + ", 数量: " + itemsToInsert.size());
            orderItemMapper.insertNewOrderItemsWithStatus(orderId, itemsToInsert, initialStatus);
        }

        // 7. 重算金额（预约订单无配送费）
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            // 🔧 预约订单：只更新 items_total 和 total_amount，两者相等
            orderMapper.updateOrderTotals(
                    orderId,
                    newTotal,    // items_total = 菜品总金额
                    newTotal     // total_amount = items_total（无配送费）
            );

            System.out.println(" 预约订单金额已更新: orderId=" + orderId +
                    ", items_total=" + newTotal +
                    ", total_amount=" + newTotal);
        }


        System.out.println(" 订单合并完成: orderId=" + orderId +
                ", 更新=" + itemsToUpdate.size() +
                ", 新增=" + itemsToInsert.size() +
                ", isReservationOrder=" + isReservationOrder +
                ", isReservationSeated=" + isReservationSeated);
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
     * 🔧【新增】如果菜品已全部完成，自动将配送状态从"未配送"改为"送单中"
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

        // 🔧【核心修改】如果没有待制作的菜品，检查是否需要自动更新配送状态
        if (!orderItemMapper.hasUnservedItems(orderId)) {
            System.out.println("⚠️ 订单 " + orderNumber + " 所有菜品已制作完成，检查配送状态...");

            // 仅配送订单且状态为"未配送"时，自动推进到"送单中"
            if ("DELIVERY".equals(order.getDeliveryMethod()) &&
                    order.getDeliveryStatus() == Order.DeliveryStatus.NOT_DELIVERED) {

                // 自动更新配送状态
                orderMapper.updateDeliveryStatus(orderId, Order.DeliveryStatus.DELIVERING.name());
                System.out.println("🚚 自动更新配送状态: " + orderNumber +
                        " [未配送 → 送单中]");

                // 可选：弹出提示告知用户
                // JOptionPane.showMessageDialog(..., "菜品已全部完成，已自动更新为【送单中】状态");
            } else {
                System.out.println(" 订单 " + orderNumber + " 已制作完成，配送状态: " +
                        (order.getDeliveryStatus() != null ? order.getDeliveryStatus().getDisplayName() : "N/A"));
            }
            return;  //  菜品已完成，直接返回，不抛异常
        }

        // 原有逻辑：标记所有菜品为已制作完成
        int updatedCount = orderItemMapper.markAllItemsAsServed(orderId);
        if (updatedCount <= 0) {
            throw new IllegalStateException("未找到可更新的菜品明細");
        }

        // 🔧【新增】如果是配送订单，自动将配送状态更新为"送单中"
        if ("DELIVERY".equals(order.getDeliveryMethod())) {
            orderMapper.updateDeliveryStatus(orderId, Order.DeliveryStatus.DELIVERING.name());
            System.out.println("🚚 配送状态已更新: " + orderNumber + " [未配送 → 送单中]");
        }

        System.out.println("✅ 外賣訂單全部標記完成 - 訂單號: " + orderNumber +
                ", 更新菜品數: " + updatedCount);
    }


    /**
     * 🔧【核心修復】根據 served_quantity 和總數量計算撤銷後的正確狀態
     * 4 種情況：
     * 1. 預約訂單（客人未入座）→ PREPARING/PREPARED/UNSERVED
     * 2. 客人已入座 + 菜品未上桌 → PREPARING/PREPARED/UNSERVED
     * 3. 客人已入座 + 菜品已上桌 → PARTIALLY_SERVED/SERVED/UNSERVED
     * 4. 普通堂食訂單 → 根據 servedQty 和 newQty 計算
     *
     * @param servedQty          已上桌/已準備數量
     * @param originalQty        原訂單總數量
     * @param cancelQuantity     本次撤銷數量
     * @param originalStatus     數據庫當前狀態
     * @param isReservationOrder 是否為預約訂單（order_type='RESERVATION'）
     * @param isReservationSeated 是否為預約入座（currentReservationId 不為空）
     * @return 撤銷後應使用的狀態字符串
     */
    private String calculateCancelledStatus(int servedQty, int originalQty, int cancelQuantity,
                                            String originalStatus,
                                            boolean isReservationOrder,
                                            boolean isReservationSeated) {
        int newQty = Math.max(0, originalQty - cancelQuantity);      // 撤銷後的新數量
        int newServedQty = Math.min(servedQty, newQty);              // 已上桌數量不能超過新總數

        // ═══════════════════════════════════════════════════════════
        // 【情況 1】預約訂單（客人未入座）→ 只能是 PREPARING/PREPARED/UNSERVED
        // ═══════════════════════════════════════════════════════════
        if (isReservationOrder && !isReservationSeated) {
            if (newQty == 0) {
                return "UNSERVED";                          // 全部撤銷，狀態重置
            } else if (newServedQty == 0) {
                return "UNSERVED";                          // 🔧【核心修復】已準備0份 = 未準備
            } else if (newServedQty >= newQty) {
                return "PREPARED";                          // 剩餘的全部已準備
            } else {
                return "PREPARING";                         // 部分準備中 (0 < served < total)
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【情況 2&3】預約入座（currentReservationId 存在）
        // ═══════════════════════════════════════════════════════════
        if (isReservationSeated) {
            // ──【情況 2】原狀態是未上桌（PREPARING/PREPARED/UNSERVED）
            if ("PREPARING".equals(originalStatus) ||
                    "PREPARED".equals(originalStatus) ||
                    "UNSERVED".equals(originalStatus)) {

                if (newQty == 0) {
                    return "UNSERVED";                      // 全部撤銷
                } else if (newServedQty == 0) {
                    return "UNSERVED";                      // 🔧【核心修復】已準備0份 = 未準備
                } else if (newServedQty >= newQty) {
                    return "PREPARED";                      // 剩餘的全部已準備
                } else {
                    return "PREPARING";                     // 部分準備中
                }
            }
            // ──【情況 3】原狀態是已上桌（PARTIALLY_SERVED/SERVED）
            else if ("PARTIALLY_SERVED".equals(originalStatus) ||
                    "SERVED".equals(originalStatus)) {

                if (newQty == 0) {
                    return "UNSERVED";                      // 全部撤銷
                } else if (newServedQty >= newQty) {
                    return "SERVED";                        // 剩餘的全部已上桌
                } else if (newServedQty > 0) {
                    return "PARTIALLY_SERVED";              // 部分上桌
                } else {
                    return "UNSERVED";                      // 撤銷後沒有已上桌的
                }
            }
            // 兜底
            else {
                return "PREPARING";
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【情況 4】普通堂食訂單（無 currentReservationId）
        // ═══════════════════════════════════════════════════════════
        if (newQty == 0) {
            return "UNSERVED";                              // 全部撤銷
        } else if (newServedQty == 0) {
            return "UNSERVED";                              // 🔧【核心修復】沒有已上桌的
        } else if (newServedQty >= newQty) {
            return "SERVED";                                // 剩餘的全部已上桌
        } else if (newServedQty > 0) {
            return "PARTIALLY_SERVED";                      // 部分上桌
        } else {
            return "UNSERVED";                              // 沒有已上桌的
        }
    }

    /**
     * 撤銷堂食訂單中的菜品（@Transactional 自動管理事務）
     * 🔧【核心修復】使用 calculateCancelledStatus 計算撤銷後的正確狀態
     * 🔧【核心修復】只有已上桌的菜品才記錄撤銷審計
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

        // ===== 6. 🔧【核心】判斷訂單類型和入座狀態（與 mergeOrderItems 一致）=====
        Order order = orderMapper.findById(orderId);
        boolean isReservationOrder = "RESERVATION".equals(order.getOrderType());
        boolean isReservationSeated = false;

        if (order.getTableId() != null) {
            Tables orderTable = tablesMapper.findById(order.getTableId());
            if (orderTable != null &&
                    orderTable.getCurrentReservationId() != null &&
                    !orderTable.getCurrentReservationId().isEmpty()) {
                isReservationSeated = true;
                System.out.println("🔍 检测到预约入座订单: orderId=" + orderId +
                        ", reservationId=" + orderTable.getCurrentReservationId());
            }
        }

        // ===== 7. 計算新數量和狀態 =====
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();

        // 🔧 获取原状态（用於狀態計算和審計記錄）
        String originalStatus = orderItemMapper.getItemStatus(orderId, itemId);

        // 🔧【核心修复】使用 calculateCancelledStatus 計算新狀態
        String newStatus = calculateCancelledStatus(
                servedQty,              // 已上桌數量
                totalQty,               // 原數量
                cancelQuantity,         // 撤銷數量
                originalStatus,         // 原狀態
                isReservationOrder,     // 是否預約訂單
                isReservationSeated     // 是否預約入座
        );

        int newQty = Math.max(0, totalQty - cancelQuantity);
        int newServedQty = Math.min(servedQty, newQty);

        System.out.println("🔧 菜品 " + itemCode +
                " 原:" + originalStatus + "(" + servedQty + "/" + totalQty + ")" +
                " - 撤銷:" + cancelQuantity +
                " → 新狀態:" + newStatus + "(" + newServedQty + "/" + newQty + ")" +
                " [預約訂單:" + isReservationOrder + ", 預約入座:" + isReservationSeated + "]");

        // ===== 8. 🔧【核心修復】執行撤銷操作 =====
        if (newQty == 0) {
            // ✅ 完全撤銷：刪除明細

            // 🔧【核心修复】只有已上桌的菜品才记录撤销审计
            // 准备中的菜品（PREPARING/PREPARED/UNSERVED）不需要记录审计
            if ("SERVED".equals(originalStatus) ||
                    "PARTIALLY_SERVED".equals(originalStatus)) {
                // 已上桌菜品：记录审计 + 删除
                orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                        cancellationReason != null ? cancellationReason : "用戶撤銷",
                        originalStatus);  // 使用原状态
                System.out.println("📝 已记录撤销审计：菜品 " + itemCode +
                        " 状态=" + originalStatus);
            } else {
                // 未上桌菜品（PREPARING/PREPARED/UNSERVED）：直接删除，不记录
                System.out.println("🗑️ 准备中的菜品直接删除，不记录审计：菜品 " + itemCode +
                        " 状态=" + originalStatus);
            }

            // 删除订单项（所有情况都删除）
            orderItemMapper.deleteOrderItem(orderId, itemId);
            System.out.println(" 菜品 " + itemCode + " 已完全撤銷並刪除");
        } else {
            // 部分撤銷：更新數量和狀態（使用 calculateCancelledStatus 計算的新狀態）
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
            System.out.println(" 菜品 " + itemCode + " 部分撤銷，新狀態: " + newStatus);
        }

        // ===== 9. 重新計算訂單總金額 =====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // ===== 10. 檢查訂單是否為空 → 刪除空訂單 =====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
            System.out.println(" 空訂單已自動刪除: orderId=" + orderId);
        }

        System.out.println("✅ 撤銷成功 - 餐桌:" + tableNumber + ", 菜品:" + itemCode + ", 數量:" + cancelQuantity);
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

    @Transactional(readOnly = true)
    public Order findPreOrderByReservationId(String reservationId) {
        return orderMapper.findPreOrderByReservationId(reservationId);
    }


    /**
     * 🔧 根据 reservation_id 查询订单明细（预约订单专用）
     */
    @Transactional(readOnly = true)
    public List<OrderItem> loadFormalOrderItemsByReservationId(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return orderMapper.findOrderItemsByReservationId(reservationId);
    }


    @Transactional
    public void updateReservationOrderItemPrepared(String reservationId, String itemCode, int preparedQty, String newStatus) {
        if (reservationId == null || reservationId.isEmpty()) throw new IllegalArgumentException("预约号不能为空");
        if (itemCode == null || itemCode.isEmpty()) throw new IllegalArgumentException("菜品编号不能为空");
        if (preparedQty < 0) throw new IllegalArgumentException("已准备数量不能为负数");

        Order order = orderMapper.findActiveOrderByReservationId(reservationId);
        if (order == null) throw new IllegalStateException("未找到预约订单: " + reservationId);

        Integer itemId = menuItemMapper.findItemIdByCode(itemCode.toUpperCase());
        if (itemId == null) throw new IllegalStateException("菜品不存在: " + itemCode);

        OrderItemServingStatus current = orderItemMapper.getServingStatus(order.getOrderId(), itemId);
        if (current == null) throw new IllegalStateException("订单中找不到菜品: " + itemCode);
        if (preparedQty > current.getQuantity()) throw new IllegalArgumentException("已准备数量不能超过总数量");

        orderItemMapper.updateServedQuantityAndStatus(order.getOrderId(), itemId, preparedQty, newStatus);
        System.out.println(" 菜品准备进度已更新: " + itemCode + " → " + preparedQty + "/" + current.getQuantity() + " (" + newStatus + ")");
    }

    @Transactional(readOnly = true)
    public int getOrderItemTotalQuantity(String reservationId, String itemCode) {
        Order order = orderMapper.findActiveOrderByReservationId(reservationId);
        if (order == null) return 0;
        Integer itemId = menuItemMapper.findItemIdByCode(itemCode.toUpperCase());
        if (itemId == null) return 0;
        OrderItemServingStatus status = orderItemMapper.getServingStatus(order.getOrderId(), itemId);
        return status != null ? status.getQuantity() : 0;
    }

    /**
     * 🔧 撤销预约订单中的菜品（通过 reservation_id）
     * @return Map{success: Boolean, needConfirm: Boolean, message: String}
     *         needConfirm=true 表示需要用户确认是否保留预约
     */
    @Transactional
    public Map<String, Object> cancelReservationOrderItem(String reservationId, int itemId, int quantity, String cancellationReason) throws SQLException {
        Map<String, Object> result = new HashMap<>();

        // 1. 基础验证
        if (reservationId == null || reservationId.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "预约号不能为空");
            return result;
        }
        if (itemId <= 0 || quantity <= 0) {
            result.put("success", false);
            result.put("message", "无效的菜品 ID 或数量");
            return result;
        }

        // 2. 通过 reservation_id 查找预点餐订单
        Order order = orderMapper.findPreOrderByReservationId(reservationId);
        if (order == null || order.getOrderId() == null) {
            result.put("success", false);
            result.put("message", "预约订单不存在: " + reservationId);
            return result;
        }
        Integer orderId = order.getOrderId();

        // 3. 查询当前菜品状态
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            result.put("success", false);
            result.put("message", "订单中找不到菜品: " + itemId);
            return result;
        }

        // 4. 计算新数量和状态
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - quantity);
        int newServedQty = Math.min(servedQty, newQty);

        // 🔧【核心修复】检查是否为最后一个菜品
        boolean isLastItem = false;
        if (newQty == 0) {
            // 查询订单中是否还有其他菜品
            List<OrderItem> remainingItems = orderMapper.findOrderItemsByReservationId(reservationId);
            if (remainingItems != null) {
                // 过滤掉当前正在删除的菜品
                long otherItemCount = remainingItems.stream()
                        .filter(item -> item.getItemId() != itemId)
                        .count();
                isLastItem = (otherItemCount == 0);
            }
        }

        // 5. 执行撤销
        if (newQty == 0) {
            // ── 完全撤销：删除明细 ──
            String itemCode = orderItemMapper.getItemCodeByItemId(itemId);

            // 🔧 只有已上桌的菜品才记录撤销审计
            if ("SERVED".equals(currentStatus) || "PARTIALLY_SERVED".equals(currentStatus)) {
                orderItemMapper.recordCancellation(itemCode, quantity,
                        cancellationReason != null ? cancellationReason : "用户撤销",
                        String.valueOf(currentStatus));
                System.out.println("📝 已记录撤销审计：菜品 " + itemCode + " 状态=" + currentStatus);
            } else {
                System.out.println("🗑️ 准备中/预约订单菜品直接删除，不记录审计：菜品 " + itemCode);
            }

            // 删除订单项
            orderItemMapper.deleteOrderItem(orderId, itemId);
            System.out.println("🗑️ 菜品 " + itemCode + " 已完全撤销并删除");

        } else {
            // ── 🔧【核心修复】部分撤销：必须调用 Mapper 更新数据库！─
            String itemCode = orderItemMapper.getItemCodeByItemId(itemId);

            // 计算撤销后的新状态（预约订单专用逻辑）
            String newStatus = calculateCancelledStatus(
                    servedQty,              // 已上桌数量
                    totalQty,               // 原数量
                    quantity,               // 撤销数量
                    "UNSERVED",             // 预约订单默认状态
                    true,                   // isReservationOrder = true
                    false                   // isReservationSeated = false（预点餐阶段客人未入座）
            );

            // 🔧【关键】执行数据库更新：数量 + 已上桌数 + 状态
            orderItemMapper.updateOrderItemAfterCancel(
                    orderId,
                    itemId,
                    newQty,
                    newServedQty,
                    newStatus
            );

            System.out.println("✅ 菜品部分撤销成功 -> " + itemCode +
                    " 原:" + totalQty + " → 新:" + newQty +
                    " (状态:" + newStatus + ")");
        }

        // 6. 重新计算订单总金额
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // 🔧【核心修复】如果是最后一个菜品，返回需要确认的标志
        if (isLastItem) {
            result.put("success", true);
            result.put("needConfirm", true);  // 需要用户确认
            result.put("orderId", orderId);
            result.put("reservationId", reservationId);
            result.put("message", "这是最后一个菜品，是否保留预约订单？");
            return result;
        }

        // 7. 检查订单是否为空 → 删除空订单
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
        }

        result.put("success", true);
        result.put("needConfirm", false);
        result.put("message", "预约订单撤销成功");
        return result;
    }

    /**
     * 🔧 确认删除预约订单（用户选择"否"时调用）
     */
    @Transactional
    public Map<String, Object> confirmDeleteReservationOrder(String reservationId, Integer orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 🔧【核心修复】先更新 total_amount 为 0（在删除前）
            orderItemMapper.updateOrderTotalAmount(orderId, 0.0);
            System.out.println(" 已将订单总金额更新为 0: orderId=" + orderId);

            // 1. 删除订单明细
            orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // 2. 删除订单主表
            orderMapper.deleteOrder(orderId);

            // 3. 🔧【核心】更新 table_reservations 的 pre_order 从 1 改为 0
            int updated = reservationMapper.updatePreOrderFlag(reservationId, false);

            result.put("success", true);
            result.put("message", "预约订单已删除，预约记录已更新");
            System.out.println(" 预约订单已删除: orderId=" + orderId +
                    ", reservationId=" + reservationId +
                    ", pre_order updated=" + (updated > 0));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除预约订单失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 🔧 保留預約訂單（用戶選擇"是"時調用）
     * 刪除所有菜品後，將訂單狀態改為 NO_ORDER，pre_order 保持為 1
     */
    @Transactional
    public Map<String, Object> confirmKeepReservationOrder(String reservationId, Integer orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 🔧【核心修復 1】先確認訂單確實沒有剩餘菜品（防禦性檢查）
            boolean hasItems = orderItemMapper.hasRemainingItems(orderId);
            if (hasItems) {
                System.out.println(" 訂單 " + orderId + " 仍有菜品，跳過狀態更新");
            } else {
                // 🔧【核心修復 2】將訂單狀態從 ORDERED → NO_ORDER
                int updated = orderMapper.updateOrderStatusOnly(orderId, "NO_ORDER", "ORDERED");
                if (updated > 0) {
                    System.out.println(" 訂單狀態已更新: orderId=" + orderId +
                            ", ORDERED → NO_ORDER");
                } else {
                    // 可能狀態已不是 ORDERED，記錄日誌但不拋異常
                    System.out.println(" 訂單 " + orderId + " 狀態可能已變更，跳過更新");
                }
            }

            // 🔧【核心修復 3】可選：將金額也清零（保持一致性）
            orderItemMapper.updateOrderTotalAmount(orderId, 0.0);

            result.put("success", true);
            result.put("message", "預約訂單已保留，可以繼續點餐");
            System.out.println(" 預約訂單已保留: orderId=" + orderId +
                    ", reservationId=" + reservationId +
                    ", status=NO_ORDER");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保留預約訂單失敗: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 🔧 撤銷預約訂單中的菜品（支持確認邏輯）
     * @return Map{success: Boolean, needConfirm: Boolean, orderId: Integer, reservationId: String, message: String}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReservationOrderItemWithConfirm(
            String tableNumber, String itemCode, int cancelQuantity,
            String cancellationReason, String reservationId) throws SQLException {

        Map<String, Object> result = new HashMap<>();

        // ===== 1. 基礎驗證 =====
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "餐桌號不能為空");
            return result;
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "菜品編號不能為空");
            return result;
        }
        if (cancelQuantity <= 0) {
            result.put("success", false);
            result.put("message", "撤銷數量必須大於 0");
            return result;
        }

        // ===== 2. 獲取餐桌 → 餐桌 ID =====
        Tables table = tablesMapper.findByDisplayId(tableNumber.trim());
        if (table == null) {
            result.put("success", false);
            result.put("message", "餐桌不存在: " + tableNumber);
            return result;
        }
        int tableId = table.getTableId();

        // ===== 3. 獲取活躍訂單（通過 reservation_id 查詢）=====
        Order order = orderMapper.findActiveOrderByReservationId(reservationId);
        if (order == null || order.getOrderId() == null) {
            result.put("success", false);
            result.put("message", "未找到預約訂單: " + reservationId);
            return result;
        }
        Integer orderId = order.getOrderId();

        // ===== 4. 獲取菜品 ID =====
        Integer itemId = menuItemMapper.findItemIdByCode(itemCode.trim().toUpperCase());
        if (itemId == null) {
            result.put("success", false);
            result.put("message", "菜品 " + itemCode + " 不存在");
            return result;
        }

        // ===== 5. 查詢當前菜品狀態 =====
        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            result.put("success", false);
            result.put("message", "訂單中找不到菜品: " + itemCode);
            return result;
        }

        // 🔧【關鍵修復】額外查詢菜品狀態字符串（OrderItemServingStatus 不包含 status）
        String currentStatusStr = orderItemMapper.getItemStatus(orderId, itemId);

        // ===== 6. 計算新數量和狀態 =====
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - cancelQuantity);
        int newServedQty = Math.min(servedQty, newQty);

        // ===== 7. 執行撤銷操作 =====
        if (newQty == 0) {
            // 🔧 完全撤銷：記錄審計 + 刪除明細
            // 只有已上桌的菜品才記錄審計
            if ("SERVED".equals(currentStatusStr) || "PARTIALLY_SERVED".equals(currentStatusStr)) {
                orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                        cancellationReason != null ? cancellationReason : "用戶撤銷",
                        currentStatusStr);  // 🔧 使用 currentStatusStr 而非 currentStatus.getStatus()
                System.out.println("📝 已記錄撤銷審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            } else {
                // 準備中的菜品直接刪除，不記錄審計
                System.out.println("🗑️ 準備中的菜品直接刪除，不記錄審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            }
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            // 🔧 部分撤銷：更新數量和狀態（使用 calculateCancelledStatus）
            String newStatus = calculateCancelledStatus(
                    servedQty, totalQty, cancelQuantity, currentStatusStr,  // 🔧 使用 currentStatusStr
                    "RESERVATION".equals(order.getOrderType()),
                    table.getCurrentReservationId() != null
            );
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
        }

        // ===== 8. 重新計算訂單總金額 =====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // ===== 9. 🔧【核心】檢查是否為預約訂單的最後一個菜品 =====
        boolean isLastItem = false;
        if (newQty == 0) {
            List<OrderItem> remainingItems = orderMapper.findOrderItemsByOrderId(orderId);
            if (remainingItems != null) {
                long otherItemCount = remainingItems.stream()
                        .filter(item -> !item.getItemCode().equalsIgnoreCase(itemCode))
                        .count();
                isLastItem = (otherItemCount == 0);
            }
        }

        // ===== 10. 如果需要確認，返回標誌 =====
        if (isLastItem) {
            result.put("success", true);
            result.put("needConfirm", true);      // 🔧 關鍵：需要用戶確認
            result.put("orderId", orderId);
            result.put("reservationId", reservationId);
            result.put("currentOrderStatus", order.getStatus());  // 傳遞當前狀態
            result.put("message", "這是預約訂單的最後一個菜品，是否保留預約？");
            return result;
        }

        // ===== 11. 檢查訂單是否為空 → 刪除空訂單 =====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
            System.out.println(" 空訂單已自動刪除: orderId=" + orderId);
        }

        result.put("success", true);
        result.put("needConfirm", false);
        result.put("message", "撤銷成功");
        return result;
    }
}