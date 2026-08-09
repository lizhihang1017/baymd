# BayMD 项目说明书（完整版）

> 本文档是 BayMD 的完整项目说明书,涵盖架构、模块、核心机制、数据库、配置、前端、管理后台、已知问题与扩展点。阅读顺序:项目现状 → 架构总览 → 核心流程 → 模块详解 → 数据库 → 配置 → 前端 → 已知问题 → 扩展点。

---

## 0. 项目现状

BayMD 是「AI 私人医生」医疗健康问答系统:ReAct Agent + 三级记忆(短时/摘要/语义)+ 多通道混合检索 + 主动随访推送 + React 前端。架构与业务解耦,可复用于任意领域 RAG 应用。

- **后端**: Spring Boot 3.5.7 / JDK 17,端口 **9090**,context-path `/api/baymd`
- **前端**: frontend / React + TypeScript + Tailwind + Vite,端口 5173,代理到 9090
- **数据库**: PostgreSQL 16 + pgvector(`rag.vector.type: pg`,向量维度 **1024**,与 text-embedding-v4 匹配)
- **缓存/消息**: Redis(Redisson 4.0)/ RocketMQ 5.2
- **模块**: `framework`(基础设施)/ `infra-ai`(LLM 客户端与路由)/ `mcp-server`(MCP 工具进程,9099)/ `bootstrap`(主应用)
- **包根**: `com.zhli.baymd`,业务包:`rag` / `ingestion` / `knowledge` / `user` / `admin` / `core`
- **AI 供应商**: 阿里云百炼 MaaS 端点(`ws-fdxd5hn6gac5hhhj.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`),key 格式 `sk-ws-`

---

## 0.1 技术栈及作用

| 技术 | 在本项目中的作用 |
|---|---|
| **Spring Boot 3.5** | 应用骨架与运行时:依赖注入、自动配置、Web 层(Controller/SSE)、AOP(权限切面/链路追踪)、`@Scheduled` 定时任务、`SmartInitializingSingleton` 启动钩子。所有模块的"胶水" |
| **JDK 17** | 语言运行时:虚拟线程之外的现代语法(record/密封类/switch 表达式),支撑流式与并发 |
| **MyBatis-Plus 3.5** | ORM:DO 实体 + Mapper,逻辑删除(`deleted` 字段)、分页、LambdaQuery 条件构造,替代手写 SQL 的样板 |
| **PostgreSQL 16 + pgvector** | 主数据库 + 向量检索:业务数据(用户/会话/知识库/文档/记忆)落库;`t_knowledge_vector`/`t_user_fact_vector` 等表用 vector 类型存储 1024 维嵌入向量,HNSW 索引做相似度检索(全局向量通道) |
| **Redis(Redisson 4.0)** | 分布式基础设施:Sa-Token 会话存储、意图树缓存、对话摘要防并发锁、公平限流器的信号量/ZSET 队列/Pub/Sub 通知 |
| **RocketMQ 5.2** | 异步消息:文档摄取分块、消息反馈等事件异步解耦,避免长任务阻塞主流程 |
| **Apache Tika 3.2** | 文档解析:PDF/Word/HTML 等格式统一提取文本,供摄取管道与报告解析使用;扫描件(<50 字符)自动转视觉模型 |
| **Sa-Token 1.43** | 认证鉴权:登录会话、token 管理、`@RequireAdmin` 角色校验,用户端/管理后台权限隔离 |
| **MCP SDK 1.1.2** | Agent 工具协议:客户端(Streamable HTTP/SSE 双传输)连外部 MCP Server 取实时数据;`mcp-server` 模块作服务端暴露工具 |
| **OkHttp 4.12** | HTTP 客户端:部分外部服务调用 |
| **AWS SDK S3** | 对象存储:文件上传存储(bucket 化),本地存储为备选 |
| **React + TS + Vite** | 前端:SSE 流式渲染、组件化管理后台 6 视图、Tailwind 样式 |
| **阿里云百炼(MaaS)** | LLM 供应商:chat(qwen3.7-plus 主力 / qwen-plus 快模型)、embedding(text-embedding-v4,1024 维)、rerank、vision 视觉模型,统一 OpenAI 兼容端点 |

