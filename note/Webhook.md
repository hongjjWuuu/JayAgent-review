Webhook 可以理解为：

> 一个系统发生指定事件后，主动向另一个系统发送 HTTP 请求，通知对方“事情发生了”。

它是一种“事件通知机制”。

## 一个简单例子

没有 Webhook 时，JayAgent-review 可能需要不断询问 GitHub：

```
JayAgent-review：有新的 PR 吗？
GitHub：没有。
JayAgent-review：现在有吗？
GitHub：有了。
```

这叫轮询。

使用 Webhook 后：

```
GitHub：刚刚创建了一个新的 PR，我主动通知你。
JayAgent-review：收到，我开始审查。
```

因此 Webhook 的核心方向是：

```
事件发生方 -> 主动通知接收方
```

## 在本项目中的含义

项目中提供两个 Webhook 接口：

```
POST /api/webhook/github
POST /api/webhook/gitlab
```

当 GitHub 或 GitLab 发生代码变更事件时，它们会主动请求这些接口：

```
GitHub/GitLab
    -> WebhookController
    -> 事件去重
    -> 写入任务表
    -> 返回 202
    -> WebhookReviewTaskDispatcher 后台执行
    -> 获取 PR/MR 的代码 Diff
    -> 执行 AI 审查
    -> 保存审查历史
    -> 回写评论
    -> 发送企业微信告警
```

例如 GitHub 中有人提交了一个 Pull Request：

```
开发者提交 PR
    -> GitHub 触发 Webhook
    -> JayAgent-review 收到通知
    -> 自动拉取 PR Diff
    -> AI 检查安全问题
    -> 把审查结果评论到 PR
```

## Webhook 和普通 API 的区别

普通 API 通常是客户端主动请求服务端：

```
客户端 -> 服务端
```

例如你调用：

```
POST /api/jayagent/scan
```

是你主动把代码 Diff 发送给 JayAgent-review。

Webhook 则是事件发生后，平台主动请求你的服务：

```
GitHub/GitLab -> JayAgent-review
```

|对比|普通 API|Webhook|
|---|---|---|
|谁发起请求|客户端主动发起|事件发生方主动通知|
|触发方式|用户或程序调用|事件自动触发|
|是否需要轮询|通常不需要|不需要|
|典型用途|查询、提交、操作|事件通知、自动触发流程|

## 为什么叫 Webhook

可以把它拆成：

- `Web`：通过 HTTP/Web 网络通信
- `Hook`：钩子、触发点

意思就是：

> 在某个事件上挂一个网络钩子，事件发生时调用指定地址。

## Webhook 请求里通常有什么

GitHub 或 GitLab 发送的请求通常包含：

```
{
  "repository": {
    "full_name": "owner/project"
  },
  "pull_request": {
    "number": 123,
    "title": "Fix security issue"
  }
}
```

还会带一些请求头：

```
X-GitHub-Delivery
X-Hub-Signature-256
```

或者 GitLab 的：

```
X-Gitlab-Event-UUID
X-Gitlab-Token
```

JayAgent-review 根据这些信息判断：

- 来自哪个平台
- 哪个仓库
- 哪个 PR/MR
- 哪个事件
- 请求是否可信
- 是否已经处理过

## 为什么需要验证 Webhook

Webhook 接口通常暴露在网络上，任何人理论上都可能请求它。

如果不验证，攻击者可能伪造请求：

```
伪造 GitHub 请求
    -> 触发代码拉取
    -> 消耗模型调用
    -> 产生错误评论
    -> 发送错误告警
```

所以项目会验证：

- GitHub 的 HMAC 签名
- GitLab 的 Token
- 共享 Secret
- 事件 ID
- 请求体大小
- Content-Type
- 重复事件

## 为什么返回 `202 Accepted`

Webhook 不会同步等待完整 AI 审查，而是：

```
收到请求
    -> 验证
    -> 去重
    -> 加入后台任务
    -> 返回 202
```

`202 Accepted` 的意思是：

