package com.nigdroid.journal

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.nigdroid.journal.databinding.ActivityAboutUsBinding

class AboutUsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutUsBinding
    private var isAppDescExpanded = false
    private var isAboutMeExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_about_us)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )
            insets
        }

        setupToolbar()
        setupClickListeners()
        animateEntrance()
    }

    private fun setupToolbar() {
        // Back button functionality
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupClickListeners() {
        // App Description expand/collapse
        binding.layoutAppDescHeader.setOnClickListener {
            isAppDescExpanded = !isAppDescExpanded
            toggleSection(
                binding.tvAppDescContent,
                binding.ivAppDescArrow,
                isAppDescExpanded
            )
        }

        // About Me expand/collapse
        binding.layoutAboutMeHeader.setOnClickListener {
            isAboutMeExpanded = !isAboutMeExpanded
            toggleSection(
                binding.layoutAboutMeContent,
                binding.ivAboutMeArrow,
                isAboutMeExpanded
            )
        }

        // Social media click listeners
        binding.ivInstagram.setOnClickListener {
            openInstagram("badshah_nigam") // Replace with your Instagram username
        }

        binding.ivLinkedIn.setOnClickListener {
            openLinkedIn("nigam-prasad-sahoo-b0768034a") // Replace with your LinkedIn profile ID
        }

        binding.ivYouTube.setOnClickListener {
            openYouTube("@pickpoooo2433") // Replace with your YouTube channel handle or ID
        }
    }

    private fun animateEntrance() {
        // Fade in animation for cards
        val cards = listOf(
            binding.cardAppDesc,
            binding.cardAboutMe
        )

        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 50f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay((index * 100).toLong())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // Animate profile photo
        binding.ivDeveloperPhoto.alpha = 0f
        binding.ivDeveloperPhoto.scaleX = 0.8f
        binding.ivDeveloperPhoto.scaleY = 0.8f

        binding.ivDeveloperPhoto.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun toggleSection(contentView: View, arrowView: View, isExpanded: Boolean) {
        if (isExpanded) {
            // Expand with smooth animation
            contentView.visibility = View.VISIBLE
            contentView.alpha = 0f
            contentView.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            rotateArrow(arrowView, 0f, 180f)

            // Smooth scroll animation
            binding.main.post {
                binding.main.smoothScrollTo(0, (contentView.parent as View).bottom)
            }
        } else {
            // Collapse
            contentView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    contentView.visibility = View.GONE
                }
                .start()

            rotateArrow(arrowView, 180f, 0f)
        }

        // Add scale animation to card
        val card = contentView.parent as View
        animateCardPress(card)
    }

    private fun rotateArrow(view: View, fromDegrees: Float, toDegrees: Float) {
        val rotate = RotateAnimation(
            fromDegrees,
            toDegrees,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        rotate.duration = 300
        rotate.fillAfter = true
        rotate.interpolator = AccelerateDecelerateInterpolator()
        view.startAnimation(rotate)
    }

    private fun animateCardPress(card: View) {
        val scaleDown = ValueAnimator.ofFloat(1f, 0.98f)
        scaleDown.duration = 100
        scaleDown.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            card.scaleX = value
            card.scaleY = value
        }

        val scaleUp = ValueAnimator.ofFloat(0.98f, 1f)
        scaleUp.duration = 100
        scaleUp.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            card.scaleX = value
            card.scaleY = value
        }

        scaleDown.start()
        scaleDown.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                scaleUp.start()
            }
        })
    }

    private fun openInstagram(username: String) {
        try {
            // Try to open in Instagram app
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.instagram.com/$username/")
            intent.setPackage("com.instagram.android")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Instagram app not found, open in browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/"))
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Instagram", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLinkedIn(profileId: String) {
        try {
            // Try to open in LinkedIn app
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/$profileId/")
            intent.setPackage("com.linkedin.android")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // LinkedIn app not found, open in browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/$profileId/"))
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open LinkedIn", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openYouTube(channelHandle: String) {
        try {
            // Try to open in YouTube app
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.youtube.com/$channelHandle")
            intent.setPackage("com.google.android.youtube")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // YouTube app not found, open in browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/$channelHandle"))
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open YouTube", Toast.LENGTH_SHORT).show()
            }
        }
    }
}