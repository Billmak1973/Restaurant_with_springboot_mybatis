package com.restaurant.listener;

import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Tables;
import com.restaurant.event.QueueChangedEvent;
import com.restaurant.service.RestaurantService;
import com.restaurant.view.RestaurantView;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 队列变更事件监听器
 * 负责：收到事件 → 刷新 Swing UI
 * 🔧 支持精准刷新指定餐桌（合并桌/聚餐桌场景）
 */
@Component
public class QueueChangeListener {

    private final RestaurantService service;
    private RestaurantView view;

    // 构造函数注入
    public QueueChangeListener(RestaurantService service) {
        this.service = service;
    }

    public void setView(RestaurantView view) {
        this.view = view;
    }

    /**
     * 监听队列变更事件
     * 🔑 关键：必须在 EDT 上更新 Swing UI
     */
    @EventListener
    @Async  // 可选：异步执行，避免阻塞业务线程
    public void handleQueueChanged(QueueChangedEvent event) {
        // 🔧 增加空值保护：防止在 View 初始化前触发事件导致空指针
        if (view == null) {
            return;
        }
        // 确保在 EDT 执行（Swing 线程安全）
        if (SwingUtilities.isEventDispatchThread()) {
            refreshQueueDisplay(event);
        } else {
            SwingUtilities.invokeLater(() -> refreshQueueDisplay(event));
        }
    }

    /**
     * 实际刷新逻辑（支持精准刷新指定餐桌）
     */
    private void refreshQueueDisplay(QueueChangedEvent event) {
        String queueType = event.getQueueType();
        List<String> affectedTables = event.getAffectedTableDisplayIds();  // 🔧 获取受影响餐桌

        // 🔧 局部刷新优化：只更新变更的队列（添加 null 检查）
        if (queueType == null) {
            // 🔧【核心修改】全量刷新时：优先刷新指定餐桌，无指定则刷新全部
            if (view != null) {
                if (affectedTables != null && !affectedTables.isEmpty()) {
                    // ✅ 精准刷新：只刷新指定的餐桌（如合并桌的2张/聚餐桌的多张）
                    for (String displayId : affectedTables) {
                        Tables updatedTable = service.getTableById(displayId);
                        if (updatedTable != null) {
                            view.refreshTableButton(displayId, updatedTable);
                        }
                    }
                    System.out.println("🎯 精准刷新餐桌: " + affectedTables);
                } else {
                    // 兜底：无指定餐桌时刷新全部（保持原有逻辑）
                    service.refreshTableCache();
                    view.updateTablesDisplay(service.getAllTables());
                }
            }

            // 队列显示保持原逻辑
            Queue<CustomerGroup> q2 = (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT");
            Queue<CustomerGroup> q4 = (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT");  // 🔧 修复类型转换
            Queue<CustomerGroup> q6 = (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT");

            view.updateQueueDisplay(
                    q2 != null ? q2 : new LinkedList<>(),  // ← 兜底：如果 null 则传空队列
                    q4 != null ? q4 : new LinkedList<>(),
                    q6 != null ? q6 : new LinkedList<>()
            );
        } else {
            // 只刷新指定队列（保持原有逻辑）
            Queue<CustomerGroup> q2 = "2_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT") : new LinkedList<>();
            Queue<CustomerGroup> q4 = "4_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT") : new LinkedList<>();
            Queue<CustomerGroup> q6 = "6_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT") : new LinkedList<>();

            view.updateQueueDisplay(q2, q4, q6);
        }
    }
}