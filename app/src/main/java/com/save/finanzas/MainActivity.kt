package com.save.finanzas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.save.finanzas.notify.Notifier
import com.save.finanzas.ui.AppViewModel
import com.save.finanzas.ui.SaveApp
import com.save.finanzas.ui.SaveTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.schedule(this)
        setContent { SaveTheme { SaveApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        // Por si se marcó algo como pagado desde una notificación
        vm.reload()
    }
}
