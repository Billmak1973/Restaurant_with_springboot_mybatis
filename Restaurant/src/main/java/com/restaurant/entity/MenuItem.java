package com.restaurant.entity;


import org.apache.ibatis.type.Alias;

@Alias("MenuItem")
public class MenuItem {
    private int itemId;
    private String itemCode;    // A1, B2 等
    private String name;        // 菜品名称
    private double price;       // 价格（使用 double）
    private int categoryId;     // 分类ID
    private boolean isActive;   // 是否可用

    /**
     * 全参构造函数
     * @param itemId 菜品主键
     * @param itemCode 菜品编码
     * @param name 菜品名称
     * @param price 菜品价格
     * @param categoryId 分类主键
     * @param isActive 是否上架
     */
    public MenuItem(int itemId, String itemCode, String name, double price,
                    int categoryId, boolean isActive) {
        this.itemId = itemId;
        this.itemCode = itemCode;
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
        this.isActive = isActive;
    }

    /**
     * 简化构造函数（用于新增菜品）
     * 默认主键为0，状态为上架
     * @param itemCode 菜品编码
     * @param name 菜品名称
     * @param price 菜品价格
     * @param categoryId 分类主键
     */
    public MenuItem(String itemCode, String name, double price, int categoryId) {
        this(0, itemCode, name, price, categoryId, true);
    }

    /**
     * 获取菜品主键
     * @return 菜品主键
     */
    public int getItemId() { return itemId; }

    /**
     * 获取菜品编码
     * @return 菜品编码
     */
    public String getItemCode() { return itemCode; }

    /**
     * 获取菜品名称
     * @return 菜品名称
     */
    public String getName() { return name; }

    /**
     * 获取菜品价格
     * @return 菜品价格
     */
    public double getPrice() { return price; }
    public int getCategoryId() { return categoryId; }

    /**
     * 获取是否上架状态
     * @return true=上架，false=下架
     */
    public boolean isActive() { return isActive; }

    /**
     * 设置菜品价格
     * @param price 菜品价格
     */
    public void setPrice(double price) { this.price = price; }
    public void setActive(boolean active) { isActive = active; }

    /**
     * 返回菜品对象的字符串表示
     * @return 格式化后的菜品信息，用于日志或界面展示
     */
    @Override
    public String toString() {
        return String.format("%s %s (%.2f元)%s",
                itemCode, name, price, isActive ? "" : " [停售]");
    }
}