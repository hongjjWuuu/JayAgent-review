# JayAgent-review

> 基于 Spring Boot、Spring AI、DeepSeek、Elasticsearch 和 SQLite 的 AI 代码审查服务。

## 项目简介

JayAgent-review 面向 GitHub / GitLab 代码变更场景，将代码 Diff 转换为结构化审查结果，并支持历史追踪和平台联动。

```text
手动扫描 / GitHub Webhook / GitLab Webhook
 -> 认证与请求校验
 -> 获取和标准化代码 Diff
 -> RAG 检索安全规则
 -> Prompt 构造与 LLM 分析
 -> 报告解析与风险评分
 -> SQLite 历史记录
 -> 评论回写 / 企业微信告警 / 前端展示
```

当前支持：

- 手动提交代码 Diff 审查
- GitHub Pull Request 和 GitLab Merge Request Webhook 审查
- 基于 Elasticsearch 的安全规则检索
- DeepSeek Chat 模型分析和结构化风险报告
- SQLite 审查历史、分页、筛选、详情、统计和趋势
- GitHub / GitLab 评论回写
- 企业微信高风险告警
- Webhook 签名、Token、共享 Secret 回退、事件去重和异步受理

## 技术栈

| 层面 | 选型 |
| :--- | :--- |
| 后端框架 | Spring Boot 3.5.7 |
| AI 框架 | Spring AI 1.1.8 |
| 聊天模型 | DeepSeek，使用 OpenAI 兼容接口 |
| 向量库 | Elasticsearch |
| 历史存储 | SQLite |
| JDK | 17 |
| 前端 | 原生 HTML / CSS / JavaScript |

## 目录结构

```text
src/main/java/com/jayagent/jayagent_review/
├── agent/         审查流程、Prompt、报告解析和结果模型
├── config/        AI、属性映射、安全和评分配置
├── controller/    HTTP 接口、Webhook、鉴权和异常处理
├── integration/   GitHub、GitLab、企业微信和外部调用重试
├── rag/           规则文件加载和向量库初始化
└── service/       Webhook 业务编排和审查历史
```

主要运行文件：

- `src/main/resources/application.yml`：运行配置
- `src/main/resources/knowledge/java_security_rules.txt`：安全规则源文件
- `src/main/resources/static/index.html`：前端测试页面
- `data/review-history.db`：SQLite 审查历史数据库
- `data/knowledge-base-state.txt`：知识库初始化状态文件

规则默认从 `classpath:knowledge/java_security_rules.txt` 读取，开发目录、JAR、Docker 均不依赖 `src/main/resources` 目录存在。`state-file`、Webhook 去重文件和历史数据库属于外部可写数据，应放在应用工作目录的 `data/` 或显式配置的外部路径。

## 启动

### 前置条件

- JDK 17
- Maven 3.9+
- 可访问的 Elasticsearch，默认地址为 `http://localhost:9200`
- DeepSeek API Key

### 环境变量

最小启动需要：

```powershell
$env:DEEPSEEK_API_KEY = "your-deepseek-api-key"
```

常用可选配置：

| 环境变量 | 用途 |
| :--- | :--- |
| `SPRING_ELASTICSEARCH_URIS` | 覆盖 Elasticsearch 地址 |
| `JAYAGENT_API_KEY` | 非 Webhook API 的访问 Key |
| `JAYAGENT_WEBHOOK_SHARED_SECRET` | Webhook 共享 Secret 回退 |
| `JAYAGENT_GITHUB_WEBHOOK_SECRET` | GitHub Webhook HMAC Secret |
| `JAYAGENT_GITLAB_WEBHOOK_TOKEN` | GitLab Webhook Token |

### 启动命令

```powershell
mvn spring-boot:run
```

启动后访问：

```text
http://localhost:8080
```

依赖下载较慢时，使用项目内置 Maven 镜像和本地仓库：

```powershell
mvn -s maven-settings-aliyun.xml -U `
  -Dmaven.repo.local=`${project.basedir}\target\maven-repository `
  -DskipTests compile
