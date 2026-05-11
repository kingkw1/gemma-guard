package com.gemmaguard.sanitizer

data class VttBlock(val startMs: Long, val endMs: Long, val text: String)

object VttParser {
    fun parse(vttContent: String): List<VttBlock> {
        val blocks = mutableListOf<VttBlock>()
        val lines = vttContent.lines().map { it.trim() }
        
        var currentStartMs = -1L
        var currentEndMs = -1L
        val currentText = StringBuilder()
        
        val timePattern = Regex("""(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})\.(\d{3})""")
        
        for (line in lines) {
            if (line.isEmpty() || line.startsWith("WEBVTT")) {
                if (currentStartMs != -1L && currentText.isNotEmpty()) {
                    blocks.add(VttBlock(currentStartMs, currentEndMs, currentText.toString().trim()))
                    currentStartMs = -1L
                    currentEndMs = -1L
                    currentText.clear()
                }
                continue
            }
            
            val match = timePattern.find(line)
            if (match != null) {
                if (currentStartMs != -1L && currentText.isNotEmpty()) {
                    blocks.add(VttBlock(currentStartMs, currentEndMs, currentText.toString().trim()))
                    currentText.clear()
                }
                
                val g = match.groupValues
                currentStartMs = parseTime(g[1], g[2], g[3], g[4])
                currentEndMs = parseTime(g[5], g[6], g[7], g[8])
            } else if (currentStartMs != -1L && !line.matches(Regex("""^\d+$"""))) { // Ignore simple cue numbers
                if (currentText.isNotEmpty()) currentText.append(" ")
                currentText.append(line)
            }
        }
        
        if (currentStartMs != -1L && currentText.isNotEmpty()) {
            blocks.add(VttBlock(currentStartMs, currentEndMs, currentText.toString().trim()))
        }
        
        return blocks
    }
    
    private fun parseTime(h: String, m: String, s: String, ms: String): Long {
        return (h.toLong() * 3600000) + (m.toLong() * 60000) + (s.toLong() * 1000) + ms.toLong()
    }
}
