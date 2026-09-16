-- Reliable Task 调用 recover() 时的原子恢复抢占。
-- ARGV:
-- 1 nowMs, 2 newOwner, 3 requestHash, 4 routeKey,
-- 5 processingTimeoutMs, 6 recoverProcessingTimeout, 7 recoverFailed,
-- 8 expectedOwner, 9 expectedVersion, 10 scanBucket
local key = KEYS[1]
local now = tonumber(ARGV[1])
local newOwner = ARGV[2]
local requestHash = ARGV[3]
local routeKey = ARGV[4]
local processingTimeout = tonumber(ARGV[5])
local recoverProcessingTimeout = ARGV[6] == '1'
local recoverFailed = ARGV[7] == '1'
local expectedOwner = ARGV[8]
local expectedVersion = ARGV[9]
local scanBucket = ARGV[10]

local function h(name)
    local value = redis.call('HGET', key, name)
    if not value then return '' end
    return value
end

local function snapshot(code, reason)
    return {
        tostring(code), reason or '',
        h('store_name'), h('scan_bucket'),
        h('namespace'), h('key'), h('route_key'), h('request_hash'),
        h('status'), h('owner_token'), h('version'), h('result_payload'),
        h('failure_code'), h('failure_message'), h('failure_retryable'),
        h('recovery_mode'), h('window_policy'), h('processing_expire_at'),
        h('window_expire_at'), h('retention_expire_at'), h('created_at'), h('updated_at'), h('completed_at')
    }
end

if redis.call('EXISTS', key) == 0 then return {'6'} end
if h('recovery_mode') ~= 'EXTERNAL_TASK' then return snapshot(4, '') end

local windowExpireAt = tonumber(h('window_expire_at')) or 0
if windowExpireAt > 0 and windowExpireAt <= now then return snapshot(4, '') end

if expectedVersion ~= '' and h('version') ~= expectedVersion then return snapshot(8, '') end
if expectedOwner ~= '' and h('owner_token') ~= expectedOwner then return snapshot(8, '') end
if h('scan_bucket') ~= scanBucket then return snapshot(7, '') end
if h('route_key') ~= routeKey then return snapshot(7, '') end
local oldHash = h('request_hash')
if oldHash ~= '' and requestHash ~= '' and oldHash ~= requestHash then return snapshot(7, '') end

local status = h('status')
if status == 'SUCCESS' then return snapshot(2, '') end
if status == 'DISCARDED' then return snapshot(9, '') end

local reason = ''
if status == 'PROCESSING' then
    local processingExpireAt = tonumber(h('processing_expire_at')) or 0
    if processingExpireAt > now then return snapshot(3, '') end
    if not recoverProcessingTimeout then return snapshot(4, '') end
    reason = 'PROCESSING_TIMEOUT'
elseif status == 'FAILED' then
    if h('failure_retryable') ~= '1' or not recoverFailed then return snapshot(5, '') end
    reason = h('failure_code')
    if reason == '' then reason = 'FAILED_RETRY' end
else
    return snapshot(5, '')
end

local nextVersion = (tonumber(h('version')) or 0) + 1
redis.call('HSET', key,
    'status', 'PROCESSING',
    'owner_token', newOwner,
    'version', tostring(nextVersion),
    'processing_expire_at', tostring(now + processingTimeout),
    'failure_code', '',
    'failure_message', '',
    'failure_retryable', '0',
    'result_payload', '',
    'completed_at', '',
    'updated_at', tostring(now))

return snapshot(1, reason)
