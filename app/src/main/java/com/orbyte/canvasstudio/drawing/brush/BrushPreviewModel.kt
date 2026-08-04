package com.orbyte.canvasstudio.drawing.brush

import android.graphics.Color
import com.orbyte.canvasstudio.drawing.BrushPreset
import com.orbyte.canvasstudio.drawing.DrawingTool
import com.orbyte.canvasstudio.drawing.pipeline.BrushDab
import com.orbyte.canvasstudio.drawing.sanitized
import com.orbyte.canvasstudio.drawing.toSettings

/**
 * One honest preview trajectory shared by the library and tests. It deliberately routes through
 * the production BrushDabBatchBuilder/BrushEvaluator instead of maintaining decorative dynamics.
 */
internal object BrushPreviewModel {
    fun dabs(preset: BrushPreset): List<BrushDab> = BrushDabBatchBuilder.build(
        points = BrushFixture.points(BrushFixture.Scenario.PREVIEW),
        settings = preset.toSettings(Color.WHITE).sanitized(),
        tool = DrawingTool.BRUSH,
    )
}
