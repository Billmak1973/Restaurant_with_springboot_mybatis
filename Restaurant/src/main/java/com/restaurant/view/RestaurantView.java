package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Order;
import com.restaurant.entity.TableReservation;
import com.restaurant.entity.Tables;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.text.AttributedString;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Collectors;
import javax.swing.JSpinner;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import com.toedter.calendar.JDateChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.*;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.Rotation;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;
import org.springframework.context.event.EventListener;
/**
 * 純GUI視圖層 - 不包含任何業務邏輯
 * 所有按鈕事件通過Controller轉發
 */
public class RestaurantView extends JFrame implements ReservationMatchCallback{

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

    /**
     * RestaurantView 构造函数 - 初始化餐厅管理系统主界面
     *
     * 主要功能：
     * 1. 窗口基础配置：设置标题、尺寸、关闭操作、背景色
     * 2. 左侧面板：餐桌可视化区域（GridLayout 3列布局，支持滚动）
     * 3. 右侧面板：系统信息面板，包含三层垂直分割：
     *    - 上层：当前队列状态显示（2/4/6人桌排队信息）
     *    - 中层：餐桌状态详情（容量/状态/顾客组/时间）
     *    - 下层：数量模式预约监控（带自动刷新功能）
     * 4. 底部面板：操作按钮区（添加顾客/拆分/合并/点餐/结账等12个功能按钮）
     * 5. 定时器初始化：每10分钟自动刷新预约列表，智能启停避免无效轮询
     *
     * 布局策略：BorderLayout + GridLayout + BoxLayout + JSplitPane 组合
     * 线程安全：SwingUtilities.invokeLater 确保UI操作在EDT执行
     *
     * @note 控制器通过 setController() 方法后续注入，非构造时注入
     */
    public RestaurantView() {
        tableComponentMap = new HashMap<>();// 餐桌显示ID → 对应JButton组件的映射缓存，用于快速定位和刷新特定餐桌的显示状态
        setTitle("餐厅管理系统");
        setSize(1500, 1000);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);//主程序窗口 → EXIT_ON_CLOSE（关掉就退出整个应用）
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

        // 先创建 innerSplitPane，再设置组件
        //7  Swing 複合佈局管理與響應式設計
        //7.1. Swing 複合佈局管理與響應式設計
        //技術說明：採用 BorderLayout + GridLayout + BoxLayout + JSplitPane 的組合策略，實現多區域協同與窗口自適應。
        //通過 JSplitPane 實現用戶可拖拽調整的區域比例，setResizeWeight(0.65) 確保內容區域優先擴展。結合 JScrollPane 的 VERTICAL_SCROLLBAR_AS_NEEDED 策略，即使預約記錄達數百條也能保持界面流暢，體現了「內容優先」的響應式設計思想。
        innerSplitPane = new JSplitPane(//无法解析符号 'innerSplitPane'
                JSplitPane.VERTICAL_SPLIT,
                tableStatusScrollPane,
                reservationsLogScroll  //  直接用新的 reservationsLogScroll
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
        // 初始化数量模式预约显示 + 定时器（每10分钟刷新）
        // ═══════════════════════════════════════════════════════════

        // 1. 初始加载（延迟执行，确保controller已设置）
        SwingUtilities.invokeLater(() -> {
            if (controller != null) {
                System.out.println(" 初始加载数量模式预约列表...");
                refreshQuantityReservationsLog();
            }
        });

        // 2. 设置定时器：每10分钟（600000毫秒）自动刷新
        refreshTimer = new javax.swing.Timer(600000, e -> {
            if (controller != null) {
                System.out.println(" 定时刷新数量模式预约列表...");
                refreshQuantityReservationsLog();
            }
        });
        refreshTimer.setRepeats(true);  // 确保重复执行
        refreshTimer.start();
        System.out.println(" 数量模式预约自动刷新已启动（每10分钟）");
    }

    /**
     * 创建样式化按钮（统一UI风格）
     *
     * @param text   按钮显示文本
     * @param bgColor 按钮背景颜色
     * @return 配置好的 JButton 对象
     *
     * @note 统一设置：
     *   • 前景色：白色文字
     *   • 字体：微软雅黑 粗体 12px
     *   • 边框：内边距 8/15/8/15
     *   • 交互：手型光标 + 禁用焦点绘制
     */
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

    /**
     * 設置 Controller 並初始化營業狀態顯示
     *
     * 執行流程：
     * 1. 注入 Controller 依賴，建立 View ↔ Controller 連接
     * 2. 檢查 service 是否就緒，避免空指針異常
     * 3. 獲取當前營業狀態 (isOpenForBusiness)
     * 4. 根據狀態動態設置「結束營業/開始營業」按鈕文本與顏色
     * 5. 更新底部狀態欄顯示 (🟢營業中 / 🔴已打烊)
     *
     * @note 此方法在 Spring 容器初始化完成後由 RestaurantApplication 調用
     *       確保 GUI 啟動時能正確反映系統初始狀態
     */
    public void setController(RestaurantController controller) {
        this.controller = controller;
        if (controller != null && controller.service != null) {
            boolean isOpen = controller.service.isOpenForBusiness();

            // 设置按钮文本
            setCloseDayButtonText(Boolean.parseBoolean(isOpen ? "结束营业" : "开始营业"));

            // 更新状态显示
            updateBusinessStatusDisplay(isOpen);
        }
    }

    // ===== 事件綁定方法（空實現，後續由Controller填充）=====
    //5.8 解決 Spring 容器管理的 Bean 如何被 Swing 事件調用的問題
    //技術說明：Spring 管理的 Bean 通常由框架調用，而 Swing 按鈕點擊是由 AWT 事件隊列觸發的。本項目通過 「事件註冊器模式」+「方法引用橋接」 完美解決了這一痛點。View 提供監聽器註冊方法，Controller 將自身業務方法作為回調傳入，AWT 事件觸發時直接調用 Controller 的方法。
    //這是本項目架構設計最精妙之處。它避開了「在 Swing 中手動獲取 Spring ApplicationContext」的笨重做法，而是利用 事件監聽器作為橋樑，將 AWT 的 ActionEvent 無縫轉發給 Spring 管理的 Controller 方法。這種設計既保持了 Swing 的事件驅動特性，又完全復用了 Spring 的依賴注入與聲明式事務，是桌面端與現代框架融合的優秀實踐。
    //// ① View 層暴露註冊方法
    //public void setAddGroupListener(ActionListener listener) {
    //    addGroupButton.addActionListener(listener);
    //}
    //
    //// ② Controller 層綁定自身方法（方法引用）
    //view.setAddGroupListener(this::handleAddGroup);
    //

    /// / ③ 用戶點擊按鈕 -> AWT 事件隊列 -> 觸發 Controller 的 handleAddGroup
    //private void handleAddGroup(ActionEvent e) {
    //    // 直接調用 Spring 管理的 Service
    //    CustomerGroup group = service.addCustomerGroup(Integer.parseInt(view.getGroupSizeInput()));
    //    // ...
    //}
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

    /**
     * 设置餐桌列表并刷新界面显示
     *
     * @param tables 餐桌数据列表，来自 Service 层的最新状态
     * @note 此方法由 Controller 调用，负责将业务数据传递至 View 层渲染
     *       内部委托给 updateTablesDisplay() 执行实际的 UI 更新操作
     */
    public void setTables(List<Tables> tables) {
        updateTablesDisplay(tables);
    }

    //7.3 3. Swing 線程安全與 EDT 規範實踐
    //技術說明：嚴格遵循 Swing 單線程規則，所有 UI 更新通過 SwingUtilities.invokeLater() 在事件調度線程 (EDT) 執行，避免多線程競爭導致的界面卡頓或崩潰
    //後端 Service 層通過 ApplicationEventPublisher 發佈 QueueChangedEvent，QueueChangeListener 捕獲後再透過 invokeLater 安全轉發至 UI。這種「後端業務線程 → 事件總線 → EDT 更新」的三級橋接，既保證了業務處理的並發性能，又確保了 UI 操作的線程安全。
    /**
     * 更新餐桌可视化显示面板(全部刷新）
     *
     * 功能说明：
     * - 在事件调度线程(EDT)中安全更新UI组件
     * - 清空现有餐桌组件并重置映射关系
     * - 根据餐桌容量初始化对应的颜色配置
     * - 遍历餐桌数据创建按钮组件并绑定事件
     * - 刷新面板布局以应用最新显示状态
     *
     * 注意：该方法为线程安全设计，必须在Swing EDT中执行
     */
    public void updateTablesDisplay(List<Tables> tables) {
        SwingUtilities.invokeLater(() -> {
            if (tablesPanel == null) return;

            tablesPanel.removeAll();// 移除所有舊餐桌按鈕
            tableComponentMap.clear(); // 清空 displayId → Component 映射緩存

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


    /**
     * 创建餐桌按钮组件
     *
     * <p>该方法负责根据餐桌实体对象生成对应的可视化按钮，用于在界面中展示餐桌状态并响应用户交互。</p>
     *
     * <p>主要功能包括：</p>
     * <ul>
     *   <li>初始化按钮的基本外观属性（布局、边框、尺寸、光标等）</li>
     *   <li>根据餐桌当前状态更新按钮背景颜色</li>
     *   <li>根据餐桌容量动态选择对应的主题颜色</li>
     *   <li>生成并设置餐桌图标，直观展示座位占用情况</li>
     *   <li>创建并添加餐桌信息标签，显示编号、容量、状态等关键信息</li>
     *   <li>绑定点击事件，将用户操作转发至控制器层处理</li>
     *   <li>将按钮组件注册到映射表，便于后续按需刷新</li>
     * </ul>
     *
     * @param table 餐桌实体对象，包含餐桌的各项属性信息
     * @param tableColors 餐桌颜色数组，用于兼容旧版颜色映射逻辑
     * @return 配置完成的餐桌按钮组件，可直接添加至界面容器
     *
     * @note 颜色选择优先依据餐桌容量而非基础编号，支持任意餐桌编号扩展
     * @note 事件处理采用转发模式，视图层仅负责收集用户输入，业务逻辑由控制器层执行
     * @note 按钮组件会缓存至 tableComponentMap，支持通过 displayId 快速定位并刷新
     */
    private JButton createTableButton(Tables table, Color[] tableColors) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        button.setPreferredSize(new Dimension(180, 180));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        updateButtonBackground(button, table);

        // 【核心修复】根据餐桌容量决定颜色，支持任意 baseId 扩展
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

    /**
     * 根据餐桌状态和类型更新按钮背景颜色
     *
     * 功能说明：
     * - 优先判断合并桌状态：当餐桌类型为合并桌且处于占用状态时，应用专用合并颜色
     * - 其次判断聚餐桌状态：当餐桌类型为聚餐桌且处于占用状态时，应用专用聚餐颜色
     * - 处理常规状态：对于其他类型的餐桌，根据当前状态（空闲/占用/准备中/拆分中/已预定）应用对应的状态颜色
     * - 确保按钮背景色正确渲染：通过设置不透明属性使背景颜色生效
     *
     * 参数说明：
     * @param button 需要更新背景色的餐桌按钮组件
     * @param table  餐桌实体对象，包含类型和状态信息
     */
    private void updateButtonBackground(JButton button, Tables table) {
        Color bgColor;

        // 合并桌优先判断：必须是 MERGED 类型 + OCCUPIED 状态
        if (table.getTableType() == Tables.TableType.MERGED &&
                table.getStatus() == Tables.TableStatus.OCCUPIED) {
            bgColor = colorMerged;  //  使用紫色
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
                case RESERVED -> bgColor = new Color(230, 220, 245); // 淡薰衣草色 - 预定状态
                default -> bgColor = colorVacant;
            }
        }

        button.setBackground(bgColor);
        button.setOpaque(true);  // 确保背景色生效
    }

    /**
     * 创建餐桌信息标签组件
     *
     * 功能说明：
     * - 接收餐桌实体对象，提取并格式化显示所需的核心信息
     * - 生成餐桌编号的富文本标识，支持子桌后缀的差异化样式渲染
     * - 动态组装顾客组信息，区分已加载完成与数据加载中的两种展示状态
     * - 整合餐桌容量、当前状态及顾客组信息，构建完整的HTML格式标签内容
     * - 创建居中对齐的JLabel组件，应用统一的字体样式配置
     *
     * @param table 餐桌实体对象，包含显示所需的各项属性
     * @return 配置完成的JLabel组件，用于界面中餐桌信息的可视化展示
     */
    private JLabel createTableInfoLabel(Tables table) {
        String displayId = table.getDisplayId();
        String statusText = getStatusText(table);

        // 子桌特殊標識
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

        //  在餐桌編號後添加子桌標識
        String html = "<html><center>" +
                "<b>餐桌 #" + displayId + "</b>" + suffixLabel + "<br>" +  // ← 這裡添加
                "容量: " + table.getCapacity() + "人 &bull; " + statusText + groupInfo +
                "</center></html>";

        JLabel label = new JLabel(html, SwingConstants.CENTER);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 13));  // 用戶偏好普通字體
        return label;
    }

