package com.shilapi.xcertplay.oneos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * OneOS 车机原生音乐上报：把 CarPlay Now Playing 元数据和播放状态
 * 推给 ILinkManager，让车机原生媒体 UI 显示投屏音乐信息。
 *
 * 参数顺序来自 DBPlay_1_9vpi 反编译的 ILinkManager AIDL
 * （setMusicInfo transaction 17，updatePlayState transaction 9）。
 *
 * 生命周期跟 CarPlay 会话走：会话开始 start()，会话结束 stop()。
 * OneOS 不可用时静默降级，不影响标准 MediaSession 路径。
 */
class OneOsMusicReporter(
    private val appContext: Context,
    private val callback: Callback,
) {
    interface Callback {
        /** OneOS 音乐通道就绪。 */
        fun onOneOsReady()
        /** OneOS 不可用，原因见日志。 */
        fun onOneOsUnavailable(reason: String)
    }

    companion object {
        private const val BIND_TIMEOUT_MS = 5000L
        /** brand: 1=HiCar，其他=CarLink；CarPlay 用 0。 */
        private const val BRAND_CARPLAY = 0
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var linkManager: OneOsLinkManager? = null
    private var bindTimeout: Runnable? = null
    private var stopped = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            OneOsBinder.logd("OneOSApiService connected (music)")
            cancelTimeout()
            if (stopped) return
            if (service == null) {
                callback.onOneOsUnavailable("service binder is null")
                return
            }
            try {
                val sm = OneOsServiceManager(service)
                val linkBinder = sm.getService(OneOsBinder.SERVICE_ID_LINK)
                if (linkBinder == null) {
                    callback.onOneOsUnavailable("getService(19) returned null")
                    return
                }
                linkManager = OneOsLinkManager(linkBinder)
                OneOsBinder.logd("OneOsLinkManager acquired")
                callback.onOneOsReady()
            } catch (t: Throwable) {
                OneOsBinder.loge("OneOS music connect failed", t)
                callback.onOneOsUnavailable(t.toString())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            OneOsBinder.logw("OneOSApiService disconnected (music)")
            linkManager = null
        }
    }

    fun start() {
        stopped = false
        val intent = Intent().setComponent(
            ComponentName(OneOsBinder.SERVICE_PACKAGE, OneOsBinder.SERVICE_CLASS)
        )
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            OneOsBinder.loge("OneOS music bindService failed", t)
            callback.onOneOsUnavailable(t.toString())
            return
        }
        if (!bound) {
            callback.onOneOsUnavailable("bindService returned false")
            return
        }
        val timeout = Runnable {
            if (linkManager == null && !stopped) {
                OneOsBinder.logw("OneOS music bind timeout")
                callback.onOneOsUnavailable("bind timeout")
                runCatching { appContext.unbindService(connection) }
            }
        }
        bindTimeout = timeout
        mainHandler.postDelayed(timeout, BIND_TIMEOUT_MS)
    }

    fun stop() {
        stopped = true
        cancelTimeout()
        linkManager = null
        runCatching { appContext.unbindService(connection) }
    }

    private fun cancelTimeout() {
        bindTimeout?.let { mainHandler.removeCallbacks(it) }
        bindTimeout = null
    }

    /**
     * 推送元数据。coverArt/lyrics/author/writer/composer/isFavorite
     * CarPlay 不提供，先传空/false，车上验证后再看。
     */
    fun pushMetadata(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?,
        positionMs: Long?,
        playing: Boolean,
    ) {
        val manager = linkManager ?: return
        val ok = manager.setMusicInfo(
            artistName = artist.orEmpty(),
            albumName = album.orEmpty(),
            coverArt = "",
            lyrics = "",
            totalTimesMs = durationMs ?: 0L,
            title = title.orEmpty(),
            authorName = "",
            writerName = "",
            composerName = "",
            playingCurrentTimeMs = positionMs ?: 0L,
            isFavorite = false,
            isPlaying = playing,
        )
        OneOsBinder.logd("setMusicInfo(title=$title) -> $ok")
    }

    /** 推送播放状态：state 1=播放，0=停止。 */
    fun pushPlaying(playing: Boolean) {
        val manager = linkManager ?: return
        val ok = manager.updatePlayState(if (playing) 1 else 0, BRAND_CARPLAY)
        OneOsBinder.logd("updatePlayState(playing=$playing) -> $ok")
    }
}
