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

@SpringBootApplication
@MapperScan("com.restaurant.mapper")
@EnableScheduling
@EnableAsync
public class RestaurantApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(RestaurantApplication.class, args);
    }

    @Bean
    // 🔧 新增注入 QueueChangeListener
    public CommandLineRunner initGui(RestaurantController controller, QueueChangeListener queueListener) {
        return args -> SwingUtilities.invokeLater(() -> {
            try {
                RestaurantView view = new RestaurantView();

                // 1. 设置 Controller 的 View
                controller.setView(view);

                // 2. 🔧 新增：设置 Listener 的 View
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