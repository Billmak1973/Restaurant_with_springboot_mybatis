package com.restaurant.listener;

import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Tables;
import com.restaurant.event.QueueChangedEvent;
import com.restaurant.service.RestaurantService;
import com.restaurant.view.RestaurantView;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 队列变更事件监听器
 * 负责：收到事件 → 刷新 Swing UI
 *  支持精准刷新指定餐桌（合并桌/聚餐桌场景）
 */
@Component
//6.2 @EventListener 自動註冊與事件分發
//技術說明：利用 Spring 的註解驅動機制，自動掃描並註冊事件監聽器，無需手動向 ApplicationContext 註冊。
//Component 確保 Bean 被 Spring 容器管理，@EventListener 自動將方法綁定到 QueueChangedEvent 類型。Spring 會根據事件類型進行路由分發，徹底消除了傳統監聽器模式的模板代碼。
//6.5   核心亮點：Service 層與 View 層的徹底解耦
//技術說明：業務邏輯層不持有任何 View 引用，僅通過 ApplicationEventPublisher 發佈事件。QueueChangeListener 作為「適配器」橋接 Spring 事件系統與 Swing UI。
// 來源：RestaurantService.java (業務層)
//eventPublisher.publishEvent(QueueChangedEvent.fullRefresh(this));
//這是本項目最優秀的解耦設計。RestaurantService 完全不知道 Swing 的存在，只負責發佈「隊列變更」的業務事實。QueueChangeListener 充當了防腐層，將 Spring 的領域事件轉換為 Swing 的 UI 操作。未來若將 GUI 替換為 JavaFX 或 Web 前端，只需更換監聽器實現，業務層代碼零修改。
public class QueueChangeListener {

    private final RestaurantService service;// 餐厅业务服务引用，用于查询排队队列快照、餐桌状态等数据，由构造函数注入确保不可变
    private RestaurantView view;// 餐厅视图层引用，用于执行界面组件的更新操作，通过 setView 方法在初始化后注入，支持延迟绑定

    /**
     * 构造函数：通过依赖注入初始化服务层引用
     *
     * @param service 餐厅业务服务实例，用于查询队列与餐桌数据
     */
    public QueueChangeListener(RestaurantService service) {
        this.service = service;
    }

    /**
     * 设置视图层引用
     *
     * 功能说明：
     * 在 View 层初始化完成后调用此方法，建立监听器与视图的关联，
     * 确保事件处理时可调用界面更新方法。
     *
     * @param view 餐厅主界面视图实例
     */
    public void setView(RestaurantView view) {
        this.view = view;
    }

    /**
     * 处理队列变更事件
     *
     * 功能说明：
     * 1. 校验视图引用有效性，启动期未注入时静默返回
     * 2. 判断当前线程类型：若在 EDT 则直接刷新，否则通过 invokeLater 调度至 EDT
     * 3. 委托 refreshQueueDisplay 方法执行具体的界面更新逻辑
     *
     * 执行特性：
     * - 标注 @EventListener：自动订阅 QueueChangedEvent 类型事件
     * - 标注 @Async：异步执行，避免界面渲染阻塞业务事务提交
     * - 空值防护：view 为 null 时直接返回，避免启动期空指针异常
     *
     * @param event 队列变更事件对象，携带队列类型与受影响餐桌列表等上下文
     */
    //5.2. 異步事件監聽 (@Async & @EventListener)
    //技術說明：通過 @Async 註解將事件監聽器切換至獨立線程池執行，防止耗時的 UI 渲染或日誌記錄阻塞核心業務事務。
    //@Async 確保數據庫事務提交後立即返回，而界面刷新在後台線程排隊執行。結合 @EnableAsync（在 RestaurantApplication 中聲明），Spring 會自動創建默認線程池處理異步任務。
    //6.3 @Async 異步線程池隔離
    //Component 確保 Bean 被 Spring 容器管理，@EventListener 自動將方法綁定到 QueueChangedEvent 類型。Spring 會根據事件類型進行路由分發，徹底消除了傳統監聽器模式的模板代碼。
    //業務層（Service）發佈事件後可立即返回，繼續執行後續邏輯或提交事務，無需等待 UI 刷新完成。@Async 將 UI 渲染任務異步化，避免了業務事務與 UI 更新在同一線程中串行阻塞，顯著提升了系統吞吐量。
    @EventListener// 声明事件监听方法，自动接收并处理指定类型的 Spring 应用事件
    @Async  // 可选：异步执行，避免阻塞业务线程  标记方法为异步执行，调用后立即返回，实际任务在独立线程中运行
    public void handleQueueChanged(QueueChangedEvent event) {
        //  增加空值保护：防止在 View 初始化前触发事件导致空指针
        if (view == null) {
            return;
        }
        // 确保在 EDT 执行（Swing 线程安全）
        //6.4. Swing EDT 線程安全更新機制
        //技術說明：Swing 組件非線程安全，所有 UI 更新必須在 Event Dispatch Thread (EDT) 上執行。監聽器內部通過 isEventDispatchThread() 判斷並自動適配。
        //這是 Swing 開發的鐵律。通過動態判斷當前線程類型，無論事件是從業務線程、定時任務還是 EDT 自身觸發，都能安全地將 UI 刷新動作路由到 EDT，徹底杜絕 ConcurrentModificationException 或界面渲染異常。
        if (SwingUtilities.isEventDispatchThread()) {
            refreshQueueDisplay(event);
        } else {
            SwingUtilities.invokeLater(() -> refreshQueueDisplay(event));
        }
    }

