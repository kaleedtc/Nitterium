import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text

@Composable
fun Test() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Title") },
        text = { Text("Text") },
        confirmButton = {
            TextButton(onClick = {}) { Text("OK") }
        }
    )
}
