# JayAgent-review 学习手册

> 本手册按项目当前最新代码说明架构、类职责、功能实现、配置和测试，不以优化周次为主线。建议结合源码逐节阅读。

## 1. 项目定位

`JayAgent-review` 是一个基于 Spring Boot、Spring AI、Elasticsearch 和 SQLite 的 AI 代码审查服务。它将请求接入、Diff 获取、规则检索、LLM 分析、结构化报告、历史保存、平台评论和企业微信告警连接成完整链路。

```text
手动扫描 / GitHub Webhook / GitLab Webhook
 -> 认证与请求校验
 -> 获取和标准化代码 Diff
 -> RAG 检索安全规则
 -> 构造 Prompt 并调用 ChatClient
 -> 解析报告和风险评分
 -> SQLite 历史记录
 -> 任务表承接 / 评论 / 告警 / 前端展示
```

## 2. 整体架构 [[项目结构速读版]] [[主要架构关系.canvas]]

```text
com.jayagent.jayagent_review
├── JayAgentApplication
├── controller/       HTTP 接口、Webhook、请求 DTO
├── service/          Webhook 编排、解析、Diff 组装和审查历史
├── agent/            AI 审查、Prompt、报告解析和结果模型
├── rag/              规则文件加载和向量库初始化
├── integration/      GitHub、GitLab、企业微信和重试支持
├── observability/    Micrometer 指标
└── config/           AI、属性、安全、关联 ID 和评分配置
```

项目是分层单体应用。Controller 负责入口，Service 负责业务编排，Agent 负责 AI 分析，RAG 负责知识库，Integration 负责外部系统，Config 负责运行策略。所有模块在一个 Spring Boot 进程内运行，但依赖方向已经按职责分开。

## 3. 启动和配置装配

### 3.1 `JayAgentApplication`

文件：`src/main/java/com/jayagent/jayagent_review/JayAgentApplication.java`。

这是 Spring Boot 启动类，负责启动 Web 服务、扫描组件和创建应用上下文。所有 Controller、Service、配置类、过滤器和初始化器都从这里装配。

### 3.2 `AiConfig`

文件：`config/AiConfig.java`。

负责 AI 相关 Bean。扫描器通过 `ChatClient.Builder` 获得 ChatClient，不直接编写模型 HTTP 调用，因此模型供应商由 Spring AI 和配置决定。

### 3.3 `JayAgentProperties`

文件：`config/JayAgentProperties.java`。

将 `application.yml` 映射为类型化 Java 配置，包含：

- `KnowledgeBase`：规则源文件、状态文件、自动初始化、最低相似度。
- `Scoring`：各风险等级的扣分值。
- `Webhook`：平台 Token、共享 Secret、严格模式、请求限制。
- `Dedup`：去重文件和 TTL。
- `ExternalApi`：连接超时、读取超时、重试和退避。

这样可以避免在业务类中散落环境变量读取、超时数字和安全开关。

### 3.4 `ApiKeyAuthFilter`

文件：`config/ApiKeyAuthFilter.java`。

拦截 `/api/**` 并校验 `X-JayAgent-Api-Key`，排除 `/api/webhook/**`。
非 Webhook API 的结果为：关闭鉴权时放行；服务端未配置 Key 时返回 `503`；请求缺失或错误时返回 `401`；正确 Key 才进入 Controller。

### 3.5 `RiskScoringConfig`

提供风险扣分配置。`JayAgentReport.getScore()` 根据 `CRITICAL`、`HIGH`、`MEDIUM`、`LOW` 累计扣分，空风险为 100，最低分不低于 0。

## 4. `agent` 包：AI 审查实现

### 4.1 `JayAgentScanner`

文件：`agent/JayAgentScanner.java`。

核心方法是：

```java
JayAgentReport scan(String codeDiff, String filePath, String reviewScope)
```

执行过程是：规范化输入，调用 `ReviewPromptBuilder` 生成检索词，查询 `VectorStore`，过滤规则上下文，构造系统 Prompt，调用 `ChatClient`，交给 `ReviewReportParser`，记录耗时并返回 `JayAgentReport`。

它现在主要承担流程编排，不再把 Prompt 规则和模型文本解析全部写在自身内部。向量库返回 `null` 时按空文档处理，避免外部实现异常造成空指针。

### 4.2 `ReviewPromptBuilder`

文件：`agent/ReviewPromptBuilder.java`。

负责三件事：根据路径和 Diff 特征构造搜索词；按最低相似度过滤文档并格式化知识上下文；构造系统 Prompt。代码出现 `password`、`secret`、`token` 时会补充敏感信息规则，出现 `SQL`、`jdbc` 时会补充注入规则。

Prompt 要求模型输出 `SUMMARY|`、`RISK|` 或 `PASS|`，并规定风险等级和字段顺序，让自然语言结果具备可解析契约。

