package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.nigdroid.journal.databinding.ActivityJournalListBinding

class JournalListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalListBinding

    //    Firebase references
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var user: FirebaseUser
    var db = FirebaseFirestore.getInstance()
    var collectionReference: CollectionReference = db.collection("Journal")

    lateinit var journalList: MutableList<Journal>
    lateinit var adapter: JournalRecyclerAdapter

    lateinit var noPostsTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_journal_list)

//  Firebase authentification
        firebaseAuth = Firebase.auth
        user = firebaseAuth.currentUser!!

//  RecyclerView

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.floatingActionButton.setOnClickListener {
            startActivity(Intent(this, AddJournalActivity::class.java))
        }

//        Post arrayList
        journalList = arrayListOf<Journal>()

    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add -> {
                startActivity(Intent(this, AddJournalActivity::class.java))
            }

            R.id.action_signout -> {
                firebaseAuth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //    Getting all posts from firebase
    override fun onStart() {
        super.onStart()

        collectionReference.whereEqualTo(
            "userId",
            user.uid
        )
            .get()
            .addOnSuccessListener {
                if (!it.isEmpty) {
                   for(document in it){
                    val journal = Journal(
                        document.data["title"].toString(),
                        document.data["thoughts"].toString(),
                        document.data["imageUrl"].toString(),
                        document.data["userId"].toString(),
                        document.data["timeAdded"] as Timestamp,
                        document.data["username"].toString()
                    )
                       journalList.add(journal)

                   }

//        Recyclerview adapter
                    adapter = JournalRecyclerAdapter(this,journalList)
                    binding.recyclerView.setAdapter(adapter)
                    adapter.notifyDataSetChanged()
                }
                else{
                   binding.NoPostTv.visibility = View.VISIBLE
                }
            }.addOnFailureListener {
                Toast.makeText(this,"Error: ${it.message}",Toast.LENGTH_SHORT).show()

//                startActivity(Intent(this, MainActivity::class.java))

            }

    }
}