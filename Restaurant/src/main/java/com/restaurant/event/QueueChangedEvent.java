package com.restaurant.event;

import org.springframework.context.ApplicationEvent;

/**
 * 队列变更事件
 * 用于通知前端刷新排队队列显示
 */
public class QueueChangedEvent extends ApplicationEvent {

    // 🔹 可选：携带变更的队列类型，用于局部刷新
    private final String queueType;  // "2_SEAT" / "4_SEAT" / "6_SEAT" / null(全量刷新)

    /**
     * 构造函数
     * @param source 事件源（通常是 Service 实例）
     * @param queueType 变更的队列类型，null 表示全部队列变更
     */
    public QueueChangedEvent(Object source, String queueType) {
        super(source);
        this.queueType = queueType;
    }

    /**
     * 获取队列类型
     * @return 队列类型或 null
     */
    public String getQueueType() {
        return queueType;
    }

    /**
     * 便捷工厂方法：全量刷新事件
     */
    public static QueueChangedEvent fullRefresh(Object source) {
        return new QueueChangedEvent(source, null);
    }

    /**
     * 便捷工厂方法：指定队列刷新
     */
    public static QueueChangedEvent of(Object source, String queueType) {
        return new QueueChangedEvent(source, queueType);
    }
}
