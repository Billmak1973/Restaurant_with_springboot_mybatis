package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Order;
import com.restaurant.entity.TableReservation;
import com.restaurant.entity.Tables;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Collectors;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DateEditor;
import javax.swing.Timer;

/**
 * 純GUI視圖層 - 不包含任何業務邏輯
 * 所有按鈕事件通過Controller轉發
 */
public class RestaurantView extends JFrame {

    // ===== 成員變量（完全保留）=====
    private javax.swing.Timer refreshTimer;
    private static final int REFRESH_INTERVAL_MS = 600000;  // 10 分钟
    private RestaurantController controller;
    private JPanel tablesPanel, rightPanel, bottomPanel;
    private final Color color1Seat = new Color(230, 210, 255);  // 更淺的紫色
    private Color color2Seat = new Color(200, 180, 255);
    private Color color4Seat = new Color(255, 150, 100);
    private Color color6Seat = new Color(100, 200, 200);
    private final Color colorVacant = new Color(255, 255, 255);
    private final Color colorOccupied = new Color(136, 255, 103);
    private final Color colorSettingUp = new Color(255, 126, 0);
    private final Color colorMerged = new Color(216, 191, 216);
    private final Color colorSplitting = new Color(255, 215, 0);
    private final Color colorGrouped = new Color(173, 216, 230);  // 淡蓝色 #ADD8E6
    private JTextField groupSizeInput;
    private JButton addGroupButton, splitTableButton, recombineTableButton,
            orderButton, checkoutButton, changeTableButton, clearAllButton,
            queueManagementButton, selectTableButton, closeDayButton, reportButton, reserveTableButton;
    private LinkedList<String> logEntries = new LinkedList<>();
    private Map<String, Component> tableComponentMap = new HashMap<>();
    private JLabel statusLabel;
    private JTextArea queueDisplay, tableStatusDisplay;
    private JPanel reservationsLogPanel;        // 预约记录容器面板
    private JScrollPane reservationsLogScroll;   // 滚动面板
    // ===== 在类的顶部成员变量区域添加 =====
    private JSplitPane innerSplitPane;  // ← 新增这行
    private JSplitPane mainSplitPane;   // ← 外层分割也建议声明
    private Map<String, Object> pendingReservationEdit = null;

    // ===== 構造函數（完全保留你的佈局代碼）=====
    public RestaurantView() {
        tableComponentMap = new HashMap<>();
        setTitle("餐厅管理系统");
        setSize(1500, 1000);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(245, 245, 245));

        // === 左側面板：餐桌可視化（完全保留）===
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(1000, 800));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tablesPanel = new JPanel(new GridLayout(0, 3, 15, 15));
        JScrollPane leftScroll = new JScrollPane(tablesPanel);
        leftScroll.setBorder(BorderFactory.createTitledBorder("餐桌状态可视化"));
        leftPanel.add(leftScroll, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.CENTER);

        // === 右側面板（完全保留）===
        // === 右側面板（完全保留）===
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(400, 800));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setBorder(BorderFactory.createTitledBorder("系统信息面板"));

// 1. 隊列狀態
        queueDisplay = new JTextArea();
        queueDisplay.setEditable(false);
        queueDisplay.setLineWrap(true);
        queueDisplay.setWrapStyleWord(true);
        queueDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        JScrollPane queueScrollPane = new JScrollPane(queueDisplay);
        queueScrollPane.setBorder(BorderFactory.createTitledBorder("当前队列状态"));

// 2. 日誌顯示 - JPanel + JButton 方案
        reservationsLogPanel = new JPanel();
        reservationsLogPanel.setLayout(new BoxLayout(reservationsLogPanel, BoxLayout.Y_AXIS));
        reservationsLogPanel.setBackground(new Color(250, 250, 250));
        reservationsLogPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        reservationsLogScroll = new JScrollPane(reservationsLogPanel);
        reservationsLogScroll.setBorder(BorderFactory.createTitledBorder("📋 數量模式預約監控"));
        reservationsLogScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        reservationsLogScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        reservationsLogScroll.getViewport().setBackground(new Color(250, 250, 250));

// 3. 餐桌狀態詳情
        tableStatusDisplay = new JTextArea();
        tableStatusDisplay.setEditable(false);
        tableStatusDisplay.setLineWrap(true);
        tableStatusDisplay.setWrapStyleWord(true);
        tableStatusDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        JScrollPane tableStatusScrollPane = new JScrollPane(tableStatusDisplay);
        tableStatusScrollPane.setBorder(BorderFactory.createTitledBorder("餐桌状态详情"));

// 🔧【修复】先创建 innerSplitPane，再设置组件
        innerSplitPane = new JSplitPane(//无法解析符号 'innerSplitPane'
                JSplitPane.VERTICAL_SPLIT,
                tableStatusScrollPane,
                reservationsLogScroll  // 🔧 直接用新的 reservationsLogScroll
        );
        innerSplitPane.setDividerLocation(300);
        innerSplitPane.setResizeWeight(0.65);

// 外層分割
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                queueScrollPane,
                innerSplitPane
        );
        splitPane.setDividerLocation(150);
        splitPane.setResizeWeight(0.2);

