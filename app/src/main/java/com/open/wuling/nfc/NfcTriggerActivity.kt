package com.open.wuling.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.WulingTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class NfcPhase { WORKING, SUCCESS, FAILURE }

private data class NfcUiState(
    val phase: NfcPhase = NfcPhase.WORKING,
    val title: String = "正在识别标签…",
    val subtitle: String = "请勿移开手机"
)

/**
 * NFC 触发入口（v67）。
 *
 * 碰已绑定的标签时，系统 NDEF 派发带着标签内容拉起本页：
 *  - 绑定模式（设置页发起，[NfcBindRequest.pending]=true）：把新密钥写进标签后回设置页；
 *  - 触发模式：校验密钥 → 交 [NfcCarController] 反向下发解锁/锁车 → 展示结果自动关闭。
 *
 * launchMode=singleTask：结果页展示期间再次碰标签不会叠出第二个实例，
 * 天然形成一层去抖。平台限制：普通 NDEF 标签必须亮屏才能读取，息屏触碰不派发。
 */
@AndroidEntryPoint
class NfcTriggerActivity : ComponentActivity() {

    @Inject lateinit var controller: NfcCarController

    private val ui = MutableStateFlow(NfcUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WulingTheme {
                NfcTriggerScreen(state = ui.collectAsState().value)
            }
        }
        handleIntent(intent)
    }

    private fun setUi(phase: NfcPhase, title: String, subtitle: String) {
        ui.value = NfcUiState(phase, title, subtitle)
    }

    private fun finishLater(delayMs: Long) {
        lifecycleScope.launch { delay(delayMs); finish() }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) { finish(); return }
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            // TECH/TAG 派发（未按 NDEF 过滤到）不处理，避免误触
            finish(); return
        }

        // ===== 绑定模式：设置页点了「绑定/重新绑定」，下一个标签拿来写入 =====
        val bindSecret = controller.consumeBindingRequest()
        if (bindSecret != null) {
            val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
            if (tag == null) {
                setUi(NfcPhase.FAILURE, "无法读取标签", "请换一张支持 NDEF 的标签（NTAG213/215/216）")
                finishLater(3200); return
            }
            setUi(NfcPhase.WORKING, "正在写入标签…", "请保持手机贴紧标签直到完成")
            lifecycleScope.launch {
                if (writeToTag(tag, bindSecret)) {
                    controller.onBindSuccess(bindSecret)
                    setUi(NfcPhase.SUCCESS, "绑定成功", "以后碰一下这个标签即可切换解锁/锁车")
                    finishLater(2400)
                } else {
                    setUi(NfcPhase.FAILURE, "写入失败", "标签可能只读或已写满，请换一张试试")
                    finishLater(3200)
                }
            }
            return
        }

        // ===== 触发模式：校验密钥后执行切换 =====
        val secret = readSecret(intent)
        if (secret == null) {
            setUi(NfcPhase.FAILURE, "无法读取标签内容", "标签数据缺失或格式不符，请重新绑定")
            finishLater(3200); return
        }
        val prefs = controller.prefs
        if (!prefs.isBound || secret != prefs.boundSecret) {
            setUi(NfcPhase.FAILURE, "标签未绑定", "请在「我的 → 设置 → NFC 车控」绑定此标签")
            finishLater(3200); return
        }
        if (!prefs.enabled) {
            setUi(NfcPhase.FAILURE, "NFC 车控未启用", "请在「我的 → 设置 → NFC 车控」打开开关")
            finishLater(3200); return
        }

        setUi(NfcPhase.WORKING, "正在发送指令…", "锁车状态碰=解锁，解锁状态碰=锁车")
        lifecycleScope.launch {
            when (val outcome = controller.executeToggle()) {
                is NfcOutcome.Success -> {
                    setUi(
                        NfcPhase.SUCCESS,
                        if (outcome.unlocked) "已解锁" else "已锁车",
                        if (outcome.unlocked) "车辆门锁已打开" else "车辆门锁已锁好"
                    )
                    finishLater(1900)
                }
                is NfcOutcome.Failure -> {
                    setUi(NfcPhase.FAILURE, "操作失败", outcome.message)
                    finishLater(3600)
                }
            }
        }
    }

    /** 从 NDEF_DISCOVERED intent 里取出我们 MIME 类型记录的密钥 */
    private fun readSecret(intent: Intent): String? = try {
        val raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return null
        val msg = raw.firstOrNull() as? NdefMessage ?: return null
        val record = msg.records.firstOrNull {
            it.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(it.type, Charsets.US_ASCII) == NfcCarController.MIME_TYPE
        } ?: return null
        String(record.payload, Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** 把密钥写成自定义 MIME 的 NDEF 记录；未格式化的标签尝试 NdefFormatable */
    private fun writeToTag(tag: Tag, secret: String): Boolean {
        return try {
            val record = NdefRecord(
                NdefRecord.TNF_MIME_MEDIA,
                NfcCarController.MIME_TYPE.toByteArray(Charsets.US_ASCII),
                ByteArray(0),
                secret.toByteArray(Charsets.UTF_8)
            )
            val message = NdefMessage(arrayOf(record, NdefRecord.createApplicationRecord(packageName)))
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) return false
                if (ndef.maxSize < message.toByteArray().size) return false
                ndef.writeNdefMessage(message)
                ndef.close()
                true
            } else {
                NdefFormatable.get(tag)?.let { fmt ->
                    fmt.connect()
                    fmt.format(message)
                    fmt.close()
                    true
                } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
private fun NfcTriggerScreen(state: NfcUiState) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { (context as? android.app.Activity)?.finish() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state.phase) {
                NfcPhase.WORKING -> CircularProgressIndicator(modifier = Modifier.size(64.dp))
                NfcPhase.SUCCESS -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(72.dp)
                )
                NfcPhase.FAILURE -> Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = state.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = when (state.phase) {
                    NfcPhase.SUCCESS -> PrimaryGreen
                    NfcPhase.FAILURE -> MaterialTheme.colorScheme.error
                    NfcPhase.WORKING -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
            Spacer(Modifier.height(30.dp))
            Text(
                text = "点按任意位置关闭",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}
