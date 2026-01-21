# Mock API Server - 开发文档

## 目录
1. [概述](#概述)
2. [架构设计](#架构设计)
3. [核心组件](#核心组件)
4. [数据流](#数据流)
5. [API 参考](#api-参考)
6. [扩展指南](#扩展指南)
7. [测试指南](#测试指南)
8. [常见问题](#常见问题)

## 概述

Mock API Server 是 Quick Note 插件的网络接口模拟功能模块,允许开发者在服务端接口未准备好时,通过配置模拟接口数据,让局域网内的设备可以访问这些模拟接口。

### 技术栈
- **开发语言**: Kotlin 100%
- **HTTP 服务器**: JDK HttpServer (com.sun.net.httpserver)
- **JSON 处理**: kotlinx.serialization 1.6.2
- **UI 框架**: IntelliJ Platform SDK (Swing)
- **并发**: Kotlin Coroutines
- **JVM 版本**: 17

### 主要特性
- ✅ RESTful API 支持 (GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD)
- ✅ JSON 格式响应
- ✅ 可配置响应延迟
- ✅ 启用/禁用端点
- ✅ 图片资源服务
- ✅ CORS 跨域支持
- ✅ 请求日志记录
- ✅ 局域网地址显示
- ✅ Postman 风格 UI

## 架构设计

### 分层架构

Mock API Server 遵循清晰的分层架构模式:

```
┌─────────────────────────────────────────┐
│         UI Layer (Swing)                │
│  - MockApiToolWindowContent             │
│  - AddEditEndpointDialog                │
│  - MockApiSettingsDialog                │
│  - NetworkAddressDialog                 │
│  - EndpointListCellRenderer             │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Service Layer                   │
│  - MockApiService (Project级)           │
│  - MockApiChangeListener (事件总线)     │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Repository Layer                   │
│  - MockApiRepository (Application级)    │
│    • JSON 序列化/反序列化               │
│    • 文件系统存储管理                   │
│    • 配置管理                           │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│       HTTP Server Layer                 │
│  - EmbeddedHttpServer (Application级)   │
│    • HttpServer 生命周期管理            │
│    • 路由配置                           │
│    • 请求处理                           │
│    • CORS 支持                          │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Persistence Layer               │
│  - JSON Files (~/.quicknote/)           │
│    • endpoints.json                     │
│    • config.json                        │
│    • images/                            │
└─────────────────────────────────────────┘
```

### 服务生命周期

```
Application 启动
    │
    ├─> MockApiRepository (Application级单例)
    ├─> EmbeddedHttpServer (Application级单例)
    │
Project 打开
    │
    └─> MockApiService (Project级实例)
        └─> 注册为 EmbeddedHttpServer.RequestListener
```

## 核心组件

### 1. 数据模型层

#### MockEndpoint
端点配置的核心数据模型。

```kotlin
@Serializable
data class MockEndpoint(
    val id: String,              // UUID,唯一标识
    val name: String,            // 显示名称
    val path: String,            // API路径,如 "/api/users"
    val method: HttpMethod,      // HTTP 方法枚举
    val statusCode: Int = 200,   // HTTP 状态码
    val responseBody: String,    // JSON 响应体
    val headers: Map<String, String> = emptyMap(),
    val delay: Long = 0,         // 响应延迟(毫秒)
    val enabled: Boolean = true, // 启用/禁用
    val description: String = "",
    val createdAt: Long,
    val modifiedAt: Long,
    val projectName: String
)
```

#### MockApiConfig
服务器配置模型。

```kotlin
@Serializable
data class MockApiConfig(
    val port: Int = 8888,                    // 服务器端口
    val host: String = "0.0.0.0",            // 绑定地址
    val imageStoragePath: String = "",       // 图片存储路径
    val enableCors: Boolean = true,          // CORS 开关
    val enableLogging: Boolean = true        // 日志开关
)
```

#### MockApiRequest
请求日志模型 (运行时,非序列化)。

```kotlin
data class MockApiRequest(
    val id: String,
    val endpointId: String?,               // 匹配的端点ID
    val timestamp: Long,
    val method: String,
    val path: String,
    val queryParams: Map<String, List<String>>,
    val headers: Map<String, List<String>>,
    val body: String?,
    val responseStatus: Int,
    val responseTime: Long                  // 响应时间(毫秒)
)
```

### 2. Repository 层

#### MockApiRepository

**职责**:
- 端点配置的 CRUD 操作
- JSON 序列化/反序列化
- 文件系统存储管理
- 图片路径解析

**关键方法**:

```kotlin
@Service(Service.Level.APP)
class MockApiRepository {
    // 端点操作
    fun saveEndpoint(projectName: String, endpoint: MockEndpoint): Path
    fun updateEndpoint(projectName: String, endpoint: MockEndpoint): Path
    fun deleteEndpoint(projectName: String, endpointId: String): Boolean
    fun findEndpointById(projectName: String, endpointId: String): MockEndpoint?
    fun findAllEndpoints(projectName: String): List<MockEndpoint>
    fun findEndpointByPath(projectName: String, method: HttpMethod, path: String): MockEndpoint?

    // 配置操作
    fun saveConfig(projectName: String, config: MockApiConfig)
    fun loadConfig(projectName: String): MockApiConfig

    // 图片路径操作
    fun getImageStoragePath(projectName: String): Path
    fun resolveImagePath(projectName: String, imagePath: String): Path?
}
```

**存储结构**:

```
~/.quicknote/
└── <ProjectName>/
    ├── notes/              # 笔记存储 (原有功能)
    │   └── *.md
    └── mockapi/            # Mock API 存储
        ├── endpoints.json  # 端点配置
        ├── config.json     # 服务器配置
        └── images/         # 默认图片目录
            └── *.png, *.jpg
```

**endpoints.json 示例**:

```json
[
  {
    "id": "uuid-1",
    "name": "Get Users",
    "path": "/api/users",
    "method": "GET",
    "statusCode": 200,
    "responseBody": "{\"users\": [{\"id\": 1, \"name\": \"John\"}]}",
    "headers": {},
    "delay": 0,
    "enabled": true,
    "description": "Returns list of users",
    "createdAt": 1704518400000,
    "modifiedAt": 1704518400000,
    "projectName": "MyProject"
  }
]
```

### 3. HTTP Server 层

#### EmbeddedHttpServer

**职责**:
- HTTP 服务器生命周期管理 (JDK HttpServer)
- 动态路由配置
- 请求处理和响应
- 图片资源服务
- CORS 支持
- 请求日志记录

**关键方法**:

```kotlin
@Service(Service.Level.APP)
class EmbeddedHttpServer {
    fun start(projectName: String, config: MockApiConfig)
    fun stop()
    fun isRunning(): Boolean
    fun getServerUrl(): String?
    fun getNetworkAddresses(): List<String>

    fun addRequestListener(listener: RequestListener)
    fun removeRequestListener(listener: RequestListener)

    interface RequestListener {
        fun onRequest(request: MockApiRequest)
    }
}
```

**路由设计**:

```kotlin
// 创建 HttpServer 并注册上下文
val server = HttpServer.create(InetSocketAddress(host, port), 0)

// 图片服务路由
server.createContext("/images") { exchange ->
    val filename = exchange.requestURI.path.removePrefix("/images/")
    val imagePath = repository.resolveImagePath(projectName, filename)
    if (imagePath != null && Files.exists(imagePath)) {
        // 写入文件内容
    } else {
        // 404
    }
}

// 通配符路由处理所有请求
server.createContext("/") { exchange ->
    val requestPath = exchange.requestURI.path
    val requestMethod = exchange.requestMethod

    val endpoint = repository.findAllEndpoints(projectName)
        .find { it.enabled && it.path == requestPath && it.method.name == requestMethod }

    if (endpoint != null) {
        // 写入响应头/响应体
    } else {
        // 404
    }
}
```

**CORS 配置**:

```kotlin
if (config.enableCors) {
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
    headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,HEAD,OPTIONS")
    headers.set("Access-Control-Max-Age", "86400")
}
```

#### NetworkUtils

网络辅助工具类。

```kotlin
object NetworkUtils {
    // 获取所有本地网络地址 (IPv4)
    fun getLocalNetworkAddresses(port: Int): List<String>

    // 获取本地 IP 地址
    fun getLocalIpAddress(): String?

    // 获取 localhost 地址
    fun getLocalhostAddress(port: Int): String

    // 检查端口是否可用
    fun isPortAvailable(port: Int): Boolean

    // 查找可用端口
    fun findAvailablePort(startPort: Int, maxAttempts: Int = 10): Int?

    // 获取所有可访问地址
    fun getAllAccessibleAddresses(port: Int): List<String>
}
```

### 4. Service 层

#### MockApiService

**职责**:
- 业务逻辑协调
- 服务器生命周期管理
- 请求历史管理
- 事件发布 (通过 Message Bus)

**关键方法**:

```kotlin
@Service(Service.Level.PROJECT)
class MockApiService(private val project: Project) : EmbeddedHttpServer.RequestListener {

    // 端点管理
    fun createEndpoint(endpoint: MockEndpoint): Result<MockEndpoint>
    fun updateEndpoint(endpoint: MockEndpoint): Result<MockEndpoint>
    fun deleteEndpoint(endpointId: String): Result<Unit>
    fun getEndpoint(endpointId: String): MockEndpoint?
    fun getAllEndpoints(): List<MockEndpoint>
    fun toggleEndpoint(endpointId: String, enabled: Boolean): Result<MockEndpoint>

    // 服务器控制
    fun startServer(): Result<String>
    fun stopServer(): Result<Unit>
    fun restartServer(): Result<String>
    fun isServerRunning(): Boolean
    fun getServerUrl(): String?
    fun getNetworkAddresses(): List<String>

    // 配置管理
    fun updateConfig(config: MockApiConfig): Result<Unit>
    fun getConfig(): MockApiConfig
    fun getImageStoragePath(): String
    fun setImageStoragePath(path: String): Result<Unit>
    fun validateImagePath(path: String): Boolean

    // 请求历史
    fun getRequestHistory(): List<MockApiRequest>
    fun clearRequestHistory()
}
```

#### MockApiChangeListener

事件监听器接口,使用 IntelliJ 的 Message Bus 机制。

```kotlin
interface MockApiChangeListener {
    fun onEndpointCreated(endpoint: MockEndpoint) {}
    fun onEndpointUpdated(endpoint: MockEndpoint) {}
    fun onEndpointDeleted(endpointId: String) {}
    fun onServerStateChanged(running: Boolean, serverUrl: String? = null) {}

    companion object {
        val TOPIC = Topic.create("MockApiChanges", MockApiChangeListener::class.java)
    }
}
```

**使用示例**:

```kotlin
// 发布事件
project.messageBus.syncPublisher(MockApiChangeListener.TOPIC)
    .onEndpointCreated(endpoint)

// 订阅事件
project.messageBus.connect(disposable).subscribe(
    MockApiChangeListener.TOPIC,
    object : MockApiChangeListener {
        override fun onEndpointCreated(endpoint: MockEndpoint) {
            // 处理端点创建事件
        }
    }
)
```

### 5. UI 层

详见 [UI 设计草稿](ui-design.md)

## 数据流

### 1. 创建端点流程

```
用户点击 Add 按钮
    │
    ▼
打开 AddEditEndpointDialog
    │
    ▼
用户填写表单 → 点击 OK
    │
    ▼
验证表单 (doValidate)
    │
    ├─> 验证失败 → 显示错误
    │
    └─> 验证通过
        │
        ▼
    调用 MockApiService.createEndpoint()
        │
        ▼
    MockApiService 生成 UUID 和时间戳
        │
        ▼
    调用 MockApiRepository.saveEndpoint()
        │
        ▼
    序列化为 JSON 并写入 endpoints.json
        │
        ▼
    发布 onEndpointCreated 事件
        │
        ▼
    MockApiToolWindowContent 收到事件
        │
        ▼
    刷新端点列表显示
        │
        ▼
    如果服务器运行中 → 重启服务器应用新端点
```

### 2. 启动服务器流程

```
用户点击 Start Server 按钮
    │
    ▼
调用 MockApiService.startServer()
    │
    ▼
加载配置 (MockApiRepository.loadConfig)
    │
    ▼
检查端口可用性 (NetworkUtils.isPortAvailable)
    │
    ├─> 端口被占用 → 抛出异常 → 显示错误
    │
    └─> 端口可用
        │
        ▼
    调用 EmbeddedHttpServer.start(projectName, config)
        │
        ▼
    创建 JDK HttpServer 实例
        │
        ├─> 配置线程池执行器
        ├─> 注册 /images 路由
        └─> 注册 / 通配符路由 (端点处理 + CORS)
        │
        ▼
    启动服务器 (非阻塞)
        │
        ▼
    发布 onServerStateChanged(true, url) 事件
        │
        ▼
    UI 更新服务器状态显示
        │
        ▼
    显示成功消息
```

### 3. 处理 HTTP 请求流程

```
客户端发送 HTTP 请求
    │
    ▼
HttpServer 接收请求
    │
    ▼
路由匹配
    │
    ├─> /images/{filename} → 图片服务路由
    │   │
    │   ▼
    │   解析文件名 → 查找图片文件
    │   │
    │   ├─> 找到 → 返回文件
    │   └─> 未找到 → 404
    │
    └─> 其他路径 → 端点匹配路由
        │
        ▼
    从 Repository 加载所有端点
        │
        ▼
    查找匹配的端点 (path + method + enabled)
        │
        ├─> 未匹配 → 返回 404
        │
        └─> 匹配成功
            │
            ▼
        应用响应延迟 (如果配置)
            │
            ▼
        设置响应头 (endpoint.headers)
            │
            ▼
        返回响应 (statusCode + responseBody)
            │
            ▼
        记录请求日志 (如果启用)
            │
            ▼
        通知 RequestListener
            │
            ▼
        MockApiService 收到请求
            │
            ▼
        添加到请求历史 (限制 100 条)
            │
            ▼
        UI 定时刷新请求日志表格
```

## API 参考

### MockApiService API

所有方法返回 `Result<T>`,允许函数式错误处理:

```kotlin
service.createEndpoint(endpoint)
    .onSuccess { created ->
        // 处理成功
    }
    .onFailure { error ->
        // 处理错误
        Messages.showErrorDialog("Error: ${error.message}")
    }
```

### 错误处理

#### 常见错误类型

| 错误 | 原因 | 处理方式 |
|------|------|----------|
| `IllegalStateException: Server is already running` | 尝试启动已运行的服务器 | 先停止服务器 |
| `IllegalStateException: Port X is already in use` | 端口被占用 | 修改端口或停止占用进程 |
| `Exception: Endpoint not found` | 删除/更新不存在的端点 | 刷新列表 |
| JSON 解析错误 | 无效的 JSON 响应体 | 验证 JSON 格式 |

## 扩展指南

### 1. 添加新的 HTTP 方法

1. 在 `HttpMethod` 枚举中添加新方法:
```kotlin
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD,
    CONNECT  // 新增
}
```

2. 在 `EndpointListCellRenderer.getMethodColor()` 中添加颜色:
```kotlin
private fun getMethodColor(method: HttpMethod): Color {
    return when (method) {
        // ... 现有方法 ...
        HttpMethod.CONNECT -> JBColor(0xABCDEF, 0xABCDEF)
    }
}
```

### 2. 添加自定义响应头

端点已支持自定义响应头,通过 `headers` 字段配置。

如需添加全局响应头:

```kotlin
// 在 EmbeddedHttpServer.configureEndpointRoutes() 中
endpoint.headers.forEach { (key, value) ->
    call.response.header(key, value)
}

// 添加全局头
call.response.header("X-Mock-API-Version", "1.0")
```

### 3. 实现动态响应

当前响应是静态 JSON。如需动态响应,可以:

1. 添加脚本字段到 `MockEndpoint`:
```kotlin
data class MockEndpoint(
    // ... 现有字段 ...
    val responseScript: String? = null  // JavaScript/Groovy 脚本
)
```

2. 在请求处理时执行脚本:
```kotlin
val response = if (endpoint.responseScript != null) {
    executeScript(endpoint.responseScript, call)
} else {
    endpoint.responseBody
}
call.respondText(response, ...)
```

### 4. 添加请求匹配规则

当前匹配基于精确路径。如需支持路径参数:

```kotlin
// 路径模板: /api/users/{id}
// 请求路径: /api/users/123

fun matchPath(template: String, actualPath: String): Boolean {
    val templateParts = template.split("/")
    val actualParts = actualPath.split("/")

    if (templateParts.size != actualParts.size) return false

    return templateParts.zip(actualParts).all { (t, a) ->
        t == a || t.startsWith("{") && t.endsWith("}")
    }
}
```

### 5. 集成外部 Mock 数据源

可以扩展 Repository 从外部源加载端点:

```kotlin
class MockApiRepository {
    fun importFromSwagger(swaggerJson: String): List<MockEndpoint> {
        // 解析 Swagger/OpenAPI 规范
        // 转换为 MockEndpoint
    }

    fun importFromPostman(postmanCollection: String): List<MockEndpoint> {
        // 解析 Postman Collection
    }
}
```

## 测试指南

### 单元测试

创建测试文件: `src/test/kotlin/com/quicknote/plugin/mockapi/`

#### 测试 Repository

```kotlin
class MockApiRepositoryTest {
    private lateinit var repository: MockApiRepository

    @BeforeEach
    fun setup() {
        repository = MockApiRepository()
    }

    @Test
    fun `should save and load endpoint`() {
        val endpoint = MockEndpoint(
            id = "test-1",
            name = "Test Endpoint",
            path = "/api/test",
            method = HttpMethod.GET,
            statusCode = 200,
            responseBody = """{"result": "ok"}""",
            // ...
        )

        repository.saveEndpoint("TestProject", endpoint)
        val loaded = repository.findEndpointById("TestProject", "test-1")

        assertEquals(endpoint, loaded)
    }
}
```

### 集成测试

使用 HTTP 客户端测试服务器:

```kotlin
class EmbeddedHttpServerTest {
    private lateinit var server: EmbeddedHttpServer
    private lateinit var repository: MockApiRepository

    @Test
    fun `should return mock response`() = runBlocking {
        // 创建测试端点
        val endpoint = MockEndpoint(...)
        repository.saveEndpoint("Test", endpoint)

        // 启动服务器
        val config = MockApiConfig(port = 18888)
        server.start("Test", config)

        // 发送 HTTP 请求
        val client = HttpClient()
        val response = client.get("http://localhost:18888/api/test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"result": "ok"}""", response.bodyAsText())

        // 清理
        server.stop()
    }
}
```

### 手动测试

#### 使用 curl

```bash
# GET 请求
curl http://localhost:8888/api/users

# POST 请求
curl -X POST http://localhost:8888/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe"}'

# 获取图片
curl http://localhost:8888/images/avatar.png --output avatar.png
```

#### 使用 Postman

1. 创建新请求
2. 设置 URL: `http://localhost:8888/api/your-endpoint`
3. 选择 HTTP 方法
4. 发送请求
5. 查看响应

#### 手机局域网测试

1. 启动服务器
2. 点击 "Show Addresses" 查看局域网地址
3. 在手机浏览器或应用中访问该地址

## 常见问题

### Q: 服务器无法启动,提示端口被占用

**A**:
1. 检查其他应用是否使用了相同端口 (默认 8888)
2. 使用 Settings 对话框修改端口
3. Windows: `netstat -ano | findstr :8888` 查看占用进程
4. Linux/Mac: `lsof -i :8888` 查看占用进程

### Q: 手机无法访问局域网地址

**A**:
1. 确保手机和电脑在同一局域网
2. 检查防火墙是否阻止了端口访问
3. Windows 防火墙: 添加入站规则允许端口
4. 尝试禁用公共网络防火墙 (仅测试时)

### Q: 图片无法显示

**A**:
1. 检查图片存储路径配置
2. 确保图片文件存在于配置的目录
3. 检查文件权限
4. 验证响应中的图片路径格式: `/images/filename.png`

### Q: 修改端点后未生效

**A**:
端点修改后会自动重启服务器。如未生效:
1. 手动停止并重新启动服务器
2. 检查端点是否已启用
3. 查看请求日志确认请求路径匹配

### Q: JSON 响应格式错误

**A**:
1. 使用在线 JSON 验证器检查格式
2. 确保特殊字符正确转义
3. AddEditEndpointDialog 会自动验证 JSON

### Q: 如何调试服务器问题

**A**:
1. 查看 IDE 日志: Help → Show Log in Explorer
2. 搜索 "MockApi" 或 "EmbeddedHttpServer"
3. 启用详细日志: thisLogger().debug()
4. 检查请求历史表格

## 性能考虑

### 1. 请求历史限制
- 最大 100 条记录 (MockApiConstants.MAX_REQUEST_HISTORY)
- 超过自动删除最旧记录
- 避免内存泄漏

### 2. 服务器重启
- 端点修改触发自动重启
- 频繁修改时考虑批量操作
- 重启过程优雅处理 (等待当前请求完成)

### 3. 文件 I/O
- Repository 操作使用 `@Synchronized` 防止并发问题
- JSON 文件大小通常 < 1MB
- 考虑大量端点时的性能

### 4. UI 刷新
- 请求日志每 2 秒刷新一次
- 使用 SwingUtilities.invokeLater 确保线程安全
- 避免 EDT 阻塞

## 安全注意事项

1. **仅用于开发环境**: Mock API Server 不提供认证/授权
2. **不要暴露到公网**: 仅绑定局域网使用
3. **验证路径输入**: 防止目录遍历攻击 (图片路径)
4. **限制请求大小**: 防止 DoS 攻击
5. **清理敏感数据**: 不要在响应中包含真实密钥/密码

## 贡献指南

欢迎贡献!在提交 PR 前:

1. 遵循 Kotlin 代码规范
2. 添加单元测试
3. 更新相关文档
4. 确保所有测试通过
5. 使用有意义的 commit 消息

## 许可证

遵循 Quick Note 插件的许可证。

## 联系方式

- GitHub Issues: https://github.com/your-repo/quick-note/issues
- Email: zhanghang2357@gmail.com

# Logcat Recorder - 开发文档

日志记录功能的详细设计与实现说明请参考：`doc/logcat-recorder.md`。

**概要**：
- UI 位置：Mock API 工具窗口左侧 Logcat 面板
- Service：`LogcatRecorderService` (Project 级)
- 设置项：`QuickNoteSettings.logcatStoragePath`
- 默认路径：`~/quick-log/`
- 命名规范：`<Project>-<Branch>-<Timestamp>.log`

