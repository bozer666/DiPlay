package com.shilapi.xcertplay.oneos

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Geely OneOS API 的最小手写 Binder 协议。
 *
 * 来源：oneOS_MediaCenter.apk（com.geely.mediacenterservice，无混淆）逆向。
 * 所有 DESCRIPTOR 与 transaction code 均从该 APK 的 AIDL 生成类（Stub/Proxy）
 * 实测提取，未猜测：
 *  - IServiceManager.getService = 2，serviceId: 按键=8，Link=19
 *  - IInputManager: intercept=1, release=2, register=3, unregister=4
 *  - IInputListener(本端实现): onKeyCodeEvent=1, onShortClick=2,
 *    onHoldingPressStarted=3, onHoldingPressStopped=4, onLongPressTriggered=5, onDoubleClick=6
 *  - ILinkManager: updatePlayState=9, setMusicInfo=17
 *
 * 注意：只覆盖骨架需要的最小方法集，不是完整 OneOS SDK。
 */
internal object OneOsBinder {
    const val TAG = "OneOs"

    // ---- OneOSApiService ----
    const val SERVICE_PACKAGE = "com.geely.service.oneosapi"
    const val SERVICE_CLASS = "com.geely.service.oneosapi.OneOSApiService"

    // ---- IServiceManager ----
    const val SM_DESCRIPTOR = "com.geely.lib.oneosapi.IServiceManager"
    const val SM_TRANSACTION_GET_SERVICE = 2
    const val SERVICE_ID_KEY_INPUT = 8
    const val SERVICE_ID_LINK = 19

    // ---- IInputManager ----
    const val IM_DESCRIPTOR = "com.geely.lib.oneosapi.input.IInputManager"
    const val IM_TRANSACTION_INTERCEPT = 1
    const val IM_TRANSACTION_RELEASE = 2
    const val IM_TRANSACTION_REGISTER = 3
    const val IM_TRANSACTION_UNREGISTER = 4

    // ---- IInputListener ----
    const val IL_DESCRIPTOR = "com.geely.lib.oneosapi.input.IInputListener"
    const val IL_TRANSACTION_ON_KEY_CODE_EVENT = 1
    const val IL_TRANSACTION_ON_SHORT_CLICK = 2
    const val IL_TRANSACTION_ON_HOLDING_STARTED = 3
    const val IL_TRANSACTION_ON_HOLDING_STOPPED = 4
    const val IL_TRANSACTION_ON_LONG_PRESS = 5
    const val IL_TRANSACTION_ON_DOUBLE_CLICK = 6

    // ---- ILinkManager ----
    const val LM_DESCRIPTOR = "com.geely.lib.oneosapi.linkmanager.ILinkManager"
    const val LM_TRANSACTION_UPDATE_PLAY_STATE = 9
    const val LM_TRANSACTION_SET_MUSIC_INFO = 17