---

## 1. 架构总览

### 1.1 对话管线（StreamChatPipeline）

```
StreamChatPipeline.execute(ctx):
  公共预处理（顺序固定）:
    detectEmergency()    → 紧急分诊检测（关键词匹配,零成本,必须最前）
    loadReportContext()  → 若用户上传了报告,加载报告内容
    loadMemory()         → 短时历史 + 长时摘要 + 语义记忆(事实/情节/画像)
    rewriteQuery()       → 查询改写 + 多问句拆分（快模型）
    resolveIntents()     → 树形意图分类（快模型）
  执行器分发:
    ExecutorRegistry.resolve(ctx) → 按 ExecutionMode 声明顺序取第一个 supports()==true
```

### 1.2 执行器优先级（ExecutorRegistry）

```
EMERGENCY > CLARIFICATION > SYSTEM_ONLY > AGENT > RAG
```

| 执行器 | 触发条件 | 行为 |
|---|---|---|
| `EmergencyExecutor` | 关键词命中高危场景（自杀/自残等） | 短路接管,输出心理援助热线,跳过后续检索/Agent |
| `ClarificationExecutor` | 意图存在歧义（规则无法判定时 LLM 二次确认） | 生成引导式选项 |
| `SystemOnlyExecutor` | 纯系统意图（欢迎/问候/关于） | 不走检索,直接闲聊 |
| `AgentExecutor` | ReAct 开启 + 深度思考 / 含 MCP 意图 | 多步推理,自主调工具 |
| `RagExecutor` | 兜底（supports 恒 true,必须最后） | 检索 + LLM 生成,SSE 流式 |

### 1.3 会话流程（用户视角）

```
GET /rag/v3/chat?question=... (SSE)
  → 管线预处理 → 执行器 → 流式回复
  → 完成后异步增强: 记忆抽取 / 追问生成 / 质量评估 / 随访规划
```

---

## 1.4 端到端问答流程（完整版）

### 纯文字描述

用户在前端输入一个问题,点击发送后,浏览器通过 SSE(Server-Sent Events)长连接把问题发给后端 `/rag/v3/chat` 接口。

后端收到问题后,先做一道"安全闸门":用关键词表快速扫描问题里有没有涉及自杀自残、心脑血管急症等高风险表述——这一步不调大模型、几乎零成本,一旦命中就立刻短路,直接返回就医警告和心理援助热线,不再走后续任何检索或生成。

通过安全闸门后,系统开始"回忆":把该用户在这个会话里最近的几轮对话原文、跨会话长期沉淀下来的事实记忆(比如用户有高血压、偏爱中医)和用户画像,一并加载进来,作为接下来大模型的背景信息。

接着是"理解问题":先由快模型把用户的问题改写得更利于检索,并把复杂问题拆成多个子问题;再由快模型做意图分类,判断用户问的是"科室推荐""症状自查""药物查询"还是"报告解读"等具体场景,并给出置信度打分。

系统根据意图和开关配置,把问题路由给 5 种执行方式之一(按优先级):歧义时先反问澄清;纯闲聊直接答;需要实时数据或多步推理时进入 Agent 模式(自主决定调用哪些工具);默认情况则进入知识库问答——并行做全局向量检索和意图定向检索,把命中的知识块去重、重排、裁剪后,连同问题一起交给主力大模型生成回答。

回答通过 SSE 流式返回,用户看到的是打字机效果的实时输出,回答中的关键结论会标注引用来源,点击可查看原始知识块。

回答完成后,系统在后台异步做增强处理:抽取用户的新事实存入记忆、生成推荐追问、对回答质量打分、判断是否需要几天后主动随访——这些都不阻塞用户看到回答。

### 完整链路（单轮）

