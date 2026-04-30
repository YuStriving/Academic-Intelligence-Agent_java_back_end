package com.xiaoce.agent.auth.common.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 集合工具类
 * 
 * <p>提供 List、Set、Map 等集合的常用操作方法，简化集合处理逻辑。
 * 
 * <p>功能覆盖：
 * <ul>
 *   <li>List 操作：判空、去重、过滤、映射、分组、合并、切片等</li>
 *   <li>Set 操作：交集、并集、差集、判空等</li>
 *   <li>Map 操作：判空、合并、过滤、转 List 等</li>
 *   <li>通用操作：判空、首尾元素、转字符串等</li>
 * </ul>
 * 
 * @author 小策
 * @since 1.0
 */
public final class CollectionsUtils {

    private CollectionsUtils() {
    }

    // ==================== 通用判空方法 ====================

    /**
     * 判断集合是否为空（null 或 empty）
     *
     * @param collection 集合
     * @return true 表示为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否非空
     *
     * @param collection 集合
     * @return true 表示非空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断 Map 是否为空
     *
     * @param map Map
     * @return true 表示为空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否非空
     *
     * @param map Map
     * @return true 表示非空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    // ==================== List 工具方法 ====================

    /**
     * 安全获取 List 元素，索引越界时返回默认值
     *
     * @param list         列表
     * @param index        索引
     * @param defaultValue 默认值
     * @param <T>          元素类型
     * @return 元素或默认值
     */
    public static <T> T getOrDefault(List<T> list, int index, T defaultValue) {
        if (list == null || index < 0 || index >= list.size()) {
            return defaultValue;
        }
        return list.get(index);
    }

    /**
     * 获取列表第一个元素，列表为空时返回 null
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return 第一个元素
     */
    public static <T> T first(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 获取列表最后一个元素，列表为空时返回 null
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return 最后一个元素
     */
    public static <T> T last(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    /**
     * 获取列表前 n 个元素
     *
     * @param list 列表
     * @param n    数量
     * @param <T>  元素类型
     * @return 前 n 个元素组成的新列表
     */
    public static <T> List<T> take(List<T> list, int n) {
        if (isEmpty(list) || n <= 0) {
            return Collections.emptyList();
        }
        return list.subList(0, Math.min(n, list.size()));
    }

    /**
     * 去掉列表前 n 个元素
     *
     * @param list 列表
     * @param n    数量
     * @param <T>  元素类型
     * @return 去掉前 n 个元素后的新列表
     */
    public static <T> List<T> drop(List<T> list, int n) {
        if (isEmpty(list) || n <= 0) {
            return list == null ? Collections.emptyList() : new ArrayList<>(list);
        }
        if (n >= list.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list.subList(n, list.size()));
    }

    /**
     * List 去重（保持原顺序）
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return 去重后的新列表
     */
    public static <T> List<T> distinct(List<T> list) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 根据属性去重（保持原顺序）
     *
     * @param list     列表
     * @param keyExtractor 提取去重依据的函数
     * @param <T>      元素类型
     * @param <K>      属性类型
     * @return 去重后的新列表
     */
    public static <T, K> List<T> distinctBy(List<T> list, Function<T, K> keyExtractor) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        Set<K> seen = ConcurrentHashMap.newKeySet();
        return list.stream()
                .filter(e -> seen.add(keyExtractor.apply(e)))
                .collect(Collectors.toList());
    }

    /**
     * 过滤 List 元素
     *
     * @param list      列表
     * @param predicate 过滤条件
     * @param <T>       元素类型
     * @return 过滤后的新列表
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * 映射 List 元素
     *
     * @param list   列表
     * @param mapper 映射函数
     * @param <T>    源类型
     * @param <R>    目标类型
     * @return 映射后的新列表
     */
    public static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }

