package cz.sucto.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import cz.sucto.android.ui.AppLockViewModel
import cz.sucto.android.ui.authenticateWithCipher
import cz.sucto.android.ui.CompanyLogo
import cz.sucto.android.ui.SoftButton
import cz.sucto.android.ui.SuctoCard
import cz.sucto.android.ui.CompanyViewModel
import cz.sucto.android.ui.PartnersViewModel
import cz.sucto.android.ui.LockScreen
import cz.sucto.android.ui.CashViewModel
import cz.sucto.android.ui.DashboardScreen
import cz.sucto.android.ui.DashboardViewModel
import cz.sucto.android.data.Company
import cz.sucto.android.ui.AuthState
import cz.sucto.android.ui.AuthViewModel
import cz.sucto.android.ui.theme.*

class MainActivity : FragmentActivity() {
    private val lock: AppLockViewModel by viewModels()
    private val auth: AuthViewModel by viewModels()

    // V přepínači aplikací se nesmí ukázat obsah: okno je při odchodu do pozadí označené jako zabezpečené.
    override fun onPause() { window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE); super.onPause() }
    override fun onResume() { super.onResume(); window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    override fun onStop() { lock.onStop(); super.onStop() }
    override fun onStart() { super.onStart(); lock.onStart(this) }

    /** Biometrické přihlášení uloženým e-mailem a heslem; spouští se jen klepnutím na tlačítko. */
    private fun loginWithSaved() {
        val cipher = auth.credentials.decryptCipher() ?: return
        authenticateWithCipher(cipher, "Přihlášení do sÚčta", { unlocked ->
            auth.credentials.load(unlocked)?.let { auth.login(it.email, it.password, manual = false) }
        }, { })
    }

    /** Uloží právě zadané přihlášení po biometrickém ověření. */
    private fun saveLogin() {
        val pending = auth.pendingCredentials() ?: return
        authenticateWithCipher(auth.credentials.encryptCipher(), "Uložení přihlášení", { cipher ->
            auth.credentials.save(cipher, pending.email, pending.password)
            auth.dismissOffer()
        }, { auth.dismissOffer() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cz.sucto.android.data.DueNotifications.schedule(this)
        // Obnovená relace po studeném startu je zamčená; po otočení obrazovky (savedInstanceState) se nezamyká znovu.
        if (savedInstanceState == null && auth.state.value.signedIn && AppLockViewModel.isAvailable(this)) lock.lock()
        enableEdgeToEdge()
        setContent {
            val model = auth
            var credentialsVersion by remember { mutableIntStateOf(0) }
            val state by model.state.collectAsStateWithLifecycle()
            SuctoTheme(dark = if (state.signedIn) androidx.compose.foundation.isSystemInDarkTheme() else true) {
                val company = state.selectedCompany
                if (state.signedIn && company != null) {
                    val owner = model.companyOwner
                    val dashboard: DashboardViewModel = viewModel(viewModelStoreOwner = owner, key = "dashboard-${state.sessionId}-${company.id}",
                        factory = model.dashboardFactory(company.id))
                    val cash: CashViewModel = viewModel(viewModelStoreOwner = owner, key = "cash-${state.sessionId}-${company.id}", factory = model.cashFactory(company.id))
                    val profile: CompanyViewModel = viewModel(viewModelStoreOwner = owner, key = "company-${state.sessionId}-${company.id}", factory = model.companyFactory(company.id))
                    val partners: PartnersViewModel = viewModel(viewModelStoreOwner = owner, key = "partners-${state.sessionId}-${company.id}", factory = model.partnersFactory(company.id))
                    val offlineSince by model.cachedDataDate.collectAsStateWithLifecycle()
                    DashboardScreen(company, state.companies, dashboard, cash, profile, partners, offlineSince = offlineSince, onSwitchCompany = model::selectCompany,
                        onCompanies = { model.selectCompany(null) }, onLogout = model::logout)
                } else if (state.signedIn) CompaniesScreen(state, model::refreshCompanies, model::logout, model::selectCompany)
                else LoginScreen(state, hasSaved = remember(state.signedIn, credentialsVersion) { model.credentials.hasSavedLogin }, onLogin = { email, password -> model.login(email, password) },
                    onBiometric = { loginWithSaved() }, onForget = { model.credentials.clear(); credentialsVersion++ })
                if (state.signedIn && state.offerSave) SaveLoginDialog(
                    onSave = { saveLogin() },
                    onLater = { model.dismissOffer() },
                    onNever = { model.dismissOffer(never = true) })
                // Zámek překrývá celou aplikaci, ale jen po přihlášení (relace je zatím jen v paměti).
                val lockState by lock.state.collectAsStateWithLifecycle()
                if (state.signedIn && lockState.locked) LockScreen(lock, this@MainActivity)
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AuthState, hasSaved: Boolean, onLogin: (String, String) -> Unit, onBiometric: () -> Unit, onForget: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password must not be stored in the saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val submit = { onLogin(email, password) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, InkSoft, Color(0xFF246B3B))))
            .safeDrawingPadding().imePadding(),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Image(painterResource(R.drawable.sucto_logo), contentDescription = null,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.login_subtitle), color = Color.White.copy(alpha = .7f),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))
            Card(shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .10f))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading, singleLine = true, label = { Text(stringResource(R.string.email)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading, singleLine = true, label = { Text(stringResource(R.string.password)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                    contentDescription = if (showPassword) "Skrýt heslo" else "Zobrazit heslo")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { submit() }, enabled = !state.loading && email.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F9E55), contentColor = Color.White)) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.login), fontWeight = FontWeight.SemiBold)
                    }
                    if (hasSaved) {
                        OutlinedButton(onClick = onBiometric, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Text("Přihlásit se biometrií", fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
                            Text("Zapomenout uložené přihlášení", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompaniesScreen(state: AuthState, onRetry: () -> Unit, onLogout: () -> Unit, onSelect: (Company) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.companies), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background),
            actions = { TextButton(onClick = onLogout, enabled = !state.loading) { Text(stringResource(R.string.logout)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            state.error?.let { error ->
                SuctoCard {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    SoftButton(stringResource(R.string.retry), onRetry)
                }
            }
            if (!state.loading && state.error == null && state.companies.isEmpty()) {
                Text(stringResource(R.string.no_companies), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.companies.forEach { company ->
                SuctoCard(onClick = { onSelect(company) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CompanyLogo(company)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(company.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.company_ic, company.ic), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (company.isTaxable) Text(stringResource(R.string.taxable), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Nabídka uložení přihlášení po úspěšném ručním přihlášení (jen s nastavenou biometrií). */
@Composable
private fun SaveLoginDialog(onSave: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    AlertDialog(onDismissRequest = onLater, title = { Text("Uložit přihlášení?") },
        text = { Text("Příště se přihlásíte biometrií. E-mail a heslo se uloží zašifrované jen do zabezpečeného úložiště tohoto zařízení a použít je půjde jen po ověření otiskem nebo obličejem.") },
        confirmButton = { TextButton(onClick = onSave) { Text("Uložit") } },
        dismissButton = { Row { TextButton(onClick = onNever) { Text("Nikdy") }; TextButton(onClick = onLater) { Text("Teď ne") } } })
}
