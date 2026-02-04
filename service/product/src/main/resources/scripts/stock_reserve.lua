-- Stock reservation Lua script for atomic operations
-- KEYS[1]: stock key (e.g., "stock:product:{productId}" or "stock:variant:{variantId}")
-- ARGV[1]: quantity to reserve (decrement)
-- 
-- Returns:
--   1: success (stock reserved)
--   0: insufficient stock
--  -1: key does not exist

local stock_key = KEYS[1]
local quantity = tonumber(ARGV[1])

-- Check if key exists
if redis.call('EXISTS', stock_key) == 0 then
    return -1
end

-- Get current stock
local current_stock = tonumber(redis.call('GET', stock_key))

-- Check if enough stock available
if current_stock < quantity then
    return 0
end

-- Decrement stock (reserve)
redis.call('DECRBY', stock_key, quantity)

return 1
