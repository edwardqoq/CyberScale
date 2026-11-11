package com.cyberscale.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyberscale.app.data.AppContainer
import com.cyberscale.app.ui.CyberScaleRoot
import com.cyberscale.app.ui.theme.CyberScaleTheme
import com.cyberscale.app.vm.ScaleViewModel
import com.cyberscale.app.vm.ScaleViewModelFactory

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as CyberScaleApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 称重时屏幕保持常亮，避免站上秤后息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CyberScaleTheme {
                val vm: ScaleViewModel = viewModel(factory = ScaleViewModelFactory(container))
                CyberScaleRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.bleController.start()
    }

    override fun onStop() {
        // 退到后台就停扫描，避免持续耗电；回到前台自动恢复
        container.bleController.stop()
        super.onStop()
    }
}
