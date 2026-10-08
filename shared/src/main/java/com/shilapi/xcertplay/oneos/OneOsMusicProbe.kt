package com.shilapi.xcertplay.oneos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * OneOS 媒体显示试探：验证 ILinkManager.setMusicInfo / updatePlayState
 * 能否让车机原生媒体 UI 显示投屏音乐信息。
 *
 * 链路：bindService(com.geely.service.oneosapi.OneOSApiService)
 *   → IServiceManager.getService(19) → ILinkManager
 *   → setMusicInfo(...) + updatePlayState(...)
 *
 * 这是"试探"不是正式功能：参数语义、是否需要 link session、
 * 显示位置，都要看车上实测的日志和屏幕。确认有效后再做正式接入
 * （CarPlay 播放状态变化时实时推送）。
 *
 * 用法：
 *   OneOsMusicProbe.probe(context, "稻香", "周杰伦", "魔杰座", object : Callback {...})
 */
object OneOsMusicProbe {

    interface Callback {
        /** 试探完成，result 里是每一步的结果摘要（成功/失败/异常）。 */
        fun onDone(result: String)
    }

    fun probe(
        appContext: Context,
        title: String,
        artist: String,
        album: String,
        callback: Callback,
    ) {
        val log = StringBuilder()
        fun step(msg: String) {
            OneOsBinder.logd("[probe] $msg")
            log.append(msg).append('\n')
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var linkManager: OneOsLinkManager? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                step("OneOSApiService connected")
                if (service == null) {
                    step("FAIL: service binder is null")
                    callback.onDone(log.toString())
                    return
                }
                try {
                    val sm = OneOsServiceManager(service)
                    val linkBinder = sm.getService(OneOsBinder.SERVICE_ID_LINK)
                    if (linkBinder == null) {
                        step("FAIL: getService(19) returned null")
                        callback.onDone(log.toString())
                        return
                    }
                    step("getService(19) ok")
                    linkManager = OneOsLinkManager(linkBinder)

                    val infoOk = linkManager!!.setMusicInfo(
                        artistName = artist,
                        albumName = album,
                        coverArt = "",
                        lyrics = "",
                        totalTimesMs = 240_000L,
                        title = title,
                        authorName = "",
                        writerName = "",
                        composerName = "",
                        playingCurrentTimeMs = 0L,
                        isFavorite = false,
                        isPlaying = true,
                    )
                    step("setMusicInfo(title=$title, artist=$artist) -> $infoOk")

                    val stateOk = linkManager!!.updatePlayState(1, 0)
                    step("updatePlayState(state=1, brand=0) -> $stateOk")

                    step("DONE: 请看车机媒体界面/小部件是否有 \"$title\" 显示")
                } catch (t: Throwable) {
                    step("EXCEPTION: $t")
                    OneOsBinder.loge("[probe] failed", t)
                } finally {
                    callback.onDone(log.toString())
                    try { appContext.unbindService(this) } catch (_: Throwable) {}
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                step("OneOSApiService disconnected")
            }
        }

        val intent = Intent().setComponent(
            ComponentName(OneOsBinder.SERVICE_PACKAGE, OneOsBinder.SERVICE_CLASS)
        )
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            step("EXCEPTION bindService: $t")
            callback.onDone(log.toString())
            return
        }
        if (!bound) {
            step("FAIL: bindService returned false")
            callback.onDone(log.toString())
            return
        }
        step("bindService ok, waiting...")
        // 10 秒兜底：连不上也给结论
        mainHandler.postDelayed({
            if (linkManager == null) {
                step("FAIL: bind timeout (10s), service not connected")
                callback.onDone(log.toString())
                try { appContext.unbindService(connection) } catch (_: Throwable) {}
            }
        }, 10_000L)
    }
}
