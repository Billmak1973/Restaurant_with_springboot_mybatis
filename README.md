# 🍽️ 餐廳管理系統 (Restaurant Management System) 專案分析報告

## 1. 專案概述
本系統是基於 **Spring Boot + Java Swing** 開發的桌面端餐廳管理解決方案，採用標準 **MVC 架構** 與 **MyBatis** 持久層，實現顧客排隊、餐桌管理、點餐結帳、預約服務及營業報表的全流程數字化運營。系統核心優勢在於：
- ✅ 支持聚餐桌/合併桌/拆分桌等複雜場景
- ✅ 事務一致性保障 + 內存緩存雙重機制
- ✅ 直觀可視化界面 + 專業報表分析
- ✅ 事件驅動架構，UI 與業務邏輯解耦

---

## 2. 技術棧一覽

| 類別 | 技術選型 | 關鍵價值 |
|------|----------|----------|
| 後端框架 | Spring Boot 2.x | 自動配置 + 聲明式事務 |
| 持久層 | MyBatis + XML | 動態 SQL + 精細控制 |
| 數據庫 | MySQL 8.0 (utf8mb4) | 事務支持 + 外鍵約束 |
| 連接池 | HikariCP | 高性能連接管理 |
| 前端界面 | Java Swing | 跨平台桌面體驗 |
| 數據可視化 | JFreeChart + Apache POI | 專業圖表 + Excel 導出 |
| 日誌監控 | SLF4J + Logback | 分級日誌 + 問題追溯 |

---

## 3. 系統架構圖

```
┌─────────────────┐
│   View 層        │
│ • RestaurantView│
│ • OrderSystemGUI│
└────────┬────────┘
         │ 事件轉發
         ▼
┌─────────────────┐
│ Controller 層    │
│ • 接收用戶操作   │
│ • 調用 Service   │
│ • 協調事務邊界   │
└────────┬────────┘
         │ 業務邏輯
         ▼
┌─────────────────┐
│ Service 層       │
│ • RestaurantSvc │
│ • OrderSvc      │
│ • MenuItemSvc   │
└────────┬────────┘
         │ DAO 調用
         ▼
┌─────────────────┐
│ Mapper 層        │
│ • MyBatis XML   │
│ • 動態 SQL      │
└────────┬────────┘
         │ JDBC
         ▼
┌─────────────────┐
│ MySQL 數據庫     │
│ • 12 張核心業務表 │
│ • 索引 + 視圖優化 │
└─────────────────┘
```

---

## 4. 核心功能模塊 ✨ *(保持原內容不變)*

### 4.1 餐桌管理系統
```java
// 支持複雜餐桌操作
├── 狀態管理
│   ├── VACANT (空閒) → OCCUPIED (占用) → SETTING_UP (準備中)
│   ├── SPLITTING (拆分中) / RESERVED (已預定)
│   └── 狀態轉換自動清理關聯數據
│
├── 餐桌操作
│   ├── 拆分餐桌：2/4 人桌 → 兩個子桌 (7 → 7a, 7b)
│   ├── 合併餐桌：兩張相鄰子桌 → 恢復主桌
│   ├── 換桌功能：顧客組轉移 + 訂單遷移
│   └── 聚餐桌：3-6 張 6 人桌組合，共享訂單狀態
│
├── 可視化顯示
│   ├── 動態圖標：椅子占用狀態實時渲染
│   ├── 顏色編碼：不同容量/狀態對應不同顏色
│   └── 懸停提示：顯示顧客組/訂單詳情
│
└── 內存緩存
    ├── ConcurrentHashMap<String, Tables> tableMap
    ├── 懶加載 + 自動刷新機制
    └── 事務提交後同步更新
```

### 4.2 顧客與隊列管理
```java
// 智能排隊與入座算法
├── 隊列分類
│   ├── 2_SEAT / 4_SEAT / 6_SEAT 三隊列獨立管理
│   ├── 位置自動重排 (ROW_NUMBER 窗口函數)
│   └── 隊列變更事件驅動 UI 刷新
│
├── 入座策略 (4 種自動分配)
│   ├── 策略 4a：單張空桌匹配 (容量完全匹配優先)
│   ├── 策略 4b：3-4 人 → 合併兩張 2 人桌
│   ├── 策略 4c：5-8 人 → 合併兩張 4 人桌 / 9-12 人 → 6 人桌
│   └── 策略 4d：1-2 人 → 自動分裂占用中餐桌
│
├── 預約優先規則
│   ├── 1.5 小時內預約 → 立即鎖定餐桌
│   ├── 預約需求統計 → 暫停同容量隊列分配
│   └── 餐桌清理時自動匹配預約提醒
│
└── 隊列維護
    ├── 編輯人數：自動重計算隊列類型
    ├── 刪除顧客：事務內同步刪除 queues + customer_groups
    └── 手動分配：支持強制指定餐桌入座
```

