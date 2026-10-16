package com.xjtu.iron.message.core.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 ThreadLocal 栈保存当前同步消费作用域。
 *
 * <p>它不负责跨线程复制；跨线程传播应由 concurrency-component 捕获和恢复 MessageContextAccessor 快照。</p>
 */
public final class ThreadLocalMessageContextAccessor implements MessageContextAccessor {

    private final ThreadLocal<Deque<CurrentMessage>> contexts =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Optional<CurrentMessage> current() {
        return Optional.ofNullable(contexts.get().peek());
    }

    @Override
    public Scope open(CurrentMessage currentMessage) {
        Objects.requireNonNull(currentMessage, "currentMessage must not be null");
        contexts.get().push(currentMessage);
        return new ContextScope(currentMessage);
    }

    /** Scope 只允许在创建它的线程中按 LIFO 顺序关闭。 */
    private final class ContextScope implements Scope {

        private final CurrentMessage currentMessage;
        private boolean closed;

        private ContextScope(CurrentMessage currentMessage) {
            this.currentMessage = currentMessage;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            Deque<CurrentMessage> stack = contexts.get();
            if (stack.isEmpty() || stack.peek() != currentMessage) {
                throw new IllegalStateException("message context scopes must close in LIFO order");
            }
            closed = true;
            stack.pop();
            if (stack.isEmpty()) {
                contexts.remove();
            }
        }
    }
}
