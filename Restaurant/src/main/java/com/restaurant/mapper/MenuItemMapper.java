
package com.restaurant.mapper;

import com.restaurant.entity.MenuItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MenuItemMapper {

    // ===== 插入操作 =====

    /**
     * 添加菜品
     * @param item 菜品对象
     * @return 影响行数
     */
    int addItem(MenuItem item);

    // ===== 查询操作 =====

    /**
     * 根据分类 ID 查询菜品列表
     * @param categoryId 分类 ID
     * @return 菜品列表
     */
    List<MenuItem> findByCategory(@Param("categoryId") int categoryId);

    /**
     * 根据 item_code 查询单个菜品
     * @param itemCode 菜品编号（如 "A1"）
     * @return 菜品对象，不存在返回 null
     */
    MenuItem findById(@Param("itemCode") String itemCode);

    /**
     * 根据 item_code 查询 item_id
     * @param itemCode 菜品编号
     * @return item_id，不存在返回 null
     */
    Integer findItemIdByCode(@Param("itemCode") String itemCode);

    /**
     * 检查菜品是否在订单明细中存在
     * @param itemCode 菜品编号
     * @return true=存在，false=不存在
     */
    boolean existsInOrderItems(@Param("itemCode") String itemCode);

    // ===== 更新操作 =====

    /**
     * 更新菜品状态（是否可售）
     * @param itemCode 菜品编号
     * @param isActive 是否可售
     * @return 影响行数
     */
    int updateStatus(@Param("itemCode") String itemCode, @Param("isActive") boolean isActive);

    /**
     * 更新菜品价格
     * @param itemCode 菜品编号
     * @param newPrice 新价格
     * @return 影响行数
     */
    int updatePrice(@Param("itemCode") String itemCode, @Param("newPrice") double newPrice);


    Integer getMaxItemNumberByPrefix(@Param("prefix") String prefix);

    // ===== 删除操作 =====

    /**
     * 物理删除菜品（仅操作 menu_items 表）
     * @param itemCode 菜品编号
     * @return 影响行数
     */
    int deletePhysically(@Param("itemCode") String itemCode);
}
