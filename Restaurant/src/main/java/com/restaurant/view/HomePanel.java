package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.*;
import com.restaurant.entity.MenuItem;
import com.restaurant.service.MenuItemService;
import com.restaurant.service.RestaurantService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HomePanel extends JPanel {
    private final JLabel tableNumberLabel = new JLabel("餐桌号：");
    private final JLabel totalPriceLabel = new JLabel("总价格：");
    private final JLabel statusLabel = new JLabel("订单情况：");

    // ===== 滚动区域组件 =====
    private final JEditorPane tempOrderEditor = new JEditorPane();
    private final JEditorPane orderedItemsEditor = new JEditorPane();
    private final JScrollPane tempScrollPane = new JScrollPane(tempOrderEditor);
    private final JScrollPane ordScrollPane = new JScrollPane(orderedItemsEditor);

    // ===== 新增：3个订单类型显示面板 =====
    private final JEditorPane dineInOrderEditor = new JEditorPane();
    private final JScrollPane dineInScrollPane = new JScrollPane(dineInOrderEditor);
    private final JEditorPane takeoutOrderEditor = new JEditorPane();
    private final JScrollPane takeoutScrollPane = new JScrollPane(takeoutOrderEditor);
    private final JEditorPane reservationOrderEditor = new JEditorPane();
    private final JScrollPane reservationScrollPane = new JScrollPane(reservationOrderEditor);

    private JTextField tableNumberField;
    private String currentTableNumber = "";
    private OrderType currentOrderType = OrderType.DINE_IN;

    private final OrderSystemGUI frame;
    private final RestaurantService service;
    private final RestaurantController controller;
    private final MenuItemService menuItemService;  // 新增字段

    private JComboBox<OrderType> orderTypeCombo;
    private JButton confirmTableBtn;
    private JLabel fieldLabel;  // 显示"餐桌号："或"订单号："
    private JCheckBox generateOrderNumberCheck;
    private String currentTakeoutOrderNumber = null;

    private JButton confirmOrderBtn;
    private JButton confirmServedBtn;
    private JButton cancelOrderItemBtn;
    private JButton cancelReorderBtn;
    private JButton cancelTakeoutBtn;
    private JButton deliveryBtn;
    private JButton prepareOrderItemBtn;
    private JLabel itemsTotalLabel, groupTableLabel;      // 菜品总价
    private JLabel deliveryFeeLabel;     // 配送费


    public HomePanel(OrderSystemGUI frame,
                     RestaurantService service,
                     RestaurantController controller,
                     MenuItemService menuItemService) {
        this.frame = frame;
        this.service = service;
        this.controller = controller;
        this.menuItemService = menuItemService;
        initializeUI();
        refreshOrderTypeDisplays();
    }


    public void setCurrentTableNumber(String tableNumber) {
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            this.currentTableNumber = "未选择";
            updateLabelsAndButtons();  //  更新标签和按钮文本
        } else {
            this.currentTableNumber = tableNumber.trim();
            tableNumberLabel.setText(getLabelPrefix() + this.currentTableNumber);
            if (tableNumberField != null && !this.currentTableNumber.equals("未选择")) {
                tableNumberField.setText(this.currentTableNumber);
            }
        }
        refreshTemporaryOrderDisplay();
        refreshFormalOrderDisplay();
        revalidate();
        repaint();
    }

    /**
     * 获取标签前缀（根据订单类型）
     */
    private String getLabelPrefix() {
        return switch (currentOrderType) {
            case DINE_IN -> "餐桌号：";
            case PICKUP -> "自取订单：";
            case DELIVERY -> "配送订单：";
            case RESERVATION -> "预约号：";  // 🔧 新增：预约模式显示"预约号："
            default -> "订单：";
        };
    }

    /**
     * 获取按钮文本（根据订单类型）
     */
    private String getButtonText() {
        return switch (currentOrderType) {
            case DINE_IN -> "确认餐桌";
            case PICKUP, DELIVERY -> "确认订单号";
            case RESERVATION -> "确认预约号";
            default -> "确认";
        };
    }

    /**
     * 获取底部输入框标签前缀（根据订单类型）
     *
     * @return "餐桌号："/"订单号："
     */
    private String getFieldLabelPrefix() {
        if (currentOrderType == null) {
            currentOrderType = OrderType.DINE_IN;
        }
        return switch (currentOrderType) {
            case DINE_IN -> "餐桌号：";
            case PICKUP, DELIVERY -> "订单号：";
            case RESERVATION -> "预约号：";
            default -> "订单：";
        };
    }

    /**
     * 更新标签和按钮文本（根据订单类型）
     */
    private void updateLabelsAndButtons() {
        String labelPrefix = getLabelPrefix();        // "餐桌号："/"自取订单："/"配送订单："
        String fieldPrefix = getFieldLabelPrefix();   // "餐桌号："/"订单号："
        String buttonText = getButtonText();          // "确认餐桌"/"确认订单号"

        //  1. 更新顶部标签（tableNumberLabel）
        if (currentTableNumber == null || currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
            if (currentOrderType == OrderType.DINE_IN) {
                tableNumberLabel.setText(labelPrefix + "未选择");
            } else if (currentOrderType == OrderType.RESERVATION) {
                tableNumberLabel.setText(labelPrefix + "待确认");  // 🔧 预约模式显示"待确认"
            } else {
                tableNumberLabel.setText(labelPrefix + "待下单");
            }
        } else {
            tableNumberLabel.setText(labelPrefix + currentTableNumber);
        }

        //  2. 更新底部输入框标签（fieldLabel）
        if (fieldLabel != null) {
            fieldLabel.setText(fieldPrefix);
        }

        //  3. 更新按钮文本（confirmTableBtn）
        if (confirmTableBtn != null) {
            confirmTableBtn.setText(buttonText);
        }
    }


    private void initializeUI() {
        // ===== 主布局：水平分割（左侧70% | 右侧30%）=====
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // ===== 左侧面板：现有内容 =====
        JPanel leftPanel = createLeftPanel();

        //  关键：设置左侧面板的最小/首选尺寸（防止被挤压）
        leftPanel.setMinimumSize(new Dimension(700, 400));
        leftPanel.setPreferredSize(new Dimension(850, 600));

        // ===== 右侧面板：3个订单列表 =====
        JPanel rightPanel = createRightPanel();


        // ===== 使用JSplitPane分割左右 =====
        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightPanel
        );

        // 修改分割比例：左侧70%，右侧30%
        mainSplitPane.setResizeWeight(0.70);      // 左侧占70%
        mainSplitPane.setDividerLocation(0.70);   // 初始分割位置70%
        mainSplitPane.setContinuousLayout(true);

        //  三重保障：确保分割位置正确
        SwingUtilities.invokeLater(() -> {
            if (mainSplitPane.isShowing()) {
                mainSplitPane.setDividerLocation(0.70);
            }
        });

        mainSplitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (mainSplitPane.isShowing() && mainSplitPane.getWidth() > 0) {
                        // 保持70/30比例
                        mainSplitPane.setDividerLocation(0.70);
                    }
                });
            }
        });

        add(mainSplitPane, BorderLayout.CENTER);
    }


    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        // -- 1. NORTH: 4 个彩色按钮 --
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton foodBtn = createSquareButton("特色食物", new Color(255, 204, 204));
        JButton drinkBtn = createSquareButton("饮料", new Color(204, 229, 255));
        JButton stirFryBtn = createSquareButton("小炒", new Color(255, 230, 180));
        JButton setMealBtn = createSquareButton("套餐", new Color(204, 255, 204));

        foodBtn.addActionListener(e -> frame.showPanel("Food"));
        drinkBtn.addActionListener(e -> frame.showPanel("Drink"));
        stirFryBtn.addActionListener(e -> frame.showPanel("StirFry"));
        setMealBtn.addActionListener(e -> frame.showPanel("SetMeal"));

        buttonPanel.add(foodBtn);
        buttonPanel.add(drinkBtn);
        buttonPanel.add(stirFryBtn);
        buttonPanel.add(setMealBtn);
        panel.add(buttonPanel, BorderLayout.NORTH);

        // -- 2. CENTER: 标签 + 订单显示 --
        JPanel middlePanel = new JPanel(new BorderLayout(0, 10));

        // 标签栏 - 左对齐
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 16);
        tableNumberLabel.setFont(labelFont);
        totalPriceLabel.setFont(labelFont);
        statusLabel.setFont(labelFont);

        itemsTotalLabel = new JLabel("菜品：0.00 元");
        itemsTotalLabel.setFont(labelFont);
        itemsTotalLabel.setVisible(false);  // 默认隐藏

        // 【新增】配送费标签（仅配送模式显示）
        deliveryFeeLabel = new JLabel("配送费：0.00 元");
        deliveryFeeLabel.setFont(labelFont);
        deliveryFeeLabel.setVisible(false);  // 默认隐藏

        groupTableLabel = new JLabel("组合桌: ");
        groupTableLabel.setFont(labelFont); // 保持字体一致
        groupTableLabel.setVisible(false);  // 默认隐藏

        labelPanel.add(tableNumberLabel);
        labelPanel.add(itemsTotalLabel);
        labelPanel.add(deliveryFeeLabel);
        labelPanel.add(totalPriceLabel);
        labelPanel.add(statusLabel);
        labelPanel.add(groupTableLabel);
        middlePanel.add(labelPanel, BorderLayout.NORTH);


        // 左右分栏：临时订单 | 已下单食物
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        JPanel leftOrderPanel = new JPanel(new BorderLayout());
        leftOrderPanel.add(new JLabel("  临时订单", SwingConstants.LEFT), BorderLayout.NORTH);
        leftOrderPanel.add(tempScrollPane, BorderLayout.CENTER);

        JPanel rightOrderPanel = new JPanel(new BorderLayout());
        rightOrderPanel.add(new JLabel("  已下单的食物", SwingConstants.LEFT), BorderLayout.NORTH);
        rightOrderPanel.add(ordScrollPane, BorderLayout.CENTER);

        contentPanel.add(leftOrderPanel);
        contentPanel.add(rightOrderPanel);
        middlePanel.add(contentPanel, BorderLayout.CENTER);

        panel.add(middlePanel, BorderLayout.CENTER);

        // -- 3. SOUTH: 操作按钮（分两行显示，居中对齐）--
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 第一行：订单类型 + 输入框 + 确认按钮
        JPanel topRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        topRowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        tableNumberField = new JTextField(10);
        confirmTableBtn = new JButton("确认餐桌");
        fieldLabel = new JLabel("订单号：");
        fieldLabel.setFont(labelFont);

        generateOrderNumberCheck = new JCheckBox("自动生号");
        generateOrderNumberCheck.setFont(labelFont);
        generateOrderNumberCheck.setSelected(false);
        generateOrderNumberCheck.setVisible(false);  // 默认隐藏

        orderTypeCombo = new JComboBox<>(OrderType.values());
        orderTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof OrderType) {
                    setText(((OrderType) value).getDisplayName());
                }
                return this;
            }
        });
        orderTypeCombo.setSelectedItem(OrderType.DINE_IN);

        // ========== 🔧 订单类型变化监听器（核心修改）==========
        orderTypeCombo.addActionListener(e -> {
            OrderType selectedType = (OrderType) orderTypeCombo.getSelectedItem();
            boolean isDineIn = (selectedType == OrderType.DINE_IN);
            boolean isDelivery = (selectedType == OrderType.DELIVERY);
            boolean isPickup = (selectedType == OrderType.PICKUP);
            boolean isReservation = (selectedType == OrderType.RESERVATION);  // 🔧 新增

            // 更新标签和按钮文本
            // 🔧 修改：预约模式也显示"预约号："
            fieldLabel.setText(isDineIn ? "餐桌号：" : (isReservation ? "预约号：" : "订单号："));
            tableNumberField.setEditable(true);
            confirmTableBtn.setEnabled(true);

            // 🔧 修改：预约模式按钮显示"确认预约号"
            if (isDineIn) {
                confirmTableBtn.setText("确认餐桌");
            } else if (isReservation) {
                confirmTableBtn.setText("确认预约号");
            } else {
                confirmTableBtn.setText("确认订单号");
            }

            // 控制复选框可见性
            // 🔧 修改：只有外卖才显示"自动生号"，预约模式不显示
            boolean isTakeout = (selectedType == OrderType.PICKUP || selectedType == OrderType.DELIVERY);
            generateOrderNumberCheck.setVisible(isTakeout);

            // 堂食/预约模式清空外卖状态
            if (isDineIn || isReservation) {
                generateOrderNumberCheck.setSelected(false);
                currentTakeoutOrderNumber = null;
            }

            // 更新全局状态
            frame.setCurrentOrderType(selectedType);
            tableNumberField.setText("");
            currentTableNumber = "";
            frame.setCurrentTableNumber("");

            // 🔧 修改：只有外卖模式才显示订单号前缀，预约模式不需要
            if (isTakeout && !generateOrderNumberCheck.isSelected()) {
                String prefix = isPickup ? "P" : "D";
                String dateStr = java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                );
                tableNumberField.setText(prefix + "-" + dateStr + "-");
                // 将光标定位到序号位置
                tableNumberField.setCaretPosition(tableNumberField.getText().length());
            }

            // 更新顶部标签
            // 🔧 修改：预约模式显示"预约号：待确认"
            if (isDineIn) {
                tableNumberLabel.setText("餐桌号：未选择");
            } else if (isReservation) {
                tableNumberLabel.setText("预约号：待确认");
            } else if (isPickup) {
                tableNumberLabel.setText("自取订单：待下单");
            } else {
                tableNumberLabel.setText("配送订单：待下单");
            }

            frame.clearTemporaryOrder(currentTableNumber);
            refreshTemporaryOrderDisplay();
            refreshFormalOrderDisplay();

            //  动态更新"确认上桌"按钮文本和可见性
            if (confirmServedBtn != null) {
                if (isDineIn) {
                    // 堂食模式：显示"确认上桌"
                    confirmServedBtn.setText("确认上桌");
                    confirmServedBtn.setToolTipText("标记菜品已送达餐桌");
                    confirmServedBtn.setVisible(true);
                } else if (isReservation) {
                    // 🔧 预约模式：隐藏"确认上桌"按钮（客人还没来）
                    confirmServedBtn.setVisible(false);
                } else {
                    // 外卖模式：显示"制作完成"
                    confirmServedBtn.setText("制作完成");
                    confirmServedBtn.setToolTipText("标记菜品已制作完成，等待取餐/配送");
                    confirmServedBtn.setVisible(true);
                }
            }

            //  控制"取消重新点餐"和"撤销外卖"按钮可见性
            if (cancelReorderBtn != null) {
                cancelReorderBtn.setVisible(isDineIn);
            }
            if (cancelTakeoutBtn != null) {
                cancelTakeoutBtn.setVisible(!isDineIn && !isReservation);  // 🔧 预约也不显示
            }

            // 在 orderTypeCombo.addActionListener 的末尾添加：
            if (prepareOrderItemBtn != null) {
                // 🔧 只有预约订单才显示"标记准备"按钮
                prepareOrderItemBtn.setVisible(!isDelivery && !isPickup);
            }

            if (itemsTotalLabel != null) {
                itemsTotalLabel.setVisible(isDelivery);
            }
            if (deliveryFeeLabel != null) {
                deliveryFeeLabel.setVisible(isDelivery);
            }

            if (deliveryBtn != null) {
                deliveryBtn.setVisible(isDelivery);
            }

            // 外卖模式提示（预约模式不显示）
            if (!isDineIn && !isReservation) {
                JOptionPane.showMessageDialog(
                        HomePanel.this,
                        "【" + selectedType.getDisplayName() + "】模式\n" +
                                " ☑ 勾选【自动生号】可生成新订单号\n" +
                                " 或直接输入已有订单号点击【确认订单号】",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
        generateOrderNumberCheck.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                generateAndDisplayOrderNumber();
            } else {
                // 🔧 修改：取消自动生号时，显示订单号前缀而不是清空
                currentTakeoutOrderNumber = null;

                OrderType type = (OrderType) orderTypeCombo.getSelectedItem();
                String prefix = "";

                if (type == OrderType.PICKUP) {
                    prefix = "P";
                    tableNumberLabel.setText("自取订单：待下单");
                } else if (type == OrderType.DELIVERY) {
                    prefix = "D";
                    tableNumberLabel.setText("配送订单：待下单");
                }

                // 🔧 生成带日期的前缀（格式：P-20260308- 或 D-20260308-）
                if (!prefix.isEmpty()) {
                    String dateStr = java.time.LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                    );
                    String orderPrefix = prefix + "-" + dateStr + "-";
                    tableNumberField.setText(orderPrefix);
                    // 将光标定位到序号位置
                    tableNumberField.setCaretPosition(tableNumberField.getText().length());
                } else {
                    tableNumberField.setText("");
                }
            }
        });

        // 第一行组件
        topRowPanel.add(fieldLabel);
        topRowPanel.add(orderTypeCombo);
        topRowPanel.add(generateOrderNumberCheck);
        topRowPanel.add(Box.createHorizontalStrut(10));
        topRowPanel.add(tableNumberField);
        topRowPanel.add(confirmTableBtn);

        // 第二行：操作按钮
        JPanel buttonRowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonRowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        confirmOrderBtn = new JButton("确认下单");
        confirmServedBtn = new JButton("确认上桌");
        cancelOrderItemBtn = new JButton("撤销菜品");
        cancelReorderBtn = new JButton("取消重新点餐");

        //  新增：撤销外卖按钮（默认隐藏）
        cancelTakeoutBtn = new JButton("🗑️ 撤销外卖");
        cancelTakeoutBtn.setVisible(false);
        cancelTakeoutBtn.setBackground(new Color(255, 100, 100));
        cancelTakeoutBtn.setForeground(Color.WHITE);
        cancelTakeoutBtn.setFocusPainted(false);

        deliveryBtn = new JButton("配送");
        deliveryBtn.setVisible(false);
        deliveryBtn.setBackground(new Color(255, 165, 0));
        deliveryBtn.setForeground(Color.WHITE);
        deliveryBtn.setFocusPainted(false);
        deliveryBtn.setToolTipText("点击更新订单状态");  // 悬停提示

        // 🔧 新增：标记准备按钮（预约订单专用）
        prepareOrderItemBtn = new JButton("🍳 标记准备");
        prepareOrderItemBtn.setBackground(new Color(255, 165, 0));  // 橙色
        prepareOrderItemBtn.setForeground(Color.WHITE);
        prepareOrderItemBtn.setFocusPainted(false);
        prepareOrderItemBtn.setVisible(true);
        prepareOrderItemBtn.setToolTipText("标记预约订单菜品准备状态");

        buttonRowPanel.add(confirmOrderBtn);
        buttonRowPanel.add(confirmServedBtn);
        buttonRowPanel.add(cancelOrderItemBtn);
        buttonRowPanel.add(cancelReorderBtn);
        buttonRowPanel.add(cancelTakeoutBtn);  //  添加新按钮
        buttonRowPanel.add(deliveryBtn);
        buttonRowPanel.add(prepareOrderItemBtn);  // 🔧 添加新按钮

        bottomPanel.add(topRowPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        bottomPanel.add(buttonRowPanel);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // ========== 🔧 绑定按钮事件 ==========

        // 确认餐桌/订单号
        confirmTableBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputStr = tableNumberField.getText().trim();
                handleConfirmTable(inputStr);
            }
        });

        // 确认下单
        confirmOrderBtn.addActionListener(this::handleConfirmOrder);

        // 确认上桌/制作完成
        confirmServedBtn.addActionListener(e -> {
            if (currentOrderType == OrderType.DINE_IN) {
                showConfirmServedDialog(currentTableNumber);
            } else {
                String orderIdentifier = currentTakeoutOrderNumber != null ?
                        currentTakeoutOrderNumber : tableNumberField.getText().trim();
                showConfirmTakeoutReadyDialog(orderIdentifier);
            }
        });

        // 撤销菜品（堂食）
        cancelOrderItemBtn.addActionListener(e -> showCancelOrderItemDialog());

        // 取消重新点餐（堂食专用）
        cancelReorderBtn.addActionListener(e -> handleCancelReorder());

        //  撤销外卖（外卖专用）
        cancelTakeoutBtn.addActionListener(e -> handleCancelTakeoutOrder());

        deliveryBtn.addActionListener(e -> handleDeliveryOrder());

        // 在事件绑定区域添加：
        prepareOrderItemBtn.addActionListener(e -> showPrepareOrderItemDialog());

        // ========== 🔧 事件绑定结束 ==========

        // 初始化编辑器
        tempOrderEditor.setContentType("text/html");
        tempOrderEditor.setEditable(false);
        orderedItemsEditor.setContentType("text/html");
        orderedItemsEditor.setEditable(false);

        return panel;
    }

    /**
     * 生成下一个外卖订单号（从数据库查询最大号+1）
     * 格式：P-20260305-001（自取）或 D-20260305-001（配送）
     */
    private void generateAndDisplayOrderNumber() {
        OrderType orderType = (OrderType) orderTypeCombo.getSelectedItem();
        if (orderType == null || orderType == OrderType.DINE_IN) {
            return;
        }
        try {
            // 1. 获取订单号前缀
            String prefix = (orderType == OrderType.PICKUP) ? "P" : "D";

            // 2. 获取今天的日期字符串（格式：20260305）
            String dateStr = java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            );

            // 3. 从数据库查询今天的最大订单号
            int nextNumber = frame.getNextTakeoutOrderNumber(orderType);

            // 4. 生成完整订单号%s-%s-%03d:（格式：P-20260305-001）
            //"%s-%s-%d"（格式：P-20260305-1）
            currentTakeoutOrderNumber = String.format("%s-%s-%d", prefix, dateStr, nextNumber);

            // 5. 更新顶部标签，替代"待下单"
            String labelPrefix = (orderType == OrderType.PICKUP) ? "自取订单：" : "配送订单：";
            tableNumberLabel.setText(labelPrefix + currentTakeoutOrderNumber);

            // 6. 自动填充到输入框（方便确认）
            tableNumberField.setText(currentTakeoutOrderNumber);

            // 7. 【关键修复】更新 currentTableNumber 并同步到全局
            currentTableNumber = currentTakeoutOrderNumber;
            frame.setCurrentTableNumber(currentTakeoutOrderNumber);

            //  8. 刷新显示（让 MenuPanel 能立即看到订单号）
            refreshTemporaryOrderDisplay();
            refreshFormalOrderDisplay();

            System.out.println("生成外卖订单号: " + currentTakeoutOrderNumber);

            //  9. 可选：自动切换到菜单面板（提升体验）
            // frame.showPanel("Food");  // 如需自动跳转可取消注释

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "生成订单号失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
            generateOrderNumberCheck.setSelected(false);
            ex.printStackTrace();
        }
    }

    /**
     * 获取当前生成的外卖订单号（供确认下单时使用）
     */
    public String getCurrentTakeoutOrderNumber() {
        return currentTakeoutOrderNumber;
    }


    /**
     * 創建右側面板（3 個訂單列表：堂食、外賣合併、預約）
     */
    private JPanel createRightPanel() {
        // 🔧 改為 3 行 1 列
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. 堂食訂單面板
        JPanel dineInPanel = createOrderTypePanel("🍽️ 堂食訂單", dineInScrollPane, new Color(255, 200, 200));
        panel.add(dineInPanel);

        // 2. 🔧 外賣訂單面板 (合併自取 + 配送)
        JPanel takeoutPanel = createOrderTypePanel(" 外賣訂單 (自取 + 配送)", takeoutScrollPane, new Color(200, 255, 255));
        panel.add(takeoutPanel);

        // 3. 🔧 預約訂單面板 (新增 - 暫時顯示佔位符)
        JPanel reservationPanel = createOrderTypePanel(" 預約訂單", reservationScrollPane, new Color(230, 230, 255));
        panel.add(reservationPanel);

        return panel;
    }


    /**
     * 创建订单类型面板（支持滚动）
     */
    private JPanel createOrderTypePanel(String title, JScrollPane scrollPane, Color bgColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgColor);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Color.GRAY, 2),
                        title,
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("Microsoft YaHei", Font.BOLD, 14),
                        Color.BLACK
                ),
                new EmptyBorder(5, 5, 5, 5)
        ));

        // 设置滚动策略
        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        scrollPane.setBorder(null);
        scrollPane.setBackground(bgColor.brighter());

        // 配置内部 JEditorPane
        if (scrollPane.getViewport().getView() instanceof JEditorPane editor) {
            editor.setContentType("text/html");
            editor.setEditable(false);
            editor.setPreferredSize(null);  // 保持自适应
            editor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        }

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JButton createSquareButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        btn.setPreferredSize(new Dimension(150, 150));
        btn.setFocusPainted(false);
        return btn;
    }

    public void updateIdentifierLabel(String identifier) {
        tableNumberLabel.setText(buildTableNumberLabelText(identifier));
        refreshTemporaryOrderDisplay();
        refreshFormalOrderDisplay();
    }

    private String buildTableNumberLabelText(String identifier) {
        String prefix = getLabelPrefix();
        if (identifier == null || identifier.isEmpty() || "未选择".equals(identifier)) {
            if (currentOrderType == OrderType.DINE_IN) {
                return prefix + "未选择";
            } else {
                return prefix + "待下单";
            }
        }
        return prefix + identifier.trim();
    }

    /**
     * 设置当前订单类型（由 OrderSystemGUI 调用）
     *
     * @param orderType 订单类型枚举
     */
    public void setCurrentOrderType(OrderType orderType) {
        if (this.currentOrderType != orderType) {
            this.currentOrderType = orderType;

            //  更新标签和按钮文本
            updateLabelsAndButtons();

            // 订单类型改变时，刷新标签前缀（保留原标识）
            String currentId = (this.currentTableNumber != null &&
                    !this.currentTableNumber.isEmpty() &&
                    !"未选择".equals(this.currentTableNumber))
                    ? this.currentTableNumber : "";
            updateIdentifierLabel(currentId);
        }
    }


    public void refreshTemporaryOrderDisplay() {
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        StringBuilder html = new StringBuilder();

        // 🔧 设置整体样式：强制不换行，支持横向滚动
        html.append("<html><head><style>" +
                "body { font-family: 'Microsoft YaHei', sans-serif; margin: 0; padding: 10px; } " +
                "table { border-collapse: collapse; width: 100%; table-layout: auto; min-width: 600px; } " + // min-width 触发滚动条
                "th, td { border: 1px solid #ddd; padding: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; } " + // 关键：不换行
                "th { background-color: #f2f2f2; text-align: center; font-weight: bold; } " +
                ".right-align { text-align: right; } " +
                ".center-align { text-align: center; } " +
                ".price { color: #d32f2f; font-weight: bold; } " +
                ".batch-tag { color: #1976d2; font-size: 12px; display: block; margin-top: 2px; } " +
                ".table-ids { color: #1976d2; font-size: 12px; } " +
                ".total-box { margin-top: 15px; padding: 10px; background-color: #e8f5e9; border-radius: 4px; text-align: right; } " +
                "</style></head><body>");

        if (tempOrder == null || tempOrder.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>（暂无临时订单）</p>");
        } else {
            html.append("<table>");
            html.append("<thead><tr>");
            html.append("<th style='width: 15%;'>菜品编号</th>");
            html.append("<th style='width: 25%;'>菜品名称</th>");
            html.append("<th style='width: 10%;' class='center-align'>数量</th>");

            // 🔧【核心修改】只有聚餐桌 + 堂食才显示"分配餐桌"列
            boolean isGroupedTable = isGroupedTable(currentTableNumber);
            boolean showAssignedTable = (currentOrderType == OrderType.DINE_IN && isGroupedTable);

            if (showAssignedTable) {
                html.append("<th style='width: 25%; text-align: left;'>分配餐桌</th>");
            }

            html.append("<th style='width: 15%;' class='right-align'>单价<br>(元)</th>");
            html.append("<th style='width: 15%;' class='right-align'>小计<br>(元)</th>");
            html.append("</tr></thead><tbody>");

            double[] totalAmount = {0.0};

            tempOrder.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String itemKey = entry.getKey();
                        int qty = entry.getValue();

                        // 🔧【核心】解析特殊 Key 格式：A1[BATCH:13,14,15]
                        String displayItemId = itemKey;
                        String assignedTables = "";
                        boolean isBatchOrder = false;

                        if (itemKey.contains("[BATCH:")) {
                            isBatchOrder = true;
                            int batchStart = itemKey.indexOf("[BATCH:");
                            displayItemId = itemKey.substring(0, batchStart);
                            assignedTables = itemKey.substring(batchStart + 7, itemKey.length() - 1);
                        }

                        // 查询菜品信息
                        String itemName = "（未知）";
                        double price = 0.0;
                        com.restaurant.entity.MenuItem item = frame.getMenuItemById(displayItemId);
                        if (item != null) {
                            itemName = item.getName();
                            price = item.getPrice();
                        }

                        double subtotal = price * qty;
                        totalAmount[0] += subtotal;

                        html.append("<tr>");
                        html.append("<td>").append(displayItemId).append("</td>");
                        html.append("<td>").append(itemName).append("</td>");

                        // 🔧 数量列：显示"一键点餐"标签
                        html.append("<td class='center-align'>").append(qty);
                        if (isBatchOrder) {
                            html.append("<span class='batch-tag'> (一键点餐)</span>");
                        }
                        html.append("</td>");

                        // 🔧 分配餐桌列（仅聚餐桌 + 堂食显示）
                        if (showAssignedTable) {
                            html.append("<td>");
                            if (isBatchOrder && !assignedTables.isEmpty()) {
                                // 🔧【核心逻辑】处理桌号显示：超过3个只显示 前2个 + ... + 最后1个
                                String[] tableIds = assignedTables.split(",");
                                String displayTableText = assignedTables; // 默认显示全部

                                if (tableIds.length > 3) {
                                    // 格式：13,14,...,18
                                    displayTableText = tableIds[0] + "," + tableIds[1] + ",...," + tableIds[tableIds.length - 1];
                                }

                                html.append("<span class='table-ids'>").append(displayTableText).append("</span>");
                            } else {
                                // 普通点餐：显示当前餐桌号
                                html.append("<span style='color: #999;'>").append(currentTableNumber).append("</span>");
                            }
                            html.append("</td>");
                        }

                        html.append("<td class='right-align'>").append(String.format("%.2f", price)).append("</td>");
                        html.append("<td class='right-align price'>").append(String.format("%.2f", subtotal)).append("</td>");
                        html.append("</tr>");
                    });

            html.append("</tbody></table>");

            // 显示总金额
            html.append("<div class='total-box'>");
            html.append("<span style='font-size: 16px; font-weight: bold;'>订单总金额：</span>");
            html.append("<span style='font-size: 20px; color: #c62828; font-weight: bold;'>")
                    .append(String.format("%.2f", totalAmount[0])).append(" 元</span>");
            html.append("</div>");
        }

        html.append("</body></html>");

        // 确保在 EDT 线程更新
        SwingUtilities.invokeLater(() -> {
            tempOrderEditor.setText(html.toString());
            tempOrderEditor.setCaretPosition(0); // 滚动到顶部

            // 🔧 强制刷新 JScrollPane 以显示横向滚动条
            tempScrollPane.revalidate();
            tempScrollPane.repaint();
        });
    }

    /**
     * 🔧 辅助方法：判断是否为聚餐桌
     */
    private boolean isGroupedTable(String tableDisplayId) {
        if (tableDisplayId == null || service == null) return false;
        com.restaurant.entity.Tables table = service.getTableById(tableDisplayId);
        return table != null && table.getTableType() == com.restaurant.entity.Tables.TableType.GROUPED;
    }


    public void refreshFormalOrderDisplay() {
        // ===== 基础验证 =====
        if (currentTableNumber == null ||
                "未选择".equals(currentTableNumber) ||
                "待下单".equals(currentTableNumber)) {
            orderedItemsEditor.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                            "<p>請選擇餐桌或確認訂單號</p></body></html>"
            );
            totalPriceLabel.setText("总价格：0.00 元");
            statusLabel.setText("订单情况：");
            // 🔧 清空并隐藏标签
            if (itemsTotalLabel != null) {
                itemsTotalLabel.setText("菜品：0.00 元");
                itemsTotalLabel.setVisible(false);
            }
            if (deliveryFeeLabel != null) {
                deliveryFeeLabel.setText("配送费：0.00 元");
                deliveryFeeLabel.setVisible(false);
            }
            if (groupTableLabel != null) {
                groupTableLabel.setVisible(false);  // ← 只需要这一行！
            }

            return;
        }

        List<OrderItem> items;

        // ===== 根据订单类型加载数据 =====
        if (currentOrderType == OrderType.DINE_IN) {
            // 堂食模式：通过餐桌号查询
            items = frame.loadFormalOrderItems(currentTableNumber);
            statusLabel.setText(controller.getOrderStatusDisplay(currentTableNumber));
            // 🔧 堂食模式：隐藏菜品/配送费标签（总价已包含在总金额中）
            if (itemsTotalLabel != null) itemsTotalLabel.setVisible(false);
            if (deliveryFeeLabel != null) deliveryFeeLabel.setVisible(false);
        } else if (currentOrderType == OrderType.RESERVATION) {
            System.out.println("🔍 加载预约订单: " + currentTableNumber);
            items = frame.loadFormalOrderItemsByReservationId(currentTableNumber);

            // 🔧【修复】根据菜品实际状态动态显示
            String orderStatusText = calculateReservationOrderStatus(items);
            statusLabel.setText("订单情况：" + orderStatusText);

            // 🔧 预约订单显示菜品总价
            if (itemsTotalLabel != null) {
                itemsTotalLabel.setVisible(true);
            }
            if (deliveryFeeLabel != null) {
                deliveryFeeLabel.setVisible(false);  // 预约订单无配送费
            }
        } else {
            // 🔧 外卖/配送模式：通过订单号查询
            System.out.println("🔍 加载外卖订单: " + currentTableNumber);
            items = frame.loadFormalOrderItemsByOrderNumber(currentTableNumber);
            // 显示外卖订单状态
            String orderStatusText = frame.getOrderStatusDisplayByOrderNumber(currentTableNumber);
            statusLabel.setText(orderStatusText);
            // 🔧【关键修复】自取+配送都显示菜品总价，只有配送显示配送费
            boolean isTakeout = (currentOrderType == OrderType.PICKUP || currentOrderType == OrderType.DELIVERY);
            if (itemsTotalLabel != null) {
                itemsTotalLabel.setVisible(isTakeout);
            }
            if (deliveryFeeLabel != null) {
                deliveryFeeLabel.setVisible(currentOrderType == OrderType.DELIVERY);  // 仅配送显示
            }
        }

        // ===== 空数据处理 =====
        if (items == null || items.isEmpty()) {
            // 🔧 更友好的空状态提示
            String emptyMsg;
            if (currentOrderType == OrderType.DINE_IN) {
                emptyMsg = "📭 该餐桌暂无订单明细";
            } else if (currentOrderType == OrderType.RESERVATION) {
                emptyMsg = "📦 预约订单 " + currentTableNumber + " 暂无菜品（可能刚确认，请稍等）";
            } else {
                emptyMsg = "📦 订单 " + currentTableNumber + " 暂无菜品（可能刚确认，请稍等）";
            }

            orderedItemsEditor.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                            "<p>" + emptyMsg + "</p></body></html>"
            );
            totalPriceLabel.setText("总价格：0.00 元");
            // 🔧 清空标签
            if (itemsTotalLabel != null) itemsTotalLabel.setText("菜品：0.00 元");
            if (deliveryFeeLabel != null) deliveryFeeLabel.setText("配送费：0.00 元");
            System.out.println(" 订单明细为空: " + currentTableNumber);
            return;
        }

        // ===== 生成并显示订单详情 =====
        String htmlContent;

        if (currentOrderType == OrderType.DINE_IN) {
            // 🔧【核心修改】堂食：判断是否为聚餐桌 + 传入当前餐桌号
            boolean isGrouped = isGroupedTable(currentTableNumber);
            htmlContent = frame.generateFormalOrderHtml(
                    currentTableNumber,
                    true,// 是否顯示總額
                    isGrouped,           // 🔧 是否聚餐桌
                    currentTableNumber   // 🔧 当前餐桌显示ID（用于显示分配餐桌）
            );
        }
        // 🔧【新增】预约订单：使用专用方法（只显示准备状态）
        else if (currentOrderType == OrderType.RESERVATION) {
            htmlContent = generateReservationOrderHtml(items, false);
        } else {
            // 🔧 外卖/自取：使用已查询的 items 数据，自己生成 HTML
            htmlContent = generateTakeoutOrderHtml(items, false);
        }

        // 🔧 关键：确保在 EDT 上更新 UI
        SwingUtilities.invokeLater(() -> {
            orderedItemsEditor.setText(htmlContent);
            orderedItemsEditor.setCaretPosition(0);  // 滚动到顶部
        });

        // 🔧 计算菜品总金额（不含配送费）
        double itemsTotal = items.stream()
                .mapToDouble(i -> i.getQuantity() * i.getPriceAtOrder())
                .sum();

        // 🔧 获取配送费（仅配送模式）
        double deliveryFee = 0.0;
        if (currentOrderType == OrderType.DELIVERY) {
            Double fee = frame.getDeliveryFeeByOrderNumber(currentTableNumber);
            if (fee != null) {
                deliveryFee = fee;
            }
        }

        // 🔧 计算最终总金额
        double total = itemsTotal + deliveryFee;

        // 🔧 更新标签显示
        if (itemsTotalLabel != null) {
            itemsTotalLabel.setText(String.format("菜品：%.2f 元", itemsTotal));
        }
        if (deliveryFeeLabel != null && currentOrderType == OrderType.DELIVERY) {
            deliveryFeeLabel.setText(String.format("配送费：%.2f 元", deliveryFee));
        }
        totalPriceLabel.setText(String.format("总价格：%.2f 元", total));

        // 🔧【新增】检查并更新组合桌标签（堂食或预约订单 + 聚餐桌）
        if ((currentOrderType == OrderType.DINE_IN || currentOrderType == OrderType.RESERVATION)) {
            // 检查餐桌类型是否为 GROUPED（聚餐桌）
            Tables table = service.getTableById(currentTableNumber);
            if (table != null && table.getTableType() == Tables.TableType.GROUPED) {
                updateGroupTableDisplay(currentTableNumber);
            } else {
                // 不是聚餐桌，隐藏标签
                if (groupTableLabel != null) {
                    groupTableLabel.setVisible(false);
                }
            }
        } else {
            // 外卖订单，隐藏组合桌标签
            if (groupTableLabel != null) {
                groupTableLabel.setVisible(false);
            }
        }

        System.out.println(" 订单刷新成功: " + currentTableNumber +
                " | 菜品: " + itemsTotal + " | 配送费: " + deliveryFee + " | 总计: " + total);
    }


    /**
     * 🔧 计算预约订单的实际状态
     */
    private String calculateReservationOrderStatus(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "暂无菜品";
        }

        int totalCount = items.size();
        int preparedCount = 0;
        int preparingCount = 0;
        int unservedCount = 0;

        for (OrderItem item : items) {
            String status = item.getStatus();
            if ("PREPARED".equals(status)) {
                preparedCount++;
            } else if ("PREPARING".equals(status)) {
                preparingCount++;
            } else if ("UNSERVED".equals(status)) {
                unservedCount++;
            }
        }

        // 全部已准备
        if (preparedCount == totalCount) {
            return "预点餐已全部准备";
        }
        // 部分准备中
        else if (preparingCount > 0 || preparedCount > 0) {
            return "预点餐准备中 (" + preparedCount + "/" + totalCount + ")";
        }
        // 全部未准备
        else {
            return "预点餐未准备";
        }
    }

    /**
     * 🔧 新增：生成预约订单HTML（只显示准备状态，不显示上桌状态）
     * 预约订单状态：UNSERVED(未准备) / PREPARING(准备中) / PREPARED(已准备)
     */
    private String generateReservationOrderHtml(List<OrderItem> items, boolean includeTotal) {
        if (items == null || items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:10px; color:#999; text-align:center;'>" +
                    "<p>📭 暂无预点餐菜品</p></body></html>";
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
                .append("<th style='width:100px; text-align:center;'>准备状态</th>")  // 🔧 改为"准备状态"
                .append("<th style='width:80px; text-align:left;'>编号</th>")
                .append("<th style='width:200px; text-align:left;'>菜品</th>")
                .append("<th style='width:90px; text-align:center;'>数量/总数量</th>")  // 🔧 预约订单只显示数量，不显示进度
                .append("<th style='width:90px; text-align:right;'>单价</th>")
                .append("<th style='width:100px; text-align:right;'>小计</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定显示顺序：UNSERVED → PREPARING → PREPARED
        for (String status : Arrays.asList("UNSERVED", "PREPARING", "PREPARED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            // 🔧 预约订单状态文本
            String statusText = switch (status) {
                case "UNSERVED" -> "⚪ 未准备";
                case "PREPARING" -> "🟡 准备中";
                case "PREPARED" -> "🟢 已准备";
                default -> status;
            };

            String statusColor = switch (status) {
                case "UNSERVED" -> "#9e9e9e";
                case "PREPARING" -> "#ffa500";
                case "PREPARED" -> "#4caf50";
                default -> "#2196f3";
            };

            for (OrderItem item : group) {
                double subtotal = item.getQuantity() * item.getPriceAtOrder();
                totalAmount += subtotal;

                html.append("<tr>");
                // 序号
                html.append(String.format("<td style='text-align:center; font-family:monospace;'>%d</td>", itemNumber++));
                // 状态
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center;'>%s</td>",
                        statusColor, statusText
                ));
                // 编号 / 菜名
                html.append(String.format(
                        "<td style='white-space:nowrap;'>%s</td>" + "<td style='white-space:nowrap;'>%s</td>",
                        item.getItemCode(), item.getItemName()
                ));
                // 数量列：显示准备进度（已准备/总数）
                String quantityProgress = String.format("%d/%d",
                        item.getServedQuantity(),  // 已准备数量
                        item.getQuantity()          // 总数量
                );
                // 部分准备时高亮背景（浅橙色）+ 加粗
                String quantityStyle = "PREPARING".equals(item.getStatus())
                        ? "background-color:#fff3e0; font-weight:bold;"
                        : "";
                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; %s'>%s</td>",
                        quantityStyle, quantityProgress
                ));
                // 单价 / 小计
                html.append(String.format(
                        "<td style='text-align:right; font-family:monospace;'>%.2f</td>" +
                                "<td style='text-align:right; font-weight:bold; color:#d32f2f; font-family:monospace;'>%.2f</td>",
                        item.getPriceAtOrder(), subtotal
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


    private String generateTakeoutOrderHtml(List<OrderItem> items, boolean includeTotal) {
        if (items == null || items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:8px; color:#999; text-align:center;'>" +
                    "<p style='font-size:13px;'>📭 暂无订单</p></body></html>";
        }

        // 按状态分组
        Map<String, List<OrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getStatus));

        StringBuilder html = new StringBuilder();
        // 🔧 字體調大到 12px，閱讀更舒適
        html.append("<html><body style='font-family: Microsoft YaHei; padding:8px; font-size: 12px; color:#333;'>");

        // 總寬微調至 680px（適應更大字體 + 美化空間）
        html.append("<table border='1' cellpadding='5' cellspacing='0' ")
                .append("style='width: 680px; border-collapse: collapse; ")
                .append("white-space: nowrap; table-layout: auto; font-size: 12px;'>");

        // 🔧 表頭 - 漸變背景 + 加粗 + 顏色
        html.append("<tr style='background: linear-gradient(to bottom, #f8f9fa, #e9ecef); font-size: 12px;'>")
                .append("<th style='width: 45px; text-align:center; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>序号</th>")
                .append("<th style='width: 90px; text-align:center; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>制作状态</th>")
                .append("<th style='width: 70px; text-align:left; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>编号</th>")
                .append("<th style='width: 140px; text-align:left; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>菜品</th>")
                .append("<th style='width: 100px; text-align:center; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>数量</th>")
                .append("<th style='width: 90px; text-align:right; padding: 6px; color:#495057; border-bottom: 2px solid #dee2e6;'>单价</th>")
                .append("<th style='width: 105px; text-align:right; padding: 6px; color:#c62828; border-bottom: 2px solid #dee2e6;'>💰 小计</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定显示顺序
        for (String status : Arrays.asList("UNSERVED", "PARTIALLY_SERVED", "SERVED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            // 🔧 状态文本 + 顏色
            String statusText = switch (status) {
                case "UNSERVED" -> "🔴 制作中";
                case "PARTIALLY_SERVED" -> "🟠 部分完成";
                case "SERVED" -> "🟢 已完成";
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

                // 🔧 数量进度百分比（用于美化）
                int served = item.getServedQuantity();
                int total = item.getQuantity();
                double progress = total > 0 ? (double) served / total : 0;
                String progressColor = progress == 1.0 ? "#4caf50" : progress > 0 ? "#ffa500" : "#ff6b6b";

                html.append("<tr style='border-bottom: 1px solid #f1f1f1; background-color: #fff;'>");

                // 🔹 序号 - 灰色圓角背景
                html.append(String.format(
                        "<td style='text-align:center; padding: 6px; font-family:monospace; font-size: 12px; " +
                                "background-color: #f8f9fa; border-radius: 3px;'>%d</td>",
                        itemNumber++));

                // 🔹 状态 - 彩色背景 + 白字 + 圓角
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center; " +
                                "padding: 6px; font-size: 12px; border-radius: 4px;'>%s</td>",
                        statusColor, statusText));

                // 🔹 编号 - 等寬字體 + 淺灰背景
                html.append(String.format(
                        "<td style='white-space:nowrap; padding: 6px; font-size: 12px; font-family:monospace; " +
                                "background-color: #fafafa; color:#6c757d;'>%s</td>",
                        item.getItemCode()));

                // 🔹 菜名 - 主內容區
                html.append(String.format(
                        "<td style='white-space:nowrap; padding: 6px; font-size: 12px; font-weight:500;'>%s</td>",
                        item.getItemName()));

                // 🔹🔥 数量列 - 美化：進度條樣式 + 顏色編碼
                String quantityText = String.format("%d/%d", served, total);
                html.append(String.format(
                        "<td style='text-align:center; padding: 6px; font-family:monospace; font-size: 12px; " +
                                "font-weight:bold; color:%s; background: linear-gradient(to right, %s15, transparent); " +
                                "border-left: 3px solid %s;'>%s</td>",
                        progressColor, progressColor, progressColor, quantityText));

                // 🔹 单价 - 右對齊 + 灰色
                html.append(String.format(
                        "<td style='text-align:right; padding: 6px; font-family:monospace; font-size: 12px; color:#6c757d;'>¥%.0f</td>",
                        item.getPriceAtOrder()));

                // 🔹🔥 小计列 - 重點美化：紅色加粗 + 淺紅背景 + 貨幣符號
                html.append(String.format(
                        "<td style='text-align:right; padding: 6px; font-family:monospace; font-size: 13px; " +
                                "font-weight:bold; color:#c62828; background-color: #ffebee; border-left: 2px solid #ffcdd2;'>¥%.0f</td>",
                        subtotal));

                html.append("</tr>");
            }
        }

        html.append("</table>");

        // 🔹🔥 总计 - 卡片式美化
        if (includeTotal && totalAmount > 0) {
            html.append(String.format(
                    "<div style='margin-top:12px; padding:10px 15px; " +
                            "background: linear-gradient(to right, #e8f5e9, #c8e6c9); " +
                            "border-left: 4px solid #4caf50; border-radius: 6px; " +
                            "text-align:right; font-size:14px; font-weight:bold; font-family:monospace; " +
                            "box-shadow: 0 2px 4px rgba(0,0,0,0.05);'>" +
                            "🧾 订单总计：<span style='color:#c62828; font-size:16px;'>¥%.0f</span></div>",
                    totalAmount));
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * 刷新所有订单类型显示
     */
    public void refreshOrderTypeDisplays() {
        refreshDineInOrders();
        refreshTakeoutOrders();      // 🔧 替換原本的兩個方法
        refreshReservationOrders();  // 🔧 新增（暫時佔位符）
    }


    /**
     * 刷新堂食订单显示
     */
    private void refreshDineInOrders() {
        List<Map<String, Object>> orders = frame.loadDineInOrders();
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding: 5px; font-size: 11px;'>");

        if (orders == null || orders.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>暂无堂食订单</p>");
        } else {
            // 🔧 按餐桌状态分组：RESERVED(预定中) / OCCUPIED(占用中)
            Map<String, List<Map<String, Object>>> grouped = orders.stream()
                    .collect(Collectors.groupingBy(o ->
                            (String) o.getOrDefault("table_status", "UNKNOWN")
                    ));

            // ── 第一组：预定中的餐桌 ──
            List<Map<String, Object>> reservedOrders = grouped.getOrDefault("RESERVED", Collections.emptyList());
            if (!reservedOrders.isEmpty()) {
                html.append("<div style='background-color: #e8eaf6; padding: 6px; margin: 8px 0; border-radius: 4px; border-left: 4px solid #3f51b5;'>");
                html.append("<b style='color: #303f9f; font-size: 12px;'>📅 预定中的餐桌 (").append(reservedOrders.size()).append(")</b></div>");

                html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                        "style='width: 100%; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");
                html.append("<tr style='background-color: #c5cae9;'>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>订单</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>餐桌</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>状态</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>时间</th>");
                html.append("</tr>");

                for (Map<String, Object> order : reservedOrders) {
                    String status = (String) order.get("order_status");
                    html.append("<tr>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("order_id")).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("table_display")).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap; color: #303f9f; font-weight: bold;'>")
                            .append(status).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("order_time")).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
            }

            // ── 第二组：占用中的餐桌 ──
            List<Map<String, Object>> occupiedOrders = grouped.getOrDefault("OCCUPIED", Collections.emptyList());
            if (!occupiedOrders.isEmpty()) {
                html.append("<div style='background-color: #e8f5e9; padding: 6px; margin: 8px 0; border-radius: 4px; border-left: 4px solid #4caf50;'>");
                html.append("<b style='color: #2e7d32; font-size: 12px;'>🟢 占用中的餐桌 (").append(occupiedOrders.size()).append(")</b></div>");

                html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                        "style='width: 100%; border-collapse: collapse; font-size: 11px;'>");
                html.append("<tr style='background-color: #c8e6c9;'>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>订单</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>餐桌</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>状态</th>");
                html.append("<th style='padding: 4px; white-space: nowrap;'>时间</th>");
                html.append("</tr>");

                for (Map<String, Object> order : occupiedOrders) {
                    String status = (String) order.get("order_status");
                    String statusColor = getStatusColor(status);
                    html.append("<tr>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("order_id")).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("table_display")).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap; color: ").append(statusColor).append("; font-weight: bold;'>")
                            .append(status).append("</td>");
                    html.append("<td style='padding: 4px; white-space: nowrap;'>").append(order.get("order_time")).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
            }

            // 🔧 如果两组都为空（理论上不会发生）
            if (reservedOrders.isEmpty() && occupiedOrders.isEmpty()) {
                html.append("<p style='color: #999; text-align: center;'>暂无堂食订单</p>");
            }
        }
        html.append("</body></html>");
        dineInOrderEditor.setText(html.toString());
    }


    /**
     * 🔧 刷新外賣訂單顯示（合併自取 + 配送）
     * ⚠️ 自取訂單表格部分完全按照原 refreshPickupOrders() 代碼，一點不變
     */
    private void refreshTakeoutOrders() {
        List<Map<String, Object>> pickupOrders = frame.loadPickupOrders();
        List<Map<String, Object>> deliveryOrders = frame.loadDeliveryOrders();

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding: 5px; font-size: 11px;'>");

        // ═══════════════════════════════════════════════════════════
        // 【區域 1】自取訂單（標題用新格式，表格完全按照原 refreshPickupOrders）
        // ═══════════════════════════════════════════════════════════
        // 🔧 標題欄位：使用和配送訂單一樣的格式
        html.append("<div style='background-color: #e8f5e9; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #4caf50; width: 300px; box-sizing: border-box;'>");
        html.append("<b style='color: #2e7d32; font-size: 12px;'>🟢 自取訂單 (").append(pickupOrders.size()).append(")</b></div>");

        if (pickupOrders == null || pickupOrders.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>暂无自取订单</p>");
        } else {
            // 🔑 關鍵：表格部分完全按照原 refreshPickupOrders() 代碼，一點不變
            html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                    "style='width: 300px; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");
            html.append("<tr style='background-color: #ccffcc;'>");
            html.append("<th style='padding: 4px; white-space: nowrap;'>订单顺序</th>");
            html.append("<th style='padding: 4px; white-space: nowrap;'>订单号</th>");
            html.append("<th style='padding: 4px; white-space: nowrap;'>状态</th>");
            html.append("<th style='padding: 4px; white-space: nowrap;'>时间</th>");
            html.append("</tr>");

            for (Map<String, Object> order : pickupOrders) {
                String status = (String) order.get("order_status");
                String statusColor = getStatusColor(status);
                //  获取订单号（可能为 null，做安全处理）
                String orderNumber = order.get("order_number") != null ?
                        order.get("order_number").toString() : "-";

                html.append("<tr>");
                html.append("<td style='padding: 4px; white-space: nowrap;'>")
                        .append(order.get("order_id")).append("</td>");
                //  新增：显示订单号列
                html.append("<td style='padding: 4px; white-space: nowrap; font-family: monospace;'>")
                        .append(orderNumber).append("</td>");
                html.append("<td style='padding: 4px; white-space: nowrap; color: ")
                        .append(statusColor).append("; font-weight: bold;'>")
                        .append(status).append("</td>");
                html.append("<td style='padding: 4px; white-space: nowrap;'>")
                        .append(order.get("order_time")).append("</td>");
                html.append("</tr>");
            }
            html.append("</table>");
        }

        // 分隔線
        html.append("<hr style='border: 0; border-top: 1px dashed #ccc; margin: 15px 0; width: 550px;'>");

        // ═══════════════════════════════════════════════════════════
        // 【區域 2】配送訂單（保持原有 width: 350px 固定寬度樣式）
        // ═══════════════════════════════════════════════════════════
        // 🔧 標題欄位：原有格式
        html.append("<div style='background-color: #e3f2fd; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #2196f3;width:350px; box-sizing: border-box;'>");
        html.append("<b style='color: #1565c0; font-size: 12px;'>🚚 配送訂單 (").append(deliveryOrders.size()).append(")</b></div>");

        if (deliveryOrders == null || deliveryOrders.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>📭 暫無配送訂單</p>");
        } else {
            // 🔑 關鍵：保持 width: 350px 固定寬度 + 像素列寬
            html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                    "style='width: 350px; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");

            html.append("<tr style='background-color: #ccccff;'>");
            html.append("<th style='padding: 4px; width: 60px; white-space: nowrap;'>順序</th>");
            html.append("<th style='padding: 4px; width: 150px; white-space: nowrap;'>訂單號</th>");
            html.append("<th style='padding: 4px; width: 120px; white-space: nowrap;'>🍳 製作</th>");
            html.append("<th style='padding: 4px; width: 120px; white-space: nowrap;'>🚚 配送</th>");
            html.append("<th style='padding: 4px; width: 200px; white-space: nowrap;'>時間</th>");
            html.append("</tr>");

            for (Map<String, Object> order : deliveryOrders) {
                String orderStatus = (String) order.get("order_status");
                String deliveryStatus = (String) order.get("delivery_status");
                String orderNumber = order.get("order_number") != null ?
                        order.get("order_number").toString() : "-";

                html.append("<tr style='border-bottom: 1px solid #eee;'>");
                html.append("<td style='padding: 4px; text-align: center; white-space: nowrap;'>")
                        .append(order.get("order_id")).append("</td>");
                html.append("<td style='padding: 4px; font-family: monospace; font-weight: bold; white-space: nowrap;'>")
                        .append(orderNumber).append("</td>");
                html.append("<td style='padding: 4px; color: ")
                        .append(getOrderStatusColor(orderStatus))
                        .append("; font-weight: bold; text-align: center; white-space: nowrap;'>")
                        .append(orderStatus).append("</td>");
                html.append("<td style='padding: 4px; color: ")
                        .append(getDeliveryStatusColor(deliveryStatus))
                        .append("; font-weight: bold; text-align: center; white-space: nowrap;'>")
                        .append(formatDeliveryStatus(deliveryStatus)).append("</td>");
                html.append("<td style='padding: 4px; text-align: center; white-space: nowrap;'>")
                        .append(order.get("order_time")).append("</td>");
                html.append("</tr>");
            }
            html.append("</table>");

            // 提示文字
            html.append("<div style='font-size: 10px; color: #666; padding: 4px; background: #f9f9f9; border-radius: 3px;'>");
            html.append("💡 提示：製作狀態由廚房控制，配送狀態由配送員更新");
            html.append("</div>");
        }

        html.append("</body></html>");
        takeoutOrderEditor.setText(html.toString());

        // 🔧 關鍵：強制刷新滾動策略，確保水平滾動條生效
        takeoutScrollPane.revalidate();
        takeoutScrollPane.repaint();
    }


    private String getStatusColor(String status) {
        if (status == null) return "#999";
        return switch (status) {
            case "制作中" -> "#ff6b6b";  // 红色
            case "制作完成" -> "#4caf50"; // 绿色
            default -> "#999";
        };
    }

    /**
     * 獲取製作狀態顏色（廚房進度）
     *
     * @param status 數據庫返回的狀態：制作中 / 制作完成 / 送單中
     */
    private String getOrderStatusColor(String status) {
        if (status == null) return "#999";
        return switch (status) {
            case "制作中" -> "#ff6b6b";        // 🔴 紅色：製作中
            case "制作完成" -> "#4caf50";      // 🟢 綠色：廚房已完成
            default -> "#999";
        };
    }


    /**
     * 獲取配送狀態顏色（配送進度）- 只處理 delivery_status 的值
     */
    private String getDeliveryStatusColor(String deliveryStatus) {
        if (deliveryStatus == null) return "#999";
        return switch (deliveryStatus) {
            case "NOT_DELIVERED" -> "#ff6b6b";   // 🔴 未配送
            case "DELIVERING" -> "#ffa500";      // 🟠 送單中
            case "DELIVERED" -> "#4caf50";       // 🟢 已送達
            default -> "#999";
        };
    }


    /**
     * 格式化配送狀態為中文顯示（帶表情符號）
     *
     * @param dbValue 數據庫 ENUM 值
     */
    private String formatDeliveryStatus(String dbValue) {
        if (dbValue == null) return "🔴 未配送";
        return switch (dbValue) {
            case "NOT_DELIVERED" -> "🔴 未配送";
            case "DELIVERING" -> "🟠 送單中";
            case "DELIVERED" -> "🟢 已送達";
            default -> dbValue;
        };
    }


    private void refreshReservationOrders() {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding: 0; margin: 0; font-size: 11px;'>");

        try {
            // 🔧 获取数量模式的预约记录
            List<Map<String, Object>> reservations = controller.getPreOrderReservationsForMonitor();

            if (reservations == null || reservations.isEmpty()) {
                // 🔧 标题栏 - 使用您指定的格式
                html.append("<div style='background-color: #e8f5e9; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #4caf50; width: 300px; box-sizing: border-box;'>");
                html.append("<b style='color: #2e7d32; font-size: 12px;'> 預約訂單 (0)</b></div>");
                html.append("<p style='color: #999; text-align: center; padding: 5px; margin: 0;'> 暂无数量模式预约</p>");
            } else {
                // 🔧 主标题栏 - 使用您指定的格式
                html.append("<div style='background-color: #e8f5e9; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #4caf50; width: 300px; box-sizing: border-box;'>");
                html.append("<b style='color: #2e7d32; font-size: 12px;'> 預約訂單 (").append(reservations.size()).append(")</b></div>");

                // 🔧 按预约时间状态分组
                Map<String, List<Map<String, Object>>> grouped = reservations.stream()
                        .collect(Collectors.groupingBy(o -> {
                            Object timeObj = o.get("reservation_time");
                            Boolean within15h = (Boolean) o.get("within_15h");
                            LocalDateTime resTime = null;
                            if (timeObj instanceof java.sql.Timestamp) {
                                resTime = ((java.sql.Timestamp) timeObj).toLocalDateTime();
                            } else if (timeObj instanceof LocalDateTime) {
                                resTime = (LocalDateTime) timeObj;
                            }
                            if (resTime == null) return "FUTURE";
                            LocalDateTime now = LocalDateTime.now();
                            if (resTime.isBefore(now)) {
                                return "EXPIRED";
                            } else if (within15h != null && within15h) {
                                return "URGENT";
                            } else {
                                return "FUTURE";
                            }
                        }));

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");

                // ── 第一组：🔴 已过期的预约 ──
                List<Map<String, Object>> expiredList = grouped.getOrDefault("EXPIRED", Collections.emptyList());
                if (!expiredList.isEmpty()) {
                    // 🔧 分组标题 - 统一使用您指定的格式（只改颜色和边框）
                    html.append("<div style='background-color: #ffebee; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #f44336; width: 300px; box-sizing: border-box;'>");
                    html.append("<b style='color: #c62828; font-size: 12px;'>🔴 已過期 (").append(expiredList.size()).append(")</b></div>");
                    html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                            "style='width: 300px; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");
                    html.append("<tr style='background-color: #ffcdd2;'>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>預約號</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>時間</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>客人</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>狀態</th>");
                    html.append("</tr>");
                    for (Map<String, Object> res : expiredList) {
                        String resId = (String) res.get("reservation_id");
                        String name = (String) res.get("customer_name");
                        String phone = (String) res.get("customer_phone");
                        Object timeObj = res.get("reservation_time");
                        String timeStr = "未知";
                        if (timeObj instanceof java.sql.Timestamp) {
                            timeStr = ((java.sql.Timestamp) timeObj).toLocalDateTime().format(formatter);
                        } else if (timeObj instanceof LocalDateTime) {
                            timeStr = ((LocalDateTime) timeObj).format(formatter);
                        }
                        html.append("<tr>");
                        html.append("<td style='padding: 4px; white-space: nowrap; font-family: monospace;'>").append(resId).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap; color: #c62828;'>").append(timeStr).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap;'>").append(name != null ? name : "-")
                                .append("<br><small style='color:#666;font-size:9px'>").append(phone).append("</small>").append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap; color: #c62828; font-weight: bold;'>已過期</td>");
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }

                // ── 第二组：🟡 1.5小时内的预约（紧急）─
                List<Map<String, Object>> urgentList = grouped.getOrDefault("URGENT", Collections.emptyList());
                if (!urgentList.isEmpty()) {
                    // 🔧 分组标题 - 统一使用您指定的格式
                    html.append("<div style='background-color: #fff8e1; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #ffa000; width: 300px; box-sizing: border-box;'>");
                    html.append("<b style='color: #ef6c00; font-size: 12px;'>🟡 1.5小時內 (").append(urgentList.size()).append(")</b></div>");
                    //這個格式如果還有文字隔行分開，就要改掉px的值
                    html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                            "style='width: 400px; border-collapse: collapse; font-size: 11px; margin-bottom: 10px;'>");
                    html.append("<tr style='background-color: #ffecb3;'>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>預約號</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>時間</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>客人</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>狀態</th>");
                    html.append("</tr>");
                    for (Map<String, Object> res : urgentList) {
                        String resId = (String) res.get("reservation_id");
                        String name = (String) res.get("customer_name");
                        String phone = (String) res.get("customer_phone");
                        Object timeObj = res.get("reservation_time");
                        String timeStr = "未知";
                        if (timeObj instanceof java.sql.Timestamp) {
                            timeStr = ((java.sql.Timestamp) timeObj).toLocalDateTime().format(formatter);
                        } else if (timeObj instanceof LocalDateTime) {
                            timeStr = ((LocalDateTime) timeObj).format(formatter);
                        }
                        html.append("<tr>");
                        html.append("<td style='padding: 4px; white-space: nowrap; font-family: monospace;'>").append(resId).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap; color: #ef6c00;'>").append(timeStr).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap;'>").append(name != null ? name : "-")
                                .append("<br><small style='color:#666;font-size:9px'>").append(phone).append("</small>").append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap; color: #ef6c00; font-weight: bold;'>即將到店</td>");
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }

                // ── 第三组：⚪ 未来时间的预约（普通）─
                List<Map<String, Object>> futureList = grouped.getOrDefault("FUTURE", Collections.emptyList());
                if (!futureList.isEmpty()) {
                    // 🔧 分组标题 - 统一使用您指定的格式
                    html.append("<div style='background-color: #e3f2fd; padding: 8px; margin-bottom: 10px; border-radius: 4px; border-left: 4px solid #2196f3; width: 300px; box-sizing: border-box;'>");
                    html.append("<b style='color: #1565c0; font-size: 12px;'>⚪ 未來預約 (").append(futureList.size()).append(")</b></div>");
                    html.append("<table border='1' cellpadding='3' cellspacing='0' " +
                            "style='width: 300px; border-collapse: collapse; font-size: 11px;'>");
                    html.append("<tr style='background-color: #bbdefb;'>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>預約號</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>時間</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>客人</th>");
                    html.append("<th style='padding: 4px; white-space: nowrap;'>狀態</th>");
                    html.append("</tr>");
                    for (Map<String, Object> res : futureList) {
                        String resId = (String) res.get("reservation_id");
                        String name = (String) res.get("customer_name");
                        String phone = (String) res.get("customer_phone");
                        Object timeObj = res.get("reservation_time");
                        String timeStr = "未知";
                        if (timeObj instanceof java.sql.Timestamp) {
                            timeStr = ((java.sql.Timestamp) timeObj).toLocalDateTime().format(formatter);
                        } else if (timeObj instanceof LocalDateTime) {
                            timeStr = ((LocalDateTime) timeObj).format(formatter);
                        }
                        html.append("<tr>");
                        html.append("<td style='padding: 4px; white-space: nowrap; font-family: monospace;'>").append(resId).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap;'>").append(timeStr).append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap;'>").append(name != null ? name : "-")
                                .append("<br><small style='color:#666;font-size:9px'>").append(phone).append("</small>").append("</td>");
                        html.append("<td style='padding: 4px; white-space: nowrap; color: #1565c0; font-weight: bold;'>待確認</td>");
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }

                // 🔧 如果三组都为空（理论上不会发生）
                if (expiredList.isEmpty() && urgentList.isEmpty() && futureList.isEmpty()) {
                    html.append("<p style='color: #999; text-align: center; padding: 5px; margin: 0;'>✨ 暂无数量模式预约</p>");
                }
            }
        } catch (Exception e) {
            html.append("<p style='color: red; text-align: center; padding: 5px; margin: 0;'>❌ 加载失败: ").append(e.getMessage()).append("</p>");
            e.printStackTrace();
        }

        html.append("</body></html>");

        SwingUtilities.invokeLater(() -> {
            reservationOrderEditor.setText(html.toString());
            reservationOrderEditor.setCaretPosition(0);
        });
    }


    /**
     * 统一处理确认按钮点击事件
     */
    private void handleConfirmTable(String inputStr) {
        if (inputStr.isEmpty()) {
            String fieldName = currentOrderType == OrderType.DINE_IN ? "餐桌号" :
                    (currentOrderType == OrderType.RESERVATION ? "预约号" : "订单号");
            showErrorDialog("请输入" + fieldName, "输入错误");
            return;
        }

        // 🔧【核心修复】根据订单类型分发处理逻辑
        if (currentOrderType == OrderType.DINE_IN) {
            // 堂食：验证餐桌
            handleDineInTableConfirm(inputStr);

        } else if (currentOrderType == OrderType.RESERVATION) {
            // 🔧 预约：只验证预约号是否存在（不调用外卖验证）
            if (!validateReservationExists(inputStr)) {
                return; // 验证失败，直接返回
            }
            // 验证通过后，执行后续逻辑（设置全局状态等）
            handleReservationConfirmed(inputStr);

        } else {
            // 外卖/配送：验证订单号
            handleTakeoutOrderConfirm(inputStr);
        }
        // 🔧【新增】确认成功后，检查并更新组合桌显示
        updateGroupTableDisplay(inputStr);
    }

    /**
     * 【新增】更新组合桌标签显示
     *
     * @param tableDisplayId 餐桌显示编号
     */
    private void updateGroupTableDisplay(String tableDisplayId) {
        // 1. 获取餐桌对象
        Tables table = service.getTableById(tableDisplayId);
        if (table == null) {
            // 隐藏标签
            groupTableLabel.setVisible(false);
            return;
        }

        // 2. 检查是否为聚餐桌（GROUPED 类型）
        if (table.getTableType() == Tables.TableType.GROUPED &&
                table.getGroupWith() != null &&
                !table.getGroupWith().isEmpty()) {

            // 3. 解析 group_with 字段（格式："7,8,9" 或 "10,11,12,13"）
            String[] tableIds = table.getGroupWith().split(",");
            String displayText;

            if (tableIds.length == 3) {
                // 正好3张：全部显示
                displayText = String.join(",", tableIds);
            } else if (tableIds.length > 3) {
                // 超过3张：显示前两个 + "..." + 最后一个
                displayText = tableIds[0] + "," + tableIds[1] + ",...," + tableIds[tableIds.length - 1];
            } else {
                // 少于3张（理论上不会发生）：直接显示
                displayText = table.getGroupWith();
            }

            // 4. 更新标签文本并显示
            groupTableLabel.setText("组合桌: " + displayText);
            groupTableLabel.setVisible(true);

        } else {
            // 不是聚餐桌：隐藏标签
            groupTableLabel.setVisible(false);
        }
    }

    /**
     * 🔧 新增：处理预约号验证通过后的逻辑
     * （复用之前外卖确认成功的逻辑，但去掉格式校验）
     */
    private void handleReservationConfirmed(String reservationId) {
        try {
            // 1. 设置全局状态
            currentTableNumber = reservationId;
            frame.setCurrentTableNumber(reservationId);

            // 2. 更新顶部标签
            tableNumberLabel.setText("预约号：" + reservationId);

            // 3. 成功反馈
            JOptionPane.showMessageDialog(frame,
                    " 预约号确认成功！\n预约号：" + reservationId,
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);

            // 4. 刷新显示
            refreshFormalOrderDisplay();
            refreshTemporaryOrderDisplay();

        } catch (Exception e) {
            showErrorDialog("系统错误: " + e.getMessage(), "错误");
            e.printStackTrace();
        }
    }

    /**
     * 🔧 验证预约号是否存在（仅检查存在性）
     */
    private boolean validateReservationExists(String reservationId) {
        try {
            // 通过 Controller 查询预约详情
            TableReservation reservation = controller.getReservationDetail(reservationId);

            if (reservation == null) {
                showErrorDialog(
                        "预约号不存在！\n" +
                                "预约号：" + reservationId + "\n\n" +
                                "可能原因：\n" +
                                "• 预约号输入错误（请检查）\n" +
                                "• 该预约记录已被删除",
                        "预约验证失败"
                );
                return false;
            }

            //  验证通过
            System.out.println(" 预约号验证成功: " + reservationId);
            return true;

        } catch (Exception e) {
            showErrorDialog("系统错误: " + e.getMessage(), "错误");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 处理外卖订单号确认（修复版 - 设置 currentTakeoutOrderNumber）
     */
    private void handleTakeoutOrderConfirm(String orderNumberStr) {
        try {
            // ===== 1. 验证订单号格式 =====
            if (!orderNumberStr.matches("(\\d+)|(P|D)-\\d{8}-\\d+")) {
                showErrorDialog("订单号格式无效（应为数字或 P/D-日期-序号）", "输入错误");
                return;
            }

            // ===== 2. 验证订单号前缀与订单类型是否匹配 =====
            if (orderNumberStr.matches("(P|D)-\\d{8}-\\d+")) {
                String prefix = orderNumberStr.substring(0, 1);
                if (currentOrderType == OrderType.PICKUP && !prefix.equals("P")) {
                    showErrorDialog(
                            "订单号前缀错误！\n当前为【自取】模式，订单号应以 'P-' 开头",
                            "输入错误");
                    return;
                }
                if (currentOrderType == OrderType.DELIVERY && !prefix.equals("D")) {
                    showErrorDialog(
                            "订单号前缀错误！\n当前为【配送】模式，订单号应以 'D-' 开头",
                            "输入错误");
                    return;
                }
            }

            // 🔧【关键新增】3. 验证订单是否存在（仅手动输入时需要）
            // 注意：自动生号生成的订单号可能还未创建，所以跳过验证
            boolean isAutoGenerated = generateOrderNumberCheck != null && generateOrderNumberCheck.isSelected();
            if (!isAutoGenerated) {
                // 调用 Service 查询活跃订单
                com.restaurant.entity.Order order =
                        frame.findActiveOrderByOrderNumber(orderNumberStr);

                if (order == null) {
                    showErrorDialog(
                            "订单不存在！\n" +
                                    "订单号：" + orderNumberStr + "\n\n" +
                                    "可能原因：\n" +
                                    "• 订单号输入错误（请检查日期和序号）\n" +
                                    "• 该订单已结账或被删除\n" +
                                    "• 请勾选【自动生号】创建新订单",
                            "订单验证失败");
                    return;  // 🔑 订单不存在，直接返回，不执行后续逻辑
                }

                // 🔧 可选：额外验证订单状态（确保是活跃订单）
                if (!"ORDERED".equals(order.getStatus())) {
                    showErrorDialog(
                            "订单状态异常！\n" +
                                    "当前状态：" + order.getStatus() + "\n" +
                                    "只有【已下单】状态的订单才能操作",
                            "订单状态错误");
                    return;
                }
            }

            // ===== 4. 验证通过，设置全局状态 =====
            currentTableNumber = orderNumberStr;
            currentTakeoutOrderNumber = orderNumberStr;
            frame.setCurrentTableNumber(orderNumberStr);

            // 🔧 建议：手动输入时保留订单号在输入框，方便用户核对
            if (!isAutoGenerated) {
                tableNumberField.setText(orderNumberStr);
            } else {
                tableNumberField.setText("");  // 自动生号可清空
            }

            // ===== 5. 成功反馈 + 刷新显示 =====
            String orderTypeText = currentOrderType == OrderType.PICKUP ? "自取" : "配送";
            JOptionPane.showMessageDialog(frame,
                    "已确认" + orderTypeText + "订单 #" + orderNumberStr,
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);

            refreshFormalOrderDisplay();
            refreshTemporaryOrderDisplay();

        } catch (Exception e) {
            showErrorDialog("系统错误: " + e.getMessage(), "错误");
            e.printStackTrace();
        }
    }


    private void handleDineInTableConfirm(String tableNumStr) {
        if (currentOrderType != OrderType.DINE_IN) {
            // 外卖/配送模式：提示用户无需确认餐桌
            JOptionPane.showMessageDialog(
                    this,
                    "【" + currentOrderType.getDisplayName() + "】模式无需确认餐桌。\n" +
                            "请直接到菜单点餐，然后点击【确认下单】生成订单。",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // 堂食模式：原有逻辑
        if (!tableNumStr.isEmpty()) {
            try {
                int tableId;
                String suffix = "";
                if (tableNumStr.matches("\\d+[a-zA-Z]")) {
                    tableId = Integer.parseInt(tableNumStr.replaceAll("[^0-9]", ""));
                    suffix = tableNumStr.replaceAll("[^a-zA-Z]", "");
                } else if (tableNumStr.matches("\\d+")) {
                    tableId = Integer.parseInt(tableNumStr);
                } else {
                    showErrorDialog("餐桌编号格式无效（例如7或7a）", "输入错误");
                    return;
                }

                Tables targetTable = service.getTableById(tableNumStr);
                if (targetTable == null) {
                    showErrorDialog("未找到餐桌 #" + tableNumStr, "错误");
                    return;
                }

                String displayId = targetTable.getDisplayId();
                Tables.TableStatus status = targetTable.getStatus();

                // ═══════════════════════════════════════════════════════════
                // 🔧【核心修改】允许 OCCUPIED 或 (RESERVED + pre_order=true) 点餐
                // ═══════════════════════════════════════════════════════════

                // 1️ 先处理 RESERVED 状态：必须 pre_order=true 才能点餐
                if (status == Tables.TableStatus.RESERVED) {
                    // 通过 reserved_table_ids 查询包含该餐桌的预定记录
                    TableReservation reservation = controller.findReservationByTableId(displayId);

                    if (reservation != null && reservation.getPreOrder()) {
                        //  pre_order=true，允许继续（不 return）
                        System.out.println(" 餐桌 " + displayId + " 已预点餐 (pre_order=1)，允许点餐");
                    } else {
                        //  没找到预定记录 或 pre_order=false，阻止点餐
                        String reason = (reservation == null) ? "未找到预定记录" : "未预点餐 (pre_order=0)";
                        JOptionPane.showMessageDialog(frame,
                                "餐桌 " + displayId + " 的预定【" + reason + "】！\n\n" +
                                        (reservation != null ? "预定号：" + reservation.getReservationId() + "\n" : "") +
                                        " 此餐桌不能确认，因为客人没有预点餐。",
                                "预定未预点餐",
                                JOptionPane.WARNING_MESSAGE);
                        return;  //  阻止
                    }
                }

                //  再检查其他不允许的状态（VACANT/SETTING_UP/SPLITTING）
                if (status != Tables.TableStatus.OCCUPIED && status != Tables.TableStatus.RESERVED) {
                    String statusText = "";
                    switch (status) {
                        case VACANT:
                            statusText = "空闲";
                            break;
                        case SETTING_UP:
                            statusText = "准备中";
                            break;
                        case SPLITTING:
                            statusText = "拆分中";
                            break;
                        default:
                            statusText = "未知状态";
                    }

                    JOptionPane.showMessageDialog(frame,
                            "餐桌 " + displayId + " 当前处于【" + statusText + "】状态，不能进行点餐操作。",
                            "无效操作",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (!checkAndWarnIfNotMainOrderTable(displayId)) {
                    return;
                }

                frame.setCurrentTableNumber(displayId);
                tableNumberField.setText("");
                frame.refreshHomeTemporaryOrder();
                JOptionPane.showMessageDialog(frame, "已成功选择餐桌：" + displayId, "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                showErrorDialog("请输入有效的餐桌号（整数）", "输入错误");
            } catch (Exception e) {
                showErrorDialog("系统错误: " + e.getMessage(), "错误");
                e.printStackTrace();
            }
        } else {
            showErrorDialog("请输入餐桌号", "输入错误");
        }
    }


    private void showErrorDialog(String message, String title) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }


    private boolean checkAndWarnIfNotMainOrderTable(String displayId) {
        Tables targetTable = service.getTableById(displayId);
        if (targetTable == null) {
            return false;
        }

        // ═══════════════════════════════════════════════════════════
        // 【情况1】合并桌（MERGED）- 保留原有逻辑（必须通过主桌操作）
        // ═══════════════════════════════════════════════════════════
        if (targetTable.getTableType() == Tables.TableType.MERGED) {
            String partnerDisplayId = targetTable.getMergedWith();
            if (partnerDisplayId == null || partnerDisplayId.isEmpty()) {
                return true;
            }

            Tables partnerTable = service.getTableById(partnerDisplayId);
            if (partnerTable == null) {
                return true;
            }

            int currentId = Integer.parseInt(displayId.replaceAll("[^0-9]", ""));
            int partnerId = Integer.parseInt(partnerDisplayId.replaceAll("[^0-9]", ""));

            if (currentId > partnerId) {
                String warningMessage = "该合并桌只能通过编号较小的餐桌（" + partnerId + "）进行操作。\n" +
                        "请切换至餐桌 " + partnerId + " 进行相关操作。";
                JOptionPane.showMessageDialog(frame,
                        warningMessage,
                        "操作受限",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }
            return true;
        }

        // ═══════════════════════════════════════════════════════════
        // 【情况2】聚餐桌（GROUPED）- 🔧 修改：允许单独操作，不再限制
        // ═══════════════════════════════════════════════════════════
        if (targetTable.getTableType() == Tables.TableType.GROUPED) {
            // 🔧 聚餐桌允许每张桌子独立点餐/结账，直接返回 true
            // 如需提示用户当前是聚餐桌，可添加可选提示（非强制）：
        /*
        String groupWith = targetTable.getGroupWith();
        if (groupWith != null && !groupWith.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "💡 提示：当前为聚餐桌 #" + displayId + "，可单独点餐。\n" +
                    "关联餐桌：" + groupWith,
                    "聚餐桌提示",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        */
            return true;  // ✅ 关键：直接允许操作
        }

        // ═══════════════════════════════════════════════════════════
        // 【情况3】普通餐桌（MAIN/SUBTABLE）- 直接允许
        // ═══════════════════════════════════════════════════════════
        return true;
    }

    /**
     * 处理确认下单（完整版：支持合并订单 + 三模式 + 金额分离 + 结账后重单）
     */
    private void handleConfirmOrder(ActionEvent e) {
        // ═══════════════════════════════════════════════════════════
        // 【步骤1】获取并验证临时订单
        // ═══════════════════════════════════════════════════════════
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        if (tempOrder == null || tempOrder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有临时订单可以确认，请先点菜",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤2】获取当前订单类型
        // ═══════════════════════════════════════════════════════════
        OrderType orderType = frame.getCurrentOrderType();

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增步骤2.3】判断是否为「结账后重新点单」
        // ═══════════════════════════════════════════════════════════
        final boolean isReorderAfterCheckout;  // 🔧 声明为final供lambda使用

        if (orderType == OrderType.DINE_IN && currentTableNumber != null &&
                !currentTableNumber.isEmpty() && !"未选择".equals(currentTableNumber)) {
            // 堂食模式：检查餐桌是否已结账
            Tables table = service.getTableById(currentTableNumber);
            if (table != null && table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {
                // 🔧 弹出二次确认对话框
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "餐桌 " + currentTableNumber + " 的订单已结账，是否要再次点单？\n" +
                                "此操作将清空原订单明细，重新生成新订单。",
                        "确认再次点单",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) {
                    return;  // 用户取消，终止操作
                }
                isReorderAfterCheckout = true;  //  标记为"结账后重单"
                System.out.println(" 检测到结账后重单：餐桌#" + currentTableNumber);
            } else {
                isReorderAfterCheckout = false;
            }
        } else {
            // 外卖/预约模式不支持重单逻辑
            isReorderAfterCheckout = false;
        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增步骤2.5】检查外卖订单是否已存在（决定是否弹窗）
        // ═══════════════════════════════════════════════════════════
        final boolean orderExists;
        final com.restaurant.entity.Order existingOrder;

        if (orderType != OrderType.DINE_IN && orderType != OrderType.RESERVATION &&
                currentTableNumber != null && !currentTableNumber.isEmpty() && !"待下单".equals(currentTableNumber)) {
            existingOrder = frame.findActiveOrderByOrderNumber(currentTableNumber);
            orderExists = (existingOrder != null);
            if (orderExists) {
                System.out.println("订单已存在：" + currentTableNumber + "，直接追加菜品，不弹窗");
            }
        } else {
            orderExists = false;
            existingOrder = null;
        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增步骤 2.6】配送订单状态验证（只有未配送才能追加菜品）
        // ═══════════════════════════════════════════════════════════
        if (orderType == OrderType.DELIVERY && orderExists && existingOrder != null) {
            Order.DeliveryStatus deliveryStatus = existingOrder.getDeliveryStatus();
            if (deliveryStatus != Order.DeliveryStatus.NOT_DELIVERED) {
                JOptionPane.showMessageDialog(
                        this,
                        " 该配送订单已进入【" + deliveryStatus.getDisplayName() + "】状态！\n\n" +
                                "订单号：" + currentTableNumber + "\n" +
                                "当前配送状态：" + deliveryStatus.getDisplayName() + "\n\n" +
                                "无法继续追加菜品，如有需要请联系客服。",
                        "配送状态限制",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤3】收集客户信息（仅外卖模式 + 新订单）
        // ═══════════════════════════════════════════════════════════
        String customerName = null;
        String customerPhone = null;
        String deliveryAddress = null;
        Double deliveryFee = 0.0;

        // 🔧【核心修复】预约订单不需要收集客户信息（已在预约时收集）
        if (orderType == OrderType.RESERVATION) {
            // 预约订单：直接从预约记录中获取客户信息（可选，用于订单关联）
            if (currentTableNumber != null && !currentTableNumber.isEmpty()) {
                TableReservation reservation = controller.getReservationDetail(currentTableNumber);
                if (reservation != null) {
                    customerName = reservation.getCustomerName();
                    customerPhone = reservation.getCustomerPhone();
                    System.out.println("📋 预约订单 - 使用预约客户信息：" + customerName + ", " + customerPhone);
                }
            }
            deliveryFee = 0.0;  // 预约订单无配送费
        }
        // 🔧 外卖订单（自取/配送）才需要弹窗收集客户信息
        else if (orderType != OrderType.DINE_IN) {
            if (!orderExists) {
                // 🔹 新外卖订单：弹窗收集客户信息
                int rowCount = (orderType == OrderType.DELIVERY) ? 4 : 3;
                JPanel infoPanel = new JPanel(new GridLayout(rowCount, 2, 8, 8));
                infoPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

                infoPanel.add(new JLabel("客户姓名:"));
                JTextField nameField = new JTextField(20);
                infoPanel.add(nameField);

                infoPanel.add(new JLabel("联系电话 *:"));
                JTextField phoneField = new JTextField(15);
                infoPanel.add(phoneField);

                JTextField addressField = null;
                JTextField feeField = null;

                if (orderType == OrderType.DELIVERY) {
                    infoPanel.add(new JLabel("配送地址 *:"));
                    addressField = new JTextField(25);
                    infoPanel.add(addressField);

                    infoPanel.add(new JLabel("配送费 (¥) *:"));
                    feeField = new JTextField("5.00", 10);
                    infoPanel.add(feeField);
                }

                int result = JOptionPane.showConfirmDialog(
                        this, infoPanel,
                        orderType.getDisplayName() + "订单 - 客户信息",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result != JOptionPane.OK_OPTION) return;

                customerName = nameField.getText().trim();
                customerPhone = phoneField.getText().trim();

                if (customerPhone.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "联系电话为必填项", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (orderType == OrderType.DELIVERY) {
                    deliveryAddress = addressField.getText().trim();
                    if (deliveryAddress.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "配送地址为必填项", "输入错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    try {
                        deliveryFee = Double.parseDouble(feeField.getText().trim());
                        if (deliveryFee < 0) {
                            JOptionPane.showMessageDialog(this, "配送费不能为负数", "输入错误", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        deliveryFee = Math.round(deliveryFee * 100.0) / 100.0;
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "配送费格式错误", "输入错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    deliveryFee = 0.0;
                }
            }
            // 🔧 订单已存在时，从数据库读取客户信息（不弹窗）
            else if (existingOrder != null) {
                customerPhone = existingOrder.getCustomerPhone();
                customerName = existingOrder.getCustomerName();
                if (orderType == OrderType.DELIVERY) {
                    deliveryAddress = existingOrder.getDeliveryAddress();
                    deliveryFee = 0.0;  // 追加时先设为0，后续计算
                }
                System.out.println("📋 使用已有订单信息：电话=" + customerPhone);
            }
        } else {
            // 🔹 堂食模式验证
            if (currentTableNumber == null || currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
                JOptionPane.showMessageDialog(this, "堂食订单请先选择餐桌", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Tables table = service.getTableById(currentTableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(this, "未找到餐桌: " + currentTableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (table.getStatus() != Tables.TableStatus.OCCUPIED &&
                    table.getStatus() != Tables.TableStatus.RESERVED) {
                JOptionPane.showMessageDialog(this, "餐桌 " + currentTableNumber +
                                " 当前状态为【" + table.getStatus().getDisplayName() + "】，不能点餐",
                        "无效操作", JOptionPane.WARNING_MESSAGE);
                return;
            }
            deliveryFee = 0.0;
        }

        boolean isDineInGroupedTable = false;
        List<String> dineInGroupedTableIds = new ArrayList<>();

        if (orderType == OrderType.DINE_IN && currentTableNumber != null) {
            Tables table = service.getTableById(currentTableNumber);
            // 🔧【核心】仅聚餐桌启用特殊逻辑
            if (table != null && table.getTableType() == Tables.TableType.GROUPED) {
                isDineInGroupedTable = true;
                // 解析聚餐桌的所有关联桌号（格式："13,14,15"）
                if (table.getGroupWith() != null) {
                    String[] ids = table.getGroupWith().split(",");
                    for (String id : ids) {
                        dineInGroupedTableIds.add(id.trim());
                    }
                }
                System.out.println("🔧 检测到堂食聚餐桌: " + currentTableNumber +
                        "，关联桌号: " + dineInGroupedTableIds);
            }
        }

// ═══════════════════════════════════════════════════════════
// 🔧【新增步骤 3.9】预约聚餐桌点餐特殊处理（仅预约类型=GROUP）
// ═══════════════════════════════════════════════════════════
        boolean isReservationGrouped = false;

        if (orderType == OrderType.RESERVATION && currentTableNumber != null) {
            // 通过 reservation_id 查询 table_reservations 表
            TableReservation reservation = controller.getReservationDetail(currentTableNumber);
            if (reservation != null && "GROUP".equals(reservation.getGroupType())) {
                isReservationGrouped = true;
                System.out.println("🔧 检测到预约聚餐桌: reservationId=" + currentTableNumber +
                        ", groupType=GROUP");
            }
        }

// ═══════════════════════════════════════════════════════════
// 🔧【分支1】堂食聚餐桌：需要传入 groupedTableIds
// ═══════════════════════════════════════════════════════════
        if (isDineInGroupedTable && !dineInGroupedTableIds.isEmpty()) {
            List<OrderItem> processedItems = new ArrayList<>();
            double itemsTotal = 0.0;

            for (Map.Entry<String, Integer> entry : tempOrder.entrySet()) {
                String itemKey = entry.getKey().trim().toUpperCase();
                int qty = entry.getValue();
                String pureItemCode;
                String assignedTableIds = null;

                if (itemKey.contains("[BATCH:")) {
                    int batchStart = itemKey.indexOf("[BATCH:");
                    pureItemCode = itemKey.substring(0, batchStart);
                    assignedTableIds = itemKey.substring(batchStart + 7, itemKey.length() - 1);

                    MenuItem menuItem = menuItemService.getMenuItemByCode(pureItemCode);
                    if (menuItem == null || !menuItem.isActive()) {
                        JOptionPane.showMessageDialog(this, "菜品 " + pureItemCode + " 不存在或已售罄",
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    OrderItem orderItem = new OrderItem();
                    orderItem.setItemId(menuItem.getItemId());
                    orderItem.setItemCode(pureItemCode);
                    orderItem.setItemName(menuItem.getName());
                    orderItem.setQuantity(qty);
                    orderItem.setPriceAtOrder(menuItem.getPrice());
                    orderItem.setAssignedTableDisplayId(assignedTableIds);
                    orderItem.setStatus("UNSERVED");
                    orderItem.setServedQuantity(0);
                    processedItems.add(orderItem);
                    itemsTotal += menuItem.getPrice() * qty;
                } else {
                    MenuItem menuItem = menuItemService.getMenuItemByCode(itemKey);
                    if (menuItem == null || !menuItem.isActive()) {
                        JOptionPane.showMessageDialog(this, "菜品 " + itemKey + " 不存在或已售罄",
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    OrderItem orderItem = new OrderItem();
                    orderItem.setItemId(menuItem.getItemId());
                    orderItem.setItemCode(itemKey);
                    orderItem.setItemName(menuItem.getName());
                    orderItem.setQuantity(qty);
                    orderItem.setPriceAtOrder(menuItem.getPrice());
                    orderItem.setAssignedTableDisplayId(currentTableNumber);
                    orderItem.setStatus("UNSERVED");
                    orderItem.setServedQuantity(0);
                    processedItems.add(orderItem);
                    itemsTotal += menuItem.getPrice() * qty;
                }
            }

            itemsTotal = Math.round(itemsTotal * 100.0) / 100.0;

            // 🔧 调用现有方法（传入 groupedTableIds）
            frame.addOrderItemsForGroupedTable(
                    currentTableNumber,      // mainTableDisplayId
                    processedItems,          // 菜品列表
                    dineInGroupedTableIds    // targetTableIds ⭐ 需要传入
            );

            SwingUtilities.invokeLater(() -> {
                frame.clearTemporaryOrder(currentTableNumber);
                frame.setCurrentOrderType(orderType);
                frame.setCurrentTableNumber(currentTableNumber);
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                refreshDineInOrders();
                JOptionPane.showMessageDialog(this, "✅ 堂食聚餐桌订单已提交！", "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            });
            return;  // 🔑 提前返回
        }

// ═══════════════════════════════════════════════════════════
// 🔧【分支2】预约聚餐桌：无需传入 groupedTableIds
// ═══════════════════════════════════════════════════════════
        if (isReservationGrouped) {
            List<OrderItem> processedItems = new ArrayList<>();
            double itemsTotal = 0.0;

            for (Map.Entry<String, Integer> entry : tempOrder.entrySet()) {
                String itemKey = entry.getKey().trim().toUpperCase();
                int qty = entry.getValue();
                String pureItemCode;
                String assignedTableIds = null;

                if (itemKey.contains("[BATCH:")) {
                    int batchStart = itemKey.indexOf("[BATCH:");
                    pureItemCode = itemKey.substring(0, batchStart);
                    assignedTableIds = itemKey.substring(batchStart + 7, itemKey.length() - 1);

                    MenuItem menuItem = menuItemService.getMenuItemByCode(pureItemCode);
                    if (menuItem == null || !menuItem.isActive()) {
                        JOptionPane.showMessageDialog(this, "菜品 " + pureItemCode + " 不存在或已售罄",
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    OrderItem orderItem = new OrderItem();
                    orderItem.setItemId(menuItem.getItemId());
                    orderItem.setItemCode(pureItemCode);
                    orderItem.setItemName(menuItem.getName());
                    orderItem.setQuantity(qty);
                    orderItem.setPriceAtOrder(menuItem.getPrice());
                    orderItem.setAssignedTableDisplayId(assignedTableIds);
                    orderItem.setStatus("UNSERVED");
                    orderItem.setServedQuantity(0);
                    processedItems.add(orderItem);
                    itemsTotal += menuItem.getPrice() * qty;
                } else {
                    MenuItem menuItem = menuItemService.getMenuItemByCode(itemKey);
                    if (menuItem == null || !menuItem.isActive()) {
                        JOptionPane.showMessageDialog(this, "菜品 " + itemKey + " 不存在或已售罄",
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    OrderItem orderItem = new OrderItem();
                    orderItem.setItemId(menuItem.getItemId());
                    orderItem.setItemCode(itemKey);
                    orderItem.setItemName(menuItem.getName());
                    orderItem.setQuantity(qty);
                    orderItem.setPriceAtOrder(menuItem.getPrice());
                    // 🔧 预约订单用 reservation_id 作为 assignedTableDisplayId
                    orderItem.setAssignedTableDisplayId(currentTableNumber);
                    orderItem.setStatus("UNSERVED");
                    orderItem.setServedQuantity(0);
                    processedItems.add(orderItem);
                    itemsTotal += menuItem.getPrice() * qty;
                }
            }

            itemsTotal = Math.round(itemsTotal * 100.0) / 100.0;

            // 🔧 调用新方法（无需传入 groupedTableIds）
            frame.addOrderItemsForReservationGroupedTable(
                    currentTableNumber,      // reservationId
                    processedItems           // 菜品列表
                    // ⭐ 不需要传入 groupedTableIds
            );

            SwingUtilities.invokeLater(() -> {
                frame.clearTemporaryOrder(currentTableNumber);
                frame.setCurrentOrderType(orderType);
                frame.setCurrentTableNumber(currentTableNumber);
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                refreshReservationOrders();  // 🔧 预约订单刷新预约列表
                JOptionPane.showMessageDialog(this, "✅ 预约聚餐桌订单已提交！", "成功",
                        JOptionPane.INFORMATION_MESSAGE);
            });
            return;  // 🔑 提前返回
        }
        List<OrderItem> orderItems = new ArrayList<>();
        double itemsTotal = 0.0;

        for (Map.Entry<String, Integer> entry : tempOrder.entrySet()) {
            String itemCode = entry.getKey().trim().toUpperCase();
            int qty = entry.getValue();

            MenuItem menuItem = menuItemService.getMenuItemByCode(itemCode);
            if (menuItem == null || !menuItem.isActive()) {
                JOptionPane.showMessageDialog(this, "菜品 " + itemCode + " 不存在或已售罄", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (qty <= 0 || qty > 99) {
                JOptionPane.showMessageDialog(this, "菜品 " + itemCode + " 数量无效", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            orderItems.add(new OrderItem(null, menuItem.getItemId(), itemCode,
                    menuItem.getName(), qty, menuItem.getPrice()));
            itemsTotal += menuItem.getPrice() * qty;
        }
        itemsTotal = Math.round(itemsTotal * 100.0) / 100.0;

        // 🔧 计算最终总金额：仅配送模式加配送费
        double finalTotalAmount = itemsTotal +
                (orderType == OrderType.DELIVERY ? deliveryFee : 0.0);
        finalTotalAmount = Math.round(finalTotalAmount * 100.0) / 100.0;

        // ═══════════════════════════════════════════════════════════
        // 【步骤5】用户最终确认（嵌入追加配送费复选框）
        // ═══════════════════════════════════════════════════════════
        JPanel confirmPanel = new JPanel(new BorderLayout(10, 10));
        confirmPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        StringBuilder msg = new StringBuilder();
        if (orderType == OrderType.DINE_IN) {
            msg.append("确认餐桌 ").append(currentTableNumber).append(" 的订单？\n");
            msg.append("共 ").append(orderItems.size()).append(" 项，总计 ¥")
                    .append(String.format("%.2f", finalTotalAmount));
        } else if (orderType == OrderType.RESERVATION) {
            // 🔧 预约订单确认信息
            msg.append("确认预约订单？\n");
            msg.append("预约号: ").append(currentTableNumber).append("\n");
            if (customerName != null) msg.append("客人: ").append(customerName).append("\n");
            if (customerPhone != null) msg.append("电话: ").append(customerPhone).append("\n");
            msg.append("菜品: ¥").append(String.format("%.2f", itemsTotal));
            msg.append("\n─────────────\n应付: ¥")
                    .append(String.format("%.2f", finalTotalAmount));
        } else {
            if (orderExists) {
                msg.append("（追加菜品）");
            }
            msg.append("确认订单信息：\n");
            msg.append("电话: ").append(customerPhone);
            if (deliveryAddress != null) msg.append("\n地址: ").append(deliveryAddress);
            msg.append("\n菜品: ¥").append(String.format("%.2f", itemsTotal));

            // 仅新订单显示配送费
            if (!orderExists && orderType == OrderType.DELIVERY && deliveryFee > 0) {
                msg.append("\n配送费: ¥").append(String.format("%.2f", deliveryFee));
            }
            msg.append("\n─────────────\n应付: ¥")
                    .append(String.format("%.2f", finalTotalAmount));
        }

        confirmPanel.add(new JLabel("<html>" + msg.toString().replaceAll("\n", "<br>") + "</html>"), BorderLayout.CENTER);

        // 🔧 仅配送模式+已存在订单：显示"追加配送费"复选框
        JCheckBox addFeeCheckBox = null;
        if (orderExists && orderType == OrderType.DELIVERY) {
            addFeeCheckBox = new JCheckBox("追加配送费（默认不追加，勾选后输入金额）");
            addFeeCheckBox.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            addFeeCheckBox.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            confirmPanel.add(addFeeCheckBox, BorderLayout.SOUTH);
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                confirmPanel,
                orderType.getDisplayName() + "订单确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // 🔧 如果勾选了追加配送费，弹出输入框
        if (addFeeCheckBox != null && addFeeCheckBox.isSelected()) {
            Double originalFee = (existingOrder != null && existingOrder.getDeliveryFee() != null)
                    ? existingOrder.getDeliveryFee() : 0.0;

            String input = JOptionPane.showInputDialog(
                    this,
                    "请输入追加的配送费金额（¥）:\n" +
                            "（原配送费: ¥" + String.format("%.2f", originalFee) + "）",
                    "追加配送费",
                    JOptionPane.PLAIN_MESSAGE
            );

            if (input != null && !input.trim().isEmpty()) {
                try {
                    double additionalFee = Double.parseDouble(input.trim());
                    if (additionalFee < 0) {
                        JOptionPane.showMessageDialog(this,
                                "追加金额不能为负数", "输入错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    // 🔧【关键修复】计算最终配送费 = 原配送费 + 追加金额
                    deliveryFee = Math.round((originalFee + additionalFee) * 100.0) / 100.0;
                    // 重新计算总金额
                    finalTotalAmount = itemsTotal + deliveryFee;
                    finalTotalAmount = Math.round(finalTotalAmount * 100.0) / 100.0;
                    System.out.println("💰 配送费计算：原=" + originalFee +
                            ", 追加=" + additionalFee + ", 最终=" + deliveryFee);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                            "金额格式错误", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤6】调用 Controller 处理下单（异步，传入 isReorderAfterCheckout）
        // ═══════════════════════════════════════════════════════════
        final String finalTableNumber = currentTableNumber;
        final List<OrderItem> finalOrderItems = new ArrayList<>(orderItems);
        final OrderType finalOrderType = orderType;
        final String finalCustomerName = customerName;
        final String finalCustomerPhone = customerPhone;
        final String finalDeliveryAddress = deliveryAddress;
        final Double finalDeliveryFee = deliveryFee;
        final double finalItemsTotal = itemsTotal;
        final double finalFinalTotalAmount = finalTotalAmount;
        final Integer finalExistingOrderId = orderExists && existingOrder != null ? existingOrder.getOrderId() : null;
        final Double finalOriginalFee = orderExists && existingOrder != null && existingOrder.getDeliveryFee() != null
                ? existingOrder.getDeliveryFee() : 0.0;

        // 🔧【关键】调用 Controller 时传入 isReorderAfterCheckout 参数
        controller.handleConfirmOrder(
                finalTableNumber, finalOrderItems, finalOrderType,
                finalCustomerName, finalCustomerPhone, finalDeliveryAddress,
                finalDeliveryFee,
                isReorderAfterCheckout,  // 🔧 新增参数：结账后重单标志
                () -> SwingUtilities.invokeLater(() -> {
                    // 1. 清空临时订单
                    frame.clearTemporaryOrder(finalTableNumber);

                    // 2. 同步订单类型
                    frame.setCurrentOrderType(finalOrderType);
                    frame.setCurrentTableNumber(finalTableNumber);

                    // 🔧【关键修复】如果是追加菜品+配送订单+有配送费变更，更新数据库中的配送费
                    if (orderExists && finalOrderType == OrderType.DELIVERY && finalExistingOrderId != null) {
                        try {
                            Double finalFee = finalDeliveryFee != null ? finalDeliveryFee : 0.0;
                            if (finalFee > finalOriginalFee) {
                                frame.updateOrderDeliveryFee(finalExistingOrderId, finalFee);
                                System.out.println(" 已更新订单配送费: orderId=" + finalExistingOrderId +
                                        ", 新配送费=" + finalFee);
                            }
                        } catch (Exception ex) {
                            System.err.println(" 更新配送费失败: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }

                    // 3. 刷新显示
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();

                    // 4. 刷新右侧订单列表
                    switch (finalOrderType) {
                        case DINE_IN -> refreshDineInOrders();
                        case PICKUP, DELIVERY -> refreshTakeoutOrders();
                        case RESERVATION -> refreshReservationOrders();  // 🔧 新增：刷新预约订单列表
                    }

                    // 5. 成功提示
                    String successMsg;
                    if (finalOrderType == OrderType.DINE_IN) {
                        if (isReorderAfterCheckout) {
                            successMsg = " 结账后重单已提交！\n餐桌: " + finalTableNumber +
                                    "\n原订单已清空，新订单总计: ¥" + String.format("%.2f", finalFinalTotalAmount);
                        } else {
                            successMsg = " 堂食订单已提交！\n餐桌: " + finalTableNumber +
                                    "\n总计: ¥" + String.format("%.2f", finalFinalTotalAmount);
                        }
                    } else if (finalOrderType == OrderType.RESERVATION) {
                        // 🔧【修复】使用 finalCustomerName/finalCustomerPhone 替代 customerName/customerPhone
                        successMsg = "✅ 预约订单已提交！\n" +
                                "预约号: " + finalTableNumber + "\n" +
                                (finalCustomerName != null ? "客人: " + finalCustomerName + "\n" : "") +
                                (finalCustomerPhone != null ? "电话: " + finalCustomerPhone + "\n" : "") +
                                "菜品: ¥" + String.format("%.2f", finalItemsTotal) + "\n" +
                                "应付: ¥" + String.format("%.2f", finalFinalTotalAmount);
                    } else {
                        String orderNum = frame.getCurrentTakeoutOrderNumber();
                        successMsg = " " + finalOrderType.getDisplayName() + "订单已生成！\n" +
                                "订单号: " + (orderNum != null ? orderNum : "请查看订单列表") +
                                "\n菜品: ¥" + String.format("%.2f", finalItemsTotal) +
                                (finalOrderType == OrderType.DELIVERY && finalDeliveryFee > 0 ?
                                        "\n新配送费: ¥" + String.format("%.2f", finalDeliveryFee) : "") +
                                "\n应付: ¥" + String.format("%.2f", finalFinalTotalAmount);
                    }
                    JOptionPane.showMessageDialog(this, successMsg, "下单成功", JOptionPane.INFORMATION_MESSAGE);
                })
        );
    }


    private void showConfirmServedDialog(String tableNumber) {
        // 創建對話框
        JFrame dialog = new JFrame("確認上桌");
        dialog.setSize(700, 320);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 操作選擇面板
        JPanel optionPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        optionPanel.setBackground(new Color(245, 248, 255));
        optionPanel.setOpaque(false);

        JRadioButton manualOption = new JRadioButton("手動指定菜品", true);  // 默認選中
        JRadioButton allOption = new JRadioButton("一鍵全部確認");
        JRadioButton sameItemsOption = new JRadioButton("聚餐桌同类菜品上菜");

        manualOption.setOpaque(false);
        allOption.setOpaque(false);
        sameItemsOption.setOpaque(false);

        ButtonGroup operationGroup = new ButtonGroup();
        operationGroup.add(manualOption);
        operationGroup.add(allOption);
        operationGroup.add(sameItemsOption);

        optionPanel.add(manualOption);
        optionPanel.add(allOption);
        optionPanel.add(sameItemsOption);

        mainPanel.add(optionPanel, BorderLayout.NORTH);

        // 輸入面板
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        JLabel tableNumberLabel = new JLabel("餐桌号:");
        JTextField tableNumberField = new JTextField();
        JLabel itemIdLabel = new JLabel("菜品编号（用逗号分隔多个菜品ID）:");
        JTextField itemIdField = new JTextField();
        JLabel quantityLabel = new JLabel(
                "<html>數量（建議將數量 &gt;1 的菜品編號寫在前面）<br>用逗號分隔；未填數量的菜品默認為 1：</html>"
        );
        JTextField quantityField = new JTextField("1");

        // 聚餐桌關聯桌號顯示標籤
        JLabel groupedTablesLabel = new JLabel("聚餐桌号:");
        JLabel groupedTablesDisplayLabel = new JLabel("");
        groupedTablesDisplayLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        groupedTablesDisplayLabel.setForeground(new Color(25, 118, 210));

        inputPanel.add(tableNumberLabel);
        inputPanel.add(tableNumberField);
        inputPanel.add(itemIdLabel);
        inputPanel.add(itemIdField);
        inputPanel.add(quantityLabel);
        inputPanel.add(quantityField);
        inputPanel.add(groupedTablesLabel);
        inputPanel.add(groupedTablesDisplayLabel);


        final String targetTableNumber = (tableNumber != null && !tableNumber.isEmpty() && !"未选择".equals(tableNumber))
                ? tableNumber
                : currentTableNumber;
// 🔧【新增】检查是否为聚餐桌，如果不是则禁用"聚餐桌同类菜品上菜"选项
        Tables table = service.getTableById(targetTableNumber);
        boolean isGroupedTable = (table != null && table.getTableType() == Tables.TableType.GROUPED);
        if (!isGroupedTable) {
            sameItemsOption.setEnabled(false);
            sameItemsOption.setToolTipText("仅聚餐桌可用（当前餐桌类型：" +
                    (table != null ? table.getTableType().getDisplayName() : "未知") + "）");
        } else {
            sameItemsOption.setToolTipText("聚餐桌同类菜品上菜");
        }

        if (!"未选择".equals(targetTableNumber)) {
            tableNumberField.setText(targetTableNumber);
            tableNumberField.setEditable(false);
            // 🔧【關鍵修復】只在初始化時設置文本，不查詢數據庫
            // 聚餐桌號只在切換到"聚餐桌同类菜品上菜"時才查詢顯示
            groupedTablesDisplayLabel.setText("");
            groupedTablesDisplayLabel.setForeground(Color.GRAY);
        }

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 🔧【核心新增】操作提示面板 (位于输入框和按钮之间)
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        hintPanel.setBackground(new Color(245, 248, 255));
        hintPanel.setOpaque(false);
        // 精炼提示语
        JLabel servingHintLabel = new JLabel("💡 提示：聚餐桌请先上【多桌共享】菜品，最后上【单桌额外】菜品。");
        servingHintLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        servingHintLabel.setForeground(new Color(255, 140, 0)); // 橙色警示色
        hintPanel.add(servingHintLabel);

        // 初始状态：如果是聚餐桌且默认选中手动，则显示；否则隐藏
        // 但为了逻辑统一，我们在 toggleVisibility 中控制，这里先设为不可见
        hintPanel.setVisible(false);

        // 使用 BorderLayout，Input 在中间，Hint 在南边（紧贴输入框下方）
        JPanel centerContainer = new JPanel(new BorderLayout(0, 5));
        centerContainer.setOpaque(false);
        centerContainer.add(inputPanel, BorderLayout.CENTER);
        centerContainer.add(hintPanel, BorderLayout.SOUTH);

        // 将中间容器添加到主面板
        mainPanel.add(centerContainer, BorderLayout.CENTER);

        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton confirmBtn = new JButton("確認");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel, BorderLayout.CENTER);

        // 🔧【關鍵修復】初始化可見性：默認"手動指定菜品"，不顯示聚餐桌號
        itemIdLabel.setVisible(true);
        itemIdField.setVisible(true);
        quantityLabel.setVisible(true);
        quantityField.setVisible(true);
        groupedTablesLabel.setVisible(false);  // 🔧 初始隱藏
        groupedTablesDisplayLabel.setVisible(false);  // 🔧 初始隱藏

        // 🔧 初始提示可见性：如果默认是手动且是聚餐桌，则显示提示
        if (manualOption.isSelected() && isGroupedTable) {
            hintPanel.setVisible(true);
        } else {
            // 确保其他模式下初始状态隐藏
            hintPanel.setVisible(false);
        }

        // 切換可見性邏輯
        ActionListener toggleVisibility = evt -> {
            if (manualOption.isSelected()) {
                // 手動指定菜品：顯示菜品編號和數量，隱藏聚餐桌號
                itemIdLabel.setVisible(true);
                itemIdField.setVisible(true);
                quantityLabel.setVisible(true);
                quantityField.setVisible(true);
                groupedTablesLabel.setVisible(false);
                groupedTablesDisplayLabel.setVisible(false);
                hintPanel.setVisible(isGroupedTable);
                dialog.setSize(700, 320);

            } else if (allOption.isSelected()) {
                // 一鍵全部確認：隱藏所有輸入，隱藏聚餐桌號
                itemIdLabel.setVisible(false);
                itemIdField.setVisible(false);
                quantityLabel.setVisible(false);
                quantityField.setVisible(false);
                groupedTablesLabel.setVisible(false);
                groupedTablesDisplayLabel.setVisible(false);
                hintPanel.setVisible(false);
                dialog.setSize(700, 280);

            } else if (sameItemsOption.isSelected()) {
                // 🔧 聚餐桌同類菜品上菜：
                // - 顯示菜品編號輸入框
                // - 隱藏數量輸入框
                // - 🔧 查詢並顯示關聯桌號
                itemIdLabel.setVisible(true);
                itemIdField.setVisible(true);
                quantityLabel.setVisible(false);
                quantityField.setVisible(false);
                groupedTablesLabel.setVisible(true);
                groupedTablesDisplayLabel.setVisible(true);
                hintPanel.setVisible(false);

                // 🔧【關鍵】只有切換到這個選項時才查詢聚餐桌號 （targetTableNumber：lambda 表达式中使用的变量应为 final 或有效 final）
                if (!targetTableNumber.isEmpty() && !"未选择".equals(targetTableNumber)) {
                    Tables currentTable = service.getTableById(targetTableNumber);//作用域中已定义变量 'table'
                    if (currentTable != null && currentTable.getTableType() == Tables.TableType.GROUPED &&
                            currentTable.getGroupWith() != null && !currentTable.getGroupWith().isEmpty()) {
                        groupedTablesDisplayLabel.setText(currentTable.getGroupWith());
                        groupedTablesDisplayLabel.setForeground(new Color(25, 118, 210));
                    } else {
                        groupedTablesDisplayLabel.setText("非聚餐桌");
                        groupedTablesDisplayLabel.setForeground(Color.GRAY);
                    }
                }

                dialog.setSize(700, 320);
            }
            dialog.revalidate();
            dialog.repaint();
        };

        manualOption.addActionListener(toggleVisibility);
        allOption.addActionListener(toggleVisibility);
        sameItemsOption.addActionListener(toggleVisibility);

        // 取消按鈕
        cancelBtn.addActionListener(evt -> dialog.dispose());

        // 確認按鈕
        confirmBtn.addActionListener(evt -> {
            String inputTableNumber = tableNumberField.getText().trim();
            if (inputTableNumber.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "餐桌号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Tables targetTable = service.getTableById(inputTableNumber);
            if (targetTable == null) {
                JOptionPane.showMessageDialog(dialog, "未找到餐桌：" + inputTableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!checkAndWarnIfNotMainOrderTable(inputTableNumber)) {
                dialog.dispose();
                return;
            }

            // 根據選項執行不同邏輯
            if (manualOption.isSelected()) {
                // 手動指定菜品模式
                String itemId = itemIdField.getText().trim();
                String quantityStr = quantityField.getText().trim();

                if (itemId.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "菜品编号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String[] itemIds = itemId.split(",");
                String[] quantityStrs = quantityStr.split(",");
                int[] quantities = new int[itemIds.length];
                boolean allValid = true;

                for (int i = 0; i < itemIds.length; i++) {
                    try {
                        int quantity = i < quantityStrs.length ?
                                Integer.parseInt(quantityStrs[i].trim()) : 1;
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(dialog, "数量必须大于0！", "错误", JOptionPane.ERROR_MESSAGE);
                            allValid = false;
                            break;
                        }
                        quantities[i] = quantity;
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog, "请输入有效的数量（整数）！", "错误", JOptionPane.ERROR_MESSAGE);
                        allValid = false;
                        break;
                    }
                }
                if (!allValid) return;

                boolean anySuccess = false;
                for (int i = 0; i < itemIds.length; i++) {
                    String id = itemIds[i].trim().toUpperCase();
                    if (!id.isEmpty()) {
                        if (markItemsAsServed(dialog, inputTableNumber, id, quantities[i])) {
                            anySuccess = true;
                        }
                    }
                }
                if (anySuccess) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshDineInOrders();
                    JOptionPane.showMessageDialog(dialog, "部分或全部菜品已成功標記為已上桌。");
                } else {
                    JOptionPane.showMessageDialog(dialog, "未找到匹配的未上桌菜品。", "提示", JOptionPane.INFORMATION_MESSAGE);
                }

            } else if (allOption.isSelected()) {
                // 一鍵全部確認模式
                if (markAllItemsAsServed(dialog, inputTableNumber)) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshDineInOrders();
                }

            } else if (sameItemsOption.isSelected()) {
                // 聚餐桌同类菜品上菜模式
                String itemId = itemIdField.getText().trim();

                if (itemId.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入菜品编号！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                handleGroupedTableSameItemsServing(dialog, inputTableNumber, itemId);
            }

            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    /**
     * 处理聚餐桌同类菜品上菜（支持选择具体 order_item_id）
     * 支持三种场景：
     * 1. 都没有上 → 全部上了，served_table_display_id = "13,14,15"
     * 2. 部分上了 → 只上剩余的，served_table_display_id 追加剩余桌号
     * 3. 有重复记录 → 让用户选择具体的 order_item_id 进行处理
     *
     * @param dialog      对话框
     * @param tableNumber 餐桌号
     * @param itemCode    菜品编号（可为空，表示选择所有菜品）
     */
    private void handleGroupedTableSameItemsServing(JFrame dialog, String tableNumber, String itemCode) {
        try {
            // 🔧【新增】验证餐桌状态必须为 OCCUPIED
            Tables table = service.getTableById(tableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(dialog, "未找到餐桌：" + tableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                JOptionPane.showMessageDialog(dialog,
                        "餐桌 " + tableNumber + " 当前状态为【" + table.getStatus().getDisplayName() + "】，不能上菜！\n" +
                                "只有【占用中】的餐桌才能标记菜品上桌。",
                        "状态错误", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 1. 查询该聚餐桌的所有订单菜品
            List<OrderItem> orderItems = frame.loadFormalOrderItems(tableNumber);
            if (orderItems == null || orderItems.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "该餐桌没有订单菜品！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 2. 过滤菜品（排除已上桌的）
            List<OrderItem> selectableItems = new ArrayList<>();
            for (OrderItem item : orderItems) {
                if (!"SERVED".equals(item.getStatus())) {
                    if (itemCode != null && !itemCode.isEmpty() &&
                            !item.getItemCode().equalsIgnoreCase(itemCode)) {
                        continue;
                    }
                    selectableItems.add(item);
                }
            }

            if (selectableItems.isEmpty()) {
                String msg = (itemCode != null && !itemCode.isEmpty()) ?
                        "菜品 " + itemCode + " 已全部上桌或不存在" :
                        "未发现有可上菜的记录！";
                JOptionPane.showMessageDialog(dialog, msg, "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 3. 🔧【核心修改】按菜品编号分组，检查是否有多个记录
            Map<String, List<OrderItem>> itemsByCode = new LinkedHashMap<>();
            for (OrderItem item : selectableItems) {
                String code = item.getItemCode().toUpperCase();
                itemsByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(item);
            }

            // 4. 🔧【核心逻辑】遍历每个菜品
            for (Map.Entry<String, List<OrderItem>> entry : itemsByCode.entrySet()) {
                List<OrderItem> sameCodeItems = entry.getValue();
                String currentCode = entry.getKey();

                // 🔧【新增】检查 quantity_distribution 并提示不均匀分配
                checkAndNotifyUnevenDistribution(dialog, sameCodeItems, currentCode);

                // 🔧 如果只有一个记录，直接上菜（不弹窗）
                if (sameCodeItems.size() == 1) {
                    OrderItem singleItem = sameCodeItems.get(0);
                    serveSingleItem(dialog, tableNumber, singleItem);
                    continue;
                }

                // 🔧 如果有多个记录，弹窗让用户选择
                showSelectionDialogForMultipleItems(dialog, tableNumber, sameCodeItems);
            }

            // 5. 刷新显示
            refreshTemporaryOrderDisplay();
            refreshFormalOrderDisplay();
            refreshDineInOrders();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(dialog, "处理失败：" + e.getMessage(), "错误",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 🔧【新增】检查 quantity_distribution 并提示不均匀分配
     *
     * @param dialog   对话框
     * @param items    同菜品的订单项列表
     * @param itemCode 菜品编号
     */
    private void checkAndNotifyUnevenDistribution(JFrame dialog, List<OrderItem> items, String itemCode) {
        for (OrderItem item : items) {
            String distributionStr = item.getQuantityDistribution();

            // 如果 distribution 为 null 或空，跳过检查
            if (distributionStr == null || distributionStr.isEmpty()) {
                continue;
            }

            // 解析 JSON 字符串
            Map<String, Integer> distribution = parseQuantityDistribution(distributionStr);
            if (distribution == null || distribution.isEmpty()) {
                continue;
            }

            // 检查所有桌号的数量是否一致
            Integer firstQty = null;
            boolean allSame = true;
            for (Integer qty : distribution.values()) {
                if (firstQty == null) {
                    firstQty = qty;
                } else if (!firstQty.equals(qty)) {
                    allSame = false;
                    break;
                }
            }

            // 如果数量不一致，弹出提示
            if (!allSame) {
                String itemName = getMenuItemNameByCode(itemCode);
                StringBuilder msgBuilder = new StringBuilder();
                msgBuilder.append("<html><b>⚠️ 菜品分配数量不均匀</b><br><br>");
                msgBuilder.append("菜品：<b>").append(itemName).append("</b> (").append(itemCode).append(")<br><br>");

                // 按桌号数字排序后显示（确保 16,17,18 顺序正确）
                List<String> sortedTableIds = new ArrayList<>(distribution.keySet());
                sortedTableIds.sort(Comparator.comparingInt(s ->
                        Integer.parseInt(s.replaceAll("[^0-9]", ""))));

                for (String tableId : sortedTableIds) {
                    msgBuilder.append("• 桌 #").append(tableId).append("：")
                            .append("<b>").append(distribution.get(tableId)).append("</b> 份 ").append(itemName).append("<br>");
                }
                msgBuilder.append("<br><font color='#666'>请按需选择上菜记录</font></html>");

                JOptionPane.showMessageDialog(dialog, msgBuilder.toString(),
                        "数量分配提示", JOptionPane.WARNING_MESSAGE);
                break; // 每个菜品只提示一次
            }
        }
    }

    /**
     * 🔧 解析 quantity_distribution JSON 字符串
     * 支持格式：{"16":4,"17":4,"18":4} 或 {"16": 2, "17": 3}
     *
     * @param jsonStr JSON 字符串
     * @return 桌号→数量的映射，解析失败返回空 Map
     */
    private Map<String, Integer> parseQuantityDistribution(String jsonStr) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (jsonStr == null || jsonStr.isEmpty()) return result;

        try {
            // 移除花括号和空格
            String content = jsonStr.trim().replaceAll("[{}\\s]", "");
            if (content.isEmpty()) return result;

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
        } catch (Exception e) {
            System.err.println("解析 quantity_distribution 失败: " + jsonStr);
            // 解析失败不影响主流程，返回空 Map 继续执行
        }
        return result;
    }

    /**
     * 🔧 根据菜品编号查询菜品名称
     * 通过 order_items.item_id → menu_items.item_id 关联查询 name
     *
     * @param itemCode 菜品编号（如 "A1"）
     * @return 菜品名称，查询失败返回"未知菜品"
     */
    private String getMenuItemNameByCode(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) return "未知菜品";

        try {
            // 通过 MenuItemService 查询（已注入）
            MenuItem item = menuItemService.getMenuItemByCode(itemCode.toUpperCase());
            if (item != null) {
                return item.getName();
            }
        } catch (Exception e) {
            System.err.println("查询菜品名称失败: " + itemCode + ", 错误: " + e.getMessage());
        }
        return "未知菜品";
    }

    /**
     * 辅助方法：直接上菜单个记录（不弹窗）
     */
    private void serveSingleItem(JFrame dialog, String tableNumber, OrderItem item) {
        try {
            // 获取分配的所有桌号
            String assignedTables = item.getAssignedTableDisplayId();
            if (assignedTables == null || assignedTables.isEmpty()) {
                assignedTables = tableNumber;  // 兜底：使用当前桌号
            }

            // 调用Controller上菜
            controller.handleMarkSpecificOrderItemAsServed(
                    tableNumber,
                    item.getOrderItemId(),
                    item.getQuantity()  // 🔧 上桌数量 = 总数量
            );

            System.out.println("✅ 直接上菜: " + item.getItemName() +
                    " | 桌号: " + assignedTables +
                    " | 数量: " + item.getQuantity());

        } catch (Exception e) {
            JOptionPane.showMessageDialog(dialog,
                    "菜品 " + item.getItemName() + " 上菜失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 辅助方法：显示选择对话框（多个记录时）
     */
    private void showSelectionDialogForMultipleItems(JFrame dialog, String tableNumber, List<OrderItem> items) {
        // 排序：优先显示部分上桌的
        items.sort((i1, i2) -> {
            boolean p1 = "PARTIALLY_SERVED".equals(i1.getStatus());
            boolean p2 = "PARTIALLY_SERVED".equals(i2.getStatus());
            if (p1 && !p2) return -1;
            if (!p1 && p2) return 1;
            return Integer.compare(i1.getOrderItemId(), i2.getOrderItemId());
        });

        // 创建对话框
        JDialog selectionDialog = new JDialog(dialog, "🍽️ 选择要上菜的菜品记录", true);
        selectionDialog.setSize(900, 500);
        selectionDialog.setLocationRelativeTo(dialog);
        selectionDialog.getContentPane().setBackground(new Color(245, 248, 250));

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(245, 248, 250));

        // 标题面板
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(new Color(245, 248, 250));
        JLabel titleLabel = new JLabel("<html><h2 style='color:#1976d2;margin:0;'>🍽️ 选择要上菜的菜品记录</h2></html>");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 表格模型
        String[] columnNames = {"选择", "菜品", "总数", "已上", "状态", "分配餐桌", "记录ID"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // 只允许编辑选择列
            }
        };

        // 添加数据到表格
        Map<Integer, OrderItem> rowToItemMap = new HashMap<>();
        int rowIndex = 0;
        for (OrderItem item : items) {
            boolean isPartiallyServed = "PARTIALLY_SERVED".equals(item.getStatus());
            String statusText = getStatusText(item.getStatus());
            String assignedTables = item.getAssignedTableDisplayId() != null ?
                    item.getAssignedTableDisplayId() : "-";

            Object[] row = {
                    isPartiallyServed, // 默认选中部分上桌的
                    item.getItemName(),
                    item.getQuantity(),
                    item.getServedQuantity(),
                    statusText,
                    assignedTables,
                    "#" + item.getOrderItemId()
            };
            tableModel.addRow(row);
            rowToItemMap.put(rowIndex++, item);
        }

        // 创建表格
        JTable itemTable = new JTable(tableModel);
        itemTable.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        itemTable.setRowHeight(35);
        itemTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        itemTable.getTableHeader().setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        itemTable.getTableHeader().setBackground(new Color(232, 245, 253));
        itemTable.getTableHeader().setForeground(new Color(25, 118, 210));
        itemTable.setGridColor(new Color(224, 224, 224));
        itemTable.setShowGrid(true);

        // 设置列宽
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(0).setMaxWidth(60);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        itemTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(2).setMaxWidth(60);
        itemTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(3).setMaxWidth(60);
        itemTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        itemTable.getColumnModel().getColumn(4).setMaxWidth(100);
        itemTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        itemTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        itemTable.getColumnModel().getColumn(6).setMaxWidth(80);

        // 设置单元格渲染器
        itemTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                OrderItem item = rowToItemMap.get(row);
                boolean isPartiallyServed = "PARTIALLY_SERVED".equals(item.getStatus());

                if (!isSelected) {
                    if (isPartiallyServed) {
                        c.setBackground(new Color(255, 251, 235));
                        c.setForeground(new Color(102, 77, 0));
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }
                }

                if (column == 2 || column == 3 || column == 6) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else if (column == 0 || column == 4) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                }

                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(itemTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 210, 220), 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 提示面板
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hintPanel.setBackground(new Color(245, 248, 250));
        hintPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        boolean hasPartiallyServed = items.stream()
                .anyMatch(i -> "PARTIALLY_SERVED".equals(i.getStatus()));

        if (hasPartiallyServed) {
            JLabel hintLabel = new JLabel("<html><span style='color:#d32f2f;font-weight:bold;'>⚠️ 黄色背景为已部分上桌，建议优先完成！</span></html>");
            hintLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            hintPanel.add(hintLabel);
        }

        JLabel tipLabel = new JLabel("<html><span style='color:#666;'>💡 勾选后要上菜的记录，点击【确定】执行上菜操作</span></html>");
        tipLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        hintPanel.add(Box.createHorizontalStrut(20));
        hintPanel.add(tipLabel);

        mainPanel.add(hintPanel, BorderLayout.SOUTH);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(new Color(245, 248, 250));
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton confirmBtn = new JButton("✓ 确认上菜");
        confirmBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        confirmBtn.setBackground(new Color(76, 175, 80));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setBorderPainted(false);
        confirmBtn.setPreferredSize(new Dimension(120, 35));
        confirmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton cancelBtn = new JButton("✗ 取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        cancelBtn.setBackground(new Color(158, 158, 158));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setPreferredSize(new Dimension(100, 35));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);

        selectionDialog.add(mainPanel, BorderLayout.CENTER);
        selectionDialog.add(buttonPanel, BorderLayout.SOUTH);

        // 按钮事件
        cancelBtn.addActionListener(e -> selectionDialog.dispose());

        confirmBtn.addActionListener(e -> {
            List<Integer> selectedOrderItemIds = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
                if (Boolean.TRUE.equals(selected)) {
                    OrderItem item = rowToItemMap.get(i);
                    selectedOrderItemIds.add(item.getOrderItemId());
                }
            }

            if (selectedOrderItemIds.isEmpty()) {
                JOptionPane.showMessageDialog(selectionDialog,
                        "⚠️ 请至少选择一条记录！", "提示",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 检查部分上桌记录
            List<OrderItem> selectedItems = selectedOrderItemIds.stream()
                    .map(id -> items.stream()
                            .filter(i -> i.getOrderItemId() == id)
                            .findFirst()
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<OrderItem> partiallyList = selectedItems.stream()
                    .filter(i -> "PARTIALLY_SERVED".equals(i.getStatus()))
                    .collect(Collectors.toList());

            if (!partiallyList.isEmpty()) {
                String servedTables = partiallyList.stream()
                        .map(OrderItem::getServedTableDisplayId)
                        .filter(Objects::nonNull)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", "));

                int confirm = JOptionPane.showConfirmDialog(selectionDialog,
                        "<html><b>⚠️ 选中的记录中已有部分上桌！</b><br><br>" +
                                "已上桌桌号：" + (servedTables.isEmpty() ? "无" : servedTables) + "<br><br>" +
                                "是否继续上完剩余餐桌？",
                        "确认上菜", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            // 执行上菜
            selectionDialog.dispose();
            int successCount = 0;
            for (Integer orderItemId : selectedOrderItemIds) {
                OrderItem targetItem = selectedItems.stream()
                        .filter(i -> i.getOrderItemId() == orderItemId)
                        .findFirst()
                        .orElse(null);
                if (targetItem == null) continue;

                try {
                    // 🔧 上桌数量 = 总数量
                    controller.handleMarkSpecificOrderItemAsServed(
                            tableNumber,
                            orderItemId,
                            targetItem.getQuantity()
                    );
                    successCount++;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog,
                            "记录 #" + orderItemId + " 上菜失败：" + ex.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
            }

            if (successCount > 0) {
                String msg = String.format("✅ 成功上菜 %d 条记录！", successCount);
                if (selectedOrderItemIds.size() > successCount) {
                    msg += String.format("（共选择 %d 条，%d 条失败）",
                            selectedOrderItemIds.size(), selectedOrderItemIds.size() - successCount);
                }
                JOptionPane.showMessageDialog(dialog, msg, "成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialog, "❌ 所有记录上菜失败，请检查", "失败",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        selectionDialog.setVisible(true);
    }


    private boolean isSingleTableAssignment(String assignedTableDisplayId) {
        return assignedTableDisplayId == null ||
                assignedTableDisplayId.isEmpty() ||
                !assignedTableDisplayId.contains(",");
    }

    private boolean markItemsAsServed(Component parentComponent, String tableNumber, String itemCode, int quantity) {
        // ===== 1-4. 基礎驗證（保持不變）=====
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "餐桌號不能為空！", "錯誤", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
            return false;
        }

        try {
            Tables table = service.getTableById(tableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(parentComponent, "未找到餐桌：" + tableNumber, "錯誤", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                String statusText = table.getStatus().getDisplayName();
                JOptionPane.showMessageDialog(parentComponent,
                        "餐桌 " + tableNumber + " 當前狀態為【" + statusText + "】，不能上菜！\n" +
                                "只有【占用中】的餐桌才能標記菜品上桌。",
                        "狀態錯誤", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } catch (Exception e) {
            System.err.println("⚠️ 查詢餐桌狀態失敗: " + e.getMessage());
        }

        com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode.trim().toUpperCase());
        if (menuItem == null) {
            JOptionPane.showMessageDialog(parentComponent, "菜品 " + itemCode + " 不存在或已停售", "錯誤", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int itemId = menuItem.getItemId();

        // ===== 5. 🔧【核心修改】聚餐桌 assigned_table_display_id 檢查 + 數量匹配校驗 =====
        try {
            Tables checkTable = service.getTableById(tableNumber);
            boolean isGroupedTable = (checkTable != null &&
                    checkTable.getTableType() == Tables.TableType.GROUPED);

            if (isGroupedTable) {
                List<OrderItem> orderItems = frame.loadFormalOrderItems(tableNumber);
                OrderItem targetItem = null;
                for (OrderItem item : orderItems) {
                    if (item.getItemCode().equalsIgnoreCase(itemCode)) {
                        targetItem = item;
                        break;
                    }
                }

                if (targetItem != null) {
                    String assignedTableId = targetItem.getAssignedTableDisplayId();
                    String status = targetItem.getStatus();
                    int preparedQuantity = targetItem.getPreparedQuantity();

                    // 🔧【核心修复】使用新方法判断是否为单桌分配 主要用來讓準備好的菜品才能全部上菜
                    boolean isSingleTable = isSingleTableAssignment(assignedTableId);

                    // 🔧 PREPARING 狀態禁止上桌
                    if ("PREPARING".equals(status)) {
                        JOptionPane.showMessageDialog(parentComponent,
                                "聚餐桌中【準備中】的菜品不能上桌！",
                                "操作受限", JOptionPane.WARNING_MESSAGE);
                        return false;
                    }

                    // 🔧 PREPARED + 單桌 → 校驗上桌數量必須等於已準備數量
                    if ("PREPARED".equals(status) && isSingleTable) {
                        if (quantity != preparedQuantity) {
                            String errorMsg = String.format(
                                    "<html><b>⚠️ 數量不匹配！</b><br><br>" +
                                            "菜品：<b>%s</b> (%s)<br>" +
                                            "已準備數量（可上桌）：<b>%d 份</b><br>" +
                                            "您輸入的數量：%d 份<br><br>" +
                                            "<font color='#4caf50'>✅ 請一次性上桌 %d 份</font></html>",
                                    targetItem.getItemName(), itemCode,
                                    preparedQuantity, quantity, preparedQuantity
                            );
                            JOptionPane.showMessageDialog(parentComponent, errorMsg,
                                    "數量校驗失敗", JOptionPane.WARNING_MESSAGE);
                            return false;
                        }
                        if (preparedQuantity <= 0) {
                            JOptionPane.showMessageDialog(parentComponent,
                                    "⚠️ 該菜品尚未準備完成，不能上桌！",
                                    "準備未完成", JOptionPane.WARNING_MESSAGE);
                            return false;
                        }
                    }
                }
            }
            //  普通餐桌：跳過 assigned_table_display_id 檢查，直接放行
        } catch (Exception e) {
            System.err.println("⚠️ 查詢菜品狀態失敗: " + e.getMessage());
        }

        // ===== 6. 轉發事件到 Controller（保持不變）=====
        try {
            controller.handleMarkItemsAsServed(tableNumber, itemId, quantity);

            // 成功後刷新 UI
            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent,
                    "標記上桌失敗: " + e.getMessage(),
                    "錯誤", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 一键标记所有菜品为已上桌（支持预约入座弹窗确认）
     *
     * @param parentComponent 父组件
     * @param tableNumber     餐桌编号（String）
     * @return 操作是否成功
     */
    private boolean markAllItemsAsServed(Component parentComponent, String tableNumber) {
        // 1. UI 验证：餐桌号
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "餐桌号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // 2. UI 验证：合并餐桌规则
        if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
            return false;
        }

        // 3. 验证餐桌状态必须为 OCCUPIED + 获取餐桌对象
        Tables table = null;
        try {
            table = service.getTableById(tableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(parentComponent, "未找到餐桌：" + tableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                JOptionPane.showMessageDialog(parentComponent,
                        "餐桌 " + tableNumber + " 当前状态为【" + table.getStatus().getDisplayName() + "】，不能上菜！\n" +
                                "只有【占用中】的餐桌才能标记菜品上桌。",
                        "状态错误", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } catch (Exception e) {
            System.err.println(" 查询餐桌状态失败: " + e.getMessage());
        }

        // 4. 【关键】判断是否为「预约入座」：检查 current_reservation_id 是否不为空
        // 客人入座后订单类型已转为 DINE_IN，必须通过此字段判断是否为预约转化
        boolean isReservationSeated = (table != null &&
                table.getCurrentReservationId() != null &&
                !table.getCurrentReservationId().isEmpty());

        // ═══════════════════════════════════════════════════════════
        // 【非预约入座】→ 直接调用 Controller 原有方法，保持普通堂食逻辑不变
        // ═══════════════════════════════════════════════════════════
        if (!isReservationSeated) {
            try {
                // 🔥 直接调用 Controller 原有方法，不弹窗、不干预
                controller.handleMarkAllItemsAsServed(tableNumber);

                // 成功后刷新 UI
                SwingUtilities.invokeLater(() -> {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshDineInOrders();
                });

                JOptionPane.showMessageDialog(parentComponent,
                        " 已将餐桌 " + tableNumber + " 的所有菜品标记为已上桌！",
                        "🎉 成功", JOptionPane.INFORMATION_MESSAGE);
                return true;

            } catch (Exception e) {
                JOptionPane.showMessageDialog(parentComponent,
                        " 标记全部菜品失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                return false;
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【预约入座】对 PREPARING/UNSERVED 菜品弹窗确认,错误标志：记录是否发生业务异常
        // ═══════════════════════════════════════════════════════════
        boolean hasBusinessError = false;  // 🔧 新增标志

        try {
            List<OrderItem> orderItems = frame.loadFormalOrderItems(tableNumber);
            if (orderItems == null || orderItems.isEmpty()) {
                JOptionPane.showMessageDialog(parentComponent, "该餐桌暂无菜品！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return false;
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【新增】检查是否有重复菜品（同一菜品有多个未完全上桌记录）
            // ═══════════════════════════════════════════════════════════
            Map<String, List<OrderItem>> itemsByCode = new LinkedHashMap<>();
            for (OrderItem item : orderItems) {
                if (!"SERVED".equals(item.getStatus())) {  // 只检查未完全上桌的
                    String code = item.getItemCode().toUpperCase();
                    itemsByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(item);
                }
            }

            // 检查是否有重复菜品（同一菜品有多个未完全上桌记录）
            List<String> duplicateItems = new ArrayList<>();
            for (Map.Entry<String, List<OrderItem>> entry : itemsByCode.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplicateItems.add(entry.getKey());
                }
            }

            // 如果有重复菜品，阻止操作并提示
            if (!duplicateItems.isEmpty()) {
                StringBuilder msg = new StringBuilder("<html><body style='font-family:Microsoft YaHei; padding:15px;'>");
                msg.append("<div style='background-color:#fff3e0; border-left:4px solid #ff9800; padding:12px; margin-bottom:15px;'>");
                msg.append("<b style='color:#e65100; font-size:14px;'>⚠️ 检测到重复菜品</b></div>");
                msg.append("<p style='margin:8px 0;'>发现以下菜品有多个未完全上桌的记录：</p>");
                for (String itemCode : duplicateItems) {
                    msg.append("<p style='margin:5px 0; color:#d32f2f;'><b>• ").append(itemCode).append("</b></p>");
                }
                msg.append("<hr style='border:0; border-top:1px dashed #ccc; margin:15px 0;'>");
                msg.append("<p style='margin:8px 0; color:#666; font-size:13px;'>");
                msg.append("💡 <b>操作建议：</b><br>");
                msg.append("请使用【聚餐桌同类菜品上菜】功能，<br>");
                msg.append("这样可以精确选择要上菜的记录，避免混淆。</p>");
                msg.append("</body></html>");

                JOptionPane.showMessageDialog(parentComponent,
                        msg.toString(),
                        "聚餐桌重复菜品提示",
                        JOptionPane.WARNING_MESSAGE);
                return false;  // 🔑 阻止操作
            }

            int markedCount = 0;
            int skippedCount = 0;
            StringBuilder skippedItems = new StringBuilder();

            for (OrderItem item : orderItems) {
                String status = item.getStatus();
                if ("SERVED".equals(status)) {
                    System.out.println("⏭️ 跳过已上桌菜品: " + item.getItemCode());
                    continue;
                }

                boolean shouldMark = true;

                // 【核心逻辑】预约入座 + (PREPARING 或 UNSERVED) → 弹窗确认
                if ("PREPARING".equals(status) || "UNSERVED".equals(status)) {
                    int confirm = JOptionPane.showConfirmDialog(parentComponent,
                            "<html><body style='font-family:Microsoft YaHei; padding:10px;'>" +
                                    "<b>\"" + item.getItemName() + "\"</b> (" + item.getItemCode() + ")<br>" +
                                    "<hr style='border:0; border-top:1px solid #eee; margin:8px 0;'>" +
                                    "📊 当前状态：<font color='#ffa500'><b>" + getStatusText(status) + "</b></font><br>" +
                                    "🍽️ 预约入座：<font color='#1976d2'>是</font><br><br>" +
                                    "<font color='#d32f2f'><b>❓ 该菜品是否已准备好可以上桌？</b></font>" +
                                    "</body></html>",
                            "🍳 确认菜品准备状态",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    if (confirm != JOptionPane.YES_OPTION) {
                        shouldMark = false;
                        skippedCount++;
                        if (skippedItems.length() > 0) skippedItems.append(", ");
                        skippedItems.append(item.getItemCode() + "(" + item.getItemName() + ")");
                        continue;
                    }
                }

                // 执行标记（如果应该标记）
                if (shouldMark) {
                    try {
                        controller.handleMarkItemsAsServed(tableNumber, item.getItemId(), item.getQuantity());
                        markedCount++;
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();

                        // 🔧【核心修复】判断是否为"聚餐桌跳过桌号"业务异常
                        if (errorMsg != null && errorMsg.contains("聚餐桌一键上桌不能跳过桌号")) {
                            // 🔧 解析错误信息中的关键数据
                            String currentTable = extractValue(errorMsg, "当前桌号：");
                            String servedTables = extractValue(errorMsg, "已上桌桌号：");

                            // 🔧 显示格式化业务提示弹窗
                            showGroupedTableServingError(parentComponent, currentTable, servedTables);

                            // 🔧【关键】设置错误标志，中断后续操作
                            hasBusinessError = true;
                            break;  // 🔑 立即退出循环，不再处理后续菜品

                        }// 2. 🔧【新增】处理“Distribution 不均匀”的错误
                        else if (errorMsg != null && errorMsg.contains("分配数量不均匀")) {
                            // 直接弹窗提示，并跳过这道菜（不中断整个循环，或者中断由你决定）
                            JOptionPane.showMessageDialog(parentComponent,
                                    "⚠️ 菜品 [" + item.getItemCode() + "] 无法一键上桌！\n\n" +
                                            "原因：该菜品在各桌的分配数量不均匀。\n" +
                                            "详细信息：" + errorMsg,
                                    "上菜失败", JOptionPane.WARNING_MESSAGE);

                            // 这里可以选择 continue (跳过这道菜，继续上下一道)
                            // 或者 markedCount 不加，让它跳过
                            continue;
                        } else {
                            // 其他系统异常：显示默认错误
                            JOptionPane.showMessageDialog(parentComponent,
                                    " 标记菜品 " + item.getItemCode() + " 失败: " + errorMsg,
                                    "错误", JOptionPane.ERROR_MESSAGE);
                        }
                        System.err.println(" 标记菜品 " + item.getItemCode() + " 失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心修复】只有没有业务错误时，才显示成功提示
            // ═══════════════════════════════════════════════════════════
            if (!hasBusinessError) {
                // 结果提示
                if (skippedCount > 0) {
                    String skipMsg = skippedItems.length() > 80 ?
                            skippedItems.substring(0, 80) + "..." : skippedItems.toString();
                    JOptionPane.showMessageDialog(parentComponent,
                            " 已标记 " + markedCount + " 个菜品为已上桌。\n\n" +
                                    "⏭ 跳过 " + skippedCount + " 个菜品（确认未准备好）：\n" +
                                    "   " + skipMsg + "\n\n" +
                                    " 请等待这些菜品准备完成后再上桌。",
                            " 部分菜品跳过", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(parentComponent,
                            " 已将餐桌 " + tableNumber + " 的所有菜品标记为已上桌！",
                            "🎉 成功", JOptionPane.INFORMATION_MESSAGE);
                }

                // 成功后刷新 UI
                SwingUtilities.invokeLater(() -> {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshDineInOrders();
                });
                return true;
            } else {
                // 🔧 发生业务错误，不刷新UI，返回false
                System.out.println("⚠️ 因业务规则错误，操作已中止");
                return false;
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent,
                    " 标记全部菜品失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 🔧 从错误消息中提取指定字段的值
     *
     * @param message 完整错误消息
     * @param key     字段键名（如 "当前桌号："）
     * @return 提取的值，找不到返回空字符串
     */
    private String extractValue(String message, String key) {
        if (message == null || key == null) return "";
        int start = message.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        int end = message.indexOf("\n", start);
        if (end == -1) end = message.length();
        return message.substring(start, end).trim();
    }

    /**
     * 🔧 显示聚餐桌上桌顺序错误弹窗（美化版）
     */
    private void showGroupedTableServingError(Component parent, String currentTable, String servedTables) {
        // 解析已上桌桌号列表
        List<String> servedList = new ArrayList<>();
        if (servedTables != null && !servedTables.isEmpty()) {
            for (String id : servedTables.split(",")) {
                servedList.add(id.trim());
            }
        }

        // 构建友好的提示信息
        StringBuilder htmlMsg = new StringBuilder();
        htmlMsg.append("<html><body style='font-family:Microsoft YaHei; padding:15px; max-width:400px;'>");
        htmlMsg.append("<div style='background-color:#fff3e0; border-left:4px solid #ff9800; padding:12px; margin-bottom:15px;'>");
        htmlMsg.append("<b style='color:#e65100; font-size:14px;'>⚠️ 上桌顺序错误</b></div>");

        htmlMsg.append("<p style='margin:8px 0;'><b>当前操作桌号：</b><font color='#1976d2'>#" + currentTable + "</font></p>");

        if (!servedList.isEmpty()) {
            htmlMsg.append("<p style='margin:8px 0;'><b>已上桌桌号：</b>");
            for (int i = 0; i < servedList.size(); i++) {
                if (i > 0) htmlMsg.append(", ");
                htmlMsg.append("<font color='#4caf50'>#").append(servedList.get(i)).append("</font>");
            }
            htmlMsg.append("</p>");
        }

        htmlMsg.append("<hr style='border:0; border-top:1px dashed #ccc; margin:15px 0;'>");
        htmlMsg.append("<p style='margin:8px 0; color:#666; font-size:13px;'>");
        htmlMsg.append("💡 <b>操作提示：</b><br>");
        htmlMsg.append("聚餐桌菜品必须按桌号顺序上桌，<br>");
        htmlMsg.append("当前桌号必须与【已上桌桌号】相邻（差值=1）<br><br>");
        htmlMsg.append("✅ 正确示例：15 → 16 → 17（顺序）<br>");
        htmlMsg.append("✅ 正确示例：17 → 16 → 15（回退）<br>");
        htmlMsg.append("❌ 错误示例：15 → 17（跳过16）</p>");
        htmlMsg.append("</body></html>");

        JOptionPane.showMessageDialog(
                parent,
                htmlMsg.toString(),
                "聚餐桌上桌顺序提示",
                JOptionPane.WARNING_MESSAGE
        );
    }

    /**
     * 辅助方法：获取状态显示文本（带表情符号）
     */
    private String getStatusText(String status) {
        if (status == null) return "⚪ 未知";
        return switch (status) {
            case "PREPARING" -> "🟡 准备中";
            case "PREPARED" -> "🟢 已准备";
            case "UNSERVED" -> "⚪ 未上桌";
            case "PARTIALLY_SERVED" -> "🟠 部分上桌";
            case "SERVED" -> "🟢 已上桌";
            default -> status;
        };
    }

    /**
     * 顯示外賣訂單「製作完成」確認對話框
     */
    private void showConfirmTakeoutReadyDialog(String orderNumber) {
        JFrame dialog = new JFrame("確認製作完成");
        dialog.setSize(650, 280);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 操作選擇：手動指定 / 一鍵全部
        JPanel optionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        JRadioButton manualOption = new JRadioButton("手動指定菜品", true);
        JRadioButton allOption = new JRadioButton("一鍵全部確認");
        ButtonGroup operationGroup = new ButtonGroup();
        operationGroup.add(manualOption);
        operationGroup.add(allOption);
        optionPanel.add(manualOption);
        optionPanel.add(allOption);
        mainPanel.add(optionPanel, BorderLayout.NORTH);

        // 輸入面板
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        JLabel orderNumberLabel = new JLabel("訂單號:");
        JTextField orderNumberField = new JTextField();
        JLabel itemIdLabel = new JLabel("菜品编号（用逗号分隔多个菜品ID）:");
        JTextField itemIdField = new JTextField();
        JLabel quantityLabel = new JLabel("<html>數量（建議將數量 &gt;1 的菜品編號寫在前面）<br>用逗號“,”分隔；未填數量的菜品默認為 1：</html>");
        JTextField quantityField = new JTextField("1");

        inputPanel.add(orderNumberLabel);
        inputPanel.add(orderNumberField);
        inputPanel.add(itemIdLabel);
        inputPanel.add(itemIdField);
        inputPanel.add(quantityLabel);
        inputPanel.add(quantityField);

        // 預填訂單號
        String targetOrderNumber = (orderNumber != null && !orderNumber.isEmpty() && !"待下单".equals(orderNumber))
                ? orderNumber : currentTakeoutOrderNumber != null ? currentTakeoutOrderNumber : currentTableNumber;
        if (targetOrderNumber != null && !targetOrderNumber.isEmpty() && !"待下单".equals(targetOrderNumber)) {
            orderNumberField.setText(targetOrderNumber);
            orderNumberField.setEditable(false);
        }

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 按鈕
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton confirmBtn = new JButton("確認");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel, BorderLayout.CENTER);

        // 切換輸入框可見性
        manualOption.addActionListener(evt -> {
            itemIdLabel.setVisible(true);
            itemIdField.setVisible(true);
            quantityLabel.setVisible(true);
            quantityField.setVisible(true);
            dialog.setSize(650, 280);
            dialog.revalidate();
            dialog.repaint();
        });
        allOption.addActionListener(evt -> {
            itemIdLabel.setVisible(false);
            itemIdField.setVisible(false);
            quantityLabel.setVisible(false);
            quantityField.setVisible(false);
            dialog.setSize(650, 280);
            dialog.revalidate();
            dialog.repaint();
        });

        cancelBtn.addActionListener(evt -> dialog.dispose());

        confirmBtn.addActionListener(evt -> {
            String inputOrderNumber = orderNumberField.getText().trim();
            if (inputOrderNumber.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "訂單號不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 驗證訂單是否存在（可選）
            Order order = frame.findActiveOrderByOrderNumber(inputOrderNumber);
            if (order == null) {
                JOptionPane.showMessageDialog(dialog, "未找到活躍訂單：" + inputOrderNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (manualOption.isSelected()) {
                String itemId = itemIdField.getText().trim();
                String quantityStr = quantityField.getText().trim();

                if (itemId.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "菜品编号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String[] itemIds = itemId.split(",");
                String[] quantityStrs = quantityStr.split(",");
                int[] quantities = new int[itemIds.length];

                boolean allValid = true;
                for (int i = 0; i < itemIds.length; i++) {
                    try {
                        int quantity = i < quantityStrs.length ? Integer.parseInt(quantityStrs[i].trim()) : 1;
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(dialog, "数量必须大于0！", "错误", JOptionPane.ERROR_MESSAGE);
                            allValid = false;
                            break;
                        }
                        quantities[i] = quantity;
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog, "请输入有效的数量（整数）！", "错误", JOptionPane.ERROR_MESSAGE);
                        allValid = false;
                        break;
                    }
                }
                if (!allValid) return;

                boolean anySuccess = false;
                for (int i = 0; i < itemIds.length; i++) {
                    String id = itemIds[i].trim().toUpperCase();
                    if (!id.isEmpty()) {
                        if (markTakeoutItemsAsReady(dialog, inputOrderNumber, id, quantities[i])) {
                            anySuccess = true;
                        }
                    }
                }

                if (anySuccess) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshTakeoutOrders();
                    JOptionPane.showMessageDialog(dialog, "部分或全部菜品已成功標記為製作完成。");
                } else {
                    JOptionPane.showMessageDialog(dialog, "未找到匹配的未製作菜品。", "提示", JOptionPane.INFORMATION_MESSAGE);
                }

            } else if (allOption.isSelected()) {
                if (markAllTakeoutItemsAsReady(dialog, inputOrderNumber)) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    refreshTakeoutOrders();
                }
            }
            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    /**
     * 部分標記外賣菜品為製作完成（View 層 - UI 驗證 + 事件轉發）
     */
    private boolean markTakeoutItemsAsReady(Component parentComponent, String orderNumber, String itemCode, int quantity) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "訂單號不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // View 層：itemCode → itemId
        com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode.trim().toUpperCase());
        if (menuItem == null) {
            JOptionPane.showMessageDialog(parentComponent, "菜品 " + itemCode + " 不存在或已停售", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int itemId = menuItem.getItemId();

        try {
            controller.handleMarkReadyForTakeout(orderNumber, itemId, quantity);
            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent, "標記製作完成失敗: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 一鍵標記外賣訂單所有菜品為製作完成
     */
    private boolean markAllTakeoutItemsAsReady(Component parentComponent, String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "訂單號不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            controller.handleMarkAllTakeoutItemsAsReady(orderNumber);
            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                JOptionPane.showMessageDialog(parentComponent, "已將訂單 " + orderNumber + " 的所有菜品標記為製作完成！", "成功", JOptionPane.INFORMATION_MESSAGE);
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent, "標記全部菜品失敗: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }


    /**
     * 🔧 询问撤销已上桌还是未上桌部分
     */
    private String askCancelPartDialog(Component parent, String itemCode, String itemName,
                                       int totalQty, int servedQty) {

//        // 🔧【Debug 1】方法入口：記錄調用參數
//        System.out.println("🔍 [DEBUG] askCancelPartDialog 被調用: " +
//                "itemCode=" + itemCode +
//                ", itemName=" + itemName +
//                ", totalQty=" + totalQty +
//                ", servedQty=" + servedQty);

        String[] options = {"① 撤销已上桌部分", "② 撤销未上桌部分"};

        int choice = JOptionPane.showOptionDialog(
                parent,
                "🍽️ 菜品 【" + itemName + "】(" + itemCode + ") 当前状态：\n" +
                        "已上桌 " + servedQty + " / 总计 " + totalQty + "\n\n" +
                        "请选择要撤销的部分：",
                "选择撤销范围",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
        );

        // 🔧【Debug 2】用戶選擇後：記錄 choice 值
//        System.out.println("🔍 [DEBUG] 用戶選擇: choice=" + choice);

        if (choice < 0) {
            // 🔧【Debug 3a】用戶取消
//            System.out.println("🔍 [DEBUG] 用戶取消選擇，返回 null");
            return null;
        }

        String result = choice == 0 ? "SERVED" : "UNSERVED";

        // 🔧【Debug 3b】記錄最終返回值
//        System.out.println("🔍 [DEBUG] 返回結果: " + result +
//                " (choice=" + choice + ")");

        return result;
    }


    /**
     * 統一撤銷菜品對話框（僅堂食需要撤銷原因，外賣不需要）
     * 🔧 修复：菜品编号比较时统一转大写，确保已上桌菜品能正确触发撤销原因弹窗
     * 🔧【新增】支持部分上桌菜品选择撤销"已上桌部分"或"未上桌部分"
     */

    private void showCancelOrderItemDialog() {
        // 1. 根據訂單類型獲取標識符 + 標籤文本
        String identifier;
        String idLabel;
        String dialogTitle;

        if (currentOrderType == OrderType.DINE_IN) {
            identifier = currentTableNumber;
            idLabel = "餐桌号:";
            dialogTitle = "撤銷堂食菜品";
        } else {
            identifier = currentTakeoutOrderNumber != null ?
                    currentTakeoutOrderNumber : tableNumberField.getText().trim();
            idLabel = "訂單號:";
            dialogTitle = "撤銷外賣菜品";
        }

        JFrame dialog = new JFrame(dialogTitle);
        dialog.setSize(650, 380);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== 輸入面板 =====
        JPanel inputPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        JLabel idLabelComp = new JLabel(idLabel);
        JTextField idField = new JTextField();
        JLabel itemIdLabel = new JLabel("菜品编号（用逗号分隔多个菜品 ID）:");
        JTextField itemIdField = new JTextField();
        JLabel quantityLabel = new JLabel("<html>撤銷數量（用逗號\",\"分隔；未填默認為 1）:</html>");
        JTextField quantityField = new JTextField("1");

        // 🔧【新增】菜品类型单选按钮面板（初始隐藏，确认时动态判断）
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        radioPanel.setBackground(new Color(245, 248, 255));

        JRadioButton sharedDishRadio = new JRadioButton("共同菜品（多桌共享）");
        JRadioButton singleTableRadio = new JRadioButton("单独桌菜品（单桌分配）");
        // 刪除該菜品在所有關聯桌子的記錄
        JRadioButton deleteFromAllGroupedTablesRadio = new JRadioButton("從所有關聯餐桌刪除");

        sharedDishRadio.setBackground(new Color(245, 248, 255));
        singleTableRadio.setBackground(new Color(245, 248, 255));
        deleteFromAllGroupedTablesRadio.setBackground(new Color(245, 248, 255));
        deleteFromAllGroupedTablesRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        sharedDishRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        singleTableRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        ButtonGroup dishTypeGroup = new ButtonGroup();
        dishTypeGroup.add(sharedDishRadio);
        dishTypeGroup.add(singleTableRadio);
        dishTypeGroup.add(deleteFromAllGroupedTablesRadio);
        singleTableRadio.setSelected(true);  // 默认选单独桌更安全

        radioPanel.add(sharedDishRadio);
        radioPanel.add(singleTableRadio);
        radioPanel.add(deleteFromAllGroupedTablesRadio);

        inputPanel.add(idLabelComp);
        inputPanel.add(idField);
        inputPanel.add(itemIdLabel);
        inputPanel.add(itemIdField);
        inputPanel.add(quantityLabel);
        inputPanel.add(quantityField);

        // 🔧 菜品类型行（默认隐藏，聚餐桌时显示）
        JLabel dishTypeLabel = new JLabel("菜品类型:");
        dishTypeLabel.setVisible(false);  //  默认隐藏
        radioPanel.setVisible(false);      //  默认隐藏
        inputPanel.add(dishTypeLabel);
        inputPanel.add(radioPanel);

        // 預填標識符（保持可編輯）
        if (identifier != null && !identifier.isEmpty() &&
                !"未选择".equals(identifier) && !"待下单".equals(identifier)) {
            idField.setText(identifier);
        }

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // ===== 按鈕面板 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton confirmBtn = new JButton("確認撤銷");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel, BorderLayout.CENTER);

        // ===== 取消按鈕 =====
        cancelBtn.addActionListener(evt -> dialog.dispose());

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】判断是否为聚餐桌，如果是则显示菜品类型选择
        // ═══════════════════════════════════════════════════════════
        if (currentOrderType == OrderType.DINE_IN) {
            Tables table = service.getTableById(identifier);  // 使用 identifier 而不是 inputId
            if (table != null && table.getTableType() == Tables.TableType.GROUPED) {
                dishTypeLabel.setVisible(true);
                radioPanel.setVisible(true);
                // System.out.println("🔧 检测到聚餐桌 #" + identifier + "，显示菜品类型选择");

                // 重新计算对话框高度
                dialog.pack();
                dialog.setSize(650, 530);  // 增加高度以容纳单选按钮
            }
        }

        // ===== 確認按鈕 =====
        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修改】確認按鈕事件處理器（整合聚餐桌專用邏輯 + 部分上桌选择撤销部分）
        // ═══════════════════════════════════════════════════════════
        confirmBtn.addActionListener(evt -> {
            // ═══════════════════════════════════════════════════════════
            // 【阶段 1】基础输入验证（保持不变）
            // ═══════════════════════════════════════════════════════════
            String inputId = idField.getText().trim();
            if (inputId.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, idLabel + "不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证订单/餐桌是否存在
            boolean valid = false;
            if (currentOrderType == OrderType.DINE_IN) {
                Tables table = service.getTableById(inputId);
                valid = (table != null && (table.getStatus() == Tables.TableStatus.OCCUPIED
                        || table.getStatus() == Tables.TableStatus.RESERVED));
            } else {
                if (inputId.startsWith("R")) {
                    TableReservation reservation = controller.getReservationDetail(inputId);
                    valid = (reservation != null);
                } else {
                    Order order = frame.findActiveOrderByOrderNumber(inputId);
                    valid = (order != null);
                }
            }
            if (!valid) {
                String typeName = currentOrderType == OrderType.DINE_IN ? "餐桌" :
                        (inputId.startsWith("R") ? "預約訂單" : "訂單");
                JOptionPane.showMessageDialog(dialog, "未找到有效" + typeName + "：" + inputId,
                        "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String itemId = itemIdField.getText().trim();
            String quantityStr = quantityField.getText().trim();
            if (itemId.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "菜品编号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String[] itemIds = itemId.split(",");
            String[] quantityStrs = quantityStr.split(",");
            int[] quantities = new int[itemIds.length];

            boolean allValid = true;
            for (int i = 0; i < itemIds.length; i++) {
                try {
                    int qty = i < quantityStrs.length ? Integer.parseInt(quantityStrs[i].trim()) : 1;
                    if (qty <= 0) {
                        JOptionPane.showMessageDialog(dialog, "数量必须大于 0！", "错误", JOptionPane.ERROR_MESSAGE);
                        allValid = false;
                        break;
                    }
                    quantities[i] = qty;
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(dialog, "请输入有效的数量（整数）！", "错误", JOptionPane.ERROR_MESSAGE);
                    allValid = false;
                    break;
                }
            }
            if (!allValid) return;

            // ═══════════════════════════════════════════════════════════
            // 【阶段 2】🔧 融合方案：分类型处理撤销原因收集
            // ═══════════════════════════════════════════════════════════

            // 全局变量：记录每个菜品的撤销部分 + 全局撤销原因
            Map<String, String> cancelPartMap = new HashMap<>();
            String globalCancellationReason = null;
            boolean needsGlobalReason = false;

            // 仅堂食订单需要处理撤销原因逻辑
            if (currentOrderType == OrderType.DINE_IN) {
                Tables table = service.getTableById(inputId);
                boolean isGroupedTable = (table != null &&
                        table.getTableType() == Tables.TableType.GROUPED);

                // ── 情况 A：普通餐桌 或 聚餐桌的「单桌模式」→ 提前收集 ──
                if (!isGroupedTable || singleTableRadio.isSelected()) {
                    List<OrderItem> orderItems = frame.loadFormalOrderItems(inputId);
                    if (orderItems != null) {
                        for (String id : itemIds) {
                            String code = id.trim().toUpperCase();
                            for (OrderItem item : orderItems) {
                                if (!code.equalsIgnoreCase(item.getItemCode())) continue;

                                // 单桌模式：只处理分配给当前餐桌的菜品
                                String assignedId = item.getAssignedTableDisplayId();
                                if (assignedId != null && !inputId.equals(assignedId.trim())) {
                                    continue;
                                }

                                String status = item.getStatus();
                                if ("PARTIALLY_SERVED".equals(status)) {
                                    // 部分上桌：弹窗询问撤销哪部分
                                    String part = askCancelPartDialog(dialog, code,
                                            item.getItemName(), item.getQuantity(),
                                            item.getServedQuantity());
                                    if (part == null) return;  // 用户取消
                                    cancelPartMap.put(code, part);
                                    needsGlobalReason = true;
                                } else if ("SERVED".equals(status)) {
                                    // 全部上桌：默认撤销已上桌部分
                                    cancelPartMap.put(code, "SERVED");
                                    needsGlobalReason = true;
                                } else {
                                    // 未上桌菜品：无需原因
                                    cancelPartMap.put(code, "UNSERVED");
                                }
                                break;  // 找到匹配项后跳出
                            }
                        }
                    }

                    // 🔹 提前询问撤销原因（普通餐桌逻辑简单，可以提前问）
                    if (needsGlobalReason) {
                        globalCancellationReason = JOptionPane.showInputDialog(dialog,
                                "<html><b>⚠️ 菜品已上桌</b><br><br>" +
                                        "请输入撤销原因（必填）：</html>",
                                "需要撤销原因", JOptionPane.WARNING_MESSAGE);
                        if (globalCancellationReason == null ||
                                globalCancellationReason.trim().isEmpty()) {
                            JOptionPane.showMessageDialog(dialog,
                                    "撤销已上桌菜品必须提供原因",
                                    "输入错误", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        globalCancellationReason = globalCancellationReason.trim();
                    }
                }
                // ── 情况 B：聚餐桌的「共同菜品/删除所有」模式 → 延迟处理 ──
                // 不提前收集 cancelPart，等用户选择具体记录后再检查
                // （在遍历菜品执行时处理）
            }

            // ═══════════════════════════════════════════════════════════
            // 【阶段 3】🔧 遍历菜品执行撤销
            // ═══════════════════════════════════════════════════════════
            boolean anySuccess = false;

            for (int i = 0; i < itemIds.length; i++) {
                String code = itemIds[i].trim().toUpperCase();
                if (code.isEmpty()) continue;

                boolean isGroupedTableCancel = false;

                if (currentOrderType == OrderType.DINE_IN) {
                    Tables table = service.getTableById(inputId);
                    if (table != null && table.getTableType() == Tables.TableType.GROUPED) {

                        // ── 单桌模式：使用提前收集的 cancelPart ──
                        if (singleTableRadio.isSelected()) {
                            List<OrderItem> orderItems = frame.loadFormalOrderItems(inputId);
                            OrderItem targetItem = null;
                            for (OrderItem item : orderItems) {
                                if (code.equalsIgnoreCase(item.getItemCode()) &&
                                        inputId.equals(item.getAssignedTableDisplayId())) {
                                    targetItem = item;
                                    break;
                                }
                            }
                            if (targetItem == null) {
                                JOptionPane.showMessageDialog(dialog,
                                        "未找到餐桌 #" + inputId + " 的单独分配菜品：" + code +
                                                "\n\n💡 提示：该菜品可能是【多桌共享】类型，请切换为「共同菜品」模式",
                                        "未找到匹配菜品", JOptionPane.WARNING_MESSAGE);
                                continue;
                            }

                            // 🔹 使用提前收集的 cancelPart 和 reason
                            String cancelPart = cancelPartMap.getOrDefault(code, "UNSERVED");
                            String finalReason = globalCancellationReason;

                            // 🔹 兜底：如果提前没问原因但实际需要，此时再问
                            if (("SERVED".equals(cancelPart) || "PARTIALLY_SERVED".equals(cancelPart)) &&
                                    (finalReason == null || finalReason.isEmpty())) {
                                finalReason = JOptionPane.showInputDialog(dialog,
                                        "<html><b>⚠️ 菜品已上桌</b><br><br>" +
                                                "菜品：<b>" + targetItem.getItemName() + "</b> (" + code + ")<br>" +
                                                "当前状态：<font color='#4caf50'>" +
                                                ("SERVED".equals(targetItem.getStatus()) ? "✅ 已全部上桌" : "🟠 部分上桌") +
                                                "</font><br><br>" +
                                                "<font color='#d32f2f'>请输入撤销原因（必填）：</font></html>",
                                        "需要撤销原因",
                                        JOptionPane.WARNING_MESSAGE);
                                if (finalReason == null || finalReason.trim().isEmpty()) return;
                            }

                            try {
                                controller.handleCancelGroupedTableOrderItemSmart(
                                        inputId,
                                        targetItem.getOrderItemId(),
                                        quantities[i],
                                        finalReason != null ? finalReason : "用户撤销",
                                        cancelPart);
                                anySuccess = true;
                                isGroupedTableCancel = true;

                                System.out.println("🗑️ 聚餐桌单桌菜品撤销成功: " +
                                        "code=" + code +
                                        ", table=" + inputId +
                                        ", orderItemId=" + targetItem.getOrderItemId() +
                                        ", cancelPart=" + cancelPart);

                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(dialog, "撤销失败：" + e.getMessage(),
                                        "错误", JOptionPane.ERROR_MESSAGE);
                                e.printStackTrace();
                            }
                        }

                        // ── 共同菜品/删除所有模式：延迟检查撤销原因 ──
                        else if (sharedDishRadio.isSelected() || deleteFromAllGroupedTablesRadio.isSelected()) {
                            // 1️ 先收集匹配的订单项
                            List<OrderItem> matchedItems = new ArrayList<>();
                            List<OrderItem> orderItems = frame.loadFormalOrderItems(inputId);
                            for (OrderItem item : orderItems) {
                                if (code.equalsIgnoreCase(item.getItemCode())) {
                                    String assignedId = item.getAssignedTableDisplayId();
                                    boolean isShared = (assignedId != null &&
                                            assignedId.split(",").length > 1);
                                    if ((sharedDishRadio.isSelected() && isShared) ||
                                            (deleteFromAllGroupedTablesRadio.isSelected())) {
                                        if (assignedId != null &&
                                                containsTable(assignedId.split(","), inputId)) {
                                            matchedItems.add(item);
                                        }
                                    }
                                }
                            }
                            if (matchedItems.isEmpty()) {
                                JOptionPane.showMessageDialog(dialog,
                                        "未找到餐桌 #" + inputId + " 的匹配菜品：" + code,
                                        "未找到匹配菜品", JOptionPane.WARNING_MESSAGE);
                                continue;
                            }

                            // 2️ 如果需要，弹出选择对话框（用户可能取消）
                            OrderItem targetItem = null;
                            if (matchedItems.size() == 1) {
                                // 只有一个匹配项，直接使用
                                targetItem = matchedItems.get(0);
                            } else {
                                // 多个匹配项，弹出选择对话框
                                List<OrderItem> selectedItems = showGroupedTableCancelSelectionDialog(
                                        dialog, inputId, code, quantities[i], matchedItems);

                                if (selectedItems == null || selectedItems.isEmpty()) {
                                    continue;  // 用户取消选择
                                }

                                // 🔧 取第一个选中的项目（或者根据需要处理多个）
                                targetItem = selectedItems.get(0);

                                // 如果需要处理多个选中的项目，可以遍历 selectedItems
                                // for (OrderItem item : selectedItems) { ... }
                            }

                            if (targetItem == null) {
                                continue;  // 没有选中任何项目
                            }

                            // 3️【关键】获取到 targetItem 后，再检查撤销原因
                            String cancelPart = null;
                            String itemStatus = targetItem.getStatus();
                            if ("PARTIALLY_SERVED".equals(itemStatus)) {
                                cancelPart = askCancelPartDialog(dialog, code,
                                        targetItem.getItemName(), targetItem.getQuantity(),
                                        targetItem.getServedQuantity());
                                if (cancelPart == null) continue;  // 用户取消
                            } else if ("SERVED".equals(itemStatus)) {
                                cancelPart = "SERVED";
                            } else {
                                cancelPart = "UNSERVED";
                            }

                            //  如果需要原因且还没提供，此时才弹窗
                            String finalReason = globalCancellationReason;
                            if (("SERVED".equals(cancelPart) || "PARTIALLY_SERVED".equals(cancelPart)) &&
                                    (finalReason == null || finalReason.isEmpty())) {
                                finalReason = JOptionPane.showInputDialog(dialog,
                                        "<html><b>⚠️ 菜品已上桌</b><br><br>" +
                                                "菜品：<b>" + targetItem.getItemName() + "</b> (" + code + ")<br>" +
                                                "<font color='#d32f2f'>请输入撤销原因（必填）：</font></html>",
                                        "需要撤销原因", JOptionPane.WARNING_MESSAGE);
                                if (finalReason == null || finalReason.trim().isEmpty()) return;
                            }

                            // 4️ 执行撤销（根据模式调用不同方法）
                            try {
                                if (sharedDishRadio.isSelected()) {
                                    // 🔧 共同菜品撤销：智能更新 quantity/served_quantity/status/distribution
                                    int currentQty = targetItem.getQuantity();
                                    int currentServedQty = targetItem.getServedQuantity();
                                    int cancelQty = quantities[i];

                                    if (cancelQty > currentQty) {
                                        JOptionPane.showMessageDialog(dialog,
                                                "撤销数量不能超过当前数量！\n当前：" + currentQty + " 份",
                                                "输入错误", JOptionPane.ERROR_MESSAGE);
                                        continue;
                                    }

                                    int newQty = Math.max(0, currentQty - cancelQty);
                                    int newServedQty = Math.max(0, currentServedQty - cancelQty);
                                    newServedQty = Math.min(newServedQty, newQty);
                                    String newStatus = calculateStatusAfterCancel(newQty, newServedQty);

                                    // 处理 assigned_table_display_id 和 quantity_distribution
                                    String newAssignedTableIds = targetItem.getAssignedTableDisplayId();
                                    String newDistribution = null;
                                    String currentDistribution = targetItem.getQuantityDistribution();

                                    if (currentDistribution != null && !currentDistribution.isEmpty()) {
                                        Map<String, Integer> distMap = parseDistribution(currentDistribution);
                                        if (distMap != null && !distMap.isEmpty()) {
                                            Integer currentAlloc = distMap.get(inputId);
                                            if (currentAlloc != null) {
                                                int newAlloc = currentAlloc - cancelQty;
                                                if (newAlloc <= 0) {
                                                    distMap.remove(inputId);
                                                    newAssignedTableIds = removeTableFromList(newAssignedTableIds, inputId);
                                                } else {
                                                    distMap.put(inputId, newAlloc);
                                                }
                                            }
                                            if (!distMap.isEmpty()) {
                                                newDistribution = formatDistribution(distMap);
                                            } else {
                                                newAssignedTableIds = removeTableFromList(newAssignedTableIds, inputId);
                                            }
                                        }
                                    } else {
                                        newAssignedTableIds = removeTableFromList(newAssignedTableIds, inputId);
                                    }

                                    // 处理 served_table_display_id
                                    String newServedTableIds = targetItem.getServedTableDisplayId();
                                    if (newServedTableIds != null && !newServedTableIds.isEmpty() &&
                                            newServedTableIds.contains(inputId)) {
                                        if (newServedQty <= 0) {
                                            newServedTableIds = removeTableFromList(newServedTableIds, inputId);
                                        }
                                    }

                                    // 调用 Controller 执行撤销
                                    controller.handleCancelSharedDishOrderItem(
                                            targetItem.getOrderItemId(),
                                            cancelQty,
                                            newQty,
                                            newServedQty,
                                            newStatus,
                                            newAssignedTableIds,
                                            newDistribution,
                                            finalReason != null ? finalReason : "用户撤销",
                                            cancelPart
                                    );

                                } else {
                                    // 🔧 删除所有关联餐桌的菜品：直接物理删除整条记录
                                    int currentQty = targetItem.getQuantity();
                                    int currentServedQty = targetItem.getServedQuantity();

                                    // 记录撤销审计日志（仅当有已上桌数量时）
                                    if (currentServedQty > 0) {
                                        double cancelledAmount = targetItem.getPriceAtOrder() * currentQty;
                                        controller.getOrderService().recordCancellation(
                                                "ITEM",
                                                targetItem.getOrderId(),
                                                null,
                                                targetItem.getItemCode(),
                                                currentQty,
                                                itemStatus,
                                                cancelledAmount,
                                                finalReason != null ? finalReason : "用户撤销"
                                        );
                                    }

                                    // 直接物理删除整条记录
                                    int deleted = controller.getOrderService().deleteOrderItemByOrderItemId(
                                            targetItem.getOrderItemId()
                                    );
                                    if (deleted <= 0) {
                                        throw new RuntimeException("删除失败：orderItemId=" + targetItem.getOrderItemId());
                                    }
                                }

                                anySuccess = true;
                                isGroupedTableCancel = true;

                                System.out.println("🗑️ 聚餐桌菜品撤销成功: " +
                                        "code=" + code +
                                        ", table=" + inputId +
                                        ", orderItemId=" + targetItem.getOrderItemId() +
                                        ", cancelPart=" + cancelPart);

                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(dialog, "撤销失败：" + e.getMessage(),
                                        "错误", JOptionPane.ERROR_MESSAGE);
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // ── 普通餐桌：使用提前收集的参数 ──
                if (!isGroupedTableCancel) {
                    String cancelPart = cancelPartMap.getOrDefault(code, null);
                    if (cancelOrderItemLogic(dialog, inputId, code, quantities[i],
                            globalCancellationReason, cancelPart)) {
                        anySuccess = true;
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 【阶段 4】结果反馈 + 刷新界面
            // ═══════════════════════════════════════════════════════════
            if (anySuccess) {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                if (currentOrderType == OrderType.DINE_IN) {
                    refreshDineInOrders();
                } else {
                    refreshTakeoutOrders();
                }
                JOptionPane.showMessageDialog(dialog, "部分或全部菜品已成功撤銷。");
            } else {
                JOptionPane.showMessageDialog(dialog, "未找到匹配的菜品或數量不足。",
                        "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            dialog.dispose();
        });

        dialog.setVisible(true);
    }



    /**
     * 🔧 显示聚餐桌撤销选择对话框（多个相同菜品记录时）
     *
     * @param parent      父窗口
     * @param tableNumber 餐桌号
     * @param itemCode    菜品编号
     * @param cancelQty   撤销数量
     * @param items       相同菜品的记录列表
     * @return 用户选择的要撤销的记录列表，取消则返回 null
     */
    private List<OrderItem> showGroupedTableCancelSelectionDialog(
            JFrame parent, String tableNumber, String itemCode, int cancelQty, List<OrderItem> items) {

        JDialog selectionDialog = new JDialog(parent, " 选择要撤销的菜品记录", true);
        selectionDialog.setSize(900, 500);
        selectionDialog.setLocationRelativeTo(parent);
        selectionDialog.getContentPane().setBackground(new Color(245, 248, 250));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(245, 248, 250));

        // 标题
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(new Color(245, 248, 250));
        JLabel titleLabel = new JLabel("<html><h2 style='color:#d32f2f;margin:0;'> 选择要撤销的菜品记录</h2></html>");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        titlePanel.add(titleLabel);

        JLabel hintLabel = new JLabel("<html><span style='color:#666;font-size:12px;'>" +
                "菜品：<b>" + itemCode + "</b>，撤销数量：<b>" + cancelQty + "</b> 份/记录</span></html>");
        titlePanel.add(hintLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 表格
        String[] columnNames = {"选择", "菜品", "总数", "已上", "状态", "分配餐桌", "实际上菜", "记录 ID"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        Map<Integer, OrderItem> rowToItemMap = new HashMap<>();
        int rowIndex = 0;
        for (OrderItem item : items) {
            String statusText = getStatusText(item.getStatus());
            String assignedTables = item.getAssignedTableDisplayId() != null ?
                    item.getAssignedTableDisplayId() : "-";
            String servedTables = item.getServedTableDisplayId() != null ?
                    item.getServedTableDisplayId() : "-";

            Object[] row = {
                    // 改为 false，默认不勾选
                    false, item.getItemName(), item.getQuantity(),
                    item.getServedQuantity(), statusText,
                    assignedTables, servedTables, "#" + item.getOrderItemId()
            };
            tableModel.addRow(row);
            rowToItemMap.put(rowIndex++, item);
        }

        JTable itemTable = new JTable(tableModel);
        itemTable.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        itemTable.setRowHeight(35);
        itemTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // 列宽设置
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        itemTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        itemTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        itemTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        itemTable.getColumnModel().getColumn(6).setPreferredWidth(150);
        itemTable.getColumnModel().getColumn(7).setPreferredWidth(80);

        // 单元格渲染器（状态颜色）
        itemTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                OrderItem item = rowToItemMap.get(row);

                if (!isSelected) {
                    String status = item.getStatus();
                    if ("SERVED".equals(status)) {
                        c.setBackground(new Color(232, 245, 233));
                    } else if ("PARTIALLY_SERVED".equals(status)) {
                        c.setBackground(new Color(255, 251, 235));
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }
                setHorizontalAlignment(column == 0 || column == 2 || column == 3 || column == 4 || column == 7 ?
                        SwingConstants.CENTER : SwingConstants.LEFT);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(itemTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 210, 220), 1));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        JButton confirmBtn = new JButton("✓ 确认撤销");
        JButton cancelBtn = new JButton("✗ 取消");
        confirmBtn.setBackground(new Color(244, 67, 54));
        confirmBtn.setForeground(Color.WHITE);
        cancelBtn.setBackground(new Color(158, 158, 158));
        cancelBtn.setForeground(Color.WHITE);

        final List<OrderItem>[] selectedItems = new List[1];

        cancelBtn.addActionListener(e -> {
            selectedItems[0] = null;
            selectionDialog.dispose();
        });

        confirmBtn.addActionListener(e -> {
            List<OrderItem> result = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
                if (Boolean.TRUE.equals(selected)) {
                    result.add(rowToItemMap.get(i));
                }
            }
            if (result.isEmpty()) {
                JOptionPane.showMessageDialog(selectionDialog, "⚠️ 请至少选择一条记录！", "提示",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedItems[0] = result;
            selectionDialog.dispose();
        });

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        selectionDialog.add(mainPanel, BorderLayout.CENTER);
        selectionDialog.add(buttonPanel, BorderLayout.SOUTH);
        selectionDialog.setVisible(true);

        return selectedItems[0];
    }

    /**
     * 撤銷菜品核心邏輯（根據類型調用不同 Controller 方法）
     * 🔧【新增】支持传入 cancelPart 参数（部分上桌时选择撤销哪部分）
     *
     * @param cancellationReason 撤銷原因（堂食可能需要，外賣=null）
     * @param cancelPart         撤销部分："SERVED"=已上桌, "UNSERVED"=未上桌, null=默认逻辑
     */
    private boolean cancelOrderItemLogic(Component parent, String identifier, String itemCode,
                                         int quantity, String cancellationReason, String cancelPart) {
        // UI 验证：itemCode → itemId
        com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode);
        if (menuItem == null) {
            JOptionPane.showMessageDialog(parent, "菜品 " + itemCode + " 不存在或已停售", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int itemId = menuItem.getItemId();

        try {
            // ═══════════════════════════════════════════════════════════
            // 🔧【新增】检查是否为聚餐桌预约订单，验证撤销数量（保持原有逻辑）
            // ═══════════════════════════════════════════════════════════
            if (currentOrderType == OrderType.RESERVATION && identifier.startsWith("R")) {
                // 1. 查询预约详情
                TableReservation reservation = controller.getReservationDetail(identifier);
                if (reservation != null && "GROUP".equals(reservation.getGroupType())) {
                    // 2. 解析桌子数量
                    int tableCount = frame.parseTableCountFromConfig(reservation.getTableConfigDesc());

                    // 3. 查询当前订单中该菜品的总数量
                    Order order = frame.findPreOrderByReservationId(identifier);
                    if (order != null) {
                        List<OrderItem> orderItems = frame.loadFormalOrderItemsByReservationId(identifier);
                        for (OrderItem item : orderItems) {
                            if (item.getItemCode().equalsIgnoreCase(itemCode)) {
                                int currentTotalQty = item.getQuantity();

                                // 4. 验证：当前总数量必须是桌子数量的倍数
                                if (currentTotalQty % tableCount != 0) {
                                    JOptionPane.showMessageDialog(parent,
                                            "<html> 数据异常！<br>" +
                                                    "聚餐桌菜品总数量（" + currentTotalQty + "）不是桌子数量（" + tableCount + "）的倍数。<br>" +
                                                    "每张桌子应该分配 " + (currentTotalQty / tableCount) + " 份菜品。</html>",
                                            "数量校验失败", JOptionPane.WARNING_MESSAGE);
                                    return false;
                                }

                                // 5. 验证：撤销数量必须是桌子数量的倍数
                                if (quantity % tableCount != 0) {
                                    JOptionPane.showMessageDialog(parent,
                                            "<html>⚠️ 撤销数量必须是桌子数量的倍数！<br><br>" +
                                                    "桌子数量：<b>" + tableCount + " 张</b><br>" +
                                                    "当前菜品总数：<b>" + currentTotalQty + " 份</b>（每张桌 " + (currentTotalQty / tableCount) + " 份）<br>" +
                                                    "<font color='red'><b>💡 建议撤销数量</b>：应为 " + tableCount + " 的倍数</font><br>" +
                                                    "（例如：" + tableCount + "、" + (tableCount * 2) + "、" + (tableCount * 3) + " 份...）<br>" +
                                                    "<small>这样可以保证每张桌子撤销相同数量的菜品</small>",
                                            "撤销数量校验失败", JOptionPane.ERROR_MESSAGE);
                                    return false;
                                }

                                // 6. 验证：撤销后剩余数量也必须是桌子数量的倍数
                                int remainingQty = currentTotalQty - quantity;
                                if (remainingQty % tableCount != 0) {
                                    JOptionPane.showMessageDialog(parent,
                                            "<html>⚠️ 撤销后剩余数量不是桌子数量的倍数！<br><br>" +
                                                    "桌子数量：<b>" + tableCount + " 张</b><br>" +
                                                    "当前总数：<b>" + currentTotalQty + " 份</b><br>" +
                                                    "撤销数量：<b>" + quantity + " 份</b><br>" +
                                                    "剩余数量：<b>" + remainingQty + " 份</b><br><br>" +
                                                    "<font color='red'>剩余数量无法平均分配给 " + tableCount + " 张桌子！</font></html>",
                                            "撤销后数量分配失败", JOptionPane.ERROR_MESSAGE);
                                    return false;
                                }

                                System.out.println(" 聚餐桌撤销验证通过 - 桌子数：" + tableCount +
                                        ", 撤销数量：" + quantity +
                                        ", 剩余数量：" + remainingQty +
                                        "（每张桌 " + (remainingQty / tableCount) + " 份）");
                                break;
                            }
                        }
                    }
                }
            }

            if (currentOrderType == OrderType.DINE_IN) {
                // 堂食：通过餐桌号撤销（需要原因）
                // 🔧 传入 cancelPart 参数
                controller.handleCancelOrderItem(identifier, itemCode, quantity,
                        cancellationReason != null ? cancellationReason : "用户撤销", cancelPart);
            }
            // 🔧【修复】预约订单：通过 reservation_id 撤销
            else if (currentOrderType == OrderType.RESERVATION) {
                // 1. 查找预点餐订单
                Order preOrder = frame.findPreOrderByReservationId(identifier);
                if (preOrder == null) {
                    JOptionPane.showMessageDialog(parent,
                            "未找到预约号 " + identifier + " 的预点餐订单",
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                // 🔧【关键修复】通过 frame 调用预约订单撤销方法
                frame.cancelReservationOrderItem(
                        identifier,  // reservation_id
                        itemId,
                        quantity,
                        cancellationReason != null ? cancellationReason : "用户撤销",
                        cancelPart                               // 🔧 新增：是否为部分撤销
                );
                System.out.println("预约订单菜品撤销成功: reservationId=" + identifier +
                        ", orderId=" + preOrder.getOrderId());
            } else {
                // 外卖：通过订单号撤销（原因可选，传 null 或默认值）
                controller.handleCancelTakeoutOrderItem(identifier, itemId, quantity,
                        cancellationReason != null ? cancellationReason : "用户撤销");
            }

            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "撤销菜品失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 撤销整个外卖订单对话框（仅外卖）
     */
    private void handleCancelTakeoutOrder() {
        String orderNumber = currentTakeoutOrderNumber != null ?
                currentTakeoutOrderNumber : tableNumberField.getText().trim();

        if (orderNumber == null || orderNumber.isEmpty() || "待下单".equals(orderNumber)) {
            JOptionPane.showMessageDialog(this, "请先确认外卖订单号！", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 验证订单是否存在
        Order order = frame.findActiveOrderByOrderNumber(orderNumber);
        if (order == null) {
            JOptionPane.showMessageDialog(this, "未找到活跃订单：" + orderNumber, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "确认要撤销整个外卖订单 " + orderNumber + " 吗？\n" +
                        "此操作将删除所有菜品明细和订单记录，不可恢复！",
                "整单撤销确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        // 可选：询问撤销原因
        String reason = JOptionPane.showInputDialog(
                this,
                "请输入整单撤销原因（如：顾客取消、忘记取餐等）:",
                "撤销原因",
                JOptionPane.QUESTION_MESSAGE
        );
        if (reason == null || reason.trim().isEmpty()) {
            reason = "顾客取消订单";
        }

        try {
            controller.handleCancelTakeoutOrder(orderNumber, reason.trim());
            refreshTemporaryOrderDisplay();
            refreshFormalOrderDisplay();
            refreshTakeoutOrders();
            JOptionPane.showMessageDialog(this, "整单撤销成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "整单撤销失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void handleDeliveryOrder() {
        // ===== 步骤1: 获取订单号（输入框 > 全局变量 > 弹窗输入）=====
        String orderNumber = getInitialOrderNumber();

        // 如果为空/不完整，弹出输入框（预填默认前缀）
        if (orderNumber == null || orderNumber.isEmpty() ||
                "待下单".equals(orderNumber) || !isValidOrderNumber(orderNumber)) {
            orderNumber = promptForOrderNumberWithDefaultPrefix();
            if (orderNumber == null || orderNumber.isEmpty()) return;  // 用户取消
        }

        // ===== 步骤2: 验证订单是否存在 =====
        Order order = frame.findActiveOrderByOrderNumber(orderNumber);
        if (order == null) {
            JOptionPane.showMessageDialog(HomePanel.this,
                    "未找到活跃订单: " + orderNumber,
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ===== 步骤3: 验证是否为配送订单 =====
        if (!"DELIVERY".equals(order.getDeliveryMethod())) {
            JOptionPane.showMessageDialog(HomePanel.this,
                    "订单 " + orderNumber + " 不是配送订单",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ===== 步骤4: 弹出状态选择对话框 =====
        Order.DeliveryStatus currentStatus = order.getDeliveryStatus();
        String[] options = {
                "🔴 " + Order.DeliveryStatus.NOT_DELIVERED.getDisplayName(),
                "🟠 " + Order.DeliveryStatus.DELIVERING.getDisplayName(),
                "🟢 " + Order.DeliveryStatus.DELIVERED.getDisplayName()
        };

        int selected = JOptionPane.showOptionDialog(
                HomePanel.this,
                "当前状态: " + currentStatus.getDisplayName() + "\n\n请选择新配送状态:",
                "更新配送状态 - " + orderNumber,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[currentStatus.ordinal()]);

        if (selected < 0) return;  // 用户取消

        Order.DeliveryStatus newStatus = switch (selected) {
            case 0 -> Order.DeliveryStatus.NOT_DELIVERED;
            case 1 -> Order.DeliveryStatus.DELIVERING;
            case 2 -> Order.DeliveryStatus.DELIVERED;
            default -> currentStatus;
        };

        // ===== 步骤5: 状态顺序校验（不能跳过"送单中"）=====
        if (newStatus == Order.DeliveryStatus.DELIVERED &&
                currentStatus != Order.DeliveryStatus.DELIVERING) {
            JOptionPane.showMessageDialog(HomePanel.this,
                    "⚠️ 不能跳过【送单中】状态！",
                    "状态顺序错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ===== 步骤6: 切换到"送单中"时检查制作状态 =====
        if (newStatus == Order.DeliveryStatus.DELIVERING) {
            String orderStatusText = frame.getOrderStatusDisplayByOrderNumber(orderNumber);
            if (!orderStatusText.contains("制作完成")) {
                int makeConfirm = JOptionPane.showConfirmDialog(
                        HomePanel.this,
                        "⚠️ 该订单尚未制作完成！\n当前状态：" + orderStatusText.replace("订单情况：", "") +
                                "\n\n是否立即标记为【制作完成】并开始送单？",
                        "制作未完成",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (makeConfirm == JOptionPane.YES_OPTION) {
                    try {
                        controller.handleMarkAllTakeoutItemsAsReady(orderNumber);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(HomePanel.this,
                                "标记制作完成失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    return;  // 用户取消
                }
            }
        }

        // ===== 步骤7: 最终确认并执行更新 =====
        int confirm = JOptionPane.showConfirmDialog(
                HomePanel.this,
                "确认将订单 " + orderNumber + " 的配送状态更新为:\n【" + newStatus.getDisplayName() + "】？",
                "确认更新",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            controller.handleUpdateDeliveryStatus(orderNumber, newStatus);
            refreshTakeoutOrders();
            refreshFormalOrderDisplay();
            JOptionPane.showMessageDialog(HomePanel.this,
                    "配送状态已更新: " + newStatus.getDisplayName(),
                    "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(HomePanel.this,
                    "更新失败: " + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // ===== 辅助方法1: 获取初始订单号 =====
    private String getInitialOrderNumber() {
        if (tableNumberField != null) {
            String input = tableNumberField.getText().trim();
            if (!input.isEmpty() && !"待下单".equals(input)) return input;
        }
        if (currentTakeoutOrderNumber != null &&
                !currentTakeoutOrderNumber.isEmpty() &&
                !"待下单".equals(currentTakeoutOrderNumber)) {
            return currentTakeoutOrderNumber;
        }
        return null;
    }

    // ===== 辅助方法2: 验证订单号格式是否完整 =====
    private boolean isValidOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isEmpty()) return false;
        return orderNumber.matches("^D-\\d{8}-\\d+$");  // D-20260313-1
    }

    // ===== 辅助方法3: 弹出输入框（预填默认前缀）=====
    private String promptForOrderNumberWithDefaultPrefix() {
        String defaultPrefix = "D-" + java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";

        JTextField inputField = new JTextField(defaultPrefix, 20);
        inputField.setCaretPosition(defaultPrefix.length());  // 光标定位到前缀后

        int result = JOptionPane.showConfirmDialog(
                HomePanel.this,
                new Object[]{
                        "请输入配送订单号：",
                        "(格式: " + defaultPrefix + "xxx)",
                        inputField
                },
                "输入订单号",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String input = inputField.getText().trim();
            if (!input.isEmpty() && !"待下单".equals(input)) {
                return input;  //  直接返回，不设置全局变量
            }
        }
        return null;
    }


    private void handleCancelReorder() {
        JDialog dialog = new JDialog(frame, "取消重新点餐", true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 180);
        dialog.setLocationRelativeTo(this);

        // 表单面板
        JPanel formPanel = new JPanel(new BorderLayout(10, 0));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        JLabel label = new JLabel("餐桌号: *", SwingConstants.LEFT);
        label.setPreferredSize(new Dimension(80, 25));

        JTextField tableField = new JTextField(25);
        if (currentTableNumber != null && !"未选择".equals(currentTableNumber)) {
            tableField.setText(currentTableNumber);
        }

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(label, BorderLayout.WEST);
        inputPanel.add(tableField, BorderLayout.CENTER);
        formPanel.add(inputPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton confirmBtn = new JButton("确认恢复");
        JButton cancelBtn = new JButton("取消");

        confirmBtn.addActionListener(ev -> {
            String tableNumber = tableField.getText().trim();

            // ===== 基础验证（保持在前端）=====
            if (tableNumber.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入餐桌号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Tables table = service.getTableById(tableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(dialog, "未找到餐桌 #" + tableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                JOptionPane.showMessageDialog(dialog,
                        "餐桌 " + tableNumber + " 当前状态为【" + table.getStatus() + "】，不能操作",
                        "无效操作", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
                return;
            }

            // ===== 调用 Controller（不抛异常，直接显示结果）=====
            Map<String, Object> result = controller.handleCancelReorder(tableNumber);

            // ===== 根据结果显示对话框 =====
            boolean success = (Boolean) result.get("success");
            String message = (String) result.get("message");

            if (success) {
                //  成功：刷新界面 + 提示
                dialog.dispose();
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                refreshDineInOrders();

//                frame.refreshAllPanels();
//                controller.refreshOrderStatusOnly();

                JOptionPane.showMessageDialog(
                        frame,
                        message,  // "餐桌 #7 已恢复为已结账状态"
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                //  失败：仅提示，不刷新
                JOptionPane.showMessageDialog(
                        dialog,
                        message,  // 具体的错误信息
                        "操作失败",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showPrepareOrderItemDialog() {
        // ═══════════════════════════════════════════════════════════
        // 【步骤 1】验证订单类型
        // ═══════════════════════════════════════════════════════════
        if (currentOrderType == null) {
            JOptionPane.showMessageDialog(this, "订单类型未初始化", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String reservationId = null;

        // ═══════════════════════════════════════════════════════════
        // 【步骤 2】根据订单类型分支验证，并解析出正确的 reservationId
        // ═══════════════════════════════════════════════════════════
        if (currentOrderType == OrderType.RESERVATION) {
            // ── 预约模式：当前输入的就是预约号 ──
            if (currentTableNumber == null || currentTableNumber.isEmpty() || "待确认".equals(currentTableNumber)) {
                JOptionPane.showMessageDialog(this,
                        "请先确认预约号！\n" +
                                "操作流程：选择【预约】→ 输入预约号 → 点击【确认预约号】",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            reservationId = currentTableNumber; // 直接使用输入的预约号
        } else if (currentOrderType == OrderType.DINE_IN) {
            // ── 堂食模式：当前输入的是餐桌号，需反查 reservation_id ──
            if (currentTableNumber == null || currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
                JOptionPane.showMessageDialog(this,
                        "请先输入餐桌号！",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 🔧【核心】通过餐桌号查询餐桌实体，获取关联的 reservation_id
            Tables table = service.getTableById(currentTableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(this,
                        "未找到餐桌：" + currentTableNumber,
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 🔧 关联 table_orders / restaurant_tables 中的 reservation_id
            reservationId = table.getCurrentReservationId();
            if (reservationId == null || reservationId.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "该餐桌未关联预约订单！\n" +
                                "餐桌号：" + currentTableNumber + "\n" +
                                "提示：此功能仅用于处理已入座且关联了预约记录的餐桌。",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } else {
            // 外卖/自取不支持标记准备状态
            JOptionPane.showMessageDialog(this,
                    "标记准备状态仅支持【堂食】或【预约】订单！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
// ═══════════════════════════════════════════════════════════
// 【步骤 2.5】🔧 创建 final 副本（关键修复！）
// ═══════════════════════════════════════════════════════════
        final String finalReservationId = reservationId;

        // ═══════════════════════════════════════════════════════════
        // 【步骤 2.6】🔧【新增】验证预约时间是否为当天（核心修复！）
        // ═══════════════════════════════════════════════════════════
        TableReservation reservation = controller.getReservationDetail(finalReservationId);
        if (reservation != null && reservation.getReservationTime() != null) {
            LocalDate reservationDate = reservation.getReservationTime().toLocalDate();
            LocalDate today = LocalDate.now();

            if (!reservationDate.isEqual(today)) {
                JOptionPane.showMessageDialog(this,
                        "<html><b>⚠️ 只能准备当天的预约菜品！</b><br><br>" +
                                "📅 预约日期：<font color='#1976d2'>" + reservationDate + "</font><br>" +
                                "📅 当前日期：<font color='#1976d2'>" + today + "</font><br><br>" +
                                "请在预约当天再进行菜品准备操作。</html>",
                        "日期限制",
                        JOptionPane.WARNING_MESSAGE);
                return;  // 🔑 关键：直接返回，阻止后续操作
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 3】创建对话框
        // ═══════════════════════════════════════════════════════════
        String dialogTitle = (currentOrderType == OrderType.RESERVATION)
                ? "🍳 标记菜品准备状态 - 预约号: " + reservationId
                : "🍳 标记菜品准备状态 - 餐桌: " + currentTableNumber + " (预约号: " + reservationId + ")";

        JDialog dialog = new JDialog(frame, dialogTitle, true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // ═══════════════════════════════════════════════════════════
        // 【步骤 4】🔧 使用解析出的 reservationId 加载订单菜品
        // ═══════════════════════════════════════════════════════════
        List<OrderItem> orderItems = frame.loadFormalOrderItemsByReservationId(reservationId);

        if (orderItems == null || orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "该预约订单暂无菜品！\n" +
                            "查询依据：" + (currentOrderType == OrderType.DINE_IN ? "餐桌关联的预约号" : "预约号") + " [" + reservationId + "]",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 5】创建菜品列表面板（支持滚动）
        // ═══════════════════════════════════════════════════════════
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(245, 248, 255));
        listPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        // 🔧【核心修复】存储：orderItemId → 已准备数量输入框
        // 使用 orderItemId（数据库主键）作为 Key，而不是 itemCode（菜品编号）
        // 这样可以区分同一个菜品的多个不同订单项（如聚餐桌场景）
        Map<Integer, JTextField> preparedQtyMap = new HashMap<>();

        for (OrderItem item : orderItems) {
            // 🔧【新增】跳过已上桌的菜品（只处理准备状态的菜品）
            String itemStatus = item.getStatus();
            if ("PARTIALLY_SERVED".equals(itemStatus) || "SERVED".equals(itemStatus)) {
                continue;  // 跳过已上桌的菜品，不显示在准备对话框中
            }

            // ── 菜品卡片面板 ──
            JPanel itemPanel = new JPanel(new BorderLayout(10, 5));
            itemPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            itemPanel.setBackground(Color.WHITE);
            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

            // ── 左侧：菜品信息 ──
            JPanel infoPanel = new JPanel(new GridLayout(4, 1, 0, 5));
            infoPanel.setBackground(Color.WHITE);
            infoPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(" " + item.getItemName() + " (" + item.getItemCode() + ")");
            nameLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            nameLabel.setForeground(new Color(30, 144, 255));
            infoPanel.add(nameLabel);

            JLabel totalQtyLabel = new JLabel("总数量：" + item.getQuantity() + " 份");
            totalQtyLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            infoPanel.add(totalQtyLabel);

            String currentStatusText = switch (item.getStatus()) {
                case "UNSERVED" -> " 未准备";
                case "PREPARING" -> " 准备中";
                case "PREPARED" -> " 已准备";
                default -> item.getStatus();
            };
            JLabel statusLabel = new JLabel("当前状态：" + currentStatusText);
            statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            infoPanel.add(statusLabel);

            JPanel qtyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            qtyPanel.setBackground(Color.WHITE);
            qtyPanel.setOpaque(false);
            qtyPanel.add(new JLabel("已准备："));

            JButton minusBtn = new JButton("-");
            minusBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            minusBtn.setPreferredSize(new Dimension(50, 35));
            minusBtn.setBackground(new Color(244, 67, 54));
            minusBtn.setForeground(Color.WHITE);
            minusBtn.setFocusPainted(false);

            JTextField preparedQtyField = new JTextField(String.valueOf(item.getPreparedQuantity()), 3);
            preparedQtyField.setHorizontalAlignment(JTextField.CENTER);
            preparedQtyField.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            preparedQtyField.setPreferredSize(new Dimension(50, 28));
            preparedQtyField.setBackground(new Color(250, 250, 250));

            JButton plusBtn = new JButton("+");
            plusBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            plusBtn.setPreferredSize(new Dimension(50, 35));
            plusBtn.setBackground(new Color(76, 175, 80));
            plusBtn.setForeground(Color.WHITE);
            plusBtn.setFocusPainted(false);

            qtyPanel.add(minusBtn);
            qtyPanel.add(preparedQtyField);
            qtyPanel.add(plusBtn);
            qtyPanel.add(new JLabel("/ " + item.getQuantity() + " 份"));
            infoPanel.add(qtyPanel);

            JLabel statusPreview = new JLabel(" 已准备", SwingConstants.RIGHT);
            statusPreview.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            statusPreview.setPreferredSize(new Dimension(120, 100));

            Runnable updatePreview = () -> {
                try {
                    int prepared = Integer.parseInt(preparedQtyField.getText().trim());
                    int total = item.getQuantity();
                    if (prepared <= 0) {
                        statusPreview.setText(" 未准备");
                        statusPreview.setForeground(new Color(158, 158, 158));
                    } else if (prepared >= total) {
                        statusPreview.setText(" 已准备");
                        statusPreview.setForeground(new Color(76, 175, 80));
                    } else {
                        statusPreview.setText("准备中");
                        statusPreview.setForeground(new Color(255, 152, 0));
                    }
                } catch (NumberFormatException ex) {
                    statusPreview.setText("⚠️ 格式错误");
                    statusPreview.setForeground(Color.RED);
                }
            };

            minusBtn.addActionListener(e -> {
                try {
                    int val = Integer.parseInt(preparedQtyField.getText().trim());
                    if (val > 0) {
                        preparedQtyField.setText(String.valueOf(val - 1));
                    }
                } catch (NumberFormatException ex) {
                    preparedQtyField.setText("0");
                }
                updatePreview.run();
            });

            plusBtn.addActionListener(e -> {
                try {
                    int val = Integer.parseInt(preparedQtyField.getText().trim());
                    if (val < item.getQuantity()) {
                        preparedQtyField.setText(String.valueOf(val + 1));
                    }
                } catch (NumberFormatException ex) {
                    preparedQtyField.setText("0");
                }
                updatePreview.run();
            });

            preparedQtyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview.run();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview.run();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    updatePreview.run();
                }
            });

            updatePreview.run();

            itemPanel.add(infoPanel, BorderLayout.CENTER);
            itemPanel.add(statusPreview, BorderLayout.EAST);
            listPanel.add(itemPanel);
            listPanel.add(Box.createRigidArea(new Dimension(0, 8)));

            // 🔧【核心修复】使用 orderItemId 作为 Key（唯一标识），而不是 itemCode
            // 这样可以正确区分同一个菜品的多个不同订单项
            preparedQtyMap.put(item.getOrderItemId(), preparedQtyField);
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                " 菜品准备进度 ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 13),
                new Color(30, 144, 255)));
        scrollPane.getViewport().setBackground(new Color(245, 248, 255));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        // 【步骤 6】按钮面板
        // ═══════════════════════════════════════════════════════════
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(new Color(245, 248, 255));

        JButton confirmBtn = new JButton("✓ 确认更新");
        confirmBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        confirmBtn.setBackground(new Color(76, 175, 80));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setPreferredSize(new Dimension(120, 35));

        JButton cancelBtn = new JButton("✗ 取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        cancelBtn.setBackground(new Color(158, 158, 158));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setPreferredSize(new Dimension(100, 35));

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // ═══════════════════════════════════════════════════════════
        // 【步骤 7】确认按钮事件
        // ═══════════════════════════════════════════════════════════
        confirmBtn.addActionListener(e -> {
            try {
                int updatedCount = 0;
                StringBuilder updateLog = new StringBuilder();

                // 🔧 遍历所有订单菜品
                for (OrderItem item : orderItems) {
                    // 🔧【新增】跳过已上桌的菜品（只处理准备状态的菜品）
                    String itemStatus = item.getStatus();
                    if ("PARTIALLY_SERVED".equals(itemStatus) || "SERVED".equals(itemStatus)) {
                        continue;  // 跳过已上桌的菜品，不显示在准备对话框中
                    }

                    // 【核心修复】使用 orderItemId 作为 Key 获取输入框
                    // 这样可以区分同一个菜品的多个不同订单项（如聚餐桌场景）
                    JTextField qtyField = preparedQtyMap.get(item.getOrderItemId());
                    if (qtyField != null) {
                        String text = qtyField.getText().trim();
                        int preparedQty;
                        try {
                            preparedQty = Integer.parseInt(text);
                        } catch (NumberFormatException ex) {
                            preparedQty = 0;
                        }

                        // 边界校验：确保准备数量在 0~总数量 之间
                        preparedQty = Math.max(0, Math.min(preparedQty, item.getQuantity()));

                        // 🔧 根据准备数量计算新状态
                        String newStatus;
                        if (preparedQty <= 0) {
                            newStatus = "UNSERVED";        // 未准备
                        } else if (preparedQty >= item.getQuantity()) {
                            newStatus = "PREPARED";         // 已全部准备
                        } else {
                            newStatus = "PREPARING";        // 部分准备中
                        }

                        //  获取分配的餐桌显示ID（聚餐桌场景用）
                        String assignedTableDisplayId = item.getAssignedTableDisplayId();

                        //  调用 Controller 更新准备状态
                        controller.updateReservationOrderItemPrepared(
                                finalReservationId,           // 预约号
                                item.getOrderItemId(),        // 🔧 使用 orderItemId（主键）
                                item.getItemCode(),           // 菜品编号
                                preparedQty,                  // 已准备数量
                                newStatus,                    // 新状态
                                assignedTableDisplayId        // 分配的餐桌显示ID
                        );

                        updatedCount++;

                        // 记录更新日志
                        String statusText = switch (newStatus) {
                            case "UNSERVED" -> "未准备";
                            case "PREPARING" -> "准备中";
                            case "PREPARED" -> "已准备";
                            default -> newStatus;
                        };
                        updateLog.append(item.getItemName())
                                .append(": ")
                                .append(preparedQty)
                                .append("/")
                                .append(item.getQuantity())
                                .append(" (")
                                .append(statusText)
                                .append(")\n");
                    }
                }

                // 🔧 显示成功提示
                JOptionPane.showMessageDialog(dialog,
                        " 成功更新 " + updatedCount + " 个菜品的准备进度！\n\n" +
                                (updateLog.length() > 0 ? updateLog.toString() : "无变更"),
                        "成功", JOptionPane.INFORMATION_MESSAGE);

                // 🔧 刷新订单显示并关闭对话框
                refreshFormalOrderDisplay();
                dialog.dispose();

            } catch (Exception ex) {
                // 🔧 异常处理
                JOptionPane.showMessageDialog(dialog,
                        " 更新失败：" + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    /**
     * 🔧 解析 quantity_distribution JSON 字符串为 Map
     * 格式示例：{"13":4,"14":4,"15":3} → Map{"13"=4, "14"=4, "15"=3}
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
            return null;
        }
    }

    /**
     * 🔧 格式化 distribution Map 为 JSON 字符串
     */
    private String formatDistribution(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return null;
        }
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

    /**
     * 🔧 从逗号分隔的桌号列表中移除指定桌号
     * 示例：输入 "13,14,15,16", "14" → 输出 "13,15,16"
     */
    private String removeTableFromList(String tableList, String tableIdToRemove) {
        if (tableList == null || tableList.isEmpty()) {
            return null;
        }
        String[] tables = tableList.split(",");
        List<String> result = new ArrayList<>();
        for (String t : tables) {
            if (!tableIdToRemove.equals(t.trim())) {
                result.add(t.trim());
            }
        }
        return result.isEmpty() ? null : String.join(",", result);
    }

    /**
     * 🔧 辅助方法：计算撤销后状态
     */
    private String calculateStatusAfterCancel(int newQty, int servedQty) {
        if (newQty <= 0) return "UNSERVED";
        if (servedQty >= newQty) return "SERVED";
        if (servedQty > 0) return "PARTIALLY_SERVED";
        return "UNSERVED";
    }

    // ═══════════════════════════════════════════════════════════
// 🔧 辅助方法：检查数组是否包含指定餐桌号
// ═══════════════════════════════════════════════════════════
    private boolean containsTable(String[] tableIds, String targetId) {
        for (String tid : tableIds) {
            if (targetId.equals(tid.trim())) {
                return true;
            }
        }
        return false;
    }
}
