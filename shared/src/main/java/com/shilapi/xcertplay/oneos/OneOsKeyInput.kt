package com.shilapi.xcertplay.oneos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/**
 * OneOS 方向盘按键对接。
 *
 * 链路：bindService(com.geely.service.oneosapi.OneOSApiService)
 *   → IServiceManager.getService(8) → IInputManager
 *   → registerListener(listener, packageName, keyCodes)
 *   → onShortClick(keyCode, softKeyFunction) → CarPlay HID
 *
 * 键值（oneOS_MediaCenter.apk 实测）：
 *  - 媒体中心自己订阅的 7 键：[119, 200087, 200088, 200085, 85, 300022, 300023]
 *  - 200087=R_MEDIA_NEXT, 200088=R_MEDIA_PREVIOUS, 200085=R_MEDIA_PLAY_PAUSE
 *  - 85/87/88=标准 MEDIA_PLAY_PAUSE/NEXT/PREVIOUS
 *  - 119/300022/300023 语义未定位，只记日志不映射
 *
 * 用法（CarPlay 会话开始时）：
 *   val keyInput = OneOsKeyInput(context, callback)
 *   keyInput.start(intercept = true)
 * 会话结束时：keyInput.stop()
 *
 * 全部 OneOS 调用都有降级保护：服务不存在 / binder 断开 / 调用抛异常
 * 都只记日志，不影响原有 MediaSession 路径。
 */
