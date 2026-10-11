package com.linxin.feature.luckin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.linxin.R
import com.linxin.core.designsystem.component.LxCard
import com.linxin.core.designsystem.component.LxChoiceChip
import com.linxin.core.designsystem.component.LxTopBar
import com.linxin.core.designsystem.theme.LxCream
import com.linxin.core.designsystem.theme.LxInkMuted
import com.linxin.core.designsystem.theme.LxSandDeep
import com.linxin.core.designsystem.theme.LxTerra
import com.linxin.core.designsystem.theme.LxTerraSoft
import com.linxin.core.luckin.LuckinMcp
import com.linxin.core.settings.LuckinPrefs
import com.linxin.core.settings.LuckinState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 趣味分支（fun-luckin）上的瑞幸两页：选门店 + 搜商品。
 *
 * 只读。下单那颗按钮还没做，等他逐项批了才接 createOrder——那是真花钱。
 * 数据来自瑞幸官方 MCP，字段是 2026-10-11 用真 token 跑通后照抄的，没猜。
 */

private fun luckyShape() = RoundedCornerShape(16.dp)

@Composable
private fun LxField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(luckyShape())
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LxPillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(luckyShape())
            .background(if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 登录状态卡：没登录就一颗登录按钮，登录了就显示掩码凭证 + 退出 */
@Composable
private fun LuckinAccountCard(state: LuckinState, onLogin: () -> Unit, onLogout: () -> Unit) {
    LxCard {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.luckin_account_title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.configured) stringResource(R.string.luckin_account_on, state.maskedToken)
                else stringResource(R.string.luckin_account_off),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.configured) {
                    LxPillButton(stringResource(R.string.luckin_account_relogin), onClick = onLogin)
                    LxPillButton(stringResource(R.string.luckin_account_logout), onClick = onLogout)
                } else {
                    LxPillButton(stringResource(R.string.luckin_account_login), onClick = onLogin)
                }
            }
        }
    }
}