> 请求已经被接受，后续任务会异步执行。

它不代表审查已经完成。

## Webhook 和消息队列的区别

Webhook 是：

```
一个系统通过 HTTP 调用另一个系统
```

消息队列是：

```
一个系统把消息放入队列
另一个系统从队列中消费消息
```

当前项目中的 Webhook 使用：

```
SQLite 任务表 + 后台轮询派发
```

把任务先持久化再执行，因此它是：

```
HTTP Webhook + 持久化任务承接
```

它还不是 RabbitMQ、Kafka 这类独立消息队列，但已经具备任务可追踪、可恢复和可重放的雏形。

## 用一句话记住

Webhook 就是：

> GitHub 或 GitLab 发生事件后，主动通过 HTTP 请求通知 JayAgent-review，让它自动开始代码审查。

项目的“自动审查入口”：GitHub 或 GitLab 发生 PR/MR 事件后，平台主动请求 JayAgent-review，系统自动拉取代码 Diff、执行审查，再把结果评论回平台。

对应代码主要是：

- [WebhookController.java](/F:/JayAgent/JayAgent-review/src/main/java/com/jayagent/jayagent_review/controller/WebhookController.java)
- [WebhookService.java](/F:/JayAgent/JayAgent-review/src/main/java/com/jayagent/jayagent_review/service/WebhookService.java)
- [WebhookEventDedupStore.java](/F:/JayAgent/JayAgent-review/src/main/java/com/jayagent/jayagent_review/controller/WebhookEventDedupStore.java)
- [FileWebhookEventDedupStore.java](/F:/JayAgent/JayAgent-review/src/main/java/com/jayagent/jayagent_review/controller/FileWebhookEventDedupStore.java)

## 1. Webhook 和手动接口的区别

手动审查是：

```
用户主动发送 Diff
 -> /api/jayagent/scan
 -> 立即执行审查
 -> 返回审查结果
```

Webhook 审查是：

```
GitHub/GitLab 发生代码变更
 -> 平台主动请求 /api/webhook/github 或 /api/webhook/gitlab
 -> JayAgent-review 验证请求
 -> 快速返回 202
 -> 后台拉取 Diff
 -> 后台执行审查
 -> 保存历史
 -> 回写评论
 -> 发送告警
```

Webhook 的特点是：调用方不需要等待完整的 AI 审查结束。

## 2. 两个 Webhook 接口

```
POST /api/webhook/github
POST /api/webhook/gitlab
```

GitHub 通常对应 Pull Request，GitLab 通常对应 Merge Request。

请求体是平台发送的 JSON，内容包括：

```
仓库信息
PR/MR 信息
分支
commit
事件 ID
源代码变更信息
```

不过当前实现不是直接相信请求体里的 Diff，而是先从 payload 中提取仓库和 PR/MR 标识，再调用：

```
GitHubApiClient.getPrFiles(...)
GitLabApiClient.getMrChanges(...)
```

重新从平台 API 获取代码变更。

这样做的好处是：

- 变更内容来源更统一
- 可以获得平台 API 返回的完整文件列表
- 不依赖 Webhook payload 是否包含完整 Diff
- 便于后续评论回写

## 3. Webhook 请求进入 Controller 后发生什么

以 GitHub 为例：

```
@PostMapping("/github")
public ResponseEntity<Map<String, Object>> handleGitHubPR(
        @RequestBody String rawBody,
        HttpServletRequest request)
```

这里没有直接把请求体映射成 DTO，而是先接收原始字符串：

```
@RequestBody String rawBody
```

这是有原因的：GitHub HMAC 签名是根据“原始请求体”计算的。

如果先把 JSON 转成 Java 对象，再重新序列化，可能发生：

- 空格变化
- 字段顺序变化
- 转义方式变化
- 数字或字符串格式变化

重新序列化后的内容可能和 GitHub 计算签名时的内容不同，导致合法请求验证失败。

所以 Webhook 的处理顺序是：

