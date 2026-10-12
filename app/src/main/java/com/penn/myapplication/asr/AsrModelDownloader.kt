package com.penn.myapplication.asr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 语音模型信息与首次下载。
 *
 * 模型：sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01（约 26MB）
 * 模型文件不打包进 APK，首次使用语音输入时按需下载到应用私有目录。
 *
 * 下载源按序回退：
 * 1. hf-mirror.com 单文件 —— 国内可直连的 HuggingFace 镜像
 * 2. huggingface.co 单文件 —— 海外网络
 * 3. GitHub Releases 整包（tar.bz2，需解压）—— 直连 GitHub 不稳，经知名加速代理回退
 */
object AsrModelInfo {

    private const val REPO = "csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"

    internal val HF_BASE_URLS = listOf("https://hf-mirror.com", "https://huggingface.co")

    private const val GITHUB_TARBALL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2"

    // GitHub 加速代理：gh-proxy 系最出名；gh.llkk.cc 当前速度较快。空串表示直连兜底。
    // 代理站时效性强，全部失效时仍会尝试直连。
    private val GITHUB_PREFIXES = listOf(
        "https://gh-proxy.com",
        "https://gh.llkk.cc",
        "",
    )

    fun modelDir(context: Context): File = File(context.filesDir, "asr/$REPO")

    fun isModelReady(context: Context): Boolean {
        val dir = modelDir(context)
        return requiredFiles().all { File(dir, it).length() > 0 }
    }

    internal fun requiredFiles(): List<String> = listOf("model.int8.onnx", "tokens.txt")

    internal fun hfFileUrl(baseUrl: String, fileName: String) = "$baseUrl/$REPO/resolve/main/$fileName"

    internal fun githubCandidates(): List<String> =
        GITHUB_PREFIXES.map { if (it.isEmpty()) GITHUB_TARBALL_URL else "$it/$GITHUB_TARBALL_URL" }
}

class AsrModelDownloader(
    private val modelDir: File,
    private val onProgress: (percent: Int) -> Unit,
) {

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    /**
     * 阻塞式下载缺失的模型文件：先走 HuggingFace 单文件源，
     * 仍有缺失再走 GitHub 整包（下载 tar.bz2 并解压）。
     * 全部源都不可用时抛 IOException。
     */
    @Throws(IOException::class)
    fun downloadBlocking() {
        modelDir.mkdirs()
        try {
            downloadFromHf()
        } catch (e: IOException) {
            // HF 全部源失败，继续尝试 GitHub 整包兜底
            Log.w(TAG, "HuggingFace 源全部失败，转 GitHub 整包兜底", e)
        }
        if (missingFiles().isNotEmpty()) {
            downloadFromGithubTarball()
        }
        val stillMissing = missingFiles()
        if (stillMissing.isNotEmpty()) {
            throw IOException("模型文件缺失: $stillMissing")
        }
        onProgress(100)
    }

    private fun missingFiles(): List<String> =
        AsrModelInfo.requiredFiles().filter { File(modelDir, it).length() == 0L }

    private fun downloadFromHf() {
        val missing = missingFiles()
        missing.forEachIndexed { index, fileName ->
            if (cancelled) throw IOException("已取消")
            val target = File(modelDir, fileName)
            downloadFile(
                candidates = AsrModelInfo.HF_BASE_URLS.map { AsrModelInfo.hfFileUrl(it, fileName) },
                target = target,
            ) { percent ->
                val overall = ((index + percent / 100.0) / missing.size * 100).toInt()
                onProgress(overall.coerceIn(0, 100))
            }
        }
    }

    private fun downloadFromGithubTarball() {
        if (cancelled) throw IOException("已取消")
        val tarball = File.createTempFile("asr-model", ".tar.bz2", modelDir)
        try {
            downloadFile(AsrModelInfo.githubCandidates(), tarball) { percent ->
                onProgress(percent.coerceIn(0, 99))
            }
            extractTarball(tarball, AsrModelInfo.requiredFiles().toSet())
        } finally {
            tarball.delete()
        }
    }

    private fun extractTarball(tarball: File, needed: Set<String>) {
        FileInputStream(tarball).use { fis ->
            BZip2CompressorInputStream(fis).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        // 只取文件名：去掉包内目录前缀，同时防路径穿越
                        val name = File(entry.name).name
                        if (name in needed) {
                            File(modelDir, name).outputStream().use { tar.copyTo(it) }
                        }
                    }
                }
            }
        }
    }

    private fun downloadFile(candidates: List<String>, target: File, onFileProgress: (Int) -> Unit) {
        var lastError: IOException? = null
        for (url in candidates) {
            if (cancelled) throw IOException("已取消")
            try {
                Log.i(TAG, "开始下载 ${target.name} ← $url")
                downloadWithRedirects(url, target, onFileProgress)
                Log.i(TAG, "下载成功 ${target.name} ← $url")
                return
            } catch (e: IOException) {
                Log.e(TAG, "下载失败 ${target.name} ← $url", e)
                lastError = e
            }
        }
        throw IOException(lastError?.message ?: "网络错误")
    }

    /** HttpURLConnection 不会自动跟随跨域 30x 重定向，手动逐跳处理 */
    private fun downloadWithRedirects(url: String, target: File, onFileProgress: (Int) -> Unit) {
        var currentUrl = url
        val partFile = File(target.parentFile, target.name + ".part")
        repeat(MAX_REDIRECTS) {
            if (cancelled) throw IOException("已取消")
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            try {
                when (conn.responseCode) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("重定向缺少目标地址")
                        // Location 可能是相对路径（如 hf-mirror 的缓存命中），基于当前 URL 解析
                        currentUrl = if (location.startsWith("http")) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        return@repeat
                    }
                    HttpURLConnection.HTTP_OK -> {
                        val total = conn.contentLengthLong
                        conn.inputStream.use { input ->
                            FileOutputStream(partFile).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var downloaded = 0L
                                var lastReported = -1
                                while (true) {
                                    if (cancelled) throw IOException("已取消")
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    downloaded += n
                                    if (total > 0) {
                                        val percent = (downloaded * 100 / total).toInt()
                                        if (percent != lastReported) {
                                            lastReported = percent
                                            onFileProgress(percent.coerceIn(0, 100))
                                        }
                                    }
                                }
                            }
                        }
                        if (!partFile.renameTo(target) || target.length() == 0L) {
                            throw IOException("文件保存失败: ${target.name}")
                        }
                        return
                    }
                    else -> throw IOException("HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("重定向次数过多")
    }

    companion object {
        private const val TAG = "AsrDownload"
        private const val MAX_REDIRECTS = 6
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
