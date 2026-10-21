# 拾页 —— release 包的代码压缩规则。
#
# 背景：release 构建开了 R8（isMinifyEnabled = true）。R8 会改名、删掉它认为
# 用不到的东西，而下面这些是**靠名字或靠生成代码间接引用**的，R8 看不出来，
# 不写明就会被干掉，表现是"debug 包好好的，release 包一跑就崩"。
#
# 改这个文件的三条规矩：
# 1. 只加"为什么必须留"的说明，不要无脑 -keep 整个包（那就等于关掉压缩）；
# 2. 优先用各库自带的 consumer 规则（Room / OkHttp / Tink 都有），
#    只在它们没覆盖到、或本项目有特殊用法时才手写；
# 3. 每次加完规则，必须出 release 包并在真机上跑一遍主流程，
#    不能只信编译通过 —— 这类问题只在运行时才暴露。

# ---------------------------------------------------------------------------
# 1. Room
# ---------------------------------------------------------------------------
# Room 在运行时按 "<包名>.AppDatabase_Impl" 这个名字反射找数据库实现，
# DAO 实现（XxxDao_Impl）同理。它们没有任何 Java 代码直接引用，
# R8 会当成死代码删掉。Room 自带的规则已覆盖，这里再显式写一遍，
# 防止将来升级 Room 时它的规则变化而我们没察觉。
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }

# ---------------------------------------------------------------------------
# 2. kotlinx.serialization
# ---------------------------------------------------------------------------
# 备份文件、AI 协议、导航返回栈都是 @Serializable 的 data class。
# 序列化器是编译期生成的 Companion.serializer()，靠名字被查到；
# 不保留的话表现为"备份恢复失败 / 旋转屏幕后返回栈空了"。
# 规则取自 kotlinx.serialization 官方推荐，作用范围限定在本项目包名内。

# 保留伴生对象字段，避免运行时再去 getDeclaredClasses 里翻
-keepclassmembers class com.shiye.journal.** {
    *** Companion;
}

# 保留 serializer() 方法本身（默认序列化器 + 具名序列化器）
-keepclasseswithmembers class com.shiye.journal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留生成的 $$serializer 类及其描述符
-keep,includedescriptorclasses class com.shiye.journal.**$$serializer { *; }

# ---------------------------------------------------------------------------
# 3. 安全加密库（security-crypto → Tink）的注解缺失
# ---------------------------------------------------------------------------
# Tink 的代码上带着 errorprone 的注解，但它的发布产物里不含这些注解类
# （对运行没影响，注解只是给静态检查用的）。R8 找不到会直接判定失败。
# 这四条是 AGP 自己生成的（见 app/build/outputs/mapping/release/missing_rules.txt），
# 照抄到这里以免每次都要重新生成一遍。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# ---------------------------------------------------------------------------
# 4. 其它
# ---------------------------------------------------------------------------
# 崩溃栈要能看出是哪一行：保留源码文件名与行号（对体积影响很小，收益很大）。
-keepattributes SourceFile,LineNumberTable
# 但对外只暴露一个假的文件名，少泄漏一点内部包结构
-renamesourcefileattribute SourceFile
