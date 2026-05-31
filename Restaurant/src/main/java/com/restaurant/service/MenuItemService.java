package com.restaurant.service;

import com.restaurant.entity.MenuItem;
import com.restaurant.mapper.MenuItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service// 声明服务层组件，交由 Spring 容器管理，封装菜品相关业务逻辑
public class MenuItemService {
    private final MenuItemMapper menuItemMapper;

    // 构造函数注入（Spring 4.3+ 支持单构造函自动注入）
    public MenuItemService(MenuItemMapper menuItemMapper) {
        this.menuItemMapper = menuItemMapper;
    }

    /**
     * 根据分类编号查询菜品列表
     *
     * 功能说明：
     * 调用 Mapper 查询指定分类下的所有菜品记录，返回按编码排序的列表，
     * 用于点餐界面按分类展示可选菜品。
     *
     * @param categoryId 菜品分类主键（如 1=特色食物，2=饮料）
     * @return 菜品列表；分类无菜品时返回空列表
     *
     * 数据来源：
     * - 直接查询数据库 menu_items 表，条件：category_id 匹配且 is_active = true
     *
     * 应用场景：
     * - 顾客点餐时按分类加载菜品
     * - 后台管理界面按分类筛选菜品
     */
    @Transactional(readOnly = true)// 标记只读事务，仅用于查询操作并启用数据库读取优化
    public List<MenuItem> getMenuItemsByCategory(int categoryId) {
        return menuItemMapper.findByCategory(categoryId);
    }