### 4.3 `ReviewReportParser`

文件：`agent/ReviewReportParser.java`。

负责解析摘要、风险行和 PASS 结果，兼容新旧风险字段格式，规范化风险级别，限制置信度在 0 到 1，并生成默认摘要。模型输出格式变化时主要修改这个类，不需要修改扫描主流程。

### 4.4 `JayAgentReport`

文件：`agent/JayAgentReport.java`。

报告包含 `reviewScope`、`summary`、`risks`、`knowledgeContext`、`analysisLatencyMs` 和 `rawAnalysis`。内部 `RiskItem` 包含级别、分类、规则 ID、文件位置、标题、描述、建议、置信度和规则来源。前端、历史和评论都消费这些结构化字段。

### 4.5 `ScheduledScanner`

定时扫描入口，复用 `JayAgentScanner`，因此定时审查和手动审查共用相同的 RAG、Prompt 和报告解析逻辑。

## 5. `controller` 包：对外入口

### 5.1 `JayAgentController`

文件：`controller/JayAgentController.java`。

提供：

```text
POST /api/jayagent/scan
```

接收 `JayAgentScanRequest`，调用扫描器，返回成功标识、审查范围、分数、摘要和风险列表。根据 `app.security.expose-raw-analysis` 决定是否返回模型原文。

### 5.2 `JayAgentScanRequest`

手动扫描请求 DTO，封装 Diff、文件路径和审查范围，并通过 Bean Validation 校验输入。使用 DTO 比直接接收 Map 更容易维护和测试。

### 5.3 `WebhookController` [[Webhook]]

提供：

```text
POST /api/webhook/github
POST /api/webhook/gitlab
```

它负责接收请求并调用 `WebhookSecurityService` 完成安全校验、解析 JSON、执行事件去重，再把任务交给 `WebhookReviewOrchestrator`。当前任务会先写入 `WebhookReviewTaskRepository`，再由 `WebhookReviewTaskDispatcher` 异步执行。校验通过后立即返回 `202 Accepted`。`202` 表示已经受理，不代表模型已经分析完成。

GitHub 优先使用 `X-Hub-Signature-256` 和 `app.jayagent.webhook.github-secret`；
GitLab 使用 `X-Gitlab-Token` 和 `app.jayagent.webhook.gitlab-token`。平台凭证没有配置时，
在允许回退的情况下使用 `X-JayAgent-Webhook-Token` 与共享 Secret 比较。严格模式下如果
没有可用凭证会返回 `401`。事件 ID 缺失返回 `400`，重复事件返回 `202` 但标记为已忽略。

### 5.4 Webhook 去重

`WebhookEventDedupStore` 定义抽象，`FileWebhookEventDedupStore` 使用文件保存事件键和时间，并按 TTL 失效和清理。去重回答“事件是否已受理”，SQLite 历史回答“审查结果是什么”，两者不是同一类数据。

### 5.5 `WebhookSecurityService`

负责请求体长度、Content-Type、事件 ID、GitHub HMAC、GitLab Token、共享 Secret 回退和严格模式校验，避免安全逻辑继续堆积在 Controller 中。

### 5.5 其他 Controller

- `ReviewHistoryController`：历史列表、详情、统计和趋势。
- `SearchController`：知识库检索测试。
- `ChatController`：模型连通性测试。
- `RuleController`：规则刷新。
- `ApiExceptionHandler`：统一处理状态异常、参数校验异常和未预期异常，输出 `success`、`status`、`message`、`timestamp` 等字段。

## 6. `service` 包：业务编排和历史

### 6.1 `WebhookService`

后台接收平台上下文和 Diff，调用 `JayAgentScanner`，生成带平台、仓库、分支、commit、PR/MR、源 URL 和 Diff 哈希的 `ReviewContext`，然后保存历史、回写评论并发送企业微信告警。

控制器只负责快速受理，服务层负责业务完成，这是 Webhook 能返回 202 的原因。

### 6.2 `WebhookReviewOrchestrator`

负责把 Webhook 请求转交给任务承接层；当前后台执行由 `WebhookReviewTaskDispatcher` 负责，任务先落 SQLite 再轮询执行。

### 6.3 `ReviewHistoryService`

文件：`service/ReviewHistoryService.java`。

当前采用 SQLite，默认数据库为 `data/review-history.db`。`ReviewHistoryService` 现在只负责对外编排，持久化、映射、统计和迁移分别由 `ReviewHistoryRepository`、`ReviewHistoryMapper`、`ReviewHistoryStatisticsService` 和 `ReviewHistoryMigrator` 承担。内部对象仍保留 `ReviewQuery`、`ReviewRecord` 和 `ReviewContext` 供外部接口兼容。

