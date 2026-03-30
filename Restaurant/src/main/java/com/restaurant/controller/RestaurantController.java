package com.restaurant.controller;

import com.restaurant.entity.*;
import com.restaurant.service.MenuItemService;
import com.restaurant.service.OrderService;
import com.restaurant.service.RestaurantService;
import com.restaurant.view.OrderSystemGUI;
import com.restaurant.view.RestaurantView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RestaurantController {

    private final RestaurantService service;
    private RestaurantView view;
    private OrderSystemGUI frame;
    private final MenuItemService menuItemService;  //  新增注入
    private final OrderService orderService;  //  新增注入

    @Autowired
    public RestaurantController(RestaurantService service, MenuItemService menuItemService, OrderService orderService) {
        this.service = service;
        this.menuItemService = menuItemService;
        this.orderService = orderService;
    }


    public void setView(RestaurantView view) {
        this.view = view;
        view.setController(this);
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

    private void bindEvents() {
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

    }


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
                view.appendToLog(message);
                view.clearGroupSizeInput();
                refreshTablesDisplay();
                //view.updateQueueDisplay(); // 如有队列UI，同步更新

            } else {
                // 添加失败：餐厅未营业或系统异常
                view.showError("添加失败：餐厅未营业或系统错误");
                view.appendToLog("顾客组添加失败 - 人数: " + groupSize);
            }

        } catch (NumberFormatException ex) {
            view.showError("请输入有效的人数");
            view.appendToLog("输入格式错误: '" + view.getGroupSizeInput() + "'");

        } catch (Exception ex) {
            ex.printStackTrace();
            view.showError("系统异常：" + ex.getMessage());
            view.appendToLog("系统异常: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
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

                //lambda 表达式中使用的变量应为 final 或有效 final
                view.appendToLog(" 餐桌 #" + displayId +
                        " 已拆分為: " +
                        subTables.get(0).getDisplayId() + ", " +
                        subTables.get(1).getDisplayId());

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

                        // 🔧【關鍵】使用預先收集的子桌信息（不再查詢數據庫）
                        String subTableNames = subTableDisplayIds.isEmpty() ? "無" :
                                String.join(", ", subTableDisplayIds);

                        view.appendToLog(" 餐桌 #" + mainTableDisplayId +
                                " 合併成功，子桌 [" + subTableNames + "] 已恢復");

                        JOptionPane.showMessageDialog(view,
                                "餐桌 #" + mainTableDisplayId + " 合併成功！\n" +
                                        "子桌已恢復為主桌狀態",
                                "操作成功",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (IllegalArgumentException | IllegalStateException ex) {
                    SwingUtilities.invokeLater(() -> {
                        view.showError("合併失敗: " + ex.getMessage());
                        view.appendToLog(" 合併失敗: " + ex.getMessage());
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        view.showError("系統錯誤: " + ex.getMessage());
                        view.appendToLog(" 系統異常: " + ex.getClass().getSimpleName());
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
                    view.appendToLog("✓ 已将餐桌 #" + fromInput + " 的顾客组转移到餐桌 #" + toInput);
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


    private void handleClearAll(ActionEvent e) {
    }

    private void handleCloseDay(ActionEvent e) {
    }

    private void handleShowBusinessReport() {
    }

    public void updateView() {
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
                    view.appendToLog("餐桌 #" + displayId + " 顾客已离店，状态更新为「准备中」");
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
                    view.appendToLog("聚餐桌组 [" + tableList + "] 顾客已离店，状态更新为「准备中」");
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
                    view.appendToLog("餐桌 #" + displayId + " 顾客已离店，状态更新为「准备中」");
                });
            } catch (Exception e) {
                showError("离店操作失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理准备中餐桌的清理逻辑
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
                service.cleanTable(displayId);
                refreshSingleTable(displayId);
                view.appendToLog("餐桌 #" + displayId + " 已清理完成，恢复为空闲状态");
            } catch (Exception e) {
                showError("清理失败: " + e.getMessage());
                e.printStackTrace();
            }
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
                    view.showInfo("✅ 预约成功！预约号：" + serviceResult.get("reservationId"));
                    view.appendToLog("新增预约：" + serviceResult.get("reservationId"));
                    refreshTablesDisplay();

                    String tableSelectionMode = (String) result.get("tableSelectionMode");
                    if ("QUANTITY".equals(tableSelectionMode)) {
                        view.refreshQuantityReservationsLog();
                    }
                } else {
                    view.showError("❌ 预约失败：" + serviceResult.get("message"));
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
                    view.showError("❌ 预约号不能为空！");
                    return;
                }
                if (selectedDisplayIds == null || selectedDisplayIds.isEmpty()) {
                    view.showError("❌ 请至少选择一张餐桌！");
                    return;
                }

                // 3️⃣ 🔧【核心】调用 Service 层执行分配逻辑（事务由 Service 管理）
                service.assignTablesToReservation(reservationId, selectedDisplayIds);

                // 4️⃣ 成功反馈 + 刷新界面
                view.showInfo("✅ 餐桌分配成功！\n已锁定：" + String.join(", ", selectedDisplayIds));
                view.appendToLog("预约 " + reservationId + " 已分配餐桌: " + String.join(",", selectedDisplayIds));
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
        // 【CANCEL / EDIT_TIME 模式】- 后续补充
        // ═══════════════════════════════════════════════════════════
        else if ("CANCEL".equals(mode)) {
            view.showInfo("⚠️ 取消预约功能开发中...");
        }
        else if ("EDIT_TIME".equals(mode)) {
            view.showInfo("⚠️ 修改预约功能开发中...");
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
                    view.showInfo(" 客人已入座！\n餐桌 #" + table.getDisplayId() +
                            " 状态已更新为【占用中】");
                } catch (Exception ex) {
                    view.showError("入座失败：" + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            case "CANCEL" -> {
                // 预留：取消预约功能
                view.showInfo("⚠️ 取消预约功能开发中...\n\n后续将实现：\n" +
                        "1. 释放餐桌（RESERVED → VACANT）\n" +
                        "2. 更新叫号系统");
                // TODO: service.processCancelReservation(table.getDisplayId());
            }
            case "DETAIL" -> {
                // 预留：查看详情功能
                view.showInfo("📄 预约详情功能开发中...");
            }
        }
    }


    /**
     * 🔧 获取数量模式预约记录（用于日志显示）
     */
    public List<Map<String, Object>> getQuantityModeReservationsForLog() {
        return service.getQuantityModeReservationsForLog();
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
     *  根据预约号片段查询预约详情（支持模糊查询）
     */
    public List<TableReservation>findReservationsByCodeFragment(String codeFragment) {
        if (service == null || codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findReservationsByCodeFragment(codeFragment);
    }
    /**
     * 获取所有餐桌（用于分配餐桌时显示）
     */
    public List<Tables> getAllVacantTables() {
        if (service == null) return new ArrayList<>();
        return service.getAllVacantTables();  // 假设Service有这个方法
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

    private void handleOpenOrderSystem() {
        new OrderSystemGUI(this, service, menuItemService, orderService).setVisible(true);
    }

    public String getOrderStatusDisplay(String tableNumber) {
        return service.getOrderStatusDisplay(tableNumber);
    }


    /**
     * 处理确认下单（完整版：支持合并订单 + 三模式 + 金额分离 + 刷新餐桌狀態失敗）
     */
    public void handleConfirmOrder(String identifier,
                                   List<OrderItem> orderItems,
                                   OrderType orderType,
                                   String customerName,
                                   String customerPhone,
                                   String deliveryAddress,
                                   Double deliveryFee,
                                   boolean isReorderAfterCheckout,  // 🔧 新增参数
                                   Runnable onSuccess) {

        // ═══════════════════════════════════════════════════════════
        // 【步骤1】基础参数验证
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
            // 非配送模式：强制配送费为0
            deliveryFee = 0.0;
        }

        // 外卖模式（自取+配送）必须填写电话
        if (orderType != OrderType.DINE_IN &&
                (customerPhone == null || customerPhone.trim().isEmpty())) {
            showError("外卖订单必须填写联系电话");
            return;
        }

        // 堂食模式专属验证
        if (orderType == OrderType.DINE_IN) {
            if (identifier == null || identifier.isEmpty() || "未选择".equals(identifier)) {
                showError("堂食订单必须先选择餐桌");
                return;
            }
            Tables table = service.getTableById(identifier);
            if (table == null || (table.getStatus() != Tables.TableStatus.OCCUPIED && table.getStatus() != Tables.TableStatus.RESERVED)) {
                showError("餐桌 " + identifier + " 状态无效，不能点餐");
                return;
            }
        }

        // ═══════════════════════════════════════════════════════════
        // 【步骤2】变量固化（供 SwingWorker 内部使用）
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
        // 【步骤3】异步执行下单逻辑（SwingWorker）
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

                    // 3.3 根据订单类型获取标识
                    if (finalOrderType == OrderType.DINE_IN) {
                        // 堂食：通过餐桌获取
                        Tables table = service.getTableById(finalIdentifier);
                        if (table == null) {
                            throw new RuntimeException("未找到餐桌: " + finalIdentifier);
                        }
                        tableId = table.getTableId();
                        existingOrderId = orderService.findActiveOrderIdByTableId(tableId);

                    } else {
                        // 外卖/配送：通过订单号获取
                        if (finalIdentifier != null &&
                                !finalIdentifier.isEmpty() &&
                                !"待下单".equals(finalIdentifier)) {

                            Order existingOrder = orderService.findActiveOrderByOrderNumber(finalIdentifier);
                            if (existingOrder != null && "ORDERED".equals(existingOrder.getStatus())) {
                                existingOrderId = existingOrder.getOrderId();
                                orderNumber = existingOrder.getOrderNumber();
                            }
                        }

                        // 如果是新外卖订单，生成订单号
                        if (existingOrderId == null) {
                            String prefix = (finalOrderType == OrderType.PICKUP) ? "P" : "D";
                            String dateStr = java.time.LocalDate.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                            Integer seq = orderService.getNextTakeoutOrderNumber(
                                    prefix, dateStr, finalOrderType.getDbDeliveryMethod());
                            orderNumber = String.format("%s-%s-%d", prefix, dateStr, seq);
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 🔧【新增步骤 3.3.5】处理已结账后重新点单（关键位置！）
                    // ═══════════════════════════════════════════════════════════
                    if (isReorderAfterCheckout && finalOrderType == OrderType.DINE_IN) {
                        // 1. 查找该餐桌最近的已结账订单
                        Integer checkedOutOrderId = orderService.findCheckedOutOrderIdByTableId(tableId);

                        if (checkedOutOrderId != null) {
                            // 2. 重置订单状态 + 清空金额 + 清空明细
                            orderService.resetCheckedOutOrder(checkedOutOrderId);

                            // 3. 复用该订单ID，后续将菜品合并到此订单
                            existingOrderId = checkedOutOrderId;

                            System.out.println(" 已重置餐桌 #" + finalIdentifier +
                                    " 的已结账订单: orderId=" + checkedOutOrderId);
                        }
                    }

                    // 3.4 核心：合并订单 或 创建新订单
                    Integer orderId;
                    if (existingOrderId != null) {
                        //  场景：已存在订单 → 合并新菜品（支持追加点单）
                        // 🔧【关键修复】itemCode 统一转大写+去空格，确保与数据库匹配
                        Map<String, Integer> newItemsMap = finalOrderItems.stream()
                                .collect(Collectors.toMap(
                                        item -> item.getItemCode().trim().toUpperCase(),  //  强制大写
                                        OrderItem::getQuantity,
                                        Integer::sum  // 相同菜品数量累加
                                ));

                        // 调用 Service 层合并逻辑（@Transactional 保证原子性）
                        orderService.mergeOrderItems(existingOrderId, newItemsMap);
                        orderId = existingOrderId;

                        if (finalOrderType == OrderType.DELIVERY && finalDeliveryFee != null) {
                            orderService.updateOrderDeliveryFee(existingOrderId, finalDeliveryFee);
                            System.out.println("🚚 已更新配送费: orderId=" + existingOrderId +
                                    ", deliveryFee=" + finalDeliveryFee);
                        }

                        System.out.println(" 订单合并成功: orderId=" + orderId +
                                ", 合并菜品数=" + newItemsMap.size());

                    } else {
                        Order.DeliveryStatus deliveryStatus = null;
                        if (finalOrderType == OrderType.DELIVERY) {
                            deliveryStatus = Order.DeliveryStatus.NOT_DELIVERED;  // 新配送订单默认"未配送"
                        }

                        //  场景：新订单 → 创建完整订单记录
                        orderId = orderService.createOrder(
                                tableId,
                                orderNumber,
                                finalOrderType.getDbOrderType(),
                                finalOrderType.getDbDeliveryMethod(),
                                finalOrderType == OrderType.DELIVERY ? finalDeliveryAddress : null,
                                finalCustomerPhone,
                                finalCustomerName,
                                itemsTotal,           // 菜品总金额
                                finalDeliveryFee,     // 配送费（非配送=0）
                                finalTotalAmount,     // 最终总金额
                                finalOrderItems,
                                deliveryStatus  // 🔧 新增参数
                        );

                        if (orderId == null || orderId <= 0) {
                            throw new RuntimeException("创建订单失败: 返回orderId无效");
                        }

                        System.out.println(" 新订单创建成功: orderId=" + orderId +
                                ", orderNumber=" + orderNumber);
                    }

                    // 3.5 更新堂食餐桌状态（仅堂食模式）
                    if (finalOrderType == OrderType.DINE_IN && finalIdentifier != null) {
                        Tables table = service.getTableById(finalIdentifier);
                        if (table != null) {
                            table.setOrderStatus(Tables.OrderStatus.ORDERED_UNFINISHED);
                        }
                    }

                    // 3.6 日志输出（调试用）
                    System.out.println("🎯 订单处理完成 | type=" + finalOrderType +
                            " | itemsTotal=" + itemsTotal +
                            " | deliveryFee=" + finalDeliveryFee +
                            " | total=" + finalTotalAmount);

                    return null;

                } catch (Exception e) {
                    // 异常包装，便于上层统一处理
                    throw new RuntimeException("下单失败: " + e.getMessage(), e);
                }
            }

            @Override
            protected void done() {
                try {
                    // 等待异步任务完成，抛出异常（如果有）
                    get();

                    // 【关键修复】执行成功回调前先刷新餐桌缓存
                    if (finalOnSuccess != null) {
                        SwingUtilities.invokeLater(() -> {
                            // 1. 刷新餐桌缓存（从数据库重新加载订单状态）
                            service.refreshTableCache();

                            // 2. 执行原有的成功回调（刷新GUI）
                            finalOnSuccess.run();
                        });
                    }

                } catch (Exception e) {
                    // 错误处理：显示友好提示 + 打印堆栈
                    showError("下单失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // 启动异步任务
        worker.execute();
    }

    public void handleMarkItemsAsServed(String tableNumber, int itemId, int quantity) throws SQLException {
        try {
            //  直接調用 Service，事務由 @Transactional 自動管理
            orderService.markItemsAsServed(tableNumber, itemId, quantity);

            // 成功後刷新餐桌緩存（可選）
            service.refreshTableCache();

            System.out.println(" Controller: 標記上桌成功 - " + tableNumber);

        } catch (IllegalArgumentException e) {
            // 業務驗證異常，直接拋出給 View 層處理
            throw e;
        } catch (SQLException e) {
            // 數據庫異常，包裝後拋出
            throw new SQLException("標記上桌失敗: " + e.getMessage(), e);
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

            System.out.println(" Controller: 全部上桌成功 - " + tableNumber);

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
            System.out.println("🎯 Controller: 外賣製作完成標記成功 - " + orderNumber);
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
            System.out.println("🎯 Controller: 外賣全部製作完成標記成功 - " + orderNumber);
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
            System.out.println("🎯 Controller: 外賣撤銷成功 - " + orderNumber);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLException("撤銷外賣菜品失敗：" + e.getMessage(), e);
        }
    }

    /**
     * 撤銷堂食訂單中的菜品（Controller 層 - 僅轉發 + 刷新）
     */
    public void handleCancelOrderItem(String tableNumber, String itemCode, int cancelQuantity, String cancellationReason) throws SQLException {
        try {
            //  調用 Service 層方法，事務由 @Transactional 自動管理
            orderService.cancelOrderItem(tableNumber, itemCode, cancelQuantity, cancellationReason);

            // 成功後刷新餐桌緩存
            service.refreshTableCache();
            System.out.println("🎯 Controller: 堂食撤銷成功 - " + tableNumber);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 業務驗證異常，直接拋出給 View 層處理
            throw e;
        } catch (SQLException e) {
            // 數據庫異常，包裝後拋出
            throw new SQLException("撤銷堂食菜品失敗: " + e.getMessage(), e);
        }
    }


    /**
     * 撤销整个外卖订单（整单取消）
     */
    public void handleCancelTakeoutOrder(String orderNumber, String reason) throws SQLException {
        try {
            orderService.cancelTakeoutOrder(orderNumber, reason);
            System.out.println("🎯 Controller: 外卖整单撤销成功 - " + orderNumber);
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
            service.refreshTableCache();  // ← 使用 RestaurantService 刷新缓存
            System.out.println(" 已刷新餐桌缓存: " + tableNumber);
        }

        return result;
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