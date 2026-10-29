# ── Ktor（MCP 本地服务器）──
# ktor-utils 的 IntellijIdeaDebugDetector 会引用 java.lang.management.ManagementFactory /
# RuntimeMXBean，这两个类只存在于桌面 JVM，Android 运行库里没有。这段代码只在
# 「检测 IDE 调试器」时走，Android 上不会被执行；但 R8 在 release 全量模式下把「缺失类」
# 直接判为错误（1.1.0 发版第一次跑 R8 就是卡在这里）。
# 只忽略警告、不 keep：让 R8 照常裁剪，不要为了绕过一个探测类把整个 ktor 打进包里。
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**
