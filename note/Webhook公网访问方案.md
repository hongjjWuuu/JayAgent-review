# Webhook 公网访问方案

## 1. 为什么需要公网地址

GitHub 和 GitLab 的 Webhook 是由平台主动向 JayAgent-review 发起 HTTP 请求。本机地址只能被本机访问：

```text
http://localhost:8080/api/webhook/github
```

GitHub/GitLab 无法访问你的 `localhost`。如果 JayAgent-review 在本机运行，需要通过公网隧道、云服务器或其他网络转发方式提供外部可访问的 HTTPS 地址。

Webhook 接口为：

```text
POST /api/webhook/github
POST /api/webhook/gitlab
```

## 2. 方案对比

| 方案 | 适用场景 | 优点 | 注意事项 |
| :--- | :--- | :--- | :--- |
| Cloudflare Tunnel | 本地开发联调 | 免费、通常无需公网 IP、支持 HTTPS | 临时地址可能变化 |
| ngrok | 快速测试和查看请求 | 配置简单、提供请求日志 | 免费版有地址变化或使用限制 |
| 云服务器 + Nginx | 长期运行 | 地址稳定、适合生产 | 需要服务器、域名、HTTPS 和运维 |
| FRP / 端口映射 | 已有公网服务器或公网 IP | 灵活、可自定义网络结构 | 配置和安全要求较高 |

第一次本地联调建议使用 Cloudflare Tunnel；想观察 Webhook 请求细节时可以使用 ngrok；长期运行则使用云服务器加 HTTPS 反向代理。

## 3. Cloudflare Tunnel

先启动 JayAgent-review：

```powershell
mvn spring-boot:run
```

保持应用运行，再打开新的 PowerShell 窗口：

```powershell
cloudflared tunnel --url http://localhost:8080
```

命令会输出类似地址：

```text
https://<your-public-domain>
```

平台 Webhook URL：

```text
https://<your-public-domain>/api/webhook/github
https://<your-public-domain>/api/webhook/gitlab
```

临时 Tunnel 的域名可能在重启后变化。地址变化后，需要同步修改平台 Webhook URL。

## 4. ngrok

先启动项目，再打开新的 PowerShell 窗口：

```powershell
ngrok http 8080
```

ngrok 会输出类似：

```text
Forwarding https://<your-tunnel-domain> -> http://localhost:8080
```

对应地址：

```text
https://<your-tunnel-domain>/api/webhook/github
https://<your-tunnel-domain>/api/webhook/gitlab
```

ngrok 适合快速测试和观察进入隧道的请求；免费版通常有地址变化或流量限制。

## 5. 云服务器 + Nginx

长期运行建议使用有公网 IP 的云服务器和域名：

```text
GitHub/GitLab
    -> https://<your-public-domain>
    -> Nginx
    -> JayAgent-review:8080
```

基本步骤：

1. 准备云服务器和域名，并将 DNS 解析到服务器公网 IP。
2. 在服务器上启动 JayAgent-review，监听 `8080`。
3. 使用 Nginx 将 HTTPS 请求转发到 `http://127.0.0.1:8080`。
4. 配置 TLS 证书。
5. 使用以下平台地址：

```text
https://<your-public-domain>/api/webhook/github
https://<your-public-domain>/api/webhook/gitlab
```

不要把 Elasticsearch 的 `9200` 端口暴露到公网。

## 6. FRP 或路由器端口映射

FRP 通常需要一台具有公网 IP 的服务器：

```text
本机 frpc -> 公网服务器 frps -> 外部访问地址 -> 本机 JayAgent-review:8080
```

路由器端口映射则是将公网端口转发到本机 `8080`。这两种方式需要自行处理防火墙、域名、TLS 和访问控制，适合熟悉网络配置的用户。

## 7. 当前项目的完整联调流程

### 7.1 启动基础服务

```powershell
Set-Location "<project-root>"
docker start es-rag
Invoke-RestMethod "http://localhost:9200"
```

如果容器不存在：

```powershell
docker compose up -d elasticsearch
```

### 7.2 设置环境变量

```powershell
$env:DEEPSEEK_API_KEY = "<your-deepseek-api-key>"
$env:JAYAGENT_API_KEY = "<your-local-api-key>"
$env:GITHUB_TOKEN = "你的GitHub访问Token"
$env:GITLAB_TOKEN = "你的GitLab访问Token"
$env:WECHAT_WEBHOOK_KEY = "企业微信机器人Key"
$env:JAYAGENT_GITHUB_WEBHOOK_SECRET = "<your-github-webhook-secret>"
$env:JAYAGENT_GITLAB_WEBHOOK_TOKEN = "<your-gitlab-webhook-token>"
$env:JAYAGENT_WEBHOOK_SHARED_SECRET = "<your-shared-webhook-secret>"
```

其中 `GITHUB_TOKEN`/`GITLAB_TOKEN` 用于应用访问平台 API；`JAYAGENT_GITHUB_WEBHOOK_SECRET`/`JAYAGENT_GITLAB_WEBHOOK_TOKEN` 用于平台回调认证；`WECHAT_WEBHOOK_KEY` 用于企业微信机器人告警。

### 7.3 启动和暴露应用

```powershell
mvn spring-boot:run
```

另开窗口启动 Cloudflare Tunnel 或 ngrok，然后把输出的 HTTPS 地址加上 `/api/webhook/github` 或 `/api/webhook/gitlab`，填写到平台 Webhook 配置中。

### 7.4 配置平台

GitHub：`Settings -> Webhooks -> Add webhook`，选择 `application/json`，Secret 与 `JAYAGENT_GITHUB_WEBHOOK_SECRET` 一致，事件选择 Pull requests。

GitLab：`Settings -> Webhooks`，Secret token 与 `JAYAGENT_GITLAB_WEBHOOK_TOKEN` 一致，事件选择 Merge request events。

### 7.5 验证结果

认证和去重通过后，Webhook 返回 `202 Accepted`。这只表示任务已接受并安排后台处理，不代表 AI 审查、评论回写或企业微信告警已经完成。继续检查：

- Spring Boot 控制台日志
- GitHub PR 或 GitLab MR 评论
- `/api/jayagent/history` 历史记录
- 高风险结果对应的企业微信消息

## 8. 安全注意事项

- 不要把 Token 放进 URL 或提交到 Git。
- 保持 `app.jayagent.webhook.strict-mode: true`。
- 不要把 Elasticsearch `9200` 暴露到公网。
- 临时隧道适合开发测试，不适合作为生产部署方案。
- 隧道关闭或地址变化后，平台将无法访问 Webhook。
