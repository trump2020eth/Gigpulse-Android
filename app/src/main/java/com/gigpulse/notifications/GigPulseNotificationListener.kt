package com.gigpulse.notifications
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
class GigPulseNotificationListener : NotificationListenerService() {
  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val pkg = sbn.packageName ?: return
    val extras = sbn.notification.extras
    val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
    val text  = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
    val intent = Intent("com.gigpulse.NOTIF").apply {
      putExtra("package", pkg); putExtra("title", title); putExtra("text", text)
    }
    sendBroadcast(intent)
  }
}
