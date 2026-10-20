package cn.shakeguard.app.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import cn.shakeguard.app.data.GuardRepository
import cn.shakeguard.app.model.AppPolicy
import cn.shakeguard.app.model.PrivilegeState
import cn.shakeguard.app.ui.I18n
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object ShizukuSensorController {
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restoreRunnables = ConcurrentHashMap<String, Runnable>()

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    fun inspectPrivilegeState(context: Context): PrivilegeState {
        val shizukuInstalled = try {
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        val shizukuBinderAlive = checkShizukuBinderViaReflection()
        val rootAvail = cachedRootAvailable ?: checkSuBinary().also { cachedRootAvailable = it }

        val backend = when {
            shizukuBinderAlive -> I18n.t(
                "Shizuku 已连接 (AppOps 实时阻断就绪)",
                "Shizuku Connected (AppOps Live Sensor Blocker Ready)"
            )
            shizukuInstalled -> I18n.t(
                "已安装 Shizuku (待在 Shizuku 内授权)",
                "Shizuku Installed (Grant Permission in Shizuku App)"
            )
            rootAvail -> I18n.t(
                "Root Shell 已就绪 (AppOps 实时阻断就绪)",
                "Root Shell Ready (AppOps Live Sensor Blocker Ready)"
            )
            else -> I18n.t(
                "免 Root 标准模式 (L1+L2 生效，支持生成 ADB 传感器禁用脚本)",
                "Rootless Mode (L1+L2 Active, Supports ADB Sensor Cutoff Script)"
            )
        }

        return PrivilegeState(
            shizukuInstalled = shizukuInstalled,
            shizukuBinderAlive = shizukuBinderAlive,
            rootAvailable = rootAvail,
            activeBackend = backend,
            canExecutePrivileged = shizukuBinderAlive || rootAvail
        )
    }

    private fun checkShizukuBinderViaReflection(): Boolean {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val pingMethod = cls.getMethod("pingBinder")
            pingMethod.invoke(null) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun checkSuBinary(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/magisk/busybox")
        return paths.any { File(it).exists() }
    }

    fun onAppEnteredForeground(context: Context, packageName: String) {
        if (!GuardRepository.masterShieldEnabled || !GuardRepository.l3ShizukuSensorCutoff) return
        val policy = GuardRepository.findPolicyForPackage(packageName)
        val durationSec = when {
            policy?.permanentBlockSensor == true -> -1
            policy != null -> policy.blockSensorWindowSec
            else -> GuardRepository.l3DefaultCutoffSec
        }
        if (durationSec == 0) return

        val state = inspectPrivilegeState(context)
        if (!state.canExecutePrivileged) return

        val appLabel = GuardRepository.getAppLabel(packageName)
        if (durationSec < 0) {
            setSensorAppOpAsync(packageName, ignore = true) { ok ->
                if (ok) {
                    GuardRepository.recordInterception(
                        layer = "L3",
                        sourceApp = appLabel,
                        targetDesc = "OP_SENSORS (Accelerometer/Gyroscope)",
                        actionTaken = I18n.t("永久切断传感器权限", "Permanent Sensor Cutoff"),
                        detail = "cmd appops set $packageName SENSORS ignore"
                    )
                }
            }
        } else {
            blockSensorsTemporarily(packageName, appLabel, durationSec)
        }
    }

    fun blockSensorsTemporarily(packageName: String, appLabel: String, seconds: Int) {
        restoreRunnables.remove(packageName)?.let { mainHandler.removeCallbacks(it) }

        setSensorAppOpAsync(packageName, ignore = true) { ok ->
            if (ok) {
                GuardRepository.recordInterception(
                    layer = "L3",
                    sourceApp = appLabel,
                    targetDesc = I18n.t("OP_SENSORS 冻结 ${seconds}s", "OP_SENSORS Frozen ${seconds}s"),
                    actionTaken = I18n.t("开屏期禁用传感器", "Splash Sensor Cutoff"),
                    detail = I18n.t(
                        "已切断 $packageName 陀螺仪/加速度计 ${seconds} 秒，度过开屏后自动恢复",
                        "Cut off $packageName accelerometer/gyroscope for ${seconds}s during splash; auto-restored after"
                    )
                )
            }
        }

        val restoreTask = Runnable {
            restoreRunnables.remove(packageName)
            val policy = GuardRepository.findPolicyForPackage(packageName)
            if (policy?.permanentBlockSensor != true) {
                setSensorAppOpAsync(packageName, ignore = false, onComplete = null)
            }
        }
        restoreRunnables[packageName] = restoreTask
        mainHandler.postDelayed(restoreTask, seconds * 1000L)
    }

    fun setSensorAppOpAsync(packageName: String, ignore: Boolean, onComplete: ((Boolean) -> Unit)?) {
        val mode = if (ignore) "ignore" else "allow"
        val cmd = "cmd appops set $packageName SENSORS $mode"
        executor.execute {
            val ok = executePrivilegedCommand(cmd)
            if (onComplete != null) {
                mainHandler.post { onComplete(ok) }
            }
        }
    }

    private fun executePrivilegedCommand(cmd: String): Boolean {
        try {
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProc = shizukuCls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProc.isAccessible = true
            val proc = newProc.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
            if (proc != null) {
                val code = proc.waitFor()
                if (code == 0) return true
            }
        } catch (_: Throwable) {
        }

        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun generateAdbCommandScript(policies: List<AppPolicy>): String {
        val sb = StringBuilder()
        if (I18n.isEn) {
            sb.appendLine("# ShakeGuard — System-Level Sensor Permission Cutoff Script")
            sb.appendLine("# Purpose: Disable accelerometer/gyroscope (OP_SENSORS) for user-selected apps via Android AppOps")
            sb.appendLine("# Usage: Run via USB ADB or Wireless Debugging (LADB / Shizuku):")
        } else {
            sb.appendLine("# 摇一摇守卫 (ShakeGuard) — 系统级传感器权限禁用脚本")
            sb.appendLine("# 作用：通过 Android 原生 AppOps 禁止高危应用读取陀螺仪与加速度计，防止运动传感器在颠簸或震颤时被误触发")
            sb.appendLine("# 用法：电脑连接手机开启 USB 调试（或手机端使用甲壳虫ADB/无线调试）执行以下命令：")
        }
        sb.appendLine()
        for (p in policies.take(15)) {
            sb.appendLine("# [${I18n.translateAppName(p.appName)}] (${I18n.translateCategory(p.category)})")
            sb.appendLine("adb shell cmd appops set ${p.packageName} SENSORS ignore")
        }
        sb.appendLine()
        if (I18n.isEn) {
            sb.appendLine("# To restore sensors for a specific app (e.g., navigation), change 'ignore' to 'allow':")
        } else {
            sb.appendLine("# 如需恢复某个应用的传感器（如地图导航），将末尾 ignore 改为 allow 即可：")
        }
        sb.appendLine("# adb shell cmd appops set com.example.demo.app SENSORS allow")
        return sb.toString()
    }
}
