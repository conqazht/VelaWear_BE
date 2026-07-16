local previousChallengeKey = redis.call('GET', KEYS[2])
if previousChallengeKey and previousChallengeKey ~= KEYS[1] then
    redis.call('DEL', previousChallengeKey)
end

local previousProofKey = redis.call('GET', KEYS[3])
if previousProofKey then
    redis.call('DEL', previousProofKey)
end

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1],
        'code', ARGV[1],
        'scope', ARGV[2],
        'purpose', ARGV[3],
        'actor', ARGV[4])
redis.call('PEXPIRE', KEYS[1], ARGV[5])
redis.call('SET', KEYS[2], KEYS[1], 'PX', ARGV[5])
redis.call('DEL', KEYS[3])
return 1
