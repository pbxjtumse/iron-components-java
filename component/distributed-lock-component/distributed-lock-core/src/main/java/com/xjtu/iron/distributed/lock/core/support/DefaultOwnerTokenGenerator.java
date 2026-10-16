package com.xjtu.iron.distributed.lock.core.support;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * 默认 ownerToken 生成器。
 *
 * <p>生成结果包含应用标识、节点标识、进程标识、线程名、时间戳和 UUID。这样做的目标不是让 token 可预测，
 * 而是在保证唯一性的同时保留一定排查价值。</p>
 */
public final class DefaultOwnerTokenGenerator implements OwnerTokenGenerator {

    /** 应用名称。 */
    private final String applicationName;

    /** 当前节点标识。 */
    private final String nodeId;

    /** 当前进程标识。 */
    private final String processId;

    public DefaultOwnerTokenGenerator() {
        this("unknown-application", defaultNodeId(), defaultProcessId());
    }

    public DefaultOwnerTokenGenerator(String applicationName, String nodeId, String processId) {
        this.applicationName = sanitize(applicationName == null ? "unknown-application" : applicationName);
        this.nodeId = sanitize(nodeId == null ? "unknown-node" : nodeId);
        this.processId = sanitize(processId == null ? "unknown-process" : processId);
    }

    @Override
    public String generate(String namespace, String lockName) {
        String threadName = sanitize(Thread.currentThread().getName());
        String safeNamespace = sanitize(namespace == null ? "default" : namespace);
        String lockHash = Integer.toHexString((lockName == null ? "" : lockName).hashCode());
        return applicationName
                + ':' + safeNamespace
                + ':' + nodeId
                + ':' + processId
                + ':' + threadName
                + ':' + lockHash
                + ':' + System.nanoTime()
                + ':' + UUID.randomUUID();
    }

    private static String defaultNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-node";
        }
    }

    private static String defaultProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return runtimeName == null ? "unknown-process" : runtimeName;
    }

    private static String sanitize(String value) {
        return value.replace(':', '_').replace(' ', '_');
    }
}
