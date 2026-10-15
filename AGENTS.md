# Modern Android Engineering & AI Agent Handbook (WanXiangHub / 万象更新)

本项目「万象更新」(WanXiangHub) 是一款基于 **Kotlin** 与 **Material Design 3** 构建的现代化 Android 应用，集成了组件化 UI、离线语音识别 (Sherpa-ONNX NDK)、日历农历算法、随机选择与待办日程管理等核心功能。

为了保证代码库的工程质量、架构严谨性、性能表现与长期可维护性，所有 AI Agent 及人类开发者在参与本项目开发时，**必须严格遵守**以下工程规范与开发准则。

---

## 1. 核心架构与分层设计规约 (Architecture & Layers)

本项目遵循 Android 官方推荐的 **Modern Android Architecture** (MVVM / Clean Architecture / MVI 范式)。

### 1.1 分层职责划分
- **UI 阶层 (UI Layer)**: `Activity` / `Fragment` / 自定义 View (如 `WheelView`, `WarmConfettiView`) 仅负责视图渲染、用户交互事件捕获、系统 WindowInsets 适配及动画播放。**严禁包含任何业务逻辑、持久化处理或复杂数据转换**。
- **状态与 ViewModel 阶层 (ViewModel / State Layer)**: 
  - 采用**单向数据流 (Unidirectional Data Flow, UDF)** 机制。
  - 视图状态统一使用 `Sealed Class` / `Sealed Interface` 建模 (如 `UiState<T>`: `Loading`, `Success(data)`, `Error(msg)`), 确保 UI 状态改变完全可预测。
  - ViewModel 仅对外暴露只读的 `StateFlow` / `SharedFlow` / `LiveData`，内部状态使用私有 `MutableStateFlow` / `MutableLiveData` 维护。
  - **一次性事件 (Single Event)**: 页面跳转、Toast/Snackbar 提示等使用 `SharedFlow` (replay = 0) 或 `SingleLiveEvent` 建模，防止屏幕旋转重绘时重复触发。
- **数据阶层 (Data Layer)**: 
  - `Repository` 作为数据唯一真实来源 (Single Source of Truth, SSOT)，统一封装本地持久化 (`SharedPreferences` / `Room` / `DataStore`) 与网络/离线 API。
  - Repository 负责 DTO/网络模型与 Domain/UI 数据模型的双向映射与清洗。
- **算法与工具阶层 (Domain / Utilities)**:
  - 复杂计算逻辑 (如 `LotteryGenerator`, `OptionSplitter`, `Lunar` 农历转换, `SherpaAsrEngine` 语音解析) 必须独立解耦为纯函数或独立 Manager 类，严禁挤压在 Activity/Fragment 中引发 God Class 膨胀。

---

## 2. Kotlin 编程规约与健壮性防护 (Kotlin Standards)

### 2.1 JSON 反序列化空安全防护 (Gson / Moshi 防暴雷规约)
- **风险背景**: Gson 等基于反射的反序列化库在将 JSON 解析为 Kotlin `data class` 时，会强行绕过 Kotlin 的非空校验。若旧数据/接口未返回某字段，即便声明为 `val category: String = "默认"`，反射仍会注入 `null`，导致后续调用 `copy()` 或 Kotlin 内置方法时抛出致命的 `NullPointerException`。
- **最佳实践**:
  1. 所有用于 JSON 持久化/网络传输的 DTO 类，属性建议声明为**可空类型** (如 `var category: String? = "默认"`)。
  2. 提供显式的 safe 兜底 Getter 属性 (例如: `val safeCategory: String get() = category ?: "默认"`)。
  3. 在数据读取/反序列化完成后 (如 `loadData()`)，执行统一的数据清洗与兜底补全。

