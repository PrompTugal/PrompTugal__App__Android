package ai.riskmandate.app.ui

import ai.riskmandate.app.Pt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Mock screens for the Workbench (docs/design/workbench.md), built only from shared components and Material,
 * with fixed fake numbers. Rendered inside the shell at 1280 x 800 dp, so the content area is about 956 dp wide.
 */

@Composable
private fun WorkbenchFrame(tab: Int, running: Boolean = false, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Kicker("Staff")
                Text("Workbench", style = MaterialTheme.typography.headlineSmall, color = Pt.colors.ink)
            }
            StatusChip("On this device", ChipKind.Allowed)
            StatusChip("Gemma 4 E2B, GPU", ChipKind.Neutral)
            if (running) OutlinedButton(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text("Stop") }
            else {
                OutlinedButton(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy run") }
                OutlinedButton(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text("Re-run") }
            }
            Button(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (running) "Running" else "Run") }
        }
        SegmentedControl(listOf("Run", "Experiments", "Tool tests"), tab, {}, Modifier.width(420.dp))
        body()
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    StatRow(label, value)
}

@Composable
private fun SliderLine(label: String, value: Float, text: String, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
            Text(text, style = Pt.type.tabular, color = Pt.colors.ink)
        }
        Slider(value = value, onValueChange = {}, valueRange = range, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
internal fun WorkbenchRunSample() = WorkbenchFrame(tab = 0) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Settings column
        Column(Modifier.width(256.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            TitledCard("Settings", kicker = "Experiment") {
                SettingLine("Model", "Gemma 4 E2B")
                SegmentedControl(listOf("GPU", "CPU"), 0, {})
                SettingLine("System prompt", "RiskMandate base")
                Text(
                    "Summarise the risk in three lines.",
                    style = Pt.type.data, color = Pt.colors.ink,
                    modifier = Modifier.fillMaxWidth().background(Pt.colors.paper2, RoundedCornerShape(10.dp)).padding(10.dp),
                )
                SliderLine("Temperature", 0.35f, "0.7", 0f..1f)
                SliderLine("Top-p", 0.95f, "0.95")
                SettingLine("Top-k", "40")
                SettingLine("Seed", "7")
                SettingLine("Max output tokens", "512")
                SettingLine("Tool set", "File tools (3)")
            }
        }
        // Answer
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitledCard("Answer", kicker = "Streamed, plain text", accessory = { StatusChip("Done", ChipKind.Allowed) }) {
                Text(
                    "The main risk is an agent with write access to the shared drive. It could publish a board deck without anyone approving it. " +
                        "Limit the mandate to read access and hold any sharing for approval.",
                    style = MaterialTheme.typography.bodyLarge, color = Pt.colors.ink,
                )
            }
            TitledCard("Tool calls proposed", accessory = { CountPill(2, "calls") }) {
                StepRow(1, "read_file", "Passed checks", ChipKind.Allowed, detail = "{\"path\":\"board-deck.md\"}", dense = true)
                StepRow(2, "share_file", "Blocked", ChipKind.Blocked, detail = "{\"to\":\"outside@example.com\"}", dense = true)
            }
        }
        // Stats
        Column(Modifier.width(304.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            TitledCard("Stats", kicker = "Last run") {
                Kicker("Timing", color = Pt.colors.muted)
                StatRow("Model load (warm)", "1.8 s", source = "runtime")
                StatRow("Time to first token", "812 ms", source = "runtime")
                StatRow("Prefill", "61.4 tok/s", source = "runtime")
                StatRow("Decode", "5.5 tok/s", source = "runtime")
                StatRow("Total time", "14.2 s", source = "app")
                Kicker("Tokens", color = Pt.colors.muted)
                StatRow("Input", "412", source = "runtime")
                StatRow("Output", "68", source = "runtime")
                Kicker("Engine", color = Pt.colors.muted)
                StatRow("Backend used", "GPU", source = "runtime", tone = Tone.Good)
                StatRow("Stop reason", "done", source = "runtime")
                Kicker("Device", color = Pt.colors.muted)
                StatRow("Memory", "1.9 to 2.3 GB", source = "app")
                StatRow("Battery", "64 to 64 %", source = "app")
                StatRow("Thermal", "normal to normal", source = "app")
                Text("runtime: reported by the runtime. app: measured in the app.", style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
            }
        }
    }
}

@Composable
internal fun WorkbenchExperimentsSample() = WorkbenchFrame(tab = 1, running = true) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(304.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitledCard("Sweep", kicker = "Experiment") {
                SettingLine("Variable", "Temperature")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (v in listOf("0.0", "0.3", "0.7", "1.0")) StatusChip(v, ChipKind.Neutral)
                }
                SettingLine("Repeats", "5")
                SettingLine("Prompt", "Summarise a risk")
                SettingLine("Tool test", "None")
            }
            TitledCard("Progress", accessory = { StatusChip("Within caps", ChipKind.Allowed) }) {
                StatRow("Run", "14 of 20")
                StatRow("Elapsed", "6:12 of 30:00")
                StatRow("Battery", "64 %", tone = Tone.Good)
                StatRow("Thermal", "normal")
                StatRow("Failed in a row", "0 of 3")
                Text("Caps: 100 runs, 30 minutes, stop at 15% battery, stop on severe thermal, stop after 3 failures in a row.", style = MaterialTheme.typography.bodySmall, color = Pt.colors.muted)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedControl(listOf("Sweep", "A/B", "Run log"), 0, {}, Modifier.width(360.dp))
            TitledCard("Temperature sweep", kicker = "Mean and spread over 5 runs each", accessory = { StatusChip("On this device", ChipKind.Neutral) }) {
                DataTable(
                    columns = listOf(
                        TableColumn("Temp.", 0.8f), TableColumn("Runs", 0.7f, true), TableColumn("TTFT, ms", 1.2f, true),
                        TableColumn("Decode, tok/s", 1.3f, true), TableColumn("Output, tokens", 1.2f, true), TableColumn("Tool test", 1.9f),
                    ),
                    rows = listOf(
                        listOf("0.0", "5", "808 ± 12", "5.6 ± 0.1", "64 ± 3"),
                        listOf("0.3", "5", "812 ± 15", "5.5 ± 0.1", "68 ± 9"),
                        listOf("0.7", "5", "815 ± 21", "5.5 ± 0.2", "74 ± 17"),
                        listOf("1.0", "4 of 5", "820 ± 34", "5.4 ± 0.3", "81 ± 29"),
                    ).mapIndexed { i, r ->
                        val passed = listOf(5, 5, 4, 2)[i]
                        r.map { TableCell(it) } + TableCell("$passed of 5") { PassRateBar(passed, 5, "$passed of 5", barWidth = 72.dp) }
                    },
                )
            }
            TitledCard("Run log", accessory = { CountPill(38, "runs") }) {
                DataTable(
                    columns = listOf(
                        TableColumn("Time", 0.7f), TableColumn("Model", 0.8f), TableColumn("Backend", 0.9f), TableColumn("Settings that differ", 1.6f),
                        TableColumn("TTFT, ms", 1.0f, true), TableColumn("tok/s", 0.7f, true), TableColumn("Stop", 1.9f),
                    ),
                    rows = listOf(
                        listOf("10:41", "E2B", "GPU", "temperature 1.0", "822", "5.4"),
                        listOf("10:40", "E2B", "GPU", "temperature 1.0", "815", "5.5"),
                        listOf("10:38", "E2B", "CPU", "backend CPU", "2 140", "1.9"),
                    ).mapIndexed { i, r ->
                        r.map { TableCell(it) } + (if (i == 2) TableCell("max tokens") { StatusChip("Max tokens", ChipKind.Held) } else TableCell("done") { StatusChip("Done", ChipKind.Allowed) })
                    },
                    selectedRow = 0,
                    onRowClick = {},
                )
            }
        }
    }
}