    /**
     * 根据菜品编码查询单个菜品详情
     *
     * 功能说明：
     * 1. 校验菜品编码参数有效性，为空时返回 null
     * 2. 调用 Mapper 查询指定编码的菜品对象
     *
     * @param itemCode 菜品编码（如 "A1"）
     * @return 菜品对象；编码无效或菜品不存在时返回 null
     *
     * 业务规则：
     * - 编码查询不区分大小写，自动转换为大写匹配
     * - 仅返回上架状态的菜品，下架菜品视为不存在
     *
     * 应用场景：
     * - 顾客点击菜品时加载详细信息
     * - 订单明细展示时查询菜品名称与价格
     */
    @Transactional(readOnly = true)
    public MenuItem getMenuItemByCode(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) {
            return null;
        }
        return menuItemMapper.findById(itemCode.trim().toUpperCase());
    }

    /**
     * 添加新菜品到菜单数据库
     *
     * 功能说明：
     * 调用 Mapper 将菜品实体插入菜单表，支持事务回滚确保数据一致性。
     *
     * @param item 待添加的菜品对象，需包含编码、名称、价格、分类等必填字段
     * @return true=添加成功；false=插入失败（如主键冲突）
     *
     * 异常处理：
     * - 数据库操作异常时抛出 RuntimeException，事务自动回滚
     * - 调用方需捕获异常并向用户展示友好提示
     *
     * 业务规则：
     * - 菜品编码需唯一，重复添加将导致主键冲突
     * - 新菜品默认状态为上架（isActive = true）
     */
    @Transactional// 开启数据库事务，确保方法内多条SQL操作的原子性与一致性
    public boolean addItem(MenuItem item) {
        try {
            int result = menuItemMapper.addItem(item);
            return result > 0;
        } catch (Exception e) {
            // 异常会自动回滚事务
            throw new RuntimeException("添加菜品失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取下一个可用的菜品编码
     *
     * 功能说明：
     * 1. 根据分类主键获取对应前缀（如 1→"A"）
     * 2. 查询该前缀下已存在的最大序号
     * 3. 返回前缀 + (最大序号 + 1) 作为新编码
     *
     * @param categoryId 菜品分类主键
     * @return 下一个可用编码（如 "A5"）；查询失败时返回前缀 + "1"
     *
     * 编码规则：
     * - 格式：前缀 + 数字序号（如 "A1", "B12"）
     * - 序号按分类独立递增，确保同类菜品编码连续
     *
     * 应用场景：
     * - 新增菜品界面自动生成默认编码
     * - 批量导入菜品时预分配唯一编码
     */
    @Transactional(readOnly = true)
    public String getNextItemCode(int categoryId) {
        String prefix = getPrefixByCategoryId(categoryId);
        Integer maxNum = menuItemMapper.getMaxItemNumberByPrefix(prefix);
        return prefix + (maxNum != null ? maxNum + 1 : 1);
    }

    /**
     * 根据菜品分类编号获取编码前缀
     *
     * 功能说明：
     * 将分类主键映射为菜品编码的字母前缀，用于自动生成新菜品的标准化编号。
     *
     * @param categoryId 菜品分类主键（1=特色食物，2=饮料，3=小炒，4=套餐）
     * @return 对应的前缀字符串（"A"/"B"/"C"/"D"）；未知分类返回默认值"X"
     *
     * 业务规则：
     * - 前缀与分类一一对应，确保菜品编码具有可读性与分类标识
     * - 默认前缀"X"用于未归类或临时菜品，便于后续整理
     */
    private String getPrefixByCategoryId(int categoryId) {
        return switch (categoryId) {
            case 1 -> "A";  // 特色食物
            case 2 -> "B";  // 饮料
            case 3 -> "C";  // 小炒
            case 4 -> "D";  // 套餐
            default -> "X";
        };
    }

    /**
     * 更新菜品的上架/下架状态
     *
     * 功能说明：
     * 1. 校验菜品编码参数有效性，为空时直接返回失败
     * 2. 调用 Mapper 更新指定菜品的活跃状态字段
     *
     * @param itemCode 菜品编码（如 "A1"）
     * @param isActive 新状态：true=上架可售，false=下架隐藏
     * @return true=更新成功；false=菜品不存在或参数无效
     *
     * 业务规则：
     * - 下架菜品仍保留历史订单记录，仅在前端点餐界面隐藏
     * - 状态变更立即生效，无需重启服务或刷新缓存
     *
     * 异常处理：
     * - 数据库更新失败时返回 false，不抛出异常，便于调用方友好提示
     */
    @Transactional
    public boolean updateStatus(String itemCode, boolean isActive) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return false;
        }

        int result = menuItemMapper.updateStatus(itemCode.trim().toUpperCase(), isActive);
        return result > 0;  // 影响行数>0表示更新成功
    }

    /**
     * 物理删除指定菜品
     *
     * 功能说明：
     * 1. 校验菜品编码参数有效性
     * 2. 检查菜品是否已被订单引用，若存在则禁止删除以保障历史数据完整性
     * 3. 执行物理删除操作，从菜单表中移除记录
     *
     * @param itemCode 待删除的菜品编码
     * @return true=删除成功；false=菜品不存在或参数无效
     *
     * 异常处理：
     * - 菜品已被订单使用时抛出 RuntimeException，阻止删除操作
     * - 调用方需捕获异常并向用户展示友好提示
     *
     * 业务规则：
     * - 仅未使用的菜品允许物理删除，已产生订单的菜品需保留
     * - 删除操作不可逆，执行前需二次确认
     */
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
     * 更新菜品价格
     *
     * 功能说明：
     * 1. 校验菜品编码参数有效性
     * 2. 查询菜品确认存在性，避免误更新不存在的记录
     * 3. 调用 Mapper 更新指定菜品的价格字段
     *
     * @param itemCode 菜品编码（如 "A1"）
     * @param newPrice 新价格数值
     * @return true=更新成功；false=菜品不存在或参数无效
     *
     * 业务规则：
     * - 价格变更仅影响新订单，历史订单按下单时价格结算
     * - 价格更新立即生效，前端点餐界面同步展示新价格
     *
     * 异常处理：
     * - 菜品不存在时返回 false，不抛出异常，便于调用方友好提示
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