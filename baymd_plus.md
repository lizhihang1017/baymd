# BayMD 功能路线图（完整版）

> 本文档是接下来开发工作的完整规格说明。执行本文档任务的 Agent 应先阅读「项目现状」和「必读文件」，再按 Phase 顺序逐个实施。每个 Phase 独立可交付，完成一个再开始下一个。

---

## 0. 项目现状（重要：以此为准，勿轻信旧文档）

BayMD 是「AI 私人医生」医疗健康问答系统：ReAct Agent + 三级记忆（短时/摘要/语义）+ RAC 检索 + React 前端。

- **README.md 基本准确**（最近更新）；**CLAUDE.md 中“7 阶段流水线”描述已过时**，实际架构见下。
- 后端：Spring Boot 3.5.7 / JDK 17，端口 **9090**，context-path `/api/baymd`
- 前端：frontend / React + Vue + TS + Tailwind，端口 5173，代理到 9090
- 数据库：PostgreSQL + pgvector（`rag.vector.type: pg`），schema 见 [resources/database/schema_pg.sql](resources/database/schema_pg.sql)
- 模块：`framework`（基础设施）/ `infra-ai`（LLM 客户端与路由）/ `mcp-server`（MCP 工具进程，9099）/ `bootstrap`（主应用）
- 包根：`com.zhi.baymd`，业务包：`rag` / `ingestion` / `knowledge` / `user` / `admin`

---

## 对话管线（实际架构）

StreamChatPipeline.execute(ctx):

- 预处理：loadMemory → rewriteQuery → resolvelntents
- 分发：ExecutorRegistry.resolve(ctx) → 执行器
- 执行器（ExecutionMode 枚举声明顺序 = 优先级）：
  - CLARIFICATION → SYSTEM ONLY → RAG → AGENT

---

- `ExecutorRegistry.resolve()` 按 `EnumMap（枚举声明顺序）` 遍历，取第一个 `supports(ctx) == true` 的执行器。新增高优先级模式必须把枚举值声明在前面。

---

## 关键约定

- **主键**: VARCHAR(20) 分布式 ID (C framework 的 `distributedid` 包)
- **ORM**: MyBatis-Plus、DO 实体 + Mapper 接口，逻辑删除字段 `deleted`
- **返回**: `Result<T>` 包装 (C framework 的 `convention` 包)
- **流式**: `SseEmitter`，聊天端点 `GET /rag/v3/chat_(SSE)`，`POST /rag/v3/stop`
- **Prompt 模板**: `bootstrap/src/main/resources/prompt/*.st`，经 `PromptTemplateLoader` 加载
- **定时任务模式**: `@Scheduled` 扫描 + `ScheduleLockManager` 分布式锁 + 表内 `lock_until` 字段 (参考 `knowledge/schedule/KnowledgeDocumentScheduleJob.java`)
- **构建/测试**: `/mvnw install_(C)` 用 install 解决模块依赖；单模块测试 `./mvnw -pl bootstrap am test`
- **提交信息**: 中文，`feat: / fix: / docs: 前缀 (见 git log 惯例)`

---

## 必读文件 (实施任何 Phase 前)

| 文件 | 作用 |
|---|---|
| `bootstrap/.../rag/service/pipeline/StreamChatPipeline.java` | 管线入口 |
| `bootstrap/.../rag/service/pipeline/ExecutorRegistry.java` + `ExecutionMode.java` + `ConversationExecutor.java` | 执行器分发机制 |
| `bootstrap/.../rag/service/pipeline/StreamChatContext.java` | 管线上下文字段 |
| `bootstrap/.../rag/core/agent/AgentTool.java` + `AgentToolRegistry.java` + `KnowledgeRetrievalTool.java` | Agent 工具接口与参考实现 |
| `bootstrap/.../rag/core/memory/extract/MemoryExtractionOrchestrator.java` | 对话结束后异步记忆提取 (Phase 2 的挂载点) |
| `bootstrap/.../knowledge/schedule/KnowledgeDocumentScheduleJob.java` | 定时扫描 + 分布式锁参考模式 |
| `bootstrap/src/main/resources/application.yaml` | 全部配置 |
| `resources/database/schema_pg.sql` | 全部表结构 |
| `frontend/src/api/baymd.ts` + `frontend/src/components/ChatView.tsx` | 前端 API 封装与聊天界面 |

