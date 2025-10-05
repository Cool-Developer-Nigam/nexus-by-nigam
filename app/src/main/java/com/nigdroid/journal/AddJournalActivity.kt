package com.nigdroid.journal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nigdroid.journal.databinding.ActivityAddJournalBinding
import java.util.Date

class AddJournalActivity : AppCompatActivity() {
    lateinit var binding: ActivityAddJournalBinding

//  credential
    var currentUserId: String=""
    var currentUserName: String=""


//    Firebase firestore
    var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    lateinit var storageReference: StorageReference
    var collectionReference: CollectionReference = db.collection("Journal")
   lateinit var imageUri: Uri

//    Firebase authentification
    lateinit var auth: FirebaseAuth
    lateinit var user: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
       binding= DataBindingUtil.setContentView(this,R.layout.activity_add_journal)

        storageReference = FirebaseStorage.getInstance().getReference()

        auth= Firebase.auth

        binding.apply{
            progressBar.visibility= View.INVISIBLE
            if(JournalUser.instance!=null){
//                currentUserId=JournalUser.instance!!.userId.toString()
//                currentUserName=JournalUser.instance!!.username.toString()

                val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                currentUserName = sharedPref.getString("username", "Unknown") ?: "Unknown"
                currentUserId= auth.currentUser!!.uid


                postUsernameTv.text=currentUserName
                tvPostDate.text= Date().toString()
            }

            postCameraButton.setOnClickListener {
                val i:Intent=Intent(Intent.ACTION_GET_CONTENT)
                i.setType("image/*")
                startActivityForResult(i,1)
            }

           PostSaveJournalButton.setOnClickListener {
               saveJournal()
           }
        }
    }

    fun saveJournal(){
        var title:String=binding.etPostTitle.text.toString().trim()
        var thoughts:String=binding.etThoughts.text.toString().trim()
        var date:String=Date().toString()

        binding.progressBar.visibility=View.VISIBLE
        if(!TextUtils.isEmpty(title) && !TextUtils.isEmpty(thoughts) && imageUri!=null){
//                Saving the path of images in storage
//            ..../journal_images/our_image.png

            val filepath:StorageReference=storageReference
                .child("journal_images")
                .child("my_image_"+ Timestamp.now().seconds)

//            Uploading the images
            filepath.putFile(imageUri).addOnSuccessListener() {
                filepath.downloadUrl.addOnSuccessListener {
                    var imageUrl:String=it.toString()
                    var timestamp:Timestamp=Timestamp(Date())

//                    creating a journal object
                    val journal:Journal=Journal(title,thoughts,imageUrl,currentUserId,timestamp,currentUserName)

                    collectionReference.add(journal)
                        .addOnSuccessListener {
                        binding.progressBar.visibility=View.INVISIBLE
                        startActivity(Intent(this, JournalListActivity::class.java))
                            finish()
                    }
                }

            }.addOnFailureListener{
                binding.progressBar.visibility=View.INVISIBLE

            }
        }
        else{
            binding.progressBar.visibility=View.INVISIBLE
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode==1 && resultCode== RESULT_OK){
           if(data!=null){
               imageUri=data.data!!     // getting the actual image
               binding.displayImage.setImageURI(imageUri)  //showing the image
               binding.postCameraButton.visibility=View.INVISIBLE
           }
        }
    }

    override fun onStart() {
        super.onStart()
      user = auth.currentUser!!
    }

    override fun onStop() {
        super.onStop()
        if(auth!=null){
            auth.signOut()
        }
    }
}