新记录默认不保存 `rawAnalysis`，是否保存由 `app.security.persist-raw-analysis` 控制。

### 6.4 SQLite、JSONL、去重文件和日志

| 数据 | 位置或形式 | 作用 |
|---|---|---|
| 审查历史 | `data/review-history.db` | 业务记录、分页、统计 |
| 旧历史 | `data/review-history.jsonl` | SQLite 迁移来源 |
| Webhook 去重 | 配置的文件 | 幂等判断和 TTL |
| 运行日志 | 日志配置位置 | 启动、异常、重试、告警 |
| 规则源文件 | `knowledge/java_security_rules.txt` | RAG 原始知识 |

SQLite 和日志并不冲突：SQLite 保存可查询业务数据，日志记录按时间发生的运行事件。

## 7. `rag` 包：安全规则知识库

### 7.1 `DocumentLoader`

从 `knowledge/java_security_rules.txt` 读取规则文本并切分为 Spring AI `Document`，只负责文件读取和切分。

### 7.2 `KnowledgeBaseInitializer`

应用启动时根据源文件状态或哈希判断是否需要导入。规则没有变化时跳过重复写入，发生变化时重新写入向量库。初始化失败会记录警告但允许服务启动，后续审查可能缺少知识上下文。

## 8. `integration` 包：外部平台

### 8.1 `GitHubApiClient`

获取 PR Diff、仓库信息和评论。GitHub 上下文包括 `repository.full_name`、PR number、head SHA 和 PR URL。

### 8.2 `GitLabApiClient`

获取 MR Diff、项目信息和评论。GitLab 上下文包括项目路径、MR IID、最后 commit 和 MR URL。

### 8.3 `ExternalApiCallSupport`

统一外部调用的连接超时、读取超时、重试次数和退避间隔，供 GitHub、GitLab 和企业微信复用。

### 8.4 `ExternalApiException` 和 `WeChatNotifier`

`ExternalApiException` 统一表示外部依赖失败；`WeChatNotifier` 向企业微信发送高风险通知。通知失败会记录日志，但不回滚已经完成的审查结果。

## 9. `observability` 包：运行时观测

`ObservabilityMetrics` 使用 Micrometer 记录 Webhook 接收/拒绝/重复、审查结果、LLM 和 RAG 耗时、GitHub/GitLab/企业微信调用结果与耗时，并通过 `/actuator/prometheus` 暴露给 Prometheus。

`CorrelationIdFilter` 生成或透传 `X-Request-Id`，写入响应头和日志 MDC。`logback-spring.xml` 将日志输出为 JSON，后台 Webhook 任务沿用接收请求的关联 ID。

## 10. 主要功能的实际流程

### 9.1 手动扫描

`POST /api/jayagent/scan` 先经过 API Key 过滤器，再进入 Controller。请求 DTO 传给扫描器，扫描器检索规则、调用模型、解析风险并计算分数，控制器返回结构化报告并保存历史。

### 9.2 GitHub 和 GitLab 审查

```text
Webhook 请求
 -> WebhookSecurityService 安全校验
 -> JSON 解析和事件去重
 -> 返回 202
 -> WebhookReviewOrchestrator 转交任务
 -> WebhookReviewTaskRepository 持久化任务
 -> WebhookReviewTaskDispatcher 轮询并执行
 -> 平台 API 分页获取 Diff
 -> DiffAssembler 组装和截断
 -> WebhookService 调用 JayAgentScanner
 -> SQLite 历史、评论、企业微信
```

GitHub 使用 HMAC，GitLab 使用 Token 或共享 Secret。历史记录保留平台、仓库、分支、commit、PR/MR 和源 URL，便于从结果回溯真实代码版本。

### 9.3 RAG 和模型分析

规则启动时进入向量库；审查时按 Diff 特征检索相关规则；低于 `min-score` 的结果被过滤；剩余上下文加入系统 Prompt；模型按约定格式输出；解析器生成风险项；报告对象完成评分。

### 9.4 异步、重试和初始化保护

Webhook 不同步等待 LLM，而是返回 202 后后台执行。外部平台统一按配置超时和重试。去重记录按 TTL 过期。规则初始化失败只影响知识上下文，不阻断应用启动。

## 11. 配置说明（UTF-8 正常版）

配置文件：`src/main/resources/application.yml`。下面按作用说明，避免只罗列键名。

### 11.1 AI 和 Elasticsearch

- 模型配置决定 ChatClient 连接的 OpenAI 或 Ollama 服务、模型名称和生成参数。
- Elasticsearch 配置决定 VectorStore 的地址、认证信息和索引连接。
- `app.jayagent.knowledge-base.min-score` 决定相似度低于多少的规则不进入 Prompt。

### 11.2 知识库

