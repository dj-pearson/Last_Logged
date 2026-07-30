package com.pearsonmedia.lastlogged.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pearsonmedia.lastlogged.R
import com.pearsonmedia.lastlogged.util.AccessibilityUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val reduceMotion = AccessibilityUtil.rememberReduceMotion()

    val progress by animateFloatAsState(
        targetValue = (pagerState.currentPage + 1) / 3f,
        animationSpec = if (reduceMotion) tween(0) else tween(400),
        label = "onboardingProgress"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress + Skip header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
            )
            if (pagerState.currentPage < 2) {
                TextButton(onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                }) {
                    Text(stringResource(R.string.skip))
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val isVisible = pagerState.currentPage == page
            when (page) {
                0 -> WelcomePage(isVisible = isVisible, reduceMotion = reduceMotion)
                1 -> CategorySelectionPage(
                    selectedCategories = selectedCategories,
                    onToggleCategory = { viewModel.toggleCategory(it) },
                    isVisible = isVisible,
                    reduceMotion = reduceMotion
                )
                2 -> WidgetPromptPage(isVisible = isVisible, reduceMotion = reduceMotion)
            }
        }

        // Navigation buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicators
            PageIndicator(
                pageCount = 3,
                currentPage = pagerState.currentPage
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (pagerState.currentPage < 2) {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.continue_action))
                }
            } else {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.get_started))
                }
            }

            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}

@Composable
private fun OnboardingIcon(
    isVisible: Boolean,
    reduceMotion: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun WelcomePage(isVisible: Boolean, reduceMotion: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingIcon(isVisible, reduceMotion, Icons.Outlined.Celebration)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_headline),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelectionPage(
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    isVisible: Boolean,
    reduceMotion: Boolean
) {
    val categories = listOf(
        "Home Maintenance" to "home",
        "Health & Wellness" to "favorite",
        "Car Care" to "directions_car",
        "Personal Care" to "person",
        "Social & Family" to "group",
        "Pet Care" to "pets"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        OnboardingIcon(isVisible, reduceMotion, Icons.Outlined.Category)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_categories_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_categories_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEach { (name, _) ->
                // Read outside the semantics {} lambda — stringResource() is @Composable.
                val chipA11y = stringResource(R.string.category_chip_a11y, name)

                FilterChip(
                    selected = selectedCategories.contains(name),
                    onClick = { onToggleCategory(name) },
                    label = { Text(name) },
                    modifier = Modifier.semantics {
                        contentDescription = chipA11y
                    }
                )
            }
        }
    }
}

@Composable
private fun WidgetPromptPage(isVisible: Boolean, reduceMotion: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingIcon(isVisible, reduceMotion, Icons.Outlined.Widgets)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_widget_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_widget_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_widget_steps),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .height(8.dp)
                    .then(
                        if (index == currentPage) Modifier
                            .fillMaxWidth(0.08f)
                            .height(8.dp)
                        else Modifier
                            .fillMaxWidth(0.04f)
                            .height(8.dp)
                    )
                    .padding(0.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = if (index == currentPage)
                            androidx.compose.ui.graphics.Color(0xFF4F46E5)
                        else
                            androidx.compose.ui.graphics.Color(0xFFD1D5DB),
                        radius = size.minDimension / 2
                    )
                }
            }
        }
    }
}