### 2.2 协程与异步编程规范 (Coroutines & Concurrency)
- **作用域管理**: ViewModel 中仅使用 `viewModelScope`，Fragment/Activity 中仅使用 `lifecycleScope`。**严禁使用 `GlobalScope`**。
- **调度器显式指定**: 涉及 IO 操作 (文件解压、磁盘读写、Sherpa-ONNX 初始) 必须显式切换至 `Dispatchers.IO`；计算密集型任务切换至 `Dispatchers.Default`；UI 刷新必须在 `Dispatchers.Main.immediate`。
- **UI 层的 Flow 收集安全**: 在 Fragment/Activity 中收集 `StateFlow` 或 `SharedFlow` 时，**必须使用 `repeatOnLifecycle(Lifecycle.State.STARTED)`** 或 `flowWithLifecycle`，确保应用退至后台时自动暂停收集，防止资源浪费与内存泄漏。
- **异常捕获**: 所有异步协程必须配置 `runCatching` 或 `CoroutineExceptionHandler`，严禁未捕获的协程异常直接崩溃 App 进程。

### 2.3 内存泄漏与生命周期防护 (Lifecycle & Leak Prevention)
- 严禁在 ViewModel、静态变量、单例或长生命周期回调中持有 `Activity` / `Context` / `View` 的引用。若需 Context，请使用 `ApplicationContext` (如 `WanXiangApp`)。
- **Fragment ViewBinding 释放**:
  ```kotlin
  private var _binding: FragmentXxxBinding? = null
  private val binding get() = _binding!!
  
  override fun onDestroyView() {
      super.onDestroyView()
      _binding = null
  }
  ```
- 定时器、`Handler` 回调、`AudioRecord` 录音线程及 JNI 原生句柄 (如 `SherpaAsrEngine`) 必须在 `onDestroy()` / `onCleared()` 时做显式 stop/release。

### 2.4 时间、历法与 Locale 避坑指南
- **Java `Calendar` 周计算避坑**: 在进行日历/周视图计算 (如 `TodoFragment` 周跨度计算) 时，**必须显式指定 `firstDayOfWeek = Calendar.SUNDAY`** (或 `Calendar.MONDAY`)，避免因系统 Locale (中国首日周一 vs 欧美首日周日) 自动切换导致周数据跳变。
- **日期格式化**: 优先使用 `java.time` (MinSDK 26+) 或线程安全的 `SimpleDateFormat` 封装。
- **农历算法 (`cn.6tail:lunar`)**: 农历日历计算逻辑需保证线程安全，重复计算视图节点时增加适当内存缓存。

---

## 3. UI 界面、Material Design 3 与布局性能规约 (UI & Material 3)

### 3.1 Material Design 3 组件与主题
- 统一使用 M3 规范组件 (`MaterialButton`, `MaterialCardView`, `FloatingActionButton`, `ShapeableImageView`, `TextInputLayout` 等)。
- 支持 Android 12+ 动态壁纸取色 (`DynamicColors.applyToActivitiesIfAvailable(this)`)。
- **MaterialButton 动态背景色避坑警告 (重点)**:
  - **切勿**将 `MaterialButton` 的 `backgroundTintList` 设置为 `Color.TRANSPARENT` 或纯透明 `ColorStateList`。在 Android HWUI 硬件加速绘制层中，透明 `backgroundTint` 会导致底层的 `MaterialShapeDrawable` 渲染出黑色的底色。
  - **正确做法**: 将 `backgroundTintList` 设置为容器背景色 (如 `MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface)`) 或设为 `null` 并动态修改 `background` Drawable。

### 3.2 布局性能与 RecyclerView 优化
- **减少布局嵌套**: 复杂 UI 优先选择 `ConstraintLayout`，控制布局层级深度 `< 5`。
- **RecyclerView 强约束**:
  - 强制使用 `ListAdapter` + `DiffUtil.ItemCallback` 实现精准高效的局部刷更新，**严禁盲目调用 `notifyDataSetChanged()`**。
  - 对于仅更改部分状态 (如 CheckBox 勾选) 的更新，优先使用 payload 参数 (`notifyItemChanged(position, payload)`) 避免重新 inflate 布局。
