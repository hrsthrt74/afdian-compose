# 开发规则

代码风格（注释、AppLog）见 `AGENTS.md`；UI 写法约定见 `DESIGN.md`。

## Miuix 规则

- 需要读取 Miuix 源码时，请访问 `C:\Users\hrsth\Documents\GitHub\miuix`，如果找不到此路径，请询问用户
- 根部只创建一个 `MiuixTheme`。
- `NavigationBar` 是导航外观；页面路由仍由 `miuix-nav` 管理，两者不要混为一个机制。
- Overlay 组件需要位于 Miuix `Scaffold` 宿主下。
- 不要把 Material 3 的参数名、颜色 token、导航 API 直接套到 Miuix。

## Android 运行规则

- 任何 OkHttp `execute()`、文件读写、加密、图片或大 JSON 处理都必须放在 `Dispatchers.IO`。
- 带 `Application` 构造参数的 `AndroidViewModel` 不要依赖默认 Compose ViewModel 工厂；使用 `ViewModelProvider.AndroidViewModelFactory`，参考 `ManagementScreen.kt`。
- 网络异常必须转成 StateFlow 中的错误状态，不能让协程异常直接杀进程。
- Compose 列表使用稳定 key；没有稳定 ID 时不要强行使用可能重复的字段。
- 新增页面先做可运行的加载/空/错误/重试状态，再补视觉细节。
