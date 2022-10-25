package org.japancv.preview.image

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Extended interface of [ImageAnalysis.Analyzer] for supporting Dual-camera (RGB Camera and IR camera)
 */
interface DualAnalyzer: ImageAnalysis.Analyzer {
    /**
     * Analyzes images to produce a result from dual-camera.
     *
     * @param rgbImage The image to analyze
     * @param irImage The image to analyze
     */
    fun analyze(rgbImage: ImageProxy, irImage: ImageProxy)
}