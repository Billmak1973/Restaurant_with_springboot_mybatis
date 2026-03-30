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

    public MenuItem(int itemId, String itemCode, String name, double price,
                    int categoryId, boolean isActive) {
        this.itemId = itemId;
        this.itemCode = itemCode;
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
        this.isActive = isActive;
    }

    // 简化构造函数（用于新增）
    public MenuItem(String itemCode, String name, double price, int categoryId) {
        this(0, itemCode, name, price, categoryId, true);
    }

    // Getters
    public int getItemId() { return itemId; }
    public String getItemCode() { return itemCode; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getCategoryId() { return categoryId; }
    public boolean isActive() { return isActive; }

    // Setters
    public void setPrice(double price) { this.price = price; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return String.format("%s %s (%.2f元)%s",
                itemCode, name, price, isActive ? "" : " [停售]");
    }
}