### 4.3 點餐與訂單系統
```java
// 完整訂單生命周期管理
├── 訂單類型
│   ├── DINE_IN (堂食)：關聯 table_id
│   ├── TAKEOUT (外賣)：PICKUP/DELIVERY 兩種配送方式
│   └── RESERVATION (預約)：關聯 reservation_id
│
├── 訂單狀態機
│   NO_ORDER → ORDERED → CHECKED_OUT
│   │
│   └── 訂菜品狀態：
│       UNSERVED → PARTIALLY_SERVED → SERVED
│       (預約訂單專用：PREPARING → PREPARED)
│
├── 聚餐桌訂單處理
│   ├── 一鍵點餐：A1[BATCH:13,14,15] 特殊 Key 格式
│   ├── 數量分配：總數量 ÷ 桌數 = 每桌份額 (必須整除)
│   ├── quantity_distribution：JSON 記錄各桌分配 {"13":4,"14":4,"15":3}
│   └── 上桌順序：禁止跳桌，必須相鄰桌號依次上桌
│
├── 菜品操作
│   ├── 標記上桌：單菜品/全部菜品/精確 order_item_id
│   ├── 撤銷菜品：支持選擇刪除「已上桌」或「未上桌」部分
│   └── 審計日誌：order_cancellations 表記錄所有撤銷操作
│
└── 臨時訂單緩存
    ├── Map<String, Map<String, Integer>> temporaryOrders
    ├── 餐桌號 → (菜品 ID → 數量) 二級緩存
    └── 確認下單時合併到正式訂單
```

### 4.4 結帳與收銀系統
```java
// 安全結帳流程
├── 結帳前置驗證
│   ├── 餐桌狀態必須為 OCCUPIED
│   ├── 訂單狀態必須為 ORDERED_FINISHED (全部上桌)
│   ├── 外賣訂單：配送訂單需 DELIVERED / 自取需製作完成
│   └── 合併桌：必須通過編號較小的主桌操作
│
├── 金額計算邏輯
│   ├── 三金額分離設計：
│   │   • items_total: 菜品總額 (不含配送費)
│   │   • delivery_fee: 配送費 (僅配送訂單)
│   │   • total_amount: 最終應付 = items_total + delivery_fee
│   │
│   ├── 定金抵扣：
│   │   • payable = max(0, items_total - prepaid_amount)
│   │   • revenue_record = max(items_total, prepaid_amount)
│   │
│   └── 找零計算：change = payment - payable
│
├── 營收記錄
│   ├── 自動累加 business_status.daily_revenue
│   ├── 跨日結帳：按 reorder_time 或 order_time 確定營收日期
│   ├── 外賣計數：incrementDailyTakeoutCount
│   └── 季度統計：item_quarterly_sales 原子操作記錄
│
└── 結帳後處理
    ├── 訂單狀態 → CHECKED_OUT
    ├── 刪除 order_items + table_orders (與外賣一致)
    ├── 餐桌狀態 → SETTING_UP (待清理)
    └── 觸發隊列分配檢查
```

