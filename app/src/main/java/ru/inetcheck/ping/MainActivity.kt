package ru.inetcheck.ping

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    private lateinit var repo: HostsRepository
    private lateinit var history: HistoryRepository
    private lateinit var globalEdit: EditText
    private lateinit var whitelistEdit: EditText
    private lateinit var chartImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = HostsRepository(this)
        history = HistoryRepository(this)
        globalEdit = findViewById(R.id.globalHosts)
        whitelistEdit = findViewById(R.id.whitelistHosts)
        chartImage = findViewById(R.id.historyChart)

        loadHosts()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            repo.globalHosts = globalEdit.text.toString().toHostList()
            repo.whitelistHosts = whitelistEdit.text.toString().toHostList()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            WidgetProvider.renderAll(this)
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            repo.resetToDefaults()
            loadHosts()
            WidgetProvider.renderAll(this)
        }

        findViewById<Button>(R.id.checkNowButton).setOnClickListener {
            sendBroadcast(
                Intent(this, WidgetProvider::class.java)
                    .setAction(WidgetProvider.ACTION_CHECK)
                    .setPackage(packageName)
            )
            Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show()
        }

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(WidgetProvider.ONE_TIME_WORK)
            .observe(this) { infos ->
                if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    renderChart()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        renderChart()
    }

    private fun loadHosts() {
        globalEdit.setText(repo.globalHosts.joinToString("\n"))
        whitelistEdit.setText(repo.whitelistHosts.joinToString("\n"))
    }

    private fun renderChart() {
        chartImage.post {
            val w = chartImage.width.takeIf { it > 0 } ?: 800
            val h = chartImage.height.takeIf { it > 0 } ?: 240
            val bmp = ChartRenderer.render(
                w, h, history.load(), getString(R.string.history_empty)
            )
            chartImage.setImageBitmap(bmp)
        }
    }

    private fun String.toHostList() =
        lines().map { it.trim() }.filter { it.isNotEmpty() }
}
