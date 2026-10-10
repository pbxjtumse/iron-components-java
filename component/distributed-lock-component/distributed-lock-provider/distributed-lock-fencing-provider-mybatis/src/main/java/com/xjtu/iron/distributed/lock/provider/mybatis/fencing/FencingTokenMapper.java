package com.xjtu.iron.distributed.lock.provider.mybatis.fencing;

import org.apache.ibatis.annotations.Param;

/** fencing token 技术表的 MyBatis Mapper。 */
public interface FencingTokenMapper {

    /** 原子递增已存在的 token。 */
    int increment(
            @Param("table") String table,
            @Param("namespace") String namespace,
            @Param("lockName") String lockName
    );

    /** 为首次出现的锁插入 token=1。 */
    int insertInitial(
            @Param("table") String table,
            @Param("namespace") String namespace,
            @Param("lockName") String lockName
    );

    /** 读取当前事务生成的 token。 */
    Long selectCurrent(
            @Param("table") String table,
            @Param("namespace") String namespace,
            @Param("lockName") String lockName
    );
}
