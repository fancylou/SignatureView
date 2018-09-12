package io.o2oa.signatureview

/**
 * Created by fancyLou on 2018/7/16.
 */

data class SVPoint(
        val x: Float,
        val y: Float,
        val time: Long
) {
    fun velocityFrom(point: SVPoint): Float {
        return distanceTo(point) / (this.time - point.time)
    }

    private fun distanceTo(point: SVPoint): Float {
        return (Math.sqrt(Math.pow((x - point.x).toDouble(), 2.toDouble()) + Math.pow((y - point.y).toDouble(), 2.toDouble()))).toFloat()
    }
}