package org.langue.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import org.langue.app.R
import org.langue.app.audio.OfflineAudioPlayer
import org.langue.app.data.AppRepository
import org.langue.engine.Entry
import org.langue.engine.LanguagePack

@Composable
fun LangueApp(repository: AppRepository) {
    val vm: LangueViewModel = viewModel(factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LangueViewModel(repository) as T
        }
    })
    LangueTheme { AppContent(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: LangueViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failed by vm.initializationFailed.collectAsStateWithLifecycle()
    val initializationMessage by vm.initializationMessage.collectAsStateWithLifecycle()
    val preparing by vm.quizPreparing.collectAsStateWithLifecycle()
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
            snackbar.showSnackbar(message)
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
        if (file != null) audio.play(file, vm::audioError) else vm.missingAudio()
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
            if (busy || preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(initializationMessage))
                    OutlinedButton(onClick = vm::initialize, enabled = !busy) { Text(stringResource(R.string.retry)) }
                }
            }
            AnimatedContent(targetState = tab, label = "tab", modifier = Modifier.weight(1f)) { activeTab ->
                when (activeTab) {
                    0 -> VocabularyScreen(library, categoryId, searchOpen, query,
                        onCategory = { categoryId = it }, onBack = { categoryId = null },
                        onSearch = { searchOpen = !searchOpen; query = "" }, onQuery = { query = it },
                        onEntry = openEntry, onImport = launchImport, enabled = !busy)
                    1 -> QuizScreen(vm, library, play, !busy && !preparing)
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
