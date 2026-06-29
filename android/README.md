# GymTrack Android — GZCLP 桌面小组件 (Kotlin + Glance)

iPhone 版(Scriptable)的 Android 对应实现。**各自独立、不与 iPhone 同步**(本机数据存本机)。
桌面小组件可**直接在 widget 上点「✅ 完成 / ⏭️ 顺延」**(Jetpack Glance 交互式小组件)。

## 功能
- 4 天 GZCLP 循环 A1→B1→A2→B2,每周三次按固定星期(默认一三五)滚动。
- 小组件显示今天该练哪天 + T1/T2/T3 动作(组×次);休息日显示下次。
- 点完成 → 指针前进、排下一次;点顺延 → 往后挪一天(撞上下一槽则吸附回网格,保持一三五)。
- 打开 App 还能看「接下来」列表、重置进度。
- **App 内编辑课表/训练星期**:首页「✏️ 编辑课表 / 训练星期」可改训练星期、增删训练日、增删/改每个动作(级别/名称/组×次)、调整训练日顺序;保存即写入数据并刷新小组件。改了训练星期会把下一次自动排到最近的新训练日。
- 调度逻辑与 iOS 版完全一致(同一套算法,已用 25 条断言交叉验证)。

## 直接装(最快)
仓库已构建好 `app/build/outputs/apk/debug/app-debug.apk`,可直接装到 Android 手机:
1. 手机「设置 → 应用 → 特殊权限 → 安装未知应用」允许浏览器/文件管理器。
2. 把 apk 传到手机点开安装。
3. 桌面长按 → 小组件 → 找 **GymTrack** → 拖到桌面。

## 用 Android Studio 构建/改课表
1. Android Studio **Open** 选 `android/` 目录,等 Gradle Sync(会自动下 Gradle 8.7 / 依赖)。
2. 连真机或开模拟器,点 ▶︎ Run。
3. **改课表/动作/训练星期**:直接在 App 首页点「✏️ 编辑课表 / 训练星期」即可,无需改代码。
   `Schedule.kt` 里的 `Gzclp`(program 列表 + `DEFAULT_WEEKDAYS`)只是**出厂默认**,首次运行或数据缺失时回退用;用户编辑后的课表存进 `gymtrack.json`。

命令行构建(用 Android Studio 自带 JDK):
```
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

## 验证调度逻辑(不需 Android,纯 Kotlin)
```
KOTLINC="/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
bash "$KOTLINC" app/src/main/java/com/gymtrack/Schedule.kt test/ScheduleTest.kt -include-runtime -d /tmp/t.jar
java -jar /tmp/t.jar
```
→ 应输出「✅ 全部通过 (25 断言)」。

## 代码结构
| 文件 | 作用 |
|------|------|
| `Schedule.kt` | GZCLP 课表 + 调度纯函数(与 iOS GymTrack.js 同逻辑) |
| `Store.kt` | 进度+课表持久化(filesDir/gymtrack.json) |
| `GymWidget.kt` | Glance 桌面小组件渲染 + 完成/顺延按钮 |
| `WidgetActions.kt` | 小组件按钮回调(complete / defer) |
| `GymWidgetReceiver.kt` | AppWidget receiver |
| `MainActivity.kt` | App 界面:首页(状态/接下来/完成/顺延/重置)+ 编辑课表/星期页 |
| `test/ScheduleTest.kt` | 纯逻辑自检(独立 kotlinc 跑) |

## 已知小限制
- 小组件 `updatePeriodMillis=0`:跨午夜后「今天」标签可能要等你打开 App 或点一下小组件才刷新到新一天(点完成/顺延/打开 App 都会刷新)。需要严格每日自动刷新可加 WorkManager,本期从简。