---

## 任务总览（按优先级排序）

| Phase | 任务 | 价值 | 依赖 |
|---|---|---|---|
| 1    | 报告解读入口（图片/PDF上传 + 多模态解析） | 补充 README 承诺缺失的场景断层 | 无 |
| 2    | 主动随访（邮件推送） | 最大差异化：从“问答机器人”到“私人医生” | 无（与 1 并行可行） |
| 3    | Agent 工具扩充 | 让 ReAct 框架有兵可用 | 部分工具联动 1、2 |
| 4    | 紧急分诊护栏 | 医疗安全底线 | 无 |
| 5    | 次级改进清单 | 见文末 | 无 |

---

## Phase 1 — 报告解读入口（图片/PDF上传 + 结构化解析）

### 背景

README 承诺“报告解读”为 7 大场景之一，但系统只有文本输入，用户无法上传化验单图片/PDF。  
现有 ingestion 模块是“知识库”提取流程，不适用于用户个人报告，需要独立的轻量入口。

### 设计

前端上传（图片/PDF）→ POST /rag/report/upload_(multipart, 复用现有 S3 存储)  
→ 解析分流：  
PDF 有文本层→ Tika 提取文本（复用 core/parser/TikaDocumentParser 的能力）  
图片/扫描 PDF→ VisionService 调用多模态模型（qwen-vl-plus）提取  
→ LLM 结构化：提取指标 JSON（{name, value, unit, refRange, flag}）  
→ 存储 medical_report 返回 reportld + 结构化预览  
前端携带 reportld 发起 chat → 管线将报告内容注入 context → 正常走 RAG 检索指标解读知识  

**关键决策：** 不改动现有 ChatMessage（纯文本）。多模态调用封装在 `infra-ai` 新增的独立 VisionService 中，输出文本后进入常规管线，避免侵入所有聊天链路。

### 改动清单

**infra-ai 新建:**
- vision/VisionService.java — 接口: `String extractText(byte[] imageBytes, String mimeType, String prompt)`
- vision/BailianVisionClient.java — 走百炼 OpenAI 兼容接口，`content` 数组含 `image_url` (base64 data URL)。复用 `ai.providers.bailian` 的 `url/api-key` 配置
- 配置类扩展 `AIModelProperties`: 新增 `ai.vision.default-model` (默认 `qwen-vl-plus`)

**bootstrap 新建:**
- rag/controller/ReportController.java — `POST /rag/report/upload` (multipart, 限 10MB, `jpg/png/pdf`)，`GET /rag/report/{id}`
- rag/service/ReportParseService.java (接口+impl) — 分流解析 + LLM 结构化 + 入库
- rag/dao/entity/MedicalReportDO.java + rag/dao/mapper/MedicalReportMapper.java
- Prompt 模板: `prompt/report-extract.st` (从报告文本/图片提取结构化指标 JSON)

**bootstrap 修改:**
- RAGChatController.chat() 增加可选参数 `reportId`
- StreamChatContext 增加 `reportContext` 字段; `StreamChatPipeline` 预处理阶段若有 `reportId` 则加载报告结构化内容注入
- RagExecutor / AgentExecutor 的 prompt 组装处拼入报告内容 (查看 `prompt/answer-chat-medical-kb.st` 决定注入位置)

**前端修改:**
- ChatView.tsx: 输入框旁加上传按钮 (回形针图标) → 上传 → 显示报告卡片 → 后续提问自动携带 `reportId`
- api/baymd.ts: `uploadReport()`、`chat` 参数扩展

