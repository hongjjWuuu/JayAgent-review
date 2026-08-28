# Webhook 接口契约

## `POST /api/webhook/github`

- 用途：接收 GitHub Pull Request Webhook
- 认证：`X-Hub-Signature-256`，可按配置回退到 `X-JayAgent-Webhook-Token`
- 事件 ID：`X-GitHub-Delivery`，可回退 `X-Request-Id`
- 响应：`202 Accepted`

### 关键字段

- `repository.full_name`
- `pull_request.number`
- `pull_request.title`
- `pull_request.head.ref`
- `pull_request.head.sha`
- `pull_request.html_url`

## `POST /api/webhook/gitlab`

- 用途：接收 GitLab Merge Request Webhook
- 认证：`X-Gitlab-Token`，可按配置回退到 `X-JayAgent-Webhook-Token`
- 事件 ID：`X-Gitlab-Event-UUID`，可回退 `X-Request-Id`
- 响应：`202 Accepted`

### 关键字段

- `project.path_with_namespace`
- `object_attributes.iid`
- `object_attributes.source_branch`
- `object_attributes.last_commit.id`
- `object_attributes.url`

## 通用约定

- 请求体必须是 `application/json`
- 超过 `app.jayagent.webhook.max-body-length` 会返回 `413`
- 鉴权失败返回 `401`
- 重复事件返回 `202`，但会标记为 duplicate
- 后台处理会继承 `X-Request-Id`
