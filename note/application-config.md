# application-config 配置笔记

> 这份笔记专门解释 `src/main/resources/application.yml` 里当前真正生效的配置项，方便你对照代码理解。

## 1. 当前配置总览

`src/main/resources/application.yml` 现在主要分成四块：

- `spring.*`：Spring AI、DeepSeek、Elasticsearch 相关配置
- `app.security.*`：接口鉴权相关配置
- `app.jayagent.*`：规则库和评分配置
- `app.review-history.*`：历史存储配置
- `management.*`：Actuator 健康检查和 Prometheus 指标端点

## 2. Spring AI 相关配置

### `spring.ai.model.chat`

当前主聊天模型走 `openai` 兼容接口，但实际请求的是 DeepSeek。

### `spring.ai.openai.api-key`

- 读取环境变量：`DEEPSEEK_API_KEY`
- 用途：访问 DeepSeek API

### `spring.ai.openai.base-url`

- 当前值：`https://api.deepseek.com`
- 用途：把 OpenAI 风格接口指向 DeepSeek

### `spring.ai.openai.chat.model`

- 当前值：`deepseek-v4-flash`
- 用途：指定聊天模型

### `spring.ai.openai.embedding.model`

- 当前值：`text-embedding-3-small`
- 用途：向量化/embedding 模型

## 3. Elasticsearch 相关配置

### `spring.elasticsearch.uris`

- 默认值：`http://localhost:9200`
- 可用环境变量覆盖：`SPRING_ELASTICSEARCH_URIS`

这个配置决定应用连接哪个 Elasticsearch 实例。

### `spring.ai.vectorstore.elasticsearch.initialize-schema`

- 当前值：`true`
- 用途：启动时自动准备向量库 schema

### `spring.ai.vectorstore.elasticsearch.index-name`

- 当前值：`spring_ai_vector_index`
- 用途：向量索引名称

## 4. 安全配置

### `app.security.api-key-enabled`

- 当前值：`true`
- 用途：是否启用 API Key 校验

### `app.security.api-key`

- 读取环境变量：`JAYAGENT_API_KEY`
- 用途：接口访问令牌

### `app.security.expose-raw-analysis`

- 当前值：`false`
- 用途：是否暴露原始分析结果

### `app.security.persist-raw-analysis`

- 当前值：`false`
- 用途：是否持久化原始分析结果

## 5. JayAgent 业务配置

### `app.jayagent.knowledge-base.auto-init`

- 当前值：`true`
- 用途：启动时自动初始化知识库

### `app.jayagent.knowledge-base.source-path`

- 当前值：`classpath:knowledge/java_security_rules.txt`
- 用途：规则文本来源。无 scheme 的相对路径按 classpath 处理；也可使用 `classpath:` 或 `file:` 指向外部规则文件。

### `app.jayagent.knowledge-base.state-file`

- 当前值：`data/knowledge-base-state.txt`
- 用途：知识库状态文件
- 这是外部可写文件，默认相对应用工作目录；JAR/Docker 不应配置为 `src/main/resources` 下的路径。

### `app.jayagent.knowledge-base.min-score`

- 当前值：`0.3`
- 用途：规则检索或匹配的最低分值阈值

### `app.jayagent.scoring.*`

这组配置控制不同风险等级的分值惩罚：

- `critical-penalty: 25`
- `high-penalty: 10`
- `medium-penalty: 5`
- `low-penalty: 1`

## 6. Webhook 配置

### `app.jayagent.webhook.dedup-store-type`

- 当前值：`file`
- `file` 适合单实例；`sqlite` 使用唯一键实现跨 JVM 的原子去重。

### `app.jayagent.webhook.dedup-store`

- 当前值：`data/webhook-events.log`
- `file` 模式下为去重日志，`sqlite` 模式下为数据库文件。
- 多实例必须共享同一个 SQLite 文件；各容器独立本地文件不具备分布式幂等能力。

### `app.jayagent.webhook.shared-secret`

- 读取环境变量：`JAYAGENT_WEBHOOK_SHARED_SECRET`
- 用途：Webhook 通用共享密钥

### `app.jayagent.webhook.github-secret`

- 读取环境变量：`JAYAGENT_GITHUB_WEBHOOK_SECRET`
- 用途：GitHub Webhook 校验

### `app.jayagent.webhook.gitlab-token`

- 读取环境变量：`JAYAGENT_GITLAB_WEBHOOK_TOKEN`
- 用途：GitLab Webhook 校验

### `app.jayagent.webhook.strict-mode`

- 当前值：`true`
- 用途：更严格地检查 Webhook 请求

### `app.jayagent.webhook.max-body-length`

- 当前值：`100000`
- 用途：限制请求体大小

### `app.jayagent.webhook.require-event-id`

- 当前值：`true`
- 用途：要求事件 ID，用于去重

### `app.jayagent.webhook.allow-shared-secret-fallback`

- 当前值：`true`
- 用途：允许共享密钥兜底验证

### `app.webhook-review-task.database-file`

- 当前值：`data/webhook-review-task.db`
- 用途：Webhook 异步任务表数据库文件

### `app.webhook-review-task.poll-interval-ms`

- 当前默认值：`5000`
- 用途：后台轮询任务表的间隔

## 7. 外部 API 配置

### `app.jayagent.external-api.*`

- `connect-timeout-millis: 3000`：建立连接的超时时间。
- `read-timeout-millis: 10000`：读取响应的超时时间。
- `max-retries: 2`：外部调用最大重试次数。
- `retry-backoff-millis: 300`：重试之间的退避等待时间。

这些配置由 `JayAgentProperties.ExternalApi` 绑定，并由 GitHub、GitLab 和企业微信客户端使用。

## 8. 历史存储配置

### `app.review-history.database-file`

- 当前值：`data/review-history.db`
- 用途：审查历史数据库文件

### `app.review-history.legacy-file`

- 当前值：`data/review-history.jsonl`
- 用途：旧格式兼容文件，由 `ReviewHistoryMigrator` 在启动时导入 SQLite

## 9. 可观测性配置

### `management.endpoints.web.exposure.include`

- 当前值：`health,info,prometheus`
- 用途：暴露健康检查、应用信息和 Prometheus 指标。

### `management.endpoint.health.probes.enabled`

- 当前值：`true`
- 用途：启用适合容器编排平台使用的存活和就绪探针。

### `X-Request-Id`

这不是 YAML 配置项。`CorrelationIdFilter` 会为请求生成或透传关联 ID，并把它写入响应头和日志 MDC。Webhook 后台任务会继续使用同一个 ID。

## 10. 学习时要记住的映射关系

- `DEEPSEEK_API_KEY` -> DeepSeek 访问凭证
- `SPRING_ELASTICSEARCH_URIS` -> ES 地址
- `JAYAGENT_API_KEY` -> 接口访问控制
- `JAYAGENT_WEBHOOK_SHARED_SECRET` -> 通用 Webhook 验证
- `JAYAGENT_GITHUB_WEBHOOK_SECRET` -> GitHub Webhook 验证
- `JAYAGENT_GITLAB_WEBHOOK_TOKEN` -> GitLab Webhook 验证

## 11. 最重要的结论

这份配置说明现在的重点不是“有没有很多参数”，而是：

- 主模型走 DeepSeek
- 向量库走 Elasticsearch
- 安全和 Webhook 通过 `app.*` 分层管理
- 历史存储已经单独抽出来
- 运行时观测通过 Actuator、Prometheus 和 JSON 日志提供
