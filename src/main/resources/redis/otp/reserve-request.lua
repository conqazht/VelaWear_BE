local ttl = redis.call('PTTL', KEYS[1])
if ttl > 0 then
    return ttl
end
if ttl == -1 then
    return tonumber(ARGV[1])
end

local reserved = redis.call('SET', KEYS[1], '1', 'PX', ARGV[1], 'NX')
if reserved then
    return 0
end
return math.max(redis.call('PTTL', KEYS[1]), 1)
