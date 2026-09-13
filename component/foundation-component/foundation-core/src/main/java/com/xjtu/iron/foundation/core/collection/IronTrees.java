package com.xjtu.iron.foundation.core.collection;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 树结构构建工具。
 *
 * <p>此工具只处理技术上的父子关系组装，不承担业务层级校验。若节点出现重复 id 或循环依赖，
 * 方法会主动抛出异常，避免静默生成错误树。</p>
 */
public final class IronTrees {

    private IronTrees() {
    }

    public static <T, K> List<T> buildTree(List<T> nodes,
                                           Function<T, K> idMapper,
                                           Function<T, K> parentIdMapper,
                                           BiConsumer<T, List<T>> childrenWriter) {
        Objects.requireNonNull(idMapper, "idMapper must not be null");
        Objects.requireNonNull(parentIdMapper, "parentIdMapper must not be null");
        Objects.requireNonNull(childrenWriter, "childrenWriter must not be null");
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<K, T> byId = new LinkedHashMap<>();
        Map<K, List<T>> childrenByParent = new LinkedHashMap<>();
        for (T node : nodes) {
            K id = idMapper.apply(node);
            if (id == null) {
                throw new IllegalArgumentException("tree node id must not be null");
            }
            if (byId.put(id, node) != null) {
                throw new IllegalArgumentException("duplicate tree node id: " + id);
            }
            K parentId = parentIdMapper.apply(node);
            childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
        }
        for (T node : nodes) {
            detectCycle(node, idMapper, parentIdMapper, byId, new HashSet<>());
            K id = idMapper.apply(node);
            childrenWriter.accept(node, List.copyOf(childrenByParent.getOrDefault(id, Collections.emptyList())));
        }
        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            K parentId = parentIdMapper.apply(node);
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(node);
            }
        }
        return Collections.unmodifiableList(roots);
    }

    private static <T, K> void detectCycle(T node,
                                           Function<T, K> idMapper,
                                           Function<T, K> parentIdMapper,
                                           Map<K, T> byId,
                                           Set<K> visited) {
        K id = idMapper.apply(node);
        if (!visited.add(id)) {
            throw new IllegalArgumentException("cycle detected in tree nodes at id: " + id);
        }
        K parentId = parentIdMapper.apply(node);
        if (parentId != null && byId.containsKey(parentId)) {
            detectCycle(byId.get(parentId), idMapper, parentIdMapper, byId, visited);
        }
        visited.remove(id);
    }
}
