package com.shiye.journal

import android.app.Application

/**
 * Application 入口。
 *
 * 职责只有一件：持有 [AppContainer]，让数据库与仓库的生命周期跟进程一致，
 * 而不是跟某个 Activity 走（否则旋转屏幕就会重新建库连接）。
 */
class ShiyeApplication : Application() {

    /** 全应用唯一的依赖容器 */
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * 这里**故意不写预置数据**。
     *
     * 播种要开库、查三张表、再插入，全过程占着数据库唯一的写连接；
     * 而首屏的日记列表查询也要用这条连接。抢在 onCreate 里启动，
     * 等于让"界面第一次出内容"排在播种后面。
     *
     * 播种改由 MainActivity 在**首帧绘制之后**触发（见 MainActivity.onCreate），
     * 反正它晚一帧几十毫秒完成，用户感知不到，却能让首屏查询先跑完。
     *
     * 换句话说：这个类现在只剩一件事 —— 持有容器。
     */
}
