package model

data class ProcessMetadata(val pid: Int, val cpu: Double, val memory: Double, val cpuTime: Int, val command: String)