```
用户提问 → GET /rag/v3/chat(SSE)
  │
  ▼
【1. 紧急分诊检测】EmergencyDetector
  │   关键词匹配(心血管/自杀/中毒等 8 类红旗词,零成本)
  │   ├─ 命中 → EmergencyExecutor 短路:直接输出就医警告 + 心理热线,结束
  │   └─ 未命中 → 继续
  ▼
【2. 报告上下文】若本会话上传过报告 → 报告内容拼入上下文
  ▼
【3. 记忆加载】loadMemory
  │   短时历史(最近 4 轮原文) + 长时摘要 + 语义记忆(事实/情节/画像)
  │   → 注入 system 消息
  ▼
【4. 查询改写】rewriteQuery(快模型)
  │   改写问题 + 拆分子问题;质量 <0.3 回退原问题
  ▼
【5. 意图分类】resolveIntents(快模型)
  │   叶子节点打分 → top3 + 阈值 0.35
  ▼
【6. 执行器分发】ExecutorRegistry(按优先级)
  │
  ├─ CLARIFICATION: 意图歧义?(候选≥2 且 LLM 确认歧义)
  │     → 输出引导选项,结束本轮(等用户选)
  │
  ├─ SYSTEM_ONLY: 问候/闲聊意图
  │     → 直接闲聊回答,不走检索
  │
  ├─ AGENT: ReAct 开启 + 深度思考 / 含 MCP 意图
  │     → 多步推理循环(≤5 轮):
  │        LLM → 工具调用? → 执行工具 → 观察结果 → 再想
  │        工具: 知识检索/计算器/药物相互作用/MCP(受工具开关控制)
  │
  └─ RAG(兜底): 多通道混合检索
        ├─ 全局向量检索(conf≥0.6)
        ├─ 意图定向检索(仅命中意图关联知识库,minScore≥0.4)
        ├─ 去重 → Rerank → 证据预算截断
        └─ system(医学模板+记忆) + 历史 + evidence+问题 → LLM 流式回答
  ▼
【7. SSE 流式输出】回答 + 引用标记 + 思维链
  ▼
【8. 异步增强】(不阻塞回答)
     记忆抽取(事实/情节) → 向量化 → 合并/画像
     追问生成(≤3 个)
     质量评估(LLM-as-Judge 4 维度)
     随访规划(值得随访? → 建任务,到期邮件)
```

### 分支总览（同一问题只会走一个分支,按优先级）

| 优先级 | 分支 | 触发条件 | 结果 |
|---|---|---|---|
| 1 | **紧急** | 关键词命中红旗词 | 就医警告,短路 |
| 2 | **澄清** | 意图歧义(候选≥2 且 LLM 确认) | 引导选项,等用户 |
| 3 | **系统直答** | 问候/关于 | 闲聊 |
| 4 | **Agent** | 深度思考 or MCP 意图 or 开关开启 | 多步推理 |
| 5 | **RAG** | 兜底(恒 true) | 检索 + 生成 |

**RAG 内部子分支**: 按命中意图选择模板 —— 纯KB(`answer-chat-medical-kb.st`)/ 纯MCP(`answer-chat-mcp.st`)/ KB+MCP混合(`answer-chat-mcp-kb-mixed.st`)。

### 多轮对话处理（三层记忆协作）

```
对话轮次
  │
  ├─ 短时(原文): t_message 最近 N 轮(默认4轮)原文
  │     → 以 user/assistant 消息序列注入,紧跟 system 之后
  │     （改写阶段只取最近 2 轮,回答阶段完整注入）
  │
  ├─ 摘要(压缩): 超过 5 轮时,异步把旧对话压缩成 ≤200 字摘要
  │     → 作为 system 消息拼在最前,替代被压缩掉的原文
  │     （Redisson 锁防并发,仅 ASSISTANT 到达时触发）
  │
  └─ 语义(长期): 跨会话记忆
        事实(用户健康/偏好) + 情节(历史咨询) + 画像
        → 向量检索问题相关记忆 → "## 关于该用户" 片段注入 system
```

关键点:
- **短时 + 摘要 = 会话内**;语义 = **跨会话**(记住用户是谁)
- 摘要触发用分布式锁防并发;仅 ASSISTANT 消息到达时压缩
- 歧义时系统主动反问,下一轮用户回答后重新走完整管线

---

## 2. 核心流程详解

### 2.1 查询改写（MultiQuestionRewriteService）

