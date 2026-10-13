package com.tv.wnotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

enum class PinMode {
    SET, ENTER, RESET
}

@Composable
fun PinDialog(
    mode: PinMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    correctPin: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(if (mode == PinMode.RESET) 0 else 1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (mode) {
                    PinMode.SET -> if (step == 1) "Set 4-digit PIN" else "Confirm PIN"
                    PinMode.ENTER -> "Enter PIN"
                    PinMode.RESET -> when (step) {
                        0 -> "Enter Old PIN"
                        1 -> "Set New PIN"
                        else -> "Confirm New PIN"
                    }
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val currentInput = when {
                    mode == PinMode.RESET && step == 0 -> oldPin
                    (mode == PinMode.SET || mode == PinMode.RESET) && step == 2 -> confirmPin
                    else -> pin
                }

                OutlinedTextField(
                    value = currentInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            when {
                                mode == PinMode.RESET && step == 0 -> oldPin = it
                                (mode == PinMode.SET || mode == PinMode.RESET) && step == 2 -> confirmPin = it
                                else -> pin = it
                            }
                            error = null
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        PinMode.SET -> {
                            if (step == 1) {
                                if (pin.length == 4) step = 2 else error = "PIN must be 4 digits"
                            } else {
                                if (pin == confirmPin) onConfirm(pin) else error = "PINs do not match"
                            }
                        }
                        PinMode.ENTER -> {
                            if (pin == correctPin) onConfirm(pin) else error = "Incorrect PIN"
                        }
                        PinMode.RESET -> {
                            when (step) {
                                0 -> if (oldPin == correctPin) step = 1 else error = "Incorrect Old PIN"
                                1 -> if (pin.length == 4) step = 2 else error = "PIN must be 4 digits"
                                2 -> if (pin == confirmPin) onConfirm(pin) else error = "PINs do not match"
                            }
                        }
                    }
                }
            ) {
                Text(if ((mode == PinMode.SET || mode == PinMode.RESET) && step < 2 || (mode == PinMode.RESET && step == 0)) "Next" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