// 最小尺寸
        queueScrollPane.setMinimumSize(new Dimension(100, 80));
        tableStatusScrollPane.setMinimumSize(new Dimension(100, 150));
        reservationsLogScroll.setMinimumSize(new Dimension(100, 100));

        rightPanel.add(splitPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);


        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottomPanel.setBackground(new Color(230, 230, 230));

        groupSizeInput = new JTextField(5);
        groupSizeInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        addGroupButton = createStyledButton("添加顾客组", new Color(70, 130, 180));
        queueManagementButton = createStyledButton("管理队列", new Color(150, 100, 50));
        selectTableButton = createStyledButton("选餐桌", new Color(160, 82, 181));
        splitTableButton = createStyledButton("拆分餐桌", new Color(150, 200, 50));
        recombineTableButton = createStyledButton("合并餐桌", new Color(50, 200, 50));
        clearAllButton = createStyledButton("清空所有餐桌", new Color(205, 92, 92));
        closeDayButton = createStyledButton("结束营业", new Color(178, 34, 34));
        changeTableButton = createStyledButton("换餐桌", new Color(255, 165, 0));
        orderButton = createStyledButton("点餐", new Color(0, 100, 255));
        checkoutButton = createStyledButton("结账", new Color(205, 185, 0));
        reportButton = createStyledButton("营业报表", new Color(0, 128, 128));
        reserveTableButton = createStyledButton("预定餐桌", new Color(186, 140, 211));  // ← 新增

        bottomPanel.add(new JLabel("组人数:"));
        bottomPanel.add(groupSizeInput);
        bottomPanel.add(addGroupButton);
        bottomPanel.add(splitTableButton);
        bottomPanel.add(recombineTableButton);
        bottomPanel.add(orderButton);
        bottomPanel.add(checkoutButton);
        bottomPanel.add(reserveTableButton);
        bottomPanel.add(changeTableButton);
        bottomPanel.add(clearAllButton);
        bottomPanel.add(queueManagementButton);
        bottomPanel.add(selectTableButton);
        bottomPanel.add(closeDayButton);
        bottomPanel.add(reportButton);

        add(bottomPanel, BorderLayout.SOUTH);
        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】初始化数量模式预约显示 + 定时器（每10分钟刷新）
        // ═══════════════════════════════════════════════════════════

        // 1. 初始加载（延迟执行，确保controller已设置）
        SwingUtilities.invokeLater(() -> {
            if (controller != null) {
                System.out.println("🔄 初始加载数量模式预约列表...");
                refreshQuantityReservationsLog();
            }
        });

        // 2. 设置定时器：每10分钟（600000毫秒）自动刷新
        refreshTimer = new javax.swing.Timer(600000, e -> {
            if (controller != null) {
                System.out.println("⏰ 定时刷新数量模式预约列表...");
                refreshQuantityReservationsLog();
            }
        });
        refreshTimer.setRepeats(true);  // 确保重复执行
        refreshTimer.start();
        System.out.println("✅ 数量模式预约自动刷新已启动（每10分钟）");
    }

    // ===== 輔助方法（完全保留）=====
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("微软雅黑", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ===== Setter for Controller（手動設置，非Spring注入）=====
    public void setController(RestaurantController controller) {
        this.controller = controller;
    }

    // ===== 事件綁定方法（空實現，後續由Controller填充）=====
    public void setAddGroupListener(ActionListener listener) {
        addGroupButton.addActionListener(listener);
    }

    public void setSplitTableListener(ActionListener listener) {
        splitTableButton.addActionListener(listener);
    }

    public void setRecombineTableListener(ActionListener listener) {
        recombineTableButton.addActionListener(listener);
    }

    public void setOrderListener(ActionListener listener) {
        orderButton.addActionListener(listener);
    }

    public void setCheckoutListener(ActionListener listener) {
        checkoutButton.addActionListener(listener);
    }

    public void setChangeTableListener(ActionListener listener) {
        changeTableButton.addActionListener(listener);
    }

    public void setClearAllListener(ActionListener listener) {
        clearAllButton.addActionListener(listener);
    }

    public void setQueueManagementListener(ActionListener listener) {
        queueManagementButton.addActionListener(listener);
    }

    public void setSelectTableListener(ActionListener listener) {
        selectTableButton.addActionListener(listener);
    }

    public void setCloseDayListener(ActionListener listener) {
        closeDayButton.addActionListener(listener);
    }

    public void setReportListener(ActionListener listener) {
        reportButton.addActionListener(listener);
    }

    public void setReserveTableListener(ActionListener listener) {
        reserveTableButton.addActionListener(listener);
    }

    public void setTables(List<Tables> tables) {
        updateTablesDisplay(tables);
    }

    public void updateTablesDisplay(List<Tables> tables) {
        SwingUtilities.invokeLater(() -> {
            if (tablesPanel == null) return;

            tablesPanel.removeAll();
            tableComponentMap.clear();

            Color[] tableColors = new Color[16];
            for (int i = 1; i <= 15; i++) {
                if (i <= 6) {
                    tableColors[i] = color2Seat;
                } else if (i <= 12) {
                    tableColors[i] = color4Seat;
                } else {
                    tableColors[i] = color6Seat;
                }
            }

            if (tables != null) {
                for (Tables table : tables) {
                    JButton tableButton = createTableButton(table, tableColors);
                    tablesPanel.add(tableButton);
                    tableComponentMap.put(table.getDisplayId(), tableButton);
                }
            }

            tablesPanel.revalidate();
            tablesPanel.repaint();
        });
    }


    private JButton createTableButton(Tables table, Color[] tableColors) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        button.setPreferredSize(new Dimension(180, 180));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        updateButtonBackground(button, table);

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修复】根据餐桌容量决定颜色，支持任意 baseId 扩展
        // ═══════════════════════════════════════════════════════════
        Color tableColor = switch (table.getCapacity()) {
            case 1 -> color1Seat;    // 1 人桌獨立顏色
            case 2 -> color2Seat;    // 2人桌 → 浅紫色
            case 4 -> color4Seat;    // 4人桌 → 浅橙色
            case 6 -> color6Seat;    // 6人桌 → 浅青色
            default -> {
                // 未知容量时记录日志 + 兜底使用2人桌颜色
                System.err.println("⚠️ 未知餐桌容量: " + table.getCapacity() +
                        ", 餐桌#" + table.getDisplayId() + " 使用默认颜色");
                yield color2Seat;
            }
        };

        // 使用计算出的颜色创建图标（不再依赖 tableColors 数组索引）
        button.setIcon(table.createTableIcon(tableColor));

        JLabel infoLabel = createTableInfoLabel(table);
        button.add(infoLabel, BorderLayout.SOUTH);

        // ═══════════════════════════════════════════════════════════
        // 【事件转发】只传递 displayId，业务逻辑交给 Controller
        // ═══════════════════════════════════════════════════════════
        button.addActionListener(e -> {
            if (controller != null) {
                controller.handleTableClick(table.getDisplayId());
            } else {
                showError("控制器未初始化，无法响应操作");
            }
        });

        // 将按钮添加到映射表（保留原有逻辑）
        tableComponentMap.put(table.getDisplayId(), button);

        return button;
    }


    private void updateButtonBackground(JButton button, Tables table) {
        Color bgColor;

        // 🔴【新增】合并桌优先判断：必须是 MERGED 类型 + OCCUPIED 状态
        if (table.getTableType() == Tables.TableType.MERGED &&
                table.getStatus() == Tables.TableStatus.OCCUPIED) {
            bgColor = colorMerged;  // 👈 使用紫色
        } else if (table.getTableType() == Tables.TableType.GROUPED && table.getStatus() == Tables.TableStatus.OCCUPIED) {
            bgColor = colorGrouped;
        }
        // 原有逻辑保持不变
        else {
            Tables.TableStatus status = table.getStatus();
            switch (status) {
                case VACANT -> bgColor = colorVacant;
                case OCCUPIED -> bgColor = colorOccupied;  // 普通占用是绿色
                case SETTING_UP -> bgColor = colorSettingUp;
                case SPLITTING -> bgColor = colorSplitting;
                //   case RESERVED -> bgColor = new Color(230, 235, 250); // 浅灰蓝色 - 预定状态
                case RESERVED -> bgColor = new Color(230, 220, 245); // 淡薰衣草色 - 预定状态
                default -> bgColor = colorVacant;
            }
        }

        button.setBackground(bgColor);
        button.setOpaque(true);  // 确保背景色生效
    }

    private JLabel createTableInfoLabel(Tables table) {
        String displayId = table.getDisplayId();
        String statusText = getStatusText(table);

        // 🔧【新增】子桌特殊標識
        String suffixLabel = "";
        if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
            suffixLabel = String.format(" <font color='#666' size='2'>(%s)</font>",
                    table.getSubTableSuffix().toUpperCase());
        }

        // 顧客組信息
        String groupInfo = "";
        if (table.getCurrentGroup() != null) {
            groupInfo = String.format("<br>顧客組: <b>#%d</b> (%d人)",
                    table.getCurrentGroup().getCallNumber(),
                    table.getCurrentGroup().getGroupSize());
        } else if (table.getCurrentGroupId() != null) {
            groupInfo = "<br>顧客組: #" + table.getCurrentGroupId() + " (加載中)";
        }

        // 🔧 在餐桌編號後添加子桌標識
        String html = "<html><center>" +
                "<b>餐桌 #" + displayId + "</b>" + suffixLabel + "<br>" +  // ← 這裡添加
                "容量: " + table.getCapacity() + "人 &bull; " + statusText + groupInfo +
                "</center></html>";

        JLabel label = new JLabel(html, SwingConstants.CENTER);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 13));  // 用戶偏好普通字體
        return label;
    }

    private String getStatusText(Tables table) {
        //  使用枚举定义的中文显示名称，而非 toString()
        String statusText = table.getStatus().getDisplayName();

        // 保留合并状态的特殊处理逻辑
        if (table.getTableType() == Tables.TableType.MERGED &&
                table.getStatus() == Tables.TableStatus.OCCUPIED) {
            statusText += " (合并中)";
        } else if (table.getTableType() == Tables.TableType.GROUPED && table.getStatus() == Tables.TableStatus.OCCUPIED) {
            statusText += "(聚餐中)";
        }

        return statusText;
    }

    /**
     * 更新队列显示（空实现，后续填充）
     */
    public void updateQueueDisplay(Queue<CustomerGroup> q2, Queue<CustomerGroup> q4, Queue<CustomerGroup> q6) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前队列\n\n");

        // 2人桌队列
        sb.append("2人桌队列:\n");
        if (q2.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q2) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getGroupSize(), position++));
            }
        }
        sb.append("\n");

        // 4人桌队列
        sb.append("4人桌队列:\n");
        if (q4.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q4) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getGroupSize(), position++));
            }
        }
        sb.append("\n");

        // 6人桌队列
        sb.append("6人桌队列:\n");
        if (q6.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q6) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getGroupSize(), position++));
            }
        }

        queueDisplay.setText(sb.toString());
    }

    /**
     * 更新餐桌状态详情面板（空实现，后续填充）
     */
    public void updateTableStatusDisplay(List<Tables> tables) {
        /* TODO */
    }

    /**
     * 添加日志（空实现，后续填充）
     */
    public void appendToLog(String message) {
        /* TODO */
    }

    /**
     * 更新日志显示（空实现，后续填充）
     */
    public void updateLogDisplay(String log) {
        /* TODO */
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
    }

    public void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "警告", JOptionPane.WARNING_MESSAGE);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    public String getGroupSizeInput() {
        if (groupSizeInput == null) {
            return "";
        }
        return groupSizeInput.getText().trim();
    }

    // ===== 新增方法：清空输入框 =====
    public void clearGroupSizeInput() {
        if (groupSizeInput != null) {
            groupSizeInput.setText("");
        }
    }

    /**
     * 刷新單個餐桌按鈕的顯示
     */
    public void refreshTableButton(String displayId, Tables updatedTable) {
        SwingUtilities.invokeLater(() -> {
            Component comp = tableComponentMap.get(displayId);
            if (comp instanceof JButton button) {
                // 1. 更新背景色（根據狀態）
                updateButtonBackground(button, updatedTable);

                // 2. 🔧【優化】顏色邏輯改為與 createTableButton 一致（根據容量而非 baseId）
                Color tableColor = switch (updatedTable.getCapacity()) {
                    case 2 -> color2Seat;
                    case 4 -> color4Seat;
                    case 6 -> color6Seat;
                    default -> color2Seat;
                };

                // 3. 更新圖標（椅子狀態）
                button.setIcon(updatedTable.createTableIcon(tableColor));

                // 4. 更新底部文字信息
                button.removeAll();
                button.add(createTableInfoLabel(updatedTable), BorderLayout.SOUTH);

                // 5. 重繪
                button.revalidate();
                button.repaint();
            }
        });
    }


    public String showCheckoutDialog() {
        String[] options = {"堂食订单", "外卖订单"};
        int typeChoice = JOptionPane.showOptionDialog(
                this, "请选择订单类型：", "结账",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
        );

        //  调试输出（可选）
        //System.out.println("typeChoice = " + typeChoice);

        // 修复 1：点击 X 或取消 → 返回空字符串 "" 表示"真正取消"
        if (typeChoice < 0) {
            return "";  // ← 关键修改：用 "" 表示取消
        }

        //  修复 2：选择"外卖订单" → 仍然返回 null（保持 Controller 兼容）
        if (typeChoice == 1) {
            return null;  // ← 保持不变
        }

        //  修复 3：堂食订单输入时点击 X → 也返回 ""
        String input = JOptionPane.showInputDialog(
                this, "请输入餐桌号：\n（例如：7 或 7a）",
                "堂食订单结账", JOptionPane.QUESTION_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return "";  // ← 关键修改：用 "" 表示取消
        }

        // 堂食订单成功
        return "DINE_IN:" + input.trim();
    }

    /**
     * 渲染订单详情 HTML（含上菜状态颜色标识）
     *
     * @note 失败时显示红色错误信息
     */
    private void renderOrderDetails(Map<String, Object> details, JEditorPane orderDisplay,
                                    JLabel statusLabel, JLabel totalLabel) {
        if (details.containsKey("error")) {
            statusLabel.setText("错误：" + details.get("error"));
            statusLabel.setForeground(Color.RED);
            totalLabel.setText("总金额：0.00 元");
            orderDisplay.setText("<html><body style='font-family: Microsoft YaHei; color: red;'>错误：" +
                    details.get("error") + "</body></html>");
            return;
        }

        try {
            // 🔧【修复 1】兼容 LocalDateTime 和 Timestamp 两种时间类型
            Object orderTimeObj = details.get("orderTime");
            java.time.LocalDateTime orderTime = null;

            if (orderTimeObj instanceof java.time.LocalDateTime) {
                orderTime = (java.time.LocalDateTime) orderTimeObj;
            } else if (orderTimeObj instanceof java.sql.Timestamp) {
                orderTime = ((java.sql.Timestamp) orderTimeObj).toLocalDateTime();
            }

            // 🔧【修复 2】金额类型只处理 double（简化版）
            Object totalAmountObj = details.get("totalAmount");
            double totalAmount = 0.0;

            if (totalAmountObj instanceof Double) {
                totalAmount = (Double) totalAmountObj;
            } else if (totalAmountObj instanceof Number) {
                // 兜底：其他 Number 类型（Integer/Long 等）转 double
                totalAmount = ((Number) totalAmountObj).doubleValue();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) details.get("items");

            // 构建 HTML 内容
            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<html><body style='font-family: Microsoft YaHei; margin: 10px;'>");

            // 🔧 使用 LocalDateTime 格式化时间
            String timeStr = (orderTime != null) ?
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(java.sql.Timestamp.valueOf(orderTime)) : "未知时间";

            htmlContent.append("<h3 style='color: #2c3e50;'>订单详情 (").append(timeStr).append(")</h3>");

            htmlContent.append("<table border='1' cellpadding='8' cellspacing='0' style='width: 100%; border-collapse: collapse; border-color: #ddd;'>");
            htmlContent.append("<tr style='background-color: #f8f9fa; font-weight: bold;'>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>菜品编号</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>菜品名称</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>数量</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>已上桌</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>单价</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>小计</th>");
            htmlContent.append("</tr>");

            boolean allServed = true;
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String itemCode = (String) item.get("itemCode");
                    String itemName = (String) item.get("itemName");
                    Integer quantity = (Integer) item.get("quantity");
                    Integer servedQuantity = (Integer) item.get("servedQuantity");
                    Double price = (Double) item.get("price");

                    // 安全处理 null 值
                    if (quantity == null) quantity = 0;
                    if (servedQuantity == null) servedQuantity = 0;
                    if (price == null) price = 0.0;

                    double subtotal = quantity * price;

                    String statusColor = "green";
                    String statusText = "已上桌";
                    if (servedQuantity < quantity) {
                        statusColor = "orange";
                        statusText = "部分上桌";
                        allServed = false;
                    }
                    if (servedQuantity == 0 && quantity > 0) {
                        statusColor = "red";
                        statusText = "未上桌";
                        allServed = false;
                    }

                    htmlContent.append("<tr style='border: 1px solid #ddd;'>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: center;'>")
                            .append(itemCode != null ? itemCode : "").append("</td>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px;'>")
                            .append(itemName != null ? itemName : "").append("</td>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: center;'>")
                            .append(quantity).append("</td>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; color: ")
                            .append(statusColor).append("; text-align: center;'>")
                            .append(servedQuantity).append("/").append(quantity)
                            .append(" (").append(statusText).append(")</td>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: right;'>¥")
                            .append(String.format("%.2f", price)).append("</td>");
                    htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: right; font-weight: bold;'>¥")
                            .append(String.format("%.2f", subtotal)).append("</td>");
                    htmlContent.append("</tr>");
                }
            }

            htmlContent.append("</table>");
            htmlContent.append("<div style='margin-top: 10px; padding: 10px; background-color: #e8f4fd; border-radius: 5px;'>");
            htmlContent.append("<strong>总计:</strong> ").append(items != null ? items.size() : 0).append(" 个菜品，总数量: ");
            if (items != null) {
                int totalQty = items.stream()
                        .mapToInt(item -> ((Integer) item.getOrDefault("quantity", 0)))
                        .sum();
                htmlContent.append(totalQty);
            } else {
                htmlContent.append("0");
            }
            htmlContent.append(" 份");
            htmlContent.append("</div>");
            htmlContent.append("</body></html>");

            orderDisplay.setText(htmlContent.toString());

            // 🔧 设置总金额（之前因为异常没执行到这里）
            totalLabel.setText("总金额：" + String.format("%.2f", totalAmount) + "元");

            // 状态判断
            if (allServed && items != null && !items.isEmpty()) {
                statusLabel.setText("订单状态：所有菜品已上桌 ✓");
                statusLabel.setForeground(new Color(0, 128, 0)); // 深绿色
            } else if (items != null && !items.isEmpty()) {
                statusLabel.setText("订单状态：部分菜品未上桌 ⚠");
                statusLabel.setForeground(new Color(255, 153, 0)); // 橙色
            } else {
                statusLabel.setText("订单状态：无订单");
                statusLabel.setForeground(Color.GRAY);
            }

        } catch (Exception ex) {
            statusLabel.setText("错误：渲染订单详情失败");
            statusLabel.setForeground(Color.RED);
            orderDisplay.setText("<html><body style='font-family: Microsoft YaHei; color: red;'>错误：" +
                    ex.getMessage() + "</body></html>");
            ex.printStackTrace();
        }
    }

    /**
     * 🔧 显示结账界面（外卖订单自动显示列表，无需输入订单号）
     *
     * @param orderType  "DINE_IN" 或 "TAKEOUT"
     * @param identifier 餐桌号（仅堂食用，外卖可传 null）
     */
    public void showCheckoutInterface(String orderType, String identifier) {
        // ===== 堂食订单：保持原有简化逻辑 =====
        if ("DINE_IN".equals(orderType)) {
            showSimpleDineInCheckout(identifier);
            return;
        }

        // ===== 外卖订单：直接显示订单列表选择界面 =====
        showTakeoutOrderListDialog();
    }


    /**
     * 🔧 堂食订单结账界面（严格堂食 + 定金抵扣 + 营收记录修正）
     *
     * @param tableNumber 餐桌号
     */
    private void showSimpleDineInCheckout(String tableNumber) {
        JFrame dialog = new JFrame("结账 - 餐桌 " + tableNumber);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 订单信息面板
        JPanel orderPanel = new JPanel(new BorderLayout(10, 10));

        // 订单列表（HTML 表格显示）
        JEditorPane orderDisplay = new JEditorPane("text/html", "");
        orderDisplay.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(orderDisplay);
        scrollPane.setPreferredSize(new Dimension(650, 250));

        // 订单状态标签
        JLabel orderStatusLabel = new JLabel("订单状态: 加载中...");
        orderStatusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));

        // 🔧【新增】定金信息标签
        JLabel depositInfoLabel = new JLabel("");
        depositInfoLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        depositInfoLabel.setForeground(new Color(0, 100, 200));

        // 总金额标签（菜品总额）
        JLabel totalLabel = new JLabel("菜品总额: 0.00 元");
        totalLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        totalLabel.setForeground(Color.RED);

        // 🔧【新增】应付金额标签（考虑定金抵扣）
        JLabel payableLabel = new JLabel("应付金额: 0.00 元");
        payableLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        payableLabel.setForeground(new Color(0, 128, 0));

        // 支付面板
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel paymentLabel = new JLabel("支付金额:");
        paymentLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        JTextField paymentField = new JTextField(10);
        paymentField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        JButton checkoutButton = new JButton("确认结账");
        checkoutButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        checkoutButton.setBackground(new Color(0, 128, 0));
        checkoutButton.setForeground(Color.WHITE);

        paymentPanel.add(paymentLabel);
        paymentPanel.add(paymentField);
        paymentPanel.add(checkoutButton);

        // 组装订单面板
        orderPanel.add(orderStatusLabel, BorderLayout.NORTH);
        orderPanel.add(scrollPane, BorderLayout.CENTER);

        // 🔧【新增】底部信息面板（定金+菜品总额+应付）
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        infoPanel.add(depositInfoLabel);
        infoPanel.add(totalLabel);
        infoPanel.add(payableLabel);
        orderPanel.add(infoPanel, BorderLayout.SOUTH);

        // 组装主面板
        mainPanel.add(orderPanel, BorderLayout.CENTER);
        mainPanel.add(paymentPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel, BorderLayout.CENTER);

        // 🔧【核心】通过 Controller 异步加载订单数据 + 定金信息
        final double[] prepaidAmountRef = {0.0};      // 预付定金
        final boolean[] hasPrepaidRef = {false};       // 是否有预付
        final double[] itemsTotalRef = {0.0};          // 菜品总额

        new Thread(() -> {
            try {
                // 1. 加载订单详情（堂食）
                Map<String, Object> orderDetails = controller.getOrderDetails("DINE_IN", tableNumber);

                // 2. 🔧 查询餐桌的 current_reservation_id 和预付信息
                Tables table = controller.getTableById(tableNumber);
                System.out.println("🔍 [DEBUG] 餐桌信息:");
                System.out.println("   tableNumber: " + tableNumber);
                System.out.println("   table: " + (table != null ? "存在" : "null"));
                if (table != null) {
                    System.out.println("   currentReservationId: " + table.getCurrentReservationId());
                }
                if (table != null && table.getCurrentReservationId() != null) {
                    String reservationId = table.getCurrentReservationId();
                    System.out.println("   reservationId: " + reservationId);

                    // 通过 reservation_id 查询订单的预付信息
                    Map<String, Object> prepaidInfo = controller.getPrepaidInfoByReservationId(reservationId);

                    System.out.println("   prepaidInfo: " + (prepaidInfo != null ? "存在" : "null"));

                    if (prepaidInfo != null) {
                        Boolean isPrepaid = (Boolean) prepaidInfo.get("is_prepaid");
                        Double prepaidAmount = (Double) prepaidInfo.get("prepaid_amount");

                        if (isPrepaid != null && isPrepaid && prepaidAmount != null && prepaidAmount > 0) {
                            hasPrepaidRef[0] = true;
                            prepaidAmountRef[0] = prepaidAmount;
                            System.out.println("    检测到预付定金: " + prepaidAmount);
                        } else {
                            System.out.println("    没有有效的预付定金");
                        }
                    }
                } else {
                    System.out.println("   ⚠️ 餐桌没有关联预约记录");
                }

                // 3. 提取菜品总额
                Object itemsTotalObj = orderDetails.get("itemsTotal");
                if (itemsTotalObj instanceof Number) {
                    itemsTotalRef[0] = ((Number) itemsTotalObj).doubleValue();
                }

                final boolean finalHasPrepaid = hasPrepaidRef[0];
                final double finalPrepaidAmount = prepaidAmountRef[0];
                final double finalItemsTotal = itemsTotalRef[0];

                SwingUtilities.invokeLater(() -> {
                    // 渲染订单详情
                    renderOrderDetails(orderDetails, orderDisplay, orderStatusLabel, totalLabel);

                    // 🔧 显示定金信息
                    if (finalHasPrepaid && finalPrepaidAmount > 0) {
                        depositInfoLabel.setText("💰 已付定金: " + String.format("%.2f", finalPrepaidAmount) + " 元");
                        depositInfoLabel.setVisible(true);

                        // 🔧【核心】计算应付金额 = 菜品总额 - 定金（最小为0）
                        double payableAmount = finalItemsTotal - finalPrepaidAmount;
                        if (payableAmount < 0) {
                            payableAmount = 0;  // 定金超过菜品总额，不退款
                        }

                        payableLabel.setText("应付金额: " + String.format("%.2f", payableAmount) + " 元");
                        payableLabel.setVisible(true);

                        // 🔧 自动填充支付框（应付金额）
                        if (payableAmount > 0) {
                            paymentField.setText(String.format("%.2f", payableAmount));
                        } else {
                            paymentField.setText("0.00");
                            paymentField.setEditable(false);  // 无需支付，禁用输入框
                        }
                    } else {
                        depositInfoLabel.setVisible(false);
                        payableLabel.setVisible(false);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 确认结账按钮事件
        checkoutButton.addActionListener(e -> {
            try {
                String paymentStr = paymentField.getText().trim();
                if (paymentStr.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入支付金额", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                double paymentAmount = Double.parseDouble(paymentStr);

                // 🔧【修复】从标签提取菜品总额
                // 🔧【修复】从标签提取菜品总额（兼容多种标签格式）
                String totalText = totalLabel.getText();
                double itemsTotal = 0.0;

                // 兼容 "总金额：" / "菜品总额：" / "总额：" 等多种格式
                if ((totalText.contains("总金额：") || totalText.contains("总金额:") ||
                        totalText.contains("菜品总额：") || totalText.contains("菜品总额:") ||
                        totalText.contains("总额：") || totalText.contains("总额:")) &&
                        totalText.contains("元")) {

                    // 提取数字部分（移除所有非数字和小数点字符）
                    String amountStr = totalText.replaceAll("[^0-9.]", "").trim();
                    try {
                        itemsTotal = Double.parseDouble(amountStr);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "订单总金额格式错误：" + totalText,
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "无法获取订单总金额\n当前显示：" + totalText,
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 🔧【核心】计算应付金额（考虑定金）
                double payableAmount = itemsTotal;
                if (hasPrepaidRef[0] && prepaidAmountRef[0] > 0) {
                    payableAmount = itemsTotal - prepaidAmountRef[0];
                    if (payableAmount < 0) {
                        payableAmount = 0;  // 定金超过菜品总额，不退款
                    }
                }

                if (paymentAmount < payableAmount) {
                    JOptionPane.showMessageDialog(dialog,
                            "支付金额不足！\n应付: " + String.format("%.2f", payableAmount) + " 元",
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 🔧【核心】计算营收记录金额 = Math.max(菜品总额, 定金)
                // 餐厅实际收到的钱 = 定金(已收) + 本次支付
                // 如果定金 >= 菜品总额：营收 = 定金（不退多余部分）
                // 如果定金 < 菜品总额：营收 = 菜品总额（定金+本次支付）
                double revenueAmount = Math.max(itemsTotal, prepaidAmountRef[0]);

                // 确认结账对话框
                String confirmMsg = "确认结账？\n";
                if (hasPrepaidRef[0] && prepaidAmountRef[0] > 0) {
                    confirmMsg += "菜品总额: " + String.format("%.2f", itemsTotal) + " 元\n" +
                            "已付定金: " + String.format("%.2f", prepaidAmountRef[0]) + " 元\n" +
                            "本次支付: " + String.format("%.2f", paymentAmount) + " 元\n";
                    if (payableAmount > 0) {
                        confirmMsg += "找零: " + String.format("%.2f", paymentAmount - payableAmount) + " 元";
                    } else {
                        confirmMsg += "💰 定金已覆盖菜品金额，无需额外支付！";
                    }
                    confirmMsg += "\n\n📊 营收记录: " + String.format("%.2f", revenueAmount) + " 元";
                } else {
                    confirmMsg += "菜品总额: " + String.format("%.2f", itemsTotal) + " 元\n" +
                            "支付金额: " + String.format("%.2f", paymentAmount) + " 元\n" +
                            "找零: " + String.format("%.2f", paymentAmount - itemsTotal) + " 元";
                }

                int confirm = JOptionPane.showConfirmDialog(dialog,
                        confirmMsg,
                        "确认结账",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        // 🔧【关键】传递营收金额给后端（不是实付金额！）
                        controller.handleCheckoutSubmitWithRevenue("DINE_IN", tableNumber, paymentAmount, revenueAmount);
                        SwingUtilities.invokeLater(() -> {
                            dialog.dispose();
                        });
                    }).start();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效的支付金额", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }

    /**
     * 加载订单详情
     */
    private void loadOrderDetail(String orderNumber, JDialog dialog) {
        if (controller == null || orderNumber == null) return;

        try {
            Map<String, Object> details = controller.getOrderDetails("TAKEOUT", orderNumber);

            if (details.containsKey("error")) {
                updateRightPanel(dialog,
                        "<html><body style='font-family:Microsoft YaHei;color:red;padding:20px;'>" +
                                "错误：" + details.get("error") + "</body></html>", "0.00");
                return;
            }

            // 🔧 更新总金额（供支付使用）
            Object totalObj = details.get("totalAmount");
            String totalAmountStr = "0.00";
            if (totalObj instanceof Number) {
                totalAmountStr = String.format("%.2f", ((Number) totalObj).doubleValue());
            }

            // 🔧 渲染详情时指定 text/html
            JEditorPane tempDisplay = new JEditorPane("text/html", "");
            JLabel tempStatus = new JLabel();
            JLabel tempTotal = new JLabel();
            renderOrderDetails(details, tempDisplay, tempStatus, tempTotal);

            // 🔧 在对话框中查找并更新右侧面板
            String htmlContent = tempDisplay.getText();
            updateRightPanel(dialog, htmlContent, totalAmountStr);

            // 🔧【新增】更新支付面板的金额显示（含配送费）
            updatePaymentPanelInDialog(dialog, details);

        } catch (Exception e) {
            e.printStackTrace();
            updateRightPanel(dialog,
                    "<html><body style='font-family:Microsoft YaHei;color:red;padding:20px;'>" +
                            "加载失败：" + e.getMessage() + "</body></html>", "0.00");
        }
    }


    /**
     * 🔧 新增：在对话框中查找并更新支付面板金额
     */
    private void updatePaymentPanelInDialog(JDialog dialog, Map<String, Object> orderDetails) {
        // 遍历对话框组件，找到支付面板并更新
        Component[] components = dialog.getContentPane().getComponents();
        for (Component comp : components) {
            if (comp instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) comp;
                Component rightComponent = splitPane.getRightComponent();

                if (rightComponent instanceof JPanel) {
                    JPanel rightPanel = (JPanel) rightComponent;
                    // 递归查找支付面板中的金额标签
                    updatePaymentLabelsInPanel(rightPanel, orderDetails);
                }
            }
        }
    }

    /**
     * 🔧 新增：递归查找并更新支付面板中的金额标签
     */
    private void updatePaymentLabelsInPanel(JPanel panel, Map<String, Object> orderDetails) {
        Component[] components = panel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String labelText = label.getText();

                // 🔧 更新菜品金额标签
                if (labelText != null && labelText.contains("菜品：")) {
                    double itemsTotal = 0.0;
                    Object itemsTotalObj = orderDetails.get("itemsTotal");
                    if (itemsTotalObj instanceof Number) {
                        itemsTotal = ((Number) itemsTotalObj).doubleValue();
                    }
                    label.setText(String.format("菜品：%.2f 元", itemsTotal));
                }
                // 🔧 更新配送费标签
                else if (labelText != null && labelText.contains("配送费：")) {
                    double deliveryFee = 0.0;
                    Object deliveryFeeObj = orderDetails.get("deliveryFee");
                    if (deliveryFeeObj instanceof Number) {
                        deliveryFee = ((Number) deliveryFeeObj).doubleValue();
                    }
                    String deliveryMethod = (String) orderDetails.get("deliveryMethod");

                    if ("DELIVERY".equals(deliveryMethod) && deliveryFee > 0) {
                        label.setText(String.format("配送费：%.2f 元", deliveryFee));
                        label.setVisible(true);
                    } else {
                        label.setVisible(false);
                    }
                }
                // 🔧 更新总金额标签
                else if (labelText != null && labelText.contains("总金额：")) {
                    double itemsTotal = 0.0;
                    double deliveryFee = 0.0;

                    Object itemsTotalObj = orderDetails.get("itemsTotal");
                    if (itemsTotalObj instanceof Number) {
                        itemsTotal = ((Number) itemsTotalObj).doubleValue();
                    }
                    Object deliveryFeeObj = orderDetails.get("deliveryFee");
                    if (deliveryFeeObj instanceof Number) {
                        deliveryFee = ((Number) deliveryFeeObj).doubleValue();
                    }

                    double totalAmount = itemsTotal + deliveryFee;
                    label.setText(String.format("总金额：%.2f 元", totalAmount));
                }
            }
            // 递归查找子面板
            else if (comp instanceof JPanel) {
                updatePaymentLabelsInPanel((JPanel) comp, orderDetails);
            }
        }
    }

    /**
     * 🔧 新增：在对话框中查找并更新右侧面板
     */
    private void updateRightPanel(JDialog dialog, String htmlContent, String totalAmount) {
        // 遍历对话框组件，找到右侧面板并更新
        Component[] components = dialog.getContentPane().getComponents();
        for (Component comp : components) {
            if (comp instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) comp;
                Component rightComponent = splitPane.getRightComponent();

                if (rightComponent instanceof JPanel) {
                    JPanel rightPanel = (JPanel) rightComponent;
                    // 查找 JEditorPane 和 JLabel
                    updateComponentInPanel(rightPanel, htmlContent, totalAmount);
                }
            }
        }
    }


    /**
     * 🔧 新增：递归查找并更新组件（支持 JScrollPane + JLabel）
     */
    private void updateComponentInPanel(JPanel panel, String htmlContent, String totalAmount) {
        Component[] components = panel.getComponents();
        for (Component comp : components) {
            // 🔧【修复 3】直接处理 JEditorPane
            if (comp instanceof JEditorPane) {
                JEditorPane editorPane = (JEditorPane) comp;
                // 只更新不可编辑的详情面板（避免误改输入框）
                if (!editorPane.isEditable()) {
                    editorPane.setText(htmlContent);
                    editorPane.setCaretPosition(0); // 滚动到顶部
                }
            }
            // 🔧【关键修复 4】处理 JScrollPane（订单详情在滚动面板里）
            else if (comp instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) comp;
                Component view = scrollPane.getViewport().getView();

                if (view instanceof JEditorPane) {
                    JEditorPane editorPane = (JEditorPane) view;
                    if (!editorPane.isEditable()) {
                        editorPane.setText(htmlContent);
                        editorPane.setCaretPosition(0);
                    }
                } else if (view instanceof JPanel) {
                    updateComponentInPanel((JPanel) view, htmlContent, totalAmount);
                }
            }
            // 🔧【新增修复 5】处理 JLabel（查找总金额标签）
            else if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String labelText = label.getText();
                // 查找包含"总金额"的标签
                if (labelText != null && labelText.contains("总金额")) {
                    label.setText("总金额：" + totalAmount + " 元");
                    System.out.println(" 已更新总金额标签: " + totalAmount);
                }
            }
            // 🔧 递归查找子面板
            else if (comp instanceof JPanel) {
                updateComponentInPanel((JPanel) comp, htmlContent, totalAmount);
            }
        }
    }


    /**
     * 🔧 外卖订单结账界面：美化版
     */
    public void showTakeoutOrderListDialog() {
        JDialog dialog = new JDialog(this, "📦 外卖订单结账", true);
        dialog.setSize(1000, 650);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(240, 248, 255)); // 淡蓝色背景


        // ===== 状态变量 =====
        final String[] selectedOrderNumber = {null};
        final JLabel[] totalLabel = {new JLabel("总金额：0.00 元")};

        // ===== 左侧：订单列表（40%）=====
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        leftPanel.setBackground(new Color(255, 255, 255));
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // 标题美化
        JLabel listTitle = new JLabel("📋 外卖订单列表（点击选择）", SwingConstants.CENTER);
        listTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        listTitle.setForeground(new Color(30, 144, 255));
        listTitle.setBorder(new EmptyBorder(10, 0, 10, 0));
        leftPanel.add(listTitle, BorderLayout.NORTH);

        // 垂直分割：自取 | 配送
        JPanel listContainer = new JPanel(new GridLayout(2, 1, 10, 10));
        listContainer.setBackground(new Color(255, 255, 255));
        listContainer.add(createOrderListPanel("🟢 自取订单", "PICKUP", selectedOrderNumber, dialog));
        listContainer.add(createOrderListPanel("🚚 配送订单", "DELIVERY", selectedOrderNumber, dialog));
        leftPanel.add(listContainer, BorderLayout.CENTER);

        // ===== 右侧：订单详情 + 支付（60%）=====
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        rightPanel.setBackground(new Color(255, 255, 255));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // 标题美化
        JLabel detailTitle = new JLabel("💰 订单详情", SwingConstants.CENTER);
        detailTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        detailTitle.setForeground(new Color(220, 20, 60));
        detailTitle.setBorder(new EmptyBorder(10, 0, 10, 0));
        rightPanel.add(detailTitle, BorderLayout.NORTH);

        // 详情显示 - 美化背景
        JEditorPane detailDisplay = new JEditorPane("text/html",
                "<html><body style='font-family:Microsoft YaHei;padding:40px;color:#999;text-align:center;" +
                        "background:linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);border-radius:10px;'>" +
                        "<div style='font-size:48px;margin-bottom:20px;'>👈</div>" +
                        "<p style='font-size:16px;'>请点击左侧订单查看详情</p></body></html>");
        detailDisplay.setEditable(false);
        detailDisplay.setBackground(new Color(255, 255, 255));
        JScrollPane detailScroll = new JScrollPane(detailDisplay);
        detailScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        rightPanel.add(detailScroll, BorderLayout.CENTER);

        // 支付区域 - 美化
        JPanel paymentPanel = createPaymentPanel(selectedOrderNumber, totalLabel, detailDisplay, dialog);
        paymentPanel.setBackground(new Color(250, 250, 255));
        paymentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 220), 1),
                new EmptyBorder(15, 15, 15, 15)
        ));
        rightPanel.add(paymentPanel, BorderLayout.SOUTH);

        // ===== 分割面板 =====
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setDividerLocation(0.40);
        mainSplit.setResizeWeight(0.40);
        mainSplit.setDividerSize(3);  // 设置分隔条宽度
        mainSplit.setBackground(new Color(240, 240, 240));  // 设置背景色
        mainSplit.setBorder(new EmptyBorder(0, 0, 0, 0));

        dialog.add(mainSplit, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private JPanel createOrderListPanel(String title, String deliveryMethod,
                                        String[] selectedOrderNumber, JDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 149, 237), 2),
                title, TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 13), new Color(30, 144, 255)
        ));
        panel.setBackground(new Color(250, 253, 255));

        // 使用垂直布局的 JPanel
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(255, 255, 255));
        listPanel.setPreferredSize(new Dimension(380, 0));
        listPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // 加载订单列表
        List<Map<String, Object>> orders = new ArrayList<>();
        if (controller != null) {
            try {
                if ("PICKUP".equals(deliveryMethod)) {
                    orders = controller.getOrderService().loadPickupOrders();
                } else {
                    orders = controller.getOrderService().loadDeliveryOrders();
                }
            } catch (Exception e) {
                System.err.println("加载订单失败: " + e.getMessage());
            }
        }

        if (orders == null || orders.isEmpty()) {
            JLabel emptyLabel = new JLabel(" 暂无外卖订单", SwingConstants.CENTER);
            emptyLabel.setForeground(new Color(150, 150, 150));
            emptyLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            emptyLabel.setBorder(new EmptyBorder(30, 0, 30, 0));
            listPanel.add(emptyLabel);
        } else {
            for (Map<String, Object> order : orders) {
                String orderNumber = order.get("order_number") != null ?
                        order.get("order_number").toString() : "-";
                String status = order.get("order_status") != null ?
                        order.get("order_status").toString() : "未知";
                String time = order.get("order_time") != null ?
                        order.get("order_time").toString() : "-";

                JPanel rowPanel = createOrderRow(orderNumber, status, time,
                        selectedOrderNumber, dialog);
                rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
                listPanel.add(rowPanel);
                listPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(new Color(255, 255, 255));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createOrderRow(String orderNumber, String status, String time,
                                  String[] selectedOrderNumber, JDialog dialog) {
        JPanel row = new JPanel(new GridLayout(1, 3, 10, 5));
        row.setBackground(new Color(255, 255, 255));
        row.setBorder(new EmptyBorder(10, 15, 10, 15));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        // 添加圆角边框
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // 订单号（蓝色可点击）
        JLabel numberLabel = new JLabel(" " + orderNumber);
        numberLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        numberLabel.setForeground(new Color(25, 118, 210));

        // 状态
        JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        statusLabel.setForeground("制作完成".equals(status) ?
                new Color(76, 175, 80) : new Color(255, 107, 107));

        // 时间
        JLabel timeLabel = new JLabel(" " + time);
        timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        timeLabel.setForeground(new Color(120, 120, 120));
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(numberLabel);
        row.add(statusLabel);
        row.add(timeLabel);

        // 鼠标悬停效果
        row.addMouseListener(new MouseAdapter() {
            private Color originalBg = row.getBackground();

            @Override
            public void mouseClicked(MouseEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        dialog,
                        "<html><div style='padding:15px;'>" +
                                "<h3 style='color:#1976d2;margin:0;'>是否对订单结账？</h3>" +
                                "<p style='font-size:16px;margin:15px 0;'><b>" + orderNumber + "</b></p>" +
                                "<p>点击【是】将加载订单信息并进入结账流程。</p></div></html>",
                        "确认结账",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    selectedOrderNumber[0] = orderNumber;
                    loadOrderDetail(orderNumber, dialog);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(new Color(232, 245, 253));
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                        new EmptyBorder(7, 11, 7, 11)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(originalBg);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                        new EmptyBorder(8, 12, 8, 12)
                ));
            }
        });

        return row;
    }

    private JPanel createPaymentPanel(String[] selectedOrderNumber, JLabel[] totalLabel,
                                      JEditorPane detailDisplay, JDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 0, 0, 0));
        panel.setBackground(new Color(250, 250, 255));

        // 🔧【关键修改】金额显示面板 - 支持配送费显示
        JPanel amountPanel = new JPanel(new GridLayout(3, 2, 10, 10));  // 改为 3 行
        amountPanel.setBackground(new Color(255, 250, 250));
        amountPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 200, 200), 1),
                new EmptyBorder(15, 15, 15, 15)
        ));

        // 🔧 新增：菜品总价标签
        JLabel itemsTotalLabel = new JLabel("菜品：0.00 元");
        itemsTotalLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        // 🔧 新增：配送费标签（默认隐藏）
        JLabel deliveryFeeLabel = new JLabel("配送费：0.00 元");
        deliveryFeeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        deliveryFeeLabel.setVisible(false);  // 默认隐藏

        // 总金额标签
        JLabel totalAmountLabel = new JLabel("总金额：0.00 元");
        totalAmountLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        totalAmountLabel.setForeground(new Color(220, 20, 60));
        totalLabel[0] = totalAmountLabel;

        // 🔧 组装金额面板
        amountPanel.add(new JLabel("菜品金额:", SwingConstants.RIGHT));
        amountPanel.add(itemsTotalLabel);

        amountPanel.add(new JLabel("配送费:", SwingConstants.RIGHT));
        amountPanel.add(deliveryFeeLabel);  // 可能隐藏

        amountPanel.add(new JLabel("应付金额:", SwingConstants.RIGHT));
        amountPanel.add(totalAmountLabel);

        panel.add(amountPanel, BorderLayout.NORTH);

        // 支付输入 - 美化
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        inputPanel.setBackground(new Color(250, 250, 255));

        JLabel paymentLabel = new JLabel("支付金额:");
        paymentLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        paymentLabel.setForeground(new Color(60, 60, 60));

        JTextField paymentField = new JTextField(12);
        paymentField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        paymentField.setPreferredSize(new Dimension(150, 35));
        paymentField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        paymentField.setBackground(new Color(255, 255, 255));

        inputPanel.add(paymentLabel);
        inputPanel.add(paymentField);
        panel.add(inputPanel, BorderLayout.CENTER);

        // 按钮 - 美化（保持不变）
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        btnPanel.setBackground(new Color(250, 250, 255));

        JButton confirmBtn = new JButton(" 确认结账");
        confirmBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        confirmBtn.setBackground(new Color(76, 175, 80));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setBorderPainted(false);
        confirmBtn.setPreferredSize(new Dimension(130, 40));
        confirmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton cancelBtn = new JButton(" 取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        cancelBtn.setBackground(new Color(158, 158, 158));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setPreferredSize(new Dimension(100, 40));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 按钮悬停效果（保持不变）
        confirmBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                confirmBtn.setBackground(new Color(56, 142, 60));
            }

            public void mouseExited(MouseEvent e) {
                confirmBtn.setBackground(new Color(76, 175, 80));
            }
        });

        cancelBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                cancelBtn.setBackground(new Color(117, 117, 117));
            }

            public void mouseExited(MouseEvent e) {
                cancelBtn.setBackground(new Color(158, 158, 158));
            }
        });

        confirmBtn.addActionListener(e -> {
            if (selectedOrderNumber[0] == null || selectedOrderNumber[0].isEmpty()) {
                showError("请先选择订单");
                return;
            }

            try {
                double payment = Double.parseDouble(paymentField.getText().trim());

                // 🔧【关键修复】先验证订单状态，再执行结账
                if (controller != null) {
                    // 1. 查询订单详情，检查状态
                    Map<String, Object> orderDetails = controller.getOrderDetails("TAKEOUT", selectedOrderNumber[0]);

                    if (orderDetails.containsKey("error")) {
                        showError("查询订单失败：" + orderDetails.get("error"));
                        return;
                    }

                    // 2. 获取订单号和配送方式
                    String orderNumber = selectedOrderNumber[0];
                    Order order = controller.getOrderService().findActiveOrderByOrderNumber(orderNumber);

                    if (order == null) {
                        showError("订单不存在或已结账：" + orderNumber);
                        return;
                    }

                    // 3. 🔧 验证订单状态
                    String deliveryMethod = order.getDeliveryMethod();

                    if ("PICKUP".equals(deliveryMethod)) {
                        // 自取订单：检查 Tables.OrderStatus
                        Tables.OrderStatus orderStatus = orderDetails.get("orderStatus") != null ?
                                (Tables.OrderStatus) orderDetails.get("orderStatus") :
                                controller.getOrderService().getOrderStatusByOrderNumber(orderNumber);

                        if (orderStatus != Tables.OrderStatus.ORDERED_FINISHED) {
                            showError(" 自取订单尚未制作完成！\n" +
                                    "当前状态：" + (orderStatus != null ? orderStatus.getDisplayName() : "未知") +
                                    "\n\n请将状态更新为【制作完成】后才能结账。");
                            return;
                        }
                    } else if ("DELIVERY".equals(deliveryMethod)) {
                        // 配送订单：检查 DeliveryStatus
                        Order.DeliveryStatus deliveryStatus = order.getDeliveryStatus();

                        if (deliveryStatus != Order.DeliveryStatus.DELIVERED) {
                            showError(" 配送订单尚未送达！\n" +
                                    "当前状态：" + (deliveryStatus != null ? deliveryStatus.getDisplayName() : "未知") +
                                    "\n\n请将配送状态更新为【已送达】后才能结账。");
                            return;
                        }
                    }

                    // 4.  状态验证通过，执行结账
                    controller.handleCheckoutSubmit("TAKEOUT", selectedOrderNumber[0], payment);

                    JOptionPane.showMessageDialog(dialog, " 结账成功！", "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                }
            } catch (NumberFormatException ex) {
                showError("请输入有效金额");
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("结账失败：" + ex.getMessage());
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

// 🔧【关键】加载订单详情时更新金额显示
        SwingUtilities.invokeLater(() -> {
            if (selectedOrderNumber[0] != null && !selectedOrderNumber[0].isEmpty()) {
                try {
                    if (controller != null) {
                        Map<String, Object> orderDetails =
                                controller.getOrderDetails("TAKEOUT", selectedOrderNumber[0]);

                        if (orderDetails != null && !orderDetails.containsKey("error")) {
                            updatePaymentPanelAmounts(
                                    orderDetails,
                                    itemsTotalLabel,
                                    deliveryFeeLabel,
                                    totalAmountLabel
                            );
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        return panel;
    }


    /**
     * 🔧 新增：更新支付面板的金额显示（支持配送费）
     */
    private void updatePaymentPanelAmounts(Map<String, Object> orderDetails,
                                           JLabel itemsTotalLabel,
                                           JLabel deliveryFeeLabel,
                                           JLabel totalAmountLabel) {
        try {
            // 获取菜品总价
            double itemsTotal = 0.0;
            Object itemsTotalObj = orderDetails.get("itemsTotal");
            if (itemsTotalObj instanceof Number) {
                itemsTotal = ((Number) itemsTotalObj).doubleValue();
            }

            // 获取配送费
            double deliveryFee = 0.0;
            Object deliveryFeeObj = orderDetails.get("deliveryFee");
            if (deliveryFeeObj instanceof Number) {
                deliveryFee = ((Number) deliveryFeeObj).doubleValue();
            }

            // 获取配送方式
            String deliveryMethod = (String) orderDetails.get("deliveryMethod");

            // 更新菜品总价标签
            itemsTotalLabel.setText(String.format("菜品：%.2f 元", itemsTotal));

            // 🔧 根据配送方式显示/隐藏配送费标签
            if ("DELIVERY".equals(deliveryMethod) && deliveryFee > 0) {
                deliveryFeeLabel.setText(String.format("配送费：%.2f 元", deliveryFee));
                deliveryFeeLabel.setVisible(true);
            } else {
                deliveryFeeLabel.setVisible(false);
            }

            // 更新总金额标签
            double totalAmount = itemsTotal + deliveryFee;
            totalAmountLabel.setText(String.format("总金额：%.2f 元", totalAmount));

        } catch (Exception e) {
            System.err.println("更新支付面板金额失败: " + e.getMessage());
        }
    }


    public String[] showChangeTableDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));

        JLabel fromLabel = new JLabel("请输入要换桌的餐桌ID（如 7 或 7a）:");
        JTextField fromField = new JTextField(10);

        JLabel toLabel = new JLabel("请输入目标空闲餐桌ID（如 8 或 8a）:");
        JTextField toField = new JTextField(10);

        panel.add(fromLabel);
        panel.add(fromField);
        panel.add(toLabel);
        panel.add(toField);

        int result = JOptionPane.showConfirmDialog(this, panel, "换桌", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String fromInput = fromField.getText().trim();
            String toInput = toField.getText().trim();

            if (fromInput.isEmpty() || toInput.isEmpty()) {
                showError("请输入完整的餐桌ID！");
                return null;
            }
            // fromInput：要换桌的餐桌编号（如 "7a"）；
            //toInput：目标餐桌编号（如 "8"）；
            // 然后返回一个包含这两个值的字符串数组：
            return new String[]{fromInput, toInput};
        } else {
            return null; // 用户点击取消
        }
    }


    public Map<String, Object> showReservationDialog(String mode, Map<String, Object> existingReservation) {
        final String finalMode = (mode == null) ? "CREATE" : mode;

        JDialog dialog = new JDialog(this, "📅 预约管理", true);
        dialog.setSize(680, 720);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // ===== 模式选择 =====
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        modePanel.setBackground(new Color(245, 248, 255));

        JRadioButton createRadio = new JRadioButton("🆕 新建预约");
        JRadioButton cancelRadio = new JRadioButton("❌ 取消预约");
        JRadioButton editTimeRadio = new JRadioButton("⏰ 修改資料");
        JRadioButton assignTableRadio = new JRadioButton("🔒 分配餐桌");

        ButtonGroup group = new ButtonGroup();
        group.add(createRadio);
        group.add(cancelRadio);
        group.add(editTimeRadio);
        group.add(assignTableRadio);

        switch (mode) {
            case "CANCEL" -> cancelRadio.setSelected(true);
            case "EDIT_TIME" -> editTimeRadio.setSelected(true);
            case "ASSIGN" -> assignTableRadio.setSelected(true);
            default -> createRadio.setSelected(true);
        }

        modePanel.add(createRadio);
        modePanel.add(cancelRadio);
        modePanel.add(editTimeRadio);
        modePanel.add(assignTableRadio);
        dialog.add(modePanel, BorderLayout.NORTH);

        // ===== 表单面板 =====
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(new Color(245, 248, 255));

        JTextField idField = new JTextField(10);
        JTextField nameField = new JTextField(20);
        JTextField phoneField = new JTextField(15);
        JTextField timeField = new JTextField(20);
        idField.setEditable(false);

        if (existingReservation != null) {
            idField.setText(String.valueOf(existingReservation.getOrDefault("reservation_id", "")));
            nameField.setText(String.valueOf(existingReservation.getOrDefault("customer_name", "")));
            phoneField.setText(String.valueOf(existingReservation.getOrDefault("customer_phone", "")));
            timeField.setText(String.valueOf(existingReservation.getOrDefault("reservation_time", "")));
        }

        rebuildFormPanel(formPanel, mode, idField, nameField, phoneField, timeField, existingReservation);
        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】将 formPanel 包装在 JScrollPane 中（支持滚动）
        // ═══════════════════════════════════════════════════════════
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);  // 去除边框
        scrollPane.getViewport().setBackground(new Color(245, 248, 255));  // 设置背景色
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);  // 需要时显示垂直滚动条
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);  // 不显示水平滚动条

        dialog.add(scrollPane, BorderLayout.CENTER);

        // ===== 模式切换监听 =====
        ActionListener switchMode = e -> {
            String selectedMode = createRadio.isSelected() ? "CREATE" :
                    cancelRadio.isSelected() ? "CANCEL" :
                            editTimeRadio.isSelected() ? "EDIT_TIME" : "ASSIGN";
            rebuildFormPanel(formPanel, selectedMode, idField, nameField, phoneField, timeField, existingReservation);
        };
        createRadio.addActionListener(switchMode);
        cancelRadio.addActionListener(switchMode);
        editTimeRadio.addActionListener(switchMode);
        assignTableRadio.addActionListener(switchMode);

        // ===== 按钮 =====
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton confirmBtn = new JButton("✓ 确认");
        JButton cancelBtn = new JButton("✗ 取消");
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        final Map<String, Object>[] result = new Map[1];


        confirmBtn.addActionListener(e -> {


            // 🔧【關鍵修復】使用標誌位控制是否關閉對話框
            boolean shouldClose = true;
            //String selectedMode = finalMode;   错误代码：直接使用了方法入口的 finalMode，不会随界面切换改变

            //  正确代码：替换为下面这段，实时读取界面上选中的单选框
            String selectedMode = createRadio.isSelected() ? "CREATE" :
                    cancelRadio.isSelected() ? "CANCEL" :
                            editTimeRadio.isSelected() ? "EDIT_TIME" : "ASSIGN";

            System.out.println("🎯 [EXEC] selectedMode 实际值: [" + selectedMode + "]");
            try {
                if ("CREATE".equals(selectedMode)) {
                    System.out.println("✅ [EXEC] 进入 CREATE 分支");  // ← 新增
                    // ═══════════════════════════════════════════════════════════
                    // 【步驟1】收集基礎信息（姓名、電話、時間）
                    // ═══════════════════════════════════════════════════════════
                    String name = nameField.getText().trim();
                    String phone = phoneField.getText().trim();

                    JSpinner dateSpinner = (JSpinner) formPanel.getClientProperty("dateSpinner");
                    JTextField timeFieldComp = (JTextField) formPanel.getClientProperty("timeField");

                    if (dateSpinner == null || timeFieldComp == null) {
                        showError("表單初始化失敗，請重試");
                        shouldClose = false;
                        return;
                    }

                    Date selectedDate = (Date) dateSpinner.getValue();
                    String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(selectedDate);
                    String timeStr = timeFieldComp.getText().trim();

                    // 驗證時間格式
                    if (!timeStr.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                        showError("時間格式錯誤！請輸入 HH:mm（例：18:30）");
                        timeFieldComp.requestFocus();
                        shouldClose = false;
                        return;
                    }
                    if (name.isEmpty() || phone.isEmpty()) {
                        showError("請填寫必填字段");
                        shouldClose = false;
                        return;
                    }
                    String reservationTime = dateStr + " " + timeStr;

                    // ═══════════════════════════════════════════════════════════
                    // 【步驟2】判斷餐桌選擇模式
                    // ═══════════════════════════════════════════════════════════
                    JRadioButton manualModeRadio = (JRadioButton) formPanel.getClientProperty("manualModeRadio");
                    JRadioButton quantityModeRadio = (JRadioButton) formPanel.getClientProperty("quantityModeRadio");

                    boolean isManualMode = (manualModeRadio != null && manualModeRadio.isSelected());

                    // ═══════════════════════════════════════════════════════════
                    // 【步驟3】收集餐桌類型 + 1.5小時確認
                    // ═══════════════════════════════════════════════════════════
                    JRadioButton personalRadio = (JRadioButton) formPanel.getClientProperty("tableTypePersonal");
                    JRadioButton mergedRadio = (JRadioButton) formPanel.getClientProperty("tableTypeMerged");
                    JRadioButton groupRadio = (JRadioButton) formPanel.getClientProperty("tableTypeGroup");

                    String tableType;
                    if (personalRadio != null && personalRadio.isSelected()) {
                        tableType = "MAIN";
                    } else if (mergedRadio != null && mergedRadio.isSelected()) {
                        tableType = "MERGED";
                    } else {
                        tableType = "GROUP";
                    }

                    JRadioButton within15hYes = (JRadioButton) formPanel.getClientProperty("within15hYes");
                    JRadioButton within15hNo = (JRadioButton) formPanel.getClientProperty("within15hNo");
                    boolean within15Hours = (within15hYes != null && within15hYes.isSelected());

                    // ═══════════════════════════════════════════════════════════
                    // 【步驟4】根據模式分別驗證（🔧 核心修復位置）
                    // ═══════════════════════════════════════════════════════════
                    List<String> selectedTables = new ArrayList<>();
                    Map<String, Integer> tableConfig = new HashMap<>();

                    if (isManualMode) {
                        // ── 模式A：手動輸入餐桌號 ──
                        JTextField manualTableField = (JTextField) formPanel.getClientProperty("manualTableField");
                        if (manualTableField == null) {
                            showError("表單初始化失敗，請重試");
                            shouldClose = false;
                            return;
                        }

                        String manualTablesInput = manualTableField.getText().trim();
                        if (manualTablesInput.isEmpty()) {
                            showError("⚠️ 手動輸入模式下，請輸入餐桌號！");
                            manualTableField.requestFocus();
                            shouldClose = false;
                            return;
                        }

                        if (!manualTablesInput.matches("\\d+(,\\d+)*")) {
                            showError("⚠️ 餐桌號格式錯誤！請輸入數字，用逗號分隔（如：7,8,13）");
                            manualTableField.requestFocus();
                            shouldClose = false;
                            return;
                        }

                        String[] tableNumbers = manualTablesInput.split(",");

                        // 🔧【核心修復】驗證餐桌數量是否符合餐桌類型限制
                        int inputCount = tableNumbers.length;
                        int maxAllowed = 1;  // 默認個人桌
                        String typeName = "個人桌";

                        if (personalRadio != null && personalRadio.isSelected()) {
                            maxAllowed = 1;
                            typeName = "個人桌";
                        } else if (mergedRadio != null && mergedRadio.isSelected()) {
                            maxAllowed = 2;
                            typeName = "合併桌";
                        } else if (groupRadio != null && groupRadio.isSelected()) {
                            maxAllowed = 99;
                            typeName = "聚餐桌";
                        }

                        // 🔧【關鍵】驗證失敗時：顯示錯誤 + 聚焦 + 設置標誌位 + 直接返回
                        if (inputCount > maxAllowed) {
                            showError(" " + typeName + " 最多只能預約 " + maxAllowed + " 張餐桌！\n" +
                                    "當前輸入：" + inputCount + " 張 (" + manualTablesInput + ")");
                            manualTableField.requestFocus();
                            manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                            shouldClose = false;  //  關鍵：標記不關閉對話框
                            return;  //  從 Lambda 返回，跳過後續邏輯
                        }

                        for (String num : tableNumbers) {
                            selectedTables.add(num.trim());
                        }

                    } else {
                        // ── 模式B：填寫桌子數量 ──
                        System.out.println("🔍 [DEBUG] 数量模式验证开始...");


                        // ── 模式B：填寫桌子數量 ──
                        JRadioButton twoSeatRadio = (JRadioButton) formPanel.getClientProperty("twoSeatRadio");  // ✅ 改为 JRadioButton + 正确的 key
                        JTextField twoSeatQty = (JTextField) formPanel.getClientProperty("twoSeatQty");
                        JRadioButton fourSeatRadio = (JRadioButton) formPanel.getClientProperty("fourSeatRadio");  // ✅ 改为 JRadioButton + 正确的 key
                        JTextField fourSeatQty = (JTextField) formPanel.getClientProperty("fourSeatQty");
                        JRadioButton sixSeatRadio = (JRadioButton) formPanel.getClientProperty("sixSeatRadio");    // ✅ 改为 JRadioButton + 正确的 key
                        JTextField sixSeatQty = (JTextField) formPanel.getClientProperty("sixSeatQty");

                        // 🔧 调试：打印组件状态
//                        System.out.println("  组件状态:");
//                        System.out.println("    twoSeatCheck: " + (twoSeatRadio != null ? "存在" : "NULL") +
//                                ", isSelected=" + (twoSeatRadio != null ? twoSeatRadio.isSelected() : "N/A"));
//                        System.out.println("    twoSeatQty: " + (twoSeatQty != null ? "存在" : "NULL") +
//                                ", text='" + (twoSeatQty != null ? twoSeatQty.getText() : "N/A") + "'");
//                        System.out.println("    fourSeatCheck: " + (fourSeatRadio != null ? "存在" : "NULL") +
//                                ", isSelected=" + (fourSeatRadio != null ? fourSeatRadio.isSelected() : "N/A"));
//                        System.out.println("    fourSeatQty: " + (fourSeatQty != null ? "存在" : "NULL") +
//                                ", text='" + (fourSeatQty != null ? fourSeatQty.getText() : "N/A") + "'");
//                        System.out.println("    sixSeatCheck: " + (sixSeatRadio != null ? "存在" : "NULL") +
//                                ", isSelected=" + (sixSeatRadio != null ? sixSeatRadio.isSelected() : "N/A"));
//                        System.out.println("    sixSeatQty: " + (sixSeatQty != null ? "存在" : "NULL") +
//                                ", text='" + (sixSeatQty != null ? sixSeatQty.getText() : "N/A") + "'");

                        // 驗證並收集2人桌
                        if (twoSeatRadio != null && twoSeatRadio.isSelected()) {
                            String qtyStr = twoSeatQty.getText().trim();
                            System.out.println("   处理2人桌: qtyStr='" + qtyStr + "'");
                            if (qtyStr.isEmpty()) {
                                showError(" 2人桌已勾選，請輸入數量！");
                                twoSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                            try {
                                int qty = Integer.parseInt(qtyStr);
                                if (qty <= 0) {
                                    showError(" 2人桌數量必須大於0！");
                                    twoSeatQty.requestFocus();
                                    shouldClose = false;
                                    return;
                                }
                                tableConfig.put("2", qty);
                                System.out.println("   2人桌配置已添加: " + qty + "张");
                            } catch (NumberFormatException ex) {
                                showError(" 2人桌數量格式錯誤！");
                                twoSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        } else {
                            System.out.println("   2人桌未勾选，跳过");
                        }

                        // 驗證並收集4人桌
                        if (fourSeatRadio != null && fourSeatRadio.isSelected()) {
                            String qtyStr = fourSeatQty.getText().trim();
                            System.out.println("   处理4人桌: qtyStr='" + qtyStr + "'");
                            if (qtyStr.isEmpty()) {
                                showError(" 4人桌已勾選，請輸入數量！");
                                fourSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                            try {
                                int qty = Integer.parseInt(qtyStr);
                                if (qty <= 0) {
                                    showError(" 4人桌數量必須大於0！");
                                    fourSeatQty.requestFocus();
                                    shouldClose = false;
                                    return;
                                }
                                tableConfig.put("4", qty);
                                System.out.println("   4人桌配置已添加: " + qty + "张");
                            } catch (NumberFormatException ex) {
                                showError(" 4人桌數量格式錯誤！");
                                fourSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        } else {
                            System.out.println("   4人桌未勾选，跳过");
                        }

                        // 驗證並收集6人桌
                        if (sixSeatRadio != null && sixSeatRadio.isSelected()) {
                            String qtyStr = sixSeatQty.getText().trim();
                            System.out.println("   处理6人桌: qtyStr='" + qtyStr + "'");
                            if (qtyStr.isEmpty()) {
                                showError(" 6人桌已勾選，請輸入數量！");
                                sixSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                            try {
                                int qty = Integer.parseInt(qtyStr);
                                if (qty <= 0) {
                                    showError(" 6人桌數量必須大於0！");
                                    sixSeatQty.requestFocus();
                                    shouldClose = false;
                                    return;
                                }
                                tableConfig.put("6", qty);
                                System.out.println("   6人桌配置已添加: " + qty + "张");
                            } catch (NumberFormatException ex) {
                                showError(" 6人桌數量格式錯誤！");
                                sixSeatQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        } else {
                            System.out.println("   6人桌未勾选，跳过");
                        }

                        // 🔧 关键调试：打印 tableConfig 内容
                        System.out.println("   tableConfig 内容: " + tableConfig);
                        System.out.println("   tableConfig.isEmpty() = " + tableConfig.isEmpty());
                        System.out.println("   tableConfig.size() = " + tableConfig.size());

                        // 驗證：至少選擇一種桌子（僅數量模式需要）
                        if (tableConfig.isEmpty()) {
                            System.err.println("   错误：tableConfig 为空！");
                            showError(" 請至少選擇一種桌子配置！");
                            shouldClose = false;
                            return;
                        } else {
                            System.out.println("   验证通过：tableConfig 不为空");
                        }

                        // 🔧【核心驗證】根據餐桌類型限制數量
                        int totalTables = tableConfig.values().stream().mapToInt(Integer::intValue).sum();
                        System.out.println("   餐桌类型: " + tableType);
                        System.out.println("   总桌子数: " + totalTables);
                        System.out.println("   tableConfig.keySet(): " + tableConfig.keySet());

                        if ("MAIN".equals(tableType)) {
                            // 個人桌：只能選 1 張桌子
                            System.out.println("  验证个人桌规则...");
                            if (totalTables != 1) {
                                System.err.println("   个人桌验证失败: totalTables=" + totalTables);
                                showError(" 個人桌只能選擇 1 張桌子！\n當前選擇：" + totalTables + " 張");
                                shouldClose = false;
                                return;
                            }
                            System.out.println("   个人桌验证通过");
                        } else if ("MERGED".equals(tableType)) {
                            // 合併桌：必須是 2 張相同容量的桌子
                            System.out.println("  验证合并桌规则...");
                            if (tableConfig.size() != 1) {
                                System.err.println("   合并桌验证失败: tableConfig.size()=" + tableConfig.size());
                                showError(" 合併桌只能選擇一種容量的桌子！");
                                shouldClose = false;
                                return;
                            }
                            int qty = tableConfig.values().iterator().next();
                            if (qty != 2) {
                                System.err.println("   合并桌验证失败: qty=" + qty);
                                showError(" 合併桌必須選擇 2 張相同容量的桌子！\n當前選擇：" + qty + " 張");
                                shouldClose = false;
                                return;
                            }
                            System.out.println("   合并桌验证通过");
                        } else if ("GROUP".equals(tableType)) {
                            // 聚餐桌：只能選擇 6 人桌，數量 >= 3 張
                            System.out.println("  验证聚餐桌规则...");
                            if (tableConfig.size() != 1) {
                                System.err.println("   聚餐桌验证失败: tableConfig.size()=" + tableConfig.size());
                                showError(" 聚餐桌只能選擇一種容量的桌子！");
                                shouldClose = false;
                                return;
                            }
                            String capacityKey = tableConfig.keySet().iterator().next();
                            if (!"6".equals(capacityKey)) {
                                System.err.println("   聚餐桌验证失败: capacityKey=" + capacityKey);
                                showError(" 聚餐桌只能使用 6 人桌！\n當前選擇：" + capacityKey + "人桌");
                                shouldClose = false;
                                return;
                            }
                            int qty = tableConfig.values().iterator().next();
                            if (qty < 3) {
                                System.err.println("   聚餐桌验证失败: qty=" + qty);
                                showError(" 聚餐桌必須選擇 3 張或以上的 6 人桌！\n當前數量：" + qty + "張");
                                shouldClose = false;
                                return;
                            }
                            System.out.println("   聚餐桌验证通过");
                        }

                        System.out.println(" [DEBUG] 数量模式验证完成 \n");
                    }

                    JRadioButton preOrderYes = (JRadioButton) formPanel.getClientProperty("preOrderYes");
                    JCheckBox prepaidCheck = (JCheckBox) formPanel.getClientProperty("prepaidCheck");
                    JTextField prepaidField = (JTextField) formPanel.getClientProperty("prepaidAmount");
                    JTextArea notes = (JTextArea) formPanel.getClientProperty("notesArea");

                    double amount = 0.0;
                    if (prepaidCheck != null && prepaidCheck.isSelected()) {
                        try {
                            amount = Double.parseDouble(prepaidField.getText());
                        } catch (NumberFormatException ex) {
                            showError("⚠️ 預付金額格式錯誤！");
                            shouldClose = false;
                            return;
                        }
                    }

                    // 🔧 獲取備註內容（允許為 null 或空字符串）
                    String notesText = null;
                    if (notes != null) {
                        notesText = notes.getText().trim();
                        if (notesText.isEmpty()) {
                            notesText = null;
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 【步驟6】🔧【核心修復】計算桌子總數 + 組裝結果
                    // ═══════════════════════════════════════════════════════════

                    // 🔧 關鍵修復：從數據計算 tableCount，不再依賴 tableSpinner
                    int totalTableCount;
                    if (isManualMode) {
                        // 手動模式：桌子數量 = 輸入的餐桌號數量
                        totalTableCount = selectedTables.size();
                    } else {
                        // 數量模式：桌子數量 = tableConfig 中所有數量的總和
                        totalTableCount = tableConfig.values().stream()
                                .mapToInt(Integer::intValue)
                                .sum();
                    }

                    // 組裝結果
                    result[0] = new HashMap<>();
                    result[0].put("mode", "CREATE");
                    result[0].put("customerName", name);
                    result[0].put("customerPhone", phone);
                    result[0].put("reservationTime", reservationTime);

                    // 🔧 使用計算出的總數，替代 tableSpinner.getValue()
                    result[0].put("tableCount", totalTableCount);

                    result[0].put("tableType", tableType);
                    result[0].put("within15Hours", within15Hours);

                    // 🔧 根據模式設置不同的字段
                    result[0].put("tableSelectionMode", isManualMode ? "MANUAL" : "QUANTITY");
                    result[0].put("tableConfig", tableConfig.isEmpty() ? null : tableConfig);
                    result[0].put("selectedTables", selectedTables.isEmpty() ? null : selectedTables);

                    result[0].put("preOrder", preOrderYes.isSelected());  // 🔧 確保傳遞
                    result[0].put("isPrepaid", prepaidCheck != null && prepaidCheck.isSelected());
                    result[0].put("prepaidAmount", amount);
                    result[0].put("notes", notesText);  // 🔧 允許為 null

                }

//                else if ("ASSIGN".equals(selectedMode)) {
//                    System.out.println(" [EXEC] 进入 ASSIGN 分支");  // ← 新增
//                    String resId = idField.getText().trim();
//                    if (resId.isEmpty()) {
//                        showError("請輸入預約號");
//                        shouldClose = false;
//                        return;
//                    }
//
//                    @SuppressWarnings("unchecked")
//                    Map<String, JCheckBox> tableBoxes = (Map<String, JCheckBox>) formPanel.getClientProperty("assignTableCheckBoxes");
//                    List<String> selectedTables = new ArrayList<>();
//                    if (tableBoxes != null) {
//                        for (Map.Entry<String, JCheckBox> entry : tableBoxes.entrySet()) {
//                            if (entry.getValue().isSelected()) {
//                                String num = entry.getKey().replaceAll("[^0-9]", "");
//                                if (!num.isEmpty()) selectedTables.add(num);
//                            }
//                        }
//                    }
//                    if (selectedTables.isEmpty()) {
//                        showError("請至少選擇一張餐桌");
//                        shouldClose = false;
//                        return;
//                    }
//
//                    result[0] = new HashMap<>();
//                    result[0].put("mode", "ASSIGN");
//                    result[0].put("reservationId", resId);
//                    result[0].put("selectedTables", selectedTables);
//
//                }
                else if ("ASSIGN".equals(selectedMode)) {
                    System.out.println("🎯 [EXEC] 进入 ASSIGN 分支");

                    String resId = idField.getText().trim();
                    if (resId.isEmpty()) {
                        showError("請輸入預約號");
                        shouldClose = false;
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, JCheckBox> tableBoxes = (Map<String, JCheckBox>) formPanel.getClientProperty("assignTableCheckBoxes");
                    List<String> selectedTables = new ArrayList<>();
                    if (tableBoxes != null) {
                        for (Map.Entry<String, JCheckBox> entry : tableBoxes.entrySet()) {
                            if (entry.getValue().isSelected()) {
                                String num = entry.getKey().replaceAll("[^0-9]", "");
                                if (!num.isEmpty()) selectedTables.add(num);
                            }
                        }
                    }
                    if (selectedTables.isEmpty()) {
                        showError("請至少選擇一張餐桌");
                        shouldClose = false;
                        return;
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【新增】餐桌连续性和相邻性验证（聚餐桌/合并桌专用）
                    // ═══════════════════════════════════════════════════════════

                    //  先查询预约详情，获取餐桌类型配置
                    TableReservation reservation = null;
                    if (controller != null) {
                        reservation = controller.getReservationDetail(resId);
                    }

                    if (reservation != null) {
                        String groupType = reservation.getGroupType();  // MAIN / MERGED / GROUP
                        String configDesc = reservation.getTableConfigDesc();

                        // 🔧 解析餐桌容量（从配置描述中提取）
                        int requiredCapacity = 0;
                        if (configDesc != null) {
                            String normalized = configDesc.replaceAll("\\s+", "");
                            if (normalized.contains("2人桌")) requiredCapacity = 2;
                            else if (normalized.contains("4人桌")) requiredCapacity = 4;
                            else if (normalized.contains("6人桌")) requiredCapacity = 6;
                        }

                        // 🔧【聚餐桌验证：连续桌号】
                        if ("GROUP".equals(groupType) && selectedTables.size() >= 3) {
                            try {
                                // 1. 转为整数数组并排序
                                List<Integer> nums = selectedTables.stream()
                                        .map(Integer::parseInt)
                                        .sorted()
                                        .collect(Collectors.toList());

                                // 2. 检查是否连续（相邻差值必须为1）
                                for (int i = 1; i < nums.size(); i++) {
                                    if (nums.get(i) - nums.get(i - 1) != 1) {
                                        showError(" 聚餐桌桌号必须连续！\n" +
                                                "当前输入：" + String.join(",", selectedTables) + "\n" +
                                                "缺少桌号：" + (nums.get(i - 1) + 1));
                                        shouldClose = false;
                                        return;
                                    }
                                }
                                System.out.println(" 聚餐桌连续验证通过: " + selectedTables);
                            } catch (NumberFormatException et) {
                                showError(" 餐桌号格式错误！请输入纯数字");
                                shouldClose = false;
                                return;
                            }
                        }

                        // 🔧【合并桌验证：左右相邻（每行3张）】
                        else if ("MERGED".equals(groupType) && selectedTables.size() == 2) {
                            try {
                                int t1 = Integer.parseInt(selectedTables.get(0));
                                int t2 = Integer.parseInt(selectedTables.get(1));

                                // 计算行号：(桌号-1) / 3
                                int row1 = (t1 - 1) / 3;
                                int row2 = (t2 - 1) / 3;

                                // 必须在同一行 + 编号相差1
                                if (row1 != row2) {
                                    showError(" 合并桌必须在同一行！\n" +
                                            "桌" + t1 + "在第" + (row1 + 1) + "行，桌" + t2 + "在第" + (row2 + 1) + "行");
                                    shouldClose = false;
                                    return;
                                }
                                if (Math.abs(t1 - t2) != 1) {
                                    showError(" 合并桌必须左右相邻！\n" +
                                            "桌" + t1 + " 和 桌" + t2 + " 不相邻");
                                    shouldClose = false;
                                    return;
                                }
                                System.out.println(" 合并桌相邻验证通过: " + t1 + "+" + t2 + " (第" + (row1 + 1) + "行)");
                            } catch (NumberFormatException ex) {
                                showError(" 餐桌号格式错误！请输入纯数字");
                                shouldClose = false;
                                return;
                            }
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧 辅助方法：验证餐桌号是否连续（聚餐桌用）
                    // ═══════════════════════════════════════════════════════════
                    // 注意：由于在 lambda 内，需定义为局部方法或使用工具类
                    // 这里直接内联验证逻辑：

                    // ═══════════════════════════════════════════════════════════
                    // 🔧 辅助方法：验证餐桌是否左右相邻（合并桌用，每行3张）
                    // ═══════════════════════════════════════════════════════════

                    result[0] = new HashMap<>();
                    result[0].put("mode", "ASSIGN");
                    result[0].put("reservationId", resId);
                    result[0].put("selectedTables", selectedTables);
                } else if ("CANCEL".equals(selectedMode)) {
                    System.out.println(" [EXEC] 进入 CANCEL 分支");  // ← 新增
                    String id = idField.getText().trim();
                    if (id.isEmpty()) {
                        showError("請輸入預約號");
                        shouldClose = false;
                        return;
                    }
                    result[0] = Map.of("mode", "CANCEL", "reservationId", id);

                } else {  // EDIT_TIME
                    System.out.println("⚠️ [EXEC] 进入 EDIT_TIME/默认分支, selectedMode=[" + selectedMode + "]");  // ← 新增
                    String id = idField.getText().trim();
                    if (id.isEmpty()) {
                        showError("請輸入預約號");
                        shouldClose = false;
                        return;
                    }

                    Map<String, Object> edits = new HashMap<>();

                    // ── 修改時間 ──
                    JCheckBox checkTime = (JCheckBox) formPanel.getClientProperty("editCheckTime");
                    JTextField newTimeField = (JTextField) formPanel.getClientProperty("editNewTime");
                    if (checkTime != null && checkTime.isSelected()) {
                        String newTime = newTimeField.getText().trim();
                        if (newTime.isEmpty() || !newTime.matches("^\\d{4}-\\d{2}-\\d{2} [0-2]?\\d:[0-5]\\d$")) {
                            showError("新時間格式錯誤！請使用 yyyy-MM-dd HH:mm");
                            newTimeField.requestFocus();
                            shouldClose = false;
                            return;
                        }
                        edits.put("newReservationTime", newTime);
                    }

                    // ── 修改桌子配置 ──
                    JCheckBox checkConfig = (JCheckBox) formPanel.getClientProperty("editCheckConfig");
                    if (checkConfig != null && checkConfig.isSelected()) {
                        Map<String, Integer> tableConfig = new HashMap<>();

                        // ── 2人桌验证 ──
                        JCheckBox twoCheck = (JCheckBox) formPanel.getClientProperty("editTwoCheck");
                        JTextField twoQty = (JTextField) formPanel.getClientProperty("editTwoQty");
                        if (twoCheck != null && twoCheck.isSelected()) {
                            try {
                                int qty = Integer.parseInt(twoQty.getText().trim());
                                if (qty > 0) {
                                    // 🔧【新增】2人桌只能选1张
                                    if (qty != 1) {
                                        showError(" 2人桌只能选择 1 张！\n当前输入：" + qty + " 张");
                                        twoQty.requestFocus();
                                        shouldClose = false;
                                        return;
                                    }
                                    tableConfig.put("2", qty);
                                }
                            } catch (NumberFormatException ex) {
                                showError(" 2人桌数量格式错误！请输入有效数字。");
                                twoQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        }
                        // ── 4人桌验证 ──
                        JCheckBox fourCheck = (JCheckBox) formPanel.getClientProperty("editFourCheck");
                        JTextField fourQty = (JTextField) formPanel.getClientProperty("editFourQty");
                        if (fourCheck != null && fourCheck.isSelected()) {
                            try {
                                int qty = Integer.parseInt(fourQty.getText().trim());
                                if (qty > 0) {
                                    // 🔧【新增】4人桌只能选1或2张
                                    if (qty != 1 && qty != 2) {
                                        showError(" 4人桌只能选择 1 张或 2 张！\n当前输入：" + qty + " 张");
                                        fourQty.requestFocus();
                                        shouldClose = false;
                                        return;
                                    }
                                    tableConfig.put("4", qty);
                                }
                            } catch (NumberFormatException ex) {
                                showError(" 4人桌数量格式错误！请输入有效数字。");
                                fourQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        }

                        JCheckBox sixCheck = (JCheckBox) formPanel.getClientProperty("editSixCheck");
                        JTextField sixQty = (JTextField) formPanel.getClientProperty("editSixQty");
                        if (sixCheck != null && sixCheck.isSelected()) {
                            try {
                                int qty = Integer.parseInt(sixQty.getText().trim());
                                if (qty > 0) {
                                    // 6人桌不限数量，直接使用
                                    tableConfig.put("6", qty);
                                }
                            } catch (NumberFormatException ex) {
                                showError(" 6人桌数量格式错误！请输入有效数字。");
                                sixQty.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        }

                        if (!tableConfig.isEmpty()) {
                            edits.put("tableConfig", tableConfig);
                        }
                    }

                    // ── 修改預付定金 ──
                    JCheckBox checkPrepaid = (JCheckBox) formPanel.getClientProperty("editCheckPrepaid");
                    if (checkPrepaid != null && checkPrepaid.isSelected()) {
                        JCheckBox prepaidCheck = (JCheckBox) formPanel.getClientProperty("editPrepaidCheck");
                        JTextField incrementField = (JTextField) formPanel.getClientProperty("editIncrementField");
                        if (prepaidCheck != null && incrementField != null) {
                            edits.put("isPrepaid", prepaidCheck.isSelected());
                            String incStr = incrementField.getText().trim();
                            if (incStr.isEmpty()) {
                                showError(" 已勾選修改預付定金，請輸入增加金額！");
                                incrementField.requestFocus();
                                shouldClose = false;
                                return;
                            }
                            try {
                                double increment = Double.parseDouble(incStr);
                                if (increment < 0) {
                                    showError(" 增加金額不能為負數！\n預付定金只增不減。");
                                    incrementField.requestFocus();
                                    shouldClose = false;
                                    return;
                                }

                                // 🔧【核心修復】從界面上的 originalLabel 提取實際值
                                JLabel originalLabel = (JLabel) formPanel.getClientProperty("editOriginalPrepaidLabel");
                                Double originalPrepaid = 0.0;
                                if (originalLabel != null) {
                                    String labelText = originalLabel.getText();
                                    // 從 "原定金: 100.00 元" 中提取數字
                                    try {
                                        originalPrepaid = Double.parseDouble(
                                                labelText.replaceAll("[^0-9.]", "")
                                        );
                                    } catch (Exception ee) {
                                        originalPrepaid = 0.0;
                                    }
                                }

                                double newTotal = originalPrepaid + increment;
                                newTotal = Math.round(newTotal * 100.0) / 100.0;

                                if (increment > 0) {
                                    int confirm = JOptionPane.showConfirmDialog(
                                            dialog,
                                            "確認增加預付定金？\n" +
                                                    "原定金：" + String.format("%.2f", originalPrepaid) + " 元\n" +
                                                    "增加：" + String.format("%.2f", increment) + " 元\n" +
                                                    "新總額：" + String.format("%.2f", newTotal) + " 元",
                                            "確認修改",
                                            JOptionPane.YES_NO_OPTION,
                                            JOptionPane.WARNING_MESSAGE
                                    );
                                    if (confirm != JOptionPane.YES_OPTION) {
                                        shouldClose = false;
                                        return;
                                    }
                                }
                                edits.put("prepaidAmount", newTotal);
                            } catch (NumberFormatException ex) {
                                showError("⚠️ 增加金額格式錯誤！請輸入有效數字。");
                                incrementField.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【新增】修改預點餐（只能從「否」改成「是」）
                    // ═══════════════════════════════════════════════════════════
                    JCheckBox checkPreOrder = (JCheckBox) formPanel.getClientProperty("editCheckPreOrder");
                    JRadioButton changeToYesRadio = (JRadioButton) formPanel.getClientProperty("editPreOrderRadio");
                    Boolean currentPreOrder = (Boolean) formPanel.getClientProperty("editCurrentPreOrder");

                    if (checkPreOrder != null && checkPreOrder.isSelected()) {
                        // 🔧 驗證：只能從「否」改成「是」
                        if (currentPreOrder != null && currentPreOrder) {
                            showError("⚠️ 當前已是預點餐狀態，不能取消預點餐！");
                            shouldClose = false;
                            return;
                        }

                        if (changeToYesRadio != null && changeToYesRadio.isSelected()) {
                            // 設置為「是」
                            edits.put("preOrder", true);
                        } else {
                            showError("⚠️ 已勾選修改預點餐，請選擇「改為：是」！");
                            shouldClose = false;
                            return;
                        }
                    }

                    // ── 修改備註 ──
                    JCheckBox checkNotes = (JCheckBox) formPanel.getClientProperty("editCheckNotes");
                    JTextArea notesArea = (JTextArea) formPanel.getClientProperty("editNotesArea");
                    if (checkNotes != null && checkNotes.isSelected() && notesArea != null) {
                        edits.put("notes", notesArea.getText().trim());
                    }

                    if (edits.isEmpty()) {
                        showError("⚠️ 請至少勾選一項修改內容");
                        shouldClose = false;
                        return;
                    }

                    result[0] = new HashMap<>();
                    result[0].put("mode", "EDIT_TIME");
                    result[0].put("reservationId", id);
                    result[0].put("edits", edits);
                }


            } catch (Exception ex) {
                showError("錯誤：" + ex.getMessage());
                ex.printStackTrace();
                shouldClose = false;  // 異常時也不關閉對話框
            }

            // 🔧【關鍵】只有驗證全部通過才關閉對話框
            if (shouldClose) {
                dialog.dispose();
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
        return result[0];
    }

    private void rebuildFormPanel(JPanel formPanel, String mode,
                                  JTextField idField,
                                  JTextField nameField,
                                  JTextField phoneField,
                                  JTextField timeFieldParam,
                                  Map<String, Object> existingReservation) {

        if (idField != null) idField.setText("");// 清空所有字段（模式切换时重置）
        formPanel.removeAll();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));


        if ("CREATE".equals(mode)) {
            // ═══════════════════════════════════════════════════════════
            // 【步骤 1】基础信息：日期 + 时间 + 姓名 + 电话
            // ═══════════════════════════════════════════════════════════

            // ── 1. 日期選擇器 ──
            SpinnerDateModel dateModel = new SpinnerDateModel();
            dateModel.setCalendarField(Calendar.DAY_OF_MONTH);
            JSpinner dateSpinner = new JSpinner(dateModel);
            JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
            dateSpinner.setEditor(dateEditor);
            dateSpinner.setValue(new Date());
            dateSpinner.setPreferredSize(new Dimension(150, 25));
            JFormattedTextField ftf = dateEditor.getTextField();
            ftf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        ftf.setSelectionStart(8);
                        ftf.setSelectionEnd(10);
                    });
                }
            });
            SwingUtilities.invokeLater(() -> {
                dateSpinner.requestFocusInWindow();
                ftf.setSelectionStart(8);
                ftf.setSelectionEnd(10);
            });

            // ── 2. 時間輸入框 ──
            JTextField timeFieldComp = new JTextField(10);
            timeFieldComp.setText("18:00");
            timeFieldComp.setToolTipText("請輸入時間，格式：HH:mm（例：18:30）");

            // ── 3. 日期 + 時間組合面板 ──
            JPanel dateTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            dateTimePanel.setBackground(new Color(245, 248, 255));
            dateTimePanel.add(new JLabel("預約日期 *:"));
            dateTimePanel.add(dateSpinner);
            dateTimePanel.add(Box.createHorizontalStrut(15));
            dateTimePanel.add(new JLabel("預約時間 *:"));
            dateTimePanel.add(timeFieldComp);
            dateTimePanel.add(new JLabel("  (HH:mm)"));
            formPanel.add(createFormField("", dateTimePanel));

            // ── 4. 時間格式驗證 ──
            final JLabel[] lockStatusLabelRef = new JLabel[1];
            Runnable autoSelectTask = () -> {
                try {
                    JRadioButton yesRadio = (JRadioButton) formPanel.getClientProperty("within15hYes");
                    JRadioButton noRadio = (JRadioButton) formPanel.getClientProperty("within15hNo");
                    if (yesRadio != null && noRadio != null) {
                        autoSelectWithin15h(dateSpinner, timeFieldComp, yesRadio, noRadio);
                    }
                } catch (Exception ex) {
                    System.err.println("自動選擇 1.5 小時選項失敗: " + ex.getMessage());
                }
            };
            timeFieldComp.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    update();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    update();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    update();
                }

                private void update() {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }

                        if (timeFieldComp.isFocusOwner() && lockStatusLabelRef[0] != null) {
                            // 🔧【关键修复】先格式化时间，再更新到输入框
                            String currentTime = timeFieldComp.getText().trim();
                            String formattedTime = formatTimeToStandard(currentTime);

                            if (!formattedTime.equals(currentTime)) {
                                timeFieldComp.setText(formattedTime);
                                timeFieldComp.setCaretPosition(formattedTime.length());
                            }

                            // 现在使用格式化后的时间
                            updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabelRef[0]);

                            // 🔧【修复】通过 formPanel 获取单选按钮引用
                            JRadioButton yesRadio = (JRadioButton) formPanel.getClientProperty("within15hYes");
                            JRadioButton noRadio = (JRadioButton) formPanel.getClientProperty("within15hNo");

                            if (yesRadio != null && noRadio != null) {
                                autoSelectWithin15h(dateSpinner, timeFieldComp, yesRadio, noRadio);
                            }
                        }
                    });
                }
            });

            timeFieldComp.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    String time = timeFieldComp.getText().trim();
                    if (!time.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                        timeFieldComp.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                        timeFieldComp.setToolTipText("❌ 格式錯誤！請輸入 HH:mm");
                        SwingUtilities.invokeLater(() -> {
                            timeFieldComp.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                            timeFieldComp.setToolTipText("請輸入時間，格式：HH:mm");
                        });
                    } else {
                        if (lockStatusLabelRef[0] != null) {
                            updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabelRef[0]);
                            autoSelectTask.run();
                        }
                    }
                }
            });
            dateSpinner.addChangeListener(e -> {
                if (lockStatusLabelRef[0] != null) {
                    updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabelRef[0]);
                    autoSelectTask.run();
                }
            });

            formPanel.add(createFormField("客人姓名 *:", nameField));
            formPanel.add(createFormField("聯繫電話 *:", phoneField));

            // ── 5. 鎖定狀態標籤 ──
            JLabel lockStatusLabel = new JLabel("鎖定狀態：計算中...");
            lockStatusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            lockStatusLabel.setForeground(Color.GRAY);
            lockStatusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
            lockStatusLabelRef[0] = lockStatusLabel;
            formPanel.add(lockStatusLabel);

            // ═══════════════════════════════════════════════════════════
            // 【步骤 2】餐桌类型选择
            // ═══════════════════════════════════════════════════════════
            JLabel tableTypeLabel = new JLabel("餐桌类型 *:");
            tableTypeLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            tableTypeLabel.setForeground(new Color(30, 144, 255));
            formPanel.add(createFormField("", tableTypeLabel));

            JPanel tableTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
            tableTypePanel.setBackground(new Color(245, 248, 255));
            JRadioButton personalRadio = new JRadioButton("个人桌（1 张主桌）");
            JRadioButton mergedRadio = new JRadioButton("合并桌（2 张同容量）");
            JRadioButton groupRadio = new JRadioButton("聚餐桌（多张同容量）");
            personalRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            mergedRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            groupRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            personalRadio.setBackground(new Color(245, 248, 255));
            mergedRadio.setBackground(new Color(245, 248, 255));
            groupRadio.setBackground(new Color(245, 248, 255));
            ButtonGroup tableTypeGroup = new ButtonGroup();
            tableTypeGroup.add(personalRadio);
            tableTypeGroup.add(mergedRadio);
            tableTypeGroup.add(groupRadio);
            personalRadio.setSelected(true);
            tableTypePanel.add(personalRadio);
            tableTypePanel.add(mergedRadio);
            tableTypePanel.add(groupRadio);
            formPanel.add(createFormField("", tableTypePanel));

            // ═══════════════════════════════════════════════════════════
            // 【步骤 3】1.5 小时确认
            // ═══════════════════════════════════════════════════════════
            JPanel timeLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            timeLabelPanel.setBackground(new Color(245, 248, 255));
            JLabel timeLabel = new JLabel("是否 1.5 小时内到店?:");
            timeLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            timeLabel.setForeground(new Color(30, 144, 255));
            timeLabelPanel.add(timeLabel);
            formPanel.add(createFormField("", timeLabelPanel));

            JPanel timeConfirmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
            timeConfirmPanel.setBackground(new Color(245, 248, 255));
            timeConfirmPanel.setBorder(BorderFactory.createEmptyBorder(5, 40, 5, 0));
            JRadioButton yesWithin15h = new JRadioButton("是");
            JRadioButton noWithin15h = new JRadioButton("否");
            yesWithin15h.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            noWithin15h.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            yesWithin15h.setBackground(new Color(245, 248, 255));
            noWithin15h.setBackground(new Color(245, 248, 255));
            ButtonGroup timeGroup = new ButtonGroup();
            timeGroup.add(yesWithin15h);
            timeGroup.add(noWithin15h);
            noWithin15h.setSelected(true);
            final boolean[] isAutoSelected = {true};
            try {
                updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabel);
                autoSelectWithin15h(dateSpinner, timeFieldComp, yesWithin15h, noWithin15h);
            } catch (Exception e) {
            }

            ActionListener timeOptionListener = e -> {
                if (isAutoSelected[0]) {
                    String autoSelectedText = yesWithin15h.isSelected() ? "是（1.5小时内）" : "否（超过1.5小时）";
                    int confirm = JOptionPane.showConfirmDialog(
                            formPanel,
                            "<html>该选项已根据预约时间自动匹配为：<b>" + autoSelectedText + "</b><br>" +
                                    "您确定要手动修改吗？</html>",
                            "确认修改",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm != JOptionPane.YES_OPTION) {
                        // 用户选择"否"，恢复自动选择
                        SwingUtilities.invokeLater(() -> {
                            autoSelectWithin15h(dateSpinner, timeFieldComp, yesWithin15h, noWithin15h);
                        });
                        return;
                    }
                    // 用户选择"是"，标记为手动修改
                    isAutoSelected[0] = false;
                }

                JRadioButton manualRadio = (JRadioButton) formPanel.getClientProperty("manualModeRadio");
                JRadioButton quantityRadio = (JRadioButton) formPanel.getClientProperty("quantityModeRadio");
                // 🔧【新增】如果是系统自动匹配选中的“否”（到店后锁定），且当前是“手动输入”，则静默切换回“填写数量”
                // 这实现了“默认填写桌子数量”的需求
                if (isAutoSelected[0] && noWithin15h.isSelected() && manualRadio.isSelected()) {
                    quantityRadio.setSelected(true);
                }
            };
            yesWithin15h.addActionListener(timeOptionListener);
            noWithin15h.addActionListener(timeOptionListener);

            timeConfirmPanel.add(yesWithin15h);
            timeConfirmPanel.add(noWithin15h);
            formPanel.add(createFormField("", timeConfirmPanel));

            // ═══════════════════════════════════════════════════════════
            // 【步骤 4】餐桌选择方式（手动输入桌号 / 填写数量）
            // ═══════════════════════════════════════════════════════════
            JPanel modeSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
            modeSelectPanel.setBackground(new Color(245, 248, 255));
            JRadioButton manualModeRadio = new JRadioButton("手动输入餐桌号");
            JRadioButton quantityModeRadio = new JRadioButton("填写桌子数量");
            manualModeRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            quantityModeRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            manualModeRadio.setBackground(new Color(245, 248, 255));
            quantityModeRadio.setBackground(new Color(245, 248, 255));
            ButtonGroup modeGroup = new ButtonGroup();
            modeGroup.add(manualModeRadio);
            modeGroup.add(quantityModeRadio);
            quantityModeRadio.setSelected(true);
            modeSelectPanel.add(manualModeRadio);
            modeSelectPanel.add(quantityModeRadio);
            JLabel tableConfigTitle = new JLabel("餐桌选择方式 *:");
            tableConfigTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            tableConfigTitle.setForeground(new Color(30, 144, 255));
            formPanel.add(createFormField("", tableConfigTitle, false));
            formPanel.add(modeSelectPanel);
            formPanel.add(Box.createVerticalStrut(5));

            // ═══════════════════════════════════════════════════════════
            // 【提前声明】模式切换相关面板（解决作用域问题）
            // ═══════════════════════════════════════════════════════════
            final JPanel manualTablePanel = new JPanel(new BorderLayout(5, 5));
            final JPanel quantityPanel = new JPanel();


            ActionListener switchMode = e -> {
                boolean isManual = manualModeRadio.isSelected();
                manualTablePanel.setVisible(isManual);//无法解析符号 'manualTablePanel'
                quantityPanel.setVisible(!isManual);
                formPanel.revalidate();
                formPanel.repaint();
            };

            // ──【模式 A】手动输入餐桌号面板（初始隐藏）─
            //  final JPanel manualTablePanel = new JPanel(new BorderLayout(5, 5));
            manualTablePanel.setBackground(new Color(245, 248, 255));
            manualTablePanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            manualTablePanel.setMaximumSize(new Dimension(400, 80));
            manualTablePanel.setVisible(false);
            JLabel manualHint = new JLabel("请输入餐桌号（用逗号分隔，如：7,8,13）:");
            manualHint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            JTextField manualTableField = new JTextField(25);
            manualTableField.setToolTipText("例如：7 或 7,8 或 13,14");
            manualTableField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

            // 餐桌号输入验证
            manualTableField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    validateTableInput();
                    validateAndConvertComma();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    validateTableInput();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    validateTableInput();
                    validateAndConvertComma();
                }

                private void validateTableInput() {
                    SwingUtilities.invokeLater(() -> {
                        String input = manualTableField.getText().trim();
                        if (input.isEmpty()) {
                            manualTableField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                            return;
                        }
                        if (!input.matches("^\\d+(,\\d+)*$")) {
                            manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                            manualTableField.setToolTipText(" 格式错误！只能输入数字，用逗号分隔");
                            return;
                        }
                        String[] tableNumbers = input.split(",");
                        Set<String> uniqueTables = new HashSet<>();
                        List<String> duplicates = new ArrayList<>();
                        for (String num : tableNumbers) {
                            String trimmed = num.trim();
                            if (uniqueTables.contains(trimmed)) duplicates.add(trimmed);
                            else uniqueTables.add(trimmed);
                        }
                        if (!duplicates.isEmpty()) {
                            manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                            manualTableField.setToolTipText(" 发现重复餐桌号：" + String.join(",", duplicates));
                            return;
                        }
                        int count = tableNumbers.length;
                        int maxTables = personalRadio.isSelected() ? 1 : (mergedRadio.isSelected() ? 2 : 99);
                        String typeName = personalRadio.isSelected() ? "个人桌" : (mergedRadio.isSelected() ? "合并桌" : "聚餐桌");

                        if ("聚餐桌".equals(typeName)) {
                            if (count < 3) {
                                manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                manualTableField.setToolTipText(" 聚餐桌必须选择 3 张或以上的餐桌！");
                                return;
                            }
                            // 🔧【新增】验证聚餐桌桌号必须连续
                            try {
                                // 1. 将桌号转换为整数数组
                                int[] tableNumbersInt = new int[tableNumbers.length];
                                for (int i = 0; i < tableNumbers.length; i++) {
                                    tableNumbersInt[i] = Integer.parseInt(tableNumbers[i].trim());
                                }

                                // 2. 排序
                                Arrays.sort(tableNumbersInt);

                                // 3. 检查是否连续（相邻数字差值必须为1）
                                for (int i = 1; i < tableNumbersInt.length; i++) {
                                    if (tableNumbersInt[i] - tableNumbersInt[i - 1] != 1) {
                                        manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                        manualTableField.setToolTipText(
                                                " 聚餐桌桌号必须连续！\n" +
                                                        " 当前输入：" + input + "\n" +
                                                        " 缺少桌号：" + (tableNumbersInt[i - 1] + 1)
                                        );
                                        return;
                                    }
                                }
                                for (String tableNum : tableNumbers) {
                                    Tables table = controller.getTableById(tableNum.trim());
                                    if (table != null && table.getCapacity() != 6) {
                                        manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                        manualTableField.setToolTipText(" 聚餐桌只能使用 6 人桌！");
                                        return;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                manualTableField.setToolTipText(" 桌号格式错误！请输入纯数字");
                                return;
                            }
                        }
                        if ("合并桌".equals(typeName)) {
                            // 验证1：必须是2张桌子
                            if (count != 2) {
                                manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                manualTableField.setToolTipText(" 合并桌必须选择 2 张餐桌！");
                                return;
                            }

                            // 验证2：不能包含2人桌
                            for (String tableNum : tableNumbers) {
                                Tables table = controller.getTableById(tableNum.trim());
                                if (table != null && table.getCapacity() == 2) {
                                    manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                    manualTableField.setToolTipText(" 合并桌不能包含 2 人桌！");
                                    return;
                                }
                            }

                            // 验证3：🔧【核心】必须是左右相邻的桌子（不能上下相邻）
                            if (tableNumbers.length == 2) {
                                try {
                                    // 解析两张桌子的编号
                                    int table1Num = Integer.parseInt(tableNumbers[0].trim());
                                    int table2Num = Integer.parseInt(tableNumbers[1].trim());

                                    // 计算行号（每行3张桌子）
                                    int row1 = (table1Num - 1) / 3;
                                    int row2 = (table2Num - 1) / 3;

                                    // 检查是否在同一行
                                    if (row1 != row2) {
                                        manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                        manualTableField.setToolTipText(
                                                " 合并桌必须是左右相邻的餐桌！\n" +
                                                        " 当前选择：桌" + table1Num + " 和 桌" + table2Num + "\n" +
                                                        " 这两张桌子不在同一行，不能合并。"
                                        );
                                        return;
                                    }

                                    // 检查是否相邻（编号相差1）
                                    if (Math.abs(table1Num - table2Num) != 1) {
                                        manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                        manualTableField.setToolTipText(
                                                " 合并桌必须是左右相邻的餐桌！\n" +
                                                        " 当前选择：桌" + table1Num + " 和 桌" + table2Num + "\n" +
                                                        " 这两张桌子不相邻。"
                                        );
                                        return;
                                    }

                                    System.out.println("✅ 合并桌验证通过：桌" + table1Num + " 和 桌" + table2Num + " 是左右相邻的");

                                } catch (NumberFormatException e) {
                                    manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                    manualTableField.setToolTipText(" 餐桌号格式错误！请输入数字");
                                    return;
                                }
                            }
                        }

                        if (count > maxTables) {
                            manualTableField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                            manualTableField.setToolTipText(" " + typeName + " 最多只能输入 " + maxTables + " 个餐桌号！");
                        } else {
                            manualTableField.setBorder(BorderFactory.createLineBorder(new Color(0, 150, 0), 1));
                            manualTableField.setToolTipText("✓ 已输入 " + count + " 个餐桌号（" + typeName + "）");
                        }
                    });
                }

                /**
                 * 验证并转换中文逗号为英文逗号
                 * 当用户输入中文逗号（，）时，自动转换为英文逗号（,）
                 */
                private void validateAndConvertComma() {
                    SwingUtilities.invokeLater(() -> {
                        String input = manualTableField.getText();
                        if (input == null || input.isEmpty()) {
                            return;
                        }

                        // 检测是否包含中文逗号（全角逗号 \uFF0C）
                        if (input.contains("，")) {
                            // 记录替换前的光标位置
                            int originalCaretPosition = manualTableField.getCaretPosition();

                            // 将中文逗号替换为英文逗号
                            String converted = input.replace("，", ",");

                            // 更新文本（这会触发 DocumentListener，但转换后不再有中文逗号，不会递归）
                            manualTableField.setText(converted);

                            // 恢复光标位置（避免越界）
                            int newCaretPosition = Math.min(originalCaretPosition, converted.length());
                            manualTableField.setCaretPosition(newCaretPosition);
                        }
                    });
                }
            });

            ActionListener updateTableHint = e -> {
                if (manualTablePanel.isVisible()) {
                    String hint;
                    if (personalRadio.isSelected()) hint = "请输入餐桌号（个人桌只能输入 1 个，如：7）:";
                    else if (mergedRadio.isSelected()) hint = "请输入餐桌号（合并桌只能输入 2 个，如：7,8）:";
                    else hint = "请输入餐桌号（聚餐桌可输入多个，如：7,8,13）:";
                    manualHint.setText(hint);
                    manualTableField.setText("");
                    manualTableField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                }
            };
            personalRadio.addActionListener(updateTableHint);
            mergedRadio.addActionListener(updateTableHint);
            groupRadio.addActionListener(updateTableHint);

            // 🔧【新增】监听“手动输入”的点击事件：如果处于“到店后锁定”状态，则弹窗警告
            manualModeRadio.addActionListener(e -> {
                // 只有当“是否1.5小时内”选的是“否”（即到店后锁定）时，才触发
                if (noWithin15h.isSelected()) {
                    SwingUtilities.invokeLater(() -> {
                        int result = JOptionPane.showConfirmDialog(
                                formPanel, // 使用 formPanel 作为父组件，弹窗会依附于主对话框
                                "<html><b>提示：建议修改预约时间</b><br><br>" +
                                        "当前设置为<b>“到店后锁定”</b>（超过1.5小时），<br>" +
                                        "系统默认只需填写<b>桌子数量</b>，无需指定具体桌号。<br>" +
                                        "如果您希望指定具体餐桌，建议将预约时间修改为<b>1.5小时内</b>。<br><br>" +
                                        "确定要保留“手动输入餐桌号”吗？",
                                "确认操作",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                        );

                        if (result != JOptionPane.YES_OPTION) {
                            // 用户点“否”或取消，强制切回“填写桌子数量”
                            quantityModeRadio.setSelected(true);
                            //手动触发 switchMode，确保面板正确切换 bug:无法解析符号 'switchMode'
                            switchMode.actionPerformed(null);

                        }
                    });
                }
            });
            manualTablePanel.add(manualHint, BorderLayout.NORTH);
            manualTablePanel.add(manualTableField, BorderLayout.CENTER);

            // ═══════════════════════════════════════════════════════════
            // 🔧【模式 B】填写桌子数量面板（完整修复版 - 保留清空逻辑 + 数组引用）
            // ═══════════════════════════════════════════════════════════
            // final JPanel quantityPanel = new JPanel();
            quantityPanel.setLayout(new BoxLayout(quantityPanel, BoxLayout.Y_AXIS));
            quantityPanel.setBackground(new Color(245, 248, 255));
            quantityPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            quantityPanel.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));
            quantityPanel.setVisible(true);

            // 🔧【关键】用数组包装解决 lambda 作用域问题
            final JTextField[] twoSeatQtyRef = {null};
            final JTextField[] fourSeatQtyRef = {null};
            final JTextField[] sixSeatQtyRef = {null};

            ButtonGroup capacityGroup = new ButtonGroup();

            // ── 2 人桌 ──
            JPanel twoSeatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            twoSeatPanel.setBackground(new Color(245, 248, 255));
            twoSeatPanel.setMaximumSize(new Dimension(400, 30));
            JRadioButton twoSeatRadio = new JRadioButton("2 人桌");
            twoSeatRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            twoSeatRadio.setBackground(new Color(245, 248, 255));
            JTextField twoSeatQty = new JTextField("", 3);
            twoSeatQty.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            twoSeatQty.setPreferredSize(new Dimension(40, 25));
            twoSeatQtyRef[0] = twoSeatQty;  // 🔧 存入数组引用

            twoSeatRadio.addActionListener(e -> {
                if (twoSeatRadio.isSelected()) {
                    twoSeatQty.setText("1");
                    twoSeatQty.requestFocus();
                    // 🔧【核心】清空其他桌型数量（保留用户要求的代码）
                    if (fourSeatQtyRef[0] != null) fourSeatQtyRef[0].setText("");
                    if (sixSeatQtyRef[0] != null) sixSeatQtyRef[0].setText("");
                }
            });
            capacityGroup.add(twoSeatRadio);
            twoSeatPanel.add(twoSeatRadio);
            twoSeatPanel.add(new JLabel("数量:"));
            twoSeatPanel.add(twoSeatQty);
            twoSeatPanel.add(new JLabel("张"));
            quantityPanel.add(twoSeatPanel);
            quantityPanel.add(Box.createVerticalStrut(5));

            // ── 4 人桌 ──
            JPanel fourSeatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            fourSeatPanel.setBackground(new Color(245, 248, 255));
            fourSeatPanel.setMaximumSize(new Dimension(400, 30));
            JRadioButton fourSeatRadio = new JRadioButton("4 人桌");
            fourSeatRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            fourSeatRadio.setBackground(new Color(245, 248, 255));
            JTextField fourSeatQty = new JTextField("", 3);
            fourSeatQty.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            fourSeatQty.setPreferredSize(new Dimension(40, 25));
            fourSeatQtyRef[0] = fourSeatQty;  // 🔧 存入数组引用

            fourSeatRadio.addActionListener(e -> {
                if (fourSeatRadio.isSelected()) {
                    // 🔧 合并桌模式下强制数量为2
                    if (mergedRadio != null && mergedRadio.isSelected()) {
                        fourSeatQty.setText("2");
                    } else {
                        fourSeatQty.setText("1");
                    }
                    fourSeatQty.requestFocus();
                    // 🔧【核心】清空其他桌型数量（保留用户要求的代码）
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");
                    if (sixSeatQtyRef[0] != null) sixSeatQtyRef[0].setText("");
                }
            });
            capacityGroup.add(fourSeatRadio);
            fourSeatPanel.add(fourSeatRadio);
            fourSeatPanel.add(new JLabel("数量:"));
            fourSeatPanel.add(fourSeatQty);
            fourSeatPanel.add(new JLabel("张"));
            quantityPanel.add(fourSeatPanel);
            quantityPanel.add(Box.createVerticalStrut(5));

            // ── 6 人桌 ──
            JPanel sixSeatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            sixSeatPanel.setBackground(new Color(245, 248, 255));
            sixSeatPanel.setMaximumSize(new Dimension(400, 30));
            JRadioButton sixSeatRadio = new JRadioButton("6 人桌");
            sixSeatRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            sixSeatRadio.setBackground(new Color(245, 248, 255));
            JTextField sixSeatQty = new JTextField("", 3);
            sixSeatQty.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            sixSeatQty.setPreferredSize(new Dimension(40, 25));
            sixSeatQtyRef[0] = sixSeatQty;  // 🔧 存入数组引用

            sixSeatRadio.addActionListener(e -> {
                if (sixSeatRadio.isSelected()) {
                    // 🔧 合并桌模式下强制数量为2
                    if (mergedRadio != null && mergedRadio.isSelected()) {
                        sixSeatQty.setText("2");//默認兩張
                    } else if (groupRadio != null && groupRadio.isSelected()) {
                        sixSeatQty.setText("3");  // 聚餐桌默认3张
                    } else {
                        sixSeatQty.setText("1");
                    }
                    sixSeatQty.requestFocus();
                    // 🔧【核心】清空其他桌型数量（保留用户要求的代码）
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");
                    if (fourSeatQtyRef[0] != null) fourSeatQtyRef[0].setText("");
                }
            });
            capacityGroup.add(sixSeatRadio);
            sixSeatPanel.add(sixSeatRadio);
            sixSeatPanel.add(new JLabel("数量:"));
            sixSeatPanel.add(sixSeatQty);
            sixSeatPanel.add(new JLabel("张"));
            quantityPanel.add(sixSeatPanel);

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心】餐桌类型切换监听器（禁用2人桌 + 强制数量 + 清空逻辑）
            // ═══════════════════════════════════════════════════════════
            ActionListener handleTableType = e -> {
                if (mergedRadio.isSelected()) {
                    // 合并桌：禁用2人桌单选按钮，强制4/6人桌数量为2
                    twoSeatRadio.setEnabled(false);
                    if (!fourSeatRadio.isSelected() && !sixSeatRadio.isSelected()) {
                        fourSeatRadio.setSelected(true);
                    }
                    // 🔧 强制数量为2（不变灰）
                    fourSeatQty.setText("2");
                    sixSeatQty.setText("2");
                    // 🔧 清空2人桌数量
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");
                } else if (groupRadio.isSelected()) {
                    // 聚餐桌：禁用2人桌和4人桌
                    twoSeatRadio.setEnabled(false);
                    twoSeatRadio.setSelected(false);
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");

                    // 🔧【核心修复】禁用4人桌并清空
                    fourSeatRadio.setEnabled(false);
                    fourSeatRadio.setSelected(false);
                    if (fourSeatQtyRef[0] != null) fourSeatQtyRef[0].setText("");

                    // 强制选择6人桌，数量设为3
                    sixSeatRadio.setSelected(true);
                    sixSeatRadio.setEnabled(true);
                    sixSeatQty.setText("3");
                } else {
                    // 个人桌：恢复所有选项
                    twoSeatRadio.setEnabled(true);
                    fourSeatRadio.setEnabled(true);  // 🔧【关键修复】恢复4人桌
                    sixSeatRadio.setEnabled(true);

                    // 🔧【关键修复】清空所有数量
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");
                    if (fourSeatQtyRef[0] != null) fourSeatQtyRef[0].setText("");
                    if (sixSeatQtyRef[0] != null) sixSeatQtyRef[0].setText("");

                    // 默认选中2人桌，数量设为1
                    twoSeatRadio.setSelected(true);
                    twoSeatQty.setText("1");
                }
            };
            personalRadio.addActionListener(handleTableType);
            mergedRadio.addActionListener(handleTableType);
            groupRadio.addActionListener(handleTableType);
            handleTableType.actionPerformed(null);  // 初始化执行

            // ═══════════════════════════════════════════════════════════
            // 模式切换监听器 + 添加面板
            // ═══════════════════════════════════════════════════════════
//            ActionListener switchMode = e -> {
//                boolean isManual = manualModeRadio.isSelected();
//                manualTablePanel.setVisible(isManual);
//                quantityPanel.setVisible(!isManual);
//                formPanel.revalidate();
//                formPanel.repaint();
//            };
            manualModeRadio.addActionListener(switchMode);
            quantityModeRadio.addActionListener(switchMode);
            switchMode.actionPerformed(null);

            // 🔧【关键】添加两个面板到 formPanel
            formPanel.add(manualTablePanel);
            formPanel.add(quantityPanel);
            formPanel.add(Box.createVerticalStrut(5));

            // ═══════════════════════════════════════════════════════════
            // 【步骤 5】预点餐 + 预付定金
            // ═══════════════════════════════════════════════════════════
            JPanel preOrderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
            preOrderPanel.setBackground(new Color(245, 248, 255));
            JRadioButton preOrderYes = new JRadioButton("是（可预点餐 + 预付）");
            JRadioButton preOrderNo = new JRadioButton("否（仅预约座位）");
            preOrderYes.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            preOrderNo.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            preOrderYes.setBackground(new Color(245, 248, 255));
            preOrderNo.setBackground(new Color(245, 248, 255));
            ButtonGroup preOrderGroup = new ButtonGroup();
            preOrderGroup.add(preOrderYes);
            preOrderGroup.add(preOrderNo);
            preOrderNo.setSelected(true);
            preOrderPanel.add(new JLabel("是否预点餐?:"));
            preOrderPanel.add(preOrderYes);
            preOrderPanel.add(preOrderNo);
            formPanel.add(createFormField("", preOrderPanel, false));

            final JPanel prepaidWrapper = new JPanel();
            prepaidWrapper.setLayout(new BoxLayout(prepaidWrapper, BoxLayout.Y_AXIS));
            prepaidWrapper.setBackground(new Color(245, 248, 255));
            prepaidWrapper.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            prepaidWrapper.setMaximumSize(new Dimension(400, 80));
            prepaidWrapper.setVisible(false);
            JPanel prepaidPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            prepaidPanel.setBackground(new Color(245, 248, 255));
            JCheckBox prepaidCheck = new JCheckBox("预付定金");
            prepaidCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            prepaidCheck.setBackground(new Color(245, 248, 255));
            JTextField prepaidField = new JTextField("0.00", 10);
            prepaidField.setPreferredSize(new Dimension(100, 25));
            prepaidField.setEditable(false);
            prepaidField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            prepaidCheck.addActionListener(ev -> {
                prepaidField.setEditable(prepaidCheck.isSelected());
                prepaidField.setText(prepaidCheck.isSelected() ? "100.00" : "0.00");
            });
            prepaidPanel.add(prepaidCheck);
            prepaidPanel.add(new JLabel("金额 (¥):"));
            prepaidPanel.add(prepaidField);
            prepaidWrapper.add(prepaidPanel);
            JLabel prepaidHint = new JLabel("💡 预付定金一般不退还，请确认后再支付");
            prepaidHint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            prepaidHint.setForeground(Color.GRAY);
            prepaidHint.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            prepaidWrapper.add(prepaidHint);
            formPanel.add(createFormField("", prepaidWrapper, false));

            ActionListener togglePreOrder = e -> {
                boolean isPreOrder = preOrderYes.isSelected();
                prepaidWrapper.setVisible(isPreOrder);
                if (!isPreOrder && prepaidCheck.isSelected()) {
                    prepaidCheck.setSelected(false);
                    prepaidField.setText("0.00");
                    prepaidField.setEditable(false);
                }
                formPanel.revalidate();
                formPanel.repaint();
            };
            preOrderYes.addActionListener(togglePreOrder);
            preOrderNo.addActionListener(togglePreOrder);
            togglePreOrder.actionPerformed(null);

            // ── 备注 ──
            JTextArea notes = new JTextArea(3, 20);
            formPanel.add(createFormField("備註:", new JScrollPane(notes)));

            // ═══════════════════════════════════════════════════════════
            // 🔧 保存引用供确认按钮使用
            // ═══════════════════════════════════════════════════════════
            formPanel.putClientProperty("dateSpinner", dateSpinner);
            formPanel.putClientProperty("timeField", timeFieldComp);
            formPanel.putClientProperty("lockStatusLabel", lockStatusLabel);
            formPanel.putClientProperty("tableTypePersonal", personalRadio);
            formPanel.putClientProperty("tableTypeMerged", mergedRadio);
            formPanel.putClientProperty("tableTypeGroup", groupRadio);
            formPanel.putClientProperty("within15hYes", yesWithin15h);
            formPanel.putClientProperty("within15hNo", noWithin15h);
            formPanel.putClientProperty("manualModeRadio", manualModeRadio);
            formPanel.putClientProperty("quantityModeRadio", quantityModeRadio);
            formPanel.putClientProperty("manualTableField", manualTableField);
            // 桌子数量配置
            formPanel.putClientProperty("twoSeatRadio", twoSeatRadio);
            formPanel.putClientProperty("twoSeatQty", twoSeatQtyRef[0]);  // 🔧 用数组引用
            formPanel.putClientProperty("fourSeatRadio", fourSeatRadio);
            formPanel.putClientProperty("fourSeatQty", fourSeatQtyRef[0]);  // 🔧 用数组引用
            formPanel.putClientProperty("sixSeatRadio", sixSeatRadio);
            formPanel.putClientProperty("sixSeatQty", sixSeatQtyRef[0]);  // 🔧 用数组引用
            // 预点餐
            formPanel.putClientProperty("preOrderYes", preOrderYes);
            formPanel.putClientProperty("preOrderNo", preOrderNo);
            formPanel.putClientProperty("prepaidCheck", prepaidCheck);
            formPanel.putClientProperty("prepaidAmount", prepaidField);
            // 备注
            formPanel.putClientProperty("notesArea", notes);
        } else if ("ASSIGN".equals(mode)) {
            // 🔧 查询方式选择：预约号 OR 电话
            JPanel queryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
            queryPanel.setBackground(new Color(245, 248, 255));
            JRadioButton byIdRadio = new JRadioButton("按预约号查询");
            JRadioButton byPhoneRadio = new JRadioButton("按电话号码查询");
            ButtonGroup queryGroup = new ButtonGroup();
            queryGroup.add(byIdRadio);
            queryGroup.add(byPhoneRadio);
            byIdRadio.setSelected(true);
            queryPanel.add(byIdRadio);
            queryPanel.add(byPhoneRadio);
            formPanel.add(createFormField("", queryPanel, false));

            // 查询输入框
            JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            inputPanel.setBackground(new Color(245, 248, 255));
            JLabel queryLabel = new JLabel("预约号 *:");
            queryLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            inputPanel.add(queryLabel);
            idField.setEditable(true);
            idField.setPreferredSize(new Dimension(200, 25));
            // 🔧【关键修改】如果 existingReservation 不为空，预填预约号
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println("🔧 ASSIGN 模式：已预填预约号 " + resId);
                }
            }
            inputPanel.add(idField);
            formPanel.add(createFormField("", inputPanel, false));

            // 查询按钮
            JButton queryBtn = new JButton("🔍 查询预定");
            queryBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            queryBtn.setBackground(new Color(30, 144, 255));
            queryBtn.setForeground(Color.WHITE);
            queryBtn.setFocusPainted(false);
            queryBtn.setPreferredSize(new Dimension(120, 30));
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            btnPanel.setBackground(new Color(245, 248, 255));
            btnPanel.add(queryBtn);
            formPanel.add(createFormField("", btnPanel, false));
            formPanel.add(Box.createVerticalStrut(10));

            // ═══════════════════════════════════════════════════════════
            // 🔧【修改点1】客人信息区域 - 添加预约号显示字段
            // ═══════════════════════════════════════════════════════════
            JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 5));  // 🔧 修改：从 4 行改为 5 行
            infoPanel.setBackground(new Color(255, 255, 255));
            infoPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                            " 📋 预定信息 ",
                            TitledBorder.LEFT,
                            TitledBorder.TOP,
                            new Font("Microsoft YaHei", Font.BOLD, 13),
                            new Color(30, 144, 255)
                    ),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));

            JTextField infoName = new JTextField();
            JTextField infoPhone = new JTextField();
            JTextField infoTime = new JTextField();
            JLabel tableConfigLabel = new JLabel("未查询");
            tableConfigLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

            // 🔧【修改点2】新增：预约号显示字段（只读）
            JTextField infoReservationId = new JTextField();
            infoReservationId.setEditable(false);
            infoReservationId.setBackground(new Color(250, 250, 250));
            infoReservationId.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            infoReservationId.setForeground(new Color(30, 144, 255));

            // 🔧【新增】备注显示字段（只读，多行文本）
            JTextArea infoNotes = new JTextArea(2, 20);
            infoNotes.setEditable(false);
            infoNotes.setBackground(new Color(250, 250, 250));
            infoNotes.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            infoNotes.setLineWrap(true);
            infoNotes.setWrapStyleWord(true);
            JScrollPane notesScrollPane = new JScrollPane(infoNotes);
            notesScrollPane.setPreferredSize(new Dimension(200, 50));

            infoName.setEditable(false);
            infoPhone.setEditable(false);
            infoTime.setEditable(false);
            infoName.setBackground(new Color(250, 250, 250));
            infoPhone.setBackground(new Color(250, 250, 250));
            infoTime.setBackground(new Color(250, 250, 250));

            // 🔧【修改点3】添加预约号显示行（放在第一位）
            infoPanel.add(new JLabel("预约号:"));
            infoPanel.add(infoReservationId);
            infoPanel.add(new JLabel("客人姓名:"));
            infoPanel.add(infoName);
            infoPanel.add(new JLabel("联系电话:"));
            infoPanel.add(infoPhone);
            infoPanel.add(new JLabel("预约时间:"));
            infoPanel.add(infoTime);
            infoPanel.add(new JLabel("餐桌配置:"));
            infoPanel.add(tableConfigLabel);
            infoPanel.add(new JLabel("备注:"));  // 🔧 新增备注标签
            infoPanel.add(notesScrollPane);      // 🔧 新增备注文本域

            formPanel.add(infoPanel);
            formPanel.add(Box.createVerticalStrut(15));

            // 🔧 餐桌分配区域
            JLabel assignLabel = new JLabel("<html><b style='color:#1976d2;'>🍽️ 分配具体餐桌号</b></html>");
            formPanel.add(createFormField("", assignLabel, false));

            // 🔧 显示可用餐桌列表（复选框）
            JPanel tableSelectionPanel = new JPanel();
            tableSelectionPanel.setLayout(new BoxLayout(tableSelectionPanel, BoxLayout.Y_AXIS));
            tableSelectionPanel.setBackground(new Color(255, 255, 255));
            tableSelectionPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                            " 可选餐桌（勾选要分配的餐桌） ",
                            TitledBorder.LEFT,
                            TitledBorder.TOP,
                            new Font("Microsoft YaHei", Font.BOLD, 13),
                            new Color(76, 175, 80)
                    ),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            tableSelectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

            // 🔧【关键】动态生成餐桌复选框容器
            JPanel tablesGrid = new JPanel();
            tablesGrid.setLayout(new GridLayout(0, 3, 10, 10));  // 0行3列，自动扩展
            tablesGrid.setBackground(new Color(255, 255, 255));
            Map<String, JCheckBox> assignTableCheckBoxes = new HashMap<>();

            // 🔧【核心】从数据库获取餐桌列表（通过Controller）
            if (controller != null) {
                try {
                    List<Tables> allTables = controller.getAllVacantTables();
                    if (allTables != null && !allTables.isEmpty()) {
                        for (Tables table : allTables) {
                            if (table.getStatus() == Tables.TableStatus.VACANT) {
                                String displayId = table.getDisplayId();
                                int capacity = table.getCapacity();
                                JCheckBox tableCheck = new JCheckBox(
                                        String.format("桌%s (%d人)", displayId, capacity)
                                );
                                tableCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                                tableCheck.setBackground(new Color(255, 255, 255));
                                tableCheck.setToolTipText(String.format("容量: %d人", capacity));
                                tablesGrid.add(tableCheck);
                                assignTableCheckBoxes.put(displayId, tableCheck);
                            }
                        }
                    } else {
                        JLabel noTableLabel = new JLabel(" 暂无空闲餐桌", SwingConstants.CENTER);
                        noTableLabel.setForeground(Color.RED);
                        noTableLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
                        tablesGrid.add(noTableLabel);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JLabel errorLabel = new JLabel(" 加载餐桌失败", SwingConstants.CENTER);
                    errorLabel.setForeground(Color.RED);
                    tablesGrid.add(errorLabel);
                }
            } else {
                JLabel initLabel = new JLabel(" 控制器未初始化", SwingConstants.CENTER);
                initLabel.setForeground(Color.ORANGE);
                tablesGrid.add(initLabel);
            }

            JScrollPane tableScroll = new JScrollPane(tablesGrid);
            tableScroll.setPreferredSize(new Dimension(550, 140));
            tableScroll.setBorder(null);
            tableScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            tableSelectionPanel.add(tableScroll);
            formPanel.add(createFormField("", tableSelectionPanel, false));

            // ═══════════════════════════════════════════════════════════
            // 🔧【修改点4】查询按钮事件 - 支持模糊查询 + 弹窗选择
            // ═══════════════════════════════════════════════════════════
            queryBtn.addActionListener(ev -> {
                String queryValue = idField.getText().trim();
                if (queryValue.isEmpty()) {
                    showError("请输入预约号或电话号码");
                    return;
                }

                // 显示查询中状态
                infoReservationId.setText("🔄 查询中...");
                infoName.setText("");
                infoPhone.setText("");
                infoTime.setText("");
                tableConfigLabel.setText("查询中...");
                tableConfigLabel.setForeground(Color.GRAY);

                // 🔧 清空餐桌复选框
                tablesGrid.removeAll();
                assignTableCheckBoxes.clear();
                tablesGrid.revalidate();
                tablesGrid.repaint();

                // 🔧 判断查询方式
                boolean isPhoneQuery = byPhoneRadio.isSelected();
                try {
                    // 🔧 调用 Controller 进行模糊查询
                    List<Map<String, Object>> results;
                    if (isPhoneQuery) {
                        // 电话号码查询
                        results = controller.findReservationsByPhone(queryValue);
                    } else {
                        // 预约号查询
                        results = controller.findReservationsByCode(queryValue);
                    }

                    // 🔧 处理查询结果
                    if (results == null || results.isEmpty()) {
                        showError("未找到匹配的预约记录！\n请检查输入是否正确。");
                        infoReservationId.setText("");
                        return;
                    }

                    // 🔧 如果只有一个结果，直接显示
                    if (results.size() == 1) {
                        Map<String, Object> reservation = results.get(0);
                        fillReservationInfo(reservation, infoReservationId, infoName,
                                infoPhone, infoTime, tableConfigLabel, infoNotes);

                        // 🔧【关键修复】更新输入框为完整预约号
                        String fullReservationId = (String) reservation.get("reservation_id");
                        if (fullReservationId != null) {
                            idField.setText(fullReservationId);
                        }

                        // 加载可分配餐桌
                        loadAvailableTables(reservation, tablesGrid, assignTableCheckBoxes);
                    }
                    // 🔧 如果有多个结果，弹出选择对话框
                    else {
                        Map<String, Object> selectedReservation = showReservationSelectionDialog(
                                results, isPhoneQuery, queryValue
                        );
                        if (selectedReservation != null) {
                            fillReservationInfo(selectedReservation, infoReservationId, infoName,
                                    infoPhone, infoTime, tableConfigLabel, infoNotes);

                            // 🔧【关键修复】更新输入框为完整预约号
                            String fullReservationId = (String) selectedReservation.get("reservation_id");
                            if (fullReservationId != null) {
                                idField.setText(fullReservationId);
                            }

                            // 加载可分配餐桌
                            loadAvailableTables(selectedReservation, tablesGrid, assignTableCheckBoxes);
                        } else {
                            // 用户取消选择
                            infoReservationId.setText("");
                            infoName.setText("");
                            infoPhone.setText("");
                            infoTime.setText("");
                            tableConfigLabel.setText("未查询");
                        }
                    }
                } catch (Exception e) {
                    showError("查询失败：" + e.getMessage());
                    e.printStackTrace();
                    infoReservationId.setText("");
                }
            });

            ActionListener switchQuery = e -> {
                if (byIdRadio.isSelected()) {
                    queryLabel.setText("预约号 *:");
                    idField.setText("");

                    // 🔧 清空所有信息显示
                    infoReservationId.setText("");
                    infoName.setText("");
                    infoPhone.setText("");
                    infoTime.setText("");
                    tableConfigLabel.setText("未查询");
                    tableConfigLabel.setForeground(Color.GRAY);

                    // 🔧 清空餐桌复选框
                    for (JCheckBox checkBox : assignTableCheckBoxes.values()) {
                        checkBox.setSelected(false);
                    }

                } else {
                    queryLabel.setText("电话号码 *:");
                    idField.setText("");

                    // 🔧 清空所有信息显示
                    infoReservationId.setText("");
                    infoName.setText("");
                    infoPhone.setText("");
                    infoTime.setText("");
                    tableConfigLabel.setText("未查询");
                    tableConfigLabel.setForeground(Color.GRAY);

                    // 🔧 清空餐桌复选框
                    for (JCheckBox checkBox : assignTableCheckBoxes.values()) {
                        checkBox.setSelected(false);
                    }
                }
            };

            byIdRadio.addActionListener(switchQuery);
            byPhoneRadio.addActionListener(switchQuery);

            // ═══════════════════════════════════════════════════════════
            // 🔧【修改点6】存引用供确认按钮使用 - 添加预约号字段引用
            // ═══════════════════════════════════════════════════════════
            formPanel.putClientProperty("assignIdField", idField);
            formPanel.putClientProperty("assignInfoReservationId", infoReservationId);  // 🔧 新增
            formPanel.putClientProperty("assignInfoName", infoName);
            formPanel.putClientProperty("assignInfoPhone", infoPhone);
            formPanel.putClientProperty("assignInfoTime", infoTime);
            formPanel.putClientProperty("assignTableConfigLabel", tableConfigLabel);
            formPanel.putClientProperty("assignInfoNotes", infoNotes);  // 🔧 新增
            formPanel.putClientProperty("assignTableCheckBoxes", assignTableCheckBoxes);
            formPanel.putClientProperty("byIdRadio", byIdRadio);
        }

        // ═══════════════════════════════════════════════════════════
        //         【CANCEL 模式】取消預約
        //═══════════════════════════════════════════════════════════

        else if ("CANCEL".equals(mode)) {
            // ── 预约号输入 ──
            JLabel idLabel = new JLabel("预约号 *:");
            idLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            idField.setEditable(true);
            idField.setPreferredSize(new Dimension(200, 25));

            // ═══════════════════════════════════════════════════════════
            // 🔧【关键修改】如果 existingReservation 不为空，预填预约号
            // ═══════════════════════════════════════════════════════════
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println("🔧 CANCEL 模式：已预填预约号 " + resId);
                }
            }
            formPanel.add(createFormField("预约号 *:", idField));

            // ── 查询按钮（居中显示）──
            JPanel queryBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            queryBtnPanel.setBackground(new Color(245, 248, 255));

            JButton queryBtn = new JButton("🔍 查询预约");
            queryBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            queryBtn.setBackground(new Color(30, 144, 255));
            queryBtn.setForeground(Color.WHITE);
            queryBtn.setFocusPainted(false);
            queryBtn.setPreferredSize(new Dimension(120, 30));

            queryBtnPanel.add(queryBtn);
            formPanel.add(queryBtnPanel);
            formPanel.add(Box.createVerticalStrut(10));

            // 🔧 查询按钮事件：模糊查询预约号（CANCEL 模式专用）
            queryBtn.addActionListener(ev -> {
                String queryValue = idField.getText().trim();
                if (queryValue.isEmpty()) {
                    showError("请输入预约号片段进行查询");
                    return;
                }

                try {
                    // 🔧 调用 Controller 进行模糊查询（支持预约号片段）
                    List<TableReservation> results = controller.findReservationsForCancel(queryValue);

                    if (results == null || results.isEmpty()) {
                        showError("未找到匹配的预约记录：\n\"" + queryValue + "\"");
                        return;
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【核心修改】无论结果数量（包括1条），都弹出选择对话框
                    // ═══════════════════════════════════════════════════════════
                    TableReservation selectedReservation = showReservationSelectionDialogForEdit(results);

                    if (selectedReservation != null) {
                        // 🔧【关键】将选择的完整预约号返回到 idField
                        String fullReservationId = selectedReservation.getReservationId();
                        if (fullReservationId != null) {
                            idField.setText(fullReservationId);
                            System.out.println(" 已选择预约号: " + fullReservationId);
                        }
                    } else {
                        // 用户取消选择：清空输入框
                        idField.setText("");
                        System.out.println(" 用户取消了预约选择");
                    }

                } catch (Exception e) {
                    showError("查询失败：" + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        // ═══════════════════════════════════════════════════════════
        // 【EDIT_TIME 模式】修改預約資料（支持多選修改項）
        // ═══════════════════════════════════════════════════════════
        else if ("EDIT_TIME".equals(mode)) {
            // ═══════════════════════════════════════════════════════════
            // 【步骤1】预约号输入 + 查询按钮
            // ═══════════════════════════════════════════════════════════
            JPanel idPanel = new JPanel(new BorderLayout(10, 0));
            idPanel.setBackground(new Color(245, 248, 255));

            JLabel idLabel = new JLabel("預約號:");
            idLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            idField.setEditable(true);
            idField.setPreferredSize(new Dimension(200, 25));

            JButton queryBtn = new JButton("🔍 查詢預約");
            queryBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            queryBtn.setBackground(new Color(30, 144, 255));
            queryBtn.setForeground(Color.WHITE);
            queryBtn.setFocusPainted(false);

            idPanel.add(idLabel, BorderLayout.WEST);
            idPanel.add(idField, BorderLayout.CENTER);
            idPanel.add(queryBtn, BorderLayout.EAST);

            formPanel.add(createFormField("", idPanel, false));

            // 🔧【关键修改】如果 existingReservation 不为空，预填预约号
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println("🔧 EDIT_TIME 模式：已预填预约号 " + resId);
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【关键修复】先声明显示字段变量（供查询按钮事件使用）
            // ═══════════════════════════════════════════════════════════
            JTextField infoReservationId = new JTextField();
            infoReservationId.setEditable(false);
            infoReservationId.setBackground(new Color(250, 250, 250));
            infoReservationId.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            infoReservationId.setForeground(new Color(30, 144, 255));

            JTextField infoName = new JTextField();
            JTextField infoPhone = new JTextField();
            JTextField infoTime = new JTextField();
            JLabel infoConfigLabel = new JLabel("未查询");
            JLabel infoPreOrderLabel = new JLabel("未查询");

            infoName.setEditable(false);
            infoPhone.setEditable(false);
            infoTime.setEditable(false);
            infoName.setBackground(new Color(250, 250, 250));
            infoPhone.setBackground(new Color(250, 250, 250));
            infoTime.setBackground(new Color(250, 250, 250));

            // ═══════════════════════════════════════════════════════════
            // 【步骤2】查询按钮事件 - 调用 Controller 查询
            // ═══════════════════════════════════════════════════════════
            // 查询按钮事件
            // 查询按钮事件 - 修改调用处
            queryBtn.addActionListener(ev -> {
                String resId = idField.getText().trim();
                if (resId.isEmpty()) {
                    showError("请输入预约号");
                    return;
                }

                try {
                    List<TableReservation> results = controller.findReservationsByCodeFragment(resId);

                    if (results == null || results.isEmpty()) {
                        showError("未找到预约记录：" + resId + "\n 不支持延期的預約再修改！如需调整，请联系管理员或重新创建预约。");
                        return;
                    }

                    if (results.size() == 1) {
                        TableReservation reservation = results.get(0);

                        // 🔧 关键：传入 formPanel 参数
                        fillReservationInfoToForm(reservation, formPanel);
                        idField.setText(reservation.getReservationId());
                        System.out.println(" 已加载预约详情: " + reservation.getReservationId());
                    } else {
                        TableReservation selectedReservation = showReservationSelectionDialogForEdit(results);
                        if (selectedReservation != null) {
                            // 🔧 关键：传入 formPanel 参数
                            fillReservationInfoToForm(selectedReservation, formPanel);
                            idField.setText(selectedReservation.getReservationId());
                        }
                    }
                } catch (Exception e) {
                    showError("查询失败：" + e.getMessage());
                    e.printStackTrace();
                }
            });

            JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 5));  // 🔧 改为 6 行（增加预约号行）
            infoPanel.setBackground(new Color(255, 255, 255));
            infoPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                            " 📋 当前预约信息 ",
                            TitledBorder.LEFT,
                            TitledBorder.TOP,
                            new Font("Microsoft YaHei", Font.BOLD, 13),
                            new Color(30, 144, 255)
                    ),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));

            // 🔧【关键】添加预约号显示行（放在第一位）
            infoPanel.add(new JLabel("预约号:"));
            infoPanel.add(infoReservationId);
            infoPanel.add(new JLabel("客人姓名:"));
            infoPanel.add(infoName);
            infoPanel.add(new JLabel("联系电话:"));
            infoPanel.add(infoPhone);
            infoPanel.add(new JLabel("预约时间:"));
            infoPanel.add(infoTime);
            infoPanel.add(new JLabel("餐桌配置:"));
            infoPanel.add(infoConfigLabel);
            infoPanel.add(new JLabel("预点餐:"));
            infoPanel.add(infoPreOrderLabel);

            formPanel.add(infoPanel);
            formPanel.add(Box.createVerticalStrut(10));

            // ═══════════════════════════════════════════════════════════
            // 【步骤4】各修改项的 CheckBox + 输入组件
            // ═══════════════════════════════════════════════════════════
            formPanel.add(new JLabel("<html><b style='color:#1976d2;'>📝 可选修改项（勾选后生效）</b></html>"));
            formPanel.add(Box.createVerticalStrut(5));

            // ── 修改时间 ──
            JCheckBox checkTime = new JCheckBox("修改预约时间");
            checkTime.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            checkTime.setBackground(new Color(245, 248, 255));
            JTextField newTimeField = new JTextField(
                    LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 20);
            newTimeField.setEnabled(false);
            checkTime.addActionListener(e -> newTimeField.setEnabled(checkTime.isSelected()));

            JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            timePanel.setBackground(new Color(245, 248, 255));
            timePanel.add(checkTime);
            timePanel.add(newTimeField);
            formPanel.add(createFormField("", timePanel, false));

            // ── 修改桌子配置 ──
            JCheckBox checkConfig = new JCheckBox("修改桌子配置");
            checkConfig.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            checkConfig.setBackground(new Color(245, 248, 255));

            JPanel configPanel = new JPanel();
            configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.Y_AXIS));
            configPanel.setBackground(new Color(245, 248, 255));
            configPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            configPanel.setMaximumSize(new Dimension(400, 150));

            // 2/4/6 人桌配置输入框
            JCheckBox twoCheck = new JCheckBox("2 人桌");
            JTextField twoQty = new JTextField("", 3);
            twoQty.setPreferredSize(new Dimension(40, 25));
            twoQty.setEnabled(false);

            JCheckBox fourCheck = new JCheckBox("4 人桌");
            JTextField fourQty = new JTextField("", 3);
            fourQty.setPreferredSize(new Dimension(40, 25));
            fourQty.setEnabled(false);

            JCheckBox sixCheck = new JCheckBox("6 人桌");
            JTextField sixQty = new JTextField("", 3);
            sixQty.setPreferredSize(new Dimension(40, 25));
            sixQty.setEnabled(false);

            // 启用监听器
            ActionListener enableQty = e -> {
                twoQty.setEnabled(twoCheck.isSelected());
                fourQty.setEnabled(fourCheck.isSelected());
                sixQty.setEnabled(sixCheck.isSelected());

                if (twoCheck.isSelected()) twoQty.requestFocus();
                else if (fourCheck.isSelected()) fourQty.requestFocus();
                else if (sixCheck.isSelected()) sixQty.requestFocus();
            };

            twoCheck.addActionListener(enableQty);
            fourCheck.addActionListener(enableQty);
            sixCheck.addActionListener(enableQty);

            // 组装行
            JPanel twoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            twoRow.setBackground(new Color(245, 248, 255));
            twoRow.add(twoCheck);
            twoRow.add(new JLabel("数量:"));
            twoRow.add(twoQty);
            twoRow.add(new JLabel("张"));
            configPanel.add(twoRow);

            JPanel fourRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            fourRow.setBackground(new Color(245, 248, 255));
            fourRow.add(fourCheck);
            fourRow.add(new JLabel("数量:"));
            fourRow.add(fourQty);
            fourRow.add(new JLabel("张"));
            configPanel.add(fourRow);

            JPanel sixRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            sixRow.setBackground(new Color(245, 248, 255));
            sixRow.add(sixCheck);
            sixRow.add(new JLabel("数量:"));
            sixRow.add(sixQty);
            sixRow.add(new JLabel("张"));
            configPanel.add(sixRow);

            // 初始禁用
            configPanel.setEnabled(false);

            checkConfig.addActionListener(e -> {
                boolean enabled = checkConfig.isSelected();
                configPanel.setEnabled(enabled);
                for (Component c : configPanel.getComponents()) {
                    if (c instanceof JPanel) {
                        for (Component cc : ((JPanel) c).getComponents()) {
                            if (cc instanceof JCheckBox || cc instanceof JTextField) {
                                cc.setEnabled(enabled);
                            }
                        }
                    }
                }
            });

            JPanel configWrapper = new JPanel(new BorderLayout(5, 5));
            configWrapper.setBackground(new Color(245, 248, 255));
            configWrapper.add(checkConfig, BorderLayout.NORTH);
            configWrapper.add(configPanel, BorderLayout.CENTER);
            formPanel.add(createFormField("", configWrapper, false));

            // ── 修改预点餐 ──
            JCheckBox checkPreOrder = new JCheckBox("修改预点餐");
            checkPreOrder.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            checkPreOrder.setBackground(new Color(245, 248, 255));

            Boolean currentPreOrder = existingReservation != null ?
                    (Boolean) existingReservation.getOrDefault("preOrder", false) : false;

            JPanel preOrderInner = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            preOrderInner.setBackground(new Color(245, 248, 255));
            preOrderInner.add(Box.createHorizontalStrut(15));

            JRadioButton changeToYesRadio = new JRadioButton("改为：是（可预点餐）");
            changeToYesRadio.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            changeToYesRadio.setBackground(new Color(245, 248, 255));

            if (currentPreOrder) {
                changeToYesRadio.setEnabled(false);
                JLabel hintLabel = new JLabel("（预点餐状态不可取消）");
                hintLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
                hintLabel.setForeground(Color.GRAY);
                preOrderInner.add(hintLabel);
            }

            preOrderInner.add(changeToYesRadio);
            preOrderInner.setEnabled(false);

            checkPreOrder.addActionListener(e -> {
                boolean enabled = checkPreOrder.isSelected();
                preOrderInner.setEnabled(enabled);
                for (Component c : preOrderInner.getComponents()) {
                    if (c instanceof JRadioButton) {
                        if (!currentPreOrder) {
                            c.setEnabled(enabled);
                        }
                    }
                }
            });

            JPanel preOrderWrapper = new JPanel(new BorderLayout(5, 5));
            preOrderWrapper.setBackground(new Color(245, 248, 255));
            preOrderWrapper.add(checkPreOrder, BorderLayout.NORTH);
            preOrderWrapper.add(preOrderInner, BorderLayout.CENTER);
            formPanel.add(createFormField("", preOrderWrapper, false));

            // ── 修改预付定金 ──
            JCheckBox checkPrepaid = new JCheckBox("修改预付定金");
            checkPrepaid.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            checkPrepaid.setBackground(new Color(245, 248, 255));

            Double originalPrepaid = existingReservation != null ?
                    (Double) existingReservation.getOrDefault("prepaidAmount", 0.0) : 0.0;

            JPanel prepaidInner = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            prepaidInner.setBackground(new Color(245, 248, 255));

            JLabel originalLabel = new JLabel("原定金: " + String.format("%.2f", originalPrepaid) + " 元");
            originalLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            originalLabel.setForeground(new Color(100, 100, 100));
            prepaidInner.add(originalLabel);

            prepaidInner.add(Box.createHorizontalStrut(15));

            JCheckBox prepaidCheck = new JCheckBox("已预付");
            prepaidCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            prepaidCheck.setBackground(new Color(245, 248, 255));
            prepaidInner.add(prepaidCheck);

            prepaidInner.add(Box.createHorizontalStrut(10));

            JLabel incrementLabel = new JLabel("增加金额 (¥):");
            incrementLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            JTextField incrementField = new JTextField("", 10);
            incrementField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            incrementField.setPreferredSize(new Dimension(100, 25));
            incrementField.setEnabled(false);

            prepaidCheck.addActionListener(ev -> {
                incrementField.setEnabled(prepaidCheck.isSelected());
                if (prepaidCheck.isSelected()) {
                    incrementField.setText("");
                    incrementField.requestFocus();
                } else {
                    incrementField.setText("");
                }
            });

            prepaidInner.add(incrementLabel);
            prepaidInner.add(incrementField);

            JLabel newTotalLabel = new JLabel("→ 新总额: " + String.format("%.2f", originalPrepaid) + " 元");
            newTotalLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            newTotalLabel.setForeground(new Color(30, 144, 255));
            prepaidInner.add(newTotalLabel);

            incrementField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void updateTotal() {
                    try {
                        String incStr = incrementField.getText().trim();
                        double increment = incStr.isEmpty() ? 0.0 : Double.parseDouble(incStr);
                        if (increment >= 0) {
                            // 🔧【核心修复】获取最新的原定金值
                            Double currentOriginalPrepaid = originalPrepaid;

                            // 尝试从 originalLabel 提取最新值（如果查询后更新了）
                            JLabel originalLabelRef = (JLabel) formPanel.getClientProperty("editOriginalPrepaidLabel");
                            if (originalLabelRef != null) {
                                String labelText = originalLabelRef.getText();
                                try {
                                    currentOriginalPrepaid = Double.parseDouble(
                                            labelText.replaceAll("[^0-9.]", "")
                                    );
                                } catch (Exception e) {
                                    // 解析失败，使用原值
                                }
                            }

                            // 计算新总额 = 原定金 + 增加金额
                            double newTotal = currentOriginalPrepaid + increment;
                            newTotalLabel.setText("→ 新总额: " + String.format("%.2f", newTotal) + " 元");
                            newTotalLabel.setForeground(new Color(30, 144, 255));  // 蓝色显示
                        }
                    } catch (NumberFormatException e) {
                        newTotalLabel.setText("→ 新总额: 格式错误");
                        newTotalLabel.setForeground(Color.RED);
                    }
                }

                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    updateTotal();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    updateTotal();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    updateTotal();
                }
            });


            prepaidInner.setEnabled(false);
            for (Component c : prepaidInner.getComponents()) {
                if (c instanceof JTextField || c instanceof JCheckBox) {
                    c.setEnabled(false);
                }
            }

            checkPrepaid.addActionListener(e -> {
                boolean enabled = checkPrepaid.isSelected();
                prepaidInner.setEnabled(enabled);
                for (Component c : prepaidInner.getComponents()) {
                    if (c instanceof JTextField || c instanceof JCheckBox) {
                        c.setEnabled(enabled);
                    }
                }
            });

            JPanel prepaidWrapper = new JPanel(new BorderLayout(5, 5));
            prepaidWrapper.setBackground(new Color(245, 248, 255));
            prepaidWrapper.add(checkPrepaid, BorderLayout.NORTH);
            prepaidWrapper.add(prepaidInner, BorderLayout.CENTER);
            formPanel.add(createFormField("", prepaidWrapper, false));

            // ── 修改备注 ──
            JCheckBox checkNotes = new JCheckBox("修改备注");
            checkNotes.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            checkNotes.setBackground(new Color(245, 248, 255));

            JTextArea notesArea = new JTextArea(3, 20);
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);
            notesArea.setEditable(true);
            JScrollPane notesScroll = new JScrollPane(notesArea);
            notesScroll.setPreferredSize(new Dimension(300, 80));

            notesArea.setEnabled(false);
            // checkNotes.addActionListener(e -> notesArea.setEnabled(checkNotes.isSelected()));
            checkNotes.addActionListener(e -> {
                boolean enabled = checkNotes.isSelected();
                notesArea.setEnabled(enabled);
                notesArea.setEditable(enabled);
            });

            JPanel notesWrapper = new JPanel(new BorderLayout(5, 5));
            notesWrapper.setBackground(new Color(245, 248, 255));
            notesWrapper.add(checkNotes, BorderLayout.NORTH);
            notesWrapper.add(notesScroll, BorderLayout.CENTER);
            formPanel.add(createFormField("", notesWrapper, false));

            // ═══════════════════════════════════════════════════════════
            // 【步骤5】保存引用供确认按钮使用
            // ═══════════════════════════════════════════════════════════
            formPanel.putClientProperty("editInfoReservationId", infoReservationId);
            formPanel.putClientProperty("editInfoName", infoName);
            formPanel.putClientProperty("editInfoPhone", infoPhone);
            formPanel.putClientProperty("editInfoTime", infoTime);
            formPanel.putClientProperty("editInfoConfigLabel", infoConfigLabel);
            formPanel.putClientProperty("editInfoPreOrderLabel", infoPreOrderLabel);

            formPanel.putClientProperty("editCheckTime", checkTime);
            formPanel.putClientProperty("editNewTime", newTimeField);
            formPanel.putClientProperty("editCheckConfig", checkConfig);
            formPanel.putClientProperty("editTwoCheck", twoCheck);
            formPanel.putClientProperty("editTwoQty", twoQty);
            formPanel.putClientProperty("editFourCheck", fourCheck);
            formPanel.putClientProperty("editFourQty", fourQty);
            formPanel.putClientProperty("editSixCheck", sixCheck);
            formPanel.putClientProperty("editSixQty", sixQty);

            formPanel.putClientProperty("editCheckPreOrder", checkPreOrder);
            formPanel.putClientProperty("editPreOrderRadio", changeToYesRadio);
            formPanel.putClientProperty("editCurrentPreOrder", currentPreOrder);

            formPanel.putClientProperty("editCheckPrepaid", checkPrepaid);
            formPanel.putClientProperty("editPrepaidCheck", prepaidCheck);
            formPanel.putClientProperty("editIncrementField", incrementField);

            formPanel.putClientProperty("editCheckNotes", checkNotes);
            formPanel.putClientProperty("editNotesArea", notesArea);
            formPanel.putClientProperty("editOriginalPrepaidLabel", originalLabel);
        }


        formPanel.revalidate();
        formPanel.repaint();
    }


    private JPanel createFormField(String labelText, Component inputComponent, boolean fixedHeight) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(new Color(245, 248, 255));
        if (fixedHeight) {
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        }
        panel.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        label.setForeground(labelText.contains("*") ? new Color(30, 144, 255) : new Color(100, 100, 100));
        panel.add(label, BorderLayout.WEST);

        // 🔧【关键修复】只有当 inputComponent 不为 null 时才添加到面板
        if (inputComponent != null) {
            panel.add(inputComponent, BorderLayout.CENTER);
        } else {
            // 如果为 null，留出一个空白占位区域，保持布局一致
            panel.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        }

        return panel;
    }

    // 重載方法：預設 fixedHeight = true（兼容原有 CREATE 模式呼叫）
    private JPanel createFormField(String labelText, Component inputComponent) {
        return createFormField(labelText, inputComponent, true);
    }

    // 🔧 新增辅助方法：计算并更新锁定状态

    private void updateLockStatus(JSpinner dateSpinner, JTextField timeFieldComp, JLabel lockStatusLabel) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            Date dateVal = (Date) dateSpinner.getValue();
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(dateVal);
            String timeStr = timeFieldComp.getText().trim();

            // 格式化時間為標準格式（9:00 → 09:00）
            timeStr = formatTimeToStandard(timeStr);

            // 验证时间格式，格式不对则不更新
            if (!timeStr.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                lockStatusLabel.setText("锁定状态：⚠ 时间格式错误");
                lockStatusLabel.setForeground(Color.ORANGE);
                return;
            }

            LocalDateTime reserveTime = LocalDateTime.parse(dateStr + " " + timeStr, formatter);
            long minutes = java.time.Duration.between(LocalDateTime.now(), reserveTime).toMinutes();

            if (minutes < 0) {
                lockStatusLabel.setText("锁定状态： 不能预约过去的时间");
                lockStatusLabel.setForeground(Color.RED);
            } else if (minutes <= 90) {
                lockStatusLabel.setText("锁定状态： 将立即锁定餐桌 (RESERVED)");
                lockStatusLabel.setForeground(new Color(200, 0, 0));
            } else {
                lockStatusLabel.setText("锁定状态： 到店后锁定 (暂不锁桌)");
                lockStatusLabel.setForeground(new Color(0, 150, 0));
            }
        } catch (Exception e) {
            lockStatusLabel.setText("锁定状态： 计算错误");
            lockStatusLabel.setForeground(Color.RED);
        }
    }

    // 🔧【新增輔助方法】格式化時間為標準格式（9:00 → 09:00）
    private String formatTimeToStandard(String timeStr) {
        if (timeStr == null || !timeStr.contains(":")) {
            return timeStr;
        }

        String[] parts = timeStr.split(":");
        if (parts.length != 2) {
            return timeStr;
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // 格式化為兩位數（09:00）
            return String.format("%02d:%02d", hour, minute);
        } catch (NumberFormatException e) {
            return timeStr;
        }
    }


    /**
     * 🔧 類成員方法：自動選擇是否 1.5 小時內到店
     *
     * @param dateSpinner 日期選擇器
     * @param timeField   時間輸入框
     * @param yesRadio    "是"單選按鈕
     * @param noRadio     "否"單選按鈕
     */
    private void autoSelectWithin15h(JSpinner dateSpinner, JTextField timeField,
                                     JRadioButton yesRadio, JRadioButton noRadio) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            Date dateVal = (Date) dateSpinner.getValue();
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(dateVal);
            String timeStr = timeField.getText().trim();

            // 格式化時間為標準格式（9:00 → 09:00）
            timeStr = formatTimeToStandard(timeStr);

            // 驗證時間格式，格式不對則不自動選擇
            if (!timeStr.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                return;
            }

            LocalDateTime reserveTime = LocalDateTime.parse(dateStr + " " + timeStr, formatter);
            long minutes = java.time.Duration.between(LocalDateTime.now(), reserveTime).toMinutes();

            // 🔧 核心邏輯：自動選擇
            if (minutes < 0) {
                // 過去的時間，選擇"否"
                noRadio.setSelected(true);
            } else if (minutes <= 90) {
                // 1.5 小時內，選擇"是"
                yesRadio.setSelected(true);
            } else {
                // 超過 1.5 小時，選擇"否"
                noRadio.setSelected(true);
            }
        } catch (Exception e) {
            // 計算錯誤時不自動選擇，保持用戶手動選擇
            System.err.println("自動選擇 1.5 小時選項失敗: " + e.getMessage());
        }
    }


    /**
     * 🔧 填充预约信息到界面
     */
    private void fillReservationInfo(
            Map<String, Object> reservation,
            JTextField idField,
            JTextField nameField,
            JTextField phoneField,
            JTextField timeField,
            JLabel configLabel,
            JTextArea notesArea) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 预约号
        String resId = (String) reservation.get("reservation_id");
        idField.setText(resId != null ? resId : "");

        // 客人姓名
        String name = (String) reservation.get("customer_name");
        nameField.setText(name != null ? name : "");

        // 联系电话
        String phone = (String) reservation.get("customer_phone");
        phoneField.setText(phone != null ? phone : "");

        // 预约时间
        Object timeObj = reservation.get("reservation_time");
        if (timeObj instanceof java.sql.Timestamp) {
            timeField.setText(((java.sql.Timestamp) timeObj).toLocalDateTime().format(formatter));
        } else if (timeObj instanceof LocalDateTime) {
            timeField.setText(((LocalDateTime) timeObj).format(formatter));
        } else {
            timeField.setText("");
        }

        // 餐桌配置
        String config = (String) reservation.get("table_config_desc");
        configLabel.setText(config != null ? config : "未设置");
        configLabel.setForeground(Color.BLACK);

        if (notesArea != null) {
            String notes = (String) reservation.get("notes");
            notesArea.setText(notes != null ? notes : "");
        }
    }


    /**
     * 🔧 显示预约选择对话框（修改资料专用 - 多个匹配结果时）
     *
     * @param results 查询结果列表
     * @return 用户选择的预约记录，取消则返回 null
     */
    private TableReservation showReservationSelectionDialogForEdit(List<TableReservation> results) {
        // 创建对话框
        JDialog dialog = new JDialog(this, "📋 选择预约记录", true);
        dialog.setSize(650, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // ═══════════════════════════════════════════════════════════
        // 【顶部标题面板】
        // ═══════════════════════════════════════════════════════════
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 144, 255));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel(
                "<html><b style='font-size:16px;color:white;'>🔍 找到 " + results.size() + " 条匹配记录</b></html>",
                SwingConstants.CENTER
        );
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(
                "<html><span style='font-size:12px;color:#e0f0ff;'>请选择要修改的预约记录</span></html>",
                SwingConstants.CENTER
        );
        subtitleLabel.setForeground(new Color(224, 240, 255));

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        dialog.add(headerPanel, BorderLayout.NORTH);

        // ═══════════════════════════════════════════════════════════
        // 【中间列表区域】- 卡片式布局
        // ═══════════════════════════════════════════════════════════
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(250, 250, 250));
        listPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        final TableReservation[] selectedResult = new TableReservation[1];

        for (int i = 0; i < results.size(); i++) {
            TableReservation res = results.get(i);
            JPanel cardPanel = createReservationEditCard(res, formatter, i);

            // 点击事件
            cardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cardPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedResult[0] = res;
                    dialog.dispose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    cardPanel.setBackground(new Color(232, 245, 253));
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                            new EmptyBorder(12, 18, 12, 18)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cardPanel.setBackground(Color.WHITE);
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                            new EmptyBorder(13, 19, 13, 19)
                    ));
                }
            });

            listPanel.add(cardPanel);
            listPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(250, 250, 250));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        // 【底部按钮面板】
        // ═══════════════════════════════════════════════════════════
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(new Color(245, 248, 255));

        JButton confirmBtn = createStyledButton("✓ 确定选择", new Color(76, 175, 80));
        JButton cancelBtn = createStyledButton("✗ 取消", new Color(158, 158, 158));

        confirmBtn.addActionListener(e -> {
            if (listPanel.getComponentCount() > 0) {
                selectedResult[0] = results.get(0);
                dialog.dispose();
            } else {
                showError("没有可选记录");
            }
        });

        cancelBtn.addActionListener(e -> {
            selectedResult[0] = null;
            dialog.dispose();
        });

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedResult[0];
    }

    /**
     * 创建预约记录卡片（修改资料专用）
     */
    private JPanel createReservationEditCard(TableReservation res, DateTimeFormatter formatter, int index) {
        JPanel card = new JPanel(new BorderLayout(10, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                new EmptyBorder(13, 19, 13, 19)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        // 左侧：序号圆圈
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setBackground(Color.WHITE);
        leftPanel.setOpaque(false);

        JLabel indexLabel = new JLabel(String.valueOf(index + 1));
        indexLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        indexLabel.setForeground(new Color(30, 144, 255));
        indexLabel.setPreferredSize(new Dimension(30, 30));
        indexLabel.setHorizontalAlignment(SwingConstants.CENTER);
        indexLabel.setBorder(BorderFactory.createLineBorder(new Color(30, 144, 255), 1));

        leftPanel.add(indexLabel);

        // 中间：详细信息
        JPanel infoPanel = new JPanel(new GridLayout(4, 1, 0, 3));
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setOpaque(false);

        String resId = res.getReservationId();
        String name = res.getCustomerName();
        String phone = res.getCustomerPhone();
        String timeStr = res.getReservationTime().format(formatter);
        String status = res.getStatus();

        // 第一行：预约号（蓝色加粗）
        JLabel idLabel = new JLabel(" 预约号：" + resId);
        idLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        idLabel.setForeground(new Color(30, 144, 255));

        // 第二行：姓名 + 电话
        JLabel nameLabel = new JLabel(" " + name + "  |   " + phone);
        nameLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        nameLabel.setForeground(new Color(80, 80, 80));

        // 第三行：时间
        JLabel timeLabel = new JLabel(" " + timeStr);
        timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(120, 120, 120));

        // 第四行：状态
        JLabel statusLabel = new JLabel(" 状态：" + getStatusText(status));
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        statusLabel.setForeground(getStatusColor(status));

        infoPanel.add(idLabel);
        infoPanel.add(nameLabel);
        infoPanel.add(timeLabel);
        infoPanel.add(statusLabel);

        // 右侧：箭头图标
        JLabel arrowLabel = new JLabel("›");
        arrowLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        arrowLabel.setForeground(new Color(200, 200, 200));
        arrowLabel.setHorizontalAlignment(SwingConstants.CENTER);
        arrowLabel.setPreferredSize(new Dimension(30, 60));

        card.add(leftPanel, BorderLayout.WEST);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(arrowLabel, BorderLayout.EAST);

        return card;
    }

    /**
     * 获取状态显示文本
     */
    private String getStatusText(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "PRE_CONFIRMED" -> "待确认";
            case "CONFIRMED" -> "已确认";
            case "DELAYED" -> "已延迟";
            case "COMPLETED" -> "已完成";
            case "NO_SHOW" -> "未到店";
            default -> status;
        };
    }

    /**
     * 获取状态颜色
     */
    private Color getStatusColor(String status) {
        if (status == null) return Color.GRAY;
        return switch (status) {
            case "PRE_CONFIRMED" -> new Color(255, 152, 0);    // 橙色
            case "CONFIRMED" -> new Color(76, 175, 80);        // 绿色
            case "DELAYED" -> new Color(244, 67, 54);          // 红色
            case "COMPLETED" -> new Color(33, 150, 243);       // 蓝色
            case "NO_SHOW" -> new Color(158, 158, 158);        // 灰色
            default -> Color.GRAY;
        };
    }


    /**
     * 🔧 填充预约信息到表单（修改资料专用）
     *
     * @param reservation 预约记录
     * @param formPanel   当前表单面板（用于获取组件引用）
     */
    private void fillReservationInfoToForm(TableReservation reservation, JPanel formPanel) {
        if (reservation == null || formPanel == null) return;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 🔧 从传入的 formPanel 获取组件引用
        JTextField infoReservationId = (JTextField) formPanel.getClientProperty("editInfoReservationId");
        if (infoReservationId != null) {
            infoReservationId.setText(reservation.getReservationId());
        }

        JTextField infoName = (JTextField) formPanel.getClientProperty("editInfoName");
        if (infoName != null) {
            infoName.setText(reservation.getCustomerName());
        }

        JTextField infoPhone = (JTextField) formPanel.getClientProperty("editInfoPhone");
        if (infoPhone != null) {
            infoPhone.setText(reservation.getCustomerPhone());
        }

        JTextField infoTime = (JTextField) formPanel.getClientProperty("editInfoTime");
        if (infoTime != null) {
            infoTime.setText(reservation.getReservationTime().format(formatter));
        }

        JLabel infoConfigLabel = (JLabel) formPanel.getClientProperty("editInfoConfigLabel");
        if (infoConfigLabel != null) {
            String configDesc = reservation.getTableConfigDesc();
            infoConfigLabel.setText(configDesc != null ? configDesc : "未设置");
            infoConfigLabel.setForeground(Color.BLACK);
        }

        // 🔧 新增：填充预点餐信息
        JLabel infoPreOrderLabel = (JLabel) formPanel.getClientProperty("editInfoPreOrderLabel");
        if (infoPreOrderLabel != null) {
            Boolean preOrder = reservation.getPreOrder();
            infoPreOrderLabel.setText(preOrder != null && preOrder ? " 是" : " 否");
            infoPreOrderLabel.setForeground(preOrder != null && preOrder ? new Color(0, 128, 0) : new Color(100, 100, 100));
        }

        // 🔧【关键修复】更新原定金显示
        JLabel originalPrepaidLabel = (JLabel) formPanel.getClientProperty("editOriginalPrepaidLabel");
        if (originalPrepaidLabel != null) {
            Double prepaidAmount = reservation.getPrepaidAmount();
            if (prepaidAmount != null && prepaidAmount > 0) {
                originalPrepaidLabel.setText("原定金: " + String.format("%.2f", prepaidAmount) + " 元");
                originalPrepaidLabel.setForeground(new Color(0, 128, 0)); // 绿色显示
            } else {
                originalPrepaidLabel.setText("原定金: 0.00 元");
                originalPrepaidLabel.setForeground(new Color(100, 100, 100));
            }
        }

        System.out.println(" 已填充预约信息: " + reservation.getReservationId());
    }

    /**
     * 🔧 显示预约选择对话框（多个匹配结果时）
     *
     * @param results      查询结果列表
     * @param isPhoneQuery 是否为电话号码查询
     * @param inputValue   输入值
     * @return 用户选择的预约记录，取消则返回 null
     */

    private Map<String, Object> showReservationSelectionDialog(
            List<Map<String, Object>> results,
            boolean isPhoneQuery,
            String inputValue) {

        // 创建对话框
        JDialog dialog = new JDialog(this, "📋 选择预约记录", true);
        dialog.setSize(650, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // ═══════════════════════════════════════════════════════════
        // 【顶部标题面板】
        // ═══════════════════════════════════════════════════════════
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 144, 255));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel(
                "<html><b style='font-size:16px;color:white;'>🔍 找到 " + results.size() + " 条匹配记录</b></html>",
                SwingConstants.CENTER
        );
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(
                "<html><span style='font-size:12px;color:#e0f0ff;'>查询条件：" +
                        (isPhoneQuery ? "电话号码" : "预约号") +
                        " 包含 \"" + inputValue + "\"</span></html>",
                SwingConstants.CENTER
        );
        subtitleLabel.setForeground(new Color(224, 240, 255));

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        dialog.add(headerPanel, BorderLayout.NORTH);

        // ═══════════════════════════════════════════════════════════
        // 【中间列表区域】- 使用卡片式布局
        // ═══════════════════════════════════════════════════════════
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(250, 250, 250));
        listPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        final Map<String, Object>[] selectedResult = new Map[1];

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> res = results.get(i);
            JPanel cardPanel = createReservationCard(res, formatter, i);

            // 点击事件
            cardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cardPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedResult[0] = res;
                    dialog.dispose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    cardPanel.setBackground(new Color(232, 245, 253));
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                            new EmptyBorder(12, 18, 12, 18)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cardPanel.setBackground(Color.WHITE);
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                            new EmptyBorder(13, 19, 13, 19)
                    ));
                }
            });

            listPanel.add(cardPanel);
            listPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(250, 250, 250));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════
        // 【底部按钮面板】
        // ═══════════════════════════════════════════════════════════
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(new Color(245, 248, 255));

        JButton confirmBtn = createStyledButton("✓ 确定选择", new Color(76, 175, 80));
        JButton cancelBtn = createStyledButton("✗ 取消", new Color(158, 158, 158));

        confirmBtn.addActionListener(e -> {
            if (listPanel.getComponentCount() > 0) {
                // 默认选择第一个
                selectedResult[0] = results.get(0);
                dialog.dispose();
            } else {
                showError("没有可选记录");
            }
        });

        cancelBtn.addActionListener(e -> {
            selectedResult[0] = null;
            dialog.dispose();
        });

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedResult[0];
    }

    /**
     * 创建预约记录卡片
     */
    private JPanel createReservationCard(Map<String, Object> res, DateTimeFormatter formatter, int index) {
        JPanel card = new JPanel(new BorderLayout(10, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                new EmptyBorder(13, 19, 13, 19)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        // 左侧：序号圆圈
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setBackground(Color.WHITE);
        leftPanel.setOpaque(false);

        JLabel indexLabel = new JLabel(String.valueOf(index + 1));
        indexLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        indexLabel.setForeground(new Color(30, 144, 255));
        indexLabel.setPreferredSize(new Dimension(30, 30));
        indexLabel.setHorizontalAlignment(SwingConstants.CENTER);
        indexLabel.setBorder(BorderFactory.createLineBorder(new Color(30, 144, 255), 1));

        leftPanel.add(indexLabel);

        // 中间：详细信息
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 0, 3));
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setOpaque(false);

        String resId = (String) res.get("reservation_id");
        String name = (String) res.get("customer_name");
        String phone = (String) res.get("customer_phone");
        Object timeObj = res.get("reservation_time");

        String timeStr = "未知时间";
        if (timeObj instanceof java.sql.Timestamp) {
            timeStr = ((java.sql.Timestamp) timeObj).toLocalDateTime().format(formatter);
        } else if (timeObj instanceof LocalDateTime) {
            timeStr = ((LocalDateTime) timeObj).format(formatter);
        }

        // 第一行：预约号（蓝色加粗）
        JLabel idLabel = new JLabel(" 预约号：" + resId);
        idLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        idLabel.setForeground(new Color(30, 144, 255));

        // 第二行：姓名 + 电话
        JLabel nameLabel = new JLabel(" " + name + "  |   " + phone);
        nameLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        nameLabel.setForeground(new Color(80, 80, 80));

        // 第三行：时间
        JLabel timeLabel = new JLabel(" " + timeStr);
        timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(120, 120, 120));

        infoPanel.add(idLabel);
        infoPanel.add(nameLabel);
        infoPanel.add(timeLabel);

        // 右侧：箭头图标
        JLabel arrowLabel = new JLabel("›");
        arrowLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        arrowLabel.setForeground(new Color(200, 200, 200));
        arrowLabel.setHorizontalAlignment(SwingConstants.CENTER);
        arrowLabel.setPreferredSize(new Dimension(30, 60));

        card.add(leftPanel, BorderLayout.WEST);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(arrowLabel, BorderLayout.EAST);

        return card;
    }


    private void loadAvailableTables(
            Map<String, Object> reservation,
            JPanel tablesGrid,
            Map<String, JCheckBox> assignTableCheckBoxes) {

        tablesGrid.removeAll();
        assignTableCheckBoxes.clear();

        // ═══════════════════════════════════════════════════════════
        // 【步骤 1】解析预约配置（桌子数量 + 容量）
        // ═══════════════════════════════════════════════════════════
        String configDesc = (String) reservation.get("table_config_desc");
        String selectionMode = (String) reservation.get("table_selection_mode");
        String groupType = (String) reservation.get("group_type");

        int targetCapacity = 0;
        int tableCount = 0;

        if (configDesc != null && !configDesc.isEmpty()) {
            // 🔧【修复】同时支持带空格和不带空格的格式
            String normalizedDesc = configDesc.replaceAll("\\s+", "");

            if (normalizedDesc.contains("2人桌")) {
                targetCapacity = 2;
            } else if (normalizedDesc.contains("4人桌")) {
                targetCapacity = 4;
            } else if (normalizedDesc.contains("6人桌")) {
                targetCapacity = 6;
            }

            if (normalizedDesc.contains("x")) {
                String[] parts = normalizedDesc.split("x");
                if (parts.length > 1) {
                    try {
                        tableCount = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                    } catch (Exception e) {
                        tableCount = 1;
                    }
                }
            }

            if (tableCount == 0) {
                Object countObj = reservation.get("table_count");
                if (countObj instanceof Number) {
                    tableCount = ((Number) countObj).intValue();
                } else {
                    tableCount = 1;
                }
            }
        }

        // 🔧【修复】如果 group_type 为 null，根据数量推断
        if (groupType == null || groupType.isEmpty()) {
            if (tableCount == 1) {
                groupType = "MAIN";
            } else if (tableCount == 2) {
                groupType = "MERGED";
            } else if (tableCount >= 3) {
                groupType = "GROUP";
            } else {
                groupType = "MAIN";
            }
        }

        System.out.println("🔧 分配餐桌 - 配置解析：容量=" + targetCapacity +
                "人，数量=" + tableCount + "张，桌型=" + groupType);

        // ═══════════════════════════════════════════════════════════
        // 【步骤 2】根据规则计算允许的容量范围
        // ═══════════════════════════════════════════════════════════
        boolean allow2Seat = false;
        boolean allow4Seat = false;
        boolean allow6Seat = false;

        if ("MAIN".equals(groupType)) {
            if (tableCount == 1) {
                if (targetCapacity == 2) {
                    allow2Seat = true;
                    allow4Seat = true;
                    allow6Seat = false;
                    System.out.println("  规则：2人桌预约 → 允许2人/4人桌");
                } else if (targetCapacity == 4) {
                    allow2Seat = false;
                    allow4Seat = true;
                    allow6Seat = true;
                    System.out.println("  规则：4人桌预约 → 允许4人/6人桌");
                } else if (targetCapacity == 6) {
                    allow2Seat = false;
                    allow4Seat = false;
                    allow6Seat = true;
                    System.out.println("  规则：6人桌预约 → 只允许6人桌");
                }
            }
        } else if ("MERGED".equals(groupType)) {
            if (tableCount == 2) {
                if (targetCapacity == 4) {
                    allow2Seat = false;
                    allow4Seat = true;
                    allow6Seat = false;
                    System.out.println("  规则：合并桌(4人) → 只允许4人桌");
                } else if (targetCapacity == 6) {
                    allow2Seat = false;
                    allow4Seat = false;
                    allow6Seat = true;
                    System.out.println("  规则：合并桌(6人) → 只允许6人桌");
                }
            }
        } else if ("GROUP".equals(groupType)) {
            allow2Seat = false;
            allow4Seat = false;
            allow6Seat = true;
            System.out.println("  规则：聚餐桌 → 只允许6人桌");
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 3】获取空闲餐桌并应用过滤规则
        // ═══════════════════════════════════════════════════════════
        if (controller != null) {
            try {
                List<Tables> allTables = controller.getAllVacantTables();
                if (allTables != null && !allTables.isEmpty()) {
                    for (Tables table : allTables) {
                        if (table.getStatus() == Tables.TableStatus.VACANT) {
                            String displayId = table.getDisplayId();
                            int capacity = table.getCapacity();

                            boolean isMatch = false;
                            if (capacity == 2 && allow2Seat) isMatch = true;
                            else if (capacity == 4 && allow4Seat) isMatch = true;
                            else if (capacity == 6 && allow6Seat) isMatch = true;

                            JCheckBox tableCheck = new JCheckBox(
                                    String.format("桌%s (%d人)", displayId, capacity)
                            );
                            tableCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                            tableCheck.setBackground(new Color(255, 255, 255));

                            if (!isMatch) {
                                tableCheck.setEnabled(false);
                                tableCheck.setToolTipText("不符合预定桌型配置（容量不匹配）");
                                tableCheck.setForeground(Color.GRAY);
                            } else {
                                tableCheck.setToolTipText(String.format("容量：%d人", capacity));
                                tableCheck.setForeground(Color.BLACK);
                            }

                            tablesGrid.add(tableCheck);
                            assignTableCheckBoxes.put(displayId, tableCheck);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (tablesGrid.getComponentCount() == 0) {
            tablesGrid.add(new JLabel("⚠️ 暂无符合条件的空闲餐桌", SwingConstants.CENTER));
        }

        tablesGrid.revalidate();
        tablesGrid.repaint();
    }


    /**
     * 🔧 显示已预定餐桌的操作对话框（3个按钮）
     *
     * @param table 餐桌对象
     * @return 用户选择的操作类型："CHECK_IN" / "CANCEL" / "DELAY" / null(取消)
     */
    public String showReservedTableDialog(Tables table) {
        String displayId = table.getDisplayId();
        JDialog dialog = new JDialog(this, "📋 预定餐桌操作 - #" + displayId, true);
        dialog.setSize(420, 400);  // 🔧 高度+20，预留延迟时间显示空间
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // 信息面板 🔧 改为自动行数（0=自动扩展），支持动态添加延迟时间行
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        infoPanel.setBackground(new Color(245, 248, 255));
        infoPanel.setBorder(new EmptyBorder(15, 20, 10, 20));

        infoPanel.add(new JLabel("餐桌号:"));
        infoPanel.add(new JLabel(displayId));
        infoPanel.add(new JLabel("容量:"));
        infoPanel.add(new JLabel(table.getCapacity() + "人"));

        // ═══════════════════════════════════════════════════════════
        // 🔧【核心修改】显示预约入座时间（从 table_reservations 表查询）
        // ═══════════════════════════════════════════════════════════
        String reservationTimeStr = "未设置";
        String rescheduledTimeStr = "";  // 🔧【新增】延迟预约时间
        String reservationId = table.getCurrentReservationId();

        if (reservationId != null && !reservationId.isEmpty() && controller != null) {
            try {
                com.restaurant.entity.TableReservation reservation =
                        controller.getReservationDetail(reservationId);

                if (reservation != null) {
                    // 原预约时间
                    if (reservation.getReservationTime() != null) {
                        reservationTimeStr = reservation.getReservationTime()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    }
                    // 🔧【新增】延迟预约时间（仅当有值时显示）
                    if (reservation.getRescheduledTime() != null) {
                        rescheduledTimeStr = reservation.getRescheduledTime()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    }
                }
            } catch (Exception e) {
                System.err.println("查询预约详情失败: " + e.getMessage());
            }
        }

        infoPanel.add(new JLabel("预约入座时间:"));
        infoPanel.add(new JLabel(reservationTimeStr));

        // 🔧【新增】延迟预约时间行（仅当有延迟时间时添加）
        if (!rescheduledTimeStr.isEmpty()) {
            JLabel delayLabel = new JLabel("延迟的预约时间:");
            JLabel delayValue = new JLabel(
                    "<html><font color='#ff9800'><b>" + rescheduledTimeStr + "</b></font></html>"
            );  // 🔧 橙色加粗显示
            infoPanel.add(delayLabel);
            infoPanel.add(delayValue);
        }
        // ═══════════════════════════════════════════════════════════

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】关联桌号：仅合并桌/聚餐桌显示
        // ═══════════════════════════════════════════════════════════
        JLabel relatedTableLabel = new JLabel("关联桌号:");
        JLabel relatedTableValue = new JLabel("无");

        boolean isMergedOrGrouped =
                table.getTableType() == Tables.TableType.MERGED ||
                        table.getTableType() == Tables.TableType.GROUPED;

        if (isMergedOrGrouped) {
            if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
                relatedTableValue.setText(table.getMergedWith());
            } else if (table.getGroupWith() != null && !table.getGroupWith().isEmpty()) {
                relatedTableValue.setText(table.getGroupWith());
            }
            infoPanel.add(relatedTableLabel);
            infoPanel.add(relatedTableValue);
        }

        dialog.add(infoPanel, BorderLayout.CENTER);

        // 按钮面板（3个按钮）
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 10, 15));
        buttonPanel.setBackground(new Color(245, 248, 255));
        buttonPanel.setBorder(new EmptyBorder(10, 30, 20, 30));

        final String[] result = new String[1];

        // 按钮1：客人已入座
        JButton checkInBtn = new JButton(" 客人已入座");
        checkInBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        checkInBtn.setBackground(new Color(76, 175, 80));
        checkInBtn.setForeground(Color.WHITE);
        checkInBtn.setFocusPainted(false);
        checkInBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkInBtn.addActionListener(e -> {
            result[0] = "CHECK_IN";
            dialog.dispose();
        });

        // 按钮2：取消预约
        JButton cancelBtn = new JButton(" 取消预约");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        cancelBtn.setBackground(new Color(244, 67, 54));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> {
            result[0] = "CANCEL";
            dialog.dispose();
        });

        // 按钮3：延迟预约
        JButton delayBtn = new JButton(" 延迟");
        delayBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        delayBtn.setBackground(new Color(255, 165, 0));
        delayBtn.setForeground(Color.WHITE);
        delayBtn.setFocusPainted(false);
        delayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        delayBtn.addActionListener(e -> {
            // ═══════════════════════════════════════════════════════════
            // 🔧【核心实现】延迟预约功能
            // ═══════════════════════════════════════════════════════════

            // 1️⃣ 获取预约号
            String delayReservationId = table.getCurrentReservationId();

            // 如果 currentReservationId 为空，尝试反向查询
            if (delayReservationId == null || delayReservationId.isEmpty()) {
                if (controller != null) {
                    com.restaurant.entity.TableReservation found =
                            controller.findReservationByTableId(table.getDisplayId());
                    if (found != null) {
                        delayReservationId = found.getReservationId();
                    }
                }
            }

            if (delayReservationId == null || delayReservationId.isEmpty()) {
                showError("⚠️ 未找到关联的预约记录！");
                return;
            }

            // 2️⃣ 弹出时间输入对话框（限制当天 + 非过去时间）
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = now.toLocalDate();

            // 创建时间选择面板
            JPanel timePanel = new JPanel(new GridLayout(2, 2, 10, 10));
            timePanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            // 日期选择器（只能选今天）
            SpinnerDateModel dateModel = new SpinnerDateModel();
            dateModel.setCalendarField(Calendar.DAY_OF_MONTH);
            JSpinner dateSpinner = new JSpinner(dateModel);
            JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
            dateSpinner.setEditor(dateEditor);
            dateSpinner.setValue(java.sql.Timestamp.valueOf(now));
            dateSpinner.setEnabled(false);  // 🔧 锁定为今天

            // 时间输入框
            JTextField timeField = new JTextField(now.format(DateTimeFormatter.ofPattern("HH:mm")), 10);
            timeField.setToolTipText("请输入时间，格式：HH:mm（例：18:30）");

            timePanel.add(new JLabel("预约日期（仅限今天）:"));
            timePanel.add(dateSpinner);
            timePanel.add(new JLabel("新预约时间 *:"));
            timePanel.add(timeField);

            // 3️⃣ 是否保留餐桌选项
            JRadioButton keepTableYes = new JRadioButton("是，保留餐桌锁定");
            JRadioButton keepTableNo = new JRadioButton("否，释放餐桌");
            keepTableYes.setSelected(true);
            ButtonGroup keepGroup = new ButtonGroup();
            keepGroup.add(keepTableYes);
            keepGroup.add(keepTableNo);

            JPanel keepPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            keepPanel.add(new JLabel("是否保留该餐桌？"));
            keepPanel.add(keepTableYes);
            keepPanel.add(keepTableNo);

            // 组装完整对话框
            JPanel dialogPanel = new JPanel(new BorderLayout(10, 10));
            dialogPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
            dialogPanel.add(new JLabel("<html><b>🕐 延迟预约 #" + delayReservationId + "</b></html>"), BorderLayout.NORTH);
            dialogPanel.add(timePanel, BorderLayout.CENTER);
            dialogPanel.add(keepPanel, BorderLayout.SOUTH);

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    dialogPanel,
                    "延迟预约",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (confirm != JOptionPane.OK_OPTION) {
                return;  // 用户取消
            }

            // 4️⃣ 验证时间格式
            String timeStr = timeField.getText().trim();
            if (!timeStr.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                showError("⚠️ 时间格式错误！请输入 HH:mm（例：18:30）");
                return;
            }

            // 5️⃣ 解析并验证时间
            try {
                LocalDateTime newTime = LocalDateTime.of(today, java.time.LocalTime.parse(timeStr));

                if (newTime.isBefore(now)) {
                    showError("⚠ 延迟时间不能是过去的时间！\n当前时间: " +
                            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    return;
                }

                boolean keepTable = keepTableYes.isSelected();

                // 6️⃣ 🔧【核心】调用 Controller 执行延迟
                if (controller != null) {
                    Map<String, Object> delayResult = controller.delayReservation(
                            delayReservationId, newTime, keepTable);

                    // 7️⃣ 处理返回结果
                    if ((Boolean) delayResult.get("success")) {
                        String successMsg = " 预约延迟成功！\n新时间: " +
                                newTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                        if (!keepTable) {
                            successMsg += "\n\n 餐桌 #" + table.getDisplayId() + " 已释放为空闲";
                        }

                        showInfo(successMsg);

                        // 🔧 刷新界面（通过 Controller）
                        SwingUtilities.invokeLater(() -> {
                            if (controller != null) {
                                controller.refreshTablesDisplay();

                                // 🔧【核心修改】只有 releaseTables=true 时才刷新预约列表
                                Boolean releaseTables = (Boolean) delayResult.get("releaseTables");
                                if (Boolean.TRUE.equals(releaseTables)) {
                                    refreshQuantityReservationsLog();
                                    System.out.println("🔄 已刷新数量模式预约列表（餐桌已释放）");
                                }
                            }
                        });
                        // ═══════════════════════════════════════════════════════════
                        // 🔧【关键修复】设置返回值 + 关闭主对话框
                        // ═══════════════════════════════════════════════════════════
                        result[0] = "DELAY";  // ← 新增：标记用户选择了"延迟"
                        dialog.dispose();     // ← 新增：关闭主对话框（不是子对话框！）

                    } else {
                        showError(" 延迟失败: " + delayResult.get("message"));
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                showError("系统错误: " + ex.getMessage());
            }
        });

        buttonPanel.add(checkInBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(delayBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);

        return result[0];
    }


    /**
     * 🔧 弹出输入框获取实际入座人数（支持合并桌 + 聚餐桌）
     *
     * @param displayId 餐桌显示ID
     * @return 入座人数，用户取消则返回 -1
     */
    public int showGuestCheckInDialog(String displayId) {
        // 1. 获取餐桌对象（通过 Controller 查询）
        Tables table = controller.getTableById(displayId);
        if (table == null) {
            showError("餐桌不存在");
            return -1;
        }

        // 2. 🔧 计算有效容量（支持普通桌/合并桌/聚餐桌）
        int effectiveCapacity = table.getCapacity();
        String tableLabel = "餐桌 #" + displayId;

        // ── 情况 1: 合并桌（2 张桌）──
        if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
            Tables partner = controller.getTableById(table.getMergedWith());
            if (partner != null) {
                effectiveCapacity = table.getCapacity() + partner.getCapacity();
                tableLabel = "合并桌 #" + displayId + "+" + partner.getDisplayId();
            }
        }
        // ── 🔧 情况 2: 聚餐桌（3 张或以上）──
        else if (table.getTableType() == Tables.TableType.GROUPED && table.getGroupWith() != null) {
            // 解析 group_with 字段（格式："7,8,9"）
            String[] groupIds = table.getGroupWith().split(",");
            int totalCapacity = 0;
            StringBuilder labelBuilder = new StringBuilder("聚餐桌 #");

            for (int i = 0; i < groupIds.length; i++) {
                String id = groupIds[i].trim();
                Tables groupedTable = controller.getTableById(id);
                if (groupedTable != null) {
                    totalCapacity += groupedTable.getCapacity();
                    if (i > 0) labelBuilder.append("+");
                    labelBuilder.append(id);
                }
            }

            effectiveCapacity = totalCapacity;
            tableLabel = labelBuilder.toString();

            // 🔧【调试日志】确认聚餐桌容量计算
            System.out.println("🔧 聚餐桌容量计算: " + tableLabel + " = " + effectiveCapacity + "人");
        }

        // 3. 弹出输入框
        String input = JOptionPane.showInputDialog(
                this,
                "请输入实际入座人数：\n" +
                        "（" + tableLabel + "，总容量：" + effectiveCapacity + "人）",
                "客人入座",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return -1;  // 用户取消
        }

        try {
            int seats = Integer.parseInt(input.trim());
            if (seats <= 0 || seats > effectiveCapacity) {
                JOptionPane.showMessageDialog(this,
                        "入座人数必须在 1-" + effectiveCapacity + " 之间！",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return -1;
            }
            return seats;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "请输入有效的数字！",
                    "输入错误",
                    JOptionPane.ERROR_MESSAGE);
            return -1;
        }
    }


    /**
     * 🔧 刷新数量模式预约显示（美化版 - 按钮分离 + 优雅交互 + 延迟状态）
     * 显示规则：
     * - 🔴 红色：1.5小时内且未过期（紧急）
     * - ⚪ 灰色：超过1.5小时但未过期（普通）
     * - 🔵 蓝色：已过期（预约时间 < 当前时间）
     * - 🟡 橙色：已延迟预约（rescheduled_time 不为空）
     */

    /**
     * 🔧 刷新数量模式预约显示（美化版 - 按钮分离 + 优雅交互 + 延迟状态 + 自动启停定时器）
     * 显示规则：
     * - 🔴 红色：1.5 小时内且未过期（紧急）
     * - ⚪ 灰色：超过 1.5 小时但未过期（普通）
     * - 🔵 蓝色：已过期（预约时间 < 当前时间）
     * - 🟡 橙色：已延迟预约（rescheduled_time 不为空）
     */
    public void refreshQuantityReservationsLog() {
        if (controller == null) {
            System.out.println("⚠️ controller 未初始化，跳过预约列表刷新");
            return;
        }

        try {
            // 1. 调用 Controller 获取数据
            List<Map<String, Object>> reservations = controller.getQuantityModeReservationsForLog();
            System.out.println("🔍 [DEBUG] 获取到预约记录数：" + reservations.size());

            // 2. 🔧 清空面板（关键！避免重复添加）
            SwingUtilities.invokeLater(() -> {
                reservationsLogPanel.removeAll();

                // 空状态处理
                if (reservations.isEmpty()) {
                    JLabel emptyLabel = new JLabel("✨ 暫無數量模式預約", SwingConstants.CENTER);
                    emptyLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
                    emptyLabel.setForeground(new Color(150, 150, 150));
                    emptyLabel.setBorder(new EmptyBorder(30, 10, 30, 10));
                    reservationsLogPanel.add(emptyLabel);
                } else {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");

                    for (Map<String, Object> res : reservations) {
                        String resId = (String) res.get("reservation_id");
                        Object timeObj = res.get("reservation_time");
                        Object rescheduledObj = res.get("rescheduled_time");  // 🔧【新增】延迟时间字段
                        String statusStr = (String) res.get("status");        // 🔧【新增】状态字段
                        Boolean within15h = (Boolean) res.get("within_15h");

                        // 解析预约时间
                        LocalDateTime resTime = null;
                        if (timeObj instanceof java.sql.Timestamp) {
                            resTime = ((java.sql.Timestamp) timeObj).toLocalDateTime();
                        } else if (timeObj instanceof LocalDateTime) {
                            resTime = (LocalDateTime) timeObj;
                        }

                        // 🔧【核心】解析延迟时间（如果有）
                        LocalDateTime rescheduledTime = null;
                        if (rescheduledObj instanceof java.sql.Timestamp) {
                            rescheduledTime = ((java.sql.Timestamp) rescheduledObj).toLocalDateTime();
                        } else if (rescheduledObj instanceof LocalDateTime) {
                            rescheduledTime = (LocalDateTime) rescheduledObj;
                        }

                        // 🔧【四色状态判断逻辑】
                        enum ReservationStatus {EXPIRED, URGENT, DELAYED, NORMAL}
                        ReservationStatus status = ReservationStatus.NORMAL;

                        // 🔧【核心】根据数据库状态 + 延迟时间判断显示状态
                        if ("DELAYED".equals(statusStr) && rescheduledTime != null) {
                            status = ReservationStatus.DELAYED;  // 🟡 已延迟（优先级最高）
                        } else if (resTime != null) {
                            if (resTime.isBefore(now)) {
                                status = ReservationStatus.EXPIRED;  // 🔵 已过期
                            } else if (within15h != null && within15h) {
                                status = ReservationStatus.URGENT;   // 🔴 紧急
                            } else {
                                long minutes = java.time.Duration.between(now, resTime).toMinutes();
                                if (minutes >= 0 && minutes <= 90) {
                                    status = ReservationStatus.URGENT;  // 🔴 紧急
                                }
                            }
                        }

                        // 🔧 根据状态设置样式
                        Color bgColor, borderColor, circleColor, textColor;
                        String circleSymbol;

                        switch (status) {
                            case EXPIRED:  // 🔵 过期：蓝色系
                                bgColor = new Color(240, 248, 255);
                                borderColor = new Color(33, 150, 243);
                                circleColor = new Color(33, 150, 243);
                                textColor = new Color(21, 101, 192);
                                circleSymbol = "🔵";
                                break;
                            case URGENT:   // 🔴 紧急：红色系
                                bgColor = new Color(255, 245, 245);
                                borderColor = new Color(244, 67, 54);
                                circleColor = new Color(244, 67, 54);
                                textColor = new Color(211, 47, 47);
                                circleSymbol = "⭕";
                                break;
                            case DELAYED:  // 🟡 延迟：橙色系（新增）
                                bgColor = new Color(255, 250, 240);
                                borderColor = new Color(255, 152, 0);
                                circleColor = new Color(255, 152, 0);
                                textColor = new Color(245, 124, 0);
                                circleSymbol = "🟡";
                                break;
                            default:       // ⚪ 普通：灰色系
                                bgColor = new Color(250, 250, 250);
                                borderColor = new Color(221, 221, 221);
                                circleColor = new Color(204, 204, 204);
                                textColor = new Color(51, 51, 51);
                                circleSymbol = "⚪";
                                break;
                        }

                        // 🔧【关键】创建面板时传入延迟时间
                        JPanel reservationPanel = createReservationPanelSeparated(
                                resId, resTime, rescheduledTime, formatter, status,
                                bgColor, borderColor, circleColor, textColor, circleSymbol
                        );

                        // 添加到主面板
                        reservationsLogPanel.add(reservationPanel);
                        reservationsLogPanel.add(Box.createRigidArea(new Dimension(0, 6)));
                    }
                }

                // 刷新面板
                reservationsLogPanel.revalidate();
                reservationsLogPanel.repaint();

                // 滚动到顶部
                SwingUtilities.invokeLater(() ->
                        reservationsLogScroll.getVerticalScrollBar().setValue(0)
                );
            });

            System.out.println(" 预约列表刷新完成：" + reservations.size() + " 条记录");

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心新增】根据查询结果自动启停定时器
            // ═══════════════════════════════════════════════════════════
            boolean hasData = !reservations.isEmpty();
            if (hasData) {
                startRefreshTimer();
                System.out.println("检测到预约数据，已启动自动刷新定时器");
            } else {
                stopRefreshTimer();
                System.out.println(" 暂无预约数据，定时器已停止（避免无效刷新）");
            }

        } catch (Exception e) {
            System.err.println(" 刷新预约列表失败: " + e.getMessage());
            e.printStackTrace();

            SwingUtilities.invokeLater(() -> {
                reservationsLogPanel.removeAll();
                JLabel errorLabel = new JLabel(
                        "<html><div style='color:red; padding:20px; text-align:center;'>" +
                                "⚠️ 加载失败：" + e.getMessage() +
                                "<br><small style='color:#666'>请检查数据库连接</small></div></html>",
                        SwingConstants.CENTER
                );
                reservationsLogPanel.add(errorLabel);
                reservationsLogPanel.revalidate();
                reservationsLogPanel.repaint();
            });

            // 🔧 异常时也停止定时器，避免无效刷新
            stopRefreshTimer();
        }
    }

    /**
     * 🔧 启动自动刷新定时器
     */
    private void startRefreshTimer() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            return;  // 已在运行，无需重复启动
        }

        refreshTimer = new javax.swing.Timer(REFRESH_INTERVAL_MS, e -> {
            if (controller != null) {
                System.out.println(" 定时刷新数量模式预约列表...");
                refreshQuantityReservationsLog();  // 递归调用，下次会根据数据决定是否继续
            }
        });
        refreshTimer.setRepeats(true);
        refreshTimer.start();
        System.out.println(" 预约列表自动刷新定时器已启动（间隔：" + (REFRESH_INTERVAL_MS / 1000) + "秒）");
    }

    /**
     * 🔧 停止自动刷新定时器
     */
    private void stopRefreshTimer() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
            System.out.println(" 预约列表自动刷新定时器已停止");
        }
    }
    /**
     * 🔧 确保预约刷新定时器正在运行
     * 如果定时器未启动或已停止，则重新启动
     */
    public void ensureRefreshTimerRunning() {
        if (refreshTimer != null && !refreshTimer.isRunning()) {
            refreshTimer.start();
            System.out.println(" 预约刷新定时器已重新启动");
        }
    }


    /**
     * 🔧 创建预约记录面板（按钮分离版 - 只有右侧按钮可点击）
     * 布局：[圆圈] [预约号+时间] [→查看按钮]
     * 特点：整行悬停微变，但只有按钮响应点击
     */
    private JPanel createReservationPanelSeparated(
            String resId, LocalDateTime resTime, LocalDateTime rescheduledTime, DateTimeFormatter formatter,
            Object status, Color bgColor, Color borderColor,
            Color circleColor, Color textColor, String circleSymbol) {

        // ── 主面板：固定高度 + 左侧彩色边框 + 底部细线分隔 ──
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBackground(bgColor);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 1, 0, borderColor),  // 左侧彩色条 + 底部细线
                BorderFactory.createEmptyBorder(10, 12, 10, 12)            // 内边距
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));  // 🔧 限制最大高度
        panel.setPreferredSize(new Dimension(0, 55));                 // 🔧 设置首选高度
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));  // 默认光标（非手型）

        // ── 左侧：状态圆圈 + 文本信息 ──
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setBackground(bgColor);
        leftPanel.setOpaque(false);

        // 状态圆圈
        JLabel circleLabel = new JLabel(circleSymbol);
        circleLabel.setForeground(circleColor);
        circleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        leftPanel.add(circleLabel);

        // 预约号 + 时间
        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setBackground(bgColor);
        textPanel.setOpaque(false);

        // 第一行：预约号
        JLabel idLabel = new JLabel(resId != null ? resId : "未知");
        idLabel.setForeground(textColor);
        idLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        textPanel.add(idLabel);

        // 第二行：时间 + 状态标签
        if (resTime != null) {
            String timeStr;

            // 🔧 如果有延迟时间，显示延迟后的时间（DELAYED 状态）
            if (rescheduledTime != null) {
                timeStr = rescheduledTime.format(formatter);
            } else {
                timeStr = resTime.format(formatter);
            }

            // 状态标签：EXPIRED 显示"已過期"，DELAYED 显示"已延遲"
            String statusText = "";
            if (status.toString().equals("EXPIRED")) {
                statusText = " (已過期)";
            } else if (rescheduledTime != null) {
                statusText = " (已延遲)";  // 🔧 新增延迟标签
            }

            JLabel timeLabel = new JLabel(timeStr + statusText);

            // 🔧 颜色逻辑：EXPIRED=蓝色，DELAYED=橙色，其他=灰色
            if (status.toString().equals("EXPIRED")) {
                timeLabel.setForeground(new Color(33, 150, 243));  // 蓝色
            } else if (rescheduledTime != null) {
                timeLabel.setForeground(new Color(255, 152, 0));    // 🔧 橙色
            } else {
                timeLabel.setForeground(new Color(120, 120, 120));  // 灰色
            }

            timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            textPanel.add(timeLabel);
        }

        leftPanel.add(textPanel);
        panel.add(leftPanel, BorderLayout.CENTER);

        // ── 右侧：独立查看按钮（只有这个可点击）─
        JButton viewBtn = new JButton(">詳情");
        viewBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        viewBtn.setForeground(new Color(150, 150, 150));
        viewBtn.setBackground(bgColor);
        viewBtn.setFocusPainted(false);
        viewBtn.setBorderPainted(false);
        viewBtn.setContentAreaFilled(false);  // 🔧 关键：不填充背景
        viewBtn.setPreferredSize(new Dimension(95, 35));  // 🔧 限制按钮大小
        viewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));  // 手型光标
        viewBtn.setToolTipText("查看預約詳情");

        // 🔧 按钮悬停效果（仅按钮变色，不影响整行）
        final Color originalBtnFg = viewBtn.getForeground();
        viewBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                viewBtn.setForeground(new Color(33, 150, 243));  // 悬停变蓝色
                viewBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));  // 稍微放大
            }

            @Override
            public void mouseExited(MouseEvent e) {
                viewBtn.setForeground(originalBtnFg);
                viewBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            }
        });

        // 🔧 绑定点击事件（只有按钮点击才触发）
        viewBtn.addActionListener(e -> handleReservationClick(resId));

        panel.add(viewBtn, BorderLayout.EAST);

        // ── 整行悬停效果（背景微变，但不影响按钮）─
        final Color originalBg = bgColor;
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // 只有面板背景微变，按钮样式不变
                panel.setBackground(bgColor.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setBackground(originalBg);
            }
        });

        return panel;
    }


    /**
     * 🔧 处理预约记录点击事件
     *
     * @param reservationId 预约号
     */
    private void handleReservationClick(String reservationId) {

        // 可选：显示确认对话框
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "<html>是否查看/操作预约 <b>" + reservationId + "</b>？</html>",
                "预约操作",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION && controller != null) {
            // 调用 Controller 查询详情
            TableReservation detail = controller.getReservationDetail(reservationId);
            if (detail != null) {
                showReservationDetailDialog(detail);
            } else {
                showError("未找到预约记录: " + reservationId);
            }
        }
    }

    /**
     * 🔧 显示预约详情弹窗
     */
    private void showReservationDetailDialog(TableReservation reservation) {
        JDialog dialog = new JDialog(this, "📋 预约详情 - " + reservation.getReservationId(), true);
        dialog.setSize(420, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // 信息面板
        JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        infoPanel.setBackground(new Color(245, 248, 255));
        infoPanel.setBorder(new EmptyBorder(20, 25, 15, 25));

        infoPanel.add(new JLabel("预约号:"));
        infoPanel.add(new JLabel(reservation.getReservationId()));

        infoPanel.add(new JLabel("客人姓名:"));
        infoPanel.add(new JLabel(reservation.getCustomerName()));

        infoPanel.add(new JLabel("联系电话:"));
        infoPanel.add(new JLabel(reservation.getCustomerPhone()));

        infoPanel.add(new JLabel("预约时间:"));
        String originalTimeStr = reservation.getReservationTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        // 🔧 检查是否为延迟预约
        if ("DELAYED".equals(reservation.getStatus()) && reservation.getRescheduledTime() != null) {
            // 延迟预约：显示两行时间
            String rescheduledTimeStr = reservation.getRescheduledTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            JPanel timePanel = new JPanel(new GridLayout(2, 1, 0, 5));
            timePanel.setBackground(new Color(245, 248, 255));

            // 第一行：原预约时间（灰色）
            JLabel originalTimeLabel = new JLabel("原时间：" + originalTimeStr);
            originalTimeLabel.setForeground(new Color(120, 120, 120));
            originalTimeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

            // 第二行：延迟后的时间（橙色加粗）
            JLabel rescheduledTimeLabel = new JLabel("延迟后：" + rescheduledTimeStr);
            rescheduledTimeLabel.setForeground(new Color(255, 152, 0));
            rescheduledTimeLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));

            timePanel.add(originalTimeLabel);
            timePanel.add(rescheduledTimeLabel);
            infoPanel.add(timePanel);
        } else {
            // 普通预约：只显示原时间
            JLabel timeLabel = new JLabel(originalTimeStr);
            timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            infoPanel.add(timeLabel);
        }

        infoPanel.add(new JLabel("桌子配置:"));
        infoPanel.add(new JLabel(reservation.getTableConfigDesc()));

        infoPanel.add(new JLabel("状态:"));
        JLabel statusLabel = new JLabel(reservation.getStatus());

        if ("CONFIRMED".equals(reservation.getStatus())) {
            statusLabel.setForeground(new Color(0, 128, 0));
        } else if ("DELAYED".equals(reservation.getStatus())) {
            statusLabel.setForeground(new Color(255, 152, 0));  // 🔧 橙色
        } else if ("PRE_CONFIRMED".equals(reservation.getStatus())) {
            statusLabel.setForeground(new Color(30, 144, 255));  // 蓝色
        }
        infoPanel.add(statusLabel);

        dialog.add(infoPanel, BorderLayout.CENTER);

        // 按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton cancelBtn = new JButton("取消預約");
        JButton EditBtn = new JButton("修改預約");
        JButton AssignBtn = new JButton("分配餐桌");
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dialog.dispose());

        // ═══════════════════════════════════════════════════════════
        // 🔧【修改】分配餐桌按钮事件 - 打开分配餐桌对话框
        // ═══════════════════════════════════════════════════════════
        // ===== 在 RestaurantView.java 的 showReservationDetailDialog 方法内 =====
        AssignBtn.addActionListener(e -> {
            // 1. 关闭当前详情对话框
            dialog.dispose();

            // 2. 准备数据
            Map<String, Object> existingData = new HashMap<>();
            existingData.put("reservation_id", reservation.getReservationId());

            // 3. 打开分配对话框（这一步日志显示正常）
            Map<String, Object> result = showReservationDialog("ASSIGN", existingData);

            // 4. 处理返回结果
            if (result != null && "ASSIGN".equals(result.get("mode"))) {
                try {
                    String resId = (String) result.get("reservationId");
                    @SuppressWarnings("unchecked")
                    List<String> selectedTables = (List<String>) result.get("selectedTables");

                    if (controller != null && resId != null && selectedTables != null && !selectedTables.isEmpty()) {
                        // 【关键修复】调用新添加的 processTableAssignment 方法
                        controller.processTableAssignment(resId, selectedTables);

                        // 刷新预约列表
                        refreshQuantityReservationsLog();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "分配餐桌失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });


        EditBtn.addActionListener(e -> {
            // 1. 关闭当前详情对话框
            dialog.dispose();

            // 2. 准备数据传给编辑对话框
            Map<String, Object> existingData = new HashMap<>();
            existingData.put("reservation_id", reservation.getReservationId());
            existingData.put("customer_name", reservation.getCustomerName());
            existingData.put("customer_phone", reservation.getCustomerPhone());
            existingData.put("reservation_time", reservation.getReservationTime());
            existingData.put("table_config_desc", reservation.getTableConfigDesc());
            existingData.put("table_selection_mode", reservation.getTableSelectionMode());
            existingData.put("preOrder", reservation.getPreOrder());
            existingData.put("isPrepaid", reservation.getIsPrepaid());
            existingData.put("prepaidAmount", reservation.getPrepaidAmount());
            existingData.put("notes", reservation.getNotes());
            existingData.put("status", reservation.getStatus());

            // 3. 打开编辑对话框
            Map<String, Object> result = showReservationDialog("EDIT_TIME", existingData);

            // 4. 🔧【关键修复】处理返回结果并直接调用 Service 保存
            if (result != null && "EDIT_TIME".equals(result.get("mode"))) {
                try {
                    String resId = (String) result.get("reservationId");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> edits = (Map<String, Object>) result.get("edits");

                    if (edits != null && !edits.isEmpty()) {
                        // 🔧【核心】直接调用 Controller 的专用方法保存（不打开新对话框！）
                        controller.updateReservationDirectly(resId, edits);

                        //  修改成功：刷新界面
                        System.out.println(" 预约修改已保存到数据库: " + resId);
                        refreshQuantityReservationsLog();
                        JOptionPane.showMessageDialog(
                                null,  // 使用 null 因为 dialog 已 dispose
                                "预约修改成功！\n预约号：" + resId,
                                "成功",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        JOptionPane.showMessageDialog(
                                null,
                                "⚠️ 您没有勾选任何修改项！\n请至少勾选一项内容再进行修改。",
                                "提示",
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            null,
                            "修改失败：" + ex.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                    );
                    ex.printStackTrace();
                }
            }
        });

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】取消预约按钮事件处理
        // ═══════════════════════════════════════════════════════════
        cancelBtn.addActionListener(e -> {
            // 1 二次确认弹窗（防止误操作）
            int confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "<html><b style='color:#d32f2f;'>⚠️ 确认取消预约？</b><br><br>" +
                            "预约号：<b>" + reservation.getReservationId() + "</b><br>" +
                            "客人：" + reservation.getCustomerName() + "<br>" +
                            "预约时间：" + reservation.getReservationTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "<br><br>" +
                            "<font color='#d32f2f'>此操作不可恢复！</font></html>",
                    "取消预约确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;  // 用户取消，终止操作
            }

            // 2️ 弹窗获取取消原因
            String cancellationReason = showCancelReasonDialog(reservation.getReservationId());
            if (cancellationReason == null) {
                return;  // 用户点击取消输入框，终止操作
            }

            // 3️ 🔧 调用 Controller 执行取消逻辑
            try {
                Map<String, Object> cancelResult = controller.cancelReservation(
                        reservation.getReservationId(),
                        cancellationReason
                );

                // 4️ 🔧 根据返回结果处理提示（使用场景标志）
                if ((Boolean) cancelResult.get("success")) {
                    // 🔹 优先使用 Service 返回的用户友好消息
                    String message = (String) cancelResult.get("userMessage");

                    // 🔹 可选：根据场景标志自定义更详细提示
                    Boolean preOrderDeleted = (Boolean) cancelResult.get("preOrderDeleted");
                    Boolean depositForfeited = (Boolean) cancelResult.get("depositForfeited");
                    Double forfeitedAmount = (Double) cancelResult.get("forfeitedAmount");

                    // 🔹 显示成功提示
                    SwingUtilities.invokeLater(() -> {
                        showInfo(message);
                        appendToLog("已取消预约：" + reservation.getReservationId() +
                                " | 原因: " + cancellationReason);

                        // 🔹 刷新界面
                        Boolean needRefresh = (Boolean) cancelResult.get("needRefresh");
                        if (needRefresh == null || needRefresh) {
                            controller.refreshTablesDisplay();
                            refreshQuantityReservationsLog();
                        }

                        // 🔹 关闭当前详情对话框
                        dialog.dispose();
                    });

                } else {
                    // 🔹 失败提示
                    String errorMsg = (String) cancelResult.get("message");
                    SwingUtilities.invokeLater(() ->
                            showError("❌ 取消失败：" + errorMsg)
                    );
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> showError("系统错误: " + ex.getMessage())
                );
            }
        });
        btnPanel.add(cancelBtn);
        btnPanel.add(EditBtn);
        btnPanel.add(AssignBtn);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /**
     * 🔧 显示取消原因输入对话框
     *
     * @param reservationId 预约号（用于提示）
     * @return 用户输入的原因，点击取消则返回 null
     */
    public String showCancelReasonDialog(String reservationId) {
        // 创建多行文本输入框
        JTextArea reasonArea = new JTextArea(4, 30);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        // 🔧【新增】设置默认提示文本（灰色，输入后消失）
        reasonArea.setText("（可选）如留空则默认：顾客主动取消预约");
        reasonArea.setForeground(Color.GRAY);
        reasonArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                // 首次聚焦时清空提示文本
                if (reasonArea.getForeground() == Color.GRAY) {
                    reasonArea.setText("");
                    reasonArea.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                // 失去焦点且为空时恢复提示文本
                if (reasonArea.getText().trim().isEmpty()) {
                    reasonArea.setText("（可选）如留空则默认：顾客主动取消预约");
                    reasonArea.setForeground(Color.GRAY);
                }
            }
        });

        // 滚动面板
        JScrollPane scrollPane = new JScrollPane(reasonArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        // 提示信息
        JLabel hintLabel = new JLabel("<html><b>请输入取消预约 #" + reservationId + " 的原因：</b></html>");
        hintLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        hintLabel.setBorder(new EmptyBorder(0, 0, 5, 0));

        // 🔧【新增】默认值说明标签（灰色小字）
        JLabel defaultHintLabel = new JLabel(
                "<html><span style='color:#888;font-size:11px;'>💡 如不填写，系统将默认使用「顾客主动取消预约」</span></html>"
        );
        defaultHintLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

        // 组装面板
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(hintLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(defaultHintLabel, BorderLayout.SOUTH);  // 🔧 添加底部提示

        // 显示对话框
        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "📝 取消预约原因",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String reason = reasonArea.getText().trim();

            // 🔧【核心修复】判断是否为提示文本或空值
            if (reason.isEmpty() ||
                    reason.equals("（可选）如留空则默认：顾客主动取消预约")) {
                return "顾客主动取消预约";  // 返回默认值
            }
            return reason;
        } else {
            // 用户点击取消
            return null;
        }
    }

}
