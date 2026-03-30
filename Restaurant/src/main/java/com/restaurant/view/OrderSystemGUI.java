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

    public OrderSystemGUI(RestaurantController controller, RestaurantService service, MenuItemService menuItemService, OrderService orderService) {
        this.controller = controller;
        this.service = service;
        this.menuItemService=menuItemService;
        this.orderService = orderService;

        // 窗口基础设置
        setTitle("订单系统");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        showPanel("Home");
    }



    /**
     * 面板切换方法（卡片式切换）
     * @param panelType 面板类型：Home/Food/Drink/StirFry/SetMeal
     */
    public void showPanel(String panelType) {
        getContentPane().removeAll();  // 清空当前内容

        switch (panelType.toLowerCase()) {
            case "home":
                if (homePanel == null) {
                    homePanel = new HomePanel(this, service, controller,menuItemService);
                }
                getContentPane().add(homePanel);
                break;

            case "food":
                if (foodPanel == null) {
                    foodPanel = new MenuPanel(this, service, controller,menuItemService, MenuPanel.FOOD);
                }
                foodPanel.setCurrentTableNumber(currentTableNumber);
                foodPanel.setCurrentOrderType(currentOrderType);
                getContentPane().add(foodPanel);
                break;

            case "drink":
                if (drinkPanel == null) {
                    drinkPanel = new MenuPanel(this, service, controller, menuItemService,MenuPanel.DRINK);
                }
                drinkPanel.setCurrentTableNumber(currentTableNumber);
                drinkPanel.setCurrentOrderType(currentOrderType);  //  同步訂單類型
                getContentPane().add(drinkPanel);
                break;

            case "stirfry":
                if (stirFryPanel == null) {
                    stirFryPanel = new MenuPanel(this, service, controller,menuItemService, MenuPanel.STIRFRY);
                }
                stirFryPanel.setCurrentTableNumber(currentTableNumber);
                stirFryPanel.setCurrentOrderType(currentOrderType);
                getContentPane().add(stirFryPanel);
                break;

            case "setmeal":
                if (setMealPanel == null) {
                    setMealPanel = new MenuPanel(this, service, controller,menuItemService, MenuPanel.SETMEAL);
                }
                setMealPanel.setCurrentTableNumber(currentTableNumber);
                setMealPanel.setCurrentOrderType(currentOrderType);
                getContentPane().add(setMealPanel);
                break;
        }

        revalidate();
        repaint();
    }

    public void setCurrentTableNumber(String tableNumber) {
        this.currentTableNumber = tableNumber.trim();

        // 同步到所有Panel并强制刷新
        if (homePanel != null) {
            homePanel.setCurrentTableNumber(this.currentTableNumber);
            homePanel.refreshTemporaryOrderDisplay();  //  强制刷新
        }
        if (foodPanel != null) foodPanel.setCurrentTableNumber(this.currentTableNumber);
        if (drinkPanel != null) drinkPanel.setCurrentTableNumber(this.currentTableNumber);
        if (stirFryPanel != null) stirFryPanel.setCurrentTableNumber(this.currentTableNumber);
        if (setMealPanel != null) setMealPanel.setCurrentTableNumber(this.currentTableNumber);
    }



    /** 设置全局订单类型并同步到所有 Panel */
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


    public MenuItem getMenuItemById(String itemId) {
        return menuItemService.getMenuItemByCode(itemId);  // 委托给 Service
    }
    public List<OrderItem> loadFormalOrderItems(String tableNumber) {
        return orderService.loadFormalOrderItems(tableNumber);
    }

    public String generateFormalOrderHtml(String tableNumber, boolean includeTotal) {
        List<OrderItem> items = loadFormalOrderItems(tableNumber);

        if (items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:10px; color:#999; text-align:center;'>" +
                    "<p>📭 暂无正式订单</p></body></html>";
        }

        // 按状态分组
        Map<String, List<OrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getStatus));

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding:10px;'>");

        html.append("<table border='1' cellpadding='6' cellspacing='0' ")
                .append("style='width:100%; table-layout:fixed; border-collapse:collapse;'>");

        // 表头
        html.append("<tr style='background-color:#f5f5f5;'>")
                .append("<th style='width:50px; text-align:center;'>序号</th>")
                .append("<th style='width:100px; text-align:center;'>状态</th>")
                .append("<th style='width:80px; text-align:left;'>编号</th>")
                .append("<th style='width:200px; text-align:left;'>菜品</th>")
                .append("<th style='width:90px; text-align:center;'>数量（已上/总数）</th>") // ← 优化表头文字
                .append("<th style='width:90px; text-align:right;'>单价</th>")
                .append("<th style='width:100px; text-align:right;'>小计</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定显示顺序
        for (String status : Arrays.asList("UNSERVED", "PARTIALLY_SERVED", "SERVED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            String statusText = switch (status) {
                case "UNSERVED" -> "🔴 未上桌";
                case "PARTIALLY_SERVED" -> "🟠 部分上桌";
                case "SERVED" -> "🟢 已上桌";
                default -> status;
            };

            String statusColor = switch (status) {
                case "UNSERVED" -> "#ff6b6b";
                case "PARTIALLY_SERVED" -> "#ffa500";
                case "SERVED" -> "#4caf50";
                default -> "#2196f3";
            };

            for (OrderItem item : group) {
                double subtotal = item.getQuantity() * item.getPriceAtOrder();
                totalAmount += subtotal;

                html.append("<tr>");

                // 序号
                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace;'>%d</td>",
                        itemNumber++
                ));

                // 状态（每一行都输出，绝不 rowspan）
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center;'>%s</td>",
                        statusColor, statusText
                ));

                // 编号 / 菜名
                html.append(String.format(
                        "<td style='white-space:nowrap;'>%s</td>" +
                                "<td style='white-space:nowrap;'>%s</td>",
                        item.getItemCode(),
                        item.getItemName()
                ));

                // ===== 核心修复：数量列显示进度 "已上/总数" =====
                String quantityProgress = String.format("%d/%d",
                        item.getServedQuantity(),
                        item.getQuantity());

                // 部分上桌时高亮背景（浅橙色）+ 加粗
                String quantityStyle = "PARTIALLY_SERVED".equals(item.getStatus())
                        ? "background-color:#fff3e0; font-weight:bold;"
                        : "";

                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; %s'>%s</td>",
                        quantityStyle,
                        quantityProgress
                ));
                // ============================================

                // 单价 / 小计
                html.append(String.format(
                        "<td style='text-align:right; font-family:monospace;'>%.2f</td>" +
                                "<td style='text-align:right; font-weight:bold; color:#d32f2f; font-family:monospace;'>%.2f</td>",
                        item.getPriceAtOrder(),
                        subtotal
                ));

                html.append("</tr>");
            }
        }

        html.append("</table>");

        // 总计
        if (includeTotal && totalAmount > 0) {
            html.append(String.format(
                    "<div style='margin-top:15px; padding:12px; background-color:#e8f5e9; " +
                            "text-align:right; font-size:18px; font-weight:bold; font-family:monospace;'>" +
                            "订单总计：<span style='color:#c62828;'>%.2f 元</span></div>",
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
     * @param itemCode 菜品编号（如 "A1"）
     * @param isActive true=售卖中, false=已售罄
     */
    public void setMenuItemStatus(String itemCode, boolean isActive) {
        menuItemStatusCache.put(itemCode.toUpperCase(), isActive);
    }


    /**
     * 添加/减少临时订单（支持负数实现取消）
     * @param tableNumber 餐桌号
     * @param itemId 菜品编号
     * @param quantity 正数=增加，负数=减少（取消）
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

    public OrderType getCurrentOrderType() {
        return currentOrderType;
    }

    /**
     * 新增：获取当前外卖订单号（代理到 HomePanel）
     */
    public String getCurrentTakeoutOrderNumber() {
        if (homePanel != null) {
            return homePanel.getCurrentTakeoutOrderNumber();
        }
        return null;
    }

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

    public com.restaurant.entity.Order findActiveOrderByOrderNumber(String currentTableNumber) {
        return  orderService.findActiveOrderByOrderNumber(currentTableNumber);
    }

    /**
     * 物理删除菜品（代理到 MenuItemService）
     * @param itemCode 菜品编号
     * @return true=删除成功
     */
    public boolean deleteMenuItemPhysically(String itemCode) {
        return menuItemService.deleteMenuItemByCode(itemCode);
    }

    // ===== OrderSystemGUI.java 新增代理方法 =====
    /**
     * 更新菜品价格（代理到 MenuItemService）
     * @param itemCode 菜品编号
     * @param newPrice 新价格
     * @return true=更新成功
     */
    public boolean updateMenuItemPrice(String itemCode, double newPrice) {
        return menuItemService.updatePrice(itemCode, newPrice);
    }
    /**
     * 更新订单配送费（代理到 OrderService）
     * @param orderId 订单ID
     * @param newDeliveryFee 新配送费（最终值）
     */
    public void updateOrderDeliveryFee(Integer orderId, Double newDeliveryFee) {
        orderService.updateOrderDeliveryFee(orderId, newDeliveryFee);
    }
}

