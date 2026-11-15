package org.langue.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.langue.app.BuildConfig
import org.langue.app.R
import org.langue.app.audio.OfflineAudioPlayer
import org.langue.app.data.AppRepository
import org.langue.engine.Category
import org.langue.engine.Entry
import org.langue.engine.LanguagePack
import org.langue.engine.VocabularySearch
import kotlinx.coroutines.flow.collect

private val Paper = Color(0xFFF8F3E9)
private val Ink = Color(0xFF282522)
private val Quiet = Color(0xFF70665C)
private val Success = Color(0xFF28744E)
private val Mistake = Color(0xFFA42C31)

@Composable
fun LangueApp(repository: AppRepository) {
    val vm: LangueViewModel = viewModel(factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LangueViewModel(repository) as T
        }
    })
    val accent = colorResource(R.color.brand_accent)
    val scheme = lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = .1f),
        onPrimaryContainer = Ink, background = Paper, onBackground = Ink,
        surface = Paper, onSurface = Ink, surfaceVariant = Color(0xFFEFE6D8),
        onSurfaceVariant = Quiet, outline = Color(0xFFB9AFA0), error = Mistake,
    )
    MaterialTheme(colorScheme = scheme) { AppContent(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: LangueViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failed by vm.initializationFailed.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var entry by remember { mutableStateOf<Entry?>(null) }
    var deletePack by remember { mutableStateOf<LanguagePack?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val audio = remember(context) { OfflineAudioPlayer(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(audio, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) audio.stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); audio.release() }
    }
    LaunchedEffect(tab, categoryId, library.active) { audio.stop(); entry = null }
    LaunchedEffect(library.active?.manifest?.id) { categoryId = null; query = ""; searchOpen = false }
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            val message = context.getString(notice.message)
            snackbar.showSnackbar(if (notice.detail.isNullOrBlank()) message else context.getString(R.string.operation_detail, message, notice.detail))
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importPack(context.contentResolver, it) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportProgress(context.contentResolver, it) }
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.restoreProgress(context.contentResolver, it) }
    }
    val launchImport: () -> Unit = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
    val play: (Entry) -> Unit = { item ->
        val file = library.active?.let { vm.repository.audioFile(it.manifest.id, item) }
        if (file != null) audio.play(file, vm::audioError) else vm.audioError()
    }
    val openEntry: (Entry) -> Unit = { item ->
        entry = item
        library.active?.manifest?.id?.let { vm.markViewed(item, it) }
    }
    BackHandler(enabled = entry == null && tab == 0 && (categoryId != null || searchOpen)) {
        if (searchOpen) { searchOpen = false; query = "" } else categoryId = null
    }
    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                listOf(
                    Triple(R.string.vocabulary, Icons.AutoMirrored.Outlined.MenuBook, 0),
                    Triple(R.string.quiz, Icons.Outlined.Psychology, 1),
                    Triple(R.string.settings, Icons.Outlined.Tune, 2),
                ).forEach { (title, icon, index) ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(title), maxLines = 1, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.initialize_error))
                    OutlinedButton(onClick = vm::initialize, enabled = !busy) { Text(stringResource(R.string.retry)) }
                }
            }
            AnimatedContent(targetState = tab, label = "tab", modifier = Modifier.weight(1f)) { activeTab ->
                when (activeTab) {
                    0 -> VocabularyScreen(library, categoryId, searchOpen, query,
                        onCategory = { categoryId = it }, onBack = { categoryId = null },
                        onSearch = { searchOpen = !searchOpen; query = "" }, onQuery = { query = it },
                        onEntry = openEntry, onImport = launchImport, enabled = !busy)
                    1 -> QuizScreen(vm, library, play, !busy)
                    else -> SettingsScreen(library, !busy, launchImport, vm::selectPack,
                        onDelete = { deletePack = it }, onReset = { confirmReset = true },
                        onExport = { exporter.launch(context.getString(R.string.progress_filename)) },
                        onRestore = { confirmRestore = true })
                }
            }
        }
    }
    entry?.let { item ->
        ModalBottomSheet(onDismissRequest = { entry = null; audio.stop() }, containerColor = Paper) {
            EntryDetail(item, library.active, play, onClose = { entry = null; audio.stop() })
        }
    }
    deletePack?.let { pack ->
        ConfirmDialog(R.string.delete_pack_title, stringResource(R.string.delete_pack_message, pack.manifest.name), R.string.delete,
            onDismiss = { deletePack = null }, onConfirm = { deletePack = null; vm.deletePack(pack.manifest.id) })
    }
    if (confirmReset) ConfirmDialog(R.string.reset_title, stringResource(R.string.reset_message), R.string.reset,
        onDismiss = { confirmReset = false }, onConfirm = { confirmReset = false; vm.resetProgress() })
    if (confirmRestore) ConfirmDialog(R.string.restore_title, stringResource(R.string.restore_message), R.string.choose_file,
        onDismiss = { confirmRestore = false }, onConfirm = {
            confirmRestore = false; restorer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        })
}

