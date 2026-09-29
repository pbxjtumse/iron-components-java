# Foundation Component Docs

Foundation 是所有 Iron 技术组件的最底层公共能力。它不承载业务语义，也不重新实现 Apache Commons、Jackson 或 JDK 已经成熟的能力。

## 当前模块

```text
foundation-core
foundation-time
foundation-id
foundation-codec
foundation-context
foundation-reflection
foundation-resource
foundation-serialization
foundation-test-support
foundation-architecture-tests
```

## 当前真实入口

- Core：`IronStrings / IronCollections / IronLists / IronMaps / IronSets / IronNumbers / IronEnums / IronExceptions`
- Time：`ClockProvider / Deadline / Expiration / DateRange / TimeRange`
- ID：`StringIdGenerator / LongIdGenerator / IdGenerators / Snowflake / UUID / ULID / NanoID`
- Context：`ExecutionContext / ContextCarrier / StandardContextCodec`
- Serialization：`Serializer / SerializerRegistry / JacksonJsonSerializer`
- Resource：`Resource / ResourceLoader / DefaultResourceLoader / IronResources`

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 序列化主流程](diagrams/sequence/L1-main-flow.puml)
4. [L2 Context 传播](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 Serializer Registry](diagrams/sequence/L3-internal-flow.puml)
6. [L4 上层组件集成](diagrams/sequence/L4-integration-flow.puml)
7. [状态图](diagrams/state/00-full-state.puml)

> Foundation 大部分工具类是无状态的，因此状态图只描述真正具有生命周期语义的基础对象。
