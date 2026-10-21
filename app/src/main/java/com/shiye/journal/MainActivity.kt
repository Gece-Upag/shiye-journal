package com.shiye.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 应用唯一入口，也是唯一一个 Activity。
 *
 * 只做四件事：开启边到边显示、取出依赖容器、退到后台时强制保存、
 * 把界面交给 [ShiyeApp]。所有页面都是 Composable，靠自写的导航状态机切换。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 让内容延伸到系统栏下方，状态栏颜色由 ShiyeTheme 按主题设置。
        // enableEdgeToEdge() 内部已经把 decorFitsSystemWindows 设成 false 了，
        // 之前这里还多调了一次 WindowCompat.setDecorFitsSystemWindows —— 重复且无作用，去掉。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as ShiyeApplication).container

        // 预置数据（模板 / 分类 / 默认灵感页）放到首帧之后再写。
        // 它要开库、查表、插数据，和首屏的日记列表查询抢同一条数据库连接；
        // 抢在前面等于让"界面第一次出内容"多等一次全量插入。
        // decorView.post 保证排在当前消息之后，也就是第一帧已经排上队了。
        window.decorView.post { container.ensureSeededAsync() }

        // 按 Home / 切到后台时把未保存的内容写出去（规格 §4.4）。
        // 放在 Activity 而不是页面里：Activity 是唯一能观察到"整个应用退到后台"的地方，
        // 而具体哪个页面有未保存内容，由各页面自己在 SaveFlushRegistry 登记。
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.saveFlushRegistry.flushAll()
            }
        })

        setContent {
            ShiyeApp(container = container)
        }
    }
}
