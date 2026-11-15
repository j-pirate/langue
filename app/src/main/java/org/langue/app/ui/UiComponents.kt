package org.langue.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.langue.app.R

@Composable
internal fun ScreenHeading(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
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
internal fun EmptyLibrary(onImport: () -> Unit, enabled: Boolean) {
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
internal fun NaturalText(text: String, modifier: Modifier = Modifier, color: Color = Ink,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, direction: String? = null) {
    Text(text, modifier, color = color, style = style.copy(textDirection = when (direction) {
        "rtl" -> TextDirection.Rtl
        "ltr" -> TextDirection.Ltr
        else -> TextDirection.Content
    }))
}

@Composable
internal fun SectionHeading(resource: Int) {
    Text(stringResource(resource), fontFamily = FontFamily.Serif, fontSize = 23.sp, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))

@Composable
internal fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Quiet)
        NaturalText(value)
    }
}

@Composable
internal fun ActionButton(resource: Int, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(resource), modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ConfirmDialog(title: Int, message: String, confirm: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title), fontFamily = FontFamily.Serif) },
        text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
