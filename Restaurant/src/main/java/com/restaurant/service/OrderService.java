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
    private final BusinessStatusMapper businessStatusMapper;  //  新增字段
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

    /**
     * 加载堂食订单的菜品明细
     *
     * 功能说明：
     * 1. 校验餐桌显示编号有效性，为空或"未选择"时返回空列表
     * 2. 查询餐桌对象并判断是否为聚餐桌类型
     * 3. 聚餐桌处理：解析 group_with 字段获取所有关联桌号，批量查询订单明细
     * 4. 普通餐桌处理：仅查询当前餐桌关联的订单明细
     *
     * @param tableDisplayId 餐桌显示编号
     * @return 订单项列表；参数无效、餐桌不存在或无关联订单时返回空列表
     *
     * 业务规则：
     * - 聚餐桌订单明细需聚合所有关联桌的数据，确保菜品展示完整
     * - 普通餐桌仅加载本桌订单，避免数据泄露
     *
     * 应用场景：
     * - 顾客点餐界面加载已点菜品
     * - 结账前展示订单明细供确认
     */
    @Transactional(readOnly = true)
    public List<OrderItem> loadFormalOrderItems(String tableDisplayId) {
        if (tableDisplayId == null || tableDisplayId.trim().isEmpty() || "未选择".equals(tableDisplayId)) {
            return Collections.emptyList();
        }

        // 【核心修改】检查是否为聚餐桌，如果是则获取所有关联桌号
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
            // System.out.println(" 聚餐桌查询订单: " + String.join(",", tableDisplayIds));
        } else {
            // 普通餐桌，只查询当前桌
            tableDisplayIds.add(tableDisplayId.trim());
        }

        // 调用 Mapper 查询所有关联餐桌的订单
        return orderMapper.findOrderItemsByTableDisplayIds(tableDisplayIds);
    }

    /**
     * 加载堂食订单列表
     *
     * 功能说明：
     * 查询所有堂食类型（DINE_IN）的订单记录，返回包含订单号、餐桌号、金额、状态等字段的映射列表，
     * 供订单管理界面展示与筛选。
     *
     * @return 堂食订单映射列表；无订单时返回空列表
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：order_type = 'DINE_IN'
     *
     * 应用场景：
     * - 前台订单管理界面展示所有堂食订单
     * - 运营报表统计堂食订单数量与营收
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadDineInOrders() {
        return orderMapper.findDineInOrders();
    }

    /**
     * 加载自取订单列表
     *
     * 功能说明：
     * 查询所有自取类型（TAKEOUT + PICKUP）的外卖订单记录，返回包含订单号、客户信息、金额、状态等字段的映射列表，
     * 供外卖订单管理界面展示与跟踪。
     *
     * @return 自取订单映射列表；无订单时返回空列表
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：order_type = 'TAKEOUT' AND delivery_method = 'PICKUP'
     *
     * 应用场景：
     * - 外卖窗口展示待制作/待取餐订单
     * - 店员通知顾客取餐时查询订单状态
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadPickupOrders() {
        return orderMapper.findPickupOrders();
    }

    /**
     * 加载配送订单列表
     *
     * 功能说明：
     * 查询所有配送类型（TAKEOUT + DELIVERY）的外卖订单记录，返回包含订单号、客户信息、配送地址、金额、配送状态等字段的映射列表，
     * 供配送员接单与跟踪使用。
     *
     * @return 配送订单映射列表；无订单时返回空列表
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：order_type = 'TAKEOUT' AND delivery_method = 'DELIVERY'
     *
     * 应用场景：
     * - 配送员接单界面展示待配送订单
     * - 客服查询配送进度时获取订单详情
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadDeliveryOrders() {
        return orderMapper.findDeliveryOrders();
    }

    /**
     * 获取下一个外卖订单序号
     *
     * 功能说明：
     * 1. 根据订单类型确定订单号前缀：自取订单用"P"，配送订单用"D"
     * 2. 获取当前日期字符串（格式：yyyyMMdd）作为订单号日期部分
     * 3. 调用 Mapper 查询指定前缀、日期、配送方式下的最大序号，返回下一个可用序号
     *
     * @param orderType 外卖订单类型枚举（PICKUP 或 DELIVERY）
     * @return 下一个订单序号；查询无结果时返回 1
     *
     * 业务规则：
     * - 订单号格式：前缀 + 日期 + 序号（如 "P-20260528-001"）
     * - 序号按日期与配送方式独立递增，确保每日每类订单号唯一
     */
    @Transactional(readOnly = true)
    public int getNextTakeoutOrderNumber(OrderType orderType) {
        String prefix = (orderType == OrderType.PICKUP) ? "P" : "D";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String deliveryMethod = orderType.getDbDeliveryMethod(); // PICKUP 或 DELIVERY

        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod, orderType.getDbOrderType());
        return next != null ? next : 1;
    }

    /**
     * 创建新订单并插入明细
     *
     * 功能说明：
     * 1. 校验配送状态合法性：堂食订单必须为 null，配送订单必须指定状态，自取订单必须为 null
     * 2. 构建订单实体：设置餐桌、订单号、类型、配送信息、客户信息、金额字段及初始状态
     * 3. 插入订单主表并获取回填的主键
     * 4. 若存在订单项列表，批量插入订单明细记录
     * 5. 校验订单创建结果，失败时记录详细日志并抛出异常
     *
     * @param tableId 关联餐桌主键（外卖订单可为 null）
     * @param orderNumber 订单编号（格式：前缀 + 日期 + 序号）
     * @param orderType 订单类型（"DINE_IN"/"TAKEOUT"）
     * @param deliveryMethod 配送方式（"DELIVERY"/"PICKUP"/null）
     * @param deliveryAddress 配送地址（仅配送订单需要）
     * @param customerPhone 客户联系电话
     * @param customerName 客户姓名
     * @param itemsTotal 菜品总额
     * @param deliveryFee 配送费（非配送订单为 0.0）
     * @param totalAmount 应付总额（= itemsTotal + deliveryFee）
     * @param orderItems 订单项列表（可为空）
     * @param deliveryStatus 配送状态枚举（仅配送订单需要）
     * @return 创建成功的订单主键；创建失败时抛出 RuntimeException
     *
     * 事务管理：
     * - 方法标注 @Transactional，确保订单主表与明细插入的原子性
     * - 失败时自动回滚，避免数据不一致
     */
    @Transactional
    public Integer createOrder(Integer tableId, String orderNumber, String orderType, String deliveryMethod,
                               String deliveryAddress, String customerPhone, String customerName, Double itemsTotal,
                                Double deliveryFee, Double totalAmount, List<OrderItem> orderItems,
                               Order.DeliveryStatus deliveryStatus
    ) {

        // 校验 delivery_status 合法性
        validateDeliveryStatus(orderType, deliveryMethod, deliveryStatus);


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

        //  设置配送状态（校验通过后）
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
            //  打印详细错误信息
            System.err.println(" 创建订单失败！order 对象状态：");
            System.err.println("  orderType=" + order.getOrderType());
            System.err.println("  deliveryMethod=" + order.getDeliveryMethod());
            System.err.println("  deliveryStatus=" + order.getDeliveryStatus());
            throw new RuntimeException("创建订单主记录失败");
        }

        return orderId;
    }

    /**
     * 校验配送状态字段的合法性
     *
     * 功能说明：
     * 1. 堂食订单：配送状态必须为 null，禁止设置
     * 2. 外卖订单：
     *    - 配送方式：配送状态不能为 null，必须指定初始状态
     *    - 自取方式：配送状态必须为 null
     *    - 未指定配送方式：配送状态必须为 null
     *
     * @param orderType 订单类型（"DINE_IN"/"TAKEOUT"）
     * @param deliveryMethod 配送方式（"DELIVERY"/"PICKUP"/null）
     * @param deliveryStatus 待校验的配送状态枚举值
     *
     * 异常处理：
     * - 任一规则校验失败时抛出 IllegalArgumentException，提示具体冲突原因
     * - 调用方需在创建订单前调用此方法，确保数据合规
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
     * 根据订单号加载订单项列表
     *
     * 功能说明：
     * 1. 校验订单号参数有效性，为空时返回空列表
     * 2. 调用 Mapper 查询该订单关联的所有订单项记录
     *
     * @param orderNumber 订单编号字符串
     * @return 订单项列表；参数为空或无关联明细时返回空列表
     *
     * 应用场景：
     * - 外卖订单详情查询时加载菜品明细
     * - 订单修改或撤销前校验可操作项
     */
    @Transactional(readOnly = true)
    public List<OrderItem> loadOrderItemsByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return orderMapper.findOrderItemsByOrderNumber(orderNumber.trim());
    }


    /**
     * 根据订单号查询配送费
     *
     * 功能说明：
     * 1. 校验订单号参数有效性，为空时返回默认值 0.0
     * 2. 通过订单号查询订单对象
     * 3. 返回配送费字段，若为空则返回 0.0
     *
     * @param orderNumber 外卖订单编号
     * @return 配送费金额；订单不存在或字段为空时返回 0.0
     *
     * 应用场景：
     * - 外卖订单结算界面显示配送费用
     * - 订单详情查询时计算应付总额
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
     * 根据餐桌主键查询活跃订单主键
     *
     * 功能说明：
     * 查询指定餐桌当前状态为"已点餐"或"未点餐"的订单主键，
     * 用于支持点餐、结账、撤销等业务流程的订单定位。
     *
     * @param tableId 餐桌数据库主键
     * @return 活跃订单主键；无匹配订单时返回 null
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：table_id 匹配且状态为有效订单状态
     *
     * 应用场景：
     * - 顾客点餐时加载或创建订单
     * - 结账时校验订单是否存在且状态正确
     */
    @Transactional(readOnly = true)
    public Integer findActiveOrderIdByTableId(Integer tableId) {
        if (tableId == null) return null;
        return orderMapper.findActiveOrderIdByTableId(tableId);
    }

    /**
     * 根据订单号查询活跃订单对象
     *
     * 功能说明：
     * 1. 校验订单号参数有效性，为空时返回 null
     * 2. 通过订单号查询订单对象
     * 3. 仅返回状态为"已点餐"的订单，过滤已结账或无效订单
     *
     * @param orderNumber 订单编号字符串
     * @return 活跃订单对象；订单不存在或状态非"已点餐"时返回 null
     *
     * 业务规则：
     * - 仅"已点餐"状态的订单视为活跃，支持后续点餐或修改操作
     * - 已结账订单需通过其他方法查询，避免状态混淆
     *
     * 应用场景：
     * - 外卖订单详情查询时校验订单有效性
     * - 订单修改前确认订单仍处于可编辑状态
     */
    @Transactional(readOnly = true)
    public Order findActiveOrderByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) return null;
        Order order = orderMapper.findByOrderNumber(orderNumber.trim());
        // 只返回 ORDERED 状态的活跃订单
        return (order != null && "ORDERED".equals(order.getStatus())) ? order : null;
    }

    /**
     * 根据预约号查询活跃的预点餐订单
     *
     * 功能说明：
     * 查询指定预约记录关联的、状态为有效的预点餐订单对象，
     * 用于顾客入座时加载提前点选的菜品明细。
     *
     * @param reservationId 预约记录唯一标识
     * @return 预点餐订单对象；无关联订单或订单无效时返回 null
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：reservation_id 匹配且订单类型为"RESERVATION"
     *
     * 应用场景：
     * - 顾客入座时自动加载预点餐菜品到正式订单
     * - 预约修改时同步更新关联订单的预付信息与菜品明细
     */
    @Transactional(readOnly = true)
    public Order findActiveOrderByReservationId(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return null;
        }
        return orderMapper.findActiveOrderByReservationId(reservationId.trim());
    }

    /**
     * 【核心修复】根据 served_quantity 和 total_quantity 计算合并后的正确状态
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


        // 【情况1】预约订单（客人未入座）→ 只能是 PREPARING/PREPARED
        if (isReservationOrder && !isReservationSeated) {
            if (servedQty >= totalQty) {
                return "PREPARED";  // 全部已准备
            } else {
                return "PREPARING"; // 部分或未准备
            }
        }


        // 【情况2&3】预约入座（currentReservationId 存在）
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


        // 【情况4】普通堂食订单（无 currentReservationId）
        if (servedQty >= totalQty) {
            return "SERVED";
        } else if (servedQty > 0) {
            return "PARTIALLY_SERVED";
        } else {
            return "UNSERVED";
        }
    }


    /**
     * 合并新菜品到现有订单
     *
     * 功能说明：
     * 1. 校验订单主键与新菜品映射有效性
     * 2. 查询订单实体，若为预点餐订单且状态为未点餐，则升级为已点餐状态
     * 3. 判断订单类型：预约订单/预约入座订单/普通堂食订单，以及是否为聚餐桌
     * 4. 查询现有订单项，构建复合键映射（聚餐桌使用"菜品编码 + 标准化分配餐桌"作为键）
     * 5. 遍历新菜品列表，分类为待更新或待新增：
     *    - 聚餐桌且已有上桌记录：强制新增记录，避免覆盖已上桌数据
     *    - 复合键匹配现有项：标记为更新，计算合并后状态与数量分布
     *    - 无匹配项：标记为新增，初始化状态为未上桌
     * 6. 执行批量数据库操作：
     *    - 已上桌菜品：使用普通堂食更新逻辑
     *    - 未上桌菜品：使用预约订单更新逻辑
     *    - 新增菜品：批量插入并设置初始状态
     * 7. 重算订单总金额并更新订单主表
     *
     * @param orderId 订单主键
     * @param newItemsMap 新菜品映射，键为菜品编码（聚餐桌含分配餐桌信息），值为新增数量
     *
     * 业务规则：
     * - 聚餐桌菜品使用复合键匹配，确保相同菜品分配不同餐桌时独立管理
     * - 已有上桌记录的聚餐桌菜品强制新增，避免影响已上桌数据
     * - 预约订单无配送费，总金额等于菜品总额
     *
     * 事务管理：
     * - 方法标注 @Transactional，确保合并操作原子性，失败自动回滚
     */
    @Transactional  // 声明事务：保证操作的原子性，失败自动回滚
    public void mergeOrderItems(Integer orderId, Map<String, Integer> newItemsMap) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单 ID 无效");
        }
        if (newItemsMap == null || newItemsMap.isEmpty()) {
            return;
        }


        // 【步骤2】查询订单实体
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在：" + orderId);
        }


        // 【步骤3】预点餐订单状态升级（NO_ORDER → ORDERED）
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


        // 【步骤4】判断订单类型和餐桌类型（核心分支逻辑）
        boolean isReservationOrder = "RESERVATION".equals(order.getOrderType());
        boolean isReservationSeated = false;
        boolean isGroupedTable = false;  //  新增：标记是否为聚餐桌

        if (order.getTableId() != null) {
            Tables table = tablesMapper.findById(order.getTableId());
            if (table != null) {
                if (table.getCurrentReservationId() != null && !table.getCurrentReservationId().isEmpty()) {
                    isReservationSeated = true;
                    System.out.println("检测到预约入座订单：orderId=" + orderId +
                            ", reservationId=" + table.getCurrentReservationId());
                }
                // 【核心修复】判断是否为聚餐桌
                if (table.getTableType() == Tables.TableType.GROUPED) {
                    isGroupedTable = true;
                    System.out.println(" 检测到聚餐桌订单：orderId=" + orderId +
                            ", tableType=GROUPED, groupWith=" + table.getGroupWith());
                }
            }
        }


        // 【步骤5】获取订单现有明细（构建匹配映射表）
        List<Map<String, Object>> rawList = orderItemMapper.getExistingItemQuantitiesRaw(orderId, null);

        Map<String, Integer> existingItems = new HashMap<>();
        Map<String, Integer> existingServedQty = new HashMap<>();
        Map<String, String> existingItemStatus = new HashMap<>();
        //  新增：创建映射表：复合键 → 原distribution
        Map<String, String> existingQuantityDistribution = new HashMap<>();

        // 【新增】记录已有上桌记录的复合键（聚餐桌一键点餐专用）
        Set<String> itemsWithServedRecords = new HashSet<>();

        for (Map<String, Object> row : rawList) {
            String code = ((String) row.get("itemCode")).trim().toUpperCase();
            Integer qty = (Integer) row.get("quantity");
            Integer served = (Integer) row.get("servedQuantity");
            String status = (String) row.get("status");
            //  提取原distribution
            String quantityDist = (String) row.get("quantityDistribution");

            // 【关键修复】提取分配的餐桌显示ID
            String assignedTableId = (String) row.get("assignedTableDisplayId");

            // 【核心修复】根据餐桌类型构建复合键
            String key = code;
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                String sortedTableIds = sortTableIds(assignedTableId);
                key = code + "|" + sortedTableIds;
                System.out.println(" 聚餐桌数据库侧复合键：" + key);
            }

            existingItems.put(key, qty);
            existingServedQty.put(key, served != null ? served : 0);
            existingItemStatus.put(key, status);
            //  记录原distribution
            existingQuantityDistribution.put(key, quantityDist);

            //  记录已有上桌记录的菜品
            if (served != null && served > 0) {
                itemsWithServedRecords.add(key);
                System.out.println(" 菜品已有上桌记录：key=" + key + ", served=" + served);
            }
        }


        // 【步骤6】分类新菜品：更新 vs 新增
        List<OrderItem> itemsToUpdate = new ArrayList<>();
        List<OrderItem> itemsToInsert = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : newItemsMap.entrySet()) {
            String itemKey = entry.getKey().trim().toUpperCase();
            int newQty = entry.getValue();

            //  解析特殊 Key 格式：B1[BATCH:13,14,15]
            String pureItemCode = itemKey;
            String assignedTableId = null;
            boolean isBatchOrder = false;

            if (itemKey.contains("[BATCH:")) {
                isBatchOrder = true;
                int batchStart = itemKey.indexOf("[BATCH:");
                pureItemCode = itemKey.substring(0, batchStart);
                assignedTableId = itemKey.substring(batchStart + 7, itemKey.length() - 1);
                System.out.println(" 解析一键点餐：pureItemCode=" + pureItemCode +
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

            //  标准化 assigned_table_display_id
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                orderItem.setAssignedTableDisplayId(sortTableIds(assignedTableId));
            } else {
                orderItem.setAssignedTableDisplayId(null);
            }

            //  构建复合键
            String key = pureItemCode;
            if (isGroupedTable && assignedTableId != null && !assignedTableId.isEmpty()) {
                String sortedTableIds = sortTableIds(assignedTableId);
                key = pureItemCode + "|" + sortedTableIds;
                System.out.println(" 使用复合键：" + key);
            }

            // 【核心修复】根据餐桌类型 + 上桌记录决定合并/插入
            boolean hasServedRecord = itemsWithServedRecords.contains(key);

            if (isGroupedTable && hasServedRecord) {
                itemsToInsert.add(orderItem);
                System.out.println(" 聚餐桌一键点餐：菜品 " + pureItemCode +
                        " 已有上桌记录，创建新记录：key=" + key);
            } else if (existingItems.containsKey(key)) {
                itemsToUpdate.add(orderItem);
                System.out.println(" 找到匹配项，准备合并更新：key=" + key +
                        ", 原数量=" + existingItems.get(key) + ", 新增=" + newQty);
            } else {
                itemsToInsert.add(orderItem);
                System.out.println(" 未找到匹配项，准备插入：key=" + key);
            }
        }


        // 【步骤7】【核心修正】处理待更新项（计算状态 + 生成distribution + 分组更新）
        if (!itemsToUpdate.isEmpty()) {
            List<OrderItem> servedGroup = new ArrayList<>();
            List<OrderItem> unservedGroup = new ArrayList<>();

            for (OrderItem item : itemsToUpdate) {
                String itemCode = item.getItemCode();
                int newQty = item.getQuantity();

                //  构建 lookupKey
                String lookupKey = itemCode;
                String itemAssignedTableId = item.getAssignedTableDisplayId();
                if (itemAssignedTableId != null && !itemAssignedTableId.isEmpty()) {
                    String sortedTableIds = sortTableIds(itemAssignedTableId);
                    lookupKey = itemCode + "|" + sortedTableIds;
                }

                //  获取原有数据
                Integer originalQty = existingItems.get(lookupKey);
                Integer originalServed = existingServedQty.getOrDefault(lookupKey, 0);
                String originalStatus = existingItemStatus.get(lookupKey);
                String originalDistribution = existingQuantityDistribution.get(lookupKey);

                //  计算合并后的状态
                String newStatus = calculateMergedStatus(
                        originalServed, originalQty, newQty,
                        originalStatus, isReservationOrder, isReservationSeated
                );

                // [核心新增】为聚餐桌生成/更新 quantity_distribution
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
                    item.setQuantityDistribution(newDistribution);  //  设置到订单项
                    System.out.println(" 更新菜品 distribution: " + itemCode +
                            " → " + newDistribution);
                }

                System.out.println(" 菜品 " + itemCode +
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

            //  已上桌的菜品 → 普通堂食逻辑
            if (!servedGroup.isEmpty()) {
                System.out.println(" 已上桌菜品 → 普通逻辑，数量：" + servedGroup.size());
                orderItemMapper.updateExistingOrderItems(orderId, servedGroup);
            }
            // 未上桌的菜品 → 预约逻辑
            if (!unservedGroup.isEmpty()) {
                System.out.println(" 未上桌菜品 → 预约逻辑，数量：" + unservedGroup.size());
                orderItemMapper.updateExistingOrderItemsForReservation(orderId, unservedGroup);
            }
        }


        // 【步骤8】处理待新增项（设置初始状态 + 生成distribution）
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
                    System.out.println(" 新菜品生成 distribution: " + distribution);
                }
            }

            System.out.println(" 新菜品初始状态：" + initialStatus + ", 数量：" + itemsToInsert.size());
            orderItemMapper.insertNewOrderItemsWithStatus(orderId, itemsToInsert, initialStatus);
        }


        // 【步骤9】重算订单金额（预约订单无配送费）
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
     * 标准化排序餐桌显示编号列表
     *
     * 功能说明：
     * 1. 校验输入字符串有效性，为空时直接返回
     * 2. 按逗号分割餐桌编号，提取数字部分进行升序排序
     * 3. 重新拼接为逗号分隔的字符串，确保"14,13,15"与"13,14,15"视为相同
     *
     * @param tableIds 餐桌显示编号列表字符串（如"14,13,15"）
     * @return 排序后的餐桌编号字符串（如"13,14,15"）；输入为空时返回原值
     *
     * 应用场景：
     * - 聚餐桌菜品分配时构建复合键，确保不同顺序的餐桌列表能正确匹配
     * - 数量分布生成时保证桌号顺序一致，便于前端展示与后端校验
     *
     * 排序规则：
     * - 仅比较餐桌编号的数字部分，忽略字母后缀（如"7a"→7）
     * - 数字相同时按原始字符串字典序排列（Java Arrays.sort 稳定排序）
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
     *
     * 功能说明：
     * 1. 校验订单号参数有效性，为空时返回默认提示文本
     * 2. 查询订单当前状态枚举值
     * 3. 拼接状态显示名称，返回格式化结果供界面展示
     *
     * @param orderNumber 订单编号字符串
     * @return 格式化状态文本（如"订单情况：已结账"）；订单不存在时返回"未找到订单"提示
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

    /**
     * 标记指定菜品为已上桌
     *
     * 功能说明：
     * 1. 校验餐桌号、菜品主键、上桌数量等基础参数有效性
     * 2. 查询餐桌对象并判断是否为聚餐桌类型
     * 3. 聚餐桌处理逻辑：
     *    - 优先查询多桌共享菜品（模糊匹配分配餐桌列表）
     *    - 若未找到则查询单桌分配菜品（精确匹配餐桌编号）
     *    - 根据分配类型调用对应处理方法：共享菜品执行顺序校验与份额计算，单桌菜品直接累加数量
     * 4. 普通餐桌处理逻辑：
     *    - 查询活跃订单及菜品明细
     *    - 校验菜品未全部上桌，计算新已上桌数量与状态
     *    - 更新订单项并检查订单是否全部完成
     * 5. 若订单所有菜品均已上桌，更新餐桌订单状态为"制作完成"
     *
     * @param tableNumber 餐桌显示编号
     * @param itemId 待上桌的菜品主键
     * @param quantity 本次上桌的菜品数量
     *
     * 业务规则：
     * - 聚餐桌共享菜品需按桌号顺序上桌，禁止跳过中间桌号
     * - 共享菜品每桌份额由总数量整除桌数计算，输入数量自动修正
     * - 普通餐桌直接累加已上桌数量，不超过菜品总数量
     *
     * 异常处理：
     * - 参数无效、餐桌/订单/菜品不存在或状态冲突时抛出相应异常
     */
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

        // 【核心修改】聚餐桌：区分【单桌分配】与【多桌共享】两种模式
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


            // 【核心分支】根据 assigned_table_display_id 判断菜品类型
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
     * 处理聚餐桌多桌共享菜品的上桌逻辑
     *
     * 功能说明：
     * 1. 校验菜品状态，若已全部上桌则直接返回
     * 2. 解析分配餐桌列表并按数字排序，确保顺序一致性
     * 3. 获取已上桌桌号列表，校验当前桌号未重复上桌
     * 4. 执行相邻性校验：当前桌号必须与任意已上桌桌号数字相邻，禁止跳过中间桌号
     * 5. 计算每桌份额：
     *    - 若 distribution 为空或均匀：总数量整除桌数
     *    - 若 distribution 不均匀：抛出异常提示分配不一致
     * 6. 自动修正输入数量：若与计算份额不符，按份额执行并记录日志
     * 7. 更新订单项：累加已上桌数量、追加当前桌号到已上桌列表、推导新状态
     *
     * @param item 待更新的订单项对象
     * @param currentTableNumber 当前操作的餐桌显示编号
     * @param inputQuantity 用户输入的上桌数量
     *
     * 业务规则：
     * - 共享菜品必须按桌号顺序上桌，确保每桌公平分配
     * - 允许回退上桌（如 18→17），但必须与已上桌桌号相邻
     * - 每桌份额固定，输入数量自动修正，避免人为误差
     *
     * 异常处理：
     * - 分配餐桌列表为空、桌号数量不足 2 或输入数量超限时抛出相应异常
     * - 跳过桌号或分配不均匀时抛出异常，提示用户按规则操作
     */
    private void handleSharedDishServing(OrderItem item, String currentTableNumber, int inputQuantity) {

        // 【步骤 0】 检查菜品状态：如果已全部上桌则直接跳过
        if ("SERVED".equals(item.getStatus())) {
            return;  //  关键：直接返回，不执行任何后续逻辑
        }

        // 【步骤 1】基础验证：检查 assigned_table_display_id 是否为空
        String assignedId = item.getAssignedTableDisplayId();
        if (assignedId == null || assignedId.isEmpty()) {
            throw new IllegalStateException("共享菜品 assigned_table_display_id 不能为空");
        }


        // 【步骤 2】解析聚餐桌号列表（格式："15,16,17" → 排序后 [15,16,17]）
        String[] tableIds = assignedId.split(",");
        List<String> sortedTableIds = Arrays.stream(tableIds)
                .map(String::trim)
                .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^0-9]", ""))))
                .collect(Collectors.toList());

        int tableCount = sortedTableIds.size();
        if (tableCount < 2) {
            throw new IllegalStateException("共享菜品必须分配给至少 2 张餐桌");
        }

        // 【步骤 3】获取已上桌的桌号列表（格式："16,18" → ["16", "18"]）
        String servedTables = item.getServedTableDisplayId();
        List<String> alreadyServedTables = (servedTables != null && !servedTables.isEmpty())
                ? Arrays.asList(servedTables.split(","))
                : Collections.emptyList();


        // 【步骤 4】【核心修复】检查当前桌号是否已上桌，避免重复更新
        String currentTableTrimmed = currentTableNumber.trim();
        if (alreadyServedTables.contains(currentTableTrimmed)) {
            System.out.println("⚠️ 桌号 " + currentTableTrimmed + " 已上桌，跳过重复更新");
            return;  //  关键：直接返回，不执行后续更新逻辑
        }


        // 【步骤 5】【核心规则】验证上桌顺序：相邻性校验（禁止任何"跳过"行为）
        int currentNum = Integer.parseInt(currentTableTrimmed.replaceAll("[^0-9]", ""));

        if (!alreadyServedTables.isEmpty()) {
            // 将已上桌桌号转换为数字列表，用于相邻性计算
            List<Integer> servedNums = alreadyServedTables.stream()
                    .map(s -> Integer.parseInt(s.replaceAll("[^0-9]", "")))
                    .collect(Collectors.toList());

            // 【核心】检查当前桌号是否与任意已上桌桌号相邻（差值=1）
            boolean hasAdjacentServed = false;
            for (Integer servedNum : servedNums) {
                if (Math.abs(currentNum - servedNum) == 1) {
                    hasAdjacentServed = true;
                    break;
                }
            }

            if (!hasAdjacentServed) {
                //  不满足相邻条件，说明跳过了中间的桌号
                throw new IllegalStateException(
                        "聚餐桌一键上桌不能跳过桌号！\n" +
                                "当前桌号：" + currentTableNumber + "\n" +
                                "已上桌桌号：" + String.join(",", alreadyServedTables) + "\n\n" +
                                "💡 提示：请按桌号顺序上桌，相邻桌号才能操作");
            }

            //  相邻验证通过，记录日志
            System.out.println(" 桌号 " + currentTableNumber +
                    " 与已上桌桌号相邻，允许上桌");
        }


        // 【步骤 6】【核心修改】检查 quantity_distribution 并计算每桌份额
        int totalQuantity = item.getQuantity();
        int perTableQuantity;  // 每桌应上桌的份额

        String quantityDistribution = item.getQuantityDistribution();
        // 【DEBUG 1】打印原始 distribution 值