### 数据库

```sql
CREATE TABLE t_medical_report (
    id    VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(20) NOT NULL,
    file_name    VARCHAR(256) NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,    -- S3 对象 key
    mime_type    VARCHAR(64),
    raw_text    TEXT,    -- 提取的原始文本
    structured    JSONB,    -- 结构化指标数组
    parse_status    VARCHAR(16) DEFAULT 'PENDING',    -- PENDING/SUCCESS/FAILED
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT DEFAULT 0
);

CREATE INDEX idx_report_user ON t_medical_report (user_id, create_time DESC);
```

### 配置 (application.yaml 追加)

```yaml
ai:
  vision:
    default-model: qwen-vl-plus
  candidates:
    - id: qwen-vl-plus
    provider: bailian
    model: qwen-vl-plus
    priority: 1
rag:
  report:
    max-file-size-mb: 10
    allowed-types: [image/jpeg, image/png, application/pdf]
```

### 验收标准

1. 上传常规图片 → 返回结构化指标 JSON，`parse_status=SUCCESS`
2. 上传带文本层 PDF → 走 Tika 路径（不调 VL 模型），日志可见分流决策
3. 携带 reportld 提问“这份报告有什么问题” → 回答引用报告中的具体指标值
4. 超大文件/不支持格式 → 返回明确错误，不产生脏数据
5. 单元测试：解析分流逻辑、结构化 JSON 解析容错（LLM 输出非法 JSON 时降级为纯文本）

---

## Phase 2 — 主动随访（邮件推送）★ 核心差异化

### 背景

系统目前纯被动问答。“私人医生”的本质是主动关怀：用户咨询头疼后 3 天，主动发邮件问“好点了吗”。用户回流回答后写回语义记忆，形成“越用越聪明”闭环。

**推送通道本期只做邮件**（用户明确指定），但通道层必须做成 SPI 可插拔，为将来短信/微信留位。

### 设计

① 触发：对话结束记忆提取完成后（挂 MemoryExtractionOrchestrator 之后），LLM 判断本轮对话是否值得随访（prompt: followup-plan.st），输出（shouldFollowUp, followUpQuestion, delayDays(1-14), topic）

② 落库：t_followup_task_(status=PENDING, trigger_time=now+delayDays) 频控规则（代码硬校验，不依赖 LLM）：
  - 同用户同 topic 7 天内已有任务 → 跳过
  - 同用户每日最多发 1 封 → 调度时顺延

③ 调度：@Scheduled 扫描到期任务（完全复制 KnowledgeDocumentScheduleJob + ScheduleLockManager 的锁模式），逐条发送

④ 发送：NotificationChannel SPI → EmailNotificationChannel
  - 隐私红线：邮件正文【绝不包含病情细节】，只写通用提醒
  - “您几天前在 BayMD 的健康咨询有一条随访，点击查看” + 深链 + 退订链接

⑤ 回流：深链 /chat?followupId=xxx → 前端加载随访问题预填输入框
  - 用户发送后走正常管线（记忆自动回写）→ 任务 status=ANSWERED

⑥ 退订：GET /followup/unsubscribe?token= (免登录，token 为任务表随机串)

### 改动清单

**framework 或 bootstrap 新建（建议放 bootstrap`rag/core/notify/`）:**
- NotificationChannel.java → 接口：String getType(); boolean send(NotificationMessage msg);
- NotificationMessage.java → `(userId, title, body, deepLink)`
- EmailNotificationChannel.java → 基于 `spring-boot-starter-mail`（JavaMailSender），HTML 模板
- NotificationDispatcher.java → 按用户可用通道分发（本期只有 email，无邮箱则跳过并记日志）

