/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.realm.example.kmmsample.Platform
import io.realm.example.kmmsample.Calculator
import java.lang.NumberFormatException

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compose for Desktop",
        state = rememberWindowState(width = 600.dp, height = 300.dp)
    ) {
        MaterialTheme {
            var operationResult by remember { mutableStateOf("🤔") }
            var leftOperandText by remember { mutableStateOf("") }
            var rightOperandText by remember { mutableStateOf("") }
            fun add() {
                operationResult = try {
                    val numA = leftOperandText.toInt()
                    val numB = rightOperandText.toInt()
                    "${Calculator.sum(numA, numB)}"
                } catch (e: NumberFormatException) {
                    "\uD83E\uDD14"
                }
            }
            Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    text = Platform().platform
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextField(
                        modifier = Modifier.width(64.dp),
                        value = leftOperandText,
                        onValueChange = { leftOperandText = it; add() },
                    )

                    Text(modifier = Modifier.align(Alignment.CenterVertically), text = "+")

                    TextField(
                        modifier = Modifier.width(64.dp),
                        value = rightOperandText,
                        onValueChange = { rightOperandText = it; add() },
                    )

                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = "= $operationResult"
                    )
                }

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    text = "History Count: ${Calculator.history().size}"
                )

            }
        }
    }
}
