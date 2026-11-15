package org.langue.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.langue.app.BuildConfig
import org.langue.app.R
import org.langue.engine.LanguagePack

@Composable
internal fun SettingsScreen(library: LibraryState, enabled: Boolean, onImport: () -> Unit, onSelect: (String) -> Unit,
    onDelete: (LanguagePack) -> Unit, onReset: () -> Unit, onExport: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeading(stringResource(R.string.settings))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeading(R.string.languages)
            Text(stringResource(R.string.languages_hint), color = Quiet, style = MaterialTheme.typography.bodyMedium)
            library.active?.let { pack ->
                InfoRow(stringResource(R.string.source_language), pack.manifest.sourceLanguage.name)
                InfoRow(stringResource(R.string.target_language), pack.manifest.targetLanguage.name)
            } ?: Text(stringResource(R.string.no_languages), color = Quiet)
            library.packs.forEach { pack ->
                OutlinedCard(onClick = { onSelect(pack.manifest.id) }, enabled = enabled,
                    shape = RoundedCornerShape(8.dp), colors = CardDefaults.outlinedCardColors(containerColor = Paper)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = library.active?.manifest?.id == pack.manifest.id,
                            onClick = { onSelect(pack.manifest.id) }, enabled = enabled)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(stringResource(R.string.language_pair, pack.manifest.sourceLanguage.name, pack.manifest.targetLanguage.name))
                            Text(pack.manifest.name, style = MaterialTheme.typography.labelSmall, color = Quiet)
                        }
                    }
                }
            }
            SectionDivider()
            SectionHeading(R.string.content)
            ActionButton(R.string.import_pack, Icons.Outlined.FileUpload, enabled, onImport)
            if (library.packs.isNotEmpty()) {
                ActionButton(R.string.update_pack, Icons.Outlined.Update, enabled, onImport)
                Text(stringResource(R.string.update_hint), color = Quiet, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.installed_packs), style = MaterialTheme.typography.labelLarge)
                library.packs.forEach { pack ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pack.manifest.name)
                            Text(stringResource(R.string.pack_version, pack.manifest.version), color = Quiet, style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { onDelete(pack) }, enabled = enabled) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            SectionDivider()
            SectionHeading(R.string.progress)
            if (library.active != null) {
                Text(stringResource(R.string.progress_scope), color = Quiet, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.answers_count, library.stats.answered))
                Text(stringResource(R.string.correct_count, library.stats.correct))
                Text(stringResource(R.string.best_count, library.stats.bestStreak))
                Text(stringResource(R.string.seen_count, library.viewed.size))
                ActionButton(R.string.reset_progress, Icons.Outlined.RestartAlt, enabled, onReset)
            }
            ActionButton(R.string.export_progress, Icons.Outlined.FileDownload, enabled && library.packs.isNotEmpty(), onExport)
            ActionButton(R.string.restore_progress, Icons.Outlined.History, enabled && library.packs.isNotEmpty(), onRestore)
            SectionDivider()
            SectionHeading(R.string.about)
            Text(stringResource(R.string.app_name), fontFamily = FontFamily.Serif, fontSize = 24.sp)
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), color = Quiet)
            Text(stringResource(R.string.about_text), color = Quiet, lineHeight = 23.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
}
