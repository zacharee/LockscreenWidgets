package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.SettingsActivity
import tk.zwander.lockscreenwidgets.util.prefManager


class MainActivity : AppCompatActivity() {
    companion object {
        const val REQ_INTRO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.add_widget -> {
                val intent = Intent(this, AddWidgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_INTRO) {
            if (resultCode != Activity.RESULT_OK) {
                finish()
            } else {
                prefManager.firstRun = false
            }
        }
    }
}