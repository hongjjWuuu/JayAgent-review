# JayAgent-review 多语言规则体系计划书

> 目标：把当前以 Java 安全审查为主的知识库，升级为支持 Java、Python 及通用安全规则的多语言审查体系，让 RAG 检索、Prompt 构造、结果解释和评测体系都能按语言分流。

## 1. 现状与问题

### 1.1 当前现状

当前项目的知识库以单文件 Java 规则为主，主要覆盖：

- 硬编码密钥
- 敏感信息泄露
- SQL 注入
- 权限与访问控制
- 异常处理
- 日志规范
- Spring Security / Session / Cookie 安全
- XSS / CSRF / 文件上传 / 路径穿越
- 依赖安全 / 许可证合规 / 供应链风险
- 工程规范问题

### 1.2 当前问题

- 规则主要偏 Java 场景，对 Python、Node.js、Go 等语言不够专项。
- 语言无关的通用安全规则和 Java 专项规则混在一起，检索噪声偏大。
- Prompt 里没有显式区分仓库语言，模型容易带入 Java 规则去审查 Python 项目。
- 缺少多语言评测集，无法量化不同语言的误报率和漏报率。

## 2. 改造目标

### 2.1 短期目标

- 保留现有 Java 审查能力不回退。
- 增加通用安全规则库。
- 增加 Python 专项规则库。
- 在审查前识别仓库主语言。
- 根据语言选择对应规则库或组合规则库。

### 2.2 中期目标

- 规则库按语言、类别、风险等级、场景分层。
- Prompt 自动带上语言上下文。
- 检索结果可按语言权重融合。
- 评测体系按语言分别统计。

### 2.3 长期目标

- 支持多语言项目混合审查。
- 支持规则版本管理、规则命中反馈和规则生命周期管理。
- 支持语言专属 Prompt 模板和语言专属审查报告结构。

## 3. 推荐架构

### 3.1 分层设计

```text
代码仓库
  -> 语言识别层
  -> 规则路由层
  -> 语言专属知识库
  -> 混合检索与重排层
  -> Prompt 构造层
  -> LLM 审查层
  -> 结构化报告层
  -> 评测与反馈层
```

### 3.2 知识库分层

- `common_security_rules.txt`
  - 语言无关规则
  - 硬编码密钥
  - 敏感信息泄露
  - 异常处理
  - 日志规范
  - 依赖安全
  - 工程规范

- `java_security_rules.txt`
  - Java / Spring / JDBC / Servlet / Security 专项规则

- `python_security_rules.txt`
  - Python / Flask / FastAPI / Django / SQLAlchemy / subprocess / pickle 专项规则

- `web_security_rules.txt`
  - 前端与接口安全专项规则

- `dependency_security_rules.txt`
  - 供应链与依赖风险专项规则

## 4. 规则库设计原则

### 4.1 每条规则必须包含的字段

建议每条规则都统一成以下结构：

- `rule_id`
- `title`
- `language`
- `category`
- `severity`
- `description`
- `bad_example`
- `good_example`
- `fix_suggestion`
- `scope`
- `source`
- `tags`

### 4.2 规则写法原则

- 每条规则尽量只表达一个风险点。
- 必须有错误示例和正确示例。
- 修复建议要可执行。
- 同一规则不要同时写太多语言的实现细节。
- 通用规则和语言专项规则分开写。

### 4.3 规则标签建议

建议标签至少包括：

- `language`
- `category`
- `framework`
- `risk`
- `api`
- `storage`
- `auth`
- `file`
- `logging`
- `dependency`

## 5. 语言识别与路由策略

### 5.1 语言识别来源

可以按以下顺序判断仓库主语言：

1. 文件后缀统计
2. 目录结构特征
3. 依赖文件特征
4. 入口文件特征
5. 用户显式指定语言

### 5.2 路由策略

#### 方案 A：单语言优先

- 识别主语言。
- 只检索对应语言规则库。
- 再补充少量通用规则。

适合：

- 单语言仓库
- 规则库较小

#### 方案 B：主语言 + 通用规则

- 先检索语言专属规则库。
- 再检索通用规则库。
- 最后做融合排序。

适合：

- 大部分真实项目
- 既需要语言专项判断，也需要通用安全判断

#### 方案 C：多库并行融合

- 同时检索多个规则库。
- 根据语言权重、类别权重和文档分数做融合。

适合：

- 混合语言仓库
- 复杂平台级项目

## 6. RAG 检索改造方案

### 6.1 当前问题

当前检索 Query 主要围绕 Java 安全关键词构造，容易对 Python 项目产生语言偏差。

### 6.2 改造方向

