#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import collections

def analyze_chars_dict():
    pinyin_count = collections.Counter()
    
    with open('app/src/main/assets/cn_dicts/chars.dict.yaml', 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 3:
                pinyin_count[parts[1]] += 1
    
    print('拼音词条数量排行（前15）:')
    for pinyin, count in pinyin_count.most_common(15):
        print(f'{pinyin}: {count}个词条')
    
    print(f'\n总拼音数: {len(pinyin_count)}')
    print(f'总词条数: {sum(pinyin_count.values())}')
    
    # 统计超过200个词条的拼音
    over_200 = [(pinyin, count) for pinyin, count in pinyin_count.items() if count > 200]
    print(f'\n超过200个词条的拼音数量: {len(over_200)}')
    for pinyin, count in sorted(over_200, key=lambda x: x[1], reverse=True):
        print(f'  {pinyin}: {count}个词条')
    
    # 统计超过500个词条的拼音
    over_500 = [(pinyin, count) for pinyin, count in pinyin_count.items() if count > 500]
    print(f'\n超过500个词条的拼音数量: {len(over_500)}')
    for pinyin, count in sorted(over_500, key=lambda x: x[1], reverse=True):
        print(f'  {pinyin}: {count}个词条')

if __name__ == '__main__':
    analyze_chars_dict() 