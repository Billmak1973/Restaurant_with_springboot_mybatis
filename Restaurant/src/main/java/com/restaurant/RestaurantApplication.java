package com.restaurant;

import com.restaurant.controller.RestaurantController;
import com.restaurant.listener.QueueChangeListener; // 新增导入
import com.restaurant.view.RestaurantView;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;

//1.1. Spring Boot 應用入口與組件掃描
//1. Spring Boot 應用入口與組件掃描
//技術說明：使用 @SpringBootApplication 標註主類，自動開啟組件掃描、自動配置和啟動引導。同時通過 @MapperScan 指定 MyBatis Mapper 接口的位置。
@SpringBootApplication
@MapperScan("com.restaurant.mapper")// // 指定 MyBatis Mapper 掃描路徑
@EnableScheduling// 開啟定時任務支持 1.7  異步處理與定時任務支持 技術說明：通過 @EnableAsync 和 @EnableScheduling 開啟異步方法和定時任務，用於背景數據刷新或自動清理。
//：定時任務確保了業務狀態表（如 business_status）每天自動創建記錄。異步處理可用於耗時操作（如發送通知），避免阻塞主線程。
@EnableAsync // 開啟異步方法支持

//分析：@MapperScan 避免了在每個 Mapper 接口上重複寫 @Mapper，簡化了配置。
// headless=false 是關鍵，確保 Spring Boot 環境下能正常彈出 Swing 窗口。
public class RestaurantApplication {
    public static void main(String[] args) {
        // 設置非頭像模式，允許啟動 Swing GUI
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(RestaurantApplication.class, args);
    }
    //1.4 Swing GUI 與 Spring 生命周期集成
    //通過定義 CommandLineRunner Bean，在 Spring 啟動完成後，將 Swing 界面啟動邏輯移交給 EDT (Event Dispatch Thread) 線程。
    @Bean
    // 🔧 新增注入 QueueChangeListener
    //這是 Spring 與 Swing 混合架構的核心。利用 Spring 管理 Controller 和 Service，然後通過 initGui 將視圖層綁定到這些 Bean 上，確保了 MVC 架構在 Spring 容器內的完整性。
    public CommandLineRunner initGui(RestaurantController controller, QueueChangeListener queueListener) {
        return args -> SwingUtilities.invokeLater(() -> {
            try {
                RestaurantView view = new RestaurantView();

                // 1. 设置 Controller 的 View (   // 注入 View 到 Controller)
                controller.setView(view);

                // 2. 🔧 新增：设置 Listener 的 View (注入 View 到監聽器)
                queueListener.setView(view);

                view.setVisible(true);
                System.out.println("GUI 已啟動");
                controller.updateQueueDisplay();
            } catch (Exception e) {
                System.err.println("GUI 啟動失敗：" + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}