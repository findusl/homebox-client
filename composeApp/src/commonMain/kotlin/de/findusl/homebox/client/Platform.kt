package de.findusl.homebox.client

interface Platform {
	val name: String
}

expect fun getPlatform(): Platform