//        System.out.println(" [DEBUG] itemCode=" + item.getItemCode() +
//                ", quantityDistribution=[" + quantityDistribution + "]");

        if (quantityDistribution == null || quantityDistribution.isEmpty()) {
            // ── 情况 1：distribution 为 null/空 → 使用【无余数整除】计算
            if (totalQuantity % tableCount != 0) {
                throw new IllegalStateException(
                        "共享菜品总数量 (" + totalQuantity + ") 不能被桌子数量 (" + tableCount + ") 整除！");
            }
            perTableQuantity = totalQuantity / tableCount;
            System.out.println(" distribution 为空，使用整除计算：每桌 " + perTableQuantity + " 份");
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
                System.out.println(" distribution 解析失败，兜底使用整除计算：每桌 " + perTableQuantity + " 份");
            } else {
                //  检查 distribution 中的数量是否一致（均匀性校验）
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
                    //  数量一致 → 依旧使用【无余数整除】计算
                    if (totalQuantity % tableCount != 0) {
                        throw new IllegalStateException(
                                "共享菜品总数量 (" + totalQuantity + ") 不能被桌子数量 (" + tableCount + ") 整除！");
                    }
                    perTableQuantity = totalQuantity / tableCount;
                    System.out.println(" distribution 均匀，使用整除计算：每桌 " + perTableQuantity + " 份");
                } else {
                    // 【关键修改】抛出带关键词的异常，供上层识别
                    throw new IllegalArgumentException(
                            "分配数量不均匀：" + item.getItemCode() +
                                    " 在各桌分配不一致: " + quantityDistribution);  //  包含"分配数量不均匀"关键词
                }
            }
        }

        //  校验：输入数量必须等于每桌份额（自动修正，不报错）
        if (inputQuantity != perTableQuantity) {
            System.out.println("⚠️ [自动修正] 共享菜品数量：传入=" + inputQuantity +
                    " -> 实际执行每桌份额=" + perTableQuantity +
                    " (总" + totalQuantity + "份 ÷ " + tableCount + "桌)");
        }


        // 【步骤 7】更新 served_quantity 和 served_table_display_id
        int newServedQuantity = item.getServedQuantity() + perTableQuantity;
        String newServedTables = (servedTables == null || servedTables.isEmpty())
                ? currentTableTrimmed
                : servedTables + "," + currentTableTrimmed;

        String newStatus = (newServedQuantity >= totalQuantity) ? "SERVED" : "PARTIALLY_SERVED";

        //  调用新 Mapper 方法，同时更新 served_quantity + served_table_display_id + status
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
     * 解析数量分配分布的 JSON 字符串为映射
     *
     * 功能说明：
     * 1. 校验输入字符串有效性，为空时返回 null
     * 2. 移除花括号与空格，按逗号分割键值对
     * 3. 提取桌号（键）与数量（值），存入有序映射保持原始顺序
     * 4. 解析失败时记录错误日志并返回 null，由调用方兜底处理
     *
     * @param jsonStr JSON 格式的分布字符串（如{"13":4,"14":4,"15":3}）
     * @return 桌号 - 数量映射；解析失败或输入为空时返回 null
     *
     * 应用场景：
     * - 聚餐桌共享菜品上桌时校验分配均匀性
     * - 订单明细展示时解析每桌分配数量
     *
     * 容错处理：
     * - 格式错误或数字解析失败时忽略该项，不影响其他项解析
     * - 返回 null 表示解析失败，调用方需实现兜底逻辑
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
            System.err.println(" 解析 quantity_distribution 失败: " + jsonStr + " | 错误: " + e.getMessage());
            return null;  // 解析失败返回 null，调用方兜底处理
        }
    }

    /**
     * 处理单桌菜品的上桌操作
     *
     * 功能说明：
     * 1. 校验菜品当前状态，若已全部上桌则抛出异常
     * 2. 计算新已上桌数量：原数量 + 输入数量，不超过菜品总数量
     * 3. 推导新状态：0 份→未上桌，部分→部分上桌，全部→已上桌
     * 4. 调用 Mapper 更新订单项的已上桌数量与状态字段
     *
     * @param item 待更新的订单项对象
     * @param inputQuantity 本次上桌的菜品数量
     *
     * 业务规则：
     * - 单桌菜品不涉及分配餐桌，直接累加上桌数量
     * - 状态自动推导，避免手动设置导致状态冲突
     *
     * 异常处理：
     * - 菜品已全部上桌时抛出 IllegalStateException，防止重复操作
     */
    private void handleSingleTableDishServing(OrderItem item, int inputQuantity) {
        int total = item.getQuantity();
        int served = item.getServedQuantity();
        String currentStatus = item.getStatus();

        if ("SERVED".equals(currentStatus)) {
            throw new IllegalStateException("该菜已全部上桌");
        }

        //  核心差异：不与 prepared_quantity 对比，直接累加
        int newServed = Math.min(served + inputQuantity, total);
        String newStatus = (newServed == 0) ? "UNSERVED" : (newServed < total ? "PARTIALLY_SERVED" : "SERVED");

        orderItemMapper.updateServedById(item.getOrderItemId(), newServed, newStatus);

        System.out.println(" 单桌菜品上桌成功：" + item.getItemCode() +
                "，数量：" + newServed + "/" + total);
    }

    /**
     * 一键标记餐桌所有菜品为已上桌
     *
     * 功能说明：
     * 1. 校验餐桌号有效性及存在性
     * 2. 查询餐桌关联的活跃订单
     * 3. 校验订单存在待上桌菜品，避免无效操作
     * 4. 批量更新订单下所有未上桌菜品的状态为"已上桌"
     * 5. 更新餐桌订单状态为"制作完成"
     *
     * @param tableNumber 餐桌显示编号
     *
     * 业务规则：
     * - 仅更新状态为"未上桌"或"部分上桌"的菜品
     * - 餐桌订单状态同步更新，确保界面展示一致
     *
     * 异常处理：
     * - 餐桌不存在、无活跃订单或无待上桌菜品时抛出相应异常
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
     * 标记外卖订单指定菜品为制作完成
     *
     * 功能说明：
     * 1. 校验订单号、菜品主键、制作数量有效性
     * 2. 通过订单号查询外卖订单及菜品当前状态
     * 3. 计算新已制作数量：原数量 + 输入数量，不超过菜品总数量
     * 4. 推导新状态：全部完成→"已上桌"，部分完成→"部分上桌"
     * 5. 调用 Mapper 更新菜品的已制作数量与状态
     *
     * @param orderNumber 外卖订单编号
     * @param itemId 待更新的菜品主键
     * @param quantity 本次标记完成的数量
     *
     * 业务规则：
     * - 外卖订单使用"制作完成"概念，对应堂食的"已上桌"状态
     * - 状态字段复用 served_quantity，保持数据模型统一
     *
     * 异常处理：
     * - 参数无效、订单不存在或菜品未找到时抛出相应异常
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
     * 一键标记外卖订单所有菜品为制作完成
     *
     * 功能说明：
     * 1. 校验订单号有效性及订单存在性
     * 2. 若订单已无待制作菜品：
     *    - 配送订单且状态为"未配送"时，自动更新为"送单中"
     *    - 其他情况直接返回，避免重复操作
     * 3. 若存在待制作菜品：批量更新所有菜品状态为"已上桌"
     * 4. 配送订单自动推进配送状态至"送单中"
     *
     * @param orderNumber 外卖订单编号
     *
     * 业务规则：
     * - 菜品全部完成后自动触发配送流程，减少人工操作
     * - 仅配送订单更新配送状态，自取订单保持原状态
     *
     * 异常处理：
     * - 订单号无效或订单不存在时抛出相应异常
     * - 无待更新菜品时静默返回，不视为错误
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

        // 【核心修改】如果没有待制作的菜品，检查是否需要自动更新配送状态
        if (!orderItemMapper.hasUnservedItems(orderId)) {
            System.out.println(" 订单 " + orderNumber + " 所有菜品已制作完成，检查配送状态...");

            // 仅配送订单且状态为"未配送"时，自动推进到"送单中"
            if ("DELIVERY".equals(order.getDeliveryMethod()) &&
                    order.getDeliveryStatus() == Order.DeliveryStatus.NOT_DELIVERED) {

                // 自动更新配送状态
                orderMapper.updateDeliveryStatus(orderId, Order.DeliveryStatus.DELIVERING.name());
                System.out.println(" 自动更新配送状态: " + orderNumber +
                        " [未配送 → 送单中]");


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

        // 【新增】如果是配送订单，自动将配送状态更新为"送单中"
        if ("DELIVERY".equals(order.getDeliveryMethod())) {
            orderMapper.updateDeliveryStatus(orderId, Order.DeliveryStatus.DELIVERING.name());
            System.out.println(" 配送状态已更新: " + orderNumber + " [未配送 → 送单中]");
        }

        System.out.println(" 外賣訂單全部標記完成 - 訂單號: " + orderNumber +
                ", 更新菜品數: " + updatedCount);
    }



    /**
     * 计算菜品撤销后的新状态
     *
     * 功能说明：
     * 1. 根据撤销数量计算新总数量与新已上桌数量
     * 2. 针对预约订单（未入座）、预约入座订单、普通堂食订单三种场景，分别推导状态
     * 3. 状态推导规则：
     *    - 新数量为 0：返回"未上桌"
     *    - 已上桌数量 ≥ 新总数量：返回"已上桌"或"已准备"
     *    - 已上桌数量 > 0 但 < 新总数量：返回"部分上桌"或"准备中"
     *
     * @param servedQty 原已上桌数量
     * @param originalQty 原总数量
     * @param cancelQuantity 撤销数量
     * @param originalStatus 原状态字符串
     * @param isReservationOrder 是否为预约订单
     * @param isReservationSeated 预约顾客是否已入座
     * @param cancelServedPart 是否撤销已上桌部分
     * @return 撤销后的新状态字符串（"UNSERVED"/"PREPARING"/"PREPARED"/"PARTIALLY_SERVED"/"SERVED"）
     *
     * 业务规则：
     * - 预约未入座订单仅使用准备相关状态，不涉及上桌状态
     * - 撤销已上桌部分时，已上桌数量直接减少；撤销未上桌部分时保持不变
     */
    private String calculateCancelledStatus(int servedQty, int originalQty, int cancelQuantity,
                                            String originalStatus,
                                            boolean isReservationOrder,
                                            boolean isReservationSeated,
                                            boolean cancelServedPart) {
        int newQty = Math.max(0, originalQty - cancelQuantity);

        // 【核心修复】计算新的已上桌数量，确保与 cancelOrderItem 中的逻辑完全一致
        int newServedQty;
        if ("PARTIALLY_SERVED".equals(originalStatus)) {
            if (cancelServedPart) {
                // 撤销已上桌部分：直接减少 served_quantity
                newServedQty = Math.max(0, servedQty - cancelQuantity);
            } else {
                // 撤销未上桌部分：served_quantity 保持不变
                newServedQty = servedQty;
            }
        } else {
            // 其他状态（SERVED/UNSERVED/PREPARING等）：已上桌数量不能超过新总数量
            newServedQty = Math.min(servedQty, newQty);
        }


        // 【情况 1】预约订单（客人未入座）→ 只能是 PREPARING/PREPARED/UNSERVED
        if (isReservationOrder && !isReservationSeated) {
            if (newQty == 0) {
                return "UNSERVED";                          // 全部撤销，状态重置
            } else if (newServedQty == 0) {
                return "UNSERVED";                          //  已准备 0 份 = 未准备
            } else if (newServedQty >= newQty) {
                return "PREPARED";                          // 剩余的全部已准备
            } else {
                return "PREPARING";                         // 部分准备中 (0 < served < total)
            }
        }


        // 【情况 2&3】预约入座（currentReservationId 存在）
        if (isReservationSeated) {
            if ("PREPARING".equals(originalStatus) || "PREPARED".equals(originalStatus) || "UNSERVED".equals(originalStatus)) {
                if (newQty == 0) return "UNSERVED";
                if (newServedQty == 0) return "UNSERVED";
                if (newServedQty >= newQty) return "PREPARED";
                return "PREPARING";
            } else if ("PARTIALLY_SERVED".equals(originalStatus) || "SERVED".equals(originalStatus)) {
                if (newQty == 0) return "UNSERVED";
                if (newServedQty >= newQty) return "SERVED";
                if (newServedQty > 0) return "PARTIALLY_SERVED";
                return "UNSERVED";
            } else {
                return "PREPARING";
            }
        }

        // 【情况 4】普通堂食订单（无 currentReservationId）
        if (newQty == 0) {
            return "UNSERVED";                              // 全部撤销
        } else if (newServedQty == 0) {
            return "UNSERVED";                              // 【核心修复】没有已上桌的 -> UNSERVED
        } else if (newServedQty >= newQty) {
            return "SERVED";                                // 剩余的全部已上桌
        } else if (newServedQty > 0) {
            return "PARTIALLY_SERVED";                      // 部分上桌
        } else {
            return "UNSERVED";                              // 没有已上桌的
        }
    }

    /**
     * 撤销堂食订单中的指定菜品
     *
     * 功能说明：
     * 1. 校验基础参数：餐桌号、菜品编码、撤销数量、撤销部分标识有效性
     * 2. 查询餐桌、活跃订单、菜品信息，获取当前上桌状态
     * 3. 判断订单类型：预约订单/预约入座订单/普通堂食订单，采用不同状态推导规则
     * 4. 校验撤销数量：部分上桌菜品需分别校验已上桌与未上桌数量上限
     * 5. 计算新数量、新已上桌数量与新状态
     * 6. 执行撤销操作：
     *    - 新数量为 0：记录审计日志（若已上桌）并删除订单项
     *    - 新数量大于 0：更新订单项数量、已上桌数量与状态
     * 7. 重算订单总金额并更新数据库
     * 8. 若订单无剩余明细，自动删除空订单
     *
     * @param tableNumber 餐桌显示编号
     * @param itemCode 待撤销的菜品编码
     * @param cancelQuantity 撤销数量
     * @param cancellationReason 撤销原因说明
     * @param cancelServedPart 撤销部分标识（"SERVED"表示撤销已上桌部分，其他值表示撤销未上桌部分）
     *
     * 异常处理：
     * - 参数校验失败、餐桌/订单/菜品不存在或撤销数量超限时抛出相应异常
     * - 事务自动回滚，确保数据一致性
     */
    @Transactional
    public void cancelOrderItem(String tableNumber, String itemCode, int cancelQuantity,
                                String cancellationReason, String cancelServedPart) throws SQLException {
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

        //  验证 cancelServedPart 参数
        if (cancelServedPart == null || cancelServedPart.trim().isEmpty()) {
            throw new IllegalArgumentException("cancelServedPart 参数不能为空");
        }

        //  将字符串转换为 boolean 逻辑
        // 支持值: "SERVED"/"true"/"1" 表示删除已上桌部分；其他值表示删除未上桌部分
        boolean shouldCancelServed = "SERVED".equalsIgnoreCase(cancelServedPart.trim()) ||
                "true".equalsIgnoreCase(cancelServedPart.trim()) ||
                "1".equals(cancelServedPart.trim());

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

        // ===== 6. 【核心】判斷訂單類型和入座狀態 =====
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
        int unservedQty = totalQty - servedQty;

        String originalStatus = orderItemMapper.getItemStatus(orderId, itemId);

        // 【核心修复】处理 PARTIALLY_SERVED 状态的撤销逻辑
        if ("PARTIALLY_SERVED".equals(originalStatus)) {
            if (shouldCancelServed) {
                if (cancelQuantity > servedQty) {
                    throw new IllegalArgumentException(
                            "菜品 " + itemCode + " 已上桌數量為 " + servedQty +
                                    "，撤銷數量 (" + cancelQuantity + ") 不能超過已上桌數量！");
                }
            } else {
                if (cancelQuantity > unservedQty) {
                    throw new IllegalArgumentException(
                            "菜品 " + itemCode + " 未上桌數量為 " + unservedQty +
                                    "，撤銷數量 (" + cancelQuantity + ") 不能超過未上桌數量！");
                }
            }
        }

        // 【核心修复】使用 calculateCancelledStatus 計算新狀態 应为5 个实参，但实际为 7 个
        String newStatus = calculateCancelledStatus(
                servedQty, totalQty, cancelQuantity, originalStatus,
                isReservationOrder, isReservationSeated, shouldCancelServed);

        int newQty = Math.max(0, totalQty - cancelQuantity);
        // 【核心】根据取消部分计算新的已上桌数量
        int newServedQty;
        if ("PARTIALLY_SERVED".equals(originalStatus)) {
            if (shouldCancelServed) {
                //  撤銷已上桌部分：直接減少 served_quantity
                newServedQty = Math.max(0, servedQty - cancelQuantity);
            } else {
                //  撤銷未上桌部分：served_quantity 保持不變
                newServedQty = servedQty;
            }
        } else {
            // 其他狀態的兜底邏輯
            newServedQty = Math.min(servedQty, newQty);
        }

        System.out.println(" 菜品 " + itemCode +
                " 原:" + originalStatus + "(" + servedQty + "/" + totalQty + ")" +
                " - 撤銷:" + cancelQuantity + " (cancelServedPart=" + cancelServedPart + ")" +
                " → 新狀態:" + newStatus + "(" + newServedQty + "/" + newQty + ")");

        // ===== 8. 【核心修復】執行撤銷操作 =====
        if (newQty == 0) {
            if ("SERVED".equals(originalStatus) || "PARTIALLY_SERVED".equals(originalStatus)) {
                orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                        cancellationReason != null ? cancellationReason : "用戶撤銷",
                        originalStatus);
            }
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
        }

        // ===== 9. 重新計算訂單總金額 =====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal); //  同步更新 items_total
        }

        // ===== 10. 檢查訂單是否為空 → 刪除空訂單 =====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
        }

        System.out.println(" 撤銷成功 - 餐桌:" + tableNumber + ", 菜品:" + itemCode + ", 數量:" + cancelQuantity);
    }


    /**
     * 撤销外卖订单中的指定菜品
     *
     * 功能说明：
     * 1. 校验订单号、菜品主键、撤销数量有效性
     * 2. 通过订单号查询外卖订单，获取菜品当前上桌状态
     * 3. 计算新数量与新已上桌数量：已上桌数量不超过新总数量
     * 4. 执行撤销操作：
     *    - 新数量为 0：记录审计日志并删除订单项
     *    - 新数量大于 0：更新订单项数量、已上桌数量与状态
     * 5. 重算订单总金额并更新数据库
     * 6. 若订单无剩余明细，自动删除空订单
     *
     * @param orderNumber 外卖订单编号
     * @param itemId 待撤销的菜品主键
     * @param quantity 撤销数量
     * @param cancellationReason 撤销原因说明
     *
     * 业务规则：
     * - 外卖订单不涉及餐桌分配，状态推导仅基于上桌数量
     * - 已上桌菜品的撤销需记录审计日志，便于财务对账
     *
     * 异常处理：
     * - 参数无效、订单不存在或菜品未找到时抛出相应异常
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
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }

        // 7. 檢查訂單是否為空 → 刪除空訂單
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
        }

        System.out.println(" 外賣撤銷成功 - 訂單號:" + orderNumber + ", 菜品:" + itemId);
    }

    /**
     * 撤销整个外卖订单
     *
     * 功能说明：
     * 1. 校验订单号有效性
     * 2. 通过订单号查询外卖订单
     * 3. 记录整单撤销审计日志：类型为"ORDER"，金额为订单总额
     * 4. 删除订单所有明细记录
     * 5. 删除订单主表记录
     *
     * @param orderNumber 外卖订单编号
     * @param reason 撤销原因说明
     *
     * 业务规则：
     * - 整单撤销不校验菜品状态，直接删除所有明细
     * - 审计日志记录订单级撤销，与菜品级撤销区分
     *
     * 异常处理：
     * - 订单号无效或订单不存在时抛出相应异常
     */
    @Transactional
    public void cancelTakeoutOrder(String orderNumber, String reason)  {
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


    /**
     * 更新外卖订单的配送状态
     *
     * 功能说明：
     * 1. 校验订单号与配送状态参数有效性
     * 2. 查询订单并验证其配送方式为"配送"，避免误操作堂食订单
     * 3. 调用 Mapper 更新订单的配送状态字段
     *
     * @param orderNumber 外卖订单编号
     * @param newStatus 新的配送状态枚举值（如 NOT_DELIVERED/DELIVERED）
     *
     * 异常处理：
     * - 参数为空、订单不存在或非配送订单时抛出相应异常
     * - 数据库更新失败时抛出 RuntimeException，确保状态变更原子性
     */
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

    /**
     * 更新订单的配送费并重新计算总金额
     *
     * 功能说明：
     * 1. 校验订单主键与配送费参数有效性
     * 2. 查询订单获取菜品总额，计算新总金额 = 菜品总额 + 新配送费
     * 3. 调用 Mapper 更新配送费字段，总金额由数据库触发器或应用层同步更新
     *
     * @param orderId 订单主键
     * @param newDeliveryFee 新的配送费金额
     *
     * 业务规则：
     * - 配送费不可为负数，避免金额计算错误
     * - 总金额自动重算，确保订单财务数据一致性
     *
     * 异常处理：
     * - 订单不存在或更新失败时抛出相应异常
     */
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

    /**
     * 计算撤销操作后的菜品状态
     *
     * 功能说明：
     * 根据撤销后的新总数量与已上桌数量，推导菜品当前状态：
     * - 新数量为 0：返回"未上桌"
     * - 已上桌数量 ≥ 新总数量：返回"已上桌"
     * - 已上桌数量 > 0 但 < 新总数量：返回"部分上桌"
     * - 其他情况：返回"未上桌"
     *
     * @param newTotalQty 撤销后的菜品总数量
     * @param servedQty 当前已上桌数量
     * @return 推导后的状态字符串（"UNSERVED"/"PARTIALLY_SERVED"/"SERVED"）
     *
     * 应用场景：
     * - 顾客撤销部分菜品后自动更新订单项状态
     * - 确保界面展示与后端状态逻辑一致
     */
    // 辅助方法：计算撤销后状态
    private String calculateStatusAfterCancel(int newTotalQty, int servedQty) {
        if (newTotalQty <= 0) return "UNSERVED";
        if (servedQty >= newTotalQty) return "SERVED";
        if (servedQty > 0) return "PARTIALLY_SERVED";
        return "UNSERVED";
    }

    /**
     * 处理外卖订单结账流程
     *
     * 功能说明：
     * 1. 查询订单并校验存在性
     * 2. 状态校验：
     *    - 已结账订单禁止重复结账
     *    - 自取订单需制作完成（ORDERED_FINISHED）方可结账
     *    - 配送订单需已送达（DELIVERED）方可结账
     * 3. 校验支付金额是否不小于订单总额
     * 4. 执行结账操作：
     *    - 更新订单状态为已结账
     *    - 记录当日营收与外卖订单计数
     *    - 记录季度销售统计
     *    - 删除订单明细与主表记录，完成数据清理
     * 5. 返回结账结果：成功标志、找零金额、订单总额、营收日期
     *
     * @param orderNumber 外卖订单编号
     * @param paymentAmount 顾客实际支付金额
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - changeAmount: double，找零金额
     *         - totalAmount: double，订单总额
     *         - revenueDate: Date，营收计入日期
     *
     * 业务规则：
     * - 自取与配送订单的结账前置条件不同，需分别校验
     * - 结账后自动清理订单数据，避免历史数据冗余
     *
     * 异常处理：
     * - 状态校验失败时直接返回错误结果，提示具体原因
     * - 系统异常时记录日志并返回友好提示
     */
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

            //  ===== 2. 加强结账前状态验证 =====
            String deliveryMethod = order.getDeliveryMethod();

            // 先检查是否已结账（最优先）
            if ("CHECKED_OUT".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "订单已结账，不能重复结账");
                return result;
            }

            if ("PICKUP".equals(deliveryMethod)) {
                //  自取订单：检查制作状态
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
                //  配送订单：检查配送状态
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

    /**
     * 根据日期计算所属季度
     *
     * 功能说明：
     * 将月份映射为季度标识：1-3 月→Q1，4-6 月→Q2，7-9 月→Q3，10-12 月→Q4，
     * 用于销售统计与经营报表的季度分组。
     *
     * @param date 待计算的日期对象
     * @return 季度字符串（"Q1" / "Q2" / "Q3" / "Q4"）
     */
    private String getQuarterFromDate(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    /**
     * 获取订单详情（支持堂食与外卖）
     *
     * 功能说明：
     * 1. 堂食订单：通过餐桌号查询关联活跃订单，返回订单时间、总金额、菜品明细及金额构成
     * 2. 外卖订单：通过订单号直接查询订单，返回相同结构的详情数据
     * 3. 补充金额明细字段：itemsTotal（菜品总额）、deliveryFee（配送费）、deliveryMethod（配送方式）
     *
     * @param orderType 订单类型（"DINE_IN" 或 "TAKEOUT"）
     * @param identifier 堂食时为餐桌显示编号，外卖时为订单号
     * @return 订单详情映射，包含以下字段：
     *         - orderTime: LocalDateTime，下单时间
     *         - totalAmount: Double，应付总额
     *         - itemsTotal: Double，菜品总额
     *         - deliveryFee: Double，配送费
     *         - deliveryMethod: String，配送方式
     *         - items: List<Map>，菜品明细列表
     *         - error: String，查询失败时的错误信息
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

                // =====  新增：补充金额明细字段（修复下方金额显示为 0 的问题）=====
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

                // =====  新增：补充金额明细字段（修复下方金额显示为 0 的问题）=====
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
     * 将订单项列表转换为视图层可用的映射列表
     *
     * 功能说明：
     * 遍历 OrderItem 对象，提取菜品编码、名称、数量、已上桌数量、单价、小计金额与状态，
     * 封装为 Map 列表供前端直接渲染，避免视图层依赖实体类结构。
     *
     * @param items 订单项对象列表
     * @return 映射列表，每个元素包含：itemCode/itemName/quantity/servedQuantity/price/subtotal/status
     *
     * 容错处理：
     * - 输入为 null 时返回空列表，避免空指针异常
     * - 小计金额自动计算：数量 × 单价，确保视图展示准确
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
     * 根据订单号查询订单状态
     *
     * 功能说明：
     * 通过订单号查询其当前订单状态枚举值，用于界面状态展示与业务流程校验。
     *
     * @param orderNumber 订单编号字符串
     * @return 订单状态枚举（如 NO_ORDER/ORDERED/CHECKED_OUT）；参数为空时返回默认值 NO_ORDER
     *
     * 应用场景：
     * - 结账前校验订单是否已点餐
     * - 重单时判断订单是否曾结账
     * - 界面动态显示订单当前进度
     */
    @Transactional(readOnly = true)
    public Tables.OrderStatus getOrderStatusByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return Tables.OrderStatus.NO_ORDER;
        }
        return orderMapper.getOrderStatusByOrderNumber(orderNumber.trim());
    }


    /**
     * 重置已结账订单为可重新点餐状态
     *
     * 功能说明：
     * 1. 校验订单主键有效性，为空时直接返回
     * 2. 调用 Mapper 更新订单状态为"已点餐"、金额清零，并记录重单时间为当前时间
     *
     * @param orderId 待重置的订单主键
     *
     * 业务规则：
     * - 重单时间（reorder_time）用于区分首次下单与重新点餐，确保营收统计准确
     * - 金额清零避免重复计费，后续点餐将重新累加
     *
     * 执行时机：
     * - 顾客结账后要求重新点餐时调用
     * - 确保订单可继续添加菜品且财务数据可追溯
     */
    @Transactional
    public void resetCheckedOutOrder(Integer orderId) {
        if (orderId == null) return;

        orderMapper.updateOrderForReorder(
                orderId,
                "ORDERED",    // 状态
                0.0,          // 金额清零
                LocalDateTime.now()  //  设置重单时间为当前时间
        );

        System.out.println(" 订单重置成功: orderId=" + orderId);
    }

    /**
     * 根据餐桌主键查询最近的已结账订单
     *
     * 功能说明：
     * 查询指定餐桌最近一次状态为"已结账"的订单主键，用于支持重新点餐场景。
     *
     * @param tableId 餐桌数据库主键
     * @return 已结账订单主键；无匹配记录时返回 null
     *
     * 应用场景：
     * - 顾客结账后要求加菜时，加载原订单明细
     * - 校验餐桌是否处于可重新点餐状态
     */
    @Transactional(readOnly = true)
    public Integer findCheckedOutOrderIdByTableId(Integer tableId) {
        if (tableId == null) return null;
        return orderMapper.findCheckedOutOrderIdByTableId(tableId);
    }


    /**
     * 取消重新点餐并恢复订单为已结账状态
     *
     * 功能说明：
     * 1. 校验餐桌号有效性及存在性
     * 2. 查询餐桌关联的活跃订单，校验是否存在
     * 3. 验证订单是否为重新点餐场景（原订单曾结账），避免误操作全新订单
     * 4. 验证订单无已上桌菜品，防止撤销已消费内容
     * 5. 执行恢复操作：更新订单状态为"已结账"、金额清零、记录重单时间
     * 6. 可选：清空订单明细，确保后续点餐从空开始
     *
     * @param tableNumber 餐桌显示编号
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - tableNumber: String，返回餐桌号供调用方刷新界面
     *
     * 业务规则：
     * - 仅允许撤销未上桌的重新点餐订单，保障顾客权益
     * - 恢复后订单状态与结账后一致，支持正常离店流程
     *
     * 异常处理：
     * - 参数校验失败时直接返回错误结果
     * - 系统异常时记录日志并返回友好提示，不暴露技术细节
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

            //  调用新方法：状态+金额+重单时间 三合一更新
            orderMapper.updateOrderForReorder(
                    orderId,
                    "CHECKED_OUT",    // 重置为已结账
                    0.0,              // 金额清零
                    now               //  更新重单时间为当前时间
            );

            // 6.1 可选：清空订单明细（根据业务需求决定）
            orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // ===== 7. 返回成功结果 =====
            result.put("success", true);
            result.put("message", "餐桌 " + tableNumber + " 已恢复为已结账状态");
            result.put("tableNumber", tableNumber);  //  返回餐桌号供调用方刷新
            System.out.println(" 已取消重新点餐 - 餐桌: " + tableNumber);

        } catch (Exception e) {
            // 系统异常：记录日志 + 返回错误
            result.put("success", false);
            result.put("message", "系统错误: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 根据预约号查询预点餐订单
     *
     * 功能说明：
     * 查询指定预约记录关联的预点餐订单对象，用于加载顾客提前点选的菜品。
     *
     * @param reservationId 预约记录唯一标识
     * @return 预点餐订单对象；无关联订单时返回 null
     *
     * 数据来源：
     * - 直接查询数据库 table_orders 表，条件：reservation_id 匹配且订单类型为"RESERVATION"
     *
     * 应用场景：
     * - 顾客入座时加载预点餐明细到正式订单
     * - 预约修改时同步更新关联订单的预付信息
     */
    @Transactional(readOnly = true)
    public Order findPreOrderByReservationId(String reservationId) {
        return orderMapper.findPreOrderByReservationId(reservationId);
    }


    /**
     * 根据预约号加载正式订单的菜品明细
     *
     * 功能说明：
     * 1. 校验预约号参数有效性，为空时返回空列表
     * 2. 调用 Mapper 查询该预约关联的所有订单项记录
     *
     * @param reservationId 预约记录唯一标识
     * @return 订单项列表；参数为空或无关联订单时返回空列表
     *
     * 应用场景：
     * - 顾客入座后加载预点餐菜品到正式订单界面
     * - 结账前展示订单明细供顾客确认
     */
    @Transactional(readOnly = true)
    public List<OrderItem> loadFormalOrderItemsByReservationId(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return orderMapper.findOrderItemsByReservationId(reservationId);
    }


    /**
     * 更新预约订单菜品的准备进度
     *
     * 功能说明：
     * 1. 校验参数有效性：预约号、订单项 ID、菜品编码、准备数量
     * 2. 精确查询订单项并校验其归属预约记录，防止越权操作
     * 3. 校验已准备数量不超过菜品总数量，避免数据逻辑冲突
     * 4. 更新订单项的已准备数量与状态字段（如"PREPARING"→"PREPARED"）
     *
     * @param reservationId 预约记录唯一标识
     * @param orderItemId 待更新的订单项主键
     * @param itemCode 菜品编码（用于日志记录）
     * @param preparedQty 已准备的数量
     * @param newStatus 更新后的状态值
     * @param assignedTableDisplayId 分配的餐桌列表（用于日志或后续逻辑）
     *
     * 异常处理：
     * - 参数无效、订单项不存在或归属校验失败时抛出相应异常
     * - 确保厨房准备进度更新操作的准确性与安全性
     */
    @Transactional
    public void updateReservationOrderItemPrepared(
            String reservationId,
            Integer orderItemId,
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

        // 【修改】通过 orderItemId 精确查询单条记录
        OrderItem orderItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (orderItem == null)
            throw new IllegalStateException("订单项不存在: #" + orderItemId);

        //  验证：预约号匹配（安全防护）
        Order order = orderMapper.findById(orderItem.getOrderId());
        if (order == null || !reservationId.equals(order.getReservationId())) {
            throw new IllegalStateException("订单项 #" + orderItemId +
                    " 不属于预约 " + reservationId);
        }

        // 【修改】验证：已准备数量不能超过该记录的总数量
        if (preparedQty > orderItem.getQuantity()) {
            throw new IllegalArgumentException(
                    "已准备数量 (" + preparedQty + ") 不能超过菜品总数量 (" +
                            orderItem.getQuantity() + ")");
        }

        // 【修改】调用新方法：通过 orderItemId 精确更新
        orderItemMapper.updatePreparedQuantityById(
                orderItemId,
                preparedQty,
                newStatus
        );

        System.out.println(" 菜品准备进度已更新: " + itemCode +
                " (orderItemId=#" + orderItemId + ") → " +
                preparedQty + "/" + orderItem.getQuantity() +
                " (" + newStatus + ")");
    }

    /**
     * 撤销预约订单中的菜品并处理确认逻辑
     *
     * 功能说明：
     * 1. 校验基础参数：预约号、菜品 ID、撤销数量有效性
     * 2. 查询预约订单及菜品当前上桌状态
     * 3. 计算新数量与新已上桌数量：已上桌数量不超过新总数量
     * 4. 执行撤销操作：
     *    - 新数量为 0：记录审计日志（若已上桌）并删除订单项
     *    - 新数量大于 0：更新订单项数量、已上桌数量与状态
     * 5. 重算订单总金额并更新数据库
     * 6. 若非部分撤销且为最后一个菜品，返回需确认标志供前端展示
     * 7. 若订单无剩余明细，自动删除空订单
     *
     * @param reservationId 预约记录唯一标识
     * @param itemId 待撤销的菜品主键
     * @param quantity 撤销数量
     * @param cancellationReason 撤销原因说明
     * @param cancelPart 是否仅撤销部分（字符串格式，支持"true"/"false"等）
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - needConfirm: boolean，是否为最后一个菜品需用户确认
     *         - orderId: Integer，关联订单主键（需确认时返回）
     *         - reservationId: String，预约记录编号（需确认时返回）
     *
     * 业务规则：
     * - 仅当非部分撤销且为最后一个菜品时才触发确认流程
     * - 已上桌菜品的撤销需记录审计日志，便于财务对账
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReservationOrderItem(
            String reservationId,
            int itemId,
            int quantity,
            String cancellationReason,
            String cancelPart) throws SQLException {

        Map<String, Object> result = new HashMap<>();

        // ===== 1-4. 基础验证 + 查询订单 + 计算新数量（保持不变）=====
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

        Order order = orderMapper.findPreOrderByReservationId(reservationId);
        if (order == null || order.getOrderId() == null) {
            result.put("success", false);
            result.put("message", "预约订单不存在: " + reservationId);
            return result;
        }
        Integer orderId = order.getOrderId();

        OrderItemServingStatus currentStatus = orderItemMapper.getServingStatus(orderId, itemId);
        if (currentStatus == null) {
            result.put("success", false);
            result.put("message", "订单中找不到菜品: " + itemId);
            return result;
        }

        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - quantity);
        int newServedQty = Math.min(servedQty, newQty);

        // ===== 5. 执行撤销（保持不变）=====
        if (newQty == 0) {
            String itemCode = orderItemMapper.getItemCodeByItemId(itemId);
            if ("SERVED".equals(currentStatus) || "PARTIALLY_SERVED".equals(currentStatus)) {
                orderItemMapper.recordCancellation(itemCode, quantity,
                        cancellationReason != null ? cancellationReason : "用户撤销",
                        String.valueOf(currentStatus));
            }
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            String itemCode = orderItemMapper.getItemCodeByItemId(itemId);
            //  将 String 转为 boolean 传入（支持 "true"/"false" 或 "1"/"0"）
            boolean isCancelPart = Boolean.parseBoolean(cancelPart);
            String newStatus = calculateCancelledStatus(servedQty, totalQty, quantity,
                    "UNSERVED", true, false, isCancelPart);
            orderItemMapper.updateOrderItemAfterCancel(
                    orderId, itemId, newQty, newServedQty, newStatus);
            System.out.println(" 菜品部分撤销成功 -> " + itemCode +
                    " 原:" + totalQty + " → 新:" + newQty +
                    " (状态:" + newStatus + ", cancelPart:" + cancelPart + ")");
        }

        // ===== 6. 重新计算订单总金额（保持不变）=====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderItemMapper.updateOrderTotalAmount(orderId, newTotal);
        }

        // 【核心修改】7. 检查是否为最后一个菜品（仅当 cancelPart=false 时执行）
        //  将 String 转为 boolean 进行判断
        boolean shouldCheckLastItem = !Boolean.parseBoolean(cancelPart);
        if (shouldCheckLastItem) {  //  关键：只有非部分撤销时才检查确认
            boolean isLastItem = false;
            if (newQty == 0) {
                List<OrderItem> remainingItems = orderMapper.findOrderItemsByReservationId(reservationId);
                if (remainingItems != null) {
                    long otherItemCount = remainingItems.stream()
                            .filter(item -> item.getItemId() != itemId)
                            .count();
                    isLastItem = (otherItemCount == 0);
                }
            }
            if (isLastItem) {
                result.put("success", true);
                result.put("needConfirm", true);
                result.put("orderId", orderId);
                result.put("reservationId", reservationId);
                result.put("message", "这是最后一个菜品，是否保留预约订单？");
                return result;
            }
        }

        // ===== 8. 检查订单是否为空 → 删除空订单（保持不变）=====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
        }

        result.put("success", true);
        result.put("needConfirm", false);
        result.put("message", "预约订单撤销成功");
        return result;
    }


    /**
     * 确认删除预约订单
     *
     * 功能说明：
     * 1. 将订单总金额更新为 0，确保财务数据一致性
     * 2. 删除订单所有明细记录
     * 3. 删除订单主表记录
     * 4. 更新预约记录的预点餐标记为否，解除预约与订单的关联
     *
     * @param reservationId 预约记录唯一标识
     * @param orderId 待删除的订单主键
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *
     * 执行时机：
     * - 用户确认不保留预约订单时调用
     * - 确保订单删除后预约记录可正常复用或取消
     *
     * 异常处理：
     * - 删除失败时记录日志并返回错误信息，不中断主流程
     */
    @Transactional
    public Map<String, Object> confirmDeleteReservationOrder(String reservationId, Integer orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 【核心修复】先更新 total_amount 为 0（在删除前）
            orderItemMapper.updateOrderTotalAmount(orderId, 0.0);
            System.out.println(" 已将订单总金额更新为 0: orderId=" + orderId);

            // 1. 删除订单明细
            orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // 2. 删除订单主表
            orderMapper.deleteOrder(orderId);

            // 3. 【核心】更新 table_reservations 的 pre_order 从 1 改为 0
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
     * 确认保留预约订单
     *
     * 功能说明：
     * 1. 校验订单是否已无剩余菜品，若有则跳过状态更新
     * 2. 使用乐观锁将订单状态从"已点餐"更新为"未点餐"，允许顾客继续点餐
     * 3. 可选：将订单总金额清零，保持数据一致性
     *
     * @param reservationId 预约记录唯一标识
     * @param orderId 待处理的订单主键
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *
     * 业务规则：
     * - 仅当订单无剩余菜品时才执行状态回退，避免数据冲突
     * - 状态更新失败时记录日志但不中断流程，保证用户体验
     */
    @Transactional
    public Map<String, Object> confirmKeepReservationOrder(String reservationId, Integer orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 【核心修復 1】先確認訂單確實沒有剩餘菜品（防禦性檢查）
            boolean hasItems = orderItemMapper.hasRemainingItems(orderId);
            if (hasItems) {
                System.out.println(" 訂單 " + orderId + " 仍有菜品，跳過狀態更新");
            } else {
                // 【核心修復 2】將訂單狀態從 ORDERED → NO_ORDER
                int updated = orderMapper.updateOrderStatusOnly(orderId, "NO_ORDER", "ORDERED");
                if (updated > 0) {
                    System.out.println(" 訂單狀態已更新: orderId=" + orderId +
                            ", ORDERED → NO_ORDER");
                } else {
                    // 可能狀態已不是 ORDERED，記錄日誌但不拋異常
                    System.out.println(" 訂單 " + orderId + " 狀態可能已變更，跳過更新");
                }
            }

            // 【核心修復 3】可選：將金額也清零（保持一致性）
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
     * 撤销预约订单中的菜品并处理确认逻辑
     *
     * 功能说明：
     * 1. 校验基础参数：餐桌号、菜品编码、撤销数量有效性
     * 2. 查询餐桌、预约订单、菜品信息，获取当前菜品上桌状态
     * 3. 解析 cancelServedPart 参数，判断是否撤销已上桌部分
     * 4. 计算新数量与新已上桌数量：
     *    - 若仅撤销未上桌部分：已上桌数量保持不变
     *    - 若撤销已上桌部分：已上桌数量不超过新总数量
     * 5. 执行撤销操作：
     *    - 新数量为 0：记录审计日志（若已上桌）并删除订单项
     *    - 新数量大于 0：更新订单项数量、已上桌数量与状态
     * 6. 重算订单总金额并更新数据库
     * 7. 若为预约订单的最后一个菜品，返回需确认标志供前端展示
     * 8. 若订单无剩余明细，自动删除空订单
     *
     * @param tableNumber 餐桌显示编号
     * @param itemCode 待撤销的菜品编码
     * @param cancelQuantity 撤销数量
     * @param cancellationReason 撤销原因说明
     * @param reservationId 预约记录唯一标识
     * @param cancelServedPart 是否撤销已上桌部分的字符串标识（支持"true"/"YES"/"1"等格式）
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - needConfirm: boolean，是否为最后一个菜品需用户确认
     *         - orderId: Integer，关联订单主键（需确认时返回）
     *         - reservationId: String，预约记录编号（需确认时返回）
     *
     * 异常处理：
     * - 参数校验失败时直接返回错误结果，不抛出异常
     * - 数据库操作失败时抛出 SQLException，由调用方统一处理
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReservationOrderItemWithConfirm(
            String tableNumber, String itemCode, int cancelQuantity,
            String cancellationReason, String reservationId, String cancelServedPart)  {

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

        // 【關鍵修復】額外查詢菜品狀態字符串
        String currentStatusStr = orderItemMapper.getItemStatus(orderId, itemId);

        // ===== 6. 解析 cancelServedPart 字符串為布爾值 =====
        // 支持："true"/"false", "YES"/"NO", "1"/"0" 等格式
        boolean shouldCancelServed = parseBooleanString(cancelServedPart);

        // ===== 7. 計算新數量和狀態 =====
        int totalQty = currentStatus.getQuantity();
        int servedQty = currentStatus.getServedQuantity();
        int newQty = Math.max(0, totalQty - cancelQuantity);

        // 【核心】计算新的已上桌数量（根据 shouldCancelServed 参数）
        int newServedQty;
        if ("PARTIALLY_SERVED".equals(currentStatusStr) && !shouldCancelServed) {
            // 删除未上桌部分：已上桌数量保持不变
            newServedQty = servedQty;
        } else {
            // 删除已上桌部分或其他情况：已上桌数量不能超过新总数量
            newServedQty = Math.min(servedQty, newQty);
        }

        // ===== 8. 執行撤銷操作 =====
        if (newQty == 0) {
            //  完全撤銷：記錄審計 + 刪除明細
            if ("SERVED".equals(currentStatusStr) || "PARTIALLY_SERVED".equals(currentStatusStr)) {
                orderItemMapper.recordCancellation(itemCode, cancelQuantity,
                        cancellationReason != null ? cancellationReason : "用戶撤銷",
                        currentStatusStr);
                System.out.println(" 已記錄撤銷審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            } else {
                System.out.println(" 準備中的菜品直接刪除，不記錄審計：菜品 " + itemCode + " 狀態=" + currentStatusStr);
            }
            orderItemMapper.deleteOrderItem(orderId, itemId);
        } else {
            //  部分撤銷：更新數量和狀態
            String newStatus = calculateCancelledStatus(
                    servedQty,
                    totalQty,
                    cancelQuantity,
                    currentStatusStr,
                    "RESERVATION".equals(order.getOrderType()),
                    table.getCurrentReservationId() != null,
                    shouldCancelServed  // 传入解析后的布尔值
            );
            orderItemMapper.updateOrderItemAfterCancel(orderId, itemId, newQty, newServedQty, newStatus);
        }

        // ===== 9. 重新計算訂單總金額 =====
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);

        }

        // ===== 10. 檢查是否為最後一個菜品 =====
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

        // ===== 11. 如果需要確認，返回標誌 =====
        if (isLastItem) {
            result.put("success", true);
            result.put("needConfirm", true);
            result.put("orderId", orderId);
            result.put("reservationId", reservationId);
            result.put("currentOrderStatus", order.getStatus());
            result.put("message", "這是預約訂單的最後一個菜品，是否保留預約？");
            return result;
        }

        // ===== 12. 檢查訂單是否為空 → 刪除空訂單 =====
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
            System.out.println(" 空訂單已自動刪除: orderId=" + orderId);
        }

        result.put("success", true);
        result.put("needConfirm", false);
        result.put("message", "撤銷成功");
        return result;
    }

    /**
     * 将字符串解析为布尔值
     *
     * 功能说明：
     * 支持多种常见格式的布尔字符串解析，包括：
     * - "true"/"false"（不区分大小写）
     * - "YES"/"NO"（不区分大小写）
     * - "1"/"0"
     * - "Y"/"N"
     *
     * @param value 待解析的字符串
     * @return 解析成功的布尔值；输入为空或格式不匹配时返回 false
     *
     * 应用场景：
     * - 解析前端传来的开关参数（如是否撤销已上桌菜品）
     * - 兼容不同客户端的布尔值传递格式，提升接口健壮性
     */
    private boolean parseBooleanString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String trimmed = value.trim().toUpperCase();
        return "TRUE".equals(trimmed) || "YES".equals(trimmed) || "1".equals(trimmed) || "Y".equals(trimmed);
    }


    /**
     * 更新订单状态与金额字段
     *
     * 功能说明：
     * 调用 Mapper 批量更新订单主表的状态、菜品总额与应付总额字段，
     * 确保订单主记录与明细数据实时一致。
     *
     * @param orderId 订单主键
     * @param status 新状态值（如"ORDERED"/"NO_ORDER"）
     * @param itemsTotal 菜品总额
     * @param totalAmount 应付总额
     * @return 数据库影响行数（1=更新成功，0=记录不存在或状态未变更）
     *
     * 执行时机：
     * - 订单项增删改后同步更新订单主表
     * - 结账、撤销、合并等业务流程中确保金额准确
     */
    @Transactional
    public int updateOrderStatusAndTotals(Integer orderId, String status,
                                          Double itemsTotal, Double totalAmount) {
        return orderMapper.updateOrderStatusAndTotals(orderId, status, itemsTotal, totalAmount);
    }


    //  堂食订单号获取
    @Transactional(readOnly = true)
    public Integer getNextDineInOrderNumber(String dateStr) {
        Integer next = orderMapper.getNextOrderNumber("T", dateStr, null, "DINE_IN");
        return next != null ? next : 1;
    }

    /**
     * 将预点餐订单状态从未点餐升级为已点餐
     *
     * 功能说明：
     * 使用乐观锁机制更新订单状态，仅当当前状态为"未点餐"时才执行更新，
     * 避免并发请求导致的状态覆盖问题。
     *
     * @param orderId 订单主键
     * @return true=状态升级成功；false=订单不存在或状态已被其他请求修改
     *
     * 并发控制：
     * - 通过 WHERE 条件限定原状态，确保更新原子性
     * - 调用方需根据返回值判断是否需要重试或提示用户
     */
    @Transactional
    public boolean upgradePreOrderStatus(Integer orderId) {
        if (orderId == null) return false;

        // 使用樂觀鎖更新：只有當前狀態是 NO_ORDER 時才更新
        int updated = orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");

        if (updated > 0) {
            System.out.println(" 預點餐訂單狀態已升級: orderId=" + orderId + " [NO_ORDER → ORDERED]");
            return true;
        } else {
            System.out.println(" 訂單 " + orderId + " 狀態可能已被其他請求更新，跳過狀態升級");
            return false;
        }
    }

    /**
     * 获取下一个外卖订单序号
     *
     * 功能说明：
     * 调用 Mapper 查询指定日期、配送方式下的最大订单序号，返回下一个可用序号。
     *
     * @param prefix 订单号前缀（如 "T"）
     * @param dateStr 日期字符串（格式：yyyyMMdd）
     * @param deliveryMethod 配送方式（如 "DELIVERY"/"PICKUP"）
     * @return 下一个订单序号；查询无结果时返回 1
     *
     * 业务规则：
     * - 订单号格式：前缀 + 日期 + 序号（如 "T-20260528-001"）
     * - 序号按日期独立递增，确保每日订单号唯一
     */
    @Transactional(readOnly = true)
    public Integer getNextTakeoutOrderNumber(String prefix, String dateStr, String deliveryMethod) {
        Integer next = orderMapper.getNextOrderNumber(prefix, dateStr, deliveryMethod, "TAKEOUT");
        return next != null ? next : 1;
    }

    /**
     * 为聚餐桌添加点餐菜品
     *
     * 功能说明：
     * 1. 校验主桌存在性，查找关联订单（预约订单→堂食订单→新建订单）
     * 2. 若订单不存在：自动创建新堂食订单并插入所有菜品明细
     * 3. 若订单存在：
     *    - 查询现有未上桌明细，构建"菜品编码 + 标准化分配餐桌"复合键映射
     *    - 遍历新菜品，按复合键精确匹配：存在则标记为更新，不存在则标记为新增
     *    - 合并操作时：基于现有数量与分布计算增量，生成新的 distribution JSON
     *    - 新增操作时：若每桌数量≥2 则生成分布记录，否则返回 null
     * 4. 批量执行数据库操作：更新现有项 + 插入新项
     * 5. 更新订单状态为已点餐，并重算订单总金额
     *
     * @param mainTableDisplayId 聚餐桌主桌显示编号
     * @param orderItems 待添加的订单项列表
     * @param targetTableIds 目标餐桌编号列表（用于分配校验）
     *
     * 业务规则：
     * - 已上桌菜品不参与合并，避免状态冲突
     * - 分配餐桌列表按数字排序后作为匹配键，确保"7,8"与"8,7"视为相同
     * - 聚餐桌菜品数量必须能被桌数整除，确保平均分配
     *
     * 异常处理：
     * - 餐桌不存在、数量无法整除或数据库操作失败时抛出相应异常
     */
    @Transactional
    public void addOrderItemsForGroupedTable(String mainTableDisplayId, List<OrderItem> orderItems,
                                             List<String> targetTableIds) {

        Tables mainTable = tablesMapper.findByDisplayId(mainTableDisplayId);
        if (mainTable == null) throw new IllegalArgumentException("餐桌不存在: " + mainTableDisplayId);

        // 【步骤 1】查找订单（保持原有逻辑）
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

        // 步骤 2：查询现有明细 + 构建精确匹配 Map

        // 2.1 查询现有明细（只查未上桌的：UNSERVED/PREPARING/PREPARED）

        // ===== 替换为以下代码 =====
        if (orderId == null) {
            // ===== 无活跃订单 -> 自动创建新订单 =====
            System.out.println(" 餐桌 " + mainTableDisplayId + " 无活跃订单，自动创建新堂食订单...");

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

        // 【关键】使用标准化复合键：itemCode|sortedAssignedTableIds → 完整记录
        Map<String, Map<String, Object>> existingMap = new HashMap<>();
        for (Map<String, Object> row : existingItemsRaw) {
            String code = ((String) row.get("itemCode")).toUpperCase();
            String assigned = (String) row.get("assignedTableDisplayId");
            String status = (String) row.get("status");

            //  跳过已上桌的菜品
            if ("PARTIALLY_SERVED".equals(status) || "SERVED".equals(status)) {
                continue;
            }

            //  标准化 assigned_table_display_id（数字排序）
            String normalizedAssigned = (assigned != null && !assigned.isEmpty()) ?
                    sortTableIds(assigned) : null;

            //  复合键：itemCode|sortedAssignedTableIds
            String key = code + "|" + (normalizedAssigned != null ? normalizedAssigned : "");

            existingMap.put(key, row);  // 存储完整记录供后续使用
        }

        // 【核心修复】步骤 3：分类待处理项（更新 vs 新增）
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
            //  标准化 assigned_table_display_id（确保与数据库一致）
            String normalizedAssigned = (assignedTables != null && !assignedTables.isEmpty()) ?
                    sortTableIds(assignedTables) : null;

            newItem.setQuantity(originalItem.getQuantity());
            newItem.setAssignedTableDisplayId(normalizedAssigned);

            // 【核心】构建查找键（使用标准化后的 assigned）
            String key = originalItem.getItemCode().toUpperCase() + "|" +
                    (normalizedAssigned != null ? normalizedAssigned : "");

            // 【核心修复】通过复合键精确查找是否已存在（基于数据库记录）
            Map<String, Object> existingRow = existingMap.get(key);
            boolean isMerge = (existingRow != null);  //  关键：是否合并由数据库决定

            //  如果需要合并，提取现有信息
            String existingDistribution = null;
            Integer existingOrderItemId = null;
            Integer originalQty = 0;  //  原数量（用于计算增量）

            if (isMerge) {
                existingOrderItemId = (Integer) existingRow.get("orderItemId");
                originalQty = (Integer) existingRow.get("quantity");
                existingDistribution = (String) existingRow.get("quantityDistribution");

                //  设置 orderItemId 供 update 使用（如果需要精确更新）
                newItem.setOrderItemId(existingOrderItemId);

                System.out.println(" 合并模式: key=" + key +
                        ", orderItemId=" + existingOrderItemId +
                        ", originalQty=" + originalQty);
            }

            // 【核心】计算 quantity_distribution（传入准确的 isMerge 和 originalQty）
            if (normalizedAssigned != null && !normalizedAssigned.isEmpty()) {
                int mergedTotalQuantity = originalQty + originalItem.getQuantity();

                String distribution = generateQuantityDistribution(
                        originalItem.getItemCode(),
                        originalQty,                    //  原数量（合并=数据库值，新增=0）
                        mergedTotalQuantity,
                        normalizedAssigned,
                        isMerge,                        //  准确传递是否合并
                        existingDistribution
                );
                newItem.setQuantityDistribution(distribution);
            }

            //  分类：合并走 update，否则走 insert
            if (isMerge) {
                itemsToUpdate.add(newItem);
            } else {
                itemsToInsert.add(newItem);
            }
        }
        // 【步骤 4】执行数据库操作（保持原有逻辑）
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
     * 生成聚餐桌菜品的数量分配记录（JSON 格式）
     *
     * 功能说明：
     * 1. 校验参数：分配餐桌列表为空或单桌时返回 null（无需分布记录）
     * 2. 校验业务规则：总数量必须能被桌数整除，否则抛出异常
     * 3. 合并操作处理：
     *    - 解析现有 distribution JSON，提取每桌当前数量
     *    - 计算每桌需增加的数量 = (新总数 - 原总数) / 桌数
     *    - 累加到现有分布，生成新 JSON
     * 4. 新增操作处理：
     *    - 若每桌数量≥2：按桌号数字排序后生成分布记录
     *    - 若每桌数量=1：返回 null（无需记录分布）
     * 5. 将分布映射转换为标准 JSON 字符串（如{"7":2,"8":2}）
     *
     * @param itemCode 菜品编码
     * @param originalQty 菜品原有数量（合并时为数据库值，新增时为 0）
     * @param totalQuantity 合并后的总数量
     * @param assignedTableDisplayId 分配的餐桌列表（已标准化排序，逗号分隔）
     * @param isMerge 是否为合并操作（由数据库精确匹配决定）
     * @param existingDistribution 现有的 distribution JSON 字符串（合并时传入）
     * @return JSON 格式的分布记录；单桌或每桌 1 份时返回 null
     *
     * 输出示例：
     * - 合并：originalQty=2, totalQuantity=4, tables="7,8" → {"7":2,"8":2}
     * - 新增：totalQuantity=4, tables="7,8" → {"7":2,"8":2}
     * - 单桌：tables="7" → null
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
//            System.out.println(" 单桌分配菜品 " + itemCode +
//                    " (assigned=" + assignedTableDisplayId + ")，不需要 quantity_distribution");
            return null;  // ← 关键：单桌直接返回 null
        }

        if (tableCount == 0) return null;

        // 3. 【核心规则】数量必须能被桌数整除
        if (totalQuantity % tableCount != 0) {
            throw new IllegalArgumentException(
                    "菜品 " + itemCode + " 的总数量 (" + totalQuantity +
                            ") 必须能被桌子数量 (" + tableCount + ") 整除！"
            );
        }

        // 4. 【核心修复】合并操作时，基于现有 distribution 更新 + 累加
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
                // 【关键】计算每桌需要增加的数量 = (新总数 - 原总数) / 桌数
                qtyToAddPerTable = (totalQuantity - originalQty) / tableCount;
                System.out.println(" 合并操作：菜品=" + itemCode +
                        ", 原数量=" + originalQty +
                        ", 新总数=" + totalQuantity +
                        ", 桌数=" + tableCount +
                        ", 每桌增加=" + qtyToAddPerTable);
            } catch (Exception e) {
                System.err.println(" 解析现有 distribution 失败: " + existingDistribution);
                e.printStackTrace();
                distribution.clear();  // 解析失败时清空，走初始化逻辑
            }
        }

        // 5. 如果 distribution 为空，按新逻辑初始化
        if (distribution.isEmpty()) {
            int qtyPerTable = totalQuantity / tableCount;

            // 【核心规则】判断是否需要记录 distribution
            boolean shouldRecord = isMerge || qtyPerTable >= 2;
            if (!shouldRecord) {
                System.out.println(" 菜品 " + itemCode + " 初次添加，每桌 " + qtyPerTable +
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
        // 【新增】合并且有 distribution 时，累加新增数量
        else if (isMerge && qtyToAddPerTable > 0) {
            for (String tableId : distribution.keySet()) {
                int newQty = distribution.get(tableId) + qtyToAddPerTable;
                distribution.put(tableId, newQty);
                System.out.println(" 桌号#" + tableId + " 数量累加: " +
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

        System.out.println(" 生成 distribution: " + itemCode +
                " (原" + originalQty + "→总" + totalQuantity + "份/" + tableCount + "桌) → " + json);

        return json.toString();
    }


    /**
     * 根据订单项主键查询菜品数量
     *
     * 功能说明：
     * 1. 校验参数有效性，为空时直接返回 0
     * 2. 查询订单项对象，返回其数量字段；记录不存在时返回 0
     *
     * @param orderItemId 订单项主键
     * @return 菜品数量；参数为空或记录不存在时返回 0
     *
     * 应用场景：
     * - 撤销菜品前校验可撤销数量上限
     * - 界面显示订单项详情时获取数量信息
     */
    @Transactional(readOnly = true)
    public int getOrderItemQuantityByOrderItemId(Integer orderItemId) {
        if (orderItemId == null) return 0;

        OrderItem item = orderItemMapper.selectByPrimaryKey(orderItemId);
        return item != null ? item.getQuantity() : 0;
    }

    /**
     * 标记聚餐桌特定订单项为已上桌
     *
     * 功能说明：
     * 1. 校验餐桌存在性及类型为聚餐桌
     * 2. 精确查询订单项并校验未全部上桌状态
     * 3. 计算新已上桌数量（不超过总数量）及新状态（全部上桌→"SERVED"，部分上桌→"PARTIALLY_SERVED"）
     * 4. 处理已上桌餐桌列表：
     *    - 聚餐桌一键点餐：直接使用分配餐桌列表作为已上桌列表
     *    - 普通单桌分配：逐桌追加餐桌编号，避免重复
     * 5. 更新订单项的已上桌数量、状态及已上桌餐桌列表
     *
     * @param tableNumber 餐桌显示编号
     * @param orderItemId 待标记的订单项主键
     * @param quantity 本次上桌的菜品数量
     *
     * 异常处理：
     * - 餐桌非聚餐桌、订单项不存在或已全部上桌时抛出相应异常
     * - 数据库更新失败时抛出 SQLException
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSpecificOrderItemAsServed(String tableNumber, int orderItemId, int quantity) {
        // 1. 验证餐桌
        Tables table = tablesMapper.findByDisplayId(tableNumber);
        if (table == null || table.getTableType() != Tables.TableType.GROUPED) {
            throw new IllegalStateException("餐桌 " + tableNumber + " 不是聚餐桌");
        }

        // 2. 【核心】根据 orderItemId 精确查询订单项
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

        // 5. 【核心修复】更新 served_table_display_id
        String assignedTables = targetItem.getAssignedTableDisplayId();
        String newServedTables;

        //  如果是聚餐桌一键点餐（assigned 包含多个桌号），直接使用完整列表
        if (assignedTables != null && !assignedTables.isEmpty() && assignedTables.contains(",")) {
            // 聚餐桌一键点餐：直接使用 assigned_table_display_id 作为 served_table_display_id
            newServedTables = assignedTables;
            System.out.println(" 聚餐桌一键点餐菜品：使用完整桌号列表: " + newServedTables);
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

        System.out.println(" 精确上菜成功: orderItemId=#" + orderItemId +
                ", table=" + tableNumber +
                ", served=" + newServedQty + "/" + totalQty +
                ", served_tables=" + newServedTables);
    }


    /**
     * 为预约聚餐桌添加点餐菜品
     *
     * 功能说明：
     * 1. 通过预约号查询关联的预点餐订单
     * 2. 查询现有订单项（仅按菜品编码匹配，忽略餐桌分配字段）
     * 3. 遍历新菜品列表：
     *    - 若菜品已存在：标记为待更新，累加数量
     *    - 若菜品不存在：标记为待插入，初始化状态为未上桌
     * 4. 批量执行数据库操作：更新现有项 + 插入新项
     * 5. 更新订单状态为已点餐，并重算订单总金额
     *
     * @param reservationId 预约记录唯一标识
     * @param orderItems 待添加的订单项列表
     *
     * 业务规则：
     * - 预约订单点餐时餐桌尚未分配，assigned_table_display_id 保持为 null
     * - 相同菜品编码自动合并数量，避免重复记录
     * - 仅当订单状态为"未点餐"时才更新为"已点餐"
     *
     * 执行时机：
     * - 顾客在预约阶段提前点餐时调用
     * - 确保入座时订单数据已准备就绪
     */
    @Transactional
    public void addOrderItemsForReservationGroupedTable(
            String reservationId,
            List<OrderItem> orderItems) {

        //  通过 reservation_id 查找预点餐订单
        Order order = orderMapper.findActiveOrderByReservationId(reservationId);
        if (order == null || order.getOrderId() == null) {
            throw new IllegalStateException("未找到预约订单: " + reservationId);
        }

        Integer orderId = order.getOrderId();

        // 【核心修改】查询现有明细时，只根据 itemCode 匹配，不考虑 assigned_table_display_id
        // 因为预约订单此时还未分配餐桌，assigned_table_display_id 都是 NULL
        List<Map<String, Object>> existingItemsRaw =
                orderItemMapper.getExistingItemQuantitiesRaw(orderId, null);

        Map<String, OrderItem> existingMap = new HashMap<>();
        for (Map<String, Object> row : existingItemsRaw) {
            String code = ((String) row.get("itemCode")).toUpperCase();
            String status = (String) row.get("status");
            // 【关键】只用 itemCode 作为 key，不考虑 assignedTableDisplayId
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

            //  预约订单点餐时，assigned_table_display_id 保持为 NULL
            newItem.setAssignedTableDisplayId(null);

            // 【关键】只用 itemCode 作为 key
            String key = originalItem.getItemCode().toUpperCase();

            if (existingMap.containsKey(key)) {
                itemsToUpdate.add(newItem);
            } else {
                itemsToInsert.add(newItem);
                existingMap.put(key, newItem);
            }
        }

        //  执行数据库操作
        if (!itemsToUpdate.isEmpty()) {
            orderItemMapper.updateExistingOrderItemsForReservation(orderId, itemsToUpdate);
        }
        if (!itemsToInsert.isEmpty()) {
            orderItemMapper.insertNewOrderItemsWithStatus(orderId, itemsToInsert, "UNSERVED");
        }

        //  更新订单状态和金额
        if ("NO_ORDER".equals(order.getStatus())) {
            orderMapper.updateOrderStatus(orderId, "ORDERED", "NO_ORDER");
        }

        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }

        System.out.println(" 预约聚餐桌点餐处理完成 - orderId=" + orderId);
    }


    /**
     * 智能撤销聚餐桌共同菜品订单项
     *
     * 功能说明：
     * 1. 校验订单项存在性及所属订单为聚餐桌类型
     * 2. 计算撤销后的新数量，校验撤销数量不超过当前数量
     * 3. 根据新数量智能分流：
     *    - 新数量为 0：调用 handleDeleteOrderItem 执行删除流程
     *    - 新数量大于 0：调用 handleUpdateOrderItem 执行更新流程
     *
     * @param orderItemId 待撤销的订单项主键
     * @param cancelQuantity 本次撤销的数量
     * @param cancellationReason 撤销原因说明
     * @param cancelPart 撤销部分标识（"SERVED"/"UNSERVED"），用于业务逻辑区分
     *
     * 异常处理：
     * - 订单项不存在、非聚餐桌订单或撤销数量超限时抛出相应异常
     * - 事务自动回滚，确保数据一致性
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelGroupedTableOrderItemSmart(int orderItemId, int cancelQuantity,
                                                 String cancellationReason, String cancelPart) {
        // ===== 1. 查询订单项（获取当前状态）=====
        OrderItem orderItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (orderItem == null) {
            throw new IllegalStateException("订单项不存在：" + orderItemId);
        }

        // ===== 2. 验证是否为聚餐桌订单 =====
        Order order = orderMapper.findById(orderItem.getOrderId());
        if (order == null || order.getTableId() == null) {
            throw new IllegalStateException("订单或餐桌不存在");
        }

        Tables table = tablesMapper.findById(order.getTableId());
        if (table == null || table.getTableType() != Tables.TableType.GROUPED) {
            throw new IllegalStateException("此方法仅支持聚餐桌（GROUPED）订单，当前餐桌类型：" +
                    (table != null ? table.getTableType() : "null"));
        }

        // ===== 3. 计算撤销后的新数量 =====
        int currentQuantity = orderItem.getQuantity();
        int newQuantity = currentQuantity - cancelQuantity;

        if (newQuantity < 0) {
            throw new IllegalArgumentException("撤销数量(" + cancelQuantity +
                    ")不能超过当前数量(" + currentQuantity + ")");
        }

        // 【日志】记录 cancelPart 参数
        System.out.println(" 聚餐桌撤销请求: orderItemId=#" + orderItemId +
                ", cancelQuantity=" + cancelQuantity +
                ", cancelPart=" + cancelPart);

        // ===== 4. 【核心】智能处理：数量=0时删除，否则更新 =====
        if (newQuantity == 0) {
            // ── 情况1：数量归零 → 删除订单项 ──
            handleDeleteOrderItem(orderItemId, orderItem, order, cancellationReason, cancelPart);
        } else {
            // ── 情况2：数量>0 → 更新订单项 ──
            handleUpdateOrderItem(orderItemId, orderItem, newQuantity, cancellationReason, cancelPart);
        }
    }

    /**
     * 处理订单项数量归零时的删除流程
     *
     * 功能说明：
     * 1. 记录撤销审计日志，包含撤销金额与原因
     * 2. 物理删除订单项记录
     * 3. 重新计算订单总金额，确保财务数据准确
     * 4. 检查订单是否无剩余明细，若是则删除空订单
     *
     * @param orderItemId 待删除的订单项主键
     * @param orderItem 待删除的订单项对象（用于日志记录）
     * @param order 关联的订单对象（用于日志与重算）
     * @param cancellationReason 撤销原因说明
     * @param cancelPart 撤销部分标识，用于生成默认原因
     *
     * 执行时机：
     * - 仅当撤销后新数量为 0 时调用
     * - 确保删除操作前已完成审计与金额重算
     */
    private void handleDeleteOrderItem(int orderItemId, OrderItem orderItem, Order order,
                                       String cancellationReason, String cancelPart)  {
        System.out.println(" 订单项数量归零，执行删除: orderItemId=#" + orderItemId +
                (cancelPart != null ? ", cancelPart=" + cancelPart : ""));

        // 1. 记录撤销审计日志
        recordCancellation(orderItem, order, cancellationReason, cancelPart);

        // 2. 删除订单项
        int deleted = orderItemMapper.deleteOrderItemByOrderItemId(orderItemId);
        if (deleted == 0) {
            throw new RuntimeException("删除订单项失败：orderItemId=" + orderItemId);
        }

        // 3. 重新计算订单总金额
        recalculateOrderTotal(order.getOrderId());

        // 4. 检查订单是否为空 → 删除空订单
        checkAndDeleteEmptyOrder(order.getOrderId());

        System.out.println(" 订单项已删除: orderItemId=#" + orderItemId);
    }

    /**
     * 处理订单项数量减少时的更新流程
     *
     * 功能说明：
     * 1. 计算新已上桌数量：取原已上桌数量与新总数量的较小值，避免逻辑冲突
     * 2. 计算新状态：根据新数量与已上桌数量判断（如"PARTIALLY_CANCELLED"）
     * 3. 记录撤销审计日志
     * 4. 更新订单项核心字段：总数量、已上桌数量、状态
     * 5. 重新计算订单总金额
     *
     * @param orderItemId 待更新的订单项主键
     * @param orderItem 待更新的订单项对象（用于获取原状态）
     * @param newQuantity 撤销后的新总数量
     * @param cancellationReason 撤销原因说明
     * @param cancelPart 撤销部分标识，用于业务逻辑区分
     *
     * 业务规则：
     * - 已上桌数量不可超过新总数量，确保数据逻辑一致
     * - 状态自动推导，避免手动设置导致状态冲突
     */
    private void handleUpdateOrderItem(int orderItemId, OrderItem orderItem, int newQuantity,
                                       String cancellationReason, String cancelPart) {
        System.out.println(" 订单项数量更新: orderItemId=#" + orderItemId +
                ", 原数量=" + orderItem.getQuantity() + ", 新数量=" + newQuantity +
                (cancelPart != null ? ", cancelPart=" + cancelPart : ""));

        // 1. 计算新的served_quantity（不能超过新数量）
        int newServedQuantity = Math.min(orderItem.getServedQuantity(), newQuantity);

        // 2. 计算新状态
        String newStatus = calculateStatusAfterCancel(newQuantity, newServedQuantity);

        // 3. 记录撤销审计日志
        recordCancellation(orderItem, null, cancellationReason, cancelPart);

        // 4. 更新订单项
        int updated = orderItemMapper.updateGroupedOrderItemQuantity(
                orderItemId,
                newQuantity,
                newServedQuantity,
                newStatus
        );
        if (updated == 0) {
            throw new RuntimeException("更新订单项失败：orderItemId=" + orderItemId);
        }

        // 5. 重新计算订单总金额
        recalculateOrderTotal(orderItem.getOrderId());

        System.out.println(" 订单项已更新: orderItemId=#" + orderItemId +
                ", 新状态=" + newStatus);
    }

    /**
     * 记录订单项撤销的审计日志
     *
     * 功能说明：
     * 1. 计算撤销金额：按下单时单价 × 撤销数量（默认 1）
     * 2. 确定撤销原因：优先使用传入原因，否则根据 cancelPart 生成描述
     * 3. 调用 Mapper 持久化审计记录，包含订单信息、菜品编码、撤销数量与金额
     *
     * @param orderItem 被撤销的订单项对象
     * @param order 关联的订单对象（可为空）
     * @param cancellationReason 用户输入的撤销原因
     * @param cancelPart 撤销部分标识（"SERVED"/"UNSERVED"），用于生成默认原因
     *
     * 容错处理：
     * - 记录失败时仅输出错误日志，不中断主业务流程
     * - 确保撤销操作的核心逻辑不受审计日志影响
     */
    private void recordCancellation(OrderItem orderItem, Order order,
                                   String cancellationReason, String cancelPart) {
        try {
            double cancelledAmount = orderItem.getPriceAtOrder() *
                    (order != null ? 1 : orderItem.getQuantity());

            //  如果 cancellationReason 为空，使用 cancelPart 作为原因
            String finalReason = (cancellationReason != null && !cancellationReason.isEmpty())
                    ? cancellationReason
                    : (cancelPart != null ? "撤销部分: " + cancelPart : "用户撤销");

            orderMapper.recordCancellation(
                    "ITEM",
                    order != null ? order.getOrderId() : orderItem.getOrderId(),
                    order != null ? order.getOrderNumber() : null,
                    orderItem.getItemCode(),
                    1,
                    orderItem.getStatus(),
                    cancelledAmount,
                    finalReason
            );
        } catch (Exception e) {
            System.err.println(" 记录撤销审计失败：" + e.getMessage());
        }
    }

    /**
     * 重新计算订单总金额并更新数据库
     *
     * 功能说明：
     * 1. 调用 Mapper 聚合计算订单下所有有效订单项的金额总和
     * 2. 若计算结果非空，同步更新订单主表的总金额与应付金额字段
     *
     * @param orderId 待重算的订单主键
     *
     * 执行时机：
     * - 订单项新增、修改、撤销或删除后调用
     * - 确保订单金额与明细数据实时一致，避免财务统计偏差
     */
    private void recalculateOrderTotal(int orderId) {
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }
    }

    /**
     * 检查并删除无明细的空订单
     *
     * 功能说明：
     * 1. 查询订单是否仍存在有效订单项
     * 2. 若无剩余明细，则物理删除订单主记录，避免数据冗余
     *
     * @param orderId 待检查的订单主键
     *
     * 业务规则：
     * - 仅当订单完全无明细时才执行删除，部分撤销保留订单
     * - 删除操作前需确保已完成金额重算与审计日志记录
     *
     * 应用场景：
     * - 聚餐桌共同菜品全部撤销后自动清理空订单
     * - 用户取消所有菜品后的订单生命周期终结处理
     */
    private void checkAndDeleteEmptyOrder(int orderId) {
        if (!orderItemMapper.hasRemainingItems(orderId)) {
            orderMapper.deleteOrder(orderId);
            System.out.println("🗑 空订单已删除: orderId=" + orderId);
        }
    }

    /**
     * 撤销聚餐桌共同菜品的部分或全部数量
     *
     * 功能说明：
     * 1. 校验订单项存在性及所属订单为聚餐桌类型
     * 2. 根据 cancelPart 参数执行内存业务逻辑（如通知厨房、记录操作日志），不涉及数据库更新
     * 3. 更新订单项核心字段：总数量、已上桌数量、状态、分配餐桌列表、数量分布 JSON
     * 4. 若原订单项有已上桌数量，记录撤销审计日志（含撤销金额与原因）
     * 5. 重新计算订单总金额，确保财务数据准确
     * 6. 若新数量为 0，检查并删除空订单，避免数据冗余
     *
     * @param orderItemId 待撤销的订单项主键
     * @param cancelQuantity 本次撤销的数量
     * @param newQuantity 撤销后的新总数量
     * @param newServedQuantity 撤销后的新已上桌数量
     * @param newStatus 撤销后的新状态（如"PARTIALLY_CANCELLED"）
     * @param newAssignedTableIds 撤销后的新分配餐桌列表（逗号分隔）
     * @param newDistribution 撤销后的新数量分布 JSON（如{"7":2,"8":2}）
     * @param cancellationReason 撤销原因，用于审计日志
     * @param cancelPart 撤销部分标识（"SERVED"/"UNSERVED"），仅用于内存业务逻辑
     *
     * 异常处理：
     * - 订单项不存在、非聚餐桌订单或数据库更新失败时抛出相应异常
     * - 事务自动回滚，确保数据一致性
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSharedDishOrderItem(
            int orderItemId, int cancelQuantity, int newQuantity,
            int newServedQuantity, String newStatus,
            String newAssignedTableIds, String newDistribution,
            String cancellationReason, String cancelPart) {

        // 1. 查询订单项（用于记录审计日志）
        OrderItem orderItem = orderItemMapper.selectByPrimaryKey(orderItemId);
        if (orderItem == null) {
            throw new IllegalStateException("订单项不存在：" + orderItemId);
        }

        // 2. 验证是否为聚餐桌订单
        Order order = orderMapper.findById(orderItem.getOrderId());
        if (order == null || order.getTableId() == null) {
            throw new IllegalStateException("订单或餐桌不存在");
        }

        Tables table = tablesMapper.findById(order.getTableId());
        if (table == null || table.getTableType() != Tables.TableType.GROUPED) {
            throw new IllegalStateException("此方法仅支持聚餐桌（GROUPED）订单");
        }

        // 【业务逻辑】根据 cancelPart 进行额外处理（不持久化）
        if (cancelPart != null) {
            if ("SERVED".equals(cancelPart)) {
                // 撤销已上桌部分：可添加额外业务逻辑（如通知厨房、更新统计等）
                System.out.println("️ 撤销已上桌部分: orderItemId=#" + orderItemId +
                        ", cancelQty=" + cancelQuantity);
            } else if ("UNSERVED".equals(cancelPart)) {
                // 撤销未上桌部分：可添加额外业务逻辑
                System.out.println(" 撤销未上桌部分: orderItemId=#" + orderItemId +
                        ", cancelQty=" + cancelQuantity);
            }
            //  此处可添加其他业务逻辑，如发送通知、记录操作日志等
            // 注意：所有逻辑都不涉及数据库更新，仅内存处理
        }

        // 3.  执行数据库更新（单条更新，精确匹配 order_item_id）
        // cancelPart 不传入 Mapper，因为不需要持久化
        int updated = orderItemMapper.updateSharedDishOrderItem(
                orderItemId,
                newQuantity,
                newServedQuantity,
                newStatus,
                newAssignedTableIds,
                newDistribution
        );

        if (updated == 0) {
            throw new RuntimeException("更新订单项失败：orderItemId=" + orderItemId);
        }

        // 4. 记录撤销审计日志（仅当有已上桌数量时记录）
        if (orderItem.getServedQuantity() > 0) {
            double cancelledAmount = orderItem.getPriceAtOrder() * cancelQuantity;
            orderMapper.recordCancellation(
                    "ITEM",
                    order.getOrderId(),
                    order.getOrderNumber(),
                    orderItem.getItemCode(),
                    cancelQuantity,
                    orderItem.getStatus(),
                    cancelledAmount,
                    cancellationReason != null ? cancellationReason : "用户撤销"
            );
        }

        // 5. 重新计算订单总金额
        recalculateOrderTotal(order.getOrderId());

        // 6. 如果数量归零，检查是否删除订单
        if (newQuantity <= 0) {
            checkAndDeleteEmptyOrder(order.getOrderId());
        }

        System.out.println(" 共同菜品撤销完成: orderItemId=#" + orderItemId +
                ", newQty=" + newQuantity +
                ", newServedQty=" + newServedQuantity +
                ", newStatus=" + newStatus +
                ", cancelPart=" + cancelPart);
    }


    /**
     * 记录订单或订单项撤销的审计日志
     *
     * 功能说明：
     * 将撤销操作的关键信息持久化到审计日志表，支持后续财务对账与运营分析。
     *
     * @param cancellationType 撤销类型（"ORDER"/"ITEM"）
     * @param orderId 关联订单主键
     * @param orderNumber 订单编号（用于日志可读性）
     * @param itemCode 撤销的菜品编码（订单级撤销时可为空）
     * @param cancelledQuantity 撤销的数量
     * @param beforeStatus 撤销前的状态
     * @param cancelledAmount 撤销涉及的金额（数量 × 单价）
     * @param reason 撤销原因说明
     *
     * 执行时机：
     * - 订单项或订单撤销成功后调用
     * - 确保所有财务变更均有迹可循
     */
    @Transactional
    public void recordCancellation(String cancellationType, Integer orderId, String orderNumber, String itemCode, Integer cancelledQuantity,
            String beforeStatus, Double cancelledAmount, String reason) {

        orderMapper.recordCancellation(
                cancellationType,
                orderId,
                orderNumber,
                itemCode,
                cancelledQuantity,
                beforeStatus,
                cancelledAmount,
                reason
        );
    }

    /**
     * 根据订单项主键物理删除订单项记录
     *
     * 功能说明：
     * 直接从数据库删除指定订单项，不经过逻辑删除标记，适用于订单清空或数据清理场景。
     *
     * @param orderItemId 待删除的订单项主键
     * @return 数据库影响行数（1=删除成功，0=记录不存在）
     *
     * 校验规则：
     * - 参数为空或小于等于 0 时抛出 IllegalArgumentException
     * - 调用方需确保删除前已处理关联的订单总金额重算等业务逻辑
     *
     * 使用场景：
     * - 订单所有菜品撤销后自动清理空订单
     * - 管理员手动清理异常数据
     */
    @Transactional
    public int deleteOrderItemByOrderItemId(Integer orderItemId) {
        if (orderItemId == null || orderItemId <= 0) {
            throw new IllegalArgumentException("orderItemId 无效");
        }
        return orderItemMapper.deleteOrderItemByOrderItemId(orderItemId);
    }
}