@Composable
private fun ScreenHeading(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp,
                modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, color = Quiet, style = MaterialTheme.typography.bodyMedium) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun VocabularyScreen(
    library: LibraryState, categoryId: String?, searchOpen: Boolean, query: String,
    onCategory: (String) -> Unit, onBack: () -> Unit, onSearch: () -> Unit, onQuery: (String) -> Unit,
    onEntry: (Entry) -> Unit, onImport: () -> Unit, enabled: Boolean,
) {
    val pack = library.active
    val category = pack?.categories?.find { it.id == categoryId }
    val counts = remember(pack) { pack?.entries?.groupingBy { it.categoryId }?.eachCount().orEmpty() }
    Column(Modifier.fillMaxSize()) {
        if (category != null && !searchOpen) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Text(category.name, fontFamily = FontFamily.Serif, fontSize = 26.sp, modifier = Modifier.padding(12.dp))
            }
        } else {
            ScreenHeading(stringResource(R.string.app_name), stringResource(R.string.app_subtitle)) {
                IconButton(onClick = onSearch, enabled = pack != null) {
                    Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        stringResource(if (searchOpen) R.string.close_search else R.string.search))
                }
            }
        }
        if (pack == null) { EmptyLibrary(onImport, enabled); return@Column }
        if (searchOpen) {
            OutlinedTextField(value = query, onValueChange = onQuery, label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            val results = remember(pack, query) { VocabularySearch.search(pack, query) }
            Text(stringResource(R.string.search_results), modifier = Modifier.padding(24.dp, 16.dp), fontWeight = FontWeight.SemiBold)
            if (results.isEmpty()) Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp), color = Quiet)
            EntryList(results, library.viewed, onEntry, Modifier.weight(1f))
        } else if (category != null) {
            val entries = pack.entries.filter { it.categoryId == category.id }
            Text(stringResource(R.string.entry_count, entries.size), Modifier.padding(24.dp, 8.dp), color = Quiet)
            if (entries.isEmpty()) Text(stringResource(R.string.empty_category), Modifier.padding(24.dp), color = Quiet)
            EntryList(entries, library.viewed, onEntry, Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxWidth().padding(24.dp, 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.categories), fontSize = 20.sp, fontFamily = FontFamily.Serif)
                Text(stringResource(R.string.category_count, pack.categories.size), color = Quiet, style = MaterialTheme.typography.labelMedium)
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(pack.categories.sortedWith(compareBy<Category> { it.order }.thenBy { it.id }), key = { _, item -> item.id }) { index, item ->
                    CategoryCard(item, index + 1, counts[item.id] ?: 0, onClick = { onCategory(item.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, enabled: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.empty_title), fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 34.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_content), color = Quiet, lineHeight = 24.sp)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onImport, enabled = enabled, shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.import_pack))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryCard(category: Category, index: Int, count: Int, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(8.dp), colors = CardDefaults.outlinedCardColors(containerColor = Paper)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (category.icon.isNullOrBlank()) Icon(Icons.Outlined.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary)
                else Text(category.icon.orEmpty(), fontSize = 24.sp)
                Text(stringResource(R.string.category_number, index), style = MaterialTheme.typography.labelSmall, color = Quiet)
            }
            Text(category.name, fontFamily = FontFamily.Serif, fontSize = 20.sp)
            category.targetName?.let { NaturalText(it, color = Quiet, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.entry_count, count), color = Quiet, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EntryList(entries: List<Entry>, viewed: Set<String>, onEntry: (Entry) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        items(entries, key = { it.id }) { entry ->
            Column(Modifier.fillMaxWidth().clickable { onEntry(entry) }.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NaturalText(entry.source, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (entry.id in viewed) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.seen), tint = Quiet, modifier = Modifier.size(17.dp))
                }
                NaturalText(entry.target, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
        }
    }
}

@Composable
private fun NaturalText(text: String, modifier: Modifier = Modifier, color: Color = Ink,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, direction: String? = null) {
    Text(text, modifier, color = color, style = style.copy(textDirection = when (direction) {
        "rtl" -> TextDirection.Rtl
        "ltr" -> TextDirection.Ltr
        else -> TextDirection.Content
    }))
}

@Composable
private fun EntryDetail(entry: Entry, pack: LanguagePack?, play: (Entry) -> Unit, onClose: () -> Unit) {
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

@Composable
private fun QuizScreen(vm: LangueViewModel, library: LibraryState, play: (Entry) -> Unit, enabled: Boolean) {
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
                        border = androidx.compose.foundation.BorderStroke(if (correct || incorrect) 2.dp else 1.dp, tone),
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

@Composable
private fun SettingsScreen(library: LibraryState, enabled: Boolean, onImport: () -> Unit, onSelect: (String) -> Unit,
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

@Composable
private fun SectionHeading(resource: Int) {
    Text(stringResource(resource), fontFamily = FontFamily.Serif, fontSize = 23.sp, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Quiet)
        NaturalText(value)
    }
}

@Composable
private fun ActionButton(resource: Int, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(resource), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ConfirmDialog(title: Int, message: String, confirm: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title), fontFamily = FontFamily.Serif) },
        text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
