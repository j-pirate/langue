package org.langue.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.langue.app.R
import org.langue.engine.Category
import org.langue.engine.Entry
import org.langue.engine.VocabularySearch

@Composable
internal fun VocabularyScreen(
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
