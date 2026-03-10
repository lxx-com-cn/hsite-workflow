## 生成workflow：AI编程的深度应用  

本次开发完全基于AI进行：所有代码、测试案例以及XML Schema均由AI生成，绝大部分代码来自DeepSeek和Kimi。这类似于“氛围编程”（vibe coding），但不同之处在于：提示词完全由手工编写，全程主控，并根据反馈情况实时调整提问方式、整理上下文，最终将多轮交互汇聚成完整的代码库。我在想，要实现真正的AI编程，必须躬身入局，学会在128k的上下文中编写有效的提示词，保存多轮交流中的关键信息，构造长期有效的“记忆体”，并时刻驱动测试代码来验证所输出的代码的正确性，并确定后续的处理。

这次AI编程不再是单向的指令输出，而是一场人机协作的深度对话。把自己当作创作者，各个大模型当作牛马，思想当作画布，在浩瀚的思绪中有效操控受限的上下文空间，提炼思想的精髓，以测试案例为栅栏，并由AI反复淬炼成最终的成果，让AI成为创造力的无限延伸。

---

## 🚀 1 分钟速览

#### **1、系统定位**

在构建一个区域级、私有化部署的健康大平台，以“聊天系统 + AI Chatbot + AI agent + 业务工作流 + 医疗区域数仓”为核心，向下聚合全域医疗健康数据，向上承载医保、医疗、医药、健康管理服务，构建多方共赢的“医疗健康超级入口”和“数字新基建”。在这套体系从上往下包括了业务表单处理，业务API调用，数据流的处理，以及AI业务处理，可以归纳有4层能用到工作流：

1. 表单层：基于业务表单的多人流转业务流程（传统BPM范畴）；
2. API层：基于REST及Spring Bean的API编排业务流程（本系统的范畴）；
3. 数据层：围绕数据处理的业务流程，传统是ETL的范畴；
4. 智体层：基于AI Agent的动态工作流，由其他系统支撑

之所以将API编排和数据流处理作为工作流的重点，源于20年前在移动BOSS 1.5时代的开发体验。当时就采用API编排技术，将不同API连接成整体解决方案，每个API对应一段SQL或简单业务功能，通过MAP做数据扁平化传输，借助Eclipse RCP建模器实现图形化拖拽设计，编译部署后发布为一个功能号，前端应用只需调用该功能号即可完成业务。这种模式极大提高了开发效率，能在2-3个月内完成一个省级BOSS系统。

这段历史让我深刻体会到，API编排能最大化复用已有业务沉淀，快速响应新需求，这正是当前workflow设计的核心理念。

#### **2、总体介绍**

BFM引擎是一个基于Spring Boot 2.7 + JDK8 + MyBatis-Flex + MySQL 5.7构建的服务编排框架，专为BFF层API聚合设计（当前只有后台，springboot形态，还未设计web界面）。它借鉴了XPDL1.0的思想，参考了BPMN 2.0理念，通过XML定义业务流程，支持丰富的活动类型（自动任务、人工任务、子流程、网关）和灵活的数据映射，以状态机驱动执行，实现了从简单同步调用到复杂异步编排的全覆盖。

1. 三层状态机联动：确保流程执行正确性，状态转换可追溯。
2. 四种网关：提供排他、并行、包容、复杂分支汇聚能力，实现流程灵活控制。
3. 五种子流程模式：覆盖同步、异步、事务、DAG编排、批量处理，满足多样化需求。
4. 异步持久化：Redis缓冲+批量刷盘，提升写性能，降低数据库压力。
5. 细粒度锁：保障高并发下的状态安全。
6. 表达式与数据映射：灵活处理变量，支持复杂数据转换。

#### **3、模块划分**

