package com.restaurant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

@Component

//2.3  @Order 註解控制執行優先級 (Execution Priority)
//技術說明：使用 @Order(0) 註解確保數據庫初始化在所有其他 CommandLineRunner 之前執行，防止業務邏輯因數據庫未準備好而失
@Order(0)//確保最高優先級執行
//@Order(0) 保證了在業務 Bean 初始化前先完成數據庫準備。executeSchemaScript() 會讀取 classpath:db/schema.sql 並執行建表語句，實現“零配置”部署。
//1.3. 數據庫結構自動初始化 (CommandLineRunner)
//技術說明：實現 CommandLineRunner 接口，在 Spring 容器啟動後自動執行數據庫結構初始化（建庫建表），無需手動導入 SQL。

//2.2. Spring CommandLineRunner 生命周期鉤子 (Lifecycle Hook)
//技術說明：實現 CommandLineRunner 接口，利用 Spring Boot 應用啟動後的回調機制，在容器完全初始化後自動執行數據庫初始化邏輯。
//CommandLineRunner 是 Spring Boot 標準的啟動後任務執行接口。將其用於數據庫初始化，確保了業務代碼運行前，基礎數據結構已經就緒，避免了 Table doesn't exist 錯誤。
//Spring 容器中可能存在多個 CommandLineRunner（如定時任務初始化、緩預熱等）。@Order(0) 保證了數據庫結構創建的原子性和優先級，是系統穩健啟動的關鍵。
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private static boolean initialized = false;

    // ===== 从 application.yml 注入配置，不再硬编码 =====
    @Value("${db.init.host:localhost}")
    private String dbHost;

    @Value("${db.init.port:3306}")
    private String dbPort;

    @Value("${db.init.name:restaurant_sys_db}")
    private String dbName;

    @Value("${db.init.username:root}")
    private String dbUsername;

    //@Value(...)：Spring 注解，声明该字段需要从配置中注入值
    //${...}:占位符语法，表示从配置文件（如 application.properties）中查找键值
    //db.init.password:配置项的键名（key）
    //:默认值分隔符，冒号后面是"找不到该键时使用的备用值"
    //""（空）:默认值为空字符串，即配置不存在时 dbPassword 被赋值为 ""
    @Value("${db.init.password:}")
    private String dbPassword;

    /**
     * 获取数据库服务器连接URL（不含数据库名）
     *
     * 功能说明：
     * 拼接主机地址、端口及连接参数，构建用于管理级操作（如创建数据库）的JDBC连接字符串。
     *
     * 连接参数说明：
     * - useSSL=false: 禁用SSL加密，适用于本地或内网开发环境
     * - serverTimezone=UTC: 指定服务器时区为UTC，避免时间转换歧义
     * - characterEncoding=utf-8: 指定字符编码为UTF-8，确保中文数据正常读写
     * - allowPublicKeyRetrieval=true: 允许客户端获取服务器公钥，兼容MySQL 8.0+认证机制
     *
     * @return 数据库服务器级别的JDBC连接URL
     */
    private String getServerUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort +
                "/?useSSL=false&serverTimezone=UTC&characterEncoding=utf-8&allowPublicKeyRetrieval=true";
    }

    /**
     * 系统必需的数据表名称列表
     * 用于初始化时校验表结构完整性
     */
    private static final String[] REQUIRED_TABLES = {
            "business_status", "customer_groups", "menu_categories",
            "menu_items", "restaurant_tables", "table_orders",
            "order_items", "item_quarterly_sales", "table_reservations",
            "order_cancellations", "queues","forfeited_deposits"
    };

    /**
     * 构造函数：初始化 JdbcTemplate
     * @param dataSource 数据源实例，用于执行数据库操作
     */
    public DatabaseInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 应用启动时执行数据库初始化
     *
     * 功能说明：
     * 1. 检查初始化标志，避免重复执行
     * 2. 确保目标数据库存在，不存在则创建
     * 3. 校验必需表结构，若缺失则执行 schema.sql 建表
     * 4. 建表成功后插入默认餐桌、菜单分类及今日营业状态数据
     * 5. 表已存在时静默跳过，不输出日志
     *
     * @param args 命令行参数（未使用）
     * @throws Exception 初始化过程中发生异常时抛出
     */
    @Override
    public void run(String... args) throws Exception {
        if (initialized) {
            return;
        }

        try {
            ensureDatabaseExists();// 確保庫存在

            if (!hasRequiredTables()) {
                // 表不存在：创建表 + 插入默认数据
                System.out.println("开始数据库初始化...");
                executeSchemaScript();

                if (hasRequiredTables()) {
                    insertDefaultTables();
                    insertDefaultCategories();
                    insertTodayBusinessStatus();
                    System.out.println("数据库初始化完成");
                } else {
                    System.err.println("表结构创建失败，请检查 schema.sql 和执行日志");
                }
            }
            // 表已存在：直接跳过，不输出任何日志
        } catch (Exception e) {
            System.err.println("数据库初始化异常：" + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            initialized = true;
        }
    }

    /**
     * 确保目标数据库存在
     *
     * 功能说明：
     * 1. 连接至数据库服务器（非目标库）
     * 2. 查询 INFORMATION_SCHEMA 确认目标库是否存在
     * 3. 若不存在则执行 CREATE DATABASE 语句创建，指定 utf8mb4 字符集
     *
     * 安全说明：
     * - 使用 PreparedStatement 防止数据库名注入
     * - 使用 IF NOT EXISTS 避免并发创建冲突
     *
     * @throws Exception 连接数据库或执行建库语句失败时抛出
     */
    private void ensureDatabaseExists() throws Exception {
        try (Connection conn = DriverManager.getConnection(getServerUrl(), dbUsername, dbPassword);
             java.sql.Statement stmt = conn.createStatement()) {

            // 使用 PreparedStatement 防止 SQL 注入
            String checkSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, dbName);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    String createDbSql = "CREATE DATABASE IF NOT EXISTS `" + dbName +
                            "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
                    stmt.execute(createDbSql);
                    System.out.println("数据库 [" + dbName + "] 创建成功");
                }
            }
        }
    }

    /**
     * 检查所有必需数据表是否已存在
     *
     * 功能说明：
     * 1. 通过 DatabaseMetaData 获取当前库中所有用户表
     * 2. 将表名转为小写存入集合，避免大小写敏感问题
     * 3. 遍历 REQUIRED_TABLES 数组，任一表缺失则返回 false
     *
     * @return true=所有必需表均存在；false=存在缺失表
     * @throws Exception 获取元数据或遍历结果集失败时抛出
     */
    private boolean hasRequiredTables() throws Exception {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            ResultSet tables = metaData.getTables(
                    conn.getCatalog(),
                    null,
                    "%",
                    new String[]{"TABLE"}
            );

            Set<String> existingTables = new HashSet<>();
            while (tables.next()) {
                existingTables.add(tables.getString("TABLE_NAME").toLowerCase());
            }

            for (String required : REQUIRED_TABLES) {
                if (!existingTables.contains(required.toLowerCase())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 执行 schema.sql 脚本创建数据库表结构
     *
     * 功能说明：
     * 1. 从 classpath:db/schema.sql 加载 SQL 脚本文件
     * 2. 逐行读取并拼接完整 SQL 语句，跳过空行与注释
     * 3. 按分号分割语句并逐条执行，捕获并过滤"表已存在"类非致命错误
     * 4. 记录执行的语句总数与成功数，创建表时输出表名日志
     *
     * 异常处理：
     * - 文件不存在时抛出 RuntimeException
     * - SQL 执行异常时，若错误信息不含"already exists"或"duplicate"则向上抛出
     *
     * @throws Exception 读取文件或执行 SQL 失败时抛出
     */
    private void executeSchemaScript() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var resource = resolver.getResource("classpath:db/schema.sql");

        if (!resource.exists()) {
            System.err.println("未找到 schema.sql，请确认文件位于 src/main/resources/db/");
            throw new RuntimeException("schema.sql not found");
        }

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            int statementCount = 0;
            int successCount = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("//")) {
                    continue;
                }

                sqlBuilder.append(trimmed).append(" ");

                if (trimmed.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    sqlBuilder.setLength(0);

                    if (sql.isEmpty() || sql.equals(";")) {
                        continue;
                    }

                    statementCount++;
                    String finalSql = sql.replace(";", "").trim();

                    try {
                        jdbcTemplate.execute(finalSql);
                        successCount++;

                        if (finalSql.toUpperCase().startsWith("CREATE TABLE")) {
                            String tableName = extractTableName(finalSql);
                            if (tableName != null) {
                                System.out.println("创建表：" + tableName);
                            }
                        }
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        if (errorMsg != null &&
                                !errorMsg.toLowerCase().contains("already exists") &&
                                !errorMsg.toLowerCase().contains("duplicate")) {

                            System.err.println("SQL #" + statementCount + " 执行失败:");
                            System.err.println("SQL: " + finalSql.substring(0, Math.min(100, finalSql.length())) + "...");
                            System.err.println("错误：" + errorMsg);
                            throw e;
                        }
                    }
                }
            }

            System.out.println("schema.sql 执行完成：共 " + statementCount + " 条语句，成功 " + successCount + " 条");

        } catch (Exception e) {
            System.err.println("读取或执行 schema.sql 时出错：" + e.getMessage());
            throw e;
        }
    }

    /**
     * 从 CREATE TABLE 语句中提取表名
     *
     * 功能说明：
     * 解析 SQL 语句，跳过可选的 IF NOT EXISTS 子句，提取第一个标识符作为表名，
     * 并移除可能存在的反引号包裹。
     *
     * @param sql CREATE TABLE 语句字符串
     * @return 提取的表名；解析失败或非建表语句时返回 null
     */
    private String extractTableName(String sql) {
        try {
            String upper = sql.toUpperCase();
            if (upper.startsWith("CREATE TABLE")) {
                String after = sql.substring("CREATE TABLE".length()).trim();
                if (after.toUpperCase().startsWith("IF NOT EXISTS")) {
                    after = after.substring("IF NOT EXISTS".length()).trim();
                }
                String name = after.split("[\\s(]")[0].replace("`", "");
                return name;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 插入默认餐桌数据（仅在首次建表时调用）
     *
     * 功能说明：
     * 向 restaurant_tables 表插入 18 张默认餐桌记录：
     * - 编号 1-6：2 人桌，容量 2
     * - 编号 7-12：4 人桌，容量 4
     * - 编号 13-18：6 人桌，容量 6
     * 所有餐桌初始状态为 VACANT，类型为 MAIN
     *
     * 容错处理：
     * - 捕获插入异常并记录错误日志，避免中断初始化流程
     */
    //2.6. 默認數據種子 (Default Data Seeding)
    //技術說明：在表結構創建後，自動插入基礎數據（如 15 張默認餐桌、4 個菜單分類），確保系統首次啟動即可使用，無需手動錄入基礎資料。
    private void insertDefaultTables() {
        String sql = "INSERT INTO restaurant_tables " +
                "(display_id, base_id, capacity, physical_capacity, status, table_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try {
            int inserted = 0;
            for (int i = 1; i <= 6; i++) {
                jdbcTemplate.update(sql, String.valueOf(i), i, 2, 2, "VACANT", "MAIN");
                inserted++;
            }
            for (int i = 7; i <= 12; i++) {
                jdbcTemplate.update(sql, String.valueOf(i), i, 4, 4, "VACANT", "MAIN");
                inserted++;
            }
            for (int i = 13; i <= 18; i++) {
                jdbcTemplate.update(sql, String.valueOf(i), i, 6, 6, "VACANT", "MAIN");
                inserted++;
            }
            System.out.println("成功插入 " + inserted + " 张默认餐桌数据");
        } catch (Exception e) {
            System.err.println("插入默认餐桌数据异常：" + e.getMessage());
        }
    }

    /**
     * 插入默认菜单分类数据（仅在首次建表时调用）
     *
     * 功能说明：
     * 向 menu_categories 表插入 4 条默认分类记录：
     * - 特色食物（前缀 A）
     * - 饮料（前缀 B）
     * - 小炒（前缀 C）
     * - 套餐（前缀 D）
     *
     * 容错处理：
     * - 捕获插入异常并记录错误日志，避免中断初始化流程
     */
    private void insertDefaultCategories() {
        String sql = "INSERT INTO menu_categories (name, prefix) VALUES (?, ?)";

        String[][] categories = {
                {"特色食物", "A"},
                {"饮料", "B"},
                {"小炒", "C"},
                {"套餐", "D"}
        };

        try {
            int inserted = 0;
            for (String[] cat : categories) {
                jdbcTemplate.update(sql, cat[0], cat[1]);
                inserted++;
            }
            System.out.println("成功插入 " + inserted + " 条默认菜单分类数据");
        } catch (Exception e) {
            System.err.println("插入默认菜单分类数据异常：" + e.getMessage());
        }
    }

    /**
     * 插入或跳过今日营业状态记录
     *
     * 功能说明：
     * 1. 查询 business_status 表是否存在当前日期的记录
     * 2. 若不存在则插入一条新记录，初始化营业状态为开启、叫号序号为 1、各项统计为 0
     * 3. 若已存在则跳过，避免重复插入消耗自增主键
     *
     * 数据字段：
     * - business_date: 当前日期（CURDATE()）
     * - is_open: true（默认营业）
     * - next_call_number: 1（起始叫号）
     * - daily_total_customers / daily_revenue / daily_takeout_count: 0（初始统计）
     */
    private void insertTodayBusinessStatus() {
        // 先检查是否存在
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_status WHERE business_date = CURDATE()",
                Integer.class
        );

        if (count != null && count == 0) {
            String sql = "INSERT INTO business_status " +
                    "(business_date, is_open, next_call_number, daily_total_customers, daily_revenue, daily_takeout_count) " +
                    "VALUES (CURDATE(), true, 1, 0, 0.00, 0)";
            int result = jdbcTemplate.update(sql);
            if (result > 0) {
                System.out.println("已创建当天营业状态记录：" + java.time.LocalDate.now());
            }
        }
        // 如果已存在，什么都不做，不消耗 AUTO_INCREMENT
    }
}