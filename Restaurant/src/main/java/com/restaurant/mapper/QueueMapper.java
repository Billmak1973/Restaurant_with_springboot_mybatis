package com.restaurant.mapper;

import com.restaurant.entity.CustomerGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QueueMapper {

    // ===== 队列插入与查询 =====

    /**
     * 插入队列记录
     * @param queueType 队列类型（2_SEAT/4_SEAT/6_SEAT）
     * @param groupId 顾客组ID
     * @param position 队列位置
     * @return 影响行数
     */
    int insertQueue(
            @Param("queueType") String queueType,
            @Param("groupId") int groupId,
            @Param("position") int position);

    /**
     * 获取指定队列类型的下一个位置
     * @param queueType 队列类型
     * @return 下一个位置编号
     */
    int getNextQueuePosition(@Param("queueType") String queueType);

    /**
     * 根据顾客组ID查询所属队列类型
     * @param groupId 顾客组ID
     * @return 队列类型，不存在返回 null
     */
    String findQueueTypeByGroupId(@Param("groupId") int groupId);

    // ===== 队列位置管理 =====

    /**
     * 重排指定队列类型的位置（使用窗口函数）
     * @param queueType 队列类型
     * @return 影响行数
     */
    int updateQueuePositions(@Param("queueType") String queueType);

    /**
     * 从队列中移除顾客组并重排位置
     * @param groupId 顾客组ID
     * @param queueType 队列类型
     * @return 影响行数（删除的行数）
     */
    int removeFromQueue(
            @Param("groupId") int groupId,
            @Param("queueType") String queueType);

    // ===== 队列加载 =====

    /**
     * 加载指定队列类型的顾客组列表（含队列位置）
     * @param queueType 队列类型
     * @return 顾客组列表，按 position 排序
     */
    List<CustomerGroup> loadQueueByType(@Param("queueType") String queueType);
}
