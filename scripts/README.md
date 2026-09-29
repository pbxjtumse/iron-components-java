# Repository Validation Scripts

脚本用于提供 Maven 执行前的快速、确定性检查，不能替代 Maven Reactor、单元测试或真实中间件联调。

## 根脚本

| 脚本 | 保留原因 | 不负责什么 |
|---|---|---|
| `validate-poms.py` | 检查 XML、聚合模块、Local Parent、内部 dependencyManagement、对外 BOM 和重复声明 | 不解析远程依赖，不判断类是否真的可加载 |
| `check-source-dependencies.py` | 检查常见第三方 API 是否由使用它的模块直接声明 | 不尝试实现完整 Java Import → Maven Artifact 推断器 |
| `build-first.sh` | 统一执行静态检查和完整 Maven Reactor | 不跳过失败，不替代真实 Redis/MQ/数据库 E2E |

推荐从仓库根目录执行：

```bash
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
bash scripts/build-first.sh
```

`build-first.sh` 已包含前两个脚本和 Retry 的组件级静态规则，日常完整验证只需要执行它。

## 组件级脚本

### Foundation

- `component/foundation-component/scripts/validate-poms.py`：只快速检查 Foundation 内 POM XML。
- `component/foundation-component/scripts/build-first.sh`：在 Foundation 目录独立验证时使用。

它们保留是因为 Foundation 可以单独开发，但不再承担全仓 Maven 架构校验。

### Retry

- `verify-package-layout.py`：保护 Retry API 包结构、模块边界和源码洁净度。
- `verify-comment-style.py`：保护 Retry 已确立的注释规范。

它们是 Retry 的特定架构测试，不应泛化为全仓 Java 风格检查；根 `build-first.sh` 会执行它们。

## 结果解释

- 脚本错误：先修复仓库中可静态确认的问题。
- 脚本通过、Maven 失败：继续看依赖解析、编译、插件、测试或环境日志。
- Maven 通过、E2E 失败：继续看真实基础设施、配置、网络和事务语义。

不要为了让脚本变绿而放宽已确认的架构边界，也不要把脚本通过描述成“组件已经完成生产验证”。
