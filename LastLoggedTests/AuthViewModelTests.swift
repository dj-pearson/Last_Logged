import XCTest
import SwiftData
@testable import LastLogged

/// Validation rules only — nothing here touches Supabase.
///
/// `AuthViewModel` takes a `ModelContext`, so each test gets an in-memory
/// container rather than the App Group store.
@MainActor
final class AuthViewModelTests: XCTestCase {

    private var container: ModelContainer!
    private var viewModel: AuthViewModel!

    override func setUpWithError() throws {
        try super.setUpWithError()

        let schema = Schema(versionedSchema: SchemaV1.self)
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        container = try ModelContainer(for: schema, configurations: [configuration])
        viewModel = AuthViewModel(modelContext: ModelContext(container))
    }

    override func tearDownWithError() throws {
        viewModel = nil
        container = nil
        try super.tearDownWithError()
    }

    // MARK: - Email

    func testValidEmailAddressesAreAccepted() {
        for address in [
            "user@example.com",
            "first.last@example.co.uk",
            "user+tag@example.com",
            "user_name@sub.example.io",
            "u@e.io",
        ] {
            viewModel.email = address
            XCTAssertTrue(viewModel.isEmailValid, "\(address) should be valid")
        }
    }

    func testInvalidEmailAddressesAreRejected() {
        for address in [
            "not-an-email",
            "@example.com",
            "user@",
            "user@example",
            "user @example.com",
            "user@exam ple.com",
        ] {
            viewModel.email = address
            XCTAssertFalse(viewModel.isEmailValid, "\(address) should be invalid")
        }
    }

    func testSurroundingWhitespaceIsToleratedOnEmail() {
        viewModel.email = "  user@example.com  "
        XCTAssertTrue(viewModel.isEmailValid)
    }

    func testEmptyEmailIsInvalidButShowsNoErrorYet() {
        viewModel.email = ""
        XCTAssertFalse(viewModel.isEmailValid)
        // No nagging before the user has typed anything.
        XCTAssertNil(viewModel.emailValidationMessage)
    }

    func testInvalidEmailProducesAMessage() {
        viewModel.email = "nope"
        XCTAssertNotNil(viewModel.emailValidationMessage)
    }

    func testValidEmailProducesNoMessage() {
        viewModel.email = "user@example.com"
        XCTAssertNil(viewModel.emailValidationMessage)
    }

    // MARK: - Password complexity

    func testPasswordMeetingEveryRuleIsValid() {
        viewModel.password = "Passw0rdd"
        XCTAssertTrue(viewModel.isPasswordValid)
    }

    func testPasswordShorterThanEightCharactersIsInvalid() {
        viewModel.password = "Pass0rd"
        XCTAssertFalse(viewModel.isPasswordValid)
    }

    func testPasswordWithoutAnUppercaseLetterIsInvalid() {
        viewModel.password = "passw0rdd"
        XCTAssertFalse(viewModel.isPasswordValid)
    }

    func testPasswordWithoutALowercaseLetterIsInvalid() {
        viewModel.password = "PASSW0RDD"
        XCTAssertFalse(viewModel.isPasswordValid)
    }

    func testPasswordWithoutADigitIsInvalid() {
        viewModel.password = "Passwordd"
        XCTAssertFalse(viewModel.isPasswordValid)
    }

    func testValidationMessagesNameEveryUnmetRule() {
        viewModel.isSignUp = true
        viewModel.password = "abc"

        let messages = viewModel.passwordValidationMessages

        XCTAssertEqual(messages.count, 3)
        XCTAssertTrue(messages.contains("At least 8 characters"))
        XCTAssertTrue(messages.contains("One uppercase letter"))
        XCTAssertTrue(messages.contains("One number"))
        XCTAssertFalse(messages.contains("One lowercase letter"))
    }

    func testValidationMessagesAreEmptyOnceThePasswordIsValid() {
        viewModel.isSignUp = true
        viewModel.password = "Passw0rdd"
        XCTAssertTrue(viewModel.passwordValidationMessages.isEmpty)
    }

    func testValidationMessagesAreSuppressedWhenSigningIn() {
        // Sign-in must not lecture an existing user about their old password.
        viewModel.isSignUp = false
        viewModel.password = "abc"
        XCTAssertTrue(viewModel.passwordValidationMessages.isEmpty)
    }

    // MARK: - Form validity

    func testSignUpRequiresAgreeingToTerms() {
        viewModel.isSignUp = true
        viewModel.email = "user@example.com"
        viewModel.password = "Passw0rdd"

        viewModel.hasAgreedToTerms = false
        XCTAssertFalse(viewModel.isFormValid)

        viewModel.hasAgreedToTerms = true
        XCTAssertTrue(viewModel.isFormValid)
    }

    func testSignInDoesNotRequireTheTermsCheckbox() {
        viewModel.isSignUp = false
        viewModel.email = "user@example.com"
        viewModel.password = "Passw0rdd"
        viewModel.hasAgreedToTerms = false

        XCTAssertTrue(viewModel.isFormValid)
    }

    func testFormIsInvalidWhenEitherFieldFails() {
        viewModel.isSignUp = false
        viewModel.email = "bad"
        viewModel.password = "Passw0rdd"
        XCTAssertFalse(viewModel.isFormValid)

        viewModel.email = "user@example.com"
        viewModel.password = "short"
        XCTAssertFalse(viewModel.isFormValid)
    }

    // MARK: - Rate limiting

    func testNoCooldownIsActiveInitially() {
        XCTAssertEqual(viewModel.cooldownSecondsRemaining, 0)
        XCTAssertFalse(viewModel.isLockedOut)
    }
}
