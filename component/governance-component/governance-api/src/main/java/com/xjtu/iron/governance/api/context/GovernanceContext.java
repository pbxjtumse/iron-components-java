package com.xjtu.iron.governance.api.context;



import com.xjtu.iron.governance.model.resource.GovernanceResourceType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class GovernanceContext {

    private String resourceName;

    private GovernanceResourceType resourceType = GovernanceResourceType.OUTBOUND;

    private String downstream;

    private String operation;

    private String traceId;

    private String requestId;

    private Instant startTime = Instant.now();

    /**
     * 二期热点参数限流会使用。
     */
    private Object[] args;

    /**
     * 二期灰度规则会使用。
     */
    private Map<String, String> tags = new HashMap<>();

    private Map<String, Object> attributes = new HashMap<>();

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public GovernanceResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(GovernanceResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getDownstream() {
        return downstream;
    }

    public void setDownstream(String downstream) {
        this.downstream = downstream;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void putAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
}
