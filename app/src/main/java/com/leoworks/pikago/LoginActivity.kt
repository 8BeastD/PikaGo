package com.leoworks.pikago

import android.animation.ObjectAnimator
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var signupButton: MaterialButton
    private lateinit var passwordToggle: View
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        setupClickListeners()
        animateEntrance()
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        loginButton = findViewById(R.id.loginButton)
        signupButton = findViewById(R.id.signupButton)
        passwordToggle = findViewById(R.id.passwordToggle)
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            performLogin()
        }

        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
    }

    private fun animateEntrance() {
        // Animate logo and form entrance
        val logo = findViewById<View>(R.id.logoContainer)
        val form = findViewById<View>(R.id.loginForm)

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

    private fun performLogin() {
        val email = emailInput.text?.toString()?.trim()
        val password = passwordInput.text?.toString()

        // Validation
        if (email.isNullOrEmpty()) {
            emailLayout.error = "Email is required"
            animateError(emailLayout)
            return
        }

        if (password.isNullOrEmpty()) {
            passwordLayout.error = "Password is required"
            animateError(passwordLayout)
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Please enter a valid email"
            animateError(emailLayout)
            return
        }

        // Clear previous errors
        emailLayout.error = null
        passwordLayout.error = null

        // Show loading state
        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                App.supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showSuccess("Login successful!")

                    // Navigate to main activity
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showError("Login failed: ${e.message}")
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        if (loading) {
            loginButton.text = "Signing in..."
            // Add loading animation to button
            val scaleX = ObjectAnimator.ofFloat(loginButton, "scaleX", 1f, 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(loginButton, "scaleY", 1f, 0.95f, 1f)
            scaleX.duration = 300
            scaleY.duration = 300
            scaleX.start()
            scaleY.start()
        } else {
            loginButton.text = "Sign In"
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}