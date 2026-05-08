package ru.inetcheck.ping

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var repo: HostsRepository
    private lateinit var history: HistoryRepository

    private lateinit var wifiDot: View
    private lateinit var wifiStatus: TextView
    private lateinit var wifiPercent: TextView
    private lateinit var mobileDot: View
    private lateinit var mobileStatus: TextView
    private lateinit var mobilePercent: TextView
    private lateinit var focusWifiDot: View
    private lateinit var focusWifiPercent: TextView
    private lateinit var focusMobileDot: View
    private lateinit var focusMobilePercent: TextView
    private lateinit var chartImage: ImageView

    private lateinit var hostsHeader: View
    private lateinit var hostsContent: View
    private lateinit var hostsChevron: ImageView
    private lateinit var globalEdit: EditText
    private lateinit var whitelistEdit: EditText
    private lateinit var focusEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = HostsRepository(this)
        history = HistoryRepository(this)

        wifiDot = findViewById(R.id.wifiDot)
        wifiStatus = findViewById(R.id.wifiStatus)
        wifiPercent = findViewById(R.id.wifiPercent)
        mobileDot = findViewById(R.id.mobileDot)
        mobileStatus = findViewById(R.id.mobileStatus)
        mobilePercent = findViewById(R.id.mobilePercent)
        focusWifiDot = findViewById(R.id.focusWifiDot)
        focusWifiPercent = findViewById(R.id.focusWifiPercent)
        focusMobileDot = findViewById(R.id.focusMobileDot)
        focusMobilePercent = findViewById(R.id.focusMobilePercent)
        chartImage = findViewById(R.id.historyChart)

        hostsHeader = findViewById(R.id.hostsHeader)
        hostsContent = findViewById(R.id.hostsContent)
        hostsChevron = findViewById(R.id.hostsChevron)
        globalEdit = findViewById(R.id.globalHosts)
        whitelistEdit = findViewById(R.id.whitelistHosts)
        focusEdit = findViewById(R.id.focusHosts)

        loadHosts()

        findViewById<MaterialButton>(R.id.checkNowButton).setOnClickListener {
            sendBroadcast(
                Intent(this, WidgetProvider::class.java)
                    .setAction(WidgetProvider.ACTION_CHECK)
                    .setPackage(packageName)
            )
            Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            repo.globalHosts = globalEdit.text.toString().toHostList()
            repo.whitelistHosts = whitelistEdit.text.toString().toHostList()
            repo.focusHosts = focusEdit.text.toString().toHostList()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            WidgetProvider.renderAll(this)
        }

        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            repo.resetToDefaults()
            loadHosts()
            WidgetProvider.renderAll(this)
        }

        hostsHeader.setOnClickListener { toggleHosts() }

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(WidgetProvider.ONE_TIME_WORK)
            .observe(this) { infos ->
                if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    renderDashboard()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        renderDashboard()
    }

    private fun toggleHosts() {
        val expand = hostsContent.visibility != View.VISIBLE
        hostsContent.visibility = if (expand) View.VISIBLE else View.GONE
        hostsChevron.animate().rotation(if (expand) 180f else 0f).setDuration(150).start()
    }

    private fun loadHosts() {
        globalEdit.setText(repo.globalHosts.joinToString("\n"))
        whitelistEdit.setText(repo.whitelistHosts.joinToString("\n"))
        focusEdit.setText(repo.focusHosts.joinToString("\n"))
    }

    private fun renderDashboard() {
        renderCard(NetworkType.WIFI, wifiDot, wifiStatus, wifiPercent)
        renderCard(NetworkType.MOBILE, mobileDot, mobileStatus, mobilePercent)
        renderFocusRow(NetworkType.WIFI, focusWifiDot, focusWifiPercent)
        renderFocusRow(NetworkType.MOBILE, focusMobileDot, focusMobilePercent)
        renderChart()
    }

    private fun renderCard(network: NetworkType, dot: View, status: TextView, percent: TextView) {
        val s = repo.lastStatusFor(network)
        dot.background = ovalDrawable(statusColorRes(s))
        status.setText(statusLabelRes(s))
        val p = history.availabilityPercent(network)
        percent.text = if (p == null) "—" else "$p%"
    }

    private fun renderFocusRow(network: NetworkType, dot: View, percent: TextView) {
        val s = repo.lastFocusStatusFor(network)
        dot.background = ovalDrawable(focusColorRes(s))
        val p = history.focusPercent(network)
        percent.text = if (p == null) "—" else "$p%"
    }

    private fun renderChart() {
        chartImage.post {
            val w = chartImage.width.takeIf { it > 0 } ?: 800
            val h = chartImage.height.takeIf { it > 0 } ?: 280
            val all = history.load()
            val bmp = ChartRenderer.render(
                this, w, h,
                ChartRenderer.Lane(getString(R.string.network_wifi), all.filter { it.network == NetworkType.WIFI }),
                ChartRenderer.Lane(getString(R.string.network_mobile), all.filter { it.network == NetworkType.MOBILE })
            )
            chartImage.setImageBitmap(bmp)
        }
    }

    private fun ovalDrawable(colorRes: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(this@MainActivity, colorRes))
    }

    private fun statusColorRes(s: Status) = when (s) {
        Status.FULL -> R.color.status_full
        Status.WHITELIST -> R.color.status_whitelist
        Status.NONE -> R.color.status_none
        Status.UNKNOWN -> R.color.status_unknown
    }

    private fun statusLabelRes(s: Status) = when (s) {
        Status.FULL -> R.string.status_open
        Status.WHITELIST -> R.string.status_only_whitelist
        Status.NONE -> R.string.status_blocked
        Status.UNKNOWN -> R.string.status_no_data
    }

    // For focus the colour scheme differs: blocked is the expected, neutral
    // state, only reachability counts as a positive signal.
    private fun focusColorRes(s: Status) = when (s) {
        Status.FULL -> R.color.status_full
        else -> R.color.status_unknown
    }

    private fun String.toHostList() =
        lines().map { it.trim() }.filter { it.isNotEmpty() }
}