1. com.hbs.site.module.bfm

   ├── config     # 配置类（持久化、线程池、RestTemplate）

   ├── controller   # REST API接口（运维、监控）

   ├── dal       # 数据访问层（实体、Mapper、Service）

   │  ├── entity   # 数据库实体（MyBatis-Flex注解）

   │  ├── mapper   # MyBatis-Flex Mapper接口

   │  └── service   # 业务服务接口与实现

   ├── data       # 数据定义与运行时

   │  ├── define   # XML Schema对应的POJO定义

   │  └── runtime   # 运行时实例（流程、活动、工作项）

   ├── engine     # 核心引擎

   │  ├── expression # SpEL表达式求值

   │  ├── gateway   # 网关执行器（排他/并行/包容/复杂）

   │  ├── invoker   # 服务调用分发器（REST/Bean/JavaBean/Message）

   │  ├── mapping   # 数据映射处理器（输入/输出）

   │  ├── persist   # 持久化服务（Redis缓冲+批量写入）

   │  ├── state    # 状态机管理（活动/流程/工作项状态）

   │  ├── subprocess # 子流程执行器（SYNC/ASYNC/TX/FUTURE/FORKJOIN）

   │  ├── transition # 转移线评估器

   │  └── usertask  # 人工任务执行器

   ├── listener    # 事件监听器

   ├── parser     # XML解析器

   └── utils      # 工具类（ID生成、对象转换）

---

## 📦 模块说明



#### 1. config模块：配置管理

功能：提供引擎运行所需的各种配置 Bean。

业务逻辑：

1. BfmPersistenceProperties：持久化相关配置（是否启用Redis缓冲、队列前缀、批量大小、间隔、线程数等）。
2. RestTemplateConfig：创建 RestTemplate 和 ObjectMapper（注册 JavaTimeModule，支持 Java8 时间类型）。
3. SubProcessThreadPoolConfig：为子流程异步执行提供两个线程池（普通异步线程池和 ForkJoin 工作窃取线程池）。

 

#### 2. controller模块：对外接口

功能：提供 REST API 用于监控流程状态和执行运维操作。

业务逻辑：

1. BfmPerformanceMonitorController：查看队列积压情况、消费统计、强制刷新队列、健康检查。
2. ProcessOpsController：查询可恢复流程、恢复流程执行、获取历史、强制终止流程（部分方法为示例骨架）。

 

#### 3. dal模块：数据访问层

功能：定义数据库实体、MyBatis-Flex Mapper 和业务 Service。

- 实体（Entity）：

1. BaseEntity：抽象基类，包含通用字段（创建时间、更新时间、逻辑删除）。
2. BfmProcessInstance：流程实例表（雪花ID主键，存储流程变量、上下文快照等）。
3. BfmActivityInstance：活动实例表（存储活动运行时数据，输入/输出/本地变量）。
4. BfmExecutionHistory：执行历史表（记录流程执行过程中的事件）。
5. BfmWorkItem：工作项表（人工任务实例，包含表单数据、处理人、状态、会签信息等）。
6. BfmPackage：流程包定义表（存储 XML 内容，版本管理）。
7. BfmProcessPausePoint：流程暂停点表（用于恢复执行）。

 

- Mapper：为每个实体提供基础 CRUD 和自定义查询方法（如根据流程实例ID查询活动、查询可恢复流程等）。

 

- Service：

1. IBfmPackageService / BfmPackageServiceImpl：流程包部署、获取、禁用。
2. IProcessInstancePersistenceService / HighPerformanceProcessInstancePersistenceService：核心持久化服务，实现了 Redis 缓冲队列的高性能写入，所有写操作先入队，由 BatchPersistenceConsumerService 批量刷库；同时提供同步降级方法。

 

#### 4. data模块:数据模型（定义与运行时）

功能：承载流程定义解析后的对象和流程运行时的内存对象。

- 子包 define：与 XML Schema 对应的 Java 类（使用 Jackson XML 注解）。

1. Package：根元素，包含 Messages、Workflow 列表、Dependencies、GlobalConfig。
2. Workflow：工作流定义，包含 Parameters、Activities（活动列表）、Transitions（转移线）、调试监控配置。
3. 活动抽象类 Activity，及其具体子类：StartEvent、EndEvent、AutoTask、UserTask、SubProcess、Gateway。
4. 其他辅助类：DataMapping（输入/输出映射）、Assignment（任务分配）、CompletionRule（完成规则）、ExtendedOperation（扩展操作）、FaultHandler（异常处理）等。

 