    /**
     * 获取餐桌状态的显示文本（支持合并桌/聚餐桌特殊标识）
     *
     * <p>该方法根据餐桌对象的状态和类型，返回用于界面显示的中文状态文本。
     * 优先使用枚举预定义的中文名称，再根据业务场景追加特殊标识。</p>
     *
     * <p><b>状态处理逻辑：</b></p>
     * <ul>
     *   <li><b>普通餐桌</b>：直接返回枚举的 getDisplayName()，如"空闲"、"占用中"、"准备中"</li>
     *   <li><b>合并餐桌</b>：当类型为 MERGED 且状态为 OCCUPIED 时，追加" (合并中)"标识</li>
     *   <li><b>聚餐桌</b>：当类型为 GROUPED 且状态为 OCCUPIED 时，追加"(聚餐中)"标识</li>
     * </ul>
     */
    private String getStatusText(Tables table) {
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
     * 更新排队队列显示区域的内容
     *
     * <p>该方法负责将三种容量的等待队列（2人桌/4人桌/6人桌）格式化后显示在界面上。
     * 每条队列记录包含：排队号码、顾客组人数、当前排队位置。</p>
     *
     * @param q2 2人桌等待队列，可能为 null 或空队列
     * @param q4 4人桌等待队列，可能为 null 或空队列
     * @param q6 6人桌等待队列，可能为 null 或空队列
     *
     * @implNote
     * <ul>
     *   <li>方法内部对每个队列参数都进行了 {@code null} 检查，避免空指针异常</li>
     *   <li>队列为空或 null 时显示"• 无等待顾客"提示</li>
     *   <li>使用 {@code position} 变量独立计数，确保显示位置与队列实际顺序一致</li>
     *   <li>最终通过 {@code queueDisplay.setText()} 一次性更新 UI，减少重绘次数</li>
     * </ul>
     *
     * @see CustomerGroup#getCallNumber() 获取排队号码
     * @see CustomerGroup#getGroupSize() 获取顾客组人数
     */
    public void updateQueueDisplay(Queue<CustomerGroup> q2, Queue<CustomerGroup> q4, Queue<CustomerGroup> q6) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前队列\n");

        // 2人桌队列（添加 null 检查）
        sb.append("2人桌队列:\n");
        if (q2 == null || q2.isEmpty()) {  // ← 添加 q2 == null 检查
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q2) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getGroupSize(), position++));
            }
        }
        sb.append("\n");

        // 4人桌队列（添加 null 检查）
        sb.append("4人桌队列:\n");
        if (q4 == null || q4.isEmpty()) {  // ← 添加 q4 == null 检查
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q4) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getGroupSize(), position++));
            }
        }
        sb.append("\n");

        // 6人桌队列（添加 null 检查）
        sb.append("6人桌队列:\n");
        if (q6 == null || q6.isEmpty()) {  // ← 添加 q6 == null 检查
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
     * 更新餐桌状态详情显示区域
     *
     * <p>该方法负责将餐桌列表数据格式化为可读文本，并更新到右侧状态显示面板。
     * 支持以下餐桌类型的状态展示：</p>
     *
     * <ul>
     *   <li><b>普通餐桌</b>：显示基础信息 + 订单状态 + 顾客组信息</li>
     *   <li><b>合并餐桌</b>：自动查询主桌（编号较小者）的订单状态，显示伙伴桌关联</li>
     *   <li><b>聚餐桌</b>：自动查询主桌（最小编号）的订单状态，关联桌号超过3个时用省略号显示</li>
     * </ul>
     *
     * <p><b>显示内容结构：</b></p>
     * <pre>
     *  餐桌 #7 [合并桌: +8] | 容量：2人 | 状态：占用中订单情况：已下单(未完成) | 顾客组: #5 (3人) |  2026-03-22 18:30:00 → 进行中
     * ────────────────────────────────────────
     * </pre>
     *
     * @param tables 餐桌对象列表，来自 Service 层查询结果
     * @note
     * <ul>
     *   <li>线程安全：所有 UI 更新通过 {@code SwingUtilities.invokeLater()} 在 EDT 执行</li>
     *   <li>空值防护：参数为 null 或空列表时显示友好提示</li>
     *   <li>性能优化：占用中餐桌才查询订单状态，避免冗余数据库调用</li>
     *   <li>主桌规则：合并桌/聚餐桌统一使用编号最小的桌号作为订单查询主键</li>
     * </ul>
     * @see Tables.TableStatus 餐桌状态枚举
     * @see Tables.OrderStatus 订单状态枚举
     * @see RestaurantController#getOrderStatusDisplay(String) 订单状态查询代理方法
     */
    public void updateTableStatusDisplay(List<Tables> tables) {
        StringBuilder sb = new StringBuilder();

        if (tables == null || tables.isEmpty()) {
            tableStatusDisplay.setText("✨ 暂无餐桌信息");
            return;
        }

        for (Tables table : tables) {
            // 1 订单状态（仅占用中餐桌显示）
            String orderStatusText = "";
            if (controller != null && table.getStatus() == Tables.TableStatus.OCCUPIED) {
                // 合并桌查询主桌的订单状态
                String queryTableId = table.getDisplayId();

                if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
                    // 合并桌：使用编号较小的桌号作为主桌
                    String partnerId = table.getMergedWith();
                    int currentNum = Integer.parseInt(table.getDisplayId().replaceAll("[^0-9]", ""));
                    int partnerNum = Integer.parseInt(partnerId.replaceAll("[^0-9]", ""));

                    // 编号较小的为主桌
                    queryTableId = (currentNum <= partnerNum) ? table.getDisplayId() : partnerId;

                    System.out.println(" 合并桌订单查询：餐桌#" + table.getDisplayId() +
                            " → 查询主桌#" + queryTableId);
                }

                // 聚餐桌：使用主桌（最小编号）查询订单状态
                else if (table.getTableType() == Tables.TableType.GROUPED && table.getGroupWith() != null) {
                    // 解析 group_with 字段（格式："13,14,15"）
                    String[] groupIds = table.getGroupWith().split(",");
                    if (groupIds.length > 0) {
                        // 找到编号最小的桌号作为主桌
                        queryTableId = groupIds[0].trim();
                        int minNum = Integer.parseInt(queryTableId.replaceAll("[^0-9]", ""));

                        for (String id : groupIds) {
                            int num = Integer.parseInt(id.trim().replaceAll("[^0-9]", ""));
                            if (num < minNum) {
                                minNum = num;
                                queryTableId = id.trim();
                            }
                        }

                        System.out.println(" 聚餐桌订单查询：餐桌#" + table.getDisplayId() +
                                " → 查询主桌#" + queryTableId + " (关联桌: " + table.getGroupWith() + ")");
                    }
                }

                orderStatusText = controller.getOrderStatusDisplay(queryTableId);
            }

            // 2 顾客组信息（保持不变）
            String customerGroupInfo = "";
            if (table.getStatus() == Tables.TableStatus.OCCUPIED && table.getCurrentGroup() != null) {
                customerGroupInfo = String.format(" | 顾客组: #%d (%d人)",
                        table.getCurrentGroup().getCallNumber(),
                        table.getCurrentGroup().getGroupSize());
            }

            // 3 合并餐桌特殊标识（保持不变）
            String mergedLabel = "";
            if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
                mergedLabel = String.format(" [合并桌: +%s]", table.getMergedWith());
            }

            // 4 聚餐桌特殊标识（修改：超过3个时用省略号）
            String groupedLabel = "";
            if (table.getTableType() == Tables.TableType.GROUPED && table.getGroupWith() != null) {
                String[] groupIds = table.getGroupWith().split(",");
                String displayGroupWith;

                if (groupIds.length > 3) {
                    //  超过3个：显示"首,次,...,尾"格式（如：13,14,...,17）
                    displayGroupWith = groupIds[0].trim() + "," +
                            groupIds[1].trim() + ",...," +
                            groupIds[groupIds.length - 1].trim();
                } else {
                    //  3个或以下：全部显示
                    displayGroupWith = table.getGroupWith();
                }
                groupedLabel = String.format(" [聚餐桌: %s]", displayGroupWith);
            }

            // 5️ 构建基础信息行（保持不变）
            sb.append(String.format(
                    " 餐桌 #%s%s%s | 容量：%d人 | 状态：%s%s%s",
                    table.getDisplayId(),
                    mergedLabel,
                    groupedLabel,
                    table.getCapacity(),
                    table.getStatus().getDisplayName(),
                    orderStatusText,
                    customerGroupInfo
            ));

            // 6️ 时间信息（仅占用中）（保持不变）
            if (table.getStatus() == Tables.TableStatus.OCCUPIED && table.getStartTime() != null) {
                String endTime = table.getEndTime() != null ?
                        table.getFormattedEndTime() : "进行中";
                sb.append(String.format(" |  %s → %s",
                        table.getFormattedStartTime(), endTime));
            }

            sb.append("\n").append("─".repeat(40)).append("\n\n");
        }

        // 7️ 更新 UI（EDT 安全）（保持不变）
        SwingUtilities.invokeLater(() -> {
            tableStatusDisplay.setText(sb.toString());
            if (tableStatusDisplay.getDocument().getLength() > 0) {
                tableStatusDisplay.setCaretPosition(0);  // 滚动到顶部
            }
        });
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

    /**
     * 获取顾客组人数输入框的内容
     * @return 输入的人数（已去除首尾空格），若输入框为空则返回空字符串
     */
    public String getGroupSizeInput() {
        if (groupSizeInput == null) {
            return "";
        }
        return groupSizeInput.getText().trim();
    }

    /**清空输入框 */
    public void clearGroupSizeInput() {
        if (groupSizeInput != null) {
            groupSizeInput.setText("");
        }
    }

    /**
     * 刷新單個餐桌按鈕的顯示
     *
     * 功能：當餐桌狀態變更時，局部更新對應按鈕的樣式與信息，
     *       避免重繪整個餐桌面板，提升界面響應性能。
     *
     * 執行流程：
     * 1. 從 tableComponentMap 獲取目標按鈕組件
     * 2. 根據餐桌狀態更新背景色（占用/空閒/準備中等）
     * 3. 根據餐桌容量選擇對應顏色，重新生成椅子狀態圖標
     * 4. 更新按鈕底部文字信息（餐桌號/容量/顧客組/時間等）
     * 5. 調用 revalidate/repaint 觸發界面重繪
     *
     * 注意：
     * - 必須在 EDT 線程執行（使用 SwingUtilities.invokeLater）
     * - 顏色邏輯與 createTableButton 保持一致，確保視覺統一
     * - 僅更新指定按鈕，不影響其他餐桌顯示
     *
     * @param displayId     餐桌顯示編號（如 "7" 或 "7a"）
     * @param updatedTable  更新後的餐桌對象（含最新狀態）
     */
    public void refreshTableButton(String displayId, Tables updatedTable) {
        SwingUtilities.invokeLater(() -> {
            // 从组件映射表中获取指定餐桌的按钮组件(只获取目标餐桌对应的按钮，不影响其他餐桌组件)
            Component comp = tableComponentMap.get(displayId);
            if (comp instanceof JButton button) {
                // 1. 更新背景色（根據狀態）(只更新这个 button，其他按钮完全不动)
                updateButtonBackground(button, updatedTable);

                // 2. 顏色邏輯改為與 createTableButton 一致（根據容量而非 baseId）
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


    /**
     * 显示结账订单类型选择对话框
     * @return
     *   - "DINE_IN:餐桌号" → 堂食订单成功
     *   - null            → 用户选择"外卖订单"（由 Controller 处理外卖列表）
     *   - "" (空字符串)    → 用户取消/关闭对话框
     */
    public String showCheckoutDialog() {
        String[] options = {"堂食订单", "外卖订单"};
        int typeChoice = JOptionPane.showOptionDialog(
                this, "请选择订单类型：", "结账",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
        );//options[0]：表示 默认选中的按钮（高亮/回车默认触发） 用户直接按 Enter 键时，等价于点击这个选项

        //  调试输出（可选）
        //System.out.println("typeChoice = " + typeChoice);

        // 点击 X 或取消 → 返回空字符串 "" 表示"真正取消"
        if (typeChoice < 0) {
            return "";  // ← 关键修改：用 "" 表示取消
        }

        //  选择"外卖订单" → 仍然返回 null（保持 Controller 兼容）
        if (typeChoice == 1) {
            return null;  //
        }

        //  堂食订单输入时点击 X → 也返回 ""
        String input = JOptionPane.showInputDialog(
                this, "请输入餐桌号：\n（例如：7 或 7a）",
                "堂食订单结账", JOptionPane.QUESTION_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return "";  // 用 "" 表示取消
        }

        // 堂食订单成功
        return "DINE_IN:" + input.trim();
    }

    /**
     * 渲染订单详情到 HTML 格式并更新 UI 组件
     *
     * 主要功能：
     * 1. 错误处理：如果 details 包含 error 字段，显示红色错误信息
     * 2. 数据解析：兼容 LocalDateTime/Timestamp 时间类型，安全转换金额类型
     * 3. HTML 表格生成：构建菜品列表表格，包含编号/名称/数量/上菜状态/单价/小计
     * 4. 状态标识：根据 servedQuantity 与 quantity 对比，用颜色标识上菜进度
     *    - 绿色：全部已上桌
     *    - 橙色：部分上桌
     *    - 红色：未上桌
     * 5. 汇总信息：显示菜品总数、总份数、订单总金额
     * 6. 状态标签更新：根据上菜进度更新订单状态提示文本和颜色
     *
     * @param details     订单详情数据 Map（含 orderTime/totalAmount/items 等字段）
     * @param orderDisplay JEditorPane 组件，用于显示 HTML 格式的订单详情
     * @param statusLabel  JLabel 组件，用于显示订单状态文本
     * @param totalLabel   JLabel 组件，用于显示订单总金额
     *
     * @note 1. 所有金额计算保留 2 位小数，使用 String.format("%.2f", value)
     *       2. 表格使用内联 CSS 样式，确保 JEditorPane 正确渲染
     *       3. 异常捕获后会在 UI 显示红色错误信息，避免界面卡死
     *       4. allServed 标志用于判断是否所有菜品都已上桌，驱动状态标签更新
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
            // 兼容 LocalDateTime 和 Timestamp 两种时间类型
            Object orderTimeObj = details.get("orderTime");
            java.time.LocalDateTime orderTime = null;

            if (orderTimeObj instanceof java.time.LocalDateTime) {
                orderTime = (java.time.LocalDateTime) orderTimeObj;
            } else if (orderTimeObj instanceof java.sql.Timestamp) {
                orderTime = ((java.sql.Timestamp) orderTimeObj).toLocalDateTime();
            }

            // 金额类型只处理 double（简化版）
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

            //  使用 LocalDateTime 格式化时间
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

                    String statusColor = "green";// HTML/CSS 颜色名称
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

            //  设置总金额（之前因为异常没执行到这里）
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
     * 显示结账界面（根据订单类型分流）
     *
     * @param orderType  订单类型："DINE_IN"（堂食）或 "TAKEOUT"（外卖）
     * @param identifier 餐桌号（仅堂食用）或订单号（仅外卖用）
     *
     * @note 堂食订单：调用简化版结账界面，直接输入餐桌号
     *       外卖订单：显示订单列表对话框，用户选择后结账
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
     *  堂食订单结账界面（严格堂食 + 定金抵扣 + 营收记录修正）
     *
     * 【功能模块说明】
     *
     * ① 对话框初始化
     *    - 创建模态对话框，设置标题/尺寸/位置/关闭行为
     *    - 使用 BorderLayout 组织主面板布局
     *
     * ② 订单信息显示区域
     *    - JEditorPane + JScrollPane：以 HTML 表格形式渲染订单明细
     *    - orderStatusLabel：显示订单当前状态（加载中/部分上桌/已完成）
     *    - totalLabel：显示菜品总额（红色醒目）
     *
     * ③ 定金与应付金额显示
     *    - depositInfoLabel：显示已付定金金额（蓝色提示）
     *    - payableLabel：计算并显示应付金额 = 菜品总额 - 定金（绿色）
     *    - 若定金≥菜品总额，应付金额=0，支付框禁用
     *
     * ④ 支付输入区域
     *    - paymentField：用户输入实际支付金额
     *    - checkoutButton：触发结账确认流程
     *
     * ⑤ 异步加载订单数据（后台线程）
     *    - 调用 Controller 获取订单详情（菜品列表/金额/状态）
     *    - 查询餐桌关联的预约记录，获取预付定金信息
     *    - 计算应付金额并更新 UI（SwingUtilities.invokeLater 确保线程安全）
     *
     * ⑥ 结账确认流程（按钮事件）
     *    ├─ 输入验证：支付金额不能为空、必须≥应付金额
     *    ├─ 金额计算：
     *    │   • itemsTotal = 从 totalLabel 解析的菜品总额
     *    │   • payableAmount = itemsTotal - 定金（最小为 0）
     *    │   • revenueAmount = Math.max(itemsTotal, 定金)  // 营收记录用
     *    ├─ 二次确认弹窗：展示菜品总额/定金/本次支付/找零/营收记录
     *    └─ 提交结账：调用 Controller.handleCheckoutSubmitWithRevenue()
     *        • 参数：订单类型、餐桌号、用户支付金额、营收记录金额
     *        • 结账成功后关闭对话框
     *
     * 【关键业务规则】
     * • 定金抵扣：应付金额 = max(0, 菜品总额 - 定金)
     * • 营收记录：记录 max(菜品总额, 定金)，确保餐厅收入不亏损
     * • 定金超额：不退多余部分，营收按定金记录
     * • 线程安全：所有 UI 更新通过 SwingUtilities.invokeLater 执行
     */
    private void showSimpleDineInCheckout(String tableNumber) {
        JFrame dialog = new JFrame("结账 - 餐桌 " + tableNumber);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);//子窗口/对话框 → DISPOSE_ON_CLOSE（关掉就行，别退出程序）

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

        // 定金信息标签
        JLabel depositInfoLabel = new JLabel("");
        depositInfoLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        depositInfoLabel.setForeground(new Color(0, 100, 200));

        // 总金额标签（菜品总额）
        JLabel totalLabel = new JLabel("菜品总额: 0.00 元");
        totalLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        totalLabel.setForeground(Color.RED);

        // 应付金额标签（考虑定金抵扣）
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

        // 底部信息面板（定金+菜品总额+应付）
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        infoPanel.add(depositInfoLabel);
        infoPanel.add(totalLabel);
        infoPanel.add(payableLabel);
        orderPanel.add(infoPanel, BorderLayout.SOUTH);

        // 组装主面板
        mainPanel.add(orderPanel, BorderLayout.CENTER);
        mainPanel.add(paymentPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel, BorderLayout.CENTER);

        // 通过 Controller 异步加载订单数据 + 定金信息
        final double[] prepaidAmountRef = {0.0};      // 预付定金
        final boolean[] hasPrepaidRef = {false};       // 是否有预付
        final double[] itemsTotalRef = {0.0};          // 菜品总额

        new Thread(() -> {
            try {
                // 1. 加载订单详情（堂食）
                Map<String, Object> orderDetails = controller.getOrderDetails("DINE_IN", tableNumber);

                // 2.  查询餐桌的 current_reservation_id 和预付信息
                Tables table = controller.getTableById(tableNumber);
//                System.out.println(" [DEBUG] 餐桌信息:");
//                System.out.println("   tableNumber: " + tableNumber);
//                System.out.println("   table: " + (table != null ? "存在" : "null"));
//                if (table != null) {
//                    System.out.println("   currentReservationId: " + table.getCurrentReservationId());
//                }
                if (table != null && table.getCurrentReservationId() != null) {
                    String reservationId = table.getCurrentReservationId();
                    //System.out.println("   reservationId: " + reservationId);

                    // 通过 reservation_id 查询订单的预付信息
                    Map<String, Object> prepaidInfo = controller.getPrepaidInfoByReservationId(reservationId);

                    System.out.println("   prepaidInfo: " + (prepaidInfo != null ? "存在" : "null"));

                    if (prepaidInfo != null) {
                        Boolean isPrepaid = (Boolean) prepaidInfo.get("is_prepaid");
                        Double prepaidAmount = (Double) prepaidInfo.get("prepaid_amount");

                        if (isPrepaid != null && isPrepaid && prepaidAmount != null && prepaidAmount > 0) {
                            hasPrepaidRef[0] = true;
                            prepaidAmountRef[0] = prepaidAmount;
                         //   System.out.println("    检测到预付定金: " + prepaidAmount);
                        } else {
                           // System.out.println("    没有有效的预付定金");
                        }
                    }
                } else {
                  //  System.out.println("    餐桌没有关联预约记录");
                }

                // 3. 提取菜品总额
                Object itemsTotalObj = orderDetails.get("itemsTotal");
                if (itemsTotalObj instanceof Number) {
                    itemsTotalRef[0] = ((Number) itemsTotalObj).doubleValue();
                }

                // [0] 是因为用单元素数组当"可变容器"：Lambda 要求外部变量必须 final，但数组引用 final 时元素仍可修改，[0] 就是取数组里那个唯一元素
                //hasPrepaidRef[0] 是"可变容器"，值可能变
                //finalHasPrepaid 是"不可变快照"，保证传给 invokeLater 时值不会中途被改
                final boolean finalHasPrepaid = hasPrepaidRef[0];// 捕获后台线程计算出的"是否有预付"快照值，供 EDT 线程安全使用
                final double finalPrepaidAmount = prepaidAmountRef[0];
                final double finalItemsTotal = itemsTotalRef[0];

                SwingUtilities.invokeLater(() -> {
                    // 渲染订单详情
                    renderOrderDetails(orderDetails, orderDisplay, orderStatusLabel, totalLabel);

                    //  显示定金信息
                    if (finalHasPrepaid && finalPrepaidAmount > 0) {
                        depositInfoLabel.setText(" 已付定金: " + String.format("%.2f", finalPrepaidAmount) + " 元");
                        depositInfoLabel.setVisible(true);

                        // 【核心】计算应付金额 = 菜品总额 - 定金（最小为0）
                        double payableAmount = finalItemsTotal - finalPrepaidAmount;
                        if (payableAmount < 0) {
                            payableAmount = 0;  // 定金超过菜品总额，不退款
                        }

                        payableLabel.setText("应付金额: " + String.format("%.2f", payableAmount) + " 元");
                        payableLabel.setVisible(true);

                        //  自动填充支付框（应付金额）
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
                // 从标签提取菜品总额（兼容多种标签格式）
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

                // 计算应付金额（考虑定金）
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

                // 计算营收记录金额 = Math.max(菜品总额, 定金)
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
                        // 传递营收金额给后端（不是实付金额！）
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

            //  更新总金额（供支付使用）
            Object totalObj = details.get("totalAmount");
            String totalAmountStr = "0.00";
            if (totalObj instanceof Number) {
                totalAmountStr = String.format("%.2f", ((Number) totalObj).doubleValue());
            }

            //  渲染详情时指定 text/html
            JEditorPane tempDisplay = new JEditorPane("text/html", "");
            JLabel tempStatus = new JLabel();
            JLabel tempTotal = new JLabel();
            renderOrderDetails(details, tempDisplay, tempStatus, tempTotal);

            // 在对话框中查找并更新右侧面板
            String htmlContent = tempDisplay.getText();
            updateRightPanel(dialog, htmlContent, totalAmountStr);

            // 更新支付面板的金额显示（含配送费）
            updatePaymentPanelInDialog(dialog, details);

        } catch (Exception e) {
            e.printStackTrace();
            updateRightPanel(dialog,
                    "<html><body style='font-family:Microsoft YaHei;color:red;padding:20px;'>" +
                            "加载失败：" + e.getMessage() + "</body></html>", "0.00");
        }
    }


    /**
     * 在对话框中查找并更新支付面板金额
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
     * 递归查找并更新支付面板中的金额标签
     *
     * 功能说明：
     * 1. 遍历面板及其子面板中的所有组件
     * 2. 识别包含"菜品"/"配送费"/"总金额"关键词的 JLabel
     * 3. 从 orderDetails 中提取对应数值并格式化更新显示文本
     * 4. 根据配送方式（配送/自取）动态控制配送费标签的可见性
     *
     * @param panel        待遍历的父面板（支持嵌套容器）
     * @param orderDetails 订单详情数据（含 itemsTotal/deliveryFee/deliveryMethod）
     */
    private void updatePaymentLabelsInPanel(JPanel panel, Map<String, Object> orderDetails) {
        Component[] components = panel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String labelText = label.getText();

                //  更新菜品金额标签
                if (labelText != null && labelText.contains("菜品：")) {
                    double itemsTotal = 0.0;
                    Object itemsTotalObj = orderDetails.get("itemsTotal");
                    if (itemsTotalObj instanceof Number) {
                        itemsTotal = ((Number) itemsTotalObj).doubleValue();
                    }
                    label.setText(String.format("菜品：%.2f 元", itemsTotal));
                }
                //  更新配送费标签
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
                //  更新总金额标签
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
     * 在对话框中查找并更新右侧面板
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
     * 递归查找并更新面板中的组件内容
     *
     * 功能说明：
     * 1. 遍历面板中的所有组件，支持嵌套容器递归查找
     * 2. 更新 JEditorPane：设置订单详情 HTML 内容，并滚动到顶部
     *    （仅更新不可编辑的详情面板，避免误改输入框）
     * 3. 处理 JScrollPane：解包获取内部的 JEditorPane 或 JPanel 继续递归
     * 4. 更新 JLabel：查找包含"总金额"的标签，刷新金额显示
     * 5. 递归处理嵌套的 JPanel，确保深层组件也能被正确更新
     *
     * @param panel      当前要遍历的面板容器
     * @param htmlContent 订单详情的 HTML 内容
     * @param totalAmount 总金额字符串（格式："0.00"）
     */
    private void updateComponentInPanel(JPanel panel, String htmlContent, String totalAmount) {
        Component[] components = panel.getComponents();
        for (Component comp : components) {
            // 直接处理 JEditorPane
            if (comp instanceof JEditorPane) {
                JEditorPane editorPane = (JEditorPane) comp;
                // 只更新不可编辑的详情面板（避免误改输入框）
                if (!editorPane.isEditable()) {
                    editorPane.setText(htmlContent);
                    editorPane.setCaretPosition(0); // 滚动到顶部
                }
            }
            // 处理 JScrollPane（订单详情在滚动面板里）
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
            // 处理 JLabel（查找总金额标签）
            else if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String labelText = label.getText();
                // 查找包含"总金额"的标签
                if (labelText != null && labelText.contains("总金额")) {
                    label.setText("总金额：" + totalAmount + " 元");
                    System.out.println(" 已更新总金额标签: " + totalAmount);
                }
            }
            //  递归查找子面板
            else if (comp instanceof JPanel) {
                updateComponentInPanel((JPanel) comp, htmlContent, totalAmount);
            }
        }
    }


    /**
     * 显示外卖订单结账对话框
     *
     * 功能说明：
     * - 创建模态对话框，用于外卖订单的结账操作
     * - 左侧区域：分类显示自取订单和配送订单列表，用户可点击选择
     * - 右侧区域：显示选中订单的详细信息（菜品清单、金额等）及支付输入框
     * - 支持订单详情实时加载，支付金额验证，结账确认等完整流程
     * - 界面采用左右分栏布局，左侧占40%展示订单列表，右侧占60%展示详情与支付
     *
     * 交互流程：
     * 1. 用户点击左侧订单 → 右侧加载该订单详情
     * 2. 用户输入支付金额 → 点击确认结账
     * 3. 系统验证金额并执行结账 → 显示结果并关闭对话框
     */
    public void showTakeoutOrderListDialog() {
        JDialog dialog = new JDialog(this, "📦 外卖订单结账", true);
        dialog.setSize(1000, 650);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(240, 248, 255)); // 淡蓝色背景


        // ===== 状态变量 =====
        final String[] selectedOrderNumber = {null};// 用于在lambda/匿名类中存储并修改选中的外卖订单号（数组引用技巧）
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
        // 添加兩個訂單列表面板
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

    /**
     * 创建外卖订单列表面板
     *
     * 功能说明：
     * 1. 根据配送方式（自取/配送）加载对应的订单列表
     * 2. 无订单时显示"暂无外卖订单"的空状态提示
     * 3. 有订单时遍历生成可点击的订单行，支持悬停高亮
     * 4. 使用垂直滚动面板容纳多条订单，避免界面溢出
     * 5. 点击订单行可加载详情并进入结账流程
     *
     * @param title              面板标题（如"🟢 自取订单"）
     * @param deliveryMethod     配送方式（"PICKUP"或"DELIVERY"）
     * @param selectedOrderNumber 用于回传选中的订单号（数组引用）
     * @param dialog             父对话框，用于弹出确认/错误提示
     * @return 组装完成的订单列表面板
     */
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

        // 判断订单列表是否为空或null
        if (orders == null || orders.isEmpty()) {
            JLabel emptyLabel = new JLabel(" 暂无外卖订单", SwingConstants.CENTER);
            emptyLabel.setForeground(new Color(150, 150, 150));
            emptyLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            emptyLabel.setBorder(new EmptyBorder(30, 0, 30, 0));
            listPanel.add(emptyLabel);
        } else {//订单列表不为空，遍历显示订单
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

    /**
     *  创建外卖订单列表中的单行记录组件
     *
     * 【功能概述】
     * 该方法用于构建外卖订单列表中的一行展示项，包含订单号、制作状态、下单时间三个信息字段，
     * 并绑定鼠标交互事件（悬停高亮 + 点击确认结账），实现直观的订单管理与结账入口。
     *
     * 【参数说明】
     * @param orderNumber        外卖订单号（格式：P-20260305-001 / D-20260305-001）
     * @param status             订单制作状态（"制作中" / "制作完成"）
     * @param time               下单时间字符串（格式：HH:mm）
     * @param selectedOrderNumber 用于回传选中订单号的数组引用（单元素数组实现"引用传递"）
     * @param dialog             父级对话框引用，用于弹出确认框时模态锁定
     *
     * 【返回值】
     * @return JPanel 订单行面板组件，可直接添加到订单列表容器中
     *
     * 【交互逻辑】
     * • 鼠标悬停：背景变浅蓝 + 边框加粗变蓝，提供视觉反馈
     * • 鼠标点击：弹出二次确认对话框 → 用户确认后加载订单详情并进入结账流程
     * • 状态着色："制作完成"显示绿色，"制作中"显示红色，快速识别订单进度
     */
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
        // 使用匿名内部类实现 MouseAdapter，仅重写关心的事件方法
        row.addMouseListener(new MouseAdapter() {
            //  保存原始背景色，用于鼠标移出时恢复（避免多次悬停导致颜色累积变化）
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
                    selectedOrderNumber[0] = orderNumber; //  写入订单号
                    loadOrderDetail(orderNumber, dialog); // 同时加载订单详情到右侧
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

    /**
     * 创建外卖订单结账的支付面板
     *
     * 功能说明：
     * • 显示金额明细：菜品总额、配送费（仅配送订单）、应付总金额
     * • 提供支付金额输入框与确认/取消操作按钮
     * • 结账前自动验证订单状态：
     *   - 自取订单：需为【制作完成】状态
     *   - 配送订单：需为【已送达】状态
     * • 支持动态更新金额显示（加载订单详情后自动刷新）
     *
     * @param selectedOrderNumber 选中的外卖订单号（数组引用，用于回传）
     * @param totalLabel          总金额显示标签（数组引用，用于外部更新）
     * @param detailDisplay       订单详情 HTML 显示组件
     * @param dialog              当前结账对话框引用（用于关闭/提示）
     * @return 配置完成的支付面板 JPanel
     */
    private JPanel createPaymentPanel(String[] selectedOrderNumber, JLabel[] totalLabel,
                                      JEditorPane detailDisplay, JDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 0, 0, 0));
        panel.setBackground(new Color(250, 250, 255));

        // 【关键修改】金额显示面板 - 支持配送费显示
        JPanel amountPanel = new JPanel(new GridLayout(3, 2, 10, 10));  // 改为 3 行
        amountPanel.setBackground(new Color(255, 250, 250));
        amountPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 200, 200), 1),
                new EmptyBorder(15, 15, 15, 15)
        ));

        //  新增：菜品总价标签
        JLabel itemsTotalLabel = new JLabel("菜品：0.00 元");
        itemsTotalLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        //  新增：配送费标签（默认隐藏）
        JLabel deliveryFeeLabel = new JLabel("配送费：0.00 元");
        deliveryFeeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        deliveryFeeLabel.setVisible(false);  // 默认隐藏

        // 总金额标签
        JLabel totalAmountLabel = new JLabel("总金额：0.00 元");
        totalAmountLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        totalAmountLabel.setForeground(new Color(220, 20, 60));
        totalLabel[0] = totalAmountLabel;

        //  组装金额面板
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

                // 【关键修复】先验证订单状态，再执行结账
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

                    // 3.  验证订单状态
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

        // 加载订单详情时更新金额显示
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
     * 更新支付面板的金额显示
     *
     *  功能说明：
     * 1. 解析订单详情中的菜品总价、配送费、配送方式
     * 2. 更新"菜品"标签显示金额
     * 3. 仅当配送方式=DELIVERY 且配送费>0 时，显示"配送费"标签
     * 4. 计算并更新"总金额 = 菜品 + 配送费"
     *
     *  参数说明：
     * @param orderDetails      订单详情数据（含 itemsTotal/deliveryFee/deliveryMethod）
     * @param itemsTotalLabel   菜品金额显示标签
     * @param deliveryFeeLabel  配送费显示标签（按需显示/隐藏）
     * @param totalAmountLabel  总金额显示标签
     *
     *  异常处理：捕获所有异常并打印错误，避免界面崩溃
     */
    private void updatePaymentPanelAmounts(Map<String, Object> orderDetails,
                                           JLabel itemsTotalLabel,
                                           JLabel deliveryFeeLabel,
                                           JLabel totalAmountLabel) {
        try {
            // 获取菜品总价
            double itemsTotal = 0.0;
            // ① 从订单详情中获取菜品总额对象（可能为 Double/BigDecimal/Integer 等 Number 子类
            Object itemsTotalObj = orderDetails.get("itemsTotal");
            // ② 安全类型检查：确保对象是 Number 类型，避免 ClassCastException
            if (itemsTotalObj instanceof Number) {
                // ③ 统一转换为 double：兼容所有 Number 子类（Double/BigDecimal/Integer 等）
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

            //  根据配送方式显示/隐藏配送费标签
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


    /**
     *  显示换桌操作对话框（用户交互入口）
     *
     * <p><b>功能说明：</b></p>
     * <ul>
     *   <li>提供图形化界面，供用户输入源餐桌和目标餐桌编号</li>
     *   <li>验证输入完整性，防止空值提交</li>
     *   <li>将用户输入封装为字符串数组返回，供 Controller 层处理业务逻辑</li>
     * </ul>
     */
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

    //7.6. 複雜對話框交互與返回值統一設計
    //技術說明：通過 Map<String, Object> 統一封裝對話框輸入結果，支持多模式（新建/取消/修改/分配）的預約管理表單。
    //通過 mode 參數動態切換表單結構，實現「一個對話框支持四種業務場景」。使用 Map 作為通用返回容器，避免定義多個 DTO 類。模態對話框 (setModal(true)) 確保用戶必須完成操作才能繼續，符合業務流程的順序性要求
    /**
     * 显示预约管理对话框（支持四种操作模式）
     *
     * <p><b>功能概述：</b> 统一入口处理预约的创建、取消、修改、分配四种业务场景，
     * 通过单选按钮动态切换表单内容，实现"一个对话框支持多种业务"的交互设计。</p>
     *
     * <p><b>核心特性：</b></p>
     * <ul>
     *   <li><b>模式切换：</b> CREATE/CANCEL/EDIT_TIME/ASSIGN 四种模式互斥选择</li>
     *   <li><b>动态表单：</b> 根据选中模式实时重建表单面板，避免多对话框维护成本</li>
     *   <li><b>智能验证：</b> 时间格式/电话格式/餐桌数量/聚餐桌规则等多层校验</li>
     *   <li><b>数据封装：</b> 使用 Map&lt;String, Object&gt; 统一返回结果，适配不同业务场景</li>
     *   <li><b>用户体验：</b> 红色边框提示/聚焦反馈/二次确认弹窗等交互细节</li>
     * </ul>
     *
     * <p><b>业务流程：</b></p>
     * <ol>
     *   <li>用户选择操作模式 → ② 表单动态渲染 → ③ 输入数据并验证 →
     *       ④ 组装结果返回 → ⑤ Controller 层执行对应业务逻辑</li>
     * </ol>
     *
     * @param mode 操作模式（CREATE/CANCEL/EDIT_TIME/ASSIGN），null 时默认为 CREATE
     * @param existingReservation 现有预约数据（编辑/取消/分配模式时传入）
     * @return 操作结果 Map，包含 mode 字段标识操作类型及其他业务数据
     *
     * @note 1. <b>线程安全</b>：所有 UI 操作在 EDT 线程执行，业务数据通过 final 变量传递
     *       2. <b>异常处理</b>：验证失败时设置 shouldClose=false 阻止对话框关闭，确保用户修正
     *       3. <b>扩展性</b>：新增模式只需在 switch 分支添加逻辑，不影响现有代码
     */
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

        // 将单选按钮加入按钮组，实现互斥选择
        ButtonGroup group = new ButtonGroup();
        group.add(createRadio);
        group.add(cancelRadio);
        group.add(editTimeRadio);
        group.add(assignTableRadio);

        // 将单选按钮加入按钮组，实现互斥选择
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
        dialog.add(modePanel, BorderLayout.NORTH);// 模式面板放在对话框顶部

        // ===== 表单面板 =====
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(new Color(245, 248, 255));

        JTextField idField = new JTextField(10);
        JTextField nameField = new JTextField(20);
        JTextField phoneField = new JTextField(15);
        JTextField timeField = new JTextField(20);
        idField.setEditable(false);// 预约号不可编辑

        // 如果传入现有预约数据，则预填表单字段
        if (existingReservation != null) {
            idField.setText(String.valueOf(existingReservation.getOrDefault("reservation_id", "")));
            nameField.setText(String.valueOf(existingReservation.getOrDefault("customer_name", "")));
            phoneField.setText(String.valueOf(existingReservation.getOrDefault("customer_phone", "")));
            timeField.setText(String.valueOf(existingReservation.getOrDefault("reservation_time", "")));
        }

        // 根据模式重建表单面板内容
        rebuildFormPanel(formPanel, mode, idField, nameField, phoneField, timeField, existingReservation);

        // 将 formPanel 包装在 JScrollPane 中（支持滚动）
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);  // 去除边框
        scrollPane.getViewport().setBackground(new Color(245, 248, 255));  // 设置背景色
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);  // 需要时显示垂直滚动条
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);  // 不显示水平滚动条

        dialog.add(scrollPane, BorderLayout.CENTER);

        // ===== 模式切换监听 =====
        ActionListener switchMode = e -> {
            // 根据当前选中的单选按钮确定模式
            String selectedMode = createRadio.isSelected() ? "CREATE" :
                    cancelRadio.isSelected() ? "CANCEL" :
                            editTimeRadio.isSelected() ? "EDIT_TIME" : "ASSIGN";
            rebuildFormPanel(formPanel, selectedMode, idField, nameField, phoneField, timeField, existingReservation);    // 重建表单面板以切换模式
        };
        // 为每个单选按钮添加模式切换监听器
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

        final Map<String, Object>[] result = new Map[1];// 用于存储对话框返回结果的数组（利用数组引用在 lambda 中修改）


        confirmBtn.addActionListener(e -> {


            // 使用標誌位控制是否關閉對話框
            boolean shouldClose = true;
            // 实时读取界面上选中的单选框
            String selectedMode = createRadio.isSelected() ? "CREATE" :
                    cancelRadio.isSelected() ? "CANCEL" :
                            editTimeRadio.isSelected() ? "EDIT_TIME" : "ASSIGN";

            //System.out.println(" [EXEC] selectedMode 实际值: [" + selectedMode + "]");
            try {
                if ("CREATE".equals(selectedMode)) {
                    System.out.println(" [EXEC] 进入 CREATE 分支");
                    // 【步驟1】收集基礎信息（姓名、電話、時間）
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
                    // 检查预约时间是否为过去时间
                    try {
                        // 解析用户选择的日期和时间
                        LocalDateTime reserveTime = LocalDateTime.parse(dateStr + " " + timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                        // 如果预约时间早于当前时间
                        if (reserveTime.isBefore(LocalDateTime.now())) {
                            showError("不能预约过去的时间！"); // 弹出错误弹窗
                            shouldClose = false; // 标记不关闭对话框
                            return; // 终止后续逻辑，拒绝预约
                        }
                    } catch (Exception ee) {
                        // 理论上不会执行到这里，因为前面已经验证了格式
                    }

                    if (name.isEmpty() || phone.isEmpty()) {
                        showError("請填寫必填字段");
                        shouldClose = false;
                        return;
                    }
                    // 验证电话号码只能包含阿拉伯数字（0-9）
                    if (!phone.matches("^\\d+$")) {
                        showError(" 聯繫電話格式錯誤！\n\n" +
                                "只能輸入阿拉伯數字（0-9）\n" +
                                "例如：13812345678");
                        phoneField.requestFocus();

                        //  红色边框提示（视觉反馈）
                        phoneField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));

                        //  3秒后恢复边框（可选，提升用户体验）
                        SwingUtilities.invokeLater(() -> {
                            try {
                                Thread.sleep(3000);
                                phoneField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                            } catch (InterruptedException ignored) {}
                        });

                        shouldClose = false;
                        return;
                    }

                    String reservationTime = dateStr + " " + timeStr;

                    // 【步驟2】判斷餐桌選擇模式
                    JRadioButton manualModeRadio = (JRadioButton) formPanel.getClientProperty("manualModeRadio");
                    JRadioButton quantityModeRadio = (JRadioButton) formPanel.getClientProperty("quantityModeRadio");

                    boolean isManualMode = (manualModeRadio != null && manualModeRadio.isSelected());

                    // 【步驟3】收集餐桌類型 + 1.5小時確認
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

                    // 【步驟4】根據模式分別驗證（ 核心修復位置）
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

                        // 驗證餐桌數量是否符合餐桌類型限制
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

                        // 驗證失敗時：顯示錯誤 + 聚焦 + 設置標誌位 + 直接返回
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
                        System.out.println(" [DEBUG] 数量模式验证开始...");


                        // ── 模式B：填寫桌子數量 ──
                        JRadioButton twoSeatRadio = (JRadioButton) formPanel.getClientProperty("twoSeatRadio");
                        JTextField twoSeatQty = (JTextField) formPanel.getClientProperty("twoSeatQty");
                        JRadioButton fourSeatRadio = (JRadioButton) formPanel.getClientProperty("fourSeatRadio");
                        JTextField fourSeatQty = (JTextField) formPanel.getClientProperty("fourSeatQty");
                        JRadioButton sixSeatRadio = (JRadioButton) formPanel.getClientProperty("sixSeatRadio");
                        JTextField sixSeatQty = (JTextField) formPanel.getClientProperty("sixSeatQty");

                        //  调试：打印组件状态
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

                        //  关键调试：打印 tableConfig 内容
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

                        // 【核心驗證】根據餐桌類型限制數量
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
                            showError(" 預付金額格式錯誤！");
                            shouldClose = false;
                            return;
                        }
                    }

                    //  獲取備註內容（允許為 null 或空字符串）
                    String notesText = null;
                    if (notes != null) {
                        notesText = notes.getText().trim();
                        if (notesText.isEmpty()) {
                            notesText = null;
                        }
                    }

                    // 【步驟6】【核心修復】計算桌子總數 + 組裝結果
                    //  關鍵修復：從數據計算 tableCount，不再依賴 tableSpinner
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
                    result[0] = new HashMap<>();// 设置操作模式为新建预约
                    result[0].put("mode", "CREATE");// 存入客户姓名
                    result[0].put("customerName", name);
                    result[0].put("customerPhone", phone);
                    result[0].put("reservationTime", reservationTime);
                    result[0].put("tableCount", totalTableCount);
                    result[0].put("tableType", tableType);
                    result[0].put("within15Hours", within15Hours);
                    result[0].put("tableSelectionMode", isManualMode ? "MANUAL" : "QUANTITY");// 根据模式设置不同的字段：手动输入桌号 / 填写桌子数量
                    result[0].put("tableConfig", tableConfig.isEmpty() ? null : tableConfig);
                    result[0].put("selectedTables", selectedTables.isEmpty() ? null : selectedTables);

                    result[0].put("preOrder", preOrderYes.isSelected());// 确保传递预点餐状态（Boolean）
                    result[0].put("isPrepaid", prepaidCheck != null && prepaidCheck.isSelected());
                    result[0].put("prepaidAmount", amount);
                    result[0].put("notes", notesText);  //  允許為 null

                }

                else if ("ASSIGN".equals(selectedMode)) {
                    System.out.println(" [EXEC] 进入 ASSIGN 分支");

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
                    // 【新增】餐桌连续性和相邻性验证（聚餐桌/合并桌专用）
                    //  先查询预约详情，获取餐桌类型配置
                    TableReservation reservation = null;
                    if (controller != null) {
                        reservation = controller.getReservationDetail(resId);
                    }

                    if (reservation != null) {
                        String groupType = reservation.getGroupType();  // MAIN / MERGED / GROUP
                        String configDesc = reservation.getTableConfigDesc();

                        //  解析餐桌容量（从配置描述中提取）
                        int requiredCapacity = 0;
                        if (configDesc != null) {
                            String normalized = configDesc.replaceAll("\\s+", "");
                            if (normalized.contains("2人桌")) requiredCapacity = 2;
                            else if (normalized.contains("4人桌")) requiredCapacity = 4;
                            else if (normalized.contains("6人桌")) requiredCapacity = 6;
                        }

                        // 【聚餐桌验证：连续桌号】
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

                        // 【合并桌验证：左右相邻（每行3张）】
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

                    result[0] = new HashMap<>();
                    result[0].put("mode", "ASSIGN");
                    result[0].put("reservationId", resId);
                    result[0].put("selectedTables", selectedTables);
                }

                else if ("CANCEL".equals(selectedMode)) {
                    System.out.println(" [EXEC] 进入 CANCEL 分支");
                    String id = idField.getText().trim();
                    if (id.isEmpty()) {
                        showError("請輸入預約號");
                        shouldClose = false;
                        return;
                    }
                    result[0] = Map.of("mode", "CANCEL", "reservationId", id); // 組裝取消預約的返回結果：模式 + 預約號

                }

                else {  // EDIT_TIME
                    System.out.println(" [EXEC] 进入 EDIT_TIME/默认分支, selectedMode=[" + selectedMode + "]");
                    // ── 1. 获取并验证预约号 ──
                    String id = idField.getText().trim();
                    if (id.isEmpty()) {
                        showError("請輸入預約號");
                        shouldClose = false;
                        return;
                    }
                    // 2. 创建用于存储修改项的 Map 容器
                    Map<String, Object> edits = new HashMap<>();

                    // ──3. 修改時間 ──
                    JCheckBox checkTime = (JCheckBox) formPanel.getClientProperty("editCheckTime");// 获取"修改时间"复选框
                    JTextField newTimeField = (JTextField) formPanel.getClientProperty("editNewTime"); // 获取新时间输入框
                    if (checkTime != null && checkTime.isSelected()) {// 如果用户勾选了修改时间
                        String newTime = newTimeField.getText().trim();
                        if (newTime.isEmpty() || !newTime.matches("^\\d{4}-\\d{2}-\\d{2} [0-2]?\\d:[0-5]\\d$")) {
                            showError("新時間格式錯誤！請使用 yyyy-MM-dd HH:mm");
                            newTimeField.requestFocus();// 聚焦到时间输入框
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
                                    // 2人桌只能选1张
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
                                    // 【新增】4人桌只能选1或2张
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

                                // 【核心修復】從界面上的 originalLabel 提取實際值
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
                                showError(" 增加金額格式錯誤！請輸入有效數字。");
                                incrementField.requestFocus();
                                shouldClose = false;
                                return;
                            }
                        }
                    }

                    // 修改預點餐（只能從「否」改成「是」）
                    JCheckBox checkPreOrder = (JCheckBox) formPanel.getClientProperty("editCheckPreOrder");
                    JRadioButton changeToYesRadio = (JRadioButton) formPanel.getClientProperty("editPreOrderRadio");
                    Boolean currentPreOrder = (Boolean) formPanel.getClientProperty("editCurrentPreOrder");

                    if (checkPreOrder != null && checkPreOrder.isSelected()) {
                        //  驗證：只能從「否」改成「是」
                        if (currentPreOrder != null && currentPreOrder) {
                            showError(" 當前已是預點餐狀態，不能取消預點餐！");
                            shouldClose = false;
                            return;
                        }

                        if (changeToYesRadio != null && changeToYesRadio.isSelected()) {
                            // 設置為「是」
                            edits.put("preOrder", true);
                        } else {
                            showError(" 已勾選修改預點餐，請選擇「改為：是」！");
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
                        showError("  請至少勾選一項修改內容");
                        shouldClose = false;
                        return;
                    }
                    // 预点餐状态下修改6人桌数量的二次确认
                    if (existingReservation != null && Boolean.TRUE.equals(existingReservation.get("preOrder"))) {
                        Object tableConfigObj = edits.get("tableConfig");
                        if (tableConfigObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Integer> newConfig = (Map<String, Integer>) tableConfigObj;

                            // 从 Map 中获取原始配置描述，而不是直接调用方法
                            String originalConfigDesc = (String) existingReservation.get("table_config_desc");
                            Map<String, Integer> originalConfig = parseTableConfigFromDesc(originalConfigDesc);

                            int originalSixSeatCount = originalConfig.getOrDefault("6", 0);
                            int newSixSeatCount = newConfig.getOrDefault("6", 0);

                            // 如果6人桌数量发生变化，且符合聚餐桌规则（≥3张）
                            if (originalSixSeatCount != newSixSeatCount && newSixSeatCount >= 3) {
                                String confirmMsg = String.format(
                                        "<html><b> 桌子数量变更提示</b><br><br>" +
                                                "当前预约已是<b>预点餐状态</b>（pre_order=1）<br>" +
                                                "6人桌数量将从 <b>%d 张</b> 变更为 <b>%d 张</b><br><br>" +
                                                "<font color='#d32f2f'> 注意：</font><br>" +
                                                "• 聚餐桌菜品份额将按比例调整<br>" +
                                                "• 已点餐菜品将重新分配到新桌子数量<br>" +
                                                "• 请确认是否继续修改？</html>",
                                        originalSixSeatCount, newSixSeatCount
                                );

                                int confirm = JOptionPane.showConfirmDialog(
                                        dialog,
                                        confirmMsg,
                                        "预点餐修改确认",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE
                                );

                                if (confirm != JOptionPane.YES_OPTION) {
                                    shouldClose = false;
                                    return; // 用户点击"否"或关闭弹窗，终止修改
                                }
                            }
                        }
                    }
                    result[0] = new HashMap<>();
                    result[0].put("mode", "EDIT_TIME");// 设置操作模式
                    result[0].put("reservationId", id);// 设置预约号
                    result[0].put("edits", edits);// 设置修改项集合
                }


            } catch (Exception ex) {
                showError("錯誤：" + ex.getMessage());
                ex.printStackTrace();
                shouldClose = false;  // 異常時也不關閉對話框
            }

            // 只有驗證全部通過才關閉對話框
            if (shouldClose) {
                dialog.dispose();
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
        return result[0];
    }

    /**
     * 重建预约对话框表单面板（支持4种模式）
     *
     * 【CREATE模式】新建预约
     * ├─ 基础信息：日期选择器 + 时间输入框（自动格式校验）+ 姓名/电话
     * ├─ 锁定状态：根据预约时间自动判断1.5小时内/外，联动"是否到店"单选
     * ├─ 餐桌类型：个人桌(1张)/合并桌(2张同容量)/聚餐桌(3+张6人桌)
     * ├─ 选择方式：手动输入桌号（带连续/容量/重复校验）或填写数量
     * ├─ 预点餐：可勾选预付定金，金额输入框联动
     * └─ 备注：多行文本输入
     *
     * 【ASSIGN模式】分配餐桌
     * ├─ 查询方式：按预约号/电话模糊查询，支持多选弹窗
     * ├─ 信息显示：预约号/姓名/电话/时间/配置/备注（只读）
     * ├─ 餐桌选择：动态加载空闲餐桌复选框，按预约配置过滤容量
     * └─ 规则校验：聚餐桌需连续桌号，合并桌需同行相邻
     *
     * 【CANCEL模式】取消预约
     * ├─ 预约号输入：支持模糊查询，弹窗选择匹配记录
     * ├─ 查询结果：调用 controller.findReservationsForCancel() 过滤有效状态
     * └─ 回填逻辑：选中后自动填充完整预约号到输入框
     *
     * 【EDIT_TIME模式】修改预约资料
     * ├─ 信息查询：预约号模糊查询 + 详情弹窗选择
     * ├─ 信息显示：当前预约信息6项（含预点餐状态）
     * ├─ 可选修改项（CheckBox多选）：
     * │  ├─ 修改时间：新时间输入框（格式校验）
     * │  ├─ 修改配置：2/4/6人桌数量输入（联动启用）
     * │  ├─ 修改预点餐：仅支持"否→是"，不可反向
     * │  ├─ 修改定金：显示原金额 + 增加金额输入，实时计算新总额
     * │  └─ 修改备注：多行文本域
     * └─ 二次确认：预点餐状态下修改6人桌数量时弹窗警告
     *
     * 【通用特性】
     * ├─ 组件引用：通过 formPanel.putClientProperty() 存储供确认按钮使用
     * ├─ 动态切换：餐桌类型/选择方式/预点餐等选项联动显示隐藏
     * ├─ 实时校验：餐桌号格式/数量范围/时间格式/连续桌号等
     * └─ 用户体验：自动聚焦、格式转换（中文逗号→英文）、悬停提示
     */
    private void rebuildFormPanel(JPanel formPanel, String mode,
                                  JTextField idField,
                                  JTextField nameField,
                                  JTextField phoneField,
                                  JTextField timeFieldParam,
                                  Map<String, Object> existingReservation) {

        if (idField != null) idField.setText("");// 清空所有字段（模式切换时重置）
        formPanel.removeAll();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));// 设置垂直布局管理器


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
            JFormattedTextField ftf = dateEditor.getTextField();// 获取格式化文本字段
            ftf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { // 添加焦点监听器
                    SwingUtilities.invokeLater(() -> {// 添加焦点监听器
                        ftf.setSelectionStart(8);//  选中日部分的起始位置
                        ftf.setSelectionEnd(10);// 选中日部分的结束位置
                    });
                }
            });
            SwingUtilities.invokeLater(() -> {// 初始化时自动聚焦并选中日部分
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
            final JLabel[] lockStatusLabelRef = new JLabel[1];// 用于引用锁定状态标签
            Runnable autoSelectTask = () -> { // 自动选择1.5小时选项的任务
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
            // 添加文档监听器验证时间格式
            timeFieldComp.getDocument().addDocumentListener(new DocumentListener() {
                @Override// 插入时验证
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
                            Thread.sleep(300); // 短暂延迟避免频繁触发
                        } catch (InterruptedException ignored) {
                        }

                        if (timeFieldComp.isFocusOwner() && lockStatusLabelRef[0] != null) {
                            // 【关键修复】先格式化时间，再更新到输入框
                            String currentTime = timeFieldComp.getText().trim();//获取当前输入
                            String formattedTime = formatTimeToStandard(currentTime);// 格式化为标准格式

                            if (!formattedTime.equals(currentTime)) {// 如果格式不同则更新
                                timeFieldComp.setText(formattedTime);// 更新输入框内容
                                timeFieldComp.setCaretPosition(formattedTime.length()); // 恢复光标位置
                            }

                            // 现在使用格式化后的时间
                            updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabelRef[0]); // 更新锁定状态显示

                            // 【修复】通过 formPanel 获取单选按钮引用
                            JRadioButton yesRadio = (JRadioButton) formPanel.getClientProperty("within15hYes");
                            JRadioButton noRadio = (JRadioButton) formPanel.getClientProperty("within15hNo");

                            if (yesRadio != null && noRadio != null) {
                                autoSelectWithin15h(dateSpinner, timeFieldComp, yesRadio, noRadio);// 自动匹配选项
                            }
                        }
                    });
                }
            });

            // 添加失去焦点监听器
            timeFieldComp.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    String time = timeFieldComp.getText().trim();// 获取输入内容
                    if (!time.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {// 验证时间格式
                        timeFieldComp.setBorder(BorderFactory.createLineBorder(Color.RED, 2));// 格式错误显示红边框
                        timeFieldComp.setToolTipText("❌ 格式錯誤！請輸入 HH:mm");
                        SwingUtilities.invokeLater(() -> {
                            timeFieldComp.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                            timeFieldComp.setToolTipText("請輸入時間，格式：HH:mm");
                        });
                    } else {
                        if (lockStatusLabelRef[0] != null) {
                            updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabelRef[0]);
                            autoSelectTask.run();// 执行自动选择任务
                        }
                    }
                }
            });
            // 日期改变时更新锁定状态
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
            final boolean[] isAutoSelected = {true};// 标记是否自动选择
            try {
                updateLockStatus(dateSpinner, timeFieldComp, lockStatusLabel); // 初始化锁定状态
                autoSelectWithin15h(dateSpinner, timeFieldComp, yesWithin15h, noWithin15h);// 自动匹配选项
            } catch (Exception e) {
            }

            // 时间选项变更监听器
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
                // 【新增】如果是系统自动匹配选中的“否”（到店后锁定），且当前是“手动输入”，则静默切换回“填写数量”
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
                manualTablePanel.setVisible(isManual);
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

                /** 验证餐桌号输入*/
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
                            // 【新增】验证聚餐桌桌号必须连续
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

                            // 验证3：【核心】必须是左右相邻的桌子（不能上下相邻）
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

                                    System.out.println(" 合并桌验证通过：桌" + table1Num + " 和 桌" + table2Num + " 是左右相邻的");

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

            // 【新增】监听“手动输入”的点击事件：如果处于“到店后锁定”状态，则弹窗警告
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
                            //手动触发 switchMode，确保面板正确切换
                            switchMode.actionPerformed(null);

                        }
                    });
                }
            });
            manualTablePanel.add(manualHint, BorderLayout.NORTH);
            manualTablePanel.add(manualTableField, BorderLayout.CENTER);

            
            // 【模式 B】填写桌子数量面板（完整修复版 - 保留清空逻辑 + 数组引用）
            // final JPanel quantityPanel = new JPanel();
            quantityPanel.setLayout(new BoxLayout(quantityPanel, BoxLayout.Y_AXIS));
            quantityPanel.setBackground(new Color(245, 248, 255));
            quantityPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 220, 240), 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            quantityPanel.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));
            quantityPanel.setVisible(true);

            // 【关键】用数组包装解决 lambda 作用域问题
            final JTextField[] twoSeatQtyRef = {null};// 2 人桌数量输入框引用
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
            twoSeatQtyRef[0] = twoSeatQty;  //  存入数组引用

            twoSeatRadio.addActionListener(e -> {
                if (twoSeatRadio.isSelected()) {// 2 人桌选中监听器
                    twoSeatQty.setText("1");
                    twoSeatQty.requestFocus();
                    // 【核心】清空其他桌型数量（保留用户要求的代码）
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
            fourSeatQtyRef[0] = fourSeatQty;  //  存入数组引用

            fourSeatRadio.addActionListener(e -> {
                if (fourSeatRadio.isSelected()) {
                    //  合并桌模式下强制数量为2
                    if (mergedRadio != null && mergedRadio.isSelected()) {
                        fourSeatQty.setText("2");
                    } else {
                        fourSeatQty.setText("1");
                    }
                    fourSeatQty.requestFocus();
                    // 【核心】清空其他桌型数量（保留用户要求的代码）
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
            sixSeatQtyRef[0] = sixSeatQty;  //  存入数组引用

            sixSeatRadio.addActionListener(e -> {
                if (sixSeatRadio.isSelected()) {
                    //  合并桌模式下强制数量为2
                    if (mergedRadio != null && mergedRadio.isSelected()) {
                        sixSeatQty.setText("2");//默認兩張
                    } else if (groupRadio != null && groupRadio.isSelected()) {
                        sixSeatQty.setText("3");  // 聚餐桌默认3张
                    } else {
                        sixSeatQty.setText("1");
                    }
                    sixSeatQty.requestFocus();
                    // 【核心】清空其他桌型数量（保留用户要求的代码）
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
            // 【核心】餐桌类型切换监听器（禁用2人桌 + 强制数量 + 清空逻辑）
            // ═══════════════════════════════════════════════════════════
            ActionListener handleTableType = e -> {
                if (mergedRadio.isSelected()) {
                    // 合并桌：禁用2人桌单选按钮，强制4/6人桌数量为2
                    twoSeatRadio.setEnabled(false);
                    if (!fourSeatRadio.isSelected() && !sixSeatRadio.isSelected()) {
                        fourSeatRadio.setSelected(true);
                    }
                    //  强制数量为2（不变灰）
                    fourSeatQty.setText("2");
                    sixSeatQty.setText("2");
                    //  清空2人桌数量
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");
                } else if (groupRadio.isSelected()) {
                    // 聚餐桌：禁用2人桌和4人桌
                    twoSeatRadio.setEnabled(false);
                    twoSeatRadio.setSelected(false);
                    if (twoSeatQtyRef[0] != null) twoSeatQtyRef[0].setText("");

                    // 【核心修复】禁用4人桌并清空
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
                    fourSeatRadio.setEnabled(true);  // 【关键修复】恢复4人桌
                    sixSeatRadio.setEnabled(true);

                    // 【关键修复】清空所有数量
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

            // 添加两个面板到 formPanel
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

            ActionListener togglePreOrder = e -> {// 预点餐切换监听器
                boolean isPreOrder = preOrderYes.isSelected();// 判断是否选中预点餐
                prepaidWrapper.setVisible(isPreOrder);// 显示/隐藏预付面板
                if (!isPreOrder && prepaidCheck.isSelected()) { // 取消预点餐时重置预付
                    prepaidCheck.setSelected(false);
                    prepaidField.setText("0.00");
                    prepaidField.setEditable(false);
                }
                formPanel.revalidate();
                formPanel.repaint();
            };
            preOrderYes.addActionListener(togglePreOrder);// 添加监听器
            preOrderNo.addActionListener(togglePreOrder);
            togglePreOrder.actionPerformed(null);// 初始化执行

            // ── 备注 ──
            JTextArea notes = new JTextArea(3, 20);// 创建多行备注输入框
            formPanel.add(createFormField("備註:", new JScrollPane(notes)));

            // ═══════════════════════════════════════════════════════════
            //  保存引用供确认按钮使用 這些 putClientProperty 是為 確認按鈕的事件處理邏輯 服務的，用於跨作用域傳遞組件引用，是 Swing 開發中處理動態表單的經典實踐。
            //如：  JSpinner dateSpinner = (JSpinner) formPanel.getClientProperty("dateSpinner");
            // ═══════════════════════════════════════════════════════════
            formPanel.putClientProperty("dateSpinner", dateSpinner);// 保存日期选择器引用
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
            formPanel.putClientProperty("twoSeatQty", twoSeatQtyRef[0]);  //  用数组引用
            formPanel.putClientProperty("fourSeatRadio", fourSeatRadio);
            formPanel.putClientProperty("fourSeatQty", fourSeatQtyRef[0]);  //  用数组引用
            formPanel.putClientProperty("sixSeatRadio", sixSeatRadio);
            formPanel.putClientProperty("sixSeatQty", sixSeatQtyRef[0]);  //  用数组引用
            // 预点餐
            formPanel.putClientProperty("preOrderYes", preOrderYes);
            formPanel.putClientProperty("preOrderNo", preOrderNo);
            formPanel.putClientProperty("prepaidCheck", prepaidCheck);
            formPanel.putClientProperty("prepaidAmount", prepaidField);
            // 备注
            formPanel.putClientProperty("notesArea", notes);
        }

        //分配模式
        else if ("ASSIGN".equals(mode)) {
            //  查询方式选择：预约号 OR 电话
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
            // 【关键修改】如果 existingReservation 不为空，预填预约号
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println(" ASSIGN 模式：已预填预约号 " + resId);
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
            // 【修改点1】客人信息区域 - 添加预约号显示字段
            // ═══════════════════════════════════════════════════════════
            JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 5));  //  修改：从 4 行改为 5 行
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

            // 【修改点2】新增：预约号显示字段（只读）
            JTextField infoReservationId = new JTextField();
            infoReservationId.setEditable(false);
            infoReservationId.setBackground(new Color(250, 250, 250));
            infoReservationId.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            infoReservationId.setForeground(new Color(30, 144, 255));

            // 【新增】备注显示字段（只读，多行文本）
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

            // 【修改点3】添加预约号显示行（放在第一位）
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
            infoPanel.add(new JLabel("备注:"));  //  新增备注标签
            infoPanel.add(notesScrollPane);      //  新增备注文本域

            formPanel.add(infoPanel);
            formPanel.add(Box.createVerticalStrut(15));

            //  餐桌分配区域
            JLabel assignLabel = new JLabel("<html><b style='color:#1976d2;'>🍽️ 分配具体餐桌号</b></html>");
            formPanel.add(createFormField("", assignLabel, false));

            //  显示可用餐桌列表（复选框）
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

            // 【关键】动态生成餐桌复选框容器
            JPanel tablesGrid = new JPanel();
            tablesGrid.setLayout(new GridLayout(0, 3, 10, 10));  // 0行3列，自动扩展
            tablesGrid.setBackground(new Color(255, 255, 255));
            Map<String, JCheckBox> assignTableCheckBoxes = new HashMap<>();

            // 【核心】从数据库获取餐桌列表（通过Controller）
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
            // 【修改点4】查询按钮事件 - 支持模糊查询 + 弹窗选择
            // ═══════════════════════════════════════════════════════════
            queryBtn.addActionListener(ev -> {
                String queryValue = idField.getText().trim();
                if (queryValue.isEmpty()) {
                    showError("请输入预约号或电话号码");
                    return;
                }

                // 显示查询中状态
                infoReservationId.setText(" 查询中...");
                infoName.setText("");
                infoPhone.setText("");
                infoTime.setText("");
                tableConfigLabel.setText("查询中...");
                tableConfigLabel.setForeground(Color.GRAY);

                //  清空餐桌复选框
                tablesGrid.removeAll();
                assignTableCheckBoxes.clear();
                tablesGrid.revalidate();
                tablesGrid.repaint();

                //  判断查询方式
                boolean isPhoneQuery = byPhoneRadio.isSelected();
                try {
                    //  调用 Controller 进行模糊查询
                    List<Map<String, Object>> results;
                    if (isPhoneQuery) {
                        // 电话号码查询
                        results = controller.findReservationsByPhone(queryValue);
                    } else {
                        // 预约号查询
                        results = controller.findReservationsByCode(queryValue);
                    }

                    //  处理查询结果
                    if (results == null || results.isEmpty()) {
                        showError("未找到匹配的预约记录！\n请检查输入是否正确。");
                        infoReservationId.setText("");
                        return;
                    }

                    //  如果只有一个结果，直接显示
                    if (results.size() == 1) {
                        Map<String, Object> reservation = results.get(0);
                        fillReservationInfo(reservation, infoReservationId, infoName,
                                infoPhone, infoTime, tableConfigLabel, infoNotes);

                        // 【关键修复】更新输入框为完整预约号
                        String fullReservationId = (String) reservation.get("reservation_id");
                        if (fullReservationId != null) {
                            idField.setText(fullReservationId);
                        }

                        // 加载可分配餐桌
                        loadAvailableTables(reservation, tablesGrid, assignTableCheckBoxes);
                    }
                    //  如果有多个结果，弹出选择对话框
                    else {
                        Map<String, Object> selectedReservation = showReservationSelectionDialog(
                                results, isPhoneQuery, queryValue
                        );
                        if (selectedReservation != null) {
                            fillReservationInfo(selectedReservation, infoReservationId, infoName,
                                    infoPhone, infoTime, tableConfigLabel, infoNotes);

                            // 【关键修复】更新输入框为完整预约号
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

                    //  清空所有信息显示
                    infoReservationId.setText("");
                    infoName.setText("");
                    infoPhone.setText("");
                    infoTime.setText("");
                    tableConfigLabel.setText("未查询");
                    tableConfigLabel.setForeground(Color.GRAY);

                    //  清空餐桌复选框
                    for (JCheckBox checkBox : assignTableCheckBoxes.values()) {
                        checkBox.setSelected(false);
                    }

                } else {
                    queryLabel.setText("电话号码 *:");
                    idField.setText("");

                    //  清空所有信息显示
                    infoReservationId.setText("");
                    infoName.setText("");
                    infoPhone.setText("");
                    infoTime.setText("");
                    tableConfigLabel.setText("未查询");
                    tableConfigLabel.setForeground(Color.GRAY);

                    //  清空餐桌复选框
                    for (JCheckBox checkBox : assignTableCheckBoxes.values()) {
                        checkBox.setSelected(false);
                    }
                }
            };

            byIdRadio.addActionListener(switchQuery);
            byPhoneRadio.addActionListener(switchQuery);

            // ═══════════════════════════════════════════════════════════
            // 【修改点6】存引用供确认按钮使用 - 添加预约号字段引用
            // ═══════════════════════════════════════════════════════════
            formPanel.putClientProperty("assignIdField", idField);
            formPanel.putClientProperty("assignInfoReservationId", infoReservationId);  //  新增
            formPanel.putClientProperty("assignInfoName", infoName);
            formPanel.putClientProperty("assignInfoPhone", infoPhone);
            formPanel.putClientProperty("assignInfoTime", infoTime);
            formPanel.putClientProperty("assignTableConfigLabel", tableConfigLabel);
            formPanel.putClientProperty("assignInfoNotes", infoNotes);  //  新增
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
            // 【关键修改】如果 existingReservation 不为空，预填预约号
            // ═══════════════════════════════════════════════════════════
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println(" CANCEL 模式：已预填预约号 " + resId);
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

            //  查询按钮事件：模糊查询预约号（CANCEL 模式专用）
            queryBtn.addActionListener(ev -> {
                String queryValue = idField.getText().trim();
                if (queryValue.isEmpty()) {
                    showError("请输入预约号片段进行查询");
                    return;
                }

                try {
                    //  调用 Controller 进行模糊查询（支持预约号片段）
                    List<TableReservation> results = controller.findReservationsForCancel(queryValue);

                    if (results == null || results.isEmpty()) {
                        showError("未找到匹配的预约记录：\n\"" + queryValue + "\"");
                        return;
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 【核心修改】无论结果数量（包括1条），都弹出选择对话框
                    // ═══════════════════════════════════════════════════════════
                    TableReservation selectedReservation = showReservationSelectionDialogForEdit(results);

                    if (selectedReservation != null) {
                        // 【关键】将选择的完整预约号返回到 idField
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

            // 【关键修改】如果 existingReservation 不为空，预填预约号
            if (existingReservation != null) {
                String resId = (String) existingReservation.get("reservation_id");
                if (resId != null && !resId.isEmpty()) {
                    idField.setText(resId);
                    System.out.println(" EDIT_TIME 模式：已预填预约号 " + resId);
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 【关键修复】先声明显示字段变量（供查询按钮事件使用）
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

                        //  关键：传入 formPanel 参数
                        fillReservationInfoToForm(reservation, formPanel);
                        idField.setText(reservation.getReservationId());
                        System.out.println(" 已加载预约详情: " + reservation.getReservationId());
                    } else {
                        TableReservation selectedReservation = showReservationSelectionDialogForEdit(results);
                        if (selectedReservation != null) {
                            //  关键：传入 formPanel 参数
                            fillReservationInfoToForm(selectedReservation, formPanel);
                            idField.setText(selectedReservation.getReservationId());
                        }
                    }
                } catch (Exception e) {
                    showError("查询失败：" + e.getMessage());
                    e.printStackTrace();
                }
            });

            JPanel infoPanel = new JPanel(new GridLayout(6, 2, 10, 5));
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

            // 【关键】添加预约号显示行（放在第一位）
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
                            // 【核心修复】获取最新的原定金值
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


    /**
     * 创建表单字段面板（标签 + 输入组件）
     *
     * @param labelText      字段标签文本（含*表示必填）
     * @param inputComponent 输入组件（可为null）
     * @param fixedHeight    是否固定高度40px
     * @return 组装好的 JPanel 面板
     *
     * 功能说明：
     * - 创建带左边距的表单行面板，采用 BorderLayout 布局
     * - 标签居左显示，必填项标签自动标蓝，普通标签显示灰色
     * - 输入组件居中放置，若为 null 则用空白占位保持布局对齐
     * - 支持固定高度模式，用于统一表单项垂直间距
     */
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

        // 【关键修复】只有当 inputComponent 不为 null 时才添加到面板
        if (inputComponent != null) {
            panel.add(inputComponent, BorderLayout.CENTER);
        } else {
            // 如果为 null，留出一个空白占位区域，保持布局一致
            panel.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * 重载方法：创建表单字段面板（默认固定高度）
     *
     * 设计目的：
     * - 提供便捷调用方式，避免每次传参都写第三个参数
     * - 默认启用固定高度模式（40px），适配大多数单行输入场景
     * - 保持向后兼容，原有调用 createFormField(label, component) 的代码无需修改
     *
     * 使用场景：
     *  普通表单项：姓名/电话/时间等单行输入组件
     *  复杂面板：查询区/表格选择区等需自适应高度的组件（请调用三参版本传 false）
     *
     * @param labelText      字段标签文本（含*表示必填）
     * @param inputComponent 输入组件（可为null）
     * @return 组装好的 JPanel 面板（高度固定为40px）
     *
     * @see #createFormField(String, Component, boolean) 三参版本支持自定义高度
     */
    private JPanel createFormField(String labelText, Component inputComponent) {
        return createFormField(labelText, inputComponent, true);
    }


    /**
     *  辅助方法：从 table_config_desc 解析桌子配置 Map
     * 例如："2人桌 x1, 6人桌 x3, " → Map{"2":1, "6":3}
     */
    private Map<String, Integer> parseTableConfigFromDesc(String configDesc) {
        Map<String, Integer> config = new HashMap<>();    // 创建结果Map，用于存储容量→数量的映射关系
        if (configDesc == null || configDesc.isEmpty()) return config;    // 空值校验：配置描述为空时直接返回空Map
        String[] parts = configDesc.split(",");    // 按逗号分割多个配置项（如："2人桌 x1, 6人桌 x3" → ["2人桌 x1", " 6人桌 x3"]）
        for (String part : parts) {
            part = part.trim();        // 去除首尾空格，避免解析错误
            if (part.contains("人桌") && part.contains("x")) {// 过滤有效配置项：必须同时包含"人桌"和"x"关键字
                String[] segs = part.split("x");
                if (segs.length == 2) {// 按"x"分割容量部分和数量部分
                    String cap = segs[0].replaceAll("[^0-9]", "");// 提取纯数字容量（移除"人桌"等非数字字符）
                    String qtyStr = segs[1].replaceAll("[^0-9]", "").trim();// 提取纯数字数量并去除空格
                    if (!cap.isEmpty() && !qtyStr.isEmpty()) {
                        try {
                            config.put(cap, Integer.parseInt(qtyStr));// 解析数量并放入Map：key=容量，value=数量
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
        }
        return config;// 返回解析完成的配置Map
    }

    /**
    *计算并更新锁定状态
    */
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

    /**格式化時間為標準格式（9:00 → 09:00）
     *
     * @param timeStr
     * @return
     */
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
     *  類成員方法：自動選擇是否 1.5 小時內到店
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

            //  核心邏輯：自動選擇
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
     *  填充预约信息到界面
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
     *  显示预约选择对话框（修改资料专用 - 多个匹配结果时）
     *
     * @param results 查询结果列表
     * @return 用户选择的预约记录，取消则返回 null
     */
    private TableReservation showReservationSelectionDialogForEdit(List<TableReservation> results) {
        // 创建对话框
        JDialog dialog = new JDialog(this, "📋 选择预约记录", true);
        dialog.setSize(650, 500);
        dialog.setLocationRelativeTo(this);//框相对于父窗口居中显示，提升用户体验
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
                selectedResult[0] = results.get(0);//
                dialog.dispose();
            } else {
                showError("没有可选记录");
            }
        });

        cancelBtn.addActionListener(e -> {
            selectedResult[0] = null;//用户未点击卡片直接点"确定"时，默认选择第一条记录
            dialog.dispose();
        });

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedResult[0];//用户点击"取消"时，返回 null 表示放弃选择
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
     * 将预约单实体数据反填至修改资料表单面板中（采用 ClientProperty 隐式松耦合架构）。
     *
     * <p><b>核心执行流程：</b></p>
     * <ol>
     *   <li><b>安全检查：</b> 拦截 reservation 或 formPanel 为 null 的异常情况。</li>
     *   <li><b>数据回填：</b> 提取预约ID、姓名、电话，通过 {@code yyyy-MM-dd HH:mm} 格式化时间并填入 JTextField。</li>
     *   <li><b>状态渲染：</b> 动态更新桌型配置与预点餐标签（开启时显示绿色“是”）。</li>
     *   <li><b>财务对账：</b> 格式化原定金为两位小数（%.2f）。若定金大于 0 则高亮绿色显示，防止发生财务对账纠纷。</li>
     * </ol>
     *
     * @param reservation TableReservation 预约单数据持久化实体对象（不可为 null）
     * @param formPanel   JPanel 包含各输入组件引用的表单主面板（需事先绑定 ClientProperty 特征键）
     */
    private void fillReservationInfoToForm(TableReservation reservation, JPanel formPanel) {
        if (reservation == null || formPanel == null) return;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        //  从传入的 formPanel 获取组件引用
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

        //  新增：填充预点餐信息
        JLabel infoPreOrderLabel = (JLabel) formPanel.getClientProperty("editInfoPreOrderLabel");
        if (infoPreOrderLabel != null) {
            Boolean preOrder = reservation.getPreOrder();
            infoPreOrderLabel.setText(preOrder != null && preOrder ? " 是" : " 否");
            infoPreOrderLabel.setForeground(preOrder != null && preOrder ? new Color(0, 128, 0) : new Color(100, 100, 100));
        }

        // 【关键修复】更新原定金显示
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
     *  显示预约选择对话框（多个匹配结果时）
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
                "<html><b style='font-size:16px;color:white;'> 找到 " + results.size() + " 条匹配记录</b></html>",
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
            cardPanel.addMouseListener(new MouseAdapter() {// 为当前循环创建出来的卡片面板绑定鼠标进出、点击的事件监听器
                @Override// 重写鼠标点击回调
                public void mouseClicked(MouseEvent e) {// 当服务员用手指或鼠标真实点击了这张特定卡片时
                    selectedResult[0] = res;// 瞬间将当前被点中卡片的底层原始数据 Map 塞进外层的共享数组容器中
                    dialog.dispose();// 立刻销毁并彻底关闭当前选择预约单的主弹窗，解除线程阻塞状态
                }

                @Override
                public void mouseEntered(MouseEvent e) {// 当鼠标指针探入此卡片的物理边界内
                    cardPanel.setBackground(new Color(232, 245, 253));
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                            new EmptyBorder(12, 18, 12, 18)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {// 当鼠标指针滑出离开卡片物理范围时
                    cardPanel.setBackground(Color.WHITE);
                    cardPanel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                            new EmptyBorder(13, 19, 13, 19)
                    ));
                }
            });

            listPanel.add(cardPanel);// 把这张做好了交互特效、绑定了数据的卡片挂载入垂直箱式面板中
            listPanel.add(Box.createRigidArea(new Dimension(0, 8)));// 在两张预约单卡片之间强行打入 8 像素的隐形隔离块防止紧贴
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
            if (listPanel.getComponentCount() > 0) {// 如果卡片列表容器里确定查有实据、存在可以供选择的组件
                // 默认选择第一个
                selectedResult[0] = results.get(0);// 触发便捷无感知快捷兜底：将结果列表里的第 0 个默认第一条记录打入容器
                dialog.dispose();// 安全关闭销毁选择主弹窗
            } else {
                showError("没有可选记录");
            }
        });

        cancelBtn.addActionListener(e -> {
            selectedResult[0] = null;//用户既然主动放弃选择，则把外层共享容器的内容清空、彻底归于 null
            dialog.dispose();
        });

        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedResult[0];
    }

    /**
     * 核心UI渲染方法：动态创建并组装单张精美的“预约详情卡片面板”。
     * <p>
     * <b>该面板采用三段式扁平化卡片布局（BorderLayout）：</b>
     * 1. <b>左侧区域 (WEST)：</b> 渲染一个带有蓝色圆圈的数字化递增序号标签（Index），用于直观呈现队列排名或列表流水的先后顺序。
     * 2. <b>中央区域 (CENTER)：</b> 垂直堆叠三行高精度的核心业务文本（GridLayout）：
     *    <ul>
     *      <li>第一行：蓝色加粗的预约号（reservation_id）</li>
     *      <li>第二行：深灰色的客户姓名与联络电话（customer_name | customer_phone）</li>
     *      <li>第三行：经过兼容性解析并格式化后的预约就餐时间（reservation_time）</li>
     *    </ul>
     * 3. <b>右侧区域 (EAST)：</b> 渲染一个轻量化的现代向右箭头符号（"›"），提供视觉暗示，引导操作员该卡片可点击并支持深度交互。
     * </p>
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

        return card;//将当前方法在内存中辛辛苦苦创建、上色、并塞满了各种标签和符号的“卡片组件（JPanel）”整体包装好，正式交付给给调用它的“上游代码”。
    }

    /**
     * 核心业务方法：动态加载、解析并智能过滤当前符合预约配置的空闲餐桌列表。
     * <p>
     * <b>该方法执行以下三大核心链路：</b>
     * 1. <b>配置解析：</b> 解析预约字典中的规格文本（如 "4人桌 x1" 或 "2人桌 x2"），推断单桌额定容量（targetCapacity）、所需桌数（tableCount）以及组合类型（MAIN/MERGED/GROUP）。
     * 2. <b>规则运算：</b> 根据组合类型和容量进行业务风控过滤。实施“单桌向下兼容、大桌严禁浪费、聚餐严格对齐”的派位卡点策略。
     * 3. <b>UI渲染绑定：</b> 动态提取全店VACANT（空闲）状态餐桌，将符合规则的开通勾选，不符合规则的强制置灰、漂灰并挂载气泡提示（ToolTip），同时双向绑定至内存字典供提交使用。
     * </p>
     */
    private void loadAvailableTables(
            Map<String, Object> reservation,
            JPanel tablesGrid,
            Map<String, JCheckBox> assignTableCheckBoxes) {

        tablesGrid.removeAll();// 清空前端餐桌网格容器中的所有旧组件，防止多次点击导致画面叠加
        assignTableCheckBoxes.clear();// 清空内存中的餐桌复选框映射集合，为重新计算和绑定建立干净的数据底座

        // 【步骤 1】解析预约配置（桌子数量 + 容量）
        String configDesc = (String) reservation.get("table_config_desc");// 从预约单的 Map 字典中提取出规格描述字符串（例如 "4人桌 x1"）
        String selectionMode = (String) reservation.get("table_selection_mode");
        String groupType = (String) reservation.get("group_type");

        int targetCapacity = 0;
        int tableCount = 0;

        if (configDesc != null && !configDesc.isEmpty()) {
            // 【修复】同时支持带空格和不带空格的格式
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

        // 【修复】如果 group_type 为 null，根据数量推断
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

        System.out.println(" 分配餐桌 - 配置解析：容量=" + targetCapacity +
                "人，数量=" + tableCount + "张，桌型=" + groupType);

        // 【步骤 2】根据规则计算允许的容量范围
        boolean allow2Seat = false;
        boolean allow4Seat = false;
        boolean allow6Seat = false;

        if ("MAIN".equals(groupType)) { // 场景 A：如果属于单张独立桌的派位逻辑
            if (tableCount == 1) {// 确认数量约束确实只有 1 张桌子
                if (targetCapacity == 2) {// 细分规则 1：如果客人预定的是 2 人桌
                    allow2Seat = true;// 允许分给 2 人桌（最完美匹配）
                    allow4Seat = true;// 允许向下兼容分给 4 人桌（大马拉小车，防爆仓）
                    allow6Seat = false;// 严禁分给 6人桌（极度浪费大桌资源）
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
        // 【步骤 3】获取空闲餐桌并应用过滤规则
        if (controller != null) {
            try {
                List<Tables> allTables = controller.getAllVacantTables();
                if (allTables != null && !allTables.isEmpty()) {
                    for (Tables table : allTables) {
                        if (table.getStatus() == Tables.TableStatus.VACANT) {
                            String displayId = table.getDisplayId();
                            int capacity = table.getCapacity();

                            boolean isMatch = false;// 声明匹配结果旗帜，默认不匹配
                            if (capacity == 2 && allow2Seat) isMatch = true;// 如果是 2人小桌 且 上面的策略规则放行了 2人桌，判定匹配成功
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
     * 显示已预定餐桌的操作对话框（3个按钮）
     *
     * @param table 餐桌对象
     * @return 用户选择的操作类型："CHECK_IN" / "CANCEL" / "DELAY" / null(取消)
     */
    public String showReservedTableDialog(Tables table) {
        String displayId = table.getDisplayId();
        JDialog dialog = new JDialog(this, "📋 预定餐桌操作 - #" + displayId, true);
        dialog.setSize(420, 400);  //  高度+20，预留延迟时间显示空间
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.getContentPane().setBackground(new Color(245, 248, 255));

        // 信息面板  改为自动行数（0=自动扩展），支持动态添加延迟时间行
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        infoPanel.setBackground(new Color(245, 248, 255));
        infoPanel.setBorder(new EmptyBorder(15, 20, 10, 20));

        infoPanel.add(new JLabel("餐桌号:"));
        infoPanel.add(new JLabel(displayId));
        infoPanel.add(new JLabel("容量:"));
        infoPanel.add(new JLabel(table.getCapacity() + "人"));

        // 【核心修改】显示预约入座时间（从 table_reservations 表查询）
        String reservationTimeStr = "未设置";
        String rescheduledTimeStr = "";  // 【新增】延迟预约时间
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
                    // 【新增】延迟预约时间（仅当有值时显示）
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

        // 【新增】延迟预约时间行（仅当有延迟时间时添加）
        if (!rescheduledTimeStr.isEmpty()) {
            JLabel delayLabel = new JLabel("延迟的预约时间:");
            JLabel delayValue = new JLabel(
                    "<html><font color='#ff9800'><b>" + rescheduledTimeStr + "</b></font></html>"
            );  //  橙色加粗显示
            infoPanel.add(delayLabel);
            infoPanel.add(delayValue);
        }

        // 【新增】关联桌号：仅合并桌/聚餐桌显示
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
            // 【核心实现】延迟预约功能
            // ═══════════════════════════════════════════════════════════

            // 1️ 获取预约号
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
                showError(" 未找到关联的预约记录！");
                return;
            }

            // 2️ 弹出时间输入对话框（限制当天 + 非过去时间）
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
            dateSpinner.setEnabled(false);  //  锁定为今天

            // 时间输入框
            JTextField timeField = new JTextField(now.format(DateTimeFormatter.ofPattern("HH:mm")), 10);
            timeField.setToolTipText("请输入时间，格式：HH:mm（例：18:30）");

            timePanel.add(new JLabel("预约日期（仅限今天）:"));
            timePanel.add(dateSpinner);
            timePanel.add(new JLabel("新预约时间 *:"));
            timePanel.add(timeField);

            // 3️ 是否保留餐桌选项
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
            dialogPanel.add(new JLabel("<html><b>延迟预约 #" + delayReservationId + "</b></html>"), BorderLayout.NORTH);
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

            // 4️ 验证时间格式
            String timeStr = timeField.getText().trim();
            if (!timeStr.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                showError(" 时间格式错误！请输入 HH:mm（例：18:30）");
                return;
            }

            // 5️ 解析并验证时间
            try {
                LocalDateTime newTime = LocalDateTime.of(today, java.time.LocalTime.parse(timeStr));

                if (newTime.isBefore(now)) {
                    showError(" 延迟时间不能是过去的时间！\n当前时间: " +
                            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    return;
                }

                boolean keepTable = keepTableYes.isSelected();

                // 6️ 【核心】调用 Controller 执行延迟
                if (controller != null) {
                    Map<String, Object> delayResult = controller.delayReservation(
                            delayReservationId, newTime, keepTable);

                    // 7️ 处理返回结果
                    if ((Boolean) delayResult.get("success")) {
                        String successMsg = " 预约延迟成功！\n新时间: " +
                                newTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                        if (!keepTable) {
                            successMsg += "\n\n 餐桌 #" + table.getDisplayId() + " 已释放为空闲";
                        }

                        showInfo(successMsg);

                        //  刷新界面（通过 Controller）
                        SwingUtilities.invokeLater(() -> {
                            if (controller != null) {
                                controller.refreshTablesDisplay();

                                // 【核心修改】只有 releaseTables=true 时才刷新预约列表
                                Boolean releaseTables = (Boolean) delayResult.get("releaseTables");// 提取后端计算的关于是否真正腾空释放了餐桌资源的总闸标志
                                if (Boolean.TRUE.equals(releaseTables)) {// 若符合释放逻辑
                                    refreshQuantityReservationsLog();
                                    System.out.println(" 已刷新数量模式预约列表（餐桌已释放）");
                                }
                            }
                        });

                        // 【关键修复】设置返回值 + 关闭主对话框
                        result[0] = "DELAY";  // ← 标记用户选择了"延迟"
                        dialog.dispose();     // ← 关闭主对话框（不是子对话框！）

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

        return result[0];// 【返回核心】当对话框关闭、代码恢复流动后，将容器中沉淀下的具体行为字符串结果抛出给调用方
    }


    /**
     *  弹出输入框获取实际入座人数（支持合并桌 + 聚餐桌）
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

        // 2.  计算有效容量（支持普通桌/合并桌/聚餐桌）
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
        // ──  情况 2: 聚餐桌（3 张或以上）──
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

            // 【调试日志】确认聚餐桌容量计算
       //     System.out.println(" 聚餐桌容量计算: " + tableLabel + " = " + effectiveCapacity + "人");
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
     *  刷新数量模式预约显示（美化版 - 按钮分离 + 优雅交互 + 延迟状态 + 自动启停定时器）
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
          //  System.out.println("🔍 [DEBUG] 获取到预约记录数：" + reservations.size());

            // 2.  清空面板（关键！避免重复添加）
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
                        Object rescheduledObj = res.get("rescheduled_time");
                        String statusStr = (String) res.get("status");
                        Boolean within15h = (Boolean) res.get("within_15h");

                        // 解析预约时间
                        LocalDateTime resTime = null;// 初始化用于最终计算的预约时间变量
                        if (timeObj instanceof java.sql.Timestamp) {// 兼容性检查：判断获取的时间是否为数据库时间戳类型
                            resTime = ((java.sql.Timestamp) timeObj).toLocalDateTime();// 将 SQL 转换成 Java 的 LocalDateTime 类型
                        } else if (timeObj instanceof LocalDateTime) {// 判断时间对象是否本身就是 LocalDateTime 类型
                            resTime = (LocalDateTime) timeObj;// 进行安全的直接类型强转分配
                        }

                        // 【核心】解析延迟时间（如果有）
                        LocalDateTime rescheduledTime = null;
                        if (rescheduledObj instanceof java.sql.Timestamp) {
                            rescheduledTime = ((java.sql.Timestamp) rescheduledObj).toLocalDateTime();
                        } else if (rescheduledObj instanceof LocalDateTime) {
                            rescheduledTime = (LocalDateTime) rescheduledObj;
                        }

                        // 【四色状态判断逻辑】
                        enum ReservationStatus {EXPIRED, URGENT, DELAYED, NORMAL}
                        ReservationStatus status = ReservationStatus.NORMAL;

                        // 【核心】根据数据库状态 + 延迟时间判断显示状态
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

                        //  根据状态设置样式
                        Color bgColor, borderColor, circleColor, textColor;
                        String circleSymbol;

                        switch (status) {
                            case EXPIRED:  //  过期：蓝色系
                                bgColor = new Color(240, 248, 255);
                                borderColor = new Color(33, 150, 243);
                                circleColor = new Color(33, 150, 243);
                                textColor = new Color(21, 101, 192);
                                circleSymbol = "🔵";
                                break;
                            case URGENT:   //  紧急：红色系
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
                            default:       //  普通：灰色系
                                bgColor = new Color(250, 250, 250);
                                borderColor = new Color(221, 221, 221);
                                circleColor = new Color(204, 204, 204);
                                textColor = new Color(51, 51, 51);
                                circleSymbol = "⚪";
                                break;
                        }

                        // 【关键】创建面板时传入延迟时间
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

           // System.out.println(" 预约列表刷新完成：" + reservations.size() + " 条记录");

            //7.7. Spring 數據綁定與實時刷新機制
            //技術說明：通過 Controller.setView() 建立前後端連接，結合 javax.swing.Timer 與事件驅動實現數據實時同步。
            //定時器 (REFRESH_INTERVAL_MS = 600000) 每 10 分鐘自動刷新預約列表，但通過 hasData 判斷智能啟停，避免無意義的輪詢。SwingUtilities.invokeLater 確保數據解析與 UI 更新在 EDT 執行，防止後端查詢阻塞界面響應。
            // 根据查询结果自动启停定时器
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
                                " 加载失败：" + e.getMessage() +
                                "<br><small style='color:#666'>请检查数据库连接</small></div></html>",
                        SwingConstants.CENTER
                );
                reservationsLogPanel.add(errorLabel);
                reservationsLogPanel.revalidate();
                reservationsLogPanel.repaint();
            });

            //  异常时也停止定时器，避免无效刷新
            stopRefreshTimer();
        }
    }

    /**
     *  启动自动刷新定时器
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
     *  停止自动刷新定时器
     */
    private void stopRefreshTimer() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
            System.out.println(" 预约列表自动刷新定时器已停止");
        }
    }
    /**
     *  确保预约刷新定时器正在运行
     * 如果定时器未启动或已停止，则重新启动
     */
    public void ensureRefreshTimerRunning() {
        if (refreshTimer != null && !refreshTimer.isRunning()) {
            refreshTimer.start();
            System.out.println(" 预约刷新定时器已重新启动");
        }
    }


    /**
     *  创建预约记录面板（按钮分离版 - 只有右侧按钮可点击）
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
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));  //  限制最大高度
        panel.setPreferredSize(new Dimension(0, 55));                 //  设置首选高度
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

            //  如果有延迟时间，显示延迟后的时间（DELAYED 状态）
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
                statusText = " (已延遲)";  //  新增延迟标签
            }

            JLabel timeLabel = new JLabel(timeStr + statusText);

            //  颜色逻辑：EXPIRED=蓝色，DELAYED=橙色，其他=灰色
            if (status.toString().equals("EXPIRED")) {
                timeLabel.setForeground(new Color(33, 150, 243));  // 蓝色
            } else if (rescheduledTime != null) {
                timeLabel.setForeground(new Color(255, 152, 0));    //  橙色
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
        viewBtn.setContentAreaFilled(false);  //  关键：不填充背景
        viewBtn.setPreferredSize(new Dimension(95, 35));  //  限制按钮大小
        viewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));  // 手型光标
        viewBtn.setToolTipText("查看預約詳情");

        //  按钮悬停效果（仅按钮变色，不影响整行）
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

        //  绑定点击事件（只有按钮点击才触发）
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
     * 处理预约列表或表格组件的点击事件。
     * 触发后先弹出一个 HTML 格式的二阶段确认对话框。若用户选择“是”，
     * 则通过 Controller 实时向底层数据库或缓存追溯完整的预约详情模型，
     * 成功获取后直接调起对应的预约详情看板。
     *
     * @param reservationId 当前被点击触发的、全局唯一的预约单号（ID）
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
     * 构建并展示特定预约的详细信息模态对话框。
     * 支持动态展示常规时间/延迟时间视图，并提供“分配餐桌”、“修改预约”、“取消预约”三大核心业务操作入口。
     *
     * @param reservation 包含当前需要展示及操作的所有数据的预约对象（TableReservation）
     */
    private void showReservationDetailDialog(TableReservation reservation) {
        JDialog dialog = new JDialog(this, " 预约详情 - " + reservation.getReservationId(), true);
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

        //  检查是否为延迟预约
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
            statusLabel.setForeground(new Color(255, 152, 0));  //  橙色
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

        // 【修改】分配餐桌按钮事件 - 打开分配餐桌对话框
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

            // 4. 【关键修复】处理返回结果并直接调用 Service 保存
            if (result != null && "EDIT_TIME".equals(result.get("mode"))) {
                try {
                    String resId = (String) result.get("reservationId");
                    @SuppressWarnings("unchecked")//意思:这里很安全，不用你提醒
                    Map<String, Object> edits = (Map<String, Object>) result.get("edits");

                    if (edits != null && !edits.isEmpty()) {
                        // 【核心】直接调用 Controller 的专用方法保存（不打开新对话框！）
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
                                " 您没有勾选任何修改项！\n请至少勾选一项内容再进行修改。",
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

        // 【新增】取消预约按钮事件处理
        cancelBtn.addActionListener(e -> {
            // 1 二次确认弹窗（防止误操作）
            int confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "<html><b style='color:#d32f2f;'> 确认取消预约？</b><br><br>" +
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

            // 3️  调用 Controller 执行取消逻辑
            try {
                Map<String, Object> cancelResult = controller.cancelReservation(
                        reservation.getReservationId(),
                        cancellationReason
                );

                // 4️  根据返回结果处理提示（使用场景标志）
                if ((Boolean) cancelResult.get("success")) {
                    //  优先使用 Service 返回的用户友好消息
                    String message = (String) cancelResult.get("userMessage");

                    //  可选：根据场景标志自定义更详细提示
                    Boolean preOrderDeleted = (Boolean) cancelResult.get("preOrderDeleted");
                    Boolean depositForfeited = (Boolean) cancelResult.get("depositForfeited");
                    Double forfeitedAmount = (Double) cancelResult.get("forfeitedAmount");

                    //  显示成功提示
                    SwingUtilities.invokeLater(() -> {
                        showInfo(message);

                        //  刷新界面
                        Boolean needRefresh = (Boolean) cancelResult.get("needRefresh");
                        if (needRefresh == null || needRefresh) {
                            controller.refreshTablesDisplay();
                            refreshQuantityReservationsLog();
                        }

                        //  关闭当前详情对话框
                        dialog.dispose();
                    });

                } else {
                    //  失败提示
                    String errorMsg = (String) cancelResult.get("message");
                    SwingUtilities.invokeLater(() ->
                            showError(" 取消失败：" + errorMsg)
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
     *  显示取消原因输入对话框
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

        // 【新增】设置默认提示文本（灰色，输入后消失）
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

        // 【新增】默认值说明标签（灰色小字）
        JLabel defaultHintLabel = new JLabel(
                "<html><span style='color:#888;font-size:11px;'> 如不填写，系统将默认使用「顾客主动取消预约」</span></html>"
        );
        defaultHintLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

        // 组装面板
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(hintLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(defaultHintLabel, BorderLayout.SOUTH);  //  添加底部提示

        // 显示对话框
        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                " 取消预约原因",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String reason = reasonArea.getText().trim();

            // 【核心修复】判断是否为提示文本或空值
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


    /**
     * 显示预约匹配提醒弹窗
     *
     * 功能说明：
     * 1. 使用 SwingUtilities.invokeLater 确保在 EDT 线程执行，避免界面卡顿
     * 2. 构建 HTML 格式提示消息，包含预约号、客人信息、预约时间及所需餐桌规格
     * 3. 通过 JOptionPane 展示模态信息对话框，提醒操作员及时分配空闲餐桌
     *
     * @param reservationId 匹配的预约记录编号
     * @param customerName 预约客人姓名
     * @param customerPhone 预约客人联系电话
     * @param reservationTime 预约时间字符串
     * @param requiredCapacity 所需餐桌容量（如 2/4/6 人桌）
     * @param requiredCount 所需餐桌数量
     *
     * 执行时机：
     * - 餐桌状态变更为空闲时，自动检查并提示匹配的 1.5 小时内预约
     * - 确保预约顾客到店时餐桌已准备就绪，提升服务体验
     *
     * 线程安全：
     * - 所有 Swing 组件操作封装在 invokeLater 中，保证线程安全
     * - 避免后台业务线程直接操作界面导致异常
     */
    @Override
    public void showReservationMatchAlert(String reservationId, String customerName,
                                          String customerPhone, String reservationTime,
                                          int requiredCapacity, int requiredCount) {
        SwingUtilities.invokeLater(() -> {
            String message = String.format(
                    "<html><div style='padding:15px; font-family:Microsoft YaHei;'>" +
                            "<h3 style='color:#1976d2; margin:0 0 10px 0;'> 找到匹配的预约！</h3>" +
                            "<hr style='border:0; border-top:1px solid #eee; margin:10px 0;'>" +
                            "<b>预约号：</b>%s<br>" +
                            "<b>客人：</b>%s (%s)<br>" +
                            "<b>预约时间：</b>%s<br>" +
                            "<b>需要：</b>%d 张 %d 人桌<br><br>" +
                            "<font color='#1976d2'><b> 当前餐桌已空闲，请及时分配！</b></font>" +
                            "</div></html>",
                    reservationId, customerName, customerPhone, reservationTime,
                    requiredCount, requiredCapacity
            );

            JOptionPane.showMessageDialog(
                    this,
                    message,
                    " 预约匹配提醒",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    /**
     *  显示子桌合并确认对话框
     *
     * @param subTableA 子桌A的显示编号（如 "7a"）
     * @param subTableB 子桌B的显示编号（如 "7b"）
     * @param mainTableDisplayId 合并后的主桌编号（如 "7"）
     * @return true=用户确认合并，false=用户取消
     */
    public boolean showMergeConfirmationDialog(String subTableA, String subTableB, String mainTableDisplayId) {
        // 使用 %s 占位符，不要直接在字符串里拼接变量
        String message = String.format(
                "<html><div style='padding:15px; font-family:Microsoft YaHei;'>" +
                        "<h3 style='color:#1976d2; margin:0 0 10px 0;'> 确认合并餐桌？</h3>" +
                        "<hr style='border:0; border-top:1px solid #eee; margin:10px 0;'>" +
                        "<b>子桌 #%s</b> + <b>#%s</b><br>" +
                        "↓ 合并为 ↓<br>" +
                        "<b style='color:#4caf50; font-size:16px;'>主桌 #%s</b><br><br>" +
                        "<font color='#666'>合并后：</font><ul style='margin:5px 0; padding-left:20px;'>" +
                        "<li>两张子桌将恢复为主桌</li>" +
                        "<li>餐桌容量恢复为原始容量</li>" +
                        "<li>顾客组将自动分配到合并后的餐桌</li>" +
                        "</ul>" +
                        "<font color='#d32f2f'><b> 此操作不可撤销，确认继续？</b></font>" +
                        "</div></html>",
                subTableA, subTableB, mainTableDisplayId // 变量放在这里作为参数
        );

        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "合并餐桌确认",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }

    /**
     *  显示合并操作结果提示
     *
     * @param success 操作是否成功
     * @param message 提示消息内容
     */
    public void showMergeResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                JOptionPane.showMessageDialog(
                        this,
                        message,
                        " 操作成功",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        message,
                        " 操作失败",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    /**
     *  動態更新「結束營業/開始營業」按鈕狀態
     * @param isOpen true=營業中(顯示結束營業/紅色), false=已打烊(顯示開始營業/綠色)
     */
    public void setCloseDayButtonText(boolean isOpen) {
        SwingUtilities.invokeLater(() -> {
            if (closeDayButton != null) {
                if (isOpen) {
                    closeDayButton.setText("結束營業");
                    closeDayButton.setBackground(new Color(178, 34, 34)); // 深紅色
                } else {
                    closeDayButton.setText("開始營業");
                    closeDayButton.setBackground(new Color(34, 139, 34)); // 森林綠
                }
                closeDayButton.revalidate();
                closeDayButton.repaint();
            }
        });
    }

    /**
     * 更新营业状态的 UI 视觉显示。
     * 根据营业或打烊状态，动态切换底部面板（bottomPanel）的标题文本、
     * 文本颜色、背景色以及边框颜色，并在最后触发淡入渐变动画。
     *
     * @param isOpen true 表示当前处于“营业中”状态，false 表示“已打烊”状态
     */
    public void updateBusinessStatusDisplay(boolean isOpen) {
        // 1. 状态配置
        String title = isOpen ? "🟢 餐厅状态：营业中" : "🔴 餐厅状态：已打烊";
        Color titleColor = isOpen ? new Color(0, 120, 0) : new Color(180, 40, 40);
        Color bgColor = isOpen ?
                new Color(232, 245, 233) :  // 浅绿色背景
                new Color(255, 235, 235);   // 浅红色背景
        Color borderColor = isOpen ?
                new Color(76, 175, 80) :     // 绿色边框
                new Color(244, 67, 54);      // 红色边框

        // 2. 创建带圆角的边框
        Border lineBorder = BorderFactory.createLineBorder(borderColor, 2);// 创建2像素宽的彩色实线边框
        Border emptyBorder = BorderFactory.createEmptyBorder(8, 12, 8, 12);// 创建内部留白（上8、左12、下8、右12
        Border compoundBorder = BorderFactory.createCompoundBorder(lineBorder, emptyBorder);// 将线条边框和留白边框组合在一起

        // 3. 创建标题边框（带字体和颜色）
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                compoundBorder,
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft YaHei UI", Font.BOLD, 14),
                titleColor
        );
        titledBorder.setTitleJustification(TitledBorder.LEFT);// 强制标题文本左对齐

        // 4. 应用边框并设置背景
        bottomPanel.setBorder(titledBorder);
        bottomPanel.setBackground(bgColor);
        bottomPanel.setOpaque(true);  // 确保背景色生效

        // 5. 刷新显示
        bottomPanel.revalidate();
        bottomPanel.repaint();

        // 6. 可选：添加状态切换动画效果
        animateStatusChange(bottomPanel, isOpen);
    }

    /**
     * 实现面板状态切换时的淡入（Fade-in）过渡动画。
     * 通过 Swing Timer 定时触发重绘，逐步将透明度从 0.0f 累加至 1.0f。
     *
     * @param panel  需要执行渐变动画的目标 JPanel 组件
     * @param isOpen 状态标识（true 表示开启，false 表示关闭，可用于扩展后续逻辑）
     */
    private void animateStatusChange(JPanel panel, boolean isOpen) {
        // 初始透明度
        float[] alpha = {0.0f};

        Timer timer = new Timer(30, e -> {
            alpha[0] += 0.05f;  // 每次增加透明度
            if (alpha[0] >= 1.0f) {
                alpha[0] = 1.0f;
                ((Timer)e.getSource()).stop();  // 动画完成，停止定时器
            }
            panel.repaint();  // 重绘面板
        });

        // 重写 paintComponent 以支持透明度（需要在 JPanel 子类中实现）
        timer.setRepeats(true);  // 需要重复执行
        timer.start();
    }

    /**
     *  顯示未結賬餐桌警告对话框（可選：確認/取消）
     * @param unpaidTables 未結賬餐桌列表
     * @return true=用戶確認打烊, false=用戶取消
     */
    public boolean showUnpaidWarningDialog(List<String> unpaidTables) {
        if (unpaidTables == null || unpaidTables.isEmpty()) {
            return true; // 無未結賬訂單，直接通過
        }

        StringBuilder warningMsg = new StringBuilder();
        warningMsg.append("<html><div style='padding:10px; font-family:Microsoft YaHei;'>");
        warningMsg.append("<h3 style='color:#d32f2f; margin:0 0 10px 0;'> 未結賬訂單提醒</h3>");
        warningMsg.append("<hr style='border:0; border-top:1px solid #eee; margin:10px 0;'>");
        warningMsg.append("<p style='margin:5px 0;'>以下餐桌有未結賬訂單：</p><ul style='margin:5px 0; padding-left:20px;'>");

        // 最多顯示 10 個，避免對話框過大
        int displayCount = Math.min(unpaidTables.size(), 10);
        for (int i = 0; i < displayCount; i++) {
            warningMsg.append("<li>餐桌 #").append(unpaidTables.get(i)).append("</li>");
        }
        if (unpaidTables.size() > 10) {
            warningMsg.append("<li style='color:#666;'>... 還有 ").append(unpaidTables.size() - 10)
                    .append(" 個餐桌未顯示</li>");
        }

        warningMsg.append("</ul>");
        warningMsg.append("<p style='color:#666; font-size:12px; margin:10px 0 0 0;'>");
        warningMsg.append(" 未結賬訂單將保留至次日，不影響打烊操作。</p>");
        warningMsg.append("<p style='color:#d32f2f; font-weight:bold; margin:15px 0 0 0;'>");
        warningMsg.append("是否確認結束營業？</p></div></html>");

        Object[] options = {" 確認打烊", " 取消"};
        int choice = JOptionPane.showOptionDialog(
                this,
                warningMsg.toString(),
                "未結賬訂單提醒",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1] // 默認選「取消」
        );

        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * 显示排队管理对话框
     * 功能：提供增加/编辑/删除顾客组的队列管理操作
     * 逻辑：根据营业状态和餐桌空闲情况动态控制选项可用性
     * 流程：收集用户输入 → 验证参数 → 调用控制器处理
     */
    public void showQueueManagementDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel callNumberLabel = new JLabel("排队号码：");
        JTextField callNumberField = new JTextField(10);
        JLabel customerCountLabel = new JLabel("客户数量：");
        JTextField customerCountField = new JTextField(10);

        // 添加新的选项
        JCheckBox addGroupCheckbox = new JCheckBox("增加顾客组");
        JCheckBox editGroupSizeCheckbox = new JCheckBox("编辑顾客组人数");
        JCheckBox deleteGroupCheckbox = new JCheckBox("删除顾客组");

        //  检查餐厅是否营业
        boolean isOpenForBusiness = controller != null && controller.service != null && controller.service.isOpenForBusiness();

        //只检查是否有VACANT状态的主餐桌
        boolean hasVacantTables = false;
        if (controller != null && controller.service != null) {
            for (Tables table : controller.service.getAllTables()) {
                // 跳过子桌，只检查主餐桌
                if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
                    continue;
                }
                // 仅检查VACANT状态（完全空闲的餐桌）
                if (table.getStatus() == Tables.TableStatus.VACANT) {
                    hasVacantTables = true;
                    break;
                }
            }
        }

        //  检查是否有排队顾客
        boolean hasWaitingCustomers = controller != null && controller.hasWaitingCustomers();

        // 只有当没有VACANT餐桌且餐厅在营业时才启用"增加顾客组"
        boolean canAddGroups = !hasVacantTables && isOpenForBusiness;
        addGroupCheckbox.setEnabled(canAddGroups);

        // 设置精确的工具提示
        if (!isOpenForBusiness) {
            addGroupCheckbox.setToolTipText("餐厅已结束营业，不能添加新顾客组");
        } else if (canAddGroups) {
            addGroupCheckbox.setToolTipText("没有空闲餐桌，新顾客必须加入队列");
        } else {
            addGroupCheckbox.setToolTipText("有空闲餐桌，新顾客应直接入座，无需加入队列");
        }

        // 单选按钮组（保持互斥选择）
        ButtonGroup group = new ButtonGroup();
        group.add(addGroupCheckbox);
        group.add(editGroupSizeCheckbox);
        group.add(deleteGroupCheckbox);

        //  修正的智能默认选择逻辑 - 考虑营业状态
        if (!isOpenForBusiness) {
            // 餐厅不营业：不选择任何选项
            group.clearSelection();
        } else if (canAddGroups) {
            // 有营业且没有空闲餐桌：默认选择"增加顾客组"
            addGroupCheckbox.setSelected(true);
        } else if (hasWaitingCustomers) {
            // 有营业、有空闲餐桌且有排队顾客：默认选择"编辑顾客组人数"
            editGroupSizeCheckbox.setSelected(true);
        } else {
            // 有营业、有空闲餐桌且无排队顾客：不选择任何选项
            group.clearSelection();
        }

        // 复选框监听器 - 全面控制UI状态
        ActionListener checkboxListener = e -> {
            boolean isAddSelected = addGroupCheckbox.isSelected();
            boolean isEditSelected = editGroupSizeCheckbox.isSelected();
            boolean isDeleteSelected = deleteGroupCheckbox.isSelected();

            // 控制排队号码字段
            callNumberField.setEnabled(!isAddSelected);
            callNumberField.setEditable(!isAddSelected);
            callNumberLabel.setEnabled(!isAddSelected);

            // 控制客户数量字段
            customerCountField.setEnabled(isAddSelected || isEditSelected);
            customerCountField.setEditable(isAddSelected || isEditSelected);
            customerCountLabel.setEnabled(isAddSelected || isEditSelected);

            // 清空不必要的字段
            if (isAddSelected) {
                callNumberField.setText("");
            }
            if (!isAddSelected && !isEditSelected) {
                customerCountField.setText("");
            }
        };

        addGroupCheckbox.addActionListener(checkboxListener);
        editGroupSizeCheckbox.addActionListener(checkboxListener);
        deleteGroupCheckbox.addActionListener(checkboxListener);

        // 初始状态设置 - 必须在设置监听器后调用
        checkboxListener.actionPerformed(null);

        // 添加状态说明标签（增强用户体验）
        JLabel statusLabel = new JLabel();
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        //  根据营业状态更新状态标签
        if (!isOpenForBusiness) {
            statusLabel.setText(" 餐厅已结束营业，不能添加新顾客");
            statusLabel.setForeground(new Color(180, 0, 0)); // 深红色
        } else if (canAddGroups) {
            statusLabel.setText(" 所有餐桌已满，新顾客必须加入队列");
            statusLabel.setForeground(new Color(180, 0, 0)); // 深红色
        } else {
            statusLabel.setText(" 有空闲餐桌，新顾客应直接入座");
            statusLabel.setForeground(new Color(0, 120, 0)); // 深绿色
        }

        // 布局设置
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        panel.add(callNumberLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(callNumberField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(customerCountLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(customerCountField, gbc);

        // 添加状态说明标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(statusLabel, gbc);

        // 添加新选项到布局
        gbc.gridy = 3;
        panel.add(addGroupCheckbox, gbc);

        gbc.gridy = 4;
        panel.add(editGroupSizeCheckbox, gbc);

        gbc.gridy = 5;
        panel.add(deleteGroupCheckbox, gbc);

        // 显示对话框
        int result = JOptionPane.showConfirmDialog(this, panel, "排队管理", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            boolean isAdd = addGroupCheckbox.isSelected();
            boolean isEdit = editGroupSizeCheckbox.isSelected();
            boolean isDelete = deleteGroupCheckbox.isSelected();

            // 验证至少选择了一个操作
            if (!isAdd && !isEdit && !isDelete) {
                showError("请选择一个操作！");
                return;
            }

            //  额外检查：如果餐厅不营业且用户尝试添加顾客组
            if (!isOpenForBusiness && isAdd) {
                showError("餐厅已结束营业，无法添加新顾客组！");
                return;
            }

            try {
                int callNumber = -1;
                int customerCount = -1;

                // 仅当不是"增加顾客组"时才验证排队号码
                if (!isAdd) {
                    String callNumberStr = callNumberField.getText().trim();
                    if (callNumberStr.isEmpty()) {
                        showError("请输入排队号码！");
                        return;
                    }
                    callNumber = Integer.parseInt(callNumberStr);
                }

                // 仅当是"增加"或"编辑"时才验证客户数量
                if (isAdd || isEdit) {
                    String customerCountStr = customerCountField.getText().trim();
                    if (customerCountStr.isEmpty()) {
                        showError("请填写客户数量！");
                        return;
                    }
                    customerCount = Integer.parseInt(customerCountStr);
                    if (customerCount <= 0) {
                        showError("客户数量必须大于0！");
                        return;
                    }
                }

                // 通过控制器处理队列管理操作
                if (controller != null) {
                    controller.handleQueueManagementAction(callNumber, customerCount, isAdd, isEdit, isDelete);
                }
            } catch (NumberFormatException ex) {
                showError("排队号码和客户数量必须是有效数字！");
            }
        }
    }

    /**
     * 显示选择餐桌对话框
     * 功能：让用户选择操作模式（新顾客入座/队列分配）、输入餐桌信息、选择餐桌类型和操作类型
     * 支持：普通桌/合并桌/聚餐桌/拼桌等多种场景，包含营业状态和队列状态的智能校验
     */
    public void showSelectTableDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // ===== 1. 操作模式选择（顶部！）=====
        gbc.gridy = 0;
        JLabel modeLabel = new JLabel("📌 操作模式:");
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(modeLabel, gbc);

        gbc.gridy = 1;
        JRadioButton newCustomerRadio = new JRadioButton("新顾客入座", true);
        JRadioButton fromQueueRadio = new JRadioButton("从队列分配顾客");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(newCustomerRadio);
        modeGroup.add(fromQueueRadio);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modePanel.add(newCustomerRadio);
        modePanel.add(fromQueueRadio);
        panel.add(modePanel, gbc);

        // ===== 2. 顾客组叫号输入（仅队列模式显示）=====
        gbc.gridy = 2;
        JLabel callNumberLabel = new JLabel("排隊號（如 5）:");
        panel.add(callNumberLabel, gbc);

        gbc.gridy = 3;
        JTextField callNumberField = new JTextField(12);
        panel.add(callNumberField, gbc);

        // 初始隐藏（默认新顾客模式）
        callNumberLabel.setVisible(false);
        callNumberField.setVisible(false);

        // ===== 3. 餐桌编号 =====
        gbc.gridy = 4;
        JLabel tableIdLabel = new JLabel("餐桌编号（如 7）:");
        panel.add(tableIdLabel, gbc);

        gbc.gridy = 5;
        JTextField tableIdField = new JTextField(12);
        panel.add(tableIdField, gbc);

        // ===== 4. 人数输入（仅新顾客模式）=====
        gbc.gridy = 6;
        JLabel peopleCountLabel = new JLabel("人数:");
        panel.add(peopleCountLabel, gbc);

        gbc.gridy = 7;
        JTextField peopleCountField = new JTextField(12);
        panel.add(peopleCountField, gbc);

        // ===== 5. 餐桌容量选项 =====
        gbc.gridy = 8;
        JLabel tableTypeLabel = new JLabel("餐桌容量:");
        tableTypeLabel.setFont(tableTypeLabel.getFont().deriveFont(Font.BOLD));
        panel.add(tableTypeLabel, gbc);

        gbc.gridy = 9;
        JCheckBox twoSeatOption = new JCheckBox("2 人桌（1-2 人）", true);
        JCheckBox fourSeatOption = new JCheckBox("4 人桌（1-4 人）");
        JCheckBox sixSeatOption = new JCheckBox("6 人桌（4-6 人）");
        ButtonGroup seatGroup = new ButtonGroup();
        seatGroup.add(twoSeatOption);
        seatGroup.add(fourSeatOption);
        seatGroup.add(sixSeatOption);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        typePanel.add(twoSeatOption);
        typePanel.add(fourSeatOption);
        typePanel.add(sixSeatOption);
        panel.add(typePanel, gbc);

        // ===== 6. 餐桌操作类型 =====
        gbc.gridy = 10;
        JLabel operationLabel = new JLabel(" 餐桌操作类型:");
        operationLabel.setFont(operationLabel.getFont().deriveFont(Font.BOLD));
        panel.add(operationLabel, gbc);

        gbc.gridy = 11;
        JCheckBox addGuestsOption = new JCheckBox("往桌子添加客人", true);
        JCheckBox mergeOption = new JCheckBox("合并桌子（2 张）");
        JCheckBox shareOption = new JCheckBox("共享餐桌（拼桌）");
        JCheckBox groupedOption = new JCheckBox("聚餐桌（3张或以上）");

        // 【修改】使用 GridLayout 实现 2 行 2 列布局
        JPanel operationPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        operationPanel.add(addGuestsOption);
        operationPanel.add(mergeOption);
        operationPanel.add(shareOption);
        operationPanel.add(groupedOption);
        panel.add(operationPanel, gbc);

        // ===== 交互逻辑 =====

        fromQueueRadio.addActionListener(e -> {
            boolean isQueueMode = fromQueueRadio.isSelected();
            callNumberLabel.setVisible(isQueueMode);
            callNumberField.setVisible(isQueueMode);
            peopleCountLabel.setVisible(!isQueueMode && !groupedOption.isSelected());
            peopleCountField.setVisible(!isQueueMode && !groupedOption.isSelected());
            twoSeatOption.setEnabled(true);
            fourSeatOption.setEnabled(true);
            sixSeatOption.setText("6人桌 ( 4-6人）");
            tableIdLabel.setText(isQueueMode ? "分配到餐桌编号:" : "餐桌编号（如 7）:");
            // 【新增】从队列分配时隐藏聚餐桌选项
            if (isQueueMode) {
                groupedOption.setVisible(false);
                groupedOption.setEnabled(false); // 禁用防止被选中

                // 如果之前选中了聚餐桌，强制取消选中，这会触发 groupedOption 的监听器进行状态清理
                if (groupedOption.isSelected()) {
                    groupedOption.setSelected(false);

                    // 清理后，如果其他操作类型（添加客人/合并/拼桌）都没选，默认选中“往桌子添加客人”
                    // 避免操作类型区域看起来是空的（虽然业务上队列分配通常不追加，但为了界面完整性）
                    if (!addGuestsOption.isSelected() && !mergeOption.isSelected() && !shareOption.isSelected()) {
                        addGuestsOption.setSelected(true);
                    }
                }
            } else {
                // 切回新顾客模式时，恢复聚餐桌选项
                groupedOption.setVisible(true);
                groupedOption.setEnabled(true);
            }
            panel.revalidate();
            panel.repaint();
        });

        newCustomerRadio.addActionListener(e -> {
            boolean isNewMode = newCustomerRadio.isSelected();
            callNumberLabel.setVisible(!isNewMode);
            callNumberField.setVisible(!isNewMode);
            peopleCountLabel.setVisible(isNewMode && !groupedOption.isSelected());
            peopleCountField.setVisible(isNewMode && !groupedOption.isSelected());
            // 【新增】新顾客入座时显示聚餐桌选项
            groupedOption.setVisible(true);
            groupedOption.setEnabled(true);

            tableIdLabel.setText("餐桌编号（如 7）:");
            if (isNewMode) {
                addGuestsOption.setToolTipText(
                        "将新顾客追加到已有顾客的餐桌（同一顾客组增加人数） " +
                                "例如：2 人桌已有 1 人，再加 1 人变为 2 人"
                );
            }
            panel.revalidate();
            panel.repaint();
        });

        addGuestsOption.addActionListener(e -> {
            if (addGuestsOption.isSelected()) {
                mergeOption.setSelected(false);
                shareOption.setSelected(false);
                groupedOption.setSelected(false);
                sixSeatOption.setVisible(true);
                twoSeatOption.setEnabled(true);
                fourSeatOption.setEnabled(true);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("2 人桌（1-2 人）");
                fourSeatOption.setText("4 人桌（1-4 人）");
                sixSeatOption.setText("6 人桌（4-6人）");

            }
            panel.revalidate();
            panel.repaint();
        });

        mergeOption.addActionListener(e -> {
            boolean isMerge = mergeOption.isSelected();
            if (isMerge) {
                twoSeatOption.setEnabled(true);
                fourSeatOption.setEnabled(true);
                sixSeatOption.setEnabled(true);

                addGuestsOption.setSelected(false);
                shareOption.setSelected(false);
                groupedOption.setSelected(false);
                sixSeatOption.setSelected(false);
                sixSeatOption.setVisible(true);
                twoSeatOption.setSelected(true);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("合并 2 人桌（3-4 人）");
                fourSeatOption.setText("合并 4 人桌（5-8 人）");
                sixSeatOption.setText("合并 6 人桌（9-12 人）");
            } else {
                sixSeatOption.setVisible(true);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("2 人桌（1-2 人）");
                fourSeatOption.setText("4 人桌（1-4 人）");
                sixSeatOption.setText("6 人桌 ( 4-6 人) ");
                twoSeatOption.setSelected(true);
            }
            panel.revalidate();
            panel.repaint();
        });

        shareOption.addActionListener(e -> {
            if (shareOption.isSelected()) {
                // 【关键修复】显式启用 2人桌和 4人桌
                // 防止因为 groupedOption 的取消逻辑未及时生效，导致按钮依然变灰
                twoSeatOption.setEnabled(true);
                fourSeatOption.setEnabled(true);

                addGuestsOption.setSelected(false);
                mergeOption.setSelected(false);
                groupedOption.setSelected(false);
                sixSeatOption.setSelected(false);
                sixSeatOption.setVisible(false);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("2 人桌（1-2 人）");
                fourSeatOption.setText("4 人桌（1-4 人）");
            } else {
                sixSeatOption.setVisible(true);
            }
            panel.revalidate();
            panel.repaint();
        });

        groupedOption.addActionListener(e -> {
            boolean isGrouped = groupedOption.isSelected();
            if (isGrouped) {
                // 互斥其他操作
                addGuestsOption.setSelected(false);
                mergeOption.setSelected(false);
                shareOption.setSelected(false);

                // 聚餐桌只能用 6 人桌
                sixSeatOption.setVisible(true);
                sixSeatOption.setSelected(true);
                sixSeatOption.setEnabled(true);
                twoSeatOption.setEnabled(false);
                fourSeatOption.setEnabled(false);

                // 隐藏普通人数输入
                peopleCountLabel.setVisible(false);
                peopleCountField.setVisible(false);

                // 更新标签提示
                tableIdLabel.setText("主餐桌编号（如 13）:");
                tableIdField.setToolTipText("输入聚餐桌组中编号最小的餐桌号");

                // 【修改】清空人数输入，由系统根据餐桌自动计算
                peopleCountField.setText("");

                sixSeatOption.setText("6 人桌 ");
                twoSeatOption.setText("2 人桌（1-2 人）");
                fourSeatOption.setText("4 人桌（1-4 人）");

                groupedOption.setToolTipText(
                        "聚餐桌规则：\n" +
                                "• 必须使用 6 人桌，数量 3-6 张（通过连续桌号自动识别）\n" +
                                "• 接待 9 人以上大型顾客组\n" +
                                "• 桌号必须连续（如 13,14,15）\n" +
                                "• 主桌为编号最小的餐桌，系统自动识别关联桌"
                );
            } else {
                // 恢复普通模式
                twoSeatOption.setEnabled(true);
                fourSeatOption.setEnabled(true);
                sixSeatOption.setVisible(true);
                peopleCountLabel.setVisible(newCustomerRadio.isSelected());
                peopleCountField.setVisible(newCustomerRadio.isSelected());
                tableIdLabel.setText("餐桌编号（如 7）:");
                tableIdField.setToolTipText("");
                sixSeatOption.setText("6 人桌(1-6人) ");
            }
            panel.revalidate();
            panel.repaint();
        });

        // ===== 创建对话框 =====
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(this, " 选择餐桌");
        dialog.setSize(580, 580);
        dialog.setLocationRelativeTo(null);

        // =====  仅禁用"新顾客入座"模式（关闭营业时）=====
        if (controller != null && controller.service != null && !controller.service.isOpenForBusiness()) {
            newCustomerRadio.setEnabled(false);
            newCustomerRadio.setToolTipText(" 餐厅已结束营业，不能添加新顾客");
            if (newCustomerRadio.isSelected() && fromQueueRadio.isEnabled()) {
                fromQueueRadio.setSelected(true);
                callNumberLabel.setVisible(true);
                callNumberField.setVisible(true);
                peopleCountLabel.setVisible(false);
                peopleCountField.setVisible(false);
                tableIdLabel.setText("分配到餐桌编号:");
            }
            panel.revalidate();
            panel.repaint();
        }

        // =====  关键修复 1：检测所有主桌是否被占用 =====
        boolean allMainTablesOccupied = false;
        if (controller != null && controller.service != null) {
            List<Tables> tables = controller.service.getAllTables();
            allMainTablesOccupied = tables.stream()
                    .filter(table -> table.getTableType() == Tables.TableType.MAIN)
                    .allMatch(table -> table.getStatus() == Tables.TableStatus.OCCUPIED);//步骤2：检查这些主桌是否【全部】都是占用状态
        }
        if (allMainTablesOccupied) {
            mergeOption.setEnabled(false);
            groupedOption.setEnabled(false);
            mergeOption.setSelected(false);
            shareOption.setSelected(false);
            groupedOption.setSelected(false);
            mergeOption.setToolTipText(" 所有主桌已被占用，无法合并");
            groupedOption.setToolTipText("所有主桌已被占用，无法创建聚餐桌");
            operationPanel.setToolTipText(" 所有主桌已被占用，合并/聚餐桌操作不可用");
            panel.revalidate();
            panel.repaint();
        }

        // =====  关键修复 2：检测队列是否为空（纯内存检查）=====
        boolean allQueuesEmpty = true;
        if (controller != null && controller.service != null) {
            Queue<CustomerGroup> q2 = controller.service.getQueue2Seat();
            Queue<CustomerGroup> q4 = controller.service.getQueue4Seat();
            Queue<CustomerGroup> q6 = controller.service.getQueue6Seat();
            allQueuesEmpty = q2.isEmpty() && q4.isEmpty() && q6.isEmpty();
        }
        if (allQueuesEmpty) {
            fromQueueRadio.setEnabled(false);
            fromQueueRadio.setSelected(false);
            newCustomerRadio.setSelected(true);
            callNumberLabel.setVisible(false);
            callNumberField.setVisible(false);
            peopleCountLabel.setVisible(true);
            peopleCountField.setVisible(true);
            tableIdLabel.setText("餐桌编号（如 7）:");
            fromQueueRadio.setToolTipText(" 当前无排队顾客，无法从队列分配");
            modePanel.setToolTipText(" 所有队列为空，仅支持新顾客入座");
            panel.revalidate();
            panel.repaint();
        }

        // =====  关闭营业且无队列时禁用"确定"按钮 =====
        boolean isClosed = (controller != null && controller.service != null &&
                !controller.service.isOpenForBusiness());
        boolean queuesEmpty = allQueuesEmpty;

        // 显示对话框（模态阻塞）
        dialog.setVisible(true);

        //  关键：对话框关闭后，如果用户点了"确定"但条件不满足 → 拦截并提示
        if (isClosed && queuesEmpty &&
                optionPane.getValue() != null &&
                optionPane.getValue().equals(JOptionPane.OK_OPTION)) {
            showError(" 餐厅已打烊且无排队顾客，无法执行此操作！");
            return;
        }

        // ===== 处理结果 =====
        if (optionPane.getValue() != null &&
                optionPane.getValue().equals(JOptionPane.OK_OPTION)) {

            String tableIdInput = tableIdField.getText().trim();
            if (tableIdInput.isEmpty()) {
                showError("请输入餐桌编号！");
                return;
            }

            boolean isFromQueue = fromQueueRadio.isSelected();
            int peopleCount = 0;
            int callNumber = 0;

            if (isFromQueue) {
                String callNumberInput = callNumberField.getText().trim();
                if (callNumberInput.isEmpty()) {
                    showError("请输入顾客组叫号！");
                    return;
                }
                try {
                    callNumber = Integer.parseInt(callNumberInput);
                    if (callNumber <= 0) {
                        showError("叫号必须大于 0！");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    showError("叫号必须为整数！");
                    return;
                }
            } else {
                // 聚餐桌模式下人数由系统根据餐桌自动计算，否则读取输入
                if (!groupedOption.isSelected()) {
                    String peopleInput = peopleCountField.getText().trim();
                    if (peopleInput.isEmpty()) {
                        showError("请输入人数！");
                        return;
                    }
                    try {
                        peopleCount = Integer.parseInt(peopleInput);
                        if (peopleCount <= 0) {
                            showError("人数必须大于 0！");
                            return;
                        }
                    } catch (NumberFormatException ex) {
                        showError("人数必须为整数！");
                        return;
                    }
                }
                //  聚餐桌：人数留空，由 Service 层根据餐桌自动计算
            }

            boolean isMerge = mergeOption.isSelected();
            boolean isTwoSeat = twoSeatOption.isSelected();
            boolean isFourSeat = fourSeatOption.isSelected();
            boolean isSixSeat = sixSeatOption.isSelected();
            boolean isAddGuests = addGuestsOption.isSelected();
            boolean isShare = shareOption.isSelected();
            boolean isGrouped = groupedOption.isSelected();

            //  调用 Controller（聚餐桌桌子数量由餐桌编号自动识别，传 0 表示自动）
            controller.handleManualTableAssignment(
                    tableIdInput,
                    peopleCount,
                    isFromQueue,
                    callNumber,
                    isMerge,
                    isTwoSeat,
                    isFourSeat,
                    isSixSeat,
                    isAddGuests,
                    isShare,
                    isGrouped,
                    0  //  聚餐桌桌子数量：传 0 表示由系统根据餐桌编号自动识别
            );
        }
    }

    /**
     *  封装确认对话框（供 Controller 调用）
     */
    public int showConfirmDialog(String message, String title) {
        return JOptionPane.showConfirmDialog(
                this,                    // 父窗口，保证模态正确
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
    }

    /**
     *  显示"追加入座"确认对话框
     * @param displayId 餐桌编号
     * @param currentSize 当前人数
     * @param remainingSeats 剩余座位
     * @param additionalPeople 要追加的人数
     * @return true=用户确认，false=用户取消
     */
    public boolean showAddGuestsConfirmationDialog(
            String displayId, int currentSize, int remainingSeats, int additionalPeople) {

        String message = String.format(
                "<html><div style='padding:10px; font-family:Microsoft YaHei;'>" +
                        "<b> 确认追加入座？</b><br><br>" +
                        "餐桌 <b>#%s</b> 已有顾客正在用餐。<br>" +
                        "当前人数：<b>%d 人</b><br>" +
                        "剩余座位：<b>%d 个</b><br><br>" +
                        "要追加 <b>%d 位</b> 新顾客到该餐桌吗？<br>" +
                        "<font color='#666'><small>请确认是否为同一组朋友</small></font>" +
                        "</div></html>",
                displayId, currentSize, remainingSeats, additionalPeople
        );

        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "确认追加入座",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }

    /**
     * 显示营业报表对话框
     * 包含三个标签页：
     * 1. 营业总览 - 按日期查询营业额/顾客数等数据
     * 2. 菜品销售分析 - 按季度统计菜品销量和销售额
     * 3. 取消预约统计 - 查看没收定金记录
     *
     * 支持导出Excel和打印功能
     */
    public void showBusinessReportDialog() {
        JDialog reportDialog = new JDialog(this, "营业报表统计", true);
        reportDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 创建主滚动面板
        JScrollPane mainScrollPane = new JScrollPane();
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.getViewport().setBackground(Color.WHITE);

        // 创建内容面板
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("微软雅黑", Font.BOLD, 12));
        tabbedPane.setPreferredSize(new Dimension(1050, 500)); // 设置选项卡面板的首选大小

        // 1. 营业总览面板 - 现在包含统计范围选择
        JPanel overviewPanel = createBusinessOverviewPanel();
        tabbedPane.addTab("营业总览", overviewPanel);

        // 2. 菜品销售分析面板 - 为了兼容性保留日期选择器参数
        JPanel dishAnalysisPanel = createDishAnalysisPanel(null, null, null, null, statusLabel);
        tabbedPane.addTab("菜品销售分析", dishAnalysisPanel);

        // 3. 取消预约统计面板
        JPanel forfeitedPanel = createForfeitedDepositPanel();
        tabbedPane.addTab("取消预约统计", forfeitedPanel);

        // 状态栏
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusLabel = new JLabel("就绪. 就緒.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        // 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton exportButton = new JButton("导出Excel");
        exportButton.setPreferredSize(new Dimension(100, 30));
        JButton printButton = new JButton("打印报表");
        printButton.setPreferredSize(new Dimension(100, 30));
        JButton closeButton = new JButton("关闭");
        closeButton.setPreferredSize(new Dimension(80, 30));
        buttonPanel.add(exportButton);
        buttonPanel.add(printButton);
        buttonPanel.add(closeButton);

        // 底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);

        // 将各组件添加到内容面板
        JScrollPane tabScrollPane = new JScrollPane(tabbedPane);
        tabScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tabScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentPanel.add(tabScrollPane, BorderLayout.CENTER);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 设置主滚动面板的内容
        mainScrollPane.setViewportView(contentPanel);

        // 设置对话框内容
        reportDialog.add(mainScrollPane);

        // 设置合理的初始大小，同时保留滚动功能
        reportDialog.setSize(1150, 750);

        // 导出按钮
        exportButton.addActionListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 0) { // 营业总览
                JTable reportTable = getTableFromPanel(overviewPanel);
                if (reportTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) reportTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                exportReportToExcel(reportTable);

            } else if (selectedIndex == 1) { // 菜品销售分析
                JTable dishTable = getDishTableFromPanel(dishAnalysisPanel);
                if (dishTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) dishTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                exportDishSalesToExcel(dishTable);

            } else if (selectedIndex == 2) { //  新增：取消预约统计
                JTable forfeitedTable = getForfeitedTableFromPanel(forfeitedPanel);
                if (forfeitedTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) forfeitedTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                exportForfeitedDepositsToExcel(forfeitedTable);
            }
        });

        // 打印按钮
        printButton.addActionListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 0) { // 营业总览
                JTable reportTable = getTableFromPanel(overviewPanel);
                if (reportTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) reportTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可打印", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    boolean complete = reportTable.print();
                    if (complete) {
                        showTimeMessage("打印任务已发送到打印机", "操作成功");
                    } else {
                        JOptionPane.showMessageDialog(reportDialog, "打印被取消", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(reportDialog, "打印失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }

            } else if (selectedIndex == 1) { // 菜品销售分析
                JTable dishTable = getDishTableFromPanel(dishAnalysisPanel);
                if (dishTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) dishTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可打印", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    boolean complete = dishTable.print();
                    if (complete) {
                        showTimeMessage("打印任务已发送到打印机", "操作成功");
                    } else {
                        JOptionPane.showMessageDialog(reportDialog, "打印被取消", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(reportDialog, "打印失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }

            } else if (selectedIndex == 2) { //  新增：取消预约统计
                JTable forfeitedTable = getForfeitedTableFromPanel(forfeitedPanel);
                if (forfeitedTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) forfeitedTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可打印", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    boolean complete = forfeitedTable.print();
                    if (complete) {
                        showTimeMessage("打印任务已发送到打印机", "操作成功");
                    } else {
                        JOptionPane.showMessageDialog(reportDialog, "打印被取消", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(reportDialog, "打印失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        closeButton.addActionListener(e -> reportDialog.dispose());

        reportDialog.setLocationRelativeTo(this);
        reportDialog.setVisible(true);
    }

    /**
     * 递归查找容器内指定名称的组件
     *
     */
    private Component findComponentByName(Container container, String name) {
        // 遍歷當前容器下的所有子組件
        for (int i = 0; i < container.getComponentCount(); i++) {
            Component comp = container.getComponent(i);
            // 判斷當前組件名稱是否與目標名稱一致
            if (name.equals(comp.getName())) {
                return comp;
            }
            // 若當前組件也是容器，則需要繼續深入查找（遞歸）
            if (comp instanceof Container) {
                // 遞歸調用自身，在子容器中查找目標名稱
                Component found = findComponentByName((Container) comp, name);
                // 檢查遞歸結果是否找到
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 创建营业概览分析面板
     *
     * 功能说明：
     * • 提供单日统计和日期范围统计两种模式选择
     * • 支持日期选择器输入查询条件
     * • 显示营业数据表格（日期/营业额/顾客数/客单价/外卖订单数）
     * • 自动生成营业额趋势图和顾客数量统计图
     * • 表格支持交错行颜色和总计行高亮显示
     * • 后台异步加载数据，避免界面卡顿
     * • 操作成功/失败时显示浮动提示消息
     */
    private JPanel createBusinessOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 保持原有结构不变，只在顶部添加统计范围面板 =====
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(BorderFactory.createTitledBorder("选择统计范围"));

        // 模式选择
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButton singleDayRadio = new JRadioButton("单日统计", true);
        JRadioButton rangeRadio = new JRadioButton("日期范围统计", false);
        modeGroup.add(singleDayRadio);
        modeGroup.add(rangeRadio);

        controlPanel.add(singleDayRadio);
        controlPanel.add(new JLabel("日期:"));

        // 日期选择器
        JDateChooser singleDayChooser = new JDateChooser(new java.util.Date());
        singleDayChooser.setDateFormatString("yyyy-MM-dd");
        singleDayChooser.setPreferredSize(new Dimension(120, 28));
        controlPanel.add(singleDayChooser);

        controlPanel.add(rangeRadio);
        controlPanel.add(new JLabel("从:"));

        JDateChooser startDateChooser = new JDateChooser();
        startDateChooser.setDateFormatString("yyyy-MM-dd");
        startDateChooser.setPreferredSize(new Dimension(120, 28));
        startDateChooser.setEnabled(false);
        controlPanel.add(startDateChooser);

        controlPanel.add(new JLabel("到:"));

        JDateChooser endDateChooser = new JDateChooser(new java.util.Date());
        endDateChooser.setDateFormatString("yyyy-MM-dd");
        endDateChooser.setPreferredSize(new Dimension(120, 28));
        endDateChooser.setEnabled(false);
        controlPanel.add(endDateChooser);

        // 模式切换监听器
        singleDayRadio.addActionListener(e -> {
            singleDayChooser.setEnabled(true);
            startDateChooser.setEnabled(false);
            endDateChooser.setEnabled(false);
            singleDayChooser.requestFocus();
        });

        rangeRadio.addActionListener(e -> {
            singleDayChooser.setEnabled(false);
            startDateChooser.setEnabled(true);
            endDateChooser.setEnabled(true);
            startDateChooser.requestFocus();
        });

        // 生成报表按钮
        JButton generateButton = new JButton("生成报表");
        generateButton.setPreferredSize(new Dimension(100, 30));
        generateButton.setFont(new Font("微软雅黑", Font.BOLD, 12));
        controlPanel.add(generateButton);
        // ===== 统计范围面板结束 =====

        // 创建表格面板 - 保持原有代码不变
        String[] columnNames = {"日期", "总营业额(元)", "顾客总数", "平均客单价(元)", "外卖订单数量"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 表格不可编辑
            }
        };

        JTable reportTable = new JTable(tableModel);
        reportTable.setRowHeight(25);
        reportTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        reportTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        reportTable.setFillsViewportHeight(true);

        // 设置表格列宽
        reportTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        reportTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        reportTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        reportTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        reportTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        // 设置表格渲染器
        reportTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (row == table.getRowCount() - 1 && value != null && value.toString().contains("总计")) {
                    // 总计行高亮显示
                    c.setFont(new Font("微软雅黑", Font.BOLD, 12));
                    c.setBackground(new Color(220, 230, 255));
                } else if (row % 2 == 0) {
                    // 交错行颜色
                    c.setBackground(new Color(230, 240, 255));  // 更明显的浅蓝色
                } else {
                    c.setBackground(Color.WHITE);
                }
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                return c;
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(reportTable);
        tableScrollPane.setName("reportTableScrollPane");
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setPreferredSize(new Dimension(950, 250)); // 保持原有高度

        // 图表区域 - 保持原有代码不变
        JPanel chartPanel = new JPanel();
        chartPanel.setBorder(BorderFactory.createTitledBorder("数据可视化"));
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));
        chartPanel.setPreferredSize(new Dimension(950, 300));
        chartPanel.setMinimumSize(new Dimension(400, 250));
        chartPanel.setName("chartPanel");

        // 将各面板添加到主面板 - 保持原有结构
        panel.add(controlPanel, BorderLayout.NORTH); // 添加统计范围面板在顶部
        panel.add(tableScrollPane, BorderLayout.CENTER);
        panel.add(chartPanel, BorderLayout.SOUTH);

        // 添加事件处理 - 保持原有功能
        generateButton.addActionListener(e -> {
            try {
                if (singleDayRadio.isSelected()) {
                    java.util.Date selectedDate = singleDayChooser.getDate();
                    if (selectedDate == null) {
                        JOptionPane.showMessageDialog(panel, "请选择一个日期");
                        return;
                    }
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(selectedDate);

                    statusLabel.setText("正在加载单日数据...");
                    generateButton.setEnabled(false);

                    SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                        @Override
                        protected List<Map<String, Object>> doInBackground() throws Exception {
                            return controller.getDailyBusinessReport(dateStr);
                        }

                        @Override
                        protected void done() {
                            try {
                                List<Map<String, Object>> reportData = get();
                                displayReportData(reportData, reportTable, tableModel, chartPanel);
                                statusLabel.setText("单日报表加载完成");
                                showTimeMessage("单日报表生成成功", "操作成功");
                            } catch (Exception ex) {
                                statusLabel.setText("加载失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(panel, "生成单日报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            } finally {
                                generateButton.setEnabled(true);
                            }
                        }
                    };
                    worker.execute();

                } else {
                    java.util.Date startDate = startDateChooser.getDate();
                    java.util.Date endDate = endDateChooser.getDate();
                    if (startDate == null || endDate == null) {
                        JOptionPane.showMessageDialog(panel, "请选择开始日期和结束日期");
                        return;
                    }
                    if (startDate.after(endDate)) {
                        JOptionPane.showMessageDialog(panel, "开始日期不能晚于结束日期");
                        return;
                    }
                    String startDateStr = new SimpleDateFormat("yyyy-MM-dd").format(startDate);
                    String endDateStr = new SimpleDateFormat("yyyy-MM-dd").format(endDate);

                    statusLabel.setText("正在加载日期范围数据...");
                    generateButton.setEnabled(false);

                    SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                        @Override
                        protected List<Map<String, Object>> doInBackground() throws Exception {
                            return controller.getDateRangeBusinessReport(startDateStr, endDateStr);
                        }

                        @Override
                        protected void done() {
                            try {
                                List<Map<String, Object>> reportData = get();
                                displayReportData(reportData, reportTable, tableModel, chartPanel);
                                statusLabel.setText("日期范围报表加载完成");
                                showTimeMessage("日期范围报表生成成功", "操作成功");
                            } catch (Exception ex) {
                                statusLabel.setText("加载失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(panel, "生成日期范围报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            } finally {
                                generateButton.setEnabled(true);
                            }
                        }
                    };
                    worker.execute();
                }
            } catch (Exception ex) {
                statusLabel.setText("错误: " + ex.getMessage());
                JOptionPane.showMessageDialog(panel, "生成报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        return panel;
    }

    /**
     * 创建菜品销售分析面板
     * 功能：提供菜品销售数据的查询、表格展示和图表可视化
     *
     * 主要模块：
     * 1. 顶部控制面板：年份/季度/类别/数量/图表类型选择器 + 加载按钮
     * 2. 中部数据表格：显示菜品编号、名称、销量、销售额、平均单价
     * 3. 底部图表区域：支持柱状图/扇形图双视图展示销售趋势
     *
     * 交互功能：
     * - 年份输入支持手动输入 + 实时格式验证
     * - 图表类型可实时切换（柱状图/扇形图）
     * - 显示数量可限制（全部/前10/25/50条）
     * - 菜品类别可筛选（全部/A/B/C/D类）
     * - 分类变更自动触发数据重载
     *
     * 数据加载：
     * - 后台线程异步加载（SwingWorker）
     * - 加载时显示进度提示
     * - 空数据/异常时友好提示
     *
     */
    private JPanel createDishAnalysisPanel(JDateChooser singleDayChooser, JDateChooser startDateChooser,
                                           JDateChooser endDateChooser, JRadioButton rangeRadio,
                                           JLabel panelStatusLabel) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 1. 顶部控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(BorderFactory.createEtchedBorder());

        // 年份选择 - 修复：允许手动输入
        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        yearPanel.add(new JLabel("年份:"));

        // 创建可编辑的年份组合框
        JComboBox<String> yearCombo = new JComboBox<>();
        yearCombo.setEditable(true);

        // 添加年份选项
        List<String> years = controller.getAvailableYearsForDishSales();
        for (String year : years) {
            yearCombo.addItem(year);
        }

        // 添加当前年份（如果不在列表中）
        String currentYear = String.valueOf(java.time.LocalDate.now().getYear());
        if (!years.contains(currentYear)) {
            yearCombo.addItem(currentYear);
        }

        // 设置默认选择
        yearCombo.setSelectedItem(currentYear);
        yearCombo.setName("yearCombo");
        yearCombo.setPreferredSize(new Dimension(100, 25));

        // 添加输入验证
        JTextField yearEditor = (JTextField) yearCombo.getEditor().getEditorComponent();
        yearEditor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String input = yearEditor.getText();
                if (!input.isEmpty()) {
                    try {
                        int year = Integer.parseInt(input);
                        if (year < 1990 || year > java.time.LocalDate.now().getYear() + 1) {
                            yearEditor.setForeground(Color.RED);
                        } else {
                            yearEditor.setForeground(Color.BLACK);
                        }
                    } catch (NumberFormatException ex) {
                        yearEditor.setForeground(Color.RED);
                    }
                }
            }
        });

        yearPanel.add(yearCombo);

        // 季度选择
        JPanel quarterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        quarterPanel.add(new JLabel("季度:"));
        JComboBox<String> quarterCombo = new JComboBox<>(new String[]{"Q1", "Q2", "Q3", "Q4"});
        quarterCombo.setSelectedItem("Q" + ((java.time.LocalDate.now().getMonthValue() - 1) / 3 + 1));
        quarterCombo.setName("quarterCombo");
        quarterCombo.setPreferredSize(new Dimension(80, 25));
        quarterPanel.add(quarterCombo);

        // 新增：添加数量选择器
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        limitPanel.add(new JLabel("显示数量:"));
        String[] limits = {"全部", "前10", "前25", "前50"};
        JComboBox<String> limitCombo = new JComboBox<>(limits);
        limitCombo.setSelectedIndex(0); // 默认"全部"
        limitCombo.setName("limitCombo");
        limitCombo.setPreferredSize(new Dimension(100, 25));
        limitPanel.add(limitCombo);

        // 新增：图表类型选择
        JPanel chartTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        chartTypePanel.add(new JLabel("图表类型:"));
        ButtonGroup chartTypeGroup = new ButtonGroup();
        JRadioButton barChartRadio = new JRadioButton("柱状图", true); // 默认选中柱状图
        JRadioButton pieChartRadio = new JRadioButton("扇形图", false);
        chartTypeGroup.add(barChartRadio);
        chartTypeGroup.add(pieChartRadio);
        chartTypePanel.add(barChartRadio);
        chartTypePanel.add(pieChartRadio);

        // 分类选择器 - 新增
        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        categoryPanel.add(new JLabel("类别:"));
        String[] categories = {"全部", "A", "B", "C", "D"}; // A=特色食物, B=饮料, C=小炒, D=套餐
        JComboBox<String> categoryCombo = new JComboBox<>(categories);
        categoryCombo.setSelectedIndex(0); // 默认"全部"
        categoryCombo.setName("categoryCombo");
        categoryCombo.setPreferredSize(new Dimension(100, 25));
        categoryPanel.add(categoryCombo);

        // 加载按钮
        JButton loadButton = new JButton("加载数据");
        loadButton.setPreferredSize(new Dimension(100, 30));
        loadButton.setFont(new Font("微软雅黑", Font.BOLD, 12));

        controlPanel.add(yearPanel);
        controlPanel.add(quarterPanel);
        controlPanel.add(limitPanel); // 添加数量选择器
        controlPanel.add(chartTypePanel); // 加入图表类型选择
        controlPanel.add(categoryPanel); // 添加分类选择器到控制面板
        controlPanel.add(loadButton);

        // 2. 菜品数据表格
        String[] dishColumns = {"菜品编号", "菜品名称", "销售数量", "销售额(元)", "平均单价(元)"};
        DefaultTableModel dishTableModel = new DefaultTableModel(dishColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable dishTable = new JTable(dishTableModel);
        dishTable.setRowHeight(25);
        dishTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        dishTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        dishTable.setFillsViewportHeight(true);
        dishTable.setName("dishTable");

        // 设置表格列宽
        dishTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        dishTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        dishTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        dishTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        dishTable.getColumnModel().getColumn(4).setPreferredWidth(100);

        // 为表格添加滚动支持
        JScrollPane dishScrollPane = new JScrollPane(dishTable);
        dishScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dishScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dishScrollPane.setPreferredSize(new Dimension(950, 250)); // 增加高度
        dishScrollPane.setName("dishScrollPane");

        // 3. 菜品图表区域 - 明确创建并命名
        JPanel chartPanel = new JPanel();
        chartPanel.setBorder(BorderFactory.createTitledBorder("销售趋势"));
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));
        chartPanel.setPreferredSize(new Dimension(950, 600)); // 增加高度到600
        chartPanel.setMinimumSize(new Dimension(400, 400)); // 增加最小高度
        chartPanel.setName("dishChartPanel"); // 修复：使用明确的名称，防止找不到

        // 初始化图表区域
        initializeChartPanel(chartPanel);

        // 为图表区域添加滚动支持 - 修复：确保滚动功能正常
        JScrollPane chartScrollPane = new JScrollPane(chartPanel);
        chartScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chartScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        chartScrollPane.setName("chartScrollPane");
        // 设置滚动面板的最小尺寸，确保图表可以放大
        chartScrollPane.setMinimumSize(new Dimension(900, 500));

        // 4. 组装数据面板
        JPanel dataPanel = new JPanel(new BorderLayout(10, 10));
        dataPanel.add(dishScrollPane, BorderLayout.NORTH); // 表格放在上方

        // 将图表滚动面板添加到数据面板 - 使用JScrollPane确保可滚动
        dataPanel.add(chartScrollPane, BorderLayout.CENTER); // 图表区域放在中间，可扩展

        // 设置数据面板的最小和首选大小，以适应滚动
        dataPanel.setPreferredSize(new Dimension(980, 750)); // 增加高度
        dataPanel.setMinimumSize(new Dimension(900, 700));

        panel.add(controlPanel, BorderLayout.NORTH);

        // 将数据面板放入滚动面板，以支持整个内容区域的滚动
        JScrollPane mainScrollPane = new JScrollPane(dataPanel);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setPreferredSize(new Dimension(1000, 800)); // 增加滚动面板的首选尺寸
        mainScrollPane.getViewport().setBackground(Color.WHITE);

        // 设置滚动面板的边框和样式
        mainScrollPane.setBorder(BorderFactory.createEmptyBorder());
        mainScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        mainScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        panel.add(mainScrollPane, BorderLayout.CENTER);

        // 5. 添加事件处理
        loadButton.addActionListener(e -> {
            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem(); // 获取数量选择
                boolean isBarChart = barChartRadio.isSelected(); // 获取当前图表类型

                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("正在加载" + (category.equals("全部") ? "" : category + "类") + "菜品销售数据...");
                }

                // 添加加载指示器
                JProgressBar progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                JPanel progressPanel = new JPanel(new BorderLayout());
                progressPanel.add(new JLabel("正在加载数据..."), BorderLayout.CENTER);
                progressPanel.add(progressBar, BorderLayout.SOUTH);

                // 替换图表区域
                chartPanel.removeAll();
                chartPanel.add(progressPanel);
                chartPanel.revalidate();
                chartPanel.repaint();

                SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                    @Override
                    protected List<Map<String, Object>> doInBackground() {
                        return controller.getQuarterlyDishSalesReport(year, quarter, category);
                    }

                    @Override
                    protected void done() {
                        try {
                            List<Map<String, Object>> reportData = get();
                            if (reportData.isEmpty()) {
                                JOptionPane.showMessageDialog(panel,
                                        "未找到" + year + "年" + quarter +
                                                (!"全部".equals(category) ? " " + category + "类" : "") +
                                                "的销售数据",
                                        "提示", JOptionPane.INFORMATION_MESSAGE);
                                if (panelStatusLabel != null) {
                                    panelStatusLabel.setText("未找到相关数据");
                                }
                                // 恢复图表区域
                                initializeChartPanel(chartPanel);
                                return;
                            }

                            // 更新表格数据
                            displayDishSalesData(reportData, dishTable, dishTableModel, chartPanel);

                            // 根据选择的数量，确定要显示的条目数
                            int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                            // 更新图表，根据选择的图表类型
                            updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                            if (panelStatusLabel != null) {
                                String categoryText = category.equals("全部") ? "" : category + "类";
                                panelStatusLabel.setText(categoryText + "菜品销售数据加载完成 (" + reportData.size() + "条)");
                            }
                        } catch (Exception ex) {
                            if (panelStatusLabel != null) {
                                panelStatusLabel.setText("加载失败: " + ex.getMessage());
                            }
                            JOptionPane.showMessageDialog(panel, "加载菜品销售数据失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                            // 恢复图表区域
                            initializeChartPanel(chartPanel);
                        }
                    }
                };
                worker.execute();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "无效的年份格式，请输入4位数字年份", "输入错误", JOptionPane.ERROR_MESSAGE);
                yearEditor.setForeground(Color.RED);
            }
        });

        // 6. 为图表类型单选按钮添加事件监听器
        ActionListener chartTypeListener = e -> {
            if (dishTableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(panel, "请先加载数据", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem();
                boolean isBarChart = barChartRadio.isSelected();

                // 重新获取数据
                List<Map<String, Object>> reportData = controller.getQuarterlyDishSalesReport(year, quarter, category);

                if (reportData.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "没有找到可显示的数据", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // 根据选择的数量，确定要显示的条目数
                int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                // 更新图表
                updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                if (panelStatusLabel != null) {
                    String chartTypeText = isBarChart ? "柱状图" : "扇形图";
                    panelStatusLabel.setText("已切换到" + chartTypeText + "显示");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(panel, "更新图表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("图表更新失败: " + ex.getMessage());
                }
            }
        };

        barChartRadio.addActionListener(chartTypeListener);
        pieChartRadio.addActionListener(chartTypeListener);

        // 7. 为数量选择器添加事件监听器
        limitCombo.addActionListener(e -> {
            if (dishTableModel.getRowCount() == 0) {
                return;
            }

            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem();
                boolean isBarChart = barChartRadio.isSelected();

                // 重新获取数据
                List<Map<String, Object>> reportData = controller.getQuarterlyDishSalesReport(year, quarter, category);

                if (reportData.isEmpty()) {
                    return;
                }

                // 根据选择的数量，确定要显示的条目数
                int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                // 仅更新图表
                updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("已更新显示数量为 " + limit);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // 8. 为分类选择器添加事件监听器
        categoryCombo.addActionListener(e -> {
            // 自动加载新分类的数据
            loadButton.doClick();
        });

        return panel;
    }

    /**
     * 根据显示限制字符串计算最大项目数（安全边界处理）
     *
     * @param limit      限制选项（"前10"/"前25"/"前50"/"全部"）
     * @param totalItems 数据集总项目数
     * @return 实际显示数量（不超过totalItems）
     */
    private int getMaxItemsFromLimit(String limit, int totalItems) {
        switch (limit) {
            case "前10":
                return Math.min(10, totalItems);
            case "前25":
                return Math.min(25, totalItems);
            case "前50":
                return Math.min(50, totalItems);
            default: // "全部"
                return totalItems;
        }
    }


    /**
     * 初始化图表区域为提示状态（无数据时显示引导文本）
     *
     * @param chartPanel 需要初始化的图表面板
     */
    private void initializeChartPanel(JPanel chartPanel) {
        chartPanel.removeAll();

        JLabel placeholderLabel = new JLabel("请选择年份和季度加载销售数据", SwingConstants.CENTER);
        placeholderLabel.setFont(new Font("微软雅黑", Font.ITALIC, 14));
        placeholderLabel.setForeground(Color.GRAY);

        chartPanel.add(placeholderLabel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }


    /**
     * 动态更新菜品销售图表（支持柱状图/饼图双视图）
     *
     * 【功能概述】
     * 根据报表数据生成菜品销售可视化图表，支持柱状图（数值精确比较）和饼图（占比直观展示）两种模式，
     * 同时展示销售额排名和销量排名两个维度的数据。
     *
     * 【参数说明】
     * @param reportData  报表数据列表，每项包含：item_name(菜品名称), total_revenue(销售额), total_quantity(销量)
     * @param chartPanel  目标图表面板，将清空并填充新生成的图表
     * @param maxItems    显示项目数量限制（0/负数=全部，>0=前N项，999+视为全部）
     * @param isBarChart  true=柱状图模式，false=饼图模式
     *
     * 【核心处理逻辑】
     * 1. 数据预处理：限制显示数量、截断长菜名（>8字符用"..."）、安全转换Number类型避免类型转换异常
     * 2. 柱状图分支：
     *    - 创建双数据集（销售额/销量）
     *    - 生成双柱状图并应用统一样式（蓝色/绿色主题）
     *    - 启用鼠标滚轮缩放，支持交互查看
     * 3. 饼图分支：
     *    - 计算总销售额/总销量用于占比计算
     *    - 按销售额降序排序，优先显示前10项或占比≥2%的项目
     *    - 合并低占比项目为"其他"类别，避免饼图扇区过多
     *    - 应用中文字体适配，自定义标签格式（名称:数值，移除百分比）
     * 4. 异常处理：图表生成失败时显示友好错误提示，保持界面不崩溃
     *
     * 【注意事项】
     * - 饼图模式下会自动合并小占比项目，确保可视化清晰
     * - 所有金额/数量字段使用Number安全转换，兼容BigDecimal/Double/Integer多种类型
     * - 图表生成后自动调用revalidate/repaint刷新界面
     */
    private void updateDishSalesChart(List<Map<String, Object>> reportData, JPanel chartPanel, int maxItems, boolean isBarChart) {
        chartPanel.removeAll();
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));

        try {
            // 确保maxItems不会超过数据集大小
            int itemsToDisplay = Math.min(maxItems, reportData.size());
            String displayText = (maxItems == reportData.size() || maxItems >= 999) ? "全部" : "前" + maxItems;

            if (isBarChart) {
                // 柱状图逻辑
                // 准备数据集
                DefaultCategoryDataset salesDataset = new DefaultCategoryDataset();
                DefaultCategoryDataset quantityDataset = new DefaultCategoryDataset();

                for (int i = 0; i < itemsToDisplay; i++) {
                    Map<String, Object> item = reportData.get(i);
                    String itemName = (String) item.get("item_name");


                    // 缩短长名称
                    if (itemName.length() > 8) {
                        itemName = itemName.substring(0, 8) + "...";
                    }

                    // 修复 BigDecimal 转 Double 的类型转换问题
                    Object revenueObj = item.get("total_revenue");
                    double totalRevenue = (revenueObj instanceof Number) ?
                            ((Number) revenueObj).doubleValue() : 0.0;

                    Object quantityObj = item.get("total_quantity");
                    int totalQuantity = (quantityObj instanceof Number) ?
                            ((Number) quantityObj).intValue() : 0;


                    // 添加到销售额数据集
                    salesDataset.addValue(totalRevenue, "销售额", itemName);

                    // 添加到销量数据集
                    quantityDataset.addValue(totalQuantity, "销量", itemName);
                }

                // 创建销售额图表
                JFreeChart salesChart = ChartFactory.createBarChart(
                        "销售额排名 (" + displayText + ")",
                        "菜品",
                        "销售额 (元)",
                        salesDataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false
                );

                // 创建销量图表
                JFreeChart quantityChart = ChartFactory.createBarChart(
                        "销量排名 (" + displayText + ")",
                        "菜品",
                        "销售数量",
                        quantityDataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false
                );
                // 自定义图表样式
                customizeChartStyle(salesChart, new Color(41, 128, 185)); // 蓝色
                customizeChartStyle(quantityChart, new Color(39, 174, 96)); // 绿色

                // 创建图表面板
                ChartPanel salesChartPanel = new ChartPanel(salesChart);
                salesChartPanel.setMouseWheelEnabled(true);

                ChartPanel quantityChartPanel = new ChartPanel(quantityChart);
                quantityChartPanel.setMouseWheelEnabled(true);

                // 添加到图表面板
                chartPanel.add(salesChartPanel);
                chartPanel.add(quantityChartPanel);
            } else {
                // 扇形图（饼图）逻辑
                if (reportData.isEmpty()) {
                    throw new RuntimeException("没有可用的销售数据");
                }

                // 计算总销售额和总销量
                double totalRevenue = 0.0;
                int totalQuantity = 0;

                // 只计算显示范围内的数据
                for (int i = 0; i < itemsToDisplay; i++) {
                    Map<String, Object> item = reportData.get(i);
                    //  修复：使用 Number 转换而不是直接强制转换
                    totalRevenue += ((Number) item.get("total_revenue")).doubleValue();
                    totalQuantity += ((Number) item.get("total_quantity")).intValue();
                }

                // 创建数据集
                DefaultPieDataset salesDataset = new DefaultPieDataset();
                DefaultPieDataset quantityDataset = new DefaultPieDataset();

                // 仅显示有意义的切片 - 按销售额排序
                List<Map<String, Object>> sortedData = new ArrayList<>(reportData.subList(0, itemsToDisplay));
                //  修复：排序时也使用 Number 转换
                sortedData.sort((a, b) -> Double.compare(
                        ((Number) b.get("total_revenue")).doubleValue(),
                        ((Number) a.get("total_revenue")).doubleValue()
                ));

                // 计算要显示的主要项目数量（最多10个，确保饼图不杂乱）
                int primaryItemsCount = Math.min(10, sortedData.size());
                double otherRevenue = 0.0;
                int otherQuantity = 0;
                int otherCount = 0;

                for (int i = 0; i < sortedData.size(); i++) {
                    Map<String, Object> item = sortedData.get(i);
                    String itemName = (String) item.get("item_name");

                    // 缩短长名称
                    if (itemName.length() > 12) {
                        itemName = itemName.substring(0, 12) + "...";
                    }

                    //  修复：使用 Number 转换
                    double revenue = ((Number) item.get("total_revenue")).doubleValue();
                    int quantity = ((Number) item.get("total_quantity")).intValue();

                    double revenuePercent = (revenue / totalRevenue) * 100;
                    double quantityPercent = totalQuantity > 0 ? ((double) quantity / totalQuantity) * 100 : 0;

                    // 只显示主要项目或占比大于2%的项目
                    if (i < primaryItemsCount - 1 || revenuePercent >= 2) {
                        // 添加到销售额数据集
                        salesDataset.setValue(itemName + " (" + String.format("%.1f%%", revenuePercent) + ")", revenue);

                        // 添加到销量数据集
                        quantityDataset.setValue(itemName + " (" + String.format("%.1f%%", quantityPercent) + ")", quantity);
                    } else {
                        otherRevenue += revenue;
                        otherQuantity += quantity;
                        otherCount++;
                    }
                }

                // 添加"其他"类别
                if (otherCount > 0) {
                    double otherRevenuePercent = (otherRevenue / totalRevenue) * 100;
                    double otherQuantityPercent = totalQuantity > 0 ? ((double) otherQuantity / totalQuantity) * 100 : 0;

                    if (otherRevenue > 0) {
                        salesDataset.setValue("其他 (" + otherCount + "项, " + String.format("%.1f%%", otherRevenuePercent) + ")", otherRevenue);
                    }

                    if (otherQuantity > 0) {
                        quantityDataset.setValue("其他 (" + otherCount + "项, " + String.format("%.1f%%", otherQuantityPercent) + ")", otherQuantity);
                    }
                }

                // 创建销售额饼图
                JFreeChart salesChart = ChartFactory.createPieChart(
                        "销售额占比 (" + displayText + ")",
                        salesDataset,
                        true,  // 显示图例
                        true,  // 生成工具提示
                        false  // 生成URLs
                );
                // 立即应用中文字体
                applyChineseFontToChart(salesChart);

                // 创建销量饼图
                JFreeChart quantityChart = ChartFactory.createPieChart(
                        "销量占比 (" + displayText + ")",
                        quantityDataset,
                        true,
                        true,
                        false
                );
                // 立即应用中文字体
                applyChineseFontToChart(quantityChart);

                // 自定义饼图样式
                customizePieChart(salesChart, "销售额");
                customizePieChart(quantityChart, "销量");

                // 创建图表面板
                ChartPanel salesChartPanel = new ChartPanel(salesChart);
                salesChartPanel.setMouseWheelEnabled(true);

                ChartPanel quantityChartPanel = new ChartPanel(quantityChart);
                quantityChartPanel.setMouseWheelEnabled(true);

                // 添加到图表面板
                chartPanel.add(salesChartPanel);
                chartPanel.add(quantityChartPanel);
            }

            chartPanel.revalidate();
            chartPanel.repaint();
        }
            catch (Exception e) {
            // 图表生成失败时显示错误
            JLabel errorLabel = new JLabel("图表生成失败: " + e.getMessage(), SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);
            errorLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
            chartPanel.add(errorLabel);

            chartPanel.revalidate();
            chartPanel.repaint();

            e.printStackTrace();
        }
    }


    /**
     * 自定义饼图的样式与属性
     * 主要功能包括：设置中文字体、彻底移除百分比显示、设置定时方向、
     * 自动弹出特定扇区、自定义颜色环以及优化边距防止标签遮挡。
     *
     * @param chart 需要进行样式定制的 JFreeChart 饼图对象
     * @param type  图表类型标识，当值为"销售额"时会触发特定的扇区弹出特效
     */
    private void customizePieChart(JFreeChart chart, String type) {
        PiePlot plot = (PiePlot) chart.getPlot();

        // 获取支持中文的字体
        Font chineseFontRegular = getChineseFont(12);
        Font chineseFontBold = getChineseFont(14);
        chineseFontBold = chineseFontBold.deriveFont(Font.BOLD); // 设置为粗体

        // 设置背景
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setShadowGenerator(null);

        // ====== 彻底修复：完全移除百分比 ======
        // 方法1：使用自定义标签生成器（最可靠）
        plot.setLabelGenerator(new PieSectionLabelGenerator() {
            @Override
            // 生成普通文本标签的方法
            // 设置饼图扇区的标签生成器，自定义显示格式
            public String generateSectionLabel(PieDataset dataset, Comparable key) {
                // 从数据集中获取当前扇区对应的数值
                Number value = dataset.getValue(key);
                return key.toString() + ": " + value.intValue();
            }

            @Override
            public AttributedString generateAttributedSectionLabel(PieDataset dataset, Comparable key) {
                // 返回空的AttributedString，因为不需要特殊格式
                // generateAttributedSectionLabel 返回 null 时，JFreeChart 会使用默认的标签格式。 显示了百分比
                return null;
            }
        });

        // 方法2：作为备选（如果方法1不工作）
        // plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
        //     "{0}: {1}",
        //     NumberFormat.getIntegerInstance(),
        //     new DecimalFormat("0%") // 使用一个不会实际显示的格式
        // ));
        // ====== 修复结束 ======

        // 设置标签字体
        plot.setLabelFont(chineseFontRegular);

        // 显示标签
        plot.setLabelLinksVisible(true);
        plot.setLabelBackgroundPaint(Color.WHITE);
        plot.setLabelOutlinePaint(Color.GRAY);
        plot.setLabelShadowPaint(new Color(0, 0, 0, 0)); // 透明阴影

        // 设置起始角度
        plot.setStartAngle(90);

        // 设置方向 - 顺时针
        plot.setDirection(Rotation.CLOCKWISE);

        // 设置标签链接样式
        plot.setLabelLinkStyle(PieLabelLinkStyle.STANDARD);
        plot.setLabelLinkPaint(Color.DARK_GRAY);
        plot.setLabelLinkStroke(new BasicStroke(1.0f));

        // 设置自动弹出主要部分 - 仅对销售额饼图应用
        if ("销售额".equals(type) && plot.getDataset().getItemCount() > 0) {
            // 只弹出第一个扇区（最大的部分）
            Comparable<?> firstKey = plot.getDataset().getKey(0);
            if (firstKey instanceof String) {
           // 设置第一个扇区的弹出比例为10%
                plot.setExplodePercent((String) firstKey, 0.10);
            }
        }

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(chineseFontRegular.deriveFont(10.0f));
            legend.setFrame(BlockBorder.NONE);
            legend.setPosition(RectangleEdge.BOTTOM);
        }

        // 设置标题字体
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(chineseFontBold);
        }

        // 为每个扇区设置颜色 - 确保只使用字符串键
        int colorIndex = 0;
        Color[] colors = {
                new Color(228, 41, 50),    // 红色
                new Color(35, 154, 223),   // 蓝色
                new Color(50, 168, 82),    // 绿色
                new Color(142, 68, 173),   // 紫色
                new Color(243, 156, 18),   // 橙色
                new Color(127, 140, 141),  // 灰色
                new Color(44, 62, 80),     // 深灰
                new Color(211, 84, 0),     // 深橙
                new Color(30, 130, 76),    // 深绿
                new Color(218, 129, 225)   // 粉色
        };

        // 遍历数据集中的所有扇区
        for (int i = 0; i < plot.getDataset().getItemCount(); i++) {
            // 获取当前扇区的键值（扇区名称）
            Comparable<?> key = plot.getDataset().getKey(i);
            // 判断键值是否为字符串类型，确保类型安全
            if (key instanceof String) {
                // 为当前扇区设置颜色，使用颜色数组循环取值
                plot.setSectionPaint((String) key, colors[colorIndex % colors.length]);
                colorIndex++;// 颜色索引递增，实现循环使用颜色数组
            }
        }

        // 设置图表边距，确保中文标签有足够显示空间
        chart.setPadding(new RectangleInsets(10, 10, 10, 10));

        // 设置绘图区域边距
        plot.setInsets(new RectangleInsets(10, 10, 10, 10));
    }

    /**
     * 定制柱状图样式（自动范围 + 中文支持 + 标签优化）
     * @param chart JFreeChart 对象
     * @param seriesColor 系列主色调
     */
    private void customizeChartStyle(JFreeChart chart, Color seriesColor) {
        // ===== 1. 获取绘图区和渲染器 =====
        CategoryPlot plot = (CategoryPlot) chart.getPlot();    // 获取图表的绘图区域，用于设置背景、坐标轴、网格线等
        BarRenderer renderer = (BarRenderer) plot.getRenderer();    // 获取柱状图渲染器，用于设置柱子颜色、边框、间距、数据标签等

        // ===== 2. 设置背景（关键：透明背景避免遮挡）=====
        plot.setBackgroundPaint(new Color(255, 255, 255, 0));  // （alpha=0）：透明
        plot.setOutlineVisible(false);  // 隐藏边框
        plot.setRangeGridlinePaint(new Color(230, 230, 230));  // 浅灰网格线
        plot.setRangeGridlineStroke(new BasicStroke(0.5f));  // 细线

        // ===== 3. 值轴配置（关键：删除 upperBound，启用自动范围）=====
        // 获取垂直坐标轴（Y 轴），用于显示数值刻度
        ValueAxis rangeAxis = plot.getRangeAxis();
        // 类型安全检查：确保是数值轴才能进行数值相关配置
        if (rangeAxis instanceof NumberAxis) {
            NumberAxis numberAxis = (NumberAxis) rangeAxis;

            // 🔑 核心：启用自动范围计算
            numberAxis.setAutoRange(true);
            numberAxis.setAutoRangeIncludesZero(true);  // 确保从0开始


            // 字体设置
            numberAxis.setLabelFont(getChineseFont(12));
            numberAxis.setTickLabelFont(getChineseFont(11));
            numberAxis.setLabelPaint(new Color(80, 80, 80));
            numberAxis.setTickLabelPaint(new Color(100, 100, 100));//设置 Y 轴刻度数字颜色（中灰色）
            numberAxis.setAxisLinePaint(new Color(220, 220, 220));// 设置 Y 轴轴线颜色（浅灰色，降低视觉干扰）
        }

        // ===== 4. 分类轴（X轴）配置 =====
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setLabelFont(getChineseFont(12));
        domainAxis.setTickLabelFont(getChineseFont(10));
        domainAxis.setTickLabelPaint(new Color(100, 100, 100));
        domainAxis.setAxisLinePaint(new Color(220, 220, 220));

        //  标签旋转 + 截断（避免重叠）  设置分类标签旋转 45 度向上，避免长名称重叠
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        domainAxis.setMaximumCategoryLabelWidthRatio(0.8f);    // 限制标签最大宽度比例，防止过长标签撑破图表

        // ===== 5. 渲染器配置（柱子样式）=====
        renderer.setSeriesPaint(0, seriesColor);
        renderer.setBarPainter(new StandardBarPainter());  // 移除渐变，纯色更清晰
        renderer.setShadowVisible(false);  // 移除阴影

        // 柱子边框
        renderer.setSeriesOutlinePaint(0, seriesColor.darker());
        renderer.setSeriesOutlineStroke(0, new BasicStroke(0.5f));

        // 柱子间距
        renderer.setItemMargin(0.15);  // 柱子之间间距
        renderer.setMaximumBarWidth(0.08);  // 限制最大宽度

        // ===== 6. 数据标签配置 =====
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(getChineseFont(10));
        renderer.setDefaultItemLabelPaint(new Color(60, 60, 60));

        // 标签位置：柱子上方
        renderer.setDefaultPositiveItemLabelPosition(
                new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER)
        );

        // 标签格式：金额用货币格式，数量用整数
        if (chart.getTitle() != null &&
                chart.getTitle().getText().contains("销售额")) {
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getCurrencyInstance())
            );
        } else {
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getIntegerInstance())
            );
        }

        // ===== 7. 图例配置 =====
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(getChineseFont(11));
            legend.setItemPaint(new Color(80, 80, 80));
            legend.setFrame(BlockBorder.NONE);  // 移除图例边框
            legend.setPosition(RectangleEdge.BOTTOM); // 设置图例位置在图表底部
        }

        // ===== 8. 标题配置 =====
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(getChineseFont(14));
            title.setPaint(new Color(51, 51, 51));
        }

        // ===== 9. 整体边距（确保中文标签有空间）=====
        // 设置图表整体内边距：上 10/左 15/下 10/右 15 像素，为中文标签预留空间
        chart.setPadding(new RectangleInsets(10, 15, 10, 15));
        // 设置绘图区内边距：底部多留 25 像素，防止旋转的标签被截断
        plot.setInsets(new RectangleInsets(10, 10, 25, 10));  // 底部多留空间给标签
    }

    /**
     * 从面板获取报表表格
     *
     * @note 通过滚动窗格名称"reportTableScrollPane"查找
     */
    private JTable getTableFromPanel(JPanel panel) {
        Component comp = findComponentByName(panel, "reportTableScrollPane");
        if (comp instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) comp;
            JViewport viewport = scrollPane.getViewport();
            if (viewport != null && viewport.getView() instanceof JTable) {
                return (JTable) viewport.getView();
            }
        }
        return null;
    }

    /**
     * 从面板获取菜品表格
     *
     * @note 通过组件名称"dishTable"直接获取
     */
    private JTable getDishTableFromPanel(JPanel panel) {
        // 通过组件名称递归查找目标表格
        Component comp = findComponentByName(panel, "dishTable");
        return (JTable) comp;    // 类型转换并返回（调用方需确保组件存在）
    }

    /**
     * 从取消预约统计面板获取表格
     * @param panel forfeitedPanel
     * @return JTable 对象，找不到返回 null
     */
    private JTable getForfeitedTableFromPanel(JPanel panel) {
        // 方法1：如果给表格设置了 name 属性，可以通过递归查找
        Component comp = findComponentByName(panel, "forfeitedTable");
        if (comp instanceof JTable) {
            return (JTable) comp;
        }

        // 方法2：递归遍历所有子组件查找 JTable
        return findTableInContainer(panel);
    }

    /**
     * 递归查找容器内的 JTable
     */
    private JTable findTableInContainer(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTable) {
                return (JTable) comp;
            }
            if (comp instanceof JScrollPane) {
                JScrollPane scroll = (JScrollPane) comp;
                if (scroll.getViewport().getView() instanceof JTable) {
                    return (JTable) scroll.getViewport().getView();
                }
            }
            if (comp instanceof Container) {
                JTable found = findTableInContainer((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 显示菜品销售报表（表格+双图表）
     *
     * @note 仅展示前10个热门菜品，自动计算总计
     */
    private void displayDishSalesData(List<Map<String, Object>> reportData, JTable dishTable,
                                      DefaultTableModel tableModel, JPanel chartPanel) {
        tableModel.setRowCount(0); // 清空表格

        if (reportData == null || reportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未找到相关菜品销售数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            // 显示空白图表
            chartPanel.removeAll();
            JLabel noDataLabel = new JLabel("暂无数据可展示", SwingConstants.CENTER);
            noDataLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
            noDataLabel.setForeground(Color.GRAY);
            chartPanel.add(noDataLabel);
            chartPanel.revalidate();
            chartPanel.repaint();
            return;
        }

        double totalRevenue = 0.0;
        int totalQuantity = 0;

        // 准备图表数据
        DefaultCategoryDataset revenueDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset quantityDataset = new DefaultCategoryDataset();

        // 填充表格
        for (Map<String, Object> data : reportData) {
            String itemCode = (String) data.get("item_code");
            String itemName = (String) data.get("item_name");
            int quantity = ((Number) data.get("total_quantity")).intValue();
            double revenue = ((Number) data.get("total_revenue")).doubleValue();
            double avgPrice = revenue / quantity;

            Object[] row = {
                    itemCode,
                    itemName,
                    quantity,
                    String.format("%.2f", revenue),
                    String.format("%.2f", avgPrice),
            };

            tableModel.addRow(row);

            // 累计总计
            totalRevenue += revenue;
            totalQuantity += quantity;

            // 为图表准备数据（只显示前10个菜品）
            if (tableModel.getRowCount() <= 10) {
                revenueDataset.addValue(revenue, "销售额", itemCode + " - " + itemName.substring(0, Math.min(8, itemName.length())));
                quantityDataset.addValue(quantity, "销售量", itemCode + " - " + itemName.substring(0, Math.min(8, itemName.length())));
            }
        }

        // 添加总计行
        Object[] totalRow = {
                "总计",
                "",
                totalQuantity,
                String.format("%.2f", totalRevenue),
                "",
                ""
        };
        tableModel.addRow(totalRow);

        // 创建图表
        createDishCharts(revenueDataset, quantityDataset, chartPanel);
    }

    /**
     * 生成菜品销售双图表（销售额/销售量排名）
     *
     * @note 适配中文并优化图表尺寸
     */
    private void createDishCharts(DefaultCategoryDataset revenueDataset,
                                  DefaultCategoryDataset quantityDataset,
                                  JPanel chartPanel) {
        chartPanel.removeAll();//可能是null的原因

        // 创建销售额图表
        JFreeChart revenueChart = ChartFactory.createBarChart(
                "热门菜品销售额排名",
                "菜品",
                "金额(元)",
                revenueDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 创建销售量图表
        JFreeChart quantityChart = ChartFactory.createBarChart(
                "热门菜品销售量排名",
                "菜品",
                "数量",
                quantityDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 修复中文乱码问题
        Font labelFont;
        Font titleFont;

        // 检查系统是否支持微软雅黑
        if (isFontAvailable("Microsoft YaHei")) {
            labelFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
            titleFont = new Font("Microsoft YaHei", Font.BOLD, 14);
        } else {
            // 使用系统默认中文字体
            labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        }

        // 设置图表样式
        setChartStyle(revenueChart, labelFont, titleFont, "销售额");
        setChartStyle(quantityChart, labelFont, titleFont, "销售量");

        // 配置图表标签
        configureChartLabels(revenueChart, true);  // 货币格式
        configureChartLabels(quantityChart, false); // 普通数字格式

        // 设置图表大小
        ChartPanel revenuePanel = new ChartPanel(revenueChart);
        revenuePanel.setPreferredSize(new Dimension(450, 450)); // 增加高度
        revenuePanel.setMaximumSize(new Dimension(450, 600)); // 允许更大高度
        revenuePanel.setMouseWheelEnabled(true);

        ChartPanel quantityPanel = new ChartPanel(quantityChart);
        quantityPanel.setPreferredSize(new Dimension(450, 450)); // 增加高度
        quantityPanel.setMaximumSize(new Dimension(450, 600)); // 允许更大高度
        quantityPanel.setMouseWheelEnabled(true);

        chartPanel.add(revenuePanel);
        chartPanel.add(quantityPanel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    /**
     * 导出菜品销售报表到Excel
     *
     * @note 自动格式化金额/数量列，失败时弹出错误
     */
    private void exportDishSalesToExcel(JTable table) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存菜品销售报表");
            fileChooser.setSelectedFile(new File("菜品销售报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx"));
            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            // 创建目录（如果不存在）
            File parentDir = fileToSave.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用Apache POI创建Excel
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("菜品销售报表");

                // 创建表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 金额列
                CellStyle currencyStyle = workbook.createCellStyle();
                currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
                currencyStyle.setBorderBottom(BorderStyle.THIN);
                currencyStyle.setBorderTop(BorderStyle.THIN);
                currencyStyle.setBorderLeft(BorderStyle.THIN);
                currencyStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 普通数字
                CellStyle numberStyle = workbook.createCellStyle();
                numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
                numberStyle.setBorderBottom(BorderStyle.THIN);
                numberStyle.setBorderTop(BorderStyle.THIN);
                numberStyle.setBorderLeft(BorderStyle.THIN);
                numberStyle.setBorderRight(BorderStyle.THIN);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                TableModel model = table.getModel();
                for (int i = 0; i < model.getColumnCount(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(model.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // 填充数据
                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        Cell cell = row.createCell(j);

                        // 设置边框
                        CellStyle borderStyle = workbook.createCellStyle();
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        cell.setCellStyle(borderStyle);

                        if (value == null || value.toString().trim().isEmpty()) {
                            cell.setCellValue("");
                            continue;
                        }

                        String cellValue = value.toString().trim();
                        cell.setCellValue(cellValue);

                        // 根据列类型设置格式
                        if (j == 3) { // 销售额列
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Double.parseDouble(cleanValue));
                                    cell.setCellStyle(currencyStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        } else if (j == 2 || j == 5) { // 销售数量和销售天数
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Integer.parseInt(cleanValue));
                                    cell.setCellStyle(numberStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        } else if (j == 4) { // 平均单价
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Double.parseDouble(cleanValue));
                                    cell.setCellStyle(currencyStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        }
                    }
                }

                // 自动调整列宽
                for (int i = 0; i < model.getColumnCount(); i++) {
                    sheet.setColumnWidth(i, Math.min((int) (sheet.getColumnWidth(i) * 1.5), 5000));
                }

                // 保存文件
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }

                JOptionPane.showMessageDialog(this, "报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出报表失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 显示营业报表数据（表格+双图表）
     *
     * @note 自动计算总计并生成趋势图
     */
    private void displayReportData(List<Map<String, Object>> reportData, JTable reportTable,
                                   DefaultTableModel tableModel, JPanel chartPanel) {
        tableModel.setRowCount(0); // 清空表格

        if (reportData == null || reportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未找到相关营业数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            // 显示空白图表
            chartPanel.removeAll();
            JLabel noDataLabel = new JLabel("暂无数据可展示", SwingConstants.CENTER);
            noDataLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
            noDataLabel.setForeground(Color.GRAY);
            chartPanel.add(noDataLabel);
            chartPanel.revalidate();
            chartPanel.repaint();
            return;
        }

        double totalRevenue = 0.0;
        int totalCustomers = 0;
        int totalOrders = 0;

        // 准备图表数据
        DefaultCategoryDataset revenueDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset customersDataset = new DefaultCategoryDataset();

        // 填充表格和计算总计
        for (Map<String, Object> data : reportData) {
            //  修复 date：使用 toString() 避免强转
            String date = data.get("date") != null ? data.get("date").toString() : "未知日期";

            //  修复 revenue：通过 Number 安全转换（支持 BigDecimal/Double/Integer）
            Object revenueObj = data.get("revenue");
            double revenue = 0.0;
            if (revenueObj instanceof Number) {
                revenue = ((Number) revenueObj).doubleValue();    //  所有数值类型都能安全转为 double
            }

            //  修复 customers：同样通过 Number 安全转换
            Object customersObj = data.get("customers");
            int customers = 0;
            if (customersObj instanceof Number) {
                customers = ((Number) customersObj).intValue();
            }

            //  修复 takeoutOrderCount
            Object orderCountObj = data.get("takeoutOrderCount");
            int orderCount = 0;
            if (orderCountObj instanceof Number) {
                orderCount = ((Number) orderCountObj).intValue();
            }

            double avgRevenuePerCustomer = customers > 0 ? revenue / customers : 0;

            Object[] row = {
                    date,
                    String.format("%.2f", revenue),
                    customers,
                    String.format("%.2f", avgRevenuePerCustomer),
                    orderCount
            };
            tableModel.addRow(row);

            // 累计总计
            totalRevenue += revenue;
            totalCustomers += customers;
            totalOrders += orderCount;

            // 为图表准备数据
            revenueDataset.addValue(revenue, "营业额", date);
            customersDataset.addValue(customers, "顾客数", date);
        }

        // 添加总计行
        if (reportData.size() > 1) {
            double avgRevenuePerCustomer = totalCustomers > 0 ? totalRevenue / totalCustomers : 0;
            Object[] totalRow = {
                    "总计",
                    String.format("%.2f", totalRevenue),
                    totalCustomers,
                    String.format("%.2f", avgRevenuePerCustomer),
                    totalOrders
            };
            tableModel.addRow(totalRow);
        }

        // 生成图表 - 修复：传递正确的数据集
        createCharts(revenueDataset, customersDataset, chartPanel);
    }

    /**
     * 创建双图表面板（营业额/顾客数量趋势）
     *
     * @note 自动处理中文字体和响应式布局，创建后立即设置坐标轴范围
     */
    private void createCharts(DefaultCategoryDataset revenueDataset,
                              DefaultCategoryDataset customersDataset,
                              JPanel chartPanel) {
        chartPanel.removeAll();

        // 创建营业额图表
        JFreeChart revenueChart = ChartFactory.createBarChart(
                "每日营业额趋势",  // 更清晰的标题
                "日期",
                "金额 (元)",
                revenueDataset,
                PlotOrientation.VERTICAL,
                true,  // 显示图例
                true,  // 生成工具提示
                false  // 生成URL
        );

        // 创建顾客数量图表
        JFreeChart customersChart = ChartFactory.createBarChart(
                "每日顾客数量统计",  // 更清晰的标题
                "日期",
                "人数",
                customersDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // ═══════════════════════════════════════════════════════════
        // 【核心修改】创建图表后立即设置坐标轴范围
        // ═══════════════════════════════════════════════════════════
        setChartAxisRanges(revenueChart, customersChart);

        // 修复中文乱码问题 - 跨平台兼容方案
        Font labelFont;
        Font titleFont;

        // 检查系统是否支持微软雅黑
        if (isFontAvailable("Microsoft YaHei")) {
            labelFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
            titleFont = new Font("Microsoft YaHei", Font.BOLD, 14);
        } else {
            // 使用系统默认中文字体
            labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        }

        // 设置营业额图表样式
        setChartStyle(revenueChart, labelFont, titleFont, "营业额");

        // 设置顾客数量图表样式
        setChartStyle(customersChart, labelFont, titleFont, "顾客数量");

        // 兼容最新JFreeChart版本的标签设置
        configureChartLabels(revenueChart, true);  // true表示货币格式
        configureChartLabels(customersChart, false); // false表示整数格式

        // 设置图表大小
        ChartPanel revenuePanel = new ChartPanel(revenueChart);
        revenuePanel.setPreferredSize(new Dimension(450, 300));
        revenuePanel.setMouseWheelEnabled(true); // 启用滚轮缩放

        ChartPanel customersPanel = new ChartPanel(customersChart);
        customersPanel.setPreferredSize(new Dimension(450, 300));
        customersPanel.setMouseWheelEnabled(true);

        chartPanel.add(revenuePanel);
        chartPanel.add(customersPanel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    /**
     *  为双图表设置合理的坐标轴范围
     * 避免数据量少时图表显示异常，提升视觉体验
     *
     * @param revenueChart   营业额图表
     * @param customersChart 顾客数量图表
     */
    private void setChartAxisRanges(JFreeChart revenueChart, JFreeChart customersChart) {
        CategoryPlot revenuePlot = (CategoryPlot) revenueChart.getPlot();
        CategoryPlot customersPlot = (CategoryPlot) customersChart.getPlot();

        // ── 营业额图表：设置Y轴范围 ──
        NumberAxis revenueAxis = (NumberAxis) revenuePlot.getRangeAxis();
        CategoryDataset revenueDataset = revenuePlot.getDataset();  //  从图表中获取数据集
        double maxRevenue = getMaxValueFromDataset((DefaultCategoryDataset) revenueDataset);
        if (maxRevenue > 0) {
            // 设置上限为最大值的1.2倍，留出顶部空间
            revenueAxis.setUpperBound(maxRevenue * 1.2);
            revenueAxis.setLowerBound(0);  // 下限始终为0
            revenueAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        }

        // ── 顾客数量图表：设置Y轴范围 ──
        NumberAxis customersAxis = (NumberAxis) customersPlot.getRangeAxis();
        CategoryDataset customersDataset = customersPlot.getDataset();  //  从图表中获取数据集
        double maxCustomers = getMaxValueFromDataset((DefaultCategoryDataset) customersDataset);
        if (maxCustomers > 0) {
            // 设置上限为最大值的1.2倍，留出顶部空间
            customersAxis.setUpperBound(maxCustomers * 1.2);
            customersAxis.setLowerBound(0);  // 下限始终为0
            customersAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        }
    }

    /**
     *  辅助方法：从数据集获取最大值
     *
     * @param dataset 分类数据集
     * @return 最大值，空数据集返回0
     */
    private double getMaxValueFromDataset(DefaultCategoryDataset dataset) {
        // 空数据集校验：返回默认值 0
        if (dataset == null || dataset.getRowCount() == 0) {
            return 0;
        }
        // 初始化最大值为 0（假设数据均为非负数）
        double maxValue = 0;
        // 遍历数据集的所有行（系列）
        for (int row = 0; row < dataset.getRowCount(); row++) {
            // 遍历数据集的所有列（分类/日期）
            for (int col = 0; col < dataset.getColumnCount(); col++) {
                // 获取当前单元格的数值
                Number value = dataset.getValue(row, col);
                // 非空校验 + 比较更新最大值
                if (value != null && value.doubleValue() > maxValue) {
                    maxValue = value.doubleValue();
                }
            }
        }
        return maxValue;
    }

    /**
     * 配置图表样式（颜色/字体/网格线）
     *
     * @param seriesName 决定主色调（"营业额"=蓝/"顾客数量"=绿）
     */
    private void setChartStyle(JFreeChart chart, Font labelFont, Font titleFont, String seriesName) {
        // 设置标题
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(titleFont);
            title.setPaint(new Color(51, 51, 51)); // 深灰色标题
        }

        // 获取绘图区
        CategoryPlot plot = (CategoryPlot) chart.getPlot();

        // 设置背景
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(230, 230, 230)); // 浅灰色网格线
        plot.setOutlinePaint(new Color(200, 200, 200)); // 边框颜色

        // 设置X轴
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setLabelFont(labelFont);
        domainAxis.setTickLabelFont(labelFont);
        domainAxis.setTickLabelPaint(new Color(80, 80, 80));
        domainAxis.setAxisLinePaint(new Color(180, 180, 180));

        // 设置Y轴
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setLabelFont(labelFont);
        rangeAxis.setTickLabelFont(labelFont);
        rangeAxis.setTickLabelPaint(new Color(80, 80, 80));
        rangeAxis.setAxisLinePaint(new Color(180, 180, 180));

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(labelFont);
            legend.setItemPaint(new Color(60, 60, 60));
        }

        // 设置渲染器样式
        CategoryItemRenderer renderer = plot.getRenderer();

        // 设置系列颜色
        if (seriesName.equals("营业额")) {
            renderer.setSeriesPaint(0, new Color(41, 128, 185)); // 蓝色
        } else {
            renderer.setSeriesPaint(0, new Color(39, 174, 96)); // 绿色
        }

        // 设置边框
        if (renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            barRenderer.setSeriesOutlinePaint(0, new Color(30, 100, 150));
            barRenderer.setSeriesOutlineStroke(0, new BasicStroke(0.5f));
            barRenderer.setShadowVisible(false); // 禁用阴影，使图表更清晰
        }
    }

    /**
     * 检查字体是否可用
     */
    private boolean isFontAvailable(String fontName) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFontNames = ge.getAvailableFontFamilyNames();
        for (String name : availableFontNames) {
            if (name.equals(fontName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兼容最新JFreeChart版本的图表标签配置
     */
    private void configureChartLabels(JFreeChart chart, boolean isCurrency) {
        // 获取图表的绘图区域（包含坐标轴、渲染器等组件）
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        // 获取分类项渲染器，用于控制柱状图/条形图的显示样式
        CategoryItemRenderer renderer = plot.getRenderer();

        // 新版JFreeChart API - 使用set*方法而不是setBase*方法
        if (isCurrency) {
            // 货币格式
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getCurrencyInstance())
            );
        } else {
            // 整数格式
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getIntegerInstance())
            );
        }

        // 设置标签位置：柱子上方居中显示
        renderer.setDefaultItemLabelsVisible(true);

        // 设置标签位置
        ItemLabelPosition position = new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12,// 锚点：柱子顶部外侧
                TextAnchor.BOTTOM_CENTER// 文本对齐：底部居中（使标签贴在柱顶）
        );
        renderer.setDefaultPositiveItemLabelPosition(position);

        // 为条形图设置适当的内边距，确保标签可见
        if (renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            barRenderer.setMaximumBarWidth(0.1); // 控制条形宽度
            barRenderer.setItemMargin(0.2); // 条形之间的间距
        }
    }


    /**
     * 导出报表到Excel（自动识别金额/人数列格式）
     *
     * @note 失败时提供CSV备选方案
     */
    //7.5. Apache POI Excel 導出與智能格式控制
    //技術說明：使用 Apache POI 將報表數據導出為 .xlsx 文件，並根據列類型自動應用貨幣格式、數字格式與邊框樣式。
    //通過列名關鍵詞（如「總營業額」「顧客總數」）自動識別列類型，避免硬編碼列索引。導出失敗時提供 CSV 備份方案，增強系統魯棒性。FileOutputStream 配合 try-with-resources 確保資源正確釋放，防止文件鎖死。
    private void exportReportToExcel(JTable table) {
        try {
            // 修复Date类问题 - 明确使用java.util.Date
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存营业报表");
            fileChooser.setSelectedFile(new File("营业报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx"));

            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }
            // 获取用户选择的文件路径
            File fileToSave = fileChooser.getSelectedFile();
            // 确保文件扩展名为.xlsx
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            // 创建目录（如果不存在）
            File parentDir = fileToSave.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用Apache POI创建Excel
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("营业报表");

                // 创建表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 金额列
                CellStyle currencyStyle = workbook.createCellStyle();
                currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
                currencyStyle.setBorderBottom(BorderStyle.THIN);
                currencyStyle.setBorderTop(BorderStyle.THIN);
                currencyStyle.setBorderLeft(BorderStyle.THIN);
                currencyStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 普通数字
                CellStyle numberStyle = workbook.createCellStyle();
                numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
                numberStyle.setBorderBottom(BorderStyle.THIN);
                numberStyle.setBorderTop(BorderStyle.THIN);
                numberStyle.setBorderLeft(BorderStyle.THIN);
                numberStyle.setBorderRight(BorderStyle.THIN);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                TableModel model = table.getModel();
                for (int i = 0; i < model.getColumnCount(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(model.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // ===== 预定义特殊列索引（用于后续格式判断）=====
                int revenueColumnIndex = -1;// 营业额/金额列
                int avgRevenueColumnIndex = -1;
                int customerColumnIndex = -1;
                int orderCountColumnIndex = -1;

                // ===== 自动检测列类型（通过列名关键词匹配）=====
                for (int i = 0; i < model.getColumnCount(); i++) {
                    String columnName = model.getColumnName(i);
                    if (columnName.contains("总营业额") || columnName.contains("金额")) {
                        revenueColumnIndex = i;
                    } else if (columnName.contains("平均客单价")) {
                        avgRevenueColumnIndex = i;
                    } else if (columnName.contains("顾客总数") || columnName.contains("人数")) {
                        customerColumnIndex = i;
                    } else if (columnName.contains("订单数量")) {
                        orderCountColumnIndex = i;
                    }
                }

                // ===== 遍历表格数据行，填充到Excel =====
                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);// Excel行号从1开始（0行为表头）
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        Cell cell = row.createCell(j);

                        // 为每个单元格设置默认边框样式
                        CellStyle borderStyle = workbook.createCellStyle();
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        cell.setCellStyle(borderStyle);

                        // 空值处理：写入空字符串
                        if (value == null || value.toString().trim().isEmpty()) {
                            cell.setCellValue("");
                            continue;
                        }

                        String cellValue = value.toString().trim();

                        // 特殊列处理 - 金额
                        if (j == revenueColumnIndex || j == avgRevenueColumnIndex) {
                            try {
                                // 移除货币符号和逗号
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    double numericValue = Double.parseDouble(cleanValue);
                                    cell.setCellValue(numericValue);
                                    cell.setCellStyle(currencyStyle);// 应用两位小数格式
                                } else {
                                    cell.setCellValue(cellValue);
                                }
                            } catch (NumberFormatException e) {
                                cell.setCellValue(cellValue);
                            }
                        }
                        // 特殊列处理 - 人数、订单数
                        else if (j == customerColumnIndex || j == orderCountColumnIndex) {
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9]", "");
                                if (!cleanValue.isEmpty()) {
                                    int numericValue = Integer.parseInt(cleanValue);
                                    cell.setCellValue(numericValue);
                                    cell.setCellStyle(numberStyle);// 应用整数格式
                                } else {
                                    cell.setCellValue(cellValue);
                                }
                            } catch (NumberFormatException e) {
                                cell.setCellValue(cellValue);
                            }
                        }
                        // 普通文本
                        else {
                            cell.setCellValue(cellValue);
                        }
                    }
                }

                // 自动调整列宽
                for (int i = 0; i < model.getColumnCount(); i++) {
                    sheet.setColumnWidth(i, Math.min((int) (sheet.getColumnWidth(i) * 1.5), 5000));
                }

                // 保存文件
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }

                JOptionPane.showMessageDialog(this, "报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);

                // 询问是否打开文件
                int openOption = JOptionPane.showConfirmDialog(this, "是否打开导出的文件?", "操作完成", JOptionPane.YES_NO_OPTION);
                if (openOption == JOptionPane.YES_OPTION) {
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(new File(filePath));
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "无法打开文件: " + ex.getMessage(), "提示", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "当前系统不支持自动打开文件，请手动打开。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出报表失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();

            // 提供备选方案
            int retryOption = JOptionPane.showConfirmDialog(this,
                    "导出失败，是否尝试导出为CSV格式？\n错误详情: " + e.getMessage(),
                    "导出失败",
                    JOptionPane.YES_NO_OPTION);

            if (retryOption == JOptionPane.YES_OPTION) {
                exportAsCSV(table);
            }
        }
    }

    /**
     * 导出报表到CSV（UTF-8编码）
     *
     * @note 自动转义特殊字符
     */
    private void exportAsCSV(JTable table) {
        try {
            // 创建文件选择对话框，用于让用户选择保存位置和文件名
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存营业报表 (CSV)");
            // 设置默认文件名：营业报表_年月日_时分秒.csv
            fileChooser.setSelectedFile(new File("营业报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".csv"));
            // 显示保存对话框，获取用户选择结果
            int userSelection = fileChooser.showSaveDialog(this);
            // 如果用户点击取消或关闭对话框，直接返回不执行导出
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }
            // 获取用户选择的文件对象
            File fileToSave = fileChooser.getSelectedFile();
            // 获取文件的绝对路径
            String filePath = fileToSave.getAbsolutePath();
            // 如果文件路径不以 .csv 结尾，自动添加扩展名
            if (!filePath.toLowerCase().endsWith(".csv")) {
                filePath += ".csv";
            }

            // 创建 StringBuilder 用于高效拼接 CSV 内容
            StringBuilder csvContent = new StringBuilder();
            // 获取表格的数据模型
            TableModel model = table.getModel();

            // 写入表头
            for (int i = 0; i < model.getColumnCount(); i++) {
                // 获取列名并进行 CSV 转义（处理逗号、引号等特殊字符）
                csvContent.append(escapeCSV(model.getColumnName(i)));
                // 如果不是最后一列，添加逗号分隔符
                if (i < model.getColumnCount() - 1) csvContent.append(",");
            }        // 表头行结束，添加换行符
            csvContent.append("\n");

            // 写入数据
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object value = model.getValueAt(i, j);
                    csvContent.append(escapeCSV(value != null ? value.toString() : ""));
                    if (j < model.getColumnCount() - 1) csvContent.append(",");
                }
                csvContent.append("\n");
            }

            // 保存文件  使用 UTF-8 编码写入文件，确保中文不乱码
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath),
                    csvContent.toString().getBytes("UTF-8"));

            JOptionPane.showMessageDialog(this, "CSV格式报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "CSV导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 转义CSV特殊字符（逗号/引号/换行）
     *
     * @note 符合RFC4180标准
     */
    private String escapeCSV(String value) {
        // 空值直接返回空字符串
        if (value == null) return "";
        // 如果包含特殊字符（逗号/双引号/换行），需要包裹并转义
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // 将内部的双引号替换为两个双引号（CSV转义规则）
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 获取支持中文的字体（优先系统中文字体）
     *
     * @param size 字体大小
     */
    //7.4 JFreeChart 圖表集成與中文適配方案
    //技術說明：集成 JFreeChart 庫實現營業數據可視化，並通過字體探測與動態替換解決中文亂碼問題。
    //通過 canDisplayUpTo() 動態探測系統可用中文字體，實現跨平台兼容（Windows/macOS/Linux）。將字體配置封裝為獨立方法，便於在柱狀圖、餅圖、圖例等多處複用。這種「探測 + 兜底」策略確保了圖表在任何環境下都能正確顯示中文標籤。
    private Font getChineseFont(int size) {
        // 尝试使用系统支持的中文字体
        String[] chineseFonts = {"微软雅黑", "Microsoft YaHei", "宋体", "SimSun", "黑体", "SimHei", "KaiTi", "楷体"};

            // 获取本地图形环境，用于查询系统可用字体
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font font = null;

        // 遍历候选字体列表，查找第一个能正常显示中文的字体
        for (String fontName : chineseFonts) {
            if (ge.getAvailableFontFamilyNames().length > 0) {
                // 尝试创建指定名称和大小的字体
                font = new Font(fontName, Font.PLAIN, size);
                // canDisplayUpTo 返回 -1 表示该字体能完全显示传入的字符串
                if (font.canDisplayUpTo("中文") == -1) {
                    return font;
                }
            }
        }

        // 如果找不到中文字体，使用默认字体并尝试显示中文
        return new Font("Dialog", Font.PLAIN, size);
    }

    /**
     * 为JFreeChart应用中文字体（解决乱码）
     *
     * @note 自动适配饼图/柱状图并调整标签间距
     */
    private void applyChineseFontToChart(JFreeChart chart) {
        Font chineseFont = getChineseFont(12);
        Font chineseFontBold = getChineseFont(14);
        chineseFontBold = chineseFontBold.deriveFont(Font.BOLD);

        // 设置标题
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(chineseFontBold);
        }

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(chineseFont);
            legend.setItemPaint(Color.BLACK);
        }

        // 设置图表区域字体
        Plot plot = chart.getPlot();
        if (plot instanceof PiePlot) {
            PiePlot piePlot = (PiePlot) plot;
            piePlot.setLabelFont(chineseFont);
            // 注意：PiePlot没有setLegendLabelFont方法，图例字体已在上面设置
        } else if (plot instanceof CategoryPlot) {
            // 柱状图/折线图：设置坐标轴标签字体
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            categoryPlot.getDomainAxis().setLabelFont(chineseFontBold);// X轴标题
            categoryPlot.getRangeAxis().setLabelFont(chineseFontBold);// Y轴标题
            categoryPlot.getDomainAxis().setTickLabelFont(chineseFont); // X轴刻度
            categoryPlot.getRangeAxis().setTickLabelFont(chineseFont); // Y轴刻度

            // 设置分类轴标签旋转45度，避免中文标签重叠
            if (categoryPlot.getDomainAxis() instanceof CategoryAxis) {
                CategoryAxis domainAxis = (CategoryAxis) categoryPlot.getDomainAxis();
                domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
            }
        }

        // 设置图表内边距，确保中文字符有足够显示空间（避免被裁剪）
        chart.setPadding(new RectangleInsets(15, 20, 15, 20));
    }

    /**
     * 导出取消预约统计到 Excel
     */
    private void exportForfeitedDepositsToExcel(JTable table) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存取消预约统计报表");
            fileChooser.setSelectedFile(new File("取消预约统计_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx"));
            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            // 创建目录（如果不存在）
            File parentDir = fileToSave.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用 Apache POI 创建 Excel
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("取消预约统计");

                // 创建表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setBorderBottom(BorderStyle.THIN);// 设置表头单元格的下边框样式为细线，确保表头与数据行之间有清晰的分隔线
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // 创建金额列样式
                CellStyle currencyStyle = workbook.createCellStyle();
                currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
                currencyStyle.setBorderBottom(BorderStyle.THIN);
                currencyStyle.setBorderTop(BorderStyle.THIN);
                currencyStyle.setBorderLeft(BorderStyle.THIN);
                currencyStyle.setBorderRight(BorderStyle.THIN);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                TableModel model = table.getModel();
                for (int i = 0; i < model.getColumnCount(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(model.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // 填充数据
                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        Cell cell = row.createCell(j);

                        // 设置边框
                        CellStyle borderStyle = workbook.createCellStyle();
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        cell.setCellStyle(borderStyle);

                        if (value == null || value.toString().trim().isEmpty()) {
                            cell.setCellValue("");
                            continue;
                        }

                        String cellValue = value.toString().trim();

                        // 金额列格式化（假设第 5 列是"没收金额"）
                        if (j == 5) { // 根据实际列索引调整
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Double.parseDouble(cleanValue));// 将清理并解析后的数值设置为单元格值，确保金额列以数字格式存储而非文本
                                    cell.setCellStyle(currencyStyle);
                                } else {
                                    cell.setCellValue(cellValue);
                                }
                            } catch (NumberFormatException ex) {
                                cell.setCellValue(cellValue);
                            }
                        } else {
                            cell.setCellValue(cellValue);
                        }
                    }
                }

                // 自动调整列宽
                for (int i = 0; i < model.getColumnCount(); i++) {
                    // 自动调整列宽：基于内容宽度乘以 1.5 倍系数，最大不超过 6000 像素，确保数据完整显示且美观
                    sheet.setColumnWidth(i, Math.min((int) (sheet.getColumnWidth(i) * 1.5), 6000));
                }

                // 保存文件
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }

                JOptionPane.showMessageDialog(this,
                        "报表已成功导出到:\n" + filePath,
                        "导出成功",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "导出报表失败: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 显示临时消息提示对话框
     */
     public void showTimeMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 创建取消预约统计面板
     */
    private JPanel createForfeitedDepositPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 顶部：控制区域 (日期选择 + 查询按钮) =====
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(BorderFactory.createTitledBorder("筛选条件"));

        controlPanel.add(new JLabel("开始日期:"));
        JDateChooser startDateChooser = new JDateChooser();
        startDateChooser.setDateFormatString("yyyy-MM-dd");
        startDateChooser.setDate(new java.util.Date()); // 默认今天
        startDateChooser.setPreferredSize(new Dimension(120, 28));
        controlPanel.add(startDateChooser);

        controlPanel.add(new JLabel("结束日期:"));
        JDateChooser endDateChooser = new JDateChooser();
        endDateChooser.setDateFormatString("yyyy-MM-dd");
        endDateChooser.setDate(new java.util.Date()); // 默认今天
        endDateChooser.setPreferredSize(new Dimension(120, 28));
        controlPanel.add(endDateChooser);

        JButton queryButton = new JButton("查询");
        queryButton.setPreferredSize(new Dimension(80, 30));
        controlPanel.add(queryButton);

        panel.add(controlPanel, BorderLayout.NORTH);

        // ===== 中间：统计汇总区域 =====
        JPanel summaryPanel = new JPanel(new GridLayout(1, 4, 10, 10));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("统计汇总"));

        JLabel totalRecordsLabel = new JLabel("总取消次数: 0", SwingConstants.CENTER);
        totalRecordsLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        summaryPanel.add(totalRecordsLabel);

        JLabel totalAmountLabel = new JLabel("总没收金额: 0.00 元", SwingConstants.CENTER);
        totalAmountLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        totalAmountLabel.setForeground(Color.RED);
        summaryPanel.add(totalAmountLabel);

        // 占位标签，保持布局平衡
        JLabel label3 = new JLabel("");
        summaryPanel.add(label3);
        JLabel label4 = new JLabel("");
        summaryPanel.add(label4);

        panel.add(summaryPanel, BorderLayout.CENTER);

        // ===== 下部：数据表格 =====
        String[] columnNames = {"记录ID", "预约号", "客户姓名", "客户电话", "原定预约时间", "没收金额(元)", "取消时间", "取消原因"};
            // 创建表格模型，重写isCellEditable使表格只读
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 只读
            }
        };

        JTable reportTable = new JTable(tableModel);
        reportTable.setRowHeight(25);
        reportTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        reportTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        reportTable.setFillsViewportHeight(true);

        // 设置列宽
        reportTable.getColumnModel().getColumn(0).setPreferredWidth(60);  // ID
        reportTable.getColumnModel().getColumn(1).setPreferredWidth(120); // 预约号
        reportTable.getColumnModel().getColumn(2).setPreferredWidth(100); // 姓名
        reportTable.getColumnModel().getColumn(3).setPreferredWidth(110); // 电话
        reportTable.getColumnModel().getColumn(4).setPreferredWidth(130); // 原定时间
        reportTable.getColumnModel().getColumn(5).setPreferredWidth(100); // 金额
        reportTable.getColumnModel().getColumn(6).setPreferredWidth(150); // 取消时间
        reportTable.getColumnModel().getColumn(7).setPreferredWidth(200); // 原因

        //  创建右对齐渲染器，用于金额列
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);    // 设置水平对齐方式为右对齐
        reportTable.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);   // 将渲染器应用到金额列（第5列，索引从0开始）

        JScrollPane scrollPane = new JScrollPane(reportTable);
        scrollPane.setPreferredSize(new Dimension(950, 300));
        panel.add(scrollPane, BorderLayout.SOUTH);

        // ===== 事件处理：点击查询 =====
        queryButton.addActionListener(e -> {
            java.util.Date startDate = startDateChooser.getDate();
            java.util.Date endDate = endDateChooser.getDate();

            if (startDate == null || endDate == null) {
                JOptionPane.showMessageDialog(panel, "请选择完整的日期范围");
                return;
            }
            if (startDate.after(endDate)) {
                JOptionPane.showMessageDialog(panel, "开始日期不能晚于结束日期");
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String startStr = sdf.format(startDate);
            String endStr = sdf.format(endDate);

            // 调用 Controller 获取数据
            try {
                List<Map<String, Object>> data = controller.getForfeitedDepositsReport(startStr, endStr);

                // 清空旧数据
                tableModel.setRowCount(0);// 清空表格模型中的所有历史数据，确保新查询结果不会与旧数据混合显示

                double totalAmount = 0.0;
                int totalCount = 0;

                if (data != null) {
                    for (Map<String, Object> row : data) {
                        //按表格列顺序组装行数据，从 Map 中提取对应字段值
                        Object[] rowData = {
                                row.get("记录ID"),
                                row.get("预约号"),
                                row.get("客户姓名"),
                                row.get("客户电话"),
                                row.get("原定预约时间"),
                                row.get("没收金额"),
                                row.get("取消时间"),
                                row.get("取消原因")
                        };
                        tableModel.addRow(rowData);

                        // 累加统计
                        totalCount++;
                        if (row.get("没收金额") instanceof Number) {
                            totalAmount += ((Number) row.get("没收金额")).doubleValue();
                        }
                    }
                }

                // 更新汇总标签
                totalRecordsLabel.setText("总取消次数: " + totalCount);
                totalAmountLabel.setText("总没收金额: " + String.format("%.2f", totalAmount) + " 元");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "查询失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        return panel;
    }

}
