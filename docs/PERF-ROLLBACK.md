# 性能优化与瘦身：结果、回滚方式、还能做什么

优化前基线：`822a4e4`（tag **`baseline-pre-perf`**，分支 `master`，一行未动）。
优化全部提交在分支 **`perf/startup`** 上。

> **真机验证：已通过（2026-10-21）**
> 用 R8 压缩后的 release 包在真机上跑完主流程，无新增问题，
> 含此前点名要重点验的 **AI 设置页 + 实发一次请求**（Tink 加密路径）。
> 至此本轮优化不再视为"待验证改动"，下面的回滚方案仅作保险保留。

## 一、这次改了什么（4 个提交，各自独立）

| 提交 | 主题 | 收益 | 风险 |
|------|------|------|------|
| `b64ac30` | 首帧不再初始化加密区；AI 配置初值只算一次；播种推迟到首帧后 | 冷启动主线程少了 Keystore 初始化、少一次 JSON 解析、首屏查询不再排在播种后面 | 低 |
| `3e4d288` | 移除未使用的 Compose 预览依赖；窗口背景改为 App 底色 | APK 少一套用不到的预览工具链；启动不再闪一下跟 App 不一样的颜色 | 极低 |
| `2b37c23` | release 开启 R8 代码压缩与资源缩减 | **release 包 10.66 MB → 2.48 MB**；类加载/校验变少，冷启动更快 | 中（见下） |
| `31a0a59` | 本文档 | — | — |

### 三条具体的启动路径改动

1. **加密区懒加载**（`features/ai/AiConfigStore.kt`）
   `EncryptedSharedPreferences.create()` 要连 Android Keystore，在真机上几十到上百毫秒，
   而且**就在调用它的那个线程上跑**。原先它在构造函数里建，而这个对象是首帧重组时被取用的 ——
   等于把 Keystore 开销压在第一帧上，哪怕用户根本没开 AI。
   改成 lazy 之后，AI 关着时一次都不碰加密区。

2. **首帧初值不再重复计算**（`ShiyeApp.kt`）
   `collectAsStateWithLifecycle(initialValue = ...)` 的实参**每次重组都会求值**，
   不管用不用得上。这里是一次偏好读取 + JSON 反序列化，用 `remember` 包住只算一次。

3. **播种推迟到首帧之后**（`ShiyeApplication.kt` → `MainActivity.kt`）
   写预置模板/分类要开库、查表、插入，占着数据库唯一的写连接；首屏日记列表查询也用这条连接。
   原来在 `Application.onCreate` 就启动，等于让"界面第一次出内容"排在播种后面。

## 二、回滚

### 一档：整包放弃

```bash
git checkout master        # master 仍停在 baseline-pre-perf，等于优化没发生过
git checkout perf/startup  # 随时回来
```

### 二档：只撤某一步（推荐）

```bash
git log --oneline baseline-pre-perf..HEAD          # 看每一步
git revert <提交 hash>                              # 只撤那一步，其余保留
```

`2b37c23`（R8）是**唯一有运行时风险**的一步。
如果真机上出现"只有 release 包才复现"的崩溃，先 `git revert 2b37c23` 验证是不是它引起的。

### 三档：只看不动

```bash
git diff baseline-pre-perf HEAD --stat              # 总共改了哪些文件
git diff baseline-pre-perf HEAD -- <文件>           # 某个文件改了什么
```

## 三、怎么验证 / 怎么自己量启动时间

```bash
export JAVA_HOME="D:/Android/Android studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew testDebugUnitTest --console=plain      # 251 个单测，优化前后都全过
./gradlew assembleDebug --console=plain
./gradlew assembleRelease --console=plain
```

release 包默认未签名，装不进手机。用 debug key 签一下（仅本地验证）：

```bash
BUILD_TOOLS="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.1.0"

"$BUILD_TOOLS/zipalign" -f -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/apk/release/shiye-release-aligned.apk
"$BUILD_TOOLS/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/release/shiye-release-signed.apk \
  app/build/outputs/apk/release/shiye-release-aligned.apk
```

因为用的是和 debug 包同一把 key，可以直接覆盖安装，不用先卸载。

**量冷启动**（手机连 USB）：

```bash
adb shell am force-stop com.shiye.journal
adb shell am start -W -n com.shiye.journal/.MainActivity   # 看 TotalTime
adb logcat -d | grep Displayed                              # 或看系统报的 Displayed 时间
```

建议**连测 5 次取中位数**，第一次会因磁盘缓存偏慢，不要算进去。
拿优化前的包（`git checkout master` 出一个 debug 包）和现在对比更直观。

## 四、还没做的：Baseline Profile（冷启动最大单项收益）

R8 之外，Compose 应用冷启动收益最大的一项是 **Baseline Profile**：
把启动会走到的方法预先编译成本地代码，Google 官方给的口径通常是 **30% 左右**。

它需要**在一台真实的设备或模拟器上跑一遍生成脚本**（不是纯编译能出的东西），
这次没有可用设备（`adb devices` 是空的，模拟器也没起），所以没做。
要补的话：手机连上 USB，我来加一个 `:baselineprofile` 模块跑
`./gradlew :app:generateReleaseBaselineProfile`，生成的文件会打进 APK。

需要注意：APK 里现在**已经有** `assets/dexopt/baseline.prof`，
那是 Compose/Room 等库自带的 profile 被合并进来的，我们自己那一层还没有。
