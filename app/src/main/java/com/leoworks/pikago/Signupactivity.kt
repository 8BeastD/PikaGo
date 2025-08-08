package com.leoworks.pikago

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class UserInsert(
    val id: String,
    val email: String,
    val phone: String? = null
)

class SignupActivity : AppCompatActivity() {

    private lateinit var fullNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var fullNameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var signupButton: MaterialButton
    private lateinit var loginButton: MaterialButton
    private lateinit var passwordToggle: View
    private lateinit var confirmPasswordToggle: View
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initializeViews()
        setupClickListeners()
        animateEntrance()
    }

    private fun initializeViews() {
        fullNameInput = findViewById(R.id.fullNameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        fullNameLayout = findViewById(R.id.fullNameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        signupButton = findViewById(R.id.signupButton)
        loginButton = findViewById(R.id.loginButton)
        passwordToggle = findViewById(R.id.passwordToggle)
        confirmPasswordToggle = findViewById(R.id.confirmPasswordToggle)
    }

    private fun setupClickListeners() {
        signupButton.setOnClickListener {
            performSignup()
        }

        loginButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        confirmPasswordToggle.setOnClickListener {
            toggleConfirmPasswordVisibility()
        }
    }

    private fun animateEntrance() {
        // Animate logo and form entrance
        val logo = findViewById<View>(R.id.logoContainer)
        val form = findViewById<View>(R.id.signupForm)

        logo.alpha = 0f
        form.alpha = 0f
        logo.translationY = -50f
        form.translationY = 50f

        logo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .start()

        form.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(200)
            .start()
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            passwordInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
            passwordToggle.background = ContextCompat.getDrawable(this, R.drawable.ic_eye_off)
        } else {
            passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
            passwordToggle.background = ContextCompat.getDrawable(this, R.drawable.ic_eye)
        }
        passwordInput.setSelection(passwordInput.text?.length ?: 0)
    }

    private fun toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible
        if (isConfirmPasswordVisible) {
            confirmPasswordInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
            confirmPasswordToggle.background = ContextCompat.getDrawable(this, R.drawable.ic_eye_off)
        } else {
            confirmPasswordInput.transformationMethod = PasswordTransformationMethod.getInstance()
            confirmPasswordToggle.background = ContextCompat.getDrawable(this, R.drawable.ic_eye)
        }
        confirmPasswordInput.setSelection(confirmPasswordInput.text?.length ?: 0)
    }

    private fun performSignup() {
        val fullName = fullNameInput.text?.toString()?.trim()
        val email = emailInput.text?.toString()?.trim()
        val password = passwordInput.text?.toString()
        val confirmPassword = confirmPasswordInput.text?.toString()

        // Clear previous errors
        clearAllErrors()

        var hasError = false

        // Validation
        if (fullName.isNullOrEmpty()) {
            fullNameLayout.error = "Full name is required"
            animateError(fullNameLayout)
            hasError = true
        }

        if (email.isNullOrEmpty()) {
            emailLayout.error = "Email is required"
            animateError(emailLayout)
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Please enter a valid email"
            animateError(emailLayout)
            hasError = true
        }

        if (password.isNullOrEmpty()) {
            passwordLayout.error = "Password is required"
            animateError(passwordLayout)
            hasError = true
        } else if (password.length < 6) {
            passwordLayout.error = "Password must be at least 6 characters"
            animateError(passwordLayout)
            hasError = true
        }

        if (confirmPassword.isNullOrEmpty()) {
            confirmPasswordLayout.error = "Please confirm your password"
            animateError(confirmPasswordLayout)
            hasError = true
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            animateError(confirmPasswordLayout)
            hasError = true
        }

        if (hasError) return

        // Show loading state
        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Create Supabase Auth user
                val user = App.supabase.auth.signUpWith(Email) {
                    this.email = email!!
                    this.password = password!!
                }

                // Step 2: Create user record in our custom table
                val userId = user?.id

                if (userId != null) {
                    val userInsert = UserInsert(
                        id = userId,
                        email = email!!,
                        phone = null // Will be updated later
                    )

                    App.supabase.from("users").insert(userInsert)
                } else {
                    throw Exception("Failed to get user ID after signup")
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showSuccess("Account created successfully! Please check your email to verify your account.")

                    // Navigate back to login
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    e.printStackTrace()
                    showError("Signup failed: ${e.message}")
                }
            }
        }
    }

    private fun clearAllErrors() {
        fullNameLayout.error = null
        emailLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null
    }

    private fun setLoading(loading: Boolean) {
        signupButton.isEnabled = !loading
        if (loading) {
            signupButton.text = "Creating Account..."
            // Add loading animation to button
            val scaleX = ObjectAnimator.ofFloat(signupButton, "scaleX", 1f, 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(signupButton, "scaleY", 1f, 0.95f, 1f)
            scaleX.duration = 300
            scaleY.duration = 300
            scaleX.start()
            scaleY.start()
        } else {
            signupButton.text = "Create Account"
        }
    }

    private fun animateError(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 600
        shake.start()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}