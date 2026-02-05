package com.xau.alerter

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        // Get trigger info from intent or from AlarmPlayer state
        val channelName = intent.getStringExtra("channel") ?: AlarmPlayer.triggerChannel
        val notifText = intent.getStringExtra("text") ?: AlarmPlayer.triggerText

        findViewById<TextView>(R.id.tvChannel).text = "#$channelName"
        findViewById<TextView>(R.id.tvNotifText).text = notifText

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            AlarmPlayer.stop(this)
            finish()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val channelName = intent.getStringExtra("channel") ?: AlarmPlayer.triggerChannel
        val notifText = intent.getStringExtra("text") ?: AlarmPlayer.triggerText
        findViewById<TextView>(R.id.tvChannel).text = "#$channelName"
        findViewById<TextView>(R.id.tvNotifText).text = notifText
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Prevent dismissal — user must press STOP
    }
}