@Composable
fun LuckinShopsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onPickShop: (LuckinMcp.Shop) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LuckinPrefs(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(LuckinState()) }
    var shops by remember { mutableStateOf<List<LuckinMcp.Shop>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lng by remember { mutableStateOf(state.lng) }
    var lat by remember { mutableStateOf(state.lat) }
    var tick by remember { mutableStateOf(0) }
    var locNote by remember { mutableStateOf<String?>(null) }
    lateinit var askLocPerm: () -> Unit
    val fillFromLocation: () -> Unit = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            askLocPerm()
        } else {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            val fresh = listOf(
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER,
            ).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .filter { System.currentTimeMillis() - it.time < 10 * 60_000L }
                .maxByOrNull { it.time }
            if (fresh == null) {
                locNote = context.getString(R.string.luckin_loc_stale)
            } else {
                locNote = null
                lng = "%.6f".format(fresh.longitude)
                lat = "%.6f".format(fresh.latitude)
                scope.launch { prefs.setPoint(lng, lat); tick++ }
            }
        }
    }
    val askLoc = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillFromLocation() else locNote = "还没给定位权限" }



    LaunchedEffect(Unit) { prefs.state.collect { s -> state = s; lng = s.lng; lat = s.lat } }

    LaunchedEffect(tick, state.token) {
        val point = lng.toDoubleOrNull()?.let { l -> lat.toDoubleOrNull()?.let { l to it } } ?: return@LaunchedEffect
        if (state.token.isBlank()) return@LaunchedEffect
        loading = true; error = null
        runCatching { LuckinMcp.queryShops(state.token, point.first, point.second) }
            .onSuccess { shops = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    Scaffold(topBar = { LxTopBar(title = stringResource(R.string.luckin_shops_title), onBack = onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            LuckinAccountCard(
                state,
                onLogin = onLogin,
                onLogout = { scope.launch { prefs.clearToken(); shops = emptyList(); tick++ } },
            )
            Spacer(Modifier.height(14.dp))
            LxCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.luckin_point_title), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.luckin_point_hint), fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LxField(lng, { lng = it }, stringResource(R.string.luckin_lng), Modifier.weight(1f))
                        LxField(lat, { lat = it }, stringResource(R.string.luckin_lat), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LxPillButton(stringResource(R.string.luckin_use_location)) { fillFromLocation() }
                        LxPillButton(stringResource(R.string.luckin_query_shops)) {
                            scope.launch { prefs.setPoint(lng, lat); tick++ }
                        }
                    }
                    locNote?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let {
                Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                Spacer(Modifier.height(14.dp))
            }
            shops.forEach { s ->
                LxCard(onClick = { onPickShop(s) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.deptName, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f))
                            Text(
                                s.distanceKm?.let { String.format("%.2f km", it) } ?: "",
                                fontSize = 13.sp, color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        s.address?.let {
                            Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.height(4.dp))
                        }
                        Row {
                            s.workStatus?.let {
                                Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                (s.workTimeStart ?: "?") + "-" + (s.workTimeEnd ?: "?") + "  " + (s.number ?: ""),
                                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 商品页：搜索框 + 热门一键搜。热门胶囊就是把同样那句话填进搜索框再搜一次，
 * 手不用打字也能走通全链路（平常人更吃这套，也是我能用点按验收的原因）。
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
    var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<LuckinMcp.Product>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    var menu by remember { mutableStateOf<List<Pair<String, List<LuckinMcp.Product>>>>(emptyList()) }
    var menuLoading by remember { mutableStateOf(true) }
    var menuError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { token = prefs.current().token }

    // 品类词各搜一次，全部回来才渲染，不做一路加载一路蹦
    LaunchedEffect(deptId, token) {
        if (token.isBlank()) { menuLoading = false; return@LaunchedEffect }
        menuLoading = true; menuError = null
        runCatching { LuckinMcp.queryMenu(token, deptId) }
            .onSuccess { groups ->
                val seen = HashSet<Long>()
                menu = groups.map { (word, list) ->
                    word to list.filter { seen.add(it.productId) }
                }.filter { it.second.isNotEmpty() }
            }
            .onFailure { menuError = it.message ?: it.javaClass.simpleName }
        menuLoading = false
    }

    Scaffold(topBar = { LxTopBar(title = deptName.ifBlank { stringResource(R.string.luckin_products_title) }, onBack = onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LxField(query, { query = it }, stringResource(R.string.luckin_search_hint), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                LxPillButton(stringResource(R.string.luckin_search), enabled = query.isNotBlank() && token.isNotBlank()) {
                    runSearch(query)
                }
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
                    Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(14.dp))
                }
                if (products.isEmpty() && !loading) {
                    Text(stringResource(R.string.luckin_products_empty), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                products.forEach { p -> LuckinProductCard(p, onPickProduct) }
            } else {
                if (menuLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                menuError?.let {
                    Text(stringResource(R.string.luckin_menu_error, it), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error)
                    Spacer(Modifier.height(14.dp))
                }
                if (!menuLoading && menu.isEmpty() && menuError == null) {
                    Text(stringResource(R.string.luckin_menu_empty), fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                menu.forEach { (word, list) ->
                    Text(word, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    list.forEach { p -> LuckinProductCard(p, onPickProduct) }
                    Spacer(Modifier.height(6.dp))
                }
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

/** 商品卡：菜单和搜索结果共用一张样子 */
@Composable
private fun LuckinProductCard(p: LuckinMcp.Product, onPick: (LuckinMcp.Product) -> Unit) {
    LxCard(onClick = { onPick(p) }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.productName, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                p.estimatePrice?.let {
                    Text("¥${"%.1f".format(it)}", fontSize = 15.sp, color = MiuixTheme.colorScheme.primary)
                }
                p.initialPrice?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("¥${"%.1f".format(it)}", fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            p.tags?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it.joinToString(" · "), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            p.productAttrs?.takeIf { it.isNotEmpty() }?.let { attrs ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.luckin_specs_count, attrs.size),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 规格只读展示用的小胶囊：外观跟 LxChoiceChip 一致，但没有点击反馈，免得看着能按 */
@Composable
private fun LxSpecChip(text: String, selected: Boolean, enabled: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val container = when {
        !enabled -> LxCream.copy(alpha = 0.65f)
        selected -> LxTerraSoft
        else -> LxCream
    }
    val border = when {
        !enabled -> LxSandDeep.copy(alpha = 0.5f)
        selected -> LxTerra
        else -> LxSandDeep
    }
    val fg = when {
        !enabled -> LxInkMuted.copy(alpha = 0.6f)
        selected -> LxTerra
        else -> LxInkMuted
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(container)
            .border(width = 1.dp, color = border, shape = shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MiuixTheme.textStyles.footnote1, color = fg)
    }
}

/**
 * 商品规格页：queryProductDetailInfo 回来的六组属性（杯型/温度/咖啡豆/咖啡浓度/糖度/加料之类）
 * 按组分块列出来，选中项高亮，加价项在名字后面带 +¥。
 *
 * 只读。改规格、算价、下单都还没接——那是写操作，等他逐项批。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuckinProductDetailScreen(
    deptId: Long,
    productId: Long,
    productName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LuckinPrefs(context) }
    var token by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<LuckinMcp.Product?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        token = prefs.current().token
        loading = true; error = null
        runCatching { LuckinMcp.productDetail(token, deptId, productId) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    Scaffold(topBar = {
        LxTopBar(title = productName.ifBlank { stringResource(R.string.luckin_detail_title) }, onBack = onBack)
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let {
                Text(stringResource(R.string.luckin_error, it), fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                Spacer(Modifier.height(14.dp))
            }
            val p = detail
            if (p != null) {
                LxCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        p.pictureUrl?.let { url ->
                            Box(
                                Modifier.size(64.dp).clip(luckyShape())
                                    .background(MiuixTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.productName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                p.estimatePrice?.let {
                                    Text(
                                        "¥${"%.1f".format(it)}",
                                        fontSize = 17.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                p.initialPrice?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.luckin_detail_origin, "%.1f".format(it)),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                val groups = p.productAttrs.orEmpty()
                if (groups.isEmpty()) {
                    Text(
                        stringResource(R.string.luckin_attrs_empty),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                groups.forEach { group ->
                    LxCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(group.attributeName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.subAttrs.orEmpty().forEach { sub ->
                                    val extra = sub.price?.takeIf { it > 0.0 }
                                    LxSpecChip(
                                        text = sub.attributeName +
                                            (extra?.let { "  +¥${"%.1f".format(it)}" } ?: "") +
                                            (if (sub.canSelected == 0) "  " +
                                                stringResource(R.string.luckin_unavailable) else ""),
                                        selected = sub.selected == true,
                                        enabled = sub.canSelected != 0,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    stringResource(R.string.luckin_detail_readonly),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
