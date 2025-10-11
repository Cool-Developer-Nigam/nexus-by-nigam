package com.nigdroid.journal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.nigdroid.journal.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Return your home fragment layout

        binding= DataBindingUtil.inflate(inflater,R.layout.fragment_home,container,false)
       binding.lifecycleOwner=this

        binding.JournalCard.setOnClickListener {
            val intent = Intent(requireContext(), JournalListActivity::class.java)
            startActivity(intent)
        }
        binding.TodoCard.setOnClickListener {
            val intent = Intent(requireContext(), TodoListActivity::class.java)
            startActivity(intent)
        }
        return binding.root
    }
}