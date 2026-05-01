package com.restaurant.service;

import com.restaurant.entity.*;
import com.restaurant.event.QueueChangedEvent;
import com.restaurant.mapper.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RestaurantService {

    private final TablesMapper tablesMapper;
    private final CustomerGroupMapper customerGroupMapper;
    private final BusinessStatusMapper businessStatusMapper;
    private final OrderMapper orderMapper; //  新增注入
    private final QueueMapper queueMapper;
    private final OrderItemMapper orderItemMapper;
    private final TableReservationMapper reservationMapper;
    private final DataSource dataSource;

    // Spring 事件发布器
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, Tables> tableMap = new ConcurrentHashMap<>();
    private final Map<Integer, CustomerGroup> customerGroupMap = new ConcurrentHashMap<>();
    private boolean isOpenForBusiness = true;
    private boolean cacheInitialized = false;
    private static final int COLS_PER_ROW = 3;  // 餐桌面板每行3列
    private Queue<CustomerGroup> queue2Seat = new LinkedList<>(); // 2人桌队列
    private Queue<CustomerGroup> queue4Seat = new LinkedList<>(); // 4人桌队列
    private Queue<CustomerGroup> queue6Seat = new LinkedList<>(); // 6人桌队列
    private final Object queueLock = new Object(); // 队列同步锁

    public RestaurantService(
            ApplicationEventPublisher eventPublisher,
            TablesMapper tablesMapper,
            CustomerGroupMapper customerGroupMapper,
            BusinessStatusMapper businessStatusMapper,
            OrderMapper orderMapper,
            QueueMapper queueMapper,
            OrderItemMapper orderItemMapper,
            TableReservationMapper reservationMapper, // 🔧 新增
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

    // ===== 初始化緩存（應用啟動時執行）=====
    @PostConstruct
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
            // 🔧【核心修复】提取异常链中的所有消息，判断是否为"首次启动预期错误"
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
     * 🔧 辅助方法：提取异常链中的所有消息（拼接成完整字符串）
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


    private void loadQueuesFromDatabase() {
        try {
            // 加载2人桌队列
            List<CustomerGroup> q2 = queueMapper.loadQueueByType("2_SEAT");
            queue2Seat = new LinkedList<>(q2);

            // 加载4人桌队列
            List<CustomerGroup> q4 = queueMapper.loadQueueByType("4_SEAT");
            queue4Seat = new LinkedList<>(q4);

            // 加载6人桌队列
            List<CustomerGroup> q6 = queueMapper.loadQueueByType("6_SEAT");
            queue6Seat = new LinkedList<>(q6);

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

    // =====  定時任務：每天 00:00 自動創建當日營業狀態 =====
    @Scheduled(cron = "0 0 0 * * ?")  // 秒 分 時 日 月 周 = 每天午夜 00:00:00
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

    // ===== 兜底方法：確保當日記錄存在（啟動時調用）=====
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

    // ===== 查詢所有餐桌 =====

    @Transactional(readOnly = true)
    public List<Tables> getAllTables() {
        if (!cacheInitialized) {
            try {
                refreshTableCache();
                cacheInitialized = true;
            } catch (Exception e) {
                System.err.println("懶加載緩存失敗: " + e.getMessage());
                return List.of();
            }
        }
        try {
            List<Tables> tables = tablesMapper.findAllTables();
            enrichTablesWithGroups(tables);

            // 🔧【新增】排序：主桌在前，子桌在後
            return sortTablesForDisplay(tables);

        } catch (Exception e) {
            System.err.println("查詢餐桌失敗: " + e.getMessage());
            return List.of();
        }
    }


    public void refreshTableCache() {
        List<Tables> tables = tablesMapper.findAllTables();
        enrichTablesWithGroups(tables);

        // 🔧【核心修复】建立 displayId -> Table 映射，用于快速定位主桌
        Map<String, Tables> displayIdMap = new HashMap<>();
        for (Tables t : tables) {
            displayIdMap.put(t.getDisplayId(), t);
        }

        // 状态缓存：避免对同一个聚餐桌组重复查询数据库
        Map<Integer, Tables.OrderStatus> statusCache = new HashMap<>();

        // 🔧【核心修复】同步订单状态（支持聚餐桌状态共享）
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

        // 🔧 排序後再更新到內存緩存
        List<Tables> sortedTables = sortTablesForDisplay(tables);
        for (Tables table : sortedTables) {
            tableMap.put(table.getDisplayId(), table);
        }
    }


    // ===== 輔助方法：為餐桌關聯顧客組 =====
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
     * 添加顾客组（完整版：支持 4 种分配策略）
     *
     * @param groupSize 顾客组人数
     * @return 创建的顾客组，或 null（如果无法添加）
     */
    @Transactional(rollbackFor = Exception.class)
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
                //   策略 4d: 1-2 人 → 自动分裂占用中的餐桌
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

    // ===== 嘗試分配餐桌 =====
    private Tables tryAssignTableToGroup(CustomerGroup group) throws SQLException {
        int groupSize = group.getGroupSize();

        // 第一層：容量完全匹配的空閒主桌
        List<Tables> available = tablesMapper.findAvailableTables(groupSize, "MAIN");
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


    // ===== 處理餐桌分配（支持局部刷新版）=====
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

        // 🔧【關鍵】4. 同步更新內存緩存（為局部刷新做準備）
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
            // ⚠️ 極少見情況：內存中沒有該餐桌（可能是緩存未初始化）
            System.err.println(" 警告: 餐桌 #" + table.getDisplayId() + " 不在內存緩存中，建議調用 refreshTableCache()");
        }

        // 5. 更新顧客組對象引用（保持業務層數據一致）
        group.setAssigned(true);
        group.setTableId(table.getTableId());

        System.out.println(" 顧客組 #" + group.getCallNumber() +
                " 已分配到餐桌 #" + table.getDisplayId());
    }


    /**
     * 🔧 策略4b/4c：嘗試合併兩張相鄰空桌分配給顧客組
     *
     * @param group         顧客組
     * @param tableCapacity 單張餐桌容量（2或4）
     * @return true=分配成功，false=無可用相鄰桌
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

            Tables table1 = adjacentPair.get(0);
            Tables table2 = adjacentPair.get(1);

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

            // 🔧【关键修复】5. 更新顾客组分配状态
            int groupUpdated = customerGroupMapper.updateAssignmentStatus(
                    group.getGroup_id(),
                    table1.getTableId(),
                    true,
                    false
            );
            if (groupUpdated == 0) {
                throw new RuntimeException("更新顧客組失敗");
            }
            // 6. 🔧 同步更新內存緩存
            syncMergedTablesToCache(table1, table2, group, seats1, seats2);
            group.setAssigned(true);
            group.setTableId(table1.getTableId());

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
     * 🔧 輔助方法：在列表中查找相鄰的餐桌對
     *
     * @param tables   可用餐桌列表（已按base_id排序）
     * @param capacity 餐桌容量
     * @return 相鄰餐桌對（2張），或null
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
     * 🔧 判斷兩張餐桌是否相鄰
     * 相鄰條件：
     * 1. 同一行：base_id / COLS_PER_ROW 的商相同
     * 2. 連續編號：base_id 相差 1
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
     * 🔧 同步更新內存緩存中的合併餐桌狀態
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
     * 获取合并餐桌的伙伴桌（通过 displayId 查询）
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
     * 🔧 策略 4d：尝试自动分裂占用中的餐桌（1-2 人专用）
     *
     * @param group 新顾客组
     * @return true=分裂成功，false=无可分裂餐桌
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

        // 4. 🔧 执行数据库操作（复用传入的 conn，不使用 @Transactional）

        // 4.1 更新主桌为 SPLITTING 状态
        int updated = tablesMapper.updateMainTableToSplitting(targetTable.getTableId());
        if (updated == 0) {
            throw new RuntimeException("更新主桌状态失败");
        }

        // 🔧【关键修复】4.2 分别插入两个子桌（确保主键回填）
        // 插入子桌 A
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

        // 🔧 4.3 更新顾客组的餐桌关联（使用已回填的 table_id）
        customerGroupMapper.updateTableId(existingGroup.getGroup_id(), subTableA.getTableId());
        customerGroupMapper.updateTableId(group.getGroup_id(), subTableB.getTableId());

        // 4.4 🔧【关键】迁移原订单到子桌 A（原顾客组继续使用）
        orderMapper.migrateOrdersToTable(targetTable.getTableId(), subTableA.getTableId());
        System.out.println(" 订单已迁移至子桌 #" + subTableA.getDisplayId());

        // 5. 🔧 同步更新内存缓存（事务提交后由调用方刷新）
        syncMemoryAfterAutoSplit(targetTable, subTableA, subTableB, existingGroup, group, originalStartTime);

        // 6. 从队列移除新顾客组（内存操作）
        removeFromQueue(group);

        System.out.println(" 餐桌 #" + targetTable.getDisplayId() +
                " 自动分裂成功: " + existingSize + "人 + " + newGroupSize + "人");
        return true;

    }

    /**
     * 🔧 内存查找可分裂餐桌（不查数据库，提升性能）
     */
    private Tables findSplittableTableForGroup(int newGroupSize) {
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
     * 🔧 创建子桌对象
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
     * 🔧 同步更新内存缓存（事务提交后调用）
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
     * 🔧 从所有队列中移除顾客组
     */
    private void removeFromQueue(CustomerGroup group) {
        synchronized (queueLock) {
            queue2Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue4Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue6Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
        }
    }



@Transactional(rollbackFor = Exception.class)
public void processCustomerDeparture(String displayId) {
    // 1. 查询主餐桌
    Tables mainTable = tablesMapper.findByDisplayId(displayId);
    if (mainTable == null) {
        throw new IllegalArgumentException("餐桌不存在: " + displayId);
    }

    // 🔧【核心】收集所有需要处理的餐桌（支持合并桌 + 聚餐桌）
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
                    System.err.println("⚠️ 聚餐桌关联桌不存在: " + trimmedId);
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

    // 🔧【关键修复】收集所有需要删除的预约记录ID（用Set去重）
    Set<String> reservationIdsToDelete = new HashSet<>();
    for (Tables t : tablesToProcess) {
        if (t.getCurrentReservationId() != null && !t.getCurrentReservationId().isEmpty()) {
            reservationIdsToDelete.add(t.getCurrentReservationId());
        }
    }

    // 🔧【调试日志】输出处理列表
    System.out.println(" [DEBUG] processCustomerDeparture:");
    System.out.println("   主桌: #" + displayId + " (类型:" + mainTable.getTableType() + ")");
    System.out.println("   处理餐桌数: " + tablesToProcess.size());
    for (Tables t : tablesToProcess) {
        System.out.println("   - 餐桌#" + t.getDisplayId() +
                ", reservationId: " + t.getCurrentReservationId());
    }
    System.out.println("   待删除预约记录数: " + reservationIdsToDelete.size());

    // 🔧【批量处理】更新所有关联餐桌的内存缓存 + 数据库状态
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
            memoryTable.setCurrentReservationId(null);  // 🔧 清空预约ID关联
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
     * 🔧【核心修复】删除订单和预约记录的顺序（关键！外键约束）
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
     * 🔧 步骤1：收集所有需要删除的订单ID（支持普通桌/合并桌/聚餐桌 + 预约订单）
     */
    Set<Integer> orderIdsToDelete = new HashSet<>();

    for (Tables table : tablesToProcess) {
        Integer orderId = null;

        // 🔧 判断是否为预约关联的餐桌
        if (table.getCurrentReservationId() != null && !table.getCurrentReservationId().isEmpty()) {
            // 🔧 预约订单：通过 reservation_id 查询订单
            orderId = orderMapper.findOrderIdByReservationId(table.getCurrentReservationId());
        } else {
            // 🔧 普通堂食订单：通过 table_id 查询订单
            orderId = orderMapper.findOrderIdByTableId(table.getTableId());
        }

        if (orderId != null) {
            orderIdsToDelete.add(orderId);
        }
    }

    /**
     * 🔧 步骤2：执行删除操作（严格按照外键依赖顺序）
     * 顺序：order_items → table_orders → table_reservations
     */
    for (Integer orderId : orderIdsToDelete) {
        try {
            // 🔧 2.1 先删订单明细（外键约束：order_items → table_orders）
            orderItemMapper.deleteOrderItemsByOrderId(orderId);

            // 🔧 2.2 再删订单主表（外键约束：table_orders → table_reservations）
            orderMapper.deleteOrder(orderId);

            System.out.println(" 已删除订单: #" + orderId + " (明细+主表)");
        } catch (Exception e) {
            // 🔧 记录可能已被其他操作删除，忽略异常（幂等处理）
            System.out.println(" 订单 #" + orderId + " 删除时异常（可能已删除）: " + e.getMessage());
        }
    }

    /**
     * 🔧 步骤3：最后删除预约记录（确保所有引用它的订单已删除）
     */
    for (String reservationId : reservationIdsToDelete) {
        try {
            int deleted = reservationMapper.delete(reservationId);
            System.out.println("️ 已删除预约记录: " + reservationId + " (影响行数: " + deleted + ")");
        } catch (Exception e) {
            // 🔧 记录已删除或不存在时忽略（幂等处理）
            System.out.println(" 预约记录 " + reservationId + " 删除时异常（可能已删除）: " + e.getMessage());
        }
    }

    // 4. 删除顾客组记录（所有桌共享同一个 group，只删一次）
    if (mainTable.getCurrentGroupId() != null) {
        customerGroupMapper.delete(mainTable.getCurrentGroupId());
        System.out.println("️ 已删除顾客组: #" + mainTable.getCurrentGroupId());
    }

    // 🔧【调试日志】输出最终结果
    String tableList = tablesToProcess.stream()
            .map(Tables::getDisplayId)
            .collect(Collectors.joining(","));
    System.out.println(" [DEBUG] 离店处理完成: 餐桌组 [" + tableList + "]" +
            ", 删除预约记录数: " + reservationIdsToDelete.size() +
            ", 删除订单数: " + orderIdsToDelete.size() + "\n");
}

    public void cleanTable(String displayId) {
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

    }

    /**
     * 拆分餐桌（2 人桌→兩個 1 人桌，4 人桌→兩個 2 人桌）
     *
     * @param displayId 主桌顯示編號（如 "7"）
     * @return 拆分後的子桌列表
     * @throws IllegalStateException 如果不符合拆分條件
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
     * 驗證餐桌是否符合拆分條件
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
     * 創建子桌對象
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
     * 同步更新內存緩存（事務提交後調用）
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
     * 🔧 餐桌顯示排序：主桌在前（1-15），子桌在後（1a,1b,2a,2b...）
     *
     * @param tables 原始餐桌列表
     * @return 排序後的列表
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

        System.out.println("🔧 餐桌排序完成：主桌" + mainTables.size() +
                "張，子桌" + subTables.size() + "張");

        return orderedTables;
    }

    /**
     * 🔧 輔助方法：獲取子桌所屬主桌的 baseId
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
     * 🔧 根据主桌ID查询子桌列表（供Controller调用，避免直接访问Mapper）
     */
    @Transactional(readOnly = true)
    public List<Tables> getSubTablesByMainTableId(int mainTableId) {
        return tablesMapper.findSubTablesByMainId(mainTableId);
    }


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

        // 5. 🔧 關鍵驗證：所有子桌必須為空閒狀態
        for (Tables subTable : subTables) {
            if (subTable.getStatus() != Tables.TableStatus.VACANT) {
                throw new IllegalStateException("子桌 #" + subTable.getDisplayId() +
                        " 必須處於空閒狀態才能合併，當前狀態：" +
                        subTable.getStatus().getDisplayName());
            }
        }

        // 6. 🔧 收集子桌顯示ID（用於日誌，在刪除前收集！）
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

        // 8. 🔧 同步更新內存緩存
        syncMemoryAfterRecombine(mainTable, subTables);

        System.out.println(" 餐桌 #" + mainTableDisplayId + " 合併成功！");
        return mainTable;
    }


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
     * 🔧 入队操作（Spring 事务模式 - 无需手动传 Connection）
     *
     * @param group 顾客组
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

    @Transactional(rollbackFor = Exception.class)
    public void removeFromQueue(int groupId, String queueType) {
        //  步骤1: 数据库操作（事务内执行）
        int deleted = queueMapper.removeFromQueue(groupId, queueType);
        if (deleted == 0) {
            throw new IllegalStateException("顾客组不在队列中: " + groupId);
        }

        // 步骤2: 注册事务回调（关键！事务提交后再同步内存）
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        //  内存同步：事务已提交，数据一致
                        syncQueueToMemory(groupId, queueType);

                        //  发布事件：通知监听器刷新UI
                        eventPublisher.publishEvent(
                                new QueueChangedEvent(RestaurantService.this, queueType)  // ⚠️ 注意 this 的引用
                        );
                    }
                }
        );
    }


    /**
     * 同步内存队列（事务提交后执行）
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
     * 重排队列位置
     */
    private void repositionQueue(Queue<CustomerGroup> queue) {
        List<CustomerGroup> list = new ArrayList<>(queue);
        queue.clear();
        int position = 1;
        for (CustomerGroup group : list) {
            group.setPosition(position++);
            queue.add(group);
        }
    }

    /**
     * 为 UI 提供队列快照（返回副本，线程安全）
     */
    public List<CustomerGroup> getQueueSnapshot(String queueType) {
        synchronized (queueLock) {
            return new LinkedList<>(getQueueByType(queueType));
        }
    }

    private Queue<CustomerGroup> getQueueByType(String type) {
        return switch (type) {
            case "2_SEAT" -> queue2Seat;
            case "4_SEAT" -> queue4Seat;
            case "6_SEAT" -> queue6Seat;
            default -> throw new IllegalArgumentException("未知队列类型: " + type);
        };
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
     * 检查餐桌是否为合并桌中的主桌（编号较小的那张）
     *
     * @param displayId 餐桌显示ID（如 "7" 或 "8"）
     * @return true=是主桌或不是合并桌，false=是合并桌中的伙伴桌
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
     * 从餐桌显示ID中提取数字编号
     *
     * @param displayId 餐桌显示ID（如 "7" 或 "7a"）
     * @return 数字编号
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

            // 7. 更新当日营收
            // ═══════════════════════════════════════════════════════════
            // 🔧【步骤 7 修改】营收日期计算：优先使用 reorder_time
            // ═══════════════════════════════════════════════════════════
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

            // 🔧 ===== 9. 【新增】删除订单记录（和外卖订单保持一致）=====
            // 9.1 删除 order_items 明细
            orderItemMapper.deleteOrderItemsByOrderId(orderId);
            System.out.println(" 已删除订单明细：orderId=" + orderId);


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

            System.out.println(" 堂食结账成功：餐桌" + tableNumber +
                    ", 金额：" + totalAmount +
                    ", 订单记录已删除");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "结账失败：" + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPrepaidInfoByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = reservationMapper.findPrepaidInfoByReservationId(reservationId);
        if (result != null && result.containsKey("prepaid_amount")) {
            Object amountObj = result.get("prepaid_amount");
            if (amountObj instanceof Number) {
                // 🔧 转为 Double 返回给 View
                result.put("prepaid_amount", ((Number) amountObj).doubleValue());
            }
        }
        return result;
    }

    /**
     * 🔧 堂食结账（支持预付定金抵扣 + 修正营收记录）
     *
     * @param tableNumber   餐桌号
     * @param paymentAmount 顾客本次实际支付金额
     * @param revenueAmount 应记录的营收金额（= Math.max(菜品总额, 定金)）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processCheckoutWithRevenue(String tableNumber, double paymentAmount, double revenueAmount) {
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

            // 7. 🔧【核心修复】更新当日营收 - 使用传入的 revenueAmount
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
     * 辅助方法：根据日期获取季度
     */
    private String getQuarterFromDate(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

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
     * 驗證換桌業務規則
     */
    private void validateTableChangeRules(Tables fromTable, Tables toTable) {
        // 🔧【關鍵】先獲取顧客組對象，確保不為 null
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
        // 🔧 使用提前獲取的 groupSize，不再調用 fromTable.getCurrentGroup()
        if (toTable.getCapacity() == 6 && groupSize < 4) {
            throw new IllegalStateException("只有 4 人及以上顧客組才能使用 6 人桌！\n" +
                    "當前顧客組：" + groupSize + "人");
        }
    }

    /**
     * 同步更新内存缓存中的餐桌状态
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
     * 获取所有空闲餐桌（从内存缓存获取，不查数据库）
     *
     * @return 空闲餐桌列表
     */
    @Transactional(readOnly = true)
    public List<Tables> getAllVacantTables() {
        return tableMap.values().stream()
                .filter(table -> table.getStatus() == Tables.TableStatus.VACANT)
                .filter(tables -> tables.getTableType() == Tables.TableType.MAIN)
                .sorted(Comparator.comparingInt(Tables::getBaseId))  // 🔧 按 baseId 升序排序
                .collect(Collectors.toList());
    }


    /**
     * 創建預約（核心業務邏輯：處理三個場景 + 自定義預約號）
     * 預約號格式：R20260322-1234-1（R + 年月日 + 手機尾號 4 位 + 當天全局順序號）
     *
     * @param data 來自 View 的表單數據 Map
     * @return 結果 Map {success: Boolean, message: String, reservationId: String}
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

            // 🔧【新增】預點餐字段（Boolean）
            Boolean preOrder = (Boolean) data.get("preOrder");
            Boolean isPrepaid = (Boolean) data.get("isPrepaid");
            Double prepaidAmount = (Double) data.get("prepaidAmount");
            String notes = (String) data.get("notes");

            // 簡單驗證
            if (customerName == null || customerPhone == null) {
                throw new IllegalArgumentException("姓名和電話不能為空");
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心】生成自定義預約編號：R+ 年月日 + 手機尾號 + 當天全局順序號
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

            // 🔧【關鍵】設置預點餐字段（Boolean，默認 false）
            reservation.setPreOrder(preOrder != null && preOrder);
            reservation.setIsPrepaid(isPrepaid);
            reservation.setPrepaidAmount(prepaidAmount);
            reservation.setNotes(notes);

            // 🔧 設置自定義預約號（作為主鍵）
            reservation.setReservationId(reservationCode);

            // ═══════════════════════════════════════════════════════════
            // 【步驟 3】根據模式分別處理桌子配置
            // ═══════════════════════════════════════════════════════════
            boolean isManualMode = "MANUAL".equals(tableSelectionMode);
            int totalTableCount;
            String configDesc;
            String reservedTableIds = null;  // 🔧【新增】声明 reservedTableIds 变量

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
                // 🔧【關鍵】先檢查 tableConfig 是否為 null
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

                    // 🔧【關鍵】餐桌類型規則驗證（僅數量模式需要，且 tableConfig 不為空）
                    validateTableTypeRules(tableType, tableConfig);
                } else {
                    // tableConfig 為空時的兜底
                    totalTableCount = 0;
                    configDesc = "";
                }
            }

            reservation.setTableCount(totalTableCount);
            reservation.setTableConfigDesc(configDesc);
            reservation.setReservedTableIds(reservedTableIds);  // 🔧【关键】在插入前设置

            // ═══════════════════════════════════════════════════════════
            // 🔧【核心修復】步驟 4：先插入預約記錄（避免外鍵約束錯誤）
            // ═══════════════════════════════════════════════════════════
            // 🔧 關鍵：先檢查編號唯一性
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

            // 🔧【關鍵修復】先插入預約記錄到 table_reservations 表
            // 這樣後續更新 restaurant_tables.current_reservation_id 時外鍵約束才能通過
            reservationMapper.insert(reservation);
            System.out.println("✅ 預約記錄已插入：" + reservationCode +
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

                        // 🔧【修改點 1】使用新方法，傳入 reservationCode
                        tablesMapper.updateTableForReservationWithId(
                                table.getTableId(),
                                "RESERVED",
                                LocalDateTime.now().plusMinutes(90),
                                reservationCode  // 🔧 新增參數
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

                    // 🔧【修改點 2】第一張桌：使用新方法
                    tablesMapper.updateTableForReservationWithId(
                            t1.getTableId(), "RESERVED", reserveTime, reservationCode
                    );
                    // 🔧【修改點 3】第二張桌：使用新方法
                    tablesMapper.updateTableForReservationWithId(
                            t2.getTableId(), "RESERVED", reserveTime, reservationCode
                    );

                    // ========= 記錄 =========
                    // ========= 🔧【關鍵修復】記錄：區分顯示格式和查詢格式 =========
                    // manual_table_numbers: 界面顯示用，用 "+" 連接（如 "10+11"）
                    String mergedIdsDisplay = t1.getDisplayId() + "+" + t2.getDisplayId();
                    // reserved_table_ids: 數據庫查詢用，用 "," 分隔（如 "10,11"），供 FIND_IN_SET 使用
                    String mergedIdsQuery = t1.getDisplayId() + "," + t2.getDisplayId();

                    reservation.setManualTableNumbers(mergedIdsDisplay);   // ✓ 顯示用 "+"
                    reservation.setReservedTableIds(mergedIdsQuery);       // ✓ 查詢用 ","

                    // 🔧【調試日誌】確認寫入值（開發時可啟用）
                    System.out.println("🔧 MERGED 預約記錄：manual_table_numbers=" + mergedIdsDisplay +
                            ", reserved_table_ids=" + mergedIdsQuery);
                }

                // ========= 🔧【新增分支 3】GROUP（聚餐桌：3 張或以上 6 人桌） =========
                else if ("GROUP".equals(tableType)) {

                    // 🔧 驗證 1: 數量必須 >= 3 張
                    if (selectedTables.size() < 3) {
                        throw new IllegalArgumentException("聚餐桌必須選擇 3 張或以上的餐桌！當前選擇：" + selectedTables.size() + "張");
                    }

                    // 🔧 驗證 2: 每張餐桌必須是 6 人桌 + 空閒狀態
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
                        // 🔧【修改點 4】循環內：使用新方法
                        tablesMapper.updateTableForReservationWithId(
                                table.getTableId(),
                                "RESERVED",
                                reserveTime,
                                reservationCode  // 🔧 新增參數
                        );
                        // 🔧 更新餐桌的 group_with 和 table_type（用於離店時識別關聯桌）
                        tablesMapper.updateTableForGroupReservation(
                                table.getTableId(),
                                String.join(",", lockedTableIds),  // group_with: "7,8,9"
                                "GROUPED"                           // table_type: GROUPED
                        );
                    }

                    // ========= 🔧【關鍵】記錄：區分顯示格式和查詢格式 =========
                    // manual_table_numbers: 界面顯示用，用 "+" 連接（如 "7+8+9"）
                    String groupIdsDisplay = String.join("+", lockedTableIds);
                    // reserved_table_ids: 數據庫查詢用，用 "," 分隔（如 "7,8,9"），供 FIND_IN_SET 使用
                    String groupIdsQuery = String.join(",", lockedTableIds);

                    reservation.setManualTableNumbers(groupIdsDisplay);   // ✓ 顯示用 "+"
                    reservation.setReservedTableIds(groupIdsQuery);       // ✓ 查詢用 ","

                    // 🔧【調試日誌】確認寫入值（可選，開發時啟用）
                    System.out.println("🔧 GROUP 預約記錄：manual_table_numbers=" + groupIdsDisplay +
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
            // 🔧【修改步骤 6】预点餐 → 创建订单记录
            // 支持两种场景：
            // 1. 数量模式 + 预点餐 → table_id = NULL
            // 2. 手动模式 + 1.5小时内 + 预点餐 → table_id = 第一个餐桌ID
            // ═══════════════════════════════════════════════════════════
            if (preOrder != null && preOrder) {
                try {
                    // 🔧 判断餐桌模式
                    Integer tableId = null;

                    // 场景2：手动模式 + 1.5小时内 → 获取第一个餐桌的 table_id
                    if (isManualMode && within15h != null && within15h &&
                            selectedTables != null && !selectedTables.isEmpty()) {

                        // 获取第一个餐桌的 table_id
                        String firstTableDisplayId = selectedTables.get(0);
                        Tables firstTable = tablesMapper.findByDisplayId(firstTableDisplayId);
                        if (firstTable != null) {
                            tableId = firstTable.getTableId();
                            System.out.println("🔧 手动模式预点餐：table_id=" + tableId +
                                    ", displayId=" + firstTableDisplayId);
                        }
                    }
                    // 场景1：数量模式 → table_id = NULL
                    // （tableId 保持 null）

                    // 🔧 创建订单记录
                    Order preOrderEntity = new Order();
                    preOrderEntity.setTableId(tableId);                    // 🔧 手动模式有table_id，数量模式为NULL
                    preOrderEntity.setOrderNumber(null);                   // 预定订单无需订单号
                    preOrderEntity.setOrderType("RESERVATION");            // 预定属于堂食
                    preOrderEntity.setDeliveryMethod(null);
                    preOrderEntity.setDeliveryAddress(null);
                    preOrderEntity.setCustomerPhone(customerPhone);
                    preOrderEntity.setCustomerName(customerName);
                    preOrderEntity.setOrderTime(LocalDateTime.now());      // 🔧 新增：订单创建时间

                    // 金额初始为 0，后续点餐后更新
                    preOrderEntity.setItemsTotal(0.0);
                    preOrderEntity.setDeliveryFee(0.0);
                    preOrderEntity.setTotalAmount(0.0);

                    preOrderEntity.setStatus("NO_ORDER");
                    preOrderEntity.setIsCheckedOut(false);

                    // 🔧 关键：设置预付信息 + 关联预约 ID
                    preOrderEntity.setIsPrepaid(isPrepaid != null && isPrepaid);
                    preOrderEntity.setPrepaidAmount(prepaidAmount != null ? prepaidAmount : 0.0);
                    preOrderEntity.setReservationId(reservationCode);      // 🔗 关联预约

                    // 插入订单主表（MyBatis 会回填 orderId）
                    orderMapper.createOrder(preOrderEntity);

                    System.out.println("✅ 预点餐订单已创建: orderId=" +
                            preOrderEntity.getOrderId() +
                            ", reservationId=" + reservationCode +
                            ", tableId=" + tableId +
                            ", 桌型:" + tableType +
                            ", 模式:" + tableSelectionMode);

                } catch (Exception e) {
                    System.err.println("⚠️ 创建预点餐订单失败：" + e.getMessage());
                    // 不抛异常，避免影响预约创建（订单可后续补创）
                    e.printStackTrace();
                }
            }

            result.put("success", true);
            result.put("message", "預約成功！預約號：" + reservationCode);
            result.put("reservationId", reservationCode);  // 🔧 返回自定義編號

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "預約失敗：" + e.getMessage());
            e.printStackTrace();
            // 事務會自動回滾
        }
        return result;
    }

    /**
     * 🔧 生成自定義預約編號：R+ 年月日 + 手機尾號 + 當天全局順序號
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
     * 重載：支持重試時添加序號後綴
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

        // 4. 🔧【關鍵】獲取當天全局最大順序號（不按手機尾號分組！）
        Integer maxSeq = reservationMapper.getMaxSequenceToday(datePrefix);
        int sequence = (maxSeq != null ? maxSeq : 0) + 1 + retryCount;

        // 5. 拼接完整編號：R20260322-1234-1
        //    格式：R + 日期 + "-" + 手機尾號 + "-" + 當天全局順序號
        return datePrefix + "-" + phoneTail + "-" + sequence;
    }

    /**
     * 🔧 輔助方法：驗證餐桌類型規則（僅數量模式使用）
     *
     * @param tableType   餐桌類型
     * @param tableConfig 桌子配置（已確保不為空）
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
        // 🔧【新增】聚餐桌：只能選擇 6 人桌，數量 >= 3 張
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


    public String getReservationIdByTable(String displayId) {
        Tables table = tablesMapper.findByDisplayId(displayId);
        if (table == null) return null;
        return reservationMapper.findActiveReservationIdByTableId(table.getTableId());
    }


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

        // 🔧【核心】收集所有需要处理的餐桌（支持合并桌 + 聚餐桌）
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

            // 🔧【核心修复】合并桌分配：优先填满编号较小的桌子（容量相同）
            int mainSeats, partnerSeats;

            // 提取 displayId 的数字部分进行比较（如 "7"→7, "7a"→7）
            int mainNum = Integer.parseInt(mainTable.getDisplayId().replaceAll("[^0-9]", ""));
            int partnerNum = Integer.parseInt(partnerTable.getDisplayId().replaceAll("[^0-9]", ""));

            if (mainNum <= partnerNum) {
                // 🔹 主桌编号小：先填满主桌，剩余给伙伴桌
                mainSeats = Math.min(actualSeats, mainTable.getCapacity());
                partnerSeats = Math.max(0, actualSeats - mainSeats);
            } else {
                // 🔹 伙伴桌编号小：先填满伙伴桌，剩余给主桌
                partnerSeats = Math.min(actualSeats, partnerTable.getCapacity());
                mainSeats = Math.max(0, actualSeats - partnerSeats);
            }

            seatAllocation.put(mainTable.getDisplayId(), mainSeats);
            seatAllocation.put(partnerTable.getDisplayId(), partnerSeats);

            System.out.println("🔧 合并桌人数分配: 主桌#" + mainTable.getDisplayId() + "=" + mainSeats +
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

            // 🔧【关键】按 displayId 数字排序（确保编号小的先分配剩余人数）
            groupedTables.sort(Comparator.comparingInt(t ->
                    Integer.parseInt(t.getDisplayId().replaceAll("[^0-9]", ""))
            ));

            tablesToProcess.addAll(groupedTables);

            // 🔧【核心算法】平均分配 + 剩余按编号从小到大分配
            int tableCount = groupedTables.size();
            int baseSeats = actualSeats / tableCount;      // 平均每桌人数
            int remaining = actualSeats % tableCount;       // 剩余人数

            System.out.println("🔧 聚餐桌分配计算: 总人数=" + actualSeats +
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

        // 🔧【批量处理】更新所有关联餐桌
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

            // 🔧 更新内存缓存
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

        // ═══════════════════════════════════════════════════════════
        // 🔧【新增步驟 6】更新預約狀態：CONFIRMED → COMPLETED
        // 客人成功入座後，預約記錄標記為已完成
        // ═══════════════════════════════════════════════════════════
        if (reservationId != null && !reservationId.isEmpty()) {
            // 1. 先查詢預約當前狀態，確保是 CONFIRMED 才更新（避免重複更新）
            TableReservation reservation = reservationMapper.findById(reservationId);
            if (reservation != null && "CONFIRMED".equals(reservation.getStatus())) {
                reservationMapper.updateStatus(reservationId, "COMPLETED");
                System.out.println("🔄 預約狀態已更新: " + reservationId + " [CONFIRMED → COMPLETED]");

                // ═══════════════════════════════════════════════════════════
                // 🔧【新增步驟 6.5】更新預點餐訂單類型：RESERVATION → DINE_IN
                // 客人入座後，預點餐訂單轉為正式堂食訂單
                // ═══════════════════════════════════════════════════════════
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

        // 🔧【调试日志】输出最终结果
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
     * 🔧 获取数量模式的预约记录（用于日志显示）
     * 委托给 Mapper 层查询
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQuantityModeReservationsForLog() {
        return reservationMapper.findQuantityModeReservationsForLog();
    }

    /**
     * 🔧 获取数量模式的预约记录（用于日志显示）
     * 委托给 Mapper 层查询
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPreOrderReservationsForMonitor() {
        return reservationMapper.findPreOrderReservationsForMonitor();
    }

    /**
     * 🔧 根据预约号查询完整预约详情
     */
    @Transactional(readOnly = true)
    public TableReservation getReservationDetail(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return null;
        }
        return reservationMapper.findDetailById(reservationId);
    }

    /**
     * 🔧 根据餐桌号查找预定记录（通过 reserved_table_ids 字段）
     */
    @Transactional(readOnly = true)
    public TableReservation findReservationByTableId(String tableDisplayId) {
        if (tableDisplayId == null || tableDisplayId.isEmpty()) {
            return null;
        }
        return reservationMapper.findReservationByTableId(tableDisplayId);
    }


    /**
     * 🔧 分配餐桌給預約記錄（核心業務邏輯 - 完整版）
     *
     * @param reservationId      預約號
     * @param selectedDisplayIds 選中的餐桌顯示 ID 列表
     */
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

        // 🔧【修復1】標準化解析：支持帶空格和不帶空格的格式
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

        // 🔧【調試日誌】
        System.out.println("🔧 分配餐桌驗證 - 預約:" + reservationId +
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

            // 🔧 核心驗證：容量是否匹配 (僅數量模式需要嚴格驗證)
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
        // 🔧【新增步驟 4.5】更新餐桌類型和關聯字段（合併桌/聚餐桌）
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
                System.out.println("🔧 合併桌更新: " + displayId1 + " ↔ " + displayId2);
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
            System.out.println("🔧 聚餐桌更新: " + groupWithStr + " (共" + tableCount + "張)");
        }
        // tableCount == 1 時保持 MAIN，無需更新


        // ═══════════════════════════════════════════════════════════
        // 🔧【步驟 5】更新訂單的 table_id（取最小餐桌號）
        // 規則：無論什麼類型的桌子，只有 1 個訂單，table_id = 最小的餐桌 ID
        // ═══════════════════════════════════════════════════════════
        if (!selectedTableIds.isEmpty()) {
            // 排序，取最小的餐桌 ID 作為主桌 ID
            selectedTableIds.sort(Integer::compareTo);
            int mainTableId = selectedTableIds.get(0);

            // 🔧 更新 table_orders 表的 table_id
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
    }


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
            StringBuilder descBuilder = new StringBuilder();
            int totalTables = 0;
            if (newConfig.containsKey("2")) {
                int qty = newConfig.get("2");
                if (qty > 0) {
                    descBuilder.append("2 人桌 x").append(qty).append(", ");
                    totalTables += qty;
                }
            }
            if (newConfig.containsKey("4")) {
                int qty = newConfig.get("4");
                if (qty > 0) {
                    descBuilder.append("4 人桌 x").append(qty).append(", ");
                    totalTables += qty;
                }
            }
            if (newConfig.containsKey("6")) {
                int qty = newConfig.get("6");
                if (qty > 0) {
                    descBuilder.append("6 人桌 x").append(qty).append(", ");
                    totalTables += qty;
                }
            }
            if (totalTables == 0) {
                throw new IllegalArgumentException("桌子配置不能为空！");
            }
            updated.setTableConfigDesc(descBuilder.toString());
            updated.setTableCount(totalTables);
            hasChanges = true;
            System.out.println(" 修改桌子配置: " + original.getTableConfigDesc() + " → " + descBuilder);
        }

        // ── 修改预点餐 ──
        if (edits.containsKey("preOrder")) {
            Boolean newPreOrder = (Boolean) edits.get("preOrder");
            if (original.getPreOrder() && !newPreOrder) {
                throw new IllegalArgumentException("预点餐状态不能从'是'改为'否'！");
            }
            if (!original.getPreOrder() && newPreOrder) {
                updated.setPreOrder(true);
                hasChanges = true;
                System.out.println(" 修改预点餐: 否 → 是");

                // 🔧【核心修复】传入修改后的预付参数（使用 updated 对象的新值）
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
     * 🔧 辅助方法：比较桌子配置是否相同
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
     * 🔧 创建预点餐订单记录（当预点餐状态从否改为是时调用）
     *
     * @param reservationId 预约号
     * @param isPrepaid     是否预付（修改后的新值）
     * @param prepaidAmount 预付金额（修改后的新值）
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
        preOrder.setStatus("NO_ORDER");  // 🔧 初始状态：未下单

        // 3. 🔧【核心修复】使用传入的新预付参数，而不是原始值
        preOrder.setIsPrepaid(isPrepaid != null && isPrepaid);
        preOrder.setPrepaidAmount(prepaidAmount != null ? prepaidAmount : 0.00);

        // 4. 初始化金额字段
        preOrder.setItemsTotal(0.00);
        preOrder.setDeliveryFee(0.00);
        preOrder.setTotalAmount(prepaidAmount != null ? prepaidAmount : 0.00);  // 🔧 总金额包含预付

        // 5. 插入数据库
        orderMapper.createOrder(preOrder);

        System.out.println(" 预点餐订单已创建: reservationId=" + reservationId +
                ", orderId=" + preOrder.getOrderId() +
                ", isPrepaid=" + preOrder.getIsPrepaid() +
                ", prepaidAmount=" + preOrder.getPrepaidAmount());
    }

    /**
     * 🔧 輔助方法：同步預付信息到關聯訂單
     *
     * @param reservationId 預約號
     * @param isPrepaid     是否預付
     * @param prepaidAmount 預付金額
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
     * 🔧 根据预约号模糊查询
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
     * 🔧 根据电话号码后4位查询
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
     * 🔧 根据预约号片段查询完整预约详情（支持模糊查询） 修改專用
     */
    @Transactional(readOnly = true)
    public List<TableReservation> findReservationsByCodeFragment(String codeFragment) {
        if (codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return reservationMapper.findDetailByCodeFragment(codeFragment);
    }


    /**
     * 🔧 CANCEL 模式专用：根据预约号片段查询（支持所有状态）
     */
    @Transactional(readOnly = true)
    public List<TableReservation> findReservationsForCancel(String codeFragment) {
        if (codeFragment == null || codeFragment.isEmpty()) {
            return Collections.emptyList();
        }
        return reservationMapper.findDetailByCodeFragmentForCancel(codeFragment);
    }


    @Transactional(rollbackFor = Exception.class)
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

            // ═══════════════════════════════════════════════════════════
            // 🔧【步骤 3】先删除预点餐订单（如果有 preOrder=true）
            // ═══════════════════════════════════════════════════════════
            boolean preOrderDeleted = false;  // 🔧【新增】标志：是否删除了预点餐
            if (Boolean.TRUE.equals(reservation.getPreOrder())) {
                deletePreOrderIfExists(reservationId);
                preOrderDeleted = true;  // 🔧【新增】标记为已删除
                System.out.println(" 预点餐订单已删除：" + reservationId);
            }

            // ═══════════════════════════════════════════════════════════
            // 🔧【步骤 4】处理预付定金没收（🔧 核心修复：必须先检查 preOrder）
            // 业务规则：定金只能和预点餐一起存在，无预点餐=无定金
            // ═══════════════════════════════════════════════════════════
            Double forfeitedAmount = 0.0;
            boolean depositForfeited = false;  // 🔧【新增】标志：是否没收了定金
            if (Boolean.TRUE.equals(reservation.getPreOrder()) &&      // 🔧【新增】先检查预点餐
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
                depositForfeited = true;  // 🔧【新增】标记为已没收
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

            // ═══════════════════════════════════════════════════════════
            // 🔧【步骤 7】组装返回结果（新增场景标志 + 用户友好消息）
            // ═══════════════════════════════════════════════════════════
            result.put("success", true);
            result.put("needRefresh", true);

            // 🔹 场景标志（供 Controller 判断提示内容）
            result.put("preOrderDeleted", preOrderDeleted);      // 是否删除了预点餐
            result.put("depositForfeited", depositForfeited);    // 是否没收了定金
            result.put("forfeitedAmount", forfeitedAmount > 0 ? forfeitedAmount : null);

            // 🔹 用户友好消息（默认兜底，Controller 可覆盖）
            String userMessage = buildUserMessage(preOrderDeleted, depositForfeited, forfeitedAmount);
            result.put("userMessage", userMessage);

            // 🔹 保留原有 message 字段（向后兼容）
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
     * 🔧 辅助方法：根据场景构建用户友好消息
     * 四种场景：
     * 1. 仅取消预约 → " 预约已取消！"
     * 2. 取消 + 删预点餐 → " 预约已取消，预点餐订单已删除。"
     * 3. 取消 + 没收定金 → " 预约已取消，并没收定金：100.00 元。"
     * 4. 取消 + 删预点餐 + 没收定金 → " 预约已取消，预点餐订单已删除，并没收定金：100.00 元。"
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
     * 🔧 辅助方法：获取状态中文显示
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
     * 🔧 辅助方法：记录没收定金到 forfeited_deposits 表
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
            // 🔧 记录失败时打印日志，但不中断取消流程（避免影响用户体验）
            System.err.println(" 记录没收定金失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 辅助方法：释放已锁定的餐桌（取消预约时调用）
     * 执行顺序：必须在删除 table_reservations 记录之前！
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
                    // 🔧【核心】调用新方法重置餐桌状态
                    tablesMapper.resetTableAfterReservationCancel(table.getTableId());

                    System.out.println("🔓 餐桌状态已重置: #" + trimmedId +
                            " [RESERVED→VACANT, " + table.getTableType() + "→MAIN]");

                    // 🔧 同步更新内存缓存
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
                System.err.println("⚠️ 释放餐桌失败: " + trimmedId + " - " + e.getMessage());
                // 不中断流程，继续处理其他餐桌
            }
        }
    }

    /**
     * 🔧 辅助方法：删除预约关联的预点餐订单（order_items + table_orders）
     *
     * @param reservationId 预约号
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
     * 🔧 延迟预约（核心业务逻辑 - 完整版）
     *
     * @param reservationId 预约号
     * @param newTime       新的预约时间
     * @param keepTable     是否保留餐桌（true=保留锁定，false=释放餐桌）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delayReservation(String reservationId, LocalDateTime newTime, boolean keepTable) {
        Map<String, Object> result = new HashMap<>();

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

            // 🔧【新增】8. 根据 group_type 重新生成 table_config_desc
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
                releaseReservedTables(reservation.getReservedTableIds());
                System.out.println("🔓 延迟预约释放餐桌: " + reservation.getReservedTableIds());

                clearTableIdByReservationId(reservationId);

            }

            // 11. 组装返回结果
            result.put("success", true);
            result.put("message", "预约延迟成功！新时间: " +
                    newTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            result.put("newTime", newTime);
            result.put("keepTable", keepTable);
            result.put("releaseTables", releaseTables);
            result.put("newStatus", newStatus);
            result.put("needRefresh", releaseTables);  // ← 新增标志位

            System.out.println("✅ 预约延迟完成: " + reservationId +
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

        return result;
    }

    /**
     * 🔧 辅助方法：根据餐桌类型重新生成 table_config_desc（从内存获取）
     *
     * @param groupType   餐桌类型：MAIN / MERGED / GROUP
     * @param reservation 预约记录
     * @return 格式化后的配置描述
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
     * 🔧 生成个人桌配置描述（从内存缓存获取容量）
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
                // 🔧【关键】从内存缓存获取餐桌（不查数据库）
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
     * 🔧 生成合并桌配置描述（从内存缓存获取容量）
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
                // 🔧【关键】从内存缓存获取餐桌（不查数据库）
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
     * 🔧 生成聚餐桌配置描述（从内存缓存获取实际容量）
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

                // 🔧【调试日志】
                System.out.println("🔧 聚餐桌配置: reservationId=" + reservation.getReservationId() +
                        ", capacity=" + capacity + "人桌" +
                        ", tableCount=" + tableCount +
                        ", reservedTableIds=" + reservedTableIds);

                return capacity + "人桌 x" + tableCount + ", ";
            }
        } catch (Exception e) {
            System.err.println("从内存获取聚餐桌容量失败: " + e.getMessage());
        }

        // 兜底：使用原配置描述
        return reservation.getTableConfigDesc();
    }

    /**
     * 🔧 辅助方法：从 table_config_desc 解析容量数字
     * 例如："2人桌 x1, " → 2, "4人桌 x2, " → 4, "6人桌 x3, " → 6
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

    // ===== 根據 displayId 獲取餐桌 =====
    public Tables getTableById(String displayId) {
        return tableMap.get(displayId);
    }

    // ===== 獲取訂單狀態顯示 =====
    public String getOrderStatusDisplay(String tableNumber) {
        // 🔧 修改：删除"餐桌不存在"提示，改为返回空状态
        if (tableNumber == null || tableNumber.isEmpty() || "未选择".equals(tableNumber)) {
            return "订单情况：";  // 返回空状态，而不是"餐桌不存在"
        }

        Tables table = getTableById(tableNumber);
        if (table == null) {
            return "订单情况：";  // 返回空状态，而不是"餐桌不存在"
        }

        return "订单情况：" + table.getOrderStatus().getDisplayName();
    }
}