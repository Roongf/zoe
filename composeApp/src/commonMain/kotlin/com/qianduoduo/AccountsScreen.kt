package com.qianduoduo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AccountsScreen(
    data: LedgerSnapshot,
    onBack: () -> Unit,
    onSave: (String?, String, AccountType) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerAccount?>(null) }
    val accounts = data.activeAccounts
    val balances = remember(data) { accounts.associate { it.id to data.balance(it) } }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("账户", onBack) {
            IconAction(Icons.Default.Add, "添加账户") { editing = null; showDialog = true }
        }
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("账户余额合计", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(Money.format(balances.values.sum()), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("所有有效账户当前余额之和，不等于本期结余",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (accounts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有账户，点右上角加一个", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn {
            items(accounts, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = item; showDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CategoryIcon(item.icon)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.type.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    val balance = balances.getValue(item.id)
                    Text(Money.format(balance), fontWeight = FontWeight.SemiBold,
                        color = if (balance < 0L) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (showDialog) AccountDialog(editing, onClose = { showDialog = false },
        onSave = { name, type -> onSave(editing?.id, name, type); showDialog = false },
        onDelete = { editing?.let { onDelete(it.id) }; showDialog = false })
}

@Composable
private fun AccountDialog(
    account: LedgerAccount?,
    onClose: () -> Unit,
    onSave: (String, AccountType) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: AccountType.CASH) }
    var confirmDelete by remember(account?.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (account == null) "添加账户" else "编辑账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("名称") }, placeholder = { Text("现金 / 微信 / 招商银行…") })
                SectionTitle("类型")
                TileGrid(AccountType.entries, 2, 8) { option ->
                    ChoiceTile(option.label, option == type) { type = option }
                }
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), type) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = {
            Row {
                if (account != null) TextButton({ confirmDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClose) { Text("取消") }
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除账户？") },
            text = {
                Text("“${account?.name.orEmpty()}”将从账户列表移除，历史流水会保留。此操作无法直接撤销。")
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("保留账户") }
            },
        )
    }
}
