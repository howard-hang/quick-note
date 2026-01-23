# Quick Note - IntelliJ IDEA Plugin

<!-- Plugin description -->
Quick Note is a JetBrains IDE plugin for fast note capture and a built-in Mock API server (JDK HttpServer).
It supports Markdown notes with YAML front matter, Git-branch scoped notes, full-text search, tagging, and a Postman-style UI
for managing mock endpoints and serving responses locally.
It also includes a Logcat recorder tool window to capture Android adb logcat logs with one-click controls.
<!-- Plugin description end -->

Quick Note 是一个功能强大的 IntelliJ 平台插件，提供便捷的笔记管理和 Mock API 服务功能。

## 功能特性

### 📝 笔记管理
- 在编辑器中快速创建笔记和代码片段
- Markdown 格式支持，带 YAML Front Matter
- 全文搜索（基于 Apache Lucene）
- Git 分支维度筛选与搜索（可切换当前分支/全部分支）
- 标签管理和文件关联
- 笔记列表浏览和过滤

### 📱 Logcat 日志记录（新功能）
用于在 IDE 内一键记录 Android `adb logcat` 日志，便于问题定位和回溯。

**位置**：位于 Mock API 工具窗口左侧 Logcat 面板。

**核心功能**：
- ✅ 一键开始/停止记录 Logcat（单按钮切换）
- ✅ 默认保存到用户目录 `quick-log/`
- ✅ 支持自定义日志保存目录
- ✅ 文件命名包含项目名、分支名、时间戳
- ✅ 一键打开日志所在文件夹
- ✅ 双击最近日志文件在文件管理器中打开

### 🌐 Mock API Server（新功能）
一个完整的网络接口模拟工具，用于在后端接口未准备好时提供模拟数据。

**核心功能**：
- ✅ RESTful API 支持（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD）
- ✅ Postman 风格的三面板 UI
- ✅ 局域网访问 - 手机和其他设备可直接访问
- ✅ 实时请求日志，显示响应时间
- ✅ 图片资源服务（`/images/*` 路由）
- ✅ CORS 跨域支持
- ✅ 可配置服务器设置（端口、图片路径）
- ✅ 自带示例接口，开箱即用

**使用场景**：
- 前端开发时后端接口未就绪
- 移动端开发需要本地 Mock 数据
- API 原型设计和演示
- 接口测试和调试

## 快速开始

### 安装
1. 从 JetBrains Marketplace 安装（即将上线）
2. 或手动构建：
   ```bash
   git clone https://github.com/your-repo/quick-note.git
   cd quick-note
   ./gradlew buildPlugin
   ```

### 使用 Mock API Server

#### 1. 打开工具窗口
- 查看 IDE 底部面板，点击 "Mock API" 标签
- 或通过菜单：View → Tool Windows → Mock API

#### 2. 查看默认示例接口
打开后会自动创建两个示例接口：
- **GET /api/users** - 获取用户列表
- **POST /api/users** - 创建用户

#### 3. 启动服务器
点击 **"Start Server"** 按钮，服务器默认在 8888 端口启动。

#### 4. 测试接口

**使用 curl**：
```bash
# GET 请求
curl http://localhost:8888/api/users

# POST 请求
curl -X POST http://localhost:8888/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"测试用户"}'
```

**使用浏览器**：
直接访问 http://localhost:8888/api/users

**使用手机（同一局域网）**：
1. 点击 "Show Addresses" 查看局域网地址
2. 在手机浏览器访问显示的地址（如 http://192.168.1.100:8888/api/users）

#### 5. 添加自定义接口
1. 点击 **"Add"** 按钮
2. 填写接口信息：
   - 名称：如 "Get Products"
   - 方法：选择 HTTP 方法（GET/POST等）
   - 路径：如 "/api/products"
   - 状态码：如 200
   - 响应体：输入 JSON 数据
3. 点击 OK 保存