```
原始请求体
 -> 请求大小检查
 -> Content-Type 检查
 -> JSON 解析
 -> 签名/Token 验证
 -> 事件去重
 -> 提交后台任务
```

## 4. 请求体大小和 Content-Type 校验

代码首先调用：

```
enforceRequestConstraints(rawBody, request);
```

它检查两个条件。

### 请求体大小

配置是：

```
app:
  jayagent:
    webhook:
      max-body-length: 100000
```

如果请求体超过限制，就返回：

```
413 Payload Too Large
```

主要作用是防止：

- 超大请求占用内存
- 恶意请求拖慢服务
- 无限制内容进入 JSON 解析和后台任务

### Content-Type

当前要求请求类型包含：

```
application/json
```

如果发送其他类型，例如：

```
text/plain
application/x-www-form-urlencoded
```

就返回：

```
415 Unsupported Media Type
```

## 5. GitHub 的 HMAC 验证

GitHub 使用：

```
X-Hub-Signature-256
```

请求头一般类似：

```
X-Hub-Signature-256: sha256=abc123...
```

服务端使用配置的 Secret 对原始请求体计算 HMAC-SHA256：

```
HMAC_SHA256(rawBody, githubSecret)
```

然后和请求头中的签名比较。

配置来源是：

```
app:
  jayagent:
    webhook:
      github-secret: ${JAYAGENT_GITHUB_WEBHOOK_SECRET:}
```

也就是环境变量：

```
JAYAGENT_GITHUB_WEBHOOK_SECRET
```

验证流程可以表示为：

```
GitHub 发送 rawBody + 签名
        |
        v
服务端用相同 Secret 重新计算签名
        |
        v
服务端签名 == 请求签名？
        |
   是 -> 继续处理
   否 -> 401
```

代码还使用了常量时间比较：

```
MessageDigest.isEqual(...)
```

相比普通字符串比较，这种方式更适合比较敏感的签名值，可以减少因比较时间差异造成的信息泄露风险。

## 6. GitLab 的 Token 验证

GitLab 使用：

```
X-Gitlab-Token
```

服务端读取：

```
app:
  jayagent:
    webhook:
      gitlab-token: ${JAYAGENT_GITLAB_WEBHOOK_TOKEN:}
```

对应环境变量：

```
JAYAGENT_GITLAB_WEBHOOK_TOKEN
```

请求头中的 Token 必须和服务端配置一致：

```
X-Gitlab-Token == configuredGitlabToken
```

不一致时返回：

```
401 Unauthorized
```

GitHub 和 GitLab 的认证方式不同：

|平台|请求头|验证方式|
|---|---|---|
|GitHub|`X-Hub-Signature-256`|HMAC-SHA256|
|GitLab|`X-Gitlab-Token`|Token 字符串比较|

## 7. 共享 Secret 回退机制

除了平台专用凭证，还支持共享 Secret：

```
app:
  jayagent:
    webhook:
      shared-secret: ${JAYAGENT_WEBHOOK_SHARED_SECRET:}
```

客户端通过：

```
X-JayAgent-Webhook-Token
```

发送共享 Token。

当前逻辑是：

```
平台专用凭证已配置？
  是 -> 使用平台专用验证
  否 -> 检查是否允许共享 Secret 回退
       是 -> 使用共享 Secret
       否 -> 401
```

相关配置：

```
strict-mode: true
allow-shared-secret-fallback: true
```

需要注意，`strict-mode: true` 并不表示一定必须配置 GitHub Secret 或 GitLab Token。当前组合的实际含义是：

```
必须有一种有效认证方式
平台 Secret 或 GitLab Token 或共享 Secret
```

如果严格模式开启，并且所有凭证都没有配置，就会返回：

```
401 Webhook authentication is not configured
```

这比“没有配置密码也允许请求通过”更安全。

## 8. 为什么 Webhook 不使用普通 API Key

普通接口使用：

```
X-JayAgent-Api-Key
```

但 Webhook 被 `ApiKeyAuthFilter` 排除：

```
/api/webhook/**
```