class OneOsKeyInput(
    private val appContext: Context,
    private val callback: Callback,
) {
    interface Callback {
        /** OneOS 通道就绪（按键已注册）。 */
        fun onOneOsReady()
        /** OneOS 不可用，原因见日志；调用方继续走标准 MediaSession 路径。 */
        fun onOneOsUnavailable(reason: String)
        /** 方向盘按键 → CarPlay HID（1..5，见 CarPlayMediaButton）。 */
        fun onMediaAction(hidAction: Int, keyCode: Int, softKeyFunction: Int)
        /** 未识别的键值，只记日志用。 */
        fun onUnknownKey(keyCode: Int, softKeyFunction: Int)
    }

    companion object {
        /** 照抄媒体中心自己的订阅数组，先全量收，再按需过滤。 */
        private val SUBSCRIBED_KEYS = intArrayOf(119, 200087, 200088, 200085, 85, 300022, 300023)

        /** 需要独占的切歌键（防媒体中心和 DiPlay 双重处理）。 */
        private val EXCLUSIVE_KEYS = intArrayOf(200087, 200088, 200085)

        private const val BIND_TIMEOUT_MS = 5000L

        // 吉利私有键值
        private const val R_MEDIA_PLAY_PAUSE = 200085
        private const val R_MEDIA_NEXT = 200087
        private const val R_MEDIA_PREVIOUS = 200088

        /** 吉利私有键 → CarPlay HID。标准键走 CarPlayMediaButton.forKeyCode()。 */
        fun hidActionFor(keyCode: Int): Int? =
            CarPlayMediaButton.forKeyCode(keyCode) ?: when (keyCode) {
                R_MEDIA_NEXT -> CarPlayMediaButton.NEXT
                R_MEDIA_PREVIOUS -> CarPlayMediaButton.PREVIOUS
                R_MEDIA_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
                else -> null
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val packageName: String = appContext.packageName

    @Volatile private var serviceBinder: IBinder? = null
    @Volatile private var inputManager: OneOsInputManager? = null
    @Volatile private var registered = false
    private val listener = OneOsInputListener()
    private var bindTimeout: Runnable? = null

    @Volatile private var interceptOnRegister = false
    @Volatile private var autoRegister = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            OneOsBinder.logd("OneOSApiService connected")
            cancelTimeout()
            if (service == null) {
                callback.onOneOsUnavailable("service binder is null")
                return
            }
            serviceBinder = service
            try {
                val sm = OneOsServiceManager(service)
                val inputBinder = sm.getService(OneOsBinder.SERVICE_ID_KEY_INPUT)
                if (inputBinder == null) {
                    callback.onOneOsUnavailable("getService(8) returned null")
                    return
                }
                inputManager = OneOsInputManager(inputBinder)
                OneOsBinder.logd("OneOsInputManager acquired")
                callback.onOneOsReady()
                if (autoRegister) {
                    val ok = registerKeys(interceptOnRegister)
                    OneOsBinder.logd("auto registerKeys ok=$ok")
                }
            } catch (t: Throwable) {
                OneOsBinder.loge("OneOS connect failed", t)
                callback.onOneOsUnavailable(t.toString())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            OneOsBinder.logw("OneOSApiService disconnected")
            serviceBinder = null
            inputManager = null
            registered = false
        }
    }

    init {
        listener.onShortClick = { keyCode, softKeyFunction ->
            OneOsBinder.logd("onShortClick keyCode=$keyCode softKeyFunction=$softKeyFunction")
            val hid = hidActionFor(keyCode)
            if (hid != null && CarPlayMediaButton.isHidPress(hid)) {
                callback.onMediaAction(hid, keyCode, softKeyFunction)
            } else {
                callback.onUnknownKey(keyCode, softKeyFunction)
            }
        }
        listener.onOther = { method, args ->
            OneOsBinder.logd("OneOsInputListener.$method args=${args.toList()}")
        }
    }

    /**
     * 连接 OneOS。连上后：
     *  - autoRegister=true：在 onOneOsReady 里自动调 [registerKeys]（默认）；
     *  - autoRegister=false：调用方在 onOneOsReady 回调里手动调 [registerKeys]。
     */
    fun start(intercept: Boolean = false, autoRegister: Boolean = true) {
        this.interceptOnRegister = intercept
        this.autoRegister = autoRegister
        val intent = Intent().setComponent(
            ComponentName(OneOsBinder.SERVICE_PACKAGE, OneOsBinder.SERVICE_CLASS)
        )
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            OneOsBinder.loge("bindService threw", t)
            callback.onOneOsUnavailable("bindService threw: $t")
            return
        }
        if (!bound) {
            callback.onOneOsUnavailable("bindService returned false (service not found?)")
            return
        }
        OneOsBinder.logd("bindService ok, waiting for onServiceConnected")
        // 超时保护：5 秒没连上就判不可用，不卡正常流程
        val timeout = Runnable {
            if (serviceBinder == null) {
                OneOsBinder.logw("OneOS bind timeout")
                callback.onOneOsUnavailable("bind timeout")
                try { appContext.unbindService(connection) } catch (_: Throwable) {}
            }
        }
        bindTimeout = timeout
        mainHandler.postDelayed(timeout, BIND_TIMEOUT_MS)
    }

    /** 注册 7 键监听；intercept=true 时对切歌键做独占。 */
    fun registerKeys(intercept: Boolean = false): Boolean {
        val mgr = inputManager
        if (mgr == null) {
            OneOsBinder.logw("registerKeys: not connected")
            return false
        }
        val ok = mgr.registerListener(listener, packageName, SUBSCRIBED_KEYS)
        OneOsBinder.logd("registerListener keys=${SUBSCRIBED_KEYS.toList()} ok=$ok")
        if (!ok) return false
        registered = true
        if (intercept) {
            for (k in EXCLUSIVE_KEYS) {
                val r = mgr.interceptKeyCode(k, packageName)
                OneOsBinder.logd("interceptKeyCode($k) -> $r")
            }
        }
        return true
    }

    fun stop() {
        cancelTimeout()
        try {
            val mgr = inputManager
            if (mgr != null && registered) {
                for (k in EXCLUSIVE_KEYS) {
                    val r = mgr.releaseKeyCode(k, packageName)
                    OneOsBinder.logd("releaseKeyCode($k) -> $r")
                }
                val ok = mgr.unregisterListener(listener, packageName)
                OneOsBinder.logd("unregisterListener ok=$ok")
            }
        } catch (t: Throwable) {
            OneOsBinder.logw("stop: unregister failed", t)
        } finally {
            registered = false
            inputManager = null
            serviceBinder = null
            try { appContext.unbindService(connection) } catch (_: Throwable) {}
            OneOsBinder.logd("OneOsKeyInput stopped")
        }
    }

    private fun cancelTimeout() {
        bindTimeout?.let { mainHandler.removeCallbacks(it) }
        bindTimeout = null
    }
}
