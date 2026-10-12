# ============================================================
# 项目：Maps — 摩托车导航系统
# 用途：R8 混淆规则（仅 protected 构建类型）
# ============================================================

# ── 高德地图 SDK（** 号覆盖所有子包：api / location / services 等）──
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-dontwarn com.amap.**
-dontwarn com.autonavi.**

# ── 腾讯地图导航 SDK ──
-keep class com.tencent.navix.** { *; }
-keep class com.tencent.tencentmap.** { *; }
-keep class com.tencent.openmap.** { *; }
-dontwarn com.tencent.**

# ── 讯飞 TTS SDK（JNI RegisterNatives，必须 keep）──
-keep class com.alibaba.idst.** { *; }
-keep class com.iflytek.** { *; }
-dontwarn com.iflytek.**

# ── 必须 keep：Room 数据库（entity + dao + database）──
# 原因：Room kapt 生成的 _Impl 类中，entity 字段名以字符串形式嵌入 SQL 查询。
# R8 重命名字段名但不会更新字符串常量 → 运行时 Room 找不到列名崩溃。
-keep class org.zarroboogs.maps.data.local.entity.** { *; }
-keep class org.zarroboogs.maps.data.local.dao.** { *; }
-keep class org.zarroboogs.maps.data.local.AppDatabase { *; }

# ── Kotlin 协程（META-INF/services 加载）──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Nordic Android BLE Library（含 Binder/IPC 回调，必须 keep）──
-keep class no.nordicsemi.android.ble.** { *; }
-dontwarn no.nordicsemi.android.ble.**

# ── 通用 R8 属性保留 ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# ── 资源保留（防 shrinkResources 误删）──
-keepclassmembers class org.zarroboogs.maps.R$drawable { *; }
-keepclassmembers class org.zarroboogs.maps.R$layout { *; }

# ============================================================
# 以下包全部走 R8 混淆，不添加任何 keep 规则：
#   org.zarroboogs.maps.core.data.model.* — 数据模型
#   org.zarroboogs.maps.core.encoder.*   — BLE 协议编码器
#   org.zarroboogs.maps.core.hub.*       — 导航调度引擎
#   org.zarroboogs.maps.core.route.*     — 路网匹配算法
#   org.zarroboogs.maps.core.road.*      — 几何引擎
#   org.zarroboogs.maps.ble.*            — BLE 核心（BleClient/NordcBleManager/BleWriter/LegacyBleClient，仅 BleService 类名 Manifest 自动保留）
#   org.zarroboogs.maps.utils.*          — 工具类
#   org.zarroboogs.maps.hub.*            — 胶水层
#   org.zarroboogs.maps.module.*         — CameraVoiceManager/CameraRouteMatcher
#   org.zarroboogs.maps.ui.*             — Activity/Fragment
#   org.zarroboogs.maps.cruise.*         — 巡航
#   org.zarroboogs.maps.network.*        — 网络
#   org.zarroboogs.maps.service.*        — 通知监听/下载服务
# ============================================================
