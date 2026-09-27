package com.outfitku.app.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.outfitku.app.R
import com.outfitku.app.data.Repo
import com.outfitku.app.ui.MainActivity

/** Kanal + pembangun notifikasi (PRD §10.3). */
object Notifier {

    const val CH_LAUNDRY = "laundry_reminder"
    const val CH_CONFIRM = "outfit_confirm"
    const val CH_LOG = "outfit_log"
    const val CH_TEST = "test"

    const val A_WASH = "WASH"
    const val A_SNOOZE = "SNOOZE"
    const val A_YA = "YA"
    const val A_KOREKSI = "KOREKSI"
    const val A_LIBUR = "LIBUR"

    private const val ACT_ICON = android.R.drawable.ic_menu_send

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_LAUNDRY) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CH_LAUNDRY, "Pengingat cuci", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pengingat nyuci berulang sampai dicuci" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CONFIRM, "Konfirmasi outfit", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOG, "Pengingat catat outfit", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TEST, "Tes notifikasi", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, ActionReceiver::class.java).putExtra("action", action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun builder(context: Context, channel: String, title: String, body: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentIntent(context))

    fun canSend(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notifyAutoConfirm(context: Context, names: List<String>) {
        if (!canSend(context)) return
        val body = if (names.isEmpty()) "Belum ada catatan outfit hari ini."
        else "Anggap pakai: ${names.joinToString(" + ")}?"
        val n = builder(context, CH_CONFIRM, "Belum catat outfit hari ini", body)
            .addAction(0, "Ya", actionIntent(context, A_YA))
            .addAction(0, "Koreksi", actionIntent(context, A_KOREKSI))
            .addAction(0, "Libur", actionIntent(context, A_LIBUR))
            .build()
        NotificationManagerCompat.from(context).notify(100, n)
    }

    fun notifyLaundry(context: Context, title: String, body: String) {
        if (!canSend(context)) return
        val n = builder(context, CH_LAUNDRY, title, body)
            .addAction(0, "Sudah Nyuci", actionIntent(context, A_WASH))
            .addAction(0, "Besok", actionIntent(context, A_SNOOZE))
            .build()
        NotificationManagerCompat.from(context).notify(200, n)
    }

    fun notifyTest(context: Context) {
        ensureChannels(context)
        val n = builder(context, CH_TEST, "Notifikasi tes ✓", "OutfitKu siap mengingatkanmu tepat waktu.")
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(999, n)
    }
}
