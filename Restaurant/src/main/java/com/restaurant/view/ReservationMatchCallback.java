package com.restaurant.view;

/**
 * 预约匹配提醒回调接口
 * 用于 Service 层通知 View 层显示弹窗
 */
public interface ReservationMatchCallback {
    /**
     * 显示预约匹配提醒弹窗
     */
    void showReservationMatchAlert(String reservationId, String customerName,
                                   String customerPhone, String reservationTime,
                                   int requiredCapacity, int requiredCount);
}