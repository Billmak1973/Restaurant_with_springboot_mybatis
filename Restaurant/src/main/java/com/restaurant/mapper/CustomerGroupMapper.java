package com.restaurant.mapper;

import com.restaurant.entity.CustomerGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerGroupMapper {

    // ===== 保存操作 =====

    /**
     * 保存顾客组
     * @param group 顾客组对象
     * @return 影响行数
     */
    int save(CustomerGroup group);

    /**
     * 保存顾客组，table_id 强制为 NULL
     * @param group 顾客组对象
     * @return 影响行数
     */
    int saveWithoutTableRef(CustomerGroup group);

    // ===== 查询操作 =====

    /**
     * 根据 group_id 查询
     */
    CustomerGroup findById(@Param("id") int id);

    /**
     * 根据 call_number 查询未分配的顾客组
     */
    CustomerGroup findByCallNumber(@Param("callNumber") int callNumber);

    // ===== 更新操作 =====

    /**
     * 更新顾客组全部字段
     */
    int update(CustomerGroup group);

    /**
     * 更新分配状态
     */
    int updateAssignmentStatus(
            @Param("groupId") int groupId,
            @Param("tableId") Integer tableId,
            @Param("isAssigned") boolean isAssigned,
            @Param("shownWaitMessage") boolean shownWaitMessage);

    // CustomerGroupMapper.java - 已有方法，无需新增
    int updateTableId(@Param("groupId") int groupId, @Param("tableId") int tableId);

    // ===== 删除操作 =====

    /**
     * 根据 ID 删除
     */
    int delete(@Param("id") int id);
}
