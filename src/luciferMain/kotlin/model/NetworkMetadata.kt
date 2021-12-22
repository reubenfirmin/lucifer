package model

data class NetworkMetadata(val pid: Int, val fd: Int, val localIp: String, val localPort: String, val peerIp: String,
                           val peerPort: String, val bytesReceived: Long, val bytesSent: Long,
                           val lastReceived: Int, val lastSent: Int, val org: String?)