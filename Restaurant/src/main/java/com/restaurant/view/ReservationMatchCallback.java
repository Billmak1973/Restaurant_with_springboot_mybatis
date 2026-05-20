package com.restaurant.view;

/**
 * 预约匹配提醒回调接口
 * 用于 Service 层通知 View 层显示弹窗
 */
public interface ReservationMatchCallback {
    /**
     * 显示预约匹配提醒弹窗
     */
    //5.7 回調接口 (ReservationMatchCallback) 與依賴倒置
    //技術說明：業務層（Service/Controller）需要在特定條件（如預約匹配成功）時彈出 UI 提示，但業務層不應依賴 Swing。通過定義 ReservationMatchCallback 接口，並由 RestaurantView 實現該接口，再通過 setter 注入回 Service，實現了依賴倒置原則（DIP）。
    //這是典型的「面向接口編程」。業務層只依賴抽象接口，不關心具體 UI 實現。當匹配觸發時，Service 直接調用 callback.showAlert()，Swing 彈窗即可彈出。這種設計讓業務邏輯可獨立測試，且未來若想將 Swing 替換為 JavaFX 或 Web，只需替換實現類即可。
    void showReservationMatchAlert(String reservationId, String customerName,
                                   String customerPhone, String reservationTime,
                                   int requiredCapacity, int requiredCount);
}