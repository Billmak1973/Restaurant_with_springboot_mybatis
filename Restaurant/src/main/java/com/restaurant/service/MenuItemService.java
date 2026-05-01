package com.restaurant.service;

import com.restaurant.entity.MenuItem;
import com.restaurant.mapper.MenuItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuItemService {
    private final MenuItemMapper menuItemMapper;

    // 构造函数注入（Spring 4.3+ 支持单构造函自动注入）
    public MenuItemService(MenuItemMapper menuItemMapper) {
        this.menuItemMapper = menuItemMapper;
    }

    @Transactional(readOnly = true)
    public List<MenuItem> getMenuItemsByCategory(int categoryId) {
        return menuItemMapper.findByCategory(categoryId);
    }

    @Transactional(readOnly = true)
    public MenuItem getMenuItemByCode(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) {
            return null;
        }
        return menuItemMapper.findById(itemCode.trim().toUpperCase());
    }

    //  新增：添加菜品（带事务）
    @Transactional
    public boolean addItem(MenuItem item) {
        try {
            int result = menuItemMapper.addItem(item);
            return result > 0;
        } catch (Exception e) {
            // 异常会自动回滚事务
            throw new RuntimeException("添加菜品失败: " + e.getMessage(), e);
        }
    }

    //  新增：获取下一个菜品编号（替代 MenuCategoryService）
    @Transactional(readOnly = true)
    public String getNextItemCode(int categoryId) {
        String prefix = getPrefixByCategoryId(categoryId);
        Integer maxNum = menuItemMapper.getMaxItemNumberByPrefix(prefix);
        return prefix + (maxNum != null ? maxNum + 1 : 1);
    }

    private String getPrefixByCategoryId(int categoryId) {
        return switch (categoryId) {
            case 1 -> "A";  // 特色食物
            case 2 -> "B";  // 饮料
            case 3 -> "C";  // 小炒
            case 4 -> "D";  // 套餐
            default -> "X";
        };
    }

    //  新增：更新菜品状态（带事务）
    @Transactional
    public boolean updateStatus(String itemCode, boolean isActive) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return false;
        }

        int result = menuItemMapper.updateStatus(itemCode.trim().toUpperCase(), isActive);
        return result > 0;  // 影响行数>0表示更新成功
    }


    @Transactional
    public boolean deleteMenuItemByCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return false;
        }

        // 先检查是否被订单使用
        if (menuItemMapper.existsInOrderItems(itemCode.trim().toUpperCase())) {
            throw new RuntimeException("无法删除菜品：该菜品已被订单使用，不能删除历史数据");
        }

        int result = menuItemMapper.deletePhysically(itemCode.trim().toUpperCase());
        return result > 0;
    }

    /**
     * 根据菜品编号更新价格
     * @param itemCode 菜品编号（如 "A1"）
     * @param newPrice 新价格
     * @return true=更新成功，false=菜品不存在
     */
    @Transactional
    public boolean updatePrice(String itemCode, double newPrice) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return false;
        }
        // 先检查菜品是否存在
        MenuItem item = menuItemMapper.findById(itemCode.trim().toUpperCase());
        if (item == null) {
            return false;
        }
        // 执行价格更新
        int result = menuItemMapper.updatePrice(itemCode.trim().toUpperCase(), newPrice);
        return result > 0;
    }
}