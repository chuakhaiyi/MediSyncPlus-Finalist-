package com.medisyncplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medisyncplus.navigation.MediSyncNavGraph
import com.medisyncplus.viewmodel.HospitalInfoUiState
import com.medisyncplus.ui.theme.MediSyncPlusTheme
import com.medisyncplus.ui.theme.*
import com.medisyncplus.workers.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.os.Build
import com.google.accompanist.permissions.*

@HiltAndroidApp
class MediSyncApplication : android.app.Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        MedicationReminderWorker.schedule(this)
        ChecklistReminderWorker.schedule(this)
        AppointmentReminderWorker.schedule(this)
        DailyChecklistGeneratorWorker.schedule(this)
        EmrSyncWorker.schedule(this)
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: com.medisyncplus.viewmodel.MediSyncViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val settingsState by viewModel.settings.collectAsState()
            val hospitalInfoState by viewModel.hospitalInfo.collectAsState()
            val isDarkTheme = settingsState.settings?.theme == "DARK"

            MediSyncPlusTheme(darkTheme = isDarkTheme) {
                // Request notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionState = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
                    LaunchedEffect(Unit) {
                        if (!permissionState.status.isGranted) {
                            permissionState.launchPermissionRequest()
                        }
                    }
                }

                var showSosDialog by remember { mutableStateOf(false) }
                var sosMessage by remember { mutableStateOf<String?>(null) }
                
                MediSyncNavGraph(onSOS = { msg ->
                    sosMessage = msg
                    showSosDialog = true 
                })
                
                // Also handle critical alerts from vitals
                LaunchedEffect(Unit) {
                    // This could be improved with a shared flow in VM, 
                    // but for now we rely on the callback passed to NavGraph
                }

                if (showSosDialog) {
                    SosDialog(
                        message = sosMessage,
                        hospitalInfo = hospitalInfoState,
                        onDismiss = { showSosDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SosDialog(message: String? = null, hospitalInfo: HospitalInfoUiState = HospitalInfoUiState(), onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Trigger automated call if message is not null (indicating a critical vital)
        LaunchedEffect(message) {
            if (message != null) {
                // In a real app, we would use an Intent to dial Ward 4B
                // val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:0312345678"))
                // context.startActivity(intent)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = RedLight
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🚨", fontSize = 56.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(if (message != null) "Critical Alert" else "Emergency Alert", 
                    fontWeight = FontWeight.Bold, fontSize = 24.sp,
                    color = Red500, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(message ?: "MediSync is alerting your care team. Please stay calm and call for help if needed.",
                    fontSize = 14.sp, color = Red500, textAlign = TextAlign.Center, lineHeight = 21.sp)
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { /* launch phone dial intent */ onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("📞 Call Hospital Now", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red500.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hospitalInfo.hospitalInfo?.wardName ?: "Ward", fontWeight = FontWeight.Bold, color = Red500)
                            Text(hospitalInfo.hospitalInfo?.wardPhone ?: "–", fontSize = 13.sp, color = Red500)
                        }
                    }
                    Surface(
                        Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red500.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Emergency", fontWeight = FontWeight.Bold, color = Red500)
                            Text("999", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Red500)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.5.dp, Red500),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)
                ) { Text("I'm okay — go back", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
