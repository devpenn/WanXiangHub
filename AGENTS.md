# Modern Android Development Guidelines (WanXiangHub / 万象更新)

本项目「万象更新」是一个基于 Kotlin 与 Material Design 3 的现代 Android 应用。为了保证代码库的工程质量、架构严谨性与长远可维护性，AI Agent 及所有开发者在参与本项目开发时，须严格遵守以下规范。

---

## 1. 核心架构与模式 (Architecture & Patterns)

- **推荐架构**：遵循 Android 官方推荐的 **Modern Android Architecture**（MVVM / MVI 架构）。
  - **UI 层**：`Fragment` / `Activity` 仅负责视图渲染、用户交互与手势响应，不包含任何复杂业务逻辑。
  - **ViewModel 层**：持有 UI State，处理用户事件，响应式暴露 `StateFlow` / `LiveData`。
  - **Data 层**：`Repository` 封装本地（Room / DataStore / SharedPreferences）与远程数据源，提供统一的 Single Source of Truth (SSOT)。
- **单一职责与高内聚**：
  - Adapter、ViewHolder、数据转换工具以及复杂算法（如农历、算法生成器）须独立解耦，避免 Activity/Fragment 膨胀为 God Class。
- **状态驱动 UI (Unidirectional Data Flow)**：
  - 推荐使用 `Sealed Interface` 或 `Sealed Class` 建模 UI 状态（如 `Loading`, `Success(data)`, `Error(msg)`），确保 UI 状态改变可预测且防并发错乱。

---

## 2. Kotlin 编程规范与健壮性 (Kotlin Standards)

- **空安全与序列化防崩 (Deserialization Robustness)**：
  - 在使用 Gson / Moshi 等库将 JSON 反序列化为 Kotlin `data class` 时，必须考虑新旧版本字段兼容性。
  - 若在类中定义非空类型（如 `var category: String = "默认"`），从旧版本 JSON 读入时反序列化可能通过反射填入 `null`，调用 Kotlin 方法（如 `copy()`）时会触发致命的 `NullPointerException`。
  - **最佳实践**：反序列化模型建议声明为可空类型（如 `category: String? = "默认"`），并提供 safe 兜底属性（如 `val safeCategory: String get() = category ?: "默认"`），并在反序列化后（如 `loadData()`）进行数据清洗。
- **不可变性**：优先使用 `val` 和不可变集合（`listOf`, `mapOf`）；内部状态暴露使用 Read-Only 属性（如 `val state: StateFlow<UiState> get() = _state`）。
- **时间与日历处理**：
  - 在进行日期/周视图计算（如 Java `Calendar`）时，**必须显式指定 `firstDayOfWeek = Calendar.SUNDAY`**，避免因中国 Locale 与欧美 Locale 默认周首日差异导致周计算跳变。

---

## 3. UI & Material Design 3 规范

- **视图与主题**：
  - 使用 Material 3 组件（`MaterialButton`, `MaterialCardView`, `FloatingActionButton` 等）。
  - **MaterialButton 动态样式控制**：在修改 `MaterialButton` 背景色时，**切勿将 `backgroundTintList` 设为 `Color.TRANSPARENT`**（在 HWUI 绘制层会导致 `MaterialShapeDrawable` 透出黑色底色）。应设为容器背景色（如 `R.color.background`）或 null。
- **布局性能**：
  - 减少布局嵌套，复杂视图优先选用 `ConstraintLayout`。
  - RecyclerView 强制使用 `ListAdapter` + `DiffUtil.ItemCallback` 实现高效局部刷新，禁止直接盲目调用 `notifyDataSetChanged()`。
  - 列表项的 Checked / Ripple 点击动画须作用于整个 Card / Container，去除 CheckBox 错位的内部微型 Ripple，避免视觉错位。

---

## 4. 工程构建与 Git 工作流 (Build & Operations)

- **Git 命令规范**：
  - **禁止使用 `&&` 或 `||` 串联多个 Git 命令**，每条 Git 命令必须单独逐条执行。
  - **分支规范**：所有功能分支（`feature/xxx`, `fix/xxx`）必须从 `main` 分支切出，且禁止将 `test` 分支合入 `main`。
- **Gradle 构建环境**：
  - 本地运行 Gradle 任务时，如遇环境兼容问题，可在 Shell 执行 `unset ANDROID_PREFS_ROOT` 确保 Gradle 插件无冲突运行。
  - 执行改动后，必须运行 `./gradlew test` 或 `./gradlew assembleDebug` 验证构建与单测无误。
- **设备部署**：
  - 部署真机/模拟器时，推荐使用 `adb push + pm install -r` 快速覆盖安装，避免弹出 USB 授权弹窗阻断。

---

## 5. 测试与代码质量 (Testing & Inspection)

- 核心 ViewModel、算法与 Repository 需补充单元测试（位于 `app/src/test/`）。
- 提交改动前，检查不引入编译警告与未使用的 Import，保持代码整洁规范。
