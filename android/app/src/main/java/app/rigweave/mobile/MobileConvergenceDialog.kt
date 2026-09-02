package app.rigweave.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class MobileSyncTab(val label:String){ACCOUNT("ACCOUNT"),STATIONS("STATIONS"),DEVICES("DEVICES"),SYNC("SYNC"),CONFLICTS("CONFLICTS")}

@Composable
fun MobileConvergenceDialog(database:QsoDatabase,dismiss:()->Unit){
    var tab by remember{mutableStateOf(MobileSyncTab.ACCOUNT)}
    var dashboard by remember{mutableStateOf(MobileSyncStore(database).dashboard())}
    val identity=remember{runCatching{MobileDeviceIdentity()}.getOrNull()}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(color=Color(0xFF111519),modifier=Modifier.fillMaxSize().padding(12.dp)){
            Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Column(Modifier.weight(1f)){Text("M9 · MOBILE DATA CONVERGENCE",color=Color(0xFFE9A72B),fontSize=23.sp,fontWeight=FontWeight.Black);Text("Local Hub stays canonical · encrypted cloud is opt-in",color=Color(0xFFA5ADB2))}
                    OutlinedButton({dashboard=MobileSyncStore(database).dashboard()}){Text("REFRESH")}
                    Button(dismiss){Text("DONE")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){MobileSyncTab.entries.forEach{item->FilterChip(selected=tab==item,onClick={tab=item},label={Text(item.label)},modifier=Modifier.weight(1f).heightIn(min=44.dp))}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(1.dp)){Metric("MODE","DIRECT STATION",Color(0xFFE9A72B),Modifier.weight(1f));Metric("PENDING",dashboard.pending.toString(),Color.White,Modifier.weight(1f));Metric("CONFLICTS",dashboard.conflicts.toString(),if(dashboard.conflicts==0)Color(0xFF42C77B)else Color(0xFFE4544D),Modifier.weight(1f));Metric("DOMAINS",dashboard.domains.toString(),Color.White,Modifier.weight(1f))}
                Box(Modifier.fillMaxSize().background(Color(0xFF1B2228)).padding(12.dp)){
                    when(tab){
                        MobileSyncTab.ACCOUNT->AccountPanel(identity)
                        MobileSyncTab.STATIONS->StationsPanel(dashboard)
                        MobileSyncTab.DEVICES->DevicesPanel(dashboard,identity)
                        MobileSyncTab.SYNC->SyncPanel(dashboard)
                        MobileSyncTab.CONFLICTS->ConflictsPanel(dashboard)
                    }
                }
            }
        }
    }
}

@Composable private fun AccountPanel(identity:MobileDeviceIdentity?)=Panel("HOSTED ACCOUNT · NOT SIGNED IN","OAuth Authorization Code + PKCE is required before Hosted account access. Direct Station Sync continues without a Hosted account."){
    Fact("DEFAULT","DIRECT_STATION_SYNC");Fact("CLOUD PAYLOAD STORAGE","DISABLED");Fact("DEVICE ID",identity?.deviceId?:"KEYSTORE UNAVAILABLE");Fact("PRIVATE IDENTITY KEY","ANDROID KEYSTORE · NON-EXPORTABLE");Boundary("Signing out must revoke the refresh-token family and remove only RigWeave sync credentials. It never clears application data.")
}

@Composable private fun StationsPanel(data:MobileSyncDashboard)=Panel("STATION / LOGBOOK LINKS","New links require a preview of local counts, duplicates, conflicts, backup state and authority before activation."){
    if(data.spaces.isEmpty())Empty("NO SYNC SPACE","Create and approve a station link from the Local Hub before mobile transfer.") else data.spaces.forEach{space->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF283139))){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(space.stationId,color=Color.White,fontWeight=FontWeight.Bold);Text("${space.logbookId} · ${space.authority.replace('_',' ')}",color=Color(0xFFA5ADB2));Text("${space.mode.replace('_',' ')} · KEY v${space.keyVersion} · ${space.state}",color=Color(0xFFE9A72B),fontSize=12.sp)}}}
}

@Composable private fun DevicesPanel(data:MobileSyncDashboard,identity:MobileDeviceIdentity?)=Panel("TRUSTED DEVICES","Approval is explicit. Revocation rotates the space key; old ciphertext follows the retained-key policy."){
    Fact("THIS DEVICE",identity?.deviceId?:"KEYSTORE UNAVAILABLE");Fact("REQUEST SIGNING","ECDSA P-256 · SHA-256");Fact("EVENT CRYPTO","XChaCha20-Poly1305");Fact("KEY ENVELOPES","LIBSODIUM SEALED BOX")
    if(data.devices.isEmpty())Empty("NO APPROVED PEER","The Local Hub has not approved another device.") else data.devices.forEach{device->Fact(device.name,"${device.platform} · ${device.state} · KEY v${device.keyVersion}")}
}

@Composable private fun SyncPanel(data:MobileSyncDashboard){val context=LocalContext.current;Panel("BOUNDED EVENT JOURNAL","QSO bodies remain canonical. The local outbox stores identity, revision, operation and payload reference only."){
    Fact("PENDING EVENTS",data.pending.toString());Fact("BATCH LIMIT","200 EVENTS · 4 MiB");Fact("RETRY","BOUNDED EXPONENTIAL + JITTER");Fact("BACKGROUND","NETWORK / POWER CONSTRAINTS");Boundary("Credentials, radio state, CAT/PTT/TUNE/TX, audio/media, exact private location, browser sessions and provider secrets are never synchronized.")
    Button(onClick={MobileSyncScheduler.syncNow(context)},modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){Text("SYNC NOW")}
}}

@Composable private fun ConflictsPanel(data:MobileSyncDashboard)=Panel("CONFLICT REVIEW","Wall-clock order never chooses canonical truth. Revision, tombstone and authority rules surface a deliberate decision."){
    if(data.conflicts==0)Empty("NO OPEN CONFLICT","Canonical revision and tombstone checks are clear.") else {Fact("OPEN",data.conflicts.toString());Fact("CHOICES","KEEP BOTH · TOMBSTONE ONE · MERGE REVIEWED FIELDS")}
}

@Composable private fun Panel(title:String,detail:String,content:@Composable ColumnScope.()->Unit){LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text(title,color=Color(0xFFE9A72B),fontWeight=FontWeight.Black);Text(detail,color=Color(0xFFA5ADB2),fontSize=13.sp)};item{Column(verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}}
@Composable private fun Metric(label:String,value:String,color:Color,modifier:Modifier){Column(modifier.background(Color(0xFF283139)).padding(10.dp)){Text(label,color=Color(0xFFA5ADB2),fontSize=10.sp);Text(value,color=color,fontWeight=FontWeight.Black,maxLines=1)}}
@Composable private fun Fact(label:String,value:String){Row(Modifier.fillMaxWidth().background(Color(0xFF283139)).padding(11.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Color(0xFFA5ADB2),fontSize=12.sp);Text(value,color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp)}}
@Composable private fun Boundary(text:String){Text(text,color=Color(0xFFE9A72B),fontSize=12.sp,modifier=Modifier.fillMaxWidth().background(Color(0xFF332713)).padding(12.dp))}
@Composable private fun Empty(title:String,detail:String){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Color(0xFFE9A72B),fontWeight=FontWeight.Black);Text(detail,color=Color(0xFFA5ADB2),fontSize=12.sp)}}
