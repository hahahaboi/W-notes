package com.tv.wnotes.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private var countDownTimer: CountDownTimer? = null

    init {
        createNotificationChannel()
    }

    fun startTimer(durationMillis: Long) {
        countDownTimer?.cancel()
        _isTimerRunning.value = true
        
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                _isTimerRunning.value = false
                endSession()
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        _isTimerRunning.value = false
    }

    private fun endSession() {
        // Vibrate
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        // Show Notification
        showNotification()

        // Go to Home Screen
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(startMain)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Session Channel"
            val descriptionText = "Notifications for session completion"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("SESSION_COMPLETE", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val builder = NotificationCompat.Builder(getApplication(), "SESSION_COMPLETE")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("W Notes")
            .setContentText("Session complete")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager: NotificationManager =
            getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}
