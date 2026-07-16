local activeChallengeKey = redis.call('GET', KEYS[2])
if not activeChallengeKey or activeChallengeKey ~= KEYS[1] then
    return 0
end

local expectedCode = redis.call('HGET', KEYS[1], 'code')
local storedScope = redis.call('HGET', KEYS[1], 'scope')
if not expectedCode or not storedScope or storedScope ~= ARGV[2] then
    return 0
end

local maxAttempts = tonumber(ARGV[4])
local attempts = tonumber(redis.call('GET', KEYS[3]) or '0')
if attempts >= maxAttempts then
    local ttl = redis.call('PTTL', KEYS[3])
    if ttl < 1 then
        ttl = tonumber(ARGV[5])
        redis.call('PEXPIRE', KEYS[3], ttl)
    end
    return -(ttl + 1)
end

if expectedCode ~= ARGV[1] then
    attempts = redis.call('INCR', KEYS[3])
    if attempts == 1 or redis.call('PTTL', KEYS[3]) < 1 then
        redis.call('PEXPIRE', KEYS[3], ARGV[5])
    end
    if attempts >= maxAttempts then
        local ttl = tonumber(ARGV[5])
        redis.call('PEXPIRE', KEYS[3], ttl)
        return -(ttl + 1)
    end
    return 0
end

redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])

local previousProofKey = redis.call('GET', KEYS[5])
if previousProofKey then
    redis.call('DEL', previousProofKey)
end

redis.call('SET', KEYS[4], storedScope, 'PX', ARGV[3])
redis.call('SET', KEYS[5], KEYS[4], 'PX', ARGV[3])
return 1
