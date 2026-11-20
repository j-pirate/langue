package org.langue.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.langue.app.R
import org.langue.engine.Entry
import org.langue.engine.LanguagePack

@Composable
internal fun EntryDetail(entry: Entry, pack: LanguagePack?, play: (Entry) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(pack?.manifest?.sourceLanguage?.name.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Quiet)
                NaturalText(entry.source, style = MaterialTheme.typography.headlineMedium, direction = pack?.manifest?.sourceLanguage?.direction)
                Text(pack?.manifest?.targetLanguage?.name.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Quiet)
                NaturalText(entry.target, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium,
                    direction = pack?.manifest?.targetLanguage?.direction)
                if (entry.alternatives.isNotEmpty()) {
                    Text(stringResource(R.string.alternatives), style = MaterialTheme.typography.labelMedium, color = Quiet)
                    entry.alternatives.forEach { NaturalText(it, direction = pack?.manifest?.targetLanguage?.direction) }
                }
                entry.transliteration?.let {
                    Text(stringResource(R.string.transliteration), style = MaterialTheme.typography.labelMedium, color = Quiet)
                    NaturalText(it)
                }
            }
        }
        if (entry.audio != null) OutlinedButton(onClick = { play(entry) }) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.listen))
        } else Text(stringResource(R.string.no_audio), color = Quiet)
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
        Spacer(Modifier.height(12.dp))
    }
}
