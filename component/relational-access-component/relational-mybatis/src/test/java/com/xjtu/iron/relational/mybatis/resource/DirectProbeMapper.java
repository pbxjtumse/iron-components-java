package com.xjtu.iron.relational.mybatis.resource;

/** Direct 多数据源测试专用 Mapper。 */
interface DirectProbeMapper {

    /** 读取当前物理数据库的标记值。 */
    String selectMarker();

    /** 更新当前物理数据库的标记值。 */
    int updateMarker(String marker);
}