#### 6. 复制接口 URL
1. 选择一个接口
2. 点击 **"Copy URL"** 按钮
3. 自动复制局域网地址 + 接口路径（如 `http://192.168.1.100:8888/api/users`）

## UI 界面

### Mock API 工具窗口布局
```
┌─────────────────────────────────────────────────────────┐
│ Server: ● Running | http://192.168.1.100:8888          │
│ [Copy URL] [Show Addresses] [Stop] [⚙️Settings]        │
├──────────────┬──────────────────┬───────────────────────┤
│ Endpoints    │ Endpoint Details │ Request Log           │
│ [+][-][↻]    │                  │                       │
│              │ Name: Get Users  │ Time | Method | Path  │
│ [GET]        │ Method: GET      │ 14:30| GET   |/api/..│
│ /api/users   │ Path: /api/users │ 14:29| POST  |/api/..│
│ Get Users ●  │ Status: 200      │                       │
│              │                  │ [Clear History]       │
│ [POST]       │ Response Body:   │                       │
│ /api/users   │ {...json...}     │                       │
│ Create User ●│                  │                       │
└──────────────┴──────────────────┴───────────────────────┘
```

## 技术栈

- **语言**：Kotlin 100%
- **HTTP 服务器**：JDK HttpServer (com.sun.net.httpserver)
- **JSON 处理**：kotlinx.serialization 1.6.2
- **全文搜索**：Apache Lucene 9.11.1
- **Markdown**：CommonMark 0.22.0
- **JVM 版本**：17

## 存储结构

```
~/.quicknote/
└── <ProjectName>/
    ├── notes/              # 笔记存储
    │   └── *.md
    └── mockapi/            # Mock API 存储
        ├── endpoints.json  # 端点配置
        ├── config.json     # 服务器配置
        └── images/         # 图片资源

~/quick-log/
└── <ProjectName>-<Branch>-<Timestamp>.log
```

## 开发文档

详细文档请查看：
- [UI 设计草稿](doc/ui-design.md) - 界面布局、颜色方案、交互设计
- [开发文档](doc/development-guide.md) - 架构设计、API 参考、扩展指南

## 配置说明

### Mock API 服务器设置
点击 Settings 按钮可配置：
- **Server Port**：服务器端口（默认 8888）
- **Image Storage Path**：图片存储目录
- **Enable CORS**：启用跨域支持
- **Enable Request Logging**：启用请求日志

### 端点配置
每个端点支持：
- HTTP 方法（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD）
- 自定义路径
- HTTP 状态码
- JSON 响应体
- 自定义响应头
- 响应延迟（模拟网络延迟）
- 启用/禁用开关

## 常见问题

### Q: 服务器无法启动，提示端口被占用
**A**: 在 Settings 中修改端口号，或使用以下命令查找占用进程：
```bash
# Windows
netstat -ano | findstr :8888

# Linux/Mac
lsof -i :8888
```

### Q: 手机无法访问局域网地址
**A**:
1. 确保手机和电脑在同一局域网
2. 检查防火墙设置，允许端口访问
3. Windows：防火墙 → 入站规则 → 添加规则允许端口

### Q: 如何添加图片资源
**A**:
1. 在 Settings 中配置图片存储路径
2. 将图片文件复制到该目录
3. 在响应中引用：`{"avatar": "/images/avatar.png"}`
4. 访问：`http://localhost:8888/images/avatar.png`

## 贡献

欢迎贡献！在提交 PR 前：
1. 遵循 Kotlin 代码规范
2. 添加单元测试
3. 更新相关文档
4. 确保所有测试通过

## 许可证

[待定]

## 联系方式

- Email: zhanghang2357@gmail.com
- GitHub Issues: [提交问题](https://github.com/your-repo/quick-note/issues)

## 更新日志

查看 [CHANGELOG.md](CHANGELOG.md) 了解详细的版本更新历史。

---

⭐ 如果这个插件对你有帮助，请给个 Star！
