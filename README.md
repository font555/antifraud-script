# 反诈剧本杀 · 中职组参赛作品

> 让中职生在 8 分钟内走完一次真实的游戏账号诈骗，每走一步都能点开权威报道原文，
> 结束后得到「你哪一步错了、损失了多少钱、真实案件里那位同学损失了多少」。

- **赛事**：世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组（对应计算机类 / 通信类专业）
- **技术栈**：Android 原生 Java，**零第三方依赖**，剧本存在 assets 里，不联网、不申请任何权限
- **作者**：kkxxdbx 等 4 人小组

---

## 一、KPI（验收标准）

| 维度 | 指标 |
|---|---|
| 剧本 | 主线 1 个（29 节点 / 单局 6–8 分钟）+ 短剧本 1 个（可选） |
| 内容 | 100% 节点有来源或标 TODO；来源 ≥4 条，含人民网 / 公安部 |
| 教育效果 | 30 人前后测，正确率提升 ≥25 个百分点 |
| 质量 | 8 条路径 0 崩溃，冷启动 <2s |

## 二、怎么跑起来

### 用 Android Studio
`File → Open` 选本目录，等 Gradle sync 完成后点运行。

### 用命令行（本项目已验证）
```powershell
$env:JAVA_HOME = 'G:\Android\jbr'     # 用 Android Studio 自带的 JDK
.\gradlew.bat assembleDebug
```
APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

## 三、目录结构

```
antifraud-script/
├── AGENTS.md                  # 给 AI / 队友的规则（先读它）
├── app/src/main/
│   ├── java/com/example/antifraud/
│   │   ├── model/             # Script / StoryNode / Choice / CaseSource（4 个数据类）
│   │   ├── engine/            # ScriptRepository（读 JSON）/ PathRecorder（记录路径）
│   │   └── ui/                # MainActivity / ChatActivity / ReviewActivity / SourcesActivity
│   ├── assets/scripts/        # 剧本 JSON（加文件即加剧本，不用改代码）
│   └── res/                   # 布局、配色、圆形背景
├── tools/validate_script.py   # 剧本校验脚本（Python，运行：py tools/validate_script.py）
└── docs/sources.md            # 案例来源清单
```

## 四、核心闭环

选剧本 → 聊天页逐节点选择 → 每一步记录「选了啥 / 风险 / 损失金额」 → 结局卡片 → 复盘页逐节点解释 → 来源页逐条对照真实报道。
