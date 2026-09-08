package com.blindtechnexus.alteloquencetts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The one-time introduction shown before the settings screen. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = stringResource(R.string.welcome_open_source))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.welcome_free))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.welcome_solution))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.welcome_more))
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.welcome_continue))
        }
    }
}
