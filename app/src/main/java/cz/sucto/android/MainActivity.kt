package cz.sucto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import cz.sucto.android.ui.CashViewModel
import cz.sucto.android.ui.DashboardScreen
import cz.sucto.android.ui.DashboardViewModel
import cz.sucto.android.data.Company
import cz.sucto.android.ui.AuthState
import cz.sucto.android.ui.AuthViewModel
import cz.sucto.android.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AuthViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            SuctoTheme(dark = if (state.signedIn) androidx.compose.foundation.isSystemInDarkTheme() else true) {
                val company = state.selectedCompany
                if (state.signedIn && company != null) {
                    val owner = model.companyOwner
                    val dashboard: DashboardViewModel = viewModel(viewModelStoreOwner = owner, key = "dashboard-${state.sessionId}-${company.id}",
                        factory = model.dashboardFactory(company.id))
                    val cash: CashViewModel = viewModel(viewModelStoreOwner = owner, key = "cash-${state.sessionId}-${company.id}", factory = model.cashFactory(company.id))
                    DashboardScreen(company, dashboard, cash, onCompanies = { model.selectCompany(null) }, onLogout = model::logout)
                } else if (state.signedIn) CompaniesScreen(state, model::refreshCompanies, model::logout, model::selectCompany)
                else LoginScreen(state, model::login)
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AuthState, onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password must not be stored in the saved instance state.
    var password by remember { mutableStateOf("") }
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
                        shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { submit() }, enabled = !state.loading && email.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F9E55), contentColor = Color.White)) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.login), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompaniesScreen(state: AuthState, onRetry: () -> Unit, onLogout: () -> Unit, onSelect: (Company) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.companies)) }, actions = {
            TextButton(onClick = onLogout, enabled = !state.loading) { Text(stringResource(R.string.logout)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (!state.loading && state.error == null && state.companies.isEmpty()) {
                Text(stringResource(R.string.no_companies))
            }
            state.companies.forEach { company ->

                Card(onClick = { onSelect(company) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(company.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.company_ic, company.ic), style = MaterialTheme.typography.bodyMedium)
                        if (company.isTaxable) Text(stringResource(R.string.taxable), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}