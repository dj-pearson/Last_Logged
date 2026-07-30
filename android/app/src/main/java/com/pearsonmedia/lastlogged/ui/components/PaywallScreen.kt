package com.pearsonmedia.lastlogged.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.service.RevenueCatService
import com.pearsonmedia.lastlogged.util.AccessibilityUtil
import com.pearsonmedia.lastlogged.util.UrlOpener
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    isHardPaywall: Boolean = false,
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val packages by viewModel.packages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedPackageId by remember { mutableStateOf(RevenueCatService.PRODUCT_ANNUAL) }
    val reduceMotion = AccessibilityUtil.rememberReduceMotion()
    val context = LocalContext.current
    val activity = context.findActivity()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    if (!isHardPaywall) {
                        IconButton(
                            onClick = {
                                viewModel.trackDismissed()
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Social proof row
            SocialProofRow()

            Spacer(modifier = Modifier.height(20.dp))

            // Auto-advancing feature carousel
            FeatureCarousel(reduceMotion = reduceMotion)

            Spacer(modifier = Modifier.height(20.dp))

            // Package cards
            packages.forEach { pkg ->
                val isSelected = selectedPackageId == pkg.identifier
                val isRecommended = pkg.productId == RevenueCatService.PRODUCT_ANNUAL ||
                    pkg.identifier.equals("$" + "rc_annual", ignoreCase = true)
                PackageCard(
                    title = pkg.title,
                    description = pkg.description,
                    price = pkg.price,
                    isSelected = isSelected,
                    isRecommended = isRecommended,
                    reduceMotion = reduceMotion,
                    onClick = { selectedPackageId = pkg.identifier }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase button with press scale
            var ctaPressed by remember { mutableStateOf(false) }
            val ctaScale by animateFloatAsState(
                targetValue = if (ctaPressed && !reduceMotion) 0.97f else 1f,
                animationSpec = if (reduceMotion) tween(0) else spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "ctaScale"
            )
            Button(
                onClick = {
                    ctaPressed = true
                    activity?.let { viewModel.purchase(it, selectedPackageId) }
                },
                enabled = !isLoading && activity != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(ctaScale)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.subscribe), fontWeight = FontWeight.SemiBold)
                }
            }
            LaunchedEffect(ctaPressed) {
                if (ctaPressed) {
                    delay(200)
                    ctaPressed = false
                }
            }

            // Restore purchases
            TextButton(onClick = { viewModel.restorePurchases() }) {
                Text(stringResource(R.string.restore_purchases))
            }

            // Continue with free (hard paywall only)
            if (isHardPaywall) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.continue_with_free))
                }
            }

            // Error
            error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Legal links
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { UrlOpener.openTerms(context) }) {
                    Text(stringResource(R.string.terms_short), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { UrlOpener.openPrivacy(context) }) {
                    Text(stringResource(R.string.privacy_short), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SocialProofRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(5) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.paywall_social_proof),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class PaywallFeature(val title: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCarousel(reduceMotion: Boolean) {
    val features = remember {
        listOf(
            PaywallFeature("Unlimited trackers", "No 3-tracker limit — track everything."),
            PaywallFeature("Full completion history", "Every log preserved, forever."),
            PaywallFeature("All widget sizes", "Small, medium, and large home-screen widgets."),
            PaywallFeature("Cloud sync", "Seamless across all your devices."),
            PaywallFeature("Priority notifications", "Never miss an overdue item.")
        )
    }
    val pagerState = rememberPagerState(pageCount = { features.size })

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(5000)
            val next = (pagerState.currentPage + 1) % features.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) { page ->
            val feature = features[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(features.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun PackageCard(
    title: String,
    description: String,
    price: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit
) {
    // Shimmer border for recommended plan
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val borderColor = when {
        isSelected && isRecommended && !reduceMotion -> MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp

    // Read outside the semantics {} lambda — stringResource() is @Composable.
    val packageA11y = stringResource(R.string.package_a11y, title, price)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                 else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(borderWidth, borderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .semantics { contentDescription = packageA11y }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (isRecommended) {
            // Animated "Most popular" badge
            val badgeScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = if (reduceMotion) tween(0) else spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "badgeScale"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .scale(badgeScale)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.most_popular),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
