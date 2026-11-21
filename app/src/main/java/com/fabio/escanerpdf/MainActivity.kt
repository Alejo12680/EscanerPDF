package com.fabio.escanerpdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fabio.escanerpdf.ui.navegacion.NavegacionApp
import com.fabio.escanerpdf.ui.theme.EscanerPDFTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscanerPDFTheme {
                NavegacionApp()
            }
        }
    }
}
