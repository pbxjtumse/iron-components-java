package com.xjtu.iron.concurrency.demo.error;

/**
 * Demo RPC 依赖异常。
 */
public final class DemoRpcException extends RuntimeException {

    /**
     * 远程服务名称。
     */
    private final String remoteService;

    public DemoRpcException(
            String remoteService,
            String message
    ) {
        super(message);
        this.remoteService = remoteService;
    }

    public String getRemoteService() {
        return remoteService;
    }
}
