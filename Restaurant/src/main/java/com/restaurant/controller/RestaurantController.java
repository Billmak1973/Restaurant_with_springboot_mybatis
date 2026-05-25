package com.restaurant.controller;

import com.restaurant.entity.*;
import com.restaurant.service.MenuItemService;
import com.restaurant.service.OrderService;
import com.restaurant.service.RestaurantService;
import com.restaurant.util.OperationResult;
import com.restaurant.view.OrderSystemGUI;
import com.restaurant.view.ReservationMatchCallback;
import com.restaurant.view.RestaurantView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RestaurantController {

    public final RestaurantService service;
    private RestaurantView view;
    private OrderSystemGUI frame;
    private final MenuItemService menuItemService;  //  新增注入
    private final OrderService orderService;  //  新增注入

    //1.5. 依賴注入與構造函數綁定
    //技術說明：使用 Spring 的依賴注入 (DI) 管理 Service 和 Controller 之間的依賴關係，推薦使用構造函數注入而非字段注入。
    //構造函數注入確保了依賴不可變且易於測試。Spring 容器自動解析參數並實例化 Controller，避免了手動 new 對象導致的 Bean 管理混亂。
    @Autowired
    public RestaurantController(RestaurantService service, MenuItemService menuItemService, OrderService orderService) {
        this.service = service;
        this.menuItemService = menuItemService;
        this.orderService = orderService;
    }


    public void setView(RestaurantView view) {
        this.view = view;
        view.setController(this);
        // 🔧【新增】建立 Service ↔ View 的回调连接
        if (service != null && view instanceof ReservationMatchCallback) {
            service.setReservationMatchCallback((ReservationMatchCallback) view);
            System.out.println("✅ 预约匹配回调已连接");
        }

        bindEvents();
        initializeTablesDisplay();
    }

    public void setFrame(OrderSystemGUI frame) {
        this.frame = frame;
    }

    private void initializeTablesDisplay() {
        if (view == null || service == null) {
            return;
        }
        try {
            List<Tables> tables = service.getAllTables();
            if (tables != null && !tables.isEmpty()) {
                view.setTables(tables);
                System.out.println("已初始化显示 " + tables.size() + " 张餐桌");
            } else {
                System.out.println("暂无餐桌数据");
            }
        } catch (Exception e) {
            System.err.println("初始化餐桌显示失败: " + e.getMessage());
        }
    }

    //5.5 ActionListener 綁定與事件分發解耦
    //技術說明：View 層不直接編寫業務邏輯，而是通過提供 setXxxListener() 方法暴露事件註冊入口。Controller 層在 bindEvents() 中將自身的業務方法（方法引用或 Lambda）綁定到這些入口上，實現「UI 與邏輯徹底解耦」。
    //這種設計避免了在 JButton.addActionListener() 中直接寫業務代碼的混亂。View 僅負責「收集用戶輸入」並「調用綁定的回調」，Controller 負責「調度 Service」並「更新 UI 狀態」，嚴格遵循單一職責原則（SRP）。
    private void bindEvents() {
        view.updateTableStatusDisplay(service.getAllTables());
        view.setAddGroupListener(e -> handleAddGroup(e));
        view.setSplitTableListener(e -> handleSplitTable(e));
        view.setRecombineTableListener(e -> handleRecombineTable(e));
        view.setOrderListener(e -> handleOpenOrderSystem());
        view.setCheckoutListener(e -> handleCheckoutAction(e));
        view.setChangeTableListener(e -> handleChangeTable());
        view.setClearAllListener(e -> handleClearAll(e));
        view.setCloseDayListener(e -> handleCloseDay(e));
        view.setReportListener(e -> handleShowBusinessReport());
        view.setReserveTableListener(e -> handleReserveTable(e));
        view.setQueueManagementListener(e -> view.showQueueManagementDialog());
        view.setSelectTableListener(e -> view.showSelectTableDialog());
    }

    //5.6 MVC 模式在 Spring + Swing 混合架構中的變體
    //傳統 Web MVC 中，View 是 HTML/JSP，由框架自動渲染；而在本系統中，View 是主動渲染的 Swing 窗口。架構變體為：Spring 容器 -> Controller (Spring Bean) -> View (Swing)。Controller 持有 Service 引用（Spring 注入）和 View 引用（手動注入），成為真正的「協調中樞」。
    //這是一種針對桌面端的 MVC 變體。Controller 作為 Spring Bean 享受事務管理與 DI 便利，同時通過顯式持有的 view 引用完成 UI 狀態同步，完美彌合了框架生命周期與桌面事件循環的鴻溝。
    private void handleAddGroup(ActionEvent e) {
        try {
            // 1. 获取输入
            String input = view.getGroupSizeInput();

            if (input == null || input.isEmpty()) {
                view.showError("请输入顾客组人数");
                return;
            }

            int groupSize = Integer.parseInt(input);

            if (groupSize <= 0) {
                view.showError("人数必须大于 0");
                return;
            }else if(groupSize > 12) {
                view.showError("人数大于12,需要提前訂餐");
                return;
            }

            // 2. 调用 Service 层业务逻辑（返回 CustomerGroup 对象）
            CustomerGroup group = service.addCustomerGroup(groupSize);

            if (group != null) {
                // 添加成功：根据分配状态给出差异化反馈
                String message;
                if (group.isAssigned()) {
                    // 已分配餐桌
                    message = "顾客组 #" + group.getCallNumber() +
                            " (" + group.getGroupSize() + "人) 已入座餐桌 #" +
                            group.getTableId();
                } else {
                    // 未分配：进入等待队列
                    message = "顾客组 #" + group.getCallNumber() +
                            " (" + group.getGroupSize() + "人) 已加入等待队列";
                    view.showInfo("已入队\n" + message);
                }

                // 记录日志 + 清空输入 + 刷新视图
                view.clearGroupSizeInput();
                refreshTablesDisplay();
                view.updateTableStatusDisplay(service.getAllTables());

            } else {
                // 添加失败：餐厅未营业或系统异常
                view.showError("添加失败：餐厅未营业或系统错误");
            }

        } catch (NumberFormatException ex) {
            view.showError("请输入有效的人数");

        } catch (Exception ex) {
            ex.printStackTrace();
            view.showError("系统异常：" + ex.getMessage());
        }
    }


    public void refreshTablesDisplay() {
        service.refreshTableCache();
        if (view != null) {
            view.updateTablesDisplay(service.getAllTables());
        }
    }

    private void refreshSingleTable(String displayId) {
        if (view != null && service != null) {
            Tables updatedTable = service.getTableById(displayId);
            System.out.println(" 刷新餐桌 #" + displayId +
                    " 状态=" + (updatedTable != null ? updatedTable.getStatus() : "null"));
            if (updatedTable != null) {
                view.refreshTableButton(displayId, updatedTable);
            }
        }
    }

    /**
     * 處理拆分餐桌事件
     */
    private void handleSplitTable(ActionEvent e) {
        // 1. 獲取用戶輸入
        String inputdisplayId = JOptionPane.showInputDialog(
                view,
                "輸入要拆分的餐桌編號（只能拆分 2 人或 4 人空閒桌）:",
                "拆分餐桌",
                JOptionPane.QUESTION_MESSAGE
        );

        if (inputdisplayId == null || inputdisplayId.trim().isEmpty()) {
            return; // 用戶取消
        }

        final String displayId = inputdisplayId.trim();

        try {
            // 2. 調用 Service 執行拆分
            List<Tables> subTables = service.splitTable(displayId);

            // 3. 操作成功：刷新界面
            SwingUtilities.invokeLater(() -> {
                // 🔧 選項 1: 全面刷新（簡單可靠）
                refreshTablesDisplay();
                //如果需要单独更新状态面板
                view.updateTableStatusDisplay(service.getAllTables());

                JOptionPane.showMessageDialog(view,
                        "餐桌 #" + displayId + " 拆分成功！\n" +
                                "新增子桌: " + subTables.get(0).getDisplayId() + ", " +
                                subTables.get(1).getDisplayId(),
                        "操作成功",
                        JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (IllegalArgumentException | IllegalStateException ex) {
            // 業務規則驗證失敗
            SwingUtilities.invokeLater(() -> {
                view.showError("拆分失敗: " + ex.getMessage());
            });
        } catch (Exception ex) {
            // 系統異常
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                view.showError("系統錯誤: " + ex.getMessage());
            });
        }
    }


    private void handleRecombineTable(ActionEvent e) {
        // 1. 獲取用戶輸入
        String input = JOptionPane.showInputDialog(
                view,
                "輸入要恢復的主桌編號（如 7）:\n" +
                        "系統將自動查找並合併其子桌（如 7a, 7b）",
                "合併餐桌",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return;
        }

        final String mainTableDisplayId = input.trim();

        Tables mainTable = service.getTableById(mainTableDisplayId);
        final List<String> subTableDisplayIds;  // ← 只聲明，不初始化

        if (mainTable != null && mainTable.isSplit()) {
            List<Tables> subTables = service.getSubTablesByMainTableId(mainTable.getTableId());
            subTableDisplayIds = subTables.stream()
                    .map(Tables::getDisplayId)
                    .collect(Collectors.toList());
        } else {
            //  但如果 if 条件不满足，变量就没有被初始化！
            // 编译错误：变量可能未初始化
            subTableDisplayIds = Collections.emptyList();

        }


        // 2. 異步執行業務邏輯
        SwingWorker<Tables, Void> worker = new SwingWorker<>() {
            @Override
            protected Tables doInBackground() {
                return service.recombineTables(mainTableDisplayId);  // 執行合併
            }

            @Override
            protected void done() {
                try {
                    Tables restoredTable = get();

                    SwingUtilities.invokeLater(() -> {
                        // 3. 刷新界面
                        view.refreshTableButton(mainTableDisplayId, restoredTable);
                        service.refreshTableCache();
                        view.updateTablesDisplay(service.getAllTables());
                        view.updateTableStatusDisplay(service.getAllTables());

                        // 🔧【關鍵】使用預先收集的子桌信息（不再查詢數據庫）
                        String subTableNames = subTableDisplayIds.isEmpty() ? "無" :
                                String.join(", ", subTableDisplayIds);

                        JOptionPane.showMessageDialog(view,
                                "餐桌 #" + mainTableDisplayId + " 合併成功！\n" +
                                        "子桌已恢復為主桌狀態",
                                "操作成功",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (IllegalArgumentException | IllegalStateException ex) {
                    SwingUtilities.invokeLater(() -> {
                        view.showError("合併失敗: " + ex.getMessage());

                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        view.showError("系統錯誤: " + ex.getMessage());
                    });
                }
            }
        };
        worker.execute();
    }


    private void handleChangeTable() {
        try {
            // 1. 调用 View 获取输入
            String[] inputs = view.showChangeTableDialog();
            if (inputs == null) {
                return; // 用户取消了操作
            }

            String fromInput = inputs[0].trim();
            String toInput = inputs[1].trim();

            // 2. 调用 Service 层执行换桌逻辑（事务由 @Transactional 管理）
            boolean success = service.changeTable(fromInput, toInput);

            if (success) {
                // 3. 刷新显示 + 记录日志
                SwingUtilities.invokeLater(() -> {
                    refreshTablesDisplay();
                    view.updateTableStatusDisplay(service.getAllTables());
                    view.showInfo("换桌成功！");
                });
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // 业务规则验证失败 → 友好提示
            SwingUtilities.invokeLater(() -> {
                view.showError("换桌失败: " + ex.getMessage());
            });
        } catch (Exception ex) {
            // 系统异常 → 记录日志 + 提示
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                view.showError("系统错误: " + ex.getMessage());
            });
        }
    }



    public boolean hasWaitingCustomers() {
        return service.hasWaitingCustomers();
    }


    private void handleClearAll(ActionEvent e) {
        try {
            // 🔧 调用 Service 获取结果对象
            Map<String, Object> result = service.clearAllTables();
            boolean success = (Boolean) result.getOrDefault("success", false);
            String message = (String) result.getOrDefault("message", "");
            boolean hasWaiting = (Boolean) result.getOrDefault("hasWaitingCustomers", false);
            boolean hasError = (Boolean) result.getOrDefault("error", false);

            // 🔧 所有 JOptionPane 移到此处，通过 View 显示
            if (hasError) {
                view.showError("清空餐桌时发生系统错误: " + message);
                return;
            }

            if (success) {
                // 🔧【核心】根据详情构建清理摘要
                StringBuilder summary = new StringBuilder("清理完成:\n");
                @SuppressWarnings("unchecked")
                Map<String, Boolean> details = (Map<String, Boolean>) result.get("cleanedDetails");

                if (details != null) {
                    if (Boolean.TRUE.equals(details.get("subTables"))) {
                        summary.append("✓ 子桌已清理\n");
                    }
                    if (Boolean.TRUE.equals(details.get("mergedTables"))) {
                        summary.append("✓ 合并桌已清理\n");
                    }
                    if (Boolean.TRUE.equals(details.get("groupedTables"))) {
                        summary.append("✓ 聚餐桌已清理\n");
                    }
                    if (Boolean.TRUE.equals(details.get("mainTables"))) {
                        summary.append("✓ 主桌已清理");
                    }
                }

                // 🔧 通过 View 显示成功消息（移除末尾多余换行）
                String summaryText = summary.toString().trim();
                if (!summaryText.equals("清理完成:")) {
                    view.showInfo(summaryText);
                } else {
                    view.showInfo(message);
                }

                // 🔧 仅当有排队顾客时才询问自动入座（通过 View 交互）
                if (hasWaiting) {
                    int response = view.showConfirmDialog(
                            "📋 有顾客在排队，是否自动安排入座？",
                            "自动入座");
                    if (response == JOptionPane.YES_OPTION) {
                        service.checkAndAssignWaitingCustomers();
                        view.showInfo("已自动安排等待顾客入座");
                    }
                }
            } else {
                view.showInfo(message);
            }

            // 刷新界面
            refreshTablesDisplay();
            view.updateTableStatusDisplay(service.getAllTables());

        } catch (Exception ex) {
            // ✅ 保留 try-catch
            view.showError("清空餐桌时发生错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 🔧 處理結束營業/開始營業事件（核心協調邏輯）
     */
    private void handleCloseDay(ActionEvent e) {
        if (service == null || view == null) return;

        try {
            if (service.isOpenForBusiness()) {
                // ═══════════════════════════════════════════════════════════
                // 【打烊流程】
                // ═══════════════════════════════════════════════════════════

                // 1️⃣ 純內存查詢未結賬餐桌（零 SQL，避免 UI 卡頓）
                List<String> unpaidTables = service.getTablesWithUnpaidOrdersInMemory();

                // 2 未結賬訂單提醒（不阻止打烊，僅提示）
                if (!unpaidTables.isEmpty()) {
                    boolean confirmed = view.showUnpaidWarningDialog(unpaidTables);
                    if (!confirmed) {
                        return; // 用戶取消，直接返回
                    }
                }

                // 3️⃣ 二次確認（防止誤操作）
                int confirm = JOptionPane.showConfirmDialog(
                        view,
                        "<html><b>確定要結束營業嗎？</b><br><br>" +
                                "🔒 此操作將：<br>" +
                                "• 停止接待新顧客<br>" +
                                "• 保留未結賬訂單至次日<br>" +
                                "• 記錄當日營業數據</html>",
                        "確認結束營業",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }

                // 4️⃣ 調用 Service 執行打烊（事務內完成）
                service.closeForDayWithPersistence();

                // 5️⃣ 更新 UI（僅負責顯示，不處理業務）
                SwingUtilities.invokeLater(() -> {
                    view.clearGroupSizeInput();
                    refreshTablesDisplay(); // 刷新餐桌狀態
                    view.setCloseDayButtonText(false); // 按鈕變為「開始營業」
                    view.updateBusinessStatusDisplay(false); // 狀態欄變紅
                });

            } else {
                // ═══════════════════════════════════════════════════════════
                // 【開業流程】（對稱設計）
                // ═══════════════════════════════════════════════════════════

                int confirm = JOptionPane.showConfirmDialog(
                        view,
                        "<html><b>確定要開始營業嗎？</b><br><br>" +
                                "🟢 餐廳將恢復：<br>" +
                                "• 接待新顧客<br>" +
                                "• 處理排隊隊列<br>" +
                                "• 初始化當日營業狀態</html>",
                        "確認開始營業",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }

                // 調用 Service 執行開業
                service.openForBusinessWithPersistence();

                // 更新 UI
                SwingUtilities.invokeLater(() -> {
                    refreshTablesDisplay();
                    view.setCloseDayButtonText(true); // 按鈕變為「結束營業」
                    view.updateBusinessStatusDisplay(true); // 狀態欄變綠
                });
            }

        } catch (Exception ex) {
            // 統一錯誤處理
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                String action = service.isOpenForBusiness() ? "打烊" : "開業";
                JOptionPane.showMessageDialog(view,
                        action + "失敗：" + ex.getMessage(),
                        "操作錯誤",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void handleShowBusinessReport() {
        view.showBusinessReportDialog();
    }


    public void updateQueueDisplay() {
        if (view != null && service != null) {
            try {
                // 直接从 Service 获取快照并更新 View
                view.updateQueueDisplay(
                        (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT"),
                        (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT"),
                        (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 处理餐桌点击事件（View → Controller 入口）
     *
     * @param displayId 餐桌显示ID（如 "7" 或 "7a"）
     */
    public void handleTableClick(String displayId) {
        try {
            // 1. 查询餐桌
            Tables table = service.getTableById(displayId);
            if (table == null) {
                showError("餐桌 #" + displayId + " 不存在");
                return;
            }

            // 2. 根据状态分发处理
            switch (table.getStatus()) {
                case OCCUPIED -> handleOccupiedTable(table);      // 占用中 → 离店确认
                case SETTING_UP -> handleSettingUpTable(table);   // 准备中 → 清理确认
                case SPLITTING -> view.showInfo(                  // 拆分中 → 提示
                        "餐桌 #" + displayId + " 当前处于拆分状态，暂不可操作");
                case VACANT -> view.showInfo(                     // 空闲 → 提示
                        "餐桌 #" + displayId + " 当前空闲，可添加顾客组");
                case RESERVED -> handleReservedTableAction(table);
                default -> showError("未知餐桌状态: " + table.getStatus());
            }

        } catch (Exception e) {
            showError("操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 处理占用中餐桌的离店逻辑（主入口 - 分类分发）
     */
    private void handleOccupiedTable(Tables table) {
        String displayId = table.getDisplayId();
        Tables.OrderStatus orderStatus = table.getOrderStatus();

        // ═══════════════════════════════════════════════════════════
        // 🔧【分类处理】根据餐桌类型分别调用对应方法
        // ═══════════════════════════════════════════════════════════

        // ── 情况1：合并餐桌（仅2张桌）─
        if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
            handleMergedTableDeparture(table, displayId, orderStatus);
            return;
        }

        // ── 情况2：聚餐桌（3张或以上）─
        if (table.getTableType() == Tables.TableType.GROUPED && table.getGroupWith() != null) {
            handleGroupedTableDeparture(table, displayId, orderStatus);
            return;
        }

        // ── 情况3：普通单餐桌（原有逻辑）─
        handleSingleTableDeparture(table, displayId, orderStatus);
    }

    // ═══════════════════════════════════════════════════════════
// 🔧【子方法1】处理合并餐桌离店（仅2张桌 - 您要求的格式）
// ═══════════════════════════════════════════════════════════
    private void handleMergedTableDeparture(Tables table, String displayId, Tables.OrderStatus orderStatus) {
        Tables partner = service.getMergedPartnerTable(displayId);
        if (partner != null && partner.getStatus() != Tables.TableStatus.OCCUPIED) {
            showError("伙伴餐桌 #" + partner.getDisplayId() + " 状态异常，无法完成合并离店");
            return;
        }

        CustomerGroup group = table.getCurrentGroup();
        if (group == null && partner != null) {
            group = partner.getCurrentGroup();
        }
        if (group == null) {
            showError("未找到关联顾客组，无法完成离店");
            return;
        }

        // 计算用餐时长
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String duration = "0分钟";
        if (table.getStartTime() != null) {
            long minutes = java.time.Duration.between(table.getStartTime(), now).toMinutes();
            duration = minutes + "分钟";
        }
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String endTimeFormatted = now.format(formatter);

        // 🔧 合并餐桌专用格式：当前餐桌 + 伙伴餐桌
        String partnerDisplayId = partner != null ? partner.getDisplayId() : "-";
        String partnerStatus = partner != null ? partner.getStatus().getDisplayName() : "-";

        String message = String.format(
                "<html><b>合并餐桌 #%s + #%s 离店确认</b><br><br>" +
                        "<font color='#d32f2f'>⚠️ 此操作将同时处理两张餐桌</font><br><br>" +
                        "<b>当前餐桌:</b> #%s (%s)<br>" +
                        "<b>伙伴餐桌:</b> #%s (%s)<br><br>" +
                        "顾客组: <b>#%d</b> (<font color='#1976d2'>%d人</font>)<br>" +
                        "开始时间: %s<br>" +
                        "结束时间: %s<br>" +
                        "总用餐时长: <b>%s</b><br><br>" +
                        "<font color='#d32f2f'><b>确认让此组合并餐桌的顾客离开?</b></font><br>" +
                        "<small>（两张餐桌将同时变为「准备中」状态）</small></html>",
                displayId, partnerDisplayId,
                displayId, table.getStatus().getDisplayName(),
                partnerDisplayId, partnerStatus,
                group.getCallNumber(), group.getGroupSize(),
                table.getFormattedStartTime(), endTimeFormatted, duration
        );

        int confirm = JOptionPane.showConfirmDialog(
                view, message, "合并餐桌离店确认",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // 检查订单状态
            if (orderStatus == Tables.OrderStatus.ORDERED_FINISHED ||
                    orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED) {
                JOptionPane.showMessageDialog(view,
                        "<html><b style='color:#d32f2f;'>⚠️ 不能离店！</b><br><br>" +
                                "该餐桌尚有未结账的订单<br>" +
                                "当前订单状态：<b>" + orderStatus.getDisplayName() + "</b><br><br>" +
                                "请先完成结账操作，然后才能让顾客离开。",
                        "未结账警告", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                service.processCustomerDeparture(displayId);
                SwingUtilities.invokeLater(() -> {
                    refreshSingleTable(displayId);
                    if (partner != null) {
                        refreshSingleTable(partner.getDisplayId());
                    }
                    view.updateTableStatusDisplay(service.getAllTables());
                });
            } catch (Exception e) {
                showError("离店操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
// 🔧【子方法2】处理聚餐桌离店（3张或以上 - 专属格式！）
// 格式：主餐桌：13，其余餐桌：14,15（无论点击哪张，主桌始终是最小编号）
// ═══════════════════════════════════════════════════════════
    private void handleGroupedTableDeparture(Tables table, String displayId, Tables.OrderStatus orderStatus) {
        // 解析 group_with 获取所有关联桌（格式："7,8,9"）
        String[] groupIds = table.getGroupWith().split(",");
        List<Tables> groupedTables = new ArrayList<>();

        for (String id : groupIds) {
            String trimmedId = id.trim();
            if (!trimmedId.isEmpty()) {
                Tables groupedTable = service.getTableById(trimmedId);
                if (groupedTable != null) {
                    groupedTables.add(groupedTable);
                }
            }
        }

        if (groupedTables.isEmpty()) {
            showError("聚餐桌 #" + displayId + " 未找到关联餐桌");
            return;
        }

        // 🔧【核心】按 displayId 数字排序，确保主桌（最小编号）始终排在第一位
        groupedTables.sort(Comparator.comparingInt(t ->
                Integer.parseInt(t.getDisplayId().replaceAll("[^0-9]", ""))
        ));

        // 验证所有关联餐桌状态
        for (Tables groupedTable : groupedTables) {
            if (groupedTable.getStatus() != Tables.TableStatus.OCCUPIED) {
                showError("关联餐桌 #" + groupedTable.getDisplayId() +
                        " 状态异常（" + groupedTable.getStatus().getDisplayName() +
                        "），无法完成离店");
                return;
            }
        }

        // 获取顾客组（聚餐桌共享同一个）
        CustomerGroup group = null;
        for (Tables groupedTable : groupedTables) {
            if (groupedTable.getCurrentGroup() != null) {
                group = groupedTable.getCurrentGroup();
                break;
            }
        }
        if (group == null) {
            showError("未找到关联顾客组，无法完成离店");
            return;
        }

        // 计算用餐时长（使用主桌的开始时间）
        Tables mainTable = groupedTables.get(0);  // ✅ 已排序，第一个永远是主桌
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String duration = "0分钟";
        if (mainTable.getStartTime() != null) {
            long minutes = java.time.Duration.between(mainTable.getStartTime(), now).toMinutes();
            duration = minutes + "分钟";
        }
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String endTimeFormatted = now.format(formatter);

        // 🔧【聚餐桌专属格式】主餐桌：13，其余餐桌：14,15
        String mainTableId = mainTable.getDisplayId();
        List<String> otherTableIds = new ArrayList<>();
        for (int i = 1; i < groupedTables.size(); i++) {  // 从第2张开始是"其余餐桌"
            otherTableIds.add(groupedTables.get(i).getDisplayId());
        }
        String otherTablesStr = String.join(",", otherTableIds);

        String message = String.format(
                "<html><b>聚餐桌 #%s 离店确认</b><br><br>" +
                        "<font color='#d32f2f'>⚠️ 此操作将同时处理 %d 张餐桌</font><br><br>" +
                        "<b>主餐桌：</b> #%s<br>" +           // ← 您要求的格式
                        "<b>其余餐桌：</b> %s<br><br>" +        // ← 您要求的格式
                        "顾客组: <b>#%d</b> (<font color='#1976d2'>%d人</font>)<br>" +
                        "开始时间: %s<br>" +
                        "结束时间: %s<br>" +
                        "总用餐时长: <b>%s</b><br><br>" +
                        "<font color='#d32f2f'><b>确认让此组聚餐桌的顾客离开?</b></font><br>" +
                        "<small>（所有餐桌将同时变为「准备中」状态）</small></html>",
                mainTableId,
                groupedTables.size(),
                mainTableId,              // 主餐桌编号
                otherTablesStr.isEmpty() ? "无" : otherTablesStr,  // 其余餐桌列表
                group.getCallNumber(), group.getGroupSize(),
                mainTable.getFormattedStartTime(), endTimeFormatted, duration
        );

        int confirm = JOptionPane.showConfirmDialog(
                view, message, "聚餐桌离店确认",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // 检查所有关联桌的订单状态
            for (Tables groupedTable : groupedTables) {
                Tables.OrderStatus groupedOrderStatus = groupedTable.getOrderStatus();
                if (groupedOrderStatus == Tables.OrderStatus.ORDERED_FINISHED ||
                        groupedOrderStatus == Tables.OrderStatus.ORDERED_UNFINISHED) {
                    JOptionPane.showMessageDialog(view,
                            "<html><b style='color:#d32f2f;'>⚠️ 不能离店！</b><br><br>" +
                                    "餐桌 #" + groupedTable.getDisplayId() + " 尚有未结账的订单<br>" +
                                    "当前订单状态：<b>" + groupedOrderStatus.getDisplayName() + "</b><br><br>" +
                                    "请先完成结账操作，然后才能让顾客离开。",
                            "未结账警告", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            try {
                // 🔧 传入主桌 displayId，Service 层会处理所有关联桌
                service.processCustomerDeparture(mainTable.getDisplayId());

                SwingUtilities.invokeLater(() -> {
                    // 刷新所有关联餐桌
                    for (Tables groupedTable : groupedTables) {
                        refreshSingleTable(groupedTable.getDisplayId());
                    }
                    String tableList = groupedTables.stream()
                            .map(Tables::getDisplayId)
                            .collect(Collectors.joining(","));
                    view.updateTableStatusDisplay(service.getAllTables());
                });
            } catch (Exception e) {
                showError("离店操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
// 🔧【子方法3】处理普通单餐桌离店（原有逻辑保持不变）
// ═══════════════════════════════════════════════════════════
    private void handleSingleTableDeparture(Tables table, String displayId, Tables.OrderStatus orderStatus) {
        CustomerGroup group = table.getCurrentGroup();
        if (group == null) {
            showError("未找到关联顾客组，无法完成离店");
            return;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String duration = "0分钟";
        if (table.getStartTime() != null) {
            long minutes = java.time.Duration.between(table.getStartTime(), now).toMinutes();
            duration = minutes + "分钟";
        }
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String endTimeFormatted = now.format(formatter);

        String message = String.format(
                "<html><b>餐桌 #%s 详情</b><br><br>" +
                        "状态: <font color='#1a75ff'>占用中</font><br>" +
                        "开始时间: %s<br>" +
                        "结束时间: %s<br>" +
                        "总时长: <b>%s</b><br><br>" +
                        "顾客组: <b>#%d</b> (<font color='#1976d2'>%d人</font>)<br><br>" +
                        "<font color='red'><b>确认让此桌顾客离开?</b></font></html>",
                displayId,
                table.getFormattedStartTime(), endTimeFormatted, duration,
                group.getCallNumber(), group.getGroupSize()
        );

        int confirm = JOptionPane.showConfirmDialog(
                view, message, "确认顾客离开",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            if (orderStatus == Tables.OrderStatus.ORDERED_FINISHED ||
                    orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED) {
                JOptionPane.showMessageDialog(view,
                        "<html><b style='color:#d32f2f;'>⚠️ 不能离店！</b><br><br>" +
                                "该餐桌尚有未结账的订单<br>" +
                                "当前订单状态：<b>" + orderStatus.getDisplayName() + "</b><br><br>" +
                                "请先完成结账操作，然后才能让顾客离开。",
                        "未结账警告", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                service.processCustomerDeparture(displayId);
                SwingUtilities.invokeLater(() -> {
                    refreshSingleTable(displayId);
                    view.updateTableStatusDisplay(service.getAllTables());
                });
            } catch (Exception e) {
                showError("离店操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理准备中餐桌的清理逻辑（修复版）
     */
    private void handleSettingUpTable(Tables table) {
        String displayId = table.getDisplayId();

        int confirm = JOptionPane.showConfirmDialog(
                view,
                "确定清理餐桌 #" + displayId + " 吗？",
                "确认清理",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // 🔧【关键1】先记录是否是子桌（用点击时的对象判断）
                boolean isSubTable =
                        table.getSubTableSuffix() != null &&
                                !table.getSubTableSuffix().isEmpty();

                // 执行清理（数据库+内存同步更新）
                service.cleanTable(displayId);

                // 刷新界面
                refreshSingleTable(displayId);
                view.updateTableStatusDisplay(service.getAllTables());

                // 清理后检查合并机会（ 必须在Controller层执行！）
                if (isSubTable) {
                    handleSubTableMergeCheck(displayId);
                } else {
                    service.checkAndAssignWaitingCustomers();
                }

            } catch (Exception e) {
                showError("清理失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void handleSubTableMergeCheck(String cleanedTableDisplayId) {
        try {
            // ✅ 直接调用 Service，由 Service 负责查库 + 业务判断
            RestaurantService.MergeOpportunity opportunity =
                    service.checkSubTableMergeOpportunity(cleanedTableDisplayId);

            if (!opportunity.isAvailable()) {
                service.checkAndAssignWaitingCustomers();
                return;
            }

            // ✅ 只负责视图交互
            boolean userConfirmed = view.showMergeConfirmationDialog(
                    opportunity.getSubTableA(),
                    opportunity.getSubTableB(),
                    opportunity.getMainTableDisplayId()
            );

            if (userConfirmed) {
                Tables mergedTable = service.recombineTables(opportunity.getMainTableDisplayId());
                view.showMergeResult(true, "✅ 合并成功！\n子桌 #" + opportunity.getSubTableA() +
                        " + #" + opportunity.getSubTableB() +
                        " → 主桌 #" + opportunity.getMainTableDisplayId());
                refreshTablesDisplay();  // ✅ 刷新视图
                service.checkAndAssignWaitingCustomers();
            } else {
                service.checkAndAssignWaitingCustomers();
            }
        } catch (Exception e) {
            e.printStackTrace();
            view.showMergeResult(false, "❌ 合并失败: " + e.getMessage());
        }
    }

    private void handleCheckoutAction(ActionEvent e) {
        // 显示结账对话框
        String input = view.showCheckoutDialog();

        //  修复：先检查是否"真正取消"（空字符串）
        if (input != null && input.isEmpty()) {
            // 用户点击 X 或取消，直接返回，不执行任何操作
            // System.out.println("用户取消结账");
            return;
        }

        // 🔧 如果返回 null，说明选择了外卖订单 → 显示订单列表
        if (input == null) {
            view.showTakeoutOrderListDialog();
            return;
        }

        // 解析输入（堂食订单）
        String[] parts = input.split(":", 2);
        if (parts.length != 2) {
            view.showError("输入格式错误");
            return;
        }

        String orderType = parts[0];
        String identifier = parts[1];

        if ("DINE_IN".equals(orderType)) {
            handleDineInCheckout(identifier);
        }
    }

    /**
     * 处理队列管理操作（增加/编辑/删除顾客组）
     *
     * @param callNumber    排队号码（编辑/删除时需要）
     * @param customerCount 客户数量（增加/编辑时需要）
     * @param isAdd         是否增加新顾客组
     * @param isEdit        是否编辑现有顾客组人数
     * @param isDelete      是否删除顾客组
     */
    public void handleQueueManagementAction(int callNumber, int customerCount,
                                            boolean isAdd, boolean isEdit, boolean isDelete) {
        try {
            // ═══════════════════════════════════════════════════════════
            // 【场景 1】增加新顾客组
            // ═══════════════════════════════════════════════════════════
            if (isAdd) {
                // 1. 参数验证
                if (customerCount <= 0 || customerCount > 12) {
                    view.showError("客户数量必须在1-12之间！");
                    return;
                }

                // 2. 调用 Service 层添加顾客组（已包含完整业务逻辑）
                CustomerGroup newGroup = service.addCustomerGroup(customerCount);

                if (newGroup != null) {
                    // 4. 检查并分配等待顾客（触发队列调度）
                    service.checkAndAssignWaitingCustomers();
                } else {
                    view.showError("添加顾客组失败：餐厅未营业或系统错误");
                    return;
                }
            }
            // ═══════════════════════════════════════════════════════════
            // 【场景 2】编辑或删除现有顾客组
            // ═══════════════════════════════════════════════════════════
            else {
                // 1. 通过 Service 层查找顾客组
                CustomerGroup targetGroup = service.findCustomerGroupByCallNumber(callNumber);
                if (targetGroup == null) {
                    view.showError("未找到排队号码 #" + callNumber + " 的顾客组！");
                    return;
                }

                // ── 删除操作 ──
                if (isDelete) {
                    // 获取顾客组所在队列类型
                    String queueType = service.getQueueTypeByGroupId(targetGroup.getGroup_id());
                    if (queueType == null) {
                        view.showError("顾客组 #" + callNumber + " 不在队列中，无法删除");
                        return;
                    }

                    // 执行删除（事务内完成）
                    service.removeFromQueue(targetGroup.getGroup_id(), queueType);

                    // 删除后释放资源，重新检查队列分配
                    service.checkAndAssignWaitingCustomers();
                }

                // ── 编辑操作 ──
                if (isEdit) {
                    // 参数验证
                    if (customerCount <= 0 || customerCount > 12) {
                        view.showError("客户数量必须在1-12之间！");
                        return;
                    }
                    // 已入座顾客组不能修改人数
                    if (targetGroup.isAssigned()) {
                        view.showError("已入座的顾客组不能修改人数！");
                        return;
                    }

                    // 执行人数变更
                    int originalSize = targetGroup.getGroupSize();
                    service.updateCustomerGroupSize(targetGroup, customerCount);

                    // 人数变更后重新评估餐桌分配
                    service.checkAndAssignWaitingCustomers();
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 【统一刷新界面】
            // ═══════════════════════════════════════════════════════════
            refreshTablesDisplay();      // 刷新餐桌显示
            updateQueueDisplay();        // 刷新队列显示

        } catch (IllegalArgumentException | IllegalStateException e) {
            // 业务规则验证失败 → 友好提示
            view.showError(e.getMessage());
        } catch (Exception e) {
            // 系统异常 → 兜底处理
            view.showError("操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void handleManualTableAssignment(
            String tableIdInput,
            int peopleCount,
            boolean isFromQueue,
            int callNumber,
            boolean isMerge,
            boolean isTwoSeat,
            boolean isFourSeat,
            boolean isSixSeat,
            boolean isAddGuests,
            boolean isShare,
            boolean isGrouped,
            int groupedTableCount
            ) {

        // ═══════════════════════════════════════════════════════════
        // 【步骤1】参数验证（保持在前端）
        // ═══════════════════════════════════════════════════════════
        if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
            view.showError("餐桌编号不能为空");
            return;
        }


        try {
            // ═══════════════════════════════════════════════════════════
            // 🔧【新增】添加客人场景：预先检查并显示确认对话框
            // ═══════════════════════════════════════════════════════════
            if (isAddGuests && !isFromQueue ) {
                // 只读查询餐桌状态（不修改数据）
                Tables table = service.getTableById(tableIdInput.trim());

                // 仅当餐桌已被占用时才显示确认
                if (table != null && table.getStatus() == Tables.TableStatus.OCCUPIED) {
                    // 获取当前顾客组人数（通过 Service 的只读方法）
                    Integer groupId = table.getCurrentGroupId();
                    int currentSize = 0;

                    if (groupId != null) {
                        CustomerGroup group = service.getCustomerGroupById(groupId);
                        if (group != null) {
                            currentSize = group.getGroupSize();
                        }
                    }

                    int remainingSeats = table.getPhysicalCapacity() - currentSize;

                    // 🔧 通过 View 显示确认对话框（MVC：View 负责交互）
                    boolean confirmed = view.showAddGuestsConfirmationDialog(
                            table.getDisplayId(),
                            currentSize,
                            remainingSeats,
                            peopleCount
                    );

                    if (!confirmed) {
                        view.showInfo("操作已取消");
                        return;  // 用户取消，直接返回，不调用 Service
                    }
                }
            }
            // ═══════════════════════════════════════════════════════════
            // 【步骤2】调用 Service 层执行业务逻辑（事务由 @Transactional 管理）
            // ═══════════════════════════════════════════════════════════
            // 注意：tryAssignCustomerToTable 方法需迁移到 RestaurantService 中
            OperationResult<Boolean>  result = service.tryAssignCustomerToTable(
                    tableIdInput, peopleCount, isFromQueue, callNumber,
                    isMerge, isTwoSeat, isFourSeat, isSixSeat,
                    isAddGuests, isShare, isGrouped,groupedTableCount
            );

            if (result.isSuccess()) {
                // ═══════════════════════════════════════════════════════════
                // 【步骤3】成功：同步内存缓存 + 刷新视图
                // ═══════════════════════════════════════════════════════════
                // syncMemoryAfterAddGuests 也需迁移到 Service 层
                service.syncMemoryAfterAddGuests(tableIdInput);

                // 显示成功消息
                view.showInfo(buildSuccessMessage(
                        isFromQueue, callNumber, peopleCount,
                        isMerge, isAddGuests, isShare, isGrouped, groupedTableCount
                ));

                // 刷新餐桌显示（关键：确保界面同步）
                refreshTablesDisplay();
                view.updateTableStatusDisplay(service.getAllTables());
            }
            else {
                // 🔧【核心修改】显示详细错误消息
                String errorMsg = result.getErrorMessage();

                if (errorMsg != null && !errorMsg.isEmpty()) {
                    // 优先显示业务层返回的详细错误
                    view.showError(errorMsg);
                } else {
                    // 兜底：通用错误消息
                    view.showError("餐桌分配失败，请检查输入参数或餐桌状态");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // ═══════════════════════════════════════════════════════════
            // 【步骤5】业务规则验证失败（用户输入问题）
            // ═══════════════════════════════════════════════════════════
            view.showError(e.getMessage());

        } catch (Exception e) {
            // ═══════════════════════════════════════════════════════════
            // 【步骤6】系统异常（兜底处理）
            // ═══════════════════════════════════════════════════════════
            e.printStackTrace();
            view.showError("系统错误: " + e.getMessage());
        }
    }

    private String buildSuccessMessage(boolean isFromQueue, int callNumber, int peopleCount,
                                       boolean isMerge, boolean isAddGuests, boolean isShare,
                                       boolean isGrouped, int groupedTableCount) {  // ← 新增参数
        StringBuilder msg = new StringBuilder();

        if (isFromQueue) {
            msg.append("排队号 #").append(callNumber).append(" 已成功就座");
        } else {
            msg.append("新顾客组（").append(peopleCount).append("人）已成功就座");
        }

        // 🔧 聚餐桌特殊处理（优先级最高）
        if (isGrouped && groupedTableCount >= 3) {
            msg.append("（聚餐桌：").append(groupedTableCount).append("张6人桌）");
        }
        // 其他模式（互斥）
        else if (isMerge) msg.append("（合并桌子）");
        else if (isAddGuests) msg.append("（添加客人）");
        else if (isShare) msg.append("（共享餐桌）");

        return msg.append("。").toString();
    }

    /**
     * 处理堂食订单结账
     */
    private void handleDineInCheckout(String tableNumber) {
        // 1. 验证餐桌编号格式
        if (!service.isValidTableNumberFormat(tableNumber)) {
            view.showError("餐桌号格式无效！\n主桌应为纯数字（如 7）\n子桌后缀只能是 a 或 b（如 7a 或 7b）");
            return;
        }

        // 2. 查找餐桌
        Tables targetTable = service.getTableById(tableNumber);
        if (targetTable == null) {
            view.showError("未找到餐桌：" + tableNumber);
            return;
        }

        // 3. 检查餐桌状态
        if (targetTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            view.showError("餐桌 " + tableNumber + " 当前处于【" +
                    targetTable.getStatus().getDisplayName() + "】状态，无法结账");
            return;
        }

        // 4. 检查是否为合并桌中的主桌
        if (!service.isMainOrderTable(tableNumber)) {
            Tables table = service.getTableById(tableNumber);
            String partnerId = table.getMergedWith();
            view.showError("该合并桌只能通过编号较小的餐桌（" + partnerId +
                    "）进行操作。\n请切换至餐桌 " + partnerId + " 进行结账操作。");
            return;
        }

        // 5. 检查订单状态（内存）
        if (targetTable.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {
            view.showError("餐桌 " + tableNumber + " 的订单已结账，无法再次结账");
            return;
        }

        // 6. 检查是否有活跃订单
        if (targetTable.getOrderStatus() == Tables.OrderStatus.NO_ORDER) {
            view.showError("餐桌 " + tableNumber + " 没有订单，无法结账");
            return;
        }

        // 所有验证通过，显示结账界面
        view.showCheckoutInterface("DINE_IN", tableNumber);
    }

    /**
     * 🔧 堂食结账提交（支持传入营收金额）
     *
     * @param orderType     "DINE_IN"
     * @param identifier    餐桌号
     * @param paymentAmount 客人本次支付金额
     * @param revenueAmount 应记录的营收金额（= Math.max(菜品总额, 定金)）
     */
    public void handleCheckoutSubmitWithRevenue(String orderType, String identifier,
                                                double paymentAmount, double revenueAmount) {
        if (service == null) return;

        Map<String, Object> result = service.processCheckoutWithRevenue(identifier, paymentAmount, revenueAmount);

        if ((Boolean) result.get("success")) {
            double changeAmount = (Double) result.get("changeAmount");
            SwingUtilities.invokeLater(() -> {
                String baseMessage = "结账成功!";
                if (changeAmount > 0) {
                    baseMessage += "\n找零金额：" + String.format("%.2f", changeAmount) + "元";
                }
                JOptionPane.showMessageDialog(view, baseMessage, "结账成功", JOptionPane.INFORMATION_MESSAGE);
                refreshTablesDisplay();
                view.updateTableStatusDisplay(service.getAllTables());
            });
        } else {
            String message = (String) result.get("message");
            SwingUtilities.invokeLater(() -> {
                view.showError("结账失败：" + message);
            });
        }
    }


    /**
     * 🔧 根据 reservation_id 查询预付信息（View 层调用）
     */
    public Map<String, Object> getPrepaidInfoByReservationId(String reservationId) {
        if (service == null || reservationId == null || reservationId.isEmpty()) {
            return Collections.emptyMap();
        }
        return service.getPrepaidInfoByReservationId(reservationId);
    }

    /**
     * 获取订单详情（支持堂食+外卖）
     *
     * @param orderType  "DINE_IN" 或 "TAKEOUT"
     * @param identifier 餐桌号 或 订单号
     */
    public Map<String, Object> getOrderDetails(String orderType, String identifier) {
        return orderService.getOrderDetails(orderType, identifier);
    }

    /**
     * 处理结账提交（支持堂食+外卖）
     */
    public void handleCheckoutSubmit(String orderType, String identifier, double paymentAmount) {
        Map<String, Object> result;

        if ("DINE_IN".equals(orderType)) {
            // 堂食结账
            result = service.processCheckout(identifier, paymentAmount);
        } else {
            // 🔧 外卖结账（需要新增 Service 方法）
            result = orderService.processTakeoutCheckout(identifier, paymentAmount);
        }

        if ((Boolean) result.get("success")) {
            double changeAmount = (Double) result.get("changeAmount");
            double totalAmount = (Double) result.get("totalAmount");
            Object revenueDateObj = result.get("revenueDate");

            SwingUtilities.invokeLater(() -> {
                String baseMessage = "结账成功!";
                if (changeAmount > 0) {
                    baseMessage += "\n找零金额：" + String.format("%.2f", changeAmount) + "元";
                }

                // 跨日提示逻辑（保留原有代码）
                if (revenueDateObj instanceof java.sql.Date) {
                    java.sql.Date revenueDate = (java.sql.Date) revenueDateObj;

                    // 使用 SimpleDateFormat 格式化为 "yyyy-MM-dd" 字符串
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    String revenueDateStr = sdf.format(revenueDate);

                    // 获取当前系统日期（东八区）
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new java.util.Date());
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    String todayStr = sdf.format(cal.getTime());

                    //  仅比较日期字符串（忽略时区问题）
                    if (!revenueDateStr.equals(todayStr)) {
                        baseMessage += "\n\n 跨日结账提示:\n该订单创建于 " + revenueDateStr +
                                "，营业额已计入该日期的统计中。";
                    }
                }


                JOptionPane.showMessageDialog(view, baseMessage, "结账成功", JOptionPane.INFORMATION_MESSAGE);

                // 刷新视图
                if ("DINE_IN".equals(orderType)) {
                    refreshTablesDisplay();
                }
            });
        } else {
            String message = (String) result.get("message");
            SwingUtilities.invokeLater(() -> {
                view.showError("结账失败：" + message);
            });
        }
    }


    public void handleReserveTable(ActionEvent e) {
        // 调用 View 获取数据
        Map<String, Object> result = view.showReservationDialog("CREATE", null);
        if (result == null) return;  // 用户取消

        String mode = (String) result.get("mode");

        // ═══════════════════════════════════════════════════════════
        // 【CREATE 模式】- 原有逻辑保持不变
        // ═══════════════════════════════════════════════════════════
        if ("CREATE".equals(mode)) {
            try {
                Map<String, Object> serviceResult = service.createReservation(result);
                if ((Boolean) serviceResult.get("success")) {
                    view.showInfo(" 预约成功！预约号：" + serviceResult.get("reservationId"));
                    refreshTablesDisplay();
                    view.updateTableStatusDisplay(service.getAllTables());
                    String tableSelectionMode = (String) result.get("tableSelectionMode");
                    if ("QUANTITY".equals(tableSelectionMode)) {
                        view.refreshQuantityReservationsLog();
                        // 🔧 核心：通知 View 确保定时器已启动（如果之前因无数据停止了）
                        view.ensureRefreshTimerRunning();
                    }
                } else {
                    view.showError(" 预约失败：" + serviceResult.get("message"));
                }
            } catch (Exception ex) {
                view.showError("系统错误：" + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增】ASSIGN 模式 - 分配具体餐桌号（核心修复！）
        // ═══════════════════════════════════════════════════════════
        else if ("ASSIGN".equals(mode)) {
            try {
                // 1️⃣ 获取参数
                String reservationId = (String) result.get("reservationId");
                @SuppressWarnings("unchecked")
                List<String> selectedDisplayIds = (List<String>) result.get("selectedTables");

                // 2️⃣ 基础验证
                if (reservationId == null || reservationId.isEmpty()) {
                    view.showError(" 预约号不能为空！");
                    return;
                }
                if (selectedDisplayIds == null || selectedDisplayIds.isEmpty()) {
                    view.showError(" 请至少选择一张餐桌！");
                    return;
                }

                // 3️⃣ 🔧【核心】调用 Service 层执行分配逻辑（事务由 Service 管理）
                service.assignTablesToReservation(reservationId, selectedDisplayIds);

                // 4️⃣ 成功反馈 + 刷新界面
                view.showInfo("✅ 餐桌分配成功！\n已锁定：" + String.join(", ", selectedDisplayIds));
                refreshTablesDisplay();

                // 可选：刷新预约监控面板
                TableReservation reservation = service.getReservationDetail(reservationId);
                if (reservation != null && "QUANTITY".equals(reservation.getTableSelectionMode())) {
                    view.refreshQuantityReservationsLog();
                }

            } catch (IllegalArgumentException | IllegalStateException ex) {
                // 业务规则验证失败 → 友好提示
                view.showError("分配失败: " + ex.getMessage());
            } catch (Exception ex) {
                // 系统异常 → 记录日志 + 提示
                ex.printStackTrace();
                view.showError("系统错误: " + ex.getMessage());
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【CANCEL 模式】
        // ═══════════════════════════════════════════════════════════
        else if ("CANCEL".equals(mode)) {
            try {
                // 1 获取预约号
                String reservationId = (String) result.get("reservationId");

                // 2 基础验证
                if (reservationId == null || reservationId.isEmpty()) {
                    view.showError("⚠️ 预约号不能为空！");
                    return;
                }

                // 3 弹窗获取取消原因
                String cancellationReason = view.showCancelReasonDialog(reservationId);
                if (cancellationReason == null) {
                    return;  // 用户点击取消，终止操作
                }

                // 4 🔧【核心】调用 Service 层执行取消逻辑（传入原因）
                Map<String, Object> cancelResult = service.cancelReservation(reservationId, cancellationReason);

                // 5 处理返回结果
                if ((Boolean) cancelResult.get("success")) {
                    // 【核心修改】优先使用 Service 返回的用户友好消息
                    String message = (String) cancelResult.get("userMessage");

                    // 🔧 可选：如果需要根据场景自定义更详细的提示，可以覆盖 message
                    Boolean preOrderDeleted = (Boolean) cancelResult.get("preOrderDeleted");
                    Boolean depositForfeited = (Boolean) cancelResult.get("depositForfeited");
                    Double forfeitedAmount = (Double) cancelResult.get("forfeitedAmount");


                    // 🔧 显示成功提示（使用 userMessage 或自定义 message）
                    view.showInfo(message);

                    // 🔧 根据需要刷新界面
                    Boolean needRefresh = (Boolean) cancelResult.get("needRefresh");
                    if (needRefresh == null || needRefresh) {
                        refreshTablesDisplay();
                        view.refreshQuantityReservationsLog();
                    }

                } else {
                    view.showError(" 取消失败：" + cancelResult.get("message"));
                }

            } catch (IllegalArgumentException | IllegalStateException ex) {
                view.showError("取消失败: " + ex.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
                view.showError("系统错误: " + ex.getMessage());
            }
        }
        else if ("EDIT_TIME".equals(mode)) {
            try {  // 🔧 新增：外层 try 捕获业务异常
                // 1️ 获取参数
                String reservationId = (String) result.get("reservationId");
                @SuppressWarnings("unchecked")
                Map<String, Object> edits = (Map<String, Object>) result.get("edits");

                // 2️ 基础验证
                if (reservationId == null || reservationId.isEmpty()) {
                    view.showError("⚠️ 预约号不能为空！");
                    return;
                }
                if (edits == null || edits.isEmpty()) {
                    view.showError("⚠️ 请至少勾选一项修改内容！");
                    return;
                }

                // 3️ 调用 Service 层执行修改（事务由 Service 管理）
                service.updateReservation(reservationId, edits);

                // 4️ 成功反馈 + 刷新界面
                view.showInfo("✅ 预约修改成功！\n预约号：" + reservationId);
                refreshTablesDisplay();

                // 刷新预约监控面板
                TableReservation reservation = service.getReservationDetail(reservationId);
                if (reservation != null && "QUANTITY".equals(reservation.getTableSelectionMode())) {
                    view.refreshQuantityReservationsLog();
                }

            } catch (IllegalArgumentException | IllegalStateException ex) {
                // 🔧【核心】业务规则验证失败 → 显示 ERROR 弹窗
                SwingUtilities.invokeLater(() -> {
                    // 使用 JOptionPane 显示标准错误弹窗
                    JOptionPane.showMessageDialog(
                            view,                                    // 父窗口
                            ex.getMessage(),                        // 错误消息（含换行和 emoji）
                            " 修改失败",                           // 弹窗标题
                            JOptionPane.ERROR_MESSAGE,              // 🔴 错误图标
                            null                                     // 自定义图标（可选）
                    );
                });

            } catch (Exception ex) {
                // 系统异常 → 记录堆栈 + 提示
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            view,
                            "系统错误: " + ex.getMessage(),
                            " 系统异常",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }
    }

    /**
     * 🔧 处理预定餐桌的用户选择（Controller 层：转发 + 业务调用）
     */
    private void handleReservedTableAction(Tables table) {
        // 1. 调用 View 显示对话框，获取用户选择
        String action = view.showReservedTableDialog(table);

        if (action == null) {
            return;  // 用户取消
        }

        // 2. 根据用户选择执行对应业务
        switch (action) {
            case "CHECK_IN" -> {
                // 2.1 调用 View 获取入座人数
                // 🔧【修改】传递 displayId 而非 capacity，让 View 层识别合并桌并计算总容量
                int actualSeats = view.showGuestCheckInDialog(table.getDisplayId());
                if (actualSeats < 0) return;  // 用户取消或输入无效

                // ... 獲取人數代碼 ...

                // 🔧【關鍵邏輯】獲取當前餐桌關聯的預定記錄 ID
                // 目的：將此 ID 傳遞給 Service，以便入座時將預定記錄與餐桌綁定
                // 後續離店時，系統會根據此 ID 刪除預定記錄，完成閉環
                String reservationId = service.getReservationIdByTable(table.getDisplayId());
                // 2.2 调用 Service 执行入座业务
                try {
                    // 將預定 ID 傳入 Service 層進行入座處理
                    service.processGuestCheckIn(table.getDisplayId(), actualSeats, reservationId);
                    refreshTablesDisplay();  // 刷新界面
                    view.updateTableStatusDisplay(service.getAllTables());
                    view.showInfo(" 客人已入座！\n餐桌 #" + table.getDisplayId() +
                            " 状态已更新为【占用中】");
                } catch (Exception ex) {
                    view.showError("入座失败：" + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            case "CANCEL" -> {
                // ═══════════════════════════════════════════════════════════
                // 🔧【核心修复】执行完整的取消预约逻辑
                // ═══════════════════════════════════════════════════════════

                // 1️⃣ 获取预约号（优先使用 currentReservationId）
                String cancelReservationId = table.getCurrentReservationId();

                // 如果 currentReservationId 为空，尝试通过 reserved_table_ids 反向查询
                if (cancelReservationId == null || cancelReservationId.isEmpty()) {
                    // 🔧【修复】调用 Service 层方法，不是 controller
                    com.restaurant.entity.TableReservation found =
                            service.findReservationByTableId(table.getDisplayId());
                    if (found != null) {
                        cancelReservationId = found.getReservationId();
                        System.out.println("🔍 通过 reserved_table_ids 找到预约: " + cancelReservationId);
                    }
                }

                // 验证预约号
                if (cancelReservationId == null || cancelReservationId.isEmpty()) {
                    view.showError("⚠️ 未找到关联的预约记录！\n餐桌 #" + table.getDisplayId() + " 可能已被其他操作修改。");
                    return;
                }

                // 2️⃣ 二次确认弹窗（显示预约详情 + 没收定金提示）
                String confirmMsg = "<html><b style='color:#d32f2f;'>⚠️ 确认取消预约？</b><br><br>" +
                        "预约号：<b>" + cancelReservationId + "</b><br>" +
                        "餐桌号：#" + table.getDisplayId() + "<br>" +
                        "容量：" + table.getCapacity() + "人桌";

                // 查询预约详情，显示更多信息
                try {
                    // 🔧【修复】调用 Service 层方法
                    com.restaurant.entity.TableReservation reservation =
                            service.getReservationDetail(cancelReservationId);
                    if (reservation != null) {
                        confirmMsg += "<br>客人：" + reservation.getCustomerName() +
                                " (" + reservation.getCustomerPhone() + ")";

                        if (reservation.getReservationTime() != null) {
                            confirmMsg += "<br>预约时间：" +
                                    reservation.getReservationTime()
                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        }

                        // 🔧 提示预付定金没收
                        if (Boolean.TRUE.equals(reservation.getIsPrepaid()) &&
                                reservation.getPrepaidAmount() != null &&
                                reservation.getPrepaidAmount() > 0) {
                            confirmMsg += "<br><br><font color='#d32f2f'><b>⚠️ 取消后将没收定金：" +
                                    String.format("%.2f", reservation.getPrepaidAmount()) + " 元</b></font>";
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("查询预约详情失败: " + ex.getMessage());
                }

                confirmMsg += "<br><br><font color='#666'><small>此操作不可恢复，预约记录将被删除</small></font></html>";

                int confirm = JOptionPane.showConfirmDialog(
                        view,
                        confirmMsg,
                        "取消预约确认",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm != JOptionPane.YES_OPTION) {
                    return;  // 用户取消操作
                }

                // 3️⃣ 弹窗获取取消原因（可选，但建议收集）
                String cancellationReason = view.showCancelReasonDialog(cancelReservationId);
                if (cancellationReason == null) {
                    return;  // 用户点击取消输入框
                }
                if (cancellationReason.trim().isEmpty()) {
                    cancellationReason = "顾客主动取消预约";  // 默认原因
                }

                // 4️⃣ 🔧【核心修复】调用 Service 层执行取消逻辑
                try {
                    Map<String, Object> cancelResult = service.cancelReservation(cancelReservationId, cancellationReason);

                    // 5️⃣ 处理返回结果
                    if ((Boolean) cancelResult.get("success")) {
                        // 🔹 组装成功提示消息
                        String successMsg = "✅ 预约已取消！\n预约号：" + cancelReservationId;

                        Double forfeitedAmount = (Double) cancelResult.get("forfeitedAmount");
                        if (forfeitedAmount != null && forfeitedAmount > 0) {
                            successMsg += "\n\n💰 没收定金：" + String.format("%.2f", forfeitedAmount) + " 元";
                        }

                        // 🔹 显示成功提示
                        view.showInfo(successMsg);

                        // 🔹 🔧【修复】刷新界面：调用本类方法
                        SwingUtilities.invokeLater(() -> {
                            refreshTablesDisplay();  // ← 直接调用本类方法，不加 controller.
                            view.updateTableStatusDisplay(service.getAllTables());
                        });

                    } else {
                        // 🔹 失败提示
                        String errorMsg = (String) cancelResult.get("message");
                        view.showError("❌ 取消失败：" + errorMsg);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    view.showError("系统错误：" + ex.getMessage());
                }
            }

            case "DELAY" -> {
                // ═══════════════════════════════════════════════════════════
                // 🔧【说明】延迟预约已在 View 层处理，此处无需重复执行
                // ═══════════════════════════════════════════════════════════

                // 可选：记录日志（调试用）
                System.out.println("ℹ️ 延迟预约操作已完成（View 层处理）: 餐桌 #" + table.getDisplayId());

                // ❌ 不要在这里再弹对话框或调用 Service！
                // 否则会导致：重复弹窗 / 重复更新数据库 / 状态不一致
            }
        }
    }

    /**
     * 🔧 新增：直接处理餐桌分配（不打开对话框，供 View 层已有数据时调用）
     */
    public void processTableAssignment(String reservationId, List<String> selectedTables) {
        try {
            // 基础验证
            if (reservationId == null || reservationId.isEmpty()) {
                view.showError(" 预约号不能为空！");
                return;
            }
            if (selectedTables == null || selectedTables.isEmpty()) {
                view.showError(" 请至少选择一张餐桌！");
                return;
            }

            // 调用 Service 层执行分配
            service.assignTablesToReservation(reservationId, selectedTables);

            // 成功反馈
            view.showInfo(" 餐桌分配成功！\n已锁定：" + String.join(", ", selectedTables));

            // 刷新界面
            refreshTablesDisplay();
            view.updateTableStatusDisplay(service.getAllTables());
            // 刷新预约监控面板（如果是数量模式）
            TableReservation reservation = service.getReservationDetail(reservationId);
            if (reservation != null && "QUANTITY".equals(reservation.getTableSelectionMode())) {
                view.refreshQuantityReservationsLog();
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            view.showError("分配失败：" + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            view.showError("系统错误：" + ex.getMessage());
        }
    }

    /**
     * 🔧 专用方法：直接更新预约（不打开任何对话框）
     */
    public void updateReservationDirectly(String reservationId, Map<String, Object> edits) {
        service.updateReservation(reservationId, edits);
    }

    /**
     * 🔧 取消预约（代理到 Service 层）
     *
     * @param reservationId      预约号
     * @param cancellationReason 取消原因
     * @return 操作结果 Map
     */
    public Map<String, Object> cancelReservation(String reservationId, String cancellationReason) {
        return service.cancelReservation(reservationId, cancellationReason);
    }

    /**
     * 🔧 延迟预约（Controller 层代理方法）
     *
     * @param reservationId 预约号
     * @param newTime       新的预约时间
     * @param keepTable     是否保留餐桌
     * @return 操作结果
     */
    public Map<String, Object> delayReservation(String reservationId, LocalDateTime newTime, boolean keepTable) {
        return service.delayReservation(reservationId, newTime, keepTable);
    }

    /**
     * 🔧 获取数量模式预约记录（用于日志显示）
     */
    public List<Map<String, Object>> getQuantityModeReservationsForLog() {
        return service.getQuantityModeReservationsForLog();
    }

    public List<Map<String, Object>> getPreOrderReservationsForMonitor() {
        return service.getPreOrderReservationsForMonitor();
    }


    /**
     * 🔧 代理方法：查询预约详情（用于弹窗显示）
     */
    public TableReservation getReservationDetail(String reservationId) {
        return service.getReservationDetail(reservationId);
    }

    /**
     * 🔧 根据餐桌号查找预定记录（通过 reserved_table_ids 字段）
     */
    public TableReservation findReservationByTableId(String tableDisplayId) {
        if (tableDisplayId == null || tableDisplayId.isEmpty()) {
            return null;
        }
        return service.findReservationByTableId(tableDisplayId);
    }

    /**
     * 🔧 根据预约号模糊查询
     */
    public List<Map<String, Object>> findReservationsByCode(String codeFragment) {
        if (service == null || codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findReservationsByCode(codeFragment);
    }

    /**
     * 🔧 根据电话号码模糊查询（后4位）
     */
    public List<Map<String, Object>> findReservationsByPhone(String phoneLast4) {
        if (service == null || phoneLast4 == null || phoneLast4.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findReservationsByPhone(phoneLast4);
    }


    /**
     * 根据预约号片段查询预约详情（支持模糊查询）
     */
    public List<TableReservation> findReservationsByCodeFragment(String codeFragment) {
        if (service == null || codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findReservationsByCodeFragment(codeFragment);
    }

    /**
     * 🔧 CANCEL 模式专用：根据预约号片段查询（支持所有状态）
     */
    public List<TableReservation> findReservationsForCancel(String codeFragment) {
        if (service == null || codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findReservationsForCancel(codeFragment);
    }

    /**
     * 获取所有餐桌（用于分配餐桌时显示）
     */
    public List<Tables> getAllVacantTables() {
        if (service == null) return new ArrayList<>();
        return service.getAllVacantTables();
    }


    /**
     * 🔧 代理方法：根据 displayId 获取餐桌（委托给 Service）
     *
     * @param displayId 餐桌显示编号（如 "7" 或 "7a"）
     * @return Tables 对象，不存在返回 null
     */
    public Tables getTableById(String displayId) {
        if (service == null || displayId == null || displayId.trim().isEmpty()) {
            return null;
        }
        return service.getTableById(displayId.trim());
    }

    /**
     * 获取单日营业报表
     * @param date 日期（格式：YYYY-MM-DD）
     * @return 报表数据列表
     */
    public List<Map<String, Object>> getDailyBusinessReport(String date) {
        return service.getDailyBusinessReport(date);
    }

    /**
     * 获取日期范围营业报表
     * @param startDate 起始日期（YYYY-MM-DD）
     * @param endDate 结束日期（YYYY-MM-DD）
     * @return 报表数据列表
     */
    public List<Map<String, Object>> getDateRangeBusinessReport(String startDate, String endDate) {
        return service.getDateRangeBusinessReport(startDate, endDate);
    }

    /**
     * 获取季度菜品销售报表
     * @param year 年份
     * @param quarter 季度（Q1-Q4）
     * @param category 菜品类别（可选）
     * @return 销售数据列表，异常时抛出运行时异常
     */
    public List<Map<String, Object>> getQuarterlyDishSalesReport(int year, String quarter, String category) {
        try {
            return service.getQuarterlyDishSalesReport(year, quarter, category);
        } catch (Exception e) {
            System.err.println("控制器获取季度菜品销售报表失败: " + e.getMessage());
            throw new RuntimeException("获取报表数据失败: " + e.getMessage(), e);
        }
    }
    /**
     * 获取菜品销售数据中有记录的年份列表
     *
     * @return 按降序排列的年份字符串列表（例如["2026", "2025"]）
     *         无数据或异常时返回包含当前年份的列表
     */
    public List<String> getAvailableYearsForDishSales() {
        return service.getAvailableYearsForDishSales();
    }

    /**
     * 获取取消预约没收定金报表
     */
    public List<Map<String, Object>> getForfeitedDepositsReport(String startDate, String endDate) {
        return service.getForfeitedDepositsReport(startDate, endDate);
    }

    private void handleOpenOrderSystem() {
        new OrderSystemGUI(this, service, menuItemService, orderService).setVisible(true);
    }

    public String getOrderStatusDisplay(String tableNumber) {
        return service.getOrderStatusDisplay(tableNumber);
    }



    /**
     * 处理确认下单（精简版：移除重复合并逻辑，统一入口处理）
     */
    public void handleConfirmOrder(String identifier,
                                   List<OrderItem> orderItems,
                                   OrderType orderType,
                                   String customerName,
                                   String customerPhone,
                                   String deliveryAddress,
                                   Double deliveryFee,
                                   boolean isReorderAfterCheckout,
                                   Runnable onSuccess) {

        // ═══════════════════════════════════════════════════════════
        // 【步骤 1】基础参数验证
        // ═══════════════════════════════════════════════════════════
        if (orderItems == null || orderItems.isEmpty()) {
            showError("订单不能为空，请先点菜");
            return;
        }

        if (orderType == null) {
            showError("订单类型不能为空");
            return;
        }

        // 配送模式专属验证
        if (orderType == OrderType.DELIVERY) {
            if (deliveryFee == null || deliveryFee < 0) {
                showError("配送订单必须填写有效的配送费");
                return;
            }
            if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
                showError("配送订单必须填写配送地址");
                return;
            }
        } else {
            deliveryFee = 0.0;
        }

        // 外卖模式必须填写电话
        if (orderType != OrderType.DINE_IN && orderType != OrderType.RESERVATION &&
                (customerPhone == null || customerPhone.trim().isEmpty())) {
            showError("外卖订单必须填写联系电话");
            return;
        }

        // 🔧 预约订单不需要餐桌验证
        if (orderType == OrderType.DINE_IN) {
            if (identifier == null || identifier.isEmpty() || "未选择".equals(identifier)) {
                showError("堂食订单必须先选择餐桌");
                return;
            }
            Tables table = service.getTableById(identifier);
            if (table == null || (table.getStatus() != Tables.TableStatus.OCCUPIED &&
                    table.getStatus() != Tables.TableStatus.RESERVED)) {
                showError("餐桌 " + identifier + " 状态无效，不能点餐");
                return;
            }
        } else if (orderType == OrderType.RESERVATION) {
            if (identifier == null || identifier.isEmpty()) {
                showError("预约订单必须有预约号");
                return;
            }
            TableReservation reservation = service.getReservationDetail(identifier);
            if (reservation == null) {
                showError("预约号不存在：" + identifier);
                return;
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤 2】变量固化（供 SwingWorker 内部使用）
        // ═══════════════════════════════════════════════════════════
        final String finalIdentifier = identifier;
        final List<OrderItem> finalOrderItems = new ArrayList<>(orderItems);
        final OrderType finalOrderType = orderType;
        final String finalCustomerName = customerName;
        final String finalCustomerPhone = customerPhone;
        final String finalDeliveryAddress = deliveryAddress;
        final Double finalDeliveryFee = deliveryFee;
        final Runnable finalOnSuccess = onSuccess;

        // ═══════════════════════════════════════════════════════════
        // 【步骤 3】异步执行下单逻辑（SwingWorker）
        // ═══════════════════════════════════════════════════════════
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // 3.1 计算菜品总金额（不含配送费）
                    double itemsTotal = finalOrderItems.stream()
                            .mapToDouble(i -> i.getQuantity() * i.getPriceAtOrder())
                            .sum();
                    itemsTotal = Math.round(itemsTotal * 100.0) / 100.0;

                    // 3.2 计算最终总金额（仅配送模式加配送费）
                    double finalTotalAmount = itemsTotal +
                            (finalOrderType == OrderType.DELIVERY ? finalDeliveryFee : 0.0);
                    finalTotalAmount = Math.round(finalTotalAmount * 100.0) / 100.0;

                    Integer tableId = null;
                    String orderNumber = null;
                    Integer existingOrderId = null;

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【核心】解析特殊 Key 格式（聚餐桌一键点餐）
                    // 特殊格式：A1[BATCH:13,14,15] 表示一键点餐，分配到 13,14,15 桌
                    // ═══════════════════════════════════════════════════════════
                    boolean isGroupedTableOrder = false;
                    List<String> groupedTableIds = new ArrayList<>();
                    for (OrderItem item : finalOrderItems) {
                        String itemCode = item.getItemCode();
                        if (itemCode != null && itemCode.contains("[BATCH:")) {
                            int batchStart = itemCode.indexOf("[BATCH:");
                            String tableIdsStr = itemCode.substring(batchStart + 7, itemCode.length() - 1);
                            String[] tableIds = tableIdsStr.split(",");
                            for (String tid : tableIds) {
                                if (!groupedTableIds.contains(tid.trim())) {
                                    groupedTableIds.add(tid.trim());
                                }
                            }
                            isGroupedTableOrder = true;
                            break;
                        }
                    }

                    // 3.3 根据订单类型获取标识 → 🔧 只查找 existingOrderId，不执行合并！
                    if (finalOrderType == OrderType.DINE_IN) {
                        // 1. 生成订单号 + 获取 tableId
                        String prefix = "T";
                        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                        Integer seq = orderService.getNextDineInOrderNumber(dateStr);
                        orderNumber = String.format("%s-%s-%03d", prefix, dateStr, seq);

                        Tables table = service.getTableById(finalIdentifier);
                        if (table == null) {
                            throw new RuntimeException("未找到餐桌：" + finalIdentifier);
                        }
                        tableId = table.getTableId();

                        // 2. 🔧【核心修复】查找现有订单逻辑优化
                        // 优先查找状态为 ORDERED 的活跃订单
                        existingOrderId = orderService.findActiveOrderIdByTableId(tableId);

                        // 🔧 如果没找到活跃订单，检查是否关联了预约（预点餐场景）
                        // 场景：客人刚入座，预点餐订单状态可能还是 NO_ORDER，findActiveOrderIdByTableId 查不到
                        if (existingOrderId == null && table.getCurrentReservationId() != null && !table.getCurrentReservationId().isEmpty()) {
                            String reservationId = table.getCurrentReservationId();
                            // 尝试通过 reservation_id 查找关联的订单（支持 NO_ORDER 和 ORDERED 状态）
                            Order linkedOrder = orderService.findActiveOrderByReservationId(reservationId);

                            if (linkedOrder != null) {
                                existingOrderId = linkedOrder.getOrderId();
                                System.out.println("🔧 [修复重复订单] 找到关联的预点餐订单 (orderId=" + existingOrderId +
                                        ", status=" + linkedOrder.getStatus() + ")，将复用该订单。");
                            }
                            // 🔧【核心修復】立即升級預點餐訂單狀態：NO_ORDER → ORDERED
                            if ("NO_ORDER".equals(linkedOrder.getStatus())) {
                                boolean upgraded = orderService.upgradePreOrderStatus(existingOrderId);
                                if (upgraded) {
                                    // 🔧 同時更新內存中的 order 對象狀態（供後續邏輯使用）
                                    linkedOrder.setStatus("ORDERED");
                                }
                            }
                        }

                        // 3. 如果仍然没找到订单，且不是预点餐场景，才报错或创建新订单
                        // (注意：你的原代码里如果是 RESERVED 状态会抛异常，这里逻辑要兼容)
                        if (existingOrderId == null) {
                            // 这里保留你原有的逻辑，如果是 RESERVED 状态报错，或者创建新订单
                            // 但根据你的日志，此时 table 状态应该是 OCCUPIED
                            if (table.getStatus() == Tables.TableStatus.RESERVED) {
                                throw new RuntimeException("餐桌 " + finalIdentifier + " 尚未添加预点餐菜品！...");
                            }
                            // 如果是 OCCUPIED 但没找到订单，说明是普通入座后首次点餐，existingOrderId 保持 null，下面会创建新订单
                        }
                    }
// ================= 修改后的代码结束 =================
//                    if (finalOrderType == OrderType.DINE_IN) {
//                        // 堂食：生成订单号 + 获取 tableId
//                        String prefix = "T";
//                        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//                        Integer seq = orderService.getNextDineInOrderNumber(dateStr);
//                        orderNumber = String.format("%s-%s-%03d", prefix, dateStr, seq);
//
//                        Tables table = service.getTableById(finalIdentifier);
//                        if (table == null) {
//                            throw new RuntimeException("未找到餐桌：" + finalIdentifier);
//                        }
//                        tableId = table.getTableId();
//
//                        // 🔧【核心修改】RESERVED 状态餐桌：只查找订单，不合并！
//                        if (table.getStatus() == Tables.TableStatus.RESERVED) {
//                            // 先找预点餐订单（NO_ORDER 状态）
//                            Order preOrder = orderService.findOrderByTableIdAndStatus(tableId, "NO_ORDER");
//                            if (preOrder != null && preOrder.getOrderId() != null) {
//                                existingOrderId = preOrder.getOrderId();
//                                System.out.println("🔧 找到预点餐订单: orderId=" + existingOrderId);
//                            } else {
//                                // 再找活跃订单
//                                existingOrderId = orderService.findActiveOrderIdByTableId(tableId);
//                                if (existingOrderId != null) {
//                                    System.out.println("🔧 找到活跃订单: orderId=" + existingOrderId);
//                                } else {
//                                    throw new RuntimeException(
//                                            "餐桌 " + finalIdentifier + " 尚未添加预点餐菜品！\n\n" +
//                                                    "请先在【预定餐桌】→【修改资料】中为该预约添加菜品，\n" +
//                                                    "或联系管理员确认预约配置。"
//                                    );
//                                }
//                            }
//                        } else {
//                            // OCCUPIED 状态：直接找活跃订单
//                            existingOrderId = orderService.findActiveOrderIdByTableId(tableId);
//                        }
//
//                    }
                    else if (finalOrderType == OrderType.PICKUP || finalOrderType == OrderType.DELIVERY) {
                        // 外卖/配送：通过订单号获取
                        if (finalIdentifier != null && !finalIdentifier.isEmpty() && !"待下单".equals(finalIdentifier)) {
                            Order existingOrder = orderService.findActiveOrderByOrderNumber(finalIdentifier);
                            if (existingOrder != null && "ORDERED".equals(existingOrder.getStatus())) {
                                existingOrderId = existingOrder.getOrderId();
                                orderNumber = existingOrder.getOrderNumber();
                            }
                        }
                        // 新外卖订单：生成订单号
                        if (existingOrderId == null) {
                            String prefix = (finalOrderType == OrderType.PICKUP) ? "P" : "D";
                            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                            Integer seq = orderService.getNextTakeoutOrderNumber(
                                    prefix, dateStr, finalOrderType.getDbDeliveryMethod());
                            orderNumber = String.format("%s-%s-%d", prefix, dateStr, seq);
                        }

                    } else if (finalOrderType == OrderType.RESERVATION) {
                        // 🔧 预约订单：只查找预点餐订单，不合并！
                        Order preOrder = orderService.findActiveOrderByReservationId(finalIdentifier);
                        if (preOrder != null && preOrder.getOrderId() != null) {
                            existingOrderId = preOrder.getOrderId();
                            System.out.println("🔧 找到预约订单: orderId=" + existingOrderId +
                                    ", reservationId=" + finalIdentifier);
                        } else {
                            throw new RuntimeException(
                                    "预约订单 " + finalIdentifier + " 尚未添加预点餐菜品！\n\n" +
                                            "请先在【预定餐桌】→【修改资料】中为该预约添加菜品，\n" +
                                            "或联系管理员确认预约配置。"
                            );
                        }
                        orderNumber = null;  // 预约订单不需要订单号
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【步骤 3.3.5】处理已结账后重新点单（仅堂食）
                    // ═══════════════════════════════════════════════════════════
                    if (isReorderAfterCheckout && finalOrderType == OrderType.DINE_IN) {
                        Integer checkedOutOrderId = orderService.findCheckedOutOrderIdByTableId(tableId);
                        if (checkedOutOrderId != null) {
                            orderService.resetCheckedOutOrder(checkedOutOrderId);
                            existingOrderId = checkedOutOrderId;
                            System.out.println(" 已重置餐桌 #" + finalIdentifier +
                                    " 的已结账订单：orderId=" + checkedOutOrderId);
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【统一入口】合并订单 或 创建新订单（所有类型都走这里！）
                    // ═══════════════════════════════════════════════════════════
                    Integer orderId;
                    if (existingOrderId != null) {
                        // 🔧【统一合并逻辑】聚餐桌一键点餐特殊处理
                        if (isGroupedTableOrder && !groupedTableIds.isEmpty()) {
                            List<OrderItem> processedItems = new ArrayList<>();
                            for (OrderItem item : finalOrderItems) {
                                String itemCode = item.getItemCode();
                                if (itemCode != null && itemCode.contains("[BATCH:")) {
                                    int batchStart = itemCode.indexOf("[BATCH:");
                                    String pureItemCode = itemCode.substring(0, batchStart);
                                    String tableIdsStr = itemCode.substring(batchStart + 7, itemCode.length() - 1);

                                    OrderItem newItem = new OrderItem();
                                    newItem.setItemId(item.getItemId());
                                    newItem.setItemCode(pureItemCode);
                                    newItem.setItemName(item.getItemName());
                                    newItem.setQuantity(item.getQuantity());  // 🔧 保持原数量
                                    newItem.setPriceAtOrder(item.getPriceAtOrder());
                                    newItem.setAssignedTableDisplayId(tableIdsStr);  // 🔧 完整桌号列表
                                    processedItems.add(newItem);
                                } else {
                                    item.setAssignedTableDisplayId(finalIdentifier);
                                    processedItems.add(item);
                                }
                            }
                            Map<String, Integer> newItemsMap = processedItems.stream()
                                    .collect(Collectors.toMap(
                                            item -> item.getItemCode().trim().toUpperCase(),
                                            OrderItem::getQuantity,
                                            Integer::sum  // 🔧 相同菜品数量累加
                                    ));
                            orderService.mergeOrderItems(existingOrderId, newItemsMap);

                        } else {
                            // 普通点餐：直接合并
                            Map<String, Integer> newItemsMap = finalOrderItems.stream()
                                    .collect(Collectors.toMap(
                                            item -> item.getItemCode().trim().toUpperCase(),
                                            OrderItem::getQuantity,
                                            Integer::sum
                                    ));
                            orderService.mergeOrderItems(existingOrderId, newItemsMap);
                        }

                        orderId = existingOrderId;

                        // 🔧【核心修复】预约订单：需要更新状态 + 金额（NO_ORDER → ORDERED）
                        if (finalOrderType == OrderType.RESERVATION) {
                            orderService.updateOrderStatusAndTotals(
                                    existingOrderId,
                                    "ORDERED",
                                    itemsTotal,
                                    finalTotalAmount
                            );
                            System.out.println(" 预约订单状态已更新: orderId=" + existingOrderId);
                        }
                        // 🔧 配送订单：更新配送费
                        else if (finalOrderType == OrderType.DELIVERY && finalDeliveryFee != null) {
                            orderService.updateOrderDeliveryFee(existingOrderId, finalDeliveryFee);
                            System.out.println(" 已更新配送费：orderId=" + existingOrderId);
                        }

                        System.out.println(" 订单合并成功：orderId=" + orderId);

                    } else {
                        // 新订单创建逻辑
                        Order.DeliveryStatus deliveryStatus = null;
                        if (finalOrderType == OrderType.DELIVERY) {
                            deliveryStatus = Order.DeliveryStatus.NOT_DELIVERED;
                        }

                        orderId = orderService.createOrder(
                                tableId,
                                orderNumber,
                                finalOrderType.getDbOrderType(),
                                finalOrderType.getDbDeliveryMethod(),
                                finalOrderType == OrderType.DELIVERY ? finalDeliveryAddress : null,
                                finalCustomerPhone,
                                finalCustomerName,
                                itemsTotal,
                                finalDeliveryFee,
                                finalTotalAmount,
                                finalOrderItems,
                                deliveryStatus
                        );

                        if (orderId == null || orderId <= 0) {
                            throw new RuntimeException("创建订单失败：返回 orderId 无效");
                        }
                        System.out.println(" 新订单创建成功：orderId=" + orderId +
                                ", orderNumber=" + orderNumber);
                    }

                    // 3.5 更新堂食/预约餐桌状态
                    if (finalOrderType == OrderType.DINE_IN && finalIdentifier != null) {
                        Tables table = service.getTableById(finalIdentifier);
                        if (table != null) {
                            table.setOrderStatus(Tables.OrderStatus.ORDERED_UNFINISHED);
                        }
                    } else if (finalOrderType == OrderType.RESERVATION && tableId != null) {
                        Tables table = service.getTableById(String.valueOf(tableId));
                        if (table != null && table.getStatus() == Tables.TableStatus.RESERVED) {
                            table.setOrderStatus(Tables.OrderStatus.ORDERED_UNFINISHED);
                        }
                    }

                    // 3.6 日志输出
                    System.out.println("🎯 订单处理完成 | type=" + finalOrderType +
                            " | itemsTotal=" + itemsTotal +
                            " | deliveryFee=" + finalDeliveryFee +
                            " | total=" + finalTotalAmount);

                    return null;

                } catch (Exception e) {
                    throw new RuntimeException("下单失败：" + e.getMessage(), e);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (finalOnSuccess != null) {
                        SwingUtilities.invokeLater(() -> {
                            if (finalOrderType == OrderType.DINE_IN && view != null) {
                                service.refreshTableCache();
                                view.updateTableStatusDisplay(service.getAllTables());
                            }
                            finalOnSuccess.run();
                        });
                    }
                } catch (Exception e) {
                    showError("下单失败：" + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * 處理標記菜品為已上桌（Controller 層 - 事件轉發 + 日誌記錄）
     *
     * @param tableNumber 餐桌編號（String，如 "7"）
     * @param itemId      菜品 ID（int）
     * @param quantity    上桌數量
     * @throws SQLException 操作失敗時拋出
     */
    public void handleMarkItemsAsServed(String tableNumber, int itemId, int quantity) throws SQLException {
        // 🔧【日誌】記錄操作開始
//        System.out.println("📝 [INFO] 標記上桌請求 - 餐桌:" + tableNumber +
//                ", 菜品ID:" + itemId + ", 數量:" + quantity);

        try {
            // 1. 直接調用 Service，事務由 @Transactional 自動管理
            orderService.markItemsAsServed(tableNumber, itemId, quantity);

            // 2. 成功後刷新餐桌緩存（可選）
            service.refreshTableCache();

            view.updateTableStatusDisplay(service.getAllTables());
            // 🔧【日誌】記錄操作成功
//            System.out.println(" [SUCCESS] 標記上桌成功 - 餐桌:" + tableNumber +
//                    ", 菜品:" + itemId + ", 數量:" + quantity +
//                    ", 時間:" + java.time.LocalDateTime.now());

        } catch (IllegalArgumentException e) {
            // 🔧【日誌】記錄業務驗證異常
//            System.err.println(" [BUSINESS_ERROR] 標記上桌失敗（業務驗證）- 餐桌:" + tableNumber +
//                    ", 菜品:" + itemId + ", 原因:" + e.getMessage());

            // 業務驗證異常，直接拋出給 View 層處理
            throw e;

        } catch (SQLException e) {
            // 🔧【日誌】記錄數據庫異常（包含完整堆棧）
//            System.err.println(" [DB_ERROR] 標記上桌失敗（數據庫異常）- 餐桌:" + tableNumber +
//                    ", 菜品:" + itemId + ", 錯誤:" + e.getMessage());
            e.printStackTrace();  // 🔧 打印完整堆棧，便於排查

            // 數據庫異常，包裝後拋出
//            throw new SQLException("標記上桌失敗: " + e.getMessage(), e);

        } catch (Exception e) {
            // 🔧【日誌】記錄未知異常（兜底保護）
//            System.err.println(" [UNKNOWN_ERROR] 標記上桌失敗（未知異常）- 餐桌:" + tableNumber +
//                    ", 菜品:" + itemId + ", 異常類型:" + e.getClass().getSimpleName() +
//                    ", 錯誤:" + e.getMessage());
//            e.printStackTrace();

            // 未知異常，包裝後拋出
            throw new RuntimeException("系統異常: " + e.getMessage(), e);
        }
    }

    /**
     * 一鍵標記所有菜品為已上桌（Controller 層 - 僅轉發 + UI 處理）
     */
    public void handleMarkAllItemsAsServed(String tableNumber) throws SQLException {
        try {
            // 直接調用 Service
            orderService.markAllItemsAsServed(tableNumber);

            // 成功後刷新餐桌緩存
            service.refreshTableCache();

            view.updateTableStatusDisplay(service.getAllTables());

          //  System.out.println(" Controller: 全部上桌成功 - " + tableNumber);

        } catch (IllegalArgumentException | SQLException e) {
            // 異常統一拋出，由 View 層顯示錯誤
            throw e;
        }
    }

    /**
     * 標記外賣訂單菜品為製作完成（Controller 層 - 僅轉發 + 刷新）
     */
    public void handleMarkReadyForTakeout(String orderNumber, int itemId, int quantity) throws SQLException {
        try {
            orderService.markTakeoutItemsAsReady(orderNumber, itemId, quantity);
            //System.out.println(" Controller: 外賣製作完成標記成功 - " + orderNumber);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLException("標記外賣完成失敗：" + e.getMessage(), e);
        }
    }

    /**
     * 一鍵標記外賣訂單所有菜品為製作完成
     */
    public void handleMarkAllTakeoutItemsAsReady(String orderNumber) throws SQLException {
        try {
            orderService.markAllTakeoutItemsAsReady(orderNumber);
            System.out.println(" Controller: 外賣全部製作完成標記成功 - " + orderNumber);
        } catch (IllegalArgumentException | IllegalStateException | SQLException e) {
            throw e;
        }
    }

    /**
     * 撤銷外賣訂單中的菜品（通過訂單號 + itemId，原因可選）
     */
    public void handleCancelTakeoutOrderItem(String orderNumber, int itemId, int quantity, String cancellationReason) throws SQLException {
        try {
            orderService.cancelTakeoutOrderItem(orderNumber, itemId, quantity, cancellationReason);
            System.out.println(" Controller: 外賣撤銷成功 - " + orderNumber);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLException("撤銷外賣菜品失敗：" + e.getMessage(), e);
        }
    }



    /**
     * 撤销堂食订单中的菜品（支持选择删除已上桌/未上桌部分）
     *
     * <p>当菜品状态为 { PARTIALLY_SERVED} 时，通过 {@code deleteServedPart} 参数区分：
     * <ul>
     *   <li>{@code true}：删除已上桌部分 → 减少 {@code served_quantity}</li>
     *   <li>{@code false}：删除未上桌部分 → 减少 {@code quantity}（总数量）</li>
     * </ul>
     *
     * @param tableNumber 餐桌显示编号（如 "7" 或 "7a"）
     * @param itemCode 菜品编号（如 "A1"）
     * @param cancelQuantity 要撤销的数量
     * @param cancellationReason 撤销原因
     * @param  {@code true}=删除已上桌部分，{@code false}=删除未上桌部分
     * @throws SQLException 操作失败时抛出
     * @throws IllegalArgumentException 参数验证失败时抛出
     */
    public void handleCancelOrderItem(String tableNumber, String itemCode, int cancelQuantity,
                                      String cancellationReason, String cancelPart) throws SQLException {
        try {
            // 🔧【核心】判断是否为预约关联的餐桌
            Tables table = service.getTableById(tableNumber);
            String reservationId = (table != null) ? table.getCurrentReservationId() : null;

            if (reservationId != null && !reservationId.isEmpty()) {
                // 🔧 预约订单：调用支持确认逻辑的方法（同步添加 cancelPart 参数）
                handleCancelReservationOrderItem(tableNumber, itemCode, cancelQuantity,
                        cancellationReason, reservationId, cancelPart);
            } else {
                // 🔧 普通堂食订单：调用支持 cancelPart 的 Service 方法
                orderService.cancelOrderItem(tableNumber, itemCode, cancelQuantity,
                        cancellationReason, cancelPart);
                service.refreshTableCache();
                view.updateTableStatusDisplay(service.getAllTables());
                System.out.println(" Controller: 堂食撤銷成功 - " + tableNumber +
                        " | cancelPart=" + cancelPart);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 業務驗證異常，直接拋出給 View 層處理
            throw e;
        } catch (SQLException e) {
            // 數據庫異常，包裝後拋出
            throw new SQLException("撤銷堂食菜品失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 🔧 新增：處理預約訂單的菜品撤銷（帶確認對話框 + 支持選擇刪除部分）
     * 當撤銷最後一個菜品時，彈窗詢問是否保留預約記錄
     *
     * @param tableNumber 餐桌顯示編號
     * @param itemCode 菜品編號
     * @param cancelQuantity 撤銷數量
     * @param cancellationReason 撤銷原因
     * @param reservationId 預約號
     * @param cancelPart true=刪除已上桌部分，false=刪除未上桌部分
     */
    private void handleCancelReservationOrderItem(String tableNumber, String itemCode, int cancelQuantity,
                                                  String cancellationReason, String reservationId,
                                                  String cancelPart) throws SQLException {
        // 1. 調用 Service 執行撤銷，返回結果（含是否需要確認的標誌）
        // 🔧【關鍵】傳入 cancelPart 參數
        Map<String, Object> result = orderService.cancelReservationOrderItemWithConfirm(
                tableNumber, itemCode, cancelQuantity, cancellationReason, reservationId, cancelPart);

        // 2. 檢查是否需要用戶確認
        if (Boolean.TRUE.equals(result.get("needConfirm"))) {
            // 🔧 彈出確認對話框
            int confirm = JOptionPane.showConfirmDialog(
                    view,
                    "<html><b>⚠️ 這是預約訂單的最後一個菜品！</b><br><br>" +
                            "是否保留預約記錄？<br><br>" +
                            "<font color='blue'>是</font>：保留預約訂單，pre_order 保持為 1，可繼續點餐<br>" +
                            "<font color='red'>否</font>：刪除預約訂單，pre_order 改為 0</html>",
                    "確認保留預約",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            Integer orderId = (Integer) result.get("orderId");

            if (confirm == JOptionPane.YES_OPTION) {
                // 用戶選擇"是"：保留預約訂單
                orderService.confirmKeepReservationOrder(reservationId, orderId);
                service.refreshTableCache();
                System.out.println("✅ 預約訂單已保留: reservationId=" + reservationId);
            } else {
                // 用戶選擇"否"：刪除預約訂單
                orderService.confirmDeleteReservationOrder(reservationId, orderId);
                service.refreshTableCache();
                System.out.println("🗑️ 預約訂單已刪除: reservationId=" + reservationId);
            }
        } else {
            // 不需要確認，直接刷新
            service.refreshTableCache();
        }

        System.out.println("🔄 Controller: 預約訂單撤銷處理完成 - " + tableNumber +
                " | cancelPart=" + cancelPart);
    }

    /**
     * 撤销整个外卖订单（整单取消）
     */
    public void handleCancelTakeoutOrder(String orderNumber, String reason) throws SQLException {
        try {
            orderService.cancelTakeoutOrder(orderNumber, reason);
            System.out.println(" Controller: 外卖整单撤销成功 - " + orderNumber);
        } catch (Exception e) {
            throw new SQLException("撤销外卖整单失败：" + e.getMessage(), e);
        }
    }

    /**
     * 处理更新配送状态
     *
     * @param orderNumber 订单号
     * @param newStatus   新配送状态
     */
    public void handleUpdateDeliveryStatus(String orderNumber, Order.DeliveryStatus newStatus) throws SQLException {
        try {
            orderService.updateDeliveryStatus(orderNumber, newStatus);
            System.out.println(" Controller: 配送状态更新成功 - " + orderNumber);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;  // 业务异常直接抛出
        } catch (Exception e) {
            throw new SQLException("更新配送状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取消重新点餐（转发到 Service，结果交给 View 显示 + 刷新缓存）
     *
     * @param tableNumber 餐桌号
     * @return Map{success: Boolean, message: String}
     */
    public Map<String, Object> handleCancelReorder(String tableNumber) {
        // 1. 调用 Service（只处理数据库）
        Map<String, Object> result = orderService.cancelReorder(tableNumber);

        // 2. 🔧 如果成功，刷新餐桌缓存（由 RestaurantService 管理）
        if (Boolean.TRUE.equals(result.get("success"))) {
            service.refreshTableCache();
            view.updateTableStatusDisplay(service.getAllTables());
         //   System.out.println(" 已刷新餐桌缓存: " + tableNumber);
        }

        return result;
    }


    public void updateReservationOrderItemPrepared(String reservationId, int orderItemId,
                                               String itemCode, int preparedQty,
                                               String newStatus, String assignedTableDisplayId) {
    try {
        orderService.updateReservationOrderItemPrepared(reservationId, orderItemId,
                itemCode, preparedQty, newStatus, assignedTableDisplayId);

        // 🔧【修复】使用 orderItemId 精确查询，而不是 itemCode 聚合查询
        int totalQuantity = orderService.getOrderItemQuantityByOrderItemId(orderItemId);

        System.out.println(" 更新菜品准备进度: " + itemCode + " → " + preparedQty + "/" +
                totalQuantity + " (" + newStatus + ")");
    } catch (Exception e) {
        throw new RuntimeException("更新准备进度失败: " + e.getMessage(), e);
    }
}
    /**
     * 🔧 精确标记单个 order_item_id 为已上桌（聚餐桌专用）
     */
    public void handleMarkSpecificOrderItemAsServed(String tableNumber, int orderItemId, int quantity) throws SQLException {
        orderService.markSpecificOrderItemAsServed(tableNumber, orderItemId, quantity);
    }


    /**
     * 🔧 聚餐桌专用：智能撤销菜品（数量=0时删除，否则更新）
     *
     * @param tableNumber        餐桌显示编号
     * @param orderItemId        订单项主键
     * @param cancelQuantity     撤销数量
     * @param cancellationReason 撤销原因
     * @param cancelPart         🔧 新增：撤销部分（"SERVED"=已上桌部分 / "UNSERVED"=未上桌部分 / "ALL"=全部）
     * @throws SQLException 操作失败时抛出
     */
    public void handleCancelGroupedTableOrderItemSmart(String tableNumber, int orderItemId,
                                                       int cancelQuantity, String cancellationReason,
                                                       String cancelPart) throws SQLException {
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }
        if (orderItemId <= 0) {
            throw new IllegalArgumentException("订单项ID无效");
        }
        if (cancelQuantity <= 0) {
            throw new IllegalArgumentException("撤销数量必须大于0");
        }
        // 🔧 参数校验：cancelPart 必须为有效值
        if (cancelPart != null && !List.of("SERVED", "UNSERVED", "ALL").contains(cancelPart)) {
            throw new IllegalArgumentException("撤销部分参数无效，必须为: SERVED / UNSERVED / ALL");
        }

        // 直接调用Service层方法（事务由@Transactional管理）
        // 🔧 传递新增的 cancelPart 参数
        orderService.cancelGroupedTableOrderItemSmart(orderItemId, cancelQuantity, cancellationReason, cancelPart);

        // 成功后刷新餐桌缓存
        service.refreshTableCache();
        System.out.println("🗑️ 聚餐桌菜品撤销完成: orderItemId=#" + orderItemId +
                ", 撤销数量=" + cancelQuantity + ", 撤销部分=" + cancelPart);
    }

    /**
     * 🔧 聚餐桌共同菜品撤销（智能更新 quantity/served_quantity/status/distribution）
     *
     * @param orderItemId         订单项主键
     * @param cancelQuantity      撤销数量
     * @param newQuantity         新总数量
     * @param newServedQuantity   新已上桌数量
     * @param newStatus           新状态
     * @param newAssignedTableIds 新 assigned_table_display_id
     * @param newDistribution     新 quantity_distribution
     * @param cancellationReason  撤销原因
     * @param cancelPart          🔧 撤销部分：SERVED/UNSERVED（仅用于业务逻辑，不持久化）
     * @throws SQLException 操作失败时抛出
     */
    public void handleCancelSharedDishOrderItem(
            int orderItemId, int cancelQuantity, int newQuantity,
            int newServedQuantity, String newStatus,
            String newAssignedTableIds, String newDistribution,
            String cancellationReason, String cancelPart) throws SQLException {

        if (orderItemId <= 0) {
            throw new IllegalArgumentException("订单项ID无效");
        }
        if (cancelQuantity <= 0) {
            throw new IllegalArgumentException("撤销数量必须大于0");
        }

        // 直接调用 Service 层方法（事务由 @Transactional 管理）
        // cancelPart 仅用于业务逻辑判断，不持久化，故不传入 Mapper
        orderService.cancelSharedDishOrderItem(
                orderItemId, cancelQuantity, newQuantity,
                newServedQuantity, newStatus,
                newAssignedTableIds, newDistribution,
                cancellationReason, cancelPart
        );

        // 成功后刷新餐桌缓存
        service.refreshTableCache();

        System.out.println("🗑️ 共同菜品撤销完成: orderItemId=#" + orderItemId +
                ", cancelQty=" + cancelQuantity +
                ", newQty=" + newQuantity +
                ", newStatus=" + newStatus +
                ", assignedTables=" + newAssignedTableIds +
                ", cancelPart=" + cancelPart);
    }

    private void showError(String message) {
        if (view != null) {
            view.showError(message);
        } else if (frame != null) {
            JOptionPane.showMessageDialog(frame, message, "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public OrderService getOrderService() {
        return orderService;
    }


}