package com.gigpulse
import android.Manifest
import android.app.Application
import android.content.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gigpulse.ui.theme.GigTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gig_settings")
data class Hotspot(val id: Long = System.currentTimeMillis(), val name: String, val lat: Double, val lng: Double, val radiusMeters: Int = 300, val isBusy: Boolean = false, val platform: String = "DoorDash")
data class Trip(val id: Long = System.currentTimeMillis(), val miles: Double, val startedAt: Long, val endedAt: Long)
data class Earning(val id: Long = System.currentTimeMillis(), val platform: String, val amount: Double, val at: Long = System.currentTimeMillis())
class AppVM(private val app: Application) : ViewModel() {
  var hotspots = mutableStateListOf<Hotspot>()
  var trips = mutableStateListOf<Trip>()
  var earnings = mutableStateListOf<Earning>()
  var mpg by mutableStateOf(24.0)
  var fuel by mutableStateOf(4.79)
  var sms by mutableStateOf("")
  init {
    viewModelScope.launch {
      val p = app.dataStore.data.first()
      mpg = p[doublePreferencesKey("mpg")] ?: 24.0
      fuel = p[doublePreferencesKey("fuel")] ?: 4.79
      sms  = p[stringPreferencesKey("sms")] ?: ""
    }
  }
  fun savePrefs() = viewModelScope.launch {
    app.dataStore.edit {
      it[doublePreferencesKey("mpg")] = mpg
      it[doublePreferencesKey("fuel")] = fuel
      it[stringPreferencesKey("sms")] = sms
    }
  }
  private val busy = listOf("busy","very busy","dash now","peak pay","surge","quest")
  fun onExternalNotification(pkg: String, title: String, text: String) {
    val payload = (title + " " + text).lowercase()
    val isBusy = busy.any { payload.contains(it) }
    if (pkg.contains("dash", true)) hotspots.replaceAll { if (it.platform.equals("DoorDash", true)) it.copy(isBusy = isBusy) else it }
    if (pkg.contains("uber", true)) hotspots.replaceAll { if (it.platform.equals("UberEats", true)) it.copy(isBusy = isBusy) else it }
  }
}
class MainActivity : ComponentActivity() {
  private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
  private val locPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == "com.gigpulse.NOTIF") {
        vm?.onExternalNotification(
          intent.getStringExtra("package").orEmpty(),
          intent.getStringExtra("title").orEmpty(),
          intent.getStringExtra("text").orEmpty()
        )
      }
    }
  }
  private var vm: AppVM? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    registerReceiver(receiver, IntentFilter("com.gigpulse.NOTIF"))
    setContent {
      GigTheme {
        val nav = rememberNavController()
        val v = remember { AppVM(application) }
        vm = v
        Scaffold(bottomBar = {
          NavigationBar {
            listOf("Dashboard","Hotspots","Trips","Earnings","Settings").forEach { label ->
              NavigationBarItem(selected = false, onClick = { nav.navigate(label) }, label = { Text(label) }, icon = { Icon(Icons.Filled.Circle, null) })
            }
          }
        }) { padding ->
          NavHost(nav, "Dashboard", Modifier.padding(padding)) {
            composable("Dashboard") { DashboardScreen(v) }
            composable("Hotspots") { HotspotsScreen(v) }
            composable("Trips") { TripsScreen(v) }
            composable("Earnings") { EarningsScreen(v) }
            composable("Settings") { SettingsScreen(v) }
          }
        }
      }
    }
  }
  override fun onDestroy() { super.onDestroy(); unregisterReceiver(receiver) }
}
@Composable fun DashboardScreen(vm: AppVM) {
  val totalE = remember(vm.earnings) { vm.earnings.sumOf { it.amount } }
  val miles = remember(vm.trips) { vm.trips.sumOf { it.miles } }
  val gas = if (vm.mpg>0) (miles/vm.mpg)*vm.fuel else 0.0
  val net = totalE - gas
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("GigPulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Stat("Earnings", "$" + "%.2f".format(totalE), Modifier.weight(1f))
      Stat("Miles", "%.1f".format(miles) + " mi", Modifier.weight(1f))
      Stat("Net", "$" + "%.2f".format(net), Modifier.weight(1f))
    }
    Text("Hotspots")
    LazyColumn {
      items(vm.hotspots) { h ->
        ListItem(headlineContent={ Text(h.name) }, supportingContent={ Text("${h.platform} • ${h.radiusMeters}m • ${"%.4f".format(h.lat)}, ${"%.4f".format(h.lng)}") }, trailingContent={ Text(if(h.isBusy) "BUSY" else "CALM") })
        Divider()
      }
    }
  }
}
@Composable fun Stat(title:String, value:String, modifier: Modifier=Modifier){
  Card(modifier){ Column(Modifier.padding(12.dp)){ Text(title, fontWeight=FontWeight.SemiBold); Text(value, style=MaterialTheme.typography.headlineSmall) } }
}
@Composable fun HotspotsScreen(vm: AppVM){
  var name by remember{ mutableStateOf("") }
  var lat by remember{ mutableStateOf(36.2077) }
  var lng by remember{ mutableStateOf(-119.3473) }
  var radius by remember{ mutableStateOf(300) }
  var platform by remember{ mutableStateOf("DoorDash") }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Hotspots", style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(name, {name=it}, label={Text("Name")})
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ OutlinedTextField(lat.toString(), {lat=it.toDoubleOrNull()?:0.0}, label={Text("Lat")}, modifier=Modifier.weight(1f)); OutlinedTextField(lng.toString(), {lng=it.toDoubleOrNull()?:0.0}, label={Text("Lng")}, modifier=Modifier.weight(1f)) }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ OutlinedTextField(radius.toString(), {radius=it.toIntOrNull()?:300}, label={Text("Radius (m)")}); FilterChip(selected = platform=="DoorDash", onClick={platform="DoorDash"}, label={Text("DoorDash")}); FilterChip(selected = platform=="UberEats", onClick={platform="UberEats"}, label={Text("UberEats")}) }
    Button(onClick={ if(name.isNotBlank()) vm.hotspots.add(Hotspot(name=name, lat=lat, lng=lng, radiusMeters=radius, platform=platform)); name=""; radius=300 }){ Text("Save Hotspot") }
    Divider()
    LazyColumn{ items(vm.hotspots){ h-> ListItem(headlineContent={Text(h.name)}, supportingContent={Text("${h.platform}")}, trailingContent={ Switch(checked=h.isBusy, onCheckedChange={c-> val i=vm.hotspots.indexOfFirst{it.id==h.id}; if(i!=-1) vm.hotspots[i]=h.copy(isBusy=c)}) }); Divider() } }
  }
}
@Composable fun TripsScreen(vm: AppVM){
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Trips / Miles", style=MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ Button(onClick={ /* TODO start MileageService */ }){ Text("Start Tracking") }; OutlinedButton(onClick={ /* stop */ }){ Text("Stop") } }
    LazyColumn{ items(vm.trips){ t-> ListItem(headlineContent={Text("${"%.1f".format(t.miles)} mi")}, supportingContent={Text("${t.startedAt} → ${t.endedAt}")}); Divider() } }
  }
}
@Composable fun EarningsScreen(vm: AppVM){
  var amount by remember{ mutableStateOf("") }
  var platform by remember{ mutableStateOf("DoorDash") }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Earnings", style=MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ FilterChip(selected=platform=="DoorDash", onClick={platform="DoorDash"}, label={Text("DoorDash")}); FilterChip(selected=platform=="UberEats", onClick={platform="UberEats"}, label={Text("UberEats")}) }
    OutlinedTextField(amount, {amount=it}, label={Text("Amount ($)")})
    Button(onClick={ val v=amount.toDoubleOrNull()?:0.0; if(v>0){ vm.earnings.add(Earning(platform=platform, amount=v)); amount="" } }){ Text("Add Earning") }
    Divider()
    LazyColumn{ items(vm.earnings){ e-> ListItem(headlineContent={Text("$${"%.2f".format(e.amount)}")}, supportingContent={Text(e.platform)}); Divider() } }
  }
}
@Composable fun SettingsScreen(vm: AppVM){
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Settings", style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(vm.mpg.toString(), { vm.mpg = it.toDoubleOrNull()?:0.0; vm.savePrefs() }, label={Text("MPG")})
    OutlinedTextField(vm.fuel.toString(), { vm.fuel = it.toDoubleOrNull()?:0.0; vm.savePrefs() }, label={Text("$ per gallon")})
    Text("Enable Notification Access for GigPulse in Android Settings → Notification access.")
  }
}