### 4.5 預約管理系統
```java
// 預約全流程支持
├── 預約創建
│   ├── 自定義編號：R20260322-1234-1 (R+ 日期 + 手機尾號 + 序號)
│   ├── 兩種模式：
│   │   • MANUAL: 手動輸入餐桌號 (7,8,13)
│   │   • QUANTITY: 填寫桌子數量 (2 人桌×1, 6 人桌×3)
│   │
│   ├── 餐桌類型規則：
│   │   • MAIN: 1 張主桌
│   │   • MERGED: 2 張同容量相鄰桌
│   │   • GROUP: 3-6 張 6 人桌 (桌號必須連續)
│   │
│   └── 預點餐 + 定金：
│       • pre_order=true → 自動創建 NO_ORDER 狀態訂單
│       • prepaid_amount ≥ 0 → 結帳時自動抵扣
│
├── 預約分配 (ASSIGN 模式)
│   ├── 模糊查詢：預約號片段 / 手機號後 4 位
│   ├── 餐桌驗證：容量匹配 + 連續性/相鄰性檢查
│   ├── 聚餐桌菜品分配：自動生成 quantity_distribution
│   └── 訂單關聯：updateTableIdByReservationId
│
├── 預約修改 (EDIT_TIME 模式)
│   ├── 可修改項：時間 / 桌子配置 / 預點餐 / 定金 / 備註
│   ├── 聚餐桌數量增加：自動按比例調整菜品數量
│   ├── 定金規則：只能增加不能減少
│   └── 預點餐規則：只能「否→是」，不可反向
│
├── 預約取消 (CANCEL 模式)
│   ├── 狀態驗證：僅 PRE_CONFIRMED/CONFIRMED/DELAYED 可取消
│   ├── 定金沒收：
│   │   • 有預點餐 + 有定金 → 記錄 forfeited_deposits
│   │   • 累加 business_status.daily_cancelled_prepaid_amount
│   │
│   ├── 餐桌釋放：
│   │   • 聚餐桌：清空 group_with + 改回 MAIN 類型
│   │   • 合併桌：清空 merged_with + 改回 MAIN 類型
│   │
│   └── 訂單清理：先刪 order_items → 再刪 table_orders → 最後刪預約
│
└── 預約入座 (CHECK_IN)
    ├── 人數分配算法：
    │   • 合併桌：優先填滿編號小的桌子
    │   • 聚餐桌：平均分配 + 剩餘按編號從小到大分配
    │
    ├── 狀態轉換：
    │   • 預約：CONFIRMED → COMPLETED
    │   • 訂單：RESERVATION → DINE_IN
    │   • 餐桌：RESERVED → OCCUPIED
    │
    └── 關聯綁定：current_reservation_id 設置 + 顧客組創建
```

### 4.6 報表與數據分析
```java
// 專業報表系統
├── 營業總覽報表
│   ├── 查詢模式：單日 / 日期範圍
│   ├── 指標計算：
│   │   • 總營收 = SUM(daily_revenue)
│   │   • 總客流 = SUM(daily_total_customers)
│   │   • 客單價 = 總營收 ÷ 總客流
│   │   • 外賣訂單數 = SUM(daily_takeout_count)
│   │
│   ├── 可視化：
│   │   • 雙柱狀圖：營收趨勢 + 客流趨勢
│   │   • 自動 Y 軸範圍：max_value × 1.2
│   │   • 中文字體自動適配
│   │
│   └── 導出支持：Excel (.xlsx) + CSV (UTF-8)
│
├── 菜品銷售分析
│   ├── 維度篩選：年份 + 季度 + 菜品分類 (A/B/C/D/全部)
│   ├── 數據聚合：
│   │   • total_quantity: SUM(quantity_sold)
│   │   • total_revenue: SUM(quantity × sale_price)
│   │   • avg_price: AVG(sale_price)
│   │
│   ├── 圖表類型：
│   │   • 柱狀圖：數值精確比較
│   │   • 餅圖：占比直觀展示 (自動合併<2% 項目)
│   │
│   └── 顯示控制：全部 / 前 10/25/50 項可選
│
├── 取消預約統計
│   ├── 篩選條件：取消時間範圍
│   ├── 統計指標：
│   │   • 總取消次數
│   │   • 總沒收金額
│   │   • 明細列表：預約號 + 客戶信息 + 原因
│   │
│   └── 數據來源：forfeited_deposits 表
│
└── 導出功能
    ├── Excel 導出：
    │   • Apache POI XSSFWorkbook
    │   • 自動格式：金額列 (貨幣格式) / 數量列 (整數)
    │   • 樣式美化：表頭底色 + 邊框 + 列寬自適應
    │
    └── CSV 備份：
        • RFC4180 標準：特殊字符轉義
        • UTF-8 編碼：支持中文
```

### 4.7 系統管理功能
```java
// 運維與配置管理
├── 營業狀態控制
│   ├── 開始營業：
│   │   • business_status.is_open = true
│   │   • 自動創建當日記錄 (UPSERT)
│   │   • 恢復隊列分配功能
│   │
│   └── 結束營業：
│       • 檢查未結帳訂單 (警告但不阻止)
│       • is_open = false + 記錄 next_call_number
│       • 禁止新顧客入座 (隊列仍可管理)
│
├── 菜單管理
│   ├── 菜品 CRUD：
│   │   • 添加：自動生成 item_code (A1, B2...)
│   │   • 修改：價格調整 / 售罄狀態切換
│   │   • 刪除：檢查 order_items 外鍵約束
│   │
│   ├── 分類管理：
│   │   • A: 特色食物 / B: 飲料 / C: 小炒 / D: 套餐
│   │   • 前綴自動分配 + 最大編號查詢
│   │
│   └── 實時緩存：
│       • menuItemStatusCache: 菜品可售狀態
│       • 菜單列表按分類懶加載
│
├── 數據庫初始化
│   ├── DatabaseInitializer (CommandLineRunner)
│   ├── 自動創建數據庫 + 執行 schema.sql
│   ├── 插入默認餐桌 (1-18 號) + 菜單分類
│   └── 配置注入：@Value 支持環境變量覆蓋
│
└── 日誌與監控
    ├── 操作日誌：appendToLog 記錄關鍵操作
    ├── 異常日誌：try-catch + printStackTrace
    └── 調試日誌：關鍵業務節點輸出 [DEBUG] 標記
```
<img width="1918" height="1005" alt="image" src="https://github.com/user-attachments/assets/fcf543da-8d78-41cc-b8b2-22f24a146149" />

