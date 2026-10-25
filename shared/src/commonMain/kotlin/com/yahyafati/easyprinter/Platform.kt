package com.yahyafati.easyprinter

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform