package com.xjtu.iron.idempotent.core.result;

/** 捕获返回值失败；事务业务执行器据此回滚 Tx-B，再按原有规则记录失败。 */
public final class ResultCaptureException extends Exception {
    public ResultCaptureException(Throwable cause) { super(cause); }
}
