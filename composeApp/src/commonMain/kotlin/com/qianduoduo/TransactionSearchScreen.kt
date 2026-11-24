package com.qianduoduo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun TransactionSearchScreen(
    data: LedgerSnapshot,
    query: String,
    demo: Boolean,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onEdit: (TransactionRecord) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val matches = remember(data, query) { data.searchTransactions(query) }
    val groups = remember(matches) { matches.groupBy { localDateTime(it.at).date } }
    val currentDate = today()
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader("搜索流水", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("transaction-search"),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconAction(Icons.Default.Close, "清空搜索") { onQuery("") } }
            } else null,
            placeholder = { Text("搜备注、分类或账户") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
        val status = when {
            query.isBlank() -> if (demo) "搜索全部时间。试试「咖喱饭」或「房租」。" else "搜索全部时间，不受首页月份限制。"
            matches.isEmpty() -> "没有找到相关流水，试试换个关键词。"
            else -> "找到 ${matches.size} 笔 · 全部时间"
        }
        Text(status, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            transactionItems(groups, data, currentDate, showYear = true, onEdit = onEdit)
        }
    }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
}