原因是 GitHub 和 GitLab 不会按照项目约定发送：

```
X-JayAgent-Api-Key
```

它们有自己的标准认证机制：

```
GitHub HMAC
GitLab Token
```

因此项目把两类接口分开：

```
手动接口、历史接口、调试接口
 -> ApiKeyAuthFilter

GitHub/GitLab Webhook
 -> 平台签名、Token、共享 Secret
```

这是一种按调用方类型区分安全机制的设计。

## 9. 事件 ID 和去重

Webhook 可能重复到达。

例如：

```
GitHub 发送一次事件
 -> 网络超时
 -> GitHub 不确定服务端是否收到
 -> GitHub 重试同一个事件
```

如果系统没有去重，就可能发生：

```
同一个 PR
 -> 拉取两次 Diff
 -> 调用两次 LLM
 -> 保存两条历史
 -> 回写两条评论
 -> 发送两次告警
```

所以 Controller 会读取平台事件 ID。

GitHub：

```
X-GitHub-Delivery
```

GitLab：

```
X-Gitlab-Event-UUID
```

如果平台事件 ID 不存在，还会尝试：

```
X-Request-Id
```

配置要求：

```
require-event-id: true
```

当事件 ID 完全缺失时，返回：

```
400 Bad Request
```

## 10. `WebhookEventDedupStore` 是什么

它是一个接口：

```
public interface WebhookEventDedupStore {
    boolean markProcessed(String eventKey);
}
```

它表达的不是具体存储方式，而是一个业务能力：

> 尝试记录这个事件；如果事件之前已经记录过，就告诉调用方它是重复事件。

接口的设计大致是：

```
markProcessed(eventKey)
        |
        +-- true  ：第一次见到，可以继续处理
        |
        +-- false ：之前处理过，应该忽略
```

Controller 中的逻辑是：

```
if (!markEventProcessed("github", eventKey, rawBody)) {
    return ResponseEntity.accepted()
            .body(acceptedResponse("github", true));
}
```

也就是说，重复 Webhook 不会返回错误，而是返回：

```
{
  "success": true,
  "platform": "github",
  "accepted": true,
  "duplicate": true,
  "message": "duplicate webhook ignored"
}
```

为什么重复请求仍然返回 `202`？

因为从调用方角度看：

```
请求格式正确
认证通过
事件已经被系统处理过
```

这不是服务器故障，所以返回成功语义更合适。

## 11. `FileWebhookEventDedupStore` 为什么使用文件

当前实现使用文件保存去重记录，适合单机部署和学习项目。

它保存的内容大致可以理解为：

```
事件键 -> 受理时间
```

例如：

```
github:delivery-123 -> 2026-08-21T10:00:00
gitlab:event-456    -> 2026-08-21T10:02:00
```

每次收到事件时：

```
读取去重文件
 -> 清理过期记录
 -> 检查当前 eventKey
 -> 如果存在，返回 false
 -> 如果不存在，写入当前时间并返回 true
```

配置中的：

```
app:
  jayagent:
    webhook:
      dedup:
        ttl-days: 30
```

表示去重记录保留 30 天。

为什么不能永久保存？

因为去重文件会不断增长，而且同一个事件在很久以后再次出现时，通常已经没有必要继续视为重复。

为什么没有直接使用 SQLite 保存去重？

当前项目把两类数据分开：

```
WebhookEventDedupStore
 -> 事件是否已经受理

ReviewHistoryService / SQLite
 -> 审查结果和业务历史
```

两者回答的问题不同：

|存储|回答的问题|
|---|---|
|去重存储|这个事件是否已经受理过？|
|SQLite 历史|这次审查产生了什么结果？|

这样做可以避免把“幂等控制”和“业务历史查询”混在一起。

## 12. `202 Accepted` 具体代表什么

Webhook 认证和去重通过后，Controller 调用：

```
submitGitHubReview(payload);
```

这个方法内部先把任务写入 SQLite 任务表，再由后台轮询派发执行。

