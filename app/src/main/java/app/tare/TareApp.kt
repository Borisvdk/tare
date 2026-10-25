package app.tare

import android.app.Application
import kotlinx.coroutines.launch

class TareApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.appScope.launch { graph.genericImporter.importIfNeeded() }
    }
}