- 子包 runtime：流程运行时的内存对象。

1. ProcessInstance：流程实例（内存ID + 数据库雪花ID），包含状态、变量、活动实例映射、工作项映射、退回栈、执行历史等。提供 start()、onActivityCompleted()、terminate() 等方法。
2. ActivityInstance：活动实例，包含活动定义引用、状态、输入/输出数据、本地变量、关联的工作项列表。提供 execute()、checkWorkItemsCompletion() 等方法。
3. WorkItemInstance：工作项实例，包含处理人、状态、表单数据、操作历史等。提供 start()、complete()、transfer()、delegate()、back() 等方法。
4. ExecutionContext：执行上下文，封装变量作用域（LOCAL/WORKFLOW/PACKAGE）和调用栈。
5. RuntimePackage：运行时包（不可变），包含所有 RuntimeWorkflow 和包级变量。
6. RuntimeWorkflow：运行时工作流，缓存活动定义、转移矩阵、参数定义等。
7. VariableScope：变量作用域管理器。

 

#### 5. engine模块：核心引擎

这是系统的中枢，按职责分为多个子包：

l engine.expression：表达式求值

ExpressionEvaluator：核心类，使用 Spring SpEL 求值，支持 ${...} 和 #{...} 包裹，处理赋值表达式、集合字面量、类实例化、嵌套属性等。将所有流程变量注册到 SpEL 上下文中，支持复杂表达式。

 

- engine.gateway：网关执行器

GatewayExecutor 接口：定义 execute(gatewayDef, gatewayInstance)。

具体实现：

1. ExclusiveGatewayExecutor：排他网关，SPLIT 时选择唯一满足条件的分支（优先 default），JOIN 时直接透传。
2. ParallelGatewayExecutor：并行网关，SPLIT 时启动所有分支，JOIN 时等待所有分支到达（计数器）。
3. InclusiveGatewayExecutor：包容网关，SPLIT 时启动所有满足条件的分支（若无则走 default），JOIN 时等待所有实际激活的分支。
4. ComplexGatewayExecutor：复杂网关（目前降级为排他网关处理）。
5. GatewayExecutorFactory：根据网关类型返回对应执行器。

 

- engine.invoker：调用分发器

InvokerDispatcher：负责调用 Spring Bean、REST、WebService、JavaBean、消息等外部服务。核心方法 invokeSpringBean 通过反射查找最佳匹配方法，并处理参数类型转换（支持 varargs、VO 对象直接传递等）。

 

- engine.mapping：数据映射处理器

1. DataMappingInputProcessor：处理输入映射，根据 DataMapping.InputMapping 从上下文获取值，支持嵌套目标（自动创建父 Map），处理类型转换（Set/List/Bean）。
2. DataMappingOutputProcessor：处理输出映射，将求值结果写入指定作用域，支持嵌套 target。

 

- engine.persist：异步持久化

1. RedisPersistenceQueueService：封装 Redis 列表作为队列，提供入队（LPUSH）和批量出队（使用 Redis 事务实现原子性）方法。
2. BatchPersistenceConsumerService：定时批量消费四个队列（流程实例、活动实例、执行历史、工作项），将消息反序列化为实体，执行插入/更新（幂等处理主键冲突）。
3. PersistenceEventListener：监听状态变更事件，异步调用持久化服务（实际调用 HighPerformanceProcessInstancePersistenceService 的对应方法）。

 

- l engine.state：状态管理

状态枚举：