    fun logd(msg: String) = Log.d(TAG, msg)
    fun logw(msg: String, t: Throwable? = null) = Log.w(TAG, msg, t)
    fun loge(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
}

/** IServiceManager 的最小代理：只实现 getService(int)。 */
internal class OneOsServiceManager(private val remote: IBinder) {
    fun getService(serviceId: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.SM_DESCRIPTOR)
            data.writeInt(serviceId)
            if (!remote.transact(OneOsBinder.SM_TRANSACTION_GET_SERVICE, data, reply, 0)) {
                OneOsBinder.logw("IServiceManager.getService($serviceId): transact returned false")
                return null
            }
            reply.readException()
            return reply.readStrongBinder()
        } catch (t: Throwable) {
            OneOsBinder.logw("IServiceManager.getService($serviceId) failed", t)
            return null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

/** IInputManager 的最小代理。 */
internal class OneOsInputManager(private val remote: IBinder) {

    fun registerListener(listener: IBinder, packageName: String, keyCodes: IntArray): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.IM_DESCRIPTOR)
            data.writeStrongBinder(listener)
            data.writeString(packageName)
            data.writeIntArray(keyCodes)
            if (!remote.transact(OneOsBinder.IM_TRANSACTION_REGISTER, data, reply, 0)) return false
            reply.readException()
            return true
        } catch (t: Throwable) {
            OneOsBinder.logw("IInputManager.registerListener failed", t)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun unregisterListener(listener: IBinder, packageName: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.IM_DESCRIPTOR)
            data.writeStrongBinder(listener)
            data.writeString(packageName)
            if (!remote.transact(OneOsBinder.IM_TRANSACTION_UNREGISTER, data, reply, 0)) return false
            reply.readException()
            return true
        } catch (t: Throwable) {
            OneOsBinder.logw("IInputManager.unregisterListener failed", t)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** 独占按键，成功返回 true。失败不抛异常（调用方降级为不独占）。 */
    fun interceptKeyCode(keyCode: Int, packageName: String): Boolean =
        transactKeyCode(OneOsBinder.IM_TRANSACTION_INTERCEPT, keyCode, packageName)

    fun releaseKeyCode(keyCode: Int, packageName: String): Boolean =
        transactKeyCode(OneOsBinder.IM_TRANSACTION_RELEASE, keyCode, packageName)

    private fun transactKeyCode(code: Int, keyCode: Int, packageName: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.IM_DESCRIPTOR)
            data.writeInt(keyCode)
            data.writeString(packageName)
            if (!remote.transact(code, data, reply, 0)) return false
            reply.readException()
            return reply.readInt() != 0
        } catch (t: Throwable) {
            OneOsBinder.logw("IInputManager.transact($code, $keyCode) failed", t)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

/**
 * IInputListener 的本端实现（Binder server 端）。
 * 回调发生在 Binder 线程池，调用方自行决定是否 post 到主线程。
 */
internal class OneOsInputListener : Binder() {

    /** 短按：(keyCode, softKeyFunction)。 */
    var onShortClick: ((keyCode: Int, softKeyFunction: Int) -> Unit)? = null

    /** 其他回调默认只记日志，需要时再接。 */
    var onOther: ((method: String, args: IntArray) -> Unit)? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.INTERFACE_TRANSACTION) {
            reply?.writeString(OneOsBinder.IL_DESCRIPTOR)
            return true
        }
        return try {
            data.enforceInterface(OneOsBinder.IL_DESCRIPTOR)
            when (code) {
                OneOsBinder.IL_TRANSACTION_ON_SHORT_CLICK -> {
                    val keyCode = data.readInt()
                    val softKeyFunction = data.readInt()
                    reply?.writeNoException()
                    onShortClick?.invoke(keyCode, softKeyFunction)
                    true
                }
                OneOsBinder.IL_TRANSACTION_ON_KEY_CODE_EVENT -> {
                    val a = intArrayOf(data.readInt(), data.readInt(), data.readInt())
                    reply?.writeNoException()
                    onOther?.invoke("onKeyCodeEvent", a)
                    true
                }
                OneOsBinder.IL_TRANSACTION_ON_HOLDING_STARTED,
                OneOsBinder.IL_TRANSACTION_ON_HOLDING_STOPPED,
                OneOsBinder.IL_TRANSACTION_ON_LONG_PRESS,
                OneOsBinder.IL_TRANSACTION_ON_DOUBLE_CLICK -> {
                    val a = intArrayOf(data.readInt(), data.readInt())
                    reply?.writeNoException()
                    val name = when (code) {
                        OneOsBinder.IL_TRANSACTION_ON_HOLDING_STARTED -> "onHoldingPressStarted"
                        OneOsBinder.IL_TRANSACTION_ON_HOLDING_STOPPED -> "onHoldingPressStopped"
                        OneOsBinder.IL_TRANSACTION_ON_LONG_PRESS -> "onLongPressTriggered"
                        else -> "onDoubleClick"
                    }
                    onOther?.invoke(name, a)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        } catch (t: Throwable) {
            OneOsBinder.logw("OneOsInputListener.onTransact($code) failed", t)
            false
        }
    }
}

/** ILinkManager 的最小代理：只实现 setMusicInfo / updatePlayState。 */
internal class OneOsLinkManager(private val remote: IBinder) {

    /**
     * 推送投屏音乐信息。参数顺序来自 DBPlay_1_9vpi 反编译的 ILinkManager AIDL
     * （transaction 17）：
     *  artistName, albumName, coverArt, lyrics, totalTimesMs, title,
     *  authorName, writerName, composerName, playingCurrentTimeMs,
     *  isFavorite, isPlaying。
     * Boolean 按 int 写（API 28 无 Parcel.writeBoolean）。
     */
    fun setMusicInfo(
        artistName: String,
        albumName: String,
        coverArt: String,
        lyrics: String,
        totalTimesMs: Long,
        title: String,
        authorName: String,
        writerName: String,
        composerName: String,
        playingCurrentTimeMs: Long,
        isFavorite: Boolean,
        isPlaying: Boolean,
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.LM_DESCRIPTOR)
            data.writeString(artistName)
            data.writeString(albumName)
            data.writeString(coverArt)
            data.writeString(lyrics)
            data.writeLong(totalTimesMs)
            data.writeString(title)
            data.writeString(authorName)
            data.writeString(writerName)
            data.writeString(composerName)
            data.writeLong(playingCurrentTimeMs)
            data.writeInt(if (isFavorite) 1 else 0)
            data.writeInt(if (isPlaying) 1 else 0)
            if (!remote.transact(OneOsBinder.LM_TRANSACTION_SET_MUSIC_INFO, data, reply, 0)) {
                return false
            }
            reply.readException()
            return true
        } catch (t: Throwable) {
            OneOsBinder.logw("ILinkManager.setMusicInfo failed", t)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 推送播放状态。语义来自 DBPlay_1_9vpi 反编译的源码 Log：
     * state: 1=播放，其他=停止；brand: 1=HiCar，其他=CarLink。
     * CarPlay 用 brand=0。
     */
    fun updatePlayState(state: Int, brand: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(OneOsBinder.LM_DESCRIPTOR)
            data.writeInt(state)
            data.writeInt(brand)
            if (!remote.transact(OneOsBinder.LM_TRANSACTION_UPDATE_PLAY_STATE, data, reply, 0)) {
                return false
            }
            reply.readException()
            return true
        } catch (t: Throwable) {
            OneOsBinder.logw("ILinkManager.updatePlayState failed", t)
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
