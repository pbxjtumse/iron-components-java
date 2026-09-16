-- NORMAL execute() 原子状态判断。
-- 不负责超时恢复；PROCESSING 超时只返回 PROCESSING_EXPIRED。
--
-- KEYS[1] redis hash key
-- ARGV:
-- 1 nowMs, 2 ownerToken, 3 requestHash, 4 routeKey, 5 processingTimeoutMs,
-- 6 idempotencyWindowMs, 7 windowPolicy, 8 recordRetentionTtlMs, 9 recoveryMode,
-- 10 storeName, 11 scanBucket, 12 namespace, 13 logicalKey
local key = KEYS[1]
local now = tonumber(ARGV[1])
local owner = ARGV[2]
local requestHash = ARGV[3]
local routeKey = ARGV[4]
local processingTimeout = tonumber(ARGV[5])
local windowMs = tonumber(ARGV[6])
local windowPolicy = ARGV[7]
local retentionMs = tonumber(ARGV[8])
local recoveryMode = ARGV[9]
local storeName = ARGV[10]
local scanBucket = ARGV[11]
local namespace = ARGV[12]
local logicalKey = ARGV[13]

local function h(name)
    local value = redis.call('HGET', key, name)
    if not value then return '' end
    return value
end

local function physical_expire_at(windowExpireAt)
    return windowExpireAt + retentionMs
end

local function apply_physical_expiry(expireAt)
    if expireAt and expireAt > 0 then redis.call('PEXPIREAT', key, expireAt) end
end

local function snapshot(code, rollover)
    return {
        tostring(code), rollover and '1' or '0',
        h('store_name'), h('scan_bucket'),
        h('namespace'), h('key'), h('route_key'), h('request_hash'),
        h('status'), h('owner_token'), h('version'), h('result_payload'),
        h('failure_code'), h('failure_message'), h('failure_retryable'),
        h('recovery_mode'), h('window_policy'), h('processing_expire_at'),
        h('window_expire_at'), h('retention_expire_at'), h('created_at'), h('updated_at'), h('completed_at')
    }
end

local function start_generation(version, createdAt)
    local windowExpireAt = now + windowMs
    local retentionExpireAt = physical_expire_at(windowExpireAt)
    redis.call('HSET', key,
        'store_name', storeName,
        'scan_bucket', scanBucket,
        'namespace', namespace,
        'key', logicalKey,
        'route_key', routeKey,
        'request_hash', requestHash,
        'status', 'PROCESSING',
        'owner_token', owner,
        'version', tostring(version),
        'result_payload', '',
        'failure_code', '',
        'failure_message', '',
        'failure_retryable', '0',
        'recovery_mode', recoveryMode,
        'window_policy', windowPolicy,
        'processing_expire_at', tostring(now + processingTimeout),
        'window_expire_at', tostring(windowExpireAt),
        'retention_expire_at', tostring(retentionExpireAt),
        'created_at', tostring(createdAt),
        'updated_at', tostring(now),
        'completed_at', '')
    apply_physical_expiry(retentionExpireAt)
end

local function touch_sliding_window()
    if windowPolicy ~= 'SLIDING_ON_ACCESS' then return end
    local nextWindow = now + windowMs
    local nextRetention = physical_expire_at(nextWindow)
    redis.call('HSET', key, 'window_expire_at', tostring(nextWindow), 'retention_expire_at', tostring(nextRetention), 'updated_at', tostring(now))
    apply_physical_expiry(nextRetention)
end

if redis.call('EXISTS', key) == 0 then
    start_generation(1, now)
    return snapshot(1, false)
end

-- scanBucket 是稳定的恢复扫描身份，不能因为 WINDOWED 新 generation 而漂移。
if h('scan_bucket') ~= scanBucket then
    return snapshot(7, false)
end

-- 语义窗口结束后，即使记录因为 retention 仍存在，也开启新的 generation。
local oldWindowExpireAt = tonumber(h('window_expire_at')) or 0
if oldWindowExpireAt > 0 and oldWindowExpireAt <= now then
    local nextVersion = (tonumber(h('version')) or 0) + 1
    start_generation(nextVersion, now)
    return snapshot(1, true)
end

-- 在有效窗口内，同一个 key 不能跨业务 route 或跨请求指纹复用。
if h('route_key') ~= routeKey then return snapshot(7, false) end
local oldHash = h('request_hash')
if oldHash ~= '' and requestHash ~= '' and oldHash ~= requestHash then return snapshot(7, false) end

local status = h('status')
if status == 'SUCCESS' then
    touch_sliding_window()
    return snapshot(2, false)
end
if status == 'DISCARDED' then
    touch_sliding_window()
    return snapshot(8, false)
end
if status == 'PROCESSING' then
    local processingExpireAt = tonumber(h('processing_expire_at')) or 0
    if processingExpireAt > now then
        touch_sliding_window()
        return snapshot(3, false)
    end
    return snapshot(4, false)
end
if status == 'FAILED' then
    touch_sliding_window()
    if h('failure_retryable') == '1' then return snapshot(5, false) end
    return snapshot(6, false)
end

return snapshot(6, false)
