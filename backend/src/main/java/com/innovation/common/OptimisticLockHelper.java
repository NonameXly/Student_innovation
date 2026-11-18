package com.innovation.common;

/**
 * MyBatis-Plus 乐观锁更新结果校验（{@code @Version} + OptimisticLockerInnerInterceptor）。
 */
public final class OptimisticLockHelper {

    public static final int CONFLICT_CODE = 409;
    public static final String CONFLICT_MESSAGE = "数据已被他人修改，请刷新后重试";

    private OptimisticLockHelper() {
    }

    public static void assertUpdated(int rows) {
        if (rows == 0) {
            throw new BusinessException(CONFLICT_CODE, CONFLICT_MESSAGE);
        }
    }

    public static void assertUpdated(boolean success) {
        if (!success) {
            throw new BusinessException(CONFLICT_CODE, CONFLICT_MESSAGE);
        }
    }
}
