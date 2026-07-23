package com.tv.wnotes.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tv.wnotes.viewmodel.SessionViewModel

@Composable
fun FocusTimerSidebar(viewModel: SessionViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (expanded) {
            // Invisible overlay to detect clicks outside the expanded menu
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { expanded = false }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimerOption("10 sec", 10 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            TimerOption("5 min", 5 * 60 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            TimerOption("15 min", 15 * 60 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            TimerOption("30 min", 30 * 60 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            TimerOption("45 min", 45 * 60 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            TimerOption("60 min", 60 * 60 * 1000L) {
                                viewModel.startTimer(it)
                                expanded = false
                            }
                            if (isTimerRunning) {
                                TextButton(onClick = { viewModel.cancelTimer(); expanded = false }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Surface(
                    onClick = { expanded = !expanded },
                    color = if (isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(width = 12.dp, height = 64.dp)
                ) {
                    // Pill shape handle
                }
            }
        }
    }
}

@Composable
fun TimerOption(label: String, duration: Long, onClick: (Long) -> Unit) {
    SuggestionChip(
        onClick = { onClick(duration) },
        label = { Text(label) },
        shape = CircleShape
    )
}
