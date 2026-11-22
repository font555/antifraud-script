# 反诈剧本杀 · 中职组参赛作品

> 一款**像素风「反诈书库」**：把每种骗局做成一本像素书，点开书 → 翻页 → 走进真实骗局对话，
> 每一步都有损失账本，结束后逐节点复盘，并对照真实案件出处。

- **赛事**：世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组
- **技术栈**：Android 原生 Java，**零第三方依赖**，剧本存在 assets 里，**不联网、不申请任何权限**
- **美术**：像素风（Canvas 手绘书本 + 开源像素字体），配色为羊皮纸 / 木色 / 番茄红

---

## 一、KPI（验收标准）

| 维度 | 指标 | 现状 |
|---|---|---|
| 剧本 | 6 类 17 本，共 405 个剧情节点 | ✅ |
| 内容 | 100% 节点有来源；来源 71 条，全部可打开原文 | ✅ |
| 质量 | 8 条路径 0 崩溃；冷启动 <2s | 待真机测试 |
| 教育效果 | 30 人前后测，正确率提升 ≥25 个百分点 | 材料已备，待实测 |

## 二、怎么跑起来

### 用 Android Studio
`File → Open` 选本目录，等 Gradle sync 完成后点运行。

### 用命令行（已验证）
```powershell
$env:JAVA_HOME = 'G:\Android\jbr'     # 用 Android Studio 自带的 JDK
.\gradlew.bat assembleDebug
```
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`（约 2.5 MB，零第三方依赖）。

## 三、界面流程（不是普通列表，是一本书）

```
启动 → 注册（昵称+头像，只存本机）→ 首次进入的聚光灯新手引导（4 步，可跳过）
     → 书架（6 本像素书，一本书=一类骗局）
     → 点书：封面沿 Y 轴翻转 + 放大 → 书页（剧本列表）
     → 聊天页：对方气泡逐条出现、损失金额滚动增长、选项卡淡入
     → 结局卡片 → 复盘页：损失数字滚动、逐节点卡片依次滑入、风险标签
     → 来源页：每条案例可点开/复制链接
```

## 四、目录结构

```
antifraud-script/
├── AGENTS.md                        # 给 AI / 队友的规则（先读它）
├── app/src/main/
│   ├── assets/
│   │   ├── data/categories.json     # 书架分类（封面色、封面大字）
│   │   ├── fonts/fusion_pixel.ttf   # 像素字体（MIT，含 LICENSE 文件）
│   │   └── scripts/*.json           # 17 个剧本（加文件即加剧本）
│   ├── java/com/example/antifraud/
│   │   ├── model/                   # Script / StoryNode / Choice / CaseSource / Category
│   │   ├── engine/                  # ScriptRepository / PathRecorder / UserStore
│   │   └── ui/                      # 书架 / 注册 / 书页 / 聊天 / 复盘 / 来源 + 像素控件
│   └── res/                         # 像素风 drawable、布局、动画、配色
├── tools/validate_script.py         # 剧本校验（Python，运行：py tools/validate_script.py）
└── docs/                            # 来源清单、前后测题、1 小时演示稿、PPT 大纲、截图
```

## 五、六个分类、17 个剧本（405 节点 / 71 条可核验来源）

| 分类 | 剧本数 | 代表剧本 | 单局时长 |
|---|---|---|---|
| 游戏账号 | 2 | 低价全皮账号的诱惑（30 节点）、卖号那天来的「高价买家」 | 6–9 分钟 |
| 刷单返利 | 3 | 点赞返现的兼职、游戏代练赚零花钱 | 6–8 分钟 |
| 冒充客服 | 3 | 客服来电要退款、会员误开通要取消 | 6–8 分钟 |
| 冒充公检法 | 3 | 屏幕里的警察、快递藏毒与假通缉令 | 6–8 分钟 |
| 校园贷兼职 | 3 | 零门槛的兼职、租借账号跑分 | 6–9 分钟 |
| AI换脸诈骗 | 3 | 视频里的表哥、我妈差点给「我」转了 3 万 5 | 6–8 分钟 |

每个剧本都包含：分支选择（14–16 个决策点）、损失账本、4–6 种结局、逐节点复盘、真实案例出处。
全部来源见 `docs/sources.md`。

## 六、开源与版权

- 字体：Fusion Pixel Font（MIT），见 `app/src/main/assets/fonts/LICENSE-fusion-pixel-font.txt`
- 界面、书本、图标全部 Canvas 手绘，无第三方图片素材
- 剧本基于公开报道改编，人物匿名化、平台名虚构，来源见 `docs/sources.md` 与各剧本 `sources` 字段