- 快模型调用（`ai.chat.fast-model` = qwen-plus-2025-07-28）,temperature=0.1,topP=0.3,thinking=false
- 输出: 改写后的单条查询 + 子问题列表
- 质量兜底: `DecompositionQualityService` 规则评估子问题质量,<0.3 回退原问题
- 场景提示词: `PromptScenes.QUERY_REWRITE`（可在管理后台覆盖）

### 2.2 意图分类（DefaultIntentClassifier）

- 从 Redis 缓存加载意图树,缺失时从 `t_intent_node` 重建
- 叶子节点扁平化 → 拼为 `{intent_list}` 槽位 → 快模型打分
- 输出: `[{"id":"...","score":0.9,"reason":"..."}]`,支持 `{"results":[...]}` 容错
- 过滤: `topKAboveThreshold(topN=3, minScore=0.35)`（RAGConstant 常量）

### 2.3 多通道混合检索（MultiChannelRetrievalEngine）

```
并行执行:
  VectorGlobalSearchChannel     → 全局向量检索（topKMultiplier=3, confidence≥0.6）
  IntentDirectedSearchChannel   → 仅检索命中意图关联的知识库（minIntentScore=0.4, topKMultiplier=2）
后处理:
  DeduplicationPostProcessor → RerankPostProcessor
```

- 证据截断: `EvidenceBudgetService` 控制 token 预算
- MCP 意图: 走 `LLMMcpParameterExtractor` 提取参数 → 调用 MCP 工具取实时数据（受 `ToolSwitchStore` 开关控制）

### 2.4 模型路由与健康检查（infra-ai）

**三状态断路器**（`ModelHealthStore`）:

```
CLOSED → (连续失败≥2) → OPEN → (30s 到期) → HALF_OPEN → (成功) → CLOSED
                                          ↘ (失败) → OPEN
```

- CAS 实现: `ConcurrentHashMap.compute()` 原子状态转换;HALF_OPEN 单探针信号量
- **故障转移**（`ModelRoutingExecutor`）: 按 priority 遍历候选 → allowCall 健康检查 → 调用 → 成功 markSuccess / 失败 markFailure → 下一个候选 → 全败抛 RemoteException

**快慢模型分层**:

| 用途 | 模型 | 说明 |
|---|---|---|
| 快模型（改写/意图等工具性调用） | qwen-plus-2025-07-28 | 低延迟 |
| 主力问答 | qwen3.7-plus | 强推理 |
| 深度思考 | qwen3.7-plus | supports-thinking |

> 性能优化成果: 首包延迟从 ~100s 降至 4-5s（快慢分层 + enable_thinking 显式控制 + embedding 批次 10）。

### 2.5 分布式公平队列限流（FairDistributedRateLimiter）

基于 Redisson 的五层协同:

| 组件 | Redis Key | 作用 |
|---|---|---|
| PermitExpirableSemaphore | `{name}:semaphore` | 并发许可 |
| ZSET 队列 | `{name}:queue` | 公平排队（score=排号） |
| AtomicLong | `{name}:queue:seq` | 排号生成 |
| RBucket entry marker | `{name}:entry:{requestId}` | 请求存活标记（TTL,防僵尸） |
| RTopic Pub/Sub | `{name}:queue:notify` | permit 释放广播 |

核心: Lua 原子抢占队头 + Ticket 状态机（PENDING→GRANTED/TIMED_OUT/CANCELLED,CAS 互斥）+ PollNotifier 通知合并。

### 2.6 对话记忆（三级记忆）

| 层级 | 实现 | 机制 |
|---|---|---|
| 短时 | `JdbcConversationMemoryStore` | 最近 N 轮原文（history-keep-turns=4） |
| 摘要 | `JdbcConversationMemorySummaryService` | ≥5 轮触发异步压缩（Redisson 锁防并发）,≤200 字符 |
| 语义 | Facts + Episodes | 对话后异步抽取 → 向量化 → 检索注入 |

**语义记忆流水线**（`MemoryExtractionOrchestrator`）:

```
对话完成
  → FactExtractionService   （原子事实,type: health/behavior/preference/goal,置信度>0.5,SHA-256 去重,最多5条）
  → EpisodeExtractionService（情节摘要,title≤15字,topics 2-5）
  → 向量化入库（t_user_fact_vector / t_user_episode_vector,1024 维）
  → FactMergingService      （同类型≥10 条 → LLM 合并去重）
  → ProfileGenerationService（事实≥20 条 → 用户画像）
  → FollowUpPlanService     （LLM 判断是否值得随访）
```

**检索注入**: 问题向量检索事实（TopK=10,minSimilarity=0.3）+ 情节（TopK=5）→ `MemoryInjector` 格式化为 `## 关于该用户` + `## 相关历史咨询` 片段 → 拼入 system 消息。

### 2.7 文档摄取（IngestionEngine）

**链式 DAG**（非并行）: 起始节点沿 `nextNodeId` 单链执行,含环检测。

```
Fetcher → Parser → Enhancer → Chunker → Enricher → Indexer
```

| 节点 | 职责 |
|---|---|
| FetcherNode | 多数据源获取（LocalFile/S3/HTTP/Feishu） |
| ParserNode | Tika 解析（PDF/Word/HTML 等） |
| EnhancerNode | AI 增强整篇（摘要/关键词） |
| ChunkerNode | 分块（策略模式） |
| EnricherNode | AI 增强每块 |
| IndexerNode | 向量化入 pgvector |

**条件评估**（ConditionEvaluator）: SpEL 表达式 / 规则对象 / 逻辑组合（all/any/not）,节点可条件跳过。

### 2.8 分块策略（ChunkingStrategy）

| 策略 | value | 说明 |
|---|---|---|
| 固定大小 | fixed_size | chunkSize/overlapSize/separator |
| 语义感知 | structure_aware | Markdown 友好,按结构边界 |
| 父子切块 | parent_child | 父块上下文 + 子块检索 |
| QA 切块 | qa | LLM 提取问答对（自定义提示词） |

### 2.9 报告解析（ReportParseServiceImpl）

```
上传（图片/PDF,≤10MB）→ 存储 → 文本提取（分流）→ LLM 结构化 → 入库
```

文本提取分流:
- PDF: Tika 提取文本层,字符数 <50 判定为扫描件 → VL 模型
- 图片: 直接 VL 模型（qwen3-vl-30b-a3b-thinking）
- 其他: Tika 兜底

结构化: 场景提示词 `REPORT_EXTRACT`,失败降级保留原文（structured=null）。

### 2.10 主动随访（FollowUp）

```
对话完成 → FollowUpPlanService（LLM: 是否值得随访?延迟天数/问题/主题）
  → t_followup_task 入库（PENDING）
  → FollowUpScheduleJob 定时扫描（@Scheduled,60s）
      → CAS 抢锁（UPDATE lock_until WHERE status=PENDING AND lock_until<now()）
      → 静默时段顺延（22:00-08:00）
      → NotificationDispatcher → EmailNotificationChannel（JavaMailSender）
      → 置 SENT
```

- 邮件内容: HTML,通用提醒 + 深链 + 退订链接,**绝不包含病情细节**（隐私红线）
- 用户未绑定/未验证邮箱则静默跳过
- Agent 工具联动: `FollowUpReminderTool`（create_followup_reminder,同主题 7 天去重）

---

## 3. 关键约定

- **主键**: VARCHAR(20) 分布式 ID（`framework` 的 distributedid 包,雪花算法）
- **ORM**: MyBatis-Plus,DO 实体 + Mapper,逻辑删除字段 `deleted`
- **返回**: `Result<T>` 包装（`framework` 的 convention 包）;错误码 `A`(参数)/`B`(服务) 两级
- **流式**: SseEmitter,聊天端点 `GET /rag/v3/chat`(SSE),`POST /rag/v3/stop`
- **认证**: Sa-Token,token 名 `Authorization`,30 天;`@RequireAdmin` 切面校验 admin 角色;`UserContextInterceptor` 注入用户上下文(TTL)
- **Prompt 模板**: `bootstrap/src/main/resources/prompt/*.st`,经 `PromptTemplateLoader` 加载,`{slot}` 占位符
- **定时任务模式**: `@Scheduled` 扫描 + 表内 `lock_until` 字段 CAS 抢锁（参考 FollowUpScheduleJob / KnowledgeDocumentScheduleJob）
- **构建**: `./mvnw install` 解决模块依赖;`./mvnw spring-boot:run -pl bootstrap` 启动
- **提交信息**: 中文,`feat: / fix: / docs:` 前缀
- **前端 API 封装**: `frontend/src/api/baymd.ts` 统一 fetch 封装

