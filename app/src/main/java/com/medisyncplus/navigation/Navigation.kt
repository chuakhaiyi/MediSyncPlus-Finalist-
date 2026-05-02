package com.medisyncplus.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.medisyncplus.data.database.DatabaseSeeder
import com.medisyncplus.ui.components.isCurrentThemeDark
import com.medisyncplus.ui.screens.*
import com.medisyncplus.ui.theme.*
import com.medisyncplus.viewmodel.MediSyncViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login        : Screen("login",        "Login",    Icons.Default.Login)
    object Home         : Screen("home",         "Home",     Icons.Default.Home)
    object Medications  : Screen("medications",  "Meds",     Icons.Default.Medication)
    object Checklist    : Screen("checklist",    "Tasks",    Icons.Default.CheckCircle)
    object Vitals       : Screen("vitals",       "Vitals",   Icons.Default.MonitorHeart)
    object Symptoms     : Screen("symptoms",     "Report",   Icons.Default.HealthAndSafety)
    object Appointments : Screen("appointments", "Visits",   Icons.Default.CalendarMonth)
    object Chat         : Screen("chat",         "AI Chat",  Icons.Default.Chat)
    object Audit        : Screen("audit",        "Audit",    Icons.Default.Assignment)
    object Settings     : Screen("settings",     "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home, Screen.Medications,
    Screen.Vitals, Screen.Appointments, Screen.Chat
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediSyncNavGraph(
    viewModel: MediSyncViewModel = hiltViewModel(),
    onSOS: (String?) -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.ui.collectAsState()

    val startRoute = if (uiState.isLoggedIn) Screen.Home.route else Screen.Login.route

    val homeState        by viewModel.home.collectAsState()
    val medState         by viewModel.meds.collectAsState()
    val checklistState   by viewModel.checklist.collectAsState()
    val vitalsState      by viewModel.vitals.collectAsState()
    val symptomState     by viewModel.symptom.collectAsState()
    val appointmentState by viewModel.appointments.collectAsState()
    val chatState        by viewModel.chat.collectAsState()
    val auditState       by viewModel.audit.collectAsState()
    val settingsState    by viewModel.settings.collectAsState()
    val hospitalInfoState by viewModel.hospitalInfo.collectAsState()

    // Toast snackbar
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHost.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    // ── Global Emergency Alert Popup ─────────────────────────────────────────
    uiState.emergencyAlert?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissEmergencyAlert() },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = Red500, modifier = Modifier.size(48.dp)) },
            title = { Text("Health Risk Detected", fontWeight = FontWeight.ExtraBold, color = Red500) },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Text("Your care team has been notified. Please follow the recommended actions or call emergency services if you feel unwell.",
                        fontSize = 12.sp, color = SlateGrey, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSOS(message); viewModel.dismissEmergencyAlert() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red500)
                ) { Text("Contact Care Team (SOS)") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEmergencyAlert() }) {
                    Text("I'm Okay / Dismiss", color = SlateGrey)
                }
            }
        )
    }

    // ── Global Medication Feeling Dialog ─────────────────────────────────────
    uiState.showFeelingDialogFor?.let { medication ->
        var feelingInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.submitFeelingAfterMedication(medication, null) },
            title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("How are you feeling right now after taking ${medication.name}?", 
                        fontSize = 14.sp, color = SlateGrey)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = feelingInput,
                        onValueChange = { feelingInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe how you feel (e.g. dizzy, better...)") },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal500,
                            cursorColor = Teal500,
                            unfocusedBorderColor = SlateGrey.copy(alpha = 0.3f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitFeelingAfterMedication(medication, feelingInput) },
                    enabled = feelingInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.submitFeelingAfterMedication(medication, null) }) {
                    Text("No issues / None", color = SlateGrey)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) { data ->
            Snackbar(data, containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B),
                contentColor = androidx.compose.ui.graphics.Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        }},
        bottomBar = {
            if (uiState.isLoggedIn) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp)) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDest = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp)) },
                            label = { Text(screen.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Teal500,
                                selectedTextColor = Teal500,
                                indicatorColor = TealLight.copy(alpha = if(isCurrentThemeDark()) 0.2f else 1f),
                                unselectedIconColor = SlateGrey,
                                unselectedTextColor = SlateGrey
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(paddingValues),
            enterTransition  = { fadeIn() + slideInHorizontally { 80 } },
            exitTransition   = { fadeOut() },
            popEnterTransition  = { fadeIn() },
            popExitTransition   = { fadeOut() + slideOutHorizontally { 80 } }
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLogin = { email, password, onSuccess, onError ->
                        viewModel.login(email, password, onSuccess, onError)
                    },
                    onLoginSuccess = { 
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    homeState = homeState,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSOS = { onSOS(null) },
                    onMarkMedTaken = { viewModel.markMedicationTaken(it) },
                    onToggleTask = { id, done -> viewModel.toggleTask(id, done) }
                )
            }
            composable(Screen.Medications.route) {
                MedicationScreen(
                    state = medState,
                    onMarkTaken = { viewModel.markMedicationTaken(it) },
                    onUnmarkTaken = { viewModel.unmarkMedicationTaken(it) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Checklist.route) {
                ChecklistScreen(
                    state = checklistState,
                    onToggleTask = { id, done -> viewModel.toggleTask(id, done) },
                    onRecordWeight = { viewModel.recordWeight(it) },
                    onRecordBp = { s, d -> viewModel.recordBp(s, d) },
                    onRecordBloodSugar = { viewModel.recordBloodSugar(it) },
                    onRecordPulse = { viewModel.recordPulse(it.toFloat()) },
                    onRecordSpo2 = { viewModel.recordSpo2(it) },
                    onRecordTemperature = { viewModel.recordTemperature(it) },
                    onCriticalAlert = { _, msg -> onSOS(msg) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Vitals.route) {
                VitalsScreen(
                    state = vitalsState,
                    settings = settingsState.settings,
                    onRecordWeight = { viewModel.recordWeight(it) },
                    onRecordBp = { s, d -> viewModel.recordBp(s, d) },
                    onRecordBloodSugar = { viewModel.recordBloodSugar(it) },
                    onRecordPulse = { viewModel.recordPulse(it.toFloat()) },
                    onRecordSpo2 = { viewModel.recordSpo2(it) },
                    onRecordTemperature = { viewModel.recordTemperature(it) },
                    onCriticalAlert = { _, msg -> onSOS(msg) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Symptoms.route) {
                SymptomScreen(
                    state = symptomState,
                    onSubmit = { symptoms, severity, note -> viewModel.submitSymptomReport(symptoms, severity, note) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Appointments.route) {
                AppointmentScreen(
                    state = appointmentState,
                    onRequestAppointment = { r, d, t, n -> viewModel.requestAppointment(r, d, t, n) },
                    onMarkReminderRead = { viewModel.markReminderRead(it) },
                    onReschedule = { id, date -> viewModel.rescheduleAppointment(id, date) },
                    onCancel = { id -> viewModel.cancelAppointmentRequest(id) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Chat.route) {
                val name = homeState.patient?.name?.split(" ")?.firstOrNull() ?: "Patient"
                ChatScreen(
                    state = chatState,
                    patientName = name,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Audit.route) {
                AuditTrailScreen(
                    state = auditState,
                    onNavigate = { navController.navigate(it) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    state = settingsState,
                    patient = homeState.patient,
                    hospitalInfoState = hospitalInfoState,
                    onUpdateSettings = { viewModel.updateSettings(it) },
                    onUpdateHospitalInfo = { viewModel.updateHospitalInfo(it) },
                    onUpdateEmergencyContact = { name, phone -> viewModel.updateEmergencyContact(name, phone) },
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onTestAi = { viewModel.testAiConnection() }
                )
            }
        }
    }
}
