package pl.misieklab.nfccardlab

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pl.misieklab.nfccardlab.ui.LabApp

class MainActivity : ComponentActivity() {
    private val model by viewModels<LabViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Avoid including credentials in screenshots / recents previews.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { LabApp(model) }
    }
    override fun onResume() {
        super.onResume()
        model.readerStatus(model.manager.enable(this) { tag -> model.onTag(tag) })
    }
    override fun onPause() {
        model.pause()
        model.manager.disable(this)
        super.onPause()
    }
}