---

## 4. 模块详解

### 4.1 framework（基础设施）

| 组件 | 说明 |
|---|---|
| `convention` | Result / ChatRequest / ChatMessage 通用模型 |
| `auth` | RequireAdmin 注解 + AOP 切面 |
| `context` | UserContext（TTL 线程上下文） |
| `errorcode` | BaseErrorCode（A000001/B000001 等） |
| `web` | GlobalExceptionHandler / Results |
| `trace` | RagTraceNode 链路追踪注解 |

### 4.2 infra-ai（AI 基础设施）

| 组件 | 说明 |
|---|---|
| `chat` | ChatClient 接口 / AbstractOpenAIStyleChatClient（模板方法）/ BaiLianChatClient / RoutingLLMService |
| `embedding` | EmbeddingService / RoutingEmbeddingService / AbstractOpenAIStyleEmbeddingClient / BaiLianEmbeddingClient（批次≤10） |
| `rerank` | RerankService（MaaS 无 /reranks 时降级 noop,仅 RRF 融合） |
| `vision` | VisionService（VL 模型,报告图片解析） |
| `model` | ModelHealthStore（断路器）/ ModelRoutingExecutor（故障转移） |
| `config` | AIModelProperties（providers/candidates/fast-model） |

### 4.3 bootstrap（主应用）

业务包:`rag` / `ingestion` / `knowledge` / `user` / `admin` / `core`(chunk)。

### 4.4 mcp-server（MCP 服务端）

- 独立 Spring Boot 应用,端口 9099,暴露 HTTP MCP 端点（`/mcp`）
- `ExampleMcpExecutor` 为示例工具;新增工具:新建 `@Component` + `SyncToolSpecification` Bean
- 与 bootstrap 形成 Client-Server 架构

---

## 5. MCP 工具生态

### 5.1 架构

```
管理后台「工具」tab
  ├── 本地 Agent 工具（5个,Spring Bean,ReAct 循环可用）
  │     search_knowledge_base / drug_interaction / create_followup_reminder
  │     medical_calculator / query_report_indicator
  ├── MCP 远程工具（McpToolRegistry,意图定向检索可用）
  └── MCP Server 管理（McpClientManager,运行时动态增删）
```

### 5.2 McpClientManager（运行时管理）

- 启动: `afterSingletonsInstantiated` 合并 yaml + DB(`t_app_config.mcp`)servers 统一连接
- `addServer(name,url,apiKey)` / `removeServer(name)` / `testConnection(...)` / `status()`
- 传输: URL 含 `/sse` 用 `HttpClientSseClientTransport`,否则 `HttpClientStreamableHttpTransport`
- 请求头: `Accept: application/json, text/event-stream`(强制) + `Authorization: Bearer <apiKey>`(可选)
- 工具开关: `ToolSwitchStore` 统一过滤本地 + MCP 工具,`isEnabled()` 空集合放行全部,即时生效

### 5.3 连接外部 MCP 要点（实测经验）

| 服务 | 端点 | 认证方式 |
|---|---|---|
| 高德地图 | `https://mcp.amap.com/sse?key=xxx` | URL query `key=`,**API Key 字段留空** |
| Ahrefs | `https://api.ahrefs.com/mcp/mcp` | `Authorization: Bearer`(填 API Key 字段) |
| 自建 mcp-server | `http://host:9099/mcp` | 无 |

> 高德 `/mcp`(Streamable)端点不支持 GET-SSE 建流(返回 405/`http+sse is not supported`),必须用 `/sse` 端点;SDK 的 `builder(url)` 会丢失 query,须用 origin + sseEndpoint 拆分。

---

## 6. 数据库

### 6.1 SQL 脚本

