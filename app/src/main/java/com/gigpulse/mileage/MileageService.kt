package com.gigpulse.mileage
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
class MileageService : Service() {
  private lateinit var client: FusedLocationProviderClient
  private var last: Location? = null
  private var miles: Double = 0.0
  override fun onCreate() {
    super.onCreate()
    client = LocationServices.getFusedLocationProviderClient(this)
    startForeground(1001, buildNotif("Tracking miles… 0.0 mi"))
    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).setMinUpdateDistanceMeters(25f).build()
    client.requestLocationUpdates(req, callback, mainLooper)
  }
  private val callback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val loc = result.lastLocation ?: return
      last?.let { prev ->
        miles += prev.distanceTo(loc) / 1609.344
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1001, buildNotif("Tracking miles… ${"%.1f".format(miles)} mi"))
      }
      last = loc
    }
  }
  private fun buildNotif(text: String) = NotificationCompat.Builder(this, ensureChannel())
    .setContentTitle("GigPulse").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
  private fun ensureChannel(): String {
    val id = "gigpulse_mileage"
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(NotificationChannel(id, "Mileage", NotificationManager.IMPORTANCE_LOW))
    }
    return id
  }
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onDestroy() { client.removeLocationUpdates(callback); super.onDestroy() }
}
