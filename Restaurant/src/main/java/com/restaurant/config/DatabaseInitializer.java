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
@Order(0)
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

    @Value("${db.init.password:}")
    private String dbPassword;

    // 动态构建数据库连接 URL
    private String getServerUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort +
                "/?useSSL=false&serverTimezone=UTC&characterEncoding=utf-8&allowPublicKeyRetrieval=true";
    }

    private static final String[] REQUIRED_TABLES = {
            "business_status", "customer_groups", "menu_categories",
            "menu_items", "restaurant_tables", "table_orders",
            "order_items", "item_quarterly_sales", "table_reservations",
            "order_cancellations", "queues","forfeited_deposits"
    };

    public DatabaseInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) throws Exception {
        if (initialized) {
            return;
        }

        try {
            ensureDatabaseExists();

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
     * 确保数据库存在（使用注入的配置）
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
     * 检查所有必需表是否存在
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
     * 执行 schema.sql 创建表结构
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
     */
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
     * 插入当天营业状态记录（仅在首次建表时调用）
     */
    private void insertTodayBusinessStatus() {
        String sql = "INSERT IGNORE INTO business_status " +
                "(business_date, is_open, next_call_number, daily_total_customers, daily_revenue, daily_takeout_count) " +
                "VALUES (CURDATE(), true, 1, 0, 0.00, 0)";
        try {
            int result = jdbcTemplate.update(sql);
            if (result > 0) {
                System.out.println("已创建当天营业状态记录：" + java.time.LocalDate.now());
            }
        } catch (Exception e) {
            System.err.println("插入当天营业状态失败：" + e.getMessage());
        }
    }
}