| 文件 | 用途 |
|---|---|
| `schema_pg.sql` | 主建表(28 表,含 pgvector 扩展,幂等) |
| `init_feature_tables.sql` | 功能表增量(t_medical_report/followup/fact/episode/drug_interaction/t_app_config) |
| `init_drug_interaction.sql` | 55+ 对药物相互作用种子数据 |
| `init_medical_kb.sql` | 医学知识库 + 5 个示例问题 |

### 6.2 表清单（28 表）

**用户与会话**: t_user / t_conversation / t_conversation_summary / t_message / t_message_feedback / t_sample_question

**知识库**: t_knowledge_base / t_knowledge_document / t_knowledge_chunk / t_knowledge_document_chunk_log / t_knowledge_document_schedule / t_knowledge_document_schedule_exec / t_knowledge_vector（pgvector,HNSW）

**意图与检索**: t_intent_node / t_query_term_mapping

**摄取**: t_ingestion_pipeline / t_ingestion_pipeline_node / t_ingestion_task / t_ingestion_task_node

**追踪**: t_rag_trace_run / t_rag_trace_node

**记忆**: t_user_fact / t_user_fact_vector / t_user_episode / t_user_episode_vector

**功能**: t_drug_interaction / t_medical_report（structured JSONB）/ t_followup_task / t_app_config（运行时配置分区）

> ⚠️ 向量维度统一 **1024**(text-embedding-v4);schema_pg.sql 内旧定义为 1536,新部署须改用 1024 修正版。

---

## 7. 配置要点（application.yaml）

| 配置节 | 关键项 | 默认值 |
|---|---|---|
| `rag.vector.type` | 向量存储 | pg |
| `rag.default.dimension` | 向量维度 | **1024**（与 embedding 匹配） |
| `rag.rate-limit.global` | 全局限流 | enabled/max-concurrent=50/max-wait-seconds=20 |
| `rag.memory` | 记忆 | history-keep-turns=4 / summary-start-turns=5 / summary-max-chars=200 |
| `rag.react` | Agent | enabled=true / max-iterations=5 |
| `rag.mcp.servers` | MCP 内置 | default: http://localhost:9099 |
| `rag.followup` | 随访 | enabled=false / quiet-hours="22:00-08:00" / expire-days=3 |
| `rag.search.channels` | 检索通道 | vector-global(0.6) / intent-directed(0.4) |
| `ai.chat` | 模型 | default=qwen3.7-plus / fast=qwen-plus-2025-07-28 |
| `ai.embedding` | Embedding | text-embedding-v4 |
| `ai.vision` | VL 模型 | qwen3-vl-30b-a3b-thinking |
| `sa-token` | 鉴权 | token-name=Authorization / timeout=30天 |

**运行时配置（DB 覆盖,重启生效）**: `t_app_config` 分区 `ai` / `rag` / `prompt` / `skill` / `mcp`,管理后台「系统设置」编辑。

---

## 8. 前端页面

前端是双端架构:用户端(`/`,聊天问答)+ 管理后台(`/admin`,独立页面)。按角色隔离,普通用户只看到用户端,管理员可切换进入后台。

### 8.1 路由（App.tsx）

```
/          → 用户端: Sidebar(会话列表) + ChatView(对话)
/admin     → 管理后台: AdminPage(6 个 Tab),非 admin 角色自动跳回 /
```

顶部导航: 用户端右上角有「管理后台」入口(admin 角色可见),后台有「返回用户端」按钮。

---

### 8.2 用户端页面

#### 8.2.1 会话侧边栏（Sidebar）

- 会话列表: 展示历史会话标题(自动生成)+ 最后消息时间
- 新建对话 / 删除对话
- 切换会话时加载对应消息历史

#### 8.2.2 对话主界面（ChatView）

| 交互 | 说明 |
|---|---|
| SSE 流式聊天 | 打字机效果,实时渲染回答 |
| 思维链展示 | Agent/深度思考模式下展示思考过程(可折叠) |
| 报告上传 | 选择图片/PDF → 上传解析 → 后续回答自动携带报告上下文 |
| 推荐追问 | 回答完成后显示最多 3 个追问按钮,点击直接发起 |
| 点赞/踩 | 每条回答可反馈,计入质量评估 |
| 引用渲染 | 回答中的证据引用可点击,查看来源知识库分块内容 |
| 紧急提示 | 命中紧急分诊时醒目展示就医警告与心理援助热线 |