- **点击水波纹与 Ripple 视觉统一**:
  - 点击响应水波纹 (Ripple) 与 Checked 状态必须作用于整个 Card / Container 容器，去除 CheckBox / RadioButton 内部错位的微型 Ripple 效果，确保点击触感区域与视觉边界一致。

### 3.3 全屏幕与 Edge-to-Edge 沉浸式
- 应用应适配 Edge-to-Edge 全屏幕绘制。
- 视图根节点需配置 `WindowInsetsListener` 或 `fitsSystemWindows`，妥善处理状态栏 (Status Bar) 与导航栏 (Navigation Bar) 的 Insets 边距 Padding，避免底部按钮或头部标题栏被系统栏遮挡。

### 3.4 页面标题基线与字号规范 (Typography & Header Alignment System)
- **全应用统一字号等级 (`dimens.xml`)**:
  - `text_size_page_title` (**28sp bold**): 一级 Tab 大标题 ("万象", "日历", "我的")
  - `text_size_section_title` (**18sp bold**): 二级标题/卡片组标题 ("首页快捷组件", "首页壁纸", "我的自选号码", 功能页 TopAppBar 标题)
  - `text_size_body_title` (**15sp bold**): 正文加粗标题/卡片内主要文本
  - `text_size_item_title` (**13sp bold**): 网格组件标题 ("经典猜数字", "电子木鱼" 等)
  - `text_size_body` (**14sp regular**): 列表正文/待办内容
  - `text_size_caption` (**12sp regular**): 次要说明/辅助时间戳
  - `text_size_badge` (**10sp bold**): 微型角标
- **页面标题垂直基线绝对对齐**:
  - 一级 Tab 页面 ("万象", "日历", "我的") 必须采用统一的 `layout_page_header.xml` 组件模板。
  - 顶部 Padding 统一基于状态栏高度 (`statusBars.top + 16dp`) 动态调整，标题行高固定 40dp。**严禁在一级 Tab 标题下方插入副标题描述**，确保切页时主标题在垂直方向上 100% 像素级对齐冻结，绝不发生上下跳动。
- **手机屏宽 3 列网格适配原则**:
  - 在手机端 (宽 360dp~412dp) 渲染网格图标组件时，**必须使用 3 列布局 (`spanCount = 3`)**，并采用**上图下文**的垂直纵向 Card 布局，确保 5 字以内功能标题 (如 "经典猜数字", "专注番茄钟", "二维码生成") 完全单行/双行完整展示，**严禁出现 `...` 省略号截断**。

### 3.5 二级功能页顶部导航栏规约 (M3 Sub-Activity Header)
- 所有二级 Activity 功能页面 (`ClassicGameActivity`, `RandomPickerActivity`, `PomodoroTimerActivity`, `QrGeneratorActivity`, `LuckyActivity`, `WoodenFishActivity`, `WarmPopupActivity`) 必须统一包含 `layout_sub_header.xml` 导航头。
- 左侧包含 40dp x 40dp 点击触摸域的返回按钮 (`ic_arrow_back`)，中间为 18sp Bold 页面标题，确保二级页面具有一致的沉浸式返回体验。

---

## 4. 离线 AI、NDK / JNI 与文件资源规约 (Native & On-Device AI)

### 4.1 Sherpa-ONNX 语音引擎与 .so 架构过滤
- 本项目包含 `sherpa-onnx` AAR 及 JNI 原生共享库 (.so)。
- **ABI Filter 规约**: 为控制 APK 体积并兼顾真机与模拟器调试，`app/build.gradle.kts` 中明确指定 ABI 过滤:
  ```kotlin
  ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
  }
  ```
- **原生 JNI 线程安全**: Sherpa-ONNX 离线识别模型加载属于计算/IO 密集型任务，必须在后台线程异步初始化。调用 Native 识别流时采用 `AtomicBoolean` 标记运行状态，保证音频录制线程与 JNI 资源销毁的线程安全。

