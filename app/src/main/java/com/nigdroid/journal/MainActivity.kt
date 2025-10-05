package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.nigdroid.journal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
private lateinit var binding: ActivityMainBinding

private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding= DataBindingUtil.setContentView(this,R.layout.activity_main)

        // Initialize Firebase Auth
        auth = Firebase.auth


        binding.btnCreateAccount.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogin.setOnClickListener {
          LoginWithEmailAndPassword(
              binding.etEmail.text.toString().trim() ,
              binding.etPassword.text.toString().trim()
          )
        }


    }
    private fun LoginWithEmailAndPassword(email: String, password: String){
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this)  { task ->

                if(task.isSuccessful){
//                sign in sucess
                    val journal = JournalUser.instance!!

                    journal.username = auth.currentUser!!.displayName
                    journal.userId = auth.currentUser!!.uid

                    goToJournalList()
                }
                else{
//                sign in failed
                    Toast.makeText(this, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                }

            }
            .addOnFailureListener {
                exception ->
            }
    }


    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if(currentUser != null){
            goToJournalList()
        }
    }

    private fun goToJournalList() {
        var intent = Intent(this, JournalListActivity::class.java)
        startActivity(intent)
        finish()
    }

}