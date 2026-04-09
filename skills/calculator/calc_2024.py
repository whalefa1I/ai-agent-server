# 计算 2024 年数据
# 已知 2025 年数据和同比增长率，求 2024 年数据
# 公式：2024 年数据 = 2025 年数据 / (1 + 增长率)

# 营业总收入：2025 年 34.3 亿元，同比 +49%
revenue_2025 = 34.3
revenue_growth = 0.49
revenue_2024 = revenue_2025 / (1 + revenue_growth)

# 总订单金额：2025 年 39.6 亿元，同比 +13%
order_2025 = 39.6
order_growth = 0.13
order_2024 = order_2025 / (1 + order_growth)

# 归母净利润：2025 年 9.2 亿元，同比 +238%
profit_2025 = 9.2
profit_growth = 2.38
profit_2024 = profit_2025 / (1 + profit_growth)

print(f"2024 年营业总收入：{revenue_2024:.2f} 亿元")
print(f"2024 年总订单金额：{order_2024:.2f} 亿元")
print(f"2024 年归母净利润：{profit_2024:.2f} 亿元")