    /**
     * 执行排队队列与餐桌显示的刷新操作
     *
     * 功能说明：
     * 1. 解析事件中的队列类型与受影响餐桌列表
     * 2. 若队列类型为 null（全量刷新）：
     *    - 优先执行精准刷新：遍历受影响餐桌列表，逐个更新餐桌按钮状态
     *    - 若无受影响餐桌列表：刷新全局餐桌缓存并重绘所有餐桌
     *    - 查询三种容量队列的快照，更新排队列表显示
     * 3. 若队列类型非 null（局部刷新）：
     *    - 仅查询指定类型的队列快照，更新对应排队列表显示
     *
     * 刷新策略优先级：
     * - 精准刷新 > 全量刷新 > 局部刷新
     * - 精准刷新仅更新指定餐桌，性能最优，适用于合并桌/聚餐桌操作
     * - 全量刷新重建所有餐桌与队列，适用于初始化或不确定变更范围的场景
     * - 局部刷新仅更新指定队列，适用于单一容量队列的顾客增减
     *
     * 容错处理：
     * - 队列快照为 null 时返回空 LinkedList，避免空指针异常
     * - 餐桌查询失败时跳过该餐桌，不影响其他餐桌刷新
     *
     * @param event 队列变更事件对象，提供刷新所需的上下文信息
     */
    //6.6 事件上下文傳遞與精準刷新策略
    //技術說明：事件攜帶 queueType 和 affectedTableDisplayIds 等上下文信息，監聽器根據信息執行全量刷新或局部精準刷新。
    //避免了「一刀切」的全局刷新。業務層可根據操作類型發佈帶有靶向信息的事件（如合併桌操作只傳 affectedTableDisplayIds），監聽器按需渲染，大幅降低了 UI 重繪開銷，提升了複雜界面下的響應流暢度。
    private void refreshQueueDisplay(QueueChangedEvent event) {
        String queueType = event.getQueueType();
        List<String> affectedTables = event.getAffectedTableDisplayIds();  //  获取受影响餐桌

        //  局部刷新优化：只更新变更的队列（添加 null 检查）
        if (queueType == null) {
            //6.7. 啟動期空值防護與異常隔離
            //技術說明：在應用啟動初期，View 可能尚未注入完成。監聽器首行進行 if (view == null) return; 防護，避免啟動期空指針崩潰。
            //Spring 初始化順序可能導致 @Async 監聽器在 View Bean 注入前被意外觸發（如定時任務或服務啟動初始化）。此防禦性編程確保了系統冷啟動的穩定性，體現了生產級代碼的嚴謹性。
            // 【核心修改】全量刷新时：优先刷新指定餐桌，无指定则刷新全部
            if (view != null) {
                if (affectedTables != null && !affectedTables.isEmpty()) {
                    //  精准刷新：只刷新指定的餐桌（如合并桌的2张/聚餐桌的多张）
                    for (String displayId : affectedTables) {
                        Tables updatedTable = service.getTableById(displayId);
                        if (updatedTable != null) {
                            view.refreshTableButton(displayId, updatedTable);
                        }
                    }
                    System.out.println(" 精准刷新餐桌: " + affectedTables);
                } else {
                    // 兜底：无指定餐桌时刷新全部（保持原有逻辑）
                    service.refreshTableCache();
                    view.updateTablesDisplay(service.getAllTables());
                }
            }

            // 队列显示保持原逻辑
            Queue<CustomerGroup> q2 = (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT");
            Queue<CustomerGroup> q4 = (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT");  //  修复类型转换
            Queue<CustomerGroup> q6 = (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT");

            view.updateQueueDisplay(
                    q2 != null ? q2 : new LinkedList<>(),  // ← 兜底：如果 null 则传空队列
                    q4 != null ? q4 : new LinkedList<>(),
                    q6 != null ? q6 : new LinkedList<>()
            );
        } else {
            // 只刷新指定队列（保持原有逻辑）
            Queue<CustomerGroup> q2 = "2_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("2_SEAT") : new LinkedList<>();
            Queue<CustomerGroup> q4 = "4_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("4_SEAT") : new LinkedList<>();
            Queue<CustomerGroup> q6 = "6_SEAT".equals(queueType) ?
                    (Queue<CustomerGroup>) service.getQueueSnapshot("6_SEAT") : new LinkedList<>();

            view.updateQueueDisplay(q2, q4, q6);
        }
    }
    //6.8. 異步事件的生命周期與 UI 響應優化
    //技術說明：結合 @Async 與 SwingUtilities.invokeLater，形成「業務異步發佈 → 異步線程接收 → 安全排隊至 EDT → UI 異步渲染」的完整生命周期。
    //整個鏈路確保了業務事務的 ACID 特性不受 UI 渲染延遲干擾，同時保證了 Swing 的單線程約束不被破壞。事件驅動的異步模型將耗時的 UI 計算移出業務主鏈，實現了高內聚、低耦合的響應式數據流。
}