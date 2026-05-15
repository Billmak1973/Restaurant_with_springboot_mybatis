package com.restaurant.event;

import org.springframework.context.ApplicationEvent;
import java.util.List;

/**
 * 队列变更事件
 * 用于通知前端刷新排队队列显示
 * 🔧 支持指定受影响的餐桌列表（合并桌/聚餐桌精准刷新）
 */
public class QueueChangedEvent extends ApplicationEvent {

    // 🔹 可选：携带变更的队列类型，用于局部刷新
    private final String queueType;  // "2_SEAT" / "4_SEAT" / "6_SEAT" / null(全量刷新)

    // 🔧 新增：受影响的餐桌显示ID列表（用于精准刷新指定餐桌）
    private final List<String> affectedTableDisplayIds;

    /**
     * 完整构造函数
     * @param source 事件源（通常是 Service 实例）
     * @param queueType 变更的队列类型，null 表示全部队列变更
     * @param affectedTableDisplayIds 受影响的餐桌显示ID列表，null 表示刷新全部餐桌
     */
    public QueueChangedEvent(Object source, String queueType, List<String> affectedTableDisplayIds) {
        super(source);
        this.queueType = queueType;
        this.affectedTableDisplayIds = affectedTableDisplayIds;
    }

    /**
     * 向后兼容的构造函数（不指定受影响餐桌）
     */
    public QueueChangedEvent(Object source, String queueType) {
        this(source, queueType, null);
    }

    /**
     * 获取队列类型
     * @return 队列类型或 null
     */
    public String getQueueType() {
        return queueType;
    }

    /**
     * 🔧 获取受影响的餐桌显示ID列表
     * @return 餐桌显示ID列表，null 表示刷新全部
     */
    public List<String> getAffectedTableDisplayIds() {
        return affectedTableDisplayIds;
    }

    /**
     * 便捷工厂方法：全量刷新事件
     */
    public static QueueChangedEvent fullRefresh(Object source) {
        return new QueueChangedEvent(source, null, null);
    }

    /**
     * 便捷工厂方法：指定队列刷新
     */
    public static QueueChangedEvent of(Object source, String queueType) {
        return new QueueChangedEvent(source, queueType, null);
    }

    /**
     * 🔧 新增：支持指定餐桌的工厂方法（精准刷新）
     * @param source 事件源
     * @param tableDisplayIds 需要刷新的餐桌显示ID列表
     */
    public static QueueChangedEvent ofTables(Object source, List<String> tableDisplayIds) {
        return new QueueChangedEvent(source, null, tableDisplayIds);
    }
}