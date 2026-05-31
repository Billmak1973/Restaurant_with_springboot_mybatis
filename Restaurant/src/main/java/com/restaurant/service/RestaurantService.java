package com.restaurant.service;

import com.restaurant.entity.*;
import com.restaurant.event.QueueChangedEvent;
import com.restaurant.mapper.*;
import com.restaurant.util.OperationResult;
import com.restaurant.view.ReservationMatchCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import javax.swing.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RestaurantService {

    /**
     * 预约匹配信息封装类（私有静态内部类）
     *
     * 功能说明：
     * 用于在服务层内部传递预约匹配检查的中间结果，
     * 包含预约基本信息与所需餐桌规格，避免方法参数过多。
     *
     * 字段说明：
     * - reservationId: 预约记录唯一标识
     * - requiredCapacity: 所需餐桌容量（2/4/6人桌）
     * - requiredCount: 所需餐桌数量
     * - reservationTime: 预约到店时间
     * - customerName: 预约客人姓名
     * - customerPhone: 预约客人联系电话
     *
     * 使用场景：
     * - 餐桌状态变更为空闲时，检查是否存在匹配的1.5小时内预约
     * - 匹配成功时构建提示信息供界面展示
     *
     * 访问权限：
     * - private修饰，仅当前类内部使用，避免外部依赖
     */
    private static class ReservationMatchInfo {
        String reservationId;
        int requiredCapacity;  // 需要的餐桌容量（2/4/6）
        int requiredCount;     // 需要的餐桌数量
        LocalDateTime reservationTime;
        String customerName;
        String customerPhone;
    }

    /**
     * 餐桌合并机会封装类（公共静态内部类）
     *
     * 功能说明：
     * 用于传递相邻空闲餐桌的合并可行性信息，
     * 支持服务层与视图层之间安全交换合并建议数据。
     *
     * 核心字段：
     * - available: 是否存在可合并的相邻餐桌对
     * - mainTableDisplayId: 合并后的主桌显示编号
     * - subTableA: 第一张子桌显示编号
     * - subTableB: 第二张子桌显示编号
     *
     * 设计模式：
     * - 静态工厂方法：none()表示无合并机会，of()表示有机会
     * - 避免外部直接new，确保对象状态初始化完整
     *
     * 框架兼容：
     * - 提供标准getter/setter，支持MyBatis结果映射
     * - 重写toString方法，便于调试日志输出
     *
     * 应用场景：
     * - 顾客点餐时检查是否可通过合并餐桌满足人数需求
     * - 界面展示合并建议供操作员确认执行
     */
    public static class MergeOpportunity {
        private boolean available = false;//标记是否存在可合并的相邻餐桌对
        private String mainTableDisplayId;//合并后的主桌显示编号
        private String subTableA;
        private String subTableB;

        /**
         * 默认构造函数
         *
         * 功能说明：
         * 供框架反射实例化或静态工厂方法内部调用，
         * 初始化时available默认为false，需显式设置有效值。
         */
        public MergeOpportunity() {
        }

        /**
         * 创建无合并机会的实例
         *
         * 功能说明：
         * 返回available=false的MergeOpportunity对象，
         * 表示当前无符合条件的相邻餐桌可合并。
         *
         * @return 不可用的合并机会实例
         *
         * 使用场景：
         * - 查询无相邻空闲餐桌时返回
         * - 调用方通过isAvailable()快速判断是否可执行合并
         */
        public static MergeOpportunity none() {
            MergeOpportunity op = new MergeOpportunity();
            op.available = false;
            return op;
        }

        /**
         * 创建有合并机会的实例
         *
         * 功能说明：
         * 返回available=true且包含完整餐桌信息的MergeOpportunity对象，
         * 表示存在可合并的相邻餐桌对。
         *
         * @param mainTableDisplayId 合并后的主桌显示编号
         * @param subTableA 第一张子桌显示编号
         * @param subTableB 第二张子桌显示编号
         * @return 可用的合并机会实例
         *
         * 使用场景：
         * - 找到相邻空闲餐桌对时构建返回结果
         * - 视图层根据返回信息展示合并建议按钮
         */
        public static MergeOpportunity of(String mainTableDisplayId, String subTableA, String subTableB) {
            MergeOpportunity op = new MergeOpportunity();
            op.available = true;
            op.mainTableDisplayId = mainTableDisplayId;
            op.subTableA = subTableA;
            op.subTableB = subTableB;
            return op;
        }

        /**
         * 判断是否存在可合并的相邻餐桌对
         *
         * @return true=存在合并机会；false=无合并机会
         */
        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        /**
         * 获取合并后的主桌显示编号
         *
         * @return 主桌显示编号；无合并机会时返回null
         */
        public String getMainTableDisplayId() {
            return mainTableDisplayId;
        }

        /**
         * 设置合并后的主桌显示编号
         *
         * @param mainTableDisplayId 主桌显示编号
         */
        public void setMainTableDisplayId(String mainTableDisplayId) {
            this.mainTableDisplayId = mainTableDisplayId;
        }


        /**
         * 获取第一张子桌的显示编号
         *
         * @return 子桌A显示编号；无合并机会时返回null
         */
        public String getSubTableA() {
            return subTableA;
        }

        /**
         * 设置第一张子桌的显示编号
         *
         * @param subTableA 子桌A显示编号
         */
        public void setSubTableA(String subTableA) {
            this.subTableA = subTableA;
        }


        /**
         * 获取第二张子桌的显示编号
         *
         * @return 子桌B显示编号；无合并机会时返回null
         */
        public String getSubTableB() {
            return subTableB;
        }

        /**
         * 设置第二张子桌的显示编号
         *
         * @param subTableB 子桌B显示编号
         */
        public void setSubTableB(String subTableB) {
            this.subTableB = subTableB;
        }

        @Override
        public String toString() {
            return "MergeOpportunity{available=" + available +
                    ", main='" + mainTableDisplayId +
                    "', a='" + subTableA + "', b='" + subTableB + "'}";
        }
    }

    private final TablesMapper tablesMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final BusinessStatusMapper businessStatusMapper;
    private final OrderMapper orderMapper;
    private final QueueMapper queueMapper;
    private final OrderItemMapper orderItemMapper;
    private final TableReservationMapper reservationMapper;
    private final DataSource dataSource;
    private ReservationMatchCallback matchCallback;

    // Spring 事件发布器
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, Tables> tableMap = new ConcurrentHashMap<>();
    private final Map<Integer, CustomerGroup> customerGroupMap = new ConcurrentHashMap<>();
    public boolean isOpenForBusiness = true;
    private boolean cacheInitialized = false;
    private static final int COLS_PER_ROW = 3;  // 餐桌面板每行3列
    private Queue<CustomerGroup> queue2Seat = new LinkedList<>(); // 2人桌队列
    private Queue<CustomerGroup> queue4Seat = new LinkedList<>(); // 4人桌队列
    private Queue<CustomerGroup> queue6Seat = new LinkedList<>(); // 6人桌队列
    private final Object queueLock = new Object(); // 队列同步锁
    //  数量模式预约缓存：预约号 → 匹配信息
    private final Map<String, ReservationMatchInfo> quantityReservationCache = new ConcurrentHashMap<>();

    //4.2. 構造函數依賴注入 (Constructor DI)
    //技術說明：採用 Spring 官方推薦的構造函數注入，明確聲明 Service 依賴的 Mapper、事件發布器等組件。
    //避免了字段注入 @Autowired 帶來的隱式依賴與測試困難。所有依賴在實例化時一次性注入，保證了對象的不可變性與線程安全性，並完美契合 Spring 的 IoC 容器生命週期。
    /**
     * RestaurantService 构造函数：通过依赖注入初始化所有必需组件
     *
     * 依赖说明：
     * - eventPublisher: Spring 事件发布器，用于发布队列变更、餐桌状态更新等事件
     * - tablesMapper: 餐桌数据访问层，负责餐厅餐桌的增删改查操作
     * - customerGroupMapper: 顾客组数据访问层，管理顾客组的创建与状态更新
     * - businessStatusMapper: 营业状态数据访问层，处理叫号、营收等统计信息
     * - orderMapper: 订单数据访问层，负责订单的创建、查询与状态流转
     * - queueMapper: 排队队列数据访问层，管理顾客排队记录的持久化
     * - orderItemMapper: 订单明细数据访问层，处理菜品与订单的关联操作
     * - reservationMapper: 预约记录数据访问层，支持预约的创建、修改与查询
     * - dataSource: 数据库连接池，用于手动事务控制场景（如自动分配餐桌）
     *
     * 设计原则：
     * - 构造函数注入：确保服务创建时所有依赖已就绪，避免空指针异常
     * - 单一职责：每个 Mapper 仅负责对应实体表的数据库操作，职责清晰
     * - 可测试性：依赖通过参数传入，便于单元测试时注入模拟对象
     */
    public RestaurantService(
            ApplicationEventPublisher eventPublisher,
            TablesMapper tablesMapper,
            CustomerGroupMapper customerGroupMapper,
            BusinessStatusMapper businessStatusMapper,
            OrderMapper orderMapper,
            QueueMapper queueMapper,
            OrderItemMapper orderItemMapper,
            TableReservationMapper reservationMapper,
            DataSource dataSource) {
        this.eventPublisher = eventPublisher;
        this.tablesMapper = tablesMapper;
        this.customerGroupMapper = customerGroupMapper;
        this.businessStatusMapper = businessStatusMapper;
        this.orderMapper = orderMapper;
        this.queueMapper = queueMapper;
        this.orderItemMapper = orderItemMapper;
        this.reservationMapper = reservationMapper;
        this.dataSource = dataSource;
    }
    /**
     * 初始化内存缓存
     *
     * 功能说明：
     * 1. 短暂延迟后刷新餐桌缓存，确保数据库初始化完成
     * 2. 从数据库加载排队队列数据到内存
     * 3. 标记缓存初始化完成标志
     * 4. 初始化当日营业状态记录
     *
     * 执行时机：
     * - Spring 容器启动完成后自动调用（@PostConstruct）
     * - 确保服务就绪时内存数据与数据库一致
     *
     * 异常处理：
     * - 首次启动时数据库未就绪的预期错误仅记录提示
     * - 其他异常记录简洁错误信息及关键堆栈，避免日志刷屏
     */
    @PostConstruct// 依赖注入完成后自动执行的初始化方法，用于执行Bean的初始配置逻辑
    public void initCache() {
        try {
            // 稍微延迟，给数据库初始化一点时间
            Thread.sleep(500);

            refreshTableCache();
            loadQueuesFromDatabase();
            cacheInitialized = true;
            System.out.println(" 餐桌缓存初始化完成");
            initializeDailyStatus();
        } catch (Exception e) {
            // 【核心修复】提取异常链中的所有消息，判断是否为"首次启动预期错误"
            String fullMessage = extractFullErrorMessage(e);

            // 预期错误关键词（首次启动时数据库/表还不存在）
            boolean isExpectedFirstRunError = fullMessage != null && (
                    fullMessage.contains("doesn't exist") ||      // 表不存在
                            fullMessage.contains("Unknown database") ||   // 数据库不存在
                            fullMessage.contains("Table") && fullMessage.contains("not found") ||
                            fullMessage.contains("CannotGetJdbcConnection") // 连接失败（数据库未创建时）
            );

            if (isExpectedFirstRunError) {
                //  预期错误：只打印一行提示，不打印堆栈
                System.out.println(" 餐桌缓存初始化等待中（数据库尚未就绪，等待初始化完成）...");
            } else {
                //  真正的异常：打印简洁错误信息 + 堆栈
                System.err.println("餐桌缓存初始化失败: " +
                        (fullMessage != null ? fullMessage : e.getClass().getSimpleName()));
                // 只打印关键堆栈（前5行），避免刷屏
                e.printStackTrace(new java.io.PrintWriter(System.err) {
                    private int count = 0;

                    @Override
                    public void println(String x) {
                        if (count++ < 5) super.println(x);
                    }
                });
            }
        }
    }

    /**
     * 提取异常链中的完整错误消息
     *
     * 功能说明：
     * 遍历异常及其所有 cause，拼接每一层的消息内容，
     * 用于判断是否为首次启动时的预期数据库错误。
     *
     * @param e 待解析的异常对象
     * @return 拼接后的完整错误消息字符串；无消息时返回空字符串
     *
     * 应用场景：
     * - 缓存初始化失败时区分预期错误与真实异常
     * - 避免仅打印顶层消息导致关键信息丢失
     */
    private String extractFullErrorMessage(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return sb.toString().trim();
    }

    /**
     * 从数据库加载排队队列到内存
     *
     * 功能说明：
     * 1. 分别加载 2 人桌、4 人桌、6 人桌的排队记录
     * 2. 为每种队列创建新的 LinkedList 副本存储到内存
     * 3. 同步更新 customerGroupMap 缓存，确保顾客组对象可快速访问
     *
     * 执行时机：
     * - 服务启动时初始化缓存
     * - 确保内存队列与数据库记录实时一致
     *
     * 容错处理：
     * - 加载失败时记录日志但不中断启动流程
     * - 空队列时静默处理，避免冗余日志
     */
    private void loadQueuesFromDatabase() {
        try {
            // 加载2人桌队列
            List<CustomerGroup> q2 = queueMapper.loadQueueByType("2_SEAT");
            queue2Seat = new LinkedList<>(q2);
            // 【修复】同步更新 customerGroupMap 這是儅編輯排隊隊伍的人數做的實時刷新
            for (CustomerGroup group : q2) {
                customerGroupMap.put(group.getGroup_id(), group);
            }

            // 加载4人桌队列
            List<CustomerGroup> q4 = queueMapper.loadQueueByType("4_SEAT");
            queue4Seat = new LinkedList<>(q4);
            // 【修复】同步更新 customerGroupMap
            for (CustomerGroup group : q4) {
                customerGroupMap.put(group.getGroup_id(), group);
            }

            // 加载6人桌队列
            List<CustomerGroup> q6 = queueMapper.loadQueueByType("6_SEAT");
            queue6Seat = new LinkedList<>(q6);
            // 【修复】同步更新 customerGroupMap
            for (CustomerGroup group : q6) {
                customerGroupMap.put(group.getGroup_id(), group);
            }

            int total = queue2Seat.size() + queue4Seat.size() + queue6Seat.size();
            if (total > 0) {
                System.out.println(" 加载队列数据: 2人桌=" + queue2Seat.size() +
                        ", 4人桌=" + queue4Seat.size() +
                        ", 6人桌=" + queue6Seat.size());
            }
        } catch (Exception e) {
            System.err.println(" 加载队列失败: " + e.getMessage());
        }
    }

    /**
     * 定时创建当日营业状态记录
     *
     * 功能说明：
     * 每天午夜 00:00:00 自动尝试插入当日营业状态记录，
     * 若记录已存在则跳过，确保每日营业数据完整性。
     *
     * 执行规则：
     * - Cron 表达式：0 0 0 * * ?（每日零点执行）
     * - 插入成功时记录日志，已存在时静默跳过
     *
     * 异常处理：
     * - 执行失败时记录错误日志，不影响主业务运行
     * - 支持后续扩展告警通知机制
     */
    @Scheduled(cron = "0 0 0 * * ?")  //启用定时任务调度
    public void autoCreateDailyStatus() {
        try {
            LocalDate today = LocalDate.now();
            int result = businessStatusMapper.insertTodayStatus(today);

            if (result > 0) {
                System.out.println(" [定時任務] 已自動創建營業狀態記錄：" + today);
            } else {
                System.out.println(" [定時任務] 營業狀態記錄已存在，跳過：" + today);
            }
        } catch (Exception e) {
            System.err.println(" [定時任務] 自動創建營業狀態失敗：" + e.getMessage());
            // 可選：記錄日誌或發送告警
        }
    }

    /**
     * 初始化当日营业状态记录
     *
     * 功能说明：
     * 1. 尝试插入今日营业状态记录，若已存在则跳过
     * 2. 加载营业状态字段并同步到内存变量 isOpenForBusiness
     *
     * 执行时机：
     * - 服务启动时自动调用，确保内存状态与数据库一致
     * - 异常时记录日志但不中断启动流程，保证系统可用性
     */
    private void initializeDailyStatus() {
        try {
            LocalDate today = LocalDate.now();
            int result = businessStatusMapper.insertTodayStatus(today);

            if (result > 0) {
                System.out.println(" 已創建當日營業狀態記錄：" + today);
            } else {
                System.out.println(" 當日營業狀態記錄已存在：" + today);
            }

            // 可選：加載營業狀態
            Boolean isOpen = businessStatusMapper.loadIsOpenStatus(today);
            if (isOpen != null) {
                this.isOpenForBusiness = isOpen;
            }
        } catch (Exception e) {
            System.err.println(" 初始化當日營業狀態失敗：" + e.getMessage());
        }
    }

    /**
     * 查询所有餐桌列表
     *
     * 功能说明：
     * 1. 懒加载检查：若缓存未初始化则先刷新餐桌缓存
     * 2. 查询数据库获取完整餐桌列表
     * 3. 增强餐桌对象：关联当前顾客组信息，便于界面展示
     * 4. 排序处理：主桌在前、子桌在后，符合界面展示顺序
     *
     * @return 餐桌列表；查询失败时返回空列表
     *
     * 缓存策略：
     * - 首次调用时触发全量缓存刷新，后续直接返回内存数据
     * - 异常时降级返回空列表，避免界面崩溃
     */
    @Transactional(readOnly = true)
    public List<Tables> getAllTables() {
        if (!cacheInitialized) {
            try {
                refreshTableCache();
                cacheInitialized = true;
            } catch (Exception e) {
                System.err.println("懶加載緩存失敗: " + e.getMessage());
                return List.of();//查詢失敗時返回「空列表」，而非 null
            }
        }
        try {
            List<Tables> tables = tablesMapper.findAllTables();
            enrichTablesWithGroups(tables);

            // 排序：主桌在前，子桌在後
            return sortTablesForDisplay(tables);

        } catch (Exception e) {
            System.err.println("查詢餐桌失敗: " + e.getMessage());
            return List.of();
        }
    }

    //4.4. 內存緩存與數據庫同步策略 (ConcurrentHashMap)
    //技術說明：為滿足 Swing 界面即時響應需求，Service 層維護一份內存態 tableMap 與 queue 快照，並在事務成功後同步更新。
    //採用「寫入數據庫 → 刷新內存 → 發佈事件」的三段式同步。ConcurrentHashMap 保證多線程下的讀寫安全，避免 Swing EDT 線程與後端工作線程競爭數據。
    /**
     * 刷新餐桌内存缓存
     *
     * 功能说明：
     * 1. 查询数据库获取最新餐桌列表并关联顾客组信息
     * 2. 构建 displayId 映射表，支持聚餐桌快速查找主桌
     * 3. 同步订单状态：
     *    - 聚餐桌：共享主桌订单状态，避免重复查询数据库
     *    - 普通桌/合并桌：独立查询各自订单状态
     * 4. 排序后更新至 tableMap 缓存，确保界面展示顺序一致
     *
     * 性能优化：
     * - 使用状态缓存避免聚餐桌组重复查询数据库
     * - 批量加载顾客组信息，减少数据库往返次数
     *
     * 调用时机：
     * - 服务启动时初始化缓存
     * - 餐桌状态变更后手动触发刷新
     */
    public void refreshTableCache() {
        List<Tables> tables = tablesMapper.findAllTables();
        enrichTablesWithGroups(tables);

        // 建立 displayId -> Table 映射，用于快速定位主桌
        Map<String, Tables> displayIdMap = new HashMap<>();
        for (Tables t : tables) {
            displayIdMap.put(t.getDisplayId(), t);
        }

        // 状态缓存：避免对同一个聚餐桌组重复查询数据库
        Map<Integer, Tables.OrderStatus> statusCache = new HashMap<>();

        // 同步订单状态（支持聚餐桌状态共享）
        for (Tables table : tables) {
            if (table.getTableId() > 0) {
                Tables.OrderStatus status = null;

                // 1. 判断是否为聚餐桌，如果是则共享主桌状态
                if (table.getTableType() == Tables.TableType.GROUPED && table.getGroupWith() != null) {
                    String[] groupIds = table.getGroupWith().split(",");
                    if (groupIds.length > 0) {
                        String mainDisplayId = groupIds[0].trim(); // 默认取第一个桌号作为主桌
                        Tables mainTable = displayIdMap.get(mainDisplayId);
                        if (mainTable != null) {
                            int mainTableId = mainTable.getTableId();
                            // 如果该主桌状态还没查过，则去数据库查询
                            if (!statusCache.containsKey(mainTableId)) {
                                Tables.OrderStatus mainStatus = orderMapper.getLatestOrderStatus(mainTableId);
                                statusCache.put(mainTableId, mainStatus != null ? mainStatus : Tables.OrderStatus.NO_ORDER);
                            }
                            // 将主桌的订单状态同步赋给当前桌子（14, 15等）
                            table.setOrderStatus(statusCache.get(mainTableId));
                            continue; // 已处理完毕，跳过后续普通查询
                        }
                    }
                }

                // 2. 普通餐桌/合并桌的正常查询逻辑
                status = orderMapper.getLatestOrderStatus(table.getTableId());
                table.setOrderStatus(status != null ? status : Tables.OrderStatus.NO_ORDER);
            }
        }

        //  排序後再更新到內存緩存
        List<Tables> sortedTables = sortTablesForDisplay(tables);
        for (Tables table : sortedTables) {
            tableMap.put(table.getDisplayId(), table);
        }
    }

    /**
     * 为餐桌列表关联顾客组信息
     *
     * 功能说明：
     * 1. 提取餐桌列表中所有非空的顾客组 ID 并去重
     * 2. 批量查询顾客组对象并构建 ID-对象映射
     * 3. 遍历餐桌列表，将匹配的顾客组对象注入餐桌属性
     *
     * @param tables 待增强的餐桌列表
     *
     * 性能优势：
     * - 批量查询替代逐条查询，减少数据库交互次数
     * - 使用 HashMap 加速顾客组匹配，时间复杂度 O(n)
     *
     * 应用场景：
     * - 查询餐桌列表后调用，确保界面能显示顾客组信息
     * - 缓存刷新时调用，保证内存数据完整性
     */
    private void enrichTablesWithGroups(List<Tables> tables) {
        if (tables == null || tables.isEmpty()) return;

        List<Integer> groupIds = tables.stream()
                .map(Tables::getCurrentGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (groupIds.isEmpty()) return;

        Map<Integer, CustomerGroup> groupMap = new HashMap<>();
        for (Integer groupId : groupIds) {
            CustomerGroup group = customerGroupMapper.findById(groupId);
            if (group != null) {
                groupMap.put(groupId, group);
            }
        }

        for (Tables table : tables) {
            if (table.getCurrentGroupId() != null) {
                table.setCurrentGroup(groupMap.get(table.getCurrentGroupId()));
            }
        }
    }

    /**
     * 添加顾客组并尝试自动分配餐桌
     *
     * 功能说明：
     * 1. 校验餐厅营业状态，非营业时直接返回 null
     * 2. 获取下一个叫号并创建顾客组对象，持久化到数据库并注册内存缓存
     * 3. 按优先级尝试四种分配策略：
     *    - 策略 4a：分配单张容量匹配的空闲主桌
     *    - 策略 4b：3-4 人顾客组合并两张相邻 2 人桌
     *    - 策略 4c：5-8 人顾客组合并两张相邻 4 人桌
     *    - 策略 4d：9-12 人顾客组合并两张相邻 6 人桌
     *    - 策略 4e：1-2 人顾客组尝试自动分裂占用中的餐桌
     * 4. 若分配失败则将顾客组加入对应容量队列，发布队列变更事件
     * 5. 递增叫号、提交事务、刷新餐桌缓存
     *
     * @param groupSize 顾客组人数
     * @return 创建成功的顾客组对象；餐厅未营业或系统异常时返回 null
     *
     * 事务管理：
     * - 使用手动事务控制（Connection 级别），确保分配逻辑原子性
     * - 异常时自动回滚，避免数据不一致
     */
    //第四章：服務層業務邏輯與事務管理 (Service Layer & Transaction Management)
    //4.1. @Transactional 聲明式事務管理
    //技術說明：使用 Spring 的 @Transactional 註解替代手動 conn.commit()/rollback()，由 Spring AOP 自動管理事務的開啟、提交與回滾。
    @Transactional(rollbackFor = Exception.class)//標註此方法為事務方法，且當方法內拋出任何 Exception 類型的異常時，強制回滾整個事務。
    //事務回滾（Transaction Rollback）是指在執行資料庫操作或程式碼邏輯時，若遇到錯誤或異常，將系統狀態恢復到事務開始前的狀態。這能確保資料的一致性與完整性。
    public CustomerGroup addCustomerGroup(int groupSize) {
        // ===== 1. 前置检查 =====
        if (!isOpenForBusiness) {
            System.out.println("餐廳未營業，無法添加顧客組");
            return null;
        }

        // ===== 2. 获取下一个叫号 + 创建顾客组 =====
        Integer nextCall = businessStatusMapper.getNextCallNumber(LocalDate.now());
        int callNumber = (nextCall != null) ? nextCall : 1;

        CustomerGroup group = new CustomerGroup(callNumber, groupSize);
        group.setStartTime(LocalDateTime.now());
        group.setAssigned(false);

        // 保存到数据库（生成 group_id）
        customerGroupMapper.save(group);

        // 注册到内存缓存
        customerGroupMap.put(group.getGroup_id(), group);

        // ===== 3. 尝试分配餐桌（4 种策略）=====
        boolean assigned = false;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                //  策略 4a: 尝试分配单张空桌（任意容量）
                Tables assignedTable = tryAssignTableToGroup(group);
                if (assignedTable != null) {
                    processTableAssignment(group, assignedTable);
                    assigned = true;
                    System.out.println(" 策略 4a: 单桌分配成功 - 餐桌 #" + assignedTable.getDisplayId());
                }
                //  策略 4b: 3-4 人 → 合并两张 2 人桌
                else if (groupSize >= 3 && groupSize <= 4) {
                    assigned = tryMergeTablesByCapacity(group, 2);
                    if (assigned) {
                        System.out.println(" 策略 4b: 合并 2 人桌分配成功 - 顾客组 #" + group.getCallNumber());
                    }
                }
                //  策略 4c: 5-8 人 → 合并两张 4 人桌
                else if (groupSize >= 5 && groupSize <= 8) {
                    assigned = tryMergeTablesByCapacity(group, 4);
                    if (assigned) {
                        System.out.println(" 策略 4c: 合并 4 人桌分配成功 - 顾客组 #" + group.getCallNumber());
                    }
                }
                //  策略 4d: 9-12 人 → 合并两张 6 人桌
                else if (groupSize >= 9 && groupSize <= 12) {
                    assigned = tryMergeTablesByCapacity(group, 6);
                    if (assigned) {
                        System.out.println(" 策略 4c: 合并 6 人桌分配成功 - 顾客组 #" + group.getCallNumber());
                    }
                }
                //   策略 4e: 1-2 人 → 自动分裂占用中的餐桌
                else if (groupSize <= 2) {
                    assigned = tryAutoSplitForGroup(group);
                    if (assigned) {
                        System.out.println(" 策略 4d: 自动分裂分配成功 - 顾客组 #" + group.getCallNumber());
                    }
                }

                // ===== 4. 未分配成功 → 加入队列 =====
                if (!assigned) {
                    // 数据库层：插入队列记录
                    enqueueGroup(group);

                    // 内存层：加入对应队列（线程安全）
                    synchronized (queueLock) {
                        if (groupSize <= 2) {
                            group.setPosition(queue2Seat.size() + 1);
                            queue2Seat.add(group);
                        } else if (groupSize <= 4) {
                            group.setPosition(queue4Seat.size() + 1);
                            queue4Seat.add(group);
                        } else {
                            group.setPosition(queue6Seat.size() + 1);
                            queue6Seat.add(group);
                        }
                    }
                    eventPublisher.publishEvent(QueueChangedEvent.fullRefresh(this));
                    System.out.println(" 顾客组 #" + group.getCallNumber() +
                            " 已加入队列，等待 " + groupSize + " 人桌");
                }


                // ===== 5. 递增下一个叫号 =====
                businessStatusMapper.incrementNextCallNumber(LocalDate.now());

                // ===== 6. 提交事务 =====
                conn.commit();

                // ===== 7. 刷新内存缓存（确保界面同步）=====
                refreshTableCache();

                return group;

            } catch (SQLException e) {
                // 异常回滚
                conn.rollback();
                System.err.println(" 分配餐桌事务失败: " + e.getMessage());
                throw new RuntimeException("分配餐桌事务失败", e);
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println(" 获取数据库连接失败: " + e.getMessage());
            throw new RuntimeException("获取数据库连接失败", e);
        }
    }

    /**
     * 尝试为顾客组分配单张餐桌
     *
     * 功能说明：
     * 1. 第一层查询：查找容量完全匹配的空闲主桌，跳过 6 人桌分配给 3 人以下顾客组的场景
     * 2. 第二层查询：若未找到，查找容量大 1 的空闲主桌，提升资源利用率
     * 3. 第三层查询：若仍未找到，查找所有类型的可用餐桌作为兜底策略
     * 4. 返回第一张满足条件的餐桌；无匹配时返回 null
     *
     * @param group 待分配的顾客组对象
     * @return 匹配的空闲餐桌对象；无合适餐桌时返回 null
     *
     * 业务规则：
     * - 3 人及以下顾客组不可分配 6 人桌，避免资源浪费
     * - 优先分配容量完全匹配的餐桌，其次考虑稍大容量
     */
    private Tables tryAssignTableToGroup(CustomerGroup group) throws SQLException {
        int groupSize = group.getGroupSize();

        // 第一層：容量完全匹配的空閒主桌
        List<Tables> available = tablesMapper.findAvailableTables(groupSize, "MAIN");
        //3.7業務狀態機與前置守門員 (State Gatekeeper)
        //技術說明：嚴格控制實體狀態流轉，防止非法躍遷（如已結帳的訂單再次下單、佔用中的餐桌被強制分配）。
        //Service 層作為「狀態守門員」，確保所有寫入操作都符合預定義的狀態機（VACANT → OCCUPIED → SETTING_UP → VACANT）。結合 Mapper 層的 WHERE status = 'VACANT' 查詢，實現了業務規則與數據層查詢的雙重防護。
        for (Tables table : available) {
            if (table.getCapacity() == 6 && groupSize < 4) continue; // 3 人以下不能坐 6 人桌
            return table;
        }

        // 第二層：容量稍大的主桌
        available = tablesMapper.findAvailableTables(groupSize + 1, "MAIN");
        for (Tables table : available) {
            if (table.getCapacity() == 6 && groupSize < 4) continue;
            return table;
        }

        // 第三層：所有類型的可用餐桌（最後手段）
        available = tablesMapper.findAvailableTables(groupSize, null);
        for (Tables table : available) {
            if (table.getCapacity() == 6 && groupSize < 4) continue;
            return table;
        }

        return null;
    }

    /**
     * 执行餐桌分配的核心逻辑
     *
     * 功能说明：
     * 1. 更新数据库：将餐桌状态设为占用，关联顾客组与入座人数
     * 2. 更新顾客组：标记为已分配，关联餐桌主键
     * 3. 累加当日顾客总数，用于经营统计
     * 4. 同步内存缓存：更新餐桌状态、顾客组引用、入座时间，清理合并/拆分标记
     * 5. 重置订单状态为未下单，确保新入座顾客可正常点餐
     *
     * @param group 待分配的顾客组对象
     * @param table 目标餐桌对象
     *
     * 异常处理：
     * - 数据库更新失败时抛出 SQLException，由调用方回滚事务
     * - 内存缓存未命中时记录警告日志，建议刷新全局缓存
     */
    private void processTableAssignment(CustomerGroup group, Tables table) throws SQLException {

        // 1. 更新數據庫：餐桌狀態 → OCCUPIED
        int updated = tablesMapper.updateTableStatus(
                table.getTableId(),
                "OCCUPIED",
                group.getGroup_id(),
                group.getGroupSize(),
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw new SQLException("更新餐桌狀態失敗：tableId=" + table.getTableId());
        }

        // 2. 更新數據庫：顧客組分配狀態
        updated = customerGroupMapper.updateAssignmentStatus(
                group.getGroup_id(),
                table.getTableId(),
                true,
                false
        );
        if (updated == 0) {
            throw new SQLException("更新顧客組狀態失敗：groupId=" + group.getGroup_id());
        }

        // 3. 累加當日顧客總數
        businessStatusMapper.incrementDailyTotalCustomers(
                group.getGroupSize(), LocalDate.now());

        // 【關鍵】4. 同步更新內存緩存（為局部刷新做準備）
        Tables memoryTable = tableMap.get(table.getDisplayId());
        if (memoryTable != null) {
            // ── 核心狀態更新 ──
            memoryTable.setStatus(Tables.TableStatus.OCCUPIED);           // 狀態變為占用
            memoryTable.assignCustomerGroup(group);                        // 關聯顧客組（同步ID+對象）
            memoryTable.setActualSeats(group.getGroupSize());             // 實際入座人數
            memoryTable.setStartTime(LocalDateTime.now());                 // 記錄開始時間

            // ── 清理可能殘留的合併/拆分標記（確保狀態純淨）─
            memoryTable.setMergedWith(null);
            memoryTable.setTableType(Tables.TableType.MAIN);
            memoryTable.setSplit(false);
            memoryTable.setSubTableSuffix(null);
            memoryTable.setMainTableId(null);

            // ── 重置訂單狀態（新入座時無訂單）─
            memoryTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);

            System.out.println(" 內存緩存已同步: 餐桌 #" + table.getDisplayId() + " → OCCUPIED");
        } else {
            //  極少見情況：內存中沒有該餐桌（可能是緩存未初始化）
            System.err.println(" 警告: 餐桌 #" + table.getDisplayId() + " 不在內存緩存中，建議調用 refreshTableCache()");
        }

        // 5. 更新顧客組對象引用（保持業務層數據一致）
        group.setAssigned(true);
        group.setTableId(table.getTableId());

        System.out.println(" 顧客組 #" + group.getCallNumber() +
                " 已分配到餐桌 #" + table.getDisplayId());
    }


    /**
     * 尝试合并指定容量的相邻餐桌并分配给顾客组
     *
     * 功能说明：
     * 1. 查询指定容量的空闲主桌列表
     * 2. 在内存中查找相邻餐桌对（同一行且编号连续）
     * 3. 计算座位分配：优先填满第一张桌，剩余人数分配给第二张
     * 4. 更新数据库：将两张餐桌标记为合并类型、占用状态，互相引用显示编号
     * 5. 更新顾客组：标记为已分配，关联主桌主键
     * 6. 同步内存缓存并累加当日顾客总数
     *
     * @param group 待分配的顾客组对象
     * @param tableCapacity 目标餐桌容量（2/4/6）
     * @return true=合并分配成功；false=无相邻餐桌或操作失败
     *
     * 业务规则：
     * - 仅合并相同容量的餐桌，确保座位分配逻辑一致
     * - 相邻判断基于 baseId 计算行号与编号连续性
     */
    private boolean tryMergeTablesByCapacity(CustomerGroup group, int tableCapacity) {
        try {
            // 1. 查詢所有符合條件的空桌
            List<Tables> availableTables = tablesMapper.findAvailableTables(tableCapacity, "MAIN");

            // 2. 在內存中找出相鄰的餐桌對（避免複雜SQL）
            List<Tables> adjacentPair = findAdjacentPair(availableTables, tableCapacity);
            if (adjacentPair == null || adjacentPair.size() != 2) {
                return false;  // 無可用相鄰桌
            }

            Tables table1 = adjacentPair.get(0);// 从相邻餐桌对列表中获取第一张餐桌（编号较小者，通常作为合并后的主桌）
            Tables table2 = adjacentPair.get(1);// 从相邻餐桌对列表中获取第二张餐桌（编号较大者，作为合并后的伙伴桌）

            // 3. 計算座位分配（優先填滿第一張桌）
            int seats1 = Math.min(group.getGroupSize(), table1.getCapacity());
            int seats2 = group.getGroupSize() - seats1;


            // 4. 更新数据库：标记两张桌为合并状态（分两步调用，确保事务内执行）
            int updated1 = tablesMapper.updateTableToMergedOccupied(
                    table1.getTableId(),
                    table2.getDisplayId(),  // table1 指向 table2
                    group.getGroup_id(),
                    seats1
            );
            int updated2 = tablesMapper.updatePartnerTableToMergedOccupied(
                    table2.getTableId(),
                    table1.getDisplayId(),  // table2 指向 table1
                    group.getGroup_id(),
                    seats2
            );

            if (updated1 == 0 || updated2 == 0) {
                throw new RuntimeException("更新合并餐桌状态失败");
            }

            // 5. 更新顾客组分配状态
            int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                    group.getGroup_id(),
                    table1.getTableId(),
                    true,
                    false
            );
            if (groupUpdated == 0) {
                throw new RuntimeException("更新顧客組失敗");
            }
            // 6.  同步更新內存緩存
            syncMergedTablesToCache(table1, table2, group, seats1, seats2);
            group.setAssigned(true);
            group.setTableId(table1.getTableId());
            // 累加當日顧客總數（合并桌分配也需统计）
            businessStatusMapper.incrementDailyTotalCustomers(
                    group.getGroupSize(), LocalDate.now());
            System.out.println(" 餐桌 #" + table1.getDisplayId() + " + #" + table2.getDisplayId()
                    + " 已合併，分配給顧客組 #" + group.getCallNumber()
                    + " (" + group.getGroupSize() + "人)");

            return true;

        } catch (Exception e) {
            System.err.println("合併餐桌分配失敗: " + e.getMessage());
            return false;
        }
    }

    /**
     * 查找相邻的空闲餐桌对
     *
     * 功能说明：
     * 遍历餐桌列表，检查相邻元素是否满足物理相邻规则（同一行且编号连续），
     * 返回第一对满足条件的餐桌；无匹配时返回 null。
     *
     * @param tables 待查找的餐桌列表
     * @param capacity 目标餐桌容量，用于过滤匹配项
     * @return 相邻餐桌列表（固定 2 张）；无匹配时返回 null
     */
    private List<Tables> findAdjacentPair(List<Tables> tables, int capacity) {
        if (tables == null || tables.size() < 2) {
            return null;
        }

        // 遍歷查找相鄰對：同一行 + base_id相差1
        for (int i = 0; i < tables.size() - 1; i++) {
            Tables t1 = tables.get(i);
            Tables t2 = tables.get(i + 1);

            // 判斷是否相鄰：同一行 + 連續編號
            if (isAdjacent(t1, t2, capacity)) {
                return Arrays.asList(t1, t2);
            }
        }
        return null;
    }

    /**
     * 判断两张餐桌是否物理相邻
     *
     * 功能说明：
     * 1. 校验两张餐桌容量均与目标容量匹配
     * 2. 计算每张餐桌所在行号：(baseId - 1) / 每行桌数
     * 3. 判断条件：行号相同且 baseId 差值绝对值为 1
     *
     * @param t1 第一张餐桌对象
     * @param t2 第二张餐桌对象
     * @param capacity 目标餐桌容量
     * @return true=两张餐桌左右相邻；false=不相邻或容量不匹配
     *
     * 相邻规则：
     * - 仅同一行的餐桌可能相邻（避免上下行误判）
     * - 编号连续指 baseId 相差 1（如 7 与 8、10 与 11）
     */
    private boolean isAdjacent(Tables t1, Tables t2, int capacity) {
        if (t1.getCapacity() != capacity || t2.getCapacity() != capacity) {
            return false;
        }

        int row1 = (t1.getBaseId() - 1) / COLS_PER_ROW;
        int row2 = (t2.getBaseId() - 1) / COLS_PER_ROW;

        // 同一行 + 編號連續 = 相鄰
        return row1 == row2 && Math.abs(t1.getBaseId() - t2.getBaseId()) == 1;
    }

    /**
     * 合并餐桌后同步更新内存缓存
     *
     * 功能说明：
     * 1. 更新第一张餐桌缓存：设为占用状态、合并类型，关联伙伴桌显示编号与顾客组
     * 2. 更新第二张餐桌缓存：设为占用状态、合并类型，关联第一张桌显示编号与顾客组
     * 3. 设置两张餐桌的实际入座人数与开始时间
     *
     * @param t1 第一张合并餐桌对象
     * @param t2 第二张合并餐桌对象
     * @param group 关联的顾客组对象
     * @param seats1 第一张餐桌分配的实际座位数
     * @param seats2 第二张餐桌分配的实际座位数
     *
     * 执行时机：
     * - 仅在数据库合并操作成功后调用
     * - 确保内存缓存与持久化数据实时一致，避免界面显示滞后
     */
    private void syncMergedTablesToCache(Tables t1, Tables t2, CustomerGroup group,
                                         int seats1, int seats2) {
        // 更新table1緩存
        Tables mem1 = tableMap.get(t1.getDisplayId());
        if (mem1 != null) {
            mem1.setStatus(Tables.TableStatus.OCCUPIED);
            mem1.setTableType(Tables.TableType.MERGED);
            mem1.setMergedWith(t2.getDisplayId());
            mem1.setCurrentGroupId(group.getGroup_id());
            mem1.setCurrentGroup(group);
            mem1.setActualSeats(seats1);
            mem1.setStartTime(LocalDateTime.now());
        }

        // 更新table2緩存
        Tables mem2 = tableMap.get(t2.getDisplayId());
        if (mem2 != null) {
            mem2.setStatus(Tables.TableStatus.OCCUPIED);
            mem2.setTableType(Tables.TableType.MERGED);
            mem2.setMergedWith(t1.getDisplayId());
            mem2.setCurrentGroupId(group.getGroup_id());
            mem2.setCurrentGroup(group);
            mem2.setActualSeats(seats2);
            mem2.setStartTime(LocalDateTime.now());
        }
    }

    /**
     * 根据餐桌显示编号查询其合并伙伴桌
     *
     * 功能说明：
     * 1. 查询指定餐桌对象，校验其是否为合并类型且存在伙伴桌引用
     * 2. 根据 merged_with 字段查询并返回伙伴桌对象
     *
     * @param displayId 餐桌显示编号
     * @return 伙伴桌对象；非合并桌或伙伴桌不存在时返回 null
     *
     * 应用场景：
     * - 合并桌入座时同步更新两张桌的状态
     * - 离店时批量处理合并桌关联的订单与预约记录
     */
    @Transactional(readOnly = true)
    public Tables getMergedPartnerTable(String displayId) {
        Tables table = tablesMapper.findByDisplayId(displayId);
        if (table != null &&
                table.getTableType() == Tables.TableType.MERGED &&
                table.getMergedWith() != null) {
            return tablesMapper.findByDisplayId(table.getMergedWith());
        }
        return null;
    }

    /**
     * 尝试为顾客组自动分裂餐桌
     *
     * 功能说明：
     * 1. 校验新顾客组人数范围（1-2人），超出则直接返回失败
     * 2. 查找符合分裂条件的目标餐桌：空闲主桌、容量可均分、两组人数均不超过子桌容量
     * 3. 创建两个子桌对象：分别关联原顾客组与新顾客组，继承或初始化订单状态
     * 4. 更新主桌状态为拆分中，插入子桌记录并回填主键
     * 5. 更新顾客组关联餐桌ID，迁移原订单至子桌A
     * 6. 累加当日顾客总数，同步内存缓存，从队列移除新顾客组
     *
     * @param group 待入座的新顾客组对象
     * @return true=自动分裂成功，顾客组已分配入座；false=无合适餐桌或校验失败
     *
     * 业务规则：
     * - 仅支持2人桌或4人桌分裂为两个等容量子桌
     * - 4人桌已有3人时禁止分裂，避免子桌容量不足
     * - 分裂后原顾客组继续使用子桌A，新顾客组分配至子桌B
     */
    public boolean tryAutoSplitForGroup(CustomerGroup group) {
        int newGroupSize = group.getGroupSize();

        // 1. 验证人数范围
        if (newGroupSize < 1 || newGroupSize > 2) {
            return false;
        }

        // 2. 查找可分裂的餐桌（内存过滤，不查库提升性能）
        Tables targetTable = findSplittableTableForGroup(newGroupSize);
        if (targetTable == null) {
            System.out.println(" 未找到可分裂的餐桌");
            return false;
        }

        CustomerGroup existingGroup = targetTable.getCurrentGroup();
        LocalDateTime originalStartTime = targetTable.getStartTime();

        if (existingGroup == null) {
            return false;
        }

        int existingSize = existingGroup.getGroupSize();
        int physicalCapacity = targetTable.getPhysicalCapacity();
        int subTableCapacity = physicalCapacity / 2;  // 2→1, 4→2

        System.out.println(" 找到可分裂餐桌: #" + targetTable.getDisplayId() +
                " (当前" + existingSize + "人，新增" + newGroupSize + "人)");

        // 3. 准备子桌数据（先创建对象，保存后 MyBatis 回填 table_id）
        Tables subTableA = createSubTable(targetTable, "a", subTableCapacity,
                existingGroup.getGroup_id(), existingSize);
        subTableA.setStartTime(originalStartTime != null ? originalStartTime : LocalDateTime.now());

        Tables subTableB = createSubTable(targetTable, "b", subTableCapacity,
                group.getGroup_id(), newGroupSize);
        subTableB.setStartTime(LocalDateTime.now());

        // 4.  执行数据库操作（复用传入的 conn，不使用 @Transactional）

        // 4.1 更新主桌为 SPLITTING 状态
        int updated = tablesMapper.updateMainTableToSplitting(targetTable.getTableId());
        if (updated == 0) {
            throw new RuntimeException("更新主桌状态失败");
        }

        //【关键修复】4.2 分别插入两个子桌（确保主键回填）
        // 插入子桌 A
        // MyBatis 的 insert 语句返回受影响行数：成功插入返回 1，失败或无变化返回 0，因此 ==0 表示插入未生效
        if (tablesMapper.saveSubTable(subTableA) == 0) {
            throw new RuntimeException("插入子桌 A 失败");
        }
        // 插入子桌 B
        if (tablesMapper.saveSubTable(subTableB) == 0) {
            throw new RuntimeException("插入子桌 B 失败");
        }

        //  验证主键已回填（调试用）
        if (subTableA.getTableId() <= 0 || subTableB.getTableId() <= 0) {
            throw new RuntimeException("子桌主键回填失败: A=" + subTableA.getTableId() +
                    ", B=" + subTableB.getTableId());
        }

        //  4.3 更新顾客组的餐桌关联（使用已回填的 table_id）
        customerGroupMapper.updateTableId(existingGroup.getGroup_id(), subTableA.getTableId());
        customerGroupMapper.updateTableId(group.getGroup_id(), subTableB.getTableId());

        // 4.4 【关键】迁移原订单到子桌 A（原顾客组继续使用）
        orderMapper.migrateOrdersToTable(targetTable.getTableId(), subTableA.getTableId());
        System.out.println(" 订单已迁移至子桌 #" + subTableA.getDisplayId());

        // 累加當日顧客總數（自动分裂分配也需统计）
        businessStatusMapper.incrementDailyTotalCustomers(
                group.getGroupSize(), LocalDate.now());

        // 5.  同步更新内存缓存（事务提交后由调用方刷新）
        syncMemoryAfterAutoSplit(targetTable, subTableA, subTableB, existingGroup, group, originalStartTime);

        // 6. 从队列移除新顾客组（内存操作）
        removeFromQueue(group);

        System.out.println(" 餐桌 #" + targetTable.getDisplayId() +
                " 自动分裂成功: " + existingSize + "人 + " + newGroupSize + "人");
        return true;

    }

    /**
     * 查找可分裂的餐桌
     *
     * 功能说明：
     * 1. 从内存缓存获取所有餐桌，按baseId升序排序确保分配顺序一致
     * 2. 过滤基础条件：状态为占用、未拆分、非合并桌、非子桌
     * 3. 校验业务规则：
     *    - 原顾客组与新顾客组人数均不超过子桌容量
     *    - 两组总人数不超过主桌物理容量
     *    - 4人桌已有3人时禁止分裂
     *    - 2人桌已有1人时仅允许新增1人
     * 4. 返回第一张满足条件的餐桌；无匹配时返回null
     *
     * @param newGroupSize 新顾客组人数
     * @return 可分裂的餐桌对象；无合适餐桌时返回null
     *
     * 性能优化：
     * - 直接操作内存缓存，避免频繁查询数据库
     * - 找到即返回，减少遍历开销
     */
    private Tables findSplittableTableForGroup(int newGroupSize) {
        // 从内存缓存 tableMap 中提取所有餐桌对象，创建新 ArrayList 副本以便后续排序和遍历，避免直接操作原始集合
        List<Tables> sortedTables = new ArrayList<>(tableMap.values());
        sortedTables.sort(Comparator.comparingInt(Tables::getBaseId));

        for (Tables table : sortedTables) {
            // === 基础状态过滤 ===
            if (table.getStatus() != Tables.TableStatus.OCCUPIED ||
                    table.isSplit() ||
                    table.getTableType() == Tables.TableType.MERGED ||
                    table.getSubTableSuffix() != null) {
                continue;
            }

            CustomerGroup existingGroup = table.getCurrentGroup();
            if (existingGroup == null) continue;

            int existingSize = existingGroup.getGroupSize();
            int physicalCapacity = table.getPhysicalCapacity();
            int subTableCapacity = physicalCapacity / 2;

            // === 核心业务规则验证 ===
            if (existingSize > subTableCapacity) continue;           // 规则 1: 原组人数≤子桌容量
            if (newGroupSize > subTableCapacity) continue;           // 规则 2: 新组人数≤子桌容量
            if (existingSize + newGroupSize > physicalCapacity) continue;  // 规则 3: 总人数≤物理容量
            if (physicalCapacity == 4 && existingSize == 3) continue;      // 规则 4:4 人桌已有 3 人禁止分裂
            if (physicalCapacity == 2 && existingSize == 1 && newGroupSize > 1) continue;  // 规则 5:2 人桌 1+2>2

            return table;
        }
        return null;
    }

    /**
     * 创建子桌对象
     *
     * 功能说明：
     * 1. 初始化子桌基础属性：继承主桌baseId，容量减半，显示编号添加后缀
     * 2. 设置子桌专属字段：类型为SUBTABLE，关联主桌ID，后缀标识
     * 3. 关联顾客组：设置currentGroupId与实际入座人数
     * 4. 初始化订单状态：子桌A继承原订单状态，子桌B设为无订单
     *
     * @param mainTable 所属主桌对象
     * @param suffix 子桌后缀标识（"a"或"b"）
     * @param capacity 子桌容量（主桌容量的一半）
     * @param groupId 关联的顾客组ID
     * @param actualSeats 该子桌的实际入座人数
     * @return 初始化完成的子桌对象，待持久化到数据库
     *
     * 注意事项：
     * - tableId 设为0，保存后由数据库自增主键回填
     * - physicalCapacity 与 capacity 保持一致，确保容量计算准确
     */
    private Tables createSubTable(Tables mainTable, String suffix, int capacity,
                                  Integer groupId, int actualSeats) {
        Tables subTable = new Tables(mainTable.getBaseId(), capacity,
                mainTable.getDisplayId() + suffix);
        subTable.setTableId(0);  // 新记录，保存后 MyBatis 回填
        subTable.setPhysicalCapacity(capacity);
        subTable.setStatus(Tables.TableStatus.OCCUPIED);
        subTable.setTableType(Tables.TableType.SUBTABLE);
        subTable.setSplit(false);
        subTable.setSubTableSuffix(suffix);
        subTable.setMainTableId(mainTable.getTableId());
        subTable.setActualSeats(actualSeats);
        subTable.setCurrentGroupId(groupId);
        subTable.setStartTime(LocalDateTime.now());
        // 订单状态：子桌 A 继承原订单，子桌 B 为新组无订单
        subTable.setOrderStatus("a".equals(suffix) ?
                mainTable.getOrderStatus() :
                Tables.OrderStatus.NO_ORDER);
        return subTable;
    }

    /**
     * 自动分裂后同步内存缓存
     *
     * 功能说明：
     * 1. 更新主桌缓存：状态设为拆分中，标记拆分标志，清空顾客组关联
     * 2. 保留原子桌开始时间：确保子桌A继承原顾客组的入座时间
     * 3. 添加子桌到缓存：将两个新子桌对象加入tableMap
     * 4. 同步顾客组引用：更新两组顾客的关联餐桌ID与已分配标记
     *
     * @param mainTable 分裂后的主桌对象
     * @param subA 关联原顾客组的子桌对象
     * @param subB 关联新顾客组的子桌对象
     * @param existingGroup 原顾客组对象
     * @param newGroup 新顾客组对象
     * @param originalStartTime 原顾客组的入座开始时间
     *
     * 执行时机：
     * - 仅在数据库分裂操作成功后调用
     * - 确保内存缓存与持久化数据实时一致，避免界面显示滞后
     */
    private void syncMemoryAfterAutoSplit(Tables mainTable, Tables subA, Tables subB,
                                          CustomerGroup existingGroup, CustomerGroup newGroup, LocalDateTime originalStartTime) {
        // 1. 更新主桌缓存状态
        Tables memMain = tableMap.get(mainTable.getDisplayId());
        if (memMain != null) {
            memMain.setStatus(Tables.TableStatus.SPLITTING);
            memMain.setSplit(true);
            memMain.setCurrentGroupId(null);
            memMain.setCurrentGroup(null);
        }
        //  确保内存中的子桌A也使用原始开始时间
        if (originalStartTime != null) {
            subA.setStartTime(originalStartTime);
        }

        // 2. 添加子桌到缓存
        tableMap.put(subA.getDisplayId(), subA);
        tableMap.put(subB.getDisplayId(), subB);

        // 3. 同步顾客组引用
        existingGroup.setTableId(subA.getTableId());
        existingGroup.setAssigned(true);
        newGroup.setTableId(subB.getTableId());
        newGroup.setAssigned(true);

        // 4. 可选：刷新全局缓存确保一致性
        // refreshTableCache();
    }

    /**
     * 从所有排队队列中移除指定顾客组
     *
     * 功能说明：
     * 遍历 2 人桌、4 人桌、6 人桌三个队列，移除与指定顾客组 ID 匹配的记录，
     * 确保顾客入座或取消排队后不会重复出现在等待列表中。
     *
     * @param group 待移除的顾客组对象
     *
     * 线程安全：
     * - 使用 synchronized 锁保护队列读写操作
     * - 确保多线程环境下内存队列数据一致性
     */
    private void removeFromQueue(CustomerGroup group) {
        synchronized (queueLock) {
            queue2Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue4Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue6Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
        }
    }

    /**
     * 处理顾客离店流程，支持普通桌/合并桌/聚餐桌多种场景
     *
     * 功能说明：
     * 1. 查询主餐桌并校验存在性
     * 2. 根据餐桌类型收集所有关联餐桌：合并桌添加伙伴桌，聚餐桌解析 group_with 字段添加所有关联桌
     * 3. 收集所有餐桌关联的预约记录 ID（使用 Set 去重）
     * 4. 批量更新所有关联餐桌：内存缓存设为准备中并清空顾客关联，数据库更新状态为准备中
     * 5. 收集并删除关联订单：先删订单明细，再删订单主表，严格遵守外键依赖顺序
     * 6. 删除预约记录：确保所有引用该预约的订单已删除后再执行
     * 7. 删除顾客组记录：所有关联桌共享同一顾客组，仅删除一次
     *
     * @param displayId 主餐桌显示编号
     *
     * 业务规则：
     * - 合并桌与聚餐桌需同步更新所有关联桌状态，确保数据一致性
     * - 删除操作遵循外键约束顺序：order_items → table_orders → table_reservations
     * - 删除失败时记录日志但不中断流程，保证离店操作最终完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void processCustomerDeparture(String displayId) {
        // 1. 查询主餐桌
        Tables mainTable = tablesMapper.findByDisplayId(displayId);
        if (mainTable == null) {
            throw new IllegalArgumentException("餐桌不存在: " + displayId);
        }

        // 【核心】收集所有需要处理的餐桌（支持合并桌 + 聚餐桌）
        List<Tables> tablesToProcess = new ArrayList<>();
        tablesToProcess.add(mainTable);

        // ── 情况 1: 合并桌 (2 张桌) ──
        if (mainTable.getTableType() == Tables.TableType.MERGED && mainTable.getMergedWith() != null) {
            Tables partner = tablesMapper.findByDisplayId(mainTable.getMergedWith());
            if (partner == null || partner.getStatus() != Tables.TableStatus.OCCUPIED) {
                throw new IllegalStateException("合并餐桌伙伴状态异常: " + mainTable.getMergedWith());
            }
            tablesToProcess.add(partner);
        }
        // ── 情况 2: 聚餐桌 (3 张或以上) ──
        else if (mainTable.getTableType() == Tables.TableType.GROUPED && mainTable.getGroupWith() != null) {
            // 解析 group_with 字段（格式："7,8,9"）
            String[] groupIds = mainTable.getGroupWith().split(",");
            for (String id : groupIds) {
                String trimmedId = id.trim();
                // 避免重复添加主桌
                if (!trimmedId.equals(displayId) && !trimmedId.isEmpty()) {
                    Tables groupedTable = tablesMapper.findByDisplayId(trimmedId);
                    if (groupedTable == null) {
                        System.err.println(" 聚餐桌关联桌不存在: " + trimmedId);
                        continue;
                    }
                    // 验证状态
                    if (groupedTable.getStatus() != Tables.TableStatus.OCCUPIED) {
                        throw new IllegalStateException("聚餐桌 #" + trimmedId +
                                " 状态异常: " + groupedTable.getStatus());
                    }
                    tablesToProcess.add(groupedTable);
                }
            }
        }

        // 【关键修复】收集所有需要删除的预约记录ID（用Set去重）
        Set<String> reservationIdsToDelete = new HashSet<>();
        for (Tables t : tablesToProcess) {
            if (t.getCurrentReservationId() != null && !t.getCurrentReservationId().isEmpty()) {
                reservationIdsToDelete.add(t.getCurrentReservationId());
            }
        }

        // 【调试日志】输出处理列表
        System.out.println(" [DEBUG] processCustomerDeparture:");
        System.out.println("   主桌: #" + displayId + " (类型:" + mainTable.getTableType() + ")");
        System.out.println("   处理餐桌数: " + tablesToProcess.size());
        for (Tables t : tablesToProcess) {
            System.out.println("   - 餐桌#" + t.getDisplayId() +
                    ", reservationId: " + t.getCurrentReservationId());
        }
        System.out.println("   待删除预约记录数: " + reservationIdsToDelete.size());

        // 【批量处理】更新所有关联餐桌的内存缓存 + 数据库状态
        for (Tables table : tablesToProcess) {
            String tid = table.getDisplayId();

            // 1. 更新内存缓存
            Tables memoryTable = tableMap.get(tid);
            if (memoryTable != null) {
                memoryTable.setCurrentGroupId(null);
                memoryTable.setCurrentGroup(null);
                memoryTable.setStatus(Tables.TableStatus.SETTING_UP);
                memoryTable.setActualSeats(0);
                memoryTable.setEndTime(LocalDateTime.now());
                memoryTable.setCurrentReservationId(null);  //  清空预约ID关联
                System.out.println(" 内存缓存已更新: #" + tid);
            }

            // 2. 更新数据库状态（占用 → 准备中）
            int updated = tablesMapper.updateTableStatusForDeparture(
                    table.getTableId(),
                    Tables.TableStatus.SETTING_UP.name(),
                    null, 0, table.getTableType().name());
            if (updated == 0) {
                System.err.println(" 数据库更新失败: #" + tid);
            } else {
                System.out.println("    数据库已更新: #" + tid);
            }
        }

        /**
         * 【核心修复】删除订单和预约记录的顺序（关键！外键约束）
         * 删除顺序：
         * 1 先删 order_items（订单明细，外键→table_orders）
         * 2 再删 table_orders（订单主表，外键→table_reservations）
         * 3 最后删 table_reservations（预约记录，被table_orders引用）
         *
         * 预约订单处理逻辑：
         * - 通过 reservation_id 查询关联的订单
         * - 先删订单明细 → 再删订单主表 → 最后删预约记录
         */

        /**
         *  步骤1：收集所有需要删除的订单ID（支持普通桌/合并桌/聚餐桌 + 预约订单）
         */
        Set<Integer> orderIdsToDelete = new HashSet<>();

        for (Tables table : tablesToProcess) {
            Integer orderId = null;

            // 判断是否为预约关联的餐桌
            if (table.getCurrentReservationId() != null && !table.getCurrentReservationId().isEmpty()) {
                //  预约订单：通过 reservation_id 查询订单
                orderId = orderMapper.findOrderIdByReservationId(table.getCurrentReservationId());
            } else {
                //  普通堂食订单：通过 table_id 查询订单
                orderId = orderMapper.findOrderIdByTableId(table.getTableId());
            }

            if (orderId != null) {
                orderIdsToDelete.add(orderId);
            }
        }

        /**
         *  步骤2：执行删除操作（严格按照外键依赖顺序）
         * 顺序：order_items → table_orders → table_reservations
         */
        for (Integer orderId : orderIdsToDelete) {
            try {
                //  2.1 先删订单明细（外键约束：order_items → table_orders）
                orderItemMapper.deleteOrderItemsByOrderId(orderId);

                //  2.2 再删订单主表（外键约束：table_orders → table_reservations）
                orderMapper.deleteOrder(orderId);

                System.out.println(" 已删除订单: #" + orderId + " (明细+主表)");
            } catch (Exception e) {
                //  记录可能已被其他操作删除，忽略异常（幂等处理）
                System.out.println(" 订单 #" + orderId + " 删除时异常（可能已删除）: " + e.getMessage());
            }
        }

        /**
         *  步骤3：最后删除预约记录（确保所有引用它的订单已删除）
         */
        for (String reservationId : reservationIdsToDelete) {
            try {
                int deleted = reservationMapper.delete(reservationId);
                System.out.println("️ 已删除预约记录: " + reservationId + " (影响行数: " + deleted + ")");
            } catch (Exception e) {
                //  记录已删除或不存在时忽略（幂等处理）
                System.out.println(" 预约记录 " + reservationId + " 删除时异常（可能已删除）: " + e.getMessage());
            }
        }

        // 4. 删除顾客组记录（所有桌共享同一个 group，只删一次）
        if (mainTable.getCurrentGroupId() != null) {
            customerGroupMapper.delete(mainTable.getCurrentGroupId());
            System.out.println("️ 已删除顾客组: #" + mainTable.getCurrentGroupId());
        }

        // 【调试日志】输出最终结果
        String tableList = tablesToProcess.stream()
                .map(Tables::getDisplayId)
                .collect(Collectors.joining(","));
        System.out.println(" [DEBUG] 离店处理完成: 餐桌组 [" + tableList + "]" +
                ", 删除预约记录数: " + reservationIdsToDelete.size() +
                ", 删除订单数: " + orderIdsToDelete.size() + "\n");
    }

    /**
     * 清理餐桌并触发后续业务检查
     *
     * 功能说明：
     * 1. 校验餐桌存在性及状态为准备中（SETTING_UP），确保仅可清理已离店餐桌
     * 2. 更新数据库状态为空闲（VACANT），清空顾客组关联与实际入座人数
     * 3. 同步更新内存缓存，确保界面即时反映最新状态
     * 4. 若餐桌类型为主桌，检查并通知匹配的预约记录
     * 5. 触发等待顾客分配检查，根据预约需求动态调整排队顾客分配策略
     *
     * @param displayId 待清理的餐桌显示编号
     *
     * 执行时机：
     * - 顾客离店后餐桌完成清理工作时调用
     * - 确保餐桌资源及时释放并重新投入分配流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void cleanTable(String displayId) throws SQLException {
        Tables table = tablesMapper.findByDisplayId(displayId);
        if (table == null || table.getStatus() != Tables.TableStatus.SETTING_UP) {
            throw new IllegalStateException("餐桌 " + displayId + " 状态不可清理");
        }

        tablesMapper.updateTableStatus(
                table.getTableId(),
                Tables.TableStatus.VACANT.name(),
                null, 0, null);

        Tables memoryTable = tableMap.get(displayId);
        if (memoryTable != null) {
            memoryTable.setStatus(Tables.TableStatus.VACANT);
            memoryTable.setCurrentGroup(null);
            memoryTable.setCurrentGroup(null);
            memoryTable.setActualSeats(0);
        }

        // 3. 检查是否有匹配的预约（仅 MAIN 类型餐桌）
        if (table.getTableType() == Tables.TableType.MAIN) {
            checkAndNotifyMatchingReservations(table);
        }

        //規則：當發現有1.5小時以内的預約入座！該類型的餐桌將會停止為安排正在排隊的顧客尋找合適的桌子
        checkAndAssignWaitingCustomers();

    }

    /**
     *  检查子桌是否有合并机会（当两张子桌都变为空闲时）
     *
     * @param displayId 刚被清理的子桌显示ID
     * @return MergeOpportunity 对象，包含合并信息或无机会标志
     */
    @Transactional(readOnly = true)
    public MergeOpportunity checkSubTableMergeOpportunity(String displayId) {
        // 【关键】从数据库查最新状态
        Tables subTable = tablesMapper.findByDisplayId(displayId);
        if (subTable == null || subTable.getMainTableId() == null) {
            return MergeOpportunity.none();
        }

        // 查询同主桌的另一张子桌
        List<Tables> subTables = tablesMapper.findSubTablesByMainId(subTable.getMainTableId());
        if (subTables.size() != 2) {
            return MergeOpportunity.none();
        }

        // 找另一张子桌
        Tables partner = subTables.stream()
                .filter(t -> !t.getDisplayId().equals(displayId))
                .findFirst()
                .orElse(null);

        // 【关键】检查两张子桌是否都为VACANT
        if (partner != null &&
                subTable.getStatus() == Tables.TableStatus.VACANT &&
                partner.getStatus() == Tables.TableStatus.VACANT) {

            // 确定哪张是a，哪张是b（按displayId排序）
            String tableA = subTable.getDisplayId();
            String tableB = partner.getDisplayId();
            if (tableA.compareTo(tableB) > 0) {
                String temp = tableA;
                tableA = tableB;
                tableB = temp;
            }

            // 【修复】提取主桌ID（去掉末尾的a或b）
            String mainTableId = subTable.getDisplayId().replaceAll("[ab]$", "");

            // System.out.println(" 子桌合并机会: #" + tableA + " + #" + tableB + " 均为空闲");

            // 【修复】参数顺序：mainTableDisplayId, subTableA, subTableB
            return MergeOpportunity.of(mainTableId, tableA, tableB);
        }

        return MergeOpportunity.none();
    }


    /**
     * 检查并尝试为等待顾客分配餐桌
     * 【前置条件】只有满足以下条件才执行分配：
     * 1. 至少有一个队列不为空（有排队顾客）
     * 2. 不是所有容量的餐桌都被1.5小时内预约占用
     */
    public void checkAndAssignWaitingCustomers() {
        // 【前置条件1】检查是否有排队顾客（三个队列都为空则直接返回）
        if (queue2Seat.isEmpty() && queue4Seat.isEmpty() && queue6Seat.isEmpty()) {
            System.out.println(" [跳过] 所有队列为空，无需执行餐桌分配检查");
            return;
        }


        // 【前置条件2】获取1.5小时内的预约需求
        Map<Integer, Integer> reservedDemand = getReservedDemandByCapacity();


        // 【前置条件3】检查是否"所有有排队顾客的容量"都被预约占用
        // 只有当某个容量：(1)有排队顾客 且 (2)无预约需求 时，才执行分配
        boolean shouldProceed = false;

        for (String queueType : Arrays.asList("2_SEAT", "4_SEAT", "6_SEAT")) {
            int capacity = parseCapacityFromQueueType(queueType);
            Queue<CustomerGroup> queue = getQueueByType(queueType);

            // 该队列有排队顾客 + 该容量无预约需求 = 可以执行分配
            if (!queue.isEmpty() && reservedDemand.getOrDefault(capacity, 0) == 0) {
                shouldProceed = true;
                break;
            }
        }

        if (!shouldProceed) {
            System.out.println(" [跳过] 无符合条件的排队顾客可分配（队列空 或 全被预约占用）");
            return;
        }

        System.out.println(" [执行] 开始检查排队顾客餐桌分配...");

        // 【主逻辑】按队列类型顺序处理（2人→4人→6人）
        for (String queueType : Arrays.asList("2_SEAT", "4_SEAT", "6_SEAT")) {
            int capacity = parseCapacityFromQueueType(queueType);
            Queue<CustomerGroup> queue = getQueueByType(queueType);

            // 该队列为空，跳过
            if (queue.isEmpty()) {
                continue;
            }

            // 【核心规则】如果该容量有1.5小时内预约需求，跳过该队列
            if (reservedDemand.getOrDefault(capacity, 0) > 0) {
                System.out.println("⏭ 容量" + capacity + "人桌有1.5小时内预约需求，暂停分配该队列顾客");
                continue;
            }

            //  条件满足：执行分配
            assignWaitingCustomersByQueueType(queueType);
        }
    }

    /**
     * 获取1.5小时内预约需求并按容量统计
     *
     * 功能说明：
     * 1. 查询当前时间起90分钟内、状态为待确认或已延迟的预约记录
     * 2. 解析每条记录的餐桌配置描述，提取容量与数量映射
     * 3. 按容量聚合统计各桌型所需的总桌数
     *
     * @return 容量 - 桌数映射，键为餐桌容量（2/4/6），值为对应需求数量
     *
     * 数据源：
     * - reservationMapper.findReservationsByTimeRange 查询预约记录
     * - parseTableConfig 解析配置描述字符串
     */
    private Map<Integer, Integer> getReservedDemandByCapacity() {
        Map<Integer, Integer> demand = new HashMap<>();

        // 查询1.5小时内的预约记录（状态为待确认或已确认）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(90);

        //  调用 Mapper 查询（需在 TableReservationMapper 中添加对应方法）
        List<TableReservation> reservations = reservationMapper.findReservationsByTimeRange(
                now, threshold, Arrays.asList("PRE_CONFIRMED", "DELAYED"));

        for (TableReservation res : reservations) {
            // 解析 table_config_desc，如 "2人桌 x1, 4人桌 x2" → {2:1, 4:2}
            String config = res.getTableConfigDesc();
            if (config != null && !config.isEmpty()) {
                Map<String, Integer> parsed = parseTableConfig(config);// 调用解析方法，将配置描述字符串（如"2人桌 x1, 4人桌 x2"）转换为容量 - 数量映射
                for (Map.Entry<String, Integer> entry : parsed.entrySet()) {// 遍历解析后的每一项，entry.getKey() 为容量字符串（如"2"），entry.getValue() 为对应桌数
                    try {
                        int cap = Integer.parseInt(entry.getKey());// 将容量键从字符串解析为整数，便于后续按数字维度聚合统计
                        int cnt = entry.getValue();// 获取该项对应的餐桌需求数量
                        demand.merge(cap, cnt, Integer::sum);//使用 merge 方法累加同容量的需求：若 cap 已存在则执行 Integer::sum 求和，否则存入新值 cnt
                    } catch (NumberFormatException e) {
                        // 解析失败跳过
                    }
                }
            }
        }

        return demand;
    }

    /**
     * 解析餐桌配置描述字符串为容量 - 数量映射
     *
     * 功能说明：
     * 1. 按逗号分割配置描述，逐项解析"X人桌 xN"格式
     * 2. 提取容量数字（去除"人桌"前缀）与数量数字（去除"x"前缀）
     * 3. 将解析结果存入映射，解析失败项自动跳过
     *
     * @param configDesc 餐桌配置描述字符串（如"2人桌 x1, 4人桌 x2"）
     * @return 容量 - 数量映射，键为容量字符串，值为对应桌数
     *
     * 容错处理：
     * - 输入为空时返回空映射
     * - 格式错误或数字解析失败时忽略该项，不影响其他项解析
     */
    private Map<String, Integer> parseTableConfig(String configDesc) {
        Map<String, Integer> config = new HashMap<>();
        if (configDesc == null || configDesc.isEmpty()) return config;

        String[] parts = configDesc.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("人桌") && part.contains("x")) {
                String[] segs = part.split("x");
                if (segs.length == 2) {
                    String cap = segs[0].replaceAll("[^0-9]", "");
                    String qty = segs[1].trim();
                    if (!cap.isEmpty() && !qty.isEmpty()) {
                        try {
                            config.put(cap, Integer.parseInt(qty));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return config;
    }

    /**
     * 解析队列类型对应的餐桌容量
     *
     * 功能说明：
     * 将队列类型标识映射为对应的餐桌容量数值：
     * - "2_SEAT" → 2人桌
     * - "4_SEAT" → 4人桌
     * - "6_SEAT" → 6人桌
     * - 未知类型 → 默认返回2人桌
     *
     * @param queueType 队列类型标识字符串
     * @return 对应的餐桌容量数值
     */
    private int parseCapacityFromQueueType(String queueType) {
        return switch (queueType) {
            case "2_SEAT" -> 2;
            case "4_SEAT" -> 4;
            case "6_SEAT" -> 6;
            default -> 2;
        };
    }

    /**
     * 按队列类型分配等待顾客
     *
     * 功能说明：
     * 1. 获取指定队列类型的顾客组队列及对应餐桌容量
     * 2. 查询当前预约需求，用于后续资源预留校验
     * 3. 遍历队列中的顾客组，优先检查合并桌分配是否会占用预约所需资源
     * 4. 调用合并策略查找合适餐桌，执行普通分配或合并分配
     * 5. 分配成功后：删除数据库队列记录、移除内存队列、重排位置序号
     * 6. 发布队列变更事件并刷新缓存，确保界面即时更新
     *
     * @param queueType 队列类型标识（"2_SEAT"/"4_SEAT"/"6_SEAT"）
     *
     * 业务规则：
     * - 每次循环仅分配一个顾客组，避免并发状态不一致
     * - 合并桌分配前校验预约需求，优先保障预约顾客资源
     * - 分配失败时记录日志并继续处理下一个顾客组
     */
    @Transactional(rollbackFor = Exception.class)
    private void assignWaitingCustomersByQueueType(String queueType) {
        // 【DEBUG】方法入口日志
        System.out.println(" [DEBUG] assignWaitingCustomersByQueueType 开始执行");
        System.out.println("   queueType: " + queueType);
        System.out.println("   capacity: " + parseCapacityFromQueueType(queueType));

        Queue<CustomerGroup> queue = getQueueByType(queueType);
        int capacity = parseCapacityFromQueueType(queueType);

        System.out.println("   当前队列大小: " + queue.size());

        if (queue.isEmpty()) {
            System.out.println("    队列为空，跳过分配");
            return;
        }

        // 【新增】在分配前获取预约需求，用于后续检查
        Map<Integer, Integer> reservedDemand = getReservedDemandByCapacity();
        System.out.println("    当前预约需求: " + reservedDemand);

        Iterator<CustomerGroup> iterator = queue.iterator();
        int processedCount = 0;

        while (iterator.hasNext()) {
            CustomerGroup group = iterator.next();
            processedCount++;

            // 【DEBUG】遍历每个顾客组
//            System.out.println("\n [DEBUG] 处理队列第 " + processedCount + " 个顾客组:");
//            System.out.println("   groupId: " + group.getGroup_id());
//            System.out.println("   callNumber: " + group.getCallNumber());
//            System.out.println("   groupSize: " + group.getGroupSize());
//            System.out.println("   isAssigned: " + group.isAssigned());
//            System.out.println("   position: " + group.getPosition());

            int groupSize = group.getGroupSize();

            // 【DEBUG】尝试分配前日志
//            System.out.println("    尝试分配餐桌 (容量要求: " + capacity + "人，实际人数: " + groupSize + ")");


            // 检查合并桌分配是否会占用预约所需资源
            boolean needsMerge = (groupSize >= 5 && groupSize <= 8) ||
                    (groupSize >= 9 && groupSize <= 12);

            if (needsMerge) {
                // 计算合并所需的桌子容量和数量
                int requiredCapacity = (groupSize >= 5 && groupSize <= 8) ? 4 : 6;
                int requiredTables = 2;  // 合并需要2张桌

                // 检查该容量的桌子是否有预约需求
                int availableForReservation = reservedDemand.getOrDefault(requiredCapacity, 0);

                if (availableForReservation >= requiredTables) {
                    System.out.println("⏭ 容量" + requiredCapacity + "人桌有预约需求（需要" +
                            availableForReservation + "张），跳过排队顾客 #" + group.getCallNumber());
                    continue;  // 跳过这个顾客组，保留在队列中
                }
                System.out.println(" 合并桌检查通过: 预约需求=" + availableForReservation +
                        ", 需要=" + requiredTables + "张");
            }

            //  使用合并策略查找合适的空闲餐桌
            Tables table = tryAssignWithMergeStrategy(group, capacity, groupSize);

            if (table != null) {
                System.out.println("    找到可用餐桌: #" + table.getDisplayId() +
                        " (容量:" + table.getCapacity() + "人，类型:" + table.getTableType() + ")");

                try {
                    boolean isMergedTable = (table.getTableType() == Tables.TableType.MERGED
                            && table.getMergedWith() != null);

                    System.out.println("    isMergedTable: " + isMergedTable);

                    if (!isMergedTable) {
//                        System.out.println("    执行普通餐桌分配: processTableAssignment");
                        processTableAssignment(group, table);
//                        System.out.println("    普通餐桌分配完成: #" + table.getDisplayId());
                    } else {
                        System.out.println("    合并桌分配已在 mergeAndAssignTables 中完成，跳过 processTableAssignment");
                    }

                    // 【DEBUG】删除数据库队列记录
//                    System.out.println("    从数据库删除队列记录: groupId=" + group.getGroup_id() +
//                            ", queueType=" + queueType);
                    int deleted = queueMapper.removeFromQueue(group.getGroup_id(), queueType);
                    if (deleted == 0) {
//                        System.err.println("    [WARN] 数据库队列记录删除失败: groupId=" +
//                                group.getGroup_id() + ", queueType=" + queueType);
                    } else {
//                        System.out.println("    数据库队列记录删除成功，影响行数: " + deleted);
                    }

                    // 【DEBUG】从内存队列移除
//                    System.out.println("    从内存队列移除顾客组: " + group.getGroup_id());
                    iterator.remove();
//                    System.out.println("    内存队列移除成功");

                    // 【DEBUG】重排队列位置
//                    System.out.println("    重排队列位置: queueType=" + queueType);
                    queueMapper.updateQueuePositions(queueType);
//                    System.out.println("    队列重排完成");

                    // 【DEBUG】发布事件刷新 UI
//                    System.out.println("   发布队列变更事件: QueueChangedEvent.of(" + queueType + ")");

                    //  使用事务同步机制，确保数据完全落盘后再触发一次 UI 全量刷新
                    refreshTableCache();
                    eventPublisher.publishEvent(QueueChangedEvent.fullRefresh(this));
//                    System.out.println(" 已发布排队分配全量刷新事件");

                    eventPublisher.publishEvent(QueueChangedEvent.of(this, queueType));
//                    System.out.println("    事件发布完成");

                    //  每次只分配一个，避免状态不一致
//                    System.out.println("\n [DEBUG] 本次分配完成，跳出循环（单次只分配一个）");
                    break;

                } catch (SQLException e) {
                    System.err.println("    [ERROR] 分配餐桌失败 - SQL异常");
                    System.err.println("      顾客组#" + group.getCallNumber() +
                            " | 队列:" + queueType +
                            " | 错误:" + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("分配餐桌异常", e);
                } catch (Exception e) {
                    System.err.println("    [ERROR] 分配餐桌失败 - 系统异常");
                    System.err.println("      顾客组#" + group.getCallNumber() +
                            " | 类型:" + e.getClass().getSimpleName() +
                            " | 错误:" + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            } else {
                // 【DEBUG】未找到可用餐桌
                System.out.println("    未找到合适的空闲餐桌，继续检查下一个顾客组");
            }
        }

        // 【DEBUG】方法结束日志
        System.out.println("\n [DEBUG] assignWaitingCustomersByQueueType 执行完毕");
        System.out.println("   已处理顾客组数量: " + processedCount);
        System.out.println("   剩余队列大小: " + queue.size());
        System.out.println("========================================\n");
    }

    /**
     * 尝试分配餐桌（支持合并桌策略）
     *
     * 功能说明：
     * 1. 查询当前各容量餐桌的预约需求，用于资源预留校验
     * 2. 5-8人顾客组：优先尝试合并2张相邻4人桌，若4人桌预约需求≥2则跳过
     * 3. 9-12人顾客组：优先尝试合并2张相邻6人桌，若6人桌预约需求≥2则跳过
     * 4. 其他人数：执行普通单桌分配，匹配容量完全相同的空闲主桌
     * 5. 合并分配成功时返回主桌对象，失败时降级为普通分配
     *
     * @param group 待分配的顾客组对象
     * @param capacity 目标餐桌容量
     * @param groupSize 顾客组实际人数
     * @return 分配成功的餐桌对象；无合适餐桌时返回 null
     *
     * 分配优先级：
     * - 合并桌策略优先于普通单桌，提升大桌资源利用率
     * - 预约需求校验优先于排队分配，保障预约顾客权益
     */
    private Tables tryAssignWithMergeStrategy(CustomerGroup group, int capacity, int groupSize) {
        // 获取预约需求（用于合并检查）
        Map<Integer, Integer> reservedDemand = getReservedDemandByCapacity();

        // ── 策略 1: 5-8 人 → 尝试合并 2 张相邻 4 人桌 ──
        if (groupSize >= 5 && groupSize <= 8) {
            // 【核心修复】检查4人桌是否有预约需求
            int required4SeatTables = reservedDemand.getOrDefault(4, 0);
            if (required4SeatTables >= 2) {
                System.out.println(" 4人桌有预约需求（需要" + required4SeatTables + "张），跳过合并4人桌");
                // 不尝试合并，继续检查其他策略
            } else {
                List<Tables> mergedPair = findAdjacentVacantTables(4);
                if (mergedPair != null && mergedPair.size() == 2) {
                    List<Tables> result = mergeAndAssignTables(group, mergedPair.get(0), mergedPair.get(1));
                    return result != null && !result.isEmpty() ? result.get(0) : null;
                }
            }
        }

        // ── 策略 2: 9-12 人 → 尝试合并 2 张相邻 6 人桌 ──
        if (groupSize >= 9 && groupSize <= 12) {
            // 检查6人桌是否有预约需求
            int required6SeatTables = reservedDemand.getOrDefault(6, 0);
            if (required6SeatTables >= 2) {
                System.out.println("6人桌有预约需求（需要" + required6SeatTables + "张），跳过合并6人桌");
            } else {
                List<Tables> mergedPair = findAdjacentVacantTables(6);
                if (mergedPair != null && mergedPair.size() == 2) {
                    List<Tables> result = mergeAndAssignTables(group, mergedPair.get(0), mergedPair.get(1));
                    return result != null && !result.isEmpty() ? result.get(0) : null;
                }
            }
        }

        // ── 策略 3: 普通单桌分配（原有逻辑）─
        return findVacantTableByCapacity(capacity, groupSize);
    }

    /**
     * 查找相邻的空闲餐桌对
     *
     * 功能说明：
     * 1. 查询指定容量的空闲主桌列表
     * 2. 在内存中遍历列表，检查相邻餐桌是否满足物理相邻规则
     * 3. 返回第一对满足条件的相邻餐桌；无匹配时返回 null
     *
     * @param capacity 目标餐桌容量
     * @return 相邻空闲餐桌列表（固定2张）；无匹配时返回 null
     *
     * 相邻规则：
     * - 两张餐桌容量相同、状态均为空闲、类型为主桌
     * - 显示编号数字部分连续且位于同一行（如7与8、10与11）
     */
    private List<Tables> findAdjacentVacantTables(int capacity) {
        List<Tables> available = tablesMapper.findAvailableTables(capacity, "MAIN");
        if (available == null || available.size() < 2) {
            return null;
        }

        // 在内存中查找相邻对（避免复杂 SQL）
        for (int i = 0; i < available.size() - 1; i++) {
            Tables t1 = available.get(i);
            Tables t2 = available.get(i + 1);

            if (isAdjacent(t1, t2, capacity)) {
                return Arrays.asList(t1, t2);
            }
        }
        return null;
    }

    /**
     * 查找指定容量的空闲餐桌
     *
     * 功能说明：
     * 从内存缓存中筛选满足以下条件的餐桌：
     * 1. 状态为空闲（VACANT）且类型为主桌（MAIN）
     * 2. 容量与需求完全匹配
     * 3. 若容量为6人桌，顾客组人数需≥4人
     * 4. 顾客组人数不超过餐桌容量
     * 5. 返回编号最小的餐桌（按baseId升序）
     *
     * @param requiredCapacity 需求餐桌容量
     * @param groupSize 顾客组实际人数
     * @return 匹配的空闲餐桌对象；无匹配时返回 null
     *
     * 业务规则：
     * - 3人及以下顾客组不可分配6人桌，避免资源浪费
     * - 优先分配编号小的餐桌，保持界面展示顺序一致
     */
    private Tables findVacantTableByCapacity(int requiredCapacity, int groupSize) {
        return tableMap.values().stream()
                .filter(t -> t.getStatus() == Tables.TableStatus.VACANT)
                .filter(t -> t.getTableType() == Tables.TableType.MAIN) // 只考虑主桌
                .filter(t -> t.getCapacity() == requiredCapacity)       // 容量完全匹配
                .filter(t -> !(t.getCapacity() == 6 && groupSize < 4))  // 3人以下不坐6人桌
                .filter(t -> groupSize <= t.getCapacity())  //  新增：确保人数不超过容量
                .min(Comparator.comparingInt(Tables::getBaseId))        // 优先编号小的
                .orElse(null);
    }

    /**
     * 合并两张餐桌并分配给顾客组
     *
     * 功能说明：
     * 1. 计算座位分配：优先填满编号较小的餐桌，剩余人数分配给另一张
     * 2. 更新数据库：将两张餐桌标记为合并类型、占用状态，互相引用显示编号
     * 3. 更新顾客组：标记为已分配，关联主桌ID
     * 4. 同步内存缓存：更新两张餐桌及顾客组对象状态
     * 5. 累加当日顾客总数：用于经营统计
     * 6. 显式更新传入对象属性：确保调用方获取最新状态，避免数据过期
     *
     * @param group 待分配的顾客组对象
     * @param table1 第一张待合并餐桌
     * @param table2 第二张待合并餐桌
     * @return 合并成功后的餐桌列表（主桌在前，伙伴桌在后）；操作失败时返回 null
     */
    private List<Tables> mergeAndAssignTables(CustomerGroup group, Tables table1, Tables table2) {
        try {
            // 1. 计算座位分配（优先填满编号较小的桌）
            int seats1 = Math.min(group.getGroupSize(), table1.getCapacity());
            int seats2 = group.getGroupSize() - seats1;

            // 2. 更新数据库：标记两张桌为合并状态
            int updated1 = tablesMapper.updateTableToMergedOccupied(
                    table1.getTableId(),
                    table2.getDisplayId(),  // table1 指向 table2
                    group.getGroup_id(),
                    seats1
            );
            int updated2 = tablesMapper.updatePartnerTableToMergedOccupied(
                    table2.getTableId(),
                    table1.getDisplayId(),  // table2 指向 table1
                    group.getGroup_id(),
                    seats2
            );

            if (updated1 == 0 || updated2 == 0) {
                throw new RuntimeException("更新合并餐桌状态失败");
            }

            // 3. 更新顾客组分配状态（关联到主桌）
            int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                    group.getGroup_id(),
                    table1.getTableId(),  // 关联到主桌
                    true,
                    false
            );
            if (groupUpdated == 0) {
                throw new RuntimeException("更新顾客组失败");
            }

            // 4. 同步更新内存缓存
            syncMergedTablesToCache(table1, table2, group, seats1, seats2);

            // 5. 更新顾客组引用
            group.setAssigned(true);
            group.setTableId(table1.getTableId());

            // 累加當日顧客總數
            businessStatusMapper.incrementDailyTotalCustomers(
                    group.getGroupSize(), LocalDate.now());

            // 【核心修复】确保返回的对象属性正确反映合并状态
            // 注意：虽然 syncMergedTablesToCache 已更新缓存，但传入的 table1/table2 对象
            // 本身属性未变，返回前需显式更新，避免调用方获取到过期数据
            table1.setTableType(Tables.TableType.MERGED);
            table1.setMergedWith(table2.getDisplayId());
            table1.setStatus(Tables.TableStatus.OCCUPIED);
            table1.setCurrentGroupId(group.getGroup_id());
            table1.setCurrentGroup(group);
            table1.setActualSeats(seats1);

            table2.setTableType(Tables.TableType.MERGED);
            table2.setMergedWith(table1.getDisplayId());
            table2.setStatus(Tables.TableStatus.OCCUPIED);
            table2.setCurrentGroupId(group.getGroup_id());
            table2.setCurrentGroup(group);
            table2.setActualSeats(seats2);

            System.out.println(" 餐桌 #" + table1.getDisplayId() + " + #" + table2.getDisplayId()
                    + " 已合并，分配给顾客组 #" + group.getCallNumber()
                    + " (" + group.getGroupSize() + "人)");

            // 【修改】返回两张桌的列表（主桌在前，伙伴桌在后）
            return Arrays.asList(table1, table2);

        } catch (Exception e) {
            System.err.println(" 合并餐桌分配失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 执行餐桌拆分操作
     *
     * 功能说明：
     * 1. 校验餐桌编号有效性及存在性
     * 2. 验证餐桌是否符合拆分条件（容量2或4人、空闲状态、未拆分过）
     * 3. 更新主桌状态为拆分中（SPLITTING）
     * 4. 创建两个子桌对象：容量减半，后缀分别为"a"和"b"
     * 5. 保存子桌到数据库并回填主键
     * 6. 同步更新内存缓存：主桌标记拆分，子桌加入缓存
     *
     * @param displayId 待拆分的主桌显示编号
     * @return 拆分成功后生成的两个子桌对象列表
     *
     * 异常处理：
     * - 参数无效、餐桌不存在或不符合拆分条件时抛出相应异常
     * - 数据库操作失败时抛出 RuntimeException
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Tables> splitTable(String displayId) {
        // 1. 驗證輸入
        if (displayId == null || displayId.trim().isEmpty()) {
            throw new IllegalArgumentException("餐桌編號不能為空");
        }

        // 2. 獲取主桌（先查數據庫確保最新狀態）
        Tables mainTable = tablesMapper.findByDisplayId(displayId);
        if (mainTable == null) {
            throw new IllegalArgumentException("餐桌 #" + displayId + " 不存在");
        }

        // 3. 業務規則驗證
        validateTableCanBeSplit(mainTable);

        // 4. 更新主桌狀態為 SPLITTING
        int updated = tablesMapper.updateMainTableToSplitting(mainTable.getTableId());
        if (updated == 0) {
            throw new RuntimeException("更新主桌狀態失敗");
        }

        // 5. 創建兩個子桌對象
        int subCapacity = mainTable.getCapacity() / 2;  // 2→1, 4→2
        Tables subTableA = createSubTable(mainTable, "a", subCapacity);
        Tables subTableB = createSubTable(mainTable, "b", subCapacity);

        // 6. 保存子桌到數據庫（MyBatis 會回填自增主鍵）
        if (tablesMapper.saveSubTable(subTableA) == 0 ||
                tablesMapper.saveSubTable(subTableB) == 0) {
            throw new RuntimeException("保存子桌失敗");
        }

        // 7.  事務提交後，同步更新內存緩存
        syncMemoryAfterSplit(mainTable, subTableA, subTableB);

        System.out.println(" 餐桌 #" + displayId + " 拆分成功: " +
                subTableA.getDisplayId() + ", " + subTableB.getDisplayId());

        return Arrays.asList(subTableA, subTableB);
    }

    /**
     * 验证餐桌是否符合拆分条件
     *
     * 功能说明：
     * 1. 校验餐桌不是子桌（子桌不可再次拆分）
     * 2. 校验餐桌容量为2人或4人（仅支持这两种容量拆分）
     * 3. 校验餐桌状态为空闲（仅空闲桌可拆分）
     * 4. 校验餐桌未处于拆分状态（避免重复拆分）
     *
     * @param table 待验证的餐桌对象
     *
     * 异常处理：
     * - 任一条件不满足时抛出 IllegalStateException，提示具体冲突原因
     */
    private void validateTableCanBeSplit(Tables table) {
        // 子桌不能被再次拆分
        if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
            throw new IllegalStateException("子桌不能被拆分");
        }
        // 只能拆分 2 人或 4 人桌
        if (table.getCapacity() != 2 && table.getCapacity() != 4) {
            throw new IllegalStateException("只能拆分 2 人或 4 人桌，當前容量: " + table.getCapacity());
        }
        // 只能拆分空閒狀態的餐桌
        if (table.getStatus() != Tables.TableStatus.VACANT) {
            throw new IllegalStateException("只能拆分空閒狀態的餐桌，當前狀態: " + table.getStatus().getDisplayName());
        }
        // 不能重複拆分
        if (table.isSplit()) {
            throw new IllegalStateException("餐桌 #" + table.getDisplayId() + " 已經處於拆分狀態");
        }
    }

    /**
     * 创建子桌对象
     *
     * 功能说明：
     * 1. 初始化子桌基础属性：继承主桌baseId，容量减半，显示编号添加后缀
     * 2. 设置子桌专属字段：类型为SUBTABLE，关联主桌ID，后缀标识
     * 3. 初始化状态字段：空闲状态、无顾客关联、实际入座数为0
     *
     * @param mainTable 所属主桌对象
     * @param suffix 子桌后缀标识（"a"或"b"）
     * @param capacity 子桌容量（主桌容量的一半）
     * @return 初始化完成的子桌对象，待持久化到数据库
     *
     * 注意事项：
     * - tableId 设为0，保存后由数据库自增主键回填
     * - physicalCapacity 与 capacity 保持一致，确保容量计算准确
     */
    private Tables createSubTable(Tables mainTable, String suffix, int capacity) {
        Tables subTable = new Tables(mainTable.getBaseId(), capacity,
                mainTable.getDisplayId() + suffix);
        subTable.setTableId(0);  // 新記錄，主鍵為 0，保存後 MyBatis 會回填
        subTable.setPhysicalCapacity(capacity);
        subTable.setStatus(Tables.TableStatus.VACANT);
        subTable.setTableType(Tables.TableType.SUBTABLE);
        subTable.setSplit(false);
        subTable.setSubTableSuffix(suffix);
        subTable.setMainTableId(mainTable.getTableId());
        subTable.setActualSeats(0);
        subTable.setCurrentGroupId(null);
        return subTable;
    }

    /**
     * 拆分餐桌后同步更新内存缓存
     *
     * 功能说明：
     * 1. 更新主桌缓存状态：设为拆分中（SPLITTING），标记拆分标志，清空关联顾客组
     * 2. 将两个新生成的子桌对象加入餐桌缓存
     * 3. 可选刷新全局缓存，确保后续查询获取最新数据
     *
     * @param mainTable 拆分后的主桌对象
     * @param subA 第一个子桌对象
     * @param subB 第二个子桌对象
     *
     * 执行时机：
     * - 仅在数据库拆分操作成功提交后调用
     * - 确保内存缓存与持久化数据实时一致
     */
    private void syncMemoryAfterSplit(Tables mainTable, Tables subA, Tables subB) {
        // 1. 更新主桌在緩存中的狀態
        Tables cachedMain = tableMap.get(mainTable.getDisplayId());
        if (cachedMain != null) {
            cachedMain.setStatus(Tables.TableStatus.SPLITTING);
            cachedMain.setSplit(true);
            cachedMain.setCurrentGroupId(null);
        }

        // 2. 添加子桌到緩存
        tableMap.put(subA.getDisplayId(), subA);
        tableMap.put(subB.getDisplayId(), subB);

        // 3. 可選：刷新全局緩存確保一致性
        refreshTableCache();
    }


    /**
     * 对餐桌列表进行显示排序
     *
     * 功能说明：
     * 1. 分离主桌与子桌：根据 subTableSuffix 字段判断是否为子桌
     * 2. 主桌排序：按 baseId 升序排列（1→2→3→...）
     * 3. 子桌排序：先按所属主桌 baseId 升序，再按后缀字母顺序（"a"→"b"）
     * 4. 合并结果：主桌列表在前，子桌列表在后
     *
     * @param tables 待排序的餐桌列表
     * @return 排序后的新列表，符合界面展示顺序要求
     *
     * 排序规则：
     * - 主桌优先显示，保持编号连续
     * - 子桌按归属主桌分组，同组内按后缀排序
     */
    private List<Tables> sortTablesForDisplay(List<Tables> tables) {
        if (tables == null || tables.isEmpty()) {
            return tables;
        }

        // 1. 分離主桌與子桌
        List<Tables> mainTables = new ArrayList<>();
        List<Tables> subTables = new ArrayList<>();

        for (Tables table : tables) {
            // 判斷是否為子桌：有子桌後綴（a/b）
            if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
                subTables.add(table);
            } else {
                mainTables.add(table);
            }
        }

        // 2. 主桌按 baseId 升序排序（1→2→3→...→15）
        mainTables.sort(Comparator.comparingInt(Tables::getBaseId));

        // 3. 子桌排序：先按主桌baseId，再按後綴字母順序
        subTables.sort((t1, t2) -> {
            // 獲取子桌所屬主桌的 baseId
            int mainBaseId1 = getMainBaseIdForSubTable(t1, tables);
            int mainBaseId2 = getMainBaseIdForSubTable(t2, tables);

            // 3.1 先按主桌 baseId 升序
            int baseIdComparison = Integer.compare(mainBaseId1, mainBaseId2);
            if (baseIdComparison != 0) {
                return baseIdComparison;
            }

            // 3.2 主桌相同，按後綴字母順序（"a" < "b"）
            String suffix1 = t1.getSubTableSuffix() != null ? t1.getSubTableSuffix() : "";
            String suffix2 = t2.getSubTableSuffix() != null ? t2.getSubTableSuffix() : "";
            return suffix1.compareTo(suffix2);
        });

        // 4. 合併：主桌在前，子桌在後
        List<Tables> orderedTables = new ArrayList<>(mainTables);
        orderedTables.addAll(subTables);

        return orderedTables;
    }

    /**
     * 获取子桌所属主桌的 baseId
     *
     * 功能说明：
     * 1. 若子桌的 mainTableId 为空，直接返回自身 baseId 作为兜底
     * 2. 遍历餐桌列表查找匹配的主桌对象，返回其 baseId
     * 3. 若未找到主桌，返回子桌自身 baseId 作为容错处理
     *
     * @param subTable 子桌对象
     * @param allTables 包含所有餐桌的列表，用于查找主桌
     * @return 子桌所属主桌的 baseId；查找失败时返回子桌自身 baseId
     *
     * 应用场景：
     * - 子桌排序时确定归属主桌的优先级
     * - 界面展示时关联子桌与主桌的层级关系
     */
    private int getMainBaseIdForSubTable(Tables subTable, List<Tables> allTables) {
        if (subTable.getMainTableId() == null) {
            return subTable.getBaseId();
        }

        // 在列表中查找對應的主桌
        for (Tables table : allTables) {
            if (table.getTableId() == subTable.getMainTableId()) {
                return table.getBaseId();
            }
        }

        // 找不到主桌時兜底
        return subTable.getBaseId();
    }

    /**
     * 根据主桌ID查询关联的子桌列表
     *
     * @param mainTableId 主桌数据库主键
     * @return 关联的子桌列表；无子桌时返回空列表
     *
     * 数据来源：
     * - 直接查询数据库 tables 表，通过 main_table_id 字段关联
     * - 适用于拆分/合并操作时校验子桌状态
     */
    @Transactional(readOnly = true)
    public List<Tables> getSubTablesByMainTableId(int mainTableId) {
        return tablesMapper.findSubTablesByMainId(mainTableId);
    }

    /**
     * 执行餐桌合并操作，将拆分后的子桌恢复为主桌
     *
     * 功能说明：
     * 1. 校验主桌编号有效性及存在性
     * 2. 验证主桌当前处于拆分状态且所有子桌均为空闲
     * 3. 收集子桌显示编号用于日志记录
     * 4. 批量删除子桌数据库记录
     * 5. 恢复主桌状态为空闲、取消拆分标记
     * 6. 同步更新内存缓存，移除子桌并重置主桌属性
     *
     * @param mainTableDisplayId 主桌显示编号
     * @return 合并成功后的主桌对象
     *
     * 异常处理：
     * - 参数无效、主桌不存在、非拆分状态或子桌非空闲时抛出相应异常
     */
    @Transactional(rollbackFor = Exception.class)
    public Tables recombineTables(String mainTableDisplayId) {
        // 1. 驗證輸入
        if (mainTableDisplayId == null || mainTableDisplayId.trim().isEmpty()) {
            throw new IllegalArgumentException("主桌編號不能為空");
        }

        // 2. 查詢主桌
        Tables mainTable = tablesMapper.findByDisplayId(mainTableDisplayId.trim());
        if (mainTable == null) {
            throw new IllegalArgumentException("主桌 #" + mainTableDisplayId + " 不存在");
        }

        // 3. 驗證主桌是否處於拆分狀態
        if (!mainTable.isSplit()) {
            throw new IllegalStateException("餐桌 #" + mainTableDisplayId + " 未被拆分，無法執行合併操作");
        }

        // 4. 查詢所有關聯子桌
        List<Tables> subTables = tablesMapper.findSubTablesByMainId(mainTable.getTableId());
        if (subTables == null || subTables.isEmpty()) {
            throw new IllegalStateException("未找到餐桌 #" + mainTableDisplayId + " 的子桌");
        }

        // 5.  關鍵驗證：所有子桌必須為空閒狀態
        for (Tables subTable : subTables) {
            if (subTable.getStatus() != Tables.TableStatus.VACANT) {
                throw new IllegalStateException("子桌 #" + subTable.getDisplayId() +
                        " 必須處於空閒狀態才能合併，當前狀態：" +
                        subTable.getStatus().getDisplayName());
            }
        }

        // 6.  收集子桌顯示ID（用於日誌，在刪除前收集！）
        List<String> deletedDisplayIds = subTables.stream()
                .map(Tables::getDisplayId)
                .collect(Collectors.toList());

        System.out.println(" 驗證通過，開始合併餐桌 #" + mainTableDisplayId +
                "，子桌: " + String.join(", ", deletedDisplayIds));

        // 7. 執行數據庫操作
        List<Integer> subTableIds = subTables.stream()
                .map(Tables::getTableId)
                .collect(Collectors.toList());

        // 7.1 刪除子桌記錄
        int deleted = tablesMapper.deleteSubTables(subTableIds);

        // 7.2 恢復主桌狀態
        tablesMapper.restoreMainTableAfterRecombine(
                mainTable.getTableId(),
                Tables.TableStatus.VACANT.name(),
                false
        );

        // 8.  同步更新內存緩存
        syncMemoryAfterRecombine(mainTable, subTables);

        System.out.println(" 餐桌 #" + mainTableDisplayId + " 合併成功！");
        return mainTable;
    }

    /**
     * 合并餐桌后同步更新内存缓存
     *
     * 功能说明：
     * 1. 更新主桌缓存：状态设为空闲，取消拆分标记，清空关联顾客组
     * 2. 从内存缓存中移除所有已删除的子桌记录
     *
     * @param mainTable 合并后的主桌对象
     * @param removedSubTables 已被删除的子桌列表
     *
     * 执行时机：
     * - 仅在数据库合并操作成功后调用，确保内存与持久化数据一致
     */
    private void syncMemoryAfterRecombine(Tables mainTable, List<Tables> removedSubTables) {
        // 更新主桌缓存
        Tables cachedMain = tableMap.get(mainTable.getDisplayId());
        if (cachedMain != null) {
            cachedMain.setStatus(Tables.TableStatus.VACANT);
            cachedMain.setSplit(false);
            cachedMain.setCurrentGroupId(null);
        }
        // 移除子桌缓存
        for (Tables sub : removedSubTables) {
            tableMap.remove(sub.getDisplayId());
        }
    }

    /**
     * 将顾客组加入排队队列
     *
     * 功能说明：
     * 1. 根据顾客组人数解析目标队列类型（2/4/6人桌）
     * 2. 获取该队列下一个可用位置序号
     * 3. 插入排队记录到数据库
     * 4. 重排队列位置序号确保连续性
     *
     * @param group 待入队的顾客组对象
     *
     * 业务规则：
     * - 队列位置从1开始连续编号，新顾客默认排在队尾
     * - 位置计算与插入在同一事务内执行，保证数据一致性
     */
    @Transactional(rollbackFor = Exception.class)  // 声明事务边界
    public void enqueueGroup(CustomerGroup group) {
        // 1. 解析队列类型
        String queueType = resolveQueueType(group.getGroupSize());

        // 2. 获取下一个位置（MyBatis 自动使用当前事务连接）
        Integer position = queueMapper.getNextQueuePosition(queueType);
        if (position == null) {
            position = 1;  // 兜底
        }

        // 3. 插入队列记录
        int inserted = queueMapper.insertQueue(queueType, group.getGroup_id(), position);
        if (inserted == 0) {
            throw new RuntimeException("插入队列记录失败");
        }

        // 4. 重排队列位置（保证连续性）
        queueMapper.updateQueuePositions(queueType);

        System.out.println(" 顾客组 #" + group.getCallNumber() +
                " 已加入 " + queueType + " 队列，位置: " + position);
    }

    /**
     * 根据顾客组人数解析对应的队列类型
     *
     * 功能说明：
     * 将顾客人数映射到餐桌容量队列：
     * - 1-2人 → 2人桌队列（"2_SEAT"）
     * - 3-4人 → 4人桌队列（"4_SEAT"）
     * - 5-12人 → 6人桌队列（"6_SEAT"）
     *
     * @param groupSize 顾客组人数
     * @return 队列类型标识字符串
     *
     * 异常处理：
     * - 人数超过12人时抛出 IllegalArgumentException
     */
    private String resolveQueueType(int groupSize) {
        if (groupSize <= 2) {
            return "2_SEAT";
        } else if (groupSize <= 4) {
            return "4_SEAT";
        } else if (groupSize <= 12) {
            return "6_SEAT";
        } else {
            throw new IllegalArgumentException("不支持的顾客组人数: " + groupSize);
        }
    }

    //3.5. Spring 事件驅動架構 (ApplicationEventPublisher)
    //技術說明：使用 Spring 內置事件機制解耦 Service 層與 View 層，避免業務層直接持有 UI 組件引用。
    //通過 TransactionSynchronizationAdapter 確保事件只在事務成功提交後才發送。這防止了因事務回滾導致 UI 誤刷新。QueueChangeListener 捕獲事件後再透過 SwingUtilities.invokeLater 安全更新界面。
    //5.3. 事務同步與事件發布 (TransactionSynchronizationManager)
    //技術說明：利用 Spring 的事務同步管理器，註冊回調函數，確保事件僅在數據庫事務成功提交後才發布，防止事務回滾時觸發虛假 UI 更新。
    //這是企業級應用的標準實踐。如果直接 publishEvent，當後續代碼拋出異常觸發 rollback 時，UI 已經刷新了錯誤數據，導致狀態不一致。afterCommit 保證了「數據落盤」與「界面響應」的原子性。
    /**
     * 从排队队列中移除指定顾客组
     *
     * 功能说明：
     * 1. 校验顾客组存在性及未入座状态，防止数据不一致
     * 2. 从数据库排队记录表中删除该顾客组的排队信息
     * 3. 彻底删除顾客组主记录，完成数据清理
     * 4. 注册事务回调，在事务提交后同步内存队列并发布队列变更事件
     *
     * @param groupId 待移除的顾客组唯一标识
     * @param queueType 队列类型（"2_SEAT"/"4_SEAT"/"6_SEAT"）
     *
     * 异常处理：
     * - 顾客组不存在或已入座时抛出 IllegalStateException
     * - 数据库删除失败时抛出 IllegalStateException
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeFromQueue(int groupId, String queueType) {

        // 【步骤1】先查询 customer_groups 确认顾客组状态（关键！）
        CustomerGroup group = customerGroupMapper.findById(groupId);
        if (group == null) {
            throw new IllegalStateException("顾客组不存在: " + groupId);
        }

        // 【核心校验】已入座的顾客组不能从队列移除（数据不一致风险）
        if (group.isAssigned()) {
            throw new IllegalStateException(
                    "顾客组 #" + groupId + " 已入座餐桌，不能从队列移除！"
            );
        }

        // 【步骤2】从 queues 表删除排队记录
        int deleted = queueMapper.removeFromQueue(groupId, queueType);
        if (deleted == 0) {
            throw new IllegalStateException("顾客组不在 " + queueType + " 队列中: " + groupId);
        }


        // 【步骤3】【核心修复】删除 customer_groups 记录
        // 规则：只有未入座 + 已出队的顾客组才彻底删除
        customerGroupMapper.delete(groupId);
        System.out.println(" 已彻底删除顾客组 #" + groupId +
                "（queues + customer_groups）");

        // 【步骤4】注册事务回调（事务提交后再同步内存 + 发布事件）
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        //  内存同步：事务已提交，数据一致
                        syncQueueToMemory(groupId, queueType);

                        //  发布事件：通知监听器刷新UI
                        eventPublisher.publishEvent(
                                new QueueChangedEvent(RestaurantService.this, queueType)
                        );

                        System.out.println(" 队列移除完成 + 内存同步 + 事件发布: groupId=" + groupId);
                    }
                }
        );
    }

    /**
     * 更新排队中顾客组的人数并调整队列
     *
     * 功能说明：
     * 1. 校验新人数范围（1-12）及顾客组未入座状态
     * 2. 从内存缓存获取权威顾客组对象，确保数据一致性
     * 3. 查询当前队列类型，根据新人数计算目标队列类型
     * 4. 从原队列移除记录，更新数据库顾客组人数
     * 5. 同步更新内存缓存对象，插入新队列并重排位置序号
     * 6. 注册事务回调，提交后同步内存队列并发布变更事件
     *
     * @param group 待更新的顾客组对象
     * @param newSize 更新后的顾客组人数
     *
     * 异常处理：
     * - 人数超限或顾客组已入座时抛出 IllegalArgumentException
     * - 顾客组不存在或不在队列中时抛出 IllegalStateException
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomerGroupSize(CustomerGroup group, int newSize) {
        // ===== 1. 基础验证 =====
        if (newSize <= 0 || newSize > 12) {
            throw new IllegalArgumentException("客戶數量必須在 1-12 之間");
        }
        if (group.isAssigned()) {
            throw new IllegalArgumentException("已入座顧客組不能修改人數");
        }

        // ===== 2. 【关键】从缓存获取权威对象引用 =====
        CustomerGroup cachedGroup = customerGroupMap.get(group.getGroup_id());
        if (cachedGroup == null) {
            CustomerGroup dbGroup = customerGroupMapper.findById(group.getGroup_id());
            if (dbGroup == null) {
                throw new IllegalStateException("顧客組不存在: " + group.getGroup_id());
            }
            customerGroupMap.put(dbGroup.getGroup_id(), dbGroup);
            cachedGroup = dbGroup;
            System.out.println(" 從數據庫重新加載顧客組到緩存: " + group.getGroup_id());
        }

        // 【核心】验证状态一致性（调试用）
        if (cachedGroup.getGroupSize() != group.getGroupSize()) {
            System.out.println(" 警告: 傳入對象與緩存對象狀態不一致，將以緩存為準");
        }

        // ===== 3. 查詢當前隊列類型 =====
        String currentQueueType = queueMapper.findQueueTypeByGroupId(cachedGroup.getGroup_id());
        if (currentQueueType == null) {
            throw new IllegalArgumentException("顧客組不在隊列中");
        }

        String newQueueType = resolveQueueType(newSize);

        // ===== 4. 從舊隊列移除 =====
        queueMapper.removeFromQueue(cachedGroup.getGroup_id(), currentQueueType);

        // ===== 5. 更新數據庫 =====
        int updated = customerGroupMapper.updateGroupSize(cachedGroup.getGroup_id(), newSize);
        if (updated == 0) {
            throw new RuntimeException("更新顧客組人數失敗：groupId=" + cachedGroup.getGroup_id());
        }

        // ===== 6. 【核心】直接更新缓存中的对象 =====
        cachedGroup.setGroupSize(newSize);
        System.out.println(" 內存緩存已同步: groupId=" + cachedGroup.getGroup_id() +
                ", newSize=" + newSize);

        // ===== 7.  可选：同步更新传入参数对象 =====
        if (group != cachedGroup) {
            group.setGroupSize(newSize);
        }

        // ===== 8. 插入新隊列 =====
        int position = queueMapper.getNextQueuePosition(newQueueType);
        queueMapper.insertQueue(newQueueType, cachedGroup.getGroup_id(), position);

        // ===== 9. 重排新隊列位置 =====
        queueMapper.updateQueuePositions(newQueueType);

        // ===== 10.  事务提交后同步内存队列 + 发布事件 =====
        // 【关键修复】创建 final 副本供匿名内部类使用
        //匿名內部類本質上是一個獨立的類實例，它「捕捉」外部變量時，實際上是拷貝了一份值（或引用）。為了保證拷貝的值不會被外部修改導致不一致，Java 強制要求：
        //「沒有名字的臨時類」，定義即實例化，常用於回調、事件監聽等場景
        final CustomerGroup finalCachedGroup = cachedGroup;
        final String finalQueueType = currentQueueType;
        final int finalNewSize = newSize;
        final String finalNewQueueType = newQueueType;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        //  使用 final 副本
                        syncQueueToMemory(finalCachedGroup.getGroup_id(), finalQueueType, finalNewSize);
                        eventPublisher.publishEvent(
                                new QueueChangedEvent(RestaurantService.this, finalNewQueueType)
                        );
                        System.out.println(" 隊列變更事件已發布: " + finalNewQueueType);
                    }
                }
        );

        System.out.println(" 顧客組 #" + cachedGroup.getCallNumber() +
                " 人數更新: " + cachedGroup.getGroupSize() +
                "，隊列: " + currentQueueType + " → " + newQueueType);
    }

    /**
     * 同步顾客组人数变更到内存队列
     *
     * 功能说明：
     * 1. 更新内存缓存中顾客组的人数信息
     * 2. 从原队列类型对应的队列中移除该顾客组
     * 3. 根据新人数重新计算目标队列类型
     * 4. 将顾客组添加到新队列末尾并重新排列所有位置序号
     *
     * @param groupId 顾客组唯一标识
     * @param oldQueueType 原队列类型（"2_SEAT"/"4_SEAT"/"6_SEAT"）
     * @param newSize 更新后的顾客组人数
     */
    private void syncQueueToMemory(int groupId, String oldQueueType, int newSize) {
        synchronized (queueLock) {
            // 1. 先更新 customerGroupMap 中的对象
            CustomerGroup cachedGroup = customerGroupMap.get(groupId);
            if (cachedGroup != null) {
                cachedGroup.setGroupSize(newSize);
                System.out.println(" customerGroupMap 已更新: groupId=" + groupId);
            }

            // 2. 从旧队列移除
            Queue<CustomerGroup> oldQueue = getQueueByType(oldQueueType);
            oldQueue.removeIf(g -> g.getGroup_id() == groupId);

            // 3. 重排旧队列位置
            repositionQueue(oldQueue);

            // 4. 【核心修复】无论队列类型是否改变，都要重新添加顾客组
            String newQueueType = resolveQueueType(newSize);
            Queue<CustomerGroup> targetQueue = getQueueByType(newQueueType);

            if (cachedGroup != null) {
                //  无论是否跨队列，都要添加回去
                cachedGroup.setPosition(targetQueue.size() + 1);
                targetQueue.add(cachedGroup);
                repositionQueue(targetQueue);

                System.out.println(" 内存队列已同步: groupId=" + groupId +
                        ", queueType=" + newQueueType +
                        ", position=" + cachedGroup.getPosition());
            }
        }
    }

    /**
     * 同步顾客组移除操作到内存队列
     *
     * 功能说明：
     * 1. 从指定队列类型对应的队列中移除指定顾客组
     * 2. 重排剩余顾客组的位置序号，确保队列连续性
     *
     * @param removedGroupId 待移除的顾客组唯一标识
     * @param queueType 队列类型（"2_SEAT"/"4_SEAT"/"6_SEAT"）
     */
    private void syncQueueToMemory(int removedGroupId, String queueType) {
        synchronized (queueLock) {
            Queue<CustomerGroup> targetQueue = getQueueByType(queueType);
            // 移除指定顾客
            targetQueue.removeIf(g -> g.getGroup_id() == removedGroupId);
            // 重排位置（保持连续性）
            repositionQueue(targetQueue);
        }
    }

    /**
     * 重排队列中顾客组的位置序号
     * 参数声明为Queue接口，明确该方法接收的是标准的先进先出排队容器
     * 功能说明：
     * 遍历队列中的所有顾客组，按当前顺序重新分配从 1 开始的连续位置编号，
     * 确保队列显示时序号连续无间隔。
     *
     * @param queue 待重排的顾客组队列
     */
    private void repositionQueue(Queue<CustomerGroup> queue) {
        List<CustomerGroup> list = new ArrayList<>(queue);//转换为ArrayList是因为Queue不支持安全遍历修改和顺序重建
        queue.clear();//清空原队列准备重建，此时List已完整保留元素的原始排队顺序
        int position = 1;// 声明位置计数器，用于为队列成员分配从1开始的连续序号
        for (CustomerGroup group : list) {// 遍历List快照，安全地为每个顾客组更新其排队位置字段
            group.setPosition(position++);
            queue.add(group);// 将更新后的顾客按新顺序重新入队，恢复队列的先进先出结构
        }
    }

    /**
     * 获取指定队列类型的快照副本
     *
     * 功能说明：
     * 返回指定队列类型当前状态的浅拷贝列表，供 UI 层安全遍历展示，
     * 避免外部操作影响内部队列数据一致性。
     * 外部用 List 提供數據快照（靈活讀取）
     * @param queueType 队列类型（"2_SEAT"/"4_SEAT"/"6_SEAT"）
     * @return 队列快照副本，按当前位置顺序排列
     */
    public List<CustomerGroup> getQueueSnapshot(String queueType) {
        synchronized (queueLock) {
            return new LinkedList<>(getQueueByType(queueType));
        }
    }

    /**
     * 根据队列类型获取对应的排队队列对象
     *定义一个私有方法，返回类型是「顾客组专用的排队队列」
     * Queue 表示「先进先出」的排队规则，<CustomerGroup> 限定队列里只能装 CustomerGroup 对象
     * 內部：Queue<CustomerGroup> 保證排隊規則正確
     * @param type 队列类型标识（"2_SEAT" / "4_SEAT" / "6_SEAT"）
     * @return 对应的 CustomerGroup 队列实例；类型未知时抛出异常
     */
    private Queue<CustomerGroup> getQueueByType(String type) {
        return switch (type) {
            case "2_SEAT" -> queue2Seat;
            case "4_SEAT" -> queue4Seat;
            case "6_SEAT" -> queue6Seat;
            default -> throw new IllegalArgumentException("未知队列类型: " + type);
        };
    }

    /**
     * 获取 2 人桌排队队列的副本
     * 返回类型同样是「顾客组队列」，确保调用方只能按顾客组类型处理数据
     * 功能说明：
     * 返回当前 2 人桌等待队列的浅拷贝，调用方遍历或展示时不会影响原始队列状态。
     *
     * @return 2 人桌队列副本，按叫号顺序排列
     *
     * 线程安全：
     * - 加锁读取确保并发环境下数据一致性
     * - 返回新 LinkedList 避免外部修改影响内部状态
     */
    public Queue<CustomerGroup> getQueue2Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue2Seat);
        }
    }

    /**
     * 获取 4 人桌排队队列的副本
     *
     * 功能说明：
     * 返回当前 4 人桌等待队列的浅拷贝，供界面安全展示或业务只读使用。
     *
     * @return 4 人桌队列副本，按叫号顺序排列
     *
     * 线程安全：
     * - 加锁读取确保并发环境下数据一致性
     * - 返回新链表隔离外部操作
     */
    public Queue<CustomerGroup> getQueue4Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue4Seat);
        }
    }

    /**
     * 获取 6 人桌排队队列的副本
     *
     * 功能说明：
     * 返回当前 6 人桌等待队列的浅拷贝，确保调用方无法直接修改内部队列结构。
     *
     * @return 6 人桌队列副本，按叫号顺序排列
     *
     * 线程安全：
     * - 同步块保护读取过程
     * - 副本返回实现读写分离
     */
    public Queue<CustomerGroup> getQueue6Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue6Seat);
        }
    }

    /**
     * 根据叫号查询顾客组信息
     * 注意：这里没有 <...> 泛型，因为返回的是单个实体，不是容器
     * @param callNumber 排队叫号（如 1, 2, 3...）
     * @return 对应的 CustomerGroup 对象；叫号不存在时返回 null
     *
     * 数据来源：
     * - 直接查询数据库 customer_groups 表，不经过内存缓存
     * - 适用于叫号输入后快速校验顾客组有效性
     */
    @Transactional(readOnly = true)
    public CustomerGroup findCustomerGroupByCallNumber(int callNumber) {
        return customerGroupMapper.findByCallNumber(callNumber);
    }

    /**
     * 根据顾客组 ID 查询其所在的队列类型
     * 注意：返回类型是 String，因为只是查询「属于哪个队列」，不是返回队列本身
     * @param groupId 顾客组唯一标识
     * @return 队列类型字符串（"2_SEAT" / "4_SEAT" / "6_SEAT"）；未排队时返回 null
     *
     * 应用场景：
     * - 顾客入座后从原队列移除时，确定应操作哪个队列
     * - 界面高亮显示顾客组当前排队位置
     */
    @Transactional(readOnly = true)
    public String getQueueTypeByGroupId(int groupId) {
        return queueMapper.findQueueTypeByGroupId(groupId);
    }

    /**
     * 验证餐桌编号格式
     *
     * @param tableNumber 餐桌编号（如 "7" 或 "7a"）
     * @return true=格式有效，false=格式无效
     */
    public boolean isValidTableNumberFormat(String tableNumber) {
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            return false;
        }

        String trimmed = tableNumber.trim();

        // 主桌格式：纯数字（如 "7"）
        if (trimmed.matches("\\d+")) {
            return true;
        }

        // 子桌格式：数字 + 单个字母后缀（如 "7a" 或 "7b"）
        if (trimmed.matches("\\d+[a-zA-Z]")) {
            return true;
        }

        return false;
    }

    /**
     * 判断餐桌是否为合并桌中的主桌
     *
     * 功能说明：
     * 1. 校验餐桌显示编号有效性，为空或不存在时返回 false
     * 2. 若餐桌非合并类型，直接返回 true（视为独立主桌）
     * 3. 若为合并桌，提取当前桌与伙伴桌的数字编号，编号较小者为主桌
     * 4. 伙伴桌不存在或关联字段为空时，默认当前桌为主桌
     *
     * @param displayId 餐桌显示编号（如 "7"、"8"、"7a"）
     * @return true=是主桌或非合并桌，可执行订单相关操作；false=是合并桌中的伙伴桌，需通过主桌操作
     */
    @Transactional(readOnly = true)
    public boolean isMainOrderTable(String displayId) {
        if (displayId == null || displayId.trim().isEmpty()) {
            return false;
        }

        Tables table = tablesMapper.findByDisplayId(displayId.trim());
        if (table == null) {
            return false;
        }

        // 不是合并桌 → 直接返回 true
        if (table.getTableType() != Tables.TableType.MERGED) {
            return true;
        }

        // 是合并桌 → 检查是否为主桌（编号较小）
        String partnerDisplayId = table.getMergedWith();
        if (partnerDisplayId == null || partnerDisplayId.isEmpty()) {
            return true;  // 无伙伴桌，视为有效
        }

        Tables partnerTable = tablesMapper.findByDisplayId(partnerDisplayId);
        if (partnerTable == null) {
            return true;  // 伙伴桌不存在，视为有效
        }

        // 提取数字部分比较（如 "7" vs "8"）
        int currentId = extractTableNumber(displayId);
        int partnerId = extractTableNumber(partnerDisplayId);

        // 编号较小的为主桌
        return currentId <= partnerId;
    }

    /**
     * 从餐桌显示编号中提取纯数字编号
     *
     * 功能说明：
     * 移除显示编号中的字母后缀与非数字字符（如 "7a" → "7"），解析为整数用于排序与比较。
     *
     * @param displayId 餐桌显示编号（支持带后缀格式）
     * @return 提取的数字编号；解析失败或输入为空时返回 0
     *
     * 应用场景：
     * - 合并桌主从判定：比较两张桌编号大小确定主桌
     * - 餐桌列表排序：按数字顺序而非字典序排列
     * - 连续桌号校验：判断多张桌是否相邻
     */
    private int extractTableNumber(String displayId) {
        if (displayId == null) {
            return 0;
        }
        try {
            // 移除字母后缀（如 "7a" → "7"）
            String numericPart = displayId.replaceAll("[^0-9]", "");
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 处理堂食订单结账流程
     *
     * 功能说明：
     * 1. 校验餐桌存在性、关联活跃订单及未结账状态
     * 2. 校验支付金额是否不小于订单总额
     * 3. 更新订单状态为已结账
     * 4. 记录当日营收：优先使用重单时间（reorder_time）确定统计日期，确保重单营收计入正确日期
     * 5. 记录季度销售统计，支持经营报表分析
     * 6. 删除订单明细记录，保持数据整洁
     * 7. 更新内存缓存中餐桌的订单状态，确保界面即时刷新
     *
     * @param tableNumber 餐桌显示编号
     * @param paymentAmount 顾客实际支付金额
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - changeAmount: double，找零金额
     *         - totalAmount: double，订单总额
     *         - revenueDate: Date，营收计入的日期
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processCheckout(String tableNumber, double paymentAmount) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 查询餐桌
            Tables table = tablesMapper.findByDisplayId(tableNumber);
            if (table == null) {
                result.put("success", false);
                result.put("message", "餐桌不存在：" + tableNumber);
                return result;
            }

            // 2. 查询活跃订单
            Integer orderId = orderMapper.findActiveOrderIdByTableId(table.getTableId());
            if (orderId == null) {
                result.put("success", false);
                result.put("message", "该餐桌没有活跃订单");
                return result;
            }

            // 3. 查询订单金额
            Order order = orderMapper.findById(orderId);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            // 4. 检查是否已结账
            if ("CHECKED_OUT".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "订单已结账");
                return result;
            }

            // 5. 检查支付金额
            double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
            if (paymentAmount < totalAmount) {
                result.put("success", false);
                result.put("message", "支付金额不足");
                return result;
            }

            // 6. 更新订单状态为已结账
            orderMapper.checkoutOrder(orderId);

            // 【步骤 7 修改】营收日期计算：优先使用 reorder_time
            LocalDate revenueDate;
            if (order.getReorderTime() != null) {
                //  有重单时间：按重单时间统计营收（确保重单营收计入正确日期）
                revenueDate = order.getReorderTime().toLocalDate();
                System.out.println(" 营收计入重单日期: " + revenueDate +
                        " (reorder_time: " + order.getReorderTime() + ")");
            } else {
                //  首次下单：按原始下单时间统计
                revenueDate = order.getOrderTime() != null ?
                        order.getOrderTime().toLocalDate() : LocalDate.now();
                System.out.println(" 营收计入下单日期: " + revenueDate);
            }

            orderMapper.updateDailyRevenue(totalAmount, java.sql.Date.valueOf(revenueDate));

            // 8. 记录季度销售
            String quarter = getQuarterFromDate(revenueDate);
            orderMapper.recordQuarterlySales(orderId, revenueDate.getYear(), quarter);

            // 9删除 order_items 明细
            orderItemMapper.deleteOrderItemsByOrderId(orderId);
          //  System.out.println(" 已删除订单明细：orderId=" + orderId);


            // 10. 更新餐桌订单状态（内存缓存）
            Tables memoryTable = tableMap.get(tableNumber);
            if (memoryTable != null) {
                memoryTable.setOrderStatus(Tables.OrderStatus.CHECKED_OUT);
            }

            // 11. 返回结果
            result.put("success", true);
            result.put("changeAmount", paymentAmount - totalAmount);
            result.put("totalAmount", totalAmount);
            result.put("revenueDate", java.sql.Date.valueOf(revenueDate));

//            System.out.println(" 堂食结账成功：餐桌" + tableNumber +
//                    ", 金额：" + totalAmount +
//                    ", 订单记录已删除");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "结账失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 根据预约号查询预付定金信息
     *
     * 功能说明：
     * 1. 校验预约号参数有效性，为空时返回空映射
     * 2. 查询数据库获取预付状态与金额等字段
     * 3. 类型安全转换：将数据库返回的 Number 类型金额统一转为 Double，确保视图层可直接使用
     *
     * @param reservationId 预约记录唯一标识
     * @return 包含预付信息的映射（键：is_prepaid/prepaid_amount 等）；查询无结果时返回空映射
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPrepaidInfoByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = reservationMapper.findPrepaidInfoByReservationId(reservationId);
        if (result != null && result.containsKey("prepaid_amount")) {
            Object amountObj = result.get("prepaid_amount");
            if (amountObj instanceof Number) {
                //  转为 Double 返回给 View
                result.put("prepaid_amount", ((Number) amountObj).doubleValue());
            }
        }
        return result;
    }

    /**
     * 处理堂食结账并记录准确营收
     *
     * 功能说明：
     * 1. 校验餐桌存在性、关联活跃订单及未结账状态
     * 2. 计算应付金额：菜品总额减去预付定金，校验顾客支付金额是否充足
     * 3. 更新订单状态为已结账，清理订单明细记录
     * 4. 记录当日营收：使用传入的 revenueAmount（取菜品总额与定金的最大值），确保财务统计准确
     * 5. 记录季度销售统计，支持经营报表分析
     * 6. 更新内存缓存中餐桌的订单状态，确保界面即时刷新
     *
     * @param tableNumber 餐桌显示编号
     * @param paymentAmount 顾客本次实际支付金额
     * @param revenueAmount 应记录的营收金额（业务规则：max(菜品总额, 预付定金)）
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，提示消息
     *         - changeAmount: double，找零金额
     *         - revenueAmount: double，实际记录的营收值
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processCheckoutWithRevenue(String tableNumber, double paymentAmount, double revenueAmount) {
        //Map<String, Object>：為了靈活承載多種不同類型的返回值，這在服務層與控制層交互、構建動態響應時非常常見。
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 查询餐桌
            Tables table = tablesMapper.findByDisplayId(tableNumber);
            if (table == null) {
                result.put("success", false);
                result.put("message", "餐桌不存在：" + tableNumber);
                return result;
            }

            // 2. 查询活跃订单
            Integer orderId = orderMapper.findActiveOrderIdByTableId(table.getTableId());
            if (orderId == null) {
                result.put("success", false);
                result.put("message", "该餐桌没有活跃订单");
                return result;
            }

            // 3. 查询订单详情
            Order order = orderMapper.findById(orderId);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            // 4. 检查是否已结账
            if ("CHECKED_OUT".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "订单已结账");
                return result;
            }

            // 5. 安全校验：验证支付金额是否足够
            double itemsTotal = order.getItemsTotal() != null ? order.getItemsTotal() : 0.0;
            double prepaidAmount = order.getPrepaidAmount() != null ? order.getPrepaidAmount() : 0.0;

            // 计算应付金额 = 菜品总额 - 定金（最小为 0）
            double payableAmount = itemsTotal - prepaidAmount;
            if (payableAmount < 0) payableAmount = 0;

            if (paymentAmount < payableAmount) {
                result.put("success", false);
                result.put("message", "支付金额不足，应付: " + String.format("%.2f", payableAmount) + " 元");
                return result;
            }

            // 6. 更新订单状态
            orderMapper.checkoutOrder(orderId);

            // 7. 【核心修复】更新当日营收 - 使用传入的 revenueAmount
            // revenueAmount = Math.max(itemsTotal, prepaidAmount)
            // 场景 1: 菜品 300, 定金 100 -> revenue=300 (记录实际消费额)
            // 场景 2: 菜品 80, 定金 100 -> revenue=100 (记录实际收入，不退多余定金)
            LocalDate revenueDate = order.getOrderTime() != null ?
                    order.getOrderTime().toLocalDate() : LocalDate.now();
            orderMapper.updateDailyRevenue(revenueAmount, java.sql.Date.valueOf(revenueDate));

            // 8. 记录季度销售统计
            String quarter = getQuarterFromDate(revenueDate);
            orderMapper.recordQuarterlySales(orderId, revenueDate.getYear(), quarter);

            // 9. 删除订单记录（结账后清理）
            orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // 10. 更新餐桌订单状态（内存缓存）
            Tables memoryTable = tableMap.get(tableNumber);
            if (memoryTable != null) {
                memoryTable.setOrderStatus(Tables.OrderStatus.CHECKED_OUT);
            }

            // 11. 返回结果
            result.put("success", true);
            result.put("changeAmount", paymentAmount - payableAmount);  // 找零
            result.put("revenueAmount", revenueAmount);                 // 返回记录到的营收金额

            System.out.println(" 堂食结账成功：餐桌 " + tableNumber +
                    ", 订单总额: " + itemsTotal +
                    ", 抵扣后实付: " + payableAmount +
                    ", 营收记录: " + revenueAmount);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "结账失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 根据日期计算所属季度
     *
     * 功能说明：
     * 将月份映射为季度标识（1-3 月→Q1，4-6 月→Q2，7-9 月→Q3，10-12 月→Q4），用于销售统计与报表分组。
     *
     * @param date 待计算的日期对象
     * @return 季度字符串（"Q1" / "Q2" / "Q3" / "Q4"）
     */
    private String getQuarterFromDate(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    /**
     * 执行顾客组换桌操作
     *
     * 功能说明：
     * 1. 刷新餐桌缓存，确保内存数据与数据库同步且顾客组对象已关联
     * 2. 从内存缓存获取源餐桌与目标餐桌对象，避免数据库查询缺失关联对象
     * 3. 校验业务规则：源桌需为占用主桌且无活跃订单，目标桌需为空闲主桌且容量足够
     * 4. 执行数据库更新：源桌状态设为准备中，目标桌状态设为占用并关联顾客组
     * 5. 更新顾客组关联餐桌 ID，同步内存缓存确保界面即时刷新
     *
     * @param fromDisplayId 源餐桌显示编号
     * @param toDisplayId 目标餐桌显示编号
     * @return true=换桌成功
     *
     * 异常处理：
     * - 参数校验失败或规则冲突时抛出 IllegalArgumentException / IllegalStateException
     * - 数据库更新失败时抛出 RuntimeException，事务自动回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean changeTable(String fromDisplayId, String toDisplayId) {
        // ===== 1. 強制刷新緩存，確保內存數據是最新的且已關聯 CustomerGroup =====
        refreshTableCache();

        // ===== 2. 【關鍵修改】直接從內存緩存獲取餐桌對象 =====
        // 不要從數據庫查，數據庫查出來的對象沒有 currentGroup 對象
        Tables fromTable = tableMap.get(fromDisplayId);
        Tables toTable = tableMap.get(toDisplayId);

        if (fromTable == null) {
            throw new IllegalArgumentException("未找到源餐桌 (內存中不存在): " + fromDisplayId);
        }
        if (toTable == null) {
            throw new IllegalArgumentException("未找到目標餐桌 (內存中不存在): " + toDisplayId);
        }

        // ===== 3. 業務規則驗證 =====
        // 此時 fromTable.getCurrentGroup() 應該不為 null
        validateTableChangeRules(fromTable, toTable);

        // ===== 4. 獲取顧客組 (從內存對象直接獲取) =====
        CustomerGroup group = fromTable.getCurrentGroup();
        if (group == null) {
            // 如果內存裡還是 null，說明 enrich 邏輯有問題，拋出明確錯誤而不是 NPE
            throw new IllegalStateException("系統緩存異常：餐桌 #" + fromDisplayId +
                    " 有 groupId 但無 Group 對象，請重啟系統或檢查數據一致性");
        }

        Integer currentGroupId = group.getGroup_id();
        int groupSize = group.getGroupSize();
        int targetCapacity = toTable.getCapacity();

        // ===== 5. 容量驗證 =====
        if (groupSize > targetCapacity) {
            throw new IllegalStateException("顧客組人數 (" + groupSize + "人) 超過目標餐桌容量 (" + targetCapacity + "人)");
        }

        // ===== 6. 執行數據庫操作 =====
        // 6.1 更新源餐桌狀態 → SETTING_UP
        int fromUpdated = tablesMapper.updateTableStatusForDeparture(
                fromTable.getTableId(), // 使用內存對象中的 ID
                Tables.TableStatus.SETTING_UP.name(),
                null,
                0,
                fromTable.getTableType().name()
        );
        if (fromUpdated == 0) {
            throw new RuntimeException("更新源餐桌狀態失敗");
        }

        // 6.2 更新目標餐桌狀態 → OCCUPIED
        int toUpdated = tablesMapper.updateTableStatus(
                toTable.getTableId(),
                Tables.TableStatus.OCCUPIED.name(),
                currentGroupId,
                groupSize,
                LocalDateTime.now()
        );
        if (toUpdated == 0) {
            throw new RuntimeException("更新目標餐桌狀態失敗");
        }

        // 6.3 更新顧客組關聯
        int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                currentGroupId,
                toTable.getTableId(),
                true,
                group.hasShownWaitMessage()
        );
        if (groupUpdated == 0) {
            throw new RuntimeException("更新顧客組餐桌關聯失敗");
        }

        // ===== 7. 同步更新內存緩存 =====
        syncMemoryAfterTableChange(fromTable, toTable, group, groupSize);

        System.out.println(" 換桌成功：" + fromDisplayId + " → " + toDisplayId +
                " (顧客組 #" + group.getCallNumber() + ")");
        return true;
    }


    /**
     * 验证换桌操作的业务规则
     *
     * 功能说明：
     * 1. 校验源餐桌：必须为占用状态的主桌，且订单状态为未下单（无活跃订单）
     * 2. 校验目标餐桌：必须为空闲状态的主桌，不可为合并桌或聚餐桌
     * 3. 容量规则校验：若目标桌为 6 人桌，顾客组人数需≥4 人
     * 4. 获取顾客组人数用于规则判断，避免重复查询缓存
     *
     * @param fromTable 源餐桌对象（当前顾客所在餐桌）
     * @param toTable 目标餐桌对象（顾客拟更换至的餐桌）
     *
     * 异常处理：
     * - 任一规则校验失败时抛出 IllegalStateException，提示具体冲突原因
     * - 调用方需捕获异常并向用户展示友好提示
     */
    private void validateTableChangeRules(Tables fromTable, Tables toTable) {
        // 【關鍵】先獲取顧客組對象，確保不為 null
        CustomerGroup group = fromTable.getCurrentGroup();
        if (group == null) {
            // 這裡不應該發生，如果發生說明緩存刷新失敗
            throw new IllegalStateException("源餐桌 #" + fromTable.getDisplayId() +
                    " 沒有顧客組，無法換桌");
        }

        int groupSize = group.getGroupSize(); // 安全獲取人數

        // 規則 1: 源餐桌必須是占用狀態
        if (fromTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            throw new IllegalStateException("源餐桌 #" + fromTable.getDisplayId() +
                    " 當前狀態為【" + fromTable.getStatus().getDisplayName() + "】，無法換桌");
        }

        // 規則 2: 源餐桌不能是合併餐桌
        if (fromTable.getTableType() == Tables.TableType.MERGED) {
            throw new IllegalStateException("合併餐桌不能直接換桌！請先通過主餐桌操作或先取消合併關係。");
        }

        if (fromTable.getTableType() == Tables.TableType.GROUPED) {
            throw new IllegalStateException("聚餐桌不能直接換桌！請先通過主餐桌操作或先取消合併關係。");
        }

        // 規則 3: 目標餐桌必須是空閒狀態
        if (toTable.getStatus() != Tables.TableStatus.VACANT) {
            throw new IllegalStateException("目標餐桌 #" + toTable.getDisplayId() +
                    " 當前狀態為【" + toTable.getStatus().getDisplayName() + "】，不是空閒狀態");
        }

        // 規則 4: 目標餐桌不能是合併餐桌
        if (toTable.getTableType() == Tables.TableType.MERGED) {
            throw new IllegalStateException("不能將顧客組轉移到合併餐桌！請選擇普通餐桌。");
        }

        if (toTable.getTableType() == Tables.TableType.GROUPED) {
            throw new IllegalStateException("不能將顧客組轉移到聚餐桌！請選擇普通餐桌。");
        }

        // 規則 5: 檢查訂單狀態（僅允許 NO_ORDER 狀態換桌）
        Tables.OrderStatus orderStatus = fromTable.getOrderStatus();
        if (orderStatus != Tables.OrderStatus.NO_ORDER) {
            String statusText = switch (orderStatus) {
                case ORDERED_UNFINISHED -> "有未完成訂單";
                case ORDERED_FINISHED -> "有已完成但未結賬訂單";
                case CHECKED_OUT -> "訂單已結賬";
                default -> "有活躍訂單";
            };
            throw new IllegalStateException("餐桌 #" + fromTable.getDisplayId() + " " + statusText +
                    "，無法換桌。結賬後請直接執行離店操作。");
        }

        // 規則 6: 6 人桌特殊規則（3 人及以下不能進 6 人桌）
        //  使用提前獲取的 groupSize，不再調用 fromTable.getCurrentGroup()
        if (toTable.getCapacity() == 6 && groupSize < 4) {
            throw new IllegalStateException("只有 4 人及以上顧客組才能使用 6 人桌！\n" +
                    "當前顧客組：" + groupSize + "人");
        }
    }

    /**
     * 换桌操作后同步更新内存缓存
     *
     * 功能说明：
     * 1. 更新源餐桌缓存：状态设为准备中（SETTING_UP），清空顾客组关联与实际入座人数
     * 2. 更新目标餐桌缓存：状态设为占用（OCCUPIED），关联顾客组并设置入座人数与开始时间
     * 3. 更新顾客组对象：将关联餐桌 ID 指向新餐桌，确保后续查询定位正确
     *
     * @param fromTable 源餐桌对象
     * @param toTable 目标餐桌对象
     * @param group 关联的顾客组对象
     * @param groupSize 顾客组实际人数
     *
     * 执行时机：
     * - 仅在数据库换桌操作成功提交后调用
     * - 确保内存缓存与持久化数据实时一致，避免界面显示滞后
     */
    private void syncMemoryAfterTableChange(Tables fromTable, Tables toTable,
                                            CustomerGroup group, int groupSize) {
        // 更新源餐桌缓存
        Tables cachedFrom = tableMap.get(fromTable.getDisplayId());
        if (cachedFrom != null) {
            cachedFrom.setStatus(Tables.TableStatus.SETTING_UP);
            cachedFrom.setCurrentGroupId(null);
            cachedFrom.setCurrentGroup(null);
            cachedFrom.setActualSeats(0);
        }

        // 更新目标餐桌缓存
        Tables cachedTo = tableMap.get(toTable.getDisplayId());
        if (cachedTo != null) {
            cachedTo.setStatus(Tables.TableStatus.OCCUPIED);
            cachedTo.setCurrentGroupId(group.getGroup_id());
            cachedTo.setCurrentGroup(group);
            cachedTo.setActualSeats(groupSize);
            cachedTo.setStartTime(LocalDateTime.now());
        }

        // 更新顾客组引用
        group.setTableId(toTable.getTableId());
    }

    /**
     * 获取所有空闲主桌列表
     *
     * 功能说明：
     * 从内存缓存 tableMap 中筛选状态为空闲（VACANT）且类型为主桌（MAIN）的餐桌，
     * 按 baseId 升序排序后返回，供换桌、分配等场景快速查询可用资源。
     *
     * @return 空闲主桌列表，按餐桌基础编号从小到大排序
     *
     * 性能优势：
     * - 直接读取内存缓存，避免频繁查询数据库，响应速度毫秒级
     * - 流式过滤 + 排序，代码简洁且易于维护
     *
     * 应用场景：
     * - 换桌时展示可选目标餐桌列表
     * - 新顾客入座时推荐空闲餐桌
     * - 运营监控面板统计实时空闲资源
     */
    @Transactional(readOnly = true)
    public List<Tables> getAllVacantTables() {
        return tableMap.values().stream()
                .filter(table -> table.getStatus() == Tables.TableStatus.VACANT)
                .filter(tables -> tables.getTableType() == Tables.TableType.MAIN)
                .sorted(Comparator.comparingInt(Tables::getBaseId))  //  按 baseId 升序排序
                .collect(Collectors.toList());
    }


    /**
     * 创建预约记录并处理关联业务逻辑
     *
     * 功能说明：
     * 1. 提取并校验基础参数：客户姓名、电话、预约时间、餐桌选择模式等
     * 2. 生成预约编号：格式为 R+ 日期 + 手机尾号 + 当日全局序号，支持冲突重试
     * 3. 构建预约对象：设置客户信息、预约时间、餐桌配置、预点餐及预付状态等
     * 4. 处理餐桌配置：
     *    - 手动模式：校验餐桌存在性与空闲状态，锁定指定餐桌
     *    - 数量模式：校验桌型规则（个人桌 1 张/合并桌 2 张同容量/聚餐桌≥3 张 6 人桌）
     * 5. 1.5 小时内预约特殊处理：
     *    - 手动模式：锁定餐桌为预留状态，设置 90 分钟保留时限
     *    - 合并桌：双向绑定 merged_with 字段，同步锁定两张餐桌
     *    - 聚餐桌：批量锁定所有关联桌，设置 group_with 与 GROUPED 类型
     * 6. 预点餐处理：若开启预点餐，创建关联订单记录，手动模式绑定首桌 ID，数量模式留空待后续分配
     * 7. 持久化预约记录并返回执行结果
     *
     * @param data 包含预约信息的映射，支持键：
     *             customerName, customerPhone, reservationTime, within15Hours,
     *             tableSelectionMode, selectedTables, tableConfig, tableType,
     *             preOrder, isPrepaid, prepaidAmount, notes
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，用户提示消息
     *         - reservationId: String，生成的预约编号（成功时返回）
     *
     * 业务规则：
     * - 预约编号全局唯一，冲突时自动重试生成
     * - 1.5 小时内手动选桌才锁定餐桌资源，其他场景仅记录不占用
     * - 合并桌必须容量相同且双向绑定，聚餐桌必须全为 6 人桌且≥3 张
     * - 预点餐订单创建失败不影响预约主流程，记录日志后继续执行
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createReservation(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        try {
            // ═══════════════════════════════════════════════════════════
            // 【步驟 1】基礎數據提取
            // ═══════════════════════════════════════════════════════════
            String customerName = (String) data.get("customerName");
            String customerPhone = (String) data.get("customerPhone");
            String reservationTimeStr = (String) data.get("reservationTime");
            Boolean within15h = (Boolean) data.get("within15Hours");
            String tableSelectionMode = (String) data.get("tableSelectionMode"); // MANUAL 或 QUANTITY

            @SuppressWarnings("unchecked")
            List<String> selectedTables = (List<String>) data.get("selectedTables");
            String tableType = (String) data.get("tableType"); // MAIN, MERGED, GROUP

            @SuppressWarnings("unchecked")
            Map<String, Integer> tableConfig = (Map<String, Integer>) data.get("tableConfig");

            // 【新增】預點餐字段（Boolean）
            Boolean preOrder = (Boolean) data.get("preOrder");
            Boolean isPrepaid = (Boolean) data.get("isPrepaid");
            Double prepaidAmount = (Double) data.get("prepaidAmount");
            String notes = (String) data.get("notes");

            // 簡單驗證
            if (customerName == null || customerPhone == null) {
                throw new IllegalArgumentException("姓名和電話不能為空");
            }

            // ═══════════════════════════════════════════════════════════
            // 【核心】生成自定義預約編號：R+ 年月日 + 手機尾號 + 當天全局順序號
            // 格式：R20260322-1234-1（所有預約都生成，不只是預點餐）
            // ═══════════════════════════════════════════════════════════
            String reservationCode = generateReservationCode(customerPhone, reservationTimeStr);

            // ═══════════════════════════════════════════════════════════
            // 【步驟 2】構建預定記錄對象
            // ═══════════════════════════════════════════════════════════
            TableReservation reservation = new TableReservation();
            reservation.setCustomerName(customerName);
            reservation.setCustomerPhone(customerPhone);

            // 解析時間 (格式：yyyy-MM-dd HH:mm)
            reservation.setReservationTime(
                    LocalDateTime.parse(reservationTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
            reservation.setWithin15h(within15h);
            reservation.setTableSelectionMode(tableSelectionMode);
            reservation.setGroupType(tableType);

            String status = "QUANTITY".equals(tableSelectionMode) ? "PRE_CONFIRMED" : "CONFIRMED";
            reservation.setStatus(status);

            // 【關鍵】設置預點餐字段（Boolean，默認 false）
            reservation.setPreOrder(preOrder != null && preOrder);
            reservation.setIsPrepaid(isPrepaid);
            reservation.setPrepaidAmount(prepaidAmount);
            reservation.setNotes(notes);

            //  設置自定義預約號（作為主鍵）
            reservation.setReservationId(reservationCode);

            // ═══════════════════════════════════════════════════════════
            // 【步驟 3】根據模式分別處理桌子配置
            // ═══════════════════════════════════════════════════════════
            boolean isManualMode = "MANUAL".equals(tableSelectionMode);
            int totalTableCount;
            String configDesc;
            String reservedTableIds = null;  // 【新增】声明 reservedTableIds 变量

            if (isManualMode) {
                // ── 手動模式：使用 selectedTables ──
                totalTableCount = (selectedTables != null) ? selectedTables.size() : 0;
                configDesc = (selectedTables != null && !selectedTables.isEmpty())
                        ? "手動指定桌號：" + String.join(",", selectedTables)
                        : "";
                if (selectedTables != null && !selectedTables.isEmpty()) {
                    reservedTableIds = String.join(",", selectedTables);
                }

            } else {
                // ── 數量模式：使用 tableConfig ──
                // 【關鍵】先檢查 tableConfig 是否為 null
                if (tableConfig != null && !tableConfig.isEmpty()) {
                    totalTableCount = tableConfig.values().stream()
                            .mapToInt(Integer::intValue)
                            .sum();

                    // 構建配置描述
                    StringBuilder descBuilder = new StringBuilder();
                    tableConfig.forEach((k, v) ->
                            descBuilder.append(k).append("人桌 x").append(v).append(", ")
                    );
                    configDesc = descBuilder.toString();

                    // 【關鍵】餐桌類型規則驗證（僅數量模式需要，且 tableConfig 不為空）
                    validateTableTypeRules(tableType, tableConfig);
                } else {
                    // tableConfig 為空時的兜底
                    totalTableCount = 0;
                    configDesc = "";
                }
            }

            reservation.setTableCount(totalTableCount);
            reservation.setTableConfigDesc(configDesc);
            reservation.setReservedTableIds(reservedTableIds);  // 【关键】在插入前设置

            // ═══════════════════════════════════════════════════════════
            // 【核心修復】步驟 4：先插入預約記錄（避免外鍵約束錯誤）
            // ═══════════════════════════════════════════════════════════
            //  關鍵：先檢查編號唯一性
            int maxRetry = 3;
            int retry = 0;
            while (retry < maxRetry) {
                if (!reservationMapper.existsByCode(reservationCode)) {
                    break;  // 編號可用，跳出循環
                }
                // 編號已存在：順序號 +1 重試
                retry++;
                reservationCode = generateReservationCode(customerPhone, reservationTimeStr, retry);
                reservation.setReservationId(reservationCode);
            }

            // 【關鍵修復】先插入預約記錄到 table_reservations 表
            // 這樣後續更新 restaurant_tables.current_reservation_id 時外鍵約束才能通過
            reservationMapper.insert(reservation);
            System.out.println(" 預約記錄已插入：" + reservationCode +
                    ", reservedTableIds=" + reservedTableIds);
            // ═══════════════════════════════════════════════════════════
            // 【步驟 5】核心邏輯分支：三個場景處理
            // ═══════════════════════════════════════════════════════════

            // 【場景 1】1.5 小時內 + 手動指定桌號 (MANUAL) → 鎖定 restaurant_tables
            if (within15h != null && within15h && isManualMode &&
                    selectedTables != null && !selectedTables.isEmpty()) {

                // ========= 【分支 1】MAIN（原本邏輯） =========
                if ("MAIN".equals(tableType)) {
                    List<String> lockedTableIds = new ArrayList<>();

                    for (String displayId : selectedTables) {
                        Tables table = tablesMapper.findByDisplayId(displayId);

                        if (table == null) {
                            throw new IllegalArgumentException("餐桌 " + displayId + " 不存在");
                        }
                        if (table.getStatus() != Tables.TableStatus.VACANT) {
                            throw new IllegalArgumentException(
                                    "餐桌 " + displayId + " 當前狀態為 " + table.getStatus()
                            );
                        }

                        // 【修改點 1】使用新方法，傳入 reservationCode
                        tablesMapper.updateTableForReservationWithId(
                                table.getTableId(),
                                "RESERVED",
                                LocalDateTime.now().plusMinutes(90),
                                reservationCode
                        );

                        lockedTableIds.add(displayId);
                    }

                    reservation.setManualTableNumbers(String.join(",", lockedTableIds));
                    reservation.setReservedTableIds(String.join(",", lockedTableIds));
                }

                // ========= 【分支 2】MERGED（新增核心） =========
                else if ("MERGED".equals(tableType)) {

                    if (selectedTables.size() != 2) {
                        throw new IllegalArgumentException("合併桌必須選擇 2 張桌");
                    }

                    Tables t1 = tablesMapper.findByDisplayId(selectedTables.get(0));
                    Tables t2 = tablesMapper.findByDisplayId(selectedTables.get(1));

                    //  基本驗證
                    if (t1 == null || t2 == null) {
                        throw new IllegalArgumentException("餐桌不存在");
                    }

                    if (t1.getStatus() != Tables.TableStatus.VACANT ||
                            t2.getStatus() != Tables.TableStatus.VACANT) {
                        throw new IllegalArgumentException("合併桌必須都是空閒");
                    }

                    //  關鍵：容量必須相同（你要求的）
                    if (!Objects.equals(t1.getCapacity(), t2.getCapacity())) {
                        throw new IllegalArgumentException("只能合併容量相同的桌子");
                    }

                    //  可選：避免已經合併過
                    if (t1.getMergedWith() != null || t2.getMergedWith() != null) {
                        throw new IllegalArgumentException("餐桌已經處於合併狀態");
                    }

                    // =========  核心：雙向綁定 =========
                    tablesMapper.mergeTables(t1.getTableId(), t2.getDisplayId());
                    tablesMapper.mergeTables(t2.getTableId(), t1.getDisplayId());

                    // =========  同時鎖定 =========
                    LocalDateTime reserveTime = LocalDateTime.now().plusMinutes(90);

                    // 第一張桌：使用新方法
                    tablesMapper.updateTableForReservationWithId(
                            t1.getTableId(), "RESERVED", reserveTime, reservationCode
                    );
                    // 第二張桌：使用新方法
                    tablesMapper.updateTableForReservationWithId(
                            t2.getTableId(), "RESERVED", reserveTime, reservationCode
                    );

                    // ========= 記錄 =========
                    // ========= 【關鍵修復】記錄：區分顯示格式和查詢格式 =========
                    // manual_table_numbers: 界面顯示用，用 "+" 連接（如 "10+11"）
                    String mergedIdsDisplay = t1.getDisplayId() + "+" + t2.getDisplayId();
                    // reserved_table_ids: 數據庫查詢用，用 "," 分隔（如 "10,11"），供 FIND_IN_SET 使用
                    String mergedIdsQuery = t1.getDisplayId() + "," + t2.getDisplayId();

                    reservation.setManualTableNumbers(mergedIdsDisplay);   // ✓ 顯示用 "+"
                    reservation.setReservedTableIds(mergedIdsQuery);       // ✓ 查詢用 ","

                    // 【調試日誌】確認寫入值（開發時可啟用）
                    System.out.println(" MERGED 預約記錄：manual_table_numbers=" + mergedIdsDisplay +
                            ", reserved_table_ids=" + mergedIdsQuery);
                }

                // ========= 【新增分支 3】GROUP（聚餐桌：3 張或以上 6 人桌） =========
                else if ("GROUP".equals(tableType)) {

                    //  驗證 1: 數量必須 >= 3 張
                    if (selectedTables.size() < 3) {
                        throw new IllegalArgumentException("聚餐桌必須選擇 3 張或以上的餐桌！當前選擇：" + selectedTables.size() + "張");
                    }

                    //  驗證 2: 每張餐桌必須是 6 人桌 + 空閒狀態
                    List<String> lockedTableIds = new ArrayList<>();
                    for (String displayId : selectedTables) {
                        Tables table = tablesMapper.findByDisplayId(displayId);

                        if (table == null) {
                            throw new IllegalArgumentException("餐桌 " + displayId + " 不存在");
                        }
                        if (table.getCapacity() != 6) {
                            throw new IllegalArgumentException("聚餐桌只能使用 6 人桌！餐桌 #" + displayId + " 是 " + table.getCapacity() + "人桌");
                        }
                        if (table.getStatus() != Tables.TableStatus.VACANT) {
                            throw new IllegalArgumentException("餐桌 " + displayId + " 當前狀態為 " + table.getStatus());
                        }

                        lockedTableIds.add(displayId);
                    }

                    // =========  同時鎖定所有餐桌 =========
                    LocalDateTime reserveTime = LocalDateTime.now().plusMinutes(90);
                    for (String displayId : lockedTableIds) {
                        Tables table = tablesMapper.findByDisplayId(displayId);
                        // 【修改點 4】循環內：使用新方法
                        tablesMapper.updateTableForReservationWithId(
                                table.getTableId(),
                                "RESERVED",
                                reserveTime,
                                reservationCode  
                        );
                        //  更新餐桌的 group_with 和 table_type（用於離店時識別關聯桌）
                        tablesMapper.updateTableForGroupReservation(
                                table.getTableId(),
                                String.join(",", lockedTableIds),  // group_with: "7,8,9"
                                "GROUPED"                           // table_type: GROUPED
                        );
                    }

                    // ========= 【關鍵】記錄：區分顯示格式和查詢格式 =========
                    // manual_table_numbers: 界面顯示用，用 "+" 連接（如 "7+8+9"）
                    String groupIdsDisplay = String.join("+", lockedTableIds);
                    // reserved_table_ids: 數據庫查詢用，用 "," 分隔（如 "7,8,9"），供 FIND_IN_SET 使用
                    String groupIdsQuery = String.join(",", lockedTableIds);

                    reservation.setManualTableNumbers(groupIdsDisplay);   // ✓ 顯示用 "+"
                    reservation.setReservedTableIds(groupIdsQuery);       // ✓ 查詢用 ","

                    // 【調試日誌】確認寫入值（可選，開發時啟用）
                    System.out.println(" GROUP 預約記錄：manual_table_numbers=" + groupIdsDisplay +
                            ", reserved_table_ids=" + groupIdsQuery);
                }

                // ========= 【分支 4】未知桌型 =========
                else {
                    throw new IllegalArgumentException("不支援的桌型：" + tableType);
                }
            }

            //【場景 2】1.5 小時內 + 數量模式（非手動輸入桌號）
            else if (within15h != null && within15h && !isManualMode) {
                reservation.setManualTableNumbers(null);
                // 不操作 restaurant_tables

            }
            // 【場景 3】1.5 小時外 → 僅記錄，不鎖桌
            else {
                reservation.setManualTableNumbers(null);
                // 不操作 restaurant_tables
            }

            // ═══════════════════════════════════════════════════════════
            // 【修改步骤 6】预点餐 → 创建订单记录
            // 支持两种场景：
            // 1. 数量模式 + 预点餐 → table_id = NULL
            // 2. 手动模式 + 1.5小时内 + 预点餐 → table_id = 第一个餐桌ID
            // ═══════════════════════════════════════════════════════════
            if (preOrder != null && preOrder) {
                try {
                    //  判断餐桌模式
                    Integer tableId = null;

                    // 场景2：手动模式 + 1.5小时内 → 获取第一个餐桌的 table_id
                    if (isManualMode && within15h != null && within15h &&
                            selectedTables != null && !selectedTables.isEmpty()) {

                        // 获取第一个餐桌的 table_id
                        String firstTableDisplayId = selectedTables.get(0);
                        Tables firstTable = tablesMapper.findByDisplayId(firstTableDisplayId);
                        if (firstTable != null) {
                            tableId = firstTable.getTableId();
                            System.out.println(" 手动模式预点餐：table_id=" + tableId +
                                    ", displayId=" + firstTableDisplayId);
                        }
                    }
                    // 场景1：数量模式 → table_id = NULL
                    // （tableId 保持 null）

                    //  创建订单记录
                    Order preOrderEntity = new Order();
                    preOrderEntity.setTableId(tableId);                    //  手动模式有table_id，数量模式为NULL
                    preOrderEntity.setOrderNumber(null);                   // 预定订单无需订单号
                    preOrderEntity.setOrderType("RESERVATION");            // 预定属于堂食
                    preOrderEntity.setDeliveryMethod(null);
                    preOrderEntity.setDeliveryAddress(null);
                    preOrderEntity.setCustomerPhone(customerPhone);
                    preOrderEntity.setCustomerName(customerName);
                    preOrderEntity.setOrderTime(LocalDateTime.now());      //  新增：订单创建时间

                    // 金额初始为 0，后续点餐后更新
                    preOrderEntity.setItemsTotal(0.0);
                    preOrderEntity.setDeliveryFee(0.0);
                    preOrderEntity.setTotalAmount(0.0);

                    preOrderEntity.setStatus("NO_ORDER");
                    preOrderEntity.setIsCheckedOut(false);

                    //  关键：设置预付信息 + 关联预约 ID
                    preOrderEntity.setIsPrepaid(isPrepaid != null && isPrepaid);
                    preOrderEntity.setPrepaidAmount(prepaidAmount != null ? prepaidAmount : 0.0);
                    preOrderEntity.setReservationId(reservationCode);      //  关联预约

                    // 插入订单主表（MyBatis 会回填 orderId）
                    orderMapper.createOrder(preOrderEntity);

                    System.out.println(" 预点餐订单已创建: orderId=" +
                            preOrderEntity.getOrderId() +
                            ", reservationId=" + reservationCode +
                            ", tableId=" + tableId +
                            ", 桌型:" + tableType +
                            ", 模式:" + tableSelectionMode);

                } catch (Exception e) {
                    System.err.println(" 创建预点餐订单失败：" + e.getMessage());
                    // 不抛异常，避免影响预约创建（订单可后续补创）
                    e.printStackTrace();
                }
            }

            result.put("success", true);
            result.put("message", "預約成功！預約號：" + reservationCode);
            result.put("reservationId", reservationCode);  //  返回自定義編號

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "預約失敗：" + e.getMessage());
            e.printStackTrace();
            // 事務會自動回滾
        }
        return result;
    }

    /**
     *  生成自定義預約編號：R+ 年月日 + 手機尾號 + 當天全局順序號
     * 格式：R20260322-1234-1
     *
     * @param phone              客戶手機號
     * @param reservationTimeStr 預約時間字符串 "yyyy-MM-dd HH:mm"
     * @return 自定義編號
     */
    private String generateReservationCode(String phone, String reservationTimeStr) {
        return generateReservationCode(phone, reservationTimeStr, 0);
    }

    /**
     * 生成预约记录唯一编号
     *
     * 功能说明：
     * 根据客户手机号、预约日期及当日全局序号，生成格式化的预约编号（如 "R20260322-1234-1"）。
     *
     * 编号规则：
     * - 前缀：固定 "R" + 预约日期（yyyyMMdd）
     * - 中段：手机号后 4 位数字（不足补 0）
     * - 后缀：当日全局递增序号（支持重试累加）
     *
     * @param phone 客户手机号
     * @param reservationTimeStr 预约时间字符串（格式：yyyy-MM-dd HH:mm）
     * @param retryCount 重试次数偏移量，用于避免并发冲突
     * @return 生成的预约编号字符串
     */
    private String generateReservationCode(String phone, String reservationTimeStr, int retryCount) {
        // 1. 提取日期部分：2026-03-22 18:30 → 20260322
        String datePart = reservationTimeStr.substring(0, 10).replace("-", "");

        // 2. 提取手機尾號 4 位（兼容不同長度）
        String phoneTail = "0000";
        if (phone != null && !phone.isEmpty()) {
            // 只保留數字
            String digitsOnly = phone.replaceAll("\\D", "");
            if (digitsOnly.length() >= 4) {
                // 取最後 4 位
                phoneTail = digitsOnly.substring(digitsOnly.length() - 4);
            } else {
                // 不足 4 位，前面補 0
                phoneTail = String.format("%4s", digitsOnly).replace(' ', '0');
            }
        }

        // 3. 構建日期前綴：R20260322（用於查詢當天最大順序號）
        String datePrefix = "R" + datePart;

        // 4. 【關鍵】獲取當天全局最大順序號（不按手機尾號分組！）
        Integer maxSeq = reservationMapper.getMaxSequenceToday(datePrefix);
        int sequence = (maxSeq != null ? maxSeq : 0) + 1 + retryCount;

        // 5. 拼接完整編號：R20260322-1234-1
        //    格式：R + 日期 + "-" + 手機尾號 + "-" + 當天全局順序號
        return datePrefix + "-" + phoneTail + "-" + sequence;
    }

    /**
     * 验证餐桌类型与配置数量的业务规则
     *
     * @param tableType 餐桌类型标识（MAIN / MERGED / GROUP）
     * @param tableConfig 餐桌配置映射，键为容量字符串，值为对应数量
     *
     * 功能说明：
     * 1. 个人桌（MAIN）：校验总桌数必须为 1 张
     * 2. 合并桌（MERGED）：校验仅一种容量且数量恰好为 2 张
     * 3. 聚餐桌（GROUP）：校验仅 6 人桌且数量≥3 张
     *
     * 异常处理：
     * - 规则校验失败时抛出 IllegalArgumentException，提示具体冲突原因
     */
    private void validateTableTypeRules(String tableType, Map<String, Integer> tableConfig) {
        if (tableConfig == null || tableConfig.isEmpty()) {
            return;  // 空配置無需驗證
        }

        if ("MAIN".equals(tableType)) {
            // 個人桌：只能選 1 張桌子
            int totalTables = tableConfig.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (totalTables != 1) {
                throw new IllegalArgumentException("⚠️ 個人桌只能選擇 1 張主桌！");
            }
        } else if ("MERGED".equals(tableType)) {
            // 合併桌：必須是 2 張相同容量的桌子
            if (tableConfig.size() != 1) {
                throw new IllegalArgumentException("⚠️ 合併桌只能選擇一種容量的桌子！");
            }
            int qty = tableConfig.values().iterator().next();
            if (qty != 2) {
                throw new IllegalArgumentException("⚠️ 合併桌必須選擇 2 張相同容量的桌子！");
            }
        }
        // 【新增】聚餐桌：只能選擇 6 人桌，數量 >= 3 張
        else if ("GROUP".equals(tableType)) {
            // 驗證 1: 只能選擇一種容量的桌子（必須是 6 人桌）
            if (tableConfig.size() != 1) {
                throw new IllegalArgumentException("⚠️ 聚餐桌只能選擇一種容量的桌子！");
            }

            // 驗證 2: 容量必須是 6 人桌
            String capacityKey = tableConfig.keySet().iterator().next();
            if (!"6".equals(capacityKey)) {
                throw new IllegalArgumentException("⚠️ 聚餐桌只能使用 6 人桌！當前選擇：" + capacityKey + "人桌");
            }

            // 驗證 3: 數量必須 >= 3 張（聚餐桌定義）
            int qty = tableConfig.values().iterator().next();
            if (qty < 3) {
                throw new IllegalArgumentException("⚠️ 聚餐桌必須選擇 3 張或以上的 6 人桌！當前數量：" + qty + "張");
            }
        }
    }

    /**
     * 根据餐桌显示编号查询关联的有效预约号
     *
     * @param displayId 餐桌显示编号（如 "7"、"13a"）
     * @return 关联的预约记录唯一标识；无关联预约或餐桌不存在时返回 null
     *
     * 功能说明：
     * 1. 查询餐桌对象获取其数据库主键
     * 2. 通过主键反向查询当前处于有效状态的预约记录
     *
     * 应用场景：
     * - 餐桌入座时自动加载预约客户信息
     * - 界面显示餐桌当前预约状态提示
     */
    public String getReservationIdByTable(String displayId) {
        Tables table = tablesMapper.findByDisplayId(displayId);
        if (table == null) return null;
        return reservationMapper.findActiveReservationIdByTableId(table.getTableId());
    }

    /**
     * 处理客人入座流程，支持普通桌/合并桌/聚餐桌多种场景
     *
     * @param displayId 主餐桌显示编号
     * @param actualSeats 实际入座人数
     * @param reservationId 关联的预约记录编号（可为空）
     *
     * 功能说明：
     * 1. 校验餐桌存在性及预定状态
     * 2. 根据餐桌类型收集需处理的所有关联餐桌（合并桌 2 张、聚餐桌多张）
     * 3. 计算人数分配策略：
     *    - 合并桌：优先填满编号较小的餐桌
     *    - 聚餐桌：平均分配后剩余人数按编号从小到大依次分配
     * 4. 创建顾客组并关联主桌，批量更新所有餐桌状态为占用
     * 5. 同步更新内存缓存，确保界面即时刷新
     * 6. 若关联预约记录，将其状态更新为已完成，并将预点餐订单类型转为堂食
     * 7. 累加当日顾客总数并递增下一个叫号
     *
     * 业务规则：
     * - 仅主桌绑定顾客组与预约号，伙伴桌通过关联字段间接归属
     * - 聚餐桌订单类型转换失败时记录日志但不中断入座流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void processGuestCheckIn(String displayId, int actualSeats, String reservationId) {
        System.out.println(" [DEBUG] processGuestCheckIn 开始:");
        System.out.println("   displayId: " + displayId);
        System.out.println("   actualSeats: " + actualSeats);
        System.out.println("   reservationId 参数: " + reservationId);

        // 1. 查询主餐桌
        Tables mainTable = tablesMapper.findByDisplayId(displayId);
        if (mainTable == null) {
            throw new IllegalArgumentException("餐桌不存在: " + displayId);
        }

        System.out.println("   table.currentReservationId (DB): " + mainTable.getCurrentReservationId());
        System.out.println("   table.status (DB): " + mainTable.getStatus());
        System.out.println("   table.tableType: " + mainTable.getTableType());
        System.out.println("   table.groupWith: " + mainTable.getGroupWith());

        // 2. 验证状态
        if (mainTable.getStatus() != Tables.TableStatus.RESERVED) {
            throw new IllegalStateException("餐桌状态不是已预定: " + mainTable.getStatus());
        }

        // 【核心】收集所有需要处理的餐桌（支持合并桌 + 聚餐桌）
        List<Tables> tablesToProcess = new ArrayList<>();
        Map<String, Integer> seatAllocation = new LinkedHashMap<>();  // displayId -> 分配人数

        // ── 情况 1: 普通桌或合并桌（2张）──
        if (mainTable.getTableType() == Tables.TableType.MERGED && mainTable.getMergedWith() != null) {
            Tables partnerTable = tablesMapper.findByDisplayId(mainTable.getMergedWith());
            if (partnerTable == null) {
                throw new IllegalStateException("合并桌伙伴不存在: " + mainTable.getMergedWith());
            }

            tablesToProcess.add(mainTable);
            tablesToProcess.add(partnerTable);

            // 【核心修复】合并桌分配：优先填满编号较小的桌子（容量相同）
            int mainSeats, partnerSeats;

            // 提取 displayId 的数字部分进行比较（如 "7"→7, "7a"→7）
            int mainNum = Integer.parseInt(mainTable.getDisplayId().replaceAll("[^0-9]", ""));
            int partnerNum = Integer.parseInt(partnerTable.getDisplayId().replaceAll("[^0-9]", ""));

            if (mainNum <= partnerNum) {
                //  主桌编号小：先填满主桌，剩余给伙伴桌
                mainSeats = Math.min(actualSeats, mainTable.getCapacity());
                partnerSeats = Math.max(0, actualSeats - mainSeats);
            } else {
                //  伙伴桌编号小：先填满伙伴桌，剩余给主桌
                partnerSeats = Math.min(actualSeats, partnerTable.getCapacity());
                mainSeats = Math.max(0, actualSeats - partnerSeats);
            }

            seatAllocation.put(mainTable.getDisplayId(), mainSeats);
            seatAllocation.put(partnerTable.getDisplayId(), partnerSeats);

            System.out.println(" 合并桌人数分配: 主桌#" + mainTable.getDisplayId() + "=" + mainSeats +
                    "人，伙伴桌#" + partnerTable.getDisplayId() + "=" + partnerSeats + "人");
        } else if (mainTable.getTableType() == Tables.TableType.GROUPED && mainTable.getGroupWith() != null) {
            // 解析 group_with 字段（格式："7,8,9"）
            String[] groupIds = mainTable.getGroupWith().split(",");
            List<Tables> groupedTables = new ArrayList<>();

            for (String id : groupIds) {
                String trimmedId = id.trim();
                if (!trimmedId.isEmpty()) {
                    Tables groupedTable = tablesMapper.findByDisplayId(trimmedId);
                    if (groupedTable == null) {
                        throw new IllegalStateException("聚餐桌关联桌不存在: " + trimmedId);
                    }
                    if (groupedTable.getStatus() != Tables.TableStatus.RESERVED) {
                        throw new IllegalStateException("聚餐桌 #" + trimmedId +
                                " 状态异常: " + groupedTable.getStatus());
                    }
                    groupedTables.add(groupedTable);
                }
            }

            if (groupedTables.isEmpty()) {
                throw new IllegalStateException("聚餐桌没有关联桌");
            }

            // 按 displayId 数字排序（确保编号小的先分配剩余人数）
            groupedTables.sort(Comparator.comparingInt(t ->
                    Integer.parseInt(t.getDisplayId().replaceAll("[^0-9]", ""))
            ));

            tablesToProcess.addAll(groupedTables);

            // 【核心算法】平均分配 + 剩余按编号从小到大分配
            int tableCount = groupedTables.size();
            int baseSeats = actualSeats / tableCount;      // 平均每桌人数
            int remaining = actualSeats % tableCount;       // 剩余人数

            System.out.println(" 聚餐桌分配计算: 总人数=" + actualSeats +
                    ", 桌子数=" + tableCount +
                    ", 平均=" + baseSeats + "人/桌, 剩余=" + remaining + "人");

            for (int i = 0; i < groupedTables.size(); i++) {
                Tables t = groupedTables.get(i);
                // 前 remaining 张桌子各多分配 1 人
                int seatsForThisTable = baseSeats + (i < remaining ? 1 : 0);
                seatAllocation.put(t.getDisplayId(), seatsForThisTable);
                System.out.println("   - 桌#" + t.getDisplayId() + " 分配 " + seatsForThisTable + "人");
            }
        }
        // ── 情况 3: 普通单桌 ──
        else {
            tablesToProcess.add(mainTable);
            seatAllocation.put(mainTable.getDisplayId(), actualSeats);
        }

        // 3. 创建顾客组（始终关联主桌）
        Integer nextCall = businessStatusMapper.getNextCallNumber(LocalDate.now());
        int callNumber = (nextCall != null) ? nextCall : 1;
        CustomerGroup group = new CustomerGroup(callNumber, actualSeats);
        group.setStartTime(LocalDateTime.now());
        group.setAssigned(true);
        group.setTableId(mainTable.getTableId());
        customerGroupMapper.save(group);

        // 【批量处理】更新所有关联餐桌
        for (Tables table : tablesToProcess) {
            String tid = table.getDisplayId();
            int seatsForTable = seatAllocation.getOrDefault(tid, 0);

            System.out.println("   即将执行更新 - 餐桌#" + tid +
                    ", 分配人数=" + seatsForTable +
                    ", reservationId=" + reservationId);

            // 主桌使用 updateTableForCheckInWithReservation（绑定 reservation_id）
            if (table.getTableId() == mainTable.getTableId()) {
                tablesMapper.updateTableForCheckInWithReservation(
                        table.getTableId(),
                        "OCCUPIED",
                        group.getGroup_id(),
                        seatsForTable,
                        LocalDateTime.now(),
                        reservationId
                );
            }
            // 其他桌子使用 updatePartnerTableForCheckIn（同样绑定 reservation_id）
            else {
                tablesMapper.updatePartnerTableForCheckIn(
                        table.getTableId(),
                        "OCCUPIED",
                        group.getGroup_id(),
                        seatsForTable,
                        reservationId
                );
            }

            //  更新内存缓存
            Tables memoryTable = tableMap.get(tid);
            if (memoryTable != null) {
                memoryTable.setStatus(Tables.TableStatus.OCCUPIED);
                memoryTable.setCurrentGroupId(group.getGroup_id());
                memoryTable.setCurrentGroup(group);
                memoryTable.setActualSeats(seatsForTable);
                memoryTable.setStartTime(LocalDateTime.now());
                memoryTable.setCurrentReservationId(reservationId);
                System.out.println("    内存缓存已同步: #" + tid +
                        ", currentReservationId=" + reservationId);
            }
        }

        // 4. 验证更新结果（查询主桌）
        Tables verifyTable = tablesMapper.findByDisplayId(displayId);
        System.out.println("    更新后验证 - DB 中 current_reservation_id: " +
                (verifyTable != null ? verifyTable.getCurrentReservationId() : "null"));

        // 5. 更新业务状态
        businessStatusMapper.incrementNextCallNumber(LocalDate.now());
        businessStatusMapper.incrementDailyTotalCustomers(actualSeats, LocalDate.now());


        //  6.更新預約狀態：CONFIRMED → COMPLETED
        // 客人成功入座後，預約記錄標記為已完成
        if (reservationId != null && !reservationId.isEmpty()) {
            // 1. 先查詢預約當前狀態，確保是 CONFIRMED 才更新（避免重複更新）
            TableReservation reservation = reservationMapper.findById(reservationId);
            if (reservation != null && "CONFIRMED".equals(reservation.getStatus())) {
                reservationMapper.updateStatus(reservationId, "COMPLETED");
                System.out.println(" 預約狀態已更新: " + reservationId + " [CONFIRMED → COMPLETED]");


                //  6.5更新預點餐訂單類型：RESERVATION → DINE_IN
                // 客人入座後，預點餐訂單轉為正式堂食訂單
                try {
                    int updated = orderMapper.updateOrderTypeByReservationId(
                            reservationId,
                            "RESERVATION",  // 原類型
                            "DINE_IN"       // 新類型
                    );
                    if (updated > 0) {
                        System.out.println(" 預點餐訂單已轉為堂食訂單: reservationId=" + reservationId);
                    } else {
                        System.out.println(" 未找到關聯的預點餐訂單，跳過訂單類型更新");
                    }
                } catch (Exception e) {
                    System.err.println("⚠ 更新訂單類型失敗: " + e.getMessage());
                    // 不拋異常，避免影響入座流程（訂單類型可後續手動調整）
                }
            } else if (reservation != null) {
                System.out.println(" 預約 " + reservationId + " 當前狀態為 " +
                        reservation.getStatus() + "，跳過狀態更新");
            }
        }

        // 【调试日志】输出最终结果
        String tableList = tablesToProcess.stream()
                .map(Tables::getDisplayId)
                .collect(Collectors.joining(","));
        String allocationDesc = tablesToProcess.stream()
                .map(t -> "#" + t.getDisplayId() + "=" + seatAllocation.get(t.getDisplayId()) + "人")
                .collect(Collectors.joining(","));

        System.out.println(" [DEBUG] 入座处理完成: 餐桌组 [" + tableList + "]" +
                ", 人数分配 [" + allocationDesc + "]" +
                ", reservationId=" + reservationId + "\n");
    }

    /**
     * 获取数量模式的预约记录列表
     *
     * 功能说明：
     * 查询所有采用数量选择模式（非手动指定桌号）的预约记录，用于日志展示与运营统计。
     *
     * @return 预约记录映射列表，包含预约号、客户信息、预约时间、桌型配置等字段
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQuantityModeReservationsForLog() {
        return reservationMapper.findQuantityModeReservationsForLog();
    }

    /**
     * 获取预点餐模式的预约记录列表
     *
     * 功能说明：
     * 查询所有已开启预点餐功能的预约记录，用于监控面板实时展示待入座顾客的点餐准备状态。
     *
     * @return 预约记录映射列表，包含预约号、客户信息、预约时间、预点餐状态等字段
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPreOrderReservationsForMonitor() {
        return reservationMapper.findPreOrderReservationsForMonitor();
    }

    /**
     * 根据预约号查询完整预约详情
     *
     * 功能说明：
     * 通过预约记录唯一标识获取包含客户信息、预约时间、餐桌配置、预付状态等全部字段的完整对象。
     *
     * @param reservationId 预约记录唯一标识
     * @return 完整的 TableReservation 对象；参数为空或查询无结果时返回 null
     */
    @Transactional(readOnly = true)
    public TableReservation getReservationDetail(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return null;
        }
        return reservationMapper.findDetailById(reservationId);
    }

    /**
     * 根据餐桌显示编号查找关联的预约记录
     *
     * 功能说明：
     * 通过餐桌的 reserved_table_ids 字段反向查询当前被该餐桌预留的预约记录，用于餐桌入座时自动加载预约信息。
     *  Java 服務層查詢方法普遍採用「返回實體對象」的設計模式
     * @param tableDisplayId 餐桌显示编号（如 "7"、"13a"）
     * @return 关联的 TableReservation 对象；参数为空或无关联预约时返回 null
     */
    @Transactional(readOnly = true)
    public TableReservation findReservationByTableId(String tableDisplayId) {
        if (tableDisplayId == null || tableDisplayId.isEmpty()) {
            return null;
        }
        return reservationMapper.findReservationByTableId(tableDisplayId);
    }


    /**
     * 为预约记录分配餐桌并更新关联状态
     *
     * 功能说明：
     * 1. 校验预约记录存在性及状态（仅限待确认或已延迟状态）
     * 2. 解析预约配置描述，提取所需餐桌容量与数量
     * 3. 验证并锁定选中的餐桌：检查空闲状态、容量匹配，更新为预留状态
     * 4. 更新预约记录的预留餐桌编号列表
     * 5. 根据分配桌数更新餐桌类型：2 张设为合并桌（双向绑定），3 张及以上设为聚餐桌（共享关联列表）
     * 6. 更新关联订单的 table_id 为最小餐桌编号，确保订单归属正确
     * 7. 将预约状态更新为已确认
     * 8. 聚餐桌预点餐特殊处理：按桌数平均分配菜品数量，生成分配明细与分布 JSON
     *
     * @param reservationId 预约记录唯一标识
     * @param selectedDisplayIds 用户选中的餐桌显示编号列表
     */
    //4.3. 複雜業務邏輯原子化封裝
    //技術說明：將涉及多個實體與多表操作的業務流程（如顧客分配、預約取消、結賬）封裝在單一 Service 方法中，確保業務原子性。
    //分析：Service 層充當「業務編排者」，將底層 Mapper 的 CRUD 呼叫組合成有意義的業務操作。即使中間涉及狀態驗證、規則計算、多表更新，也能在單一事務內安全完成。
    @Transactional(rollbackFor = Exception.class)
    public void assignTablesToReservation(String reservationId, List<String> selectedDisplayIds) {
        // 1. 查詢預約詳情
        TableReservation reservation = reservationMapper.findDetailById(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("預約記錄不存在：" + reservationId);
        }
        if (!"PRE_CONFIRMED".equals(reservation.getStatus()) &&
                !"DELAYED".equals(reservation.getStatus())) {
            throw new IllegalStateException("預約狀態異常，無法分配餐桌：" + reservation.getStatus());
        }

        // 2. 解析預定需要的桌型配置
        int requiredCapacity = 0;
        int requiredCount = 0;
        String configDesc = reservation.getTableConfigDesc();
        String groupType = reservation.getGroupType();  // MAIN/MERGED/GROUP

        System.out.println("🔍 [DEBUG] 開始解析桌型配置:");
        System.out.println("  table_config_desc = " + configDesc);
        System.out.println("  group_type = " + groupType);

        // 【修復1】標準化解析：支持帶空格和不帶空格的格式
        if (configDesc != null && !configDesc.isEmpty()) {
            // 移除所有空格和逗號，統一格式： "2 人桌 x1," → "2人桌x1"
            String normalizedDesc = configDesc.replaceAll("\\s+", "").replaceAll(",", "");
            System.out.println("  normalizedDesc = " + normalizedDesc);

            // 解析容量（支持"2人桌"、"4人桌"、"6人桌"）
            if (normalizedDesc.contains("2人桌")) {
                requiredCapacity = 2;
                System.out.println("    識別容量：2人桌");
            } else if (normalizedDesc.contains("4人桌")) {
                requiredCapacity = 4;
                System.out.println("    識別容量：4人桌");
            } else if (normalizedDesc.contains("6人桌")) {
                requiredCapacity = 6;
                System.out.println("    識別容量：6人桌");
            } else {
                System.out.println("    未識別容量配置");
            }

            // 提取數量 (例如 "x2")
            if (normalizedDesc.contains("x")) {
                String[] parts = normalizedDesc.split("x");
                if (parts.length > 1) {
                    try {
                        requiredCount = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                        System.out.println("    識別數量：" + requiredCount + "張");
                    } catch (Exception e) {
                        requiredCount = reservation.getTableCount(); // 兜底
                        System.out.println("    數量解析失敗，使用 table_count: " + requiredCount);
                    }
                } else {
                    requiredCount = reservation.getTableCount();
                }
            } else {
                requiredCount = reservation.getTableCount();
            }
        } else {
            // 如果是手動模式，可能沒有 configDesc，直接取 tableCount
            requiredCount = reservation.getTableCount();
            System.out.println("   table_config_desc 為空，使用 table_count: " + requiredCount);
        }

        // 【調試日誌】
        System.out.println(" 分配餐桌驗證 - 預約:" + reservationId +
                ", 容量:" + requiredCapacity + "人，數量:" + requiredCount +
                ", 桌型:" + groupType);

        if (requiredCapacity == 0 && "QUANTITY".equals(reservation.getTableSelectionMode())) {
            System.err.println(" 無法解析桌型配置: table_config_desc = " + configDesc);
            throw new IllegalStateException("無法解析預定桌型配置（" + configDesc + "），請聯繫管理員");
        }

        // 3. 驗證並鎖定選中的餐桌
        List<Integer> selectedTableIds = new ArrayList<>();
        List<String> validDisplayIds = new ArrayList<>();
        LocalDateTime reserveTime = LocalDateTime.now().plusMinutes(90); // 鎖定 1.5 小時

        for (String displayId : selectedDisplayIds) {
            Tables table = tablesMapper.findByDisplayId(displayId);
            if (table == null) {
                throw new IllegalArgumentException("餐桌不存在：" + displayId);
            }
            if (table.getStatus() != Tables.TableStatus.VACANT) {
                throw new IllegalStateException("餐桌 " + displayId + " 當前狀態為 " + table.getStatus() + "，不可分配");
            }

            //  核心驗證：容量是否匹配 (僅數量模式需要嚴格驗證)
            if ("QUANTITY".equals(reservation.getTableSelectionMode()) && requiredCapacity > 0) {
                if (table.getCapacity() != requiredCapacity) {
                    throw new IllegalArgumentException("餐桌 " + displayId + " 容量 (" + table.getCapacity() + "人) 與預定配置 (" + requiredCapacity + "人) 不符");
                }
            }

            // 更新餐桌狀態為 RESERVED
            tablesMapper.updateTableForReservation(table.getTableId(), "RESERVED", reserveTime);
            // 綁定預約 ID (用於入座時查找)
            tablesMapper.updateTableForCheckInWithReservation(
                    table.getTableId(), "RESERVED", null, 0, null, reservationId
            );

            selectedTableIds.add(table.getTableId());
            validDisplayIds.add(displayId);
        }

        // 4. 更新預約記錄中的 reserved_table_ids
        String idsStr = String.join(",", validDisplayIds);
        reservationMapper.updateReservedTableIds(reservationId, idsStr);

        // ═══════════════════════════════════════════════════════════
        // 【新增步驟 4.5】更新餐桌類型和關聯字段（合併桌/聚餐桌）
        // 規則：
        // - 2張桌 → table_type = MERGED, merged_with = 伙伴桌display_id（雙向綁定）
        // - 3張或以上 → table_type = GROUPED, group_with = 所有桌號列表（如"10,11,12"）
        // - 1張桌 → 保持 MAIN，無需更新
        // ═══════════════════════════════════════════════════════════
        int tableCount = validDisplayIds.size();

        if (tableCount == 2) {
            // ── 合併桌（2張）：雙向綁定 merged_with ──
            String displayId1 = validDisplayIds.get(0);
            String displayId2 = validDisplayIds.get(1);

            Tables table1 = tablesMapper.findByDisplayId(displayId1);
            Tables table2 = tablesMapper.findByDisplayId(displayId2);

            if (table1 != null && table2 != null) {
                // 更新 table1: merged_with = table2 的 display_id
                tablesMapper.updateTableTypeAndMergedWith(
                        table1.getTableId(),
                        "MERGED",
                        displayId2  // 伙伴桌的 display_id
                );
                // 更新 table2: merged_with = table1 的 display_id（雙向綁定）
                tablesMapper.updateTableTypeAndMergedWith(
                        table2.getTableId(),
                        "MERGED",
                        displayId1
                );
                System.out.println(" 合併桌更新: " + displayId1 + " ↔ " + displayId2);
            }
        } else if (tableCount >= 3) {
            // ── 聚餐桌（3張或以上）：所有桌的 group_with 指向同一列表 ──
            String groupWithStr = String.join(",", validDisplayIds);  // 格式："10,11,12"

            for (String displayId : validDisplayIds) {
                Tables table = tablesMapper.findByDisplayId(displayId);
                if (table != null) {
                    tablesMapper.updateTableTypeAndGroupWith(
                            table.getTableId(),
                            "GROUPED",
                            groupWithStr  // 所有桌號列表，如 "10,11,12"
                    );
                }
            }
            System.out.println(" 聚餐桌更新: " + groupWithStr + " (共" + tableCount + "張)");
        }
        // tableCount == 1 時保持 MAIN，無需更新


        // ═══════════════════════════════════════════════════════════
        // 【步驟 5】更新訂單的 table_id（取最小餐桌號）
        // 規則：無論什麼類型的桌子，只有 1 個訂單，table_id = 最小的餐桌 ID
        // ═══════════════════════════════════════════════════════════
        if (!selectedTableIds.isEmpty()) {
            // 排序，取最小的餐桌 ID 作為主桌 ID
            selectedTableIds.sort(Integer::compareTo);
            int mainTableId = selectedTableIds.get(0);

            //  更新 table_orders 表的 table_id
            int updated = orderMapper.updateTableIdByReservationId(reservationId, mainTableId);
            if (updated > 0) {
                System.out.println(" 訂單 table_id 已更新: reservationId=" + reservationId +
                        ", table_id=" + mainTableId);
            } else {
                System.out.println(" 未找到關聯訂單，跳過 table_id 更新");
            }
        }

        if ("PRE_CONFIRMED".equals(reservation.getStatus())) {
            reservationMapper.updateStatus(reservationId, "CONFIRMED");
            System.out.println(" 預約狀態已更新: " + reservationId + " [PRE_CONFIRMED → CONFIRMED]");
        } else if ("DELAYED".equals(reservation.getStatus())) {
            reservationMapper.updateStatus(reservationId, "CONFIRMED");
            System.out.println(" 預約狀態已更新: " + reservationId + " [PRE_CONFIRMED → CONFIRMED]");
        }


        System.out.println(" 餐桌分配成功：預約 " + reservationId + " -> 桌號 " + idsStr);

        // ═══════════════════════════════════════════════════════════
// 【新增步驟 6】处理聚餐桌预点餐的菜品分配逻辑
// 规则：
// - 如果 group_type=GROUP 且 pre_order=true
// - 根据 reserved_table_ids 更新 order_items 的 assigned_table_display_id 和 quantity_distribution
// ═══════════════════════════════════════════════════════════
        if ("GROUP".equals(groupType) && Boolean.TRUE.equals(reservation.getPreOrder())) {
            // 6.1 通过 reservation_id 查询预点餐订单
            Order preOrder = orderMapper.findPreOrderByReservationId(reservationId);
            if (preOrder != null && preOrder.getOrderId() != null) {
                Integer orderId = preOrder.getOrderId();

                // 6.2 查询该订单的所有订单项
                List<OrderItem> orderItems = orderItemMapper.findOrderItemsByOrderId(orderId);

                if (orderItems != null && !orderItems.isEmpty()) {
                    // 解析 reserved_table_ids 获取桌号列表（如 "13,14,15" → ["13", "14", "15"]）
                    String[] tableIdArray = idsStr.split(",");
                    tableCount = tableIdArray.length;

                    System.out.println(" 聚餐桌预点餐处理：reservationId=" + reservationId +
                            ", 桌号列表=" + idsStr + ", 桌子数量=" + tableCount);

                    for (OrderItem item : orderItems) {
                        int quantity = item.getQuantity();
                        String itemCode = item.getItemCode();

                        // 设置 assigned_table_display_id 为所有桌号列表
                        item.setAssignedTableDisplayId(idsStr);

                        // 处理 quantity_distribution
                        String distributionJson = null;

                        // 【核心逻辑】计算每桌分配数量
                        int perTableQty = quantity / tableCount;
                        int remainder = quantity % tableCount;

                        if (perTableQty >= 1) {
                            // 验证：数量必须能被桌子数量整除（余数为0）
                            if (remainder != 0) {
                                throw new IllegalStateException(
                                        "聚餐桌菜品 " + itemCode + " 的数量 (" + quantity +
                                                ") 不能被桌子数量 (" + tableCount + ") 整除！余数=" + remainder +
                                                "。请确保数量是 " + tableCount + " 的倍数（如 " + tableCount + ", " +
                                                (tableCount * 2) + ", " + (tableCount * 3) + " 等）");
                            }

                            // 【关键修复】只有当每桌数量 > 1 时，才需要 quantity_distribution
                            if (perTableQty > 1) {
                                // 构建 quantity_distribution JSON 字符串
                                // 格式：{"13":2,"14":2,"15":2}
                                StringBuilder jsonBuilder = new StringBuilder("{");
                                for (int i = 0; i < tableIdArray.length; i++) {
                                    if (i > 0) jsonBuilder.append(",");
                                    jsonBuilder.append("\"").append(tableIdArray[i].trim()).append("\":")
                                            .append(perTableQty);
                                }
                                jsonBuilder.append("}");
                                distributionJson = jsonBuilder.toString();

                                System.out.println(" 聚餐桌菜品分配: " + itemCode +
                                        " 总数量=" + quantity + " → 每桌" + perTableQty + "份, distribution=" + distributionJson);
                            } else {
                                // perTableQty == 1，不需要 quantity_distribution
                                System.out.println(" 聚餐桌菜品分配: " + itemCode +
                                        " 总数量=" + quantity + " → 每桌1份，无需 distribution");
                            }
                        } else {
                            // quantity < tableCount，这种情况不应该发生
                            throw new IllegalStateException(
                                    "聚餐桌菜品 " + itemCode + " 的数量 (" + quantity +
                                            ") 小于桌子数量 (" + tableCount + ")！每桌至少需要1份");
                        }

                        // 6.3 更新订单项的 assigned_table_display_id 和 quantity_distribution
                        orderItemMapper.updateOrderItemDistribution(
                                item.getOrderItemId(),
                                idsStr,
                                distributionJson
                        );
                    }
                    System.out.println(" 聚餐桌预点餐菜品分配完成，共更新 " + orderItems.size() + " 个菜品");
                }
            }
        }
    }

    /**
     * 更新预约记录的可修改字段
     *
     * 功能说明：
     * 1. 查询原预约记录并校验状态（仅限待确认或已确认状态可修改）
     * 2. 复制原记录数据至新对象，仅修改传入 edits 映射中指定的字段
     * 3. 支持修改项：
     *    - 预约时间：校验非过去时间，重新计算 within_15h 标记
     *    - 桌子配置：校验聚餐桌规则（仅 6 人桌、数量只增不减），重建配置描述与分组类型
     *    - 预点餐状态：仅允许从否改为是，开启时创建预点餐订单
     *    - 预付金额：仅允许增加，同步更新关联订单的预付信息
     *    - 备注信息：直接更新文本内容
     * 4. 校验至少有一项实际修改，避免无效更新
     * 5. 执行数据库更新并输出操作日志
     *
     * @param reservationId 待修改的预约记录唯一标识
     * @param edits 包含待修改字段及新值的映射，支持键：newReservationTime/tableConfig/preOrder/prepaidAmount/notes
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateReservation(String reservationId, Map<String, Object> edits) {
        // 1 查询原预约记录
        TableReservation original = reservationMapper.findDetailById(reservationId);
        if (original == null) {
            throw new IllegalArgumentException("预约记录不存在：" + reservationId);
        }

        // 验证状态
        if (!"PRE_CONFIRMED".equals(original.getStatus()) &&
                !"CONFIRMED".equals(original.getStatus())) {
            throw new IllegalStateException("当前预约状态不可修改：" + original.getStatus());
        }

        // 2 创建修改后的对象（先复制原数据）
        TableReservation updated = new TableReservation();
        updated.setReservationId(reservationId);
        updated.setCustomerName(original.getCustomerName());
        updated.setCustomerPhone(original.getCustomerPhone());
        updated.setReservationTime(original.getReservationTime());
        updated.setTableCount(original.getTableCount());
        updated.setTableConfigDesc(original.getTableConfigDesc());
        updated.setGroupType(original.getGroupType());
        updated.setReservedTableIds(original.getReservedTableIds());
        updated.setTableSelectionMode(original.getTableSelectionMode());
        updated.setManualTableNumbers(original.getManualTableNumbers());
        updated.setStatus(original.getStatus());
        updated.setWithin15h(original.getWithin15h());
        updated.setPreOrder(original.getPreOrder());
        updated.setIsPrepaid(original.getIsPrepaid());
        updated.setPrepaidAmount(original.getPrepaidAmount());
        updated.setNotes(original.getNotes());

        boolean hasChanges = false;

        // ── 修改预约时间 ──
        if (edits.containsKey("newReservationTime")) {
            String newTimeStr = (String) edits.get("newReservationTime");
            LocalDateTime newTime = LocalDateTime.parse(
                    newTimeStr,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            );
            if (newTime.isEqual(original.getReservationTime())) {
                throw new IllegalArgumentException("新预约时间不能与原时间相同！");
            }
            if (newTime.isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("预约时间不能是过去的时间！");
            }
            updated.setReservationTime(newTime);
            long minutes = java.time.Duration.between(LocalDateTime.now(), newTime).toMinutes();
            updated.setWithin15h(minutes >= 0 && minutes <= 90);
            hasChanges = true;
            System.out.println(" 修改预约时间: " + original.getReservationTime() + " → " + newTime);
        }


        // ── 修改桌子配置 ──
        if (edits.containsKey("tableConfig")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> newConfig = (Map<String, Integer>) edits.get("tableConfig");
            if (isTableConfigSame(newConfig, original.getTableConfigDesc())) {
                throw new IllegalArgumentException("新桌子配置不能与原配置相同！");
            }
            // 【新增】聚餐桌预点餐数量调整逻辑
            Integer originalTableCount = null;  // 记录旧数量
            Integer newTotalTables = null;       // 记录新数量

            if ("GROUP".equals(original.getGroupType())) {
                int originalCount = original.getTableCount();

                //  直接內聯計算新配置的總桌子數量（替代 calculateTotalTables 方法）
                newTotalTables = 0;
                if (newConfig != null) {
                    for (Integer qty : newConfig.values()) {
                        if (qty != null) {
                            newTotalTables += qty;
                        }
                    }
                    originalTableCount = original.getTableCount();

                    // 2️ 仅当数量增加时才调整菜品数量
                    if (newTotalTables != null && originalTableCount != null &&
                            newTotalTables > originalTableCount && originalTableCount > 0) {

                        adjustGroupedTableOrderItems(reservationId, originalTableCount, newTotalTables);
                    }
                }

                if (newTotalTables < originalCount) {
                    throw new IllegalArgumentException(
                            "⚠️ 聚餐桌不能減少桌子數量！\n\n" +
                                    "📊 當前配置：" + originalCount + " 張 6 人桌（" + original.getTableConfigDesc() + "）\n" +
                                    "📊 新配置：" + newTotalTables + " 張桌子\n\n" +
                                    "💡 聚餐桌規則：\n" +
                                    "   • 聚餐桌必須 ≥3 張 6 人桌，只能增加不能減少\n" +
                                    "   • 如需減少桌子，請【取消預約】後重新創建"
                    );
                }

                //  額外驗證：聚餐桌必須全是 6 人桌
                if (newConfig.containsKey("2") || newConfig.containsKey("4")) {
                    throw new IllegalArgumentException(
                            "⚠️ 聚餐桌只能使用 6 人桌！\n\n" +
                                    "📋 當前配置包含非 6 人桌，請移除 2 人桌/4 人桌"
                    );
                }

                System.out.println(" 聚餐桌驗證通過：桌子數量 " + originalCount + " → " + newTotalTables + "（只增不減）");
            }

            // ── 構建新配置描述 ──
            StringBuilder descBuilder = new StringBuilder();

            //  直接內聯計算總桌子數量（替代 calculateTotalTables 方法）
            int totalTables = 0;
            if (newConfig != null) {
                for (Integer qty : newConfig.values()) {
                    if (qty != null) {
                        totalTables += qty;
                    }
                }
            }

            if (newConfig.containsKey("2") && newConfig.get("2") > 0) {
                descBuilder.append("2 人桌 x").append(newConfig.get("2")).append(", ");
            }
            if (newConfig.containsKey("4") && newConfig.get("4") > 0) {
                descBuilder.append("4 人桌 x").append(newConfig.get("4")).append(", ");
            }
            if (newConfig.containsKey("6") && newConfig.get("6") > 0) {
                descBuilder.append("6 人桌 x").append(newConfig.get("6")).append(", ");
            }

            if (totalTables == 0) {
                throw new IllegalArgumentException("桌子配置不能為空！");
            }

            updated.setTableConfigDesc(descBuilder.toString());
            updated.setTableCount(totalTables);

            //  根據桌子數量重新計算 groupType
            String newGroupType;
            if (totalTables == 1) {
                newGroupType = "MAIN";
            } else if (totalTables == 2) {
                newGroupType = "MERGED";
            } else {
                newGroupType = "GROUP";  // ≥3 張 → 聚餐桌
            }
            updated.setGroupType(newGroupType);

            hasChanges = true;
            System.out.println(" 修改桌子配置: " + original.getTableConfigDesc() + " → " + descBuilder);
            System.out.println(" 餐桌類型更新: " + original.getGroupType() + " → " + newGroupType);
        }
        if (edits.containsKey("preOrder")) {
            Boolean newPreOrder = (Boolean) edits.get("preOrder");
            if (original.getPreOrder() && !newPreOrder) {
                throw new IllegalArgumentException("预点餐状态不能从'是'改为'否'！");
            }
            if (!original.getPreOrder() && newPreOrder) {
                updated.setPreOrder(true);
                hasChanges = true;
                System.out.println(" 修改预点餐: 否 → 是");

                // 【核心修复】传入修改后的预付参数（使用 updated 对象的新值）
                Boolean finalIsPrepaid = updated.getIsPrepaid();
                Double finalPrepaidAmount = updated.getPrepaidAmount();
                createPreOrderRecord(reservationId, finalIsPrepaid, finalPrepaidAmount);
            }
        }

        // ── 修改预付金额 ──
        if (edits.containsKey("prepaidAmount")) {
            Double newPrepaidAmount = (Double) edits.get("prepaidAmount");
            Double originalPrepaid = original.getPrepaidAmount() != null ?
                    original.getPrepaidAmount() : 0.0;
            if (newPrepaidAmount < originalPrepaid) {
                throw new IllegalArgumentException(
                        "预付金额只能增加，不能减少！\n" +
                                "原金额：" + String.format("%.2f", originalPrepaid) + " 元\n" +
                                "新金额：" + String.format("%.2f", newPrepaidAmount) + " 元"
                );
            }
            if (newPrepaidAmount > originalPrepaid) {
                updated.setIsPrepaid(true);
                updated.setPrepaidAmount(newPrepaidAmount);
                hasChanges = true;
                System.out.println(" 修改预付金额: " + originalPrepaid + " → " + newPrepaidAmount);
                syncPrepaidToOrder(reservationId, true, newPrepaidAmount);
            }
        }

        // ── 修改备注 ──
        if (edits.containsKey("notes")) {
            String newNotes = (String) edits.get("notes");
            String originalNotes = original.getNotes() != null ? original.getNotes() : "";
            updated.setNotes(newNotes);
            hasChanges = true;
            System.out.println(" 修改备注: " + originalNotes + " → " + newNotes);
        }

        // 4 验证：至少有一项修改
        if (!hasChanges) {
            throw new IllegalArgumentException("没有实际修改内容！");
        }

        // 5 执行数据库更新
        int updatedRows = reservationMapper.updateReservation(updated);
        if (updatedRows == 0) {
            throw new RuntimeException("更新预约记录失败");
        }

        System.out.println(" 预约修改成功: " + reservationId);
    }

    /**
     * 比较新旧桌子配置是否相同
     *
     * 功能说明：
     * 1. 解析原始配置描述字符串（如"2 人桌 x1, 4 人桌 x2, "）为容量 - 数量映射
     * 2. 与新配置映射逐项比对容量键与数量值
     * 3. 返回是否完全一致，用于校验用户是否实际修改了桌型配置
     *
     * @param newConfig 新桌子配置映射，键为容量字符串（"2"/"4"/"6"），值为对应数量
     * @param originalDesc 原始配置描述字符串
     * @return true=配置完全相同，false=存在差异
     */
    private boolean isTableConfigSame(Map<String, Integer> newConfig, String originalDesc) {
        if (originalDesc == null || originalDesc.isEmpty()) {
            return newConfig.isEmpty();
        }

        // 解析原配置描述（如："2人桌 x1, 4人桌 x2, "）
        Map<String, Integer> originalConfig = new HashMap<>();
        String[] parts = originalDesc.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // 解析 "2人桌 x1" 或 "4人桌 x2"
            if (part.contains("人桌") && part.contains("x")) {
                String[] segments = part.split("x");
                if (segments.length == 2) {
                    String capacity = segments[0].replaceAll("[^0-9]", "");
                    String qty = segments[1].trim();
                    if (!capacity.isEmpty() && !qty.isEmpty()) {
                        try {
                            originalConfig.put(capacity, Integer.parseInt(qty));
                        } catch (NumberFormatException e) {
                            // 忽略
                        }
                    }
                }
            }
        }

        // 比较两个 Map
        if (newConfig.size() != originalConfig.size()) {
            return false;
        }

        for (Map.Entry<String, Integer> entry : newConfig.entrySet()) {
            Integer originalQty = originalConfig.get(entry.getKey());
            if (originalQty == null || !originalQty.equals(entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 创建预点餐订单记录
     *
     * 功能说明：
     * 1. 检查是否已存在该预约关联的预点餐订单，避免重复创建
     * 2. 构建订单实体：设置预约号、订单类型、下单时间、初始状态为未下单
     * 3. 设置预付信息：根据传入参数标记是否预付及预付金额
     * 4. 初始化金额字段：菜品总价与配送费设为 0，订单总额设为预付金额
     * 5. 持久化到数据库并输出创建日志
     *
     * @param reservationId 预约记录唯一标识
     * @param isPrepaid 是否已预付定金
     * @param prepaidAmount 预付定金金额
     */
    private void createPreOrderRecord(String reservationId, Boolean isPrepaid, Double prepaidAmount) {
        // 1. 先检查是否已存在预点餐订单
        Order existingOrder = orderMapper.findPreOrderByReservationId(reservationId);
        if (existingOrder != null) {
            System.out.println(" 预点餐订单已存在，跳过创建: reservationId=" + reservationId);
            return;
        }

        // 2. 构建订单实体
        Order preOrder = new Order();
        preOrder.setReservationId(reservationId);
        preOrder.setOrderType("RESERVATION");
        preOrder.setOrderTime(LocalDateTime.now());
        preOrder.setStatus("NO_ORDER");  //  初始状态：未下单

        // 3. 【核心修复】使用传入的新预付参数，而不是原始值
        preOrder.setIsPrepaid(isPrepaid != null && isPrepaid);
        preOrder.setPrepaidAmount(prepaidAmount != null ? prepaidAmount : 0.00);

        // 4. 初始化金额字段
        preOrder.setItemsTotal(0.00);
        preOrder.setDeliveryFee(0.00);
        preOrder.setTotalAmount(prepaidAmount != null ? prepaidAmount : 0.00);  //  总金额包含预付

        // 5. 插入数据库
        orderMapper.createOrder(preOrder);

        System.out.println(" 预点餐订单已创建: reservationId=" + reservationId +
                ", orderId=" + preOrder.getOrderId() +
                ", isPrepaid=" + preOrder.getIsPrepaid() +
                ", prepaidAmount=" + preOrder.getPrepaidAmount());
    }

    /**
     * 同步预付信息到关联的预点餐订单
     *
     * 功能说明：
     * 根据预约号查找关联订单，更新其预付状态与预付金额字段，确保预约与订单的财务信息一致。
     *
     * @param reservationId 预约记录唯一标识
     * @param isPrepaid 是否预付的新状态值
     * @param prepaidAmount 预付金额的新数值
     *
     * 执行时机：
     * - 预约信息修改（如切换预付状态）后调用
     * - 订单尚未创建时自动跳过，避免空指针异常
     */
    private void syncPrepaidToOrder(String reservationId, Boolean isPrepaid, Double prepaidAmount) {
        try {
            int updated = orderMapper.updatePrepaidInfoByReservationId(
                    reservationId, isPrepaid, prepaidAmount);
            if (updated > 0) {
                System.out.println("預付信息已同步到訂單: reservationId=" + reservationId +
                        ", is_prepaid=" + isPrepaid + ", prepaid_amount=" + prepaidAmount);
            } else {
                // 可能還沒有創建預點餐訂單，記錄日誌但不拋異常
                System.out.println("ℹ 未找到關聯的預點餐訂單，跳過預付信息同步: " + reservationId);
            }
        } catch (Exception e) {
            // 同步失敗不影響預約修改主流程，但記錄日誌
            System.err.println(" 同步預付信息到訂單失敗: " + e.getMessage());
            // 可選：根據業務需求決定是否拋出異常
            // throw new RuntimeException("同步預付信息失敗", e);
        }
    }

    /**
     * 调整聚餐桌预点餐的菜品数量
     *
     * 功能说明：
     * 1. 查询预约关联的预点餐订单及未上桌的订单项
     * 2. 计算新旧桌数比例因子：ratio = newTableCount / oldTableCount
     * 3. 按公式「新数量 = 原数量 × ratio」重新计算每道菜品数量，而且必然是整數
     * 4. 校验新数量能否被新桌数整除，确保菜品可平均分配至各桌
     * 5. 批量更新订单项数量，并重新计算订单总金额
     *
     * @param reservationId 预约记录唯一标识
     * @param oldTableCount 原聚餐桌数量
     * @param newTableCount 新聚餐桌数量
     *
     * 业务规则：
     * - 仅调整未上桌的菜品，已上桌部分保持不变
     * - 新数量必须能被新桌数整除，否则抛出异常提示用户重新输入
     */
    private void adjustGroupedTableOrderItems(String reservationId,
                                              int oldTableCount,
                                              int newTableCount) {
        // 1 通过 reservation_id 查询预点餐订单
        Order preOrder = orderMapper.findPreOrderByReservationId(reservationId);
        if (preOrder == null || preOrder.getOrderId() == null) {
            System.out.println(" 预约 " + reservationId + " 无关联预点餐订单，跳过数量调整");
            return;
        }
        Integer orderId = preOrder.getOrderId();

        // 2 查询该订单的所有订单项（只处理未上桌的）
        List<OrderItem> orderItems = orderItemMapper.findOrderItemsByOrderId(orderId);
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        // 3 计算比例因子：新/旧
        double ratio = (double) newTableCount / oldTableCount;

        // 4 遍历调整每个菜品数量
        for (OrderItem item : orderItems) {

            int originalQty = item.getQuantity();

            // 【按您要求】使用乘法计算新数量
            double newQtyDouble = originalQty * ratio;              // 3 × 1.33 = 4
            int newQty = (int) Math.round(newQtyDouble);

            //  验证：新数量必须能被新桌子数整除（保证平均分配）
            if (newQty % newTableCount != 0) {
                throw new IllegalArgumentException(
                        "⚠️ 菜品 [" + item.getItemCode() + "] 调整后数量 (" + newQty +
                                ") 无法被新桌子数 (" + newTableCount + ") 整除！\n" +
                                " 请确保原数量是桌子数量比例的整数倍"
                );
            }

            //  更新数据库
            orderItemMapper.updateOrderItemQuantity(item.getOrderItemId(), newQty);

//            System.out.println(" 聚餐桌菜品数量调整: " + item.getItemCode() +
//                    " | 原数量:" + originalQty + " → 新数量:" + newQty +
//                    " | 比例因子:" + ratio);
        }

        // 5 重新计算订单总金额
        Double newTotal = orderItemMapper.recalculateOrderTotal(orderId);
        if (newTotal != null) {
            orderMapper.updateOrderTotals(orderId, newTotal, newTotal);
        }
    }

    /**
     *  根据预约号模糊查询
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findReservationsByCode(String codeFragment) {
        if (codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询 reservation_id 包含该片段的记录
        return reservationMapper.findReservationsByCodeFragment(codeFragment);
    }

    /**
     *  根据电话号码后4位查询
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findReservationsByPhone(String phoneLast4) {
        if (phoneLast4 == null || phoneLast4.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询 customer_phone 以该4位结尾的记录
        return reservationMapper.findReservationsByPhoneLast4(phoneLast4);
    }

    /**
     *  根据预约号片段查询完整预约详情（支持模糊查询） 修改專用
     */
    @Transactional(readOnly = true)
    public List<TableReservation> findReservationsByCodeFragment(String codeFragment) {
        if (codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return reservationMapper.findDetailByCodeFragment(codeFragment);
    }


    /**
     *  CANCEL 模式专用：根据预约号片段查询（支持所有状态）
     */
    @Transactional(readOnly = true)
    public List<TableReservation> findReservationsForCancel(String codeFragment) {
        if (codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return reservationMapper.findDetailByCodeFragmentForCancel(codeFragment);
    }


    /**
     * 取消预约记录并执行关联清理操作
     *
     * 功能说明：
     * 1. 查询预约记录：根据预约号获取完整预约详情，校验存在性
     * 2. 状态校验：已完成状态的预约不可取消，直接返回错误
     * 3. 删除预点餐订单：若预约标记为已预点餐，调用 deletePreOrderIfExists 清理关联订单数据
     * 4. 处理定金没收：若预约含预付定金且已预点餐，记录没收明细并累加当日取消预付金额统计
     * 5. 释放预留餐桌：调用 releaseReservedTables 将关联餐桌状态重置为空闲、类型恢复为主桌
     * 6. 删除预约主记录：执行数据库删除操作，确保预约数据彻底移除
     * 7. 构建返回结果：包含成功标志、刷新提示、预点餐删除标志、定金没收标志及用户友好消息
     *
     * @param reservationId 待取消的预约记录唯一标识
     * @param cancellationReason 用户输入的取消原因，用于定金没收记录
     * @return 操作结果映射，包含以下字段：
     *         - success: boolean，操作是否成功
     *         - message: String，系统提示消息
     *         - needRefresh: boolean，是否需刷新界面
     *         - preOrderDeleted: boolean，是否删除了预点餐订单
     *         - depositForfeited: boolean，是否没收了定金
     *         - forfeitedAmount: Double，没收的定金金额（若无则为 null）
     *         - userMessage: String，面向用户的友好提示文本
     */
    @Transactional(rollbackFor = Exception.class)
    //4.6精確異常處理與 Fail-Fast 機制
    //技術說明：採用快速失敗（Fail-Fast）設計，在方法入口或關鍵節點進行嚴格的參數與狀態校驗，無效則立即拋出 IllegalArgumentException 或 IllegalStateException。
    //提前攔截非法狀態，減少無效的數據庫交互。返回 Map 結構的結果對象，將成功標誌、錯誤訊息、場景標誌（如 preOrderDeleted）統一封裝，方便 Controller 層構建對話框。
    public Map<String, Object> cancelReservation(String reservationId, String cancellationReason) {
        Map<String, Object> result = new HashMap<>();

        try {
            // ===== 步骤 1：查询预约记录 =====
            TableReservation reservation = reservationMapper.findDetailById(reservationId);
            if (reservation == null) {
                result.put("success", false);
                result.put("message", "预约记录不存在：" + reservationId);
                return result;
            }

            // ===== 步骤 2：状态验证 =====
            String status = reservation.getStatus();
            if ("COMPLETED".equals(status)) {
                result.put("success", false);
                result.put("message", "当前预约状态不可取消：" + getStatusText(status));
                return result;
            }


            // 【步骤 3】先删除预点餐订单（如果有 preOrder=true）
            boolean preOrderDeleted = false;  // 标志：是否删除了预点餐
            if (Boolean.TRUE.equals(reservation.getPreOrder())) {
                deletePreOrderIfExists(reservationId);
                preOrderDeleted = true;  // 标记为已删除
                System.out.println(" 预点餐订单已删除：" + reservationId);
            }


            // 【步骤 4】处理预付定金没收（ 核心修复：必须先检查 preOrder）
            // 业务规则：定金只能和预点餐一起存在，无预点餐=无定金
            Double forfeitedAmount = 0.0;
            boolean depositForfeited = false;  // 标志：是否没收了定金
            if (Boolean.TRUE.equals(reservation.getPreOrder()) &&      // 先检查预点餐
                    Boolean.TRUE.equals(reservation.getIsPrepaid()) &&     // 再检查是否预付
                    reservation.getPrepaidAmount() != null &&
                    reservation.getPrepaidAmount() > 0) {

                forfeitedAmount = reservation.getPrepaidAmount();
                String reason = (cancellationReason != null && !cancellationReason.trim().isEmpty())
                        ? cancellationReason.trim()
                        : "顾客主动取消预约";

                recordForfeitedDeposit(reservation, forfeitedAmount, reason);
                businessStatusMapper.incrementDailyCancelledPrepaidAmount(
                        java.sql.Date.valueOf(LocalDate.now()),
                        forfeitedAmount
                );
                depositForfeited = true;  // 标记为已没收
                System.out.println(" 没收定金已记录：" + forfeitedAmount);
            }

            // ===== 步骤 5：释放已锁定的餐桌 =====
            if (reservation.getReservedTableIds() != null &&
                    !reservation.getReservedTableIds().isEmpty()) {
                releaseReservedTables(reservation.getReservedTableIds());
                System.out.println(" 餐桌状态已重置");
            }

            // ===== 步骤 6：删除预约主记录（最后执行！）=====
            int deleted = reservationMapper.delete(reservationId);
            if (deleted == 0) {
                throw new RuntimeException("删除预约记录失败");
            }

            // 【步骤 7】组装返回结果（新增场景标志 + 用户友好消息）
            result.put("success", true);
            result.put("needRefresh", true);

            //  场景标志（供 Controller 判断提示内容）
            result.put("preOrderDeleted", preOrderDeleted);      // 是否删除了预点餐
            result.put("depositForfeited", depositForfeited);    // 是否没收了定金
            result.put("forfeitedAmount", forfeitedAmount > 0 ? forfeitedAmount : null);

            // 用户友好消息（默认兜底，Controller 可覆盖）
            String userMessage = buildUserMessage(preOrderDeleted, depositForfeited, forfeitedAmount);
            result.put("userMessage", userMessage);

            //  保留原有 message 字段（向后兼容）
            result.put("message", "预约已取消并删除");

            System.out.println(" 预约取消完成：" + reservationId +
                    (forfeitedAmount > 0 ? " | 没收定金：" + forfeitedAmount : ""));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "系统错误：" + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 根据取消预约的执行结果构建用户提示消息
     * 四种场景：
     * 1. 仅取消预约 → " 预约已取消！"
     * 2. 取消 + 删预点餐 → " 预约已取消，预点餐订单已删除。"
     * 3. 取消 + 没收定金 → " 预约已取消，并没收定金：100.00 元。"
     * 4. 取消 + 删预点餐 + 没收定金 → " 预约已取消，预点餐订单已删除，并没收定金：100.00 元。"
     *   @param preOrderDeleted 是否删除了预点餐订单
     *   @param depositForfeited 是否没收了定金
     *   @param forfeitedAmount 没收的定金金额
     *   @return 拼接完成的中文提示字符串，用于界面弹窗展示
     */
    private String buildUserMessage(boolean preOrderDeleted, boolean depositForfeited, Double forfeitedAmount) {
        if (!preOrderDeleted && !depositForfeited) {
            return " 预约已取消！";
        } else if (preOrderDeleted && !depositForfeited) {
            return " 预约已取消，预点餐订单已删除。";
        } else if (!preOrderDeleted && depositForfeited) {
            return " 预约已取消，并没收定金：" + String.format("%.2f", forfeitedAmount) + " 元。";
        } else { // 两者都有
            return " 预约已取消，预点餐订单已删除，并没收定金：" +
                    String.format("%.2f", forfeitedAmount) + " 元。";
        }
    }

    /**
     * 将预约状态英文枚举值转换为中文显示文本
     *
     * @param status 数据库存储的状态字符串（如 "PRE_CONFIRMED"）
     * @return 对应的中文描述（如 "待确认"）；未知状态时返回原文或"未知"
     */
    private String getStatusText(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "PRE_CONFIRMED" -> "待确认";
            case "CONFIRMED" -> "已确认";
            case "NO_SHOW" -> "未到店";
            case "DELAYED" -> "已延迟";
            default -> status;
        };
    }

    /**
     * 记录预约取消时的没收定金信息
     *
     * @param reservation 被取消的预约记录对象
     * @param amount 没收的定金金额
     * @param reason 没收原因说明
     */
    private void recordForfeitedDeposit(TableReservation reservation, Double amount, String reason) {
        try {
            reservationMapper.insertForfeitedDeposit(
                    reservation.getReservationId(),
                    reservation.getCustomerName(),
                    reservation.getCustomerPhone(),
                    reservation.getReservationTime(),
                    amount,
                    reason
            );
            System.out.println(" 没收定金已记录: 预约号=" + reservation.getReservationId() +
                    ", 金额=" + amount + ", 原因=" + reason);
        } catch (Exception e) {
            //  记录失败时打印日志，但不中断取消流程（避免影响用户体验）
            System.err.println(" 记录没收定金失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 释放预约关联的已锁定餐桌
     *
     * @param reservedTableIds 预留餐桌编号字符串，格式如 "7,8,9"
     *
     * 功能说明：
     * 遍历餐桌编号列表，将每张餐桌的状态重置为空闲、类型恢复为主桌，
     * 并同步更新内存缓存，确保后续分配操作可正常使用。
     */
    private void releaseReservedTables(String reservedTableIds) {
        if (reservedTableIds == null || reservedTableIds.isEmpty()) {
            return;
        }

        String[] tableDisplayIds = reservedTableIds.split(",");
        for (String displayId : tableDisplayIds) {
            String trimmedId = displayId.trim();
            if (trimmedId.isEmpty()) continue;

            try {
                Tables table = tablesMapper.findByDisplayId(trimmedId);
                if (table != null) {
                    // 【调用新方法重置餐桌状态
                    tablesMapper.resetTableAfterReservationCancel(table.getTableId());

                    System.out.println(" 餐桌状态已重置: #" + trimmedId +
                            " [RESERVED→VACANT, " + table.getTableType() + "→MAIN]");

                    //  同步更新内存缓存
                    Tables memoryTable = tableMap.get(trimmedId);
                    if (memoryTable != null) {
                        memoryTable.setStatus(Tables.TableStatus.VACANT);
                        // 如果是合并/聚餐，类型改回 MAIN
                        if (memoryTable.getTableType() != Tables.TableType.MAIN) {
                            memoryTable.setTableType(Tables.TableType.MAIN);
                            memoryTable.setMergedWith(null);
                            memoryTable.setGroupWith(null);
                        }
                        // 清空预约相关字段
                        memoryTable.setCurrentReservationId(null);
                        memoryTable.setReservedTime(null);
                        memoryTable.setCurrentGroupId(null);
                        memoryTable.setActualSeats(0);
                    }
                }
            } catch (Exception e) {
                System.err.println(" 释放餐桌失败: " + trimmedId + " - " + e.getMessage());
                // 不中断流程，继续处理其他餐桌
            }
        }
    }

    /**
     * 删除预约关联的预点餐订单（若存在）
     *
     * @param reservationId 预约记录唯一标识
     *
     * 功能说明：
     * 查询是否存在与该预约关联的预点餐订单，若存在则先删除订单明细再删除订单主记录，
     * 确保预约取消时不会遗留无效的订单数据。
     */
    private void deletePreOrderIfExists(String reservationId) {
        try {
            // 1 先查询是否存在预点餐订单
            Order preOrder = orderMapper.findPreOrderByReservationId(reservationId);
            if (preOrder == null) {
                System.out.println(" 预约 " + reservationId + " 无关联预点餐订单，跳过订单删除");
                return;
            }

            Integer orderId = preOrder.getOrderId();
            System.out.println(" 找到预点餐订单: orderId=" + orderId +
                    ", reservationId=" + reservationId);

            // 2️ 删除订单明细（order_items）- 必须先删子表
            int itemsDeleted = orderItemMapper.deleteOrderItemsByOrderId(orderId);
            System.out.println("️ 已删除订单明细: " + itemsDeleted + " 条");

            // 3 删除订单主记录（table_orders）
            int orderDeleted = orderMapper.deleteOrder(orderId);
            System.out.println(" 已删除预点餐订单主记录: orderId=" + orderId);

        } catch (Exception e) {
            // 订单删除失败不影响预约取消主流程，但记录日志
            System.err.println(" 删除预点餐订单失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 延迟预约记录并处理关联餐桌释放
     *
     * 功能说明：
     * 1. 校验预约记录存在性、延迟时间有效性及当前状态是否允许延迟
     * 2. 计算延迟时长，判断是否需更新 within_15h 标记及预约状态
     * 3. 根据餐桌分组类型重新生成配置描述，更新预约记录的延迟时间、状态、配置等信息
     * 4. 若延迟超过 30 分钟且未保留餐桌，则释放预留餐桌并清理订单中的分配信息
     * 5. 聚餐桌特殊处理：查找关联活跃订单，清空订单明细的分配餐桌与数量分布字段
     * 6. 返回执行结果，包含成功标志、提示消息、新时间、是否释放餐桌等关键信息
     *
     * @param reservationId 预约记录唯一标识
     * @param newTime 新的预约时间
     * @param keepTable 是否保留原预留餐桌
     * @return 操作结果映射，包含 success、message、newTime、releaseTables 等字段
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delayReservation(String reservationId, LocalDateTime newTime, boolean keepTable) {
        Map<String, Object> result = new HashMap<>();// 创建结果映射对象，用于返回操作状态与消息

        try {
            // 1. 查询预约记录
            TableReservation reservation = reservationMapper.findDetailById(reservationId);
            if (reservation == null) {
                result.put("success", false);
                result.put("message", "预约记录不存在：" + reservationId);
                return result;
            }

            // 2. 验证时间
            LocalDateTime now = LocalDateTime.now();
            if (newTime.isBefore(now)) {
                result.put("success", false);
                result.put("message", "延迟时间不能是过去的时间！");
                return result;
            }

            // 3. 验证状态
            String status = reservation.getStatus();
            if ("COMPLETED".equals(status) || "NO_SHOW".equals(status)) {
                result.put("success", false);
                result.put("message", "当前预约状态不可延迟：" + getStatusText(status));
                return result;
            }

            // 4. 计算延迟时长
            long delayMinutes = java.time.Duration.between(now, newTime).toMinutes();

            // 5. 计算新的 within_15h 值
            Boolean newWithin15h = reservation.getWithin15h();
            if (Boolean.TRUE.equals(reservation.getWithin15h())) {
                long minutesFromCreated = java.time.Duration.between(
                        reservation.getCreatedAt(), newTime).toMinutes();
                if (minutesFromCreated > 90) {
                    newWithin15h = false;
                }
            }

            // 6. 决定是否释放餐桌
            boolean releaseTables = (!keepTable && delayMinutes >= 30);

            // 7. 确定新状态
            String newStatus = reservation.getStatus();
            if (delayMinutes >= 30 && !keepTable) {
                newStatus = "DELAYED";
            }

            // 8. 根据 group_type 重新生成 table_config_desc
            String groupType = reservation.getGroupType();  // MAIN / MERGED / GROUP
            String newConfigDesc = generateTableConfigDesc(groupType, reservation);

            String newTableSelectionMode = releaseTables ? "QUANTITY" : reservation.getTableSelectionMode();

            // 9. 更新预约记录（传入新的 configDesc）
            int updated = reservationMapper.updateReservationForDelay(
                    reservationId, newTime, newStatus, newWithin15h, newConfigDesc, newTableSelectionMode, releaseTables);
            if (updated == 0) {
                throw new RuntimeException("更新预约记录失败");
            }

            // 10. 如果需要释放餐桌，执行释放逻辑
            if (releaseTables && reservation.getReservedTableIds() != null &&
                    !reservation.getReservedTableIds().isEmpty()) {


                // 【新增核心逻辑】聚餐桌 (GROUPED) 特殊处理
                String[] tableIds = reservation.getReservedTableIds().split(",");
                boolean isGroupedTableFound = false;
                Integer foundOrderId = null;

                // 1. 遍历餐桌，检查是否存在 GROUPED 类型的餐桌
                for (String displayId : tableIds) {
                    Tables table = tablesMapper.findByDisplayId(displayId.trim());
                    if (table != null && table.getTableType() == Tables.TableType.GROUPED) {
                        isGroupedTableFound = true;
                        break; // 只要发现一张是聚餐桌，就需要执行后续逻辑
                    }
                }

                // 2. 如果是聚餐桌，必须找到关联的活跃订单
                if (isGroupedTableFound) {
                    // 通过 reservation_id 查找活跃订单 ID
                    foundOrderId = orderMapper.findActiveOrderIdByReservationId(reservationId);

                    // 【中止条件】如果没有找到订单，直接抛出异常中止操作
                    if (foundOrderId == null) {
                        System.out.println("聚餐桌模式下未找到关联的活跃订单 (reservationId: " + reservationId + ")，无法释放餐桌！");
                    }

                    // 3. 成功匹配后，清理 order_items 中的分配信息
                    // 将 assigned_table_display_id 和 quantity_distribution 设置为 NULL
                    int updatedCount = orderItemMapper.clearDistributionByOrderId(foundOrderId);
                  //  System.out.println("🧹 已清理聚餐桌订单明细的分配信息: orderId=" + foundOrderId + ", 更新行数=" + updatedCount);
                }
                // ═══════════════════════════════════════════════════════════

                // 4. 执行原有的释放餐桌逻辑
                releaseReservedTables(reservation.getReservedTableIds());
             //   System.out.println(" 延迟预约释放餐桌: " + reservation.getReservedTableIds());

                // 5. 清空订单中的 table_id 关联
                clearTableIdByReservationId(reservationId);
            }

            result.put("success", true);    // 构建成功返回结果：设置成功标志
            result.put("message", "预约延迟成功！新时间: " +
                    newTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));    // 设置成功提示消息，包含格式化后的新时间
            result.put("newTime", newTime);    // 返回新时间对象
            result.put("keepTable", keepTable);
            result.put("releaseTables", releaseTables);
            result.put("newStatus", newStatus);
            result.put("needRefresh", releaseTables);      // 设置刷新标志：若释放餐桌则需刷新界面

            System.out.println(" 预约延迟完成: " + reservationId +
                    " → " + newTime +
                    " | 延迟:" + delayMinutes + "分钟" +
                    (releaseTables ? " | 已释放餐桌" : " | 保留餐桌锁定") +
                    " | within_15h:" + newWithin15h +
                    " | configDesc:" + newConfigDesc);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "系统错误: " + e.getMessage());
            e.printStackTrace();
        }

        return result;// 返回最终结果映射
    }

    /**
     * 根据餐桌类型生成格式化配置描述
     *
     * 功能说明：
     * 1. 校验预约记录及预留餐桌编号有效性
     * 2. 根据 group_type 字段分发至对应生成方法：
     *    - MAIN：调用 generateMainTableConfigDescFromCache 生成个人桌描述
     *    - MERGED：调用 generateMergedTableConfigDescFromCache 生成合并桌描述
     *    - GROUP：调用 generateGroupedTableConfigDescFromCache 生成聚餐桌描述
     * 3. 未知类型或参数异常时返回原始配置描述作为兜底
     *
     * @param groupType 餐桌分组类型（MAIN / MERGED / GROUP）
     * @param reservation 预约记录对象，包含预留餐桌编号与原始配置
     * @return 格式化后的配置描述字符串（如"4 人桌 x2, "）
     */
    private String generateTableConfigDesc(String groupType, TableReservation reservation) {
        if (groupType == null || reservation.getReservedTableIds() == null ||
                reservation.getReservedTableIds().isEmpty()) {
            return reservation.getTableConfigDesc();  // 兜底：保持原值
        }

        switch (groupType) {
            case "MAIN":  // 个人桌：从内存获取实际容量
                return generateMainTableConfigDescFromCache(reservation);

            case "MERGED":  //  合并桌：2张相同容量的桌子
                return generateMergedTableConfigDescFromCache(reservation);

            case "GROUP":  // 聚餐桌：多张同容量桌子
                return generateGroupedTableConfigDescFromCache(reservation);

            default:
                return reservation.getTableConfigDesc();  // 未知类型保持原值
        }
    }

    /**
     * 从内存缓存生成个人桌配置描述
     *
     * 功能说明：
     * 1. 解析预约记录中的预留餐桌编号，提取第一张桌的显示编号
     * 2. 从内存缓存 tableMap 查询该餐桌对象，获取实际容量
     * 3. 生成标准格式描述字符串（如"4 人桌 x1, "）
     * 4. 缓存查询失败时回退至原始配置描述，并尝试解析容量作为兜底
     *
     * @param reservation 预约记录对象，包含 reserved_table_ids 与原始配置描述
     * @return 格式化后的个人桌配置描述字符串
     */
    private String generateMainTableConfigDescFromCache(TableReservation reservation) {
        String reservedTableIds = reservation.getReservedTableIds();
        if (reservedTableIds == null || reservedTableIds.isEmpty()) {
            return reservation.getTableConfigDesc();  // 兜底
        }

        try {
            // 从 reserved_table_ids 获取第一个桌号
            String[] tableIds = reservedTableIds.split(",");
            if (tableIds.length > 0) {
                String displayId = tableIds[0].trim();
                // 从内存缓存获取餐桌（不查数据库）
                Tables table = tableMap.get(displayId);
                if (table != null) {
                    int capacity = table.getCapacity();
                    return capacity + "人桌 x1, ";
                }
            }
        } catch (Exception e) {
            System.err.println("从内存获取餐桌容量失败: " + e.getMessage());
        }

        // 兜底：从原描述解析
        int personalCapacity = parseCapacityFromDesc(reservation.getTableConfigDesc());
        return personalCapacity + "人桌 x1, ";
    }

    /**
     * 从内存缓存生成合并桌配置描述
     *
     * 功能说明：
     * 根据预约记录中预留的餐桌编号，从内存缓存 tableMap 获取第一张餐桌的实际容量，
     * 生成格式化的合并桌配置描述字符串（如"4人桌 x2, "），用于预约匹配与显示。
     *
     * @param reservation 预约记录对象，包含 reserved_table_ids 与原始配置描述
     * @return 格式化后的配置描述字符串；缓存查询失败时回退至原始描述或解析兜底值
     */
    private String generateMergedTableConfigDescFromCache(TableReservation reservation) {
        String reservedTableIds = reservation.getReservedTableIds();
        if (reservedTableIds == null || reservedTableIds.isEmpty()) {
            return reservation.getTableConfigDesc();  // 兜底
        }

        try {
            // 从 reserved_table_ids 获取桌号
            String[] tableIds = reservedTableIds.split(",");
            if (tableIds.length >= 2) {
                String displayId = tableIds[0].trim();
                // 【关键】从内存缓存获取餐桌（不查数据库）
                Tables table = tableMap.get(displayId);
                if (table != null) {
                    int capacity = table.getCapacity();
                    return capacity + "人桌 x2, ";
                }
            }
        } catch (Exception e) {
            System.err.println("从内存获取合并桌容量失败: " + e.getMessage());
        }

        // 兜底：从原描述解析
        int mergedCapacity = parseCapacityFromDesc(reservation.getTableConfigDesc());
        return mergedCapacity + "人桌 x2, ";
    }

    /**
     * 从内存缓存生成聚餐桌配置描述
     *
     * 功能说明：
     * 根据预约记录中预留的餐桌编号列表，从内存缓存获取第一张餐桌的实际容量与桌数，
     * 生成格式化的聚餐桌配置描述字符串（如"6人桌 x3, "），用于预约匹配与显示。
     *
     * @param reservation 预约记录对象，包含 reserved_table_ids 与原始配置描述
     * @return 格式化后的配置描述字符串；缓存查询失败时回退至原始配置描述
     */
    private String generateGroupedTableConfigDescFromCache(TableReservation reservation) {
        String reservedTableIds = reservation.getReservedTableIds();
        if (reservedTableIds == null || reservedTableIds.isEmpty()) {
            return reservation.getTableConfigDesc();  // 兜底
        }

        try {
            // 1. 解析餐桌ID列表
            String[] tableIdArray = reservedTableIds.split(",");
            if (tableIdArray.length == 0) {
                return reservation.getTableConfigDesc();
            }

            // 2. 从第一张桌获取容量（聚餐桌所有桌容量相同）
            String firstTableId = tableIdArray[0].trim();
            Tables firstTable = tableMap.get(firstTableId);

            if (firstTable != null) {
                int capacity = firstTable.getCapacity();
                int tableCount = tableIdArray.length;

                // 【调试日志】
//                System.out.println(" 聚餐桌配置: reservationId=" + reservation.getReservationId() +
//                        ", capacity=" + capacity + "人桌" +
//                        ", tableCount=" + tableCount +
//                        ", reservedTableIds=" + reservedTableIds);

                return capacity + "人桌 x" + tableCount + ", ";
            }
        } catch (Exception e) {
            System.err.println("从内存获取聚餐桌容量失败: " + e.getMessage());
        }

        // 兜底：使用原配置描述
        return reservation.getTableConfigDesc();
    }

    /**
     * 从配置描述字符串中解析餐桌容量数字
     *
     * 功能说明：
     * 提取配置描述中"人桌"前的数字作为餐桌容量（如"4人桌 x2, " → 4），
     * 用于缓存生成失败时的兜底逻辑或历史数据兼容处理。
     *
     * @param configDesc 餐桌配置描述字符串
     * @return 解析出的容量数字；解析失败或输入为空时返回默认值 2
     */
    private int parseCapacityFromDesc(String configDesc) {
        if (configDesc == null || configDesc.isEmpty()) {
            return 2;  // 默认返回2人桌
        }
        try {
            // 提取"人桌"前面的数字
            String[] parts = configDesc.split("人桌");
            if (parts.length > 0) {
                String capacityStr = parts[0].replaceAll("[^0-9]", "");
                if (!capacityStr.isEmpty()) {
                    return Integer.parseInt(capacityStr);
                }
            }
        } catch (Exception e) {
            System.err.println("解析餐桌容量失败: " + configDesc);
        }
        return 2;  // 解析失败时兜底返回2
    }

    /**
     * 根据预约ID清空订单的餐桌ID
     *
     * @param reservationId 预约ID
     */
    @Transactional
    public void clearTableIdByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return;
        }

        int updated = orderMapper.clearTableIdByReservationId(reservationId);
        if (updated > 0) {
            System.out.println(" 已清空订单餐桌ID: reservationId=" + reservationId +
                    ", 影响行数=" + updated);
        }
    }

    /**
     * 刷新数量模式预约缓存
     *
     * 功能说明：
     * 从数据库查询所有处于"预确认"或"延期"状态、且采用数量选择模式的预约记录，
     * 解析其餐桌配置需求后存入内存缓存，供后续餐桌空闲时快速匹配通知。
     *
     * 执行流程：
     * 1. 查询数据库：获取数量模式预约的完整信息列表
     * 2. 清空缓存：移除旧数据，确保缓存与数据库状态同步
     * 3. 遍历过滤：仅保留选择模式为 QUANTITY、状态为有效、配置描述非空的记录
     * 4. 解析配置：将餐桌配置字符串（如"4人桌x2"）解析为容量与数量匹配信息
     * 5. 填充缓存：将解析成功的预约对象按预约号存入 quantityReservationCache
     *
     * 数据来源：
     * - reservationMapper.findQuantityModeReservationsForLog 查询预约记录
     * - parseReservationConfig 解析餐桌配置描述
     * - parseReservationTime 统一时间类型转换
     *
     * 调用时机：
     * - 餐桌清理操作完成后自动触发，确保新空闲餐桌能及时匹配待入座预约
     * - 系统启动或预约状态变更时可手动调用，保持缓存实时性
     */
    @Transactional(readOnly = true)
    public void refreshQuantityReservationCache() {
        List<Map<String, Object>> reservations = reservationMapper.findQuantityModeReservationsForLog();

        //  关键调试日志
        //  System.out.println(" [CACHE REFRESH] 查询结果数: " + reservations.size());

        quantityReservationCache.clear();
        int parsedCount = 0;
        int filteredCount = 0;

        for (Map<String, Object> res : reservations) {
            String resId = (String) res.get("reservation_id");
            String statusStr = (String) res.get("status");
            String configDesc = (String) res.get("table_config_desc");
            String selectionMode = (String) res.get("table_selection_mode");

            //  记录每条记录的过滤原因
            if (!"QUANTITY".equals(selectionMode)) {
                System.out.println(" 过滤 [" + resId + "]: selection_mode=" + selectionMode);
                filteredCount++;
                continue;
            }
            if (!"PRE_CONFIRMED".equals(statusStr) && !"DELAYED".equals(statusStr)) {
                System.out.println(" 过滤 [" + resId + "]: status=" + statusStr);
                filteredCount++;
                continue;
            }
            if (configDesc == null || configDesc.isEmpty()) {
                System.out.println(" 过滤 [" + resId + "]: configDesc 为空");
                filteredCount++;
                continue;
            }

            ReservationMatchInfo info = parseReservationConfig(configDesc);
            if (info != null) {
                info.reservationId = resId;
                info.reservationTime = parseReservationTime(res.get("reservation_time"));
                info.customerName = (String) res.get("customer_name");
                info.customerPhone = (String) res.get("customer_phone");
                quantityReservationCache.put(resId, info);
                // System.out.println(" 缓存成功: " + resId + " | " + info.requiredCapacity + "人×" + info.requiredCount);
                parsedCount++;
            } else {
                //  System.out.println(" 解析失败 [" + resId + "]: configDesc=[" + configDesc + "]");
                filteredCount++;
            }
        }

//        System.out.println("📊 缓存统计: 成功=" + parsedCount + ", 过滤=" + filteredCount +
//                ", 最终缓存大小=" + quantityReservationCache.size());
    }

    /**
     * 解析预约配置字符串为匹配信息对象
     *
     * 功能说明：
     * 将描述预约需求的配置文本（如"4人桌x2"）解析为包含所需容量和数量的匹配信息对象。
     *
     * @param configDesc 预约配置描述字符串
     * @return 解析成功的 ReservationMatchInfo 对象；格式错误或无法识别时返回 null
     */
    private ReservationMatchInfo parseReservationConfig(String configDesc) {
        if (configDesc == null || configDesc.isEmpty()) return null;

        ReservationMatchInfo info = new ReservationMatchInfo();

        // 标准化：移除空格
        String normalized = configDesc.replaceAll("\\s+", "");

        // 解析容量（ 修复：使用不带空格的字符串）
        if (normalized.contains("2人桌")) {
            info.requiredCapacity = 2;
        } else if (normalized.contains("4人桌")) {
            info.requiredCapacity = 4;
        } else if (normalized.contains("6人桌")) {
            info.requiredCapacity = 6;
        } else {
            return null;  // 无法识别容量
        }

        // 提取数量（支持 "x1" 格式）
        if (normalized.contains("x")) {
            String[] parts = normalized.split("x");
            if (parts.length > 1) {
                try {
                    info.requiredCount = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    info.requiredCount = 1;
                }
            }
        }

        return info.requiredCount > 0 ? info : null;
    }

    /**
     * 解析预约时间对象为 LocalDateTime 类型
     *
     * 功能说明：
     * 兼容处理数据库返回的 Timestamp 或内存中的 LocalDateTime 类型，统一转换为 LocalDateTime。
     *
     * @param timeObj 待转换的时间对象（Timestamp 或 LocalDateTime）
     * @return 转换后的 LocalDateTime 对象；类型不匹配或为 null 时返回 null
     */
    private LocalDateTime parseReservationTime(Object timeObj) {
        if (timeObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timeObj).toLocalDateTime();
        } else if (timeObj instanceof LocalDateTime) {
            return (LocalDateTime) timeObj;
        }
        return null;
    }

    /**
     * 检查并通知匹配的预约记录
     *
     * 功能说明：
     * 当餐桌被清理为空闲状态后，遍历预约缓存，查找容量与数量需求匹配的预约记录，
     * 若当前空闲餐桌满足预约需求，则通过回调通知界面显示匹配提醒，并移除该预约缓存。
     *
     * @param cleanedTable 刚被清理为空闲状态的餐桌对象
     *
     * 执行时机：
     * - 餐桌清理操作成功后自动调用
     * - 确保预约顾客能及时收到入座通知，提升服务响应效率
     */
    private void checkAndNotifyMatchingReservations(Tables cleanedTable) {
        // 【DEBUG】方法入口日志
//    System.out.println("🔍 [DEBUG] 开始检查预约匹配: 餐桌#" + cleanedTable.getDisplayId());
//    System.out.println("   tableType: " + cleanedTable.getTableType());
//    System.out.println("   tableStatus: " + cleanedTable.getStatus());
//    System.out.println("   matchCallback: " + (matchCallback != null ? "已设置 ✓" : "NULL! ⚠️"));

        //  先刷新缓存确保最新
        refreshQuantityReservationCache();
//    System.out.println("   缓存中预约数量: " + quantityReservationCache.size());

        int cleanedCapacity = cleanedTable.getCapacity();
//    System.out.println("   cleanedCapacity: " + cleanedCapacity + "人");

        // 遍历缓存中的预约
        for (ReservationMatchInfo info : quantityReservationCache.values()) {
//        System.out.println("    检查预约: #" + info.reservationId +
//                " | 容量:" + info.requiredCapacity + "人×" + info.requiredCount + "张" +
//                " | 客人:" + info.customerName);

            // 1 容量必须匹配
            if (info.requiredCapacity != cleanedCapacity) {
                // System.out.println("       容量不匹配 (需要:" + info.requiredCapacity + " vs 当前:" + cleanedCapacity + ")，跳过");
                continue;
            }

            //  检查当前空闲餐桌数量是否满足需求
            long availableCount = tableMap.values().stream()
                    .filter(t -> t.getStatus() == Tables.TableStatus.VACANT
                            && t.getTableType() == Tables.TableType.MAIN
                            && t.getCapacity() == cleanedCapacity)
                    .count();

            //  System.out.println("       空闲餐桌计数: " + availableCount + " / 需要: " + info.requiredCount);

            if (availableCount >= info.requiredCount) {
                //    System.out.println("       匹配条件满足！");

                //  匹配成功！通过回调通知 View 层（替代事件发布）
                if (matchCallback != null) {
                    String timeStr = info.reservationTime != null ?
                            info.reservationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "未知";

                    System.out.println("       调用 matchCallback.showReservationMatchAlert()...");
                    matchCallback.showReservationMatchAlert(
                            info.reservationId,
                            info.customerName,
                            info.customerPhone,
                            timeStr,
                            cleanedCapacity,
                            info.requiredCount
                    );
                } else {
                    System.out.println("       matchCallback 为 NULL，无法通知 View 层！");
                }

//            System.out.println(" 匹配成功: 预约#" + info.reservationId +
//                    " | 餐桌#" + cleanedTable.getDisplayId() + " 已空闲");

                //  找到匹配后移除缓存，避免重复提醒
                quantityReservationCache.remove(info.reservationId);
                System.out.println("    已移除缓存: " + info.reservationId);
                break;  // 一次清理只提醒一个预约
            } else {
                System.out.println("       空闲餐桌数量不足，继续检查下一个预约");
            }
        }
        //System.out.println(" [DEBUG] 预约匹配检查完成 ");
    }
    /**
     * 设置预约匹配回调接口
     *
     * 功能说明：
     * 注册一个回调接口实现，用于在预约匹配成功时通知视图层显示提醒对话框。
     *
     * @param callback 实现 ReservationMatchCallback 接口的对象，负责处理匹配通知的界面展示
     *
     * 设计模式：
     * - 采用回调机制解耦业务逻辑与界面展示，Service 层无需直接依赖 Swing 组件
     * - 调用方（如 Controller）在初始化时注入回调，实现职责分离
     */
    public void setReservationMatchCallback(ReservationMatchCallback callback) {
        this.matchCallback = callback;
    }

    /**
     * 純內存查詢：獲取有未結賬訂單的餐桌顯示 ID 列表
     * 規則：訂單狀態為 ORDERED_UNFINISHED 或 ORDERED_FINISHED 且餐桌狀態為 OCCUPIED
     *
     * @return 未結賬餐桌的 display_id 列表
     */
    @Transactional(readOnly = true)
    public List<String> getTablesWithUnpaidOrdersInMemory() {
        List<String> unpaidTables = new ArrayList<>();

        for (Tables table : tableMap.values()) {
            // 只檢查占用中的餐桌
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                continue;
            }

            Tables.OrderStatus orderStatus = table.getOrderStatus();
            // 未結賬狀態：已下单但未结账
            if (orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED ||
                    orderStatus == Tables.OrderStatus.ORDERED_FINISHED) {
                unpaidTables.add(table.getDisplayId());
            }
        }

        // 按餐桌編號排序（方便用戶查看）
        unpaidTables.sort(Comparator.comparingInt(id ->
                Integer.parseInt(id.replaceAll("[^0-9]", ""))
        ));

        return unpaidTables;
    }

    /**
     * 执行打烊操作并持久化状态
     *
     * 功能说明：
     * 1. 更新内存状态：将 isOpenForBusiness 标记设为 false，表示停止营业
     * 2. 持久化到数据库：更新或插入当日营业记录，设置 is_open=false 并保留下一个叫号
     * 3. 异常回滚机制：若数据库操作失败，自动恢复内存状态为营业中，并抛出异常通知调用方
     *
     * 执行时机：
     * - 每日营业结束时由管理员触发
     * - 确保内存状态与数据库记录严格一致，避免次日营业数据错乱
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeForDayWithPersistence() {
        // 1️⃣ 先更新內存狀態
        this.isOpenForBusiness = false;

        try {
            // 2️⃣ 持久化到數據庫
            LocalDate today = LocalDate.now();

            // 【修复】使用 Integer 接收，允许为 null
            Integer nextCall = businessStatusMapper.getNextCallNumber(today);

            int updated = businessStatusMapper.updateBusinessStatus(
                    today,
                    false,  // is_open = false
                    nextCall != null ? nextCall : 1  //  现在可以安全比较
            );

            if (updated == 0) {
                // 兜底：如果 UPSERT 返回 0，嘗試插入新記錄
                businessStatusMapper.insertTodayStatus(today);
            }

            System.out.println(" 打烊持久化成功：" + today + " | is_open=false");

        } catch (Exception e) {
            // 【關鍵防禦】數據庫失敗時，恢復內存狀態
            this.isOpenForBusiness = true;
            System.err.println(" 打烊持久化失敗，已恢復內存狀態：" + e.getMessage());
            throw new RuntimeException("打烊持久化失敗", e);
        }
    }

    /**
     * 执行开业操作并持久化状态
     *
     * 功能说明：
     * 1. 更新内存状态：将 isOpenForBusiness 标记设为 true，表示开始营业
     * 2. 持久化到数据库：尝试插入当日营业记录，若已存在则更新 is_open=true 及叫号状态
     * 3. 异常回滚机制：若数据库操作失败，自动恢复内存状态为打烊，并抛出异常通知调用方
     *
     * 执行时机：
     * - 每日营业开始时由管理员触发
     * - 确保新叫号从 1 开始或延续昨日进度，保证排队系统连续性
     */
    @Transactional(rollbackFor = Exception.class)
    public void openForBusinessWithPersistence() {
        this.isOpenForBusiness = true;

        try {
            LocalDate today = LocalDate.now();
            int result = businessStatusMapper.insertTodayStatus(today);

            if (result > 0) {
                System.out.println(" 創建當日營業記錄：" + today);
            } else {
                // 【修复】同样使用 Integer 接收
                Integer nextCall = businessStatusMapper.getNextCallNumber(today);
                businessStatusMapper.updateBusinessStatus(
                        today,
                        true,
                        nextCall != null ? nextCall : 1
                );
                System.out.println(" 更新當日營業狀態：" + today + " | is_open=true");
            }

            // updateQueueDisplay();

        } catch (Exception e) {
            this.isOpenForBusiness = false;
            System.err.println("❌ 開業持久化失敗，已恢復內存狀態：" + e.getMessage());
            throw new RuntimeException("開業持久化失敗", e);
        }
    }

    /**
     * 获取当前营业状态
     *
     * 功能说明：
     * 返回内存中记录的营业状态标识，供控制器或视图层查询当前是否允许顾客入座、点餐等操作。
     *
     * @return true=营业中，false=已打烊
     *
     * 数据来源：
     * - 直接读取内存变量 isOpenForBusiness，避免频繁查询数据库，提升响应速度
     * - 该变量由 openForBusinessWithPersistence / closeForDayWithPersistence 方法维护一致性
     */
    @Transactional(readOnly = true)
    public boolean isOpenForBusiness() {
        return this.isOpenForBusiness;
    }

    /**
     * 检查是否存在等待入座的顾客
     *
     * 功能说明：
     * 遍历 2 人桌、4 人桌、6 人桌三个排队队列，判断是否有顾客仍在等待分配餐桌。
     *
     * @return true=至少有一个队列非空，存在等待顾客；false=所有队列均为空
     *
     * 应用场景：
     * - 打烊前校验：若仍有等待顾客，提示管理员先处理队列再执行打烊
     * - 界面提示：队列面板显示"暂无等待顾客"或"当前等待 X 组"
     * - 自动叫号：仅当队列非空且营业中时触发叫号逻辑
     */
    public boolean hasWaitingCustomers() {
        return !queue2Seat.isEmpty() || !queue4Seat.isEmpty() || !queue6Seat.isEmpty();
    }

    /**
     * 尝试将顾客分配到餐桌的统一入口方法
     *
     * 功能说明：
     * 根据调用参数判断业务场景，将顾客或顾客组分配到指定餐桌，支持排队分配、合并桌、聚餐桌、共享桌、追加客人等多种模式。
     *
     * 执行流程：
     * 1. 排队模式（isFromQueue=true）：
     *    - 根据叫号查询排队顾客组，验证存在性与未入座状态
     *    - 使用顾客组实际人数，委托 assignQueuedGroupToTable 执行分配
     * 2. 合并桌模式（isMerge=true）：
     *    - 解析两个餐桌编号，校验存在性、空闲状态、容量匹配、编号连续且同行
     *    - 调用 assignMergedTables 执行合并分配
     * 3. 聚餐桌模式（isGrouped=true）：
     *    - 解析至少3张餐桌编号，校验均为6人桌、空闲状态、桌号连续
     *    - 计算总容量（6人×桌数），调用 assignGroupedTables 执行分配
     * 4. 共享桌模式（isShare=true）：
     *    - 校验目标餐桌为占用中主桌，容量匹配，调用 handleShareTable 执行拼桌
     * 5. 追加客人模式（isAddGuests=true）：
     *    - 调用 handleAddToExistingGroup 执行人数追加逻辑
     * 6. 普通分配：基础校验后返回成功，由调用方继续处理
     *
     * @param tableIdInput 餐桌编号输入（单桌/合并桌/聚餐桌格式）
     * @param peopleCount 顾客人数（排队模式时会被实际人数覆盖）
     * @param isFromQueue 是否为排队顾客分配
     * @param callNumber 排队叫号（排队模式时有效）
     * @param isMerge 是否为合并餐桌操作
     * @param isTwoSeat 是否选择2人桌容量
     * @param isFourSeat 是否选择4人桌容量
     * @param isSixSeat 是否选择6人桌容量
     * @param isAddGuests 是否为追加客人操作
     * @param isShare 是否为共享/拼桌操作
     * @param isGrouped 是否为聚餐桌操作
     * @param groupedTableCount 聚餐桌数量（预留参数）
     * @return 操作结果封装，包含成功标志与提示消息
     */
    public OperationResult<Boolean> tryAssignCustomerToTable(
            String tableIdInput, int peopleCount, boolean isFromQueue, int callNumber, boolean isMerge, boolean isTwoSeat, boolean isFourSeat, boolean isSixSeat, boolean isAddGuests, boolean isShare, boolean isGrouped, int groupedTableCount) {
        // ===== 1. 队列模式：优先处理排队顾客的分配 =====
        if (isFromQueue) {
            CustomerGroup group = customerGroupMapper.findByCallNumber(callNumber);

            // 验证顾客组存在性且未入座
            if (group == null || group.isAssigned()) {
                return OperationResult.error(
                        "排队号 #" + callNumber + " 不存在或已入座"
                );
            }

            // 使用顾客组实际人数
            peopleCount = group.getGroupSize();
            System.out.println(" 从队列分配：排队号#" + callNumber +
                    "，实际人数=" + peopleCount + "人");

            // 委托专用方法处理分配（该方法内部也改用 Mapper 调用）
            return assignQueuedGroupToTable(
                    group, tableIdInput, isTwoSeat, isFourSeat, isSixSeat,
                    isShare, isMerge
            );
        }
        // ===== 2. 基础验证 =====
        if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
            return OperationResult.error("餐桌编号不能为空");
        }

        //先判断模式 → 再按需查库
        // 【关键修复】先检查合并模式，避免提前查询单个餐桌
        if (isMerge) {
            System.out.println(" 进入合并分支");
            System.out.println(" 原始输入: [" + tableIdInput + "]");
            // 1. 输入验证 + 中文逗号转换
            if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
                return OperationResult.error("请输入餐桌编号（格式：7,8）");
            }

            String normalizedInput = tableIdInput.trim().replace("，", ",");
            String[] tableIds = normalizedInput.split(",");

            if (tableIds.length != 2) {
                return OperationResult.error("合并操作必须输入 2 张餐桌编号，格式：7,8");
            }

            String tableId1 = tableIds[0].trim();
            String tableId2 = tableIds[1].trim();

            // 2. 【关键】使用 Service 层方法查询（带缓存 + 顾客组关联）
            Tables table1 = getTableById(tableId1);
            Tables table2 = getTableById(tableId2);

            System.out.println("🔍 [DEBUG] 查询餐桌 1: " + (table1 != null ? tableId1 : "null"));
            System.out.println("🔍 [DEBUG] 查询餐桌 2: " + (table2 != null ? tableId2 : "null"));

            if (table1 == null) return OperationResult.error("餐桌 #" + tableId1 + " 不存在");
            if (table2 == null) return OperationResult.error("餐桌 #" + tableId2 + " 不存在");

            // 3. 状态验证
            if (table1.getStatus() != Tables.TableStatus.VACANT) {
                return OperationResult.error("餐桌 #" + tableId1 + " 状态为【" +
                        table1.getStatus().getDisplayName() + "】，必须为空闲");
            }
            if (table2.getStatus() != Tables.TableStatus.VACANT) {
                return OperationResult.error("餐桌 #" + tableId2 + " 状态为【" +
                        table2.getStatus().getDisplayName() + "】，必须为空闲");
            }

            // 4. 容量验证 + 用户选项匹配
            int expectedCapacity = isTwoSeat ? 2 : (isFourSeat ? 4 : 6);
            if (table1.getCapacity() != expectedCapacity || table2.getCapacity() != expectedCapacity) {
                return OperationResult.error("餐桌容量与选择不符，请确认选项");
            }

            // 5. 相邻验证（左右相邻，不能上下）
            try {
                int num1 = Integer.parseInt(tableId1.replaceAll("[^0-9]", ""));
                int num2 = Integer.parseInt(tableId2.replaceAll("[^0-9]", ""));
                int row1 = (num1 - 1) / 3;
                int row2 = (num2 - 1) / 3;

                if (row1 != row2 || Math.abs(num1 - num2) != 1) {
                    return OperationResult.error("餐桌 #" + tableId1 + " 和 #" + tableId2 +
                            " 不是左右相邻，无法合并（如 7+8、10+11）");
                }
            } catch (NumberFormatException e) {
                return OperationResult.error("餐桌编号格式错误");
            }

            // 6. 执行合并分配
            try {
                boolean success = assignMergedTables(table1, table2, peopleCount);
                return success ? OperationResult.success(true) : OperationResult.error("合并分配失败");
            } catch (Exception e) {
                e.printStackTrace();  //  确保异常可见
                return OperationResult.error("合并时发生错误：" + e.getMessage());
            }
        }

        // ===== 5. 聚餐桌场景 =====
        if (isGrouped) {
            // 1. 输入验证
            if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
                return OperationResult.error("请输入聚餐桌编号（格式：13,14,15）");
            }

            // 2. 解析餐桌号列表
            String normalizedInput = tableIdInput.trim().replace("，", ",");
            String[] tableIds = normalizedInput.split(",");

            // 3. 验证数量（聚餐桌必须≥3张）
            if (tableIds.length < 3) {
                return OperationResult.error("聚餐桌必须选择 3 张或以上的餐桌，当前输入：" + tableIds.length + " 张");
            }

            // 4. 验证所有餐桌都是6人桌且状态空闲
            List<Tables> groupedTables = new ArrayList<>();
            for (String tid : tableIds) {
                String trimmedId = tid.trim();
                if (trimmedId.isEmpty()) {
                    return OperationResult.error("餐桌编号格式错误，请检查输入");
                }

                Tables t = getTableById(trimmedId);
                if (t == null) {
                    return OperationResult.error("餐桌 #" + trimmedId + " 不存在");
                }

                //  验证是否为6人桌
                if (t.getCapacity() != 6) {
                    return OperationResult.error("餐桌 #" + trimmedId + " 是 " + t.getCapacity() + " 人桌，聚餐桌必须全部使用 6 人桌！");
                }

                // 验证餐桌状态
                if (t.getStatus() != Tables.TableStatus.VACANT) {
                    return OperationResult.error("餐桌 #" + trimmedId + " 状态为【" +
                            t.getStatus().getDisplayName() + "】，必须为空闲");
                }

                groupedTables.add(t);
            }

            // 5. 验证桌号是否连续（聚餐桌要求桌号连续）
            try {
                List<Integer> tableNumbers = new ArrayList<>();
                for (Tables t : groupedTables) {
                    int num = Integer.parseInt(t.getDisplayId().replaceAll("[^0-9]", ""));
                    tableNumbers.add(num);
                }

                // 排序并检查连续性
                Collections.sort(tableNumbers);
                for (int i = 1; i < tableNumbers.size(); i++) {
                    if (tableNumbers.get(i) - tableNumbers.get(i - 1) != 1) {
                        return OperationResult.error("聚餐桌桌号必须连续！\n" +
                                "当前输入：" + String.join(",", tableIds) + "\n" +
                                "缺少桌号：" + (tableNumbers.get(i - 1) + 1));
                    }
                }
            } catch (NumberFormatException e) {
                return OperationResult.error("餐桌编号格式错误，请输入纯数字");
            }

            // 6. 计算总人数（6人桌 × 数量）
            int totalPeople = 6 * groupedTables.size();
            System.out.println(" 聚餐桌分配：" + groupedTables.size() + "张6人桌，总容量=" + totalPeople + "人");

            // 7. 执行聚餐桌分配
            try {
                boolean success = assignGroupedTables(groupedTables, totalPeople);
                return success ? OperationResult.success(true) : OperationResult.error("聚餐桌分配失败");
            } catch (Exception e) {
                e.printStackTrace();
                return OperationResult.error("聚餐桌分配时发生错误：" + e.getMessage());
            }
        }

        Tables table = tablesMapper.findByDisplayId(tableIdInput.trim());
        if (table == null) {
            return OperationResult.error("餐桌 #" + tableIdInput + " 不存在");
        }
        // ===== 3. 共享餐桌场景（仅新顾客模式）=====
        if (isShare) {
            if (peopleCount <= 0) {
                return OperationResult.error("共享餐桌需要指定新顾客组人数");
            }
            return handleShareTable(table, peopleCount);
        }
        // ===== 4. 添加客人场景 =====
        if (isAddGuests) {
            return handleAddToExistingGroup(table, peopleCount, isTwoSeat, isFourSeat, isSixSeat);
        }


        return OperationResult.success(true);

    }

    /**
     * 将排队中的顾客组分配到指定餐桌
     *
     * 功能说明：
     * 根据操作类型（合并/共享/普通）将已排队的顾客组分配到对应餐桌，并完成状态同步与队列移除。
     *
     * 执行流程：
     * 1. 合并场景（isMerge=true）：
     *    - 解析两个餐桌编号，支持中文逗号自动转换
     *    - 校验餐桌存在性、空闲状态、容量匹配、编号连续且同行
     *    - 调用 assignMergedTablesWithQueuedGroup 执行合并分配
     * 2. 共享场景（isShare=true）：
     *    - 校验目标餐桌为占用中主桌，容量匹配，类型支持共享
     *    - 加载原顾客组信息，调用 handleShareTableWithQueuedGroup 执行拼桌
     *    - 分配成功后从队列移除该顾客组
     * 3. 普通分配场景：
     *    - 校验目标餐桌为空闲主桌，容量匹配
     *    - 6人桌特殊规则：3人及以下顾客组不可分配
     *    - 更新餐桌状态为占用，关联顾客组，累加当日顾客数
     *    - 同步内存缓存并从队列移除
     *
     * @param group 待分配的排队顾客组对象
     * @param tableIdInput 用户输入的餐桌编号（单桌或合并桌格式）
     * @param isTwoSeat 是否选择2人桌容量
     * @param isFourSeat 是否选择4人桌容量
     * @param isSixSeat 是否选择6人桌容量
     * @param isShare 是否为共享/拼桌操作
     * @param isMerge 是否为合并餐桌操作
     * @return 操作结果封装，包含成功标志与提示消息
     */
    private OperationResult<Boolean> assignQueuedGroupToTable(CustomerGroup group, String tableIdInput,
                                                              boolean isTwoSeat, boolean isFourSeat,
                                                              boolean isSixSeat, boolean isShare,
                                                              boolean isMerge) {

//        System.out.println("  [DEBUG] 開始分配隊列顧客組：");
//        System.out.println("   排隊號: #" + group.getCallNumber());
//        System.out.println("   人數: " + group.getGroupSize());
//        System.out.println("   餐桌輸入: " + tableIdInput);
//        System.out.println("   是否合併: " + isMerge);

        // ===== 1. 合併場景：解析單輸入框中的兩個餐桌號（格式：7,8）=====
        if (isMerge) {
            // 1.1 驗證輸入格式
            if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
                return OperationResult.error("請輸入餐桌編號（格式：7,8）");
            }

            // 【新增】將中文逗號自動轉換為英文逗號
            String normalizedInput = tableIdInput.trim().replace("，", ",");

            // 1.2 按逗號分割餐桌號
            String[] tableIds = normalizedInput.split(",");
            if (tableIds.length != 2) {
                return OperationResult.error("合併操作必須輸入 2 張餐桌編號，格式：7,8");
            }

            // 1.3 去除空格並驗證
            String tableId1 = tableIds[0].trim();
            String tableId2 = tableIds[1].trim();

            if (tableId1.isEmpty() || tableId2.isEmpty()) {
                return OperationResult.error("餐桌編號不能為空");
            }

            //  這裡才是正確查詢單張餐桌的地方
            Tables table1 = getTableById(tableId1);
            Tables table2 = getTableById(tableId2);

            System.out.println("    餐桌 1: " + (table1 != null ? table1.getDisplayId() : "null"));
            System.out.println("    餐桌 2: " + (table2 != null ? table2.getDisplayId() : "null"));

            if (table1 == null) {
                return OperationResult.error("餐桌 #" + tableId1 + " 不存在");
            }
            if (table2 == null) {
                return OperationResult.error("餐桌 #" + tableId2 + " 不存在");
            }

            // 【新增】驗證餐桌是否處於空閒（VACANT）狀態
            if (table1.getStatus() != Tables.TableStatus.VACANT) {
                return OperationResult.error("餐桌 #" + tableId1 + " 當前狀態為【" +
                        table1.getStatus().getDisplayName() + "】，必須為空閒狀態才能合併");
            }
            if (table2.getStatus() != Tables.TableStatus.VACANT) {
                return OperationResult.error("餐桌 #" + tableId2 + " 當前狀態為【" +
                        table2.getStatus().getDisplayName() + "】，必須為空閒狀態才能合併");
            }

            // ═══════════════════════════════════════════════════════════
            // 驗證餐桌容量與用戶選擇的容量選項匹配 + 兩桌容量必須相同
            // ═══════════════════════════════════════════════════════════
            int expectedCapacity = isTwoSeat ? 2 : (isFourSeat ? 4 : 6);
            String expectedText = switch (expectedCapacity) {
                case 2 -> "2 人桌";
                case 4 -> "4 人桌";
                case 6 -> "6 人桌";
                default -> expectedCapacity + "人桌";
            };

            // 驗證餐桌 1 容量
            if (table1.getCapacity() != expectedCapacity) {
                return OperationResult.error(
                        "餐桌 #" + tableId1 + " 是 " + table1.getCapacity() + "人桌，" +
                                "與選擇的【" + expectedText + "】選項不匹配，請重新選擇"
                );
            }

            // 驗證餐桌 2 容量
            if (table2.getCapacity() != expectedCapacity) {
                return OperationResult.error(
                        "餐桌 #" + tableId2 + " 是 " + table2.getCapacity() + "人桌，" +
                                "與選擇的【" + expectedText + "】選項不匹配，請重新選擇"
                );
            }

            // 【防禦性檢查】確保兩張餐桌容量相同（理論上前面已驗證，但加上更安全）
            if (table1.getCapacity() != table2.getCapacity()) {
                return OperationResult.error(
                        "合併的兩張餐桌容量必須相同！餐桌 #" + tableId1 + " 是 " +
                                table1.getCapacity() + "人桌，餐桌 #" + tableId2 + " 是 " +
                                table2.getCapacity() + "人桌"
                );
            }
            // ═══════════════════════════════════════════════════════════
            // 验证餐桌编号是否连续（左右相邻，不能上下相邻）
            // ═══════════════════════════════════════════════════════════
            try {
                // 1. 提取纯数字编号（去除 a/b 后缀）
                int num1 = Integer.parseInt(tableId1.replaceAll("[^0-9]", ""));
                int num2 = Integer.parseInt(tableId2.replaceAll("[^0-9]", ""));

                // 2. 计算行号（每行 3 张桌）
                int row1 = (num1 - 1) / 3;
                int row2 = (num2 - 1) / 3;

                // 3. 验证：必须在同一行 + 编号相差 1
                if (row1 != row2) {
                    return OperationResult.error(
                            "餐桌 #" + tableId1 + " 和 #" + tableId2 + " 不在同一行，无法合并！ " +
                                    "💡 合并桌必须是左右相邻的餐桌（如 7+8、10+11）"
                    );
                }

                if (Math.abs(num1 - num2) != 1) {
                    return OperationResult.error(
                            "餐桌 #" + tableId1 + " 和 #" + tableId2 + " 不相邻，无法合并！ " +
                                    "💡 合并桌必须是编号连续的餐桌（如 7+8、10+11）"
                    );
                }

                System.out.println(" 连续验证通过：桌" + num1 + " 和 桌" + num2 +
                        " 是左右相邻的（第" + (row1 + 1) + "行）");

            } catch (NumberFormatException e) {
                return OperationResult.error("餐桌编号格式错误，请输入纯数字");
            }
            // ═══════════════════════════════════════════════════════════

            // 1.5 委託專用方法處理合併分配
            return assignMergedTablesWithQueuedGroup(table1, table2, group);
        }
        // ===== 2. 共享场景：队列顾客共享到已有顾客的餐桌 =====
        if (isShare) {
            // 2.1 验证餐桌输入
            if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
                return OperationResult.error("请输入餐桌编号");
            }

            // 2.2 查询餐桌（使用 Mapper）
            Tables table = tablesMapper.findByDisplayId(tableIdInput.trim());
            if (table == null) {
                return OperationResult.error("餐桌 #" + tableIdInput + " 不存在");
            }

            // 【修复】加载餐桌上的原顾客组（用新变量，不要覆盖 group 参数！）
            CustomerGroup existingGroup = null;
            if (table.getCurrentGroupId() != null && table.getCurrentGroupId() > 0) {
                existingGroup = customerGroupMapper.findById(table.getCurrentGroupId());
                if (existingGroup != null) {
                    table.setCurrentGroup(existingGroup);
                    System.out.println(" 已加载餐桌 #" + tableIdInput +
                            " 的原顾客组: #" + existingGroup.getCallNumber());
                }
            }


            // 2.3 验证餐桌状态（必须是 OCCUPIED）
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                return OperationResult.error("餐桌 #" + tableIdInput + " 当前状态为【" +
                        table.getStatus().getDisplayName() + "】，必须是占用状态才能共享");
            }


            // 2.4 验证餐桌类型（不能是合并桌或子桌）
            if (table.getTableType() == Tables.TableType.MERGED ||
                    table.getTableType() == Tables.TableType.SUBTABLE
                    || table.getTableType() == Tables.TableType.GROUPED) {
                return OperationResult.error("该类型餐桌不能进行共享操作");
            }

            // 2.5 【新增】验证餐桌容量与用户选择的容量选项匹配
            int expectedCapacity = isTwoSeat ? 2 : (isFourSeat ? 4 : 6);
            if (table.getCapacity() != expectedCapacity) {
                String expectedText = switch (expectedCapacity) {
                    case 2 -> "2人桌";
                    case 4 -> "4人桌";
                    case 6 -> "6人桌";
                    default -> expectedCapacity + "人桌";
                };
                return OperationResult.error(
                        "餐桌 #" + tableIdInput + " 是 " + table.getCapacity() + "人桌，" +
                                "与选择的【" + expectedText + "】选项不匹配，请重新选择"
                );
            }


            // 2.6 调用共享业务方法（传入队列中的顾客组）
            OperationResult<Boolean> result = handleShareTableWithQueuedGroup(table, group);

            // 分配成功后从队列移除
            if (result.isSuccess()) {
                removeFromQueueByGroupId(group.getGroup_id());
                System.out.println(" 队列顾客 #" + group.getCallNumber() +
                        " 已从队列移除并完成共享");
            }
            return result;
        }

        // ===== 3. 普通分配场景：队列顾客分配到空闲主桌 =====
        // 3.1 验证餐桌输入
        if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
            return OperationResult.error("请输入餐桌编号");
        }

        // 3.2 查询餐桌（使用 Mapper，保持与片段1一致）
        Tables table = tablesMapper.findByDisplayId(tableIdInput.trim());
        if (table == null) {
            return OperationResult.error("餐桌 #" + tableIdInput + " 不存在");
        }

        // 3.3 验证餐桌状态（必须是 VACANT）
        if (table.getStatus() != Tables.TableStatus.VACANT) {
            return OperationResult.error("餐桌 #" + tableIdInput + " 当前状态为【" +
                    table.getStatus().getDisplayName() + "】，必须是空闲状态才能分配");
        }

        // 3.4 验证餐桌类型（只能是普通主桌）
        if (table.getTableType() != Tables.TableType.MAIN) {
            return OperationResult.error("餐桌 #" + tableIdInput + " 是【" +
                    table.getTableType().getDisplayName() + "】，不能直接分配队列顾客");
        }

        // 3.5 验证餐桌容量与用户选择的容量选项匹配
        int expectedCapacity = isTwoSeat ? 2 : (isFourSeat ? 4 : 6);
        String expectedText = switch (expectedCapacity) {
            case 2 -> "2人桌";
            case 4 -> "4人桌";
            case 6 -> "6人桌";
            default -> expectedCapacity + "人桌";
        };
        if (table.getCapacity() != expectedCapacity) {
            return OperationResult.error(
                    "餐桌 #" + tableIdInput + " 是 " + table.getCapacity() + "人桌，" +
                            "与选择的【" + expectedText + "】选项不匹配，请重新选择"
            );
        }

        // 3.6 【核心规则】3人及以下顾客组不能使用6人桌
        int groupSize = group.getGroupSize();
        if (table.getCapacity() == 6 && groupSize < 4) {
            return OperationResult.warning(
                    "3人或以下的顾客组不能使用6人桌，请选择2人桌或4人桌"
            );
        }

        // 3.7 执行核心分配逻辑（事务内操作）
        try {
            // ── 更新数据库：餐桌状态 → OCCUPIED ──
            int updated = tablesMapper.updateTableStatus(
                    table.getTableId(),
                    Tables.TableStatus.OCCUPIED.name(),
                    group.getGroup_id(),
                    groupSize,
                    LocalDateTime.now()
            );
            if (updated == 0) {
                throw new RuntimeException("更新餐桌状态失败");
            }

            // ── 更新数据库：顾客组分配状态 ──
            updated = customerGroupMapper.updateAssignmentStatus(
                    group.getGroup_id(),
                    table.getTableId(),
                    true,   // isAssigned = true
                    false   // shownWaitMessage = false
            );
            if (updated == 0) {
                throw new RuntimeException("更新顾客组状态失败");
            }

            // ── 累加当日顾客总数 ──
            businessStatusMapper.incrementDailyTotalCustomers(
                    groupSize, LocalDate.now());

            // ── 同步更新内存缓存 ──
            Tables memoryTable = tableMap.get(table.getDisplayId());
            if (memoryTable != null) {
                memoryTable.setStatus(Tables.TableStatus.OCCUPIED);
                memoryTable.assignCustomerGroup(group);
                memoryTable.setActualSeats(groupSize);
                memoryTable.setStartTime(LocalDateTime.now());
                memoryTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                System.out.println(" 内存缓存已同步: 餐桌 #" + table.getDisplayId() + " → OCCUPIED");
            }

            // ── 更新顾客组对象引用 ──
            group.setAssigned(true);
            group.setTableId(table.getTableId());

            // ── 从队列中移除（数据库 + 内存）─
            removeFromQueueByGroupId(group.getGroup_id());

            System.out.println(" 队列顾客 #" + group.getCallNumber() +
                    " 已分配到餐桌 #" + table.getDisplayId());

            return OperationResult.success(true);

        } catch (Exception e) {
            System.err.println(" 分配餐桌失败: " + e.getMessage());
            return OperationResult.error("系统异常：" + e.getMessage());
        }
    }

    /**
     * 将排队顾客组分配到两张合并餐桌
     *
     * 功能说明：
     * 1. 校验两张餐桌均为空闲主桌，未处于拆分状态
     * 2. 容量与业务规则校验：
     *    - 两张6人桌合并时，顾客组人数需≥9人
     *    - 总人数不得超过两桌物理容量之和
     *    - 含6人桌时总人数需≥4人
     * 3. 确定主桌：baseId 较小的餐桌作为主桌，用于关联顾客组
     * 4. 分配座位：优先填满主桌，剩余人数分配给伙伴桌
     * 5. 更新数据库：设置两桌为合并类型、占用状态，互相引用显示编号
     * 6. 更新顾客组：标记为已分配，关联主桌ID
     * 7. 同步内存缓存：更新两桌及顾客组对象状态
     * 8. 从排队队列移除该顾客组，发布变更事件
     *
     * @param mainTable 第一张待合并餐桌
     * @param partnerTable 第二张待合并餐桌
     * @param group 排队中的顾客组对象
     * @return 操作结果封装，包含成功标志与提示消息
     */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult<Boolean> assignMergedTablesWithQueuedGroup(
            Tables mainTable,
            Tables partnerTable,
            CustomerGroup group) {

        // ===== 1. 验证餐桌状态 =====
        if (mainTable.getStatus() != Tables.TableStatus.VACANT ||
                partnerTable.getStatus() != Tables.TableStatus.VACANT) {
            return OperationResult.error(
                    "合并的两张餐桌必须都处于空闲状态（当前: " +
                            mainTable.getStatus() + " / " + partnerTable.getStatus() + ")"
            );
        }
        if (mainTable.getTableType() != Tables.TableType.MAIN ||
                partnerTable.getTableType() != Tables.TableType.MAIN) {
            return OperationResult.error("只能合并主桌，不能合并子桌或已合并的餐桌");
        }
        if (mainTable.isSplit() || partnerTable.isSplit()) {
            return OperationResult.error("餐桌处于拆分状态，无法合并");
        }

        // ===== 2. 容量与业务规则验证 =====
        int groupSize = group.getGroupSize();  //  修正：CustomerGroup 属性名是 groupSize
        int totalCapacity = mainTable.getPhysicalCapacity() + partnerTable.getPhysicalCapacity();

        // 【新增】两张6人桌合并规则：必须≥9人
        if (mainTable.getPhysicalCapacity() == 6 && partnerTable.getPhysicalCapacity() == 6) {
            if (groupSize < 9) {
                return OperationResult.error(
                        String.format("⚠️ 两张6人桌合并需要至少9人！\n" +
                                "当前人数：%d人\n" +
                                "规则说明：\n" +
                                "• 5-8人 → 合并两张4人桌\n" +
                                "• 9-12人 → 合并两张6人桌", groupSize)
                );
            }
        }
        if (groupSize > totalCapacity) {
            return OperationResult.error(
                    String.format("顾客组人数(%d) 超过合并后总容量(%d)", groupSize, totalCapacity)
            );
        }
        // 6 人桌规则：含 6 人桌且总人数<4
        if ((mainTable.getPhysicalCapacity() == 6 || partnerTable.getPhysicalCapacity() == 6) && groupSize < 4) {
            return OperationResult.warning("3 人或以下的客户不能使用 6 人桌（即使合并）");
        }

        // ===== 3. 确定主桌（baseId 较小的）=====
        Tables actualMainTable = mainTable.getBaseId() <= partnerTable.getBaseId() ? mainTable : partnerTable;
        Tables actualPartnerTable = (actualMainTable == mainTable) ? partnerTable : mainTable;

        // ===== 4. 计算每张餐桌实际座位数 =====
        int seatsMain = Math.min(groupSize, actualMainTable.getPhysicalCapacity());
        int seatsPartner = groupSize - seatsMain;

        // =====  添加详细日志（验证通过后，执行前）=====
//        System.out.println(" 开始合并餐桌分配：");
//        System.out.println("   主桌 ID: " + actualMainTable.getTableId() +
//                ", DisplayId: " + actualMainTable.getDisplayId());
//        System.out.println("   伙伴桌 ID: " + actualPartnerTable.getTableId() +
//                ", DisplayId: " + actualPartnerTable.getDisplayId());
//        System.out.println("   顾客组 ID: " + group.getGroup_id() +
//                ", 人数: " + groupSize);
//        System.out.println("   主桌座位: " + seatsMain +
//                ", 伙伴桌座位: " + seatsPartner);
//        System.out.println("   合并后总容量: " + totalCapacity);


        // ===== 5. 执行数据库更新（核心事务操作 - 使用 Mapper）=====
        // 5.1 更新餐桌合并状态
        int tablesUpdated = tablesMapper.updateMergeStatus(
                actualMainTable.getTableId(),
                actualPartnerTable.getTableId(),
                actualPartnerTable.getDisplayId(),  // main 的 merged_with
                actualMainTable.getDisplayId(),     // partner 的 merged_with
                group.getGroup_id(),
                seatsMain,
                seatsPartner
        );
        if (tablesUpdated == 0) {
            throw new RuntimeException("合并餐桌状态更新失败");
        }

        // 5.2 更新顾客组分配状态（仅关联主餐桌 ID）
        int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                group.getGroup_id(),
                actualMainTable.getTableId(),  // 仅主桌 ID
                true,   // isAssigned
                false   // shownWaitMessage
        );
        if (groupUpdated == 0) {
            throw new RuntimeException("更新顾客组分配状态失败");
        }

        // 5.3 增加当日顾客数
        businessStatusMapper.incrementDailyTotalCustomers(groupSize, LocalDate.now());

        // ===== 6. 更新内存状态（事务提交前）=====
        updateMemoryForMergedTables(
                actualMainTable,
                actualPartnerTable,
                group,
                seatsMain,
                seatsPartner
        );

        // ===== 7. 从队列移除（使用 Mapper + 发布事件）=====
        removeFromQueueByGroupId(group.getGroup_id());

        System.out.println(" 队列顾客组 #" + group.getCallNumber() +
                " (" + groupSize + "人) 已分配到合并餐桌 #" +
                actualMainTable.getDisplayId() + " + #" + actualPartnerTable.getDisplayId());

        return OperationResult.success(true);
    }

    /**
     * 更新合并餐桌的内存缓存状态
     *
     * 功能说明：
     * 1. 更新主桌缓存：设置为合并类型、占用状态，关联伙伴桌显示编号与顾客组
     * 2. 更新伙伴桌缓存：同步设置合并类型、占用状态，关联主桌显示编号与顾客组
     * 3. 设置两桌的实际入座人数（按容量优先分配原则计算）
     * 4. 更新顾客组对象：标记为已分配，关联主桌ID
     *
     * @param mainTable 主桌对象（baseId 较小者）
     * @param partnerTable 伙伴桌对象
     * @param group 关联的顾客组对象
     * @param seatsMain 主桌分配的实际座位数
     * @param seatsPartner 伙伴桌分配的实际座位数
     *
     * 执行时机：
     * - 仅在数据库事务成功提交前调用，确保内存状态与持久化数据一致
     * - 避免后续查询出现"数据库已更新但缓存未同步"的数据不一致问题
     */
    private void updateMemoryForMergedTables(Tables mainTable, Tables partnerTable,
                                             CustomerGroup group, int seatsMain, int seatsPartner) {
        // 更新主桌缓存
        Tables cachedMain = tableMap.get(mainTable.getDisplayId());
        if (cachedMain != null) {
            cachedMain.setStatus(Tables.TableStatus.OCCUPIED);
            cachedMain.setTableType(Tables.TableType.MERGED);
            cachedMain.setMergedWith(partnerTable.getDisplayId());
            cachedMain.setCurrentGroupId(group.getGroup_id());
            cachedMain.setCurrentGroup(group);
            cachedMain.setActualSeats(seatsMain);
            cachedMain.setStartTime(LocalDateTime.now());
        }

        // 更新伙伴桌缓存
        Tables cachedPartner = tableMap.get(partnerTable.getDisplayId());
        if (cachedPartner != null) {
            cachedPartner.setStatus(Tables.TableStatus.OCCUPIED);
            cachedPartner.setTableType(Tables.TableType.MERGED);
            cachedPartner.setMergedWith(mainTable.getDisplayId());
            cachedPartner.setCurrentGroupId(group.getGroup_id());
            cachedPartner.setCurrentGroup(group);
            cachedPartner.setActualSeats(seatsPartner);
            cachedPartner.setStartTime(LocalDateTime.now());
        }

        // 更新顾客组引用
        group.setAssigned(true);
        group.setTableId(mainTable.getTableId());
    }

    /**
     *  处理队列顾客组共享到已有顾客的餐桌
     * <p>
     * 业务规则：
     * - 目标餐桌必须是 OCCUPIED 状态的主桌
     * - 总人数不能超过物理容量
     * - 6 人桌总人数必须 ≥ 4 人
     * - 4 人桌特殊规则：已有 3 人不能共享；已有 1 人不能加 3 人
     *
     * @param mainTable   目标餐桌（已有顾客）
     * @param queuedGroup 队列中的顾客组（待共享）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult<Boolean> handleShareTableWithQueuedGroup(
            Tables mainTable,
            CustomerGroup queuedGroup) {


        // ===== 1. 基础验证 =====
        if (mainTable == null) {
            return OperationResult.error("餐桌不存在");
        }

        if (mainTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 不是占用状态，无法共享");
        }

        if (mainTable.getTableType() == Tables.TableType.MERGED ||
                mainTable.getTableType() == Tables.TableType.SUBTABLE) {
            return OperationResult.error("该类型餐桌不能进行共享操作");
        }

        CustomerGroup existingGroup = mainTable.getCurrentGroup();
        if (existingGroup == null) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 无关联顾客组");
        }

        int existingSize = existingGroup.getGroupSize();
        int newGroupSize = queuedGroup.getGroupSize();
        int totalSize = existingSize + newGroupSize;
        int physicalCapacity = mainTable.getPhysicalCapacity();

        // ===== 2. 容量与规则验证 =====
        if (totalSize > physicalCapacity) {
            return OperationResult.error(
                    String.format("餐桌容量不足！已有 %d 人，新增 %d 人，总计 %d 人，超过容量 %d 人",
                            existingSize, newGroupSize, totalSize, physicalCapacity));
        }

        if (physicalCapacity == 6 && totalSize < 4) {
            return OperationResult.warning("6 人桌规则：总人数必须 ≥ 4 人");
        }

        if (physicalCapacity == 4) {
            if (existingSize == 3) {
                return OperationResult.error("4 人桌已有 3 位顾客，不能再共享");
            }
            if (existingSize == 1 && newGroupSize == 3) {
                return OperationResult.error("4 人桌已有 1 位顾客，不能添加 3 人共享");
            }
        }

        // ===== 3. 获取主桌订单状态（使用 Mapper）=====
        Tables.OrderStatus mainOrderStatus = orderMapper.getLatestOrderStatus(mainTable.getTableId());

        // ===== 4. 关键差异：直接使用队列中的顾客组（不创建新组）=====
        CustomerGroup newGroup = queuedGroup;
        newGroup.setAssigned(true);
        newGroup.setStartTime(LocalDateTime.now());

        //  累加当日顾客总数（使用 Mapper）
        businessStatusMapper.incrementDailyTotalCustomers(newGroupSize, LocalDate.now());


        Tables subTableA = createSubTableA(mainTable, existingGroup, mainOrderStatus);
        Tables subTableB = createSubTableB(mainTable, newGroup);

        // ===== 6. 持久化子桌（使用 Mapper，@Transactional 自动管理事务）=====
        //  注意：saveSubTable 会回填自增主键 table_id
        if (tablesMapper.saveSubTable(subTableA) == 0) {
            throw new RuntimeException("保存子桌 A 失败");
        }
        if (tablesMapper.saveSubTable(subTableB) == 0) {
            throw new RuntimeException("保存子桌 B 失败");
        }

        //  验证主键已回填（调试用）
        if (subTableA.getTableId() <= 0 || subTableB.getTableId() <= 0) {
            throw new RuntimeException("子桌主键回填失败");
        }

        // ===== 7. 关联顾客组与子桌（使用 Mapper）=====
        customerGroupMapper.updateTableId(existingGroup.getGroup_id(), subTableA.getTableId());
        customerGroupMapper.updateTableId(newGroup.getGroup_id(), subTableB.getTableId());

        // ===== 8. 迁移所有订单到子桌 A（使用 Mapper）=====
        if (tablesMapper.hasAnyOrders(mainTable.getTableId())) {
            orderMapper.migrateOrdersToTable(mainTable.getTableId(), subTableA.getTableId());
        }

        // ===== 9. 更新主桌状态为 SPLITTING + 清空顾客组引用（使用 Mapper）=====
        tablesMapper.updateSplitStatus(mainTable.getTableId(), true);

        // ===== 10. 同步内存 =====
        syncMemoryAfterShare(mainTable, subTableA, subTableB, newGroup, existingGroup);

        System.out.println(" 共享餐桌成功: #" + mainTable.getDisplayId() +
                " → " + subTableA.getDisplayId() + "(原顾客) + " +
                subTableB.getDisplayId() + "(新顾客 #" + newGroup.getCallNumber() + ")");

        return OperationResult.success(true);
    }


    /**
     * 创建子桌A（继承主桌状态：开始时间 + 订单状态）
     * <p>
     * 严格匹配您的设计：
     * - 使用 getCapacity() 表示物理容量
     * - 使用 getId() 获取ID
     * - 不操作 tableMap（由 syncMemoryAfterShare 统一处理）
     */
    private Tables createSubTableA(Tables mainTable, CustomerGroup existingGroup, Tables.OrderStatus orderStatus) {
        int subTableCapacity = mainTable.getCapacity() / 2; // 严格使用 getCapacity()
        Tables subTableA = new Tables(
                mainTable.getBaseId(),
                subTableCapacity,
                mainTable.getDisplayId() + "a"
        );

        subTableA.setTableType(Tables.TableType.SUBTABLE);
        subTableA.setSubTableSuffix("a");
        subTableA.setMainTableId(mainTable.getTableId()); // 严格使用 getTableId()
        subTableA.setStatus(Tables.TableStatus.OCCUPIED);
        subTableA.assignCustomerGroup(existingGroup); // 使用 assignCustomerGroup 保证ID同步
        subTableA.setStartTime(mainTable.getStartTime()); // ✅ 继承开始时间
        subTableA.setOrderStatus(orderStatus);            // ✅ 继承订单状态（瞬态字段）
        subTableA.setCapacity(subTableCapacity);          // capacity = physical_capacity
        subTableA.setActualSeats(existingGroup.getGroupSize());
        subTableA.setSplit(false);
        subTableA.setMergedWith(null);

        return subTableA;
    }

    /**
     * 创建子桌B（新顾客组状态）
     * <p>
     * 严格匹配您的设计：
     * - 新顾客组使用当前时间
     * - 订单状态 = NO_ORDER
     */
    private Tables createSubTableB(Tables mainTable, CustomerGroup newGroup) {
        int subTableCapacity = mainTable.getCapacity() / 2; // 严格使用 getCapacity()
        Tables subTableB = new Tables(
                mainTable.getBaseId(),
                subTableCapacity,
                mainTable.getDisplayId() + "b"
        );

        subTableB.setTableType(Tables.TableType.SUBTABLE);
        subTableB.setSubTableSuffix("b");
        subTableB.setMainTableId(mainTable.getTableId()); // 严格使用 getTableId()
        subTableB.setStatus(Tables.TableStatus.OCCUPIED);
        subTableB.assignCustomerGroup(newGroup); // 使用 assignCustomerGroup 保证ID同步
        subTableB.setStartTime(LocalDateTime.now());               //  当前时间
        subTableB.setOrderStatus(Tables.OrderStatus.NO_ORDER);     //  无订单
        subTableB.setCapacity(subTableCapacity);                   // capacity = physical_capacity
        subTableB.setActualSeats(newGroup.getGroupSize());
        subTableB.setSplit(false);
        subTableB.setMergedWith(null);

        return subTableB;
    }

    /**
     * 同步内存缓存：餐桌共享/拆分操作后更新相关对象状态
     *
     * 功能说明：
     * 1. 更新主桌内存状态：标记为拆分中（SPLITTING），清空关联的顾客组引用
     * 2. 注册两个新子桌：将 subTableA 和 subTableB 加入餐桌缓存 tableMap
     * 3. 注册顾客组：将新顾客组与原顾客组加入顾客组缓存 customerGroupMap
     * 4. 更新原顾客组关联：将其餐桌引用指向子桌 A，确保后续查询定位正确
     *
     * @param mainTable 原始主桌对象
     * @param subTableA 关联原顾客组的子桌对象
     * @param subTableB 关联新顾客组的子桌对象
     * @param newGroup 新创建的顾客组对象
     * @param existingGroup 原有顾客组对象
     *
     * 执行时机：
     * - 仅在餐桌共享/拆分业务逻辑成功提交后调用
     * - 确保内存缓存与数据库状态实时一致，避免界面显示滞后
     */
    private void syncMemoryAfterShare(
            Tables mainTable,
            Tables subTableA,
            Tables subTableB,
            CustomerGroup newGroup,
            CustomerGroup existingGroup) {

        // 1. 更新主桌内存状态 → SPLITTING
        Tables memoryMain = tableMap.get(mainTable.getDisplayId());
        if (memoryMain != null) {
            memoryMain.setSplit(true);
            memoryMain.setStatus(Tables.TableStatus.SPLITTING);
            memoryMain.setCurrentGroup(null);
            memoryMain.setCurrentGroupId(null);
        }

        //  保留：更新 tableMap（这才是关键）
        tableMap.put(subTableA.getDisplayId(), subTableA);
        tableMap.put(subTableB.getDisplayId(), subTableB);

        // 3. 注册新顾客组
        customerGroupMap.put(newGroup.getGroup_id(), newGroup);
        customerGroupMap.put(existingGroup.getGroup_id(), existingGroup);
        // 4. 更新原顾客组餐桌引用
        existingGroup.setTableId(subTableA.getTableId());

        System.out.println(" 内存同步完成：共享餐桌 #" + mainTable.getDisplayId() +
                " → " + subTableA.getDisplayId() + "(原顾客) + " +
                subTableB.getDisplayId() + "(新顾客 #" + newGroup.getCallNumber() + ")");
    }

    /**
     * 同步内存缓存：添加顾客后刷新餐桌及关联对象状态
     *
     * 功能说明：
     * 1. 从数据库重新加载指定餐桌的最新数据，确保获取持久化后的准确状态
     * 2. 更新内存中餐桌对象的基础属性：实际入座人数、状态、顾客组 ID、入座时间
     * 3. 同步顾客组对象引用：优先从缓存获取，未命中则查库并回填缓存，消除"有 ID 无对象"问题
     * 4. 合并桌特殊处理：若餐桌为合并类型，同步更新伙伴桌的内存状态与顾客组引用
     *
     * @param displayId 餐桌显示编号，用于定位内存缓存与数据库记录
     *
     * 执行时机：
     * - 顾客入座、追加人数、状态变更等写操作成功后调用
     * - 确保后续读请求直接从内存获取最新数据，提升查询性能
     */
    public void syncMemoryAfterAddGuests(String displayId) {
        // ===== 1. 獲取內存中的餐桌引用（快速失敗）=====
        Tables memoryTable = tableMap.get(displayId);
        if (memoryTable == null) return;  // 防禦性編程

        // ===== 2. 從 Mapper 重新加載最新數據（Spring 事務自動管理）=====
        try {
            //  2.1 從數據庫加載最新餐桌數據
            Tables dbTable = tablesMapper.findByDisplayId(displayId);
            if (dbTable == null) return;

            //  2.2 更新餐桌基礎屬性（保持對象引用不變）
            memoryTable.setActualSeats(dbTable.getActualSeats());
            memoryTable.setStatus(dbTable.getStatus());
            memoryTable.setCurrentGroupId(dbTable.getCurrentGroupId());  // 僅設置 ID
            memoryTable.setStartTime(dbTable.getStartTime());

            //  2.3 【關鍵】同步 currentGroup 對象引用（消除"有 ID 無對象"警告）
            Integer groupId = dbTable.getCurrentGroupId();
            if (groupId != null && groupId > 0) {
                // 優先從內存獲取（避免重複查庫）
                CustomerGroup group = customerGroupMap.get(groupId);

                // 內存中不存在 → 從 Mapper 加載並同步到內存
                if (group == null) {
                    group = customerGroupMapper.findById(groupId);
                    if (group != null) {
                        customerGroupMap.put(groupId, group);  // 確保內存有此對象
                    }
                }

                // ✅ 關鍵：設置 currentGroup 引用
                memoryTable.setCurrentGroup(group);
            } else {
                // 清理無效引用
                memoryTable.setCurrentGroup(null);
            }

            //  2.4 合併桌特殊處理（同步夥伴桌）
            if (dbTable.getTableType() == Tables.TableType.MERGED &&
                    dbTable.getMergedWith() != null) {

                String partnerDisplayId = dbTable.getMergedWith();
                Tables partner = tableMap.get(partnerDisplayId);

                if (partner != null) {
                    Tables dbPartner = tablesMapper.findByDisplayId(partnerDisplayId);
                    if (dbPartner != null) {
                        // 同步夥伴桌基礎屬性
                        partner.setActualSeats(dbPartner.getActualSeats());
                        partner.setStatus(dbPartner.getStatus());
                        partner.setCurrentGroupId(dbPartner.getCurrentGroupId());
                        partner.setStartTime(dbPartner.getStartTime());

                        // 同步夥伴桌的 currentGroup
                        Integer partnerGroupId = dbPartner.getCurrentGroupId();
                        if (partnerGroupId != null && partnerGroupId > 0) {
                            CustomerGroup partnerGroup = customerGroupMap.get(partnerGroupId);
                            if (partnerGroup == null) {
                                partnerGroup = customerGroupMapper.findById(partnerGroupId);
                                if (partnerGroup != null) {
                                    customerGroupMap.put(partnerGroupId, partnerGroup);
                                }
                            }
                            partner.setCurrentGroup(partnerGroup);
                        } else {
                            partner.setCurrentGroup(null);
                        }
                    }
                }
            }

            System.out.println(" 內存緩存已同步：餐桌 #" + displayId);

        } catch (Exception e) {
            System.err.println(" 同步內存緩存失敗: " + e.getMessage());
            e.printStackTrace();
            //  可選：根據業務需求決定是否拋出異常
            // throw new RuntimeException("同步餐桌緩存失敗", e);
        }
    }


    /**
     * 从排队队列中移除指定顾客组
     *
     * 功能说明：
     * 1. 查询顾客组当前所在的队列类型（如普通队列、大桌队列等）
     * 2. 从数据库队列记录中删除该顾客组
     * 3. 重排剩余顾客的队列位置序号，确保序号连续
     * 4. 发布队列变更事件，通知监听器刷新界面显示
     * 5. 同步清理内存中的队列缓存，保证数据一致性
     *
     * @param groupId 待移除的顾客组唯一标识
     *
     * 执行时机：
     * - 顾客组入座餐桌后自动从等待队列移除
     * - 顾客主动取消排队时手动调用
     */
    private void removeFromQueueByGroupId(int groupId) {
        // 1. 查询顾客组所在队列类型
        String queueType = queueMapper.findQueueTypeByGroupId(groupId);
        if (queueType == null) {
            return;  // 不在队列中，无需处理
        }

        // 2. 从数据库队列移除
        int removed = queueMapper.removeFromQueue(groupId, queueType);
        if (removed > 0) {
            // 3. 重排队列位置
            queueMapper.updateQueuePositions(queueType);

            // 4. 发布事件通知监听器刷新 UI
            eventPublisher.publishEvent(new QueueChangedEvent(this, queueType));

            // 5. 同步清理内存队列（线程安全）
            synchronized (queueLock) {
                getQueueByType(queueType).removeIf(g -> g.getGroup_id() == groupId);
                repositionQueue(getQueueByType(queueType));
            }
        }
    }

    /**
     * 处理餐桌共享/拆分操作
     *
     * 功能说明：
     * 1. 参数校验：确保餐桌存在、状态为占用中、类型支持共享操作
     * 2. 容量验证：计算现有顾客与新顾客总人数，确保不超过餐桌物理容量
     * 3. 业务规则校验：6人桌总人数需≥4人，4人桌特定组合限制等
     * 4. 创建新顾客组：分配叫号、记录人数与入座时间
     * 5. 创建两个子桌：分别关联原顾客组与新顾客组，继承主桌订单状态
     * 6. 持久化子桌记录并回填主键，关联顾客组与对应子桌
     * 7. 迁移主桌原有订单至子桌A，确保订单归属正确
     * 8. 更新主桌状态为拆分中，同步内存缓存确保界面即时刷新
     *
     * @param mainTable 待拆分的主桌对象
     * @param newGroupSize 新加入顾客组的人数
     * @return 操作结果封装，包含成功标志与提示消息
     *
     * 业务场景：
     * - 已占用餐桌有新顾客希望拼桌共享
     * - 系统自动将原桌拆分为两个逻辑子桌，分别服务不同顾客组
     */
    private OperationResult<Boolean> handleShareTable(Tables mainTable, int newGroupSize) {
        // ═══════════════════════════════════════════════════════════
        // 【关键修复】强制使用内存缓存对象（放在 DEBUG 之前！）
        // ═══════════════════════════════════════════════════════════
        Tables cachedTable = tableMap.get(mainTable.getDisplayId());
        if (cachedTable != null) {
            mainTable = cachedTable;  //  替换引用，后续全部操作缓存对象
        }

        // 【双重保险】如果缓存中 currentGroup 仍为 null，手动加载
        if (mainTable.getCurrentGroup() == null && mainTable.getCurrentGroupId() != null) {
            CustomerGroup group = customerGroupMapper.findById(mainTable.getCurrentGroupId());
            if (group != null) {
                mainTable.setCurrentGroup(group);
                customerGroupMap.put(group.getGroup_id(), group);  // 同步到全局缓存
                System.out.println(" 已手动加载顾客组 #" + group.getGroup_id());
            }
        }

        // 【DEBUG】打印入参状态（此时已确保使用修复后的缓存对象）
//        System.out.println(" [DEBUG] handleShareTable 入口:");
//        System.out.println("   mainTable.displayId: " + mainTable.getDisplayId());
//        System.out.println("   mainTable.currentGroupId: " + mainTable.getCurrentGroupId());
//        System.out.println("   mainTable.currentGroup: " +
//                (mainTable.getCurrentGroup() != null ?
//                        "存在 [call#" + mainTable.getCurrentGroup().getCallNumber() + "]" : "NULL"));
//        System.out.println("   mainTable.status: " + mainTable.getStatus());

        try {
            // ===== 1. 基础验证 =====
            if (mainTable == null) {
                return OperationResult.error("餐桌不存在");
            }

            if (mainTable.getStatus() != Tables.TableStatus.OCCUPIED) {
                return OperationResult.error("餐桌 #" + mainTable.getDisplayId() +
                        " 不是占用状态，无法共享");
            }

            if (mainTable.getTableType() == Tables.TableType.MERGED ||
                    mainTable.getTableType() == Tables.TableType.SUBTABLE) {
                return OperationResult.error("该类型餐桌不能进行共享操作");
            }

            CustomerGroup existingGroup = mainTable.getCurrentGroup();
            if (existingGroup == null) {
                return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 无关联顾客组");
            }

            // ===== 2. 容量计算与规则验证 =====
            int existingSize = existingGroup.getGroupSize();
            int totalSize = existingSize + newGroupSize;
            int physicalCapacity = mainTable.getCapacity();

            if (totalSize > physicalCapacity) {
                return OperationResult.error(
                        String.format("餐桌容量不足！已有 %d 人，新增 %d 人，总计 %d 人，超过容量 %d 人",
                                existingSize, newGroupSize, totalSize, physicalCapacity));
            }

            if (physicalCapacity == 6 && totalSize < 4) {
                return OperationResult.warning("6人桌规则：总人数必须 ≥ 4人");
            }

            if (physicalCapacity == 4) {
                if (existingSize == 3) {
                    return OperationResult.error("4人桌已有3位顾客，不能再共享");
                }
                if (existingSize == 1 && newGroupSize == 3) {
                    return OperationResult.error("4人桌已有1位顾客，不能添加3人共享");
                }
            }

            // ===== 3. 获取主桌订单状态 =====
            Tables.OrderStatus mainOrderStatus = orderMapper.getLatestOrderStatus(mainTable.getTableId());

            // ===== 4. 创建新顾客组 =====
            Integer nextCall = businessStatusMapper.getNextCallNumber(LocalDate.now());
            int callNumber = (nextCall != null) ? nextCall : 1;

            CustomerGroup newGroup = new CustomerGroup(callNumber, newGroupSize);
            newGroup.setAssigned(true);
            newGroup.setStartTime(LocalDateTime.now());

            customerGroupMapper.saveWithoutTableRef(newGroup);
            if (newGroup.getGroup_id() <= 0) {
                return OperationResult.error("新顾客组ID生成失败");
            }

            businessStatusMapper.incrementNextCallNumber(LocalDate.now());
            businessStatusMapper.incrementDailyTotalCustomers(newGroupSize, LocalDate.now());

            // ===== 5. 创建子桌 =====
            Tables subTableA = createSubTableA(mainTable, existingGroup, mainOrderStatus);
            Tables subTableB = createSubTableB(mainTable, newGroup);

            // ===== 6. 持久化子桌 =====
            tablesMapper.saveSubTable(subTableA);
            tablesMapper.saveSubTable(subTableB);

            if (subTableA.getTableId() <= 0 || subTableB.getTableId() <= 0) {
                throw new RuntimeException("子桌主键回填失败");
            }

            // ===== 7. 关联顾客组与子桌 =====
            customerGroupMapper.updateTableId(existingGroup.getGroup_id(), subTableA.getTableId());
            customerGroupMapper.updateTableId(newGroup.getGroup_id(), subTableB.getTableId());

            // ===== 8. 迁移订单到子桌A =====
            if (orderMapper.hasAnyOrders(mainTable.getTableId())) {
                orderMapper.migrateOrdersToTable(mainTable.getTableId(), subTableA.getTableId());
            }

            // ===== 9. 更新主桌状态为 SPLITTING =====
            tablesMapper.updateSplitStatus(mainTable.getTableId(), true);

            // ===== 10. 同步内存缓存 =====
            syncMemoryAfterShare(mainTable, subTableA, subTableB, newGroup, existingGroup);

            System.out.println(" 共享餐桌分配成功: #" + mainTable.getDisplayId() +
                    " → 子桌 #" + subTableA.getDisplayId() + " + #" + subTableB.getDisplayId());

            return OperationResult.success(true);

        } catch (Exception e) {
            System.err.println(" 共享餐桌分配失败: " + e.getMessage());
            e.printStackTrace();
            return OperationResult.error("共享餐桌分配失败: " + e.getMessage());
        }
    }


    /**
     * 处理向现有餐桌追加顾客或空桌新入座
     *
     * 功能说明：
     * 1. 参数校验：验证餐桌对象、追加人数范围（1-12）、容量类型选择一致性
     * 2. 容量规则校验：
     *    - 空桌入座：2人桌限1-2人，4人桌限1-4人，6人桌限4-6人（3人及以下不可用）
     *    - 已占桌追加：追加后总人数不得超过物理容量，6人桌最终人数仍需≥4人
     * 3. 空桌场景：创建新顾客组，分配叫号，更新餐桌状态为占用，持久化并刷新缓存
     * 4. 已占桌场景：校验当前顾客组，累加人数后更新顾客组与餐桌记录，同步合并桌伙伴状态
     * 5. 更新业务统计：累加当日顾客总数，递增叫号（仅新入座时）
     *
     * @param table 目标餐桌对象
     * @param additionalPeople 本次入座或追加的人数
     * @param isTwoSeat 是否选择2人桌容量
     * @param isFourSeat 是否选择4人桌容量
     * @param isSixSeat 是否选择6人桌容量
     * @return 操作结果封装，包含成功标志与提示消息
     *
     * @throws Exception 系统异常时捕获并返回错误结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult<Boolean> handleAddToExistingGroup(
            Tables table, int additionalPeople, boolean isTwoSeat, boolean isFourSeat, boolean isSixSeat) {

        // ===== 1. 基础参数验证 =====
        if (table == null) {
            return OperationResult.error("餐桌对象不能为空");
        }
        if (additionalPeople <= 0 || additionalPeople > 12) {
            return OperationResult.error("人数必须在 1-12 之间，当前输入：" + additionalPeople);
        }

        // ===== 2. 验证：餐桌实际容量与选择是否匹配 =====
        int actualCapacity = table.getCapacity();
        int selectedCapacity = 0;
        String selectedCapacityName = "";

        if (isTwoSeat) {
            selectedCapacity = 2;
            selectedCapacityName = "2人桌";
        } else if (isFourSeat) {
            selectedCapacity = 4;
            selectedCapacityName = "4人桌";
        } else if (isSixSeat) {
            selectedCapacity = 6;
            selectedCapacityName = "6人桌";
        } else {
            return OperationResult.error("请至少选择一种餐桌容量类型");
        }

        // 【核心验证】餐桌实际容量必须与选择的容量匹配
        if (actualCapacity != selectedCapacity) {
            return OperationResult.error(
                    "餐桌 #" + table.getDisplayId() + " 是 " + actualCapacity + "人桌，不是 " + selectedCapacityName + "！\n" +
                            "请选择正确的餐桌容量类型。"
            );
        }

        // 【容量规则验证】- 针对空桌新入座的初始人数验证
        if (table.getStatus() == Tables.TableStatus.VACANT) {
            if (actualCapacity == 2 && (additionalPeople < 1 || additionalPeople > 2)) {
                return OperationResult.error("2人桌只能容纳 1-2 人，当前输入：" + additionalPeople + "人");
            } else if (actualCapacity == 4 && (additionalPeople < 1 || additionalPeople > 4)) {
                return OperationResult.error("4人桌只能容纳 1-4 人，当前输入：" + additionalPeople + "人");
            }
            //  6人桌新入座：人数必须在 4-6 之间
            else if (actualCapacity == 6 && (additionalPeople < 4 || additionalPeople > 6)) {
                return OperationResult.error("6人桌只能容纳 4-6 人，当前输入：" + additionalPeople + "人\n" +
                        "3人及以下请使用2人桌或4人桌");
            }
        }

        // ===== 场景 1: 空桌 → 创建新顾客组 =====
        if (table.getStatus() == Tables.TableStatus.VACANT) {
            try {
                //  获取下一个叫号
                Integer callNumber = businessStatusMapper.getNextCallNumber(LocalDate.now());
                if (callNumber == null) {
                    callNumber = 1;
                }

                // 创建新顾客组
                CustomerGroup newGroup = new CustomerGroup(callNumber, additionalPeople);
                newGroup.setAssigned(true);
                newGroup.setStartTime(LocalDateTime.now());
                newGroup.setTableId(table.getTableId());

                // 保存顾客组（MyBatis 自动回填 group_id）
                int saved = customerGroupMapper.save(newGroup);
                if (saved == 0 || newGroup.getGroup_id() <= 0) {
                    return OperationResult.error("顾客组保存失败，请重试");
                }

                // 同步到内存缓存
                customerGroupMap.put(newGroup.getGroup_id(), newGroup);

                // 更新餐桌状态（内存）
                table.setCurrentGroupId(newGroup.getGroup_id());
                table.setStatus(Tables.TableStatus.OCCUPIED);
                table.setActualSeats(additionalPeople);
                table.setStartTime(LocalDateTime.now());

                // 持久化到数据库
                int tableUpdated = tablesMapper.update(table);
                if (tableUpdated == 0) {
                    return OperationResult.error("餐桌状态更新失败");
                }

                // 更新业务状态
                businessStatusMapper.incrementNextCallNumber(LocalDate.now());
                businessStatusMapper.incrementDailyTotalCustomers(additionalPeople, LocalDate.now());

                //  刷新内存缓存
                refreshTableCache();

                System.out.println(" 新顾客组入座成功: 餐桌#" + table.getDisplayId() +
                        ", 顾客组#" + newGroup.getGroup_id() +
                        ", 人数:" + additionalPeople);

                return OperationResult.success(true);

            } catch (Exception e) {
                System.err.println(" 创建新顾客组失败: " + e.getMessage());
                e.printStackTrace();
                return OperationResult.error("系统异常: " + e.getMessage());
            }
        }

        // ===== 场景 2: 已占桌 → 追加人数 =====
        if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
            return OperationResult.error("餐桌 #" + table.getDisplayId() + " 状态异常（" +
                    table.getStatus().getDisplayName() + "），不能追加顾客");
        }

        Integer groupId = table.getCurrentGroupId();
        if (groupId == null || groupId <= 0) {
            return OperationResult.error("餐桌 #" + table.getDisplayId() + " 无有效顾客组 ID");
        }

        // 从内存缓存获取顾客组（优先）
        CustomerGroup group = customerGroupMap.get(groupId);
        if (group == null) {
            // 缓存未命中 → 从数据库加载
            group = customerGroupMapper.findById(groupId);
            if (group == null) {
                return OperationResult.error("顾客组数据异常（ID=" + groupId + " 不存在）");
            }
            customerGroupMap.put(groupId, group);
        }

        // 【核心计算】验证追加后的总人数
        int currentSize = group.getGroupSize();
        int newSize = currentSize + additionalPeople;
        int physicalCapacity = table.getPhysicalCapacity();

        // 【容量验证】追加后总人数不能超过物理容量
        if (newSize > physicalCapacity) {
            return OperationResult.error(
                    "餐桌 #" + table.getDisplayId() + " 物理容量为 " + physicalCapacity + "人\n" +
                            "当前已有 " + currentSize + "人，不能再追加 " + additionalPeople + "人\n" +
                            "剩余座位：" + (physicalCapacity - currentSize) + "人"
            );
        }

        // 【6人桌特殊规则】3人及以下不能使用6人桌（验证的是最终总人数）
        if (physicalCapacity == 6 && newSize < 4) {
            return OperationResult.error(
                    "6人桌不能容纳 " + newSize + "人！\n" +
                            "规则：6人桌最少需要4人，追加后总共只有 " + newSize + "人\n" +
                            "请使用2人桌或4人桌"
            );
        }

        try {
            // 更新内存对象
            group.setGroupSize(newSize);
            table.setActualSeats(newSize);

            // 持久化到数据库
            int groupUpdated = customerGroupMapper.update(group);
            int tableUpdated = tablesMapper.update(table);

            if (groupUpdated == 0 || tableUpdated == 0) {
                return OperationResult.error("数据库更新失败");
            }

            // 【新增】更新当日顾客总数（累加追加的人数）
            businessStatusMapper.incrementDailyTotalCustomers(additionalPeople, LocalDate.now());

            //  合并桌处理：同步更新伙伴桌的实际入座人数
            if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
                Tables partner = tablesMapper.findByDisplayId(table.getMergedWith());
                if (partner != null && partner.getStatus() == Tables.TableStatus.OCCUPIED) {
                    // 确定主桌（base_id 较小的作为主桌）
                    Tables master = (table.getBaseId() <= partner.getBaseId()) ? table : partner;
                    master.setActualSeats(newSize);
                    tablesMapper.update(master);
                    System.out.println(" 合并桌伙伴已同步: #" + partner.getDisplayId());
                }
            }

            //  刷新内存缓存确保一致性
            refreshTableCache();

            System.out.println(" 顾客追加成功: 餐桌#" + table.getDisplayId() +
                    ", 顾客组#" + group.getGroup_id() +
                    ", 人数:" + currentSize + " → " + newSize);

            return OperationResult.success(true);

        } catch (Exception e) {
            System.err.println(" 追加顾客失败: " + e.getMessage());
            e.printStackTrace();
            return OperationResult.error("系统异常: " + e.getMessage());
        }
    }

    /**
     * 根据顾客组ID查询顾客组信息
     *
     * 功能说明：
     * 1. 优先从内存缓存 customerGroupMap 中获取顾客组对象
     * 2. 缓存未命中时查询数据库，并将结果回填缓存供后续使用
     *
     * @param groupId 顾客组唯一标识
     * @return 对应的 CustomerGroup 对象；groupId 为空或查询无结果时返回 null
     */
    @Transactional(readOnly = true)
    public CustomerGroup getCustomerGroupById(Integer groupId) {
        if (groupId == null) return null;

        // 优先从内存缓存获取
        CustomerGroup group = customerGroupMap.get(groupId);
        if (group != null) {
            return group;
        }

        // 缓存未命中时查数据库
        return customerGroupMapper.findById(groupId);
    }

    /**
     * 合并两张餐桌并分配给顾客组
     *
     * 功能说明：
     * 1. 确定主桌：编号较小的餐桌作为主桌，另一张作为关联桌
     * 2. 创建顾客组：获取当日叫号并立即递增，记录顾客人数和入座时间
     * 3. 分配座位：优先填满主桌容量，剩余人数分配给关联桌
     * 4. 更新餐桌状态：两张餐桌均标记为合并类型、占用状态，互相引用显示编号
     * 5. 更新顾客组：标记为已分配，关联主桌ID
     * 6. 同步内存缓存：更新餐桌与顾客组缓存确保界面即时刷新
     * 7. 累加当日顾客总数：用于经营统计报表
     *
     * @param table1 第一张待合并餐桌
     * @param table2 第二张待合并餐桌
     * @param peopleCount 顾客组总人数
     * @return true=合并分配成功，false=操作失败
     * @throws SQLException 数据库更新失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean assignMergedTables(Tables table1, Tables table2, int peopleCount) throws SQLException {
        // 1. 確定主桌（编号较小的为主桌）
        Tables mainTable = table1.getBaseId() <= table2.getBaseId() ? table1 : table2;
        Tables partnerTable = (mainTable == table1) ? table2 : table1;

        // 2. 創建顧客組（獲取叫號 + 立即遞增）
        Integer nextCall = businessStatusMapper.getNextCallNumber(LocalDate.now());
        int callNumber = (nextCall != null) ? nextCall : 1;

        CustomerGroup group = new CustomerGroup(callNumber, peopleCount);
        group.setStartTime(LocalDateTime.now());
        group.setAssigned(false);

        // 保存到数据库（生成 group_id）
        customerGroupMapper.save(group);

        //  關鍵：保存成功后立即递增叫号
        businessStatusMapper.incrementNextCallNumber(LocalDate.now());

        // 3. 分配座位（优先填满主桌）
        int seatsMain = Math.min(peopleCount, mainTable.getCapacity());
        int seatsPartner = peopleCount - seatsMain;

        // 4. 更新餐桌狀態（调用 Mapper 方法）
        int updated1 = tablesMapper.updateTableToMergedOccupied(
                mainTable.getTableId(),
                partnerTable.getDisplayId(),  // main 指向 partner
                group.getGroup_id(),
                seatsMain
        );
        int updated2 = tablesMapper.updatePartnerTableToMergedOccupied(
                partnerTable.getTableId(),
                mainTable.getDisplayId(),     // partner 指向 main
                group.getGroup_id(),
                seatsPartner
        );

        if (updated1 == 0 || updated2 == 0) {
            throw new SQLException("更新合并餐桌状态失败");
        }

        // 5. 更新顧客組分配狀態
        int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                group.getGroup_id(),
                mainTable.getTableId(),  // 关联到主桌
                true,
                false
        );
        if (groupUpdated == 0) {
            throw new SQLException("更新顾客组失败");
        }

        // 6. 同步內存緩存
        syncMergedTablesToCache(mainTable, partnerTable, group, seatsMain, seatsPartner);

        // 7. 增加當日顧客數
        businessStatusMapper.incrementDailyTotalCustomers(peopleCount, LocalDate.now());

        System.out.println(" 合并餐桌分配成功: #" + mainTable.getDisplayId() +
                " + #" + partnerTable.getDisplayId() +
                " → 顾客组 #" + group.getCallNumber() +
                " (" + peopleCount + "人)");

        return true;
    }

    /**
     * 分配聚餐桌给顾客组（3张或以上6人桌）
     *
     * 功能说明：
     * 1. 校验参数：聚餐桌列表必须包含至少3张餐桌
     * 2. 确定主桌：列表中编号最小的餐桌作为主桌，用于关联顾客组
     * 3. 构建关联桌号字符串：格式如 "13,14,15"，存储于每张餐桌的 group_with 字段
     * 4. 创建顾客组：获取当日叫号，标记为已分配，关联主桌ID
     * 5. 批量更新餐桌状态：所有餐桌标记为聚餐桌类型、占用状态，设置实际座位数为容量值
     * 6. 同步内存缓存：更新 tableMap 确保界面即时反映最新状态
     * 7. 累加当日顾客总数并递增下一个叫号
     * 8. 刷新餐桌缓存：确保后续查询获取最新数据
     *
     * @param groupedTables 聚餐桌列表（至少3张）
     * @param totalPeople 顾客组总人数
     * @return true=分配成功
     * @throws IllegalArgumentException 餐桌数量不足3张时抛出
     * @throws RuntimeException 更新任意餐桌状态失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean assignGroupedTables(List<Tables> groupedTables, int totalPeople) {
        if (groupedTables == null || groupedTables.size() < 3) {
            throw new IllegalArgumentException("聚餐桌必须至少3张");
        }

        // 1. 获取主桌（编号最小的桌）
        Tables mainTable = groupedTables.get(0);
        String mainTableDisplayId = mainTable.getDisplayId();

        // 2. 构建关联桌号字符串（如 "13,14,15"）
        List<String> allTableIds = groupedTables.stream()
                .map(Tables::getDisplayId)
                .collect(Collectors.toList());
        String groupWithIds = String.join(",", allTableIds);

        System.out.println(" 聚餐桌分配：主桌=" + mainTableDisplayId +
                "，关联桌=" + groupWithIds + "，总人数=" + totalPeople);

        // 3. 创建顾客组（使用当前叫号）
        Integer nextCall = businessStatusMapper.getNextCallNumber(LocalDate.now());
        int callNumber = (nextCall != null) ? nextCall : 1;

        CustomerGroup group = new CustomerGroup(callNumber, totalPeople);
        group.setStartTime(LocalDateTime.now());
        group.setAssigned(true);
        group.setTableId(mainTable.getTableId());  // 关联主桌

        // 保存到数据库
        customerGroupMapper.save(group);
        customerGroupMap.put(group.getGroup_id(), group);

        // 4. 批量更新所有聚餐桌状态
        for (Tables table : groupedTables) {
            // 4.1 更新数据库：状态→OCCUPIED，类型→GROUPED，设置group_with
            int updated = tablesMapper.updateTableForGroupedCheckIn(
                    table.getTableId(),
                    Tables.TableStatus.OCCUPIED.name(),
                    group.getGroup_id(),
                    table.getCapacity(),  // 每桌6人
                    groupWithIds,
                    "GROUPED"
            );

            if (updated == 0) {
                throw new RuntimeException("更新聚餐桌 #" + table.getDisplayId() + " 状态失败");
            }

            // 4.2 同步更新内存缓存
            Tables memoryTable = tableMap.get(table.getDisplayId());
            if (memoryTable != null) {
                memoryTable.setStatus(Tables.TableStatus.OCCUPIED);
                memoryTable.setTableType(Tables.TableType.GROUPED);
                memoryTable.setGroupWith(groupWithIds);
                memoryTable.setCurrentGroupId(group.getGroup_id());
                memoryTable.setCurrentGroup(group);
                memoryTable.setActualSeats(table.getCapacity());  // 每桌6人
                memoryTable.setStartTime(LocalDateTime.now());
                memoryTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
            }

            System.out.println("  餐桌 #" + table.getDisplayId() + " 已更新为聚餐桌状态");
        }

        // 5. 累加当日顾客总数
        businessStatusMapper.incrementDailyTotalCustomers(totalPeople, LocalDate.now());

        // 6. 递增下一个叫号
        businessStatusMapper.incrementNextCallNumber(LocalDate.now());

        // 7. 刷新内存缓存
        refreshTableCache();

        System.out.println(" 聚餐桌分配成功：主桌 #" + mainTableDisplayId +
                "，顾客组 #" + callNumber + "（" + totalPeople + "人）");

        return true;
    }

    /**
     * 清理所有可重置的餐桌并返回执行结果
     *
     * 功能说明：
     * 1. 刷新餐桌缓存确保数据最新
     * 2. 检查是否存在可清理的餐桌，无则直接返回提示
     * 3. 按类型依次尝试清理：子桌 → 合并桌 → 聚餐桌 → 主桌
     * 4. 每种类型清理前检查是否存在可清理项，清理异常时记录日志并继续执行其他类型
     * 5. 构建清理详情映射，记录每种餐桌类型的清理结果
     * 6. 返回执行结果：包含成功标志、提示消息、是否有等待顾客、各类型清理详情
     *
     * @return 清理结果映射，包含以下字段：
     *         - success: boolean，是否至少清理成功一种类型
     *         - message: String，用户友好提示消息
     *         - hasWaitingCustomers: boolean，是否仍有等待顾客
     *         - cleanedDetails: Map<String, Boolean>，各餐桌类型的清理结果
     *         - error: boolean（可选），发生系统异常时标记
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clearAllTables() {
        Map<String, Object> result = new HashMap<>();

        try {
            refreshTableCache();

            if (!hasCleanableTables()) {
                result.put("success", false);
                result.put("message", "目前没有可清理的桌子");
                result.put("hasWaitingCustomers", hasWaitingCustomers());
                return result;
            }

            boolean cleanedAny = false;

            //  记录每种类型的清理结果
            boolean subTablesCleaned = false;
            boolean mergedTablesCleaned = false;
            boolean groupedTablesCleaned = false;
            boolean mainTablesCleaned = false;

            // 清理子桌
            try {
                if (hasCleanableSubTables()) {
                    subTablesCleaned = cleanupSubTables();
                    if (subTablesCleaned) cleanedAny = true;
                }
            } catch (Exception e) {
                System.err.println(" 子桌清理异常: " + e.getMessage());
            }

            // 清理合并桌
            try {
                if (hasCleanableMergedTables()) {
                    mergedTablesCleaned = cleanupMergedTables();
                    if (mergedTablesCleaned) cleanedAny = true;
                }
            } catch (Exception e) {
                System.err.println(" 合并桌清理异常: " + e.getMessage());
            }

            // 清理聚餐桌
            try {
                if (hasCleanableGroupedTables()) {
                    groupedTablesCleaned = cleanupGroupedTables();
                    if (groupedTablesCleaned) cleanedAny = true;
                }
            } catch (Exception e) {
                System.err.println(" 聚餐桌清理异常: " + e.getMessage());
            }

            // 清理主桌
            try {
                if (hasCleanableMainTables()) {
                    mainTablesCleaned = cleanupMainTables();
                    if (mainTablesCleaned) cleanedAny = true;
                }
            } catch (Exception e) {
                System.err.println(" 主桌清理异常: " + e.getMessage());
            }

            //  构建清理详情（供 Controller 构建消息）
            Map<String, Boolean> cleanedDetails = new HashMap<>();
            cleanedDetails.put("subTables", subTablesCleaned);
            cleanedDetails.put("mergedTables", mergedTablesCleaned);
            cleanedDetails.put("groupedTables", groupedTablesCleaned);
            cleanedDetails.put("mainTables", mainTablesCleaned);

            result.put("success", cleanedAny);
            result.put("message", cleanedAny ? "餐桌清理完成！" : "没有可清理的桌子");
            result.put("hasWaitingCustomers", hasWaitingCustomers());
            result.put("cleanedDetails", cleanedDetails);  //  关键：返回详情

            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "系统错误: " + e.getMessage());
            result.put("error", true);
            return result;
        }
    }

    /**
     * 检查是否存在可清理的餐桌
     *
     * 功能说明：
     * 遍历所有餐桌，判断是否存在不满足"不可清理条件"的餐桌。
     * 不可清理条件包括：
     * - 状态为 VACANT 且类型为 MAIN 的空闲主桌
     * - 状态为 OCCUPIED 且订单状态为未下单/已完成/未完成中的任一
     *
     * @return true=存在可清理的餐桌，false=所有餐桌均不可清理
     */
    private boolean hasCleanableTables() {
        for (Tables table : getAllTables()) {
            Tables.TableStatus tableStatus = table.getStatus();
            Tables.OrderStatus orderStatus = table.getOrderStatus();
            Tables.TableType tableType = table.getTableType();

            // 不可清理条件 = (VACANT+Main) OR (OCCUPIED + 三种订单状态之一)
            boolean isUncleanable =
                    tableStatus == Tables.TableStatus.VACANT && tableType == Tables.TableType.MAIN ||
                            (tableStatus == Tables.TableStatus.OCCUPIED &&
                                    (orderStatus == Tables.OrderStatus.NO_ORDER ||
                                            orderStatus == Tables.OrderStatus.ORDERED_FINISHED ||
                                            orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED));

            // 只要有一张桌子不满足不可清理条件 → 存在可清理桌子
            if (!isUncleanable) {
                return true;
            }
        }
        return false; // 所有桌子都不可清理
    }

    /**
     * 检查是否存在可清理的子桌
     *
     * 功能说明：
     * 调用 collectSubTablesForDeletion 方法收集可清理子桌列表，
     * 通过判断列表是否为空来确定是否存在可清理的子桌。
     *
     * @return true=存在可清理的子桌，false=无可清理的子桌
     */
    private boolean hasCleanableSubTables() {
        return !collectSubTablesForDeletion().isEmpty();
    }

    /**
     * 清理可删除的子桌
     *
     * 功能说明：
     * 1. 收集满足清理条件的子桌列表，按主桌分组
     * 2. 逐条删除子桌关联的顾客组记录和订单记录
     * 3. 批量删除子桌数据库记录
     * 4. 检查各主桌是否仍有剩余子桌，若无则恢复主桌为空闲状态
     * 5. 同步更新内存缓存并发布事件通知界面刷新
     *
     * @return true=至少清理成功一个子桌，false=无可清理的子桌
     *
     * 异常处理：
     * - 发生异常时抛出 RuntimeException，由事务管理器回滚操作
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanupSubTables() {
        // 1. 收集需要删除的子桌
        List<Tables> subTablesToDelete = collectSubTablesForDeletion();

        // 2. 无子桌可清理时直接返回
        if (subTablesToDelete.isEmpty()) {
            return false;
        }

        // 3. 按主桌ID分组（用于后续恢复主桌状态）
        Map<Integer, List<Tables>> groupedByMainTable = subTablesToDelete.stream()
                .collect(Collectors.groupingBy(Tables::getMainTableId));

        try {
            // === 关键：先清理关联的顾客组和订单 ===
            for (Tables subTable : subTablesToDelete) {
                // 3.1 删除关联的顾客组（如果存在）
                if (subTable.getCurrentGroupId() != null) {
                    customerGroupMapper.delete(subTable.getCurrentGroupId());
                }

                // 3.2 删除该子桌的订单记录（外键约束：先删子表）
                orderMapper.deleteTableOrdersByTableId(subTable.getTableId());
            }

            // 4. 批量删除子桌记录
            List<Integer> tableIdsToDelete = subTablesToDelete.stream()
                    .map(Tables::getTableId)
                    .collect(Collectors.toList());
            tablesMapper.deleteSubTables(tableIdsToDelete);

            // 5. 检查并恢复主桌状态
            for (Map.Entry<Integer, List<Tables>> entry : groupedByMainTable.entrySet()) {
                Integer mainTableId = entry.getKey();

                // 查询该主桌下是否还有剩余子桌
                List<Tables> remainingSubTables = tablesMapper.findSubTablesByMainId(mainTableId);

                if (remainingSubTables.isEmpty()) {
                    // 从内存缓存获取主桌对象
                    Tables mainTable = tableMap.values().stream()
                            .filter(t -> t.getTableId() == mainTableId)
                            .findFirst()
                            .orElse(null);

                    if (mainTable != null && mainTable.isSplit()) {
                        // 5.1 更新数据库：恢复主桌为空闲状态
                        tablesMapper.restoreMainTableAfterRecombine(
                                mainTableId,
                                Tables.TableStatus.VACANT.name(),
                                false  // isSplit = false
                        );

                        // 5.2 同步更新内存缓存
                        mainTable.setStatus(Tables.TableStatus.VACANT);
                        mainTable.setSplit(false);
                        mainTable.setCurrentGroupId(null);
                        mainTable.setCurrentGroup(null);
                        mainTable.setStartTime(null);
                        mainTable.setEndTime(null);
                        mainTable.setActualSeats(0);

                        System.out.println(" 主桌 #" + mainTable.getDisplayId() + " 已恢复为空闲状态");
                    }
                }
            }

            // 6. 同步内存缓存 + 发布事件通知
            updateMemoryAfterSubTableDeletion(subTablesToDelete, groupedByMainTable);
            return true;

        } catch (Exception e) {
            System.err.println(" 子桌清理失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("子桌清理失败", e);
        }
    }


    /**
     * 判断单个子桌是否满足清理条件
     *
     * 功能说明：
     * 检查子桌是否满足以下任一条件即可清理：
     * 1. 订单状态为已结账（CHECKED_OUT），无论餐桌状态如何
     * 2. 餐桌状态为准备中（SETTING_UP），无论订单状态如何（客人已离店）
     * 3. 订单状态为未下单（NO_ORDER）且餐桌状态为空闲（VACANT）
     *
     * @param subTable 待判断的子桌实体对象
     * @return true=满足清理条件可删除，false=不满足条件需保留
     */
    private boolean isSubTableDeletable(Tables subTable) {
        Tables.OrderStatus orderStatus = subTable.getOrderStatus();
        Tables.TableStatus tableStatus = subTable.getStatus();

        return orderStatus == Tables.OrderStatus.CHECKED_OUT ||          // 条件1: 已结账（无视status）
                tableStatus == Tables.TableStatus.SETTING_UP ||           // 条件2: 准备中（无视orderStatus，客人已走）
                (orderStatus == Tables.OrderStatus.NO_ORDER &&            // 条件3: 未下单+空闲
                        tableStatus == Tables.TableStatus.VACANT);
    }

    /**
     * 收集可清理的子桌列表
     *
     * 功能说明：
     * 1. 按主桌ID分组所有子桌，确保同一主桌的子桌作为整体处理
     * 2. 验证每组子桌是否全部满足清理条件（任一子桌不满足则整组跳过）
     * 3. 返回所有可安全删除的子桌列表，保证数据一致性
     *
     * 业务规则：
     * - 同一主桌的所有子桌必须同时可清理，才能执行删除操作
     * - 子桌状态可不同（如已结账/准备中），但每个子桌需各自满足任一清理条件
     *
     * @return 可清理的子桌列表，为空时表示无满足条件的子桌
     */
    private List<Tables> collectSubTablesForDeletion() {
        // 1. 按主桌ID分组所有子桌
        Map<Integer, List<Tables>> subTablesByMainTable = new HashMap<>();
        for (Tables table : getAllTables()) {
            if (table.getTableType() != Tables.TableType.SUBTABLE ||
                    table.getMainTableId() == null) {
                continue;
            }
            //按主桌 ID 將子桌進行分組。若該主桌 ID 尚未建立對應列表，則自動創建一個空列表，隨後將當前子桌加入該列表中。
            //執行邏輯：
           // key 已存在且對應值不為 null 直接返回該值，不執行 Lambda
            // key 不存在 或 對應值為 null 執行 Lambda 計算新值 → 存入 Map → 返回新值
            subTablesByMainTable
                    .computeIfAbsent(table.getMainTableId(), k -> new ArrayList<>())
                    .add(table);
        }

        // 2. 按主桌分组验证：所有子桌必须同时满足清理条件
        List<Tables> candidates = new ArrayList<>();
        for (Map.Entry<Integer, List<Tables>> entry : subTablesByMainTable.entrySet()) {
            Integer mainTableId = entry.getKey();
            List<Tables> subTables = entry.getValue();

            //  关键修复：检查该主桌的【所有】子桌是否都可清理
            //allMatch 是 Java Stream API 中的終端操作，用於判斷流中的所有元素是否都滿足指定條件。
            //subTables.stream()：將子桌列表轉為數據流
            //allMatch(...)：逐一呼叫 isSubTableDeletable() 檢查每個子桌
            //返回結果：只有當列表中的每一個子桌都返回 true 時，allDeletable 才會是 true；只要有一個子桌不滿足條件，就立即返回 false
            boolean allDeletable = subTables.stream()
                    .allMatch(this::isSubTableDeletable);

            if (allDeletable) {
                // 仅当所有子桌都满足条件时，才将它们加入候选列表
                //addAll(Collection<? extends E> c)：将另一个集合中的所有元素批量追加到当前列表。
                candidates.addAll(subTables);
                System.out.println(" 主桌 #" + mainTableId + " 的 " + subTables.size() +
                        " 个子桌全部满足清理条件: " +
                        subTables.stream()
                                .map(t -> t.getDisplayId() + "(" + t.getStatus() + "/" + t.getOrderStatus() + ")")
                                .collect(Collectors.joining(", ")));
            } else {
                // 任一子桌不满足条件 → 整组跳过（不清理任何子桌）
                System.out.println(" 主桌 #" + mainTableId + " 有子桌不满足清理条件，跳过整组: " +
                        subTables.stream()
                                .map(t -> t.getDisplayId() + "(" +
                                        (isSubTableDeletable(t) ? "✓" : "✗") +
                                        " " + t.getStatus() + "/" + t.getOrderStatus() + ")")
                                .collect(Collectors.joining(", ")));
                //Collectors.joining() 是 Java Stream API 中的字符串连接收集器，用于将流中的元素拼接成一个字符串。
            }
        }

        return candidates;
    }

    /**
     * 事务提交后同步更新内存缓存状态
     *
     * 功能说明：
     * 1. 从内存缓存 tableMap 中移除已删除的子桌记录
     * 2. 检查各主桌是否仍有存活子桌，若无则恢复主桌为空闲状态
     * 3. 重置主桌的拆分标志、顾客组、时间戳、订单状态等临时字段
     *
     * @param deletedSubTables 已从数据库删除的子桌列表
     * @param groupedByMainTable 按主桌ID分组的子桌映射，用于定位需恢复的主桌
     *
     * 执行时机：
     * - 仅在数据库事务成功提交后调用，确保内存状态与持久化数据一致
     */
    private void updateMemoryAfterSubTableDeletion(
            List<Tables> deletedSubTables,
            Map<Integer, List<Tables>> groupedByMainTable) {

        // 1. 从内存中移除子桌
        for (Tables subTable : deletedSubTables) {
            //  使用 tableMap.remove()，key 是 displayId
            tableMap.remove(subTable.getDisplayId());
            System.out.println(" 内存清理: 子桌 #" + subTable.getDisplayId());
        }

        // 2. 恢复主桌内存状态
        for (Map.Entry<Integer, List<Tables>> entry : groupedByMainTable.entrySet()) {
            Integer mainTableId = entry.getKey();

            //  从 tableMap.values() 中查找主桌
            Tables mainTable = null;
            for (Tables t : tableMap.values()) {
                if (t.getTableId() == mainTableId) {
                    mainTable = t;
                    break;
                }
            }

            if (mainTable != null && mainTable.isSplit()) {
                //  检查是否还有存活子桌（使用 tableMap.values()）
                //anyMatch 是 Java Stream API 中的終端操作，用於判斷流中是否存在至少一個元素滿足指定條件。只要找到第一個符合的，就立即返回 true。
                //tableMap.values().stream()：將記憶體中所有餐桌物件轉為數據流
                //.anyMatch(...)：逐一檢查是否存在任意一張餐桌同時滿足三個條件：
                //mainTableId 不為 null
                //mainTableId 正好等於當前正在處理的主桌 ID
                //餐桌類型仍是 SUBTABLE（子桌）
                //返回結果：只要找到一張符合條件的殘留子桌，hasRemaining 即為 true；遍歷完都沒找到則為 false
                boolean hasRemaining = tableMap.values().stream()
                        .anyMatch(t -> t.getMainTableId() != null &&
                                t.getMainTableId().equals(mainTableId) &&
                                t.getTableType() == Tables.TableType.SUBTABLE);

                if (!hasRemaining) {
                    mainTable.setSplit(false);
                    mainTable.setStatus(Tables.TableStatus.VACANT);
                    mainTable.setCurrentGroup(null);
                    mainTable.setCurrentGroupId(null);
                    mainTable.setStartTime(null);
                    mainTable.setEndTime(null);
                    mainTable.setActualSeats(0);
                    mainTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                    System.out.println(" 内存恢复: 主桌 #" + mainTable.getDisplayId() + " 已恢复为空闲状态");
                }
            }
        }
    }

    /**
     * 清理已结账的合并桌
     *
     * 功能说明：
     * 1. 筛选所有类型为 MERGED 的餐桌，按配对关系去重处理
     * 2. 仅当代表桌（编号较小者）订单状态为已结账时执行清理
     * 3. 删除关联的顾客组记录和订单记录
     * 4. 批量更新配对餐桌的状态为 VACANT，类型重置为 MAIN
     * 5. 清空顾客组、预约、时间戳等临时字段，同步更新内存缓存
     *
     * @return true=至少清理成功一对合并桌，false=无可清理的合并桌
     *
     * 异常处理：
     * - 发生任何异常时回滚事务，保证餐桌状态与订单数据的一致性
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanupMergedTables() {
        //System.out.println(" [DEBUG] cleanupMergedTables() 开始执行");
        List<Tables> allTables = getAllTables();
        //  System.out.println(" [DEBUG] 当前餐桌总数: " + allTables.size());

        // 【关键】打印所有餐桌的类型和状态，确认是否有 MERGED
        //   System.out.println("📋 [DEBUG] 所有餐桌状态概览:");
//       for (Tables t : allTables) {
//            System.out.println("   #" + t.getDisplayId() +
//                    " | type=" + t.getTableType() +
//                    " | status=" + t.getStatus() +
//                    " | orderStatus=" + t.getOrderStatus() +
//                    " | mergedWith=" + t.getMergedWith());
//        }
        List<Tables> mergedTables = getAllTables().stream()
                .filter(t -> t.getTableType() == Tables.TableType.MERGED)
                .collect(Collectors.toList());

        if (mergedTables.isEmpty()) {
            return false;
        }

        //這段邏輯本質上是 「防重複執行過濾器」。HashSet 用最少的代碼量、最低的記憶體開銷和最高的查找速度，
        // 解決了「避免同一組合桌被清理兩次」的問題，是 Java 集合框架中處理此類場景的標準實踐。
        Set<String> processedPairs = new HashSet<>();
        boolean cleanedAny = false;

        for (Tables table : mergedTables) {
            String partnerId = table.getMergedWith();
            if (partnerId == null || partnerId.isEmpty()) continue;

            String pairKey = Stream.of(table.getDisplayId(), partnerId)
                    .sorted()
                    .collect(Collectors.joining("|"));
            if (processedPairs.contains(pairKey)) continue;
            processedPairs.add(pairKey);

            Tables partner = getTableById(partnerId);
            if (partner == null || partner.getTableType() != Tables.TableType.MERGED) {
                continue;
            }

            // 只检查代表桌（较小）是否已结账
            Tables representative = table.getDisplayId().compareTo(partner.getDisplayId()) < 0
                    ? table : partner;
            if (representative.getOrderStatus() != Tables.OrderStatus.CHECKED_OUT) {
                continue;
            }

            //  Mapper 调用（无需传 Connection）
            if (representative.getCurrentGroupId() != null) {
                customerGroupMapper.delete(representative.getCurrentGroupId());
            }
//            System.out.println("🔍 [DEBUG] 检查合并桌: " + representative.getDisplayId());
//            System.out.println("   订单状态 (内存): " + representative.getOrderStatus());
//            System.out.println("   期望状态: " + Tables.OrderStatus.CHECKED_OUT);
//            System.out.println("   是否匹配: " + (representative.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT));

            orderMapper.deleteTableOrdersByTableId(representative.getTableId());
            tablesMapper.updateMergedPairToVacant(table.getTableId(), partner.getTableId());

            // 更新内存状态（两张都更新）
            for (Tables t : new Tables[]{table, partner}) {
                t.setMergedWith(null);
                t.setTableType(Tables.TableType.MAIN);
                t.setStatus(Tables.TableStatus.VACANT);
                t.setCurrentGroupId(null);
                t.setStartTime(null);
                t.setEndTime(null);
                t.setCurrentReservationId(null);
                t.setActualSeats(0);
                t.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                t.setCurrentReservationId(null);
            }

            cleanedAny = true;
            //System.out.println("同时清理合并桌: " + table.getDisplayId() + " ↔ " + partner.getDisplayId());
        }


        return cleanedAny;
    }

    /**
     * 检查是否存在可清理的合并桌
     * 清理条件：
     * 1. 餐桌类型 = MERGED（合并桌）
     * 2. 餐桌状态 = OCCUPIED（占用中）
     * 3. 订单状态 = CHECKED_OUT（已结账）
     *
     * @return true=存在可清理的合并桌，false=无可清理的合并桌
     */
    @Transactional(readOnly = true)
    public boolean hasCleanableMergedTables() {
        List<Tables> allTables = tablesMapper.findAllTables();
        for (Tables table : allTables) {
            // 检查是否同时满足三个条件
            if (table.getTableType() == Tables.TableType.MERGED &&
                    table.getStatus() == Tables.TableStatus.OCCUPIED &&
                    table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否存在可清理的合并桌
     * 清理条件：
     * 1. 餐桌类型 = GROUPED（聚餐桌）
     * 2. 餐桌状态 = OCCUPIED（占用中）
     * 3. 订单状态 = CHECKED_OUT（已结账）
     *
     * @return true=存在可清理的合并桌
     */
    @Transactional(readOnly = true)
    public boolean hasCleanableGroupedTables() {
        List<Tables> allTables = tablesMapper.findAllTables();
        for (Tables table : allTables) {
            // 检查是否同时满足三个条件
            if (table.getTableType() == Tables.TableType.GROUPED &&
                    table.getStatus() == Tables.TableStatus.OCCUPIED &&
                    table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理已结账的聚餐桌
     *
     * 功能说明：
     * 将满足清理条件的聚餐桌组重置为空闲主桌状态，执行以下操作：
     * 1. 筛选可清理的聚餐桌组（仅处理每组中编号最小的代表桌，避免重复）
     * 2. 删除关联的顾客组记录
     * 3. 删除关联的订单记录（聚餐桌组共享一个订单，仅删除一次）
     * 4. 批量更新组内所有餐桌的状态为 VACANT，类型重置为 MAIN
     * 5. 清空餐桌的聚餐桌关联字段、预约关联、时间戳等临时数据
     * 6. 同步更新内存缓存，确保后续查询立即生效
     *
     * @return true=至少清理成功一个聚餐桌组，false=无可清理的聚餐桌
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanupGroupedTables() {
       // System.out.println(" [DEBUG] cleanupGroupedTables() 开始执行");

        List<Tables> allTables = getAllTables();

        //  筛选出可清理的聚餐桌（只取代表桌，避免重复处理）
        List<Tables> cleanableGrouped = allTables.stream()
                .filter(t -> t.getTableType() == Tables.TableType.GROUPED)
                .filter(t -> t.getStatus() == Tables.TableStatus.OCCUPIED)
                .filter(t -> t.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT)
                //  关键：只处理 group_with 中编号最小的桌（代表桌），避免同一组重复清理
                .filter(t -> {
                    if (t.getGroupWith() == null || t.getGroupWith().isEmpty()) return false;
                    String[] groupIds = t.getGroupWith().split(",");
                    if (groupIds.length == 0) return false;
                    int currentNum = extractTableNumber(t.getDisplayId());
                    int minNum = Arrays.stream(groupIds)//將字串陣列轉為 Stream<String>，啟用函數式流水線操作
                            .mapToInt(id -> extractTableNumber(id.trim()))//.mapToInt(id -> ...):將每個字串元素映射為基本型別 int，生成 IntStream id.trim():去除首尾空白字元，防範 "13, 14 ,15" 這類格式導致解析失敗
                            .min()//在 IntStream 中尋找最小值
                            .orElse(Integer.MAX_VALUE);//在 IntStream 中尋找最小值
                    return currentNum == minNum;
                })
                .collect(Collectors.toList());

        if (cleanableGrouped.isEmpty()) {
            System.out.println(" 无可清理的聚餐桌");
            return false;
        }

        boolean cleanedAny = false;

        for (Tables representative : cleanableGrouped) {
            try {
                //  解析 group_with 获取所有关联桌号
                String groupWith = representative.getGroupWith();
                if (groupWith == null || groupWith.isEmpty()) continue;

                String[] groupIds = groupWith.split(",");
                List<String> displayIds = Arrays.stream(groupIds)
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .collect(Collectors.toList());

                if (displayIds.isEmpty()) continue;

                System.out.println(" 清理聚餐桌组: " + String.join(",", displayIds));

                // [步骤 1】删除顾客组记录（聚餐桌共享同一个顾客组，只删一次）
                if (representative.getCurrentGroupId() != null) {
                    customerGroupMapper.delete(representative.getCurrentGroupId());
                    System.out.println(" 已删除顾客组: #" + representative.getCurrentGroupId());
                }

                // 【步骤 2】【核心修复】只删除一次订单记录（聚餐桌只有一个订单）
                // 通过代表桌的 tableId 删除关联的订单即可
                orderMapper.deleteTableOrdersByTableId(representative.getTableId());
                System.out.println(" 已删除订单: 代表桌 #" + representative.getDisplayId());

                // 【步骤 3】批量更新所有关联桌的状态
                for (String displayId : displayIds) {
                    Tables table = getTableById(displayId);
                    if (table != null) {
                        //  使用专用的 resetGroupedTableToVacant 方法
                        tablesMapper.resetGroupedTableToVacant(table.getTableId());

                        //  同步更新内存缓存
                        Tables cached = tableMap.get(displayId);
                        if (cached != null) {
                            cached.setStatus(Tables.TableStatus.VACANT);
                            cached.setTableType(Tables.TableType.MAIN);
                            cached.setGroupWith(null);                    //  清空聚餐桌关联字段
                            cached.setCurrentGroupId(null);
                            cached.setActualSeats(0);
                            cached.setStartTime(null);
                            cached.setEndTime(null);
                            cached.setCurrentReservationId(null);
                            cached.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                        }
                        System.out.println(" 餐桌 #" + displayId + " 已重置为空闲主桌");
                    }
                }

                cleanedAny = true;
                System.out.println(" 聚餐桌组清理完成: " + String.join(",", displayIds));

            } catch (Exception e) {
                System.err.println(" 清理聚餐桌组失败: " + representative.getGroupWith());
                System.err.println("  错误: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return cleanedAny;
    }

    /**
     * 检查是否存在可清理的主桌
     *
     * 功能说明：
     * 判断内存缓存中是否存在满足以下任一条件的主桌：
     * - 餐桌状态为 SETTING_UP（准备中）
     * - 餐桌状态为 OCCUPIED 且订单状态为 CHECKED_OUT（已结账的占用桌）
     *
     * @return true=存在可清理的主桌，false=无可清理的主桌
     */
    @Transactional(readOnly = true)
    public boolean hasCleanableMainTables() {
        return tableMap.values().stream()
                .filter(t -> t.getTableType() == Tables.TableType.MAIN)
                .anyMatch(t ->
                        t.getStatus() == Tables.TableStatus.SETTING_UP ||
                                (t.getStatus() == Tables.TableStatus.OCCUPIED &&
                                        t.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT)
                );
    }

    /**
     * 清理可重置的主桌
     *
     * 功能说明：
     * 将满足清理条件的主桌重置为空闲状态，执行以下操作：
     * 1. 筛选可清理的主桌（状态为 SETTING_UP 或已结账的 OCCUPIED）
     * 2. 对已结账的占用桌：删除关联的顾客组记录和订单记录（先删明细再删主表）
     * 3. 更新餐桌状态为 VACANT，清空顾客组、预约、时间戳等临时字段
     * 4. 单独清空 current_reservation_id 字段，确保预约关联彻底解除
     * 5. 同步更新内存缓存，确保界面立即反映最新状态
     *
     * @return true=至少清理成功一个主桌，false=无可清理的主桌
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanupMainTables() {
        // 收集可清理的主桌（从内存缓存）
        //Collectors.toList() 是 Java 8+ 將 Stream 過濾結果「實體化」的標準寫法。其底層預設返回 ArrayList，無需額外指定集合類型，語意清晰且符合業界慣例。
        //ArrayList 基於連續記憶體陣列，遍歷時數據局部性極佳，速度遠快於 LinkedList 的分散節點與指標跳轉。
        List<Tables> mainTablesToClean = tableMap.values().stream()
                .filter(t -> t.getTableType() == Tables.TableType.MAIN)
                .filter(t ->
                        t.getStatus() == Tables.TableStatus.SETTING_UP ||
                                (t.getStatus() == Tables.TableStatus.OCCUPIED &&
                                        t.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT)
                )
                .collect(Collectors.toList());

        if (mainTablesToClean.isEmpty()) {
            System.out.println(" 无可清理的主桌");
            return false;
        }

        boolean cleanedAny = false;

        for (Tables table : mainTablesToClean) {
            String displayId = table.getDisplayId();
            int tableId = table.getTableId();

            // ── 情况1：已结账的占用桌，需要清理顾客组和订单 ──
            if (table.getStatus() == Tables.TableStatus.OCCUPIED &&
                    table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {

                // 1. 删除顾客组
                Integer groupId = table.getCurrentGroupId();
                if (groupId != null) {
                    customerGroupMapper.delete(groupId);
                    customerGroupMap.remove(groupId);  // 同步内存缓存
                    System.out.println(" 已删除顾客组: #" + groupId);
                }

                // 2. 删除订单（先删明细，再删主表）
                Integer orderId = orderMapper.findOrderIdByTableId(tableId);
                if (orderId != null) {
                    orderItemMapper.deleteOrderItemsByOrderId(orderId);
                    orderMapper.deleteOrder(orderId);
                    System.out.println(" 已删除订单: #" + orderId);
                }
            }

            // ── 重置餐桌状态为 VACANT ──
            tablesMapper.updateTableStatus(
                    tableId,
                    Tables.TableStatus.VACANT.name(),
                    null,  // currentGroupId
                    0,     // actualSeats
                    null   // startTime
            );

            // 单独清空 current_reservation_id
            tablesMapper.clearCurrentReservationId(tableId);

            // ── 同步更新内存缓存 ──
            Tables cachedTable = tableMap.get(displayId);
            if (cachedTable != null) {
                cachedTable.setStatus(Tables.TableStatus.VACANT);
                cachedTable.setCurrentGroupId(null);
                cachedTable.setCurrentGroup(null);
                cachedTable.setStartTime(null);
                cachedTable.setEndTime(null);
                cachedTable.setActualSeats(0);
                cachedTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                cachedTable.setCurrentReservationId(null);  //  清空预约关联
            }

            cleanedAny = true;
            System.out.println(" 清理主桌完成: #" + displayId);
        }

        return cleanedAny;
    }


    /**
     * 查询单日报表数据
     *
     * 功能说明：
     * 根据指定日期查询餐厅当日经营数据，包括订单统计、营收明细、菜品销量等核心指标。
     *
     * @param date 查询日期，格式必须为 "yyyy-MM-dd"（如 "2026-03-15"）
     * @return 报表数据列表，每条记录包含指标名称、数值、单位等字段
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，提升查询性能
     *
     * 异常处理：
     * - 日期格式校验失败时抛出 IllegalArgumentException，提示正确格式
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailyBusinessReport(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("无效的日期格式，应为 yyyy-MM-dd");
        }
        //  MyBatis 自动获取连接、执行SQL、映射结果、释放连接
        return businessStatusMapper.getDailyReport(date);
    }

    /**
     * 查询日期范围报表数据
     *
     * 功能说明：
     * 根据起止日期查询餐厅在指定时间段内的经营汇总数据，支持多日趋势分析。
     *
     * @param startDate 起始日期，格式必须为 "yyyy-MM-dd"
     * @param endDate 结束日期，格式必须为 "yyyy-MM-dd"
     * @return 报表数据列表，按日期聚合展示营收、订单量、客单价等指标
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，确保查询过程安全高效
     *
     * 异常处理：
     * - 任一日期参数为空或格式错误时抛出 IllegalArgumentException
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDateRangeBusinessReport(String startDate, String endDate) {
        if (startDate == null || endDate == null ||
                !startDate.matches("\\d{4}-\\d{2}-\\d{2}") ||
                !endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("日期格式错误，应为 yyyy-MM-dd");
        }
        return businessStatusMapper.getDateRangeReport(startDate, endDate);
    }

    /**
     * 查询季度菜品销售报表
     *
     * 功能说明：
     * 按年份、季度及菜品分类统计菜品销售数据，用于分析季节性销售趋势与品类表现。
     *
     * @param year 目标年份（如 2026）
     * @param quarter 目标季度（取值："Q1"、"Q2"、"Q3"、"Q4"）
     * @param category 菜品分类（如 "主食"、"饮料"），为空时统计全部分类
     * @return 报表数据列表，包含菜品名称、销量、营收、占比等字段
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，优化数据库查询性能
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQuarterlyDishSalesReport(int year, String quarter, String category) {
        return orderMapper.getQuarterlyDishSalesReport(year, quarter, category);
    }

    /**
     * 获取菜品销售统计可用的年份列表
     *
     * 功能说明：
     * 查询数据库中订单记录涉及的所有年份，用于报表筛选条件。
     *
     * 执行流程：
     * 1. 调用 orderMapper.getAvailableYearsForDishSales 查询可用年份
     * 2. 若查询结果为空或 null，返回当前年份作为兜底值，确保界面始终有可选年份
     *
     * @return 年份字符串列表（如 ["2024", "2025", "2026"]），按升序排列
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，提升查询性能，避免锁表
     *
     * 注意事项：
     * - 兜底逻辑确保报表功能在无历史数据时仍可正常使用
     * - 返回年份格式为四位数字字符串，便于前端直接绑定下拉框
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableYearsForDishSales() {
        List<String> years = orderMapper.getAvailableYearsForDishSales();
        //  兜底逻辑：如果数据库没有数据，返回当前年份
        if (years == null || years.isEmpty()) {
            // 返回仅包含当前年份的不可变列表，确保报表筛选条件始终有默认值
            //Collections.singletonList() 是 Java 集合框架中的一个工具方法，用于创建一个仅包含指定元素的不可变列表。
            return Collections.singletonList(String.valueOf(LocalDate.now().getYear()));
        }
        return years;
    }

    /**
     * 获取取消预约没收定金报表数据
     *
     * 功能说明：
     * 查询指定时间范围内，因取消预约而被没收的定金记录，用于财务对账与业务分析。
     *
     * @param startDate 查询起始日期（格式：yyyy-MM-dd）
     * @param endDate 查询结束日期（格式：yyyy-MM-dd）
     * @return 报表数据列表，每条记录包含预约号、客户信息、定金金额、取消时间等字段
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，确保查询过程不修改数据，提升并发性能
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getForfeitedDepositsReport(String startDate, String endDate) {
        return businessStatusMapper.selectForfeitedDeposits(startDate, endDate);
    }

    /**
     * 根据餐桌显示编号获取餐桌实体
     *
     * 功能说明：
     * 从内存缓存 tableMap 中快速查询餐桌对象，避免频繁访问数据库。
     *
     * @param displayId 餐桌显示编号（如 "7"、"7a"、"13,14,15"）
     * @return 对应的 Tables 实体对象；若缓存未命中则返回 null
     *
     * 性能优化：
     * - 优先查缓存：O(1) 时间复杂度，适用于高频调用场景
     * - 缓存未命中时由调用方决定是否触发数据库查询并刷新缓存
     */
    public Tables getTableById(String displayId) {
        return tableMap.get(displayId);
    }

    /**
     * 获取餐桌的订单状态显示文本
     *
     * 功能说明：
     * 根据餐桌显示编号，返回用于界面展示的订单状态描述，支持缓存查询与自动兜底刷新。
     *
     * 执行流程：
     * 1. 参数校验：displayId 为空时返回默认提示文本
     * 2. 缓存查询：优先从 tableMap 获取餐桌对象，避免查库
     * 3. 缓存未命中：查询数据库并刷新缓存，确保数据最新
     * 4. 状态过滤：仅占用中（OCCUPIED）或已预约（RESERVED）的餐桌显示订单状态
     * 5. 枚举映射：根据 OrderStatus 枚举值返回对应的中文描述文本
     *
     * @param displayId 餐桌显示编号
     * @return 格式化的状态文本（如 " | 订单情况：已结账"），非有效状态时返回空字符串
     *
     * 事务配置：
     * - readOnly=true：声明为只读事务，确保查询过程安全高效
     */
    @Transactional(readOnly = true)
    public String getOrderStatusDisplay(String displayId) {
        if (displayId == null || displayId.isEmpty()) {
            return " | 订单情况：-";
        }

        // 1. 从内存缓存获取餐桌（避免查库）
        Tables table = tableMap.get(displayId);
        if (table == null) {
            // 兜底：查数据库 + 刷新缓存
            table = tablesMapper.findByDisplayId(displayId);
            if (table != null) {
                refreshTableCache();
                table = tableMap.get(displayId);
            }
        }
        if (table == null) return " | 订单情况：餐桌不存在";

        // 2. 仅占用中餐桌显示订单状态
        if (table.getStatus() != Tables.TableStatus.OCCUPIED &&
                table.getStatus() != Tables.TableStatus.RESERVED) {
            return "";
        }

        // 3. 根据 OrderStatus 枚举返回对应文本
        Tables.OrderStatus orderStatus = table.getOrderStatus();
        return switch (orderStatus) {
            case NO_ORDER -> " | 订单情况：未下单";
            case ORDERED_UNFINISHED -> " | 订单情况：已下单 (未完成)";
            case ORDERED_FINISHED -> " | 订单情况：已下单 (已完成)";
            case CHECKED_OUT -> " | 订单情况：已结账";
            default -> " | 订单情况：未知";
        };
    }
}