**bootstrap 新建:**
- `rag/core/followup/plan/FollowUpPlanService.java` → LLM 判断是否随访（注意与已有 `followup/FollowUpGenerator.java` 区分命名，二者用途不同）
- `rag/service/FollowUpTaskService.java`（接口+impl） → 任务 CRUD + 频控校验
- `rag/service/schedule/FollowUpScheduleJob.java` → 定时扫描发送
- `rag/controller/FollowUpController.java` → `GET /followup/list`（用户查看）、`GET /followup/unsubscribe`、`GET /followup/{id}`（深链取随访问题）
- `rag/dao/entity/FollowUpTaskDO.java` + Mapper
- Prompt: `prompt/followup-plan.st`
- 邮件 HTML 模板: `resources/mail/followup-notify.html`

**bootstrap 修改:**
- `pom.xml` 加 `spring-boot-starter-mail`
- 记忆提取完成后调用 `FollowUpPlanService`（找到 `MemoryExtractionOrchestrator` 的调用完成点，异步执行，失败不影响主流程）
- `user` 模块: `UserController` 加绑定/解绑邮箱接口；绑定时发送验证邮件（6 位码，Redis 存 5 分钟）— 防止把健康提醒发到错误邮箱

**前端修改:**
- `SettingsView.tsx`：邮箱绑定卡片（输入邮箱 → 收验证码 → 确认） + 随访开关
- `App.tsx` / `ChatView.tsx`：解析 URL `followupId` 参数 → 拉取随访问题预填输入框

### 数据库

```sql
-- 用户表扩展
ALTER TABLE user ADD COLUMN email VARCHAR(128);
ALTER TABLE user ADD COLUMN email_verified SMALLINT DEFAULT 0;
ALTER TABLE user ADD COLUMN followup_enabled SMALLINT DEFAULT 1;

-- 随访任务表
CREATE TABLE t_followup_task (
    id    VARCHAR(20) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(20) NOT NULL,
    conversation_id VARCHAR(20),
    topic    VARCHAR(64),    -- 随访主题（频控去重用）
    question    VARCHAR(512) NOT NULL,    -- 随访问题（App 内展示）
    trigger_time    TIMESTAMP NOT NULL,
    status    VARCHAR(16) DEFAULT 'PENDING',    -- PENDING/SENT/ANSWERED/CANCELLED/EXPIRED
    channel    VARCHAR(16) DEFAULT 'email',
    unsub_token    VARCHAR(64) NOT NULL,
    lock_until    TIMESTAMP,    -- 调度分布式锁
    sent_time    TIMESTAMP,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted    SMALLINT DEFAULT 0
);

CREATE INDEX idx_followup_scan ON t_followup_task (status, trigger_time);
CREATE INDEX idx_followup_user ON t_followup_task (user_id, topic, create_time DESC);
```

### 配置 (application.yaml 追加)

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.qq.com}
    port: 465
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}    # QQ/163 邮箱用授权码
    properties:
    mail.smtp.ssl.enable: true

rag:
  followup:
    enabled: true
    scan-delay-ms: 60000
    daily-limit-per-user: 1
    topic-dedup-days: 7
    quiet-hours: "22:00-08:00"        # 静默时段内到期的任务顺延到08:00
    expire-days: 3                    # SENT后3天未回答→EXPIRED
    deep-link-base: http://localhost:5173