    /**
     * 将 List 按指定大小分片
     *
     * @param list     列表
     * @param chunkSize 分片大小
     * @param <T>      元素类型
     * @return 分片后的列表的列表
     */
    public static <T> List<List<T>> chunk(List<T> list, int chunkSize) {
        if (isEmpty(list) || chunkSize <= 0) {
            return Collections.emptyList();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + chunkSize, list.size()))));
        }
        return chunks;
    }

    /**
     * 合并多个 List
     *
     * @param lists 多个列表
     * @param <T>   元素类型
     * @return 合并后的新列表
     */
    @SafeVarargs
    public static <T> List<T> merge(List<T>... lists) {
        if (lists == null || lists.length == 0) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (List<T> list : lists) {
            if (isNotEmpty(list)) {
                result.addAll(list);
            }
        }
        return result;
    }

    /**
     * List 转 Set
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return Set
     */
    public static <T> Set<T> toSet(List<T> list) {
        if (isEmpty(list)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(list);
    }

    /**
     * List 按指定字段分组
     *
     * @param list     列表
     * @param classifier 分组函数
     * @param <T>      元素类型
     * @param <K>      分组键类型
     * @return 分组后的 Map
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream()
                .collect(Collectors.groupingBy(classifier));
    }

    /**
     * List 转 Map（键值对转换）
     *
     * @param list       列表
     * @param keyMapper  键映射函数
     * @param valueMapper 值映射函数
     * @param <T>        元素类型
     * @param <K>        键类型
     * @param <V>        值类型
     * @return 转换后的 Map
     */
    public static <T, K, V> Map<K, V> toMap(List<T> list,
                                            Function<T, K> keyMapper,
                                            Function<T, V> valueMapper) {
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream()
                .collect(Collectors.toMap(keyMapper, valueMapper, (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * 安全获取 List 大小
     *
     * @param list 列表
     * @return 大小，null 时返回 0
     */
    public static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    // ==================== Set 工具方法 ====================

    /**
     * 求两个 Set 的交集
     *
     * @param set1 集合1
     * @param set2 集合2
     * @param <T>  元素类型
     * @return 交集
     */
    public static <T> Set<T> intersect(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return Collections.emptySet();
        }
        Set<T> result = new LinkedHashSet<>(set1);
        result.retainAll(set2);
        return result;
    }

    /**
     * 求两个 Set 的并集
     *
     * @param set1 集合1
     * @param set2 集合2
     * @param <T>  元素类型
     * @return 并集
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return set2 == null ? Collections.emptySet() : new LinkedHashSet<>(set2);
        }
        if (isEmpty(set2)) {
            return new LinkedHashSet<>(set1);
        }
        Set<T> result = new LinkedHashSet<>(set1);
        result.addAll(set2);
        return result;
    }

    /**
     * 求两个 Set 的差集（set1 - set2）
     *
     * @param set1 集合1
     * @param set2 集合2
     * @param <T>  元素类型
     * @return 差集
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return Collections.emptySet();
        }
        if (isEmpty(set2)) {
            return new LinkedHashSet<>(set1);
        }
        Set<T> result = new LinkedHashSet<>(set1);
        result.removeAll(set2);
        return result;
    }

    /**
     * 判断两个 Set 是否有交集
     *
     * @param set1 集合1
     * @param set2 集合2
     * @param <T>  元素类型
     * @return true 表示有交集
     */
    public static <T> boolean hasIntersection(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return false;
        }
        Set<T> smaller = set1.size() <= set2.size() ? set1 : set2;
        Set<T> larger = smaller == set1 ? set2 : set1;
        for (T item : smaller) {
            if (larger.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set 转 List
     *
     * @param set 集合
     * @param <T> 元素类型
     * @return List
     */
    public static <T> List<T> toList(Set<T> set) {
        if (isEmpty(set)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(set);
    }

    /**
     * 安全获取 Set 大小
     *
     * @param set 集合
     * @return 大小，null 时返回 0
     */
    public static int size(Set<?> set) {
        return set == null ? 0 : set.size();
    }

    // ==================== Map 工具方法 ====================

    /**
     * 安全获取 Map 值，key 不存在时返回默认值
     *
     * @param map          Map
     * @param key          键
     * @param defaultValue 默认值
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 值或默认值
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (isEmpty(map)) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * 合并两个 Map（map2 的值覆盖 map1）
     *
     * @param map1 Map1
     * @param map2 Map2
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 合并后的新 Map
     */
    public static <K, V> Map<K, V> merge(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> result = new LinkedHashMap<>();
        if (isNotEmpty(map1)) {
            result.putAll(map1);
        }
        if (isNotEmpty(map2)) {
            result.putAll(map2);
        }
        return result;
    }

    /**
     * 按值过滤 Map
     *
     * @param map       Map
     * @param predicate 过滤条件
     * @param <K>       键类型
     * @param <V>       值类型
     * @return 过滤后的新 Map
     */
    public static <K, V> Map<K, V> filterByValue(Map<K, V> map, Predicate<V> predicate) {
        if (isEmpty(map)) {
            return Collections.emptyMap();
        }
        return map.entrySet().stream()
                .filter(e -> predicate.test(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * 按键过滤 Map
     *
     * @param map       Map
     * @param predicate 过滤条件
     * @param <K>       键类型
     * @param <V>       值类型
     * @return 过滤后的新 Map
     */
    public static <K, V> Map<K, V> filterByKey(Map<K, V> map, Predicate<K> predicate) {
        if (isEmpty(map)) {
            return Collections.emptyMap();
        }
        return map.entrySet().stream()
                .filter(e -> predicate.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * Map 的 keySet 转 List
     *
     * @param map Map
     * @param <K> 键类型
     * @return key 列表
     */
    public static <K> List<K> keysToList(Map<K, ?> map) {
        if (isEmpty(map)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(map.keySet());
    }

    /**
     * Map 的 values 转 List
     *
     * @param map Map
     * @param <V> 值类型
     * @return value 列表
     */
    public static <V> List<V> valuesToList(Map<?, V> map) {
        if (isEmpty(map)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 安全获取 Map 大小
     *
     * @param map Map
     * @return 大小，null 时返回 0
     */
    public static int size(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }

    // ==================== 数组与集合互转 ====================

    /**
     * 数组转 List
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return List
     */
    public static <T> List<T> arrayToList(T[] array) {
        if (array == null || array.length == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * List 转数组
     *
     * @param list  列表
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 数组
     */
    public static <T> T[] listToArray(List<T> list, Class<T> clazz) {
        if (isEmpty(list)) {
            return (T[]) java.lang.reflect.Array.newInstance(clazz, 0);
        }
        return list.toArray((T[]) java.lang.reflect.Array.newInstance(clazz, list.size()));
    }

    // ==================== 字符串与集合 ====================

    /**
     * 集合元素拼接为字符串
     *
     * @param collection 集合
     * @param delimiter  分隔符
     * @return 拼接后的字符串
     */
    public static String join(Collection<?> collection, String delimiter) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * 集合元素拼接为字符串（默认逗号分隔）
     *
     * @param collection 集合
     * @return 拼接后的字符串
     */
    public static String join(Collection<?> collection) {
        return join(collection, ",");
    }

    /**
     * 字符串分割为 List（过滤空白元素）
     *
     * @param str       字符串
     * @param delimiter 分隔符
     * @return List
     */
    public static List<String> splitToList(String str, String delimiter) {
        if (str == null || str.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(str.split(delimiter))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ==================== 不可变集合 ====================

    /**
     * 创建不可变 List
     *
     * @param elements 元素
     * @param <T>      类型
     * @return 不可变 List
     */
    @SafeVarargs
    public static <T> List<T> immutableList(T... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    /**
     * 创建不可变 Set
     *
     * @param elements 元素
     * @param <T>      类型
     * @return 不可变 Set
     */
    @SafeVarargs
    public static <T> Set<T> immutableSet(T... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(elements)));
    }

    /**
     * 创建不可变 Map
     *
     * @param entries 键值对数组，偶数位为 key，奇数位为 value
     * @param <K>     键类型
     * @param <V>     值类型
     * @return 不可变 Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> immutableMap(Object... entries) {
        if (entries == null || entries.length == 0 || entries.length % 2 != 0) {
            return Collections.emptyMap();
        }
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}

