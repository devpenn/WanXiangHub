package com.penn.myapplication.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 sherpa-onnx 的流式语音识别控制器。
 *
 * 采样 16kHz 单声道 PCM，边录边识别（onPartial 实时出字）；
 * 用户点"完成"、停顿超过 1.4 秒或到达最长时长时结束并回调 onFinal。
 * 所有回调都切到主线程。
 */
class SherpaAsrEngine(private val modelDir: File) {

    interface Listener {
        /** 模型加载完成，可以开始说话 */
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(true)
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var finishRequested = false
    private var thread: Thread? = null

    val isRunning: Boolean
        get() = !stopped.get()

    @SuppressLint("MissingPermission") // 调用方保证已授予 RECORD_AUDIO
    fun start(listener: Listener) {
        if (!stopped.get()) return
        stopped.set(false)
        cancelled.set(false)
        finishRequested = false

        thread = Thread {
            var recognizer: OnlineRecognizer? = null
            var stream: OnlineStream? = null
            var audioRecord: AudioRecord? = null
            try {
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                            model = File(modelDir, "model.int8.onnx").absolutePath,
                        ),
                        tokens = File(modelDir, "tokens.txt").absolutePath,
                        numThreads = 2,
                        debug = false,
                    ),
                    enableEndpoint = true,
                )
                val rec = OnlineRecognizer(assetManager = null, config = config)
                recognizer = rec
                val st = rec.createStream()
                stream = st

                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, SAMPLE_RATE * 4)
                )
                audioRecord = record
                record.startRecording()
                mainHandler.post { listener.onReady() }

                val chunk = ShortArray(SAMPLE_RATE / 10) // 每次 100ms
                val floats = FloatArray(chunk.size)
                var lastPartial = ""
                var hasSpoken = false
                var totalSamples = 0
                val maxSamples = SAMPLE_RATE * MAX_DURATION_SECONDS

                while (!stopped.get()) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n <= 0) continue

                    for (i in 0 until n) floats[i] = chunk[i] / 32768.0f
                    st.acceptWaveform(floats.copyOf(n), SAMPLE_RATE)
                    totalSamples += n

                    while (rec.isReady(st)) rec.decode(st)

                    val text = rec.getResult(st).text.trim()
                    if (text.isNotEmpty() && text != lastPartial) {
                        hasSpoken = true
                        lastPartial = text
                        mainHandler.post { if (!cancelled.get()) listener.onPartial(text) }
                    }

                    // 点完成 / 停顿超 1.4s（端点检测）/ 达到最长时长 → 收尾
                    if (finishRequested || (hasSpoken && rec.isEndpoint(st)) || totalSamples >= maxSamples) {
                        st.inputFinished()
                        while (rec.isReady(st)) rec.decode(st)
                        val finalText = rec.getResult(st).text.trim().ifEmpty { lastPartial }
                        mainHandler.post { if (!cancelled.get()) listener.onFinal(finalText) }
                        break
                    }
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    if (!cancelled.get()) listener.onError(t.message ?: "语音识别出错")
                }
            } finally {
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                try { stream?.release() } catch (_: Exception) {}
                try { recognizer?.release() } catch (_: Exception) {}
                stopped.set(true)
            }
        }.also { it.start() }
    }

    /** 结束录音，等待最终结果（触发 onFinal） */
    fun finish() {
        finishRequested = true
    }

    /** 放弃本次识别，不再触发任何回调 */
    fun cancel() {
        cancelled.set(true)
        finishRequested = true
        stopped.set(true)
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MAX_DURATION_SECONDS = 60
    }
}
