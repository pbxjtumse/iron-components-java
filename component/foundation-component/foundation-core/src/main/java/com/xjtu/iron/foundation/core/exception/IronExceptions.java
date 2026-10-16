package com.xjtu.iron.foundation.core.exception;

import com.xjtu.iron.foundation.core.text.IronStrings;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 异常链工具统一门面。
 *
 * <p>该类只处理异常链、堆栈和包装异常，不判断是否可重试、是否瞬时错误或是否属于业务错误。</p>
 */
public final class IronExceptions {

    private IronExceptions() {
    }

    public static Throwable rootCause(Throwable failure) {
        if (failure == null) {
            return null;
        }
        return org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(failure) == null
                ? failure
                : org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(failure);
    }

    public static boolean contains(Throwable failure, Class<? extends Throwable> type) {
        if (failure == null || type == null) {
            return false;
        }
        return org.apache.commons.lang3.exception.ExceptionUtils.indexOfType(failure, type) >= 0;
    }

    public static Throwable unwrapCompletion(Throwable failure) {
        if ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    public static void reinterruptIfInterrupted(Throwable failure) {
        if (contains(failure, InterruptedException.class)) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 恢复线程中断标记，并返回原异常，便于调用方继续抛出。
     *
     * <p>
     * 该方法主要用于老代码兼容。
     * </p>
     *
     * @param exception 中断异常
     * @return 原始中断异常
     */
    public static InterruptedException restoreInterrupt(
            InterruptedException exception
    ) {
        Thread.currentThread().interrupt();
        return exception;
    }


    public static String briefMessage(Throwable failure, int maxCodePoints) {
        if (failure == null) {
            return null;
        }
        Throwable root = rootCause(failure);
        String message = root.getClass().getName() + ": " + root.getMessage();
        return IronStrings.truncateWithSuffix(message, maxCodePoints, "...");
    }

    public static String stackTraceToString(Throwable failure, int maxCodePoints) {
        if (failure == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return IronStrings.truncateWithSuffix(writer.toString(), maxCodePoints, "...");
    }
}
