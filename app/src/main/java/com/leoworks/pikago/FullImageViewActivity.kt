package com.leoworks.pikago

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar

class FullImageViewActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var ivFullImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image_view)

        initializeViews()
        setupToolbar()
        loadImage()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        ivFullImage = findViewById(R.id.ivFullImage)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = intent.getStringExtra("title") ?: "Image View"
        }
    }

    private fun loadImage() {
        val imageUrl = intent.getStringExtra("image_url")

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(ivFullImage)
        } else {
            Snackbar.make(ivFullImage, "No image to display", Snackbar.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}