- `app.jayagent.knowledge-base.source-path`：规则源文件，例如 `knowledge/java_security_rules.txt`。
- `app.jayagent.knowledge-base.state-file`：记录规则状态或哈希，用于跳过无变化初始化。
- `app.jayagent.knowledge-base.auto-init`：是否随应用启动自动加载规则。

### 11.3 安全

- `app.security.api-key-enabled`：是否启用非 Webhook API Key。
- `JAYAGENT_API_KEY`：客户端放在 `X-JayAgent-Api-Key` 请求头中的值。
- `app.security.expose-raw-analysis`：是否把模型原文放进 API 响应，默认 `false`。
- `app.security.persist-raw-analysis`：是否把模型原文写进新历史记录，默认 `false`。
- Webhook Secret、平台 Token 和严格模式控制 GitHub/GitLab 请求认证。

Webhook 的认证、请求体上限、事件 ID 要求、共享 Secret 回退和去重 TTL 都位于
`app.jayagent.webhook.*`；连接超时、读取超时、重试次数和退避间隔位于
`app.jayagent.external-api.*`。审查历史仍独立位于 `app.review-history.*`。

严格模式下平台 Secret 和共享 Secret 都没有配置时直接返回 `401`，不会因为凭证缺失而放行。

### 11.4 Webhook 和去重

请求大小限制防止超大 payload；Content-Type 限制防止非 JSON 请求；去重文件保存事件键和受理时间；TTL 决定事件记录多久后失效并清理。

### 11.5 外部 API

- 连接超时：建立网络连接的最长等待时间。
- 读取超时：连接后等待响应的最长时间。
- 最大重试次数：可重试错误的最大重复次数。
- 退避间隔：两次重试之间的等待时间。

这些参数由 `ExternalApiCallSupport` 使用。

### 11.6 可观测性

- `/actuator/health`：健康检查。
- `/actuator/info`：应用信息。
- `/actuator/prometheus`：Prometheus 指标。
- `X-Request-Id`：请求关联 ID，后台 Webhook 任务会继承。
- `logback-spring.xml`：JSON 日志输出配置。

### 11.7 Java 和 Maven

当前项目使用 Java 17，`pom.xml` 中的 `java.version` 和 `maven.compiler.release` 均为 `17`。依赖下载较慢时可使用项目镜像和本地仓库：

```powershell
mvn -s maven-settings-aliyun.xml -U `
  -Dmaven.repo.local=`${project.basedir}\target\maven-repository `
  -DskipTests compile
```

## 11. 测试和当前验收状态

当前测试覆盖：

- `JayAgentScannerTest`：摘要、风险行和解析器边界。
- `WebhookControllerAsyncTest`：202 受理和后台提交。
- `WebhookControllerSecurityTest`：签名、Token 和严格模式。
- `FileWebhookEventDedupStoreTest`：去重和 TTL。
- `ExternalApiCallSupportTest`：超时、重试和退避。
- `KnowledgeBaseInitializerTest`：初始化异常不阻断启动。
- `ApiKeyAuthFilterTest`：API Key 的缺失、错误和正确场景。
- `JayAgentApplicationTests`：Spring 容器和关键 Bean 装配。
- `DiffAssemblerTest`：Diff 截断和特殊文件。
- `ReviewReportParserJsonTest`：结构化 JSON 输出和旧协议回退。

验证命令：

```powershell
mvn -DskipTests compile
mvn test
```

最近一次完整测试结果为 `Tests run: 25, Failures: 0, Errors: 0`。容器测试会 Mock 模型和向量库，因此证明的是应用能够装配和启动，不等同于真实 Elasticsearch、GitHub、GitLab 或模型服务联通。

## 12. 当前代码的整体理解

学习这个项目不需要先背所有类名。抓住四条关系即可：

1. `JayAgentController -> JayAgentScanner -> JayAgentReport`：手动审查。
2. `ReviewPromptBuilder -> VectorStore -> ChatClient -> ReviewReportParser`：RAG 和模型分析。
3. `WebhookController -> WebhookSecurityService -> WebhookReviewOrchestrator -> WebhookReviewTaskDispatcher -> WebhookService`：平台接入和异步审查。
4. `ReviewHistoryService -> Repository/Mapper/Statistics/Migrator`，配合 `ApiKeyAuthFilter`、`ApiExceptionHandler` 和 `ObservabilityMetrics`：数据、安全、错误和运行观测边界。

当你能说明一次请求如何进入系统、如何检索规则、如何调用模型、如何生成风险、如何保存并返回时，就掌握了项目的核心架构。

## 13. 后续工程方向

当前主链路已经可运行，后续重点是继续完善 Webhook 任务可恢复性、将文件或 SQLite 去重演进到 Redis/集中式数据库、补充更完整的 Webhook 集成测试、OpenAPI、链路追踪和生产告警规则。
