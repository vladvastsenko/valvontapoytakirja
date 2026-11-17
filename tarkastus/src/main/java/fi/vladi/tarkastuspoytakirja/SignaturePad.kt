package fi.vladi.tarkastuspoytakirja

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.model.SignaturePoint
import fi.vladi.tarkastuspoytakirja.model.SignatureStroke

@Composable
fun SignaturePad(
    strokes: List<SignatureStroke>,
    onChange: (List<SignatureStroke>) -> Unit
) {
    var activePoints by remember { mutableStateOf(emptyList<SignaturePoint>()) }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnChange by rememberUpdatedState(onChange)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Allekirjoitus", style = MaterialTheme.typography.titleSmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    fun point(offset: Offset): SignaturePoint = SignaturePoint(
                        x = (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                        y = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                    )
                    detectDragGestures(
                        onDragStart = { offset -> activePoints = listOf(point(offset)) },
                        onDragEnd = {
                            val completed = activePoints
                            if (completed.isNotEmpty()) {
                                latestOnChange(latestStrokes + SignatureStroke(completed))
                            }
                            activePoints = emptyList()
                        },
                        onDragCancel = { activePoints = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            activePoints = activePoints + point(change.position)
                        }
                    )
                }
        ) {
            fun drawStroke(points: List<SignaturePoint>) {
                if (points.isEmpty()) return
                val path = Path()
                points.forEachIndexed { index, point ->
                    val position = Offset(point.x * size.width, point.y * size.height)
                    if (index == 0) path.moveTo(position.x, position.y)
                    else path.lineTo(position.x, position.y)
                }
                if (points.size == 1) {
                    val point = points.first()
                    drawCircle(
                        color = Color.Black,
                        radius = 2.5.dp.toPx(),
                        center = Offset(point.x * size.width, point.y * size.height)
                    )
                } else {
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            strokes.forEach { drawStroke(it.points) }
            drawStroke(activePoints)
        }
        Text("Piirrä allekirjoitus sormella yllä olevaan kenttään.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {
                activePoints = emptyList()
                latestOnChange(emptyList())
            }
        ) { Text("Tyhjennä allekirjoitus") }
    }
}
