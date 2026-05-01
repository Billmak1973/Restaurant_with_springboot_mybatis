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

        // 🔧【核心修改】检查是否为聚餐桌，如果是则获取所有关联桌号
        Tables table = tablesMapper.findByDisplayId(tableDisplayId.trim());
        if (table == null) {
            return Collections.emptyList();
        }

        List<String> tableDisplayIds = new ArrayList<>();

        // 判断是否为聚餐桌
        if (table.getTableType() == Tables.TableType.GROUPED &&
                table.getGroupWith() != null &&
                !table.getGroupWith().isEmpty()) {
            // 解析 group_with 字段（格式："16,17,18"）
            String[] groupIds = table.getGroupWith().split(",");
            for (String id : groupIds) {
                tableDisplayIds.add(id.trim());
            }
            // System.out.println("🔧 聚餐桌查询订单: " + String.join(",", tableDisplayIds));
        } else {
            // 普通餐桌，只查询当前桌
            tableDisplayIds.add(tableDisplayId.trim());
        }

        // 调用 Mapper 查询所有关联餐桌的订单
        return orderMapper.findOrderItemsByTableDisplayIds(tableDisplayIds);
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

        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod, orderType.getDbOrderType());
        return next != null ? next : 1;
    }


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
     * 🔧 根据 reservation_id 查找活跃订单（预约订单专用）
     *
     * @param reservationId 预约号
     * @return 订单对象，不存在返回 null
     */
    @Transactional(readOnly = true)
    public Order findActiveOrderByReservationId(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return null;
        }
        return orderMapper.findActiveOrderByReservationId(reservationId.trim());
    }

    /**
     * 🔧【核心修复】根据 served_quantity 和 total_quantity 计算合并后的正确状态
     * 4种情况：
     * 1. 预约订单（客人未入座）→ PREPARING/PREPARED
     * 2. 客人已入座 + 菜品未上桌 → PREPARING
     * 3. 客人已入座 + 菜品已上桌 → PARTIALLY_SERVED/SERVED
     * 4. 普通堂食订单 → PARTIALLY_SERVED/SERVED/UNSERVED
     *
     * @param servedQty           已上桌/已准备数量
     * @param originalQty         原订单总数量
     * @param newQty              本次新增数量
     * @param originalStatus      数据库当前状态
     * @param isReservationOrder  是否为预约订单（order_type='RESERVATION'）
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


    @Transactional  // 声明事务：保证操作的原子性，失败自动回滚
    public void mergeOrderItems(Integer orderId,      // 参数1：订单主键ID
                                Map<String, Integer> newItemsMap) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单 ID 无效");
        }
        if (newItemsMap == null || newItemsMap.isEmpty()) {
            return;
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤2】查询订单实体
        // ─────────────────────────────────────────────────────────────
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在：" + orderId);
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤3】预点餐订单状态升级（NO_ORDER → ORDERED）
        // ─────────────────────────────────────────────────────────────
        if ("RESERVATION".equals(order.getOrderType()) &&
                "NO_ORDER".equals(order.getStatus()) &&
                !newItemsMap.isEmpty()) {
            int updated = orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");
            if (updated > 0) {
                System.out.println(" [状态升级] 预点餐订单：orderId=" + orderId +
                        ", reservationId=" + order.getReservationId() +
                        ", NO_ORDER → ORDERED");
            } else {
                System.out.println("[状态升级] 订单 " + orderId + " 可能已被其他请求升级，跳过");
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤4】判断订单类型和餐桌类型（核心分支逻辑）
        // ─────────────────────────────────────────────────────────────
        boolean isReservationOrder = "RESERVATION".equals(order.getOrderType());
        boolean isReservationSeated = false;
        boolean isGroupedTable = false;  // 🔧 新增：标记是否为聚餐桌

        if (order.getTableId() != null) {
            Tables table = tablesMapper.findById(order.getTableId());
            if (table != null) {
                if (table.getCurrentReservationId() != null && !table.getCurrentReservationId().isEmpty()) {
                    isReservationSeated = true;
                    System.out.println("检测到预约入座订单：orderId=" + orderId +
                            ", reservationId=" + table.getCurrentReservationId());
                }
                // 🔧【核心修复】判断是否为聚餐桌
                if (table.getTableType() == Tables.TableType.GROUPED) {
                    isGroupedTable = true;
                    System.out.println("🔧 检测到聚餐桌订单：orderId=" + orderId +
                            ", tableType=GROUPED, groupWith=" + table.getGroupWith());
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤5】获取订单现有明细（构建匹配映射表）
        // ─────────────────────────────────────────────────────────────
        List<Map<String, Object>> rawList = orderItemMapper.getExistingItemQuantitiesRaw(orderId, null);

        Map<String, Integer> existingItems = new HashMap<>();
        Map<String, Integer> existingServedQty = new HashMap<>();
        Map<String, String> existingItemStatus = new HashMap<>();
        // 🔧 新增：创建映射表：复合键 → 原distribution
        Map<String, String> existingQuantityDistribution = new HashMap<>();

        // 🔧【新增】记录已有上桌记录的复合键（聚餐桌一键点餐专用）
        Set<String> itemsWithServedRecords = new HashSet<>();

        for (Map<String, Object> row : rawList) {
            String code = ((String) row.get("itemCode")).trim().toUpperCase();
            Integer qty = (Integer) row.get("quantity");
            Integer served = (Integer) row.get("servedQuantity");
            String status = (String) row.get("status");
            // 🔧 提取原distribution
            String quantityDist = (String) row.get("quantityDistribution");

            // 🔧【关键修复】提取分配的餐桌显示ID
            String assignedTableId = (String) row.get("assignedTableDisplayId");

            // 🔧【核心修复】根据餐桌类型构建复合键
            String key = code;
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                String sortedTableIds = sortTableIds(assignedTableId);
                key = code + "|" + sortedTableIds;
                System.out.println(" 聚餐桌数据库侧复合键：" + key);
            }

            existingItems.put(key, qty);
            existingServedQty.put(key, served != null ? served : 0);
            existingItemStatus.put(key, status);
            // 🔧 记录原distribution
            existingQuantityDistribution.put(key, quantityDist);

            // 🔧 记录已有上桌记录的菜品
            if (served != null && served > 0) {
                itemsWithServedRecords.add(key);
                System.out.println(" 菜品已有上桌记录：key=" + key + ", served=" + served);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤6】分类新菜品：更新 vs 新增
        // ─────────────────────────────────────────────────────────────
        List<OrderItem> itemsToUpdate = new ArrayList<>();
        List<OrderItem> itemsToInsert = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : newItemsMap.entrySet()) {
            String itemKey = entry.getKey().trim().toUpperCase();
            int newQty = entry.getValue();

            // 🔧 解析特殊 Key 格式：B1[BATCH:13,14,15]
            String pureItemCode = itemKey;
            String assignedTableId = null;
            boolean isBatchOrder = false;

            if (itemKey.contains("[BATCH:")) {
                isBatchOrder = true;
                int batchStart = itemKey.indexOf("[BATCH:");
                pureItemCode = itemKey.substring(0, batchStart);
                assignedTableId = itemKey.substring(batchStart + 7, itemKey.length() - 1);
                System.out.println("🔧 解析一键点餐：pureItemCode=" + pureItemCode +
                        ", assignedTableId=" + assignedTableId);
            }

            MenuItem menuItem = menuItemMapper.findById(pureItemCode);
            if (menuItem == null || !menuItem.isActive()) {
                throw new RuntimeException("菜品不存在或已售罄：" + pureItemCode);
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setItemCode(pureItemCode);
            orderItem.setQuantity(newQty);
            orderItem.setPriceAtOrder(menuItem.getPrice());

            // 🔧 标准化 assigned_table_display_id
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                orderItem.setAssignedTableDisplayId(sortTableIds(assignedTableId));
            } else {
                orderItem.setAssignedTableDisplayId(null);
            }

            // 🔧 构建复合键
            String key = pureItemCode;
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                String sortedTableIds = sortTableIds(assignedTableId);
                key = pureItemCode + "|" + sortedTableIds;
                System.out.println("🔧 使用复合键：" + key);
            }

            // 🔧【核心修复】根据餐桌类型 + 上桌记录决定合并/插入
            boolean hasServedRecord = itemsWithServedRecords.contains(key);

            if (isGroupedTable && hasServedRecord) {
                itemsToInsert.add(orderItem);
                System.out.println("🔧 聚餐桌一键点餐：菜品 " + pureItemCode +
                        " 已有上桌记录，创建新记录：key=" + key);
            } else if (existingItems.containsKey(key)) {
                itemsToUpdate.add(orderItem);
                System.out.println("🔧 找到匹配项，准备合并更新：key=" + key +
                        ", 原数量=" + existingItems.get(key) + ", 新增=" + newQty);
            } else {
                itemsToInsert.add(orderItem);
                System.out.println("🔧 未找到匹配项，准备插入：key=" + key);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤7】🔧【核心修正】处理待更新项（计算状态 + 生成distribution + 分组更新）
        // ─────────────────────────────────────────────────────────────
        if (!itemsToUpdate.isEmpty()) {
            List<OrderItem> servedGroup = new ArrayList<>();
            List<OrderItem> unservedGroup = new ArrayList<>();

            for (OrderItem item : itemsToUpdate) {
                String itemCode = item.getItemCode();
                int newQty = item.getQuantity();

                // 🔧 构建 lookupKey
                String lookupKey = itemCode;
                String itemAssignedTableId = item.getAssignedTableDisplayId();
                if (itemAssignedTableId != null && !itemAssignedTableId.isEmpty()) {
                    String sortedTableIds = sortTableIds(itemAssignedTableId);
                    lookupKey = itemCode + "|" + sortedTableIds;
                }

                // 🔧 获取原有数据
                Integer originalQty = existingItems.get(lookupKey);
                Integer originalServed = existingServedQty.getOrDefault(lookupKey, 0);
                String originalStatus = existingItemStatus.get(lookupKey);
                String originalDistribution = existingQuantityDistribution.get(lookupKey);

                // 🔧 计算合并后的状态
                String newStatus = calculateMergedStatus(
                        originalServed, originalQty, newQty,
                        originalStatus, isReservationOrder, isReservationSeated
                );

                // 🔧【核心新增】为聚餐桌生成/更新 quantity_distribution
                if (isGroupedTable && itemAssignedTableId != null && !itemAssignedTableId.isEmpty()) {
                    int totalQuantity = originalQty + newQty;  // 合并后的总数量
                    String newDistribution = generateQuantityDistribution(
                            itemCode,
                            originalQty,              // 原数量
                            totalQuantity,            // 合并后总数量
                            itemAssignedTableId,      // 分配的餐桌显示ID
                            true,                     // isMerge: 是合并操作
                            originalDistribution      // 原distribution
                    );
                    item.setQuantityDistribution(newDistribution);  // 🔧 设置到订单项
                    System.out.println("🔧 更新菜品 distribution: " + itemCode +
                            " → " + newDistribution);
                }

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

                item.setStatus(newStatus);
            }

            // 🔹 已上桌的菜品 → 普通堂食逻辑
            if (!servedGroup.isEmpty()) {
                System.out.println(" 已上桌菜品 → 普通逻辑，数量：" + servedGroup.size());
                orderItemMapper.updateExistingOrderItems(orderId, servedGroup);
            }
            // 🔹 未上桌的菜品 → 预约逻辑
            if (!unservedGroup.isEmpty()) {
                System.out.println(" 未上桌菜品 → 预约逻辑，数量：" + unservedGroup.size());
                orderItemMapper.updateExistingOrderItemsForReservation(orderId, unservedGroup);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤8】处理待新增项（设置初始状态 + 生成distribution）
        // ─────────────────────────────────────────────────────────────
        if (!itemsToInsert.isEmpty()) {
            String initialStatus = "UNSERVED";

            for (OrderItem item : itemsToInsert) {
                if (isGroupedTable &&
                        item.getAssignedTableDisplayId() != null &&
                        !item.getAssignedTableDisplayId().isEmpty()) {

                    String distribution = generateQuantityDistribution(
                            item.getItemCode(),
                            0,                           // originalQty: 新菜品=0
                            item.getQuantity(),          // totalQuantity: 本次新增总数量
                            item.getAssignedTableDisplayId(),
                            false,                       // isMerge: 非合并，是新增
                            null                         // existingDistribution: 新菜品无现有值
                    );
                    item.setQuantityDistribution(distribution);
                    System.out.println("🔧 新菜品生成 distribution: " + distribution);
                }
            }

            System.out.println(" 新菜品初始状态：" + initialStatus + ", 数量：" + itemsToInsert.size());
            orderItemMapper.insertNewOrderItemsWithStatus(orderId, itemsToInsert, initialStatus);
        }

        // ─────────────────────────────────────────────────────────────
        // 【步骤9】重算订单金额（预约订单无配送费）
        // ─────────────────────────────────────────────────────────────
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
            System.out.println(" 预约订单金额已更新：orderId=" + orderId +
                    ", items_total=" + newTotal +
                    ", total_amount=" + newTotal);
        }

        System.out.println(" 订单合并完成：orderId=" + orderId +
                ", 更新=" + itemsToUpdate.size() +
                ", 新增=" + itemsToInsert.size() +
                ", isReservationOrder=" + isReservationOrder +
                ", isReservationSeated=" + isReservationSeated +
                ", isGroupedTable=" + isGroupedTable);
    }




    /**
     * 🔧 辅助方法：排序餐桌ID（确保 "14,13,15" = "13,14,15"）
     */
    private String sortTableIds(String tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return tableIds;
        }

        String[] ids = tableIds.split(",");
        Arrays.sort(ids, (a, b) -> {
            // 提取数字部分排序
            int numA = Integer.parseInt(a.replaceAll("[^0-9]", ""));
            int numB = Integer.parseInt(b.replaceAll("[^0-9]", ""));
            return Integer.compare(numA, numB);
        });

        return String.join(",", ids);
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
        // ===== 1-3. 基础验证 + 获取餐桌（保持不变）=====
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber.trim())) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }
        if (itemId <= 0) throw new IllegalArgumentException("无效的菜品 ID");
        if (quantity <= 0) throw new IllegalArgumentException("数量必须大于 0");

        Tables table = tablesMapper.findByDisplayId(tableNumber.trim());
        if (table == null) throw new IllegalStateException("餐桌不存在：" + tableNumber);

        boolean isGroupedTable = (table.getTableType() == Tables.TableType.GROUPED);

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修改】聚餐桌：区分【单桌分配】与【多桌共享】两种模式
        // ═══════════════════════════════════════════════════════════
        OrderItem targetItem;

        if (isGroupedTable) {
            // ── 尝试 1：先查多桌共享模糊匹配（新逻辑优先）──
            targetItem = orderItemMapper.findSharedOrderItemByFuzzyTableId(itemId, tableNumber.trim());

            // ── 尝试 2：若未找到，再查单桌精确匹配（原有逻辑兜底）──
            if (targetItem == null) {
                targetItem = orderItemMapper.findOrderItemByAssignedTableAndItemId(itemId, tableNumber.trim());
            }

            if (targetItem == null) {
                throw new IllegalStateException(
                        "聚餐桌 #" + tableNumber + " 未找到可上桌菜品（itemId=" + itemId + "）"
                );
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心分支】根据 assigned_table_display_id 判断菜品类型
            // ═══════════════════════════════════════════════════════════
            String assignedId = targetItem.getAssignedTableDisplayId();
            boolean isSharedDish = (assignedId != null && assignedId.contains(","));

            if (isSharedDish) {
                // ──【多桌共享菜品】处理逻辑 ──
                handleSharedDishServing(targetItem, tableNumber, quantity);
            } else {
                // ──【单桌分配菜品】处理逻辑（跳过 prepared_quantity 校验）──
                handleSingleTableDishServing(targetItem, quantity);
            }

        } else {
            // ── 普通桌逻辑（完全保留，逻辑不变）──
            int tableId = table.getTableId();
            Integer orderId = orderMapper.findActiveOrderIdByTableId(tableId);
            if (orderId == null) throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");

            List<OrderItem> allItems = orderMapper.findOrderItemsByOrderId(orderId);
            targetItem = null;
            for (OrderItem item : allItems) {
                if (item.getItemId() != itemId) continue;
                String assignedId = item.getAssignedTableDisplayId();
                if (assignedId == null || assignedId.isEmpty() || assignedId.trim().equals(tableNumber.trim())) {
                    targetItem = item;
                    break;
                }
            }
            if (targetItem == null) {
                throw new IllegalStateException("未找到可上桌菜品（table=" + tableNumber + ", itemId=" + itemId + "）");
            }

            // ===== 普通桌数量校验（保持不变）=====
            int total = targetItem.getQuantity();
            int served = targetItem.getServedQuantity();
            String currentStatus = targetItem.getStatus();
            if ("SERVED".equals(currentStatus)) throw new IllegalStateException("该菜已全部上桌");

            int newServed = Math.min(served + quantity, total);
            String status = (newServed == 0) ? "UNSERVED" : (newServed < total ? "PARTIALLY_SERVED" : "SERVED");

            orderItemMapper.updateServedById(targetItem.getOrderItemId(), newServed, status);

            // 普通桌需检查订单完成状态
            boolean allServed = !orderItemMapper.hasUnservedItems(targetItem.getOrderId());
            if (allServed) table.setOrderStatus(Tables.OrderStatus.ORDERED_FINISHED);
        }

        System.out.println(" 上桌成功 table=" + tableNumber +
                ", item=" + targetItem.getItemCode() +
                (isGroupedTable ? " [聚餐桌模式]" : ""));
    }

    /**
     * 🔧 处理【多桌共享菜品】上桌逻辑（聚餐桌专用）
     * <p>
     * 📋 核心规则：
     * 1. 共享菜品必须按桌号顺序上桌，不能跳过中间桌号
     * 2. 允许"回退"上桌（如 18→17），但前提是当前桌号必须与已上桌桌号相邻
     * 3. 禁止"跳跃"上桌（如 18→16，跳过了 17）
     * 4. 🔧【新增】检查 quantity_distribution：null 或均匀时按整除计算，不均匀时跳过
     *
     * @param item               订单项对象
     * @param currentTableNumber 当前操作的餐桌号（如 "16"）
     * @param inputQuantity      用户输入的上桌数量
     * @throws SQLException 数据库操作异常
     */
    private void handleSharedDishServing(OrderItem item, String currentTableNumber, int inputQuantity) throws SQLException {
        // ═══════════════════════════════════════════════════════════
        // 【步骤 0】🔧 检查菜品状态：如果已全部上桌则直接跳过
        // ══════════════════════════════════════════════════════════

        if ("SERVED".equals(item.getStatus())) {
            return;  // 🔑 关键：直接返回，不执行任何后续逻辑
        }
        // ═══════════════════════════════════════════════════════════
        // 【步骤 1】基础验证：检查 assigned_table_display_id 是否为空
        // ═══════════════════════════════════════════════════════════
        String assignedId = item.getAssignedTableDisplayId();
        if (assignedId == null || assignedId.isEmpty()) {
            throw new IllegalStateException("共享菜品 assigned_table_display_id 不能为空");
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 2】解析聚餐桌号列表（格式："15,16,17" → 排序后 [15,16,17]）
        // ═══════════════════════════════════════════════════════════
        String[] tableIds = assignedId.split(",");
        List<String> sortedTableIds = Arrays.stream(tableIds)
                .map(String::trim)
                .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^0-9]", ""))))
                .collect(Collectors.toList());

        int tableCount = sortedTableIds.size();
        if (tableCount < 2) {
            throw new IllegalStateException("共享菜品必须分配给至少 2 张餐桌");
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 3】获取已上桌的桌号列表（格式："16,18" → ["16", "18"]）
        // ═══════════════════════════════════════════════════════════
        String servedTables = item.getServedTableDisplayId();
        List<String> alreadyServedTables = (servedTables != null && !servedTables.isEmpty())
                ? Arrays.asList(servedTables.split(","))
                : Collections.emptyList();

        // ═══════════════════════════════════════════════════════════
        // 【步骤 4】🔧【核心修复】检查当前桌号是否已上桌，避免重复更新
        // ═══════════════════════════════════════════════════════════
        String currentTableTrimmed = currentTableNumber.trim();
        if (alreadyServedTables.contains(currentTableTrimmed)) {
            System.out.println("⚠️ 桌号 " + currentTableTrimmed + " 已上桌，跳过重复更新");
            return;  // 🔑 关键：直接返回，不执行后续更新逻辑
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 5】🔧【核心规则】验证上桌顺序：相邻性校验（禁止任何"跳过"行为）
        // ═══════════════════════════════════════════════════════════
        int currentNum = Integer.parseInt(currentTableTrimmed.replaceAll("[^0-9]", ""));

        if (!alreadyServedTables.isEmpty()) {
            // 将已上桌桌号转换为数字列表，用于相邻性计算
            List<Integer> servedNums = alreadyServedTables.stream()
                    .map(s -> Integer.parseInt(s.replaceAll("[^0-9]", "")))
                    .collect(Collectors.toList());

            // 🔧【核心】检查当前桌号是否与任意已上桌桌号相邻（差值=1）
            boolean hasAdjacentServed = false;
            for (Integer servedNum : servedNums) {
                if (Math.abs(currentNum - servedNum) == 1) {
                    hasAdjacentServed = true;
                    break;
                }
            }

            if (!hasAdjacentServed) {
                // ❌ 不满足相邻条件，说明跳过了中间的桌号
                throw new IllegalStateException(
                        "聚餐桌一键上桌不能跳过桌号！\n" +
                                "当前桌号：" + currentTableNumber + "\n" +
                                "已上桌桌号：" + String.join(",", alreadyServedTables) + "\n\n" +
                                "💡 提示：请按桌号顺序上桌，相邻桌号才能操作");
            }

            // ✅ 相邻验证通过，记录日志
            System.out.println("ℹ️ 桌号 " + currentTableNumber +
                    " 与已上桌桌号相邻，允许上桌");
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 6】🔧【核心修改】检查 quantity_distribution 并计算每桌份额
        // ═══════════════════════════════════════════════════════════
        int totalQuantity = item.getQuantity();
        int perTableQuantity;  // 每桌应上桌的份额

        String quantityDistribution = item.getQuantityDistribution();
            // 🔧【DEBUG 1】打印原始 distribution 值
//        System.out.println("🔍 [DEBUG] itemCode=" + item.getItemCode() +
//                ", quantityDistribution=[" + quantityDistribution + "]");

        if (quantityDistribution == null || quantityDistribution.isEmpty()) {
            // ── 情况 1：distribution 为 null/空 → 使用【无余数整除】计算
            if (totalQuantity % tableCount != 0) {
                throw new IllegalStateException(
                        "共享菜品总数量 (" + totalQuantity + ") 不能被桌子数量 (" + tableCount + ") 整除！");
            }
            perTableQuantity = totalQuantity / tableCount;
            System.out.println("🔧 distribution 为空，使用整除计算：每桌 " + perTableQuantity + " 份");
        } else {
            // ── 情况 2：distribution 不为空 → 解析并检查均匀性
            Map<String, Integer> distributionMap = parseDistribution(quantityDistribution);

            if (distributionMap == null || distributionMap.isEmpty()) {
                // 解析失败，兜底使用整除计算
                if (totalQuantity % tableCount != 0) {
                    throw new IllegalStateException(
                            "共享菜品总数量 (" + totalQuantity + ") 不能被桌子数量 (" + tableCount + ") 整除！");
                }
                perTableQuantity = totalQuantity / tableCount;
                System.out.println("🔧 distribution 解析失败，兜底使用整除计算：每桌 " + perTableQuantity + " 份");
            } else {
                // 🔧 检查 distribution 中的数量是否一致（均匀性校验）
                boolean isUniform = true;
                Integer firstQty = null;

                for (Integer qty : distributionMap.values()) {
                    if (firstQty == null) {
                        firstQty = qty;
                    } else if (!qty.equals(firstQty)) {
                        isUniform = false;
                        break;
                    }
                }

                if (isUniform) {
                    // ✅ 数量一致 → 依旧使用【无余数整除】计算
                    if (totalQuantity % tableCount != 0) {
                        throw new IllegalStateException(
                                "共享菜品总数量 (" + totalQuantity + ") 不能被桌子数量 (" + tableCount + ") 整除！");
                    }
                    perTableQuantity = totalQuantity / tableCount;
                    System.out.println("🔧 distribution 均匀，使用整除计算：每桌 " + perTableQuantity + " 份");
                } else {
                    // 🔧【关键修改】抛出带关键词的异常，供上层识别
                    throw new IllegalArgumentException(
                            "分配数量不均匀：" + item.getItemCode() +
                                    " 在各桌分配不一致: " + quantityDistribution);  // 🔑 包含"分配数量不均匀"关键词
                }
            }
        }

        // 🔧 校验：输入数量必须等于每桌份额（自动修正，不报错）
        if (inputQuantity != perTableQuantity) {
            System.out.println("⚠️ [自动修正] 共享菜品数量：传入=" + inputQuantity +
                    " -> 实际执行每桌份额=" + perTableQuantity +
                    " (总" + totalQuantity + "份 ÷ " + tableCount + "桌)");
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 7】更新 served_quantity 和 served_table_display_id
        // ═══════════════════════════════════════════════════════════
        int newServedQuantity = item.getServedQuantity() + perTableQuantity;
        String newServedTables = (servedTables == null || servedTables.isEmpty())
                ? currentTableTrimmed
                : servedTables + "," + currentTableTrimmed;

        String newStatus = (newServedQuantity >= totalQuantity) ? "SERVED" : "PARTIALLY_SERVED";

        // 🔧 调用新 Mapper 方法，同时更新 served_quantity + served_table_display_id + status
        orderItemMapper.updateServedWithTableInfo(
                item.getOrderItemId(),
                newServedQuantity,
                newStatus,
                newServedTables
        );

        System.out.println(" 共享菜品上桌成功：" + item.getItemCode() +
                "，桌号：" + currentTableTrimmed +
                "，本次：" + perTableQuantity + " 份，累计：" + newServedQuantity + "/" + totalQuantity);
    }

    /**
     * 🔧 辅助方法：解析 quantity_distribution JSON 字符串为 Map
     * 格式示例：{"13":4,"14":4,"15":3} → Map{"13"=4, "14"=4, "15"=3}
     *
     * @param jsonStr JSON 格式的 distribution 字符串
     * @return 解析后的 Map，解析失败返回 null
     */
    private Map<String, Integer> parseDistribution(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }

        Map<String, Integer> result = new LinkedHashMap<>();

        try {
            // 移除花括号和空格
            String content = jsonStr.replaceAll("[{}\\s]", "");
            if (content.isEmpty()) {
                return result;
            }

            // 按逗号分割键值对
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String tableId = kv[0].replaceAll("\"", "").trim();
                    int qty = Integer.parseInt(kv[1].trim());
                    result.put(tableId, qty);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("⚠️ 解析 quantity_distribution 失败: " + jsonStr + " | 错误: " + e.getMessage());
            return null;  // 解析失败返回 null，调用方兜底处理
        }
    }

    /**
     * 🔧 处理【单桌分配菜品】上桌逻辑（聚餐桌专用）
     * 规则：跳过 prepared_quantity 校验，直接累加 served_quantity
     */
    private void handleSingleTableDishServing(OrderItem item, int inputQuantity) {
        int total = item.getQuantity();
        int served = item.getServedQuantity();
        String currentStatus = item.getStatus();

        if ("SERVED".equals(currentStatus)) {
            throw new IllegalStateException("该菜已全部上桌");
        }

        // 🔧 核心差异：不与 prepared_quantity 对比，直接累加
        int newServed = Math.min(served + inputQuantity, total);
        String newStatus = (newServed == 0) ? "UNSERVED" : (newServed < total ? "PARTIALLY_SERVED" : "SERVED");

        orderItemMapper.updateServedById(item.getOrderItemId(), newServed, newStatus);

        System.out.println(" 单桌菜品上桌成功：" + item.getItemCode() +
                "，数量：" + newServed + "/" + total);
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
            System.out.println(" 订单 " + orderNumber + " 所有菜品已制作完成，检查配送状态...");

            // 仅配送订单且状态为"未配送"时，自动推进到"送单中"
            if ("DELIVERY".equals(order.getDeliveryMethod()) &&
                    order.getDeliveryStatus() == Order.DeliveryStatus.NOT_DELIVERED) {

                // 自动更新配送状态
                orderMapper.updateDeliveryStatus(orderId, Order.DeliveryStatus.DELIVERING.name());
                System.out.println(" 自动更新配送状态: " + orderNumber +
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
            System.out.println(" 配送状态已更新: " + orderNumber + " [未配送 → 送单中]");
        }

        System.out.println(" 外賣訂單全部標記完成 - 訂單號: " + orderNumber +
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
     * @param servedQty           已上桌/已準備數量
     * @param originalQty         原訂單總數量
     * @param cancelQuantity      本次撤銷數量
     * @param originalStatus      數據庫當前狀態
     * @param isReservationOrder  是否為預約訂單（order_type='RESERVATION'）
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
                System.out.println(" 检测到预约入座订单: orderId=" + orderId +
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
            //  完全撤銷：刪除明細

            // 🔧【核心修复】只有已上桌的菜品才记录撤销审计
            // 准备中的菜品（PREPARING/PREPARED/UNSERVED）不需要记录审计
            if ("SERVED".equals(originalStatus) ||
                    "PARTIALLY_SERVED".equals(originalStatus)) {
                // 已上桌菜品：记录审计 + 删除
                orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                        cancellationReason != null ? cancellationReason : "用戶撤銷",
                        originalStatus);  // 使用原状态
                System.out.println(" 已记录撤销审计：菜品 " + itemCode +
                        " 状态=" + originalStatus);
            } else {
                // 未上桌菜品（PREPARING/PREPARED/UNSERVED）：直接删除，不记录
                System.out.println(" 准备中的菜品直接删除，不记录审计：菜品 " + itemCode +
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

        System.out.println(" 配送状态更新成功: " + orderNumber + " → " + newStatus.getDisplayName());
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

        System.out.println(" 配送费更新成功: orderId=" + orderId +
                ", 新配送费=" + newDeliveryFee + ", 新总金额=" + totalAmount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelGroupedTableOrderItemByOrderItemId(int orderItemId, String cancellationReason) throws SQLException {
        // ===== 1. 基础验证 =====
        if (orderItemId <= 0) {
            throw new IllegalArgumentException("订单项 ID 无效：" + orderItemId);
        }

        // ===== 2. 查询订单项（获取餐桌信息）=====
        OrderItem orderItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (orderItem == null) {
            throw new IllegalStateException("订单项不存在：" + orderItemId);
        }

        // ===== 3. 验证是否为聚餐桌订单 =====
        Order order = orderMapper.findById(orderItem.getOrderId());
        if (order == null || order.getTableId() == null) {
            throw new IllegalStateException("订单或餐桌不存在");
        }

        Tables table = tablesMapper.findById(order.getTableId());
        if (table == null || table.getTableType() != Tables.TableType.GROUPED) {
            throw new IllegalStateException("此方法仅支持聚餐桌（GROUPED）订单，当前餐桌类型：" +
                    (table != null ? table.getTableType() : "null"));
        }

        // ===== 4. 🔧【核心】执行聚餐桌专用撤销（只处理 quantity_distribution 为 null 的场景）=====
        // 如果 quantity_distribution 不为 null，说明是一键点餐菜品，不走此逻辑
        if (orderItem.getQuantityDistribution() != null) {
            throw new IllegalStateException("一键点餐菜品（有 quantity_distribution）请使用普通撤销逻辑");
        }

        // 🔧【修复】提取要撤销的桌号
        String tableDisplayIdToCancel = extractTableDisplayIdToCancel(orderItem, cancellationReason);

        // 🔧【修复】使用提取出的桌号
        int updated = orderItemMapper.cancelGroupedTableOrderItemByOrderItemId(
                orderItemId,
                tableDisplayIdToCancel,  // ← 使用提取的桌号
                cancellationReason
        );

        if (updated == 0) {
            throw new RuntimeException("聚餐桌菜品撤销失败：可能数量已为 0 或数据不匹配");
        }

        // ===== 5. 🔧【新增】记录撤销审计日志 =====
        try {
            // 计算撤销金额（撤销 1 份）
            double cancelledAmount = orderItem.getPriceAtOrder();

            // 记录到 order_cancellations 表
            orderMapper.recordCancellation(
                    "ITEM",                          // cancellationType: 菜品撤销
                    order.getOrderId(),              // orderId
                    order.getOrderNumber(),          // orderNumber
                    orderItem.getItemCode(),         // itemCode
                    1,                               // cancelledQuantity: 撤销 1 份
                    orderItem.getStatus(),           // beforeStatus: 撤销前状态
                    cancelledAmount,                 // cancelledAmount: 撤销金额
                    cancellationReason != null ? cancellationReason : "用户撤销"  // reason
            );

            System.out.println("📝 已记录撤销审计：orderItemId=#" + orderItemId +
                    ", itemCode=" + orderItem.getItemCode() +
                    ", amount=" + cancelledAmount);
        } catch (Exception e) {
            // 审计记录失败不影响主流程，但记录日志
            System.err.println("⚠️ 记录撤销审计失败：" + e.getMessage());
            e.printStackTrace();
        }

        // ===== 6. 记录日志 =====
        System.out.println("🗑️ 聚餐桌菜品撤销成功: orderItemId=#" + orderItemId +
                ", table=" + tableDisplayIdToCancel +  // ← 使用提取的桌号
                ", reason=" + (cancellationReason != null ? cancellationReason : "用户撤销"));
    }
    /**
     * 🔧 提取要撤销的桌号
     */
    private String extractTableDisplayIdToCancel(OrderItem orderItem, String cancellationReason) {
        // 如果 cancellationReason 本身就是桌号（如 "16"），直接使用
        if (cancellationReason != null && cancellationReason.matches("\\d+")) {
            return cancellationReason;
        }

        // 否则从 assigned_table_display_id 中取第一个桌号
        if (orderItem.getAssignedTableDisplayId() != null &&
                !orderItem.getAssignedTableDisplayId().isEmpty()) {
            String[] tables = orderItem.getAssignedTableDisplayId().split(",");
            if (tables.length > 0) {
                return tables[0].trim();
            }
        }

        return cancellationReason;  // 兜底
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelGroupedTableOrderItemWithDistribution(
            String tableDisplayId, int orderItemId, String cancelTableId, int cancelQuantity) {

        OrderItem item = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (item == null) throw new IllegalStateException("订单项不存在: #" + orderItemId);

        // 🔧【修复 1】解析 + 空值校验
        Map<String, Integer> distribution = parseQuantityDistribution(item.getQuantityDistribution());
        if (distribution == null || distribution.isEmpty()) {
            throw new IllegalStateException("❌ distribution 解析失败或为空！原始值: " + item.getQuantityDistribution());
        }

        Integer tableQty = distribution.get(cancelTableId);
        if (tableQty == null) {
            throw new IllegalStateException("❌ 桌号 " + cancelTableId + " 不在 distribution 中");
        }
        if (cancelQuantity > tableQty) {
            throw new IllegalArgumentException("撤销数量 " + cancelQuantity + " > 分配数量 " + tableQty);
        }

        // 🔧【修复 2】更新 distribution
        int newTableQty = tableQty - cancelQuantity;
        int newTotalQty = item.getQuantity() - cancelQuantity;

        if (newTableQty <= 0) {
            distribution.remove(cancelTableId);
            System.out.println("🗑️ 移除桌号: " + cancelTableId);
        } else {
            distribution.put(cancelTableId, newTableQty);
            System.out.println("✏️ 更新桌号 " + cancelTableId + ": " + tableQty + " → " + newTableQty);
        }

        // 🔧【修复 3】按桌号数字升序排序（关键！）
        Map<String, Integer> sortedMap = distribution.entrySet().stream()
                .sorted(Comparator.comparingInt(e ->
                        Integer.parseInt(e.getKey().replaceAll("[^0-9]", ""))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // 🔧【修复 4】生成新值（用排序后的 Map）
        String newDistributionStr = sortedMap.isEmpty() ? null : formatQuantityDistribution(sortedMap);
        String newAssignedTableIds = sortedMap.isEmpty() ? null :
                String.join(",", sortedMap.keySet());

        // 🔧【修复 5】计算新状态
        String newStatus = calculateStatusAfterCancel(newTotalQty, item.getServedQuantity());

        // 🔧【修复 6】调试日志（必须加！）
        System.out.println("🔥 DEBUG BEFORE SQL:");
        System.out.println("  orderItemId: " + orderItemId);
        System.out.println("  newTotalQty: " + newTotalQty);
        System.out.println("  newStatus: " + newStatus);
        System.out.println("  newDistribution: " + newDistributionStr);
        System.out.println("  newAssignedIds: " + newAssignedTableIds);

        // 🔧【修复 7】执行更新/删除
        if (newTotalQty <= 0) {
            orderItemMapper.deleteOrderItemByOrderItemId(orderItemId);
        } else {
            orderItemMapper.updateGroupedOrderItemWithDistribution(
                    orderItemId, newTotalQty, newStatus, newDistributionStr, newAssignedTableIds);
        }
    }

    // 辅助方法：计算撤销后状态
    private String calculateStatusAfterCancel(int newTotalQty, int servedQty) {
        if (newTotalQty <= 0) return "UNSERVED";
        if (servedQty >= newTotalQty) return "SERVED";
        if (servedQty > 0) return "PARTIALLY_SERVED";
        return "UNSERVED";
    }


    /**
     * 🔧 解析 quantity_distribution JSON 字符串
     */
    private Map<String, Integer> parseQuantityDistribution(String jsonStr) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (jsonStr == null || jsonStr.isEmpty()) return result;
        try {
            String content = jsonStr.replaceAll("[{}\\s]", "");
            if (content.isEmpty()) return result;
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String tableId = kv[0].replaceAll("\"", "").trim();
                    int qty = Integer.parseInt(kv[1].trim());
                    result.put(tableId, qty);
                }
            }
        } catch (Exception e) {
            System.err.println("解析 quantity_distribution 失败: " + jsonStr);
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 🔧 格式化 distribution 为 JSON 字符串
     */
    private String formatQuantityDistribution(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) return null;
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
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

            System.out.println(" 外卖订单结账成功：" + orderNumber +
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
     *
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
            System.out.println(" 已取消重新点餐 - 餐桌: " + tableNumber);

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


    /**
     * 🔧 更新预约订单菜品准备状态（精确到 order_item_id）
     *
     * @param reservationId          预约号
     * @param orderItemId            订单项 ID（精确标识）
     * @param itemCode               菜品编号（用于日志）
     * @param preparedQty            已准备数量
     * @param newStatus              新状态
     * @param assignedTableDisplayId 分配的餐桌显示ID
     */
    @Transactional
    public void updateReservationOrderItemPrepared(
            String reservationId,
            Integer orderItemId,        // 🔧 新增：精确标识
            String itemCode,
            int preparedQty,
            String newStatus,
            String assignedTableDisplayId) {

        // ===== 验证部分 =====
        if (reservationId == null || reservationId.isEmpty())
            throw new IllegalArgumentException("预约号不能为空");
        if (orderItemId == null || orderItemId <= 0)
            throw new IllegalArgumentException("订单项 ID 无效");
        if (itemCode == null || itemCode.isEmpty())
            throw new IllegalArgumentException("菜品编号不能为空");
        if (preparedQty < 0)
            throw new IllegalArgumentException("已准备数量不能为负数");

        // 🔧【修改】通过 orderItemId 精确查询单条记录
        OrderItem orderItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (orderItem == null)
            throw new IllegalStateException("订单项不存在: #" + orderItemId);

        // 🔧 验证：预约号匹配（安全防护）
        Order order = orderMapper.findById(orderItem.getOrderId());
        if (order == null || !reservationId.equals(order.getReservationId())) {
            throw new IllegalStateException("订单项 #" + orderItemId +
                    " 不属于预约 " + reservationId);
        }

        // 🔧【修改】验证：已准备数量不能超过该记录的总数量
        if (preparedQty > orderItem.getQuantity()) {
            throw new IllegalArgumentException(
                    "已准备数量 (" + preparedQty + ") 不能超过菜品总数量 (" +
                            orderItem.getQuantity() + ")");
        }

        // 🔧【修改】调用新方法：通过 orderItemId 精确更新
        orderItemMapper.updatePreparedQuantityById(
                orderItemId,              // 🔧 精确主键
                preparedQty,
                newStatus
        );

        System.out.println("🍳 菜品准备进度已更新: " + itemCode +
                " (orderItemId=#" + orderItemId + ") → " +
                preparedQty + "/" + orderItem.getQuantity() +
                " (" + newStatus + ")");
    }


    /**
     * 🔧 撤销预约订单中的菜品（通过 reservation_id）
     *
     * @return Map{success: Boolean, needConfirm: Boolean, message: String}
     * needConfirm=true 表示需要用户确认是否保留预约
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
                System.out.println(" 已记录撤销审计：菜品 " + itemCode + " 状态=" + currentStatus);
            } else {
                System.out.println("🗑准备中/预约订单菜品直接删除，不记录审计：菜品 " + itemCode);
            }

            // 删除订单项
            orderItemMapper.deleteOrderItem(orderId, itemId);
            System.out.println(" 菜品 " + itemCode + " 已完全撤销并删除");

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

            System.out.println(" 菜品部分撤销成功 -> " + itemCode +
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
     *
     * @return Map{success: Boolean, needConfirm: Boolean, orderId: Integer, reservationId: String, message: String}
     * 当被注解的方法在执行过程中抛出任何异常（包括受检异常和运行时异常）时，Spring 都会自动回滚当前数据库事务。
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
                System.out.println(" 已記錄撤銷審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            } else {
                // 準備中的菜品直接刪除，不記錄審計
                System.out.println(" 準備中的菜品直接刪除，不記錄審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            }
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            // 🔧 部分撤銷：更新數量和狀態（使用 calculateCancelledStatus）
            String newStatus = calculateCancelledStatus(
                    servedQty, totalQty, cancelQuantity, currentStatusStr,  //  使用 currentStatusStr
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

        // ===== 9. 【核心】檢查是否為預約訂單的最後一個菜品 =====
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
            result.put("needConfirm", true);      //  關鍵：需要用戶確認
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

    @Transactional(readOnly = true)
    public Order findOrderByTableIdAndStatus(Integer tableId, String status) {
        if (tableId == null) return null;
        return orderMapper.findOrderByTableIdAndStatus(tableId, status);
    }

    @Transactional
    public int updateOrderStatusAndTotals(Integer orderId, String status,
                                          Double itemsTotal, Double totalAmount) {
        return orderMapper.updateOrderStatusAndTotals(orderId, status, itemsTotal, totalAmount);
    }


    //  堂食订单号获取
    @Transactional(readOnly = true)
    public Integer getNextDineInOrderNumber(String dateStr) {
        // 修改前: orderMapper.getNextOrderNumber("T", dateStr, "DINE_IN");
        // 修改后：明确传入4个参数（deliveryMethod 传 null，orderType 传 "DINE_IN"）
        Integer next = orderMapper.getNextOrderNumber("T", dateStr, null, "DINE_IN");
        return next != null ? next : 1;
    }

    //  外卖订单号获取（如果有调用3参版本的地方）
    @Transactional(readOnly = true)
    public Integer getNextTakeoutOrderNumber(String prefix, String dateStr, String deliveryMethod) {
        // 修改前: orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod);
        // 修改后：传入 orderType = "TAKEOUT"
        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod, "TAKEOUT");
        return next != null ? next : 1;
    }

    @Transactional
    public void addOrderItemsForGroupedTable(
            String mainTableDisplayId,
            List<OrderItem> orderItems,
            List<String> targetTableIds) {

        Tables mainTable = tablesMapper.findByDisplayId(mainTableDisplayId);
        if (mainTable == null) throw new IllegalArgumentException("餐桌不存在: " + mainTableDisplayId);

        // ═══════════════════════════════════════════════════════════
        // 【步骤 1】查找订单（保持原有逻辑）
        // ═══════════════════════════════════════════════════════════
        Integer orderId = null;
        if (mainTable.getCurrentReservationId() != null && !mainTable.getCurrentReservationId().isEmpty()) {
            orderId = orderMapper.findActiveOrderIdByReservationId(mainTable.getCurrentReservationId());
        }
        if (orderId == null) {
            orderId = orderMapper.findActiveOrderIdByTableId(mainTable.getTableId());
            if (orderId == null) {
                orderId = orderMapper.findOrderIdByTableIdAndStatus(mainTable.getTableId(), "NO_ORDER");
            }
        }
//        if (orderId == null) {
//            throw new IllegalStateException("餐桌 " + mainTableDisplayId + " 沒有活躍訂單");
//        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修复】步骤 2：查询现有明细 + 构建精确匹配 Map
        // ═══════════════════════════════════════════════════════════

        // 2.1 查询现有明细（只查未上桌的：UNSERVED/PREPARING/PREPARED）
        // ===== 原代码（删除或注释掉）=====
// if (orderId == null) {
//     throw new IllegalStateException("餐桌 " + mainTableDisplayId + " 沒有活躍訂單");
// }

        // ===== 替换为以下代码 =====
        if (orderId == null) {
            // ===== 无活跃订单 -> 自动创建新订单 =====
            System.out.println("🔧 餐桌 " + mainTableDisplayId + " 无活跃订单，自动创建新堂食订单...");

            // 1. 计算菜品总金额
            double itemsTotal = 0.0;
            for (OrderItem item : orderItems) {
                itemsTotal += item.getQuantity() * item.getPriceAtOrder();
            }
            itemsTotal = Math.round(itemsTotal * 100.0) / 100.0;

            // 2. 生成堂食订单号 (格式: T-YYYYMMDD-NNN)
            String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            Integer seq = orderMapper.getNextOrderNumber("T", dateStr, null, "DINE_IN");
            if (seq == null) seq = 1; // 首次下单兜底
            String orderNumber = String.format("T-%s-%03d", dateStr, seq);

            // 3. 调用 createOrder 创建订单主表及明细（会自动插入 orderItems，保留 assignedTableDisplayId 等字段）
            orderId = createOrder(
                    mainTable.getTableId(),
                    orderNumber,
                    "DINE_IN",
                    null,               // deliveryMethod
                    null,               // deliveryAddress
                    null,               // customerPhone
                    null,               // customerName
                    itemsTotal,
                    0.0,                // deliveryFee
                    itemsTotal,         // totalAmount
                    orderItems,         // 订单项列表（Controller 已设置好分配桌号和 distribution）
                    null                // deliveryStatus
            );

            if (orderId == null || orderId <= 0) {
                throw new RuntimeException("创建堂食订单失败，返回 orderId 无效");
            }

            System.out.println(" 新订单创建成功: orderId=" + orderId + ", orderNumber=" + orderNumber);
            return; // 订单已创建且明细已插入，直接返回，不执行后续的“合并逻辑”
        }

        List<Map<String, Object>> existingItemsRaw = orderItemMapper.getExistingItemQuantitiesRaw(orderId, null);

        // 🔧【关键】使用标准化复合键：itemCode|sortedAssignedTableIds → 完整记录
        Map<String, Map<String, Object>> existingMap = new HashMap<>();
        for (Map<String, Object> row : existingItemsRaw) {
            String code = ((String) row.get("itemCode")).toUpperCase();
            String assigned = (String) row.get("assignedTableDisplayId");
            String status = (String) row.get("status");

            // 🔧 跳过已上桌的菜品
            if ("PARTIALLY_SERVED".equals(status) || "SERVED".equals(status)) {
                continue;
            }

            // 🔧 标准化 assigned_table_display_id（数字排序）
            String normalizedAssigned = (assigned != null && !assigned.isEmpty()) ?
                    sortTableIds(assigned) : null;

            // 🔧 复合键：itemCode|sortedAssignedTableIds
            String key = code + "|" + (normalizedAssigned != null ? normalizedAssigned : "");

            existingMap.put(key, row);  // 存储完整记录供后续使用
        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修复】步骤 3：分类待处理项（更新 vs 新增）
        // ═══════════════════════════════════════════════════════════

        List<OrderItem> itemsToUpdate = new ArrayList<>();
        List<OrderItem> itemsToInsert = new ArrayList<>();

        for (OrderItem originalItem : orderItems) {
            OrderItem newItem = new OrderItem();
            newItem.setItemId(originalItem.getItemId());
            newItem.setItemCode(originalItem.getItemCode());
            newItem.setItemName(originalItem.getItemName());
            newItem.setPriceAtOrder(originalItem.getPriceAtOrder());
            newItem.setStatus("UNSERVED");
            newItem.setServedQuantity(0);

            String assignedTables = originalItem.getAssignedTableDisplayId();
            // 🔧 标准化 assigned_table_display_id（确保与数据库一致）
            String normalizedAssigned = (assignedTables != null && !assignedTables.isEmpty()) ?
                    sortTableIds(assignedTables) : null;

            newItem.setQuantity(originalItem.getQuantity());
            newItem.setAssignedTableDisplayId(normalizedAssigned);

            // 🔧【核心】构建查找键（使用标准化后的 assigned）
            String key = originalItem.getItemCode().toUpperCase() + "|" +
                    (normalizedAssigned != null ? normalizedAssigned : "");

            // 🔧【核心修复】通过复合键精确查找是否已存在（基于数据库记录）
            Map<String, Object> existingRow = existingMap.get(key);
            boolean isMerge = (existingRow != null);  // 🔑 关键：是否合并由数据库决定

            // 🔧 如果需要合并，提取现有信息
            String existingDistribution = null;
            Integer existingOrderItemId = null;
            Integer originalQty = 0;  // 🔧 原数量（用于计算增量）

            if (isMerge) {
                existingOrderItemId = (Integer) existingRow.get("orderItemId");
                originalQty = (Integer) existingRow.get("quantity");
                existingDistribution = (String) existingRow.get("quantityDistribution");

                // 🔧 设置 orderItemId 供 update 使用（如果需要精确更新）
                newItem.setOrderItemId(existingOrderItemId);

                System.out.println("🔧 合并模式: key=" + key +
                        ", orderItemId=" + existingOrderItemId +
                        ", originalQty=" + originalQty);
            }

            // 🔧【核心】计算 quantity_distribution（传入准确的 isMerge 和 originalQty）
            if (normalizedAssigned != null && !normalizedAssigned.isEmpty()) {
                int mergedTotalQuantity = originalQty + originalItem.getQuantity();

                String distribution = generateQuantityDistribution(
                        originalItem.getItemCode(),
                        originalQty,                    // 🔧 原数量（合并=数据库值，新增=0）
                        mergedTotalQuantity,
                        normalizedAssigned,
                        isMerge,                        // 🔧 准确传递是否合并
                        existingDistribution
                );
                newItem.setQuantityDistribution(distribution);
            }

            // 🔧 分类：合并走 update，否则走 insert
            if (isMerge) {
                itemsToUpdate.add(newItem);
            } else {
                itemsToInsert.add(newItem);
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 4】执行数据库操作（保持原有逻辑）
        // ═══════════════════════════════════════════════════════════

        if (!itemsToUpdate.isEmpty()) {
            orderItemMapper.updateExistingOrderItemsForGroupedTable(orderId, itemsToUpdate);
            System.out.println(" 更新现有菜品: " + itemsToUpdate.size() + "条");
        }
        if (!itemsToInsert.isEmpty()) {
            orderItemMapper.addOrderItems(orderId, itemsToInsert);
            System.out.println(" 新增菜品记录: " + itemsToInsert.size() + "条");
        }

        // 【步骤 5】状态与金额更新（保持原有逻辑）
        Order order = orderMapper.findById(orderId);
        if (order != null && "NO_ORDER".equals(order.getStatus())) {
            orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");
        }
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }

        System.out.println(" 聚餐桌点餐处理完成 - orderId=" + orderId);
    }

    /**
     * 🔧 生成数量分配记录（JSON格式）
     *
     * @param itemCode               菜品编号
     * @param originalQty            🔧 菜品原有数量（合并时=数据库中的值，新增时=0）
     * @param totalQuantity          🔧 菜品总数量（合并后 = originalQty + 新增数量）
     * @param assignedTableDisplayId 分配的餐桌显示ID（已标准化排序）
     * @param isMerge                🔧 是否为合并操作（由数据库精确匹配决定）
     * @param existingDistribution   现有的 distribution JSON 字符串（合并时传入）
     * @return JSON 字符串，或 null（不需要记录时）
     */
    private String generateQuantityDistribution(String itemCode, int originalQty, int totalQuantity,
                                                String assignedTableDisplayId, boolean isMerge,
                                                String existingDistribution) {
        // 1. 验证参数
        if (assignedTableDisplayId == null || assignedTableDisplayId.isEmpty()) {
            return null;
        }


        // 2. 解析桌号列表
        String[] tableIds = assignedTableDisplayId.split(",");
        int tableCount = tableIds.length;

        if (tableCount == 1) {
//            System.out.println("🔧 单桌分配菜品 " + itemCode +
//                    " (assigned=" + assignedTableDisplayId + ")，不需要 quantity_distribution");
            return null;  // ← 关键：单桌直接返回 null
        }

        if (tableCount == 0) return null;

        // 3. 🔧【核心规则】数量必须能被桌数整除
        if (totalQuantity % tableCount != 0) {
            throw new IllegalArgumentException(
                    "菜品 " + itemCode + " 的总数量 (" + totalQuantity +
                            ") 必须能被桌子数量 (" + tableCount + ") 整除！"
            );
        }

        // 4. 🔧【核心修复】合并操作时，基于现有 distribution 更新 + 累加
        Map<String, Integer> distribution = new LinkedHashMap<>();
        int qtyToAddPerTable = 0;

        if (isMerge && existingDistribution != null && !existingDistribution.isEmpty()) {
            // 解析现有 distribution
            try {
                String jsonContent = existingDistribution.replaceAll("[{}\\s]", "");
                String[] pairs = jsonContent.split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":");
                    if (kv.length == 2) {
                        String tableId = kv[0].replaceAll("\"", "").trim();
                        int qty = Integer.parseInt(kv[1].trim());
                        distribution.put(tableId, qty);
                    }
                }
                // 🔧【关键】计算每桌需要增加的数量 = (新总数 - 原总数) / 桌数
                qtyToAddPerTable = (totalQuantity - originalQty) / tableCount;
                System.out.println("🔧 合并操作：菜品=" + itemCode +
                        ", 原数量=" + originalQty +
                        ", 新总数=" + totalQuantity +
                        ", 桌数=" + tableCount +
                        ", 每桌增加=" + qtyToAddPerTable);
            } catch (Exception e) {
                System.err.println("🔧 解析现有 distribution 失败: " + existingDistribution);
                e.printStackTrace();
                distribution.clear();  // 解析失败时清空，走初始化逻辑
            }
        }

        // 5. 如果 distribution 为空，按新逻辑初始化
        if (distribution.isEmpty()) {
            int qtyPerTable = totalQuantity / tableCount;

            // 🔧【核心规则】判断是否需要记录 distribution
            boolean shouldRecord = isMerge || qtyPerTable >= 2;
            if (!shouldRecord) {
                System.out.println("🔧 菜品 " + itemCode + " 初次添加，每桌 " + qtyPerTable +
                        " 份 < 2，不记录 distribution");
                return null;
            }

            // 按桌号数字排序（确保分配顺序一致）
            List<String> sortedTableIds = Arrays.stream(tableIds)
                    .map(String::trim)
                    .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^0-9]", ""))))
                    .collect(Collectors.toList());

            for (String tableId : sortedTableIds) {
                distribution.put(tableId, qtyPerTable);
            }
        }
        // 🔧【新增】合并且有 distribution 时，累加新增数量
        else if (isMerge && qtyToAddPerTable > 0) {
            for (String tableId : distribution.keySet()) {
                int newQty = distribution.get(tableId) + qtyToAddPerTable;
                distribution.put(tableId, newQty);
                System.out.println("🔧 桌号#" + tableId + " 数量累加: " +
                        (newQty - qtyToAddPerTable) + " → " + newQty);
            }
        }

        // 6. 转换为 JSON 字符串
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");

        System.out.println("🔧 生成 distribution: " + itemCode +
                " (原" + originalQty + "→总" + totalQuantity + "份/" + tableCount + "桌) → " + json);

        return json.toString();
    }


    /**
     * 🔧 根据 orderItemId 获取菜品总数量（精确查询，不聚合）
     */
    @Transactional(readOnly = true)
    public int getOrderItemQuantityByOrderItemId(Integer orderItemId) {
        if (orderItemId == null) return 0;

        OrderItem item = orderItemMapper.selectByPrimaryKey(orderItemId);
        return item != null ? item.getQuantity() : 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSpecificOrderItemAsServed(String tableNumber, int orderItemId, int quantity) throws SQLException {
        // 1. 验证餐桌
        Tables table = tablesMapper.findByDisplayId(tableNumber);
        if (table == null || table.getTableType() != Tables.TableType.GROUPED) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 不是聚餐桌");
        }

        // 2. 🔧【核心】根据 orderItemId 精确查询订单项
        OrderItem targetItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (targetItem == null) {
            throw new IllegalStateException("未找到订单明细记录: #" + orderItemId);
        }

        // 3. 验证状态
        if ("SERVED".equals(targetItem.getStatus())) {
            throw new IllegalStateException("该记录已全部上桌，不能重复操作");
        }

        // 4. 计算新数量
        int totalQty = targetItem.getQuantity();
        int servedQty = targetItem.getServedQuantity();
        int newServedQty = Math.min(servedQty + quantity, totalQty);
        String newStatus = (newServedQty >= totalQty) ? "SERVED" : "PARTIALLY_SERVED";

        // 5. 🔧【核心修复】更新 served_table_display_id
        String assignedTables = targetItem.getAssignedTableDisplayId();
        String newServedTables;

        // 🔧 如果是聚餐桌一键点餐（assigned 包含多个桌号），直接使用完整列表
        if (assignedTables != null && !assignedTables.isEmpty() && assignedTables.contains(",")) {
            // 聚餐桌一键点餐：直接使用 assigned_table_display_id 作为 served_table_display_id
            newServedTables = assignedTables;
            System.out.println("🔧 聚餐桌一键点餐菜品：使用完整桌号列表: " + newServedTables);
        } else {
            // 普通单桌分配：逐桌追加
            String currentServedTables = targetItem.getServedTableDisplayId();
            if (currentServedTables == null || currentServedTables.isEmpty()) {
                newServedTables = tableNumber;
            } else if (!currentServedTables.contains(tableNumber)) {
                newServedTables = currentServedTables + "," + tableNumber;
            } else {
                newServedTables = currentServedTables;  // 已包含，不重复添加
            }
        }

        // 6. 执行更新
        orderItemMapper.updateServedWithTableInfo(
                orderItemId, newServedQty, newStatus, newServedTables);

        System.out.println("🔧 精确上菜成功: orderItemId=#" + orderItemId +
                ", table=" + tableNumber +
                ", served=" + newServedQty + "/" + totalQty +
                ", served_tables=" + newServedTables);
    }

    /**
     * 🔧 预约聚餐桌点餐（无需 targetTableIds 参数）
     * 预约订单的餐桌分配可能在入座时才确定
     */
    @Transactional
    public void addOrderItemsForReservationGroupedTable(
            String reservationId,
            List<OrderItem> orderItems) {

        // 🔧 通过 reservation_id 查找预点餐订单
        Order order = orderMapper.findActiveOrderByReservationId(reservationId);
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("未找到预约订单: " + reservationId);
        }

        Integer orderId = order.getOrderId();

        // 🔧【核心修改】查询现有明细时，只根据 itemCode 匹配，不考虑 assigned_table_display_id
        // 因为预约订单此时还未分配餐桌，assigned_table_display_id 都是 NULL
        List<Map<String, Object>> existingItemsRaw =
                orderItemMapper.getExistingItemQuantitiesRaw(orderId, null);

        Map<String, OrderItem> existingMap = new HashMap<>();
        for (Map<String, Object> row : existingItemsRaw) {
            String code = ((String) row.get("itemCode")).toUpperCase();
            String status = (String) row.get("status");
            // 🔧【关键】只用 itemCode 作为 key，不考虑 assignedTableDisplayId
            existingMap.put(code, new OrderItem());
        }

        List<OrderItem> itemsToUpdate = new ArrayList<>();
        List<OrderItem> itemsToInsert = new ArrayList<>();

        for (OrderItem originalItem : orderItems) {
            OrderItem newItem = new OrderItem();
            newItem.setItemId(originalItem.getItemId());
            newItem.setItemCode(originalItem.getItemCode());
            newItem.setItemName(originalItem.getItemName());
            newItem.setPriceAtOrder(originalItem.getPriceAtOrder());
            newItem.setStatus("UNSERVED");
            newItem.setServedQuantity(0);
            newItem.setQuantity(originalItem.getQuantity());

            // 🔧 预约订单点餐时，assigned_table_display_id 保持为 NULL
            newItem.setAssignedTableDisplayId(null);

            // 🔧【关键】只用 itemCode 作为 key
            String key = originalItem.getItemCode().toUpperCase();

            if (existingMap.containsKey(key)) {
                itemsToUpdate.add(newItem);
            } else {
                itemsToInsert.add(newItem);
                existingMap.put(key, newItem);
            }
        }

        // 🔧 执行数据库操作
        if (!itemsToUpdate.isEmpty()) {
            orderItemMapper.updateExistingOrderItemsForReservation(orderId, itemsToUpdate);
        }
        if (!itemsToInsert.isEmpty()) {
            orderItemMapper.insertNewOrderItemsWithStatus(orderId, itemsToInsert, "UNSERVED");
        }

        // 🔧 更新订单状态和金额
        if ("NO_ORDER".equals(order.getStatus())) {
            orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");
        }

        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }

        System.out.println(" 预约聚餐桌点餐处理完成 - orderId=" + orderId);
    }



}