package com.restaurant.util;

public class OperationResult<T> {
    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final ErrorType errorType; // 决定弹窗类型（ERROR/WARNING/INFO）

    private OperationResult(boolean success, T data, String errorMessage, ErrorType errorType) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    public static <T> OperationResult<T> success(T data) {
        return new OperationResult<>(true, data, null, null);
    }

    public static <T> OperationResult<T> error(String message) {
        return new OperationResult<>(false, null, message, ErrorType.ERROR);
    }

    public static <T> OperationResult<T> warning(String message) {
        return new OperationResult<>(false, null, message, ErrorType.WARNING);
    }

    public static <T> OperationResult<T> info(String message) {
        return new OperationResult<>(false, null, message, ErrorType.INFO);
    }

    // Getter 省略...
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public ErrorType getErrorType() { return errorType; }
    public T getData() { return data; }

    // 弹窗类型枚举
    public enum ErrorType {
        ERROR,      // JOptionPane.ERROR_MESSAGE → ❌ 红色错误图标
        WARNING,    // JOptionPane.WARNING_MESSAGE → ⚠️ 黄色警告图标
        INFO        // JOptionPane.INFORMATION_MESSAGE → ℹ️ 蓝色信息图标
    }
}