package com.costproject.app.ui.view.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.costproject.app.ui.view.common.FormField
import com.costproject.app.ui.viewmodel.AuthMode
import com.costproject.app.ui.viewmodel.AuthViewModel

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isRegister = state.mode == AuthMode.Register

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Scaffold's default insets exclude the keyboard. Mark the ones it
                // applied as consumed, then pad for the keyboard - before the
                // scroll modifier, so the form can scroll above the keyboard.
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "COST PROJECT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Kalkulator Biaya Pekerjaan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isRegister) "Buat Akun" else "Masuk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    if (isRegister) {
                        FormField(
                            value = state.fullName,
                            onValueChange = viewModel::onFullNameChange,
                            label = "Nama Lengkap",
                            errorText = state.fieldErrors["fullName"],
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    FormField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email",
                        errorText = state.fieldErrors["email"],
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )
                    FormField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "Kata Sandi",
                        errorText = state.fieldErrors["password"],
                        enabled = !state.isSubmitting,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                    )

                    state.errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::submit,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (isRegister) "Daftar" else "Masuk")
                        }
                    }
                    TextButton(
                        onClick = viewModel::toggleMode,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
