package org.langue.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.langue.app.R
import org.langue.engine.Entry

@Composable
internal fun QuizScreen(vm: LangueViewModel, library: LibraryState, play: (Entry) -> Unit, enabled: Boolean) {
    val state by vm.practice.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeading(stringResource(R.string.practice_heading), stringResource(R.string.practice_subtitle))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.current_streak), library.stats.currentStreak, Modifier.weight(1f))
                StatCard(stringResource(R.string.personal_best), library.stats.bestStreak, Modifier.weight(1f))
            }
            val description = stringResource(R.string.streak_progress, library.stats.currentStreak, library.stats.bestStreak)
            LinearProgressIndicator(progress = { library.stats.currentStreak.toFloat() / maxOf(1, library.stats.bestStreak) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description })
            val question = state.question
            if (question == null) {
                Text(stringResource(if (library.active == null) R.string.empty_content else R.string.quiz_unavailable), color = Quiet, lineHeight = 24.sp)
            } else {
                NaturalText(question.entry.source, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 14.dp), direction = library.active?.manifest?.sourceLanguage?.direction)
                question.options.forEach { option ->
                    val selected = state.selectedId
                    val correct = selected != null && option.id == question.entry.id
                    val incorrect = selected == option.id && option.id != question.entry.id
                    val tone = when { correct -> Success; incorrect -> Mistake; else -> MaterialTheme.colorScheme.outline }
                    OutlinedCard(onClick = { vm.answer(option.id) }, enabled = enabled && selected == null,
                        border = BorderStroke(if (correct || incorrect) 2.dp else 1.dp, tone),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (correct || incorrect) tone.copy(alpha = .08f) else Paper,
                            disabledContainerColor = if (correct || incorrect) tone.copy(alpha = .08f) else Paper,
                            disabledContentColor = Ink)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            NaturalText(option.target, Modifier.weight(1f), color = if (correct || incorrect) tone else Ink,
                                direction = library.active?.manifest?.targetLanguage?.direction)
                            if (selected != null && option.audio != null) IconButton(onClick = { play(option) }) {
                                Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.listen))
                            }
                            if (correct || incorrect) Icon(if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                stringResource(if (correct) R.string.correct_answer else R.string.incorrect_answer), tint = tone)
                        }
                    }
                }
                if (state.selectedId != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (state.selectedId == question.entry.id) R.string.correct_answer else R.string.incorrect_answer),
                            color = if (state.selectedId == question.entry.id) Success else Mistake)
                        if (state.selectedId != question.entry.id) NaturalText(question.entry.target, color = Success)
                    }
                    Button(onClick = vm::nextQuestion, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.next_question))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier) {
    OutlinedCard(modifier, shape = RoundedCornerShape(8.dp), colors = CardDefaults.outlinedCardColors(containerColor = Paper)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Quiet, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.stat_number, value), fontFamily = FontFamily.Serif, fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
