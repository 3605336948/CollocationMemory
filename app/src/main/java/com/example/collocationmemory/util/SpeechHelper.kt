package com.example.collocationmemory.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 单词朗读（TTS）。
 *
 * 用法：Application 启动时 [init] 一次，之后任何地方调 [speak] 即可。
 * 之所以做成单例而不是每个页面各建一个：TextToSpeech 初始化要几十到几百毫秒，
 * 每次建新的会让第一张卡片读不出来；而且一个进程里同时挂多个 TTS 实例
 * 在部分机型上会直接失败。
 *
 * 降级策略：设备上没有英文语音数据（常见于精简版 ROM、没有装语音引擎的平板），
 * [speak] 返回 false，调用方据此给用户一个提示，不影响背单词本身。
 */
object SpeechHelper {

    private const val TAG = "SpeechHelper"

    /** 语速略慢一点，背单词时更容易听清音节 */
    private const val SPEECH_RATE = 0.85f

    private var tts: TextToSpeech? = null

    /** 语音引擎状态：初始化中 / 可用 / 不可用（没有引擎或没有英文语音数据） */
    private enum class InitState { INITIALIZING, READY, UNAVAILABLE }

    @Volatile
    private var initState = InitState.INITIALIZING

    /** 引擎是否可用且装了英文语音数据 */
    val ready: Boolean
        get() = initState == InitState.READY

    /**
     * 引擎是否明确不可用。
     *
     * 「初始化中」不算不可用 —— 那时调用 [speak] 会把文本挂起，
     * ready 之后自动补读，界面上不该有任何提示。
     */
    fun isUnavailable(): Boolean = initState == InitState.UNAVAILABLE

    /**
     * 「没有英文语音」的提示，整个进程只弹一次。
     *
     * 复习/新学每翻一张卡片都会尝试朗读，没有语音数据的设备上
     * 如果不去重，用户每看一个词就被弹一次 toast，很扰民。
     * 学习页、复习页、手动点「英」都走这个口子拿提示额度。
     */
    @Volatile
    private var unavailableWarned = false

    /** 返回 true 表示「这次该提示」； false 表示本进程已经提示过了 */
    fun consumeUnavailableWarning(): Boolean {
        if (unavailableWarned) return false
        unavailableWarned = true
        return true
    }

    /**
     * 等引擎初始化出结果（可用 / 不可用）。
     *
     * 初始化是异步的，进学习页那一刻往往还没结论；直接判断会把
     * 「还没初始化完」误当成「不可用」或漏掉提示。最多等 [timeoutMs]，
     * 返回 true 表示最终可用。
     */
    suspend fun awaitReady(timeoutMs: Long = 3000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (initState == InitState.INITIALIZING && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }
        return initState == InitState.READY
    }

    /** 初始化时临时挂着的待读文本（引擎还没 ready 就有人要读） */
    @Volatile
    private var pendingText: String? = null

    fun init(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "语音引擎初始化失败：status=$status")
                initState = InitState.UNAVAILABLE
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val result = engine.setLanguage(Locale.US)
            initState = if (result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setSpeechRate(SPEECH_RATE)
                // 引擎是在后台线程异步就绪的，把这期间攒下的那次朗读补上
                pendingText?.let {
                    pendingText = null
                    speak(it)
                }
                InitState.READY
            } else {
                Log.w(TAG, "设备没有可用的英文语音数据（setLanguage 返回 $result）")
                InitState.UNAVAILABLE
            }
        }
    }

    /**
     * 朗读一段英文。返回 false 表示没读成（引擎不可用 / 没有英文语音数据）。
     *
     * 引擎异步初始化，如果还没 ready，文本会被记下来，ready 之后自动读一次。
     */
    fun speak(text: String): Boolean {
        val engine = tts
        // 引擎还没建起来（init 未调用或正在构造）或明确不可用
        if (engine == null || initState == InitState.UNAVAILABLE) return false
        if (initState != InitState.READY) {
            // 引擎还在异步初始化：记下来，ready 之后自动补读。
            // 返回 true —— 调用方不该把「稍后就读」当成失败去提示用户。
            if (text.isNotBlank()) pendingText = text
            return true
        }
        val cleaned = forSpeech(text)
        if (cleaned.isBlank()) return false
        val params = android.os.Bundle().apply {
            // 走媒体通道，铃声音量调静音时不会突然出声
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }
        val result = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "朗读失败：speak 返回 $result")
            return false
        }
        return true
    }

    /** 停掉正在读的内容（切页面时用） */
    fun stop() {
        runCatching { tts?.stop() }
    }

    /**
     * 把词条文本处理成适合朗读的样子。
     *
     * 词库里有 "take ... into account" 这类省略号占位写法，
     * 直接交给 TTS 会读成「dot dot dot」，替换成 something 更接近真实读法。
     */
    private fun forSpeech(text: String): String = text
        .replace("…", " something ")
        .replace(Regex("\\.{2,}"), " something ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val UTTERANCE_ID = "collocation_memory_tts"
}