然后 Controller 立即返回：

```
ResponseEntity.accepted()
```

所以：

```
202 Accepted
```

只代表：

> 服务已经接受这个 Webhook，并安排了后台处理。

它不代表：

- LLM 已经返回结果
- 审查已经完成
- 评论已经成功写回
- 企业微信告警已经发送成功

完整流程仍在后台执行：

```
202 返回
   |
   v
WebhookReviewTaskRepository / Dispatcher
   |
   v
GitHubApiClient / GitLabApiClient 获取 Diff
   |
   v
WebhookService.review(...)
   |
   v
JayAgentScanner
   |
   v
ReviewHistoryService 保存历史
   |
   +-> GitHub/GitLab 评论
   |
   +-> 企业微信告警
```

这和手动扫描接口不同。手动接口通常会等待扫描完成后返回结果；Webhook 接口优先保证快速响应平台。

## 13. 后台任务具体做什么

以 GitHub 为例，后台任务会：

1. 从 payload 读取仓库名称。
2. 拆分 owner 和 repository。
3. 获取 PR 编号、标题、分支和 commit SHA。
4. 调用 GitHub API 获取 PR 文件和 Diff。
5. 组装 `ReviewContext`。
6. 调用 `WebhookService.review(...)`。
7. 获取结构化 `JayAgentReport`。
8. 生成平台评论文本。
9. 尝试把评论写回 GitHub。

GitLab 流程类似：

```
projectId
 -> MR IID
 -> GitLab API 获取变更
 -> ReviewContext
 -> WebhookService.review
 -> GitLab 评论
```

评论回写失败时，当前代码会记录日志，但不会让已经完成的审查结果消失。

例如：

```
try {
    gitHubApiClient.postComment(...);
} catch (ExternalApiException ex) {
    log.warn(...);
}
```

这体现了一个重要的失败隔离原则：

```
评论失败
 !=
审查失败
```

审查结果仍然可以保存在 SQLite 中。

## 14. 当前实现中的一个重要边界

截图文字中提到“事件类型校验”，但从当前 `WebhookController` 的实际代码看，明确实现的是：

- 请求体大小校验
- Content-Type 校验
- JSON 解析
- GitHub 签名验证
- GitLab Token 验证
- 共享 Secret 回退
- 事件 ID 要求
- 去重
- 异步处理

当前代码没有看到一个独立的、严格的事件类型白名单校验，例如：

```
只接受 pull_request opened
只接受 pull_request synchronize
只接受 merge_request open
```

因此学习文档中的“事件类型”更准确地说应该改成：

```
Webhook payload 解析和事件 ID 校验
```

这不会影响当前主流程理解，但从文档准确性来说值得修正。

## 15. 这套设计为什么适合 Webhook

Webhook 有几个天然特点：

### 调用方会重试

因此必须有幂等处理：

```
事件 ID + 去重存储
```

### 调用方希望快速收到响应

因此不能同步等待 LLM：

```
先返回 202
后台执行审查
```

### 请求来自外部系统

因此不能只依赖普通登录态：

```
HMAC / Token / Secret
```

### 外部 API 可能失败

因此需要把：

```
拉取 Diff
评论回写
告警发送
```

和核心审查结果适当隔离。

这就是当前 Webhook 结构的核心考虑。

## 16. 一句话总结

这部分可以记成：

```
WebhookController
 = 外部事件入口
 + 请求安全验证
 + 事件去重
 + 快速受理
```

而不是：

```
WebhookController
 = 完成全部审查工作
```

真正的完整审查由后台流程完成：

```
WebhookController
 -> WebhookService
 -> JayAgentScanner
 -> ReviewHistoryService
 -> 平台评论 / 企业微信告警
```

`WebhookEventDedupStore` 则专门解决：

```
同一个外部事件重复到达时，不要重复执行昂贵的审查任务
```

所以这部分的设计重点是：

```
先验证来源
 -> 再判断是否重复
 -> 快速返回
 -> 后台完成复杂工作
```