```

### 验收标准

1. 完成一轮“我最近头疼”对话 → t_followup_task 产生 PENDING 任务，trigger_time 合理
2. 把 trigger_time 改为过去 → 1 分钟内收到邮件；邮件正文**无病情词汇**，含深链和退订链接
3. 点深链 → 前端预填随访问题 → 发送 → 任务变 ANSWERED，语义记忆新增对应 Fact
4. 同主题 7 天内二次对话 → 不产生重复任务（日志可见频控命中）
5. 点退订 → followup_enabled=0，后续不再产生任务
6. 未绑定邮箱的用户 → 任务照常产生但发送跳过（等绑定后补发或过期）
7. 静默时段任务顺延；单元测试覆盖频控、静默时段、过期逻辑

---

## Phase 3 — Agent 工具扩充

### 背景

ReAct框架完整（ReAct loop + AgentToolRegistry），但本地工具只有 KnowledgeRetrievalTool 一个，MCP端只有 echo 示例。Agent有脑无手。

### 工具接口（现成，直接实现）

```java
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getParametersSchema();
    AgentToolResult execute(Map<String, Object> parameters);
    String getType();
}
```

注册机制：声明为 Spring `@Component` 即被 `AgentToolRegistry` 自动收集（先阅读其构造器确认收集方式），参考 `KnowledgeRetrievalTool.java` 依样实现。

### 新增工具（按序实施）

**3.1 MedicalCalculatorTool**（纯本地计算，零外部依赖，先做）
- 位置：`rag/core/agent/tool/MedicalCalculatorTool.java`
- 参数：`calculator: "bmi"|"egfr"|"bsa"|"heart_rate_max", params: {...}`
- BMI = kg/m²; eGFR 用 CKD-EPI 2021 公式；BSA 用 Mosteller 公式
- 返回值带解读区间（如 BMI 24.5 → “超重（中国标准 ≥ 24）”）
- 公式必须写单元测试，用医学文献标准值验证

**3.2 DrugInteractionTool**（药物相互作用）
- 位置：同上目录
- 数据源：新表 `drug_interaction (drug_a, drug_b, severity, description)`，附带一份种子 SQL（常见 50 组相互作用，如华法林 + 阿司匹林、辛伐他汀 + 克拉霉素）
- 查询：两药名标准化后双向查表；查不到 → 明确返回“未收录，建议咨询药师”（禁止编造）

**3.3 FollowUpReminderTool**（联动 Phase 2）
- Agent 可在对话中主动为用户创建随访任务：“三天后提醒我复查测血压”
- 参数：`question, delayDays, topic` → 调用 `FollowUpTaskService`（走同样的频控）

**3.4 ReportQueryTool**（联动 Phase 1）
- 查询当前用户历史报告的结构化指标，支持按指标名过滤和时间对比（“我的血糖比上次高吗”）

### 同步修改

Agent system prompt（ReActLoop 的工具注入段/AgentExecutor）确认多工具描述渲染正确  
rag/core/agent/ 下工具类如新增多，移入子包 tool/ 并保持 KnowledgeRetrievalTool 兼容

### 验收标准

1. 问“我身高175体重80，BMI多少”→ Agent 调 calculator 工具→ 回答含 26.1 和“超重”解读
2. 问“华法林和阿司匹林能一起吃吗”→ 调 interaction 工具→ 回答含严重度与依据；问未收录组合→ 明确未收录
3. 对话中说“提醒我三天后复测血压”→ t_followup_task 新增任务
4. 每个工具有单元测试（参数校验、边界、未命中）

---

## Phase 4 — 紧急分诊护栏

### 背景

红旗症状（胸痛、呼吸困难、意识不清等）走常规检索管线又慢又危险。必须短路：跳过检索，直接输出急救指引。

### 设计（两级检测，第一级零成本）

1. **关键词级**：预处理阶段正则/关键词表匹配原始问题（不等改写）。
   词表：胸痛/胸闷/压榨/呼吸困难/喘不上气/意识不清/昏迷/抽搐/大出血/剧烈头痛/自杀/轻生/割腕/药物过量/中毒/过敏性休克...
   （实施时扩充到≥40条，含常见口语变体）

2. **LLM 级**：intent-classifier.st 增加输出字段 is_emergency（复用已有分类调用，不新增 LLM 请求），捕获关键词漏网的表述（如“感觉自己快不行了”）
   命中任一 → EmergencyExecutor 接管

### 改动清单

- `ExecutionMode` 枚举**最前面**新增 `EMERGENCY`（`EnumMap` 按声明顺序遍历，位置即优先级）
- 新建 `rag/service/pipeline/EmergencyExecutor.java` — 实现 `ConversationExecutor`:
  - `supports()`：检查 `ctx` 中的紧急标志
  - `execute()`：**不调用检索/Agent**，直接流式输出固定模板：识别到的风险 + 立即拨打 120 / 就近急诊 + 简要现场处置（如胸痛：停止活动静坐） + 免责声明；写 `trace`
- 新建 `rag/core/guardrails/EmergencyDetector.java` — 关键词表 + 匹配逻辑；词表配置或常量类
- 修改 `StreamChatPipeline`：预处理最前调用 `detector`，结果放入 `StreamChatContext`（新增 `emergency` 字段）
- 修改 `prompt/intent-classifier.st` + `DefaultIntentClassifier` 解析 `is_emergency`
- 前端 `ChatView.tsx`：SSE 帧带紧急标记时渲染红色警示卡片样式
- 自杀/自残类特殊处理：额外输出心理援助热线（全国 24 小时：12356）

### 验收标准

1. "我爸爸突然胸口剧痛出冷汗" 一秒回急救指引（无检索延迟），trace 显示 EMERGENCY 模式
2. "感觉自己快不行了，喘不过气" → （关键词未全中）LLM 级兜底命中
3. "胸痛的常见原因有哪些" 这类**知识性提问** → 词表需结合语境权衡；允许保守策略（宁可误报），但需在回答前强调说明"如您正在经历该症状请立即就医"
4. 普通问题零误伤：跑既有测试集确认无回归
5. `EmergencyDetector` 单元测试 ≥ 20 用例（含正反例）

---

## Phase 5 — 次级改进清单（完成 1-4 后按需领取）

1. **引用/追问前端渲染检查**：后端 `CitationCollector` / `FollowUpGenerator` 已产出并随 SSE 完成帧下发（查 `EnrichedStreamCallback.java`），但 `ChatView.tsx` 疑似未渲染。补齐引用来源卡片 + 追问按钮（点击即发送）。

2. **健康档案显示优化**：语义记忆的画像（`ProfileGenerationService`）用户不可见。新增档案页：过敏史/慢病/用药清单结构化展示，支持用户编辑，编辑结果作为高置信 Fact 写回。

3. **离线评估基线**：构建 200+ 条医疗 QA 金标准集（可从 cMedQA2 抽样），实现 `recall@k` / `faithfulness` 评估脚本，接入 CI。此后所有检索改动都要跑基线。

4. **BM25 词法检索通道**：现有“多通道”两条全是向量。利用 PG `tsvector`（中文需 `zhparser` 或用 `jieba` 预分词存储）新增 `LexicalSearchChannel`，实现 `SearchChannel` 接口注册进 `MultiChannelRetrievalEngine`，与 `RRF` 融合（已有 `RRFPostProcessor`）。用 Phase 5.3 的基线量化提升。

5. **成本/延迟可观测**：`trag_trace_node` 已记录各阶段耗时，聚合出延迟瀑布 + 每次对话 token 成本估算（`HeuristicTokenCounterService` 可用），在 admin Dashboard 加一页。

---

## 通用要求（所有 Phase 适用）

1. **不破坏现有行为**：改动 `StreamChatPipeline` / `StreamChatContext` 等公共类时跑全量测试 / `mvnw install`。

2. **新表同步更新**：`resources/database/schema_pg.sql`（保持幂等：`CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`）。

3. **配置新增键**：同步到 `META-INF/additional-spring-configuration-metadata.json`（如该文件对应模式要求）。

4. **每个 Phase 单独提交**：提交信息中文 `feat...`，格式参考 git log。

5. **LLM 输出解析必须容错**：JSON 解析失败要有降级路径，参考现有 `LLMResponseCleaner`。

6. **异步任务失败不影响主流程**：随访规划、报告解析等异步环节 `catch` 全部异常并记录日志。

7. **外部依赖（邮箱 SMTP、VLM 模型）不可用时优雅降级，功能开关（enabled 配置）默认关闭上线，验证后开启**。
