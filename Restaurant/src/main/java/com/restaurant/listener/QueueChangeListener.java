package com.restaurant.listener;

import com.restaurant.entity.CustomerGroup;
import com.restaurant.event.QueueChangedEvent;
import com.restaurant.service.RestaurantService;
import com.restaurant.view.RestaurantView;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.util.Queue;

/**
 * 队列变更事件监听器
 * 负责：收到事件 → 刷新 Swing UI
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
     * 实际刷新逻辑
     */
    private void refreshQueueDisplay(QueueChangedEvent event) {
        String queueType = event.getQueueType();

        // 🔹 局部刷新优化：只更新变更的队列
        if (queueType == null) {
            // 全量刷新
            view.updateQueueDisplay(
                    (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT"),
                    (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT"),
                    (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT")
            );
        } else {
            // 只刷新指定队列（其他传 null，View 层判断跳过）
            view.updateQueueDisplay(
                    "2_SEAT".equals(queueType) ? (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT") : null,
                    "4_SEAT".equals(queueType) ? (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT") : null,
                    "6_SEAT".equals(queueType) ? (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT") : null
            );
        }
    }
}