@Composable
internal fun WorkbenchToolTestsSample() = WorkbenchFrame(tab = 2) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("31 of 40", "Native calls", Modifier.weight(1f), tone = Tone.Warn, detail = "78% over 2 runs each")
                StatTile("35 of 40", "Text protocol", Modifier.weight(1f), tone = Tone.Good, detail = "88% over 2 runs each")
            }
            TitledCard("Cases by kind", kicker = "20 cases", accessory = { OutlinedButton(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) { Text("Run all") } }) {
                DataTable(
                    columns = listOf(TableColumn("Case", 2.1f), TableColumn("Kind", 1.9f), TableColumn("Native", 1.8f), TableColumn("Text", 1.8f)),
                    rows = listOf(
                        listOf("right_tool_1", "Right tool", 4 to 5, 5 to 5),
                        listOf("right_tool_2", "Right tool", 5 to 5, 5 to 5),
                        listOf("arg_values_1", "Argument values", 3 to 5, 4 to 5),
                        listOf("no_tool_fits_1", "No tool fits", 2 to 5, 4 to 5),
                        listOf("made_up_name_1", "Not offered", 5 to 5, 5 to 5),
                        listOf("two_step_1", "Two-step", 1 to 5, 3 to 5),
                        listOf("keep_secret_1", "Keeps a secret", 4 to 5, 4 to 5),
                    ).map { (name, kind, a, b) ->
                        listOf(
                            TableCell(name as String), TableCell(kind as String),
                            TableCell("${(a as Pair<*, *>).first} of 5") { PassRateBar(a.first as Int, 5, "${a.first} of 5", barWidth = 44.dp) },
                            TableCell("${(b as Pair<*, *>).first} of 5") { PassRateBar(b.first as Int, 5, "${b.first} of 5", barWidth = 44.dp) },
                        )
                    },
                    selectedRow = 3,
                    onRowClick = {},
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitledCard("no_tool_fits_1", kicker = "No tool fits", accessory = { StatusChip("2 of 5 native", ChipKind.Blocked) }) {
                Text("What this tests: with only calendar tools offered, asked for the weather, the model answers in text instead of calling a tool.", style = MaterialTheme.typography.bodyMedium, color = Pt.colors.muted)
                SegmentedControl(listOf("Native", "Text", "Both"), 2, {})
                StatRow("Tools offered", "add_event, list_events")
                StatRow("Expected", "no tool call")
            }
            TitledCard("Runs", accessory = { CountPill(3, "failed") }) {
                StepRow(1, "Native, 5.6 tok/s", "Passed", ChipKind.Allowed, dense = true)
                StepRow(2, "Native, 5.5 tok/s", "Wrong tool", ChipKind.Blocked, detail = "called add_event {\"title\":\"weather\"}", dense = true)
                StepRow(3, "Text, 5.4 tok/s", "Passed", ChipKind.Allowed, dense = true)
                Text("Raw output, run 2:", style = MaterialTheme.typography.labelMedium, color = Pt.colors.muted)
                Text(
                    "<tool_call>{\"name\":\"add_event\",\"args\":{\"title\":\"weather\"}}</tool_call>",
                    style = Pt.type.data, color = Pt.colors.ink,
                    modifier = Modifier.fillMaxWidth().background(Pt.colors.paper2, RoundedCornerShape(10.dp)).padding(10.dp),
                )
            }
        }
    }
}