### 4.2 模型解压与存储管理
- 语音模型大文件 (如 tar.bz2 压缩包) 使用 `commons-compress` 解压，**必须采用流式解压**，防止一次性读入内存引发 OutOfMemoryError。
- 模型文件持久化放置在 App 私有外部存储目录 (`context.getExternalFilesDir(null)`)，下载/解压完成后校验 MD5/文件完整性，支持断点续传与失败重试。

---

## 5. 工程构建、Gradle 与环境规约 (Build & Environment)

### 5.1 依赖管理与 Version Catalog
- 依赖版本统一放置于 `gradle/libs.versions.toml` (Version Catalog) 中进行管理。
- 项目工程参数约束: `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`, JVM Target `11`。

### 5.2 Gradle 运行环境踩坑
- **环境冲突问题**: 在本地 Shell 中运行 Gradle 命令行任务时，若遇 Android Studio 环境变量干扰，可在 Shell 命令前执行 `unset ANDROID_PREFS_ROOT` 确保 Gradle 插件无冲突运行。
- **构建验证**: 任何代码或配置改动完成后，**必须**运行 `./gradlew assembleDebug` 或 `./gradlew test` 进行构建与单测验证。

---

## 6. AI Agent 行为约束与 Git 协作规约 (Agent Protocols & Git Safety)

### 6.1 Git 命令执行强约束 (原子化执行)
- **严格禁止 Git 命令串联**: **严禁使用 `&&` 或 `||` 将多条 Git 命令串联** (例如: `git add . && git commit -m "xxx"` 是被绝对禁止的！)。
- **规范做法**: 每一条 Git 命令必须作为一个独立的工具调用单独执行：
  1. `git status`
  2. `git add <file>`
  3. `git commit -m "description"`
- **分支规范**:
  - 所有功能与修复分支 (`feature/xxx`, `fix/xxx`) 必须从 `main` 分支切出。
  - `test` 分支作为测试隔离分支，**绝对禁止**将 `test` 分支合入 `main` 分支。

### 6.2 代码修改与文件操作禁忌
- **严禁使用 Shell 命令编辑文件**: 绝对禁止使用 `run_shell_command` 配合 `sed`、`awk`、`echo >`、`cat << EOF` 等 Shell 重定向指令修改代码文件！
- **必须使用专属文件编辑工具**: 必须且只能使用 `replace_file_content` (单块替换)、`multi_replace_file_content` (多块替换) 或 `write_file` (整体覆盖)。
- **编辑前必读**: 在编辑任何文件前，必须先调用 `read_file` 获取当前最新内容，保证乐观锁与 IDE 内存缓冲区同步。

---

## 7. 测试、检查与代码质量 (Testing & Cleanliness)

### 7.1 单元测试策略
- 核心业务解析器、算法生成器、Repository 必须在 `app/src/test/java/` 下补充单测。
- 现有单元测试套件必须保持 100% 通过:
  - `LotteryParserTest` (彩票数据解析测试)
  - `OptionSplitterTest` (选项切割解析测试)
  - `LotteryGeneratorTest` (随机号码生成测试)
  - `LotteryScheduleTest` (开奖日程测试)
  - `LuckyStoreTest` (持久化存储测试)
  - `AsrModelInfoTest` (ASR 模型配置测试)

### 7.2 代码整洁与命名规范
- **零警告零冗余**: 提交前清理无用的 Import 引用，不引入编译 Warning。
- **命名约定**:
  - 类名 / 接口名: `PascalCase` (如 `TodoFragment`, `SherpaAsrEngine`)
  - 方法名 / 变量名: `camelCase` (如 `loadData()`, `isDone`)
  - XML 资源文件: `snake_case`，且需带明确前缀 (`activity_*.xml`, `fragment_*.xml`, `item_*.xml`, `ic_*.xml`, `bg_*.xml`)
  - Color / Dimension 资源: `R.color.color_xxx`, `R.dimen.spacing_xxx`

---

> **总结**: 作为 AI Agent，在理解与执行用户需求时，请始终秉持 Senior Android Developer 的严谨态度，遵循现代 Android 最佳实践，优先保证代码安全、架构清晰与视觉体验。