---

### 8.3 管理后台页面（AdminPage,6 个 Tab）

```
运营 | 知识库 | 用户记忆 | 提示词 | 工具 | 系统设置
```

#### 8.3.1 运营（OpsView）

- KPI 卡片: 在线用户(5 分钟活跃)/ 总用户 / 总会话 / 总消息 / 估算 Token / 今日消息 / 今日 Token
- 趋势图: 按小时统计消息数与字符数(默认 24h,可调至 168h),30s 自动刷新
- 数据来源: `GET /admin/ops/overview` + `GET /admin/ops/trends`

#### 8.3.2 知识库（KnowledgeView）

- 知识库列表: 名称/描述/文档数,入口「查看文档」「上传文档」
- 文档管理: 列表(状态/分块数)→ 上传(md/pdf/doc/docx/txt/html)→ 查看分块内容 → 删除
- **分块设置(Dify 式)**: 四种策略(fixed_size / structure_aware / parent_child / qa),每个策略独立参数表单
- **分块预览**: 选择文件后先预览前 10 块(含策略参数实时生效)→ 确认后才真正上传
- QA 策略支持自定义提取提示词(占位符 `{doc}`)

#### 8.3.3 用户记忆（MemoryView）

- 用户列表: 有记忆的用户(含事实数/情节数)
- 事实明细: type(health/behavior/preference/goal)/ 内容 / 置信度,支持删除(级联删向量)
- 情节明细: 标题 / 摘要 / 标签,支持删除
- 数据来源: `GET /admin/memory/users` `/facts` `/episodes` + DELETE

#### 8.3.4 提示词（PromptView）

- 左侧: 18 个 LLM 调用场景列表(查询改写/意图分类/RAG回答/系统直答/Agent/记忆抽取/随访/评估等)
- 右侧: 每个场景的 **system 提示词 + user 提示词** 两个文本框,默认预填当前内置模板
- **超参数**: 温度 / 最大 token / topP 输入框,默认预填内置值,留空用内置
- 占位符提示: 每个场景标注可用槽位(如 `{question}` `{evidence}` `{intent_list}`)
- 「恢复默认」一键还原;保存只写与默认不同的场景
- 数据来源: `GET /admin/config/prompt-scenes` + `PUT /admin/config/prompt`(DB 覆盖,重启生效)

#### 8.3.5 工具（ToolsView）

- **本地 Agent 工具区**: 5 个内置工具(知识检索/计算器/药物相互作用/随访提醒/报告查询),每行开关
- **MCP 远程工具区**: 来自 MCP Server 的工具,同样可开关
- **MCP Server 管理**: 添加 Server(名称 + URL + 可选 API Key)、测试连接(显示发现的工具列表)、删除
- 开关即时生效(热生效,无需重启);保存持久化到 DB
- 数据来源: `GET/PUT /admin/tools` + `GET/POST/DELETE /admin/mcp/servers`

#### 8.3.6 系统设置（SettingsView）

- **AI 配置表单**(AiConfigForm): 供应商 / 模型候选 / 默认模型,保存到 DB(`t_app_config.ai`),重启生效
- **RAG 配置表单**(RagConfigForm): 查询改写 / 记忆 / 检索通道 / ReAct 超参,保存到 DB(`t_app_config.rag`)
- **链路追踪**: Trace 运行列表 → 点击查看**瀑布图**(节点耗时/状态/父子层级)
- **隐私**: 一键清空所有用户记忆
- 数据来源: `GET/PUT /admin/config/{section}` + `GET /rag/traces/runs`

---

### 8.4 角色与权限

| 角色 | 能力 |
|---|---|
| user | 用户端全部(对话/上传报告/反馈/绑定邮箱接收随访) |
| admin | 用户端 + 管理后台全部(11 个 controller 标注 `@RequireAdmin` 切面校验) |

> 管理后台所有接口均需 admin 角色,`RequireAdminAspect` AOP 拦截;`UserContextInterceptor` 从 Sa-Token 会话注入用户上下文(TTL)。
