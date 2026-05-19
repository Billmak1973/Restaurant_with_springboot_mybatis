package com.restaurant.mapper;

import com.restaurant.entity.CustomerGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 顾客组数据访问接口（操作 customer_groups 表）
 */
@Mapper
public interface CustomerGroupMapper {

    // ===== 保存操作 =====

    /**
     * 保存顾客组（含 table_id 关联）
     */
    int save(CustomerGroup group);

    /**
     * 保存顾客组（table_id 强制为 NULL，用于未分配时）
     */
    int saveWithoutTableRef(CustomerGroup group);

    // ===== 查询操作 =====

    /**
     * 根据 group_id 查询顾客组详情
     */
    CustomerGroup findById(@Param("id") int id);

    /**
     * 根据叫号查询【未分配】的顾客组（用于队列匹配）
     */
    CustomerGroup findByCallNumber(@Param("callNumber") int callNumber);

    // ===== 更新操作 =====

    /**
     * 更新顾客组全部字段（全量更新）
     */
    int update(CustomerGroup group);

    /**
     * 更新顾客组分配状态（餐桌/入座标记/等待提示）
     */
    int updateAssignmentStatus(
            @Param("groupId") int groupId,
            @Param("tableId") Integer tableId,
            @Param("isAssigned") boolean isAssigned,
            @Param("shownWaitMessage") boolean shownWaitMessage);

    /**
     * 仅更新顾客组关联的餐桌 ID
     */
    int updateTableId(@Param("groupId") int groupId, @Param("tableId") int tableId);

    /**
     * 更新顾客组人数（用于队列编辑）
     */
    int updateGroupSize(@Param("groupId") int groupId, @Param("newSize") int newSize);

    // ===== 删除操作 =====

    /**
     * 根据 ID 物理删除顾客组记录
     */
    int delete(@Param("id") int id);
}