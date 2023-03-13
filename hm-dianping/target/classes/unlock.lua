-- 得到锁的KEY
-- local key =KEYS[1]
-- 当前线程标识
-- local threatId = ARGV[1]
-- 获取锁中的线程标识  get key
--local id = redis.call('get',KEYS[1])

-- 判断是否与指定的标识（当前线程标识）一致
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    -- 	释放锁
    return redis.call('del',KEYS[1])
end
return 0