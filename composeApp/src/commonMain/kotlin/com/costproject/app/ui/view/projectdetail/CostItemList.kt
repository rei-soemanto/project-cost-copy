package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.SectionCard

/**
 * Shared scaffolding for the four cost tabs: the scrolling list, the numbered
 * removable card per row, the running total, and the add button (or the
 * limit-reached notice).
 *
 * Each tab previously repeated this ~60-line skeleton verbatim; only the input
 * fields actually differ, so those are supplied through [fields].
 *
 * @param minItems rows below which the remove button is hidden. Lists that start
 *   with one row use 1; optional lists that may be empty use 0.
 */
@Composable
fun <T> CostItemList(
    items: List<T>,
    title: String,
    addLabel: String,
    idOf: (T) -> String,
    totalOf: (T) -> Long,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    minItems: Int = 0,
    maxItems: Int? = null,
    maxReachedLabel: String? = null,
    fields: @Composable ColumnScope.(T) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // itemsIndexed supplies the row number directly. The previous code called
        // list.indexOf(item) inside the item body, which is O(n) per row.
        itemsIndexed(items, key = { _, item -> idOf(item) }) { index, item ->
            SectionCard(
                title = title,
                number = (index + 1).toString(),
                onRemove = { onRemove(idOf(item)) },
                canRemove = items.size > minItems
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields(item)
                    ItemTotalRow(text = "Total: " + totalOf(item).formatRupiah())
                }
            }
        }
        item {
            if (maxItems == null || items.size < maxItems) {
                val label = if (maxItems == null) addLabel else "$addLabel (${items.size}/$maxItems)"
                AddButton(text = label, onClick = onAdd)
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = maxReachedLabel ?: "Batas maksimal tercapai",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
