package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.MenuItem;
import com.restaurant.entity.OrderItem;
import com.restaurant.service.MenuItemService;
import com.restaurant.service.RestaurantService;
import com.restaurant.entity.OrderType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MenuPanel extends JPanel {
    // 菜单类型常量
    public static final int FOOD = 0;
    public static final int DRINK = 1;
    public static final int STIRFRY = 2;
    public static final int SETMEAL = 3;

    private final int menuType;
    private final OrderSystemGUI frame;
    private final MenuItemService menuItemService;  //  新增
    private OrderType currentOrderType = OrderType.DINE_IN;

    // UI 组件
    private JLabel tableNumberDisplay;
    private JEditorPane temporaryHtmlDisplay;
    private JEditorPane orderedHtmlDisplay;
    private JEditorPane menuTableDisplay;
    private JScrollPane tempScrollPane;
    private JScrollPane ordScrollPane;
    private JScrollPane menuScrollPane;

    private String currentTableNumber = "未选择";
    private static final Map<String, String> menuCache = new ConcurrentHashMap<>();
    private List<MenuItem> menuItems = null;

    // ===== 主题颜色定义 =====
    private final Color FOOD_COLOR = new Color(255, 228, 225);  // 温暖的粉色系
    private final Color DRINK_COLOR = new Color(220, 240, 255);  // 清爽的蓝色系
    private final Color STIRFRY_COLOR = new Color(255, 245, 220); // 温暖的黄色系
    private final Color SETMEAL_COLOR = new Color(225, 255, 240); // 清新的绿色系

    // 主题颜色配置
    private Color backgroundColor;
    private Color titleColor;
    private Color borderColor;
    private Color headerBgColor;
    private Color buttonBgColor;
    private Color buttonHoverColor;

    public MenuPanel(OrderSystemGUI frame, RestaurantService service, RestaurantController controller, MenuItemService menuItemService, int menuType) {
        this.frame = frame;
        this.menuType = menuType;
        this.menuItemService = menuItemService;

        // ===== 初始化主题颜色 =====
       setupThemeColors();

       initializeUI();
       loadMenuItems(true);
    }


    // ===== MenuPanel 中的方法修改 =====
    public void setCurrentTableNumber(String tableNumber) {
        this.currentTableNumber = tableNumber;
        if (tableNumberDisplay != null) {
            //  关键：使用动态前缀替代硬编码 "餐桌号："
            tableNumberDisplay.setText(getOrderTypeLabelPrefix() + tableNumber);
        }
        // 关键：自动刷新两个区域
        refreshTemporaryOrderDisplay();
        refreshFormalOrderDisplay();
    }

     /**
     * 根据订单类型返回标签前缀
     */
    private String getOrderTypeLabelPrefix() {
        if (currentOrderType == null) {
            currentOrderType = OrderType.DINE_IN; // 默认值
        }

        return switch (currentOrderType) {
            case DINE_IN -> "餐桌号：";
            case PICKUP -> "自取订单：";
            case DELIVERY -> "配送订单：";
            default -> "订单：";
        };
    }

    /**
     * 新增：設置當前訂單類型（由 OrderSystemGUI 調用）
     * @param orderType 訂單類型枚舉
     */
    public void setCurrentOrderType(OrderType orderType) {
        if (this.currentOrderType != orderType) {
            this.currentOrderType = orderType;
            // 訂單類型改變時，刷新標籤顯示
            setCurrentTableNumber(this.currentTableNumber);
        }
        // ：强制刷新正式订单区域
        refreshFormalOrderDisplay();
        // 同步刷新临时订单区域（保持一致性）
        refreshTemporaryOrderDisplay();
    }

    /**
     *  新增：獲取當前訂單類型（供外部查詢）
     */
    public OrderType getCurrentOrderType() {
        return currentOrderType;
    }

    private void initializeUI() {
        // ===== STEP 1: 主布局 - BorderLayout 三层结构 =====
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(backgroundColor); // 设置背景色

        // ===== STEP 2: NORTH 区域 - 餐桌号显示 =====
        JPanel tableNumberPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableNumberDisplay = new JLabel("餐桌号：" + currentTableNumber);
        tableNumberDisplay.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        tableNumberPanel.setBackground(backgroundColor); // 关键：设置背景色为主题色
        tableNumberPanel.setOpaque(true); // 确保背景色生效
        tableNumberPanel.add(tableNumberDisplay);
        add(tableNumberPanel, BorderLayout.NORTH);

        // ===== STEP 3: CENTER 区域 - 内容面板（订单+菜单）=====
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(backgroundColor); // 设置内容面板背景色

        // 3.1 订单区域（占40%高度）→ 关键：设置首选高度250px
        JPanel orderPanel = new JPanel(new BorderLayout());
        orderPanel.setPreferredSize(new Dimension(0, 250));
        orderPanel.setBackground(backgroundColor); // 订单区域背景色

        // 临时订单区域
        temporaryHtmlDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; padding:10px;'>" +
                        "<p style='color:gray;'>暂无临时订单</p>" +
                        "</body></html>");
        temporaryHtmlDisplay.setEditable(false);
        tempScrollPane = new JScrollPane(temporaryHtmlDisplay);
        tempScrollPane.setBorder(BorderFactory.createTitledBorder("  临时订单  "));
        tempScrollPane.setBackground(backgroundColor);

        // 已下单食物区域
        orderedHtmlDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; padding:10px;'>" +
                        "<p style='color:gray;'>暂无已下单食物</p>" +
                        "</body></html>");
        orderedHtmlDisplay.setEditable(false);
        ordScrollPane = new JScrollPane(orderedHtmlDisplay);
        ordScrollPane.setBorder(BorderFactory.createTitledBorder("  已下单的食物  "));
        ordScrollPane.setBackground(backgroundColor);

        // 左右面板
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(backgroundColor);
        leftPanel.add(tempScrollPane, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(backgroundColor);
        rightPanel.add(ordScrollPane, BorderLayout.CENTER);

        //  修复1：设置相等的最小/首选尺寸（防止挤压）
        leftPanel.setMinimumSize(new Dimension(300, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        rightPanel.setPreferredSize(new Dimension(300, 0));

        // 水平分割面板（强制50/50）
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightPanel
        );
        splitPane.setResizeWeight(0.5);      // 窗口缩放时保持50/50
        splitPane.setContinuousLayout(true);
        splitPane.setBackground(backgroundColor);
        splitPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        // 三重保障强制50%位置
        // ① 立即设置
        splitPane.setDividerLocation(0.5);

        // ② 延迟到组件显示后设置
        SwingUtilities.invokeLater(() -> {
            if (splitPane.isShowing()) {
                splitPane.setDividerLocation(0.5);
            }
        });

        // ③ 监听显示+尺寸变化（终极保障）
        splitPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && splitPane.isShowing()) {
                splitPane.setDividerLocation(0.5);
            }
        });
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (splitPane.isShowing() && splitPane.getWidth() > 0) {
                        splitPane.setDividerLocation(0.5);
                    }
                });
            }
        });

        orderPanel.add(splitPane, BorderLayout.CENTER);

        // 3.2 菜单区域（占60%高度）
        JPanel menuPanel = new JPanel(new BorderLayout());
        menuPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        menuPanel.setBackground(backgroundColor);

        // ===== 修改：使用主题色设置菜单标题 =====
        String title = getMenuTypeTitle();
        JLabel menuTitleLabel = new JLabel(title, SwingConstants.CENTER);
        menuTitleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        menuTitleLabel.setForeground(titleColor); // 标题文字颜色
        menuTitleLabel.setOpaque(true);
        menuTitleLabel.setBackground(headerBgColor); // 标题背景色
        menuTitleLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor));
        menuPanel.add(menuTitleLabel, BorderLayout.NORTH);

        // 菜品表格 - 初始显示"加载中"（数据将在构造函数中异步加载）
        menuTableDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; text-align:center; padding:20px; color:#7f8c8d;'>"
                        + "<p>⏳ 正在加载菜品数据...</p>"
                        + "</body></html>");
        menuTableDisplay.setEditable(false);
        menuScrollPane = new JScrollPane(menuTableDisplay);
        menuScrollPane.setBorder(BorderFactory.createTitledBorder("  菜品列表  "));
        menuScrollPane.setBackground(backgroundColor);
        menuPanel.add(menuScrollPane, BorderLayout.CENTER);

        // 垂直分割：订单区域40% | 菜单区域60%
        JSplitPane contentSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                orderPanel,
                menuPanel
        );
        contentSplitPane.setResizeWeight(0.4);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setDividerLocation(0.4);
        contentSplitPane.setBackground(backgroundColor);
        contentSplitPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        contentPanel.add(contentSplitPane, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        // ===== STEP 4: SOUTH 区域 - 操作按钮 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBackground(backgroundColor);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton orderBtn = createThemedButton("点菜", buttonBgColor);
        JButton cancelOrderBtn = createThemedButton("取消点菜", buttonBgColor);
        JButton updateStatusBtn = createThemedButton("更新菜品状态", buttonBgColor);
        JButton addItemBtn = createThemedButton("添加菜品", buttonBgColor);
        JButton removeItemBtn = createThemedButton("删除菜品", buttonBgColor);
        JButton reviseItemBtn = createThemedButton("更改菜品价格", buttonBgColor);
        JButton backBtn = createThemedButton("返回主页", buttonBgColor);

        // 返回主页按钮事件（关键：实现跳转）
        backBtn.addActionListener(e -> frame.showPanel("Home"));
        addItemBtn.addActionListener(e -> showAddItemDialog());
       updateStatusBtn.addActionListener(e -> showUpdateStatusDialog()); // ← 新增绑定
       orderBtn.addActionListener(e -> showOrderDialog());  // ← 绑定点菜对话框
        cancelOrderBtn.addActionListener(e -> showCancelOrderDialog());
        removeItemBtn.addActionListener(e->showRemoveItemDialog());
        reviseItemBtn.addActionListener(e -> showReviseItemPriceDialog());

        buttonPanel.add(orderBtn);
        buttonPanel.add(cancelOrderBtn);
        buttonPanel.add(updateStatusBtn);
        buttonPanel.add(addItemBtn);
        buttonPanel.add(removeItemBtn);
        buttonPanel.add(reviseItemBtn);
        buttonPanel.add(backBtn);

        add(buttonPanel, BorderLayout.SOUTH);

        // ===== 关键修复：延迟设置垂直分隔条位置 =====
        SwingUtilities.invokeLater(() -> {
            if (contentSplitPane != null && contentSplitPane.isShowing()) {
                contentSplitPane.setDividerLocation(0.4);
            }
        });
    }

    private String getMenuTypeTitle() {
        return switch (menuType) {
            case FOOD -> "特色食物菜单";
            case DRINK -> "饮料菜单";
            case STIRFRY -> "小炒菜单";
            case SETMEAL -> "套餐菜单";
            default -> "未知菜单";
        };
    }

    /**
     * 创建主题化按钮
     */
    private JButton createThemedButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        button.setBackground(bgColor);
        button.setForeground(Color.DARK_GRAY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(100, 30));

        // 添加悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(buttonHoverColor);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    /**
     * 设置主题颜色（根据菜单类型）
     */
    private void setupThemeColors() {
        switch (menuType) {
            case FOOD:
                backgroundColor = FOOD_COLOR;
                titleColor = new Color(200, 50, 50);
                borderColor = new Color(220, 150, 150);
                headerBgColor = new Color(255, 200, 200);
                buttonBgColor = new Color(255, 180, 180);
                buttonHoverColor = new Color(255, 150, 150);
                break;
            case DRINK:
                backgroundColor = DRINK_COLOR;
                titleColor = new Color(50, 100, 200);
                borderColor = new Color(150, 200, 255);
                headerBgColor = new Color(200, 230, 255);
                buttonBgColor = new Color(180, 220, 255);
                buttonHoverColor = new Color(150, 200, 255);
                break;
            case STIRFRY:
                backgroundColor = STIRFRY_COLOR;
                titleColor = new Color(150, 100, 0);
                borderColor = new Color(255, 220, 150);
                headerBgColor = new Color(255, 220, 180);
                buttonBgColor = new Color(255, 200, 150);
                buttonHoverColor = new Color(255, 180, 130);
                break;
            case SETMEAL:
                backgroundColor = SETMEAL_COLOR;
                titleColor = new Color(0, 100, 50);
                borderColor = new Color(150, 255, 200);
                headerBgColor = new Color(200, 255, 220);
                buttonBgColor = new Color(180, 255, 220);
                buttonHoverColor = new Color(150, 255, 200);
                break;
        }
    }

    private void loadMenuItems(boolean useCache) {
        String cacheKey = String.valueOf(menuType);

        if (useCache && menuCache.containsKey(cacheKey)) {
            SwingUtilities.invokeLater(() -> {
                menuTableDisplay.setText(menuCache.get(cacheKey));
                menuTableDisplay.setCaretPosition(0);
            });
            return;
        }

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                //  关键：使用注入的 Service，不再 new DAO
                int categoryId = getMenuTypeConstant();

                //  通过 Spring 管理的 Service 查询
                List<MenuItem> items = menuItemService.getMenuItemsByCategory(categoryId);

                menuItems = items;
                return generateMenuTableWithItems(items);
            }

            @Override
            protected void done() {
                try {
                    String htmlContent = get();
                    menuCache.put(cacheKey, htmlContent);
                    SwingUtilities.invokeLater(() -> {
                        menuTableDisplay.setText(htmlContent);
                        menuTableDisplay.setCaretPosition(0);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(
                                frame,
                                "加载菜单失败: " + e.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                    e.printStackTrace();
                    menuTableDisplay.setText(generateEmptyMenuTable());
                }
            }
        };
        worker.execute();
    }

    private int getMenuTypeConstant() {
        return switch (menuType) {
            case FOOD -> 1;      // 对应 menu_categories 中 "特色食物" 的 category_id
            case DRINK -> 2;     // "饮料"
            case STIRFRY -> 3;   // "小炒"
            case SETMEAL -> 4;   // "套餐"
            default -> 1;
        };
    }

    /**
     * 统一风格：简约表格（所有列文字居中）
     */
    private String generateMenuTableWithItems(List<com.restaurant.entity.MenuItem> items) {
        StringBuilder html = new StringBuilder();
        // ===== 修改：使用主题色设置表格样式 =====
        html.append("<html><body style='font-family: Microsoft YaHei; margin:10px; background-color:#")
                .append(Integer.toHexString(backgroundColor.brighter().getRGB() & 0x00FFFFFF))
                .append(";'>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='width:95%; margin:0 auto; border-collapse:collapse; border-color:#")
                .append(Integer.toHexString(borderColor.getRGB() & 0x00FFFFFF))
                .append(";'>");

        // 表头（所有列居中）- 使用主题色
        html.append("<tr style='background-color:#")
                .append(Integer.toHexString(headerBgColor.getRGB() & 0x00FFFFFF))
                .append(";'>");
        html.append("<th style='text-align:center; padding:8px;'>编号</th>");
        html.append("<th style='text-align:center; padding:8px;'>菜品名称</th>");
        html.append("<th style='text-align:center; padding:8px;'>价格</th>");
        html.append("<th style='text-align:center; padding:8px;'>状态</th>");
        html.append("</tr>");

        // 表体
        if (items == null || items.isEmpty()) {
            html.append("<tr>");
            html.append("<td colspan='4' style='text-align:center; padding:15px; color:gray;'>");
            html.append("🍽️ 暂无菜品数据");
            html.append("</td>");
            html.append("</tr>");
        } else {
            boolean evenRow = true;
            for (com.restaurant.entity.MenuItem item : items) {
                // 状态显示：根据 is_active 字段
                String statusHtml = item.isActive()
                        ? "<span style='color:green; font-weight:bold;'>✓ 售卖中</span>"
                        : "<span style='color:red; font-weight:bold;'>✗ 已停售</span>";

                // ===== 修正：为奇偶行设置不同的背景色（修复运算符优先级）=====
                String rowBg = evenRow
                        ? "background-color: #ffffff;"
                        : "background-color: #" + Integer.toHexString(backgroundColor.brighter().getRGB() & 0x00FFFFFF) + ";";
                evenRow = !evenRow;

                html.append("<tr style='").append(rowBg).append("'>");
                // 所有列都设置 text-align:center
                html.append("<td style='text-align:center;'>").append(item.getItemCode()).append("</td>");
                html.append("<td style='text-align:center;'>").append(item.getName()).append("</td>");
                html.append("<td style='text-align:center; color:#d32f2f; font-weight:bold;'>")
                        .append(String.format("%.2f", item.getPrice())).append(" 元</td>");
                html.append("<td style='text-align:center;'>").append(statusHtml).append("</td>");
                html.append("</tr>");
            }
        }

        html.append("</table></body></html>");
        return html.toString();
    }

    // 加載失敗時：空表格（保持完全一致的居中对齐）
    private String generateEmptyMenuTable() {
        StringBuilder html = new StringBuilder();
        // ===== 修改：使用主题色设置表格样式 =====
        html.append("<html><body style='font-family: Microsoft YaHei; margin:10px; background-color:").append(backgroundColor.brighter().getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='width:95%; margin:0 auto; border-collapse:collapse; border-color:").append(borderColor.getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<tr style='background-color:#f0f0f0; background-color:").append(headerBgColor.getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<th style='text-align:center; padding:8px;'>编号</th>");
        html.append("<th style='text-align:center; padding:8px;'>菜品名称</th>");
        html.append("<th style='text-align:center; padding:8px;'>价格</th>");
        html.append("<th style='text-align:center; padding:8px;'>状态</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td colspan='4' style='text-align:center; padding:15px; color:gray;'>");
        html.append(" 暂无菜品数据");
        html.append("</td>");
        html.append("</tr>");
        html.append("</table></body></html>");
        return html.toString();
    }

    /**
     * 刷新临时订单显示
     */
    public void refreshTemporaryOrderDisplay() {
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding: 10px;'>");

        if (tempOrder == null || tempOrder.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>（暂无临时订单）</p>");
        } else {
            html.append("<table border='1' cellpadding='5' cellspacing='0' style='width: 100%; border-collapse: collapse;'>");
            html.append("<tr style='background-color: #f0f0f0;'>");
            html.append("<th style='padding: 8px; text-align: left;'>菜品编号</th>");
            html.append("<th style='padding: 8px; text-align: left;'>菜品名称</th>");
            html.append("<th style='padding: 8px; text-align: center;'>数量</th>");
            html.append("<th style='padding: 8px; text-align: right;'>单价(元)</th>");
            html.append("<th style='padding: 8px; text-align: right;'>小计(元)</th>");
            html.append("</tr>");

            double[] totalAmount = {0.0};

            tempOrder.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String itemId = entry.getKey();
                        int qty = entry.getValue();

                        // 修复：优先通过全局方法查询完整菜品信息
                        String itemName = "（未知）";
                        double price = 0.0;

                        // 方案1：直接通过frame全局查询（推荐）
                        com.restaurant.entity.MenuItem item = frame.getMenuItemById(itemId);
                        if (item != null) {
                            itemName = item.getName();
                            price = item.getPrice();
                        }
                        // 方案2：作为后备，再尝试从当前菜单的menuItems查找
                        else if (menuItems != null) {
                            for (com.restaurant.entity.MenuItem mi : menuItems) {
                                if (itemId.equals(mi.getItemCode())) {
                                    itemName = mi.getName();
                                    price = mi.getPrice();
                                    break;
                                }
                            }
                        }

                        double subtotal = price * qty;
                        totalAmount[0] += subtotal;

                        html.append("<tr style='border-top: 1px solid #ddd;'>");
                        html.append("<td style='padding: 8px;'>").append(itemId).append("</td>");
                        html.append("<td style='padding: 8px;'>").append(itemName).append("</td>");
                        html.append("<td style='padding: 8px; text-align: center;'>").append(qty).append("</td>");
                        html.append("<td style='padding: 8px; text-align: right;'>").append(String.format("%.2f", price)).append("</td>");
                        html.append("<td style='padding: 8px; text-align: right; font-weight: bold; color: #d32f2f;'>")
                                .append(String.format("%.2f", subtotal)).append("</td>");
                        html.append("</tr>");
                    });

            html.append("</table>");

            // 显示总金额
            html.append("<div style='margin-top: 15px; padding: 10px; background-color: #e8f5e9; border-radius: 4px; text-align: right;'>");
            html.append("<span style='font-size: 16px; font-weight: bold;'>订单总金额：</span>");
            html.append("<span style='font-size: 20px; color: #c62828; font-weight: bold;'>")
                    .append(String.format("%.2f", totalAmount[0])).append(" 元</span>");
            html.append("</div>");
        }

        html.append("</body></html>");
        temporaryHtmlDisplay.setText(html.toString());
    }



    public void refreshFormalOrderDisplay() {
        // ===== 基礎驗證 =====
        if (currentTableNumber == null ||
                "未选择".equals(currentTableNumber) ||
                "待下单".equals(currentTableNumber)) {
            orderedHtmlDisplay.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                            "<p>請選擇餐桌或確認訂單號</p></body></html>"
            );
            return;
        }

        List<OrderItem> items;

        // ===== 根據訂單類型加載數據 =====
        if (currentOrderType == OrderType.DINE_IN) {
            // 堂食模式：通過餐桌號查詢 + 使用 frame 生成 HTML
            items = frame.loadFormalOrderItems(currentTableNumber);
        } else {
            // 外賣/配送模式：通過訂單號查詢
            items = frame.loadFormalOrderItemsByOrderNumber(currentTableNumber);
        }

        // ===== 空數據處理 =====
        if (items == null || items.isEmpty()) {
            orderedHtmlDisplay.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                            "<p>📭 暫無訂單明細</p></body></html>"
            );
            return;
        }

        // ===== 生成HTML表格 =====
        String htmlContent;
        if (currentOrderType == OrderType.DINE_IN) {
            // 堂食：使用 frame 的方法（顯示"未上桌/已上桌"）
            htmlContent = frame.generateFormalOrderHtml(currentTableNumber, false);
        } else {
            // 🔧 外賣/自取：使用已查詢的 items 數據，自己生成 HTML（顯示"製作中/製作完成"）
            htmlContent = generateTakeoutOrderHtml(items, false);
        }

        // 🔧 關鍵：確保在 EDT 上更新 UI
        SwingUtilities.invokeLater(() -> {
            orderedHtmlDisplay.setText(htmlContent);
            orderedHtmlDisplay.setCaretPosition(0);  // 滾動到頂部
        });
    }

    /**
     * 🔧 新增：生成外賣訂單HTML（使用已查詢的 items 數據）
     */
    private String generateTakeoutOrderHtml(List<OrderItem> items, boolean includeTotal) {
        if (items == null || items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:10px; color:#999; text-align:center;'><p>📭 暫無訂單</p></body></html>";
        }

        // 按狀態分組
        Map<String, List<OrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getStatus));

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding:10px;'>");
        html.append("<table border='1' cellpadding='6' cellspacing='0' ")
                .append("style='width:100%; table-layout:fixed; border-collapse:collapse;'>");

        // 表頭
        html.append("<tr style='background-color:#f5f5f5;'>")
                .append("<th style='width:50px; text-align:center;'>序號</th>")
                .append("<th style='width:100px; text-align:center;'>狀態</th>")
                .append("<th style='width:80px; text-align:left;'>編號</th>")
                .append("<th style='width:200px; text-align:left;'>菜品</th>")
                .append("<th style='width:90px; text-align:center;'>數量（已做/總數）</th>")
                .append("<th style='width:90px; text-align:right;'>單價</th>")
                .append("<th style='width:100px; text-align:right;'>小計</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定顯示順序
        for (String status : Arrays.asList("UNSERVED", "PARTIALLY_SERVED", "SERVED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            // 🔧 外賣模式狀態文本
            String statusText = switch (status) {
                case "UNSERVED" -> "🔴 製作中";
                case "PARTIALLY_SERVED" -> "🟠 部分完成";
                case "SERVED" -> "🟢 製作完成";
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

                // 序號
                html.append(String.format("<td style='text-align:center; font-family:monospace;'>%d</td>", itemNumber++));

                // 狀態
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center;'>%s</td>",
                        statusColor, statusText
                ));

                // 編號 / 菜名
                html.append(String.format(
                        "<td style='white-space:nowrap;'>%s</td>" + "<td style='white-space:nowrap;'>%s</td>",
                        item.getItemCode(), item.getItemName()
                ));

                // 數量列：已做/總數
                String quantityProgress = String.format("%d/%d", item.getServedQuantity(), item.getQuantity());
                String quantityStyle = "PARTIALLY_SERVED".equals(item.getStatus())
                        ? "background-color:#fff3e0; font-weight:bold;" : "";
                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; %s'>%s</td>",
                        quantityStyle, quantityProgress
                ));

                // 單價 / 小計
                html.append(String.format(
                        "<td style='text-align:right; font-family:monospace;'>%.2f</td>" +
                                "<td style='text-align:right; font-weight:bold; color:#d32f2f; font-family:monospace;'>%.2f</td>",
                        item.getPriceAtOrder(), subtotal
                ));

                html.append("</tr>");
            }
        }

        html.append("</table>");

        // 總計
        if (includeTotal && totalAmount > 0) {
            html.append(String.format(
                    "<div style='margin-top:15px; padding:12px; background-color:#e8f5e9; " +
                            "text-align:right; font-size:18px; font-weight:bold; font-family:monospace;'>" +
                            "訂單總計：<span style='color:#c62828;'>%.2f 元</span></div>",
                    totalAmount
            ));
        }

        html.append("</body></html>");
        return html.toString();
    }
    /**
     * 显示添加菜品对话框
     * 改造后：使用注入的 menuItemService，不再手动 new DAO
     */
    private void showAddItemDialog() {
        JDialog dialog = new JDialog(frame, "添加新菜品 - " + getMenuTypeTitle(), true);

        // ===== 主题样式设置 =====
        dialog.getContentPane().setBackground(backgroundColor);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 280);
        dialog.setLocationRelativeTo(this);

        // ===== 表单面板 =====
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        // 1. 菜品名称
        formPanel.add(new JLabel("菜品名称: *"));
        JTextField nameField = new JTextField(20);
        formPanel.add(nameField);

        // 2. 价格
        formPanel.add(new JLabel("价格 (元): *"));
        JTextField priceField = new JTextField(10);
        formPanel.add(priceField);

        // 3. 菜品编号（自动生成）
        formPanel.add(new JLabel("菜品编号:"));
        String prefix = getPrefixForCurrentMenu();

        //  关键改造：通过 Service 获取下一个编号（替代 MenuCategoryService）
        String nextCode = prefix + "1"; // 默认值
        try {
            int categoryId = getMenuTypeConstant(); // 1/2/3/4
            nextCode = menuItemService.getNextItemCode(categoryId);
        } catch (Exception e) {
            System.err.println("生成菜品编号失败: " + e.getMessage());
            // 保留默认值
        }
        final String nextCodeFinal = nextCode; // final 供 Lambda 使用

        JLabel codeLabel = new JLabel("<html><b>" + nextCodeFinal + "</b> (自动生成)</html>");
        formPanel.add(codeLabel);

        // 4. 分类提示（只读）
        formPanel.add(new JLabel("所属分类:"));
        formPanel.add(new JLabel("<html><b>" + getMenuTypeTitle() + "</b></html>"));

        // ===== 按钮面板 =====
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认添加</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        // ===== 确认按钮事件处理器（核心改造）=====
        confirmBtn.addActionListener(ev -> {
            String name = nameField.getText().trim();
            String priceText = priceField.getText().trim();

            // 1. 输入验证
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品名称", "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                nameField.requestFocus();
                return;
            }
            if (priceText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入价格", "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                priceField.requestFocus();
                return;
            }

            try {
                // 2. 价格解析与格式化
                double price = Double.parseDouble(priceText);
                price = Math.round(price * 100.0) / 100.0; // 保留2位小数

                if (price <= 0) {
                    JOptionPane.showMessageDialog(dialog, "价格必须大于0", "输入错误",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 3. 创建实体对象
                int categoryId = getMenuTypeConstant();
                MenuItem newItem = new MenuItem(nextCodeFinal, name, price, categoryId);

                //通过注入的 Service 添加菜品（替代 new DAOImpl）
                //@Transactional 保证事务一致性
                boolean success = menuItemService.addItem(newItem);

                if (success) {
                    // 4. 成功反馈
                    String successMsg = String.format(
                            "菜品添加成功!\n编号: %s\n名称: %s\n价格: %.2f元",
                            newItem.getItemCode(),
                            newItem.getName(),
                            newItem.getPrice()
                    );
                    JOptionPane.showMessageDialog(dialog, successMsg, "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // 5. 缓存失效 + 刷新显示
                    String cacheKey = String.valueOf(menuType);
                    menuCache.remove(cacheKey);      // 清除旧缓存
                    loadMenuItems(false);            // 强制重新加载

                    // 6. 提示用户新菜品已显示
                    JOptionPane.showMessageDialog(frame,
                            "✨ 新菜品已添加并显示在菜单中！",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);

                } else {
                    throw new RuntimeException("数据库插入返回影响行数为0");
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "请输入有效的数字价格（例如：38.50）",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                priceField.requestFocus();

            } catch (Exception ex) {
                // 捕获所有业务/数据库异常
                JOptionPane.showMessageDialog(dialog,
                        "添加菜品失败: " + ex.getMessage(),
                        "操作错误",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace(); // 开发环境打印，生产环境建议用 logger
            }
        });

        // ===== 取消按钮事件 =====
        cancelBtn.addActionListener(ev -> dialog.dispose());

        // ===== 组装对话框 =====
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        // 显示对话框（模态）
        dialog.setVisible(true);
    }

    // 辅助方法：获取当前菜单的前缀
    private String getPrefixForCurrentMenu() {
        return switch (menuType) {
            case FOOD -> "A";
            case DRINK -> "B";
            case STIRFRY -> "C";
            default -> "D";
        };
    }

    /**
     * 显示更新菜品状态对话框
     * 改造后：使用注入的 menuItemService，不再手动 new DAO
     */
    private void showUpdateStatusDialog() {
        JDialog dialog = new JDialog(frame, "更新菜品状态 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 200);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // ===== 表单面板 =====
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        // 1. 菜品编号输入
        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);
        itemCodeField.setOpaque(true);
        formPanel.add(itemCodeField);

        // 2. 状态选择
        formPanel.add(new JLabel("目标状态: *"));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(backgroundColor);

        JRadioButton activeRadio = new JRadioButton("✓ 售卖中", true);
        JRadioButton inactiveRadio = new JRadioButton("✗ 已售罄");

        // 修复单选按钮背景色
        activeRadio.setBackground(backgroundColor);
        activeRadio.setOpaque(true);
        inactiveRadio.setBackground(backgroundColor);
        inactiveRadio.setOpaque(true);

        ButtonGroup group = new ButtonGroup();
        group.add(activeRadio);
        group.add(inactiveRadio);
        statusPanel.add(activeRadio);
        statusPanel.add(inactiveRadio);
        formPanel.add(statusPanel);

        // ===== 按钮面板 =====
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认更新</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        // ===== 确认按钮事件处理器（核心改造）=====
        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();

            // 1. 输入验证
            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                itemCodeField.requestFocus();
                return;
            }

            // 2. 验证菜品编号前缀是否匹配当前菜单类型
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "菜品编号前缀错误！\n当前菜单类型应为 '" + expectedPrefix + "' 开头（如 " + expectedPrefix + "1）",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 3. 获取目标状态
            boolean isActive = activeRadio.isSelected();

            try {
                //  关键改造：通过注入的 Service 更新状态（替代 new DAO）
                // @Transactional 保证事务一致性
                boolean success = menuItemService.updateStatus(itemCode, isActive);

                if (success) {
                    // 4. 成功反馈
                    String statusText = isActive ? "✓ 售卖中" : "✗ 已售罄";
                    String successMsg = String.format(
                            " 菜品状态更新成功！\n编号: %s\n新状态: %s",
                            itemCode, statusText
                    );
                    JOptionPane.showMessageDialog(dialog, successMsg, "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // 5. 缓存失效 + 强制刷新显示
                    String cacheKey = String.valueOf(menuType);
                    menuCache.remove(cacheKey);      // 清除旧缓存
                    loadMenuItems(false);            // 强制重新加载

                    // 6. 同步更新全局状态缓存（供点菜时验证）
                    if (frame instanceof OrderSystemGUI) {
                        ((OrderSystemGUI) frame).setMenuItemStatus(itemCode, isActive);
                    }

                    // 7. 提示用户菜单已刷新
                    JOptionPane.showMessageDialog(frame,
                            "✨ 菜单已刷新，状态变更已生效！",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);

                } else {
                    // 更新返回0行：菜品不存在
                    JOptionPane.showMessageDialog(dialog,
                            "⚠️ 未找到菜品编号: " + itemCode + "\n请检查编号是否正确",
                            "警告",
                            JOptionPane.WARNING_MESSAGE);
                    itemCodeField.requestFocus();
                }

            } catch (Exception ex) {
                // 捕获所有业务/数据库异常
                JOptionPane.showMessageDialog(dialog,
                        "更新菜品状态失败: " + ex.getMessage(),
                        "操作错误",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace(); // 开发环境打印，生产环境建议用 logger
            }
        });

        // ===== 取消按钮事件 =====
        cancelBtn.addActionListener(ev -> dialog.dispose());

        // ===== 组装对话框 =====
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        // 显示对话框（模态）
        dialog.setVisible(true);
    }

    /**
     * 显示点菜对话框（统一简化版 - 仅菜品选择）
     * 📝 联系电话/配送地址在【确认下单】时收集，不在此处重复输入
     */
    private void showOrderDialog() {
        // ===== 1. 验证前置条件 =====
        if (currentOrderType == OrderType.DINE_IN) {
            // 堂食模式：必须选择餐桌
            if (currentTableNumber == null || currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
                JOptionPane.showMessageDialog(frame, "请先选择餐桌号！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        // 外卖模式：允许直接点菜，订单号由系统生成或后续输入

        // ===== 2. 创建对话框 =====
        JDialog dialog = new JDialog(frame, "点菜 - " + currentOrderType.getDisplayName(), true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // ===== 3. 表单面板（仅菜品信息）=====
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        // 字段1：菜品编号
        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemIdField = new JTextField(15);
        itemIdField.setBackground(backgroundColor);
        formPanel.add(itemIdField);

        // 字段2：数量
        formPanel.add(new JLabel("数量: *"));
        JTextField quantityField = new JTextField("1", 10);
        quantityField.setBackground(backgroundColor);
        formPanel.add(quantityField);

        // ===== 4. 按钮面板 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(backgroundColor);

        //  关键：创建按钮后立即添加到 panel
        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认点菜</html> ", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);
        buttonPanel.add(confirmBtn);  // ← 必须添加！
        buttonPanel.add(cancelBtn);   // ← 必须添加！

        // ===== 5. 实时验证：输入菜品编号后自动查询信息（完整版）=====
        itemIdField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                String itemId = itemIdField.getText().trim().toUpperCase();
                if (!itemId.isEmpty()) {
                    // 通过注入的 Service 查询菜品（只读事务）
                    MenuItem item = menuItemService.getMenuItemByCode(itemId);
                    if (item != null && item.isActive()) {
                        //  完整反馈：显示菜品名称和价格（临时提示，不占用表单空间）
                        String tooltip = String.format("%s - ¥%.2f", item.getName(), item.getPrice());
                        itemIdField.setToolTipText(tooltip);

                        // 可选：在数量框旁显示简短提示（如需更强反馈可启用）
                        // quantityField.setToolTipText("已验证: " + item.getName());
                    } else {
                        //  完整反馈：明确告知用户菜品状态
                        String errorMsg = (item == null) ? "菜品不存在" : "菜品已售罄";
                        itemIdField.setToolTipText("❌ " + errorMsg);

                        // 可选：高亮输入框提示错误
                        itemIdField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                        SwingUtilities.invokeLater(() -> {
                            itemIdField.setBorder(BorderFactory.createLineBorder(backgroundColor, 1));
                        });
                    }
                } else {
                    itemIdField.setToolTipText(null);
                }
            }
        });

        // ===== 6. 确认按钮事件处理器（核心逻辑 - 完整版）=====
        confirmBtn.addActionListener(ev -> {
            String itemId = itemIdField.getText().trim().toUpperCase();
            String quantityStr = quantityField.getText().trim();

            // === 输入验证 ===
            if (itemId.isEmpty()) {
                showError(dialog, "请输入菜品编号", itemIdField);
                return;
            }
            if (!itemId.matches("[A-D]\\d+")) {
                showError(dialog, "菜品编号格式错误（如 A1, B2）", itemIdField);
                return;
            }
            // 验证前缀匹配当前菜单
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemId.startsWith(expectedPrefix)) {
                showError(dialog, "当前菜单应为 '" + expectedPrefix + "' 开头", itemIdField);
                return;
            }
            if (quantityStr.isEmpty()) {
                showError(dialog, "请输入数量", quantityField);
                return;
            }

            try {
                int quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0 || quantity > 99) {
                    showError(dialog, "数量必须在 1-99 之间", quantityField);
                    return;
                }

                // === 验证菜品可用性（通过 Service）===
                MenuItem item = menuItemService.getMenuItemByCode(itemId);
                if (item == null || !item.isActive()) {
                    showError(dialog, "菜品 " + itemId + " 不存在或已售罄", itemIdField);
                    return;
                }

                // === 添加到临时订单缓存（前端逻辑，保持不变）===
                boolean success = frame.addTemporaryOrder(currentTableNumber, itemId, quantity);

                if (success) {
                    // 记录日志（开发调试用）
                    System.out.println("临时订单: " + currentOrderType + " #" + currentTableNumber +
                            " +" + quantity + "x" + itemId + " [" + item.getName() + "]");

                    // 成功反馈
                    JOptionPane.showMessageDialog(dialog,
                            "✓ 点菜成功！\n" + item.getName() + " x" + quantity + " @ ¥" + String.format("%.2f", item.getPrice()),
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // 刷新两个 Panel 的临时订单显示
                    refreshTemporaryOrderDisplay();
                    frame.refreshHomeTemporaryOrder();
                } else {
                    JOptionPane.showMessageDialog(dialog, "点菜失败，请重试", "错误", JOptionPane.ERROR_MESSAGE);
                }

            } catch (NumberFormatException ex) {
                showError(dialog, "数量必须是整数", quantityField);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(dialog, "系统错误: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // ===== 取消按钮事件 =====
        cancelBtn.addActionListener(ev -> dialog.dispose());

        // ===== 7. 组装对话框 =====
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        //  关键：显示前确保组件已添加
        dialog.setVisible(true);

        // 可选：默认聚焦菜品编号输入框，提升用户体验
        itemIdField.requestFocus();
    }



    /**
     * 辅助方法：显示错误并聚焦输入框
     */
    private void showError(JDialog dialog, String message, JTextField field) {
        JOptionPane.showMessageDialog(dialog, message, "输入错误", JOptionPane.ERROR_MESSAGE);
        if (field != null) {
            field.requestFocus();
            field.selectAll();
        }
    }

    private void showCancelOrderDialog() {
        // 1. 验证餐桌选择
        if (currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
            JOptionPane.showMessageDialog(this, "请先选择餐桌号", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. 获取当前餐桌的临时订单
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        if (tempOrder == null || tempOrder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前餐桌没有临时订单可以取消", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 3. 创建取消点菜对话框
        JDialog dialog = new JDialog(frame, "取消点菜", true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 220);
        dialog.setLocationRelativeTo(this);

        // 4. 构建菜品选择下拉框（显示：编号 - 名称 (当前数量)）
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel itemLabel = new JLabel("选择菜品:");
        JComboBox<String> itemComboBox = new JComboBox<>();

        // 填充下拉框选项
        tempOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String itemId = entry.getKey();
                    int currentQty = entry.getValue();
                    String itemName = "（未知）";

                    // 尝试获取菜品名称
                    com.restaurant.entity.MenuItem item = frame.getMenuItemById(itemId);
                    if (item != null) {
                        itemName = item.getName();
                    }

                    itemComboBox.addItem(String.format("%s - %s (当前: %d 份)",
                            itemId, itemName, currentQty));
                });

        JLabel qtyLabel = new JLabel("取消数量:");
        JTextField qtyField = new JTextField("1");

        formPanel.add(itemLabel);
        formPanel.add(itemComboBox);
        formPanel.add(qtyLabel);
        formPanel.add(qtyField);

        // 5. 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton confirmBtn = new JButton("确认取消");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);

        // 6. 确认取消逻辑
        confirmBtn.addActionListener(ev -> {
            try {
                // 解析选中的菜品
                String selectedItem = (String) itemComboBox.getSelectedItem();
                if (selectedItem == null) {
                    JOptionPane.showMessageDialog(dialog, "请选择要取消的菜品", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 提取菜品编号（格式: "A1 - 宫保鸡丁 (当前: 2 份)"）
                String itemId = selectedItem.split(" - ")[0].trim().toUpperCase();

                // 验证数量
                String qtyStr = qtyField.getText().trim();
                if (qtyStr.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入取消数量", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int cancelQty = Integer.parseInt(qtyStr);
                if (cancelQty <= 0) {
                    JOptionPane.showMessageDialog(dialog, "取消数量必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 获取当前数量（双重验证）
                int currentQty = tempOrder.getOrDefault(itemId, 0);
                if (cancelQty > currentQty) {
                    JOptionPane.showMessageDialog(dialog,
                            String.format("取消数量不能超过当前数量！\n当前 %s 有 %d 份，您输入了 %d 份",
                                    itemId, currentQty, cancelQty),
                            "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 7. 执行取消操作（核心：添加负数量）
                frame.addTemporaryOrder(currentTableNumber, itemId, -cancelQty);

                // 8. 刷新UI
                refreshTemporaryOrderDisplay();          // 刷新当前MenuPanel
                frame.refreshHomeTemporaryOrder();       // 刷新HomePanel

                // 9. 关闭对话框并提示成功
                dialog.dispose();
                JOptionPane.showMessageDialog(frame,
                        String.format("已取消 %s × %d 份", itemId, cancelQty),
                        "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "取消数量必须是有效整数", "输入错误", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "取消操作失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showRemoveItemDialog() {
        JDialog dialog = new JDialog(frame, "物理删除菜品 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(480, 220);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);
        itemCodeField.setOpaque(true);
        formPanel.add(itemCodeField);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>⚠️</b>&nbsp;强制删除</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();

            // 验证前缀匹配当前菜单类型
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "菜品编号必须以 '" + expectedPrefix + "' 开头（当前菜单类型：" + getMenuTypeTitle() + "）",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                boolean deleted = frame.deleteMenuItemPhysically(itemCode);

                if (deleted) {
                    // 刷新 UI
                    String cacheKey = String.valueOf(menuType);
                    menuCache.remove(cacheKey);
                    loadMenuItems(false);
                    refreshTemporaryOrderDisplay();

                    JOptionPane.showMessageDialog(dialog,
                            "菜品 " + itemCode + " 已物理删除！\n" +
                                    "注意：历史订单中该菜品信息已丢失。",
                            "删除成功", JOptionPane.WARNING_MESSAGE);
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "未找到菜品：" + itemCode + "\n请检查编号是否正确",
                            "删除失败", JOptionPane.ERROR_MESSAGE);
                }
            } catch (RuntimeException e) {
                // 捕获业务异常（如外键约束）
                JOptionPane.showMessageDialog(dialog,
                        "删除失败：" + e.getMessage(),
                        "操作错误", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(dialog,
                        "系统错误：" + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showReviseItemPriceDialog() {
        JDialog dialog = new JDialog(frame, "更改菜品价格 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 220);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);
        itemCodeField.setOpaque(true);
        formPanel.add(itemCodeField);

        formPanel.add(new JLabel("新价格 (元): *"));
        JTextField priceField = new JTextField(10);
        priceField.setBackground(backgroundColor);
        priceField.setOpaque(true);
        formPanel.add(priceField);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认修改</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();
            String priceText = priceField.getText().trim();

            // 1. 基础验证
            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (priceText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入新价格", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2. 格式完整性验证（防止仅输入前缀字母）
            if (itemCode.length() <= 1 || !Character.isDigit(itemCode.charAt(1))) {
                JOptionPane.showMessageDialog(dialog,
                        "<html>菜品编号格式错误！<br>" +
                                "正确格式应为：前缀字母 + 数字（例如 A1、B2、C3）<br>" +
                                "仅输入字母（如 'A'）无效</html>",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 3. 前缀验证（防止跨菜单操作）
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "<html>菜品编号前缀错误！<br>" +
                                "当前菜单应为 '" + expectedPrefix + "' 开头（例如 " + expectedPrefix + "1）</html>",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            //  4. 【关键新增】菜品存在性验证（查询数据库）
            com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode);
            if (menuItem == null) {
                JOptionPane.showMessageDialog(dialog,
                        "<html> 菜品 " + itemCode + " 不存在！<br><br>" +
                                "可能原因：<br>" +
                                "• 编号输入错误（请检查数字部分）<br>" +
                                "• 该菜品已被删除<br>" +
                                "• 请先通过「添加菜品」功能创建该菜品</html>",
                        "菜品不存在", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // 显示找到的菜品信息（增强用户体验）
            System.out.println("✓ 找到菜品: " + menuItem.getName() + " (当前价格: " + menuItem.getPrice() + "元)");

            // 5. 价格格式验证
            double newPrice;
            try {
                newPrice = Double.parseDouble(priceText);
                newPrice = Math.round(newPrice * 100.0) / 100.0; // 保留2位小数
                if (newPrice <= 0) {
                    JOptionPane.showMessageDialog(dialog, "价格必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                //  可选：价格合理性警告（防止误操作）
                double currentPrice = menuItem.getPrice();
                double changeRatio = Math.abs(newPrice - currentPrice) / currentPrice;
                if (changeRatio > 0.5 && currentPrice > 10) { // 价格变动超过50%且原价>10元
                    int confirm = JOptionPane.showConfirmDialog(dialog,
                            "<html> 价格变动较大！<br>" +
                                    "当前价格: " + String.format("%.2f", currentPrice) + "元 → 新价格: " + String.format("%.2f", newPrice) + "元<br>" +
                                    "变动幅度: " + String.format("%.0f%%", changeRatio * 100) + "<br><br>" +
                                    "确认要修改吗？</html>",
                            "价格变动警告", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效的数字价格", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 6. 调用业务方法
            boolean success = frame.updateMenuItemPrice(itemCode, newPrice);
            if (success) {
                // 7. 刷新UI
                String cacheKey = String.valueOf(menuType);
                menuCache.remove(cacheKey);  // 清除菜单缓存
                loadMenuItems(false);        // 重新加载菜单

                JOptionPane.showMessageDialog(dialog,
                        "<html> 菜品 " + itemCode + " 价格更新成功！<br>" +
                                "「" + menuItem.getName() + "」<br>" +
                                "价格已从 " + String.format("%.2f", menuItem.getPrice()) + " 元<br>" +
                                "更新为 " + String.format("%.2f", newPrice) + " 元</html>",
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                //  增强失败提示（虽然理论上不会走到这里，因为前面已验证存在性）
                JOptionPane.showMessageDialog(dialog,
                        "价格修改失败，请稍后重试或联系管理员",
                        "操作失败", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
