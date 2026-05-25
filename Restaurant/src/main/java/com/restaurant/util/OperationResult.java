package com.restaurant.util;

public class OperationResult<T> {
    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final ErrorType errorType; // 决定弹窗类型（ERROR/WARNING/INFO）

    /**
     * 私有构造函数
     * 用于创建操作结果对象，设置成功状态、返回数据、错误信息和错误类型
     */
    private OperationResult(boolean success, T data, String errorMessage, ErrorType errorType) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }
    /**
     * 创建成功结果
     * @param data 返回的业务数据
     * @return 成功状态的操作结果
     */
    public static <T> OperationResult<T> success(T data) {
        return new OperationResult<>(true, data, null, null);
    }

    /**
     * 创建错误结果
     * @param message 错误提示信息
     * @return 错误状态的操作结果，类型为 ERROR
     */
    public static <T> OperationResult<T> error(String message) {
        return new OperationResult<>(false, null, message, ErrorType.ERROR);
    }

    /**
     * 创建警告结果
     * @param message 警告提示信息
     * @return 警告状态的操作结果，类型为 WARNING
     */
    public static <T> OperationResult<T> warning(String message) {
        return new OperationResult<>(false, null, message, ErrorType.WARNING);
    }

    public static <T> OperationResult<T> info(String message) {
        return new OperationResult<>(false, null, message, ErrorType.INFO);
    }

    /**
     * 判断操作是否成功
     * @return true=成功，false=失败/警告/信息
     */
    public boolean isSuccess() { return success; }

    /**
     * 获取错误/提示信息
     * @return 提示内容，成功时为 null
     */
    public String getErrorMessage() { return errorMessage; }
    public ErrorType getErrorType() { return errorType; }
    /**
     * 获取返回的业务数据
     * @return 数据对象，失败时为 null
     */
    public T getData() { return data; }

    /**
     * 弹窗类型枚举
     * 用于区分不同级别的提示，对应 JOptionPane 的图标样式
     */
    public enum ErrorType {
        ERROR,      // JOptionPane.ERROR_MESSAGE →  红色错误图标
        WARNING,    // JOptionPane.WARNING_MESSAGE →  黄色警告图标
        INFO        // JOptionPane.INFORMATION_MESSAGE →  蓝色信息图标
    }
}