package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demo 完成事件监听器。
 *
 * <p>
 * 监听器必须作为独立组件声明，不要在依赖 AsyncExecutor 的 Controller 中通过 @Bean 创建，
 * 否则可能形成 AsyncExecutor -> Listener -> Controller -> AsyncExecutor 循环依赖。
 * </p>
 */
@Component
@Order(100)
public final class DemoCompletedTaskExecutionListener
        implements TaskExecutionListener {

    @Override
    public void onCompleted(TaskExecutionEvent event) {
        System.out.println(
                "[demo-listener] completed taskId="
                        + event.getTaskId()
                        + ", taskName="
                        + event.getTaskName()
                        + ", status="
                        + event.getStatus()
                        + ", executionMode="
                        + event.getExecutionMode()
                        + ", queueCost="
                        + event.getTiming().getQueueCostMillis()
                        + ", runCost="
                        + event.getTiming().getRunCostMillis()
        );
    }
}

