package com.example.audiobookplayerbeta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var spinnerRewindValue: Spinner
    private lateinit var checkBoxAutoplayNext: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferencesManager = SharedPreferencesManager(this)

        spinnerRewindValue = findViewById(R.id.spinner_rewind_value)
        checkBoxAutoplayNext = findViewById(R.id.checkbox_autoplay_next)
        val imagePrevious: ImageView = findViewById(R.id.image_previous)

        setupRewindSpinner()
        setupAutoplayCheckbox()

        imagePrevious.setOnClickListener {
            finish() // Return to the previous screen
        }
    }

    private fun setupRewindSpinner() {
        val rewindValues = resources.getStringArray(R.array.rewind_values).toList()

        // Create the custom adapter
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, android.R.id.text1, rewindValues) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView.text = getItem(position)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView.text = getItem(position)
                return view
            }
        }

        spinnerRewindValue.adapter = adapter

        // Set the current selection based on shared preferences
        val currentValue = "${sharedPreferencesManager.rewindValue}s"
        val selectedIndex = rewindValues.indexOf(currentValue)
        spinnerRewindValue.setSelection(if (selectedIndex >= 0) selectedIndex else 1) // Default to 10s

        spinnerRewindValue.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedValue = rewindValues[position].replace("s", "").toInt() // Convert "5s" to 5
                sharedPreferencesManager.rewindValue = selectedValue

                // Broadcast the change
                Intent("com.example.audiobookplayerbeta.PREFERENCE_CHANGED").apply {
                    putExtra("rewind_value", selectedValue)
                    sendBroadcast(this)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAutoplayCheckbox() {
        checkBoxAutoplayNext.isChecked = sharedPreferencesManager.autoplayNext
        checkBoxAutoplayNext.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesManager.autoplayNext = isChecked
        }
    }
}
