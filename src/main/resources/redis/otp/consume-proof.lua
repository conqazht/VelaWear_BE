local storedScope = redis.call('GET', KEYS[1])
if not storedScope or storedScope ~= ARGV[1] then
    return 0
end

local activeProofKey = redis.call('GET', KEYS[2])
if not activeProofKey or activeProofKey ~= KEYS[1] then
    return 0
end

redis.call('DEL', KEYS[1], KEYS[2])
return 1
