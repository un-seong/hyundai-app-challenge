package com.chaon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaon.app.ui.AnnounceBanner
import com.chaon.app.ui.ChaonColors
import com.chaon.app.ui.ChaonTheme
import com.chaon.app.ui.DiagScreen
import com.chaon.app.ui.HomeScreen
import com.chaon.app.ui.RestViewModel
import com.chaon.app.ui.ReturnScreen
import com.chaon.app.ui.Screen
import com.chaon.app.ui.SessionScreen
import com.chaon.app.ui.SetupScreen
import com.chaon.app.ui.WakeScreen
import com.chaon.app.ui.Header

class MainActivity : ComponentActivity() {

    private val vm: RestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChaonTheme { ChaonApp(vm) }
        }
    }
}

@Composable
private fun ChaonApp(vm: RestViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()

    // 휴식 중에는 화면이 꺼지지 않게
    val view = LocalView.current
    // 온밤은 밤새 화면을 켜 두지 않는다 (배터리 절약)
    val keepOn = (state.screen == Screen.SESSION && state.session.mode != com.chaon.app.session.RestMode.ONBAM) ||
        state.screen == Screen.WAKE
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Surface 로 감싸야 색을 지정하지 않은 글자가 기본 검정이 아니라 밝은 색으로 나온다
    Surface(Modifier.fillMaxSize(), color = ChaonColors.Bg, contentColor = ChaonColors.Text) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                vehicle = state.vehicle,
                demoMode = state.demoMode,
                onToggleDemo = { vm.setDemoMode(!state.demoMode) },
                onDiag = { vm.goTo(Screen.DIAG) },
                onDemoGear = vm::demoSetGear,
                sttAvailable = state.sttAvailable,
                listening = state.listening,
                onMic = vm::listenCommand,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.screen) {
                    Screen.HOME -> HomeScreen(state, vm)
                    Screen.SETUP -> SetupScreen(state, vm)
                    Screen.SESSION -> SessionScreen(state, vm)
                    Screen.WAKE -> WakeScreen(state, vm)
                    Screen.RETURN -> ReturnScreen(state, vm)
                    Screen.DIAG -> DiagScreen(state, vm)
                }
            }
        }
        AnnounceBanner(state.banner, Modifier.align(Alignment.BottomCenter))
    }
    }
}