1. ProcStatus：流程状态（CREATED, RUNNING, SUSPENDED, COMPLETED, TERMINATED, CANCELED）。ActStatus：活动状态（CREATED, RUNNING, SUSPENDED, COMPLETED, SKIPPED, TERMINATED, CANCELED）。WorkStatus：工作项状态（CREATED, RUNNING, COMPLETED, TERMINATED, CANCELED）。
2. 状态转换规则：每个枚举类内定义了 TRANSITION_RULES 静态映射，通过 canTransitionTo() 校验。
3. 事件类：ProcessStatusChangedEvent、ActivityStatusChangedEvent、WorkItemStatusChangedEvent，继承自 Spring ApplicationEvent。
4. StatusTransitionManager：核心状态管理器，使用细粒度锁（ConcurrentHashMap + ReentrantLock）保证每个实例的状态转换线程安全。提供 transition() 和 forceTransition() 方法，转换成功后会发布事件，并触发下游驱动（通过 ProcessInstanceExecutor）、级联终止等逻辑。同时实现了 SmartLifecycle，支持优雅关闭。

 

- engine.subprocess：子流程执行器

SubProcessExecutor 接口：定义 execute(subProcess, activityInstance)。

五种执行模式：

1. SyncSubProcessExecutor：同步阻塞，等待子流程完成。
2. AsyncSubProcessExecutor：异步执行，使用 CompletableFuture，主线程不阻塞，但等待结果时使用 future.get(timeout)（同步等待异步结果），实现了“异步执行 + 同步等待结果”的模式。
3. TxSubProcessExecutor：在 @Transactional(propagation = REQUIRES_NEW) 中执行子流程，确保事务独立。
4. FutureSubProcessExecutor：DAG 并行执行，根据子流程定义构建 DAG，使用 CompletableFuture 实现并行，支持嵌套 Future（同步）。
5. ForkJoinSubProcessExecutor：使用 ForkJoinPool 对批量输入数据进行分治并行处理，每个分片启动一个子流程。
6. SubProcessExecutorFactory：根据 ExecutionStrategy 的 mode 返回对应执行器。
7. SubProcessStarter 接口：由 ServiceOrchestrationEngine 实现，用于启动子流程实例，解耦循环依赖。
8. TxModeHolder：ThreadLocal 标记当前是否处于 TX 模式，用于禁用重试等。

 

- engine.transition：转移线评估

TransitionEvaluator：根据当前活动 ID 从工作流定义获取出栈转移线，逐一评估条件表达式，按优先级排序，返回可执行的转移线列表（排他网关只取最高优先级，包容网关取所有满足条件的）。

 

- engine.usertask：人工任务执行

1. UserTaskExecutor：根据 UserTask 配置推断任务类型（SINGLE/OR_SIGN/COUNTERSIGN），解析分配人，创建 WorkItemInstance，设置权限、到期时间，并注册到 WorkItemService。
2. WorkItemService：提供工作项的完整业务操作：认领、完成、转办、委托、退回、催办、加签等，并维护内存索引（生产环境应替换为数据库）。核心方法 findWorkItem 先查内存，再遍历流程实例兜底。

 

- engine 顶层类

1. ServiceOrchestrationEngine：引擎入口，负责部署流程包、启动流程实例、执行活动。内部持有上述所有组件，通过 executeActivity 根据活动类型分发给对应执行器（开始/结束事件、自动任务、人工任务、子流程、网关）。同时实现 SubProcessStarter 接口。
2. ProcessInstanceExecutor：流程实例执行驱动器，核心方法是 onActivityCompleted，当活动完成时由状态管理器回调。它使用 TransitionEvaluator 获取下游活动，并通过引擎的 executeActivity启动它们，内部维护活动锁和完成记录防止重复执行。

 

#### 6. listener 模块：事件监听器

StatusChangeListener：异步监听状态变更事件，用于日志、监控、级联等。使用 @Async，并捕获 Spring 关闭时的 TaskRejectedException 避免异常。

 

#### 7. parser 模块：XML 解析器

WorkflowParser：使用 Jackson XML 将 XML 流解析为 Package 对象，并进行基本校验（非空、开始/结束事件等）。支持从 Resource 或路径批量解析。

 

#### 8. utils 模块：工具类

IdGenerator：雪花 ID 生成器（单例），基于 MAC 地址生成 workerId。

ObjectConverter：Map 到 Bean 的转换工具，依次尝试 Jackson、BeanUtils、反射。

 

 