```

## 主要接口

除 Webhook 外的 `/api/**` 接口默认受 API Key 过滤器保护，客户端使用 `X-JayAgent-Api-Key` 请求头。

| 方法 | 路径 | 作用 | 成功状态 |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/jayagent/scan` | 手动代码审查 | `200` |
| `GET` | `/api/jayagent/history` | 历史分页和筛选 | `200` |
| `GET` | `/api/jayagent/history/{id}` | 历史详情 | `200` |
| `GET` | `/api/jayagent/stats` | 历史统计和趋势 | `200` |
| `POST` | `/api/webhook/github` | GitHub PR Webhook | `202` |
| `POST` | `/api/webhook/gitlab` | GitLab MR Webhook | `202` |
| `GET` | `/api/search/test` | 知识库检索测试 | `200` |
| `GET` | `/api/chat/test` | 模型连通性测试 | `200` |
| `POST` | `/api/rules/refresh` | 规则库刷新 | `200` |

Webhook 通过请求限制、平台认证和事件去重后立即返回 `202 Accepted`，完整审查在后台执行。GitHub 使用 `X-Hub-Signature-256`，GitLab 使用 `X-Gitlab-Token`；平台凭证缺失时，可按配置回退到 `X-JayAgent-Webhook-Token` 共享 Secret。

默认 Webhook 策略：严格模式开启、要求事件 ID、请求体上限 100000 字符、去重 TTL 30 天。

默认去重实现是单机文件模式：

```yaml
app:
  jayagent:
    webhook:
      dedup-store-type: file
      dedup-store: data/webhook-events.log
```

多实例部署可使用共享 SQLite 文件：

```yaml
app:
  jayagent:
    webhook:
      dedup-store-type: sqlite
      dedup-store: data/webhook-dedup.db
```

 SQLite 通过唯一键保证并发事件只被一个实例受理；各容器不共享文件时不能提供多实例去重保证。

Docker Compose 会将宿主机项目目录下的 `data/` 挂载到应用容器的 `/app/data`。审查历史、Webhook 任务、去重记录和知识库状态会写入宿主机目录，容器重建后仍可保留。

## 配置层级

当前配置以 `src/main/resources/application.yml` 为准：

- `spring.*`：Spring AI、DeepSeek 和 Elasticsearch
- `app.security.*`：非 Webhook API Key、原始分析结果暴露和持久化
- `app.jayagent.*`：知识库、评分、Webhook 和外部 API
- `app.review-history.*`：SQLite 数据库和旧 JSONL 迁移文件

详细配置说明见 [`note/application-config.md`](note/application-config.md)。

## 测试

运行完整测试：

```powershell
mvn -s maven-settings-aliyun.xml -U `
  -Dmaven.repo.local=`${project.basedir}\target\maven-repository test
```

测试覆盖审查报告解析、扫描流程、API Key 鉴权、Webhook 异步受理与安全校验、文件去重和 TTL、外部 API 重试、知识库初始化以及 Spring 容器装配。

## 文档入口

- [`note/启动手册.md`](note/启动手册.md)：本地启动和排障
- [`note/application-config.md`](note/application-config.md)：配置项说明
- [`note/JayAgent-review学习手册.md`](note/JayAgent-review学习手册.md)：完整架构和功能学习手册
- [`note/项目结构速读版.md`](note/项目结构速读版.md)：包、类职责和源码阅读路线
- [`note/审查历史存储方案说明.md`](note/审查历史存储方案说明.md)：SQLite 和历史迁移说明
- [`note/可观测性与部署说明.md`](note/可观测性与部署说明.md)：关联 ID、Prometheus 指标和部署边界
- [`note/webhook-openapi.md`](note/webhook-openapi.md)：Webhook 接口契约

## 当前状态和后续方向

截至 2026 年 9 月 10 日，项目已经完成以下主要能力：

- Webhook 鉴权、平台解析、Diff 组装和异步任务派发已经拆分到独立组件。
- Webhook 任务已经写入 SQLite，支持重试次数持久化、失败终止和重启后恢复。
- 审查历史已经拆分为 Repository、Mapper、统计服务和迁移器。
- 已接入请求关联 ID、结构化 JSON 日志、Actuator 和 Prometheus 指标。
- Docker Compose 已支持认证变量传递、`data/` 目录挂载、服务健康检查和 Elasticsearch 就绪依赖。
- 已补充 Webhook OpenAPI 说明、任务重试测试以及本地配置和部署文档。

当前仍需继续推进的事项：

- 使用 Docker Desktop 完成真实的启动、停止、重建和数据恢复验证。
- 增加更完整的 GitHub/GitLab 真实平台联调测试。
- 将文件或 SQLite 去重演进为适合跨主机多实例的 Redis 或集中式数据库。
- 为进程内异步任务继续评估消息队列或外部任务系统。
- 补充 OpenAPI 自动生成、链路追踪和生产级告警规则。
