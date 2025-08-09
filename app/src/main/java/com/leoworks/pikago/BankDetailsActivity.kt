package com.leoworks.pikago

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.leoworks.pikago.models.BankDetails
import com.leoworks.pikago.repository.ProfileRepository
import com.leoworks.pikago.App
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject


class BankDetailsActivity : AppCompatActivity() {

    private lateinit var repo: ProfileRepository
    private var existing: BankDetails? = null

    private lateinit var root: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnSave: MaterialButton

    private lateinit var tilHolder: TextInputLayout
    private lateinit var tilAccount: TextInputLayout
    private lateinit var tilIfsc: TextInputLayout
    private lateinit var tilBank: TextInputLayout
    private lateinit var tilBranch: TextInputLayout

    private lateinit var etHolder: TextInputEditText
    private lateinit var etAccount: TextInputEditText
    private lateinit var etIfsc: TextInputEditText
    private lateinit var etBank: TextInputEditText
    private lateinit var etBranch: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bank_details)

        supportActionBar?.title = "Bank Details"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Edge to edge
        root = findViewById(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        repo = ProfileRepository(this)

        bindViews()
        setListeners()

        // Load any existing details
        loadBankDetails()
    }

    private fun bindViews() {
        progress = findViewById(R.id.progress)
        btnSave = findViewById(R.id.btnSaveBank)

        tilHolder = findViewById(R.id.tilHolder)
        tilAccount = findViewById(R.id.tilAccount)
        tilIfsc = findViewById(R.id.tilIfsc)
        tilBank = findViewById(R.id.tilBank)
        tilBranch = findViewById(R.id.tilBranch)

        etHolder = findViewById(R.id.etHolder)
        etAccount = findViewById(R.id.etAccount)
        etIfsc = findViewById(R.id.etIfsc)
        etBank = findViewById(R.id.etBank)
        etBranch = findViewById(R.id.etBranch)
    }

    private fun setListeners() {
        btnSave.setOnClickListener { validateAndSave() }
        etBranch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validateAndSave()
                true
            } else false
        }
    }

    private fun loadBankDetails() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                existing = repo.getBankDetails()
                existing?.let { bd ->
                    // Pre-fill existing data
                    etHolder.setText(bd.account_holder_name)
                    etAccount.setText(bd.account_number)
                    etIfsc.setText(bd.ifsc_code)
                    etBank.setText(bd.bank_name)
                    etBranch.setText(bd.branch_name ?: "")
                    btnSave.text = "Update Details"

                    // Change title to indicate update mode
                    supportActionBar?.title = "Update Bank Details"
                } ?: run {
                    // New entry mode
                    btnSave.text = "Save Details"
                    supportActionBar?.title = "Add Bank Details"
                }
            } catch (e: Exception) {
                showError("Failed to load bank details: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun validateAndSave() {
        // clear errors
        tilHolder.error = null
        tilAccount.error = null
        tilIfsc.error = null
        tilBank.error = null
        tilBranch.error = null

        val holder = etHolder.text?.toString()?.trim().orEmpty()
        val account = etAccount.text?.toString()?.trim().orEmpty()
        val ifsc = etIfsc.text?.toString()?.trim().orEmpty().uppercase()
        val bank = etBank.text?.toString()?.trim().orEmpty()
        val branch = etBranch.text?.toString()?.trim().orEmpty().ifBlank { null }

        var ok = true
        if (holder.isEmpty()) { tilHolder.error = "Account holder name is required"; ok = false }
        if (account.length < 9) { tilAccount.error = "Enter a valid account number"; ok = false }
        if (!isValidIfsc(ifsc)) { tilIfsc.error = "Invalid IFSC format"; ok = false }
        if (bank.isEmpty()) { tilBank.error = "Bank name is required"; ok = false }

        if (!ok) {
            showError("Please fix the highlighted fields")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                if (existing == null) {
                    // Create new bank details
                    repo.saveBankDetails(
                        accountHolderName = holder,
                        accountNumber = account,
                        ifscCode = ifsc,
                        bankName = bank,
                        branchName = branch
                    )
                } else {
                    // Update existing bank details using JsonObject
                    val updateData = buildJsonObject {
                        put("account_holder_name", JsonPrimitive(holder))
                        put("account_number", JsonPrimitive(account))
                        put("ifsc_code", JsonPrimitive(ifsc))
                        put("bank_name", JsonPrimitive(bank))
                        put("branch_name", branch?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                    }

                    App.supabase.from("bank_details")
                        .update(updateData) {
                            filter {
                                eq("id", existing!!.id)
                            }
                            select()
                        }
                        .decodeSingle<BankDetails>()
                }

                // Recompute completion percentage
                repo.calculateProfileCompletion()

                showSuccess("Bank details saved successfully")
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showError("Failed to save: ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun isValidIfsc(value: String): Boolean {
        // Simple IFSC: 4 letters + 0 + 6 digits  (e.g., HDFC0ABC123)
        return Regex("^[A-Z]{4}0[0-9A-Z]{6}\$").matches(value)
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !loading
        btnSave.text = if (loading) {
            if (existing != null) "Updating..." else "Saving..."
        } else {
            if (existing != null) "Update Details" else "Save Details"
        }
    }

    private fun showError(msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}