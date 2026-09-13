package com.xjtu.iron.relational.spi.connection;

/**
 * 当前 Relational Access 调用对物理 Connection 的所有权。
 */
public enum ConnectionOwnership {

    /**
     * 本次访问自行获取 Connection。
     *
     * <p>典型场景：没有外部事务，DefaultConnectionProvider 直接通过 DataSource.getConnection()
     * 拿到连接。Relational Access 执行结束后应该物理关闭连接，将连接归还给连接池。</p>
     */
    OWNED,

    /**
     * Connection 由外部事务上下文持有。
     *
     * <p>典型场景：Spring @Transactional 已经把 Connection 绑定到当前线程，Relational Access
     * 只是借用这条连接。执行结束后只能逻辑释放，不能物理关闭，否则会破坏外层事务。</p>
     */
    BORROWED
}
