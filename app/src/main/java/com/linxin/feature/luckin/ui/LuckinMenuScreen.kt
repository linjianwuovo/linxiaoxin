package com.linxin.feature.luckin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxChoiceChip
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.luckin.LuckinMcp
import com.linxin.core.luckin.LuckinWeb
import com.linxin.core.settings.LuckinPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 菜单页：分类和商品来自瑞幸自家网关（匿名可读，见 LuckinWeb），
 * 分类里的商品名按展开懒加载补，六发并发、补完进缓存。
 * 上面那个搜索框走官方 MCP，那是带登录的口径，跟菜单不是一回事。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuckinProductsScreen(
    deptId: Long,
    deptName: String,
    onBack: () -> Unit,
    onPickProduct: (LuckinMcp.Product) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LuckinPrefs(context) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<LuckinMcp.Product>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    var kinds by remember { mutableStateOf<List<LuckinWeb.MenuKind>>(emptyList()) }
    var kindsLoading by remember { mutableStateOf(true) }
    var kindsError by remember { mutableStateOf<String?>(null) }
    var openKind by remember { mutableStateOf<String?>(null) }
    var briefs by remember { mutableStateOf<Map<Long, LuckinWeb.ItemBrief>>(emptyMap()) }
    var hydrating by remember { mutableStateOf(false) }

    val runSearch: (String) -> Unit = { raw ->
        val q = raw.trim()
        if (q.isNotBlank() && token.isNotBlank()) {
            query = q
            scope.launch {
                loading = true; error = null; searched = true
                runCatching { LuckinMcp.searchProducts(token, deptId, q) }
                    .onSuccess { products = it }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { token = prefs.current().token }

    LaunchedEffect(deptId) {
        kindsLoading = true; kindsError = null
        runCatching { LuckinWeb.menuCached(deptId) }
            .onSuccess { list ->
                kinds = list
                if (openKind == null) openKind = list.firstOrNull()?.kindId
            }
            .onFailure { kindsError = it.message ?: it.javaClass.simpleName }
        kindsLoading = false
    }

    // 展开哪个分类就补哪个分类的名字，六发并发
    LaunchedEffect(openKind, kinds) {
        val kind = kinds.firstOrNull { it.kindId == openKind } ?: return@LaunchedEffect
        val missing = kind.items.map { it.productId }.filter { it !in briefs }
        if (missing.isEmpty()) return@LaunchedEffect
        hydrating = true
        coroutineScope {
            val gate = Semaphore(6)
            missing.map { id ->
                async { gate.withPermit { runCatching { LuckinWeb.itemBriefCached(deptId, id) }.getOrNull() } }
            }.awaitAll().forEach { b -> if (b != null) briefs = briefs + (b.productId to b) }
        }
        hydrating = false
    }

    Scaffold(topBar = {
        LxTopBar(title = deptName.ifBlank { stringResource(R.string.luckin_products_title) }, onBack = onBack)
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LxField(query, { query = it }, stringResource(R.string.luckin_search_hint), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                LxPillButton(
                    stringResource(R.string.luckin_search),
                    enabled = query.isNotBlank() && token.isNotBlank(),
                ) { runSearch(query) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.luckin_hot_title),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    stringResource(R.string.luckin_hot_1),
                    stringResource(R.string.luckin_hot_2),
                    stringResource(R.string.luckin_hot_3),
                ).forEach { name ->
                    LxChoiceChip(
                        text = name,
                        selected = query == name && products.isNotEmpty(),
                        enabled = token.isNotBlank(),
                        onClick = { runSearch(name) },
                    )
                }
                if (searched) {
                    LxChoiceChip(
                        text = stringResource(R.string.luckin_back_to_menu),
                        selected = false,
                        onClick = { searched = false; products = emptyList(); query = "" },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (searched) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                error?.let {
                    Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(14.dp))
                }
                if (products.isEmpty() && !loading) {
                    Text(stringResource(R.string.luckin_products_empty), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                products.forEach { p -> LuckinProductCard(p, onPickProduct) }
            } else {
                if (kindsLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                kindsError?.let {
                    Text(stringResource(R.string.luckin_menu_error, it), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(14.dp))
                }
                if (!kindsLoading && kinds.isEmpty() && kindsError == null) {
                    Text(stringResource(R.string.luckin_menu_empty), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                kinds.forEach { kind ->
                    val open = kind.kindId == openKind
                    LxCard(onClick = { openKind = if (open) null else kind.kindId }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    kind.kindName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.luckin_kind_count, kind.items.size),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            if (kind.kindDesc.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    kind.kindDesc,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (open) {
                        kind.items.forEach { item ->
                            val brief = briefs[item.productId]
                            LuckinMenuRow(item, brief) {
                                onPickProduct(
                                    LuckinMcp.Product(
                                        productId = item.productId,
                                        productName = brief?.name.orEmpty(),
                                        skuCode = null,
                                        pictureUrl = brief?.pictureUrl,
                                        initialPrice = item.initialPrice,
                                        estimatePrice = item.discountPrice.takeIf { it > 0.0 },
                                        productAttrs = null,
                                        tags = null,
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.luckin_menu_note),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 菜单里的一行：名字还没补上时先显示商品 id，但不挡着点进去看规格 */
@Composable
private fun LuckinMenuRow(
    item: LuckinWeb.MenuItem,
    brief: LuckinWeb.ItemBrief?,
    onPick: () -> Unit,
) {
    LxCard(onClick = onPick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    brief?.name ?: "#${item.productId}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    color = if (brief == null) MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                val deal = item.discountPrice.takeIf { it > 0.0 }
                if (deal != null) {
                    Text("¥${"%.1f".format(deal)}", fontSize = 15.sp, color = MiuixTheme.colorScheme.primary)
                    if (item.initialPrice > deal) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "¥${"%.1f".format(item.initialPrice)}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Text("¥${"%.1f".format(item.initialPrice)}", fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface)
                }
            }
            if (brief == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.luckin_name_pending),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
