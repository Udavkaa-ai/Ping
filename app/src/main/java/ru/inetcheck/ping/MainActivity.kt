package ru.inetcheck.ping

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var repo: HostsRepository
    private lateinit var globalEdit: EditText
    private lateinit var whitelistEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = HostsRepository(this)
        globalEdit = findViewById(R.id.globalHosts)
        whitelistEdit = findViewById(R.id.whitelistHosts)

        load()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            repo.globalHosts = globalEdit.text.toString().toHostList()
            repo.whitelistHosts = whitelistEdit.text.toString().toHostList()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            WidgetProvider.renderAll(this)
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            repo.resetToDefaults()
            load()
            WidgetProvider.renderAll(this)
        }
    }

    private fun load() {
        globalEdit.setText(repo.globalHosts.joinToString("\n"))
        whitelistEdit.setText(repo.whitelistHosts.joinToString("\n"))
    }

    private fun String.toHostList() =
        lines().map { it.trim() }.filter { it.isNotEmpty() }
}