---

## 5. 數據庫設計精要

### 核心表關係圖
```
business_status ──┬── customer_groups ──┬── queues
                  │                     │
restaurant_tables ─┼── table_orders ────┼── order_items ──┬── menu_items
                  │                     │                 │
table_reservations┴── forfeited_deposits┴── order_cancellations
```

### 設計亮點
- 🔹 **外鍵約束**：確保數據一致性，避免孤兒記錄
- 🔹 **索引優化**：關鍵查詢字段建立複合索引，提升檢索效率
- 🔹 **視圖抽象**：`quarterly_item_summary` 簡化報表查詢邏輯
- 🔹 **原子操作**：季度銷售記錄使用臨時表 + UPSERT，保證併發安全

---

## 6. 專案結構一覽

```
restaurant-system/
├── src/main/java/com/restaurant/
│   ├── config/          # 配置類 (數據庫初始化)
│   ├── controller/      # 控制器 (事件轉發)
│   ├── entity/          # 實體類 + 枚舉
│   ├── event/listener/  # 事件機制 + 監聽器
│   ├── mapper/          # MyBatis 接口 + XML
│   ├── service/         # 業務邏輯層
│   ├── view/            # Swing 界面組件
│   └── RestaurantApplication.java
│
├── src/main/resources/
│   ├── application.yml  # 應用配置
│   ├── db/schema.sql    # 數據庫腳本
│   └── mapper/*.xml     # MyBatis 映射
│
└── pom.xml              # Maven 依賴管理
```

---

## 7. 快速開始 🚀 *(完結段)*

### 一鍵啟動
```bash
# 1. 確保 MySQL 8.0+ 運行中
# 2. 配置 application.yml 數據庫連接
# 3. 執行啟動命令
mvn spring-boot:run

# ✅ 系統將自動：
#    • 創建數據庫 + 初始化表結構
#    • 插入默認餐桌與菜單分類
#    • 啟動 GUI 管理界面
```

### 配置說明
```yaml
# application.yml 關鍵配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/restaurant_sys_db?useSSL=false&serverTimezone=UTC
    username: ${DB_USERNAME:root}    # 支持環境變量覆蓋
    password: ${DB_PASSWORD:}
    
db:
  init:
    host: ${DB_HOST:localhost}       # 靈活部署支持
    port: ${DB_PORT:3306}
    name: ${DB_NAME:restaurant_sys_db}
```

### 常見問題
| 問題 | 解決方案 |
|------|----------|
| 首次啟動報「表不存在」 | 檢查 `schema.sql` 路徑，確認 `allowMultiQueries=true` |
| 中文顯示亂碼 | 確認 MySQL 使用 `utf8mb4`，JDBC 連接添加 `characterEncoding=utf-8` |
| Swing 界面卡頓 | 檢查是否在主線程執行耗時操作，確保使用 `SwingUtilities.invokeLater` |

---

> 🎯 **專案總結**  
> 本系統以「穩定、高效、易用」為設計原則，通過嚴謹的 MVC 分層、事務管理與事件驅動架構，實現了餐廳運營全流程的數字化閉環。無論是日常點餐結帳，還是複雜的聚餐桌調度、預約管理，系統均能提供流暢體驗與可靠保障。  
>   
> 📦 **交付物**：可執行 Jar 包 + 數據庫腳本 + 配置模板，支持一鍵部署。  
> 🔧 **擴展建議**：後續可接入微信支付/會員系統/移動端 API，進一步提升商業價值。  
>   
> ✨ **願景**：讓每一家餐廳，都能輕鬆擁抱數字化運營時代。

---
*📄 文檔版本：v1.0 | 最後更新：2026-05*  
*🔐 版權所有 © 2026 BILL MAK*
