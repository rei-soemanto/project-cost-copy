package com.costproject.app.ui.viewmodel

import com.costproject.app.data.DataError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun obvious_mistakes_are_caught_without_calling_the_server() = runTest {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.toggleMode() // register
        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("short")

        vm.submit()
        advanceUntilIdle()

        val errors = vm.uiState.value.fieldErrors
        assertEquals(setOf("fullName", "email", "password"), errors.keys)
        assertTrue(repo.registerCalls.isEmpty())
    }

    @Test
    fun a_successful_login_clears_the_password_and_opens_the_session() = runTest {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf("a@b.com" to "password123"), repo.loginCalls)
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals("", vm.uiState.value.password)
        assertNull(vm.uiState.value.errorMessage)
        // Navigation reacts to this; the ViewModel itself does not navigate.
        assertTrue(repo.hasSession.value)
    }

    @Test
    fun wrong_credentials_show_the_repository_message() = runTest {
        val repo = FakeAuthRepository().apply { result = Result.failure(DataError.InvalidCredentials()) }
        val vm = AuthViewModel(repo)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("wrong-password")

        vm.submit()
        advanceUntilIdle()

        assertEquals("Email atau kata sandi salah.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun server_validation_errors_land_on_their_fields() = runTest {
        val repo = FakeAuthRepository().apply {
            result = Result.failure(DataError.Validation("invalid", mapOf("email" to "Email tidak valid")))
        }
        val vm = AuthViewModel(repo)
        vm.toggleMode()
        vm.onFullNameChange("Rei")
        vm.onEmailChange("rei@example.com")
        vm.onPasswordChange("password123")

        vm.submit()
        advanceUntilIdle()

        assertEquals("Email tidak valid", vm.uiState.value.fieldErrors["email"])
    }

    @Test
    fun editing_a_field_clears_its_error() = runTest {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.submit() // everything blank
        assertTrue("email" in vm.uiState.value.fieldErrors)

        vm.onEmailChange("a")
        assertFalse("email" in vm.uiState.value.fieldErrors)
        assertTrue("password" in vm.uiState.value.fieldErrors, "unrelated field error was cleared too")
    }

    @Test
    fun a_second_tap_while_submitting_is_ignored() = runTest {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password123")

        vm.submit()
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.loginCalls.size)
    }
}
