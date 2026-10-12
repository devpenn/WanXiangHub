package com.penn.myapplication

import com.penn.myapplication.asr.AsrModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelInfoTest {

    @Test
    fun `HF 单文件源 hf-mirror 优先`() {
        val url = AsrModelInfo.hfFileUrl("https://hf-mirror.com", "tokens.txt")
        assertEquals(
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01/resolve/main/tokens.txt",
            url
        )
    }

    @Test
    fun `GitHub 整包候选 首选 gh-proxy 代理 直连兜底`() {
        val candidates = AsrModelInfo.githubCandidates()
        val tarballPath =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2"

        assertEquals("https://gh-proxy.com/$tarballPath", candidates.first())
        assertTrue(candidates.contains("https://gh.llkk.cc/$tarballPath"))
        assertEquals(tarballPath, candidates.last())
    }

    @Test
    fun `必需文件齐全`() {
        assertEquals(listOf("model.int8.onnx", "tokens.txt"), AsrModelInfo.requiredFiles())
    }
}
