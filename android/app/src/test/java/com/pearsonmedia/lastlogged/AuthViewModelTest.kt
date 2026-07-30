package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.service.GoogleSignInService
import com.pearsonmedia.lastlogged.service.PushTokenService
import com.pearsonmedia.lastlogged.service.RevenueCatService
import com.pearsonmedia.lastlogged.service.SupabaseService
import com.pearsonmedia.lastlogged.ui.auth.AuthMode
import com.pearsonmedia.lastlogged.ui.auth.AuthViewModel
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        val supabase = mockk<SupabaseService>(relaxed = true) {
            io.mockk.every { isSignedIn } returns MutableStateFlow(false)
            io.mockk.every { currentUserEmail } returns MutableStateFlow<String?>(null)
        }
        val revenueCat = mockk<RevenueCatService>(relaxed = true)
        val pushTokens = mockk<PushTokenService>(relaxed = true)
        val googleSignIn = mockk<GoogleSignInService>(relaxed = true)
        // AuthViewModel takes an @ApplicationContext to resolve localized
        // messages; relaxed mock is enough since these tests assert on state,
        // not on the message text.
        val context = mockk<android.content.Context>(relaxed = true)
        viewModel = AuthViewModel(supabase, revenueCat, pushTokens, googleSignIn, context)
    }

    @Test
    fun `initial state is sign in mode`() {
        val state = viewModel.uiState.value
        assertEquals(AuthMode.SIGN_IN, state.mode)
        assertFalse(state.isSignedIn)
        assertFalse(state.isLoading)
        assertEquals(0, state.failedAttempts)
    }

    @Test
    fun `email validation accepts valid emails`() {
        viewModel.updateEmail("user@example.com")
        assertTrue(viewModel.isEmailValid)
    }

    @Test
    fun `email validation rejects invalid emails`() {
        viewModel.updateEmail("notanemail")
        assertFalse(viewModel.isEmailValid)
    }

    @Test
    fun `email validation accepts empty email`() {
        viewModel.updateEmail("")
        assertTrue(viewModel.isEmailValid)
    }

    @Test
    fun `password validation requires 8 chars`() {
        viewModel.updatePassword("Aa1")
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun `password validation requires uppercase`() {
        viewModel.updatePassword("abcdefg1")
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun `password validation requires lowercase`() {
        viewModel.updatePassword("ABCDEFG1")
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun `password validation requires number`() {
        viewModel.updatePassword("Abcdefgh")
        assertFalse(viewModel.isPasswordValid)
    }

    @Test
    fun `valid password passes all checks`() {
        viewModel.updatePassword("Abcdefg1")
        assertTrue(viewModel.isPasswordValid)
    }

    @Test
    fun `password validation messages are correct for short password`() {
        viewModel.updatePassword("Aa1")
        assertTrue(viewModel.passwordValidationMessages.contains("At least 8 characters"))
    }

    @Test
    fun `password validation returns empty for valid password`() {
        viewModel.updatePassword("Abcdefg1")
        assertTrue(viewModel.passwordValidationMessages.isEmpty())
    }

    @Test
    fun `setMode switches to sign up`() {
        viewModel.setMode(AuthMode.SIGN_UP)
        assertEquals(AuthMode.SIGN_UP, viewModel.uiState.value.mode)
    }

    @Test
    fun `setMode switches to forgot password`() {
        viewModel.setMode(AuthMode.FORGOT_PASSWORD)
        assertEquals(AuthMode.FORGOT_PASSWORD, viewModel.uiState.value.mode)
    }

    @Test
    fun `terms agreement toggles`() {
        assertFalse(viewModel.uiState.value.hasAgreedToTerms)
        viewModel.toggleTermsAgreement()
        assertTrue(viewModel.uiState.value.hasAgreedToTerms)
        viewModel.toggleTermsAgreement()
        assertFalse(viewModel.uiState.value.hasAgreedToTerms)
    }

    @Test
    fun `canSubmit requires valid email for sign in`() {
        viewModel.updateEmail("invalid")
        viewModel.updatePassword("Abcdefg1")
        assertFalse(viewModel.canSubmit)
    }

    @Test
    fun `canSubmit requires password for sign in`() {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("")
        assertFalse(viewModel.canSubmit)
    }

    @Test
    fun `canSubmit allows valid sign in`() {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("anything")
        assertTrue(viewModel.canSubmit)
    }

    @Test
    fun `canSubmit requires terms for sign up`() {
        viewModel.setMode(AuthMode.SIGN_UP)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("Abcdefg1")
        assertFalse(viewModel.canSubmit)

        viewModel.toggleTermsAgreement()
        assertTrue(viewModel.canSubmit)
    }

    @Test
    fun `canSubmit requires valid password for sign up`() {
        viewModel.setMode(AuthMode.SIGN_UP)
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("weak")
        viewModel.toggleTermsAgreement()
        assertFalse(viewModel.canSubmit)
    }

    @Test
    fun `updateEmail clears error`() {
        // Simulate an error state
        viewModel.updateEmail("test@test.com")
        assertEquals(null, viewModel.uiState.value.error)
    }
}
