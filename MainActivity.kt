package com.workstation.smartupgrade

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Typeface
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var log: TextView
    private var running = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku 已授权，可以执行触摸命令")
                appendLog("Shizuku 授权成功")
            } else {
                setStatus("Shizuku 授权被拒绝")
                appendLog("请在 Shizuku 中重新授权")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "工位智能助手"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        status = TextView(this).apply {
            text = "正在检查 Shizuku…"
            textSize = 16f
            setPadding(0, 18, 0, 18)
        }
        val authorize = Button(this).apply {
            text = "连接 / 请求 Shizuku 授权"
            setOnClickListener { requestShizuku() }
        }
        val testTap = Button(this).apply {
            text = "测试点击屏幕中心"
            setOnClickListener { runShell("input tap 540 1200") }
        }
        val testSwipe = Button(this).apply {
            text = "测试向上拖动"
            setOnClickListener { runShell("input swipe 540 1500 540 900 500") }
        }
        val stop = Button(this).apply {
            text = "停止自动化"
            setOnClickListener {
                running = false
                appendLog("已停止后续自动化任务")
                setStatus("已停止")
            }
        }
        val note = TextView(this).apply {
            text = "说明：测试坐标是示例值，需按手机分辨率调整。此版本先验证 Shizuku 触控链路，尚未包含游戏画面识别和自动升级策略。"
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        log = TextView(this).apply { textSize = 13f; text = "运行日志：\n" }
        layout.addView(title)
        layout.addView(status)
        layout.addView(authorize)
        layout.addView(testTap)
        layout.addView(testSwipe)
        layout.addView(stop)
        layout.addView(note)
        layout.addView(log)
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }

    private fun refreshStatus() {
        if (!Shizuku.pingBinder()) {
            setStatus("未检测到 Shizuku，请先启动 Shizuku")
        } else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setStatus("Shizuku 已授权")
        } else {
            setStatus("Shizuku 正在运行，应用尚未获得授权")
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku 未运行，请先启动 Shizuku")
            return
        }
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setStatus("已经获得 Shizuku 授权")
            return
        }
        Shizuku.requestPermission(1001)
    }

    private fun runShell(command: String) {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku 未运行")
            return
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setStatus("请先点击连接 / 请求 Shizuku 授权")
            return
        }
        executor.execute {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
                val code = process.waitFor()
                runOnUiThread {
                    appendLog("$ $command\n退出码：$code")
                    if (output.isNotBlank()) appendLog(output)
                    if (error.isNotBlank()) appendLog(error)
                    if (code == 0) setStatus("命令执行完成") else setStatus("命令执行失败，请检查日志")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setStatus("执行失败")
                    appendLog(e.toString())
                }
            }
        }
    }

    private fun setStatus(value: String) {
        if (::status.isInitialized) status.text = value
    }

    private fun appendLog(value: String) {
        if (::log.isInitialized) log.append("\n$value\n")
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        executor.shutdownNow()
        super.onDestroy()
    }
}
