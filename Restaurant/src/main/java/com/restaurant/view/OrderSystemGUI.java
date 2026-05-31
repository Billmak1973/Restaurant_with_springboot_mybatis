package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.MenuItem;
import com.restaurant.entity.Order;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.OrderType;
import com.restaurant.service.MenuItemService;
import com.restaurant.service.OrderService;
import com.restaurant.service.RestaurantService;

import javax.swing.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OrderSystemGUI extends JFrame {
    private final RestaurantController controller;
    private final RestaurantService service;
    private final MenuItemService menuItemService;
    private final OrderService orderService;

    // 面板缓存（懒加载）
    private HomePanel homePanel;
    private MenuPanel foodPanel;
    private MenuPanel drinkPanel;
    private MenuPanel stirFryPanel;
    private MenuPanel setMealPanel;

    private String currentTableNumber = "";
    private final Map<String, Boolean> menuItemStatusCache = new ConcurrentHashMap<>();

    // 临时订单缓存：餐桌号 -> (菜品ID -> 数量) - 改为 ConcurrentHashMap 保持一致性
    private final Map<String, Map<String, Integer>> temporaryOrders = new ConcurrentHashMap<>();
    private OrderType currentOrderType = OrderType.DINE_IN;  //  默認堂食

    /**
     * 订单系统窗口构造函数
     *
     * @param controller       餐厅控制器，负责业务逻辑协调
     * @param service          餐厅服务层，提供餐桌/顾客组等业务操作
     * @param menuItemService  菜品服务层，提供菜单查询与管理
     * @param orderService     订单服务层，提供订单创建/查询/结账等操作
     *
     * 主要功能：
     * 1. 保存各服务层引用，供后续面板调用
     * 2. 配置窗口基础属性（标题/尺寸/关闭行为/居中显示）
     * 3. 默认显示首页面板（HomePanel）
     */
    public OrderSystemGUI(RestaurantController controller, RestaurantService service, MenuItemService menuItemService, OrderService orderService) {
        this.controller = controller;
        this.service = service;
        this.menuItemService = menuItemService;
        this.orderService = orderService;

        // 窗口基础设置
        setTitle("订单系统");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        showPanel("Home");
    }


    /**
     * 切换显示指定功能面板（懒加载 + 状态同步）
     *
     * 功能说明：
     * 1. 清空当前窗口内容，准备加载新面板
     * 2. 根据 panelType 参数匹配对应功能模块（首页/菜品/饮品/炒菜/套餐）
     * 3. 采用懒加载模式：面板首次使用时才创建实例，避免内存浪费
     * 4. 同步当前餐桌号和订单类型，确保面板数据与上下文一致
     * 5. 刷新界面布局，应用最新显示状态
     *
     * @param panelType 面板类型标识（不区分大小写）：
     *                  "home"=首页, "food"=菜品, "drink"=饮品,
     *                  "stirfry"=炒菜, "setmeal"=套餐
     *
     * @note 线程安全：所有操作在主线程执行，无需额外同步
     * @note 性能优化：面板实例复用，避免重复创建开销
     */
    public void showPanel(String panelType) {
        getContentPane().removeAll();  //  清空当前窗口所有内容，为加载新面板做准备

        switch (panelType.toLowerCase()) {
            case "home":
                if (homePanel == null) {
                    homePanel = new HomePanel(this, service, controller, menuItemService);//首次访问：创建首页面板实例，注入依赖服务
                }
                getContentPane().add(homePanel);// 将首页面板添加到窗口内容区域
                break;//结束当前分支，避免穿透执行其他case

            case "food":
                if (foodPanel == null) {
                    foodPanel = new MenuPanel(this, service, controller, menuItemService, MenuPanel.FOOD); // 首次访问：创建菜品菜单面板，指定类型为 FOOD
                }
                foodPanel.setCurrentTableNumber(currentTableNumber);//同步当前餐桌号，确保点餐关联正确桌台
                foodPanel.setCurrentOrderType(currentOrderType);//同步订单类型（堂食/外卖/自取）
                getContentPane().add(foodPanel);//加菜品面板到窗口
                break;//

            case "drink":
                if (drinkPanel == null) {
                    drinkPanel = new MenuPanel(this, service, controller, menuItemService, MenuPanel.DRINK);
                }
                drinkPanel.setCurrentTableNumber(currentTableNumber);
                drinkPanel.setCurrentOrderType(currentOrderType);  //  同步訂單類型
                getContentPane().add(drinkPanel);
                break;

            case "stirfry":
                if (stirFryPanel == null) {
                    stirFryPanel = new MenuPanel(this, service, controller, menuItemService, MenuPanel.STIRFRY);
                }
                stirFryPanel.setCurrentTableNumber(currentTableNumber);
                stirFryPanel.setCurrentOrderType(currentOrderType);
                getContentPane().add(stirFryPanel);
                break;

            case "setmeal":
                if (setMealPanel == null) {
                    setMealPanel = new MenuPanel(this, service, controller, menuItemService, MenuPanel.SETMEAL);
                }
                setMealPanel.setCurrentTableNumber(currentTableNumber);
                setMealPanel.setCurrentOrderType(currentOrderType);
                getContentPane().add(setMealPanel);
                break;
        }

        revalidate();
        repaint();
    }

    /**
     * 设置当前餐桌编号并同步到所有菜单面板
     *
     * @param tableNumber 餐桌显示编号（如 "7" 或 "7a"）
     *
     * 功能说明：
     * 1. 保存餐桌编号到成员变量
     * 2. 将餐桌号同步到所有已初始化的菜单面板（Home/Food/Drink/StirFry/SetMeal）
     * 3. 强制刷新临时订单显示，确保界面数据一致
     */
    public void setCurrentTableNumber(String tableNumber) {
        this.currentTableNumber = tableNumber.trim();

        // 同步到所有Panel并强制刷新
        if (homePanel != null) {
            homePanel.setCurrentTableNumber(this.currentTableNumber);// 更新主页面板的餐桌号
            homePanel.refreshTemporaryOrderDisplay(); // 强制刷新临时订单显示，确保数据同步
        }
        if (foodPanel != null) foodPanel.setCurrentTableNumber(this.currentTableNumber);
        if (drinkPanel != null) drinkPanel.setCurrentTableNumber(this.currentTableNumber);
        if (stirFryPanel != null) stirFryPanel.setCurrentTableNumber(this.currentTableNumber);
        if (setMealPanel != null) setMealPanel.setCurrentTableNumber(this.currentTableNumber);
    }


    /**
     * 设置全局订单类型并同步到所有 Panel
     */
    public void setCurrentOrderType(OrderType orderType) {
        this.currentOrderType = orderType;

        // 同步到 HomePanel
        if (homePanel != null) {
            homePanel.setCurrentOrderType(orderType);
        }
        // 同步到各菜单 Panel
        if (foodPanel != null) foodPanel.setCurrentOrderType(orderType);
        if (drinkPanel != null) drinkPanel.setCurrentOrderType(orderType);
        if (stirFryPanel != null) stirFryPanel.setCurrentOrderType(orderType);
        if (setMealPanel != null) setMealPanel.setCurrentOrderType(orderType);
    }

    /**
     * 刷新 HomePanel 的临时订单显示
     */
    public void refreshHomeTemporaryOrder() {
        if (homePanel != null) {
            homePanel.refreshTemporaryOrderDisplay();
        }
    }

    /**
     * 获取指定餐桌的临时订单
     */
    public Map<String, Integer> getTemporaryOrderForTable(String tableNumber) {
        if (tableNumber == null || tableNumber.isEmpty()) {
            return Collections.emptyMap();
        }
        return temporaryOrders.getOrDefault(tableNumber, Collections.emptyMap());
    }

    /**
     * 根据菜品编号获取菜品信息
     * @param itemId 菜品编号（如 "A1"）
     * @return MenuItem 菜品实体对象，包含：
     *         - itemId: 菜品主键ID
     *         - itemCode: 菜品编号（A1/B2等）
     *         - name: 菜品名称
     *         - price: 单价
     *         - categoryId: 所属分类ID
     *         - isActive: 是否可售状态
     *         若菜品不存在或已停售，返回 null
     */
    public MenuItem getMenuItemById(String itemId) {
        return menuItemService.getMenuItemByCode(itemId);
    }
    /**
     * 加载指定餐桌的正式订单明细
     * @param tableNumber 餐桌显示编号（如 "7" 或 "7a"）
     * @return List<OrderItem> 订单明细列表，每项包含：
     *         - orderItemId: 明细主键
     *         - itemId/itemCode: 菜品标识
     *         - quantity: 下单总数量
     *         - servedQuantity: 已上桌数量
     *         - status: 上菜状态（UNSERVED/PARTIALLY_SERVED/SERVED）
     *         - priceAtOrder: 下单时单价
     *         - assignedTableDisplayId: 分配桌号（聚餐桌用）
     *         若餐桌无活跃订单，返回空列表
     */
    public List<OrderItem> loadFormalOrderItems(String tableNumber) {
        return orderService.loadFormalOrderItems(tableNumber);
    }


    /**
     * 生成正式订单的 HTML 表格展示内容
     *
     * 功能说明：
     * 1. 根据餐桌号加载该桌的正式订单明细（含菜品编号、名称、数量、状态、价格等）
     * 2. 按菜品状态分组显示：准备中 → 已准备 → 未上桌 → 部分上桌 → 已上桌（固定顺序）
     * 3. 支持聚餐桌模式：额外显示"分配餐桌"和"实际上菜"两列，展示菜品在各桌的分配与实际上菜情况
     * 4. 视觉优化：
     *    - 状态列使用彩色背景标识（紫色/蓝色/灰色/橙色/绿色）
     *    - 数量列对"部分上桌"状态高亮显示
     *    - 聚餐桌模式下，桌号列表过长时自动截断显示（如：13,14,...,17）
     *    - 实际上菜桌号按数字大小排序后显示
     * 5. 支持显示订单总计金额（可选）
     *
     * @param tableNumber        餐桌显示编号（如 "7" 或 "7a"）
     * @param includeTotal       是否在底部显示订单总计金额
     * @param isGroupedTable     是否为聚餐桌模式（决定显示额外两列）
     * @param currentTableDisplayId 当前操作的餐桌显示编号（用于聚餐桌单桌筛选，本方法暂未使用）
     * @return 完整的 HTML 字符串，可直接用于 JEditorPane 渲染展示
     *
     * @note 1. 空订单时返回友好的"暂无正式订单"提示页面
     *       2. 表格采用 min-width:2000px + table-layout:auto 确保长内容可横向滚动
     *       3. 所有金额保留 2 位小数，使用 ¥ 符号格式化
     *       4. 聚餐桌模式下，"分配餐桌"列显示 assigned_table_display_id，"实际上菜"列显示 served_table_display_id（已排序）
     */
    public String generateFormalOrderHtml(String tableNumber, boolean includeTotal,
                                          boolean isGroupedTable, String currentTableDisplayId) {
        List<OrderItem> items = loadFormalOrderItems(tableNumber);

        if (items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                    "<p>暂无正式订单</p></body></html>";
        }

        // 按菜品状态分组：将订单明细按状态分类（如：未上桌/部分上桌/已上桌/准备中）
        //将订单明细列表按照菜品状态自动分类，生成一个以状态为键、该状态下所有订单项列表为值的 Map。
        Map<String, List<OrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getStatus));

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding:15px;'>");

        //  表格样式：min-width 2000px + table-layout:auto 确保列宽能撑开
        html.append("<table border='1' cellpadding='0' cellspacing='0' ")
                .append("style='width:100%; table-layout:auto; min-width:2000px; border-collapse:collapse; border:1px solid #ddd;'>");

        //  表头：重点加宽【分配餐桌】【实际上菜】【单价】【小计】四列
        html.append("<tr style='background-color:#f8f9fa; height:45px;'>")
                .append("<th style='width:80px; text-align:center; white-space:nowrap; padding:10px;'>序号</th>")
                .append("<th style='width:140px; text-align:center; white-space:nowrap; padding:10px;'>状态</th>")
                .append("<th style='width:130px; text-align:left; white-space:nowrap; padding:10px;'>编号</th>")
                .append("<th style='width:450px; text-align:left; white-space:nowrap; padding:10px;'>菜品</th>")
                .append("<th style='width:180px; text-align:center; white-space:nowrap; padding:10px;'>数量（已上/总数）</th>");

        if (isGroupedTable) {
            // 分配餐桌：220→300px (+80)
            html.append("<th style='width:300px; text-align:left; white-space:nowrap; padding:10px;'>分配餐桌</th>");
            // 实际上菜：220→300px (+80)
            html.append("<th style='width:300px; text-align:left; white-space:nowrap; padding:10px;'>实际上菜</th>");
        }

        // 单价：140→220px (+80) + 小计：160→240px (+80)
        html.append("<th style='width:260px; text-align:right; white-space:nowrap; padding:10px;'>单价</th>")
                .append("<th style='width:280px; text-align:right; white-space:nowrap; padding:10px;'>小计</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定显示顺序
        for (String status : Arrays.asList("PREPARING", "PREPARED", "UNSERVED", "PARTIALLY_SERVED", "SERVED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            String statusText = switch (status) {
                case "PREPARING" -> "准备中";
                case "PREPARED" -> "已准备";
                case "UNSERVED" -> "未上桌";
                case "PARTIALLY_SERVED" -> "部分上桌";
                case "SERVED" -> "已上桌";
                default -> status;
            };

            String statusColor = switch (status) {
                case "PREPARING" -> "#9c27b0";
                case "PREPARED" -> "#2196f3";
                case "UNSERVED" -> "#9e9e9e";
                case "PARTIALLY_SERVED" -> "#ff9800";
                case "SERVED" -> "#4caf50";
                default -> "#2196f3";
            };

            for (OrderItem item : group) {
                double subtotal = item.getQuantity() * item.getPriceAtOrder();
                totalAmount += subtotal;

                String displayItemId = item.getItemCode();
                String assignedTables = item.getAssignedTableDisplayId();
                String servedTables = item.getServedTableDisplayId();
                boolean isBatchOrder = (assignedTables != null && assignedTables.contains(","));

                html.append("<tr style='white-space:nowrap; height:40px;'>");

                // 序号
                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; padding:12px 8px;'>%d</td>",
                        itemNumber++
                ));

                // 状态
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center; padding:12px 8px;'>%s</td>",
                        statusColor, statusText
                ));

                // 编号 / 菜名
                html.append(String.format(
                        "<td style='white-space:nowrap; padding:12px 8px;'>%s</td>" +
                                "<td style='white-space:nowrap; padding:12px 8px; font-size:14px;'>%s</td>",
                        displayItemId,
                        item.getItemName()
                ));

                // 数量列
                String quantityProgress = String.format("%d / %d",
                        item.getServedQuantity(),
                        item.getQuantity()
                );
                String batchLabel = isBatchOrder ? "<br><span style='color:#1976d2; font-size:11px;'>[一键点餐]</span>" : "";
                String quantityStyle = "PARTIALLY_SERVED".equals(item.getStatus())
                        ? "background-color:#fff3e0; font-weight:bold;"
                        : "";

                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; padding:12px 8px; %s'>%s%s</td>",
                        quantityStyle,
                        quantityProgress,
                        batchLabel
                ));

                // 聚餐模式列
                if (isGroupedTable) {
                    html.append("<td style='padding:12px 8px; text-align:left; font-size:13px; width:300px;'>");
                    if (assignedTables != null && !assignedTables.isEmpty()) {
                        //  长桌号列表时允许换行显示
                        String[] tableIds = assignedTables.split(",");
                        if (tableIds.length > 3) {
                            // 超过3个桌号时换行显示：前2个 + ... + 最后1个
                            String displayText = tableIds[0] + ", " + tableIds[1] + ", ..., " + tableIds[tableIds.length - 1];
                            html.append("<span style='color:#1976d2; line-height:1.4;'>").append(displayText).append("</span>");
                        } else {
                            html.append("<span style='color:#1976d2;'>").append(assignedTables).append("</span>");
                        }
                    } else {
                        html.append("<span style='color:#ccc;'>—</span>");
                    }
                    html.append("</td>");

                    //實際上菜列
                    html.append("<td style='padding:12px 8px; text-align:left; font-size:13px; width:300px;'>");
                    boolean isSingleTableAssignment = (assignedTables == null || !assignedTables.contains(","));
                    if (isSingleTableAssignment) {
                        html.append("<span style='color:#999;'>不適用</span>");
                    } else {
                        if (servedTables != null && !servedTables.isEmpty()) {
                            // 將桌號按數字大小排序
                            String[] servedIds = servedTables.split(",");
                            List<Integer> servedTableNumbers = new ArrayList<>();
                            for (String id : servedIds) {
                                try {
                                    servedTableNumbers.add(Integer.parseInt(id.trim()));
                                } catch (NumberFormatException e) {
                                    // 忽略無效的桌號
                                }
                            }
                            // 排序（從小到大）
                            Collections.sort(servedTableNumbers);

                            //  長桌號列表時允許換行顯示
                            if (servedTableNumbers.size() > 3) {
                                String displayText = servedTableNumbers.get(0) + ", " +
                                        servedTableNumbers.get(1) + ", ..., " +
                                        servedTableNumbers.get(servedTableNumbers.size() - 1);
                                html.append("<span style='color:#4caf50; font-weight:bold; line-height:1.4;'>")
                                        .append(displayText).append("</span>");
                            } else {
                                // 將排序後的桌號用逗號連接
                                String sortedTablesStr = servedTableNumbers.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
                                html.append("<span style='color:#4caf50; font-weight:bold;'>")
                                        .append(sortedTablesStr).append("</span>");
                            }
                        } else {
                            html.append("<span style='color:#ccc;'>—</span>");
                        }
                    }
                    html.append("</td>");

                }

                // 单价/小计列 - 加宽 + 右对齐 + 货币格式
                html.append(String.format(
                        "<td style='text-align:right; font-family:monospace; white-space:nowrap; padding:12px 8px; width:220px;'>¥ %.2f</td>" +
                                "<td style='text-align:right; font-weight:bold; color:#d32f2f; font-family:monospace; white-space:nowrap; padding:12px 8px; width:240px;'>¥ %.2f</td>",
                        item.getPriceAtOrder(),
                        subtotal
                ));

                html.append("</tr>");
            }
        }

        html.append("</table>");

        // 总计栏
        if (includeTotal && totalAmount > 0) {
            html.append(String.format(
                    "<div style='margin-top:20px; padding:15px; background-color:#f1f8e9; " +
                            "text-align:right; font-size:20px; border:1px solid #c8e6c9; font-family:monospace;'>" +
                            "订单总计：<span style='color:#c62828; font-weight:bold;'>¥ %.2f 元</span></div>",
                    totalAmount
            ));
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * 加载堂食订单列表（供 View 层调用）- 不限制数量
     */
    public List<Map<String, Object>> loadDineInOrders() {
        return orderService.loadDineInOrders();
    }

    /**
     * 加载自取订单列表 - 不限制数量
     */
    public List<Map<String, Object>> loadPickupOrders() {
        return orderService.loadPickupOrders();
    }

    /**
     * 加载配送订单列表 - 不限制数量
     */
    public List<Map<String, Object>> loadDeliveryOrders() {
        return orderService.loadDeliveryOrders();
    }


    /**
     * 设置菜品状态缓存
     *
     * @param itemCode 菜品编号（如 "A1"）
     * @param isActive true=售卖中, false=已售罄
     */
    public void setMenuItemStatus(String itemCode, boolean isActive) {
        menuItemStatusCache.put(itemCode.toUpperCase(), isActive);
    }


    /**
     * 添加/减少临时订单（支持负数实现取消）
     *
     * @param tableNumber 餐桌号
     * @param itemId      菜品编号
     * @param quantity    正数=增加，负数=减少（取消）
     * @return 操作是否成功
     */
    public boolean addTemporaryOrder(String tableNumber, String itemId, int quantity) {
        // 1. 基础验证
        if (tableNumber == null || tableNumber.trim().isEmpty() ||
                "未选择".equals(tableNumber.trim()) ||
                itemId == null || itemId.isEmpty()) {
            return false;
        }

        String normalizedTableNumber = tableNumber.trim();
        String normalizedItemId = itemId.trim().toUpperCase();

        // 2. 获取该餐桌的临时订单 Map（不存在则创建）
        Map<String, Integer> tableOrders = temporaryOrders.get(normalizedTableNumber);
        if (tableOrders == null) {
            if (quantity <= 0) {
                // 尝试减少不存在的订单 → 无效操作
                return false;
            }
            tableOrders = new HashMap<>();
            temporaryOrders.put(normalizedTableNumber, tableOrders);
        }

        // 3. 计算新数量
        int currentQty = tableOrders.getOrDefault(normalizedItemId, 0);
        int newQty = currentQty + quantity;

        // 4. 处理归零/负数情况（自动清理）
        if (newQty <= 0) {
            tableOrders.remove(normalizedItemId);

            // 如果该餐桌订单变为空，移除整个餐桌记录
            if (tableOrders.isEmpty()) {
                temporaryOrders.remove(normalizedTableNumber);
            }

            System.out.println("临时订单清理 - 餐桌" + normalizedTableNumber + ": " +
                    normalizedItemId + " (原数量: " + currentQty + ", 取消: " + (-quantity) + ")");
        } else {
            // 正常更新数量
            tableOrders.put(normalizedItemId, newQty);
            System.out.println("临时订单更新 - 餐桌" + normalizedTableNumber + ": " +
                    normalizedItemId + " × " + quantity + " (新数量: " + newQty + ")");
        }

        return true;
    }

    /**
     * 获取下一个外卖订单序号
     *
     * @param orderType 订单类型（自取 PICKUP 或配送 DELIVERY）
     * @return 下一笔订单的流水序号
     *
     * 设计说明：
     * 1. 采用委托模式，将业务逻辑下沉至 Service 层，保持当前类职责单一
     * 2. 供 UI 层【自动生号】功能调用，根据当前日期与订单类型查询数据库最大序号并加 1
     * 3. 确保自取（P 前缀）与配送（D 前缀）订单序号独立递增，防止编号冲突
     * 4. 避免在 View/Controller 层直接操作数据库，符合 MVC 分层规范
     */
    public int getNextTakeoutOrderNumber(OrderType orderType) {
        return orderService.getNextTakeoutOrderNumber(orderType);
    }

    /**
     * 清除指定餐桌的临时订单（新增方法）
     */
    public void clearTemporaryOrder(String tableNumber) {
        if (tableNumber != null && !tableNumber.trim().isEmpty()) {
            temporaryOrders.remove(tableNumber.trim());
        }
    }

    /**
     * 获取当前全局订单类型
     *
     * @return OrderType 枚举值
     *
     * 使用 OrderType 枚举而非 String 的原因：
     * 1. 类型安全：编译期检查，避免非法订单类型值传入
     * 2. 代码可读性：DINE_IN/TAKEOUT/DELIVERY 等常量语义明确
     * 3. 集中维护：订单类型相关逻辑统一在枚举类中管理
     * 4. 扩展友好：新增订单类型只需修改枚举定义，无需改动调用方
     * 5. 数据库映射：枚举的 dbOrderType/dbDeliveryMethod 方法支持与
     *    数据库字段的双向转换，保证数据一致性
     */
    public OrderType getCurrentOrderType() {
        return currentOrderType;
    }

    /**
     * 获取当前外卖订单号（代理到 HomePanel）
     */
    public String getCurrentTakeoutOrderNumber() {
        if (homePanel != null) {
            return homePanel.getCurrentTakeoutOrderNumber();
        }
        return null;
    }

    /**
     * 根据外卖订单号查询订单明细列表
     *
     * @param orderNumber 外卖订单号（格式：P-20260305-001 / D-20260305-001）
     * @return OrderItem 列表，如果订单不存在或无明细则返回空列表
     * @note 该方法仅查询状态为 ORDERED 的活跃订单明细
     * @see OrderService#loadOrderItemsByOrderNumber(String)
     */
    public List<OrderItem> loadFormalOrderItemsByOrderNumber(String orderNumber) {
        return orderService.loadOrderItemsByOrderNumber(orderNumber);
    }


    /**
     * 根据订单号查询配送费
     */
    public Double getDeliveryFeeByOrderNumber(String orderNumber) {
        return orderService.getDeliveryFeeByOrderNumber(orderNumber);
    }

    /**
     * 根据订单号获取订单状态显示（外卖订单用）
     */
    public String getOrderStatusDisplayByOrderNumber(String orderNumber) {
        return orderService.getOrderStatusDisplayByOrderNumber(orderNumber);
    }

    /**
     *  使用完全限定类名 com.restaurant.entity.Order 的原因：
     * 1. 避免命名冲突：当前类可能导入了其他同名类（如 java.sql.Order 或自定义 Order）
     * 2. 明确类型来源：确保返回的是实体层的 Order 对象，而非其他包的同类
     * 3. 增强代码可读性：调用方一眼就能识别返回值的完整类型路径
     * 4. 兼容未导入场景：即使当前文件未 import com.restaurant.entity.Order 也能正常编译
     */
    public com.restaurant.entity.Order findActiveOrderByOrderNumber(String currentTableNumber) {
        return orderService.findActiveOrderByOrderNumber(currentTableNumber);
    }

    /**
     * 物理删除菜品（代理到 MenuItemService）
     *
     * @param itemCode 菜品编号
     * @return true=删除成功
     */
    public boolean deleteMenuItemPhysically(String itemCode) {
        return menuItemService.deleteMenuItemByCode(itemCode);
    }

    /**
     *  检查菜品是否在任何临时订单中（供菜单管理调用）
     * @param itemCode 菜品编号
     * @return true=菜品在某个临时订单中，false=未被使用
     */
    public boolean isItemInAnyTemporaryOrder(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) {
            return false;
        }
        String normalizedItemCode = itemCode.trim().toUpperCase();

        // 遍历所有餐桌的临时订单
        for (Map<String, Integer> order : temporaryOrders.values()) {
            if (order != null && order.containsKey(normalizedItemCode)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 更新菜品价格（代理到 MenuItemService）
     *
     * @param itemCode 菜品编号
     * @param newPrice 新价格
     * @return true=更新成功
     */
    public boolean updateMenuItemPrice(String itemCode, double newPrice) {
        return menuItemService.updatePrice(itemCode, newPrice);
    }

    /**
     * 更新订单配送费（代理到 OrderService）
     *
     * @param orderId        订单ID
     * @param newDeliveryFee 新配送费（最终值）
     */
    public void updateOrderDeliveryFee(Integer orderId, Double newDeliveryFee) {
        orderService.updateOrderDeliveryFee(orderId, newDeliveryFee);
    }

    /**
     *  根据预约号查询订单明细（预约订单专用）
     */
    public List<OrderItem> loadFormalOrderItemsByReservationId(String reservationId) {
        return orderService.loadFormalOrderItemsByReservationId(reservationId);
    }

    /**
     * 根据预约号查找预点餐订单
     *
     * 功能：通过预约号查询关联的预点餐订单记录
     * 流程：调用 OrderService 层执行数据库查询，Controller 仅做代理转发
     *
     * @param reservationId 预约号（唯一标识）
     * @return Order 对象：查询成功时返回预点餐订单实体；未找到或异常时返回 null
     *
     * @note 返回值说明：
     *   • 非 null → 存在预点餐订单，调用方可安全获取菜品/金额等信息
     *   • null     → 无预点餐记录，调用方需做空值判断避免空指针
     */
    public Order findPreOrderByReservationId(String reservationId) {
        return orderService.findPreOrderByReservationId(reservationId);
    }

    /**
     *  撤销预约订单中的菜品（通过 reservation_id）- 添加 cancelPart 参数
     */
    public void cancelReservationOrderItem(String reservationId, int itemId,
                                           int quantity, String cancellationReason,
                                           String cancelPart) throws SQLException {
        orderService.cancelReservationOrderItem(reservationId, itemId, quantity,
                cancellationReason, cancelPart);
    }

    /**
     * 为聚餐桌批量添加订单菜品
     *
     * 功能：将订单菜品添加到聚餐桌关联的多张子桌
     * 参数：主桌编号、菜品列表、目标子桌编号列表
     * 实现：委托给 Service 层处理具体业务逻辑
     */
    public void addOrderItemsForGroupedTable(
            String mainTableDisplayId,
            List<OrderItem> orderItems,
            List<String> targetTableIds) {

        orderService.addOrderItemsForGroupedTable(
                mainTableDisplayId,
                orderItems,
                targetTableIds
        );
    }
    /**
     *  预约聚餐桌点餐（无需传入 groupedTableIds）
     * 预约订单可能还未分配具体餐桌，由 Service 层后续处理
     */
    public void addOrderItemsForReservationGroupedTable(
            String reservationId,
            List<OrderItem> orderItems) {
        // 🔧 委托给 Service 层处理，不传入 targetTableIds
        orderService.addOrderItemsForReservationGroupedTable(
                reservationId,
                orderItems
        );
    }

    /**
     * 从配置描述字符串解析餐桌数量
     *
     * 功能：提取 "6人桌 x3" 格式字符串中的数字 3
     * 规则：按 "x" 分割，取后半部分纯数字；解析失败返回默认值 1
     * 用途：解析预约配置/订单配置中的桌子数量字段
     *
     * @param configDesc 配置描述（如 "6人桌 x3"）
     * @return 餐桌数量，解析失败时返回 1
     */
    public int parseTableCountFromConfig(String configDesc) {
        if (configDesc == null || configDesc.isEmpty()) {
            return 1;  // 默认1张桌子
        }

        try {
            // 提取 "x" 后面的数字
            if (configDesc.contains("x")) {
                String[] parts = configDesc.split("x");
                if (parts.length > 1) {
                    String qtyStr = parts[1].replaceAll("[^0-9]", "").trim();
                    if (!qtyStr.isEmpty()) {
                        return Integer.parseInt(qtyStr);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析桌子数量失败: " + e.getMessage());
        }

        return 1;  // 解析失败时返回默认值
    }

}

