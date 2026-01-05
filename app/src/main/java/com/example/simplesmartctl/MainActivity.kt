package net.bokumin45.simplesmartctl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.usb.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnRun: Button
    private lateinit var btnShowScore: Button
    private lateinit var btnLangSwitch: Button
    private lateinit var btnDisconnect: Button

    private val ACTION_USB_PERMISSION = "com.example.simplesmartctl.USB_PERMISSION"
    private var isReceiverRegistered = false
    private var cachedSmartData: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        btnRun = findViewById(R.id.btnRunSmart)
        btnShowScore = findViewById(R.id.btnShowScore)
        btnLangSwitch = findViewById(R.id.btnLangSwitch)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        btnRun.setOnClickListener {
            btnShowScore.visibility = View.GONE
            cachedSmartData = null
            tvLog.text = getString(R.string.log_start) + "\n"
            findAndConnectDevice()
        }

        btnShowScore.setOnClickListener {
            cachedSmartData?.let { data ->
                analyzeAndShowScore(data)
            }
        }

        btnLangSwitch.setOnClickListener {
            toggleLanguage()
        }

        btnDisconnect.setOnClickListener {
            disconnectDevice()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSafe()
    }

    private fun toggleLanguage() {
        val currentLang = resources.configuration.locales.get(0).language
        val newLang = if (currentLang == "ja") "en" else "ja"
        val locale = Locale(newLang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    private fun disconnectDevice() {
        unregisterReceiverSafe()
        cachedSmartData = null
        btnShowScore.visibility = View.GONE
        tvLog.text = ""
        updateStatus(getString(R.string.status_disconnected))
        appendLog(getString(R.string.msg_disconnected))
    }

    private fun unregisterReceiverSafe() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(usbReceiver)
            } catch (e: Exception) { }
            isReceiverRegistered = false
        }
    }

    private fun findAndConnectDevice() {
        try {
            val deviceList = usbManager.deviceList
            if (deviceList.isEmpty()) {
                updateStatus(getString(R.string.log_no_device))
                return
            }
            for (device in deviceList.values) {
                appendLog(getString(R.string.log_found, device.deviceName))
                if (hasStorageInterface(device)) {
                    updateStatus(getString(R.string.status_connected, device.deviceName))
                    checkPermission(device)
                    return
                } else {
                    appendLog(getString(R.string.log_skip))
                }
            }
            updateStatus(getString(R.string.status_not_found))
        } catch (e: Exception) {
            updateStatus(e.message ?: "Error")
        }
    }

    private fun hasStorageInterface(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        }
        return false
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun checkPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            runSmartCtl(device)
        } else {
            unregisterReceiverSafe()
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, pendingFlags)
            val filter = IntentFilter(ACTION_USB_PERMISSION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
            updateStatus(getString(R.string.status_permission_request))
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { runSmartCtl(it) }
                    } else {
                        updateStatus(getString(R.string.status_permission_denied))
                    }
                }
                unregisterReceiverSafe()
            }
        }
    }

    private fun runSmartCtl(device: UsbDevice) {
        updateStatus(getString(R.string.status_retrieving))
        Thread {
            var connection: UsbDeviceConnection? = null
            var intf: UsbInterface? = null

            try {
                connection = usbManager.openDevice(device)
                if (connection == null) return@Thread

                intf = findStorageInterface(device)
                if (intf == null) {
                    connection.close()
                    return@Thread
                }

                connection.claimInterface(intf, true)
                val (epIn, epOut) = findEndpoints(intf)

                if (epIn != null && epOut != null) {
                    val smartData = executeScsiTransaction(connection, epOut, epIn)
                    runOnUiThread {
                        if (smartData != null) {
                            cachedSmartData = smartData
                            updateStatus(getString(R.string.status_success))
                            appendLog(getString(R.string.log_success_click))
                            btnShowScore.visibility = View.VISIBLE
                            parseAndDisplaySmartLogOnly(smartData)
                        } else {
                            updateStatus(getString(R.string.status_failed))
                            appendLog(getString(R.string.log_fail_msg))
                        }
                    }
                }
                connection.releaseInterface(intf)
                connection.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    data class AttributeScore(
        val name: String,
        val rawValue: Long,
        val score: Int,
        val comment: String
    )

    private fun analyzeAndShowScore(data: ByteArray) {
        val scores = ArrayList<AttributeScore>()
        val attrMap = HashMap<Int, Long>()

        for (i in 0 until 30) {
            val offset = 2 + (i * 12)
            if (offset + 11 >= data.size) break
            val id = data[offset].toInt() and 0xFF
            if (id == 0) continue

            var raw: Long = 0
            for (k in 0 until 6) {
                raw += (data[offset + 5 + k].toLong() and 0xFF) shl (8 * k)
            }
            attrMap[id] = raw
        }

        val reallocated = attrMap[5] ?: 0
        val score5 = if (reallocated == 0L) 10 else if (reallocated < 10) 5 else 1
        scores.add(AttributeScore(getString(R.string.attr_reallocated), reallocated, score5,
            if(score5==10) getString(R.string.msg_normal) else getString(R.string.msg_physical_damage)))

        val pending = attrMap[197] ?: 0
        val score197 = if (pending == 0L) 10 else 1
        scores.add(AttributeScore(getString(R.string.attr_pending), pending, score197,
            if(score197==10) getString(R.string.msg_normal) else getString(R.string.msg_read_unstable)))

        val uncorrectable = attrMap[198] ?: 0
        val score198 = if (uncorrectable == 0L) 10 else 1
        scores.add(AttributeScore(getString(R.string.attr_uncorrectable), uncorrectable, score198,
            if(score198==10) getString(R.string.msg_normal) else getString(R.string.msg_data_corrupt)))

        val tempRaw = attrMap[194] ?: attrMap[190] ?: 0
        val temp = tempRaw and 0xFF
        val scoreTemp = when {
            temp == 0L -> 10
            temp < 45 -> 10
            temp < 55 -> 8
            temp < 60 -> 5
            else -> 2
        }
        scores.add(AttributeScore(getString(R.string.attr_temp), temp, scoreTemp, getString(R.string.unit_temp, temp)))

        val hours = attrMap[9] ?: 0
        val scoreHours = when {
            hours < 10000 -> 10
            hours < 20000 -> 9
            hours < 30000 -> 8
            hours < 40000 -> 7
            hours < 50000 -> 6
            else -> 5
        }
        scores.add(AttributeScore(getString(R.string.attr_hours), hours, scoreHours, getString(R.string.unit_hours, hours)))

        val criticalMinScore = listOf(score5, score197, score198).minOrNull() ?: 10
        val averageScore = scores.map { it.score }.average()
        val finalScore = if (criticalMinScore < 5) criticalMinScore.toDouble() else averageScore

        val sb = StringBuilder()
        sb.append(getString(R.string.score_total) + "\n")
        sb.append(getString(R.string.rank_format, finalScore) + "\n")
        sb.append(when {
            finalScore >= 9.0 -> getString(R.string.state_s)
            finalScore >= 7.0 -> getString(R.string.state_a)
            finalScore >= 5.0 -> getString(R.string.state_b)
            else -> getString(R.string.state_c)
        })
        sb.append("\n\n------------------------\n")

        for (item in scores) {
            sb.append("* ${item.name}\n")
            sb.append("  ${item.score}/10\n")
            sb.append("  ${item.rawValue} (${item.comment})\n\n")
        }

        val resultText = sb.toString()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title))
            .setMessage(resultText)
            .setPositiveButton(getString(R.string.dialog_close), null)
            .setNeutralButton(getString(R.string.dialog_copy)) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SMART Analysis", resultText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun findStorageInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE && intf.interfaceProtocol == 0x50) return intf
        }
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return intf
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    private fun findEndpoints(intf: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint?> {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                else epOut = ep
            }
        }
        return Pair(epIn, epOut)
    }

    private fun executeScsiTransaction(conn: UsbDeviceConnection, epOut: UsbEndpoint, epIn: UsbEndpoint): ByteArray? {
        val tag = 0xAA55AA55.toInt()
        val cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
        cbw.putInt(0x43425355)
        cbw.putInt(tag)
        cbw.putInt(512)
        cbw.put(0x80.toByte())
        cbw.put(0.toByte())
        cbw.put(16.toByte())

        val cdb = ByteArray(16)
        cdb[0] = 0x85.toByte()
        cdb[1] = (0x04 shl 1).toByte()
        cdb[2] = 0x2E.toByte()
        cdb[4] = 0xD0.toByte()
        cdb[6] = 0x01.toByte()
        cdb[10] = 0x4F.toByte()
        cdb[12] = 0xC2.toByte()
        cdb[14] = 0xB0.toByte()
        cbw.put(cdb)

        val cbwBytes = cbw.array()
        if (conn.bulkTransfer(epOut, cbwBytes, cbwBytes.size, 2000) < 0) return null

        val dataBuffer = ByteArray(512)
        val readLen = conn.bulkTransfer(epIn, dataBuffer, dataBuffer.size, 3000)

        val cswBuffer = ByteArray(13)
        conn.bulkTransfer(epIn, cswBuffer, cswBuffer.size, 2000)

        return if (readLen == 512) dataBuffer else null
    }

    private fun parseAndDisplaySmartLogOnly(data: ByteArray) {
        val sb = StringBuilder()
        sb.append(String.format("%-4s %-25s %4s %4s %12s\n", "ID", "Name", "Cur", "Wst", "Raw"))
        sb.append("-".repeat(60) + "\n")

        for (i in 0 until 30) {
            val offset = 2 + (i * 12)
            if (offset + 11 >= data.size) break

            val id = data[offset].toInt() and 0xFF
            if (id == 0) continue

            val current = data[offset + 3].toInt() and 0xFF
            val worst = data[offset + 4].toInt() and 0xFF
            var raw: Long = 0
            for (k in 0 until 6) {
                raw += (data[offset + 5 + k].toLong() and 0xFF) shl (8 * k)
            }

            val name = getAttributeName(id)
            sb.append(String.format("%-4d %-25s %4d %4d %12d\n", id, name, current, worst, raw))
        }
        appendLog(sb.toString())
    }

    private fun getAttributeName(id: Int): String {
        return when (id) {
            1 -> "Read Error Rate"
            2 -> "Throughput Performance"
            3 -> "Spin-Up Time"
            4 -> "Start/Stop Count"
            5 -> "Reallocated Sectors"
            7 -> "Seek Error Rate"
            8 -> "Seek Time Performance"
            9 -> "Power-On Hours"
            10 -> "Spin Retry Count"
            12 -> "Power Cycle Count"
            167 -> "SSD Protect Mode"
            168 -> "Phy Error Count"
            169 -> "Bad Block Count"
            171 -> "Program Fail Count"
            172 -> "Erase Fail Count"
            173 -> "Wear Leveling Count"
            174 -> "Unexpected Power Loss"
            187 -> "Reported Uncorrectable"
            189 -> "High Fly Writes"
            190 -> "Airflow Temperature"
            191 -> "G-Sense Error Rate"
            192 -> "Power-Off Retract"
            193 -> "Load/Unload Cycle"
            194 -> "Temperature"
            195 -> "Hardware ECC Recovered"
            196 -> "Reallocation Event"
            197 -> "Current Pending Sector"
            198 -> "Uncorrectable Sector"
            199 -> "UDMA CRC Error"
            200 -> "Multi-Zone Error Rate"
            230 -> "Drive Life Prot Status"
            231 -> "SSD Life Left"
            233 -> "Media Wearout Indicator"
            240 -> "Head Flying Hours"
            241 -> "Total Host Writes"
            242 -> "Total Host Reads"
            else -> "Attr $id"
        }
    }

    private fun updateStatus(text: String) {
        runOnUiThread { tvStatus.text = text }
    }

    private fun appendLog(text: String) {
        runOnUiThread { tvLog.append(text + "\n") }
    }
}