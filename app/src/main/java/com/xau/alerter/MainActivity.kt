package com.xau.alerter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchMonitoring: SwitchMaterial
    private lateinit var etChannels: EditText
    private lateinit var etKeywords: EditText
    private lateinit var tvAlarmSound: TextView
    private lateinit var tvNotifStatus: TextView

    private lateinit var switchNtfy: SwitchMaterial
    private lateinit var tvNtfyStatus: TextView
    private lateinit var etNtfyServer: EditText
    private lateinit var etNtfyTopic: EditText

    private val ntfyStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(NtfyService.EXTRA_STATUS) ?: return
            updateNtfyStatusText(status)
        }
    }

    private val soundPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        PrefsManager.setAlarmUri(this, uri)
        updateAlarmSoundLabel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchMonitoring = findViewById(R.id.switchMonitoring)
        etChannels = findViewById(R.id.etChannels)
        etKeywords = findViewById(R.id.etKeywords)
        tvAlarmSound = findViewById(R.id.tvAlarmSound)
        tvNotifStatus = findViewById(R.id.tvNotifStatus)

        switchNtfy = findViewById(R.id.switchNtfy)
        tvNtfyStatus = findViewById(R.id.tvNtfyStatus)
        etNtfyServer = findViewById(R.id.etNtfyServer)
        etNtfyTopic = findViewById(R.id.etNtfyTopic)

        // Load saved values
        switchMonitoring.isChecked = PrefsManager.isEnabled(this)
        etChannels.setText(PrefsManager.getChannelsRaw(this))
        etKeywords.setText(PrefsManager.getKeywordsRaw(this))
        updateAlarmSoundLabel()

        // Load ntfy settings
        switchNtfy.isChecked = PrefsManager.isNtfyEnabled(this)
        etNtfyServer.setText(PrefsManager.getNtfyServer(this))
        etNtfyTopic.setText(PrefsManager.getNtfyTopic(this))
        val savedStatus = getSharedPreferences("ntfy_status", MODE_PRIVATE)
            .getString("status", NtfyService.STATUS_DISCONNECTED)!!
        updateNtfyStatusText(if (switchNtfy.isChecked) savedStatus else NtfyService.STATUS_DISCONNECTED)

        // Monitoring toggle
        switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            PrefsManager.setEnabled(this, isChecked)
            syncNtfyService()
        }

        // ntfy toggle
        switchNtfy.setOnCheckedChangeListener { _, isChecked ->
            PrefsManager.setNtfyEnabled(this, isChecked)
            syncNtfyService()
        }

        // Select alarm sound
        findViewById<Button>(R.id.btnPickSound).setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    PrefsManager.getAlarmUri(this@MainActivity)
                )
            }
            soundPicker.launch(intent)
        }

        // Test alarm
        findViewById<Button>(R.id.btnTestAlarm).setOnClickListener {
            if (AlarmPlayer.isPlaying) {
                AlarmPlayer.stop(this)
                Toast.makeText(this, "Alarm stopped", Toast.LENGTH_SHORT).show()
            } else {
                AlarmPlayer.start(this, "test", "This is a test alarm")
            }
        }

        // Grant notification access
        findViewById<Button>(R.id.btnNotifAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Disable battery optimization
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotifStatus()

        val filter = IntentFilter(NtfyService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ntfyStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ntfyStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
        try {
            unregisterReceiver(ntfyStatusReceiver)
        } catch (_: Exception) {}
    }

    private fun saveSettings() {
        val channels = etChannels.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        PrefsManager.setChannels(this, channels)
        PrefsManager.setKeywords(this, etKeywords.text.toString())
        PrefsManager.setNtfyServer(this, etNtfyServer.text.toString().trim())
        PrefsManager.setNtfyTopic(this, etNtfyTopic.text.toString().trim())
    }

    private fun syncNtfyService() {
        val shouldRun = PrefsManager.isEnabled(this) && PrefsManager.isNtfyEnabled(this)
        if (shouldRun) {
            saveSettings() // persist server/topic before starting
            val intent = Intent(this, NtfyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(Intent(this, NtfyService::class.java))
            updateNtfyStatusText(NtfyService.STATUS_DISCONNECTED)
        }
    }

    private fun updateNtfyStatusText(status: String) {
        when (status) {
            NtfyService.STATUS_CONNECTED -> {
                tvNtfyStatus.text = "ntfy: Connected"
                tvNtfyStatus.setTextColor(getColor(R.color.green))
            }
            NtfyService.STATUS_CONNECTING -> {
                tvNtfyStatus.text = "ntfy: Connecting..."
                tvNtfyStatus.setTextColor(getColor(R.color.blue))
            }
            else -> {
                tvNtfyStatus.text = "ntfy: Disabled"
                tvNtfyStatus.setTextColor(getColor(R.color.alarm_red))
            }
        }
    }

    private fun updateAlarmSoundLabel() {
        val uri = PrefsManager.getAlarmUri(this)
        if (uri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                tvAlarmSound.text = ringtone?.getTitle(this) ?: "Custom sound"
            } catch (e: Exception) {
                tvAlarmSound.text = "Custom sound"
            }
        } else {
            tvAlarmSound.text = "Default alarm"
        }
    }

    private fun updateNotifStatus() {
        val enabled = isNotificationAccessEnabled()
        if (enabled) {
            tvNotifStatus.text = "Notification access: GRANTED"
            tvNotifStatus.setTextColor(getColor(R.color.green))
        } else {
            tvNotifStatus.text = "Notification access: NOT GRANTED (tap button below)"
            tvNotifStatus.setTextColor(getColor(R.color.alarm_red))
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val cn = ComponentName(this, DiscordNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }
}