- 检索前先识别主语言。
- Query 中加入语言上下文。
- 规则召回时按语言过滤。
- 对通用规则和语言专属规则分别设定权重。
- Prompt 中明确告诉模型当前语言和规则来源。

### 6.3 推荐检索流程

```text
代码 Diff
  -> 语言识别
  -> 构造语言感知 Query
  -> 检索语言专属规则库
  -> 检索通用规则库
  -> 合并候选集
  -> 按语言与类别重排
  -> 过滤 minScore
  -> 注入 Prompt
```

## 7. Prompt 改造方案

### 7.1 必须增加的信息

Prompt 至少要显式包含：

- 当前仓库语言
- 当前使用的规则库类型
- 当前审查目标
- 当前风险类别范围
- 输出格式要求

### 7.2 推荐的 Prompt 结构

```text
You are a code review agent for a <language> project.

Current language: <language>
Rule sources:
- common security rules
- <language> specific rules

Inspect the diff for:
- secrets
- injection
- auth/access control
- logging leakage
- file/path safety

Use the retrieved context below as primary evidence.
If no matching evidence exists, say so clearly.
```

### 7.3 语言专项 Prompt 模板

- Java：强调 Spring Security、JDBC、Session/Cookie、Servlet、Maven 依赖。
- Python：强调 FastAPI、Flask、Django、subprocess、pickle、yaml、requests。
- 前端：强调 XSS、CSRF、token 存储、接口暴露、前端权限控制。

## 8. Python 规则库建议内容

建议优先加入以下 Python 专项规则：

- `pickle` 反序列化风险
- `yaml.load` 风险
- `eval` / `exec`
- `subprocess` 和 `shell=True`
- `os.system`
- `requests` 外部请求风险
- 文件路径和目录穿越
- FastAPI / Flask 参数校验
- Django 权限与中间件
- SQLAlchemy 参数化与 ORM 安全
- 日志与敏感信息泄露

## 9. Java 规则库继续细化方向

当前 Java 规则还能继续拆细：

- Spring Security 认证与授权
- Session / Cookie / CORS / CSRF
- JDBC / MyBatis 参数化
- 文件上传下载
- 路径穿越
- 异常处理和日志
- 依赖与许可证
- 代码规范类问题

## 10. 评测体系改造

### 10.1 按语言分别评测

- Java Gold Set
- Python Gold Set
- 通用安全 Gold Set

### 10.2 评测指标

- Recall@K
- Precision@K
- MRR
- 误报率
- 漏报率
- 审查建议可执行率
- 语言命中准确率

### 10.3 评测样本建议

每条样本至少包含：

- 仓库语言
- 文件路径
- Diff
- 期望规则
- 期望风险等级
- 期望修复建议

## 11. 前端展示改造

建议前端增加：

- 仓库主语言识别结果
- 规则命中语言标签
- 规则类别标签
- 规则来源库
- 通用规则 / 语言规则区分

这样用户能清楚知道：

- 这条风险是通用规则命中的
- 还是某种语言专项规则命中的

## 12. 迁移步骤

### 阶段 1：不破坏现有能力

- 保留当前 `java_security_rules.txt`
- 新增 `common_security_rules.txt`
- 先让检索支持“Java + 通用规则”

### 阶段 2：加入 Python

- 新增 `python_security_rules.txt`
- 增加语言识别
- Prompt 加入语言信息

### 阶段 3：评测与权重

- 建立多语言 Gold Set
- 调整规则权重
- 优化召回与重排

### 阶段 4：平台化

- 规则版本管理
- 规则命中反馈回流
- 多语言模板管理
- 规则生命周期治理

## 13. 风险与边界

### 13.1 风险

- 规则库过大导致召回噪声上升
- 多语言 Prompt 让模型上下文变长
- 语言识别不准导致错误路由
- 规则重复导致审查建议冗余

### 13.2 控制方法

- 规则分层
- 规则标签
- 语言过滤
- 规则重排
- 评测门禁

## 14. 最小可行方案

如果想快速落地，推荐的最小版本是：

1. 保留现有 Java 规则库
2. 新增一个通用规则库
3. 新增 Python 规则库
4. 审查前自动识别主语言
5. 检索时优先语言专属规则，再补通用规则
6. Prompt 中显式写明当前语言
7. 用多语言样本做基本评测

## 15. 结论

多语言规则体系的本质，不只是多加几个文件，而是要把：

- 语言识别
- 规则分层
- 检索路由
- Prompt 模板
- 评测体系
- 前端展示
- 规则治理

全部串起来。

当前最稳妥的路径是先做：

- `Java + 通用规则`
- 再扩展 `Python`
- 再做多语言融合与评测闭环
