package com.costproject.app.ui.view.projectdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.costproject.app.domain.model.Project
import com.costproject.app.ui.util.formatRupiah
import com.costproject.app.ui.view.common.AddButton
import com.costproject.app.ui.view.common.SummaryRow

@Composable
fun RekapTab(
    project: Project,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GRAND TOTAL",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.grandTotal.formatRupiah(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detail Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Project", project.name.ifBlank { "-" })
                    SummaryRow("Customer", project.customer.ifBlank { "-" })
                    SummaryRow("Penanggung Jawab", project.pic.ifBlank { "-" })
                    SummaryRow("Dibuat", project.createdAt)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rincian Biaya",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Jasa", project.totalJasa.formatRupiah())
                    SummaryRow("Barang", project.totalBarang.formatRupiah())
                    SummaryRow("Transportasi", project.totalTransportasi.formatRupiah())
                    SummaryRow("Lain-lain", project.totalLainLain.formatRupiah())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Total Biaya", project.grandTotal.formatRupiah(), isTotal = true)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kontrak Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        "Harga Kontrak",
                        if (project.hargaKontrak.isBlank()) "-" else project.nilaiKontrak.formatRupiah()
                    )
                    SummaryRow("Total Biaya", project.grandTotal.formatRupiah())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sisa Kontrak",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (project.hargaKontrak.isBlank()) "-"
                            else project.sisaKontrak.formatRupiah(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (project.sisaKontrak >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (project.hargaKontrak.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (project.sisaKontrak >= 0)
                                "Kontrak dikurangi total Jasa, Barang, Transportasi, dan Lain-lain. Nilai positif = sisa keuntungan."
                            else
                                "Biaya melebihi nilai kontrak. Nilai negatif = potensi kerugian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            AddButton(text = "Reset Data Project Ini", onClick = onReset)
        }
    }
}
